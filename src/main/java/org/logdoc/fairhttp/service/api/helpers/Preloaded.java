package org.logdoc.fairhttp.service.api.helpers;

import com.typesafe.config.Config;

/**
 * @author Denis Danilin | me@loslobos.ru
 * 03.02.2023 14:29
 * FairHttpService ☭ sweat and blood
 */
public interface Preloaded {
    void configure(Config config) throws Exception;
}
