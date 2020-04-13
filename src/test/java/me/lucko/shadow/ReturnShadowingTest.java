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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class ReturnShadowingTest {

    @Test
    public void testArrayReturnShadowing() {
        DataClass data = new DataClass(new Item[]{new Item(2), new Item(5)});
        DataClassShadow shadow = ShadowFactory.global().shadow(DataClassShadow.class, data);

        assertEquals(2, shadow.getItems()[0].getValue());
        assertEquals(5, shadow.getItems()[1].getValue());
    }

    @ClassTarget(DataClass.class)
    private interface DataClassShadow extends Shadow {
        @Field
        @ReturnShadowingStrategy(ReturnShadowingStrategy.ShadowReturnArray.class)
        ItemShadow[] getItems();
    }

    @ClassTarget(Item.class)
    private interface ItemShadow extends Shadow {
        @Field
        @Target("i")
        int getValue();
    }

    private static final class DataClass {
        private final Item[] items;

        private DataClass(Item[] items) {
            this.items = items;
        }
    }

    private static final class Item {
        private final int i;

        private Item(int i) {
            this.i = i;
        }
    }

}
