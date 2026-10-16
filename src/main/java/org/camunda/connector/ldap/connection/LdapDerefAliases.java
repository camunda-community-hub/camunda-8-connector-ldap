package org.camunda.connector.ldap.connection;

import java.util.Locale;

/**
 * How alias entries are dereferenced during the search (JNDI {@code java.naming.ldap.derefAliases}).
 */
public enum LdapDerefAliases {
    NEVER,
    SEARCHING,
    FINDING,
    ALWAYS;

    String jndiValue() {
        return name().toLowerCase(Locale.ROOT);
    }
}
