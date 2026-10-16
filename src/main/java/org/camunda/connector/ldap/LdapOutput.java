package org.camunda.connector.ldap;

/* ******************************************************************** */
/*                                                                      */
/*  LdapOutput                                                          */
/*                                                                      */
/*  Describes the output of the LDAP functions for the Cherry template. */
/*  Search fills entries; create, modify, delete and rename fill        */
/*  status and dn.                                                      */
/* ******************************************************************** */

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.camunda.connector.cherrytemplate.CherryOutput;
import io.camunda.connector.cherrytemplate.RunnerParameter;
import org.camunda.connector.ldap.connection.LdapEntry;

import java.util.List;
import java.util.Map;

public class LdapOutput implements CherryOutput {

    public static final String OUTPUT_ENTRIES = "entries";
    public static final String OUTPUT_STATUS = "status";
    public static final String OUTPUT_DN = "entryDn";
    public static final RunnerParameter parameterOutputEntries = RunnerParameter.getInstance(
                    OUTPUT_ENTRIES,
                    "Entries", // label
                    List.class,
                    RunnerParameter.Level.FREE,
                    "Search: the entries matching the filter, each one with its DN and its attributes")
            .addCondition(LdapInput.LDAP_FUNCTION, List.of("SEARCH"));
    public static final RunnerParameter parameterOutputStatus = RunnerParameter.getInstance(
            OUTPUT_STATUS,
            "Status",
            String.class,
            RunnerParameter.Level.REQUIRED,
            "Result of the operation: SUCCESS, CREATED, ALREADY_CREATED, MODIFIED, DELETED, ALREADY_DELETED or RENAMED");
    public static final RunnerParameter parameterOutputDn = RunnerParameter.getInstance(
                    OUTPUT_DN,
                    "DN",
                    String.class,
                    RunnerParameter.Level.FREE,
                    "Create, modify or delete: the DN of the entry. Rename: the new DN")
            .addCondition(LdapInput.LDAP_FUNCTION, List.of("CREATE", "MODIFY", "DELETE", "RENAME"));
    public List<RunnerParameter> outputParametersList = List.of(parameterOutputStatus,
            parameterOutputEntries,
            parameterOutputDn);
    public List<LdapEntry> entries;
    public LdapStatus status;
    public String entryDn;

    @JsonIgnore
    @Override
    public List<Map<String, Object>> getOutputParameters() {
        return outputParametersList.stream().map(t -> t.toMap(null)).toList();
    }

    /**
     * Outcome of an LDAP operation, written to the {@code status} output.
     */
    public enum LdapStatus {
        /**
         * Search completed.
         */
        SUCCESS,
        CREATED,
        /**
         * CREATE on an entry that already exists, with IgnoreIfAlreadyDone.
         */
        MODIFIED,
        DELETED,
        /**
         * DELETE on a missing entry, with IgnoreIfAlreadyDone.
         */
        ALREADY_DELETED,
        RENAMED
    }
}
