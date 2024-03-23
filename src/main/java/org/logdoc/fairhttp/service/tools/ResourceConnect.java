package org.logdoc.fairhttp.service.tools;

import java.net.InetAddress;
import java.util.concurrent.Callable;

/**
 * @author Denis Danilin | me@loslobos.ru
 * 20.03.2024 11:41
 * fair-http-server ☭ sweat and blood
 */
public interface ResourceConnect {
    byte[] sep = new byte[] {'\r', '\n'};

    String resource();
    String query();

    void readUpTo(int lim);

    void write(byte[] data);

    void close();

    void writeAndClose(byte[] data);

    InetAddress remote();

    Throwable errorCause();

    String errorMessage();

    boolean hasTask();

    Runnable getTask();

    enum RCState {
        Initial, ReadHeaders, FullyRead, SomeWritten, FullyWritten, GraceClosed, ErrorClosed
    }
}
