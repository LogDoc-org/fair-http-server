package org.logdoc.fairhttp.service.http;

import com.fasterxml.jackson.databind.JsonNode;
import org.logdoc.fairhttp.service.api.helpers.Headers;
import org.logdoc.fairhttp.service.api.helpers.MimeType;
import org.logdoc.fairhttp.service.tools.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.SocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Pattern;

import static org.logdoc.fairhttp.service.tools.HttpBinStreaming.getBoundary;
import static org.logdoc.fairhttp.service.tools.HttpBinStreaming.stringQuotes;
import static org.logdoc.helpers.Texts.isEmpty;
import static org.logdoc.helpers.Texts.notNull;

/**
 * @author Denis Danilin | me@loslobos.ru
 * 26.07.2023 15:01
 * fair-http-server ☭ sweat and blood
 */
public class Request {
    private final byte[] rawHead;
    private final Function<Request, byte[]> bodySupplier;
    private final SocketAddress remote;

    private Map<String, String> q, h, c;
    private String m, p, u;
    private String[] hz;
    private Body b;
    private MimeType t;

    Request(final SocketAddress remote, final byte[] rawHead, final Function<Request, byte[]> bodySupplier) {
        this.rawHead = rawHead;
        this.remote = remote;
        this.bodySupplier = bodySupplier;
    }

    public SocketAddress getRemote() {
        return remote;
    }

    public String method() {
        if (m == null)
            makeFirstLine();

        return m;
    }

    public String path() {
        if (p == null)
            makeFirstLine();

        return p;
    }

    public String uri() {
        if (u == null)
            makeFirstLine();

        return u;
    }

    public Map<String, String> queryMap() {
        if (q == null)
            makeFirstLine();

        return q;
    }

    public String queryParam(final String name) {
        return queryMap().get(name);
    }

    public Map<String, String> headersMap() {
        if (h == null)
            synchronized (this) {
                final Map<String, String> hm = new HashMap<>(8);
                String name;

                for (int i = 1, idx; i < heads().length; i++) {
                    if ((idx = heads()[i].indexOf(':')) != -1) {
                        name = notNull(heads()[i].substring(0, idx));

                        if (!name.isEmpty())
                            hm.put(name.toUpperCase(Locale.ROOT), notNull(heads()[i].substring(idx + 1)));
                    }
                }

                h = Collections.unmodifiableMap(hm);
            }

        return h;
    }

    public String header(final String name) {
        return headersMap().get(notNull(name).toUpperCase(Locale.ROOT));
    }

    private synchronized void makeFirstLine() {
        synchronized (this) {
            final String[] parts = heads()[0].split("\\s", 3);
            m = parts[0];
            u = parts[1];

            if (u.indexOf('?') == -1) {
                p = u;
                q = Collections.emptyMap();
            } else {
                p = u.substring(0, u.indexOf('?'));

                final Map<String, String> qm = new HashMap<>(4);
                Arrays.stream(u.substring(u.indexOf('?') + 1).split(Pattern.quote("&")))
                        .map(pair -> pair.split(Pattern.quote("=")))
                        .forEach(kv -> qm.put(kv[0], URLDecoder.decode(kv[1], StandardCharsets.UTF_8)));

                q = Collections.unmodifiableMap(qm);
            }
        }
    }

    private String[] heads() {
        if (hz == null)
            synchronized (this) {
                hz = new String(rawHead, StandardCharsets.UTF_8).split("\n");
            }

        return hz;
    }

    public Body body() {
        if (b == null)
            synchronized (this) {
                b = new Body(bodySupplier.apply(this), contentType());
            }

        return b;
    }

    private MimeType contentType() {
        if (t == null)
            synchronized (this) {
                try {
                    t = new MimeType(header(Headers.ContentType));
                } catch (Exception e) {
                    t = MimeType.BINARY;
                }
            }

        return t;
    }

    public boolean hasHeader(final String name) {
        return header(name) != null;
    }

    public String cookie(final String name) {
        return cookies().get(name);
    }

    public Map<String, String> cookies() {
        if (c == null)
            synchronized (this) {
                final Map<String, String> m = new HashMap<>();

                String v = header(Headers.RequestCookies);

                if (!isEmpty(v))
                    Arrays.stream(v.split(";"))
                            .filter(s -> s.contains("="))
                            .forEach(c -> {
                                final String[] parts = c.split(Pattern.quote("="), 2);
                                if (parts.length != 2) return;

                                m.put(notNull(parts[0]), stringQuotes(parts[1]));
                            });

                c = Collections.unmodifiableMap(m);
            }

        return c;
    }

    public static class Body {
        private final MimeType contentType;
        private final byte[] data;
        private Form f;
        private MultiForm m;
        private JsonNode j;

        private Body(final byte[] data, final MimeType contentType) {
            this.data = data;
            this.contentType = contentType;
        }

        public byte[] asBytes() {
            return Arrays.copyOf(data, data.length);
        }

        public JsonNode asJson() {
            if (j == null && contentTypeMatch(MimeType.JSON))
                synchronized (this) {
                    j = Json.parse(data);
                }

            return j;
        }

        public <T> T map(final Class<? extends T> klass) {
            return Json.fromJson(asJson(), klass);
        }

        public MultiForm asMultipart() throws IOException {
            if (m == null && data != null && data.length > 0 && contentTypeMatch(MimeType.MULTIPART))
                synchronized (this) {
                    m = new MultiForm();

                    final List<Pair<Integer, Integer>> positions = HttpBinStreaming.markParts(data, getBoundary(contentType));

                    for (final Pair<Integer, Integer> partMark : positions) {
                        final AtomicReference<MimeType> cTypeHold = new AtomicReference<>(MimeType.TEXTPLAIN);
                        final Map<String, String> partHeaders = new HashMap<>(8) {
                            @Override
                            public String put(final String key, final String value) {
                                return super.put(key.toUpperCase(), value);
                            }

                            @Override
                            public String get(final Object key) {
                                return super.get(String.valueOf(key).toUpperCase());
                            }
                        };

                        try (final ByteArrayOutputStream tempo = new ByteArrayOutputStream(256)) {
                            final AtomicBoolean inHead = new AtomicBoolean(true);
                            final Consumer<Byte> headersConsumer = HttpBinStreaming.headersTicker(tempo,
                                    len -> {},
                                    chunked -> {},
                                    cTypeHold::set,
                                    partHeaders::put,
                                    (n, v) -> {},
                                    unused -> tempo.reset(),
                                    unused -> {
                                        tempo.reset();
                                        inHead.set(false);
                                    }
                            );
                            final Consumer<Byte> bodyConsumer = tempo::write;

                            for (int i = partMark.first; i < partMark.second; i++) {
                                if (inHead.get())
                                    headersConsumer.accept(data[i]);
                                else
                                    bodyConsumer.accept(data[i]);
                            }

                            final String cd;
                            if ((cd = partHeaders.get(Headers.ContentDisposition)) != null) {
                                String fileName = null, fieldName = null;

                                final String cdl = cd.trim().toLowerCase();
                                if (cdl.startsWith(Headers.FormData) || cdl.startsWith(Headers.Attachment)) {
                                    try {
                                        final ParameterParser parser = new ParameterParser();
                                        parser.setLowerCaseNames();

                                        final Map<String, String> parameters = parser.parse(cd, ';');

                                        fileName = parameters.get("filename");
                                        fieldName = parameters.get("name");
                                    } catch (final Exception ignore) {}
                                }

                                if (isEmpty(fieldName))
                                    continue;

                                if (!isEmpty(fileName))
                                    m.fileData(fieldName, fileName, tempo.toByteArray(), cTypeHold.get());
                                else if (cTypeHold.get().getBaseType().startsWith("text/"))
                                    m.textData(fieldName, tempo.toString(StandardCharsets.UTF_8));
                                else
                                    m.binData(fieldName, tempo.toByteArray(), partHeaders);
                            }
                        }
                    }
                }

            return m;
        }

        public Form asForm() {
            if (f == null && data != null && data.length > 0 && contentTypeMatch(MimeType.FORM))
                synchronized (this) {
                    f = new Form();
                    final String fdata = new String(data, StandardCharsets.UTF_8);

                    Arrays.stream(fdata.split(Pattern.quote("&")))
                            .map(pair -> pair.split(Pattern.quote("=")))
                            .forEach(kv -> {
                                final String v = URLDecoder.decode(kv[1], StandardCharsets.UTF_8);

                                if (isEmpty(v)) {
                                    if (!f.containsKey(kv[0]))
                                        f.put(kv[0], new ArrayList<>(2));

                                    f.get(kv[0]).add(v);
                                }
                            });
                }

            return f;
        }

        private boolean contentTypeMatch(final MimeType expected) {
            if (this.contentType == null)
                return expected == null;

            try {
                return this.contentType.match(expected);
            } catch (final Exception ignore) {
            }

            return false;
        }
    }
}
