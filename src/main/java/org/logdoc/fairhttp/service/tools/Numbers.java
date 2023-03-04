package org.logdoc.fairhttp.service.tools;

import static org.logdoc.fairhttp.service.tools.Strings.notNull;

/**
 * @author Denis Danilin | me@loslobos.ru
 * 07.02.2023 10:57
 * FairHttpService ☭ sweat and blood
 */
public class Numbers {
    public static long getLong(final Object value, final int radix) {
        try {
            return Long.parseLong(String.valueOf(value), radix);
        } catch (Exception ee) {
            return 0;
        }
    }

    public static long getLong(final Object value) {
        try {
            return Long.decode(String.valueOf(value));
        } catch (Exception e) {
            try {
                return Long.parseLong(value.toString().replaceAll("([^0-9-])", ""));
            } catch (Exception ee) {
                return 0;
            }
        }
    }

    public static int getInt(final Object parameter, final int max, final int min) {
        final int i = getInt(parameter);

        return i > max ? max : Math.max(i, min);
    }

    public static int getInt(final Object parameter) {
        final String param = notNull(parameter);
        try {
            return Integer.decode(param);
        } catch (Exception e) {
            try {
                return Integer.parseInt(param.replaceAll("([^0-9-])", ""));
            } catch (Exception ee) {
                return 0;
            }
        }
    }

    public static short getShort(final Object parameter) {
        final String param = notNull(parameter);
        try {
            return Short.decode(param);
        } catch (Exception e) {
            try {
                return Short.parseShort(param.replaceAll("([^0-9-])", ""));
            } catch (Exception ee) {
                return 0;
            }
        }
    }

    public static double getDouble(final Object parameter) {
        try {
            return Double.parseDouble(parameter.toString().replaceAll("([^0-9-\\.])", ""));
        } catch (Exception e) {
            return 0;
        }
    }

    public static float getFloat(final Object parameter) {
        try {
            return Float.parseFloat(parameter.toString().replaceAll("([^0-9-\\.])", ""));
        } catch (Exception e) {
            return 0;
        }
    }
}
