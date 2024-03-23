package org.logdoc.fairhttp.service.http;

import org.logdoc.fairhttp.service.api.helpers.Headers;
import org.logdoc.fairhttp.service.http.tasks.RCHeaders;
import org.logdoc.fairhttp.service.http.tasks.RCSignature;
import org.logdoc.fairhttp.service.tools.ResourceConnect;
import org.logdoc.fairhttp.service.tools.ScanBuf;
import org.logdoc.helpers.Sporadics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import static org.logdoc.helpers.Digits.getInt;
import static org.logdoc.helpers.Texts.notNull;

/**
 * @author Denis Danilin | me@loslobos.ru
 * 20.03.2024 13:46
 * fair-http-server ☭ sweat and blood
 */
final class RCWrap implements ResourceConnect {
    private static final Logger logger = LoggerFactory.getLogger(RCWrap.class);
    private final UUID uuid;
    private final Socket socket;
    private final InputStream is;
    private final OutputStream os;
    private final int maxRequestSize, readTimeout, execTimeout;
    private final RCBackup backup;
    private final ScanBuf buf;
    private final Map<String, String> headers;

    private byte[] body = null;
    private int totalRead;
    private boolean want2read;
    private String method, resource, proto;
    private boolean headersAreDone;
    private boolean promptIsDone;
    private Runnable task;
    private int contentLength;
    private boolean chunked;
    private boolean gzip;
    private boolean deflate;

    RCWrap(final Socket socket, final int maxRequestSize, final int readTimeout, final int execTimeout, final RCBackup backup) throws IOException {
        uuid = Sporadics.generateUuid();
        socket.setSoTimeout(readTimeout);
        this.socket = socket;
        this.is = socket.getInputStream();
        this.os = socket.getOutputStream();
        this.backup = backup;
        this.headers = new HashMap<>(8);

        this.maxRequestSize = maxRequestSize;
        this.readTimeout = readTimeout;
        this.execTimeout = execTimeout;
        buf = new ScanBuf();

        final CompletableFuture<RequestId> getIdStage = new CompletableFuture<>();
        getIdStage.thenAccept(this::gotId);
        getIdStage.exceptionally(failed());

        task = new RCSignature(socket, getIdStage);
    }

    private <K> Function<Throwable, K> failed() {
        return e -> {
            logger.error("Stage failed: " + e.getMessage(), e);
//            write(Response.ServerError(e.getMessage())); // todo
            seppukku();
            return (K) null;
        };
    }

    private void seppukku() {
        // todo
    }

    private void gotId(final RequestId requestId) {
        if (!backup.canProcess(requestId)) {
//            write(Response.NotFound()); // todo
            seppukku();
            return;
        }

        final CompletableFuture<Map<String, String>> getHeaders = new CompletableFuture<>();
        getHeaders.thenAccept(this::gotHeaders);
        getHeaders.exceptionally(failed());

        task = new RCHeaders(socket, getHeaders);
    }

    private void gotHeaders(final Map<String, String> headers) {
        if (headers == null || headers.isEmpty()) {
//            write(Response.ClientError("Insufficient headers block")); // todo
            seppukku();
            return;
        }

        contentLength = getInt(headers.get(Headers.ContentLength));
        final String te = notNull(headers.get(Headers.TransferEncoding));

        chunked = te.contains("chunked");
        gzip = te.contains("gzip");
        deflate = te.contains("deflate");

        backup.setWeAreReady(this);
    }

    @Override
    public void readUpTo(final int lim) {
        if (!want2read)
            return;

        try {
            byte[] data = new byte[lim];
            boolean readed = false;
            int off = 0, toRead = lim, read;

            while (!readed) {
                read = is.read(data, off, toRead);

                if (read == -1) {
                    want2read = false;
                    readed = true;
                } else {
                    off += read;
                    toRead -= read;

                    readed = toRead == 0;

                    totalRead += read;

                    if (totalRead > maxRequestSize)
                        throw new IllegalStateException("Request size exceeded limit " + maxRequestSize);
                }
            }

            buf.append(data);

            if (body == null) {
                if (!headersAreDone) {
                    if (!promptIsDone) {
                        if (proto == null) {
                            if (resource == null) {
                                if (method == null) {
                                    if (buf.missed((byte) ' '))
                                        throw new IllegalStateException("Wrong HTTP request format");

                                    method = buf.scanAndCut((byte) ' ').toString();

                                    if (buf.missed((byte) ' '))
                                        return;
                                }

                                if (buf.missed((byte) ' '))
                                    return;

                                resource = buf.scanAndCut((byte) ' ').toString();

                                if (buf.missed((byte) ' '))
                                    return;
                            }

                            if (buf.missed(sep))
                                return;

                            proto = buf.scanAndCut(sep).toString();
                            promptIsDone = true;
                        }

                        if (buf.missed(sep))
                            return;
                    }

                    if (buf.missed(sep))
                        return;

                    if (buf.missed((byte) ':'))
                        throw new IllegalStateException("Wrong header? " + buf);

                    headers.put(
                            buf.scanAndCut((byte) ':').toString(),
                            buf.scanAndCut(sep).toString()
                    );

                    if (buf.startsWith(sep)) {
                        buf.skip(sep.length);
                        headersAreDone = true;

                        task = () -> {
                            RCWrap.this.task = null;
                            backup.warmUpRequest(RCWrap.this);
                        };
                    } else
                        return;
                }

                if (want2read)
                    return;

                body = buf.asData();
            }
        } catch (final Exception e) {
            logger.error("Socket #" + uuid + " read error: " + e.getMessage(), e);
            backup.panic(e, this);
        }
    }

    @Override
    public boolean hasTask() {
        return task != null;
    }

    @Override
    public Runnable getTask() {
        return task;
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
