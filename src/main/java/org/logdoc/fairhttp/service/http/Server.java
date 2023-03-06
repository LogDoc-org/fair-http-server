package org.logdoc.fairhttp.service.http;

import com.typesafe.config.Config;
import org.logdoc.fairhttp.service.api.helpers.DynamicRoute;
import org.logdoc.fairhttp.service.api.helpers.FairHttpServer;
import org.logdoc.fairhttp.service.api.helpers.MimeType;
import org.logdoc.fairhttp.service.api.helpers.endpoint.Endpoint;
import org.logdoc.fairhttp.service.http.statics.DirectRead;
import org.logdoc.fairhttp.service.http.statics.NoStatics;
import org.logdoc.fairhttp.service.tools.ConfigPath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.Inet4Address;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import static org.logdoc.fairhttp.service.tools.Strings.isEmpty;

/**
 * @author Denis Danilin | me@loslobos.ru
 * 03.02.2023 17:39
 * FairHttpService ☭ sweat and blood
 */
public class Server implements FairHttpServer {
    private static final Logger logger = LoggerFactory.getLogger(Server.class);
    private static Server backRef = null;

    private final SortedSet<Endpoint> endpoints;
    private final AtomicInteger densityFactor, threadCount;
    private final List<FThread> running;
    private final Config config;
    private final Function<String, Http.Response> assets;

    public Server(final Config config) {
        synchronized (Server.class) {
            if (backRef != null)
                throw new IllegalStateException("Only one instance can be started");

            backRef = this;
        }

        this.config = config;

        this.endpoints = new TreeSet<>();

        densityFactor = new AtomicInteger(2);
        threadCount = new AtomicInteger(0);

        running = new ArrayList<>(16);

        final String dir = config.hasPath(ConfigPath.STATIC_DIR) ? config.getString(ConfigPath.STATIC_DIR) : null;
        assets = isEmpty(dir) ? new NoStatics() : new DirectRead();
    }

    public void start() {
        new Thread(() -> {
            try (final ServerSocket socket = new ServerSocket(config.getInt(ConfigPath.PORT))) {
                Socket child;
                LOOP:
                do {
                    child = socket.accept();

                    for (final FThread f : running)
                        if (f.accept(child))
                            continue LOOP;

                    densityFactor.incrementAndGet();
                    final FThread f = new FThread(child, this);
                    f.start();

                    running.add(f);
                } while (child != null);
            } catch (final Exception e) {
                e.printStackTrace();
                logger.error(e.getMessage(), e);
                System.exit(-1);
            }
        }) {
            @Override
            public synchronized void start() {
                setPriority(7);
                setName("FairHttpServer");
                super.start();
            }
        }.start();
        try {
            logger.info("Listen at:\thttp://" + Inet4Address.getLocalHost().getHostAddress() + ":" + config.getInt(ConfigPath.PORT));
        } catch (final Exception e) {
            logger.error("Cant get local host: " + e.getMessage(), e);
        }
    }

    void handleRequest(final SocketDriver driver) {
        final Http.Request request = driver.request();

        final String hardPath = request.path();

        for (final Endpoint e : endpoints)
            if (e.match(request.method, hardPath)) {
                logger.info(request.toString());
                final Function<Throwable, Void> errorHandler = t -> {
                    logger.error(t.getMessage(), t);
                    final Http.Response response = Http.Response.ServerError();
                    if (t.getMessage() != null)
                        response.setPayload(t.getMessage().getBytes(StandardCharsets.UTF_8), MimeType.TEXTPLAIN);

                    driver.response(response);
                    return null;
                };

                if (e.isAsync()) {
                    CompletableFuture.runAsync(() -> e.handleAsync(request)
                            .thenAccept(driver::response)
                            .exceptionally(errorHandler));
                } else
                    CompletableFuture.runAsync(() -> driver.response(e.handle(request)))
                            .exceptionally(errorHandler);

                return;
            }

        CompletableFuture.runAsync(() -> driver.response(assets.apply(request.path())));
    }

    boolean mayClose() {
        return threadCount.get() > 2;
    }

    void threadStopped(final FThread f) {
        threadCount.decrementAndGet();

        synchronized (running) {
            running.remove(f);
        }

        if (densityFactor.get() > 2)
            densityFactor.decrementAndGet();
    }

    void threadStarted() {
        threadCount.incrementAndGet();
    }

    int capacityLimit() {
        return (int) Math.pow(2, densityFactor.get());
    }

    @Override
    public synchronized void setupDynamicEndpoints(final Collection<DynamicRoute> routes) {
        Endpoint ep;
        for (final DynamicRoute pretend : routes)
            try {
                endpoints.add((ep = new Endpoint(pretend)));
                logger.info("Added endpoint: " + ep);
            } catch (final Exception e) {
                logger.error("Cant add endpoint '" + pretend + "' :: " + e.getMessage(), e);
            }

    }

    @Override
    public synchronized void setupConfigEndpoints(final Collection<String> raw) {
        Endpoint ep;
        for (final String pretend : raw)
            try {
                endpoints.add((ep = new Endpoint(pretend)));
                logger.info("Added endpoint: " + ep);
            } catch (final ArrayIndexOutOfBoundsException ignore) {
            } catch (final Exception e) {
                logger.error("Cant add endpoint '" + pretend + "' :: " + e.getMessage(), e);
            }
    }

    @Override
    public synchronized boolean removeEndpoint(final String method, final String signature) {
        for (final Endpoint e : endpoints)
            if (e.equals(method, signature)) {
                endpoints.remove(e);
                return true;
            }

        return false;
    }

    @Override
    public synchronized boolean addEndpoint(final DynamicRoute route) {
        return endpoints.add(new Endpoint(route));
    }
}
