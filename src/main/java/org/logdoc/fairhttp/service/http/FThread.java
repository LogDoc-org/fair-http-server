package org.logdoc.fairhttp.service.http;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Denis Danilin | me@loslobos.ru
 * 07.02.2023 13:50
 * FairHttpService ☭ sweat and blood
 */
class FThread extends Thread {
    private static final Logger logger = LoggerFactory.getLogger(FThread.class);

    private final Server server;

    private final List<SocketDriver> opened;

    FThread(final Socket socket, final Server server) {
        this.server = server;

        opened = new ArrayList<>(8);

        setDaemon(true);

        server.threadStarted();
        accept(socket);
    }

    boolean accept(final Socket socket) {
        if (opened.size() >= server.capacityLimit())
            return false;

        try {
            socket.setSoTimeout(30);

            synchronized (opened) {
                return opened.add(new SocketDriver(socket));
            }
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
            while (opened.isEmpty())
                try {
                    sleep(30);
                } catch (final InterruptedException e) {
                    if (isInterrupted())
                        break MAIN;
                }

            final int iterationLimit = opened.size() == 1 ? Integer.MAX_VALUE : Math.max((1024 * 1024) - server.capacityLimit(), 1024 * 8);
            boolean read = false;

            for (int i = 0; i < opened.size(); i++) {
                final SocketDriver s = opened.get(i);
                if (s.state() == SocketDriver.STATE.ACCEPTING) {
                    read = true;
                    try {
                        s.read(iterationLimit);

                        if (s.state() == SocketDriver.STATE.REQUEST_READY)
                            server.handleRequest(s);
                    } catch (final IOException e) {
                        try {
                            opened.remove(i--).close();
                        } catch (final Exception ignore) {
                        }
                    }
                } else if (s.state() == SocketDriver.STATE.SOCKETERROR) {
                    read = true;
                    try {
                        opened.remove(i--).close();
                    } catch (final Exception ignore) {
                    }
                }
            }

            if (!read)
                try {
                    sleep(30);
                } catch (final InterruptedException e) {
                    if (isInterrupted())
                        break;
                }
        }

        try {
            join(30);
        } catch (final Exception ignore) {
        }
        server.threadStopped(this);
    }
}
