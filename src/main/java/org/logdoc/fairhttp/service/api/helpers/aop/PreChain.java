package org.logdoc.fairhttp.service.api.helpers.aop;

import org.logdoc.fairhttp.service.http.Request;
import org.logdoc.fairhttp.service.http.Response;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * @author Denis Danilin | me@loslobos.ru
 * 20.09.2023 13:19
 * fair-http-server ☭ sweat and blood
 */
public final class PreChain implements Function<Request, Response> {
    private final List<Pre> handlers = new ArrayList<>(8);

    public void addFirst(final Pre pre) {
        if (pre != null)
            handlers.add(0, pre);
    }

    public void addLast(final Pre pre) {
        if (pre != null)
            handlers.add(pre);
    }

    @Override
    public Response apply(final Request request) {
        for (final Pre pre : handlers) {
            pre.earlyBroken = null;
            pre.accept(request);

            if (pre.earlyBroken != null)
                return pre.earlyBroken;
        }

        return null;
    }

    public void remove(final Pre pre) {
        if (pre != null)
            handlers.remove(pre);
    }
}
