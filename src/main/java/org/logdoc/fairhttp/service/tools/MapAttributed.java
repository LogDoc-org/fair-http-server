package org.logdoc.fairhttp.service.tools;

import java.util.HashMap;
import java.util.Map;

import static org.logdoc.helpers.Texts.isEmpty;

/**
 * @author Denis Danilin | me@loslobos.ru
 * 20.09.2023 13:32
 * fair-http-server ☭ sweat and blood
 */
public abstract class MapAttributed {
    protected Map<String, Object> map;

    public void setAttribute(final String name, final Object value) {
        if (isEmpty(name))
            return;

        if (value == null) {
            removeAttribute(name);
            return;
        }

        if (map == null)
            synchronized (this) {
                map = new HashMap<>(2);
            }

        map.put(name, value);
    }

    @SuppressWarnings("unchecked")
    public <T> T get(final String name) {
        return (T) getAttribute(name);
    }

    public Object getAttribute(final String name) {
        if (name == null || map == null)
            return null;

        return map.get(name);
    }

    public Object removeAttribute(final String name) {
        if (name != null && map != null)
            return map.remove(name);

        return null;
    }
}
