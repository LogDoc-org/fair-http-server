package org.logdoc.fairhttp.service.tools;

/**
 * @author Denis Danilin | me@loslobos.ru
 * 17.02.2023 16:45
 * FairHttpService ☭ sweat and blood
 */
public class Pair<A, B> {
    public final A first;
    public final B second;

    private Pair(final A first, final B second) {
        this.first = first;
        this.second = second;
    }

    public static <F, S> Pair<F, S> create(final F first, final S second) {
        return new Pair<>(first, second);
    }
}
