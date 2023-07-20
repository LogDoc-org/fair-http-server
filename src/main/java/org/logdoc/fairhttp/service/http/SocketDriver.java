package org.logdoc.fairhttp.service.http;

import org.logdoc.fairhttp.service.api.helpers.Headers;
import org.logdoc.fairhttp.service.api.helpers.MimeType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;
import java.util.zip.InflaterInputStream;

import static org.logdoc.fairhttp.service.tools.HttpBinStreaming.stringQuotes;
import static org.logdoc.helpers.Digits.getInt;
import static org.logdoc.helpers.Texts.notNull;

/**
 * @author Denis Danilin | me@loslobos.ru
 * 20.07.2023 12:37
 * fair-http-server ☭ sweat and blood
 */
class SocketDriver {
    private static final Logger logger = LoggerFactory.getLogger(SocketDriver.class);

    private final String id;
    private final Socket socket;
    private final InputStream is;
    private final OutputStream os;
    private final int maxBodyBytes;

    private int contentLength;
    private boolean chunked, gzip, deflate;

    SocketDriver(final Socket socket, final int maxBodyBytes) throws IOException {
        this.socket = socket;
        this.maxBodyBytes = maxBodyBytes;
        this.is = socket.getInputStream();
        this.os = socket.getOutputStream();
        id = socket.getRemoteSocketAddress().toString();
    }

    void soTimeout(final int timeout) {
        try {
            socket.setSoTimeout(timeout);
        } catch (final SocketException e) {
            logger.error(id + " :: " + e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }

    void close() {
        try {socket.close();} catch (final Exception ignore) {}
    }

    public Http.Request head() {
        byte[] head = new byte[8192];
        final Http.Request request = new Http.Request(this);
        final byte[] end = new byte[]{'\r', '\n'};

        try {
            soTimeout(1500);

            for (int i = 0; i <= head.length; i++) {
                head[i] = (byte) is.read();

                if (i > 4 && end[0] == head[i - 3] && end[1] == head[i - 2] && end[0] == head[i - 1] && end[1] == head[i]) {
                    head = Arrays.copyOfRange(head, 0, i - 3);
                    break;
                }
            }
        } catch (final ArrayIndexOutOfBoundsException e) {
            throw new IllegalStateException(id + " :: Headers section is out of the limit 8192 bytes");
        } catch (final Exception e) {
            logger.error(id + " :: " + e.getMessage(), e);
            throw new RuntimeException(e);
        }

        final String[] heads = new String(head, StandardCharsets.UTF_8).split("\n");
        final String[] firstLine = heads[0].split("\\s", 3);

        if (firstLine.length != 3)
            throw new IllegalStateException("Wrong request first line: " + heads[0]);

        request.method = notNull(firstLine[0]);
        request.path = notNull(firstLine[1]);
        request.proto = notNull(firstLine[2]);

        for (int i = 1; i < heads.length; i++) {
            final int idx;
            if ((idx = heads[i].indexOf(':')) != -1) {
                String name = heads[i].substring(0, idx).trim();

                if (!name.isEmpty()) {
                    final String value = notNull(heads[i].substring(idx + 1));

                    if (name.equalsIgnoreCase(Headers.ContentLength)) {
                        contentLength = getInt(value);

                        if (contentLength > maxBodyBytes)
                            throw new IllegalStateException("Max request size is exceeded: " + maxBodyBytes);
                    } else if (name.equalsIgnoreCase(Headers.TransferEncoding)) {
                        final String v = notNull(value).toLowerCase(Locale.ROOT);

                        chunked = v.contains("chunked");
                        gzip = v.contains("gzip");
                        deflate = v.contains("deflate");
                    } else if (name.equalsIgnoreCase(Headers.ContentType))
                        try {
                            request.contentType = (new MimeType(value));
                        } catch (Exception e) {
                            logger.warn("Cant parse request content-type :: " + e.getMessage(), e);
                        }
                    else if (name.equalsIgnoreCase(Headers.RequestCookies)) {
                        name = Headers.RequestCookies;

                        if (!value.isEmpty())
                            Arrays.stream(value.split(";"))
                                    .filter(s -> s.contains("="))
                                    .forEach(c -> {
                                        final String[] parts = c.split(Pattern.quote("="), 2);
                                        if (parts.length != 2) return;

                                        request.cookies.put(notNull(parts[0]), stringQuotes(parts[1]));
                                    });
                    }

                    request.headers.put(name.toUpperCase(), value);
                }

            } else
                logger.warn("Wrong headers line: " + heads[i]);
        }

        return request;
    }

    byte[] body() {
        try {
            InputStream i = is;

            if (deflate)
                i = new InflaterInputStream(i);

            if (gzip)
                i = new GZIPInputStream(i);

            if (chunked)
                return chunksFrom(i);

            if (contentLength > 0) {
                final byte[] data = new byte[contentLength];

                for (int j = 0; j < contentLength; j++)
                    data[j] = (byte) i.read();

                return data;
            }

            try (final ByteArrayOutputStream bos = new ByteArrayOutputStream(maxBodyBytes == 0 ? 32 * 1024 * 1024 : maxBodyBytes)) {
                final byte[] buf = new byte[1024];

                int read, sum = 0;

                do {
                    read = is.read();
                    if (read > 0) {
                        bos.write(buf, 0, read);
                        sum += read;
                    }
                } while (read != -1 && (maxBodyBytes <= 0 || sum < maxBodyBytes));

                bos.flush();
                return bos.toByteArray();
            }
        } catch (final Exception e) {
            logger.error(id + " :: " + e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }

    private byte[] chunksFrom(final InputStream is) throws IOException {
        soTimeout(3000);
        try (final ByteArrayOutputStream bos = new ByteArrayOutputStream(maxBodyBytes == 0 ? 32 * 1024 * 1024 : maxBodyBytes)) {
            int chunkSize, sum = 0;

            do {
                chunkSize = getChunkSize();

                if (chunkSize > 0) {
                    sum += chunkSize;

                    if (maxBodyBytes > 0 && sum > maxBodyBytes)
                        throw new IllegalStateException("Max request size is exceeded: " + maxBodyBytes);
                    for (int i = 0; i < chunkSize; i++) bos.write(is.read());
                }
            } while (chunkSize > 0);

            bos.flush();
            return bos.toByteArray();
        }
    }

    private int getChunkSize() throws IOException {
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

    void write(final byte[] data) {
        if (data != null && data.length > 0)
            try {
                os.write(data);
                os.flush();
            } catch (IOException e) {
                logger.error(id + " :: " + e.getMessage(), e);
                throw new RuntimeException(e);
            }
    }

    byte read() throws IOException {
        return (byte) is.read();
    }

    public boolean isClosed() {
        return socket.isClosed();
    }
}
