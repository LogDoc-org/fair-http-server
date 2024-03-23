package org.logdoc.fairhttp.service.http;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * @author Denis Danilin | me@loslobos.ru
 * 21.03.2024 16:06
 * fair-http-server ☭ sweat and blood
 */
public class RequestId {
    public final String method, uri, path;
    private final Map<String, String> q;

    public RequestId(final String method, final String resource) {
        this.method = method;
        this.uri = resource;

        if (uri.indexOf('?') == -1) {
            path = uri;
            q = Collections.emptyMap();
        } else {
            path = uri.substring(0, uri.indexOf('?'));

            final Map<String, String> qm = new HashMap<>(4);
            Arrays.stream(uri.substring(uri.indexOf('?') + 1).split(Pattern.quote("&")))
                    .map(pair -> pair.split(Pattern.quote("=")))
                    .filter(pair -> pair.length > 1)
                    .forEach(kv -> {
                        String v = null;
                        try {
                            v = URLDecoder.decode(kv[1], StandardCharsets.UTF_8);
                        } catch (final IllegalArgumentException ignore) {
                            v = URLDecoder.decode(kv[1], StandardCharsets.US_ASCII);
                        } catch (final Exception ignore) {}

                        if (v != null)
                            qm.put(kv[0], v);
                    });

            q = Collections.unmodifiableMap(qm);
        }
    }

    public String query(final String name) {
        return q.get(name);
    }
}
