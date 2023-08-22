package org.logdoc.fairhttp.service.tools;

import org.logdoc.helpers.Texts;
import org.logdoc.helpers.std.MimeType;

import java.util.HashMap;
import java.util.Map;

import static org.logdoc.helpers.std.MimeTypes.BINARY;
import static org.logdoc.helpers.std.MimeTypes.TEXTPLAIN;

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

    public void binData(final String name, final byte[] data, final Map<String, String> headers) {
        if (Texts.isEmpty(name))
            return;

        put(name, new Part(null, data, BINARY, headers));
    }

    public void textData(final String name, final String value) {
        if (Texts.isEmpty(name))
            return;

        put(name, new Part(value, null, TEXTPLAIN, null));
    }

    public void fileData(final String name, final String fileName, final byte[] data, final MimeType contentType) {
        if (Texts.isEmpty(name))
            return;

        put(name, new Part(fileName, data, contentType == null ? BINARY : contentType, null));
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
