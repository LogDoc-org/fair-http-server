package org.logdoc.fairhttp.service.tools;

/**
 * @author Denis Danilin | me@loslobos.ru
 * 04.03.2023 13:09
 * fair-http-server ☭ sweat and blood
 */
public interface ConfigPath {
    String PORT = "fair.http.port";
    String MAX_REQUEST = "fair.http.max_request_body";
    String READ_TIMEOUT = "fair.http.request_read_timeout_ms";
    String CORS = "fair.http.cors";
    String CORS_ORIGINS = "origins";
    String CORS_METHODS = "methods";
    String CORS_HEADERS = "headers";
    String CORS_EXPOSE = "expose";
    String CORS_CREDS = "allow_credentials";
}
