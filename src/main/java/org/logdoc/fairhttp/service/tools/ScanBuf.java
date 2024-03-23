package org.logdoc.fairhttp.service.tools;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * @author Denis Danilin | me@loslobos.ru
 * 21.03.2024 10:55
 * fair-http-server ☭ sweat and blood
 */
public class ScanBuf {
    private byte[] buf;

    public ScanBuf() {
        buf = new byte[0];
    }

    public ScanBuf(final byte[] buf) {
        this.buf = buf == null ? new byte[0] : buf;
    }

    public byte[] asData() {
        return buf;
    }

    public void append(final byte[] a) {
        if (a == null || a.length == 0)
            return;

        if (buf.length == 0)
            buf = a;
        else {
            final byte[] tmp = new byte[buf.length + a.length];
            System.arraycopy(buf, 0, tmp, 0, buf.length);
            System.arraycopy(a, 0, tmp, buf.length, a.length);
            buf = tmp;
        }
    }

    public boolean missed(final byte mark) {
        return !has(mark);
    }

    public boolean missed(final byte[] mark) {
        return !has(mark);
    }

    public boolean has(final byte mark) {
        for (final byte b : buf)
            if (b == mark)
                return true;

        return false;
    }

    public ScanBuf scanAndCut(final byte mark) {
        return scanAndCut(mark, true);
    }

    public ScanBuf scanAndCut(final byte mark, final boolean skipMark) {
        for (int i = 0; i < buf.length; i++)
            if (buf[i] == mark) {
                if (0 == i)
                    return new ScanBuf();

                skip(i + (skipMark ? 1 : 0));

                return new ScanBuf(Arrays.copyOfRange(buf, 0, i));
            }

        return null;
    }

    public boolean has(final byte[] mark) {
        CYCLE:
        for (int i = 0; i < buf.length - mark.length; i++)
            if (buf[i] == mark[0]) {
                for (int j = 1; j < mark.length; j++)
                    if (buf[i + j] != mark[j])
                        continue CYCLE;

                return true;
            }

        return false;
    }

    public ScanBuf scanAndCut(final byte[] mark) {
        return scanAndCut(mark, true);
    }

    public ScanBuf scanAndCut(final byte[] mark, final boolean skipMark) {
        CYCLE:
        for (int i = 0; i < buf.length; i++)
            if (buf[i] == mark[0]) {
                for (int j = 1; j < mark.length; j++)
                    if (buf[i + j] != mark[j])
                        continue CYCLE;

                if (0 == i)
                    return new ScanBuf();

                skip(i + (skipMark ? mark.length : 0));

                return new ScanBuf(Arrays.copyOfRange(buf, 0, i));
            }

        return null;
    }

    @Override
    public String toString() {
        return new String(buf, StandardCharsets.UTF_8);
    }

    public boolean startsWith(final byte[] mark) {
        if (buf.length < mark.length || buf[0] != mark.length)
            return false;

        for (int j = 1; j < mark.length; j++)
            if (buf[j] != mark[j])
                return false;

        return true;
    }

    public void skip(final int length) {
        buf = Arrays.copyOfRange(buf, length, buf.length);
    }
}
