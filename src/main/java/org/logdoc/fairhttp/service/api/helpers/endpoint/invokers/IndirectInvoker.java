package org.logdoc.fairhttp.service.api.helpers.endpoint.invokers;

import org.logdoc.fairhttp.service.DI;
import org.logdoc.fairhttp.service.http.Request;
import org.logdoc.fairhttp.service.http.Response;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * @author Denis Danilin | me@loslobos.ru
 * 12.08.2023 17:43
 * fair-http-server ☭ sweat and blood
 */
public class IndirectInvoker extends AResolvingInvoker {

    public IndirectInvoker(final Method method, final List<BiFunction<Request, Map<String, String>, ?>> resolvers, final Function<Throwable, Response> errorHandler, final int execTimeout) {
        super(method, resolvers, errorHandler, execTimeout);
    }

    @SuppressWarnings("unchecked")
    @Override
    protected Supplier<Response> supplyAction(final Request request, final Map<String, String> pathMap) {
        final Object[] params = prepareResolvers(request, pathMap);

        return () -> {
            try {
                return ((CompletionStage<Response>) method.invoke(DI.gain(method.getDeclaringClass()), params)).toCompletableFuture().get(execTimeout, TimeUnit.SECONDS);
            } catch (final RuntimeException e) {
                throw e;
            } catch (final Exception e) {
                throw new RuntimeException(e);
            }
        };
    }
}
