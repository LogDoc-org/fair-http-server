package org.logdoc.fairhttp.service.api.helpers.endpoint.invokers;

import org.logdoc.fairhttp.service.DI;
import org.logdoc.fairhttp.service.http.Http;
import org.logdoc.fairhttp.service.tools.Json;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * @author Denis Danilin | me@loslobos.ru
 * 23.05.2023 13:15
 * fair-http-server ☭ sweat and blood
 */
public class BodyRequestInvoker implements RequestInvoker {
    private final Method method;
    private final Class<?> targetCls;

    public BodyRequestInvoker(final Method method, final Class<?> targetCls) {
        this.method = method;
        this.targetCls = targetCls;
    }

    @Override
    public CompletionStage<Http.Response> apply(final Http.Request request, final Map<String, String> map) {
        try {
            return CompletableFuture.completedFuture((Http.Response) method.invoke(DI.gain(method.getDeclaringClass()), Json.fromJson(request.bodyAsJson(), targetCls), request));
        } catch (final Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }
}
