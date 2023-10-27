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
    private final Socket socket;
    private final String id;
    private final int maxRequestSize, readTimeout;

    Handler(final Socket socket, final Server server, final int maxRequestSize, final int readTimeout) {
        this.server = server;
        this.socket = socket;
        this.readTimeout = readTimeout;
        this.maxRequestSize = maxRequestSize;
        id = socket.getRemoteSocketAddress().toString();

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
            if (i > 0) {
                String data = "";
                try { data = " ::> " + new String(Arrays.copyOfRange(head, 0, i), StandardCharsets.UTF_8); } catch (final Exception ignore) { }
                logger.error(id + " :: Cant read request headers, timed out after " + i + " bytes. Drop connection." + data);
            }

            close();
            return;
        } catch (final IOException e) {
            logger.error(id + " :: Error read request headers. Drop connection.");
            close();
            return;
        } catch (final ArrayIndexOutOfBoundsException e) {
            String data = "";
            try { data = " ::> " + new String(Arrays.copyOfRange(head, 0, 1024), StandardCharsets.UTF_8); } catch (final Exception ignore) { }
            logger.error(id + " :: Headers section is out of the limit 8192 bytes. Drop connection." + data, e);
            close();
            return;
        }

        server.handleRequest(new Request(socket.getRemoteSocketAddress(), head, this::readBody), this::response);
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
                        read = i.read(buf);
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
            ((WebSocket) response).spinOff(socket);
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
