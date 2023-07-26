package org.logdoc.fairhttp.service.http;

import org.logdoc.fairhttp.service.api.helpers.Headers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.zip.GZIPInputStream;
import java.util.zip.InflaterInputStream;

import static org.logdoc.helpers.Digits.getInt;
import static org.logdoc.helpers.Texts.notNull;

/**
 * @author Denis Danilin | me@loslobos.ru
 * 19.07.2023 15:05
 * fair-http-server ☭ sweat and blood
 */
public class Handler extends Thread {
    private static final Logger logger = LoggerFactory.getLogger(Handler.class);

    private final Server server;
    //    private final SocketDriver driver;
    private final Socket socket;
    private final String id;
    private final int maxRequestSize, readTimeout;

    Handler(final Socket socket, final Server server, final int maxRequestSize, final int readTimeout) throws IOException {
        this.server = server;
        this.socket = socket;
        this.readTimeout = readTimeout;
        this.maxRequestSize = maxRequestSize;
        id = socket.getRemoteSocketAddress().toString();
//        this.driver = new SocketDriver(socket, maxRequestSize);

        setDaemon(true);
    }


    @Override
    public void run() {
        int i = 0;
        byte[] head = new byte[8192];

        try {
            final byte[] end = new byte[]{'\r', '\n'};

            socket.setSoTimeout(readTimeout);
            final InputStream is = socket.getInputStream();

            for (; i <= head.length; i++) {
                head[i] = (byte) is.read();

                if (i > 4 && end[0] == head[i - 3] && end[1] == head[i - 2] && end[0] == head[i - 1] && end[1] == head[i]) {
                    head = Arrays.copyOfRange(head, 0, i - 3);
                    break;
                }
            }

            if (i < 10) {
                logger.error(id + " :: Insufficient request, only " + i + " bytes read. Drop connection.");
                close();
                return;
            }
        } catch (final SocketTimeoutException e) {
            if (i > 0)
                logger.error(id + " :: Cant read request headers, timed out after " + i + " bytes. Drop connection.");
            close();
            return;
        } catch (final IOException e) {
            logger.error(id + " :: Error read request headers. Drop connection.");
            close();
            return;
        } catch (final ArrayIndexOutOfBoundsException e) {
            logger.error(id + " :: Headers section is out of the limit 8192 bytes. Drop connection", e);
            close();
            return;
        }

//        try {
        /*    final String[] heads = new String(head, StandardCharsets.UTF_8).split("\n");
            final String[] firstLine = heads[0].split("\\s", 3);

            if (firstLine.length != 3)
                throw new IllegalStateException("Wrong request first line: " + heads[0]);

            final Http.Request request = new Http.Request(this);
            request.method = notNull(firstLine[0]);
            request.path = notNull(firstLine[1]);
            request.proto = notNull(firstLine[2]);

            for (i = 1; i < heads.length; i++) {
                final int idx;
                if ((idx = heads[i].indexOf(':')) != -1) {
                    String name = heads[i].substring(0, idx).trim();

                    if (!name.isEmpty()) {
                        final String value = notNull(heads[i].substring(idx + 1));

                        if (name.equalsIgnoreCase(Headers.ContentLength)) {
                            contentLength = getInt(value);

                            if (contentLength > maxRequestSize)
                                throw new IllegalStateException("Max request size is exceeded: " + maxRequestSize);
                        } else if (name.equalsIgnoreCase(Headers.TransferEncoding)) {
                            final String v = notNull(value).toLowerCase(Locale.ROOT);

                            chunked = v.contains("chunked");
                            gzip = v.contains("gzip");
                            deflate = v.contains("deflate");
                        } else if (name.equalsIgnoreCase(Headers.ContentType))
                            try {
                                request.contentType = (new MimeType(value));
                            } catch (Exception e) {
                                logger.warn("Cant parse request content-type out of request header 'Content-Type' :: `" + value + "`", e);
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

            return request;*/
        server.handleRequest(new Request(socket.getRemoteSocketAddress(), head, this::readBody), this::response);
/*
        } catch (final DriverException e) {
            logger.error("Call #" + callId.getAndIncrement(), e);
            try {driver.write(Http.Response.ServerError(notNull(e.getMessage(), "Server internal error")).asBytes());} catch (final Exception ignore) {}
            close();
        } catch (final Exception e) {
            logger.error(e.getMessage(), e);
            try {driver.write(Http.Response.ServerError(notNull(e.getMessage(), "Server internal error")).asBytes());} catch (final Exception ignore) {}
            close();
        }
*/
    }

    private byte[] readBody(final Request request) {
        try {
            InputStream i = socket.getInputStream();

            final int contentLength = getInt(request.header(Headers.ContentLength));
            final String te = notNull(request.header(Headers.TransferEncoding));

            final boolean chunked = te.contains("chunked"),
                    gzip = te.contains("gzip"),
                    deflate = te.contains("deflate");

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

            socket.setSoTimeout(readTimeout);
            try (final ByteArrayOutputStream bos = new ByteArrayOutputStream(maxRequestSize == 0 ? 32 * 1024 * 1024 : maxRequestSize)) {
                final byte[] buf = new byte[1024];

                int read, sum = 0;
                try {
                    do {
                        read = i.read();
                        if (read > 0) {
                            bos.write(buf, 0, read);
                            sum += read;
                        }
                    } while (read != -1 && (maxRequestSize <= 0 || sum < maxRequestSize));
                } catch (final SocketTimeoutException ignore) {}

                bos.flush();

                return bos.toByteArray();
            }
        } catch (final Exception e) {
            logger.error(id + " :: " + e.getMessage(), e);
        }

        return new byte[0];
    }

    private byte[] chunksFrom(final InputStream is) throws IOException {
        socket.setSoTimeout(readTimeout);
        try (final ByteArrayOutputStream bos = new ByteArrayOutputStream(maxRequestSize == 0 ? 32 * 1024 * 1024 : maxRequestSize)) {
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

    void response(final Response response) {
        if (response instanceof WebSocket) {
            try {
                new WSHandler(socket, (WebSocket) response).start();
            } catch (IOException e) {
                logger.error("Cant open websocket :: " + e.getMessage(), e);

                response(Response.ServerError("Cant open websocket :: " + e.getMessage()));
            }

            return;
        }

        try (final OutputStream os = socket.getOutputStream()) {
            final byte[] data = response.asBytes();

            if (data != null && data.length > 0) {
                os.write(data);
                os.flush();
            }
        } catch (final Exception e) {
            logger.error(id + " :: " + e.getMessage(), e);
        } finally {
            close();
        }
    }

    void close() {
        try {socket.close();} catch (final Exception ignore) {}
    }
}
