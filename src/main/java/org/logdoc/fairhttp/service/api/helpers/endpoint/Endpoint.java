package org.logdoc.fairhttp.service.api.helpers.endpoint;

import org.logdoc.fairhttp.service.api.helpers.aop.Post;
import org.logdoc.fairhttp.service.api.helpers.aop.PostChain;
import org.logdoc.fairhttp.service.api.helpers.aop.Pre;
import org.logdoc.fairhttp.service.api.helpers.aop.PreChain;
import org.logdoc.fairhttp.service.http.Request;
import org.logdoc.fairhttp.service.http.Response;
import org.logdoc.helpers.gears.Pair;
import org.slf4j.ILoggerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Objects;
import java.util.function.BiFunction;

/**
 * @author Denis Danilin | me@loslobos.ru
 * 14.02.2023 14:31
 * FairHttpService ☭ sweat and blood
 */
public class Endpoint implements Comparable<Endpoint> {
    private static final Logger preLogger = LoggerFactory.getLogger("PreFilters"), postLogger = LoggerFactory.getLogger("PostFilters");

    private final String method;
    private final Signature signature;
    private final BiFunction<Request, Map<String, String>, Response> invoker;
    private final PreChain pre;
    private final PostChain post;

    public Endpoint(final String method, final Signature signature, final BiFunction<Request, Map<String, String>, Response> invoker) {
        this.method = method;
        this.signature = signature;
        this.invoker = invoker;

        pre = new PreChain();
        post = new PostChain();
    }

    public Pair<Boolean, Boolean> match(final String method, final String hardPath) {
        return Pair.create(this.method.equals(method), signature.matches(hardPath));
    }

    public Response call(final Request request) {
        final Response broken = pre.apply(request);

        if (broken != null) {
            preLogger.warn(this + " :: request call interrupted by pre-filters");
            return broken;
        }

        return post.apply(invoker.apply(request, signature.values(request.path())));
    }

    public void removePre(final Pre pre) {
        this.pre.remove(pre);
    }

    public void removePost(final Post post) {
        this.post.remove(post);
    }

    public void addFirstPre(final Pre pre) {this.pre.addFirst(pre);}

    public void addLastPre(final Pre pre) {this.pre.addLast(pre);}

    public void addFirstPost(final Post pre) {post.addFirst(pre);}

    public void addLastPost(final Post pre) {post.addLast(pre);}

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

    public String method() {
        return method;
    }

    public String signature() {
        return signature.proper;
    }
}
