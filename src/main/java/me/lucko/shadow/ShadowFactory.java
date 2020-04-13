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
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.Objects;

/**
 * Creates instances of {@link Shadow} interfaces.
 */
public class ShadowFactory {

    private static final ShadowFactory INSTANCE = new ShadowFactory();

    /**
     * Returns a shared static {@link ShadowFactory} instance.
     *
     * @return a shared instance
     */
    public static ShadowFactory global() {
        return INSTANCE;
    }

    private final @NonNull LoadingMap<Class<? extends Shadow>, ShadowDefinition> shadows = LoadingMap.of(this::initShadow);
    private final @NonNull TargetLookup targetLookup = new TargetLookup();

    /**
     * Constructs a new shadow factory.
     */
    public ShadowFactory() {

    }

    /**
     * Creates a shadow for the given object.
     *
     * @param shadowClass the class of the shadow definition
     * @param handle the handle object
     * @param <T> the shadow type
     * @return the shadow instance
     */
    public final <T extends Shadow> @NonNull T shadow(@NonNull Class<T> shadowClass, @NonNull Object handle) {
        Objects.requireNonNull(shadowClass, "shadowClass");
        Objects.requireNonNull(handle, "handle");

        // register the shadow first
        ShadowDefinition shadowDefinition = this.shadows.get(shadowClass);

        // ensure the target class of the shadow is assignable from the handle class
        Class<?> targetClass = shadowDefinition.getTargetClass();
        if (!targetClass.isAssignableFrom(handle.getClass())) {
            throw new IllegalArgumentException("Target class " + targetClass.getName() + " is not assignable from handle class " + handle.getClass().getName());
        }

        // return a proxy instance
        return createProxy(shadowClass, new ShadowInvocationHandler(this, shadowDefinition, handle));
    }

    /**
     * Creates a static shadow for the given class.
     *
     * @param shadowClass the class of the shadow definition
     * @param <T> the shadow type
     * @return the shadow instance
     */
    public final <T extends Shadow> @NonNull T staticShadow(@NonNull Class<T> shadowClass) {
        Objects.requireNonNull(shadowClass, "shadowClass");

        // register the shadow first
        ShadowDefinition shadowDefinition = this.shadows.get(shadowClass);

        // return a proxy instance
        return createProxy(shadowClass, new ShadowInvocationHandler(this, shadowDefinition, null));
    }

    /**
     * Creates a shadow for the given object, by invoking a constructor on the shadows
     * target.
     *
     * @param shadowClass the class of the shadow definition
     * @param args the arguments to pass to the constructor
     * @param <T> the shadow type
     * @return the shadow instance
     */
    public final <T extends Shadow> @NonNull T constructShadow(@NonNull Class<T> shadowClass, @NonNull Object... args) {
        return constructShadow(shadowClass, ShadowingStrategy.ForShadows.INSTANCE, args);
    }

    /**
     * Creates a shadow for the given object, by invoking a constructor on the shadows
     * target.
     *
     * @param shadowClass the class of the shadow definition
     * @param unwrapper the unwrapper to use
     * @param args the arguments to pass to the constructor
     * @param <T> the shadow type
     * @return the shadow instance
     */
    public final <T extends Shadow> @NonNull T constructShadow(@NonNull Class<T> shadowClass, ShadowingStrategy.@NonNull Unwrapper unwrapper, @NonNull Object... args) {
        Objects.requireNonNull(shadowClass, "shadowClass");

        // register the shadow first
        ShadowDefinition shadowDefinition = this.shadows.get(shadowClass);

        final Class<?>[] argumentTypes = Arrays.stream(args).map(Object::getClass).toArray(Class[]::new);
        Class<?>[] unwrappedParameterTypes;
        Object[] unwrappedArguments;
        try {
            unwrappedParameterTypes = unwrapper.unwrapAll(argumentTypes, this);
            unwrappedArguments = unwrapper.unwrapAll(args, unwrappedParameterTypes, this);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        Class<?>[] unwrappedArgumentTypes = Arrays.stream(unwrappedArguments).map(Object::getClass).toArray(Class[]::new);

        MethodHandle targetConstructor = shadowDefinition.findTargetConstructor(unwrappedArgumentTypes);

        Object newInstance;
        try {
            newInstance = targetConstructor.invokeWithArguments(unwrappedArguments);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }

        // create a shadow for the new instance
        return shadow(shadowClass, newInstance);
    }

    /**
     * Registers a target resolver with the shadow factory.
     *
     * @param targetResolver the resolver
     */
    public final void registerTargetResolver(@NonNull TargetResolver targetResolver) {
        this.targetLookup.registerResolver(targetResolver);
    }

    private @NonNull ShadowDefinition initShadow(@NonNull Class<? extends Shadow> shadowClass) {
        try {
            return new ShadowDefinition(this, shadowClass, this.targetLookup.lookupClass(shadowClass)
                    .orElseThrow(() -> new IllegalStateException("Shadow class " + shadowClass.getName() + " does not have a defined target class."))
            );
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("Class not found for shadow " + shadowClass.getName(), e);
        }
    }

    @NonNull TargetLookup getTargetLookup() {
        return this.targetLookup;
    }

    @NonNull Class<?> getTargetClass(@NonNull Class<?> shadowClass) {
        final ShadowDefinition definition = this.shadows.getIfPresent(shadowClass);
        return definition == null ? shadowClass : definition.getTargetClass();
    }

    private static <T> @NonNull T createProxy(@NonNull Class<T> interfaceType, @NonNull InvocationHandler handler) {
        ClassLoader classLoader = interfaceType.getClassLoader();
        return interfaceType.cast(Proxy.newProxyInstance(classLoader, new Class[]{interfaceType}, handler));
    }

}
