/*
 * Copyright 2014-2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.timeandspace.jpsg;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

import static java.lang.String.format;


public enum PrimitiveType implements Option {

    BOOL ("boolean", "Boolean", "bool", false),
    BOOLEAN ("boolean", "Boolean", "boolean", false),
    BYTE ("byte", "Byte"),
    CHAR ("char", "Character"),
    SHORT ("short", "Short"),

    INT ("int", "Integer") {
        @Override
        public String formatValue(String value) {
            return value;
        }
    },

    LONG ("long", "Long") {
        @Override
        public String formatValue(String value) {
            return value + "L";
        }
    },

    FLOAT ("float", "Float") {

        @Override
        public String intermediateReplace(String content, String dim) {
            content = super.intermediateReplace(content, dim);
            content = INT.intermediateReplace(content, dim + ".bits");
            return content;
        }

        @Override
        public String finalReplace(String content, String dim) {
            content = INT.finalReplace(content, dim + ".bits");
            content = super.finalReplace(content, dim);
            return content;
        }

        @Override
        public String formatValue(String value) {
            return value + ".0f";
        }

        @Override
        String minValue() {
            return "Float.NEGATIVE_INFINITY";
        }

        @Override
        String maxValue() {
            return "Float.POSITIVE_INFINITY";
        }

        @Override
        public PrimitiveType bitsType() {
            return INT;
        }
    },

    DOUBLE ("double", "Double") {

        @Override
        public String intermediateReplace(String content, String dim) {
            content = super.intermediateReplace(content, dim);
            content = LONG.intermediateReplace(content, dim + ".bits");
            return content;
        }


        @Override
        public String finalReplace(String content, String dim) {
            content = LONG.finalReplace(content, dim + ".bits");
            content = super.finalReplace(content, dim);
            return content;
        }

        @Override
        public String formatValue(String value) {
            return value + ".0";
        }

        @Override
        String minValue() {
            return "Double.NEGATIVE_INFINITY";
        }

        @Override
        String maxValue() {
            return "Double.POSITIVE_INFINITY";
        }

        @Override
        public PrimitiveType bitsType() {
            return LONG;
        }
    };

    public static final List<PrimitiveType> NUMERIC_TYPES =
            Arrays.asList(BYTE, CHAR, SHORT, INT, LONG, FLOAT, DOUBLE);

    public final boolean isNumeric;
    public final String className;
    final Pattern classNameP;

    public final String standalone;
    final Pattern standaloneP;

    public final String lower;
    final Pattern lowerP;

    public final String title;
    final Pattern titleP;

    public final String upper;
    final Pattern upperP;

    /** Constructor for numeric primitives */
    PrimitiveType(String prim, String className) {
        this(prim, className, prim, true);
    }

    PrimitiveType(String prim, String className, String lowerId, boolean isNumeric) {
        this.isNumeric = isNumeric;
        this.className = className;
        // disallowed `.` behind because of inner and fully qualified classes, e. g. mypackage.Long
        // allowed `#` ahead because of Javadoc links: {@link Integer#valueOf}
        classNameP = Pattern.compile("(?<![A-Za-z0-9_$#.])" + className + "(?![A-Za-z0-9_$])");

        standalone = prim;
        standaloneP = Pattern.compile("(?<![A-Za-z0-9_$#])" + prim + "(?![A-Za-z0-9_$#])");

        lower = lowerId;
        // look around not to replace keyword substring inside words
        // Uppercase ahead is ok -- consider Integer.intValue
        // Special case - plural form (ints, chars)
        lowerP = Pattern.compile(
                "(?<![A-Za-z])" + prim + "(?![a-rt-z].|s[a-z])");

        title = lowerId.substring(0, 1).toUpperCase() + prim.substring(1);
        // lookahead not to replace Int in ex. doInterrupt
        // special case - plural form: addAllInts
        titleP = Pattern.compile("\\$?" + title + "(?![a-rt-z].|s[a-z])");

        upper = lowerId.toUpperCase();
        // lookahead and lookbehind not to replace INT in ex. INTERLEAVE_CONSTANT
        upperP = Pattern.compile("(?<![A-Z])" + upper + "(?![A-RT-Z].|S[A-Z])");
    }

    String minValue() {
        return className + ".MIN_VALUE";
    }

    String maxValue() {
        return className + ".MAX_VALUE";
    }

    @Override
    public String defaultValue() {
        if ("Boolean".equals(className)) {
            return "false";
        } else {
            return formatValue("0");
        }
    }

    public String formatValue(String value) {
        return format("(%s) %s", standalone, value);
    }

    public PrimitiveType bitsType() {
        return this;
    }

    @Override
    public String intermediateReplace(String content, String dim) {
        IntermediateOption intermediate = IntermediateOption.of(dim);
        content = classNameP.matcher(content).replaceAll(intermediate.className);
        content = standaloneP.matcher(content).replaceAll(intermediate.standalone);
        content = lowerP.matcher(content).replaceAll(intermediate.lower);
        content = titleP.matcher(content).replaceAll(intermediate.title);
        content = upperP.matcher(content).replaceAll(intermediate.upper);
        return content;
    }


    @Override
    public String finalReplace(String content, String dim) {
        IntermediateOption intermediate = IntermediateOption.of(dim);
        content = intermediate.classNameP.matcher(content).replaceAll(className);
        content = intermediate.standaloneP.matcher(content).replaceAll(standalone);
        content = intermediate.lowerP.matcher(content).replaceAll(lower);
        content = intermediate.titleP.matcher(content).replaceAll(title);
        content = intermediate.upperP.matcher(content).replaceAll(upper);
        return content;
    }

    @Override
    public String toString() {
        return title;
    }
}
