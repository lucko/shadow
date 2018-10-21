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

/**
 * Interface to represent a resolver which can identify the corresponding targets for
 * shadow classes, methods and fields.
 */
public interface TargetResolver {

    /**
     * Attempts to find the corresponding target class for the given shadow class.
     *
     * @param shadowClass the shadow class
     * @return the target, if any
     * @throws ClassNotFoundException if the resultant target class cannot be loaded
     */
    default @NonNull Optional<Class<?>> lookupClass(@NonNull Class<? extends Shadow> shadowClass) throws ClassNotFoundException {
        return Optional.empty();
    }

    /**
     * Attempts to find the corresponding target method name for the given shadow method.
     *
     * @param shadowMethod the shadow method to lookup a target method for
     * @param shadowClass the class defining the shadow method
     * @param targetClass the target class. the resultant method should resolve for this class.
     * @return the target, if any
     */
    default @NonNull Optional<String> lookupMethod(@NonNull Method shadowMethod, @NonNull Class<? extends Shadow> shadowClass, @NonNull Class<?> targetClass) {
        return Optional.empty();
    }

    /**
     * Attempts to find the corresponding target field name for the given shadow method.
     *
     * @param shadowMethod the shadow method to lookup a target field for
     * @param shadowClass the class defining the shadow method
     * @param targetClass the target class. the resultant method should resolve for this class.
     * @return the target, if any
     */
    default @NonNull Optional<String> lookupField(@NonNull Method shadowMethod, @NonNull Class<? extends Shadow> shadowClass, @NonNull Class<?> targetClass) {
        return Optional.empty();
    }

}
