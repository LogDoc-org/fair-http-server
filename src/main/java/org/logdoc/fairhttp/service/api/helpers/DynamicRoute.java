package org.logdoc.fairhttp.service.api.helpers;

import org.logdoc.fairhttp.service.http.Http;

import java.util.Map;
import java.util.concurrent.CompletionStage;
import java.util.function.BiFunction;

import static org.logdoc.fairhttp.service.tools.Strings.isEmpty;
import static org.logdoc.fairhttp.service.tools.Strings.notNull;

/**
 * @author Denis Danilin | me@loslobos.ru
 * 01.03.2023 14:27
 * FairHttpService ☭ sweat and blood
 */
public class DynamicRoute {
    public final String method;
    public final String endpoint;
    public final BiFunction<Http.Request, Map<String, String>, Http.Response> callback;
    public final BiFunction<Http.Request, Map<String, String>, CompletionStage<Http.Response>> asyncCallback;

    /**
     * One synced endpoint
     * @param method HTTP method (GET, POST, PUT, etc)
     * @param endpoint Fully qualified endpoint. If its patterned - it must be Java RegEx synthax compliant and groups must be correctly mappable to callback arguments
     * @param callback invocation of request with mapped path variables by names given in pathVarsNames
     * @return Fully constructed Route to endpoint
     */
    public static DynamicRoute sync(final String method, final String endpoint, final BiFunction<Http.Request, Map<String, String>, Http.Response> callback) {
        return new DynamicRoute(method, endpoint, callback, null);
    }

    /**
     * One async endpoint
     * @param method HTTP method (GET, POST, PUT, etc)
     * @param endpoint Fully qualified endpoint. If its patterned - it must be Java RegEx synthax compliant and groups must be correctly mappable to callback arguments
     * @param callback invocation of request with mapped path variables by names given in pathVarsNames
     * @return Fully constructed Route to endpoint
     */
    public static DynamicRoute async(final String method, final String endpoint, final BiFunction<Http.Request, Map<String, String>, CompletionStage<Http.Response>> callback) {
        return new DynamicRoute(method, endpoint, null, callback);
    }

    private DynamicRoute(final String method, final String endpoint, final BiFunction<Http.Request, Map<String, String>, Http.Response> callback, final BiFunction<Http.Request, Map<String, String>, CompletionStage<Http.Response>> asyncCallback) {
        if (isEmpty(method))
            throw new NullPointerException("Method is not defined");

        if (callback == null && asyncCallback == null)
            throw new NullPointerException("Callback is not defined");

        this.method = notNull(method);
        this.endpoint = isEmpty(endpoint) ? "/" : notNull(endpoint);
        this.callback = callback;
        this.asyncCallback = asyncCallback;
    }
}
