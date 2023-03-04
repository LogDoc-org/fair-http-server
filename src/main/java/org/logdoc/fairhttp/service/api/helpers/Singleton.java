package org.logdoc.fairhttp.service.api.helpers;

/**
 * @author Denis Danilin | me@loslobos.ru
 * 03.02.2023 14:10
 * FairHttpService ☭ sweat and blood
 */
public interface Singleton {
    default <T> T get() {
        return (T) this;
    }
}
