package org.camunda.connector.ldap;

public class LdapError {
    public static final String INVALID_INPUT = "INVALID_INPUT";
    public static final String INVALID_INPUT_LABEL = "The username and password do not go together";

    public static final String LDAP_AUTHENTICATION_FAILED = "LDAP_AUTHENTICATION_FAILED";
    public static final String LDAP_AUTHENTICATION_FAILED_LABEL = "The LDAP bind was rejected";

    public static final String LDAP_CONNECTION_FAILED = "LDAP_CONNECTION_FAILED";
    public static final String LDAP_CONNECTION_FAILED_LABEL = "The LDAP server cannot be reached";

    public static final String LDAP_SEARCH_FAILED = "LDAP_SEARCH_FAILED";
    public static final String LDAP_SEARCH_FAILED_LABEL = "The LDAP search failed";

    public static final String UNKNOWN_FUNCTION = "UNKNOWN_FUNCTION";
    public static final String UNKNOWN_FUNCTION_LABEL = "The LDAP function is unknown";

    public static final String LDAP_CREATE_FAILED = "LDAP_CREATE_FAILED";
    public static final String LDAP_CREATE_FAILED_LABEL = "The LDAP entry could not be created";

    public static final String LDAP_MODIFY_FAILED = "LDAP_MODIFY_FAILED";
    public static final String LDAP_MODIFY_FAILED_LABEL = "The LDAP entry could not be modified";

    public static final String LDAP_DELETE_FAILED = "LDAP_DELETE_FAILED";
    public static final String LDAP_DELETE_FAILED_LABEL = "The LDAP entry could not be deleted";

    public static final String LDAP_RENAME_FAILED = "LDAP_RENAME_FAILED";
    public static final String LDAP_RENAME_FAILED_LABEL = "The LDAP entry could not be renamed";
}
