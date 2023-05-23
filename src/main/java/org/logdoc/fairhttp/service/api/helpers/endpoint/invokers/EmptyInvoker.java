package org.logdoc.fairhttp.service.api.helpers.endpoint.invokers;

import org.logdoc.fairhttp.service.DI;
import org.logdoc.fairhttp.service.http.Http;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * @author Denis Danilin | me@loslobos.ru
 * 23.05.2023 12:50
 * fair-http-server ☭ sweat and blood
 */
public class EmptyInvoker implements RequestInvoker {
    private final Method method;

    public EmptyInvoker(final Method method) {
        this.method = method;
    }

    @Override
    public CompletionStage<Http.Response> apply(final Http.Request request, final Map<String, String> map) {
        try {
            return CompletableFuture.completedFuture((Http.Response) method.invoke(DI.gain(method.getDeclaringClass())));
        } catch (final Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }
}
