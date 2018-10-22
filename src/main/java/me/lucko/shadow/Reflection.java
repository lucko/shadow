/*
 * This file is part of shadow, licensed under the MIT License.
 *
 *  Copyright (c) lucko (Luck) <luck@lucko.me>
 *  Copyright (c) contributors
 *
 *  Permission is hereby granted, free of charge, to any person obtaining a copy
 *  of this software and associated documentation files (the "Software"), to deal
 *  in the Software without restriction, including without limitation the rights
 *  to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 *  copies of the Software, and to permit persons to whom the Software is
 *  furnished to do so, subject to the following conditions:
 *
 *  The above copyright notice and this permission notice shall be included in all
 *  copies or substantial portions of the Software.
 *
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *  FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *  AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *  LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 *  OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 *  SOFTWARE.
 */

package me.lucko.shadow;

import org.checkerframework.checker.nullness.qual.NonNull;

import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

final class Reflection {

    public static void ensureStatic(Member member) {
        if (!Modifier.isStatic(member.getModifiers())) {
            throw new IllegalArgumentException();
        }
    }

    public static <T> @NonNull T getInstance(@NonNull Class<T> returnType, @NonNull Class<? extends T> implementationType) {
        try {
            Method getInstanceMethod = implementationType.getDeclaredMethod("getInstance");
            ensureStatic(getInstanceMethod);
            if (getInstanceMethod.getParameterCount() != 0) {
                throw new IllegalArgumentException();
            }
            if (!returnType.isAssignableFrom(getInstanceMethod.getReturnType())) {
                throw new IllegalArgumentException();
            }
            ensureAccessible(getInstanceMethod);
            //noinspection unchecked
            return (T) getInstanceMethod.invoke(null);
        } catch (Exception e) {
            // ignore
        }

        if (implementationType.isEnum()) {
            T[] enumConstants = implementationType.getEnumConstants();
            if (enumConstants.length == 1) {
                return enumConstants[0];
            }
        }

        try {
            Field instanceField = implementationType.getDeclaredField("instance");
            ensureStatic(instanceField);
            if (!returnType.isAssignableFrom(instanceField.getType())) {
                throw new IllegalArgumentException();
            }
            ensureAccessible(instanceField);
            //noinspection unchecked
            return (T) instanceField.get(null);
        } catch (Exception e) {
            // ignore
        }

        try {
            Field instanceField = implementationType.getDeclaredField("INSTANCE");
            ensureStatic(instanceField);
            if (!returnType.isAssignableFrom(instanceField.getType())) {
                throw new IllegalArgumentException();
            }
            ensureAccessible(instanceField);
            //noinspection unchecked
            return (T) instanceField.get(null);
        } catch (Exception e) {
            // ignore
        }

        try {
            Constructor<? extends T> constructor = implementationType.getDeclaredConstructor();
            ensureAccessible(constructor);
            return constructor.newInstance();
        } catch (Exception e) {
            // ignore
        }

        throw new RuntimeException("Unable to obtain an instance of " + implementationType.getName());
    }

    public static void ensureAccessible(AccessibleObject accessibleObject) {
        if (!accessibleObject.isAccessible()) {
            accessibleObject.setAccessible(true);
        }
    }

    public static void ensureModifiable(Field field) {
        if (Modifier.isFinal(field.getModifiers())) {
            try {
                Field modifierField = Field.class.getDeclaredField("modifiers");
                modifierField.setAccessible(true);
                modifierField.setInt(field, field.getModifiers() & ~Modifier.FINAL);
            } catch (IllegalAccessException | NoSuchFieldException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public static Field findField(Class<?> searchClass, String fieldName) {
        Field field = null;
        do {
            try {
                field = searchClass.getDeclaredField(fieldName);
            } catch (NoSuchFieldException ignored) {
                searchClass = searchClass.getSuperclass();
            }
        } while (field == null && searchClass != Object.class);
        return field;
    }

    private Reflection() {

    }

}
