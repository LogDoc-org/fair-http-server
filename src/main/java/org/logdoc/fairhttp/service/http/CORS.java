package org.logdoc.fairhttp.service.http;

import com.typesafe.config.Config;
import org.logdoc.fairhttp.service.api.helpers.Headers;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import static org.logdoc.fairhttp.service.tools.ConfigPath.CORS;
import static org.logdoc.fairhttp.service.tools.ConfigPath.*;
import static org.logdoc.helpers.Texts.isEmpty;
import static org.logdoc.helpers.Texts.notNull;

/**
 * @author Denis Danilin | me@loslobos.ru
 * 18.04.2023 10:59
 * fair-http-server ☭ sweat and blood
 */
class CORS {
    private static final String OriginReply = "Access-Control-Allow-Origin",
            MethodReply = "Access-Control-Allow-Methods",
            HeadersReply = "Access-Control-Allow-Headers",
            ExposeReply = "Access-Control-Expose-Headers",
            CredsReply = "Access-Control-Allow-Credentials",

    OriginRequest = "Origin",
            MethodRequest = "Access-control-request-method",
            HeadersRequest = "Access-control-request-headers";

    private final String originStr, methodsStr, headersStr, allowCreds, exposeStr;
    private final boolean noWilds, multiOrigins;


    CORS(final Config config) {
        final Config cors = config != null && config.hasPath(CORS) ? config.getConfig(CORS) : null;

        final Set<String> origins = new HashSet<>(), methods = new HashSet<>(), headers = new HashSet<>(), expose = new HashSet<>();
        final boolean allowCreds = cors == null || cors.isEmpty() || !cors.hasPath(CORS_CREDS) || cors.getBoolean(CORS_CREDS);

        if (cors != null && !cors.isEmpty()) {
            setCorsValues(cors, CORS_ORIGINS, origins);
            setCorsValues(cors, CORS_METHODS, methods);
            setCorsValues(cors, CORS_HEADERS, headers);
            setCorsValues(cors, CORS_EXPOSE, expose);
        }

        originStr = notNull(isEmpty(origins) ? "*" : origins.stream().map(String::trim).filter(s -> !isEmpty(s)).collect(Collectors.joining(", ")), "*");
        methodsStr = notNull(isEmpty(methods) ? "*" : methods.stream().map(String::trim).filter(s -> !isEmpty(s)).collect(Collectors.joining(", ")), "*");
        headersStr = notNull(isEmpty(headers) ? "*" : headers.stream().map(String::trim).filter(s -> !isEmpty(s)).collect(Collectors.joining(", ")), "*");
        exposeStr = notNull(isEmpty(expose) ? "*" : expose.stream().map(String::trim).filter(s -> !isEmpty(s)).collect(Collectors.joining(", ")), "*");

        noWilds = !originStr.equals("*") && !methodsStr.equals("*") && !headersStr.equals("*") && !exposeStr.equals("*");
        multiOrigins = originStr.contains(",");

        this.allowCreds = String.valueOf(allowCreds);
    }

    private void setCorsValues(final Config cors, final String path, final Set<String> target) {
        if (cors.hasPath(path))
            try {
                target.addAll(new ArrayList<>(cors.getStringList(path)));
            } catch (final Exception ignore) {
                try {
                    target.add(cors.getString(path));
                } catch (final Exception ignored) {
                }
            }
    }

    Response wrap(final Request request, Response response) {
        if (multiOrigins)
            response.header("Vary", "origin"); // multiple origins must be noted

        if ((!request.hasHeader(Headers.Auth) && !request.hasHeader(Headers.RequestCookies))
                || noWilds // no wildcards
        ) { // particular values or wilds allowed
            return response
                    .withHeader(OriginReply, originStr)
                    .withHeader(MethodReply, methodsStr)
                    .withHeader(HeadersReply, headersStr)
                    .withHeader(ExposeReply, exposeStr)
                    .withHeader(CredsReply, allowCreds);
        }

        if (request.hasHeader(MethodRequest))
            response.header(MethodReply, methodsStr.equals("*") ? request.header(MethodRequest) : methodsStr);

        if (request.hasHeader(HeadersRequest))
            response.header(HeadersReply, headersStr.equals("*") ? request.header(HeadersRequest) : headersStr);

        if (!exposeStr.equals("*"))
            response.header(ExposeReply, exposeStr);

        return response
                .withHeader(OriginReply, originStr.equals("*") ? request.header(OriginRequest) : originStr)
                .withHeader(CredsReply, allowCreds);
    }
}
