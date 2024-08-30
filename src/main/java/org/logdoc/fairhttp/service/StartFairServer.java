package org.logdoc.fairhttp.service;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.logdoc.fairhttp.service.api.helpers.Preloaded;
import org.logdoc.fairhttp.service.http.Response;
import org.logdoc.fairhttp.service.http.Server;
import org.logdoc.fairhttp.service.tools.ErrorHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;

import static org.logdoc.helpers.std.MimeTypes.TEXTPLAIN;

/**
 * @author Denis Danilin | me@loslobos.ru
 * 03.02.2023 14:08
 * FairHttpService ☭ sweat and blood
 */
public class StartFairServer {
    private static final Logger logger = LoggerFactory.getLogger(StartFairServer.class);

    public static void main(final String[] args) {
        new StartFairServer().start();
    }

    @SuppressWarnings("unchecked")
    private void start() {
        final Config c = ConfigFactory.defaultApplication().withFallback(ConfigFactory.defaultReference()).resolve();
        DI.init(c);

        byte[] endpoints = null;
        try (final InputStream is = StartFairServer.class.getClassLoader().getResourceAsStream("routes"); final ByteArrayOutputStream os = new ByteArrayOutputStream(64 * 1024)) {
            if (is != null) {
                final byte[] buf = new byte[1024 * 640];
                int read;

                while ((read = is.read(buf)) != -1)
                    os.write(buf, 0, read);

                os.flush();
                endpoints = os.toByteArray();
            }
        } catch (final Exception e) {
            logger.atDebug().log("Cant load routes config: " + e.getMessage(), e);
        }

        final Server s = new Server(c);
        DI.bindProvider(Server.class, () -> s);

        final Logger errorLogger = LoggerFactory.getLogger(ErrorHandler.class);

        s.setupErrorHandler(t -> {
            errorLogger.error(t.getMessage(), t);
            final Response response = Response.ServerError();
            if (t.getMessage() != null)
                response.setPayload(t.getMessage().getBytes(StandardCharsets.UTF_8), TEXTPLAIN);

            return response;
        });

        if (c.hasPath("fair.error_handler"))
            try {
                final ErrorHandler handler = (ErrorHandler) Class.forName(c.getString("fair.error_handler")).getDeclaredConstructor().newInstance();
                s.setupErrorHandler(handler::handle);
            } catch (final Exception e) {
                logger.warn("Cant setup custom error handler: " + e.getMessage());
            }

        s.setupConfigEndpoints(endpoints);
        s.start();

        if (c.hasPath("fair.preload.load"))
            new HashSet<>(c.getStringList("fair.preload.load")).forEach(lc -> {
                try {
                    DI.preload((Class<Preloaded>) Class.forName(lc));
                } catch (final Exception e) {
                    logger.error("Cant preload '" + lc + "' :: " + e.getMessage(), e);
                }
            });

        DI.initEagers();
    }
}
