package org.camunda.connector.ldap.function.delete;

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

import javax.naming.NameNotFoundException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * DELETE: removes one entry. The directory refuses to delete an entry that still has children.
 */
public class LdapDeleteFunction implements LdapSubFunction {

    public static final String SUB_FUNCTION_TYPE = "DELETE";

    private final Logger logger = LoggerFactory.getLogger(LdapDeleteFunction.class);

    @Override
    public LdapOutput executeSubFunction(LdapClient ldapClient,
                                         LdapInput ldapInput,
                                         OutboundConnectorContext context) throws ConnectorException {
        long markerTime = System.currentTimeMillis();
        String dn = ldapInput.DeleteEntryDN;
        if (dn == null || dn.isBlank()) {
            logger.error("DELETE: the DN of the entry is missing");
            throw new ConnectorException(LdapError.INVALID_INPUT, "The DN of the entry to delete is required");
        }

        boolean ignoreIfAlreadyDone = ldapInput.ignoresIfAlreadyDone();
        LdapStatus status = ldapClient.execute(ldapInput, LdapError.LDAP_DELETE_FAILED, ctx -> {
            // JNDI destroySubcontext does not report a missing entry, so the existence is checked first
            try {
                ctx.lookup(dn);
            } catch (NameNotFoundException e) {
                // LDAP error 32 (noSuchObject): the entry is already gone
                if (!ignoreIfAlreadyDone) {
                    throw e;
                }
                logger.info("DELETE: [{}] does not exist, ignored (IgnoreIfAlreadyDone)", dn);
                return LdapStatus.ALREADY_DELETED;
            }
            ctx.destroySubcontext(dn);
            return LdapStatus.DELETED;
        });

        LdapOutput ldapOutput = new LdapOutput();
        ldapOutput.status = status;
        ldapOutput.entryDn = dn;
        logger.info("LdapDeleteFunction: deleted [{}] in {} ms", dn, System.currentTimeMillis() - markerTime);
        return ldapOutput;
    }

    @Override
    public List<RunnerParameter> getInputsParameter() {
        return Arrays.asList(LdapInput.parameterDeleteEntryDn, LdapInput.parameterIgnoreIfAlreadyDone);
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
                LdapError.LDAP_DELETE_FAILED, LdapError.LDAP_DELETE_FAILED_LABEL);
    }

    @Override
    public String getSubFunctionName() {
        return "Delete";
    }

    @Override
    public String getSubFunctionDescription() {
        return "Delete an entry from its DN";
    }

    @Override
    public String getSubFunctionType() {
        return SUB_FUNCTION_TYPE;
    }
}
