package org.camunda.connector.ldap.function.create;

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

import javax.naming.NameAlreadyBoundException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.BasicAttribute;
import javax.naming.directory.BasicAttributes;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * CREATE: adds an entry at a DN, with its object classes and attributes.
 */
public class LdapCreateFunction implements LdapSubFunction {

    public static final String SUB_FUNCTION_TYPE = "CREATE";

    private final Logger logger = LoggerFactory.getLogger(LdapCreateFunction.class);

    /**
     * The object classes come from the object class input (several, separated by commas), then each attribute of
     * the map. Attribute values are not logged.
     */
    private static Attributes toAttributes(LdapInput ldapInput) {
        Attributes attributes = new BasicAttributes(true);
        Attribute objectClasses = new BasicAttribute("objectClass");
        Arrays.stream(ldapInput.objectClass.split(","))
                .map(String::trim)
                .filter(name -> !name.isEmpty())
                .forEach(objectClasses::add);
        attributes.put(objectClasses);
        if (ldapInput.createEntryAttributes != null) {
            ldapInput.createEntryAttributes.forEach((name, value) -> {
                if (!LdapAttributes.isEmptyValue(value)) {
                    attributes.put(LdapAttributes.toAttribute(name, value));
                }
            });
        }
        return attributes;
    }

    @Override
    public LdapOutput executeSubFunction(LdapClient ldapClient,
                                         LdapInput ldapInput,
                                         OutboundConnectorContext context) throws ConnectorException {
        long markerTime = System.currentTimeMillis();
        String dn = ldapInput.CreateEntryDN;
        if (dn == null || dn.isBlank()) {
            logger.error("CREATE: the DN of the new entry is missing");
            throw new ConnectorException(LdapError.INVALID_INPUT, "The DN of the new entry is required");
        }
        if (ldapInput.objectClass == null || ldapInput.objectClass.isBlank()) {
            logger.error("CREATE: the object class is missing for [{}]", dn);
            throw new ConnectorException(LdapError.INVALID_INPUT, "The object class of the new entry is required");
        }

        Attributes attributes = toAttributes(ldapInput);
        boolean ignoreIfAlreadyDone = ldapInput.ignoresIfAlreadyDone();
        LdapStatus status = ldapClient.execute(ldapInput, LdapError.LDAP_CREATE_FAILED, ctx -> {
            try {
                ctx.createSubcontext(dn, attributes);
                return LdapStatus.CREATED;
            } catch (NameAlreadyBoundException e) {
                // LDAP error 68 (entryAlreadyExists)
                if (!ignoreIfAlreadyDone) {
                    throw e;
                }
                logger.info("CREATE: [{}] already exists, ignored (IgnoreIfAlreadyDone)", dn);
                return LdapStatus.CREATED;
            }
        });

        LdapOutput ldapOutput = new LdapOutput();
        ldapOutput.status = status;
        ldapOutput.entryDn = dn;
        logger.info("LdapCreateFunction: created [{}] in {} ms", dn, System.currentTimeMillis() - markerTime);
        return ldapOutput;
    }

    @Override
    public List<RunnerParameter> getInputsParameter() {
        return Arrays.asList(LdapInput.parameterObjectClass, LdapInput.parameterCreateEntryDn,
                LdapInput.parameterCreateEntryAttributes, LdapInput.parameterIgnoreIfAlreadyDone);
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
                LdapError.LDAP_CREATE_FAILED, LdapError.LDAP_CREATE_FAILED_LABEL);
    }

    @Override
    public String getSubFunctionName() {
        return "Create";
    }

    @Override
    public String getSubFunctionDescription() {
        return "Create an entry at a DN, with its object class and attributes";
    }

    @Override
    public String getSubFunctionType() {
        return SUB_FUNCTION_TYPE;
    }
}
