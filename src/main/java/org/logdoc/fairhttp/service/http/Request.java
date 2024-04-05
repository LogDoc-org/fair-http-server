package org.logdoc.fairhttp.service.http;

import com.fasterxml.jackson.databind.JsonNode;
import org.logdoc.fairhttp.errors.BodyReadError;
import org.logdoc.fairhttp.service.api.helpers.Headers;
import org.logdoc.fairhttp.service.tools.*;
import org.logdoc.fairhttp.service.tools.websocket.extension.IExtension;
import org.logdoc.fairhttp.service.tools.websocket.protocol.IProtocol;
import org.logdoc.helpers.std.MimeType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;
import java.util.zip.InflaterInputStream;

import static org.logdoc.fairhttp.service.tools.websocket.protocol.IProtocol.WS_VERSION;
import static org.logdoc.helpers.Digits.getInt;
import static org.logdoc.helpers.Texts.isEmpty;
import static org.logdoc.helpers.Texts.notNull;
import static org.logdoc.helpers.std.MimeTypes.TEXTPLAIN;

/**
 * @author Denis Danilin | me@loslobos.ru
 * 26.07.2023 15:01
 * fair-http-server ☭ sweat and blood
 */
public class Request extends MapAttributed {
    private static final Logger logger = LoggerFactory.getLogger(Request.class);
    private static final byte CR = 0x0D, LF = 0x0A, DASH = 0x2D;

    public static final byte[] streamEnd = {DASH, DASH},
            headerSeparator = {CR, LF, CR, LF},
            fieldSeparator = {CR, LF},
            boundaryPrefix = {CR, LF, DASH, DASH};

    private final RequestId id;
    private final Map<String, String> headers;
    private final Socket socket;
    private final int maxRequestSize;
    private Map<String, String> c;
    private int contentLength;
    private boolean chunked, gzip, deflate;
    private byte[] body;
    private String bs;
    private JsonNode bj;
    private Form bf;
    private MultiForm bm;

    Request(final RequestId id, final Map<String, String> headers, final Socket socket, final int maxRequestSize) {
        this.id = id;
        this.headers = headers;
        this.socket = socket;
        this.maxRequestSize = maxRequestSize;
    }

    public Socket getSocket() {
        return socket;
    }

    private String stringQuotes(String value) {
        if (value == null)
            return null;

        value = notNull(value);

        if (!value.startsWith("\""))
            return value;

        return value.substring(1, value.length() - 1);
    }

    public boolean isWebsocketUpgradable() {
        return isWebsocketUpgradable(null, null);
    }

    public boolean isWebsocketUpgradable(final IExtension extension) {
        return isWebsocketUpgradable(extension, null);
    }

    public boolean isWebsocketUpgradable(final IExtension extension, final IProtocol protocol) {
        try {MessageDigest.getInstance("SHA-1");} catch (final NoSuchAlgorithmException ignore) {
            return false;
        }

        return WS_VERSION.equals(header(Headers.SecWebsocketVersion))
                && (extension == null || extension.acceptProvidedExtensionAsServer(header(Headers.SecWebsocketExtensions)))
                && (protocol == null || protocol.acceptProtocol(header(Headers.SecWebsocketProtocols)));
    }

    public SocketAddress getRemote() {
        return socket.getRemoteSocketAddress();
    }

    public String method() {
        return id.method;
    }

    public String path() {
        return id.path;
    }

    public String uri() {
        return id.uri;
    }

    public String queryParam(final String name) {
        return id.query(name);
    }

    public String header(final String name) {
        return headers.get(name);
    }

    private void calcBody() {
        contentLength = getInt(headers.get(Headers.ContentLength));

        final String te = notNull(headers.get(Headers.TransferEncoding));

        chunked = te.contains("chunked");
        gzip = te.contains("gzip");
        deflate = te.contains("deflate");
    }

    private InputStream getIs() throws IOException {
        final InputStream is = socket.getInputStream();

        if (gzip)
            return new GZIPInputStream(is);

        if (deflate)
            return new InflaterInputStream(is);

        return is;
    }

    public byte[] bodyBytes() throws BodyReadError {
        if (body != null)
            return body;

        calcBody();

        if (chunked)
            return (body = readChunks());

        if (contentLength <= 0)
            return (body = new byte[0]);

        body = new byte[contentLength];
        int total = 0, read;

        try {
            final InputStream is = getIs();
            while (total != contentLength) {
                read = is.read(body, total, contentLength - total);

                if (read == -1)
                    throw new BodyReadError("Cant read " + contentLength + " bytes of body, got only " + total);

                total += read;
            }

            return body;
        } catch (final BodyReadError be) {
            throw be;
        } catch (final Exception e) {
            throw new BodyReadError(e);
        }
    }

    public String bodyString() throws BodyReadError {
        if (bs != null)
            return bs;

        return (bs = new String(bodyBytes(), StandardCharsets.UTF_8));
    }

    public JsonNode bodyJson() throws BodyReadError {
        if (bj != null)
            return bj;

        return (bj = Json.parse(bodyBytes()));
    }

    public Form bodyForm() throws BodyReadError {
        if (bf != null)
            return bf;

        bf = new Form();
        final String bs = bodyString();

        Arrays.stream(bs.split(Pattern.quote("&")))
                .map(pair -> pair.split(Pattern.quote("=")))
                .forEach(kv -> {
                    final String v = URLDecoder.decode(kv[1], StandardCharsets.UTF_8);

                    if (!isEmpty(v)) {
                        if (!bf.containsKey(kv[0]))
                            bf.put(kv[0], new ArrayList<>(2));

                        bf.get(kv[0]).add(v);
                    }
                });

        return bf;
    }

    public MultiForm bodyMultiForm() throws BodyReadError {
        if (bm != null)
            return bm;

        try {
            final byte[] body = bodyBytes();
            bm = new MultiForm();
            final byte[] bnd = new MimeType(header(Headers.ContentType)).getParameter("boundary").getBytes(StandardCharsets.ISO_8859_1);
            boolean done = false;
            int i = 0;

            while (!done) {
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

                i = indexOf(body, bnd, i);

                if (i == -1)
                    break;

                i += bnd.length;

                final int hdrs = indexOf(body, headerSeparator, i + 1);

                if (hdrs > i) {
                    int hdr;

                    while ((hdr = indexOf(body, fieldSeparator, i)) <= hdrs) {
                        final String hs = notNull(new String(Arrays.copyOfRange(body, i, hdr), StandardCharsets.UTF_8));
                        final int sep = hs.indexOf(':');

                        if (sep != -1)
                            partHeaders.put(notNull(hs.substring(0, sep)), notNull(hs.substring(sep + 1)));

                        i = hdr + fieldSeparator.length;
                    }

                    i += 2;

                    int till = indexOf(body, boundaryPrefix, i);

                    if (till == -1) {
                        done = true;
                        till = indexOf(body, streamEnd, i);
                    }

                    final byte[] pb = Arrays.copyOfRange(body, i, till);
                    i = till;

                    final String cd;
                    if ((cd = partHeaders.get(Headers.ContentDisposition)) != null) {
                        MimeType cType = TEXTPLAIN;
                        try {cType = new MimeType(partHeaders.get(Headers.ContentType));} catch (final Exception ignore) {}

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
                            bm.fileData(fieldName, fileName, pb, cType);
                        else if (cType.getBaseType().startsWith("text/"))
                            bm.textData(fieldName, new String(pb, StandardCharsets.UTF_8));
                        else
                            bm.binData(fieldName, pb, partHeaders);
                    }
                }
            }

            return bm;
        } catch (final BodyReadError e) {
            throw e;
        } catch (final Exception e) {
            throw new BodyReadError(e);
        }
    }

    private int indexOf(final byte[] data, final byte[] match, final int from) {
        MAIN:
        for (int i = from; i < data.length - match.length; i++) {
            for (int j = 0; j < match.length; j++)
                if (data[i + j] != match[j])
                    continue MAIN;

            return i;
        }

        return -1;
    }

    private byte[] readChunks() throws BodyReadError {
        try {
            final InputStream is = getIs();

            try (final ByteArrayOutputStream bos = new ByteArrayOutputStream(16 * 1024)) {
                int chunkSize, sum = 0;

                do {
                    chunkSize = getChunkSize(is);

                    if (chunkSize > 0) {
                        sum += chunkSize;

                        if (maxRequestSize > 0 && sum > maxRequestSize)
                            throw new IllegalStateException("Max request size is exceeded: " + maxRequestSize);

                        for (int i = 0; i < chunkSize; i++) bos.write(is.read());
                    }
                } while (chunkSize > 0);

                bos.flush();
                return bos.toByteArray();
            }

        } catch (final Exception e) {
            logger.error(e.getMessage(), e);
            throw new BodyReadError(e);
        }
    }

    private int getChunkSize(final InputStream is) throws IOException {
        int b;

        try (final ByteArrayOutputStream os = new ByteArrayOutputStream(8)) {
            do {
                b = is.read();

                if (Character.digit(b, 16) != -1)
                    os.write(b);
            } while (b != '\n');

            return Integer.parseInt(os.toString(StandardCharsets.US_ASCII), 16);
        }
    }

    public boolean hasHeader(final String name) {
        return header(name) != null;
    }

    public String cookie(final String name) {
        return cookies().get(name);
    }

    public Map<String, String> cookies() {
        if (c == null) {
            final Map<String, String> m = new HashMap<>();

            final String v = header(Headers.RequestCookies);

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

    public <T> T jsonmap(final Class<? extends T> klass) throws BodyReadError {
        return Json.fromJson(bodyJson(), klass);
    }
}
