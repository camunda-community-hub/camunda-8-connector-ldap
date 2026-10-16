package org.camunda.connector.ldap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.unboundid.ldap.listener.InMemoryDirectoryServer;
import com.unboundid.ldap.listener.InMemoryDirectoryServerConfig;
import com.unboundid.ldap.listener.InMemoryListenerConfig;
import io.camunda.connector.api.error.ConnectorException;
import java.time.Clock;
import java.time.Duration;
import org.camunda.connector.ldap.connection.LdapClient;
import org.camunda.connector.ldap.connection.LdapClient.LdapProtocol;
import org.camunda.connector.ldap.connection.LdapContextFactory;
import org.camunda.connector.ldap.connection.LdapDerefAliases;
import org.camunda.connector.ldap.connection.LdapScope;
import org.camunda.connector.ldap.function.create.LdapCreateFunction;
import org.camunda.connector.ldap.function.delete.LdapDeleteFunction;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.camunda.connector.ldap.LdapOutput.LdapStatus;
import org.junit.jupiter.api.Test;

import java.util.Map;

class LdapOperationTest {

  private static final String BASE_DN = "dc=example,dc=com";
  private static final String ADMIN_DN = "cn=admin,dc=example,dc=com";
  private static final String ADMIN_PASSWORD = "secret";
  private static final String ALICE_DN = "uid=alice,ou=people,dc=example,dc=com";
  private static final String MISSING_DN = "uid=nobody,ou=people,dc=example,dc=com";

  private static InMemoryDirectoryServer server;
  private static int port;

  private final LdapClient client = new LdapClient(new LdapContextFactory(Duration.ofHours(1), Clock.systemUTC()));

  @BeforeAll
  static void startDirectory() throws Exception {
    InMemoryDirectoryServerConfig config = new InMemoryDirectoryServerConfig(BASE_DN);
    config.setListenerConfigs(new InMemoryListenerConfig("plain", null, 0, null, null, null));
    server = new InMemoryDirectoryServer(config);
    server.add("dn: dc=example,dc=com", "objectClass: top", "objectClass: domain", "dc: example");
    server.add("dn: ou=people,dc=example,dc=com", "objectClass: top", "objectClass: organizationalUnit", "ou: people");
    server.add("dn: " + ADMIN_DN, "objectClass: top", "objectClass: person", "cn: admin", "sn: admin",
        "userPassword: " + ADMIN_PASSWORD);
    server.add("dn: " + ALICE_DN, "objectClass: top", "objectClass: person", "objectClass: organizationalPerson",
        "objectClass: inetOrgPerson", "uid: alice", "cn: Alice Martin", "sn: Martin");
    server.startListening();
    port = server.getListenPort();
  }

  @AfterAll
  static void stopDirectory() {
    server.shutDown(true);
  }

  @Test
  void createOfAnExistingEntryIsAnErrorByDefault() {
    ConnectorException error = assertThrows(ConnectorException.class,
        () -> create(ALICE_DN, false));

    assertEquals(LdapError.LDAP_CREATE_FAILED, error.getErrorCode());
  }

  @Test
  void createOfAnExistingEntryIsASuccessWhenIgnoredIfAlreadyDone() throws ConnectorException {
    LdapOutput output = create(ALICE_DN, true);

    assertEquals(LdapStatus.CREATED, output.status);
    assertEquals(ALICE_DN, output.entryDn);
  }

  @Test
  void createOfANewEntryReportsCreated() throws ConnectorException {
    LdapOutput output = create("uid=carol,ou=people,dc=example,dc=com", false);

    assertEquals(LdapStatus.CREATED, output.status);
  }

  @Test
  void deleteOfAMissingEntryIsAnErrorByDefault() {
    ConnectorException error = assertThrows(ConnectorException.class, () -> delete(MISSING_DN, false));

    assertEquals(LdapError.LDAP_DELETE_FAILED, error.getErrorCode());
  }

  @Test
  void deleteOfAMissingEntryIsASuccessWhenIgnoredIfAlreadyDone() throws ConnectorException {
    LdapOutput output = delete(MISSING_DN, true);

    assertEquals(LdapStatus.ALREADY_DELETED, output.status);
  }

  @Test
  void deleteOfAnExistingEntryReportsDeleted() throws ConnectorException {
    create("uid=dave,ou=people,dc=example,dc=com", false);

    LdapOutput output = delete("uid=dave,ou=people,dc=example,dc=com", false);

    assertEquals(LdapStatus.DELETED, output.status);
  }

  private LdapOutput create(String dn, boolean ignoreIfAlreadyDone) throws ConnectorException {
    LdapInput input = connection();
    input.ldapFunction = "CREATE";
    input.CreateEntryDN = dn;
    input.objectClass = "top,person,organizationalPerson,inetOrgPerson";
    input.createEntryAttributes = Map.of("uid", "x", "cn", "x", "sn", "x");
    input.IgnoreIfAlreadyDone = ignoreIfAlreadyDone;
    return new LdapCreateFunction().executeSubFunction(client, input, null);
  }

  private LdapOutput delete(String dn, boolean ignoreIfAlreadyDone) throws ConnectorException {
    LdapInput input = connection();
    input.ldapFunction = "DELETE";
    input.DeleteEntryDN = dn;
    input.IgnoreIfAlreadyDone = ignoreIfAlreadyDone;
    return new LdapDeleteFunction().executeSubFunction(client, input, null);
  }

  private static LdapInput connection() {
    return new LdapInput(
        "localhost", port, LdapProtocol.LDAP, ADMIN_DN, ADMIN_PASSWORD, BASE_DN, LdapScope.SUBTREE,
        null, null, false, LdapDerefAliases.ALWAYS, 0L, 0L, 0L, 30);
  }
}
