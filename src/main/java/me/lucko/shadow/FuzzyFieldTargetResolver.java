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

import java.lang.reflect.Method;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A {@link TargetResolver} for fields to match common "getter" and "setter" method patterns.
 */
final class FuzzyFieldTargetResolver implements TargetResolver {
    static final FuzzyFieldTargetResolver INSTANCE = new FuzzyFieldTargetResolver();

    private static final Pattern GETTER_PATTERN = Pattern.compile("(get)[A-Z].*");
    private static final Pattern GETTER_IS_PATTERN = Pattern.compile("(is)[A-Z].*");
    private static final Pattern SETTER_PATTERN = Pattern.compile("(set)[A-Z].*");

    private FuzzyFieldTargetResolver() {

    }

    @Override
    public @NonNull Optional<String> lookupField(@NonNull Method shadowMethod, @NonNull Class<? extends Shadow> shadowClass, @NonNull Class<?> targetClass) {
        String methodName = shadowMethod.getName();
        Matcher matcher = GETTER_PATTERN.matcher(methodName);
        if (matcher.matches()) {
            return Optional.of(methodName.substring(3, 4).toLowerCase() + methodName.substring(4));
        }

        matcher = GETTER_IS_PATTERN.matcher(methodName);
        if (matcher.matches()) {
            return Optional.of(methodName.substring(2, 3).toLowerCase() + methodName.substring(3));
        }

        matcher = SETTER_PATTERN.matcher(methodName);
        if (matcher.matches()) {
            return Optional.of(methodName.substring(3, 4).toLowerCase() + methodName.substring(4));
        }

        return Optional.empty();
    }
}
