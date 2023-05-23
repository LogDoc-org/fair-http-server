package org.logdoc.fairhttp.service.api.helpers;

import org.logdoc.fairhttp.service.http.Http;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.CompletionStage;
import java.util.function.BiFunction;

/**
 * @author Denis Danilin | me@loslobos.ru
 * 04.03.2023 13:15
 * fair-http-server ☭ sweat and blood
 */
public interface FairHttpServer {

    void setupDynamicEndpoints(Collection<DynamicRoute> routes);

    void setupConfigEndpoints(Collection<String> raw);

    boolean removeEndpoint(String method, String signature);

    boolean addEndpoint(String method, String endpoint, BiFunction<Http.Request, Map<String, String>, CompletionStage<Http.Response>> callback);
}
