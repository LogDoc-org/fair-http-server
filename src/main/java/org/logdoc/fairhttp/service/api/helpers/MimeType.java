package org.logdoc.fairhttp.service.api.helpers;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * @author javax.mail
 * 27.12.2022 13:53
 * fairhttp ☭ sweat and blood
 *
 * Borrowed from javax.mail
 */
public class MimeType {

    private static final String TSPECIALS = "()<>@,;:/[]?=\\\"";
    public static MimeType FORM = of("application/x-www-form-urlencoded");
    public static MimeType JSON = of("application/json");
    public static MimeType TEXTPLAIN = of("text/plain");
    public static MimeType TEXTHTML = of("text/html");
    public static MimeType BINARY = of("application/octet-stream");
    public static MimeType XML = of("application/xml");
    public static MimeType MULTIPART = of("multipart/form-data");
    private String primaryType;
    private String subType;
    private ParamsList parameters;

    public MimeType() {
        primaryType = "application";
        subType = "*";
        parameters = new ParamsList();
    }

    public MimeType(final String rawdata) throws Exception {
        parse(rawdata);
    }

    public MimeType(final String primary, final String sub) throws Exception {
        if (isValidToken(primary))
            primaryType = primary.toLowerCase();
        else
            throw new Exception("Primary type is invalid.");

        if (isValidToken(sub))
            subType = sub.toLowerCase();
        else
            throw new Exception("Sub type is invalid.");

        parameters = new ParamsList();
    }

    private static MimeType of(final String knownSpec) {
        final MimeType mt = new MimeType();
        mt.primaryType = knownSpec.substring(0, knownSpec.indexOf('/'));
        mt.subType = knownSpec.substring(knownSpec.indexOf('/') + 1);
        mt.setParameter("charset", "UTF-8");

        return mt;
    }

    private static boolean isTokenChar(final char c) {
        return ((c > 040) && (c < 0177)) && (TSPECIALS.indexOf(c) < 0);
    }

    public static MimeType guessMime(final int[] head16bytes) {
        if (head16bytes[0] == '<' && head16bytes[1] == 's' && head16bytes[2] == 'v' && head16bytes[3] == 'g' && head16bytes[4] == ' ')
            return of("image/svg+xml");

        if (head16bytes[0] == 'G' && head16bytes[1] == 'I' && head16bytes[2] == 'F' && head16bytes[3] == '8')
            return of("image/gif");

        if (head16bytes[0] == '#' && head16bytes[1] == 'd' && head16bytes[2] == 'e' && head16bytes[3] == 'f')
            return of("image/x-bitmap");

        if (head16bytes[0] == 0xCA && head16bytes[1] == 0xFE && head16bytes[2] == 0xBA && head16bytes[3] == 0xBE)
            return of("application/java-vm");

        if (head16bytes[0] == 0xAC && head16bytes[1] == 0xED)
            return of("application/x-java-serialized-object");

        if (head16bytes[0] == 0x2E && head16bytes[1] == 0x73 && head16bytes[2] == 0x6E && head16bytes[3] == 0x64)
            return of("audio/basic");  // .au BE

        if (head16bytes[0] == 0x64 && head16bytes[1] == 0x6E && head16bytes[2] == 0x73 && head16bytes[3] == 0x2E)
            return of("audio/basic");  // .au LE

        if (head16bytes[0] == 'R' && head16bytes[1] == 'I' && head16bytes[2] == 'F' && head16bytes[3] == 'F')
            return of("audio/x-wav");

        if (head16bytes[0] == '<') {
            if (head16bytes[1] == '!'
                    || ((head16bytes[1] == 'h' && (head16bytes[2] == 't' && head16bytes[3] == 'm' && head16bytes[4] == 'l' ||
                    head16bytes[2] == 'e' && head16bytes[3] == 'a' && head16bytes[4] == 'd') ||
                    (head16bytes[1] == 'b' && head16bytes[2] == 'o' && head16bytes[3] == 'd' && head16bytes[4] == 'y'))) ||
                    ((head16bytes[1] == 'H' && (head16bytes[2] == 'T' && head16bytes[3] == 'M' && head16bytes[4] == 'L' ||
                            head16bytes[2] == 'E' && head16bytes[3] == 'A' && head16bytes[4] == 'D') ||
                            (head16bytes[1] == 'B' && head16bytes[2] == 'O' && head16bytes[3] == 'D' && head16bytes[4] == 'Y'))))
                return of("text/html");

            if (head16bytes[1] == '?' && head16bytes[2] == 'x' && head16bytes[3] == 'm' && head16bytes[4] == 'l' && head16bytes[5] == ' ')
                return of("application/xml");
        }

        if (head16bytes[0] == 0xef && head16bytes[1] == 0xbb && head16bytes[2] == 0xbf && head16bytes[3] == '<' && head16bytes[4] == '?' && head16bytes[5] == 'x')
            return of("application/xml");

        if (head16bytes[0] == '!' && head16bytes[1] == ' ' && head16bytes[2] == 'X' && head16bytes[3] == 'P' && head16bytes[4] == 'M' && head16bytes[5] == '2')
            return of("image/x-pixmap");

        if (head16bytes[0] == 0xfe && head16bytes[1] == 0xff && head16bytes[2] == 0 && head16bytes[3] == '<' && head16bytes[4] == 0 && head16bytes[5] == '?' && head16bytes[6] == 0 && head16bytes[7] == 'x')
            return of("application/xml");

        if (head16bytes[0] == 0xff && head16bytes[1] == 0xfe && head16bytes[2] == '<' && head16bytes[3] == 0 && head16bytes[4] == '?' && head16bytes[5] == 0 && head16bytes[6] == 'x' && head16bytes[7] == 0)
            return of("application/xml");

        if (head16bytes[0] == 137 && head16bytes[1] == 80 && head16bytes[2] == 78 && head16bytes[3] == 71 && head16bytes[4] == 13 && head16bytes[5] == 10 && head16bytes[6] == 26 && head16bytes[7] == 10)
            return of("image/png");

        if (head16bytes[0] == 0xFF && head16bytes[1] == 0xD8 && head16bytes[2] == 0xFF) {
            if (head16bytes[3] == 0xE0 || head16bytes[3] == 0xEE)
                return of("image/jpeg");

            if (head16bytes[3] == 0xE1 && head16bytes[6] == 'E' && head16bytes[7] == 'x' && head16bytes[8] == 'i' && head16bytes[9] == 'f' && head16bytes[10] == 0)
                return of("image/jpeg");
        }

        if (head16bytes[0] == 0x00 && head16bytes[1] == 0x00 && head16bytes[2] == 0xfe && head16bytes[3] == 0xff && head16bytes[4] == 0 && head16bytes[5] == 0 && head16bytes[6] == 0 && head16bytes[7] == '<' &&
                head16bytes[8] == 0 && head16bytes[9] == 0 && head16bytes[10] == 0 && head16bytes[11] == '?' &&
                head16bytes[12] == 0 && head16bytes[13] == 0 && head16bytes[14] == 0 && head16bytes[15] == 'x')
            return of("application/xml");

        if (head16bytes[0] == 0xff && head16bytes[1] == 0xfe && head16bytes[2] == 0x00 && head16bytes[3] == 0x00 && head16bytes[4] == '<' && head16bytes[5] == 0 && head16bytes[6] == 0 && head16bytes[7] == 0 &&
                head16bytes[8] == '?' && head16bytes[9] == 0 && head16bytes[10] == 0 && head16bytes[11] == 0 &&
                head16bytes[12] == 'x' && head16bytes[13] == 0 && head16bytes[14] == 0 && head16bytes[15] == 0)
            return of("application/xml");

        final byte[] bytes = new byte[head16bytes.length];
        for (int i = 0; i < bytes.length; i++) bytes[i] = (byte) head16bytes[i];

        final String s = new String(bytes, StandardCharsets.UTF_8).replaceAll("\\s", "");

        if (s.startsWith("{\"") || s.startsWith("[{") || s.startsWith("[\"") || s.matches("^\\[\\d.*"))
            return of("application/json");

        return BINARY;
    }

    private void parse(final String rawdata) throws Exception {
        int slashIndex = rawdata.indexOf('/');
        int semIndex = rawdata.indexOf(';');

        if (slashIndex < 0 && semIndex < 0)
            throw new Exception("Unable to find a sub type.");

        if (slashIndex < 0)
            throw new Exception("Unable to find a sub type.");

        final String r = rawdata.substring(0, slashIndex).trim().toLowerCase();

        if (semIndex < 0) {
            primaryType = r;
            subType = rawdata.substring(slashIndex + 1).trim().toLowerCase();
            parameters = new ParamsList();
        } else if (slashIndex < semIndex) {
            primaryType = r;
            subType = rawdata.substring(slashIndex + 1, semIndex).trim().toLowerCase();
            parameters = new ParamsList(rawdata.substring(semIndex));
        } else
            throw new Exception("Unable to find a sub type.");

        if (!isValidToken(primaryType))
            throw new Exception("Primary type is invalid.");

        if (!isValidToken(subType))
            throw new Exception("Sub type is invalid.");
    }

    public String getPrimaryType() {
        return primaryType;
    }

    public void setPrimaryType(final String primary) throws Exception {
        if (!isValidToken(primaryType))
            throw new Exception("Primary type is invalid.");

        primaryType = primary.toLowerCase();
    }

    public String getSubType() {
        return subType;
    }

    public void setSubType(final String sub) throws Exception {
        if (!isValidToken(subType))
            throw new Exception("Sub type is invalid.");

        subType = sub.toLowerCase();
    }

    public ParamsList getParameters() {
        return parameters;
    }

    public String getParameter(final String name) {
        return parameters.get(name);
    }

    public void setParameter(final String name, final String value) {
        parameters.set(name, value);
    }

    public void removeParameter(final String name) {
        parameters.remove(name);
    }

    public String toString() {
        return getBaseType() + parameters.toString();
    }

    public String getBaseType() {
        return primaryType + "/" + subType;
    }

    public boolean match(final MimeType type) {
        return primaryType.equals(type.getPrimaryType())
                && (subType.equals("*")
                || type.getSubType().equals("*")
                || (subType.equals(type.getSubType())));
    }

    public boolean match(final String rawdata) throws Exception {
        return match(new MimeType(rawdata));
    }

    private boolean isValidToken(final String s) {
        int len = s.length();
        if (len > 0) {
            for (int i = 0; i < len; ++i) {
                char c = s.charAt(i);
                if (!isTokenChar(c)) {
                    return false;
                }
            }
            return true;
        } else {
            return false;
        }
    }

    public static class ParamsList {
        private static final String TSPECIALS = "()<>@,;:/[]?=\\\"";
        private final Map<String, String> parameters;

        public ParamsList() {
            parameters = new HashMap<>();
        }

        public ParamsList(final String parameterList) throws Exception {
            parameters = new HashMap<>();

            parse(parameterList);
        }

        private static boolean isTokenChar(char c) {
            return ((c > 040) && (c < 0177)) && (TSPECIALS.indexOf(c) < 0);
        }

        private static int skipWhiteSpace(String rawdata, int i) {
            int length = rawdata.length();

            while ((i < length) && Character.isWhitespace(rawdata.charAt(i)))
                i++;

            return i;
        }

        private static String quote(String value) {
            boolean needsQuotes = false;

            int length = value.length();
            for (int i = 0; (i < length) && !needsQuotes; i++) {
                needsQuotes = !isTokenChar(value.charAt(i));
            }

            if (needsQuotes) {
                final StringBuilder buffer = new StringBuilder();
                buffer.ensureCapacity((int) (length * 1.5));

                buffer.append('"');

                for (int i = 0; i < length; ++i) {
                    char c = value.charAt(i);
                    if ((c == '\\') || (c == '"'))
                        buffer.append('\\');
                    buffer.append(c);
                }

                buffer.append('"');

                return buffer.toString();
            }

            return value;
        }

        private static String unquote(String value) {
            int valueLength = value.length();
            final StringBuilder buffer = new StringBuilder();
            buffer.ensureCapacity(valueLength);

            boolean escaped = false;
            for (int i = 0; i < valueLength; ++i) {
                char currentChar = value.charAt(i);
                if (!escaped && (currentChar != '\\')) {
                    buffer.append(currentChar);
                } else if (escaped) {
                    buffer.append(currentChar);
                    escaped = false;
                } else
                    escaped = true;
            }

            return buffer.toString();
        }

        protected void parse(final String parameterList) throws Exception {
            if (parameterList == null)
                return;

            int length = parameterList.length();

            if (length == 0)
                return;

            int i;
            char c;
            for (i = skipWhiteSpace(parameterList, 0);
                 i < length && (c = parameterList.charAt(i)) == ';';
                 i = skipWhiteSpace(parameterList, i)) {
                int lastIndex;
                String name;
                String value;

                i++;
                i = skipWhiteSpace(parameterList, i);

                if (i >= length)
                    return;

                lastIndex = i;
                while ((i < length) && isTokenChar(parameterList.charAt(i)))
                    i++;

                name = parameterList.substring(lastIndex, i).toLowerCase();

                i = skipWhiteSpace(parameterList, i);

                if (i >= length || parameterList.charAt(i) != '=')
                    throw new Exception("Couldn't find the '=' that separates a parameter name from its value.");

                i++;
                i = skipWhiteSpace(parameterList, i);

                if (i >= length)
                    throw new Exception("Couldn't find a value for parameter named " + name);

                c = parameterList.charAt(i);
                if (c == '"') {
                    i++;
                    if (i >= length)
                        throw new Exception("Encountered unterminated quoted parameter value.");

                    lastIndex = i;

                    while (i < length) {
                        c = parameterList.charAt(i);
                        if (c == '"')
                            break;
                        if (c == '\\')
                            i++;
                        i++;
                    }

                    if (c != '"')
                        throw new Exception("Encountered unterminated quoted parameter value.");

                    value = unquote(parameterList.substring(lastIndex, i));
                    i++;
                } else if (isTokenChar(c)) {
                    lastIndex = i;
                    while (i < length && isTokenChar(parameterList.charAt(i)))
                        i++;
                    value = parameterList.substring(lastIndex, i);
                } else
                    throw new Exception("Unexpected character encountered at index " + i);

                parameters.put(name, value);
            }

            if (i < length)
                throw new Exception("More characters encountered in input than expected.");
        }

        public int size() {
            return parameters.size();
        }

        public boolean isEmpty() {
            return parameters.isEmpty();
        }

        public String get(String name) {
            return parameters.get(name.trim().toLowerCase());
        }

        public void set(String name, String value) {
            parameters.put(name.trim().toLowerCase(), value);
        }

        public void remove(String name) {
            parameters.remove(name.trim().toLowerCase());
        }

        public Collection<String> getNames() {
            return parameters.keySet();
        }

        public String toString() {
            final StringBuilder s = new StringBuilder();

            for (final String key : parameters.keySet())
                s.append("; ").append(key).append('=').append(quote(parameters.get(key)));

            return s.toString();
        }
    }

}
