package org.camunda.connector.ldap.connection;

import javax.naming.directory.SearchControls;

/**
 * How deep the search goes from the base DN.
 */
public enum LdapScope {
    /**
     * Only the base entry.
     */
    BASE(SearchControls.OBJECT_SCOPE),
    /**
     * Direct children of the base entry.
     */
    ONELEVEL(SearchControls.ONELEVEL_SCOPE),
    /**
     * The base entry and all its descendants.
     */
    SUBTREE(SearchControls.SUBTREE_SCOPE);

    private final int jndiScope;

    LdapScope(int jndiScope) {
        this.jndiScope = jndiScope;
    }

    int jndiScope() {
        return jndiScope;
    }
}
