[![Community badge: Stable](https://img.shields.io/badge/Lifecycle-Stable-brightgreen)](https://github.com/Camunda-Community-Hub/community/blob/main/extension-lifecycle.md#stable-)
[![Community extension badge](https://img.shields.io/badge/Community%20Extension-An%20open%20source%20community%20maintained%20project-FF4700)](https://github.com/camunda-community-hub/community)
![Compatible with: Camunda Platform 8](https://img.shields.io/badge/Compatible%20with-Camunda%20Platform%208-0072Ce)

# camunda-8-connector-ldap

![Ldap.png](Ldap.png)

Outbound Camunda 8 connector for LDAP directories. One connector, five operations, chosen with the `ldapFunction` input:

| `ldapFunction` | What it does |
|---|---|
| `SEARCH` (default) | Returns the entries matching a filter under a base DN |
| `CREATE` | Adds an entry at a DN, with its object class and attributes |
| `MODIFY` | Replaces the values of attributes on an entry; an empty value removes the attribute |
| `DELETE` | Deletes one entry; an entry with children cannot be deleted |
| `RENAME` | Renames or moves an entry from a source DN to a destination DN |

It is a Camunda 8 port of the [Bonita LDAP connector](https://github.com/bonitasoft/bonita-connector-ldap), built on the
Connectors SDK (8.9.8) and the Cherry template.

## Connection inputs (all operations)

These inputs apply to every operation.

| Name | Type | Required | Default | Description |
|---|---|---|---|---|
| `host` | string | yes | | Directory server host name or IP address |
| `port` | number | no | | Directory server port. Empty means 636 for LDAPS and 389 for LDAP and TLS |
| `protocol` | `LDAPS`, `TLS`, `LDAP` | yes | `LDAPS` | `TLS` upgrades a plain connection with StartTLS |
| `connectionType` | `ANONYMOUS`, `USER_PASSWORD` | yes | `ANONYMOUS` | `ANONYMOUS`: no bind. `USER_PASSWORD`: bind with the username and password, which are then shown and required |
| `username` | string | only with `USER_PASSWORD` | | Bind DN, for example `cn=admin,dc=example,dc=org` |
| `password` | string (secret) | only with `USER_PASSWORD` | | Use a secret, for example `{{secrets.LDAP_PASSWORD}}` |

With `connectionType` set to `ANONYMOUS` the connector does not bind, and any username or password is ignored.

The connector keeps the connection open between executions and reuses it. A connection is re-opened after one hour, and
it is re-opened once if the server dropped it. Every connection is logged at INFO level without its password.

## Operation: SEARCH

Shown when `ldapFunction` is `SEARCH`.

| Name | Type | Required | Default | Description |
|---|---|---|---|---|
| `baseDn` | string | yes | | Entry where the search starts, for example `ou=people,dc=example,dc=org` |
| `scope` | `SUBTREE`, `ONELEVEL`, `BASE` | yes | `SUBTREE` | How deep the search goes |
| `filter` | string | yes | | LDAP filter, for example `(&(objectClass=inetOrgPerson)(uid=alice))` |
| `attributes` | string | no | empty | Comma-separated attributes to return; empty returns all. `userPassword` is never returned (see [Excluded attributes](#excluded-attributes)) |
| `followReferrals` | boolean | no | `false` | Follow LDAP referrals to other directory servers |
| `derefAliases` | `ALWAYS`, `NEVER`, `SEARCHING`, `FINDING` | yes | `ALWAYS` | How alias entries are resolved |
| `ldapSearchPageSize` | number | yes | `0` | Entries the directory sends per request (LDAP paged results). `0` disables paging. This is a transport setting and does not change the result |
| `pageSize` | number | yes | `0` | Number of entries in one result page. `0` returns all entries |
| `pageNumber` | number | yes | `0` | Page to return, starting at `0`. Ignored when `pageSize` is `0` |
| `timeLimit` | number | no | `30` | Maximum duration of the search in seconds. `0` means no limit |

The search reads up to `(pageNumber + 1) × pageSize` entries and returns only the requested page.

**Output:** `entries`, the matching entries of the page. Each entry has a `dn` and its `attributes`.

## Operation: CREATE

Shown when `ldapFunction` is `CREATE`.

| Name | Type | Required | Default | Description |
|---|---|---|---|---|
| `CreateEntryDN` | string | yes | | Full DN of the entry to create, for example `ou=camunda,dc=example,dc=org` |
| `objectClass` | string | yes | | Object class of the new entry, for example `inetOrgPerson`. Separate several with commas |
| `createEntryAttributes` | map | yes | | Attributes as name/value pairs, for example `cn`, `sn` and `mail` |
| `IgnoreIfAlreadyDone` | boolean | no | `false` | When `true`, an entry that already exists is a success (status `ALREADY_CREATED`) |

The entry is not created in missing parents: the parent of `CreateEntryDN` must already exist. The RDN attribute must be
allowed by the object class (for example `ou` for `organizationalUnit`).

**Output:** `status` (`CREATED` or `ALREADY_CREATED`) and `entryDn`.

## Operation: MODIFY

Shown when `ldapFunction` is `MODIFY`.

| Name | Type | Required | Default | Description |
|---|---|---|---|---|
| `dn` | string | yes | | Full DN of the existing entry to modify |
| `entryAttributes` | map | yes | | Attributes as name/value pairs. Each value replaces the attribute; an empty value removes it. At least one attribute is required |

**Output:** `status` (`MODIFIED`) and `entryDn`.

## Operation: DELETE

Shown when `ldapFunction` is `DELETE`.

| Name | Type | Required | Default | Description |
|---|---|---|---|---|
| `DeleteEntryDN` | string | yes | | Full DN of the entry to delete. An entry with children cannot be deleted |
| `IgnoreIfAlreadyDone` | boolean | no | `false` | When `true`, a missing entry is a success (status `ALREADY_DELETED`) |

Without `IgnoreIfAlreadyDone`, deleting a missing entry fails with `LDAP_DELETE_FAILED`.

**Output:** `status` (`DELETED` or `ALREADY_DELETED`) and `entryDn`.

## Operation: RENAME

Shown when `ldapFunction` is `RENAME`.

| Name | Type | Required | Default | Description |
|---|---|---|---|---|
| `sourceDn` | string | yes | | Current full DN of the entry |
| `destinationDn` | string | yes | | New full DN of the entry |

A rename can only stay inside the directory's naming context. A destination outside it fails with error 71 ("cannot rename
between DSAs"), and the destination's parent must already exist.

**Output:** `status` (`RENAMED`) and `entryDn` (the new DN).

## Outputs

| Name | Type | Shown | Description |
|---|---|---|---|
| `status` | string | always | Outcome: `SUCCESS` (search), `CREATED`, `ALREADY_CREATED`, `MODIFIED`, `DELETED`, `ALREADY_DELETED` or `RENAMED` |
| `entries` | list | `SEARCH` | The matching entries of the requested page |
| `entryDn` | string | `CREATE`, `MODIFY`, `DELETE`, `RENAME` | The DN of the entry (for rename, the new DN) |

## Errors

Errors are thrown as `ConnectorException`. Each code can be caught with an error boundary event on the task, or with the
element template's error expression.

| Code | Meaning | Operations |
|---|---|---|
| `INVALID_INPUT` | A required input is missing, for example the username or password of a `USER_PASSWORD` connection | all |
| `UNKNOWN_FUNCTION` | The `ldapFunction` value is not one of the five operations | all |
| `LDAP_AUTHENTICATION_FAILED` | The bind was rejected (wrong DN or password) | all |
| `LDAP_CONNECTION_FAILED` | The server cannot be reached, or the TLS negotiation failed | all |
| `LDAP_SEARCH_FAILED` | Any other LDAP error during the search | SEARCH |
| `LDAP_CREATE_FAILED` | The entry could not be created (for example, the parent is missing or an attribute is not allowed) | CREATE |
| `LDAP_MODIFY_FAILED` | The entry could not be modified | MODIFY |
| `LDAP_DELETE_FAILED` | The entry could not be deleted (missing, or has children) | DELETE |
| `LDAP_RENAME_FAILED` | The entry could not be renamed | RENAME |

Errors other than the connection and bind errors carry the server's message, for example
`LDAP operation failed: [LDAP: error code 65 - attribute 'dc' not allowed]`.

## Build

```bash
mvn install
```

The build runs the tests against an in-memory LDAP server and generates:

- `element-templates/ldap-function.json`: the Modeler element template, committed on purpose so modelers can use it
  without building
- `doc/ldap-function.md`: the function documentation
- `target/classes/META-INF/services/...`: the SPI registration of the connector

## Using it

Deploy the JAR with the connector runtime (Self-Managed standalone or embedded), and import
`element-templates/ldap-function.json` into Modeler. The template ID is `org.camunda.connector.ldap.LdapFunction`, and the
task type is `c-ldap-function`. The template only shows the inputs and outputs that belong to the chosen `ldapFunction`.

## Security notes

- **Filter injection.** The filter is sent to the directory as written. Escape values that come from process variables
  (RFC 4515: `*`, `(`, `)`, `\` and NUL) before building the filter with FEEL.
- **Credentials.** Store the bind password as a secret, for example `{{secrets.LDAP_PASSWORD}}`, never as a literal in the
  BPMN.
- **TLS.** Prefer `LDAPS` or `TLS`. With `TLS`, credentials are only sent after the StartTLS negotiation succeeds.
- **Write operations.** CREATE, MODIFY, DELETE and RENAME change the directory. Keep the BPMN paths that call them under
  control, and restrict the bind account to the rights the process needs.

## Excluded attributes

The connector **always** removes the `userPassword` attribute from the results. This is hardcoded in the connector
(`LdapClient.EXCLUDED_ATTRIBUTE`): it applies whether the Attributes input is empty, lists `userPassword` by name, or
asks for it in another letter case or with an option such as `userPassword;binary`. No input, element template
property, or configuration option can turn it off, and the connector never passes it to the process.

To read a password for an actual check, use a bind: the connector binds with the username and password you give it, and
does not need to read the stored value. Directory administrators who want to hide the stored password from search
results must also protect it on the directory side (access control and hashing); this connector only protects what it
returns.

## Status

- Unit and integration tests cover search scope, attribute selection, paged results, size limits, bad credentials, an
  unreachable server, the excluded attribute, and the create and delete operations (including `IgnoreIfAlreadyDone`)
  against an in-memory LDAP server.
- **Not yet tested:** the MODIFY and RENAME operations have no automated tests. RENAME was checked on OpenLDAP with
  `ldapmodify`, not through the connector.
- **Not yet tested:** the `TLS` (StartTLS) and `LDAPS` paths against a real server (OpenLDAP, Active Directory). Test them
  against your directory before relying on them.
- Referral following and alias dereferencing are implemented but not covered by automated tests.
