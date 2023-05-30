package org.logdoc.fairhttp.service.api.helpers;

/**
 * @author Denis Danilin | me@loslobos.ru
 * 27.05.2023 15:38
 * fair-http-server ☭ sweat and blood
 */
public interface EagerSingleton extends Singleton, Comparable<EagerSingleton> {
    default int startPriority() {
        return 0;
    }

    @Override
    default int compareTo(EagerSingleton o) {
        return Integer.compare(o.startPriority(), this.startPriority());
    }
}
