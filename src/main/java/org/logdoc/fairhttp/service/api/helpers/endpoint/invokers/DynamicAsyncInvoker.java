package org.logdoc.fairhttp.service.api.helpers.endpoint.invokers;

import com.fasterxml.jackson.databind.JsonNode;
import org.logdoc.fairhttp.service.DI;
import org.logdoc.fairhttp.service.http.Http;
import org.logdoc.fairhttp.service.http.Request;
import org.logdoc.fairhttp.service.http.Response;
import org.logdoc.fairhttp.service.tools.Form;
import org.logdoc.fairhttp.service.tools.MultiForm;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * @author Denis Danilin | me@loslobos.ru
 * 23.05.2023 14:24
 * fair-http-server ☭ sweat and blood
 */
public class DynamicAsyncInvoker implements RequestInvoker {
    private final List<ArgumentDefinition> args;
    private final Method targetMethod;

    public DynamicAsyncInvoker(final Method targetMethod, final List<ArgumentDefinition> args) {
        this.args = args;
        this.targetMethod = targetMethod;
    }

    @SuppressWarnings("unchecked")
    @Override
    public CompletionStage<Response> apply(final Request request, final Map<String, String> pathMap) {
        final Map<String, String> query = request.queryMap(), cookies = request.cookies();
        final Form form = request.body().asForm();
        final MultiForm multiForm;
        try {
            multiForm = request.body().asMultipart();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        final JsonNode json = request.body().asJson();

        try {
            final Object[] params = new Object[args.size()];

            int i = 0;
            for (final ArgumentDefinition ad : args)
                params[i++] = ad.resolve(query, cookies, form, multiForm, pathMap, json, request);

            return (CompletionStage<Response>) targetMethod.invoke(DI.gain(targetMethod.getDeclaringClass()), params);
        } catch (final Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }
}
