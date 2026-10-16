package org.camunda.connector.ldap.connection;

import java.util.List;
import java.util.Map;

/**
 * One entry returned by the search.
 *
 * @param dn         the distinguished name of the entry
 * @param attributes attribute name to its values; binary values are Base64 encoded
 */
public record LdapEntry(String dn, Map<String, List<String>> attributes) {
}
