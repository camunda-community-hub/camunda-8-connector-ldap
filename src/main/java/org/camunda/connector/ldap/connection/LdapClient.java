package org.camunda.connector.ldap.connection;

import io.camunda.connector.api.error.ConnectorException;
import org.camunda.connector.ldap.LdapError;
import org.camunda.connector.ldap.LdapInput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.naming.*;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;
import javax.naming.ldap.Control;
import javax.naming.ldap.LdapContext;
import javax.naming.ldap.PagedResultsControl;
import javax.naming.ldap.PagedResultsResponseControl;
import java.io.IOException;
import java.util.*;

/**
 * Runs an LDAP search with the JDK's JNDI LDAP provider. The connection is taken from a {@link LdapContextFactory},
 * so consecutive searches with the same connection parameters reuse the same open connection.
 */
public class LdapClient {

    private static final Logger logger = LoggerFactory.getLogger(LdapClient.class);
    /**
     * The password attribute is never returned to the process, whatever the input asks for.
     */
    private static final String EXCLUDED_ATTRIBUTE = "userPassword";
    private final LdapContextFactory ldapContextFactory;

    public LdapClient() {
        this(LdapContextFactory.getInstance());
    }

    public LdapClient(LdapContextFactory ldapContextFactory) {
        this.ldapContextFactory = ldapContextFactory;
    }

    private static <T> T runOn(LdapContextFactory.LdapConnection ldapConnection, LdapOperation<T> operation)
            throws NamingException, IOException {
        synchronized (ldapConnection) {
            if (ldapConnection.isClosed()) {
                throw new CommunicationException("The LDAP connection was closed by another operation");
            }
            return operation.run(ldapConnection.context());
        }
    }

    private static List<LdapEntry> readEntries(LdapContext ctx, LdapInput ldapInput)
            throws NamingException, IOException {
        List<LdapEntry> entries = doSearch(ctx, ldapInput);
        return ldapInput.pageSizeValue() > 0 ? pageOf(entries, ldapInput) : entries;
    }

    private static List<LdapEntry> doSearch(LdapContext ctx, LdapInput ldapInput)
            throws NamingException, IOException {
        // A paged search leaves its control on a reused connection: clear it before this search
        ctx.setRequestControls(null);

        SearchControls controls = new SearchControls();
        controls.setSearchScope(ldapInput.scope.jndiScope());
        controls.setCountLimit(ldapInput.resultLimit());
        controls.setTimeLimit(ldapInput.timeLimitSeconds() * 1000);
        controls.setReturningAttributes(returnedAttributes(ldapInput));

        List<LdapEntry> entries = new ArrayList<>();
        byte[] cookie = null;
        do {
            if (ldapInput.ldapSearchPageSizeValue() > 0) {
                ctx.setRequestControls(new Control[]{
                        new PagedResultsControl((int) ldapInput.ldapSearchPageSizeValue(), cookie, Control.CRITICAL)
                });
            }
            cookie = readPage(ctx, ldapInput, controls, entries);
        } while (cookie != null && !sizeLimitReached(ldapInput, entries));
        return entries;
    }

    /**
     * Reads one page of results into {@code entries}.
     *
     * @return the cookie of the next page, or null when there is no further page
     */
    private static byte[] readPage(LdapContext ctx, LdapInput request, SearchControls controls,
                                   List<LdapEntry> entries) throws NamingException {
        NamingEnumeration<SearchResult> answer = null;
        try {
            answer = ctx.search(request.baseDn, request.filter, controls);
            while (answer.hasMore() && !sizeLimitReached(request, entries)) {
                entries.add(toEntry(answer.next()));
            }
        } catch (SizeLimitExceededException e) {
            // The server stopped at its own size limit: keep the entries already read
            return null;
        } finally {
            if (answer != null) {
                answer.close();
            }
        }
        return sizeLimitReached(request, entries) ? null : nextCookie(ctx);
    }

    /**
     * Stops reading once {@code (pageNumber + 1) * pageSize} entries are available.
     */
    private static boolean sizeLimitReached(LdapInput request, List<LdapEntry> entries) {
        return request.resultLimit() > 0 && entries.size() >= request.resultLimit();
    }

    /**
     * Returns only the requested page of the entries read. Pages start at 0.
     */
    private static List<LdapEntry> pageOf(List<LdapEntry> entries, LdapInput request) {
        long pageSize = request.pageSizeValue();
        int from = (int) Math.min(request.pageNumberValue() * pageSize, entries.size());
        int to = (int) Math.min(from + pageSize, entries.size());
        return new ArrayList<>(entries.subList(from, to));
    }

    private static byte[] nextCookie(LdapContext ctx) throws NamingException {
        Control[] responseControls = ctx.getResponseControls();
        if (responseControls != null) {
            for (Control control : responseControls) {
                if (control instanceof PagedResultsResponseControl pagedControl) {
                    byte[] cookie = pagedControl.getCookie();
                    return cookie != null && cookie.length > 0 ? cookie : null;
                }
            }
        }
        return null;
    }

    /**
     * The attribute names to ask the server for: the requested ones without the excluded attributes.
     * When no list is given, null asks for all attributes, and the excluded ones are dropped in {@link #toEntry}.
     */
    private static String[] returnedAttributes(LdapInput ldapInput) {
        String[] requested = ldapInput.attributeNames();
        if (requested == null) {
            return null;
        }
        return Arrays.stream(requested)
                .filter(name -> !isExcluded(name))
                .toArray(String[]::new);
    }

    /**
     * Attributes that are never returned, whatever the input says. This list is hardcoded on purpose: it cannot be
     * changed from the element template.
     */
    private static boolean isExcluded(String attributeId) {
        // "userPassword;binary" and similar options refer to the same attribute
        String baseName = attributeId.split(";", 2)[0].trim();
        return EXCLUDED_ATTRIBUTE.equalsIgnoreCase(baseName);
    }

    private static LdapEntry toEntry(SearchResult result) throws NamingException {
        Map<String, List<String>> attributes = new LinkedHashMap<>();
        Attributes found = result.getAttributes();
        NamingEnumeration<? extends Attribute> attributeList = found.getAll();
        while (attributeList.hasMore()) {
            Attribute attribute = attributeList.next();
            if (isExcluded(attribute.getID())) {
                continue;
            }
            List<String> values = attributes.computeIfAbsent(attribute.getID(), name -> new ArrayList<>());
            NamingEnumeration<?> valueList = attribute.getAll();
            while (valueList.hasMore()) {
                values.add(toText(valueList.next()));
            }
        }
        return new LdapEntry(result.getNameInNamespace(), attributes);
    }

    private static String toText(Object value) {
        if (value instanceof byte[] bytes) {
            return Base64.getEncoder().encodeToString(bytes);
        }
        return String.valueOf(value);
    }

    /**
     * Searches the directory.
     *
     * @param ldapInput the validated search inputs
     * @return the matching entries of the requested page, in the order the server returned them
     * @throws ConnectorException with code {@code LDAP_AUTHENTICATION_FAILED}, {@code LDAP_CONNECTION_FAILED}
     *                            or {@code LDAP_SEARCH_FAILED}
     */
    public List<LdapEntry> search(LdapInput ldapInput) throws ConnectorException {
        return execute(ldapInput, LdapError.LDAP_SEARCH_FAILED, ctx -> readEntries(ctx, ldapInput));
    }

    /**
     * Runs an operation on the LDAP connection for these inputs. The connection is reused while it is open; when the
     * server dropped it, a new one is opened once. Errors become connector errors: a rejected bind, an unreachable
     * server, or {@code failureCode} for any other LDAP error.
     *
     * @param ldapInput   the connection parameters
     * @param failureCode the error code used when the operation itself fails
     * @param operation   the work to run on the context
     * @return the result of the operation
     * @throws ConnectorException when the operation or the connection fails
     */
    public <T> T execute(LdapInput ldapInput, String failureCode, LdapOperation<T> operation) throws ConnectorException {
        try {
            return runWithReconnect(ldapInput, operation);
        } catch (AuthenticationException e) {
            logger.error("LDAP bind rejected by {}:{}", ldapInput.host, ldapInput.effectivePort(), e);
            throw new ConnectorException(LdapError.LDAP_AUTHENTICATION_FAILED, "LDAP bind failed: " + e.getMessage(), e);
        } catch (CommunicationException | IOException e) {
            logger.error("Cannot reach LDAP server {}:{}", ldapInput.host, ldapInput.effectivePort(), e);
            throw new ConnectorException(LdapError.LDAP_CONNECTION_FAILED, "Cannot reach the LDAP server: " + e.getMessage(), e);
        } catch (NamingException e) {
            logger.error("LDAP operation {} failed", failureCode, e);
            throw new ConnectorException(failureCode, "LDAP operation failed: " + e.getMessage(), e);
        }
    }

    private <T> T runWithReconnect(LdapInput ldapInput, LdapOperation<T> operation) throws NamingException, IOException {
        LdapContextFactory.LdapConnection ldapConnection = ldapContextFactory.getLdapConnection(ldapInput);
        try {
            return runOn(ldapConnection, operation);
        } catch (CommunicationException e) {
            // The cached connection may have been dropped by the server (idle timeout, restart): open a new one, once
            logger.warn("LDAP connection to {}:{} failed, opening a new one", ldapInput.host, ldapInput.effectivePort(), e);
            ldapContextFactory.evict(ldapConnection);
            return runOn(ldapContextFactory.getLdapConnection(ldapInput), operation);
        }
    }

    public enum LdapProtocol {
        /**
         * Plain LDAP, no encryption.
         */
        LDAP,
        /**
         * LDAP over TLS (LDAPS, port 636 by default).
         */
        LDAPS,
        /**
         * Plain LDAP upgraded to TLS with StartTLS (port 389 by default).
         */
        TLS
    }

    /**
     * An operation run on an open LDAP context.
     */
    @FunctionalInterface
    public interface LdapOperation<T> {
        T run(LdapContext ctx) throws NamingException, IOException;
    }
}
