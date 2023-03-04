package org.logdoc.fairhttp.service.api.helpers.endpoint;

import org.logdoc.fairhttp.service.api.helpers.DynamicRoute;
import org.logdoc.fairhttp.service.http.Http;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.concurrent.CompletionStage;

/**
 * @author Denis Danilin | me@loslobos.ru
 * 14.02.2023 14:31
 * FairHttpService ☭ sweat and blood
 */
public class Endpoint implements Comparable<Endpoint> {
    private static final Logger logger = LoggerFactory.getLogger(Endpoint.class);

    private final String method;
    private final Signature signature;
    private final Invoker invoker;

    public Endpoint(final DynamicRoute route) {
        method = route.method.trim().toUpperCase();
        signature = new Signature(route.endpoint);
        invoker = new Invoker(route);
    }

    public Endpoint(String line) throws Exception {
        int idx;

        if ((idx = line.indexOf('#')) != -1 || (idx = line.indexOf("//")) != -1)
            line = line.substring(0, idx).trim();

        if (line.trim().isEmpty())
            throw new ArrayIndexOutOfBoundsException();

        final String[] pretend = line.split("\\s+", 3);

        if (pretend.length != 3)
            throw new ArrayIndexOutOfBoundsException();

        method = pretend[0].toUpperCase();
        signature = new Signature(pretend[1]);
        invoker = new Invoker(pretend[2], signature::isPathVar);
    }

    public boolean match(final String method, final String hardPath) {
        return this.method.equals(method) && signature.matches(hardPath);
    }

    public boolean isAsync() {
        return invoker.async;
    }

    public CompletionStage<? extends Http.Response> handleAsync(final Http.Request request) {
        return invoker.handleAsync(request, signature.values(request.path()));
    }

    public Http.Response handle(final Http.Request request) {
        return invoker.handle(request, signature.values(request.path()));
    }

    @Override
    public int compareTo(final Endpoint o) {
        final int res = method.compareTo(o.method);

        return res == 0 ? signature.compareTo(o.signature) : res;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final Endpoint endpoint = (Endpoint) o;
        return method.equals(((Endpoint) o).method) && signature.equals(endpoint.signature);
    }

    public boolean equals(final String method, final String signature) {
        return this.method.equals(method) && this.signature.equalString(signature);
    }

    @Override
    public int hashCode() {
        return Objects.hash(method, signature);
    }

    public String toString() {
        return method + "\t" + signature;
    }
}
