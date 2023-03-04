package org.logdoc.fairhttp.service.tools;

import org.logdoc.fairhttp.service.api.helpers.MimeType;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Denis Danilin | me@loslobos.ru
 * 02.03.2023 14:11
 * FairHttpService ☭ sweat and blood
 */
public class MultiForm extends HashMap<String, MultiForm.Part> implements FieldForm {
    @Override
    public String field(final String name) {
        final Part o = get(name);

        return o == null ? null : o.value;
    }

    public void binData(final String name, final byte[] data, final Map<String, List<String>> headers) {
        if (Strings.isEmpty(name))
            return;

        put(name, new Part(null, data, MimeType.BINARY, headers));
    }

    public void textData(final String name, final String value) {
        if (Strings.isEmpty(name))
            return;

        put(name, new Part(value, null, MimeType.TEXTPLAIN, null));
    }

    public void fileData(final String name, final byte[] data, final String contentType) {
        if (Strings.isEmpty(name))
            return;

        MimeType type = MimeType.BINARY;
        try { type = new MimeType(contentType); } catch (final Exception ignore) { }

        put(name, new Part(null, data, type, null));
    }

    public static class Part {
        public final String value;
        public final byte[] data;
        public final MimeType mimeType;
        public final Map<String, List<String>> headers;

        private Part(final String value, final byte[] data, final MimeType mimeType, final Map<String, List<String>> headers) {
            this.value = value;
            this.data = data;
            this.mimeType = mimeType;
            this.headers = headers;
        }
    }
}
