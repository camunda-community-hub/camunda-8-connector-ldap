package org.camunda.connector.ldap.function.search;

import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.cherrytemplate.RunnerParameter;
import org.camunda.connector.ldap.LdapError;
import org.camunda.connector.ldap.LdapInput;
import org.camunda.connector.ldap.LdapOutput;
import org.camunda.connector.ldap.LdapOutput.LdapStatus;
import org.camunda.connector.ldap.LdapSubFunction;
import org.camunda.connector.ldap.connection.LdapClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * SEARCH: returns the entries matching a filter under a base DN.
 */
public class LdapSearchFunction implements LdapSubFunction {

    public static final String SUB_FUNCTION_TYPE = "SEARCH";

    private final Logger logger = LoggerFactory.getLogger(LdapSearchFunction.class);

    @Override
    public LdapOutput executeSubFunction(LdapClient ldapClient,
                                         LdapInput ldapInput,
                                         OutboundConnectorContext context) throws ConnectorException {
        long markerTime = System.currentTimeMillis();
        if (ldapInput.baseDn == null || ldapInput.baseDn.isBlank()) {
            logger.error("SEARCH: the base DN is missing");
            throw new ConnectorException(LdapError.INVALID_INPUT, "The base DN is required to search");
        }
        if (ldapInput.filter == null || ldapInput.filter.isBlank()) {
            logger.error("SEARCH: the filter is missing");
            throw new ConnectorException(LdapError.INVALID_INPUT, "The filter is required to search");
        }

        LdapOutput ldapOutput = new LdapOutput();
        ldapOutput.entries = ldapClient.search(ldapInput);
        ldapOutput.status = LdapStatus.SUCCESS;
        logger.info("LdapSearchFunction: found [{}] result in {} ms", ldapOutput.entries.size(),
                System.currentTimeMillis() - markerTime);
        return ldapOutput;
    }

    @Override
    public List<RunnerParameter> getInputsParameter() {
        return Arrays.asList(LdapInput.parameterBaseDn, LdapInput.parameterScope, LdapInput.parameterFilter,
                LdapInput.parameterAttributes, LdapInput.parameterFollowReferrals, LdapInput.parameterDerefAliases,
                LdapInput.parameterLdapSearchPageSize, LdapInput.parameterPageSize, LdapInput.parameterPageNumber);
    }

    @Override
    public List<RunnerParameter> getOutputsParameter() {
        return List.of(LdapOutput.parameterOutputEntries);
    }

    @Override
    public Map<String, String> getSubFunctionListBpmnErrors() {
        return Map.of(
                LdapError.INVALID_INPUT, LdapError.INVALID_INPUT_LABEL,
                LdapError.LDAP_AUTHENTICATION_FAILED, LdapError.LDAP_AUTHENTICATION_FAILED_LABEL,
                LdapError.LDAP_CONNECTION_FAILED, LdapError.LDAP_CONNECTION_FAILED_LABEL,
                LdapError.LDAP_SEARCH_FAILED, LdapError.LDAP_SEARCH_FAILED_LABEL);
    }

    @Override
    public String getSubFunctionName() {
        return "Search";
    }

    @Override
    public String getSubFunctionDescription() {
        return "Search an LDAP directory and return the matching entries with their attributes";
    }

    @Override
    public String getSubFunctionType() {
        return SUB_FUNCTION_TYPE;
    }
}
