package org.logdoc.fairhttp.service.http;

import com.typesafe.config.Config;
import org.logdoc.fairhttp.service.api.helpers.Endpoint;
import org.logdoc.fairhttp.service.api.helpers.endpoint.Route;
import org.logdoc.fairhttp.service.api.helpers.endpoint.Signature;
import org.logdoc.fairhttp.service.api.helpers.endpoint.invokers.*;
import org.logdoc.fairhttp.service.http.statics.AssetsRead;
import org.logdoc.fairhttp.service.http.statics.NoStatics;
import org.logdoc.fairhttp.service.tools.ConfigTools;
import org.logdoc.fairhttp.service.tools.ResourceConnect;
import org.logdoc.helpers.gears.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.Inet4Address;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Function;
import java.util.stream.Collectors;

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
    private final SortedSet<Route> routes;
    private final int port, maxRequestBytes;
    private final int readTimeoutMs, execTimeoutSeconds;
    private final AssetsRead assets;
    private final CORS cors;
    private final Map<Integer, String> maps;
    private final ExecutorService executorService;

    private Function<Throwable, Response> errorHandler;

    public Server(final int port, final int maxRequestBytes, final int readTimeoutMs, final int execTimeoutSeconds, final CORS cors, final AssetsRead assets) {
        executorService = new ThreadPoolExecutor(
                Runtime.getRuntime().availableProcessors(),     // Core pool size
                Runtime.getRuntime().availableProcessors() * 2, // Max pool size
                60L, TimeUnit.SECONDS,                          // Keep-alive time
                new LinkedBlockingQueue<>(1000),        // Work queue
                new ThreadPoolExecutor.CallerRunsPolicy()       // Rejection policy
        );
        routes = new TreeSet<>();
        maps = new HashMap<>(0);

        this.port = port;
        this.maxRequestBytes = maxRequestBytes;
        this.readTimeoutMs = readTimeoutMs;
        this.execTimeoutSeconds = execTimeoutSeconds;
        this.cors = cors;
        this.assets = assets;
        errorHandler = throwable -> {
            if (throwable != null)
                logger.error(throwable.getMessage(), throwable);

            return throwable == null ? Response.ServerError() : Response.ServerError(throwable.getMessage());
        };
    }

    public Server(final int port, final int maxRequestBytes) { // minimal
        this(port, maxRequestBytes, 15000, 180, new CORS(null), new NoStatics());
    }

    public Server(final Config config) {
        this(
                config.getInt("fair.http.port"),
                config.getBytes("fair.http.max_request_body").intValue(),
                config.getInt("fair.http.request_read_timeout_ms"),
                config.getInt("fair.http.handler_exec_timeout_sec"),
                new CORS(ConfigTools.sureConf(config, "fair.http.cors")),
                AssetsRead.ofConfig(ConfigTools.sureConf(config, "fair.http.statics"))
        );

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
    }

    public void start() {
        new Thread(() -> {
            try (final ServerSocket socket = new ServerSocket(port)) {
                logger.info("Listen at:\thttp://" + Inet4Address.getLocalHost().getHostAddress() + ":" + socket.getLocalPort());

                Socket child;

                while ((child = socket.accept()) != null)
                    new RCWrap(child, maxRequestBytes, readTimeoutMs, this);
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
        final Iterator<Route> i = routes.iterator();
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
        final Iterator<Route> i = routes.iterator();
        Pair<Boolean, Boolean> match;

        Response mappableResponse = null;
        Route e;

        while (i.hasNext()) {
            match = (e = i.next()).match(id.method, id.path);

            if (match.first && match.second) {
                mappableResponse = e.call(new Request(id, headers, rc.getInput(), maxRequestBytes));
                break;
            }

            if (match.second && id.method.equals("OPTIONS")) {
                mappableResponse = Response.NoContent();
                break;
            }
        }

        if (mappableResponse == null) {
            if (id.method.equals("GET"))
                mappableResponse = assets.apply(id.path);

            if (mappableResponse == null)
                mappableResponse = Response.NotFound();
        }

        if (mayBeMapped && maps.containsKey(mappableResponse.getCode())) {
            handleRequest0(new RequestId(id.method, maps.get(mappableResponse.getCode())), headers, rc, false);
            return;
        }

        writeResponse(cors.wrap(headers, mappableResponse), rc);
    }

    private void writeResponse(final Response response, final ResourceConnect rc) {
        CompletableFuture.runAsync(() -> rc.write(response));
    }

    public void addEndpoints(final Collection<Endpoint> endpoints) {
        for (final Endpoint pretend : endpoints)
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
                                ? new DirectUnresolvingInvoker(argued.invMethod, errorHandler, execTimeoutSeconds)
                                : new IndirectUnresolvingInvoker(argued.invMethod, errorHandler, execTimeoutSeconds);
                    } else {
                        invoker = direct
                                ? new DirectInvoker(argued.invMethod, Collections.unmodifiableList(argued.args.stream().map(arg -> arg.magic).collect(Collectors.toList())), errorHandler, execTimeoutSeconds)
                                : new IndirectInvoker(argued.invMethod, Collections.unmodifiableList(argued.args.stream().map(arg -> arg.magic).collect(Collectors.toList())), errorHandler, execTimeoutSeconds);
                    }

                    final Route ep = new Route(argued.method, new Signature(argued.path), invoker);
                    if (routes.add(ep))
                        logger.info("Added endpoint: " + ep);
                });
    }

    public synchronized boolean removeEndpoint(final String method, final String signature) {
        return routes.removeIf(e -> e.equals(method, signature));
    }

    @SuppressWarnings({"unchecked", "UnusedReturnValue"})
    public synchronized void addEndpoint(final Endpoint endpoint) {
        if (routes.add(new Route(endpoint.method, new Signature(endpoint.endpoint),
                endpoint.indirect
                        ? (req, pathMap) -> {
                    try {
                        return CompletableFuture.supplyAsync(() -> {
                                    if (endpoint.shouldBreak(req, pathMap))
                                        return endpoint.breakWithResponse;

                                    try {
                                        return ((CompletionStage<Response>) endpoint.callback.apply(req, pathMap)).toCompletableFuture().get(execTimeoutSeconds, TimeUnit.SECONDS);
                                    } catch (final InterruptedException | ExecutionException | TimeoutException e) {
                                        throw new RuntimeException(e);
                                    }
                                })
                                .exceptionally(e -> {
                                    if (e instanceof RuntimeException)
                                        throw (RuntimeException) e;

                                    throw new RuntimeException(e);
                                })
                                .get(execTimeoutSeconds, TimeUnit.SECONDS);
                    } catch (final Exception ex) {
                        return errorHandler.apply(ex);
                    }
                }
                        : (req, pathMap) -> {
                    try {
                        return CompletableFuture.supplyAsync(() -> {
                                    if (endpoint.shouldBreak(req, pathMap))
                                        return endpoint.breakWithResponse;

                                    return ((Response) endpoint.callback.apply(req, pathMap));
                                })
                                .exceptionally(e -> {
                                    if (e instanceof RuntimeException)
                                        throw (RuntimeException) e;

                                    throw new RuntimeException(e);
                                })
                                .get(execTimeoutSeconds, TimeUnit.SECONDS);
                    } catch (final Exception ex) {
                        return errorHandler.apply(ex);
                    }
                })))
            logger.info("Added endpoint: " + endpoint.method + "\t" + endpoint.endpoint);
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
