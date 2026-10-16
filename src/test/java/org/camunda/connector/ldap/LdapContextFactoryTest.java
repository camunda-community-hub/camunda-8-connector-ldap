package org.camunda.connector.ldap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.unboundid.ldap.listener.InMemoryDirectoryServer;
import com.unboundid.ldap.listener.InMemoryDirectoryServerConfig;
import com.unboundid.ldap.listener.InMemoryListenerConfig;
import io.camunda.connector.api.error.ConnectorException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

import org.camunda.connector.ldap.connection.*;
import org.camunda.connector.ldap.connection.LdapClient.LdapProtocol;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class LdapContextFactoryTest {

  private static final String BASE_DN = "dc=example,dc=com";
  private static final String ADMIN_DN = "cn=admin,dc=example,dc=com";
  private static final String ADMIN_PASSWORD = "secret";

  private static InMemoryDirectoryServer server;
  private static int port;

  @BeforeAll
  static void startDirectory() throws Exception {
    InMemoryDirectoryServerConfig config = new InMemoryDirectoryServerConfig(BASE_DN);
    config.setListenerConfigs(new InMemoryListenerConfig("plain", null, 0, null, null, null));
    server = new InMemoryDirectoryServer(config);
    server.add("dn: dc=example,dc=com", "objectClass: top", "objectClass: domain", "dc: example");
    server.add("dn: " + ADMIN_DN, "objectClass: top", "objectClass: person", "cn: admin", "sn: admin",
        "userPassword: " + ADMIN_PASSWORD);
    server.add("dn: uid=alice,dc=example,dc=com", "objectClass: top", "objectClass: person",
        "objectClass: organizationalPerson", "objectClass: inetOrgPerson", "uid: alice", "cn: Alice Martin",
        "sn: Martin");
    server.add("dn: uid=bob,dc=example,dc=com", "objectClass: top", "objectClass: person",
        "objectClass: organizationalPerson", "objectClass: inetOrgPerson", "uid: bob", "cn: Bob Durand",
        "sn: Durand");
    server.startListening();
    port = server.getListenPort();
  }

  @AfterAll
  static void stopDirectory() {
    server.shutDown(true);
  }

  @Test
  void sameParametersReuseTheOpenConnection() throws Exception {
    LdapContextFactory factory = new LdapContextFactory(Duration.ofHours(1), Clock.systemUTC());

    LdapContextFactory.LdapConnection first = factory.getLdapConnection(input(ADMIN_PASSWORD));
    LdapContextFactory.LdapConnection second = factory.getLdapConnection(input(ADMIN_PASSWORD));

    assertSame(first, second);
    assertFalse(first.isClosed());
  }

  @Test
  void aDifferentPasswordOpensANewConnection() throws Exception {
    LdapContextFactory factory = new LdapContextFactory(Duration.ofHours(1), Clock.systemUTC());

    LdapContextFactory.LdapConnection withPassword = factory.getLdapConnection(input(ADMIN_PASSWORD));
    LdapContextFactory.LdapConnection withOtherAliases = factory.getLdapConnection(input(ADMIN_PASSWORD, LdapDerefAliases.NEVER));

    assertNotSame(withPassword, withOtherAliases);
    // the cached one is not affected
    assertSame(withPassword, factory.getLdapConnection(input(ADMIN_PASSWORD)));
  }

  @Test
  void aConnectionOlderThanTheMaxAgeIsReopened() throws Exception {
    MutableClock clock = new MutableClock(Instant.parse("2026-10-15T10:00:00Z"));
    LdapContextFactory factory = new LdapContextFactory(Duration.ofHours(1), clock);
    LdapContextFactory.LdapConnection opened = factory.getLdapConnection(input(ADMIN_PASSWORD));

    clock.advance(Duration.ofMinutes(59));
    assertSame(opened, factory.getLdapConnection(input(ADMIN_PASSWORD)), "still fresh after 59 minutes");

    clock.advance(Duration.ofMinutes(2));
    LdapContextFactory.LdapConnection renewed = factory.getLdapConnection(input(ADMIN_PASSWORD));

    assertNotSame(opened, renewed);
    assertTrue(opened.isClosed(), "the old connection is closed when it is replaced");
    assertFalse(renewed.isClosed());
  }

  @Test
  void evictClosesTheConnectionAndTheNextGetLdapConnectionOpensANewOne() throws Exception {
    LdapContextFactory factory = new LdapContextFactory(Duration.ofHours(1), Clock.systemUTC());
    LdapContextFactory.LdapConnection opened = factory.getLdapConnection(input(ADMIN_PASSWORD));

    factory.evict(opened);

    assertTrue(opened.isClosed());
    assertNotSame(opened, factory.getLdapConnection(input(ADMIN_PASSWORD)));
  }

  @Test
  void pagedThenNonPagedSearchesOnTheSameCachedConnectionReturnTheFullResult() throws ConnectorException {
    LdapClient client = new LdapClient(new LdapContextFactory(Duration.ofHours(1), Clock.systemUTC()));

    // The paged search leaves its paged-results control on the connection; the next search must not inherit it
    List<LdapEntry> paged = client.search(search(1, 0, 0));
    List<LdapEntry> unpaged = client.search(search(0, 0, 0));

    assertEquals(3, paged.size());
    assertEquals(3, unpaged.size());
  }

  private static LdapInput input(String password) {
    return input(password, LdapDerefAliases.ALWAYS);
  }

  private static LdapInput input(String password, LdapDerefAliases derefAliases) {
    return new LdapInput(
        "localhost", port, LdapProtocol.LDAP, ADMIN_DN, password, BASE_DN, LdapScope.SUBTREE,
        "(objectClass=person)", null, false, derefAliases, 0L, 0L, 0L, 30);
  }

  private static LdapInput search(long ldapSearchPageSize, long pageSize, long pageNumber) {
    return new LdapInput(
        "localhost", port, LdapProtocol.LDAP, ADMIN_DN, ADMIN_PASSWORD, BASE_DN, LdapScope.SUBTREE,
        "(objectClass=person)", null, false, LdapDerefAliases.ALWAYS, ldapSearchPageSize, pageSize, pageNumber, 30);
  }

  /** A clock the test moves forward explicitly. */
  private static final class MutableClock extends Clock {

    private Instant now;

    MutableClock(Instant now) {
      this.now = now;
    }

    void advance(Duration duration) {
      now = now.plus(duration);
    }

    @Override
    public ZoneId getZone() {
      return ZoneId.of("UTC");
    }

    @Override
    public Clock withZone(ZoneId zone) {
      return this;
    }

    @Override
    public Instant instant() {
      return now;
    }
  }
}
