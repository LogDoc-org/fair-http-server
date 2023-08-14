package org.logdoc.fairhttp.service.api.helpers.endpoint.invokers;

import org.logdoc.fairhttp.service.http.Request;
import org.logdoc.fairhttp.service.http.Response;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * @author Denis Danilin | me@loslobos.ru
 * 12.08.2023 17:54
 * fair-http-server ☭ sweat and blood
 */
public abstract class ARequestInvoker implements BiFunction<Request, Map<String, String>, Response> {
    protected final Method method;
    protected final Function<Throwable, Response> errorHandler;
    protected final int execTimeout;

    protected ARequestInvoker(final Method method, final Function<Throwable, Response> errorHandler, final int execTimeout) {
        this.method = method;
        this.errorHandler = errorHandler;
        this.execTimeout = execTimeout;
    }

    @Override
    public final Response apply(final Request request, final Map<String, String> pathMap) {
        try {
            return CompletableFuture.supplyAsync(supplyAction(request, pathMap))
                    .exceptionally(e -> {
                        if (e instanceof RuntimeException)
                            throw (RuntimeException) e;

                        throw new RuntimeException(e);
                    })
                    .get(execTimeout, TimeUnit.SECONDS);
        } catch (final Exception e) {
            return errorHandler.apply(e);
        }
    }

    protected abstract Supplier<Response> supplyAction(final Request request, final Map<String, String> pathMap);
}
