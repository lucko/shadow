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

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class BasicTest {

    @Test
    public void testShadow() {
        DataClass data = new DataClass("foo", 5, false);
        DataClassShadow shadow = ShadowFactory.global().shadow(DataClassShadow.class, data);

        Assertions.assertEquals("foo", shadow.getTheString());
        Assertions.assertEquals(5, shadow.getTheInteger());
        Assertions.assertFalse(shadow.isTheBoolean());

        shadow.setTheString("bar");
        Assertions.assertEquals("bar", shadow.getTheString());
        Assertions.assertEquals("bar", data.theString);
        shadow.incrementTheInteger();
        Assertions.assertEquals(6, shadow.getTheInteger());
    }

    @Test
    public void testConstruction() {
        DataClassShadow shadow = ShadowFactory.global().constructShadow(DataClassShadow.class, "baz", 42, true);
        Assertions.assertEquals("baz", shadow.getTheString());

        Object target = shadow.getShadowTarget();
        Assertions.assertNotNull(target);
        Assertions.assertTrue(target instanceof DataClass);

        DataClass casted = (DataClass) target;
        Assertions.assertTrue(casted.theBoolean);
        Assertions.assertEquals(42, casted.i);
    }

    @Test
    public void testEqualityHashcode() {
        DataClass data1 = new DataClass("foo", 5, false);
        DataClass data2 = new DataClass("foo", 5, false);
        DataClassShadow shadow1 = ShadowFactory.global().shadow(DataClassShadow.class, data1);
        DataClassShadow shadow1_2 = ShadowFactory.global().shadow(DataClassShadow.class, data1);
        DataClassShadow shadow2 = ShadowFactory.global().shadow(DataClassShadow.class, data2);

        // ensure underlying properties are correct
        Assertions.assertNotEquals(data1, data2);

        // test shadow equals/hashcode
        Assertions.assertEquals(shadow1, shadow1_2);
        Assertions.assertNotEquals(shadow1, shadow2);
        Assertions.assertEquals(shadow1.hashCode(), shadow1_2.hashCode());
    }

    @Test
    public void testShadowInspectionMethods() {
        DataClass data = new DataClass("foo", 5, false);
        DataClassShadow shadow = ShadowFactory.global().shadow(DataClassShadow.class, data);

        Assertions.assertEquals(DataClassShadow.class, shadow.getShadowClass());
        Assertions.assertEquals(data, shadow.getShadowTarget());
        Assertions.assertNotNull(shadow.getShadowTarget());
        Assertions.assertEquals(DataClass.class, shadow.getShadowTarget().getClass());
    }

    @ClassTarget(DataClass.class)
    private interface DataClassShadow extends Shadow {
        @Field
        String getTheString();

        @Field
        @Target("i")
        int getTheInteger();

        @Field
        boolean isTheBoolean();

        @Field
        void setTheString(String value);

        void incrementTheInteger();
    }

    private static final class DataClass {
        private final String theString;
        private int i;
        private final boolean theBoolean;

        private DataClass(String theString, int theInteger, boolean theBoolean) {
            this.theString = theString;
            this.i = theInteger;
            this.theBoolean = theBoolean;
        }

        private void incrementTheInteger() {
            this.i++;
        }
    }

}
