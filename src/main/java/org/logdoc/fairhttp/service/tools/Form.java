package org.logdoc.fairhttp.service.tools;

import org.logdoc.helpers.Texts;

import java.util.HashMap;
import java.util.List;

/**
 * @author Denis Danilin | me@loslobos.ru
 * 06.02.2023 18:28
 * FairHttpService ☭ sweat and blood
 */
public class Form extends HashMap<String, List<String>> implements FieldForm {
    public String field(final String name) {
        final List<String> fields = get(name);

        return Texts.isEmpty(fields) ? null : fields.get(0);
    }

    public List<String> fields(final String name) {
        return get(name);
    }
}
