package org.logdoc.fairhttp.service.api.helpers.endpoint.invokers;

import org.logdoc.fairhttp.service.DI;
import org.logdoc.fairhttp.service.http.Http;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * @author Denis Danilin | me@loslobos.ru
 * 23.05.2023 13:10
 * fair-http-server ☭ sweat and blood
 */
public class RequestOnlyInvoker implements RequestInvoker {
    private final Method method;

    public RequestOnlyInvoker(final Method method) {
        this.method = method;
    }

    @Override
    public CompletionStage<Http.Response> apply(final Http.Request request, final Map<String, String> map) {
        try {
            return CompletableFuture.completedFuture((Http.Response) method.invoke(DI.gain(method.getDeclaringClass()), request));
        } catch (final Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }
}
