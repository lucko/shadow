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

import java.lang.reflect.Method;
import java.util.Optional;

final class TargetLookup {

    public static @NonNull Optional<Class<?>> lookupClass(Class<? extends Shadow> shadowClass) throws ClassNotFoundException {
        TargetClass targetClass = shadowClass.getAnnotation(TargetClass.class);
        if (targetClass != null) {
            return Optional.of(targetClass.value());
        }

        Target target = shadowClass.getAnnotation(Target.class);
        if (target != null) {
            return Optional.of(Class.forName(target.value()));
        }

        DynamicClassTarget dynamicClassTarget = shadowClass.getAnnotation(DynamicClassTarget.class);
        if (dynamicClassTarget != null) {
            return Optional.of(Reflection.getInstance(dynamicClassTarget.value()).computeClass(shadowClass));
        }

        return Optional.empty();
    }

    public static @NonNull Optional<String> lookupMethod(@NonNull Method method, @NonNull Class<? extends Shadow> shadowClass, @NonNull Class<?> targetClass) {
        Target target = method.getAnnotation(Target.class);
        if (target != null) {
            return Optional.of(target.value());
        }

        DynamicMethodTarget dynamicTarget = method.getAnnotation(DynamicMethodTarget.class);
        if (dynamicTarget != null) {
            Class<? extends DynamicMethodTarget.Function> functionClass = dynamicTarget.value();
            return Optional.of(Reflection.getInstance(functionClass).computeMethod(method, shadowClass, targetClass));
        }

        return Optional.empty();
    }

    public static @NonNull Optional<String> lookupField(@NonNull Method method, @NonNull Class<? extends Shadow> shadowClass, @NonNull Class<?> targetClass) {
        Target target = method.getAnnotation(Target.class);
        if (target != null) {
            return Optional.of(target.value());
        }

        DynamicFieldTarget dynamicTarget = method.getAnnotation(DynamicFieldTarget.class);
        if (dynamicTarget != null) {
            Class<? extends DynamicFieldTarget.Function> functionClass = dynamicTarget.value();
            return Optional.of(Reflection.getInstance(functionClass).computeField(method, shadowClass, targetClass));
        }

        return Optional.empty();
    }

    private TargetLookup() {

    }

}
