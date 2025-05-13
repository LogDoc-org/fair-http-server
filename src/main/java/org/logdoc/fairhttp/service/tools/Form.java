package org.logdoc.fairhttp.service.tools;

import org.logdoc.helpers.Texts;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

/**
 * @author Denis Danilin | me@loslobos.ru
 * 06.02.2023 18:28
 * FairHttpService sweat and blood
 */
public class Form extends HashMap<String, List<String>> implements FieldForm {
    public Form() {}

    public Form(final byte[] bytes) {
        if (bytes == null || bytes.length < 3) return;

        String key, value;
        // b is the beginning of the key, s is the position of '=', i is current position
        for (int i = 0, b = 0, s = 0; i < bytes.length; i++) {
            if (bytes[i] == '=') {
                if (s <= b) { // s is effectively reset for each new potential key via b moving forward
                    s = i;
                }
            } else if (bytes[i] == '&' || i + 1 == bytes.length) {
                // Determine the actual end of the value string
                // If current char is '&', value ends before it.
                // If current char is the last char of bytes AND it's not '&', value includes it.
                int valueEnd = (i + 1 == bytes.length && bytes[i] != '&') ? i + 1 : i;

                if (s > b && s < valueEnd) { // A valid key-value pair segment
                    key = new String(Arrays.copyOfRange(bytes, b, s)).trim();
                    if (!key.isBlank()) {
                        value = URLDecoder.decode(new String(Arrays.copyOfRange(bytes, s + 1, valueEnd)), StandardCharsets.UTF_8);
                        if (!value.isBlank()) {
                            if (get(key) == null) {
                                put(key, new ArrayList<>());
                            }
                            get(key).add(value);
                        }
                    }
                }
                // Move 'b' to start after the '&' for the next key
                b = i + 1;
                // Reset 's' to be potentially found after 'b'
                s = i + 1; // More accurately, s should be re-evaluated relative to new 'b', effectively s = b for next segment start
            }
        }
    }

    public String field(final String name) {
        final List<String> fields = get(name);

        return Texts.isEmpty(fields) ? null : fields.get(0);
    }

    public List<String> fields(final String name) {
        return get(name);
    }
}
