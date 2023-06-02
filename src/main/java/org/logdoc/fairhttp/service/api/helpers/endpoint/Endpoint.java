package org.logdoc.fairhttp.service.api.helpers.endpoint;

import org.logdoc.fairhttp.service.api.helpers.endpoint.invokers.RequestInvoker;
import org.logdoc.fairhttp.service.http.Http;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletionStage;
import java.util.function.BiFunction;

import static org.logdoc.helpers.Texts.notNull;

/**
 * @author Denis Danilin | me@loslobos.ru
 * 14.02.2023 14:31
 * FairHttpService ☭ sweat and blood
 */
public class Endpoint implements Comparable<Endpoint> {
    private final String method;
    private final Signature signature;
    private final RequestInvoker invoker;

    public Endpoint(final String method, final String endpoint, final BiFunction<Http.Request, Map<String, String>, CompletionStage<Http.Response>> callback) {
        this.method = notNull(method).trim().toUpperCase();
        signature = new Signature(endpoint);
        invoker = callback::apply;
    }

    public Endpoint(String line) throws NoSuchMethodException {
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
        invoker = InvokerFactory.build(pretend[2]);
        if (invoker == null)
            throw new NoSuchMethodException("Cant build endpoint handler");
    }

    public boolean match(final String method, final String hardPath) {
        return this.method.equals(method) && signature.matches(hardPath);
    }

    public CompletionStage<? extends Http.Response> call(final Http.Request request) {
        return invoker.apply(request, signature.values(request.path()));
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

    public boolean pathMatch(final String hardPath) {
        return signature.matches(hardPath);
    }
}
