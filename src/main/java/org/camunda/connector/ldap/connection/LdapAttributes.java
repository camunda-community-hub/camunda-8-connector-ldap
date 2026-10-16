package org.camunda.connector.ldap.connection;

import javax.naming.directory.Attribute;
import javax.naming.directory.BasicAttribute;
import java.util.Collection;

/**
 * Helpers to turn the attribute values given as process inputs into JNDI attributes.
 */
public final class LdapAttributes {

    private LdapAttributes() {
    }

    /**
     * @param name  attribute name, for example {@code mail}
     * @param value a single value, or a collection for several values
     * @return the JNDI attribute, without null values
     */
    public static Attribute toAttribute(String name, Object value) {
        BasicAttribute attribute = new BasicAttribute(name);
        if (value instanceof Collection<?> values) {
            for (Object item : values) {
                if (item != null) {
                    attribute.add(String.valueOf(item));
                }
            }
        } else {
            attribute.add(String.valueOf(value));
        }
        return attribute;
    }

    /**
     * @return true when the value means "no value": null, a blank string or an empty collection
     */
    public static boolean isEmptyValue(Object value) {
        if (value == null) {
            return true;
        }
        if (value instanceof String text) {
            return text.isBlank();
        }
        if (value instanceof Collection<?> values) {
            return values.isEmpty();
        }
        return false;
    }
}
