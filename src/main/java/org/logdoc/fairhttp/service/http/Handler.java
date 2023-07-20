package org.logdoc.fairhttp.service.http;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.Socket;
import java.net.SocketTimeoutException;

import static org.logdoc.helpers.Texts.notNull;

/**
 * @author Denis Danilin | me@loslobos.ru
 * 19.07.2023 15:05
 * fair-http-server ☭ sweat and blood
 */
public class Handler extends Thread {
    private static final Logger logger = LoggerFactory.getLogger(Handler.class);

    private final Server server;

    private final SocketDriver driver;

    Http.Request request;

    Handler(final Socket socket, final Server server, final int maxRequestSize) throws IOException {
        this.server = server;
        this.driver = new SocketDriver(socket, maxRequestSize);

        setDaemon(true);
    }

    @Override
    public void run() {
        try {
            request = driver.head();
            server.handleRequest(this);
        } catch (final Exception e) {
            logger.error(e.getMessage(), e);
            try {driver.write(Http.Response.ServerError(notNull(e.getMessage(), "Server internal error")).asBytes());} catch (final Exception ignore) {}
            close();
        }
    }

    void response(final Http.Response response) {
        try {
            if (response instanceof Http.WebSocket) {
                ((Http.WebSocket) response).prepare(request);
                new WSHandler(driver, (Http.WebSocket) response).start();
            } else {
                driver.write(response.asBytes());
                close();
            }
        } catch (final Throwable e) {
            logger.error(e.getMessage(), e);
        }
    }

    void close() {
        driver.close();
    }
}
