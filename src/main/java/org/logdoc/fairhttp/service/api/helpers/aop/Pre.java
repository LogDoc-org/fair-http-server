package org.logdoc.fairhttp.service.api.helpers.aop;

import org.logdoc.fairhttp.service.http.Request;
import org.logdoc.fairhttp.service.http.Response;

import java.util.function.Consumer;

/**
 * @author Denis Danilin | me@loslobos.ru
 * 20.09.2023 13:13
 * fair-http-server ☭ sweat and blood
 */
public abstract class Pre implements Consumer<Request> {
    Response earlyBroken = null;

    public final void breakChain(final Response response) {
        if (response != null && earlyBroken == null)
            earlyBroken = response;
    }
}
