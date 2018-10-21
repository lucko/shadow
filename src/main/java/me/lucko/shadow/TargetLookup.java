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
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Base implementation of {@link TargetResolver} that delegates to the default + other registered
 * resolvers.
 */
final class TargetLookup implements TargetResolver {

    private final @NonNull List<TargetResolver> resolvers = new CopyOnWriteArrayList<>(Arrays.asList(
            ClassTarget.RESOLVER,
            Target.RESOLVER,
            DynamicClassTarget.RESOLVER,
            DynamicMethodTarget.RESOLVER,
            DynamicFieldTarget.RESOLVER,
            FuzzyFieldTargetResolver.INSTANCE
    ));

    TargetLookup() {

    }

    public void registerResolver(@NonNull TargetResolver targetResolver) {
        Objects.requireNonNull(targetResolver, "targetResolver");
        if (!this.resolvers.contains(targetResolver)) {
            this.resolvers.add(0, targetResolver);
        }
    }

    @Override
    public @NonNull Optional<Class<?>> lookupClass(@NonNull Class<? extends Shadow> shadowClass) throws ClassNotFoundException {
        for (TargetResolver resolver : this.resolvers) {
            Optional<Class<?>> result = resolver.lookupClass(shadowClass);
            if (result.isPresent()) {
                return result;
            }
        }
        return Optional.empty();
    }

    @Override
    public @NonNull Optional<String> lookupMethod(@NonNull Method shadowMethod, @NonNull Class<? extends Shadow> shadowClass, @NonNull Class<?> targetClass) {
        for (TargetResolver resolver : this.resolvers) {
            Optional<String> result = resolver.lookupMethod(shadowMethod, shadowClass, targetClass);
            if (result.isPresent()) {
                return result;
            }
        }
        return Optional.empty();
    }

    @Override
    public @NonNull Optional<String> lookupField(@NonNull Method shadowMethod, @NonNull Class<? extends Shadow> shadowClass, @NonNull Class<?> targetClass) {
        for (TargetResolver resolver : this.resolvers) {
            Optional<String> result = resolver.lookupField(shadowMethod, shadowClass, targetClass);
            if (result.isPresent()) {
                return result;
            }
        }
        return Optional.empty();
    }

}
