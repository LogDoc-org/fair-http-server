package org.logdoc.fairhttp.service.http;

import com.typesafe.config.Config;
import org.logdoc.fairhttp.service.api.helpers.Route;
import org.logdoc.fairhttp.service.api.helpers.endpoint.Endpoint;
import org.logdoc.fairhttp.service.api.helpers.endpoint.Signature;
import org.logdoc.fairhttp.service.api.helpers.endpoint.invokers.*;
import org.logdoc.fairhttp.service.http.statics.AssetsRead;
import org.logdoc.fairhttp.service.http.statics.BundledRead;
import org.logdoc.fairhttp.service.http.statics.DirectRead;
import org.logdoc.fairhttp.service.http.statics.NoStatics;
import org.logdoc.fairhttp.service.tools.ConfigPath;
import org.logdoc.fairhttp.service.tools.ConfigTools;
import org.logdoc.fairhttp.service.tools.ResourceConnect;
import org.logdoc.helpers.gears.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.Inet4Address;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.logdoc.fairhttp.service.http.statics.BundledRead.PlaceHolder;
import static org.logdoc.helpers.Digits.getInt;
import static org.logdoc.helpers.Texts.isEmpty;
import static org.logdoc.helpers.Texts.notNull;

/**
 * @author Denis Danilin | me@loslobos.ru
 * 03.02.2023 17:39
 * FairHttpService ☭ sweat and blood
 */
public class Server implements RCBackup {
    private static final Logger logger = LoggerFactory.getLogger(Server.class);
    private static Server backRef = null;
    private final SortedSet<Endpoint> endpoints;
    private final int port, maxRequestBytes;
    private final int readTimeout, execTimeout;
    private final AssetsRead assets;
    private final CORS cors;
    private final Map<Integer, String> maps;
    private final ExecutorService executorService;
    private Function<Throwable, Response> errorHandler;

    public Server(final int port, final int maxRequestBytes) { // minimal
        this.port = port;
        this.maxRequestBytes = maxRequestBytes;
        this.readTimeout = 15000;
        this.execTimeout = 180;
        this.endpoints = new TreeSet<>();
        maps = new HashMap<>(0);

        assets = new NoStatics();
        cors = new CORS(null);

        errorHandler = throwable -> {
            if (throwable != null)
                logger.error(throwable.getMessage(), throwable);

            return throwable == null ? Response.ServerError() : Response.ServerError(throwable.getMessage());
        };

        executorService = Executors.newCachedThreadPool();
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
        execTimeout = config.getInt(ConfigPath.EXEC_TIMEOUT);
        maps = new HashMap<>(0);

        this.endpoints = new TreeSet<>();

        final Config staticsCfg = ConfigTools.sureConf(config, "fair.http.statics");
        final String dir = staticsCfg != null && staticsCfg.hasPath("root") && !staticsCfg.getIsNull("root") ? notNull(staticsCfg.getString("root")) : null;

        AssetsRead assets0 = new NoStatics();

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

        if (config.hasPath("fair.http"))
            try {
                config.getConfig("fair.http").root().unwrapped()
                        .forEach((s, o) -> {
                            if (s.startsWith("map") && s.endsWith("_to") && !isEmpty(o))
                                maps.put(getInt(s), notNull(o));
                        });

                maps.remove(0);

                if (!maps.isEmpty()) {
                    final Set<Integer> codes = maps.keySet();

                    for (final int code : codes) {
                        final int l = String.valueOf(code).length();

                        if (l > 3)
                            maps.remove(code);
                        else if (l < 3) {
                            final String mapping = maps.remove(code);

                            final int from = getInt(code + "0".repeat(l == 2 ? 1 : 2));
                            final int untill = l == 1 ? 100 : 10;

                            for (int i = from; i < untill; i++)
                                maps.put(i, mapping);
                        }
                    }
                }
            } catch (final Exception e) {
                logger.error("Cant setup code mappings: " + e.getMessage(), e);
            }

        assets = assets0;

        cors = new CORS(config);

        executorService = Executors.newCachedThreadPool();
    }

    public void start() {
        new Thread(() -> {
            try (final ServerSocket socket = new ServerSocket(port)) {
                logger.info("Listen at:\thttp://" + Inet4Address.getLocalHost().getHostAddress() + ":" + socket.getLocalPort());

                Socket child;

                while ((child = socket.accept()) != null)
                    new RCWrap(child, maxRequestBytes, readTimeout, this);
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
    }

    @Override
    public boolean canProcess(final RequestId id) {
        final Iterator<Endpoint> i = endpoints.iterator();
        Pair<Boolean, Boolean> reply;

        while (i.hasNext()) {
            reply = i.next().match(id.method, id.path);

            if (reply.first && reply.second)
                return true;

            if (reply.second && id.method.equals("OPTIONS"))
                return true;
        }

        return id.method.equals("GET") && (maps.containsKey(404) || assets.canProcess(id.path));
    }

    @Override
    public void handleRequest(final RequestId id, final Map<String, String> headers, final ResourceConnect rc) {
        handleRequest0(id, headers, rc, !maps.isEmpty());
    }

    @Override
    public void meDead(final ResourceConnect rc) {
        //
    }

    @Override
    public void submit(final Runnable task) {
        executorService.submit(task);
    }

    public void handleRequest0(final RequestId id, final Map<String, String> headers, final ResourceConnect rc, final boolean mayBeMapped) {
        final Iterator<Endpoint> i = endpoints.iterator();
        Pair<Boolean, Boolean> match;

        Response mappableResponse = null;
        Endpoint e;

        while (i.hasNext()) {
            match = (e = i.next()).match(id.method, id.path);

            if (match.first && match.second) {
                mappableResponse = e.call(new Request(id, headers, rc.getInput(), maxRequestBytes));
                break;
            }

            if (match.second && id.method.equals("OPTIONS")) {
                mappableResponse = cors.wrap(headers, Response.NoContent());
                break;
            }
        }

        if (mappableResponse == null) {
            if (id.method.equals("GET"))
                mappableResponse = assets.apply(id.path);

            if (mappableResponse == null)
                mappableResponse = Response.NotFound();
        }

        if (mayBeMapped && maps.containsKey(mappableResponse.code)) {
            handleRequest0(new RequestId(id.method, maps.get(mappableResponse.code)), headers, rc, false);
            return;
        }

        writeResponse(mappableResponse, rc);
    }

    private void writeResponse(final Response response, final ResourceConnect rc) {
        CompletableFuture.runAsync(() -> rc.write(response));
    }

    public void addEndpoints(final Collection<Route> endpoints) {
        for (final Route pretend : endpoints)
            addEndpoint(pretend);
    }

    public synchronized void setupConfigEndpoints(final byte[] raw) {
        if (raw == null || raw.length == 0)
            return;

        EndpointResolver.resolve(raw)
                .forEach(argued -> {
                    final boolean unresolving = isEmpty(argued.args);
                    final boolean direct = Response.class.isAssignableFrom(argued.invMethod.getReturnType());

                    final ARequestInvoker invoker;

                    if (unresolving) {
                        invoker = direct
                                ? new DirectUnresolvingInvoker(argued.invMethod, errorHandler, execTimeout)
                                : new IndirectUnresolvingInvoker(argued.invMethod, errorHandler, execTimeout);
                    } else {
                        invoker = direct
                                ? new DirectInvoker(argued.invMethod, Collections.unmodifiableList(argued.args.stream().map(arg -> arg.magic).collect(Collectors.toList())), errorHandler, execTimeout)
                                : new IndirectInvoker(argued.invMethod, Collections.unmodifiableList(argued.args.stream().map(arg -> arg.magic).collect(Collectors.toList())), errorHandler, execTimeout);
                    }

                    final Endpoint ep = new Endpoint(argued.method, new Signature(argued.path), invoker);
                    if (endpoints.add(ep))
                        logger.info("Added endpoint: " + ep);
                });
    }

    public synchronized boolean removeEndpoint(final String method, final String signature) {
        return endpoints.removeIf(e -> e.equals(method, signature));
    }

    @SuppressWarnings({"unchecked", "UnusedReturnValue"})
    public synchronized void addEndpoint(final Route route) {
        if (endpoints.add(new Endpoint(route.method, new Signature(route.endpoint),
                route.indirect
                        ? (req, pathMap) -> {
                    try {
                        return CompletableFuture.supplyAsync(() -> {
                                    if (route.shouldBreak(req, pathMap))
                                        return route.breakWithResponse;

                                    try {
                                        return ((CompletionStage<Response>) route.callback.apply(req, pathMap)).toCompletableFuture().get(execTimeout, TimeUnit.SECONDS);
                                    } catch (final InterruptedException | ExecutionException | TimeoutException e) {
                                        throw new RuntimeException(e);
                                    }
                                })
                                .exceptionally(e -> {
                                    if (e instanceof RuntimeException)
                                        throw (RuntimeException) e;

                                    throw new RuntimeException(e);
                                })
                                .get(execTimeout, TimeUnit.SECONDS);
                    } catch (final Exception ex) {
                        return errorHandler.apply(ex);
                    }
                }
                        : (req, pathMap) -> {
                    try {
                        return CompletableFuture.supplyAsync(() -> {
                                    if (route.shouldBreak(req, pathMap))
                                        return route.breakWithResponse;

                                    return ((Response) route.callback.apply(req, pathMap));
                                })
                                .exceptionally(e -> {
                                    if (e instanceof RuntimeException)
                                        throw (RuntimeException) e;

                                    throw new RuntimeException(e);
                                })
                                .get(execTimeout, TimeUnit.SECONDS);
                    } catch (final Exception ex) {
                        return errorHandler.apply(ex);
                    }
                })))
            logger.info("Added endpoint: " + route.method + "\t" + route.endpoint);
    }

    public synchronized void setupErrorHandler(final Function<Throwable, Response> errorHandler) {
        if (errorHandler != null)
            this.errorHandler = errorHandler;
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
