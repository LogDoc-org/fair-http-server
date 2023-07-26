package org.logdoc.fairhttp.service.tools;

import org.logdoc.fairhttp.service.http.Response;

/**
 * @author Denis Danilin | me@loslobos.ru
 * 23.06.2023 12:51
 * fair-http-server ☭ sweat and blood
 */
public interface ErrorHandler {
    Response handle(Throwable t);
}
