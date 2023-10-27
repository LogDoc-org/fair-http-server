package org.logdoc.fairhttp.service.api.helpers.aop;

import org.logdoc.fairhttp.service.http.Request;
import org.logdoc.fairhttp.service.http.Response;

import java.util.function.BiFunction;

/**
 * @author Denis Danilin | me@loslobos.ru
 * 20.09.2023 13:26
 * fair-http-server ☭ sweat and blood
 */
public abstract class Post implements BiFunction<Request, Response, Response> {
    @Override
    public Response apply(final Request request, final Response response) {
        final Response mod = handle(request, response);
        return mod == null ? response : mod;
    }

    protected abstract Response handle(final Request request, final Response response);
}
