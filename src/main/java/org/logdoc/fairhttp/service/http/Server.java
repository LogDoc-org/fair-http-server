package org.logdoc.fairhttp.service.http;

import com.typesafe.config.Config;
import org.logdoc.fairhttp.service.api.helpers.DynamicRoute;
import org.logdoc.fairhttp.service.api.helpers.MimeType;
import org.logdoc.fairhttp.service.api.helpers.endpoint.Endpoint;
import org.logdoc.fairhttp.service.http.statics.BundledRead;
import org.logdoc.fairhttp.service.http.statics.DirectRead;
import org.logdoc.fairhttp.service.http.statics.NoStatics;
import org.logdoc.fairhttp.service.tools.ConfigPath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.Inet4Address;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import java.util.function.Function;

import static org.logdoc.fairhttp.service.http.statics.BundledRead.PlaceHolder;
import static org.logdoc.helpers.Texts.isEmpty;
import static org.logdoc.helpers.Texts.notNull;

/**
 * @author Denis Danilin | me@loslobos.ru
 * 03.02.2023 17:39
 * FairHttpService ☭ sweat and blood
 */
public class Server {
    private static final Logger logger = LoggerFactory.getLogger(Server.class);
    private static Server backRef = null;

    private final SortedSet<Endpoint> endpoints;
    private final AtomicInteger densityFactor, threadCount;
    private final List<FThread> running;
    private final int port;
    private final Function<String, Http.Response> assets;
    private final CORS cors;

    public Server(final int port) { // minimal
        this.port = port;
        this.endpoints = new TreeSet<>();

        densityFactor = new AtomicInteger(2);
        threadCount = new AtomicInteger(0);

        running = new ArrayList<>(16);
        assets = new NoStatics();
        cors = new CORS(null);
    }

    public Server(final Config config) {
        synchronized (Server.class) {
            if (backRef != null)
                throw new IllegalStateException("Only one instance can be started");

            backRef = this;
        }

        port = config.getInt(ConfigPath.PORT);

        this.endpoints = new TreeSet<>();

        densityFactor = new AtomicInteger(2);
        threadCount = new AtomicInteger(0);

        running = new ArrayList<>(16);

        final Config staticsCfg = config.hasPath("fair.http.statics") && !config.getIsNull("fair.http.statics") ? config.getConfig("fair.http.statics") : null;
        final String dir = staticsCfg != null && staticsCfg.hasPath("root") && !staticsCfg.getIsNull("root") ? notNull(staticsCfg.getString("root")) : null;

        Function<String, Http.Response> assets0 = new NoStatics();

        try {
            if (!isEmpty(dir)) {
                if (dir.startsWith(PlaceHolder))
                    assets0 = new BundledRead(staticsCfg, dir);
                else {
                    final Path p = Paths.get(dir);
                    if (Files.exists(p) && Files.isDirectory(p))
                        assets0 = new DirectRead(staticsCfg, dir);
                }
            }
        } catch (final IllegalStateException ise) {
            logger.debug("Cant setup static assets: " + ise.getMessage() + ", noop.");
            assets0 = new NoStatics();
        }

        assets = assets0;

        cors = new CORS(config);
    }

    public void start() {
        new Thread(() -> {
            try (final ServerSocket socket = new ServerSocket(port)) {
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
            logger.info("Listen at:\thttp://" + Inet4Address.getLocalHost().getHostAddress() + ":" + port);
        } catch (final Exception e) {
            logger.error("Cant get local host: " + e.getMessage(), e);
        }
    }

    void handleRequest(final SocketDriver driver) {
        final Http.Request request = driver.request();

        final String hardPath = request.path();

        boolean hasMatchPath = false;

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

                CompletableFuture.runAsync(() -> e.call(request)
                        .thenApply(rsp -> cors.wrap(request, rsp))
                        .thenAccept(driver::response)
                        .exceptionally(errorHandler));

                return;
            } else if (!hasMatchPath && e.pathMatch(hardPath))
                hasMatchPath = true;

        if (hasMatchPath && request.method.equals("OPTIONS")) {
            driver.response(cors.wrap(request, Http.Response.NoContent()));
            return;
        }

        if (request.method.equals("GET"))
            CompletableFuture.runAsync(() -> driver.response(assets.apply(request.path())));
        else
            driver.response(Http.Response.NotFound());
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

    public synchronized void setupDynamicEndpoints(final Collection<DynamicRoute> routes) {
        Endpoint ep;
        for (final DynamicRoute pretend : routes)
            try {
                endpoints.add((ep = new Endpoint(pretend.method, pretend.endpoint, pretend.callback)));
                logger.info("Added endpoint: " + ep);
            } catch (final Exception e) {
                logger.error("Cant add endpoint '" + pretend + "' :: " + e.getMessage(), e);
            }

    }

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

    public synchronized boolean removeEndpoint(final String method, final String signature) {
        for (final Endpoint e : endpoints)
            if (e.equals(method, signature)) {
                endpoints.remove(e);
                return true;
            }

        return false;
    }

    public synchronized boolean addEndpoint(final String method, final String endpoint, final BiFunction<Http.Request, Map<String, String>, CompletionStage<Http.Response>> callback) {
        return endpoints.add(new Endpoint(method, endpoint, callback));
    }
}
