package org.logdoc.fairhttp.service.api.helpers.aop;

import org.logdoc.fairhttp.service.http.Request;
import org.logdoc.fairhttp.service.http.Response;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;

/**
 * @author Denis Danilin | me@loslobos.ru
 * 20.09.2023 13:25
 * fair-http-server ☭ sweat and blood
 */
public class PostChain implements BiFunction<Request, Response, Response> {
    private final List<Post> handlers = new ArrayList<>(8);

    public void addFirst(final Post pre) {
        if (pre != null)
            handlers.add(0, pre);
    }

    public void addLast(final Post pre) {
        if (pre != null)
            handlers.add(pre);
    }

    @Override
    public Response apply(final Request request, final Response response) {
        Response r0 = response, tmp;

        for (final Post pre : handlers) {
            tmp = pre.apply(request, r0);
            if (tmp != null)
                r0 = tmp;
        }

        return r0;
    }

    public void remove(final Post post) {
        if (post != null)
            this.handlers.remove(post);
    }
}
