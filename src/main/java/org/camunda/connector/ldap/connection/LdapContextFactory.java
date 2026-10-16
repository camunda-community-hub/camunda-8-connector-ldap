package org.camunda.connector.ldap.connection;

import org.camunda.connector.ldap.LdapInput;
import org.camunda.connector.ldap.connection.LdapClient.LdapProtocol;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.naming.Context;
import javax.naming.NamingException;
import javax.naming.ldap.InitialLdapContext;
import javax.naming.ldap.LdapContext;
import javax.naming.ldap.StartTlsRequest;
import javax.naming.ldap.StartTlsResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * Keeps LDAP connections open between searches, so that a new search does not have to reconnect and bind again.
 * <p>
 * A connection is identified by a signature: a SHA-256 hash of the host, port, protocol, bind DN, password, referral
 * and alias settings. The password only enters that hash; it is never stored in clear text in the key and never logged.
 * <ul>
 *   <li>no connection for the signature: a new one is opened and kept</li>
 *   <li>a connection for the signature, opened less than {@link #DEFAULT_MAX_AGE} ago: it is reused</li>
 *   <li>a connection older than that: it is closed and a new one is opened</li>
 * </ul>
 * Every acquisition logs the connection parameters at INFO level.
 */
public class LdapContextFactory {

    /**
     * Age after which a connection is closed and opened again.
     */
    public static final Duration DEFAULT_MAX_AGE = Duration.ofHours(1);
    private static final Logger logger = LoggerFactory.getLogger(LdapContextFactory.class);
    private static final String LDAP_CONTEXT_FACTORY = "com.sun.jndi.ldap.LdapCtxFactory";
    private static final LdapContextFactory INSTANCE = new LdapContextFactory(DEFAULT_MAX_AGE, Clock.systemUTC());

    private final Map<String, LdapConnection> connections = new HashMap<>();
    private final Duration maxAge;
    private final Clock clock;

    public LdapContextFactory(Duration maxAge, Clock clock) {
        this.maxAge = maxAge;
        this.clock = clock;
    }

    /**
     * @return the factory shared by all connectors running in this JVM
     */
    public static LdapContextFactory getInstance() {
        return INSTANCE;
    }

    /**
     * Hash identifying a connection. The password is part of it, so two passwords never share a connection.
     */
    static String signature(LdapInput ldapInput) {
        // An anonymous connection has no password: it hashes as the empty string
        String password = ldapInput.hasCredentials() ? Objects.toString(ldapInput.password, "") : "";
        String passCode;
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(password.getBytes(StandardCharsets.UTF_8));
            passCode = HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            // Never fall back to a key without the password: two passwords would then share one connection
            throw new IllegalStateException("SHA-256 is not available to identify LDAP connections", e);
        }
        return ldapInput.host + "_" + ldapInput.effectivePort() + "_" + ldapInput.protocol + "_" + (ldapInput.hasCredentials() ? ldapInput.username : "")
                + "_" + passCode + "_" + ldapInput.followsReferrals() + "_" + ldapInput.derefAliases;
    }

    private static Hashtable<String, Object> environment(LdapInput ldapInput) {
        Hashtable<String, Object> env = new Hashtable<>();
        env.put(Context.INITIAL_CONTEXT_FACTORY, LDAP_CONTEXT_FACTORY);
        env.put(Context.PROVIDER_URL, providerUrl(ldapInput));
        env.put(Context.REFERRAL, ldapInput.followsReferrals() ? "follow" : "ignore");
        env.put("java.naming.ldap.derefAliases", ldapInput.derefAliases.jndiValue());
        // With TLS the credentials are added after StartTLS, so they are not sent in clear text here
        if (ldapInput.protocol != LdapProtocol.TLS && ldapInput.hasCredentials()) {
            env.put(Context.SECURITY_AUTHENTICATION, "simple");
            env.put(Context.SECURITY_PRINCIPAL, ldapInput.username);
            env.put(Context.SECURITY_CREDENTIALS, ldapInput.password);
        } else {
            env.put(Context.SECURITY_AUTHENTICATION, "none");
        }
        return env;
    }

    private static String providerUrl(LdapInput ldapInput) {
        String scheme = ldapInput.protocol == LdapProtocol.LDAPS ? "ldaps://" : "ldap://";
        return scheme + ldapInput.host + ":" + ldapInput.effectivePort();
    }

    /**
     * Returns an open connection for these parameters, reusing the cached one when it is not too old.
     *
     * @param input the search inputs; only the connection parameters are used
     * @return an open connection, which must not be used after it has been evicted or closed
     */
    public synchronized LdapConnection getLdapConnection(LdapInput input) throws NamingException, IOException {
        String signature = signature(input);
        String action = "OPEN";
        LdapConnection cached = connections.get(signature);
        if (cached != null) {
            if (Duration.between(cached.openedAt, clock.instant()).compareTo(maxAge) <= 0) {
                logConnection("REUSE", signature, input);
                return cached;
            }
            // Close under this lock: a search that still uses the old connection holds its monitor, so this waits for it
            connections.remove(signature);
            cached.close();
            action = "RENEW";
        }
        LdapConnection opened = open(signature, input);
        connections.put(signature, opened);
        logConnection(action, signature, input);
        return opened;
    }

    /**
     * Removes a ldapConnection that failed, so that the next acquisition opens a new one.
     *
     * @param ldapConnection the ldapConnection to discard
     */
    public void evict(LdapConnection ldapConnection) {
        synchronized (this) {
            connections.remove(ldapConnection.signature, ldapConnection);
        }
        // Outside the factory lock, so a search that is still running on it can finish first
        ldapConnection.close();
    }

    /**
     * Closes every cached connection.
     */
    public synchronized void closeAll() {
        connections.values().forEach(LdapConnection::close);
        connections.clear();
    }

    private LdapConnection open(String signature, LdapInput ldapInput) throws NamingException, IOException {
        LdapContext ctx = new InitialLdapContext(environment(ldapInput), null);
        StartTlsResponse startTls = null;
        try {
            if (ldapInput.protocol == LdapProtocol.TLS) {
                startTls = (StartTlsResponse) ctx.extendedOperation(new StartTlsRequest());
                startTls.negotiate();
                // Credentials are sent only once the channel is encrypted
                if (ldapInput.hasCredentials()) {
                    ctx.addToEnvironment(Context.SECURITY_AUTHENTICATION, "simple");
                    ctx.addToEnvironment(Context.SECURITY_PRINCIPAL, ldapInput.username);
                    ctx.addToEnvironment(Context.SECURITY_CREDENTIALS, ldapInput.password);
                }
            }
        } catch (NamingException | IOException e) {
            LdapConnection.closeQuietly(startTls, ctx);
            throw e;
        }
        return new LdapConnection(signature, ctx, startTls, clock.instant());
    }

    private void logConnection(String action, String signature, LdapInput ldapInput) {
        logger.info("LDAP connection {}: url={}, username={}, protocol={}, followReferrals={}, derefAliases={}, maxAge={}, signature={}",
                action, providerUrl(ldapInput), ldapInput.username, ldapInput.protocol, ldapInput.followsReferrals(), ldapInput.derefAliases,
                maxAge, signature.substring(0, 12));
    }

    /**
     * An open LDAP context, with the StartTLS response when the connection is upgraded.
     * A connection is used by one thread at a time: callers synchronize on it.
     */
    public static final class LdapConnection {

        private final String signature;
        private final LdapContext context;
        private final StartTlsResponse startTls;
        private final Instant openedAt;
        private boolean closed;

        private LdapConnection(String signature, LdapContext context, StartTlsResponse startTls, Instant openedAt) {
            this.signature = signature;
            this.context = context;
            this.startTls = startTls;
            this.openedAt = openedAt;
        }

        private static void closeQuietly(StartTlsResponse startTls, LdapContext ctx) {
            if (startTls != null) {
                try {
                    startTls.close();
                } catch (IOException ignored) {
                    // the connection is being discarded, so a failure while closing changes nothing
                }
            }
            if (ctx != null) {
                try {
                    ctx.close();
                } catch (NamingException ignored) {
                    // the connection is being discarded, so a failure while closing changes nothing
                }
            }
        }

        /**
         * @return the JNDI context; only valid while the connection is open, and while the caller holds its monitor
         */
        LdapContext context() {
            return context;
        }

        public synchronized boolean isClosed() {
            return closed;
        }

        synchronized void close() {
            if (!closed) {
                closed = true;
                closeQuietly(startTls, context);
            }
        }
    }
}
