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

import me.lucko.shadow.ShadowingStrategy.Unwrapper;
import me.lucko.shadow.ShadowingStrategy.Wrapper;

import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

import java.lang.invoke.MethodHandle;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.Objects;

/**
 * Invocation handler for {@link Shadow}s.
 */
final class ShadowInvocationHandler implements InvocationHandler {
    private static final Method GET_SHADOW_TARGET_METHOD;
    private static final Method GET_SHADOW_CLASS_METHOD;
    private static final Method OBJECT_TOSTRING_METHOD;
    private static final Method OBJECT_EQUALS_METHOD;
    private static final Method OBJECT_HASHCODE_METHOD;
    static {
        try {
            GET_SHADOW_TARGET_METHOD = Shadow.class.getMethod("getShadowTarget");
            GET_SHADOW_CLASS_METHOD = Shadow.class.getMethod("getShadowClass");
            OBJECT_TOSTRING_METHOD = Object.class.getMethod("toString");
            OBJECT_EQUALS_METHOD = Object.class.getMethod("equals", Object.class);
            OBJECT_HASHCODE_METHOD = Object.class.getMethod("hashCode");
        } catch (NoSuchMethodException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private final @NonNull ShadowFactory shadowFactory;
    private final @NonNull ShadowDefinition shadow;
    private final @Nullable Object handle;

    ShadowInvocationHandler(@NonNull ShadowFactory shadowFactory, @NonNull ShadowDefinition shadow, @Nullable Object handle) {
        this.shadowFactory = shadowFactory;
        this.shadow = shadow;
        this.handle = handle;
    }

    @Override
    public Object invoke(Object shadowInstance, Method shadowMethod, Object[] args) throws Throwable {
        // implement methods in Shadow
        if (GET_SHADOW_TARGET_METHOD.equals(shadowMethod)) {
            return this.handle;
        }
        if (GET_SHADOW_CLASS_METHOD.equals(shadowMethod)) {
            return this.shadow.getShadowClass();
        }

        // implement some object methods
        if (OBJECT_TOSTRING_METHOD.equals(shadowMethod)) {
            return "Shadow(shadowClass=" + this.shadow.getShadowClass() + ", targetClass=" + this.shadow.getTargetClass() + ", target=" + this.handle + ")";
        }
        if (OBJECT_EQUALS_METHOD.equals(shadowMethod)) {
            Object otherObject = args[0];
            if (otherObject == this) {
                return true;
            }
            if (!(otherObject instanceof Shadow)) {
                return false;
            }
            Shadow other = (Shadow) otherObject;
            return this.shadow.getShadowClass().equals(other.getShadowClass()) && Objects.equals(this.handle, other.getShadowTarget());
        }
        if (OBJECT_HASHCODE_METHOD.equals(shadowMethod)) {
            return this.shadow.getShadowClass().hashCode() ^ Objects.hashCode(this.handle);
        }

        // just execute default methods on the proxy object itself
        if (shadowMethod.isDefault()) {
            Class<?> declaringClass = shadowMethod.getDeclaringClass();
            return PrivateMethodHandles.forClass(declaringClass)
                    .unreflectSpecial(shadowMethod, declaringClass)
                    .bindTo(shadowInstance)
                    .invokeWithArguments(args);
        }

        if (args == null) {
            args = new Object[0];
        }

        Object returnValue;

        if (shadowMethod.isAnnotationPresent(Field.class)) {
            ShadowDefinition.TargetField targetField = this.shadow.findTargetField(shadowMethod);

            if (args.length == 0) {
                // getter
                returnValue = bindWithHandle(targetField.getterHandle(), shadowMethod).invoke();

            } else if (args.length == 1) {
                // setter
                MethodHandle setter = bindWithHandle(targetField.setterHandle(), shadowMethod);
                Unwrapper unwrapper = getUnwrapper(shadowMethod);
                Class<?> unwrappedType = unwrapper.unwrap(shadowMethod.getParameterTypes()[0], this.shadowFactory);
                Object value = unwrapper.unwrap(args[0], unwrappedType, this.shadowFactory);
                setter.invokeWithArguments(value);

                if (shadowMethod.getReturnType() == void.class) {
                    returnValue = null;
                } else {
                    // allow chaining
                    returnValue = this.handle;
                }
            } else {
                throw new IllegalStateException("Unable to determine accessor type (getter/setter) for " + this.shadow.getTargetClass().getName() + "#" + shadowMethod.getName());
            }
        } else {
            // assume method target
            Unwrapper unwrapper = getUnwrapper(shadowMethod);
            Class<?>[] unwrappedParameterTypes = unwrapper.unwrapAll(shadowMethod.getParameterTypes(), this.shadowFactory);
            Object[] unwrappedArguments = unwrapper.unwrapAll(args, unwrappedParameterTypes, this.shadowFactory);
            Class<?>[] unwrappedArgumentTypes = getArgumentTypes(unwrappedArguments, unwrappedParameterTypes);

            ShadowDefinition.TargetMethod targetMethod = this.shadow.findTargetMethod(shadowMethod, unwrappedArgumentTypes);
            returnValue = bindWithHandle(targetMethod.handle(), shadowMethod).invokeWithArguments(unwrappedArguments);
        }

        Wrapper wrapper = getWrapper(shadowMethod);
        return wrapper.wrap(returnValue, shadowMethod.getReturnType(), this.shadowFactory);
    }

    static Class<?>[] getArgumentTypes(Object[] arguments, Class<?>[] fallback) {
        Class<?>[] types = new Class[arguments.length];
        for (int i = 0; i < arguments.length; i++) {
            final Object arg = arguments[i];
            if (arg == null) {
                types[i] = fallback == null ? Object.class : fallback[i];
            } else {
                types[i] = arg.getClass();
            }
        }
        return types;
    }

    private static Wrapper getWrapper(Method shadowMethod) {
        ShadowingStrategy shadowingStrategy = shadowMethod.getAnnotation(ShadowingStrategy.class);
        Wrapper wrapper;
        if (shadowingStrategy == null || shadowingStrategy.wrapper() == Wrapper.class) {
            wrapper = ShadowingStrategy.ForShadows.INSTANCE;
        } else {
            wrapper = Reflection.getInstance(Wrapper.class, shadowingStrategy.wrapper());
        }
        return wrapper;
    }

    private static Unwrapper getUnwrapper(Method shadowMethod) {
        ShadowingStrategy shadowingStrategy = shadowMethod.getAnnotation(ShadowingStrategy.class);
        Unwrapper unwrapper;
        if (shadowingStrategy == null || shadowingStrategy.unwrapper() == Unwrapper.class) {
            unwrapper = ShadowingStrategy.ForShadows.INSTANCE;
        } else {
            unwrapper = Reflection.getInstance(Unwrapper.class, shadowingStrategy.unwrapper());
        }
        return unwrapper;
    }

    private @NonNull MethodHandle bindWithHandle(MethodHandle methodHandle, @NonNull AnnotatedElement annotatedElement) {
        if (annotatedElement.isAnnotationPresent(Static.class)) {
            return methodHandle;
        } else {
            if (this.handle == null) {
                throw new IllegalStateException("Cannot call non-static method from a static shadow instance.");
            }
            return methodHandle.bindTo(this.handle);
        }
    }

}
