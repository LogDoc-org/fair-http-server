package org.logdoc.fairhttp.service.tools;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.stream.Collectors;

@SuppressWarnings("UnusedDeclaration")
public class ClassWrapper {
    public static boolean isServiceMethod(final Method m) {
        final int mods = m.getModifiers();

        return m.isSynthetic() || (mods & Modifier.PRIVATE) > 0 || (mods & Modifier.STATIC) > 0;
    }

    public static Field[] getFields(final Object o) {
        return getFields(o.getClass());
    }

    public static Field[] getFields(final Class<?> cls) {
        final Set<Field> list = new HashSet<>(0);
        final Set<String> uniqueNames = new HashSet<>(0);

        list.addAll(Arrays.asList(cls.getDeclaredFields()));

        if (cls.getSuperclass() != null)
            Collections.addAll(list, getFields(cls.getSuperclass()));

        final List<Field> toRemove = new ArrayList<>(0);

        toRemove.addAll(list.parallelStream().filter(f -> !uniqueNames.add(f.getName())).collect(Collectors.toList()));

        if (!toRemove.isEmpty())
            toRemove.forEach(list::remove);

        return list.toArray(new Field[0]);
    }

    public static Field[] getAllFields(final Class<?> cls) {
        final List<Field> list = new ArrayList<>(0);

        list.addAll(Arrays.asList(cls.getDeclaredFields()));

        if (cls.getSuperclass() != null)
            Collections.addAll(list, getFields(cls.getSuperclass()));

        return list.toArray(new Field[0]);
    }

    public static Field findField(final Object o, final String name) throws NoSuchFieldException {
        return findField(o.getClass(), name);
    }

    public static Field findField(final Class<?> cls, final String name) throws NoSuchFieldException {
        try {
            return cls.getDeclaredField(name);
        } catch (NoSuchFieldException e) {
            if (cls.getSuperclass() == null)
                throw e;

            return findField(cls.getSuperclass(), name);
        }
    }

    public static Method findMethod(final Object o, final String name, final Class<?> ... params) throws NoSuchMethodException {
        return findMethod(o.getClass(), name, params);
    }

    public static Method findMethod(final Class<?> cls, final String name, final Class<?> ... params) throws NoSuchMethodException {
        try {
            return cls.getDeclaredMethod(name, params);
        } catch (NoSuchMethodException e) {
            if (cls.getSuperclass() == null)
                throw e;

            return findMethod(cls.getSuperclass(), name, params);
        }
    }

    public static Set<Method> getAllMethods(Class<?> cls) {
        final Set<Method> methods = new HashSet<>(0);

        final Method[] mine = cls.getDeclaredMethods();
        methods.addAll(Arrays.asList(mine));

        if (cls.getSuperclass() != null)
            methods.addAll(getAllMethods(cls.getSuperclass()));

        return methods;
    }

    public static boolean classIsSimple(final Class<?> cls) {
        return cls.isEnum() || CharSequence.class.isAssignableFrom(cls) || Number.class.isAssignableFrom(cls) || Boolean.class.isAssignableFrom(cls) || Character.class.isAssignableFrom(cls) || Byte.class.isAssignableFrom(cls);
    }

}
