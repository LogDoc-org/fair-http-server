package org.logdoc.fairhttp.service.tools;

import java.util.function.Consumer;

/**
 * @author Denis Danilin | me@loslobos.ru
 * 14.08.2023 19:07
 * fair-http-server ☭ sweat and blood
 */
public interface PhasedConsumer<T> extends Consumer<T> {
    void warmUp(T t);
}
