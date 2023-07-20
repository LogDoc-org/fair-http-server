package org.logdoc.fairhttp.service.http;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * @author Denis Danilin | me@loslobos.ru
 * 20.07.2023 11:22
 * fair-http-server ☭ sweat and blood
 */
class WSHandler extends Thread {
    private static final Logger logger = LoggerFactory.getLogger(WSHandler.class);

    private final SocketDriver driver;

    private final Http.WebSocket ws;

    public WSHandler(final SocketDriver driver, final Http.WebSocket ws) {
        this.driver = driver;
        this.ws = ws;
        ws.setWriteHandler(driver::write);
        setDaemon(true);

        driver.soTimeout(0);
    }

    @Override
    public void run() {
        try {
            driver.write(ws.asBytes());

            do {
                ws.accept(driver.read());
            } while (!driver.isClosed());
        } catch (final IOException e) {
            logger.error(e.getMessage(), e);
        }
    }
}
