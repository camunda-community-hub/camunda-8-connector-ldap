package org.camunda.connector.ldap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.unboundid.ldap.listener.InMemoryDirectoryServer;
import com.unboundid.ldap.listener.InMemoryDirectoryServerConfig;
import com.unboundid.ldap.listener.InMemoryListenerConfig;
import io.camunda.connector.api.error.ConnectorException;
import org.camunda.connector.ldap.connection.LdapClient;
import org.camunda.connector.ldap.connection.LdapClient.LdapProtocol;
import java.io.IOException;
import java.net.ServerSocket;
import java.util.HashSet;
import java.util.List;

import org.camunda.connector.ldap.connection.LdapDerefAliases;
import org.camunda.connector.ldap.connection.LdapEntry;
import org.camunda.connector.ldap.connection.LdapScope;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class LdapClientTest {

  private static final String BASE_DN = "dc=example,dc=com";
  private static final String ADMIN_DN = "cn=admin,dc=example,dc=com";
  private static final String ADMIN_PASSWORD = "secret";
  private static final String ALICE_DN = "uid=alice,ou=people,dc=example,dc=com";

  private static InMemoryDirectoryServer server;
  private static int port;

  private final LdapClient client = new LdapClient();

  @BeforeAll
  static void startDirectory() throws Exception {
    InMemoryDirectoryServerConfig config = new InMemoryDirectoryServerConfig(BASE_DN);
    config.setListenerConfigs(new InMemoryListenerConfig("plain", null, 0, null, null, null));
    server = new InMemoryDirectoryServer(config);
    server.add(
        "dn: dc=example,dc=com", "objectClass: top", "objectClass: domain", "dc: example");
    server.add(
        "dn: ou=people,dc=example,dc=com", "objectClass: top", "objectClass: organizationalUnit", "ou: people");
    server.add(
        "dn: " + ADMIN_DN, "objectClass: top", "objectClass: person", "cn: admin", "sn: admin",
        "userPassword: " + ADMIN_PASSWORD);
    server.add(
        "dn: " + ALICE_DN, "objectClass: top", "objectClass: person", "objectClass: organizationalPerson",
        "objectClass: inetOrgPerson", "uid: alice", "cn: Alice Martin", "sn: Martin",
        "mail: alice@example.com", "userPassword: alice-pw");
    server.add(
        "dn: uid=bob,ou=people,dc=example,dc=com", "objectClass: top", "objectClass: person",
        "objectClass: organizationalPerson", "objectClass: inetOrgPerson", "uid: bob", "cn: Bob Durand",
        "sn: Durand", "mail: bob@example.com");
    server.startListening();
    port = server.getListenPort();
  }

  @AfterAll
  static void stopDirectory() {
    server.shutDown(true);
  }

  @Test
  void subtreeSearchReturnsMatchingEntriesWithRequestedAttributes() throws ConnectorException {
    List<LdapEntry> entries = client.search(request(BASE_DN, "(objectClass=inetOrgPerson)",
        LdapScope.SUBTREE, "cn, mail", 0, 0, 0));

    assertEquals(2, entries.size());
    LdapEntry alice = entries.stream().filter(e -> e.dn().equals(ALICE_DN)).findFirst().orElseThrow();
    assertEquals(List.of("Alice Martin"), alice.attributes().get("cn"));
    assertEquals(List.of("alice@example.com"), alice.attributes().get("mail"));
    assertFalse(alice.attributes().containsKey("objectClass"), "only the requested attributes are returned");
  }

  @Test
  void userPasswordIsNeverReturned() throws ConnectorException {
    LdapEntry all = client.search(request(ALICE_DN, "(objectClass=*)", LdapScope.BASE, null, 0, 0, 0)).get(0);
    assertFalse(all.attributes().containsKey("userPassword"), "not returned when all attributes are requested");

    LdapEntry requested = client.search(request(ALICE_DN, "(objectClass=*)", LdapScope.BASE, "cn, userPassword", 0, 0, 0)).get(0);
    assertEquals(List.of("Alice Martin"), requested.attributes().get("cn"));
    assertFalse(requested.attributes().containsKey("userPassword"), "not returned when asked for by name");

    LdapEntry onlyPassword = client.search(request(ALICE_DN, "(objectClass=*)", LdapScope.BASE, "userpassword", 0, 0, 0)).get(0);
    assertTrue(onlyPassword.attributes().isEmpty(), "asking only for the password, in any case, returns no attribute");
  }

  @Test
  void baseScopeReturnsOnlyTheBaseEntry() throws ConnectorException {
    List<LdapEntry> entries = client.search(request(ALICE_DN, "(objectClass=*)", LdapScope.BASE, null, 0, 0, 0));

    assertEquals(1, entries.size());
    assertEquals(ALICE_DN, entries.get(0).dn());
  }

  @Test
  void onelevelScopeReturnsDirectChildrenOnly() throws ConnectorException {
    List<LdapEntry> entries = client.search(request(BASE_DN, "(objectClass=*)", LdapScope.ONELEVEL, null, 0, 0, 0));

    // ou=people and cn=admin are direct children; alice and bob are two levels down and must not appear
    List<String> dns = entries.stream().map(LdapEntry::dn).toList();
    assertEquals(2, dns.size());
    assertTrue(dns.contains("ou=people,dc=example,dc=com"));
    assertTrue(dns.contains(ADMIN_DN));
  }

  @Test
  void ldapSearchPageSizeDoesNotChangeTheResult() throws ConnectorException {
    // admin, alice and bob are read one entry per LDAP request, and all three are returned
    List<LdapEntry> entries = client.search(request(BASE_DN, "(objectClass=person)", LdapScope.SUBTREE, null, 1, 0, 0));

    assertEquals(3, entries.size());
  }

  @Test
  void pageNumberZeroReturnsTheFirstPage() throws ConnectorException {
    List<LdapEntry> page = client.search(request(BASE_DN, "(objectClass=person)", LdapScope.SUBTREE, null, 0, 2, 0));

    assertEquals(2, page.size());
  }

  @Test
  void pagesAreDisjointAndCoverAllEntries() throws ConnectorException {
    List<LdapEntry> first = client.search(request(BASE_DN, "(objectClass=person)", LdapScope.SUBTREE, null, 0, 2, 0));
    List<LdapEntry> second = client.search(request(BASE_DN, "(objectClass=person)", LdapScope.SUBTREE, null, 0, 2, 1));
    List<LdapEntry> third = client.search(request(BASE_DN, "(objectClass=person)", LdapScope.SUBTREE, null, 0, 2, 2));

    assertEquals(2, first.size());
    assertEquals(1, second.size(), "the last page holds the remaining entry");
    assertEquals(0, third.size(), "a page past the end is empty");

    HashSet<String> seen = new HashSet<>();
    first.forEach(e -> seen.add(e.dn()));
    second.forEach(e -> seen.add(e.dn()));
    assertEquals(3, seen.size(), "no entry is returned twice across pages");
  }

  @Test
  void pageNumberCombinedWithLdapSearchPageSize() throws ConnectorException {
    // Page 1 of size 2 is entries 3 and 4 of the result; the directory is asked one entry at a time
    List<LdapEntry> page = client.search(request(BASE_DN, "(objectClass=person)", LdapScope.SUBTREE, null, 1, 2, 1));

    assertEquals(1, page.size());
  }

  @Test
  void wrongPasswordFailsWithAuthenticationError() {
    LdapInput wrongPassword = new LdapInput(
        "localhost", port, LdapProtocol.LDAP, ADMIN_DN, "not-the-password", BASE_DN, LdapScope.SUBTREE,
        "(objectClass=*)", null, false, LdapDerefAliases.ALWAYS, 0L, 0L, 0L, 30);

    ConnectorException error = assertThrows(ConnectorException.class, () -> client.search(wrongPassword));
    assertEquals("LDAP_AUTHENTICATION_FAILED", error.getErrorCode());
  }

  @Test
  void unreachableServerFailsWithConnectionError() throws IOException {
    int closedPort;
    try (ServerSocket socket = new ServerSocket(0)) {
      closedPort = socket.getLocalPort();
    }
    LdapInput unreachable = new LdapInput(
        "localhost", closedPort, LdapProtocol.LDAP, null, null, BASE_DN, LdapScope.SUBTREE,
        "(objectClass=*)", null, false, LdapDerefAliases.ALWAYS, 0L, 0L, 0L, 5);

    ConnectorException error = assertThrows(ConnectorException.class, () -> client.search(unreachable));
    assertEquals("LDAP_CONNECTION_FAILED", error.getErrorCode());
  }

  private static LdapInput request(String baseDn, String filter, LdapScope scope, String attributes,
      long ldapSearchPageSize, long pageSize, long pageNumber) {
    return new LdapInput(
        "localhost", port, LdapProtocol.LDAP, ADMIN_DN, ADMIN_PASSWORD, baseDn, scope, filter, attributes,
        false, LdapDerefAliases.ALWAYS, ldapSearchPageSize, pageSize, pageNumber, 30);
  }
}
