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
import org.checkerframework.checker.nullness.qual.Nullable;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Array;
import java.lang.reflect.Method;

/**
 * Defines the strategy to use to shadow the return value of a {@link Shadow} method.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ReturnShadowingStrategy {

    /**
     * Gets the loading function class.
     *
     * <p>An instance of the function is retrieved/constructed on demand by the implementation in
     * the following order.</p>
     * <p></p>
     * <ul>
     * <li>a static method named {@code getInstance} accepting no parameters and returning an instance of the implementation.</li>
     * <li>via a single enum constant, if the loading function class is an enum following the enum singleton pattern.</li>
     * <li>a static field named {@code instance} with the same type as and containing an instance of the implementation.</li>
     * <li>a static field named {@code INSTANCE} with the same type as and containing an instance of the implementation.</li>
     * <li>a no-args constructor</li>
     * </ul>
     *
     * <p>Values defined for this property should be aware of this, and ensure an instance can be
     * retrieved/constructed.</p>
     *
     * @return the loading function class
     */
    @NonNull Class<? extends Function> value();

    /**
     * A functional interface encapsulating the return shadowing computation.
     */
    @FunctionalInterface
    interface Function {
        /**
         * Computes the return value for a shadow method invocation.
         *
         * @param returnValue the initial return value
         * @param shadowMethod the shadow method which has been invoked
         * @param shadowFactory the shadow factory
         * @return the computed return value
         * @throws Throwable anything
         */
        @Nullable Object compute(@Nullable Object returnValue, @NonNull Method shadowMethod, @NonNull ShadowFactory shadowFactory) throws Throwable;
    }

    /**
     * A {@link ReturnShadowingStrategy.Function} which attempts no shadowing of the return value.
     */
    enum None implements Function {
        INSTANCE;

        @Override
        public @Nullable Object compute(@Nullable Object returnValue, @NonNull Method shadowMethod, @NonNull ShadowFactory shadowFactory) {
            return returnValue;
        }
    }

    /**
     * A {@link ReturnShadowingStrategy.Function} which attempts to shadow the return value
     * if the shadow method returns a {@link Shadow}.
     *
     * <p>This is applied by default when a {@link ReturnShadowingStrategy} is not defined.</p>
     */
    enum ShadowReturn implements Function {
        INSTANCE;

        @SuppressWarnings("unchecked")
        @Override
        public @Nullable Object compute(@Nullable Object returnValue, @NonNull Method shadowMethod, @NonNull ShadowFactory shadowFactory) {
            if (returnValue == null) {
                return null;
            }

            if (Shadow.class.isAssignableFrom(shadowMethod.getReturnType())) {
                return shadowFactory.shadow((Class<? extends Shadow>) shadowMethod.getReturnType(), returnValue);
            }
            return returnValue;
        }
    }

    /**
     * A {@link ReturnShadowingStrategy.Function} which shadows the return value
     * if the shadow method returns an array of {@link Shadow}s.
     */
    enum ShadowReturnArray implements Function {
        INSTANCE;

        @SuppressWarnings("unchecked")
        @Override
        public @Nullable Object compute(@Nullable Object returnValue, @NonNull Method shadowMethod, @NonNull ShadowFactory shadowFactory) {
            if (returnValue == null) {
                return null;
            }

            Class<?> returnValueType = returnValue.getClass();
            if (!returnValueType.isArray()) {
                throw new RuntimeException("Return value is not an array: " + returnValueType);
            }

            Class<?> shadowReturnType = shadowMethod.getReturnType();
            if (!shadowReturnType.isArray()) {
                throw new RuntimeException("Shadow method does not return an array: " + shadowReturnType);
            }

            Class<?> shadowArrayComponentType = shadowReturnType.getComponentType();
            if (!Shadow.class.isAssignableFrom(shadowArrayComponentType)) {
                throw new RuntimeException("Shadow method does not return an array of shadow components: " + shadowArrayComponentType);
            }

            Object[] returnValueArray = (Object[]) returnValue;
            Object[] shadowedReturnArray = (Object[]) Array.newInstance(shadowArrayComponentType, returnValueArray.length);

            for (int i = 0; i < returnValueArray.length; i++) {
                Object object = returnValueArray[i];
                if (object != null) {
                    shadowedReturnArray[i] = shadowFactory.shadow((Class<? extends Shadow>) shadowArrayComponentType, object);
                }
            }

            return shadowedReturnArray;
        }
    }

}
