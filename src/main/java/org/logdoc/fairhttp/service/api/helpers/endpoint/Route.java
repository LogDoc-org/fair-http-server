package org.logdoc.fairhttp.service.api.helpers.endpoint;

import org.logdoc.fairhttp.service.http.Request;
import org.logdoc.fairhttp.service.http.Response;
import org.logdoc.helpers.gears.Pair;

import java.util.Map;
import java.util.Objects;
import java.util.function.BiFunction;

/**
 * @author Denis Danilin | me@loslobos.ru
 * 14.02.2023 14:31
 * FairHttpService ☭ sweat and blood
 */
public class Route implements Comparable<Route> {
    private final String method;
    private final Signature signature;
    private final BiFunction<Request, Map<String, String>, Response> invoker;

    public Route(final String method, final Signature signature, final BiFunction<Request, Map<String, String>, Response> invoker) {
        this.method = method;
        this.signature = signature;
        this.invoker = invoker;

    }

    public Pair<Boolean, Boolean> match(final String method, final String hardPath) {
        return Pair.create(this.method.equals(method), signature.matches(hardPath));
    }

    public Response call(final Request request) {
        return invoker.apply(request, signature.values(request.path()));
    }

    @Override
    public int compareTo(final Route o) {
        final int res = method.compareTo(o.method);

        return res == 0 ? signature.compareTo(o.signature) : res;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final Route route = (Route) o;
        return method.equals(((Route) o).method) && signature.equals(route.signature);
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

    public boolean pathMatch(final String hardPath) {
        return signature.matches(hardPath);
    }

    public String method() {
        return method;
    }

    public String signature() {
        return signature.proper;
    }
}
