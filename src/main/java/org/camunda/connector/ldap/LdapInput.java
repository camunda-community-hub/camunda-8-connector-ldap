package org.camunda.connector.ldap;

/* ******************************************************************** */
/*                                                                      */
/*  LdapInput                                                           */
/*                                                                      */
/*  Inputs of the LDAP search. The RunnerParameter list describes each  */
/*  input for the Cherry element template. The public field names are   */
/*  the input variable names read at runtime.                           */
/* ******************************************************************** */

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.cherrytemplate.CherryInput;
import io.camunda.connector.cherrytemplate.RunnerParameter;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.camunda.connector.ldap.connection.LdapClient.LdapProtocol;
import org.camunda.connector.ldap.connection.LdapDerefAliases;
import org.camunda.connector.ldap.connection.LdapScope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * the JsonIgnoreProperties is mandatory: the template may contain additional widget to help the designer.
 * This avoids the MAPPING Exception
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class LdapInput implements CherryInput {

  /** How the connection authenticates: without credentials, or with the username and password. */
  public enum ConnectionType {
    ANONYMOUS,
    USER_PASSWORD
  }

    /**
     * Attention, each Input here must be added in the LdapFunction, list of InputVariables
     */
    public static final String HOST = "host";
    public static final String PORT = "port";
    public static final String PROTOCOL = "protocol";
    public static final String USERNAME = "username";
    public static final String PASSWORD = "password";
  public static final String CONNECTION_TYPE = "connectionType";
    public static final String BASE_DN = "baseDn";
    public static final String SCOPE = "scope";
    public static final String FILTER = "filter";
    public static final String ATTRIBUTES = "attributes";
    public static final String FOLLOW_REFERRALS = "followReferrals";
    public static final String DEREF_ALIASES = "derefAliases";
    public static final String LDAP_SEARCH_PAGE_SIZE = "ldapSearchPageSize";
    public static final String PAGE_SIZE = "pageSize";
    public static final String PAGE_NUMBER = "pageNumber";
    public static final String TIME_LIMIT = "timeLimit";
    public static final String LDAP_FUNCTION = "ldapFunction";
    public static final String OBJECT_CLASS = "objectClass";
    public static final String DN = "dn";
    public static final String ENTRY_ATTRIBUTES = "entryAttributes";
    public static final String SOURCE_DN = "sourceDn";
    public static final String DESTINATION_DN = "destinationDn";
    public static final String CREATE_ENTRY_DN = "CreateEntryDN";
    public static final String CREATE_ENTRY_ATTRIBUTES = "createEntryAttributes";
    public static final String DELETE_ENTRY_DN = "DeleteEntryDN";
    public static final String IGNORE_IF_ALREADY_DONE = "IgnoreIfAlreadyDone";
    public static final String PROTOCOL_LDAPS = LdapProtocol.LDAPS.name();
    public static final String PROTOCOL_TLS = LdapProtocol.TLS.name();
    public static final String PROTOCOL_LDAP = LdapProtocol.LDAP.name();
    public static final RunnerParameter parameterHost = RunnerParameter.getInstance(
                    HOST,
                    "Host", // label
                    String.class,
                    RunnerParameter.Level.REQUIRED,
                    "Host name or IP address of the directory server")
            .setGroup("LdapConnection");
    public static final RunnerParameter parameterPort = RunnerParameter.getInstance(
                    PORT,
                    "Port",
                    Integer.class,
                    RunnerParameter.Level.OPTIONAL,
                    "Port of the directory server. Empty means 636 for LDAPS and 389 otherwise")
            .setVisibleInTemplate()
      .setGroup("LdapConnection");
    public static final RunnerParameter parameterProtocol = RunnerParameter.getInstance(
                    PROTOCOL,
                    "Protocol",
                    String.class,
                    PROTOCOL_LDAPS,
                    RunnerParameter.Level.REQUIRED,
                    "LDAPS (TLS from the start), TLS (StartTLS) or LDAP (no encryption)")
            .addChoice(PROTOCOL_LDAPS, "LDAPS (TLS from the start)")
            .addChoice(PROTOCOL_TLS, "TLS (StartTLS)")
            .addChoice(PROTOCOL_LDAP, "LDAP (no encryption)")
            .setGroup("LdapConnection");
    public static final RunnerParameter parameterConnectionType = RunnerParameter.getInstance(
          CONNECTION_TYPE,
          "Connection type",
          String.class,
          "ANONYMOUS",
          RunnerParameter.Level.REQUIRED,
          "anonymous: no bind. user/password: bind with the username and password below")
      .addChoice("ANONYMOUS", "anonymous")
      .addChoice("USER_PASSWORD", "user/password")
      .setGroup("LdapConnection");

  public static final RunnerParameter parameterUsername = RunnerParameter.getInstance(
                    USERNAME,
                    "Username (bind DN)",
                    String.class,
                    RunnerParameter.Level.REQUIRED,
                    "Bind DN, for example cn=search,dc=example,dc=org. Empty means an anonymous bind")
            .addCondition(CONNECTION_TYPE, List.of("USER_PASSWORD"))
      .setGroup("LdapConnection");
    public static final RunnerParameter parameterPassword = RunnerParameter.getInstance(
                    PASSWORD,
                    "Password",
                    String.class,
                    RunnerParameter.Level.REQUIRED,
                    "Required when a username is set. Use a secret, for example {{secrets.LDAP_PASSWORD}}")
            .addCondition(CONNECTION_TYPE, List.of("USER_PASSWORD"))
      .setGroup("LdapConnection");
    public static final RunnerParameter parameterBaseDn = RunnerParameter.getInstance(
                    BASE_DN,
                    "Base DN",
                    String.class,
                    RunnerParameter.Level.REQUIRED,
                    "DN of the entry where the search starts, for example ou=people,dc=example,dc=org")
            .addCondition(LDAP_FUNCTION, List.of("SEARCH"))
            .setGroup("Search");
    public static final RunnerParameter parameterScope = RunnerParameter.getInstance(
                    SCOPE,
                    "Scope",
                    String.class,
                    "SUBTREE",
                    RunnerParameter.Level.REQUIRED,
                    "How deep the search goes from the base DN")
            .addChoice(LdapScope.SUBTREE.name(), "Subtree (base and all descendants)")
            .addChoice(LdapScope.ONELEVEL.name(), "One level (direct children)")
            .addChoice(LdapScope.BASE.name(), "Base (base entry only)")
            .addCondition(LDAP_FUNCTION, List.of("SEARCH"))
            .setGroup("Search");
    public static final RunnerParameter parameterFilter = RunnerParameter.getInstance(
                    FILTER,
                    "Filter",
                    String.class,
                    RunnerParameter.Level.REQUIRED,
                    "LDAP search filter, for example (&(objectClass=inetOrgPerson)(uid=alice)). Escape values taken from process variables (RFC 4515)")
            .addCondition(LDAP_FUNCTION, List.of("SEARCH"))
            .setGroup("Search");
    public static final RunnerParameter parameterAttributes = RunnerParameter.getInstance(
                    ATTRIBUTES,
                    "Attributes",
                    String.class,
                    RunnerParameter.Level.OPTIONAL,
                    "Comma-separated attribute names to return, for example cn, mail. Empty returns all attributes")
            .addCondition(LDAP_FUNCTION, List.of("SEARCH"))
            .setGroup("Search");
    public static final RunnerParameter parameterPageNumber = RunnerParameter.getInstance(
                    PAGE_NUMBER,
                    "Page number",
                    Long.class,
                    "0",
                    RunnerParameter.Level.REQUIRED,
                    "Page to return, starting at 0 (0 is the first page). Ignored when page size is 0")
            .addCondition(LDAP_FUNCTION, List.of("SEARCH"))
            .setGroup("Search");
    public static final RunnerParameter parameterPageSize = RunnerParameter.getInstance(
                    PAGE_SIZE,
                    "Page size",
                    Long.class,
                    "0",
                    RunnerParameter.Level.REQUIRED,
                    "Number of entries in one result page. 0 returns all entries")
            .addCondition(LDAP_FUNCTION, List.of("SEARCH"))
            .setGroup("Search");
    public static final RunnerParameter parameterFollowReferrals = RunnerParameter.getInstance(
                    FOLLOW_REFERRALS,
                    "Follow referrals",
                    Boolean.class,
                    "false",
                    RunnerParameter.Level.OPTIONAL,
                    "Follow LDAP referrals to other directory servers")
            .addCondition(LDAP_FUNCTION, List.of("SEARCH"))
            .setGroup("Options");
    public static final RunnerParameter parameterDerefAliases = RunnerParameter.getInstance(
                    DEREF_ALIASES,
                    "Dereference aliases",
                    String.class,
                    "ALWAYS",
                    RunnerParameter.Level.REQUIRED,
                    "How alias entries are resolved during the search")
            .addChoice(LdapDerefAliases.ALWAYS.name(), "Always")
            .addChoice(LdapDerefAliases.NEVER.name(), "Never")
            .addChoice(LdapDerefAliases.SEARCHING.name(), "When searching")
            .addChoice(LdapDerefAliases.FINDING.name(), "When finding the base")
            .addCondition(LDAP_FUNCTION, List.of("SEARCH"))
            .setGroup("Options");
    public static final RunnerParameter parameterLdapSearchPageSize = RunnerParameter.getInstance(
                    LDAP_SEARCH_PAGE_SIZE,
                    "LDAP search page size",
                    Long.class,
                    "100",
                    RunnerParameter.Level.REQUIRED,
                    "Number of entries the directory sends per request (LDAP paged results). 0 disables paging. This is a transport setting: it does not change the result")
            .addCondition(LDAP_FUNCTION, List.of("SEARCH"))
            .setGroup("Options");
    public static final RunnerParameter parameterIgnoreIfAlreadyDone = RunnerParameter.getInstance(
                    IGNORE_IF_ALREADY_DONE,
                    "Ignore if already done",
                    Boolean.class,
                    "false",
                    RunnerParameter.Level.OPTIONAL,
                    "CREATE: an entry that already exists is not an error. DELETE: an entry that does not exist is not an error")
            .addCondition(LDAP_FUNCTION, List.of("CREATE", "DELETE"))
            .setVisibleInTemplate()
            .setGroup("Options");
    public static final RunnerParameter parameterTimeLimit = RunnerParameter.getInstance(
                    TIME_LIMIT,
                    "Time limit (seconds)",
                    Integer.class,
                    "30",
                    RunnerParameter.Level.OPTIONAL,
                    "Maximum duration of the search in seconds. 0 means no limit")
            .setGroup("Options");
    public static final RunnerParameter parameterLdapFunction = RunnerParameter.getInstance(
                    LDAP_FUNCTION,
                    "LDAP function",
                    String.class,
                    "SEARCH",
                    RunnerParameter.Level.REQUIRED,
                    "Operation to run on the directory")
            .addChoice("SEARCH", "Search entries")
            .addChoice("CREATE", "Create an entry")
            .addChoice("MODIFY", "Modify the attributes of an entry")
            .addChoice("DELETE", "Delete an entry")
            .addChoice("RENAME", "Rename or move an entry")
            .setGroup("Operation");
    public static final RunnerParameter parameterObjectClass = RunnerParameter.getInstance(
                    OBJECT_CLASS,
                    "Object class",
                    String.class,
                    RunnerParameter.Level.REQUIRED,
                    "Object class of the new entry, for example inetOrgPerson. Separate several with commas")
            .addCondition(LDAP_FUNCTION, List.of("CREATE"))
            .setGroup("Create");
    public static final RunnerParameter parameterDn = RunnerParameter.getInstance(
                    DN,
                    "DN",
                    String.class,
                    RunnerParameter.Level.REQUIRED,
                    "Full DN of the existing entry to modify")
            .addCondition(LDAP_FUNCTION, List.of("MODIFY"))
            .setGroup("Entry");
    public static final RunnerParameter parameterEntryAttributes = RunnerParameter.getInstance(
                    ENTRY_ATTRIBUTES,
                    "Attributes",
                    Map.class,
                    RunnerParameter.Level.REQUIRED,
                    "Attributes as name/value pairs. For MODIFY, an empty value removes the attribute")
            .addCondition(LDAP_FUNCTION, List.of("MODIFY"))
            .setGroup("Entry");
    public static final RunnerParameter parameterCreateEntryDn = RunnerParameter.getInstance(
                    CREATE_ENTRY_DN,
                    "DN of the new entry",
                    String.class,
                    RunnerParameter.Level.REQUIRED,
                    "Full DN of the entry to create, for example uid=carol,ou=people,dc=example,dc=org")
            .addCondition(LDAP_FUNCTION, List.of("CREATE"))
            .setGroup("Create");
    public static final RunnerParameter parameterCreateEntryAttributes = RunnerParameter.getInstance(
                    CREATE_ENTRY_ATTRIBUTES,
                    "Attributes of the new entry",
                    Map.class,
                    RunnerParameter.Level.REQUIRED,
                    "Attributes as name/value pairs, for example cn, sn and mail")
            .addCondition(LDAP_FUNCTION, List.of("CREATE"))
            .setGroup("Create");
    public static final RunnerParameter parameterDeleteEntryDn = RunnerParameter.getInstance(
                    DELETE_ENTRY_DN,
                    "DN of the entry to delete",
                    String.class,
                    RunnerParameter.Level.REQUIRED,
                    "Full DN of the entry to delete. An entry with children cannot be deleted")
            .addCondition(LDAP_FUNCTION, List.of("DELETE"))
            .setGroup("Delete");
    public static final RunnerParameter parameterSourceDn = RunnerParameter.getInstance(
                    SOURCE_DN,
                    "Source DN",
                    String.class,
                    RunnerParameter.Level.REQUIRED,
                    "Current full DN of the entry")
            .addCondition(LDAP_FUNCTION, List.of("RENAME"))
            .setGroup("Rename");
    public static final RunnerParameter parameterDestinationDn = RunnerParameter.getInstance(
                    DESTINATION_DN,
                    "Destination DN",
                    String.class,
                    RunnerParameter.Level.REQUIRED,
                    "New full DN of the entry")
            .addCondition(LDAP_FUNCTION, List.of("RENAME"))
            .setGroup("Rename");
    private static final Logger logger = LoggerFactory.getLogger(LdapInput.class);
    @JsonIgnore
    public List<RunnerParameter> inputParametersList = List.of(
            parameterLdapFunction,
            parameterHost,
            parameterPort,
            parameterProtocol,
            parameterConnectionType,
            parameterUsername,
            parameterPassword,
            parameterBaseDn,
            parameterScope,
            parameterFilter,
            parameterAttributes,
            parameterFollowReferrals,
            parameterDerefAliases,
            parameterLdapSearchPageSize,
            parameterPageSize,
            parameterPageNumber,
            parameterIgnoreIfAlreadyDone,
            parameterTimeLimit,
            parameterObjectClass,
            parameterDn,
            parameterEntryAttributes,
            parameterSourceDn,
            parameterDestinationDn,
            parameterCreateEntryDn,
            parameterCreateEntryAttributes,
            parameterDeleteEntryDn);

    @NotBlank
    public String host;

    @Min(1)
    @Max(65535)
    public Integer port;

    @NotNull
    public LdapProtocol protocol = LdapProtocol.LDAPS;

    public String username;

    public String password;

  public ConnectionType connectionType = ConnectionType.ANONYMOUS;

    public String baseDn;

    @NotNull
    public LdapScope scope = LdapScope.SUBTREE;

    public String filter;

    public String attributes;

    public Boolean followReferrals = false;

    @NotNull
    public LdapDerefAliases derefAliases = LdapDerefAliases.ALWAYS;

    @Min(0)
    public Long ldapSearchPageSize = 100L;

    @Min(0)
    public Long pageSize = 0L;

    @Min(0)
    public Long pageNumber = 0L;

    @Min(0)
    public Integer timeLimit = 30;

    /**
     * CREATE: an existing entry is success. DELETE: a missing entry is success.
     */
    public Boolean IgnoreIfAlreadyDone = false;

    /**
     * The operation to run: SEARCH (default), CREATE, MODIFY, DELETE or RENAME.
     */
    public String ldapFunction = "SEARCH";

    public String objectClass;

    public String dn;

    public Map<String, Object> entryAttributes;

    public String sourceDn;

    public String destinationDn;

    public String CreateEntryDN;

    public Map<String, Object> createEntryAttributes;

    public String DeleteEntryDN;

    public LdapInput() {
    }

    public LdapInput(String host, Integer port, LdapProtocol protocol, String username, String password,
                     String baseDn, LdapScope scope, String filter, String attributes, Boolean followReferrals,
                     LdapDerefAliases derefAliases, Long ldapSearchPageSize, Long pageSize, Long pageNumber, Integer timeLimit) {
        this.host = host;
        this.port = port;
        this.protocol = protocol;
        this.username = username;
        this.password = password;
        this.baseDn = baseDn;
        this.scope = scope;
        this.filter = filter;
        this.attributes = attributes;
        this.followReferrals = followReferrals;
        this.derefAliases = derefAliases;
        this.ldapSearchPageSize = ldapSearchPageSize;
        this.pageSize = pageSize;
        this.pageNumber = pageNumber;
        this.timeLimit = timeLimit;
        this.connectionType = username == null || username.isBlank() ? ConnectionType.ANONYMOUS : ConnectionType.USER_PASSWORD;
    }

    /**
     * @return true when CREATE and DELETE must treat "already done" as a success
     */
    public boolean ignoresIfAlreadyDone() {
        return Boolean.TRUE.equals(IgnoreIfAlreadyDone);
    }

    public String getLdapFunction() {
        return ldapFunction;
    }

    @JsonIgnore
    @Override
    public List<Map<String, Object>> getInputParameters() {
        return inputParametersList.stream().map(t -> t.toMap(null)).toList();
    }

    /**
     * @return the port to connect to, applying the protocol default when none is set
     */
    public int effectivePort() {
        if (port != null) {
            return port;
        }
        return protocol == LdapProtocol.LDAPS ? 636 : 389;
    }

    /**
     * @return true when a bind DN is configured, so the search binds instead of running anonymously
     */
    public boolean hasCredentials() {
        return connectionType == ConnectionType.USER_PASSWORD && username != null && !username.isBlank();
    }

    /**
     * @return the attribute names to return, or null to return all attributes
     */
    public String[] attributeNames() {
        if (attributes == null || attributes.isBlank()) {
            return null;
        }
        return Arrays.stream(attributes.split(","))
                .map(String::trim)
                .filter(name -> !name.isEmpty())
                .toArray(String[]::new);
    }

    /**
     * @return the LDAP paged-results page size (transport), 0 (paging disabled) when unset
     */
    public long ldapSearchPageSizeValue() {
        return ldapSearchPageSize == null ? 0 : ldapSearchPageSize;
    }

    /**
     * @return the number of entries in one result page, 0 (no paging, all entries) when unset
     */
    public long pageSizeValue() {
        return pageSize == null ? 0 : pageSize;
    }

    /**
     * @return the requested page, starting at 0; 0 (the first page) when unset
     */
    public long pageNumberValue() {
        return pageNumber == null ? 0 : pageNumber;
    }

    /**
     * The number of entries the search has to read so that the requested page is available:
     * {@code (pageNumber + 1) * pageSize}.
     *
     * @return the number of entries to read, 0 (no limit) when paging is disabled
     */
    public long resultLimit() {
        if (pageSizeValue() == 0) {
            return 0;
        }
        return Math.multiplyExact(Math.addExact(pageNumberValue(), 1), pageSizeValue());
    }

    /**
     * @return the search duration in seconds, 0 (no limit) when unset
     */
    public int timeLimitSeconds() {
        return timeLimit == null ? 0 : timeLimit;
    }

    /**
     * @return whether LDAP referrals are followed, false when unset
     */
    public boolean followsReferrals() {
        return Boolean.TRUE.equals(followReferrals);
    }

    /**
     * Checks the cross-field rules that the Jakarta constraints cannot express.
     *
     * @throws ConnectorException with code {@code INVALID_INPUT} when the credentials are incomplete
     */
    public void checkCredentials() throws ConnectorException {
        if (connectionType != ConnectionType.USER_PASSWORD) {
            // anonymous: username and password are not used
            return;
        }
        if (username == null || username.isBlank()) {
            logger.error("A username is required for a user/password connection");
            throw new ConnectorException(LdapError.INVALID_INPUT, "A username is required for a user/password connection");
        }
        if (password == null || password.isEmpty()) {
            logger.error("A password is required for a user/password connection");
            throw new ConnectorException(LdapError.INVALID_INPUT, "A password is required for a user/password connection");
        }
    }

    /**
     * Checks the paging rules: a page number needs a page size, and the requested page must be computable.
     *
     * @throws ConnectorException with code {@code INVALID_INPUT} when the paging inputs are inconsistent
     */
    public void checkPaging() throws ConnectorException {
        if (pageSizeValue() == 0 && pageNumberValue() > 0) {
            logger.error("A page size greater than 0 is required to select a page number");
            throw new ConnectorException(LdapError.INVALID_INPUT, "A page size greater than 0 is required to select a page number");
        }
        try {
            resultLimit();
        } catch (ArithmeticException e) {
            logger.error("The page number and page size are too large", e);
            throw new ConnectorException(LdapError.INVALID_INPUT, "The page number and page size are too large", e);
        }
    }
}
