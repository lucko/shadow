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

import java.lang.invoke.MethodHandle;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Creates instances of {@link Shadow} interfaces.
 */
public final class ShadowFactory {

    private static final ShadowFactory INSTANCE = new ShadowFactory();

    /**
     * Creates a shadow for the given object.
     *
     * @param shadowClass the class of the shadow definition
     * @param handle the handle object
     * @param <T> the shadow type
     * @return the shadow instance
     */
    public static <T extends Shadow> @NonNull T shadow(@NonNull Class<T> shadowClass, @NonNull Object handle) {
        return INSTANCE.createShadowProxy(shadowClass, handle);
    }

    /**
     * Creates a static shadow for the given class.
     *
     * @param shadowClass the class of the shadow definition
     * @param <T> the shadow type
     * @return the shadow instance
     */
    public static <T extends Shadow> @NonNull T staticShadow(@NonNull Class<T> shadowClass) {
        return INSTANCE.createStaticShadowProxy(shadowClass);
    }

    /**
     * Creates a shadow for the given object, by invoking a constructor on the shadows
     * target.
     *
     * @param shadowClass the class of the shadow definition
     * @param arguments the arguments to pass to the constructor
     * @param <T> the shadow type
     * @return the shadow instance
     */
    public static <T extends Shadow> @NonNull T constructShadow(@NonNull Class<T> shadowClass, @NonNull Object... arguments) {
        return INSTANCE.constructShadowInstance(shadowClass, arguments);
    }

    private final @NonNull Map<Class<? extends Shadow>, ShadowDefinition> shadows = new ConcurrentHashMap<>();

    private ShadowFactory() {

    }

    private static <T> @NonNull T createProxy(@NonNull Class<T> interfaceType, @NonNull InvocationHandler handler) {
        ClassLoader classLoader = interfaceType.getClassLoader();
        return interfaceType.cast(Proxy.newProxyInstance(classLoader, new Class[]{interfaceType}, handler));
    }

    public <T extends Shadow> @NonNull T createShadowProxy(@NonNull Class<T> shadowClass, @NonNull Object handle) {
        Objects.requireNonNull(shadowClass, "shadowClass");
        Objects.requireNonNull(handle, "handle");

        // register the shadow first
        ShadowDefinition shadowDefinition = registerShadow(shadowClass);

        // ensure the target class of the shadow is assignable from the handle class
        Class<?> targetClass = this.shadows.get(shadowClass).getTargetClass();
        if (!targetClass.isAssignableFrom(handle.getClass())) {
            throw new IllegalArgumentException("Target class " + targetClass.getName() + " is not assignable from handle class " + handle.getClass().getName());
        }

        // return a proxy instance
        return createProxy(shadowClass, new ShadowInvocationHandler(this, shadowDefinition, handle));
    }

    public <T extends Shadow> @NonNull T createStaticShadowProxy(@NonNull Class<T> shadowClass) {
        Objects.requireNonNull(shadowClass, "shadowClass");

        // register the shadow first
        ShadowDefinition shadowDefinition = registerShadow(shadowClass);

        // return a proxy instance
        return createProxy(shadowClass, new ShadowInvocationHandler(this, shadowDefinition, null));
    }

    public <T extends Shadow> @NonNull T constructShadowInstance(@NonNull Class<T> shadowClass, @NonNull Object... args) {
        Objects.requireNonNull(shadowClass, "shadowClass");

        // register the shadow first
        ShadowDefinition shadowDefinition = registerShadow(shadowClass);

        Object[] unwrappedArguments = unwrapShadows(args);
        Class[] unwrappedArgumentTypes = Arrays.stream(unwrappedArguments).map(Object::getClass).toArray(Class[]::new);

        MethodHandle targetConstructor = shadowDefinition.findTargetConstructor(unwrappedArgumentTypes);

        Object newInstance;
        try {
            newInstance = targetConstructor.invokeWithArguments(unwrappedArguments);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }

        // create a shadow for the new instance
        return createShadowProxy(shadowClass, newInstance);
    }

    private @NonNull ShadowDefinition registerShadow(@NonNull Class<? extends Shadow> c) {
        return this.shadows.computeIfAbsent(c, shadowClass -> {
            try {
                return new ShadowDefinition(shadowClass, TargetLookup.lookupClass(shadowClass)
                        .orElseThrow(() -> new IllegalStateException("Shadow class " + shadowClass.getName() + " does not have a " +
                                "@Target, @TargetClass or @DynamicClassTarget annotation present."))
                );
            } catch (ClassNotFoundException e) {
                throw new RuntimeException("Class not found for shadow " + shadowClass.getName(), e);
            }
        });
    }

    @NonNull Object[] unwrapShadows(@Nullable Object[] objects) {
        if (objects == null) {
            return new Object[0];
        }

        return Arrays.stream(objects).map(this::unwrapShadow).toArray(Object[]::new);
    }

    @Nullable Object unwrapShadow(@Nullable Object object) {
        if (object == null) {
            return null;
        }

        if (object instanceof Shadow) {
            Shadow shadow = (Shadow) object;
            registerShadow(shadow.getClass());
            return unwrapShadow(shadow.getShadowTarget());
        }

        return object;
    }

}
