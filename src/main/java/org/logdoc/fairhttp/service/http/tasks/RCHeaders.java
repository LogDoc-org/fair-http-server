package org.logdoc.fairhttp.service.http.tasks;

import java.io.InputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CompletableFuture;

import static org.logdoc.helpers.Texts.notNull;

/**
 * @author Denis Danilin | me@loslobos.ru
 * 22.03.2024 10:20
 * fair-http-server ☭ sweat and blood
 */
public class RCHeaders implements Runnable {
    private final Socket socket;
    private final CompletableFuture<Map<String, String>> stage;

    public RCHeaders(final Socket socket, final CompletableFuture<Map<String, String>> stage) {
        this.socket = socket;
        this.stage = stage;
    }

    @Override
    public void run() {
        try {
            final Map<String, String> map = new HashMap<>(0) {
                @Override
                public String get(final Object key) {
                    return super.get(notNull(key).toUpperCase(Locale.ROOT));
                }
            };
            final byte[] buf = new byte[8192];
            final InputStream is = socket.getInputStream();

            int i = 0, idx;
            do {
                buf[i++] = (byte) is.read();
            } while (i < 4 || buf[i - 3] != '\r' || buf[i - 2] != '\n' || buf[i - 1] != '\r' || buf[i] != '\n');

            final String data = new String(Arrays.copyOfRange(buf, 0, i - 3), StandardCharsets.UTF_8);
            final String[] heads = data.split("\n");

            String name;

            for (i = 0; i < heads.length; i++) {
                if ((idx = heads[i].indexOf(':')) != -1) {
                    name = notNull(heads[i].substring(0, idx));

                    if (!name.isEmpty())
                        map.put(name.toUpperCase(Locale.ROOT), notNull(heads[i].substring(idx + 1)));
                }
            }

            stage.complete(Collections.unmodifiableMap(map));
        } catch (final ArrayIndexOutOfBoundsException e) {
            stage.completeExceptionally(new IllegalStateException("Request headers block exceeds 8192 bytes limit"));
        } catch (final Exception e) {
            stage.completeExceptionally(e);
        }
    }
}
