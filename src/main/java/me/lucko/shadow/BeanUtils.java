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

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

final class BeanUtils {

    /**
     * Find an accessible method that matches the given name and has compatible parameters.
     *
     * <p>Compatible parameters mean that every method parameter is assignable from
     * the given parameters. In other words, it finds a method with the given name
     * that will take the parameters given.</p>
     *
     * <p>This method is slightly undeterministic since it loops
     * through methods names and return the first matching method.</p>
     *
     * <p>This method can match primitive parameter by passing in wrapper classes.
     * For example, a <code>Boolean</code> will match a primitive <code>boolean</code>
     * parameter.
     *
     * @param clazz          find method in this class
     * @param methodName     find method with this name
     * @param parameterTypes find method with compatible parameters
     * @return The accessible method
     */
    public static Method getMatchingMethod(final Class<?> clazz, final String methodName, final Class<?>[] parameterTypes) {
        // try exact match
        try {
            Method method = clazz.getDeclaredMethod(methodName, parameterTypes);
            Reflection.ensureAccessible(method);
            return method;
        } catch (NoSuchMethodException e) {
            // ignore
        }

        // search through all methods
        Method bestMatch = null;
        float bestMatchCost = Float.MAX_VALUE;

        search:
        for (final Method method : clazz.getDeclaredMethods()) {
            if (!method.getName().equals(methodName)) {
                continue;
            }

            // compare parameters
            final Class<?>[] methodsParams = method.getParameterTypes();
            if (methodsParams.length != parameterTypes.length) {
                continue;
            }

            for (int i = 0; i < methodsParams.length; i++) {
                if (!isAssignmentCompatible(methodsParams[i], parameterTypes[i])) {
                    continue search;
                }
            }

            float cost = getTotalTransformationCost(parameterTypes, method.getParameterTypes());
            if (cost < bestMatchCost) {
                bestMatch = method;
                bestMatchCost = cost;
            }
        }

        if (bestMatch == null && clazz.getSuperclass() != null) {
            bestMatch = getMatchingMethod(clazz.getSuperclass(), methodName, parameterTypes);
        }

        if (bestMatch == null && clazz.getInterfaces() != null) {
            Class<?>[] interfaces = clazz.getInterfaces();
            for (Class<?> i : interfaces) {
                bestMatch = getMatchingMethod(i, methodName, parameterTypes);
                if (bestMatch != null) {
                    break;
                }
            }
        }

        return bestMatch;
    }

    public static Constructor<?> getMatchingConstructor(final Class<?> clazz, final Class<?>[] parameterTypes) {
        // try exact match
        try {
            Constructor<?> constructor = clazz.getDeclaredConstructor(parameterTypes);
            Reflection.ensureAccessible(constructor);
            return constructor;
        } catch (NoSuchMethodException e) {
            // ignore
        }

        // search through all methods
        Constructor<?> bestMatch = null;
        float bestMatchCost = Float.MAX_VALUE;

        search:
        for (final Constructor<?> constructor : clazz.getDeclaredConstructors()) {
            // compare parameters
            final Class<?>[] methodsParams = constructor.getParameterTypes();
            if (methodsParams.length != parameterTypes.length) {
                continue;
            }

            for (int n = 0; n < methodsParams.length; n++) {
                if (!isAssignmentCompatible(methodsParams[n], parameterTypes[n])) {
                    continue search;
                }
            }

            float cost = getTotalTransformationCost(parameterTypes, constructor.getParameterTypes());
            if (cost < bestMatchCost) {
                bestMatch = constructor;
                bestMatchCost = cost;
            }
        }

        return bestMatch;
    }

    /**
     * Returns the sum of the object transformation cost for each class in the source
     * argument list.
     *
     * @param srcArgs  The source arguments
     * @param destArgs The destination arguments
     * @return The total transformation cost
     */
    private static float getTotalTransformationCost(final Class<?>[] srcArgs, final Class<?>[] destArgs) {
        float totalCost = 0.0f;
        for (int i = 0; i < srcArgs.length; i++) {
            Class<?> srcClass, destClass;
            srcClass = srcArgs[i];
            destClass = destArgs[i];
            totalCost += getObjectTransformationCost(srcClass, destClass);
        }

        return totalCost;
    }

    /**
     * Gets the number of steps required needed to turn the source class into the
     * destination class. This represents the number of steps in the object hierarchy
     * graph.
     *
     * @param srcClass  The source class
     * @param destClass The destination class
     * @return The cost of transforming an object
     */
    private static float getObjectTransformationCost(Class<?> srcClass, final Class<?> destClass) {
        float cost = 0.0f;
        while (srcClass != null && !destClass.equals(srcClass)) {
            if (destClass.isPrimitive()) {
                final Class<?> destClassWrapperClazz = getPrimitiveWrapper(destClass);
                if (destClassWrapperClazz != null && destClassWrapperClazz.equals(srcClass)) {
                    cost += 0.25f;
                    break;
                }
            }
            if (destClass.isInterface() && isAssignmentCompatible(destClass, srcClass)) {
                // slight penalty for interface match.
                // we still want an exact match to override an interface match, but
                // an interface match should override anything where we have to get a
                // superclass.
                cost += 0.25f;
                break;
            }
            cost++;
            srcClass = srcClass.getSuperclass();
        }

        /*
         * If the destination class is null, we've travelled all the way up to
         * an Object match. We'll penalize this by adding 1.5 to the cost.
         */
        if (srcClass == null) {
            cost += 1.5f;
        }

        return cost;
    }

    /**
     * <p>Determine whether a type can be used as a parameter in a method invocation.
     * This method handles primitive conversions correctly.</p>
     *
     * <p>In order words, it will match a <code>Boolean</code> to a <code>boolean</code>,
     * a <code>Long</code> to a <code>long</code>,
     * a <code>Float</code> to a <code>float</code>,
     * a <code>Integer</code> to a <code>int</code>,
     * and a <code>Double</code> to a <code>double</code>.
     * Now logic widening matches are allowed.
     * For example, a <code>Long</code> will not match a <code>int</code>.
     *
     * @param parameterType    the type of parameter accepted by the method
     * @param parameterization the type of parameter being tested
     * @return true if the assignment is compatible.
     */
    private static boolean isAssignmentCompatible(final Class<?> parameterType, final Class<?> parameterization) {
        // try plain assignment
        if (parameterType.isAssignableFrom(parameterization)) {
            return true;
        }

        if (parameterType.isPrimitive()) {
            final Class<?> parameterWrapperClazz = getPrimitiveWrapper(parameterType);
            if (parameterWrapperClazz != null) {
                return parameterWrapperClazz.equals(parameterization);
            }
        }

        return false;
    }

    private static Class<?> getPrimitiveWrapper(final Class<?> primitiveType) {
        if (primitiveType == Integer.TYPE) {
            return Integer.class;
        }
        if (primitiveType == Long.TYPE) {
            return Long.class;
        }
        if (primitiveType == Boolean.TYPE) {
            return Boolean.class;
        }
        if (primitiveType == Byte.TYPE) {
            return Byte.class;
        }
        if (primitiveType == Character.TYPE) {
            return Character.class;
        }
        if (primitiveType == Float.TYPE) {
            return Float.class;
        }
        if (primitiveType == Double.TYPE) {
            return Double.class;
        }
        if (primitiveType == Short.TYPE) {
            return Short.class;
        }
        if (primitiveType == Void.TYPE) {
            return Void.class;
        }
        return null;
    }

    private BeanUtils() {
    }

}