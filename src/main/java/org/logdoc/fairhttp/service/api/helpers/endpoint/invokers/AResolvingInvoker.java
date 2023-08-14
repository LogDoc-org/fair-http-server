package org.logdoc.fairhttp.service.api.helpers.endpoint.invokers;

import org.logdoc.fairhttp.service.http.Request;
import org.logdoc.fairhttp.service.http.Response;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * @author Denis Danilin | me@loslobos.ru
 * 12.08.2023 18:03
 * fair-http-server ☭ sweat and blood
 */
public abstract class AResolvingInvoker extends ARequestInvoker {
    private final List<BiFunction<Request, Map<String, String>, ?>> resolvers;

    public AResolvingInvoker(final Method method, final List<BiFunction<Request, Map<String, String>, ?>> resolvers, final Function<Throwable, Response> errorHandler, final int execTimeout) {
        super(method, errorHandler, execTimeout);
        this.resolvers = resolvers;
    }

    protected Object[] prepareResolvers(final Request request, final Map<String, String> pathMap) {
        final Object[] params = new Object[resolvers.size()];

        int i = 0;
        for (final BiFunction<Request, Map<String, String>, ?> ad : resolvers)
            params[i++] = ad.apply(request, pathMap);

        return params;
    }
}
