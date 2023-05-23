package org.logdoc.fairhttp.service.api.helpers.endpoint.invokers;

import com.fasterxml.jackson.databind.JsonNode;
import org.logdoc.fairhttp.service.DI;
import org.logdoc.fairhttp.service.http.Http;
import org.logdoc.fairhttp.service.tools.Form;
import org.logdoc.fairhttp.service.tools.MultiForm;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * @author Denis Danilin | me@loslobos.ru
 * 23.05.2023 13:45
 * fair-http-server ☭ sweat and blood
 */
public class DynamicInvoker implements RequestInvoker {
    private final List<ArgumentDefinition> args;
    private final Method targetMethod;

    public DynamicInvoker(final Method targetMethod, final List<ArgumentDefinition> args) {
        this.args = args;
        this.targetMethod = targetMethod;
    }

    @Override
    public CompletionStage<Http.Response> apply(final Http.Request request, final Map<String, String> pathMap) {
        final Map<String, String> query = request.queryMap(), cookies = request.cookieMap();
        final Form form = request.bodyAsForm();
        final MultiForm multiForm = request.bodyAsMultipart();
        final JsonNode json = request.bodyAsJson();

        try {
            final Object[] params = new Object[args.size()];

            int i = 0;
            for (final ArgumentDefinition ad : args)
                params[i++] = ad.resolve(query, cookies, form, multiForm, pathMap, json, request);

            return CompletableFuture.completedFuture((Http.Response) targetMethod.invoke(DI.gain(targetMethod.getDeclaringClass()), params));
        } catch (final Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }
}
