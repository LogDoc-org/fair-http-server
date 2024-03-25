package org.logdoc.fairhttp.service.http.tasks;

import org.logdoc.fairhttp.service.http.RequestId;
import org.slf4j.ILoggerFactory;

import java.io.EOFException;
import java.io.InputStream;
import java.net.Socket;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;

import static org.logdoc.helpers.Texts.notNull;

/**
 * @author Denis Danilin | me@loslobos.ru
 * 21.03.2024 13:50
 * fair-http-server ☭ sweat and blood
 */
public class RCSignature implements Runnable {
    private final Socket socket;
    private final CompletableFuture<RequestId> stage;

    public RCSignature(final Socket socket, final CompletableFuture<RequestId> stage) {
        this.socket = socket;
        this.stage = stage;
    }

    @Override
    public void run() {
        try {
            socket.setSoTimeout(500);
            final InputStream is = socket.getInputStream();

            final byte[] buf = new byte[4096];
            String s = "";

            for (int i = 0; i < 4096; i++) {
                buf[i] = (byte) is.read();

                if (buf[i] == -1)
                    throw new EOFException();

                if (buf[i] == '\r' && is.read() == '\n') {
                    s = new String(Arrays.copyOfRange(buf, 0, i));
                    break;
                }
            }

            if (s.isBlank())
                throw new IllegalStateException("Didnt read first line");

            final String[] parts = s.trim().split("\\s", 3);

            stage.complete(new RequestId(notNull(parts[0]).toUpperCase(), notNull(parts[1])));
        } catch (final Exception e) {
            stage.completeExceptionally(e);
        }
    }

}
