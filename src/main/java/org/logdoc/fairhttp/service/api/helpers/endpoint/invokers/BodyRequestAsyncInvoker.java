package org.logdoc.fairhttp.service.api.helpers.endpoint.invokers;

import org.logdoc.fairhttp.service.DI;
import org.logdoc.fairhttp.service.http.Request;
import org.logdoc.fairhttp.service.http.Response;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * @author Denis Danilin | me@loslobos.ru
 * 23.05.2023 16:32
 * fair-http-server ☭ sweat and blood
 */
public class BodyRequestAsyncInvoker implements RequestInvoker {
    private final Method method;
    private final Class<?> targetCls;

    public BodyRequestAsyncInvoker(final Method method, final Class<?> targetCls) {
        this.method = method;
        this.targetCls = targetCls;
    }

    @SuppressWarnings("unchecked")
    @Override
    public CompletionStage<Response> apply(final Request request, final Map<String, String> map) {
        try {
            return (CompletionStage<Response>) method.invoke(DI.gain(method.getDeclaringClass()), request.body().map(targetCls), request);
        } catch (final Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }
}
