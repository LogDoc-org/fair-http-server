package org.logdoc.fairhttp.service.api.helpers.endpoint;

import com.fasterxml.jackson.databind.JsonNode;
import org.logdoc.fairhttp.service.api.Controller;
import org.logdoc.fairhttp.service.api.helpers.endpoint.invokers.*;
import org.logdoc.fairhttp.service.http.Http;
import org.logdoc.helpers.Texts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.*;
import java.util.stream.Collectors;

import static org.logdoc.fairhttp.service.api.helpers.endpoint.invokers.ArgumentDefinition.Container.*;
import static org.logdoc.helpers.Reflects.getAllMethods;
import static org.logdoc.helpers.Texts.isEmpty;
import static org.logdoc.helpers.Texts.notNull;

/**
 * @author Denis Danilin | me@loslobos.ru
 * 22.05.2023 15:22
 * fair-http-server ☭ sweat and blood
 */
class InvokerFactory {
    private static final Logger logger = LoggerFactory.getLogger(InvokerFactory.class);
    private static Args buildArgs(final String data) {
        if (notNull(data).isBlank())
            return null;

        return new Args(data.trim());
    }

    private static Class<?> doClassName(final String data) throws ClassNotFoundException {
        if (isEmpty(data))
            return null;

        if (data.indexOf('.') != -1)
            return Class.forName(data);

        switch (data.trim()) {
            case "int":
            case "Int":
                return int.class;
            case "byte":
                return byte.class;
            case "char":
            case "Char":
                return char.class;
            case "short":
                return short.class;
            case "long":
                return long.class;
            case "double":
                return double.class;
            case "float":
                return float.class;
            case "boolean":
                return boolean.class;
            case "Integer":
            case "integer":
                return Integer.class;
            case "Character":
            case "character":
                return Character.class;
            case "Short":
                return Short.class;
            case "Long":
                return Long.class;
            case "Double":
                return Double.class;
            case "Float":
                return Float.class;
            case "Boolean":
                return Boolean.class;
            case "Json":
            case "json":
                return JsonNode.class;
            case "String":
            case "string":
            default:
                return String.class;
        }
    }

    static RequestInvoker build(final String invokerData) {
        try {
            final Args args = doInvokerData(invokerData);

            if (Http.Response.class.isAssignableFrom(args.targetMethod.getReturnType())) {
                if (args.size() == 0)
                    return new EmptyInvoker(args.targetMethod);

                if (args.size() == 1) {
                    final ArgData ad = args.get(0);

                    if (ad.container == Request)
                        return new RequestOnlyInvoker(args.targetMethod);

                    if (ad.container == Body)
                        return new BodyOnlyInvoker(args.targetMethod, ad.cls);
                }

                if (args.size() == 2) {
                    final ArgData fd = args.get(0);
                    final ArgData sd = args.get(1);

                    if (fd.container == Body && sd.container == Request)
                        return new BodyRequestInvoker(args.targetMethod, fd.cls);

                    if (fd.container == Request && sd.container == Body)
                        return new RequestBodyInvoker(args.targetMethod, sd.cls);
                }

                return new DynamicInvoker(args.targetMethod, args.stream().map(ad0 -> new ArgumentDefinition(ad0.container, ad0.cls, ad0.name)).collect(Collectors.toList()));
            } else {
                if (args.size() == 0)
                    return new EmptyAsyncInvoker(args.targetMethod);

                if (args.size() == 1) {
                    final ArgData ad = args.get(0);

                    if (ad.container == Request)
                        return new RequestOnlyAsyncInvoker(args.targetMethod);

                    if (ad.container == Body)
                        return new BodyOnlyAsyncInvoker(args.targetMethod, ad.cls);
                }

                if (args.size() == 2) {
                    final ArgData fd = args.get(0);
                    final ArgData sd = args.get(1);

                    if (fd.container == Body && sd.container == Request)
                        return new BodyRequestAsyncInvoker(args.targetMethod, fd.cls);

                    if (fd.container == Request && sd.container == Body)
                        return new RequestBodyAsyncInvoker(args.targetMethod, sd.cls);
                }

                return new DynamicAsyncInvoker(args.targetMethod, args.stream().map(ad0 -> new ArgumentDefinition(ad0.container, ad0.cls, ad0.name)).collect(Collectors.toList()));
            }
        } catch (final Exception e) {
            logger.error(invokerData + " :: " + e.getMessage(), e);
        }

        return null;
    }

    @SuppressWarnings("unchecked")
    private static Args doInvokerData(String invokerData) throws Exception {
        invokerData = invokerData.replace("()", "");
        final int parenth = invokerData.indexOf('(');

        final String signature = parenth > 0 ? invokerData.substring(0, parenth) : invokerData;
        final Args args = parenth > 0 ? buildArgs(invokerData.substring(parenth + 1, invokerData.lastIndexOf(')'))) : null;

        final int lastDot = signature.lastIndexOf('.');
        final String clsName = signature.substring(0, lastDot);
        final String mthName = signature.substring(lastDot + 1);

        final Class<? extends Controller> cls = (Class<? extends Controller>) Class.forName(clsName);
        Method method = null;

        final Set<Method> allMethods = getAllMethods(cls);
        for (final Method m : allMethods)
            if (m.getName().equals(mthName)) {
                if (args == null) {
                    if (m.getParameterCount() == 0) {
                        method = m;
                        break;
                    }

                    if (m.getParameterCount() == 1 && Http.Request.class.isAssignableFrom(m.getParameterTypes()[0])) {
                        method = m;
                        break;
                    }

                    continue;
                }

                if (args.match(m)) {
                    method = m;
                    break;
                }
            }

        if (method == null)
            throw new NoSuchMethodException(invokerData);

        if (args == null) {
            final Args a = new Args("");
            a.targetMethod = method;

            return a;
        }

        args.targetMethod = method;

        return args;
    }

    private static class ArgData {
        String name;
        Class<?> cls;
        ArgumentDefinition.Container container;

        @Override
        public String toString() {
            return "ArgData{" +
                    "name='" + name + '\'' +
                    ", cls=" + cls +
                    ", container=" + container +
                    '}';
        }
    }

    private static class Args extends ArrayList<ArgData> {
        Method targetMethod;

        private Args(final String data) {
            if (!Texts.isEmpty(data))
                Arrays.stream(data.split(","))
                        .map(d -> d.replaceAll("\\s", ""))
                        .filter(d -> !d.trim().isBlank())
                        .map(d -> {
                            try {
                                final ArgData ad = new ArgData();

                                int left = d.indexOf('[');
                                int right = d.indexOf(']');

                                if ((left != -1 && right == -1) || (left == -1 && right != -1))
                                    throw new UnknownFormatFlagsException(d);

                                final long leftCount = d.chars().filter(c -> c == '[').count();
                                final long rightCount = d.chars().filter(c -> c == ']').count();

                                if (leftCount != rightCount || leftCount > 2)
                                    throw new UnknownFormatFlagsException(d);

                                if (left == -1) {
                                    ad.container = Unknown;
                                    ad.name = d;
                                    ad.cls = String.class;
                                } else {
                                    if (leftCount == 1) {
                                        if (left == 0) {
                                            if (right == d.length() - 1) { // body or request
                                                ad.container = valueOf(d.substring(left + 1, right));
                                                if (ad.container == Request)
                                                    ad.cls = Http.Request.class;
                                                else if (ad.container != Body)
                                                    throw new UnknownFormatFlagsException(d);
                                            } else {
                                                ad.cls = doClassName(d.substring(left + 1, right));
                                                ad.container = Unknown;
                                            }
                                        } else {
                                            ad.container = ArgumentDefinition.Container.valueOf(d.substring(left + 1, right));
                                            ad.name = d.substring(0, left);
                                            if (ad.container != Request && ad.container != Body)
                                                ad.cls = String.class;
                                        }
                                    } else {
                                        ad.cls = doClassName(d.substring(left + 1, right));

                                        left = d.lastIndexOf('[');
                                        ad.container = ArgumentDefinition.Container.valueOf(d.substring(left + 1, d.length() - 1));
                                        ad.name = d.substring(right + 1, left);
                                    }
                                }

                                return ad;
                            } catch (final Exception e) {
                                logger.error("Cant parse args = " + d + " :: " + e.getMessage(), e);

                                return null;
                            }
                        })
                        .filter(Objects::nonNull)
                        .forEach(this::add);
        }

        public boolean match(final Method m) {
            if (size() > m.getParameterCount())
                return false;

            final Class<?>[] types = m.getParameterTypes();

            if (types.length - size() > 1)
                return false;

            for (int i = 0; i < types.length; i++) {
                final ArgData ad = get(i);
                final Class<?> cls = types[i];

                if (cls.equals(Http.Request.class) && ad.cls != null && !ad.cls.equals(cls)) {
                    final ArgData msd = new ArgData();
                    msd.cls = Http.Request.class;
                    msd.container = Request;

                    add(i--, msd);
                    continue;
                }

                if (ad.cls == null)
                    ad.cls = cls;

                if (!cls.isAssignableFrom(ad.cls))
                    return false;
            }

            return true;
        }

        @Override
        public boolean add(final ArgData argData) {
            if (argData == null)
                throw new NullPointerException();

            return super.add(argData);
        }
    }

}
