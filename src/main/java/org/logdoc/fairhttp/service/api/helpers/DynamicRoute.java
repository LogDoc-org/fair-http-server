package org.logdoc.fairhttp.service.api.helpers;

import org.logdoc.fairhttp.service.http.Http;

import java.util.List;
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
    public final List<String> pathVarsNames;
    public final BiFunction<Http.Request, Map<String, String>, Http.Response> callback;
    public final BiFunction<Http.Request, Map<String, String>, CompletionStage<Http.Response>> asyncCallback;

    /**
     * One synced endpoint
     * @param method HTTP method (GET, POST, PUT, etc)
     * @param endpoint Fully qualified endpoint. If its patterned - it must be Java RegEx synthax compliant and groups must be correctly mappable to callback arguments
     * @param pathVarsNames If and only if endpoint is patterned - it must contains same number of names as number of the arguments, to which values from path will be mapped for invocation of each request
     * @param callback invocation of request with mapped path variables by names given in pathVarsNames
     * @return Fully constructed Route to endpoint
     */
    public static DynamicRoute sync(final String method, final String endpoint, final List<String> pathVarsNames, final BiFunction<Http.Request, Map<String, String>, Http.Response> callback) {
        return new DynamicRoute(method, endpoint, pathVarsNames, callback, null);
    }

    /**
     * One async endpoint
     * @param method HTTP method (GET, POST, PUT, etc)
     * @param endpoint Fully qualified endpoint. If its patterned - it must be Java RegEx synthax compliant and groups must be correctly mappable to callback arguments
     * @param pathVarsNames List of path variables names. If and only if endpoint is patterned - it must contains same number of names as number of the arguments, to which values from path will be mapped for invocation of each request
     * @param callback invocation of request with mapped path variables by names given in pathVarsNames
     * @return Fully constructed Route to endpoint
     */
    public static DynamicRoute async(final String method, final String endpoint, final List<String> pathVarsNames, final BiFunction<Http.Request, Map<String, String>, CompletionStage<Http.Response>> callback) {
        return new DynamicRoute(method, endpoint, pathVarsNames, null, callback);
    }

    private DynamicRoute(final String method, final String endpoint, final List<String> pathVarsNames, final BiFunction<Http.Request, Map<String, String>, Http.Response> callback, final BiFunction<Http.Request, Map<String, String>, CompletionStage<Http.Response>> asyncCallback) {
        if (isEmpty(method))
            throw new NullPointerException("Method is not defined");

        if (callback == null && asyncCallback == null)
            throw new NullPointerException("Callback is not defined");

        this.method = notNull(method);
        this.endpoint = isEmpty(endpoint) ? "/" : notNull(endpoint);
        this.pathVarsNames = pathVarsNames;
        this.callback = callback;
        this.asyncCallback = asyncCallback;
    }
}
