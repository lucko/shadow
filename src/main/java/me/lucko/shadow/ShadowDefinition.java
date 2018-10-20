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

import java.lang.invoke.MethodHandle;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Represents a processed {@link Shadow} definition.
 */
final class ShadowDefinition {
    private static final Pattern GET_PATTERN = Pattern.compile("(get)[A-Z].*");
    private static final Pattern GET_IS_PATTERN = Pattern.compile("(is)[A-Z].*");
    private static final Pattern SET_PATTERN = Pattern.compile("(set)[A-Z].*");

    private final @NonNull Class<? extends Shadow> shadowClass;
    private final @NonNull Class<?> targetClass;

    // caches
    private final @NonNull Map<Method, MethodHandle> methods = new ConcurrentHashMap<>();
    private final @NonNull Map<Method, FieldMethodHandle> fields = new ConcurrentHashMap<>();
    private final @NonNull Map<Class[], MethodHandle> constructors = new ConcurrentHashMap<>();

    ShadowDefinition(@NonNull Class<? extends Shadow> shadowClass, @NonNull Class<?> targetClass) {
        this.shadowClass = shadowClass;
        this.targetClass = targetClass;
    }

    public @NonNull Class<? extends Shadow> getShadowClass() {
        return this.shadowClass;
    }

    public @NonNull Class<?> getTargetClass() {
        return this.targetClass;
    }

    public @NonNull MethodHandle findTargetMethod(@NonNull Method shadowMethod, @NonNull Class<?>[] argumentTypes) {
        return this.methods.computeIfAbsent(shadowMethod, m -> {
            String methodName = TargetLookup.lookupMethod(m, this.shadowClass, this.targetClass).orElseGet(m::getName);
            Method method = BeanUtils.getMatchingMethod(this.targetClass, methodName, argumentTypes);
            if (method == null) {
                throw new RuntimeException(new NoSuchMethodException(this.targetClass.getName() + "." + methodName));
            }

            Reflection.ensureAccessible(method);

            try {
                return PrivateMethodHandles.forClass(method.getDeclaringClass()).unreflect(method);
            } catch (IllegalAccessException e) {
                throw new RuntimeException(e);
            }
        });
    }

    public @NonNull FieldMethodHandle findTargetField(@NonNull Method shadowMethod) {
        return this.fields.computeIfAbsent(shadowMethod, m -> {
            String fieldName = TargetLookup.lookupField(m, this.shadowClass, this.targetClass).orElseGet(() -> {
                String methodName = m.getName();
                Matcher matcher = GET_PATTERN.matcher(methodName);
                if (matcher.matches()) {
                    return methodName.substring(3, 4).toLowerCase() + methodName.substring(4);
                }

                matcher = GET_IS_PATTERN.matcher(methodName);
                if (matcher.matches()) {
                    return methodName.substring(2, 3).toLowerCase() + methodName.substring(3);
                }

                matcher = SET_PATTERN.matcher(methodName);
                if (matcher.matches()) {
                    return methodName.substring(3, 4).toLowerCase() + methodName.substring(4);
                }

                return methodName;
            });
            Field field = Reflection.findField(this.targetClass, fieldName);
            if (field == null) {
                throw new RuntimeException(new NoSuchFieldException(this.targetClass.getName() + "#" + fieldName));
            }

            Reflection.ensureAccessible(field);
            Reflection.ensureModifiable(field);

            try {
                return new FieldMethodHandle(field);
            } catch (IllegalAccessException e) {
                throw new RuntimeException(e);
            }
        });
    }

    public @NonNull MethodHandle findTargetConstructor(@NonNull Class<?>[] argumentTypes) {
        return this.constructors.computeIfAbsent(argumentTypes, m -> {
            Constructor<?> constructor = BeanUtils.getMatchingConstructor(this.targetClass, argumentTypes);
            if (constructor == null) {
                throw new RuntimeException(new NoSuchMethodException(this.targetClass.getName() + ".<init>" + " - " + Arrays.toString(argumentTypes)));
            }

            Reflection.ensureAccessible(constructor);

            try {
                return PrivateMethodHandles.forClass(constructor.getDeclaringClass()).unreflectConstructor(constructor);
            } catch (IllegalAccessException e) {
                throw new RuntimeException(e);
            }
        });
    }

}
