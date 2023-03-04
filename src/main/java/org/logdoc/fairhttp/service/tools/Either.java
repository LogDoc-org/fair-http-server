package org.logdoc.fairhttp.service.tools;

import java.util.Optional;

/**
 * @author Denis Danilin | me@loslobos.ru
 * 15.02.2023 12:30
 * FairHttpService ☭ sweat and blood
 */
public class Either<L, R> {

    public final Optional<L> left;

    public final Optional<R> right;

    private Either(final Optional<L> left, final Optional<R> right) {
        this.left = left;
        this.right = right;
    }

    public static <L, R> Either<L, R> Left(final L value) {
        return new Either<L, R>(Optional.of(value), Optional.<R>empty());
    }

    public static <L, R> Either<L, R> Right(final R value) {
        return new Either<L, R>(Optional.<L>empty(), Optional.of(value));
    }

    @Override
    public String toString() {
        return "Either(left: " + this.left + ", right: " + this.right + ")";
    }
}
