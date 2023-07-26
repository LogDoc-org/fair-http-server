package org.logdoc.fairhttp.service.http;

import org.logdoc.fairhttp.service.tools.websocket.frames.CloseFrame;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

/**
 * @author Denis Danilin | me@loslobos.ru
 * 20.07.2023 11:22
 * fair-http-server ☭ sweat and blood
 */
class WSHandler extends Thread {
    private static final Logger logger = LoggerFactory.getLogger(WSHandler.class);

    private final Socket socket;
    private final InputStream is;
    private final OutputStream os;

    private final WebSocket ws;

    public WSHandler(final Socket socket, final WebSocket ws) throws IOException {
        this.socket = socket;
        this.ws = ws;

        setDaemon(true);

        socket.setSoTimeout(0);
        os = socket.getOutputStream();
        is = socket.getInputStream();

        this.ws.setWriteHandler(bytes -> {
            try {
                os.write(bytes);
                os.flush();
            } catch (IOException e) {
                logger.error("Cant write to websocket :: " + e.getMessage(), e);
                try { socket.close(); } catch (final Exception ignore) { }
                try { ws.close(CloseFrame.ABNORMAL_CLOSE, e.getMessage()); } catch (final Exception ignore) { }
            }
        });
    }

    @Override
    public void run() {
        try {
            os.write(ws.asBytes());

            do {
                ws.accept((byte) is.read());
            } while (!socket.isClosed());
        } catch (final IOException e) {
            logger.error(e.getMessage(), e);
        }
    }
}
