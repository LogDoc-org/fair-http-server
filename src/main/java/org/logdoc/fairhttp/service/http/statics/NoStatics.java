package org.logdoc.fairhttp.service.http.statics;

import org.logdoc.fairhttp.service.http.Response;

/**
 * @author Denis Danilin | me@loslobos.ru
 * 04.03.2023 13:29
 * fair-http-server ☭ sweat and blood
 */
public class NoStatics implements AssetsRead {
    @Override
    public Response apply(final String s) {
        return Response.NotFound();
    }

    @Override
    public boolean canProcess(String path) {
        return false;
    }
}
