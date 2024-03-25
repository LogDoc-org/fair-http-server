package org.logdoc.fairhttp.service.http;

import org.logdoc.fairhttp.service.api.helpers.Headers;
import org.logdoc.fairhttp.service.http.tasks.RCHeaders;
import org.logdoc.fairhttp.service.http.tasks.RCSignature;
import org.logdoc.fairhttp.service.tools.ResourceConnect;
import org.logdoc.helpers.Sporadics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.Socket;
import java.net.SocketException;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import static org.logdoc.helpers.Digits.getInt;
import static org.logdoc.helpers.Texts.isEmpty;

/**
 * @author Denis Danilin | me@loslobos.ru
 * 20.03.2024 13:46
 * fair-http-server ☭ sweat and blood
 */
final class RCWrap implements ResourceConnect {
    private static final Logger logger = LoggerFactory.getLogger(RCWrap.class);
    private final UUID uuid;
    private final Socket socket;
    private final int maxRequestSize, readTimeout;
    private final RCBackup backup;

    private RequestId requestId;

    RCWrap(final Socket socket, final int maxRequestSize, final int readTimeout, final RCBackup backup) throws IOException {
        uuid = Sporadics.generateUuid();
        socket.setSoTimeout(readTimeout);
        this.socket = socket;
        this.backup = backup;

        this.maxRequestSize = maxRequestSize;
        this.readTimeout = readTimeout;

        final CompletableFuture<RequestId> getIdStage = new CompletableFuture<>();
        getIdStage.thenAccept(this::gotId);
        getIdStage.exceptionally(failed());

        backup.submit(new RCSignature(socket, getIdStage));
    }

    @Override
    public Socket getInput() {
        return socket;
    }

    private <K> Function<Throwable, K> failed() {
        return e -> {
            write(Response.ServerError(e.getMessage()));
            seppukku();
            return (K) null;
        };
    }

    private void seppukku() {
        try { socket.close(); } catch (final Exception ignore) { }
        backup.meDead(this);
    }

    private void gotId(final RequestId requestId) {
        if (!backup.canProcess(requestId)) {
            write(Response.NotFound());
            seppukku();
            return;
        }

        this.requestId = requestId;

        final CompletableFuture<Map<String, String>> getHeaders = new CompletableFuture<>();
        getHeaders.thenAccept(this::gotHeaders);
        getHeaders.exceptionally(failed());

        backup.submit(new RCHeaders(socket, getHeaders));
    }

    private void gotHeaders(final Map<String, String> headers) {
        if (headers == null || headers.isEmpty()) {
            write(Response.ClientError("Insufficient headers block"));
            seppukku();
            return;
        }

        if (getInt(headers.get(Headers.ContentLength)) > maxRequestSize) {
            write(Response.ClientError("Max request size limit is exceeded: " + headers.get(Headers.ContentLength) + " / " + maxRequestSize));
            seppukku();
            return;
        }

        try {
            socket.setSoTimeout(readTimeout);
        } catch (final SocketException e) {
            logger.error(e.getMessage(), e);
            write(Response.ServerError("Internal error"));
            seppukku();
        }

        backup.handleRequest(requestId, headers, this);
    }


    @Override
    public void write(final Response response) {
        if (response == null)
            return;

        if (response instanceof WebSocket) {
            ((WebSocket) response).spinOff(socket);
            backup.meDead(this);
            return;
        }

        try {
            socket.getOutputStream().write(response.asBytes());
            socket.getOutputStream().flush();
        } catch (final IOException e) {
            logger.error("Cant write response: " + e.getMessage(), e);
        } finally {
            seppukku();
        }
    }

    @Override
    public void write(final byte[] data) {
        if (isEmpty(data))
            return;

        try {
            socket.getOutputStream().write(data);
            socket.getOutputStream().flush();
        } catch (final Exception e) {
            logger.error(e.getMessage(), e);
        } finally {
            seppukku();
        }
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (!(o instanceof RCWrap)) return false;
        final RCWrap rcWrap = (RCWrap) o;
        return Objects.equals(uuid, rcWrap.uuid);
    }

    @Override
    public int hashCode() {
        return Objects.hash(uuid);
    }
}
