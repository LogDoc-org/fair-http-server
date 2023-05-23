package org.logdoc.fairhttp.service.api.helpers.endpoint.invokers;

import org.logdoc.fairhttp.service.http.Http;

import java.util.Map;
import java.util.concurrent.CompletionStage;
import java.util.function.BiFunction;

/**
 * @author Denis Danilin | me@loslobos.ru
 * 23.05.2023 12:51
 * fair-http-server ☭ sweat and blood
 */
public interface RequestInvoker extends BiFunction<Http.Request, Map<String, String>, CompletionStage<Http.Response>> {

}
