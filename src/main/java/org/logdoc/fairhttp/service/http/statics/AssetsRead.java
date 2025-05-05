package org.logdoc.fairhttp.service.http.statics;

import org.logdoc.fairhttp.service.http.Response;

import java.util.function.Function;

/**
 * @author Denis Danilin | me@loslobos.ru
 * 22.03.2024 13:33
 * fair-http-server ☭ sweat and blood
 */
public interface AssetsRead extends Function<String, Response> {
    boolean canProcess(String path);
}
