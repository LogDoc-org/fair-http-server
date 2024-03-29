package org.logdoc.fairhttp.service.api.helpers.endpoint;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Denis Danilin | me@loslobos.ru
 * 26.02.2023 13:51
 * FairHttpService ☭ sweat and blood
 */
public class Signature implements Comparable<Signature> {
    private static final Pattern searcher = Pattern.compile("/[:*]([^/]+)"), placeHold = Pattern.compile("/:([^/]+)"), greedHold = Pattern.compile("/\\*([^/]+)$");
    final String proper;
    private final int weight;
    private final String string;
    private final Pattern pattern;
    private final List<String> names;

    public Signature(final String raw) {
        proper = raw.trim().replaceAll("/{2,}", "/");

        Matcher phm = searcher.matcher(proper);
        if (phm.find()) { // patterned
            names = new ArrayList<>(4);

            do {
                names.add(phm.group(1));
            } while (phm.find());

            pattern = Pattern.compile("^" + proper.replaceAll(placeHold.pattern(), "/([^/]+)").replaceAll(greedHold.pattern(), "/(.*)") + "$");
            string = pattern.pattern();
        } else {
            string = proper;
            pattern = null;
            names = null;
        }

        int cnt = 0;
        for (int i = 0; i < string.length(); i++)
            if (string.charAt(i) == '/')
                cnt++;

        weight = cnt;
    }

    boolean matches(final String hardPath) {
        if (pattern != null)
            return pattern.matcher(hardPath).matches();

        return string.equals(hardPath);
    }

    Map<String, String> values(final String hardPath) {
        if (pattern == null)
            return Collections.emptyMap();

        final Matcher m = pattern.matcher(hardPath);

        final Map<String, String> values = new HashMap<>(4);

        if (m.matches())
            for (int i = 0; i < m.groupCount(); i++)
                values.put(names.get(i), m.group(i + 1));

        return values;
    }

    @Override
    public int compareTo(final Signature o) {
        final int res = Integer.compare(weight, o.weight);

        return res == 0 ? string.compareTo(o.string) : res;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final Signature signature = (Signature) o;
        return weight == signature.weight && string.equals(signature.string);
    }

    @Override
    public int hashCode() {
        return Objects.hash(weight, string);
    }

    @Override
    public String toString() {
        return string;
    }

    boolean equalString(final String signature) {
        return string.equals(signature);
    }
}
