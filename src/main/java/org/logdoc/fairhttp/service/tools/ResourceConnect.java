package org.logdoc.fairhttp.service.tools;

import org.logdoc.fairhttp.service.http.Response;

import java.net.Socket;

/**
 * @author Denis Danilin | me@loslobos.ru
 * 20.03.2024 11:41
 * fair-http-server ☭ sweat and blood
 */
public interface ResourceConnect {
    void write(byte[] data);

    void write(Response response);

    Socket getInput();
}
