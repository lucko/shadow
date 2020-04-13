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

/**
 * Defines the strategy to use when wrapping and unwrapping (shadow) objects.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ShadowingStrategy {

    /**
     *  Gets the {@link Wrapper} function.
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
     * @return the wrapper function
     */
    @NonNull Class<? extends Wrapper> wrapper() default Wrapper.class;

    /**
     *  Gets the {@link Unwrapper} function.
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
     * @return the unwrapper function
     */
    @NonNull Class<? extends Unwrapper> unwrapper() default Unwrapper.class;

    /**
     * A function for wrapping objects to {@link Shadow}s.
     */
    @FunctionalInterface
    interface Wrapper {
        /**
         * Wraps the given {@code object} to a shadow.
         *
         * @param unwrapped the object being returned (not a shadow)
         * @param expectedType the expected type of the object (possibly a shadow)
         * @param shadowFactory the shadow factory
         * @return the wrapped value
         * @throws Exception anything
         */
        @Nullable Object wrap(@Nullable Object unwrapped, @NonNull Class<?> expectedType, @NonNull ShadowFactory shadowFactory) throws Exception;
    }

    /**
     * A function for unwrapping {@link Shadow}s to objects.
     */
    interface Unwrapper {
        /**
         * Unwraps the given {@code object} to a non-shadow object.
         *
         * @param wrapped the object (possibly a shadow)
         * @param expectedType the expected type of the object
         * @param shadowFactory the shadow factory
         * @return the unwrapped value
         * @throws Exception anything
         */
        @Nullable Object unwrap(@Nullable Object wrapped, @NonNull Class<?> expectedType, @NonNull ShadowFactory shadowFactory) throws Exception;

        @NonNull Class<?> unwrap(Class<?> wrappedClass, @NonNull ShadowFactory shadowFactory);

        /**
         * Unwraps all of the given {@code object}s to non-shadow objects.
         *
         * @param wrapped the wrapped objects
         * @param expectedTypes the expected types of the objects
         * @param shadowFactory the shadow factory
         * @return the unwrapped values
         * @throws Exception anything
         */
        default @NonNull Object[] unwrapAll(@Nullable Object[] wrapped, @NonNull Class<?>[] expectedTypes, @NonNull ShadowFactory shadowFactory) throws Exception {
            if (wrapped.length != expectedTypes.length) {
                throw new IllegalStateException("wrapped.length != expectedTypes.length");
            }

            Object[] unwrapped = new Object[wrapped.length];
            for (int i = 0; i < wrapped.length; i++) {
                unwrapped[i] = unwrap(wrapped[i], expectedTypes[i], shadowFactory);
            }
            return unwrapped;
        }

        default @NonNull Class<?>[] unwrapAll(@NonNull Class<?>[] wrapped, @NonNull ShadowFactory shadowFactory) throws Exception {
            Class<?>[] unwrapped = new Class[wrapped.length];
            for (int i = 0; i < wrapped.length; i++) {
                unwrapped[i] = unwrap(wrapped[i], shadowFactory);
            }
            return unwrapped;
        }
    }

    /**
     * A {@link Wrapper} and {@link Unwrapper} which do nothing.
     */
    enum None implements Wrapper, Unwrapper {
        INSTANCE;

        @Override
        public @Nullable Object wrap(@Nullable Object unwrapped, @NonNull Class<?> expectedType, @NonNull ShadowFactory shadowFactory) {
            return unwrapped;
        }

        @Override
        public @Nullable Object unwrap(@Nullable Object wrapped, @NonNull Class<?> expectedType, @NonNull ShadowFactory shadowFactory) {
            return wrapped;
        }

        @Override
        public @NonNull Class<?> unwrap(Class<?> wrappedClass, @NonNull ShadowFactory shadowFactory) {
            return wrappedClass;
        }
    }

    /**
     * A (un)wrapper which wraps and unwraps basic shadow objects.
     *
     * <p>This is applied by default when a {@link ShadowingStrategy} is not defined.</p>
     */
    enum ForShadows implements Wrapper, Unwrapper {
        INSTANCE;

        @SuppressWarnings("unchecked")
        @Override
        public @Nullable Object wrap(@Nullable Object unwrapped, @NonNull Class<?> expectedType, @NonNull ShadowFactory shadowFactory) {
            if (unwrapped == null) {
                return null;
            }

            if (Shadow.class.isAssignableFrom(expectedType)) {
                return shadowFactory.shadow((Class<? extends Shadow>) expectedType, unwrapped);
            }

            return unwrapped;
        }

        @Override
        public @Nullable Object unwrap(@Nullable Object wrapped, @NonNull Class<?> expectedType, @NonNull ShadowFactory shadowFactory) {
            if (wrapped == null) {
                return null;
            }

            if (wrapped instanceof Shadow) {
                Shadow shadow = (Shadow) wrapped;
                return shadow.getShadowTarget();
            }

            return wrapped;
        }

        @Override
        public @NonNull Class<?> unwrap(Class<?> wrappedClass, @NonNull ShadowFactory shadowFactory) {
            return shadowFactory.getTargetClass(wrappedClass);
        }
    }

    /**
     * A (un)wrapper which wraps and unwraps one-dimensional shadow arrays.
     */
    enum ForShadowArrays implements Wrapper, Unwrapper {
        INSTANCE;

        @SuppressWarnings("unchecked")
        @Override
        public @Nullable Object wrap(@Nullable Object unwrapped, @NonNull Class<?> expectedType, @NonNull ShadowFactory shadowFactory) {
            if (unwrapped == null) {
                return null;
            }

            Class<?> unwrappedType = unwrapped.getClass();
            if (!unwrappedType.isArray()) {
                throw new RuntimeException("Object to be wrapped is not an array: " + unwrappedType);
            }

            if (!expectedType.isArray()) {
                throw new RuntimeException("Expected type is not an array: " + expectedType);
            }

            Class<?> wrappedArrayComponentType = expectedType.getComponentType();
            if (!Shadow.class.isAssignableFrom(wrappedArrayComponentType)) {
                throw new RuntimeException("Expected type is not an array of shadow components: " + wrappedArrayComponentType);
            }

            Object[] /* Object[] */ unwrappedArray = (Object[]) unwrapped;
            Object[] /* Shadow[] */ wrappedArray = (Object[]) Array.newInstance(wrappedArrayComponentType, unwrappedArray.length);

            for (int i = 0; i < unwrappedArray.length; i++) {
                Object o = unwrappedArray[i];
                if (o != null) {
                    wrappedArray[i] = shadowFactory.shadow((Class<? extends Shadow>) wrappedArrayComponentType, o);
                }
            }

            return wrappedArray;
        }

        @Override
        public @Nullable Object unwrap(@Nullable Object wrapped, @NonNull Class<?> expectedType, @NonNull ShadowFactory shadowFactory) throws Exception {
            if (wrapped == null) {
                return null;
            }

            System.out.println(wrapped);
            System.out.println(expectedType);

            Class<?> wrappedType = wrapped.getClass();
            if (!wrappedType.isArray()) {
                throw new RuntimeException("Object to be unwrapped is not an array: " + wrappedType);
            }

            if (!expectedType.isArray()) {
                throw new RuntimeException("Expected type is not an array: " + expectedType);
            }

            Class<?> wrappedArrayComponentType = wrappedType.getComponentType();
            if (!Shadow.class.isAssignableFrom(wrappedArrayComponentType)) {
                throw new RuntimeException("Wrapped type is not an array of shadow components: " + wrappedArrayComponentType);
            }

            Object[] /* Shadow[] */ wrappedArray = (Object[]) wrapped;
            Object[] /* Object[] */ unwrappedArray = (Object[]) Array.newInstance(expectedType.getComponentType(), wrappedArray.length);

            for (int i = 0; i < wrappedArray.length; i++) {
                Object o = wrappedArray[i];
                if (o != null) {
                    unwrappedArray[i] = ((Shadow) o).getShadowTarget();
                }
            }

            return unwrappedArray;
        }

        @Override
        public @NonNull Class<?> unwrap(Class<?> wrappedClass, @NonNull ShadowFactory shadowFactory) {
            if (!wrappedClass.isArray()) {
                throw new RuntimeException("Object to be unwrapped is not an array: " + wrappedClass);
            }

            final Class<?> unwrappedComponentType = shadowFactory.getTargetClass(wrappedClass.getComponentType());
            return Array.newInstance(unwrappedComponentType, 0).getClass();
        }
    }

}
