package org.logdoc.fairhttp.service.tools;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigValue;
import com.typesafe.config.ConfigValueType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @author Denis Danilin | me@loslobos.ru
 * 26.04.2023 10:09
 * fair-http-server ☭ sweat and blood
 */
public class ConfigTools {

    @SuppressWarnings("unchecked")
    public static List<String> sureStrings(final Config c, final String name) {
        final List<String> list = new ArrayList<>(0);

        if (!c.hasPath(name) || c.getIsNull(name))
            return list;

        final ConfigValue cv = c.getValue(name);

        if (cv.valueType() == ConfigValueType.STRING)
            list.add(String.valueOf(cv.unwrapped()));
        else if (cv.valueType() == ConfigValueType.LIST)
            list.addAll((Collection<String>) cv.unwrapped());

        return list;
    }

    public static Config sureConf(final Config c, final String name) {
        if (c.hasPath(name) && !c.getIsNull(name) && c.getValue(name).valueType() == ConfigValueType.OBJECT)
            return c.getConfig(name);

        return null;
    }

    public static boolean sureBool(final Config c, final String name) {
        if (c.hasPath(name) && !c.getIsNull(name) && c.getValue(name).valueType() == ConfigValueType.BOOLEAN)
            return c.getBoolean(name);

        return false;
    }

    public static boolean sureNN(final Config c, final String name) {
        return c.hasPath(name) && !c.getIsNull(name);
    }
}
