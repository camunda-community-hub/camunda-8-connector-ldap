package org.camunda.connector.ldap.function.modify;

import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.cherrytemplate.RunnerParameter;
import org.camunda.connector.ldap.LdapError;
import org.camunda.connector.ldap.LdapInput;
import org.camunda.connector.ldap.LdapOutput;
import org.camunda.connector.ldap.LdapOutput.LdapStatus;
import org.camunda.connector.ldap.LdapSubFunction;
import org.camunda.connector.ldap.connection.LdapAttributes;
import org.camunda.connector.ldap.connection.LdapClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.naming.directory.BasicAttribute;
import javax.naming.directory.DirContext;
import javax.naming.directory.ModificationItem;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * MODIFY: replaces the values of the given attributes on an entry. An empty value removes the attribute.
 */
public class LdapModifyFunction implements LdapSubFunction {

    public static final String SUB_FUNCTION_TYPE = "MODIFY";

    private final Logger logger = LoggerFactory.getLogger(LdapModifyFunction.class);

    @Override
    public LdapOutput executeSubFunction(LdapClient ldapClient,
                                         LdapInput ldapInput,
                                         OutboundConnectorContext context) throws ConnectorException {
        long markerTime = System.currentTimeMillis();
        String dn = ldapInput.dn;
        if (dn == null || dn.isBlank()) {
            logger.error("MODIFY: the DN of the entry is missing");
            throw new ConnectorException(LdapError.INVALID_INPUT, "The DN of the entry to modify is required");
        }
        if (ldapInput.entryAttributes == null || ldapInput.entryAttributes.isEmpty()) {
            logger.error("MODIFY: no attribute to modify for [{}]", dn);
            throw new ConnectorException(LdapError.INVALID_INPUT, "At least one attribute to modify is required");
        }

        // Attribute names are logged, values never
        logger.info("LdapModifyFunction: modifying [{}], attributes {}", dn, ldapInput.entryAttributes.keySet());
        List<ModificationItem> items = new ArrayList<>();
        ldapInput.entryAttributes.forEach((name, value) -> {
            if (LdapAttributes.isEmptyValue(value)) {
                items.add(new ModificationItem(DirContext.REMOVE_ATTRIBUTE, new BasicAttribute(name)));
            } else {
                items.add(new ModificationItem(DirContext.REPLACE_ATTRIBUTE, LdapAttributes.toAttribute(name, value)));
            }
        });
        ModificationItem[] modifications = items.toArray(new ModificationItem[0]);

        ldapClient.execute(ldapInput, LdapError.LDAP_MODIFY_FAILED, ctx -> {
            ctx.modifyAttributes(dn, modifications);
            return null;
        });

        LdapOutput ldapOutput = new LdapOutput();
        ldapOutput.status = LdapStatus.MODIFIED;
        ldapOutput.entryDn = dn;
        logger.info("LdapModifyFunction: modified [{}] in {} ms", dn, System.currentTimeMillis() - markerTime);
        return ldapOutput;
    }

    @Override
    public List<RunnerParameter> getInputsParameter() {
        return Arrays.asList(LdapInput.parameterDn, LdapInput.parameterEntryAttributes);
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
                LdapError.LDAP_MODIFY_FAILED, LdapError.LDAP_MODIFY_FAILED_LABEL);
    }

    @Override
    public String getSubFunctionName() {
        return "Modify";
    }

    @Override
    public String getSubFunctionDescription() {
        return "Replace the values of the given attributes on an entry; an empty value removes the attribute";
    }

    @Override
    public String getSubFunctionType() {
        return SUB_FUNCTION_TYPE;
    }
}
