package org.logdoc.fairhttp.service.http;

import com.typesafe.config.Config;
import org.logdoc.fairhttp.service.api.helpers.Route;
import org.logdoc.fairhttp.service.api.helpers.aop.Post;
import org.logdoc.fairhttp.service.api.helpers.aop.Pre;
import org.logdoc.fairhttp.service.api.helpers.endpoint.Endpoint;
import org.logdoc.fairhttp.service.api.helpers.endpoint.Signature;
import org.logdoc.fairhttp.service.api.helpers.endpoint.invokers.*;
import org.logdoc.fairhttp.service.http.statics.BundledRead;
import org.logdoc.fairhttp.service.http.statics.DirectRead;
import org.logdoc.fairhttp.service.http.statics.NoStatics;
import org.logdoc.fairhttp.service.tools.ConfigPath;
import org.logdoc.fairhttp.service.tools.ConfigTools;
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
import java.util.function.BiPredicate;
import java.util.function.Consumer;
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
public class Server {
    private static final Logger logger = LoggerFactory.getLogger(Server.class);
    private static Server backRef = null;
    private final SortedSet<Endpoint> endpoints;
    private final int port, maxRequestBytes;
    private final int readTimeout, execTimeout;
    private final Function<String, Response> assets;
    private final CORS cors;
    private final Map<Integer, String> maps;
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
        handleRequest0(request, responseConsumer, !maps.isEmpty());
    }

    private void handleRequest0(final Request request, final Consumer<Response> responseConsumer, final boolean mayBeMapped) {
        Response mappableResponse = null;

        try {
            for (final Endpoint e : endpoints) {
                final Pair<Boolean, Boolean> ms = e.match(request.method(), request.path());

                if (ms.first && ms.second) {
                    mappableResponse = e.call(request);
                    break;
                } else if (ms.second && request.method().equals("OPTIONS")) {
                    responseConsumer.accept(cors.wrap(request, Response.NoContent()));
                    return;
                }
            }
        } catch (final ConcurrentModificationException cme) {
            synchronized (endpoints) { // someone added/removed endpoint, just try to repeat op over fixed endpoints // todo refactor it
                handleRequest0(request, responseConsumer, mayBeMapped);
                return;
            }
        }

        if (mappableResponse == null) {
            if (request.method().equals("GET"))
                mappableResponse = assets.apply(request.uri());

            if (mappableResponse == null)
                mappableResponse = Response.NotFound();
        }

        if (mayBeMapped && maps.containsKey(mappableResponse.code)) {
            handleRequest0(request.remap(maps.get(mappableResponse.code)), responseConsumer, false);
            return;
        }

        responseConsumer.accept(mappableResponse);
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
        for (final Endpoint e : endpoints)
            if (e.equals(method, signature)) {
                endpoints.remove(e);
                return true;
            }

        return false;
    }

    public synchronized void removePre(final BiPredicate<String, String> methodSignaturePredicate, final Pre filter) {
        if (methodSignaturePredicate == null || filter == null)
            return;

        changeChain(methodSignaturePredicate, e -> e.removePre(filter));
    }

    public synchronized void removePost(final BiPredicate<String, String> methodSignaturePredicate, final Post filter) {
        if (methodSignaturePredicate == null || filter == null)
            return;

        changeChain(methodSignaturePredicate, e -> e.removePost(filter));
    }

    public synchronized void addFirstPre(final BiPredicate<String, String> methodSignaturePredicate, final Pre filter) {
        if (methodSignaturePredicate == null || filter == null)
            return;

        changeChain(methodSignaturePredicate, e -> e.addFirstPre(filter));
    }

    public synchronized void addLastPre(final BiPredicate<String, String> methodSignaturePredicate, final Pre filter) {
        if (methodSignaturePredicate == null || filter == null)
            return;

        changeChain(methodSignaturePredicate, e -> e.addLastPre(filter));
    }

    public synchronized void addFirstPost(final BiPredicate<String, String> methodSignaturePredicate, final Post filter) {
        if (methodSignaturePredicate == null || filter == null)
            return;

        changeChain(methodSignaturePredicate, e -> e.addFirstPost(filter));
    }

    public synchronized void addLastPost(final BiPredicate<String, String> methodSignaturePredicate, final Post filter) {
        if (methodSignaturePredicate == null || filter == null)
            return;

        changeChain(methodSignaturePredicate, e -> e.addLastPost(filter));
    }

    private synchronized void changeChain(final BiPredicate<String, String> methodSignaturePredicate, final Consumer<Endpoint> consumer) {
        endpoints.stream()
                .filter(b -> methodSignaturePredicate.test(b.method(), b.signature()))
                .forEach(consumer);
    }

    @SuppressWarnings({"unchecked", "UnusedReturnValue"})
    public synchronized boolean addEndpoint(final Route endpoint) {
        Endpoint ep;

        if (endpoints.add((ep = new Endpoint(endpoint.method, new Signature(endpoint.endpoint),
                endpoint.indirect
                        ? (req, pathMap) -> {
                    try {
                        return CompletableFuture.supplyAsync(() -> {
                                    try {
                                        return ((CompletionStage<Response>) endpoint.callback.apply(req, pathMap)).toCompletableFuture().get(execTimeout, TimeUnit.SECONDS);
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
                        return CompletableFuture.supplyAsync(() -> ((Response) endpoint.callback.apply(req, pathMap)))
                                .exceptionally(e -> {
                                    if (e instanceof RuntimeException)
                                        throw (RuntimeException) e;

                                    throw new RuntimeException(e);
                                })
                                .get(execTimeout, TimeUnit.SECONDS);
                    } catch (final Exception ex) {
                        return errorHandler.apply(ex);
                    }
                })))) {
            logger.info("Added endpoint: " + ep);

            return true;
        } else
            return false;
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
