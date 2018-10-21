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

/**
 * Represents a processed {@link Shadow} definition.
 */
final class ShadowDefinition {
    private final @NonNull ShadowFactory shadowFactory;
    private final @NonNull Class<? extends Shadow> shadowClass;
    private final @NonNull Class<?> targetClass;

    // caches
    private final @NonNull LoadingMap<MethodInfo, MethodHandle> methods = LoadingMap.of(this::loadTargetMethod);
    private final @NonNull LoadingMap<Method, FieldMethodHandle> fields = LoadingMap.of(this::loadTargetField);
    private final @NonNull LoadingMap<Class[], MethodHandle> constructors = LoadingMap.of(this::loadTargetConstructor);

    ShadowDefinition(@NonNull ShadowFactory shadowFactory, @NonNull Class<? extends Shadow> shadowClass, @NonNull Class<?> targetClass) {
        this.shadowFactory = shadowFactory;
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
        return this.methods.get(new MethodInfo(shadowMethod, argumentTypes));
    }

    public @NonNull FieldMethodHandle findTargetField(@NonNull Method shadowMethod) {
        return this.fields.get(shadowMethod);
    }

    public @NonNull MethodHandle findTargetConstructor(@NonNull Class<?>[] argumentTypes) {
        return this.constructors.get(argumentTypes);
    }

    private @NonNull MethodHandle loadTargetMethod(@NonNull MethodInfo methodInfo) {
        Method shadowMethod = methodInfo.method;
        String methodName = this.shadowFactory.getTargetLookup().lookupMethod(shadowMethod, this.shadowClass, this.targetClass).orElseGet(shadowMethod::getName);
        Method method = BeanUtils.getMatchingMethod(this.targetClass, methodName, methodInfo.argumentTypes);
        if (method == null) {
            throw new RuntimeException(new NoSuchMethodException(this.targetClass.getName() + "." + methodName));
        }

        Reflection.ensureAccessible(method);

        try {
            return PrivateMethodHandles.forClass(method.getDeclaringClass()).unreflect(method);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    private @NonNull FieldMethodHandle loadTargetField(@NonNull Method shadowMethod) {
        String fieldName = this.shadowFactory.getTargetLookup().lookupField(shadowMethod, this.shadowClass, this.targetClass).orElseGet(shadowMethod::getName);
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
    }

    private @NonNull MethodHandle loadTargetConstructor(@NonNull Class<?>[] argumentTypes) {
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
    }

    private static final class MethodInfo {
        private final Method method;
        private final Class<?>[] argumentTypes;

        MethodInfo(Method method, Class<?>[] argumentTypes) {
            this.method = method;
            this.argumentTypes = argumentTypes;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            MethodInfo that = (MethodInfo) o;
            return this.method.equals(that.method);
        }

        @Override
        public int hashCode() {
            return this.method.hashCode();
        }
    }

}
