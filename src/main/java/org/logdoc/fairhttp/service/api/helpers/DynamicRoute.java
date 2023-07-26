package org.logdoc.fairhttp.service.api.helpers;

import org.logdoc.fairhttp.service.http.Request;
import org.logdoc.fairhttp.service.http.Response;

import java.util.Map;
import java.util.concurrent.CompletionStage;
import java.util.function.BiFunction;

import static org.logdoc.helpers.Texts.isEmpty;
import static org.logdoc.helpers.Texts.notNull;

/**
 * @author Denis Danilin | me@loslobos.ru
 * 01.03.2023 14:27
 * FairHttpService ☭ sweat and blood
 */
public class DynamicRoute {
    public final String method;
    public final String endpoint;
    public final BiFunction<Request, Map<String, String>, CompletionStage<Response>> callback;

    /**
     * @param method   HTTP method (GET, POST, PUT, etc)
     * @param endpoint Fully qualified endpoint. If its patterned - it must be Java RegEx synthax compliant and groups must be correctly mappable to callback arguments
     * @param callback invocation of request with mapped path variables by names given in pathVarsNames
     */
    public DynamicRoute(final String method, final String endpoint, final BiFunction<Request, Map<String, String>, CompletionStage<Response>> callback) {
        if (isEmpty(method))
            throw new NullPointerException("Method is not defined");

        this.method = notNull(method);
        this.endpoint = isEmpty(endpoint) ? "/" : notNull(endpoint);
        this.callback = callback;
    }
}
