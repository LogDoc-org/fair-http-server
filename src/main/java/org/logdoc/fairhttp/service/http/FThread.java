package org.logdoc.fairhttp.service.http;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author Denis Danilin | me@loslobos.ru
 * 07.02.2023 13:50
 * FairHttpService ☭ sweat and blood
 */
class FThread extends Thread {
    private static final Logger logger = LoggerFactory.getLogger(FThread.class);

    private final Server server;

    private final Set<SocketDriver> opened;

    FThread(final Socket socket, final Server server) {
        this.server = server;

        opened = ConcurrentHashMap.newKeySet(8);

        setDaemon(true);

        server.threadStarted();
        logger.debug("New FThread started");
        accept(socket);
    }

    boolean accept(final Socket socket) {
        if (opened.size() >= server.capacityLimit())
            return false;

        try {
            socket.setSoTimeout(30);

            return opened.add(new SocketDriver(socket));
        } catch (final Exception e) {
            logger.error("Cant setup socket timeout: " + e.getMessage(), e);
        }

        return false;
    }

    @SuppressWarnings("BusyWait")
    @Override
    public void run() {
        MAIN:
        while (!opened.isEmpty() || !server.mayClose()) {
            while (opened.isEmpty() || opened.stream().allMatch(s -> s.state() != SocketDriver.STATE.ACCEPTING))
                try {
                    sleep(30);
                } catch (final InterruptedException e) {
                    if (isInterrupted())
                        break MAIN;
                }

            final int iterationLimit = opened.size() == 1 ? Integer.MAX_VALUE : 1024 * (16 - (server.capacityLimit() - 2));

            final List<SocketDriver> toRemove = new ArrayList<>(8);

            for (final SocketDriver s : opened)
                if (s.state() == SocketDriver.STATE.ACCEPTING) {
                    try {
                        s.read(iterationLimit);

                        if (s.state() == SocketDriver.STATE.REQUEST_READY)
                            server.handleRequest(s);
                    } catch (final IOException e) {
                        logger.debug("Closing socket: " + s + " :: " + e.getMessage());
                        toRemove.add(s);
                    }
                } else if (s.state() == SocketDriver.STATE.SOCKETERROR)
                    toRemove.add(s);

            toRemove.forEach(driver -> {
                try {
                    driver.close();
                } catch (final Exception ignore) {
                }
                opened.remove(driver);
            });
        }

        try {
            join(30);
        } catch (final Exception ignore) {
        }
        server.threadStopped(this);
        logger.debug("FThread stopped");
    }
}
