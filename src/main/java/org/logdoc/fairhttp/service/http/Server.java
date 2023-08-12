package org.logdoc.fairhttp.service.http;

import com.typesafe.config.Config;
import org.logdoc.fairhttp.service.api.helpers.DynamicRoute;
import org.logdoc.fairhttp.service.api.helpers.endpoint.Endpoint;
import org.logdoc.fairhttp.service.http.statics.BundledRead;
import org.logdoc.fairhttp.service.http.statics.DirectRead;
import org.logdoc.fairhttp.service.http.statics.NoStatics;
import org.logdoc.fairhttp.service.tools.ConfigPath;
import org.logdoc.fairhttp.service.tools.ConfigTools;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.Inet4Address;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.BiFunction;
import java.util.function.Consumer;
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
    private final int port, maxRequestBytes;
    private final int readTimeout;
    private final Function<String, Response> assets;
    private final CORS cors;

    private Function<Throwable, Response> errorHandler;

    public Server(final int port, final int maxRequestBytes) { // minimal
        this.port = port;
        this.maxRequestBytes = maxRequestBytes;
        this.readTimeout = 15000;
        this.endpoints = new TreeSet<>();

        assets = new NoStatics();
        cors = new CORS(null);

        errorHandler = throwable -> {
            if (throwable != null)
                logger.error(throwable.getMessage(), throwable);

            return throwable == null ? Response.ServerError() : Response.ServerError(throwable.getMessage());
        };
    }

    public Server(final Config config) {
        synchronized (Server.class) {
            if (backRef != null)
                throw new IllegalStateException("Only one instance can be started");

            backRef = this;
        }

        port = config.getInt(ConfigPath.PORT);
        maxRequestBytes = config.getBytes(ConfigPath.MAX_REQUEST).intValue();
        readTimeout = config.getInt(ConfigPath.READ_TIMEOUT);

        this.endpoints = new TreeSet<>();

        final Config staticsCfg = ConfigTools.sureConf(config, "fair.http.statics");
        final String dir = staticsCfg != null && staticsCfg.hasPath("root") && !staticsCfg.getIsNull("root") ? notNull(staticsCfg.getString("root")) : null;

        Function<String, Response> assets0 = new NoStatics();

        try {
            if (!isEmpty(dir)) {
                if (dir.startsWith(PlaceHolder))
                    assets0 = new BundledRead(staticsCfg, dir);
                else {
                    final Path p = Paths.get(dir);
                    if (Files.exists(p) && Files.isDirectory(p))
                        assets0 = new DirectRead(staticsCfg, dir);
                }
            } else
                logger.debug("No statics: dir is empty `" + dir + "`");
        } catch (final IllegalStateException ise) {
            logger.error("Cant setup static assets: " + ise.getMessage() + ", noop.");
            assets0 = new NoStatics();
        }

        assets = assets0;

        cors = new CORS(config);
    }

    public void start() {
        new Thread(() -> {
            try (final ServerSocket socket = new ServerSocket(port)) {
                Socket child;
                do {
                    child = socket.accept();

                    if (child != null)
                        new Handler(child, this, maxRequestBytes, readTimeout).start();
                } while (child != null);
            } catch (final Exception e) {
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

    void handleRequest(final Request request, final Consumer<Response> responseConsumer) {
        boolean hasMatchPath = false;

        final Function<Throwable, Void> errorHandler = t -> {
            responseConsumer.accept(Server.this.errorHandler.apply(t));

            return null;
        };

        for (final Endpoint e : endpoints)
            if (e.match(request.method(), request.path())) {
                CompletableFuture.runAsync(() -> e.call(request)
                        .thenApply(rsp -> cors.wrap(request, rsp))
                        .thenAccept(responseConsumer))
                        .exceptionally(errorHandler);

                return;
            } else if (!hasMatchPath && e.pathMatch(request.path()))
                hasMatchPath = true;

        if (hasMatchPath && request.method().equals("OPTIONS")) {
            responseConsumer.accept(cors.wrap(request, Response.NoContent()));
            return;
        }

        if (request.method().equals("GET"))
            CompletableFuture.runAsync(() -> responseConsumer.accept(assets.apply(request.uri()))).exceptionally(errorHandler);
        else
            responseConsumer.accept(Response.NotFound());
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

    public synchronized boolean addEndpoint(final String method, final String endpoint, final BiFunction<Request, Map<String, String>, CompletionStage<Response>> callback) {
        return endpoints.add(new Endpoint(method, endpoint, callback));
    }

    public void setupErrorHandler(final Function<Throwable, Response> errorHandler) {
        if (errorHandler != null)
            synchronized (this) {
                this.errorHandler = errorHandler;
            }
    }

    public Response errorAsResponse(final String error) {
        return errorHandler.apply(new Throwable(error));
    }

    public Response errorAsResponse(final Throwable t) {
        if (t == null)
            return Response.ServerError();

        return errorHandler.apply(t);
    }
}
