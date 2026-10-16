package org.camunda.connector.ldap;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.camunda.connector.api.error.ConnectorException;
import org.camunda.connector.ldap.connection.LdapClient.LdapProtocol;
import org.camunda.connector.ldap.connection.LdapDerefAliases;
import org.camunda.connector.ldap.connection.LdapScope;
import org.junit.jupiter.api.Test;

class LdapInputTest {

  @Test
  void portDefaultsToTheProtocolPort() {
    assertEquals(636, search(null, LdapProtocol.LDAPS, null, null, null).effectivePort());
    assertEquals(389, search(null, LdapProtocol.LDAP, null, null, null).effectivePort());
    assertEquals(389, search(null, LdapProtocol.TLS, null, null, null).effectivePort());
    assertEquals(1636, search(1636, LdapProtocol.LDAPS, null, null, null).effectivePort());
  }

  @Test
  void attributesAreSplitAndTrimmed() {
    assertArrayEquals(new String[] {"cn", "mail"},
        search(null, LdapProtocol.LDAP, null, null, " cn , mail ").attributeNames());
    assertNull(search(null, LdapProtocol.LDAP, null, null, "  ").attributeNames());
    assertNull(search(null, LdapProtocol.LDAP, null, null, null).attributeNames());
  }

  @Test
  void anonymousBindNeedsNoPassword() throws ConnectorException {
    LdapInput anonymous = search(null, LdapProtocol.LDAP, null, null, null);

    assertFalse(anonymous.hasCredentials());
    anonymous.checkCredentials();
  }

  @Test
  void usernameWithoutPasswordIsRejected() {
    LdapInput request = search(null, LdapProtocol.LDAP, "cn=search,dc=example,dc=com", null, null);

    ConnectorException error = assertThrows(ConnectorException.class, request::checkCredentials);
    assertEquals("INVALID_INPUT", error.getErrorCode());
  }

  @Test
  void passwordIsIgnoredForAnAnonymousConnection() throws ConnectorException {
    LdapInput request = search(null, LdapProtocol.LDAP, null, "secret", null);

    assertFalse(request.hasCredentials());
    request.checkCredentials();
  }

  @Test
  void userPasswordConnectionWithoutPasswordIsRejected() {
    LdapInput request = search(null, LdapProtocol.LDAP, "cn=search,dc=example,dc=com", null, null);
    request.connectionType = LdapInput.ConnectionType.USER_PASSWORD;

    ConnectorException error = assertThrows(ConnectorException.class, request::checkCredentials);
    assertEquals("INVALID_INPUT", error.getErrorCode());
  }

  @Test
  void completeCredentialsPass() throws ConnectorException {
    LdapInput request = search(null, LdapProtocol.TLS, "cn=search,dc=example,dc=com", "secret", null);

    assertTrue(request.hasCredentials());
    request.checkCredentials();
  }

  @Test
  void resultLimitIsTheEndOfTheRequestedPage() {
    // page 2 (the third page) of size 10 needs the first 30 entries
    assertEquals(30, paging(10, 2).resultLimit());
    // paging disabled: no limit, whatever the page number
    assertEquals(0, paging(0, 0).resultLimit());
  }

  @Test
  void pageNumberWithoutPageSizeIsRejected() {
    ConnectorException error = assertThrows(ConnectorException.class, () -> paging(0, 1).checkPaging());
    assertEquals("INVALID_INPUT", error.getErrorCode());
  }

  @Test
  void pageNumberAndPageSizeAreAccepted() throws ConnectorException {
    paging(10, 2).checkPaging();
  }

  @Test
  void pageNumberThatOverflowsIsRejected() {
    ConnectorException error = assertThrows(ConnectorException.class,
        () -> paging(Long.MAX_VALUE, Long.MAX_VALUE - 1).checkPaging());
    assertEquals("INVALID_INPUT", error.getErrorCode());
  }

  private static LdapInput paging(long pageSize, long pageNumber) {
    return new LdapInput(
        "localhost", null, LdapProtocol.LDAP, null, null, "dc=example,dc=com", LdapScope.SUBTREE,
        "(objectClass=*)", null, false, LdapDerefAliases.ALWAYS, 0L, pageSize, pageNumber, 30);
  }

  private static LdapInput search(Integer port, LdapProtocol protocol, String username, String password,
      String attributes) {
    return new LdapInput(
        "localhost", port, protocol, username, password, "dc=example,dc=com", LdapScope.SUBTREE,
        "(objectClass=*)", attributes, false, LdapDerefAliases.ALWAYS, 0L, 0L, 0L, 30);
  }
}
