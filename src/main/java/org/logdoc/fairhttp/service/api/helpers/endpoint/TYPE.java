package org.logdoc.fairhttp.service.api.helpers.endpoint;

import org.logdoc.fairhttp.service.http.Http;
import org.logdoc.fairhttp.service.tools.Numbers;
import org.logdoc.fairhttp.service.tools.Strings;

/**
 * @author Denis Danilin | me@loslobos.ru
 * 26.02.2023 15:18
 * FairHttpService ☭ sweat and blood
 */
public enum TYPE {
    Character(java.lang.Character.class, "."),
    chr(char.class, "."),

    Integer(java.lang.Integer.class, "\\d+"),
    intgr(int.class, "\\d+"),

    Boolean(java.lang.Boolean.class, "true|false|TRUE|FALSE|0|1"),
    bool(boolean.class, "true|false|TRUE|FALSE|0|1"),

    Byte(java.lang.Byte.class, "[a-zA-Z0-9]+"),
    byt(byte.class, "[a-zA-Z0-9]+"),

    Short(java.lang.Short.class, "[0-9]+"),
    shrt(short.class, "[0-9]+"),

    Long(java.lang.Long.class, "[0-9]+"),
    lng(long.class, "[0-9]+"),

    Double(java.lang.Double.class, "[0-9.]+"),
    dbl(double.class, "[0-9.]+"),

    Float(java.lang.Float.class, "[0-9.]+"),
    flt(float.class, "[0-9.]+"),

    String(java.lang.String.class, "[^/]+"),

    Request(Http.Request.class, ".*"),

    Custom(Void.class, ".*");

    public final Class<?> ofClass;
    public final String pattern;

    TYPE(final Class<?> ofClass, final java.lang.String pattern) {
        this.ofClass = ofClass;
        this.pattern = pattern;
    }

    public static TYPE ofSign(final String sign) {
        if ("char".equals(sign))
            return chr;

        if ("int".equals(sign))
            return intgr;

        if ("boolean".equals(sign))
            return bool;

        if ("byte".equals(sign))
            return byt;

        if ("short".equals(sign))
            return shrt;

        if ("long".equals(sign))
            return lng;

        if ("double".equals(sign))
            return dbl;

        if ("float".equals(sign))
            return flt;

        return valueOf(sign);
    }

    public Object stringAs(final String v, final boolean optional, final String defValue) {
        if (v == null || v.length() == 0) {
            if (!optional)
                throw new NullPointerException();

            if (defValue != null)
                return stringAs(defValue, false, null);

            return null;
        }

        switch (this) {
            case bool:
            case Boolean:
                return Strings.getBoolean(v);
            case byt:
            case Byte:
                return java.lang.Byte.parseByte(v.substring(0, 1));
            case Character:
            case chr:
                return v.charAt(0);
            case dbl:
            case Double:
                return Numbers.getDouble(v);
            case Float:
            case flt:
                return Numbers.getFloat(v);
            case Integer:
            case intgr:
                return Numbers.getInt(v);
            case lng:
            case Long:
                return Numbers.getLong(v);
            case Short:
            case shrt:
                return Numbers.getShort(v);
        }

        return v;
    }
}
