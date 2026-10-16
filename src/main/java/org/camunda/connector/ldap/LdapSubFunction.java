package org.camunda.connector.ldap;

import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.cherrytemplate.RunnerParameter;
import org.camunda.connector.ldap.connection.LdapClient;

import java.util.List;
import java.util.Map;

/**
 * One LDAP operation (search, create, modify, delete, rename). {@link LdapFunction} picks the operation from the
 * {@code ldapFunction} input and calls {@link #executeSubFunction}.
 */
public interface LdapSubFunction {
    LdapOutput executeSubFunction(LdapClient ldapClient,
                                  LdapInput ldapInput,
                                  OutboundConnectorContext context) throws ConnectorException;

    List<RunnerParameter> getInputsParameter();

    List<RunnerParameter> getOutputsParameter();

    Map<String, String> getSubFunctionListBpmnErrors();

    String getSubFunctionName();

    String getSubFunctionDescription();

    String getSubFunctionType();

}
