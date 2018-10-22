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
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.Arrays;
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
            return "Shadow(shadowClass=" + this.shadow.getShadowClass() + ", targetClass=" + this.shadow.getTargetClass() + ", target=" + Objects.toString(this.handle) + ")";
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

        Object returnValue;

        if (shadowMethod.isAnnotationPresent(Field.class)) {
            ShadowDefinition.TargetField targetField = this.shadow.findTargetField(shadowMethod);

            if (args == null || args.length == 0) {
                // getter
                returnValue = bindWithHandle(targetField.getterHandle(), shadowMethod).invoke();

            } else if (args.length == 1) {
                // setter
                MethodHandle setter = bindWithHandle(targetField.setterHandle(), shadowMethod);
                Object value = this.shadowFactory.unwrapShadow(args[0]);
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
            Object[] unwrappedArguments = this.shadowFactory.unwrapShadows(args);
            Class[] unwrappedArgumentTypes = Arrays.stream(unwrappedArguments).map(Object::getClass).toArray(Class[]::new);

            ShadowDefinition.TargetMethod targetMethod = this.shadow.findTargetMethod(shadowMethod, unwrappedArgumentTypes);
            returnValue = bindWithHandle(targetMethod.handle(), shadowMethod).invokeWithArguments(unwrappedArguments);
        }

        if (returnValue != null && Shadow.class.isAssignableFrom(shadowMethod.getReturnType())) {
            //noinspection unchecked
            returnValue = this.shadowFactory.shadow((Class<? extends Shadow>) shadowMethod.getReturnType(), returnValue);
        }
        return returnValue;
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
