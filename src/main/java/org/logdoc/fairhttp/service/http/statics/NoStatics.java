package org.logdoc.fairhttp.service.http.statics;

import org.logdoc.fairhttp.service.http.Http;

import java.util.function.Function;

/**
 * @author Denis Danilin | me@loslobos.ru
 * 04.03.2023 13:29
 * fair-http-server ☭ sweat and blood
 */
public class NoStatics implements Function<String, Http.Response> {
    @Override
    public Http.Response apply(final String s) {
        return Http.Response.NotFound();
    }
}
