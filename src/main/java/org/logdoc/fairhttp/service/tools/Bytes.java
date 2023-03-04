package org.logdoc.fairhttp.service.tools;

import org.logdoc.fairhttp.service.api.helpers.MimeType;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * @author Denis Danilin | me@loslobos.ru
 * 15.02.2023 14:26
 * FairHttpService ☭ sweat and blood
 */
public class Bytes {
    public static byte[] FEED = "\r\n".getBytes(StandardCharsets.US_ASCII);
    public static byte[] PROTO = "HTTP/1.1".getBytes(StandardCharsets.US_ASCII);
    public static final byte CR = 0x0D, LF = 0x0A, DASH = 0x2D;
    public static final byte[] headerSeparator = {CR, LF, CR, LF}, fieldSeparator = {CR, LF}, streamEnd = {DASH, DASH}, boundaryPrefix = {CR, LF, DASH, DASH};

    public static MimeType guessMime(final byte[] head16bytes) {
        final int[] ints = new int[head16bytes.length];
        for (int i = 0; i < head16bytes.length; i++)
            ints[i] = head16bytes[i];

        return MimeType.guessMime(ints);
    }

    public static boolean subArrayEquals(final byte[] bigArray, final byte[] match, final int fromIdx) {
        if (fromIdx < 0 || bigArray.length - fromIdx < match.length) return false;

        for (int i = fromIdx, j = 0; i < bigArray.length - match.length && j < match.length; i++, j++)
            if (bigArray[i] != match[j])
                return false;

        return true;
    }

    public static void copy(final InputStream in, final OutputStream out) {
        copy(false, in, out);
    }

    public static void copy(final boolean rethrow, final InputStream in, final OutputStream out) {
        final byte[] tmp = new byte[1024 * 1024];
        int read;

        try {
            while ((read = in.read(tmp, 0, tmp.length)) != -1)
                out.write(tmp, 0, read);

            out.flush();
        } catch (final IOException e) {
            if (rethrow)
                throw new RuntimeException(e);
        }
    }

    public static long copy(final InputStream pIn, final OutputStream pOut, final boolean pClose) throws IOException {
        long total = 0;
        final byte[] pBuffer = new byte[1024 * 512];

        for (int res = 0; res != -1;) {
            res = pIn.read(pBuffer, 0, pBuffer.length);

            if (res > 0) {
                total += res;

                if (pOut != null)
                    pOut.write(pBuffer, 0, res);
            }
        }

        if (pOut != null) {
            if (pClose)
                pOut.close();
            else
                pOut.flush();
        }
        pIn.close();
        if (pClose && pOut != null)
            pOut.close();

        return total;
    }

}
