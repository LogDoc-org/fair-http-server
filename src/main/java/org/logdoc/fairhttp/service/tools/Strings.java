package org.logdoc.fairhttp.service.tools;

import java.io.*;
import java.lang.reflect.Array;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/**
 * @author Denis Danilin | me@loslobos.ru
 * 04.02.2023 14:59
 * FairHttpService ☭ sweat and blood
 */
public class Strings {
    public static final String MimeASCII = "US-ASCII",
            MimeBase64 = "B",
            MimeQuotedPrintable = "Q",
            MimeEncodedMarker = "=?",
            MimeEncodedEnd = "?=",
            MimeWhitespace = " \t\r\n";

    public static final int MimeShift = Byte.SIZE / 2;

    private static final SecureRandom rnd;

    static {
        rnd = new SecureRandom();
        rnd.setSeed(System.currentTimeMillis());
    }

    private static long tol(final byte[] buf, final int shift) {
        return (toi(buf, shift) << 32) + ((toi(buf, shift + 4) << 32) >>> 32);
    }

    private static long toi(final byte[] buf, int shift) {
        return (buf[shift] << 24)
                + ((buf[++shift] & 0xFF) << 16)
                + ((buf[++shift] & 0xFF) << 8)
                + (buf[++shift] & 0xFF);
    }

    public static UUID generateUuid() {
        final byte[] buffer = new byte[16];
        rnd.nextBytes(buffer);

        return generateUuid(buffer);
    }

    private static UUID generateUuid(final byte[] buffer) {
        long r1, r2;

        r1 = tol(buffer, 0);
        r2 = tol(buffer, 1);

        r1 &= ~0xF000L;
        r1 |= 4 << 12;
        r2 = ((r2 << 2) >>> 2);
        r2 |= (2L << 62);

        return new UUID(r1, r2);
    }

    public static boolean getBoolean(final Object o) {
        if (o == null)
            return false;

        if (o instanceof Boolean)
            return (Boolean) o;

        return notNull(o).equalsIgnoreCase("true") || !notNull(o).equals("0");
    }

    public static String notNull(final Object o, final String def) {
        if (o == null)
            return def == null ? "" : def.trim();

        if (o instanceof String)
            return ((String) o).trim();

        return String.valueOf(o).trim();
    }

    public static String notNull(final Object o) {
        return notNull(o, "");
    }

    @SuppressWarnings("rawtypes")
    public static boolean isEmpty(final Object o) {
        if (o == null)
            return true;

        if (o.getClass().isArray())
            return Array.getLength(o) == 0;

        if (o instanceof Collection)
            return ((Collection) o).isEmpty();

        if (o instanceof Map)
            return ((Map) o).isEmpty();

        if (o.getClass().isEnum())
            return false;

        return notNull(o).isEmpty();
    }

    public static String mimeDecodeText(final String text) {
        if (!text.contains(MimeEncodedMarker))
            return text;

        int offset = 0;
        final int endOffset = text.length();

        int startWhiteSpace = -1, endWhiteSpace = -1;

        final StringBuilder decodedText = new StringBuilder(text.length());

        boolean previousTokenEncoded = false;

        while (offset < endOffset) {
            char ch = text.charAt(offset);

            if (MimeWhitespace.indexOf(ch) != -1) {
                startWhiteSpace = offset;

                while (offset < endOffset) {
                    ch = text.charAt(offset);
                    if (MimeWhitespace.indexOf(ch) != -1)
                        offset++;
                    else {
                        endWhiteSpace = offset;
                        break;
                    }
                }
            } else {
                int wordStart = offset;

                while (offset < endOffset) {
                    ch = text.charAt(offset);
                    if (MimeWhitespace.indexOf(ch) == -1)
                        offset++;
                    else
                        break;
                }
                final String word = text.substring(wordStart, offset);

                if (word.startsWith(MimeEncodedMarker)) {
                    try {
                        final String decodedWord = mimeDecodeWord(word);

                        if (!previousTokenEncoded && startWhiteSpace != -1) {
                            decodedText.append(text, startWhiteSpace, endWhiteSpace);
                            startWhiteSpace = -1;
                        }
                        previousTokenEncoded = true;
                        decodedText.append(decodedWord);
                        continue;
                    } catch (final Exception ignore) {
                    }
                }

                if (startWhiteSpace != -1) {
                    decodedText.append(text, startWhiteSpace, endWhiteSpace);
                    startWhiteSpace = -1;
                }
                previousTokenEncoded = false;
                decodedText.append(word);
            }
        }

        return decodedText.toString();
    }

    public static String mimeDecodeWord(final String word) throws Exception {
        if (!word.startsWith(MimeEncodedMarker))
            throw new Exception("Invalid RFC 2047 encoded-word: " + word);

        int charsetPos = word.indexOf('?', 2);
        if (charsetPos == -1)
            throw new Exception("Missing charset in RFC 2047 encoded-word: " + word);

        String charset = word.substring(2, charsetPos).toLowerCase();

        int encodingPos = word.indexOf('?', charsetPos + 1);
        if (encodingPos == -1)
            throw new Exception("Missing encoding in RFC 2047 encoded-word: " + word);

        String encoding = word.substring(charsetPos + 1, encodingPos);

        int encodedTextPos = word.indexOf(MimeEncodedEnd, encodingPos + 1);
        if (encodedTextPos == -1)
            throw new Exception("Missing encoded text in RFC 2047 encoded-word: " + word);

        String encodedText = word.substring(encodingPos + 1, encodedTextPos);

        if (encodedText.isEmpty())
            return "";

        try {
            final ByteArrayOutputStream out = new ByteArrayOutputStream(encodedText.length());

            byte[] encodedData = encodedText.getBytes(MimeASCII);

            switch (encoding) {
                case MimeBase64:
                    Bytes.copy(Base64.getDecoder().wrap(new ByteArrayInputStream(encodedData)), out);
                    break;
                case MimeQuotedPrintable:  // maybe quoted printable.
                    mimeDecodeQP(encodedData, out);
                    break;
                default:
                    throw new UnsupportedEncodingException("Unknown RFC 2047 encoding: " + encoding);
            }

            return out.toString(mimeCharset(charset));
        } catch (IOException e) {
            throw new UnsupportedEncodingException("Invalid RFC 2047 encoding");
        }
    }

    public static int mimeDecodeQP(byte[] data, final OutputStream out) throws IOException {
        int off = 0;
        int length = data.length;
        int endOffset = off + length;
        int bytesWritten = 0;

        while (off < endOffset) {
            byte ch = data[off++];

            if (ch == '_')
                out.write(' ');
            else if (ch == '=') {
                if (off + 1 >= endOffset)
                    throw new IOException("Invalid quoted printable encoding; truncated escape sequence");

                byte b1 = data[off++];
                byte b2 = data[off++];

                if (b1 == '\r')
                    if (b2 != '\n') {
                        throw new IOException("Invalid quoted printable encoding; CR must be followed by LF");
                    } else {
                        int c1 = hexToBinary(b1);
                        int c2 = hexToBinary(b2);
                        out.write((c1 << MimeShift) | c2);
                        bytesWritten++;
                    }
            } else {
                out.write(ch);
                bytesWritten++;
            }
        }

        return bytesWritten;
    }

    public static int hexToBinary(final byte b) throws IOException {
        final int i = Character.digit((char) b, 16);

        if (i == -1)
            throw new IOException("Invalid quoted printable encoding: not a valid hex digit: " + b);

        return i;
    }

    public static String mimeCharset(final String charset) {
        if (charset == null)
            return null;

        switch (charset.toLowerCase()) {
            case "iso-2022-cn":
                return "ISO2022CN";
            case "iso-2022-kr":
                return "ISO2022KR";
            case "utf-8":
                return "UTF8";
            case "utf8":
                return "UTF8";
            case "ja_jp.iso2022-7":
                return "ISO2022JP";
            case "ja_jp.eucjp":
                return "EUCJIS";
            case "euc-kr":
                return "KSC5601";
            case "euckr":
                return "KSC5601";
            case "us-ascii":
                return "ISO-8859-1";
            case "x-us-ascii":
                return "ISO-8859-1";
            default:
                return charset;
        }
    }


    // http://bjoern.hoehrmann.de/utf-8/decoder/dfa/
    private static final int[] utf8d = {
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            0, // 00..1f
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            0, // 20..3f
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            0, // 40..5f
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            0, // 60..7f
            1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9,
            9, // 80..9f
            7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7,
            7, // a0..bf
            8, 8, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2,
            2, // c0..df
            0xa, 0x3, 0x3, 0x3, 0x3, 0x3, 0x3, 0x3, 0x3, 0x3, 0x3, 0x3, 0x3, 0x4, 0x3, 0x3, // e0..ef
            0xb, 0x6, 0x6, 0x6, 0x5, 0x8, 0x8, 0x8, 0x8, 0x8, 0x8, 0x8, 0x8, 0x8, 0x8, 0x8, // f0..ff
            0x0, 0x1, 0x2, 0x3, 0x5, 0x8, 0x7, 0x1, 0x1, 0x1, 0x4, 0x6, 0x1, 0x1, 0x1, 0x1, // s0..s0
            1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 0, 1, 1, 1, 1, 1, 0, 1, 0, 1, 1, 1, 1, 1,
            1, // s1..s2
            1, 2, 1, 1, 1, 1, 1, 2, 1, 2, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 2, 1, 1, 1, 1, 1, 1, 1,
            1, // s3..s4
            1, 2, 1, 1, 1, 1, 1, 1, 1, 2, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 3, 1, 3, 1, 1, 1, 1, 1,
            1, // s5..s6
            1, 3, 1, 1, 1, 1, 1, 3, 1, 3, 1, 1, 1, 1, 1, 1, 1, 3, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1
            // s7..s8
    };

    public static boolean isValidUTF8(final byte[] data, int off) {
        final int len = data.length;
        if (len < off)
            return false;

        for (int i = off, state = 0; i < len; ++i) {
            state = utf8d[256 + (state << 4) + utf8d[(0xff & data[i])]];

            if (state == 1)
                return false;
        }

        return true;
    }

    public static boolean isValidUTF8(final byte[] data) {
        return isValidUTF8(data, 0);
    }

    public static String stringUtf8(final byte[] bytes) {
        return new String(bytes, StandardCharsets.UTF_8);
    }
}
