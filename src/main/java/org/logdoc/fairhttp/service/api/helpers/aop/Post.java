package org.logdoc.fairhttp.service.api.helpers.aop;

import org.logdoc.fairhttp.service.http.Response;

import java.util.function.Function;

/**
 * @author Denis Danilin | me@loslobos.ru
 * 20.09.2023 13:26
 * fair-http-server ☭ sweat and blood
 */
public abstract class Post implements Function<Response, Response> {
    @Override
    public Response apply(final Response response) {
        final Response mod = handle(response);
        return mod == null ? response : mod;
    }

    protected abstract Response handle(final Response response);
}
