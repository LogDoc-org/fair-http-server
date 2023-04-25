package org.logdoc.fairhttp.service.tools;

/**
 * @author Denis Danilin | me@loslobos.ru
 * 04.03.2023 13:09
 * fair-http-server ☭ sweat and blood
 */
public interface ConfigPath {
    String PORT = "fair.http.port";
    String CORS = "fair.http.cors";
    String CORS_ORIGINS = "origins";
    String CORS_METHODS = "methods";
    String CORS_HEADERS = "headers";
    String CORS_CREDS = "allow_credentials";
}
