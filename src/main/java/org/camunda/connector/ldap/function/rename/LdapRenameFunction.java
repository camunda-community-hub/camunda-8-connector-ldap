package org.camunda.connector.ldap.function.rename;

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
 * RENAME: changes the DN of an entry, which also moves it when the parent changes.
 */
public class LdapRenameFunction implements LdapSubFunction {

    public static final String SUB_FUNCTION_TYPE = "RENAME";

    private final Logger logger = LoggerFactory.getLogger(LdapRenameFunction.class);

    @Override
    public LdapOutput executeSubFunction(LdapClient ldapClient,
                                         LdapInput ldapInput,
                                         OutboundConnectorContext context) throws ConnectorException {
        long markerTime = System.currentTimeMillis();
        String sourceDn = ldapInput.sourceDn;
        String destinationDn = ldapInput.destinationDn;
        if (sourceDn == null || sourceDn.isBlank()) {
            logger.error("RENAME: the source DN is missing");
            throw new ConnectorException(LdapError.INVALID_INPUT, "The source DN is required");
        }
        if (destinationDn == null || destinationDn.isBlank()) {
            logger.error("RENAME: the destination DN is missing for [{}]", sourceDn);
            throw new ConnectorException(LdapError.INVALID_INPUT, "The destination DN is required");
        }

        ldapClient.execute(ldapInput, LdapError.LDAP_RENAME_FAILED, ctx -> {
            ctx.rename(sourceDn, destinationDn);
            return null;
        });

        LdapOutput ldapOutput = new LdapOutput();
        ldapOutput.status = LdapStatus.RENAMED;
        ldapOutput.entryDn = destinationDn;
        logger.info("LdapRenameFunction: renamed [{}] to [{}] in {} ms", sourceDn, destinationDn,
                System.currentTimeMillis() - markerTime);
        return ldapOutput;
    }

    @Override
    public List<RunnerParameter> getInputsParameter() {
        return Arrays.asList(LdapInput.parameterSourceDn, LdapInput.parameterDestinationDn);
    }

    @Override
    public List<RunnerParameter> getOutputsParameter() {
        return List.of(LdapOutput.parameterOutputStatus, LdapOutput.parameterOutputDn);
    }

    @Override
    public Map<String, String> getSubFunctionListBpmnErrors() {
        return Map.of(
                LdapError.INVALID_INPUT, LdapError.INVALID_INPUT_LABEL,
                LdapError.LDAP_AUTHENTICATION_FAILED, LdapError.LDAP_AUTHENTICATION_FAILED_LABEL,
                LdapError.LDAP_CONNECTION_FAILED, LdapError.LDAP_CONNECTION_FAILED_LABEL,
                LdapError.LDAP_RENAME_FAILED, LdapError.LDAP_RENAME_FAILED_LABEL);
    }

    @Override
    public String getSubFunctionName() {
        return "Rename";
    }

    @Override
    public String getSubFunctionDescription() {
        return "Rename or move an entry from a source DN to a destination DN";
    }

    @Override
    public String getSubFunctionType() {
        return SUB_FUNCTION_TYPE;
    }
}
