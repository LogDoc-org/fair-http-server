package org.logdoc.fairhttp.service.tools;

import org.logdoc.helpers.Texts;
import org.logdoc.helpers.std.MimeType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.logdoc.helpers.std.MimeTypes.BINARY;

/**
 * @author Denis Danilin | me@loslobos.ru
 * 02.03.2023 14:11
 * FairHttpService ☭ sweat and blood
 */
public class MultiForm extends Form implements FieldForm {
    private final Map<String, List<Part>> parts = new HashMap<>();

    public Part get(final String name) {
        return parts.containsKey(name) ? parts.get(name).get(0) : null;
    }

    public List<Part> look(final String name) {
        return parts.get(name);
    }

    public void binData(final String name, final byte[] data, final Map<String, String> headers) {
        if (Texts.isEmpty(name))
            return;

        parts.putIfAbsent(name, new ArrayList<>(2));
        parts.get(name).add(new Part(null, data, BINARY, headers));
    }

    public void textData(final String name, final String value) {
        if (Texts.isEmpty(name))
            return;

        putIfAbsent(name, new ArrayList<>(2));
        super.get(name).add(value);
    }

    public void fileData(final String name, final String fileName, final byte[] data, final MimeType contentType) {
        if (Texts.isEmpty(name))
            return;

        parts.putIfAbsent(name, new ArrayList<>(2));
        parts.get(name).add(new Part(fileName, data, contentType == null ? BINARY : contentType, null));
    }

    public static class Part {
        public final String value;
        public final byte[] data;
        public final MimeType mimeType;
        public final Map<String, String> headers;

        private Part(final String value, final byte[] data, final MimeType mimeType, final Map<String, String> headers) {
            this.value = value;
            this.data = data;
            this.mimeType = mimeType;
            this.headers = headers;
        }
    }
}
