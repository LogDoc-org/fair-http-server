package org.logdoc.fairhttp.service;

import org.logdoc.helpers.Texts;

import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.logdoc.helpers.Texts.hexStringToByteArray;
import static org.logdoc.helpers.Texts.isEmpty;

/**
 * @author Denis Danilin | me@loslobos.ru
 * 26.04.2023 16:23
 * fair-http-server ☭ sweat and blood
 */
public class Test {
    private static final String routes =
            "         /v1/api/confirm\n" +
                    "/v1/api/signup\n" +
                    "/v1/api/:id/signin\n" +
                    "/v1/api/:token/test\n" +
                    "/v1/api/token/refresh/*\n" +
                    "/v1/:id/:name/dashboard/*file  \n" +
                    "  /v1/api/:profile/voided1/*name\n" +
                    "/v1/api/profile/voided2/*id\n" +
                    "/:v/api/profile/voided3";

    private static final Pattern searcher = Pattern.compile("/[:*]([^/]+)"), placeHold = Pattern.compile("/:([^/]+)"), greedHold = Pattern.compile("/\\*([^/]+)$");
    private final static String Characters = "0123456789AaBbCcDdEeFfGgHhIiJjKklLMmNnOoPpQqRrSsTtUuVvWwXxYyZz._-";

    public static void main(final String[] args) throws Exception {
        final String a =
//"8y2e3qQCPk9C6UJweQoTgXr9pM9wgBKQafUtbmzDEL9AVEbbfXprCCbfZtbMqiNoCy6d7ET9cFdQDtmChdZ6UvE6c";
        "f32d9edea4023e4f42e94270790a13817afda4cf7080129069f52d6e6cc310bf405446db7d7a6b0826df66d6ccaa23680b2e9dec44fd7057500ed98285d67a52f13a";
//"47ufaRDvdLmnP9eEViDddrrHny";
//"cmlnaHQgZXh0ZW5zaW9u";
//                "OJUWO2DUEBSXQ5DFNZZWS33O";

//        final byte[] aa = Base64.getDecoder().decode(a.getBytes(StandardCharsets.US_ASCII));

        Files.write(Paths.get("/tmp/file.bin"), hexStringToByteArray(a));


//        System.out.println(Decode(a));
    }

    public static byte[] Decode(String inString0) throws Exception {
        final char[] inString = inString0.toCharArray();

        if (inString.length % 2 != 0)
            throw new Exception("Invalid string length: " + inString0.length());

        try (final ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {

            for (int i = 0; i < inString.length; i += 2) {
                char ch = inString[i];
                char ch2 = inString[i + 1];
                int index = Characters.indexOf(ch);
                int index2 = Characters.indexOf(ch2);

                if (index < 0 || index2 < 0)
                    throw new Exception("Invalid character: " + ch);

                int val = 0;
                val += (index * Characters.length()) + index2;

                bytes.write((byte) val);
            }

            return bytes.toByteArray();
        }
    }

    public static void main1(final String[] args) throws Exception {
        Arrays.stream(routes.split("\n"))
                .map(Texts::notNull)
                .forEach(raw -> {
                    String proper;
                    int weight;
                    String string;
                    Pattern pattern;
                    List<String> names;

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

                    System.out.println("\n" + raw);
                    if (!isEmpty(names)) System.out.println("\tNames: " + names);
                    System.out.println("\tWeight: " + weight);
                    System.out.println("\tMatcher: " + string);
                });
    }
/*

    private static List<Preform> readPreforms(final byte[] content) {
        final CharBuf cb = new CharBuf(content, '#');
        final List<Preform> out = new ArrayList<>(16);

        while (cb.markNonEmpty()) {
            cb.pos2mark().markEmpty();
            final Preform p = new Preform();

            if (!p.method(cb.marked())) p.crook();

            if (cb.markNonEmpty()) {
                cb.pos2mark().markEmpty();

                if (!p.path(cb.marked())) p.crook();

                if (cb.markNonEmpty()) {
                    cb.pos2mark();

                    if (cb.hasFurther('(', true))
                        cb.mark(')', true);
                    else
                        cb.markEmpty();

                    if (!p.invoker(cb.marked())) p.crook();
                } else
                    p.crook();
            } else
                p.crook();

            if (!p.isCrooked())
                out.add(p);
        }

        return out;
    }

    private static Pair<Method, String> invokerSignature(final String inv) {
        int idx;
        Method method = null;

        final String args = (idx = inv.indexOf('(')) != -1 ? notNull(inv.substring(idx).replace("(", "").replace(")", "")) : null;
        final String sign = idx != -1 ? notNull(inv.substring(0, idx)) : notNull(inv);

        if (isEmpty(inv) || (idx = inv.lastIndexOf('.')) == -1)
            return null;

        final String cls = sign.substring(0, idx);
        final String mth = sign.substring(idx + 1);

        try {
            final Class<?> c = Class.forName(cls);

            method = Reflects.findMethods(c)
                    .stream()
                    .filter(m -> m.getName().equals(mth) && m.trySetAccessible() && (Response.class.isAssignableFrom(m.getReturnType()) || CompletionStage.class.isAssignableFrom(m.getReturnType())))
                    .findAny()
                    .orElse(null);
        } catch (final Exception e) {
            p(e.getMessage());
            e.printStackTrace();
        }

        return Pair.create(method, args);
    }

    public static void main(final String[] argz) throws Exception {
        readPreforms(routes.getBytes(StandardCharsets.UTF_8))
                .stream()
                .peek(Test::p)
                .map(p -> {
                    final Pair<Method, String> sign = invokerSignature(p.invoker);

                    if (sign != null && sign.first != null)
                        return new Exposed(p.method, p.path, sign.first, notNull(sign.second, "()"));

                    return null;
                })
                .filter(Objects::nonNull)
                .peek(Test::p)
                .map(e -> {
                    final Argued a = new Argued(e);

                    fillArgs(a, e.args);

                    return a.invMethod == null
                            ? null
                            : a;
                })
                .filter(Objects::nonNull)
                .peek(Test::p)
                .map(a -> {
                    final ARequestInvoker invoker;

                    if (isEmpty(a.args))
                        invoker = Response.class.isAssignableFrom(a.invMethod.getReturnType())
                                ? new DirectInvoker(a.invMethod)
                                : new DirectUnresolvingInvoker(a.invMethod, )
                })
                .collect(Collectors.toList());
    }

    private static void fillArgs(final Argued a, final String args0) {
        final String args = notNull(args0.replaceAll("\\s", "").replace("()", ""));

        final int count;
        if (isEmpty(args) || args.equals("()"))
            count = 0;
        else {
            if (args.contains(","))
                count = args.split(",").length;
            else
                count = 1;
        }

        final boolean hasRq = args.toLowerCase(Locale.ROOT).contains("[request]");

        final String mn = a.invMethod.getName();

        final List<Method> possible = Reflects.findMethods(a.invMethod.getDeclaringClass())
                .stream()
                .filter(m -> m.getName().equals(mn) && (m.getParameterCount() == count || m.getParameterCount() == count + (hasRq ? 0 : 1)))
                .collect(Collectors.toList());

        if (possible.isEmpty()) {
            a.invMethod = null;
            return;
        }

        final List<String> defs = count == 0 ? new ArrayList<>(0) : new ArrayList<>(Arrays.asList(args.split(",")));
        final List<Argued.Arg<?>> solved = new ArrayList<>(0);

        Method matched = null;

        for (final Method cm : possible) {
            final List<Class<?>> given = Arrays.asList(cm.getParameterTypes());

            if (!hasRq)
                for (int i = 0; i < given.size(); i++)
                    if (Request.class.isAssignableFrom(given.get(i))) {
                        defs.add(i, "[Request]");
                        break;
                    }

            if (defs.size() != given.size())
                continue;

            matched = cm;

            for (int i = 0; i < defs.size(); i++) {
                final String d = notNull(defs.get(i));
                final Class<?> g = given.get(i);

                if (d.toLowerCase(Locale.ROOT).contains("[request]") && Request.class.isAssignableFrom(g))
                    solved.add(new Argued.Arg<>(Request.class, (req, pathMap) -> req, null, i));
                else if (d.toLowerCase(Locale.ROOT).contains("[body]"))
                    solved.add(new Argued.Arg<>(g, (req, pathMap) -> Json.fromJson(req.body().asJson(), g), null, i));
                else {
                    try {
                        final BiFunction<Request, Map<String, String>, String> getString;
                        final String name;

                        if (d.contains("[")) {
                            final Argued.Arg.Cnt c = Argued.Arg.Cnt.valueOf(capitalize(d.substring(d.indexOf('[') + 1, d.indexOf(']'))));
                            name = notNull(d.substring(0, d.indexOf('[')));

                            if (isEmpty(name))
                                throw new Exception();

                            switch (c) {
                                case Cookie:
                                    getString = (req, pathMap) -> req.cookie(name);
                                    break;
                                case Path:
                                    getString = (req, pathMap) -> pathMap.get(name);
                                    break;
                                case Query:
                                    getString = (req, pathMap) -> req.queryParam(name);
                                    break;
                                case Form:
                                    getString = (req, pathMap) -> req.body().formField(name);
                                    break;
                                default:
                                    throw new Exception();
                            }
                        } else {
                            name = notNull(d);

                            getString = (req, pathMap) -> {
                                String s;

                                if ((s = pathMap.get(name)) != null)
                                    return s;

                                if ((s = req.body().formField(name)) != null)
                                    return s;

                                if ((s = req.queryParam(name)) != null)
                                    return s;

                                return req.cookie(name);
                            };
                        }
                        final BiFunction<Request, Map<String, String>, ?> magic;
                        final BiFunction<String, Function<String, ?>, ?> isNil = (s, pass) -> s == null ? null : pass.apply(s);

                        if (String.class.isAssignableFrom(g))
                            magic = getString;
                        else {
                            if (g.equals(short.class))
                                magic = (req, pathMap) -> getShort(getString.apply(req, pathMap));
                            else if (g.equals(int.class))
                                magic = (req, pathMap) -> getInt(getString.apply(req, pathMap));
                            else if (g.equals(long.class))
                                magic = (req, pathMap) -> getLong(getString.apply(req, pathMap));
                            else if (g.equals(double.class))
                                magic = (req, pathMap) -> getDouble(getString.apply(req, pathMap));
                            else if (g.equals(float.class))
                                magic = (req, pathMap) -> getFloat(getString.apply(req, pathMap));
                            else if (g.equals(boolean.class))
                                magic = (req, pathMap) -> getBoolean(getString.apply(req, pathMap));
                            else if (g.equals(char.class))
                                magic = (req, pathMap) -> {
                                    final String s = getString.apply(req, pathMap);
                                    return s.isEmpty() ? '\0' : s.charAt(0);
                                };
                            else if (g.equals(byte.class))
                                magic = (req, pathMap) -> {
                                    final String s = getString.apply(req, pathMap);
                                    return s.isEmpty() ? '\0' : s.getBytes(StandardCharsets.UTF_8)[0];
                                };
                            else if (Long.class.isAssignableFrom(g))
                                magic = (req, pathMap) -> isNil.apply(getString.apply(req, pathMap), Digits::getLong);
                            else if (Integer.class.isAssignableFrom(g))
                                magic = (req, pathMap) -> isNil.apply(getString.apply(req, pathMap), Digits::getInt);
                            else if (Short.class.isAssignableFrom(g))
                                magic = (req, pathMap) -> isNil.apply(getString.apply(req, pathMap), Digits::getShort);
                            else if (Float.class.isAssignableFrom(g))
                                magic = (req, pathMap) -> isNil.apply(getString.apply(req, pathMap), Digits::getFloat);
                            else if (Double.class.isAssignableFrom(g))
                                magic = (req, pathMap) -> isNil.apply(getString.apply(req, pathMap), Digits::getDouble);
                            else if (Boolean.class.isAssignableFrom(g))
                                magic = (req, pathMap) -> isNil.apply(getString.apply(req, pathMap), Texts::getBoolean);
                            else if (Character.class.isAssignableFrom(g))
                                magic = (req, pathMap) -> isNil.apply(getString.apply(req, pathMap), s -> s.isEmpty() ? null : s.charAt(0));
                            else if (Byte.class.isAssignableFrom(g))
                                magic = (req, pathMap) -> isNil.apply(getString.apply(req, pathMap), s -> s.isEmpty() ? null : s.getBytes(StandardCharsets.UTF_8)[0]);
                            else
                                magic = (req, pathMap) -> {
                                    try {
                                        return Json.fromJson(Json.parse(getString.apply(req, pathMap)), g);
                                    } catch (final Exception e) {
                                        e.printStackTrace();
                                    }

                                    return null;
                                };
                        }

                        solved.add(new Argued.Arg<>(g, magic, name, i));
                    } catch (final Exception ignore) {
                        matched = null;
                        break;
                    }
                }
            }

            if (matched != null)
                break;
        }

        if (matched == null) {
            a.invMethod = null;
            return;
        }

        a.args.addAll(solved);
    }

    private static void p(final Object o) {
        System.out.println(o);
    }

    private static class CharBuf {
        private final char[] data;
        private int pos, mark;

        CharBuf(final String content) {
            this(content.getBytes(StandardCharsets.UTF_8));
        }

        CharBuf(final byte[] content) {
            this(content, '\0');
        }

        CharBuf(final byte[] content, final char commentChar) {
            String s = new String(content, StandardCharsets.UTF_8);

            if (commentChar != '\0') {
                int idx, idx2;
                while ((idx = s.indexOf(commentChar)) != -1) {
                    idx2 = s.indexOf('\n', idx + 1);

                    s = s.substring(0, idx) + (idx2 != -1 ? s.substring(idx2) : "");
                }
            }

            this.data = s.toCharArray();
//            pos = -1;
            mark = -1;
        }

        boolean markNonEmpty() {
            for (int i = pos; i < data.length; i++)
                if (!Character.isWhitespace(data[i])) {
                    mark = i;
                    return true;
                }

            return false;
        }

        void markEmpty() {
            for (int i = pos; i < data.length; i++)
                if (Character.isWhitespace(data[i])) {
                    mark = i;
                    return;
                }

            mark = data.length;
        }

        CharBuf pos2mark() {
            pos = Math.max(mark, pos);
            mark = -1;

            return this;
        }

        String marked() {
            try {
                return mark > pos ? new String(Arrays.copyOfRange(data, pos, mark)) : null;
            } finally {
                pos = mark;
            }
        }

        public boolean hasFurther(final char ch, final boolean breakOnWhitespace) {
            for (int i = pos; i < data.length; i++)
                if (data[i] == ch)
                    return true;
                else if (breakOnWhitespace && Character.isWhitespace(data[i]))
                    return false;

            return false;
        }

        public void mark(final char c, final boolean shiftAfter) {
            for (int i = pos + 1; i < data.length; i++)
                if (data[i] == c) {
                    mark = i;
                    if (shiftAfter)
                        mark++;
                    return;
                }

            mark = data.length;
        }
    }

    static class Argued {
        String method, path;
        Method invMethod;
        SortedSet<Arg<?>> args;

        public Argued(final Exposed e) {
            this.method = e.method;
            this.path = e.path;
            this.invMethod = e.invMethod;

            args = new TreeSet<>();
        }

        public void arg(final Arg<?> arg) {
            if (arg != null)
                args.add(arg);
        }

        @Override
        public String toString() {
            return "Argued{" +
                    "method='" + method + '\'' +
                    ", path='" + path + '\'' +
                    ", invMethod=" + invMethod.getName() +
                    ", args=" + args +
                    '}';
        }

        static class Arg<T> implements Comparable<Arg<T>> {
            public Class<T> cls;
            public String refName;
            public int order;
            public BiFunction<Request, Map<String, String>, T> magic;

            public Arg() {
            }

            @SuppressWarnings("unchecked")
            public Arg(final Class<T> cls, final BiFunction<Request, Map<String, String>, ?> magic, final String refName, final int order) {
                this.cls = cls;
                this.magic = (BiFunction<Request, Map<String, String>, T>) magic;
                this.refName = refName;
                this.order = order;
            }

            @Override
            public String toString() {
                return "Arg{" +
                        "cls=" + cls.getSimpleName() +
                        (refName != null ? ", refName='" + refName + '\'' : "") +
                        '}';
            }

            @Override
            public int compareTo(final Arg<T> o) {
                return Integer.compare(order, o.order);
            }

            @Override
            public boolean equals(final Object o) {
                if (this == o) return true;
                if (!(o instanceof Arg)) return false;
                final Arg<?> arg = (Arg<?>) o;
                return order == arg.order;
            }

            @Override
            public int hashCode() {
                return Objects.hash(order);
            }

            public enum Cnt {Form, Body, Query, Cookie, Path, Unknown, Request}
        }
    }

    private static class Exposed {
        private final String method, path;
        private final Method invMethod;
        private final String args;

        public Exposed(final String method, final String path, final Method invMethod, final String args) {
            this.method = method;
            this.path = path;
            this.invMethod = invMethod;
            this.args = args;
        }

        @Override
        public String toString() {
            return "Exposed{" +
                    "method='" + method + '\'' +
                    ", path='" + path + '\'' +
                    ", invMethod=" + invMethod +
                    ", args='" + args + '\'' +
                    '}';
        }
    }

    private static class Preform {
        private String method, path, invoker;
        private boolean crooked;

        void crook() {
            crooked = true;
        }

        boolean isCrooked() {
            return crooked;
        }

        boolean method(final String s) {
            this.method = clean(s);
            return method != null && method.matches("^[A-Z]+$");
        }

        boolean path(final String s) {
            this.path = clean(s);
            return path != null;
        }

        boolean invoker(final String s) {
            this.invoker = clean(s);
            return invoker != null;
        }

        private String clean(final String s) {
            if (isEmpty(s))
                return null;

            return s.replaceAll("\\s", "");
        }

        @Override
        public String toString() {
            return "Preform{" +
                    "method='" + method + '\'' +
                    ", path='" + path + '\'' +
                    ", invoker='" + invoker + '\'' +
                    '}';
        }
    }
*/
}
