package org.logdoc.fairhttp.service.tools;

import org.logdoc.fairhttp.service.http.Http;

/**
 * @author Denis Danilin | me@loslobos.ru
 * 23.06.2023 12:51
 * fair-http-server ☭ sweat and blood
 */
public interface ErrorHandler {
    Http.Response handle(Throwable t);
}
