package org.logdoc.fairhttp.service.tools;

import java.io.*;
import java.util.Base64;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import static org.logdoc.helpers.Bytes.copy;
import static org.logdoc.helpers.Texts.hexToBinary;
import static org.logdoc.helpers.Texts.mimeCharset;

/**
 * @author Denis Danilin | me@loslobos.ru
 * 09.06.2023 12:11
 * fair-http-server ☭ sweat and blood
 */
public class ParameterParser {
    private static final int MimeShift = Byte.SIZE / 2;
    private static final String MimeASCII = "US-ASCII", MimeBase64 = "B", MimeQuotedPrintable = "Q", MimeEncodedMarker = "=?", MimeEncodedEnd = "?=", MimeWhitespace = " \t\r\n";

    private char[] chars;
    private int pos;
    private int len;
    private int i1;
    private int i2;
    private boolean lowerCaseNames = false;

    public ParameterParser() {
        super();

        chars = null;
        pos = len = i1 = i2 = 0;
    }

    private boolean hasChar() {
        return this.pos < this.len;
    }

    private String getToken(final boolean quoted) {
        while ((i1 < i2) && (Character.isWhitespace(chars[i1]))) i1++;

        while ((i2 > i1) && (Character.isWhitespace(chars[i2 - 1]))) i2--;

        if (quoted && ((i2 - i1) >= 2) && (chars[i1] == '"') && (chars[i2 - 1] == '"')) {
            i1++;
            i2--;
        }

        return i2 > i1 ? new String(chars, i1, i2 - i1) : null;
    }

    private boolean isOneOf(char ch, final char[] charray) {
        for (char aCharray : charray)
            if (ch == aCharray) return true;

        return false;
    }

    private String parseToken(final char[] terminators) {
        char ch;
        i1 = i2 = pos;
        while (hasChar()) {
            ch = chars[pos];
            if (isOneOf(ch, terminators)) break;

            i2++;
            pos++;
        }

        return getToken(false);
    }

    private String parseQuotedToken(final char[] terminators) {
        char ch;
        i1 = i2 = pos;
        boolean quoted = false;
        boolean charEscaped = false;
        while (hasChar()) {
            ch = chars[pos];
            if (!quoted && isOneOf(ch, terminators)) {
                break;
            }
            if (!charEscaped && ch == '"') {
                quoted = !quoted;
            }
            charEscaped = (!charEscaped && ch == '\\');
            i2++;
            pos++;

        }
        return getToken(true);
    }

    public void setLowerCaseNames() {
        this.lowerCaseNames = true;
    }

    public Map<String, String> parse(final String str, char separator) {
        if (str == null) return new HashMap<>();

        return parse(str.toCharArray(), separator);
    }

    private Map<String, String> parse(final char[] chars, char separator) {
        if (chars == null) return new HashMap<>();

        return parse(chars, chars.length, separator);
    }

    private Map<String, String> parse(final char[] chars, int length, char separator) {
        if (chars == null) return new HashMap<>();

        final HashMap<String, String> params = new HashMap<>();
        this.chars = chars;
        this.pos = 0;
        this.len = length;

        String paramName, paramValue;
        while (hasChar()) {
            paramName = parseToken(new char[]{'=', separator});
            paramValue = null;
            if (hasChar() && (chars[pos] == '=')) {
                pos++; // skip '='
                paramValue = parseQuotedToken(new char[]{separator});

                if (paramValue != null) paramValue = mimeDecodeText(paramValue);

            }
            if (hasChar() && (chars[pos] == separator)) pos++; // skip separator

            if ((paramName != null) && (!paramName.isEmpty())) {
                if (this.lowerCaseNames) paramName = paramName.toLowerCase(Locale.ENGLISH);

                params.put(paramName, paramValue);
            }
        }

        return params;
    }

    private String mimeDecodeText(final String text) {
        if (!text.contains(MimeEncodedMarker)) return text;

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
                    if (MimeWhitespace.indexOf(ch) != -1) offset++;
                    else {
                        endWhiteSpace = offset;
                        break;
                    }
                }
            } else {
                int wordStart = offset;

                while (offset < endOffset) {
                    ch = text.charAt(offset);
                    if (MimeWhitespace.indexOf(ch) == -1) offset++;
                    else break;
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

    private String mimeDecodeWord(final String word) throws Exception {
        if (!word.startsWith(MimeEncodedMarker)) throw new Exception("Invalid RFC 2047 encoded-word: " + word);

        int charsetPos = word.indexOf('?', 2);
        if (charsetPos == -1) throw new Exception("Missing charset in RFC 2047 encoded-word: " + word);

        String charset = word.substring(2, charsetPos).toLowerCase();

        int encodingPos = word.indexOf('?', charsetPos + 1);
        if (encodingPos == -1) throw new Exception("Missing encoding in RFC 2047 encoded-word: " + word);

        String encoding = word.substring(charsetPos + 1, encodingPos);

        int encodedTextPos = word.indexOf(MimeEncodedEnd, encodingPos + 1);
        if (encodedTextPos == -1) throw new Exception("Missing encoded text in RFC 2047 encoded-word: " + word);

        String encodedText = word.substring(encodingPos + 1, encodedTextPos);

        if (encodedText.isEmpty()) return "";

        try {
            final ByteArrayOutputStream out = new ByteArrayOutputStream(encodedText.length());

            byte[] encodedData = encodedText.getBytes(MimeASCII);

            switch (encoding) {
                case MimeBase64:
                    copy(Base64.getDecoder().wrap(new ByteArrayInputStream(encodedData)), out);
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

    private void mimeDecodeQP(byte[] data, final OutputStream out) throws IOException {
        int off = 0;
        int length = data.length;
        int endOffset = off + length;

        while (off < endOffset) {
            byte ch = data[off++];

            if (ch == '_')
                out.write(' ');
            else if (ch == '=') {
                if (off + 1 >= endOffset) throw new IOException("Invalid quoted printable encoding; truncated escape sequence");

                byte b1 = data[off++];
                byte b2 = data[off++];

                if (b1 == '\r') if (b2 != '\n')
                    throw new IOException("Invalid quoted printable encoding; CR must be followed by LF");

                int c1 = hexToBinary(b1);
                int c2 = hexToBinary(b2);
                out.write((c1 << MimeShift) | c2);
            } else out.write(ch);
        }
    }
}
