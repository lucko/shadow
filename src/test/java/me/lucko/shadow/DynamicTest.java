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
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

public class DynamicTest {

    @Test
    public void testDynamicShadow() {
        DataClass data = new DataClass("foo");
        DataClassShadow shadow = ShadowFactory.global().shadow(DataClassShadow.class, data);

        Assertions.assertEquals("foo", shadow.getString());
    }

    private static final class ClassTargetFunction implements DynamicClassTarget.Function {
        public ClassTargetFunction() {

        }

        @Override
        public @NonNull Class<?> computeClass(@NonNull Class<? extends Shadow> shadowClass) {
            return DataClass.class;
        }
    }

    private static final class FieldTargetFunction implements DynamicFieldTarget.Function {

        @Override
        public @NonNull String computeField(@NonNull Method shadowMethod, @NonNull Class<? extends Shadow> shadowClass, @NonNull Class<?> targetClass) {
            return shadowMethod.getName().substring(3).toLowerCase() + "Value";
        }
    }

    @DynamicClassTarget(ClassTargetFunction.class)
    private interface DataClassShadow extends Shadow {
        @ShadowField
        @DynamicFieldTarget(FieldTargetFunction.class)
        String getString();
    }

    private static final class DataClass {
        private final String stringValue;

        private DataClass(String string) {
            this.stringValue = string;
        }
    }

}
