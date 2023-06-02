package org.logdoc.fairhttp.service.tools;

import org.logdoc.fairhttp.service.api.helpers.Headers;
import org.logdoc.fairhttp.service.api.helpers.MimeType;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static org.logdoc.helpers.Bytes.copy;
import static org.logdoc.helpers.Texts.*;

public class MultiHelper {
    public static final byte CR = 0x0D, LF = 0x0A, DASH = 0x2D;
    public static final byte[] headerSeparator = {CR, LF, CR, LF}, fieldSeparator = {CR, LF}, streamEnd = {DASH, DASH}, boundaryPrefix = {CR, LF, DASH, DASH};

    private static final int MimeShift = Byte.SIZE / 2;
    private static final String MimeASCII = "US-ASCII",
            MimeBase64 = "B",
            MimeQuotedPrintable = "Q",
            MimeEncodedMarker = "=?",
            MimeEncodedEnd = "?=",
            MimeWhitespace = " \t\r\n";

    public static void process(final MultiHandler handler, final MimeType contentType, final byte[] input) throws Exception {
        processBoundary(handler, getBoundary(contentType), input);
    }

    public static void processBoundary(final MultiHandler handler, final byte[] boundary, final byte[] input) throws Exception {
        try (final InputStream is = new ByteArrayInputStream(input); final ByteArrayOutputStream sump = new ByteArrayOutputStream(1024 * 16)) {
            PartHeaders headers = null;

            while (nextBoundary(boundary, is, sump)) {
                if (headers != null) {
                    stripPart(sump.toByteArray(), headers, handler);
                    sump.reset();
                }

                headers = getParsedHeaders(readHeaders(is));
            }

            if (headers != null)
                stripPart(sump.toByteArray(), headers, handler);
        }
    }

    public static void stripPart(final byte[] bytes, final PartHeaders headers, final MultiHandler handler) throws Exception {
        final String fieldName = getFieldName(headers);
        final String fileName = getFileName(headers);
        final String subContentType = headers.getHeader(Headers.ContentType);
        if (subContentType == null)
            handler.part(fieldName, bytes, headers);
        else {
            if (subContentType.toLowerCase().startsWith(MimeType.MULTIPART.getBaseType()))
                processBoundary(handler, getBoundary(new MimeType(subContentType)), bytes);
            else if (!isEmpty(fileName))
                handler.part(fieldName, fileName, bytes, subContentType);
            else if (subContentType.startsWith("text/"))
                handler.part(fieldName, new String(bytes, StandardCharsets.UTF_8));
            else
                handler.part(fieldName, bytes, headers);
        }
    }

    public static byte[] getBoundary(final MimeType contentType) {
        String boundaryStr = contentType.getParameter("boundary");

        if (boundaryStr == null)
            throw new NullPointerException();

        return boundaryStr.getBytes(StandardCharsets.ISO_8859_1);
    }

    public static String getFileName(final PartHeaders headers) {
        return getFileName(headers.getHeader(Headers.ContentDisposition));
    }

    public static String getFileName(final String pContentDisposition) {
        String fileName = null;
        if (pContentDisposition != null) {
            String cdl = pContentDisposition.toLowerCase();
            if (cdl.startsWith(Headers.FormData) || cdl.startsWith(Headers.Attachment)) {
                ParameterParser parser = new ParameterParser();
                parser.setLowerCaseNames();
                final Map<String, String> params = parser.parse(pContentDisposition, ';');

                if (params.containsKey("filename"))
                    fileName = notNull(params.get("filename"));
            }
        }

        return fileName;
    }

    public static String getFieldName(final PartHeaders headers) {
        return getFieldName(headers.getHeader(Headers.ContentDisposition));
    }

    public static String getFieldName(final String pContentDisposition) {
        if (pContentDisposition != null && pContentDisposition.toLowerCase().startsWith(Headers.FormData)) {
            final ParameterParser parser = new ParameterParser();
            parser.setLowerCaseNames();
            return notNull(parser.parse(pContentDisposition, ';').get("name"));
        }

        return null;
    }


    public static PartHeaders getParsedHeaders(final String headerPart) {
        final int len = headerPart.length();
        final PartHeaders headers = new PartHeaders();
        int start = 0;
        for (; ; ) {
            int end = parseEndOfLine(headerPart, start);
            if (start == end)
                break;

            final StringBuilder header = new StringBuilder(headerPart.substring(start, end));
            start = end + 2;
            while (start < len) {
                int nonWs = start;
                while (nonWs < len) {
                    char c = headerPart.charAt(nonWs);
                    if (c != ' ' && c != '\t')
                        break;

                    ++nonWs;
                }
                if (nonWs == start)
                    break;

                end = parseEndOfLine(headerPart, nonWs);
                header.append(" ").append(headerPart, nonWs, end);
                start = end + 2;
            }

            parseHeaderLine(headers, header.toString());
        }
        return headers;
    }

    public static int parseEndOfLine(final String headerPart, final int idx) {
        int index = idx;

        for (; ; ) {
            int offset = headerPart.indexOf('\r', index);
            if (offset == -1 || offset + 1 >= headerPart.length())
                throw new IllegalStateException("Expected headers to be terminated by an empty line.");

            if (headerPart.charAt(offset + 1) == '\n')
                return offset;

            index = offset + 1;
        }
    }

    public static void parseHeaderLine(final PartHeaders headers, final String header) {
        final int colonOffset = header.indexOf(':');
        if (colonOffset == -1 || colonOffset >= header.length() - 1)
            return;

        headers.addHeader(header.substring(0, colonOffset).trim(), header.substring(colonOffset + 1).trim());
    }

    public static String readHeaders(final InputStream stream) throws IOException {
        int i = 0;
        byte b;

        try (final ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            int size = 0;
            while (i < headerSeparator.length) {
                b = (byte) stream.read();
                if (++size > 10240)
                    throw new IOException("Header section is too big");

                if (b == headerSeparator[i])
                    i++;
                else
                    i = 0;

                baos.write(b);
            }

            return baos.toString(StandardCharsets.UTF_8);
        }
    }


    public static boolean nextBoundary(final byte[] boundary, final InputStream stream, final OutputStream sump) throws IOException {
        final byte[] premarker = new byte[boundaryPrefix.length + boundary.length];
        final byte[] match = new byte[boundaryPrefix.length + boundary.length];
        System.arraycopy(boundaryPrefix, 0, match, 0, boundaryPrefix.length);
        System.arraycopy(boundary, 0, match, boundaryPrefix.length, boundary.length);

        if (stream.read(premarker, 0, premarker.length) != premarker.length)
            throw new IOException("EOF suddenly");

        while (!Arrays.equals(premarker, match)) {
            sump.write(premarker[0]);
            System.arraycopy(premarker, 1, premarker, 0, premarker.length - 1);
            premarker[premarker.length - 1] = (byte) stream.read();
        }

        final byte[] marker = new byte[]{(byte) stream.read(), (byte) stream.read()};

        if (Arrays.equals(marker, streamEnd))
            return false;
        else if (Arrays.equals(marker, fieldSeparator))
            return true;
        else
            throw new IOException("Unexpected characters follow a boundary");
    }

    private static class PartHeaders extends ConcurrentHashMap<String, List<String>> {

        public String getHeader(final String name) {
            final String nameLower = name.toLowerCase();

            return containsKey(nameLower) ? get(nameLower).get(0) : null;
        }

        public void addHeader(final String name, final String value) {
            final String nameLower = name.toLowerCase();

            putIfAbsent(nameLower, new ArrayList<>(2));
            get(nameLower).add(value);
        }
    }

    private static class ParameterParser {
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
            while ((i1 < i2) && (Character.isWhitespace(chars[i1])))
                i1++;

            while ((i2 > i1) && (Character.isWhitespace(chars[i2 - 1])))
                i2--;

            if (quoted && ((i2 - i1) >= 2) && (chars[i1] == '"') && (chars[i2 - 1] == '"')) {
                i1++;
                i2--;
            }

            return i2 > i1 ? new String(chars, i1, i2 - i1) : null;
        }

        private boolean isOneOf(char ch, final char[] charray) {
            for (char aCharray : charray)
                if (ch == aCharray)
                    return true;

            return false;
        }

        private String parseToken(final char[] terminators) {
            char ch;
            i1 = i2 = pos;
            while (hasChar()) {
                ch = chars[pos];
                if (isOneOf(ch, terminators))
                    break;

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
            if (str == null)
                return new HashMap<>();

            return parse(str.toCharArray(), separator);
        }

        private Map<String, String> parse(final char[] chars, char separator) {
            if (chars == null)
                return new HashMap<>();

            return parse(chars, chars.length, separator);
        }

        private Map<String, String> parse(final char[] chars, int length, char separator) {
            if (chars == null)
                return new HashMap<>();

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

                    if (paramValue != null)
                        paramValue = mimeDecodeText(paramValue);

                }
                if (hasChar() && (chars[pos] == separator))
                    pos++; // skip separator

                if ((paramName != null) && (paramName.length() > 0)) {
                    if (this.lowerCaseNames)
                        paramName = paramName.toLowerCase(Locale.ENGLISH);

                    params.put(paramName, paramValue);
                }
            }

            return params;
        }

    }

    private static String mimeDecodeText(final String text) {
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

    private static String mimeDecodeWord(final String word) throws Exception {
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

    private static void mimeDecodeQP(byte[] data, final OutputStream out) throws IOException {
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

    }


    public interface MultiHandler {

        void part(String fieldName, byte[] data, Map<String, List<String>> headers);
        void part(String fieldName, String data);
        void part(String fieldName, String fileName, byte[] data, String contentType);
    }
}
