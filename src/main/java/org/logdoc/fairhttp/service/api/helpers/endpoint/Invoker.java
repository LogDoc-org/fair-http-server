package org.logdoc.fairhttp.service.api.helpers.endpoint;

import org.logdoc.fairhttp.service.api.Controller;
import org.logdoc.fairhttp.service.api.helpers.DynamicRoute;
import org.logdoc.fairhttp.service.api.helpers.MimeType;
import org.logdoc.fairhttp.service.http.Http;
import org.logdoc.fairhttp.service.tools.Json;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.BiFunction;
import java.util.function.Function;

import static org.logdoc.fairhttp.service.DI.gain;

/**
 * @author Denis Danilin | me@loslobos.ru
 * 26.02.2023 13:51
 * FairHttpService ☭ sweat and blood
 */
@SuppressWarnings("unchecked")
class Invoker {
    final boolean async;
    private final BiFunction<Http.Request, Map<String, String>, ? extends Http.Response> invoker;
    private final BiFunction<Http.Request, Map<String, String>, ? extends CompletionStage<? extends Http.Response>> asyncInvoker;

    Invoker(final DynamicRoute route) {
        async = route.asyncCallback != null;
        invoker = route.callback;
        asyncInvoker = route.asyncCallback;
    }

    Invoker(final String raw0, final Function<String, Boolean> isPathFunction) throws Exception {
        final String raw = raw0.replaceAll("\\s", "").replace("()", "");
        final String className, methodName, signature;
        final Class<?>[] args;
        final List<BiFunction<Http.Request, Map<String, String>, Object>> argResolvers;

        if (!raw.contains("(")) {
            signature = raw;
            final int lastDot = raw.lastIndexOf('.');
            className = raw.substring(0, lastDot);
            methodName = raw.substring(lastDot + 1);
            args = null;
            argResolvers = null;
        } else {
            final int brake = raw.indexOf('(');
            signature = raw.substring(0, brake);
            final int lastDot = signature.lastIndexOf('.');
            className = signature.substring(0, lastDot);
            methodName = signature.substring(lastDot + 1);

            final List<Class<?>> argTypes = new ArrayList<>(4);
            argResolvers = new ArrayList<>(4);

            final String[] argsDefs = raw.substring(brake + 1, raw.indexOf(')')).split(",");

            for (final String argDef : argsDefs) {
                final int lastColon = argDef.lastIndexOf(':');
                final int colon = argDef.indexOf(':');
                final String canvasAndName = argDef.substring(0, lastColon);
                String typeDef = argDef.substring(lastColon + 1);
                final boolean optional;
                String defValueString = null;

                if (typeDef.contains("=")) {
                    final int equal = typeDef.indexOf('=');
                    defValueString = typeDef.substring(equal + 1);
                    if (defValueString.equalsIgnoreCase("null")) {
                        defValueString = null;
                        optional = true;
                    } else
                        optional = false;

                    typeDef = typeDef.substring(0, equal);
                } else
                    optional = false;

                final TYPE type = typeDef.contains(".") ? TYPE.Custom : TYPE.ofSign(typeDef);

                argTypes.add(type == TYPE.Custom ? Class.forName(typeDef) : type.ofClass);

                final String name;
                final CANVAS canvas;

                if (colon < lastColon) {
                    canvas = CANVAS.valueOf(canvasAndName.substring(0, colon));
                    name = canvasAndName.substring(colon + 1);
                } else {
                    name = canvasAndName;
                    canvas = type == TYPE.Request ? CANVAS.Request : isPathFunction.apply(name) ? CANVAS.Path : CANVAS.Query;
                }

                final String finalDefValueString = defValueString;
                final BiFunction<Http.Request, Map<String, String>, Object> resolver;

                switch (canvas) {
                    case Request:
                        resolver = (request, pathVars) -> request;
                        break;
                    case Path:
                        resolver = (request, pathVars) -> type.stringAs(pathVars.get(name), optional, finalDefValueString);
                        break;
                    case Query:
                        resolver = (request, pathVars) -> type.stringAs(request.queryParam(name), optional, finalDefValueString);
                        break;
                    case Form:
                        resolver = (request, pathVars) -> {
                            if (request.contentTypeMismatch(MimeType.MULTIPART))
                                return type.stringAs(request.bodyAsForm().field(name), optional, finalDefValueString);

                            return type.stringAs(request.bodyAsMultipart().field(name), optional, finalDefValueString);
                        };
                        break;
                    case Cookie:
                        resolver = (request, pathVars) -> type.stringAs(request.cookieValue(name), optional, finalDefValueString);
                        break;
                    default: // Body
                        final Class<?> typed = Class.forName(typeDef);
                        resolver = (request, pathVars) -> {
                            final Object o = Json.fromJson(request.bodyAsJson(), typed);
                            if (o == null && !optional)
                                throw new RuntimeException(name);

                            return o;
                        };
                        break;
                }

                argResolvers.add(resolver);
            }

            args = argTypes.toArray(new Class[0]);
        }

        final Class<? extends Controller> controller = (Class<? extends Controller>) Class.forName(className);
        final Method method = args == null ? controller.getDeclaredMethod(methodName) : controller.getDeclaredMethod(methodName, args);

        async = CompletionStage.class.isAssignableFrom(method.getReturnType());
        final Logger logger = LoggerFactory.getLogger(signature);

        if (async) {
            invoker = null;
            if (args == null)
                asyncInvoker = (request, pathVars) -> {
                    CompletionStage<Http.Response> response;

                    try {
                        response = (CompletionStage<Http.Response>) method.invoke(gain(controller));
                    } catch (final Exception e) {
                        logger.error(e.getMessage(), e);

                        final Http.Response real = Http.Response.ServerError();
                        if (e.getMessage() != null)
                            real.setPayload(e.getMessage().getBytes(StandardCharsets.UTF_8), MimeType.TEXTPLAIN);

                        response = CompletableFuture.completedStage(real);
                    }

                    return response;
                };
            else
                asyncInvoker = (request, pathVars) -> {
                    CompletionStage<Http.Response> response;

                    try {
                        final List<Object> arguments = new ArrayList<>(argResolvers.size());

                        for (final BiFunction<Http.Request, Map<String, String>, Object> resolver : argResolvers)
                            arguments.add(resolver.apply(request, pathVars));

                        response = (CompletionStage<Http.Response>) method.invoke(gain(controller), arguments.toArray(new Object[0]));
                    } catch (final Exception e) {
                        logger.error(e.getMessage(), e);

                        final Http.Response real = Http.Response.ServerError();
                        if (e.getMessage() != null)
                            real.setPayload(e.getMessage().getBytes(StandardCharsets.UTF_8), MimeType.TEXTPLAIN);

                        response = CompletableFuture.completedStage(real);
                    }

                    return response;
                };
        } else {
            asyncInvoker = null;
            if (args == null)
                invoker = (request, pathVars) -> {
                    Http.Response response;

                    try {
                        response = (Http.Response) method.invoke(gain(controller));
                        logger.debug("Invoked :: " + response);
                    } catch (final Exception e) {
                        logger.error(e.getMessage(), e);

                        response = Http.Response.ServerError();
                        if (e.getMessage() != null)
                            response.setPayload(e.getMessage().getBytes(StandardCharsets.UTF_8), MimeType.TEXTPLAIN);
                    }

                    return response;
                };
            else
                invoker = (request, pathVars) -> {
                    Http.Response response;

                    try {
                        final List<Object> arguments = new ArrayList<>(argResolvers.size());

                        for (final BiFunction<Http.Request, Map<String, String>, Object> resolver : argResolvers)
                            arguments.add(resolver.apply(request, pathVars));

                        response = (Http.Response) method.invoke(gain(controller), arguments.toArray(new Object[0]));
                        logger.debug("Invoked async");
                    } catch (final Exception e) {
                        logger.error(e.getMessage(), e);

                        response = Http.Response.ServerError();
                        if (e.getMessage() != null)
                            response.setPayload(e.getMessage().getBytes(StandardCharsets.UTF_8), MimeType.TEXTPLAIN);
                    }

                    return response;
                };
        }
    }

    Http.Response handle(final Http.Request request, final Map<String, String> pathVars) {
        return invoker.apply(request, pathVars);
    }

    CompletionStage<? extends Http.Response> handleAsync(final Http.Request request, final Map<String, String> pathVars) {
        return asyncInvoker.apply(request, pathVars);
    }
}
