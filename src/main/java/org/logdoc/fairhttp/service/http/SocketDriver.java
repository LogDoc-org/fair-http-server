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
import java.net.SocketTimeoutException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.logdoc.helpers.Digits.getInt;
import static org.logdoc.helpers.Texts.notNull;

/**
 * @author Denis Danilin | me@loslobos.ru
 * 26.02.2023 17:11
 * FairHttpService ☭ sweat and blood
 */
public class SocketDriver {
    private static final Logger logger = LoggerFactory.getLogger(SocketDriver.class);

    private final String id;
    private final InputStream is;
    private final OutputStream os;
    private final AtomicReference<STATE> state;
    private final ByteArrayOutputStream tmp;

    private Consumer<Byte> consumer;

    private volatile Http.Request request;

    SocketDriver(final Socket socket) throws IOException {
        socket.setSoTimeout(30);
        is = socket.getInputStream();
        os = socket.getOutputStream();
        state = new AtomicReference<>(STATE.ACCEPTING);
        tmp = new ByteArrayOutputStream(1024 * 8);
        id = socket.getRemoteSocketAddress().toString();

        consumer = methodConsume();
    }

    public STATE state() {
        return state.get();
    }

    public void state(final STATE state) {
        this.state.set(state);
    }

    void read(final int max) throws IOException {
        try {
            for (int i = 0, b = 0; i < max && b != -1; i++) {
                b = is.read();

                if (b != -1)
                    handleByte((byte) b); // fill
            }
        } catch (final SocketTimeoutException ignore) {
        }
    }

    private void handleByte(final byte b) {
        if (consumer != null)
            consumer.accept(b);
    }

    private Consumer<Byte> methodConsume() {
        request = new Http.Request();

        return b -> {
            if (Character.isSpaceChar(b)) {
                request.method = tmp.toString(StandardCharsets.US_ASCII);
                logger.debug("Request method: " + request.method);
                tmp.reset();
                consumer = pathConsume();
                return;
            }

            tmp.write(b);
        };
    }

    private Consumer<Byte> pathConsume() {
        return b -> {
            if (Character.isSpaceChar(b)) {
                request.path = URLDecoder.decode(tmp.toString(StandardCharsets.US_ASCII), StandardCharsets.UTF_8).replaceAll("/{2,}", "/");
                logger.debug("Request path: " + request.path);
                tmp.reset();
                consumer = protoConsume();
                return;
            }

            tmp.write(b);
        };
    }

    private Consumer<Byte> protoConsume() {
        return b -> {
            if (b == '\n') {
                request.proto = tmp.toString(StandardCharsets.US_ASCII).trim();
                logger.debug("Request proto: " + request.proto);
                tmp.reset();
                consumer = headersConsume();
                return;
            }

            tmp.write(b);
        };
    }

    private Consumer<Byte> headersConsume() {
        return b -> {
            if (b == '\n') {
                final String headerLine = tmp.toString(StandardCharsets.UTF_8).trim();
                tmp.reset();
                final int idx;
                if ((idx = headerLine.indexOf(':')) != -1) {
                    String name = headerLine.substring(0, idx).trim();

                    if (!name.isEmpty()) {
                        final String value = notNull(headerLine.substring(idx + 1));

                        if (name.equalsIgnoreCase(Headers.ContentLength))
                            request.knownBodyLength = getInt(value);
                        else if (name.equalsIgnoreCase(Headers.TransferEncoding))
                            request.chunked = "chunked".equalsIgnoreCase(value);
                        else if (name.equalsIgnoreCase(Headers.ContentType))
                            try {
                                request.contentType = new MimeType(value);
                            } catch (Exception e) {
                                logger.warn(e.getMessage(), e);
                            }
                        else if (name.equalsIgnoreCase(Headers.RequestCookies)) {
                            name = Headers.RequestCookies;

                            if (!value.isEmpty())
                                Arrays.stream(value.split(";"))
                                        .filter(s -> s.contains("="))
                                        .forEach(c -> {
                                            final int i = c.indexOf('=');
                                            try {
                                                request.cookies.put(c.substring(0, i).trim(), c.substring(i + 2, c.length() - 1).trim());
                                            } catch (final Exception e) {
                                                logger.warn("Broken cookie piece: `" + c + "`; cookie line is: `" + headerLine + "`");
                                            }
                                        });
                        }

                        request.headers.put(name, value);
                        logger.debug("Request header: '" + name + "' = '" + value + "'");
                    }
                } else if (headerLine.isEmpty()) {
                    logger.debug("Request headers done, body reader ready");
                    tmp.reset();
                    state(STATE.REQUEST_READY);
                    consumer = tmp::write;
                    return;
                }

                consumer = headersConsume();
            } else
                tmp.write(b);
        };
    }

    @Override
    public String toString() {
        return id + " [" + state + "]";
    }

    void close() {
        try {
            is.close();
        } catch (final Exception ignore) {
        }
        try {
            os.close();
        } catch (final Exception ignore) {
        }
    }

    Http.Request request() {
        if (tmp.size() > 0)
            request.preRead = tmp.toByteArray();
        tmp.reset();

        request.is = is;

        return request;
    }

    void response(final Http.Response response) {
        try {
            request.skipBody();

            if (response instanceof Http.WebSocket) {
                ((Http.WebSocket) response).prepare(request, os, unused -> state(STATE.SOCKETERROR));
                consumer = (Http.WebSocket) response;
            }

            response.writeTo(os);

            consumer = methodConsume();
            state(STATE.ACCEPTING);
        } catch (final Throwable e) {
            state(STATE.SOCKETERROR);
            logger.error(e.getMessage(), e);
        }
    }

    public enum STATE {ACCEPTING, SOCKETERROR, REQUEST_READY}
}
