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

import java.util.*;
import java.util.regex.Pattern;

import static java.lang.String.format;


public enum PrimitiveType implements Option {

    BOOL ("boolean", "Boolean", "bool", false),
    BOOLEAN ("boolean", "Boolean", "boolean", false),
    BYTE ("byte", "Byte"),
    CHAR ("char", "Character"),
    CHARACTER("char", "Character", "character", true),
    SHORT ("short", "Short"),

    INT ("int", "Integer") {
        @Override
        public String formatValue(String value) {
            return value;
        }
    },

    INTEGER("int", "Integer", "integer", true) {
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
            // INT and INTEGER could be used interchangeably here because bits modifier replaces
            // only standalone primitive (int) occurrences.
            content = INT.intermediateReplace(content, dim + ".bits");
            return content;
        }

        @Override
        public String finalReplace(String content, String dim) {
            // INT and INTEGER could be used interchangeably here because bits modifier replaces
            // only standalone primitive (int) occurrences.
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

    public static final List<PrimitiveType> NUMERIC_TYPES_WITH_SHORT_IDS =
            Arrays.asList(BYTE, CHAR, SHORT, INT, LONG, FLOAT, DOUBLE);

    public static final Map<String, PrimitiveType> UPPER_CASE_NAME_TO_TYPE;
    static {
        UPPER_CASE_NAME_TO_TYPE = new HashMap<>();
        for (PrimitiveType option : PrimitiveType.values()) {
            UPPER_CASE_NAME_TO_TYPE.put(option.name(), option);
        }
    }

    public final boolean isNumeric;
    public final String className;
    private final String fullyQualifiedClassName;
    final Pattern classNameP;

    public final String standalone;
    final Pattern standaloneP;

    public static class IdReplacement {
        public final String lower;
        final Pattern lowerP;

        public final String title;
        final Pattern titleP;

        public final String upper;
        final Pattern upperP;

        IdReplacement(String lowerId) {
            lower = lowerId;
            // look around not to replace keyword substring inside words
            // Uppercase ahead is ok -- consider Integer.intValue
            // Special case - plural form (ints, chars)
            lowerP = Pattern.compile(
                    "(?<![A-Za-z])" + lowerId + "(?![a-rt-z].|s[a-z])");

            title = StringUtils.capitalize(lowerId);
            // lookahead not to replace Int in ex. doInterrupt
            // special case - plural form: addAllInts
            titleP = Pattern.compile("\\$?" + title + "(?![a-rt-z].|s[a-z])");

            upper = lowerId.toUpperCase();
            // lookahead and lookbehind not to replace INT in ex. INTERLEAVE_CONSTANT
            upperP = Pattern.compile("(?<![A-Z])" + upper + "(?![A-RT-Z].|S[A-Z])");
        }
    }

    public final IdReplacement neutralIdReplacement;
    public final IdReplacement shortIdReplacement;
    public final IdReplacement longIdReplacement;

    /** Constructor for numeric primitives */
    PrimitiveType(String prim, String className) {
        this(prim, className, prim, true);
    }

    PrimitiveType(String prim, String className, String lowerId, boolean isNumeric) {
        this.isNumeric = isNumeric;
        this.className = className;
        fullyQualifiedClassName = "java.lang." + className;
        // Disallowed `.` behind because of inner and fully qualified classes, e. g. mypackage.Long.
        // Fully qualified class name (e. g. `java.lang.Long`) is replaced separately in
        // intermediateReplace() before replacing the following pattern.
        // Allowed `#` ahead because of Javadoc links: {@link Integer#valueOf}.
        classNameP = Pattern.compile("(?<![A-Za-z0-9_$#.])" + className + "(?![A-Za-z0-9_$])");

        standalone = prim;
        standaloneP = Pattern.compile("(?<![A-Za-z0-9_$#])" + prim + "(?![A-Za-z0-9_$#])");

        neutralIdReplacement = new IdReplacement(lowerId);
        String classNameBasedId = StringUtils.uncapitalize(className);
        List<String> ids = Arrays.asList(prim, lowerId, classNameBasedId);
        String shortestId = Collections.min(ids, StringLengthComparator.INSTANCE);
        if (shortestId.length() < lowerId.length()) {
            shortIdReplacement = new IdReplacement(shortestId);
        } else {
            shortIdReplacement = neutralIdReplacement;
        }
        String longestId = Collections.max(ids, StringLengthComparator.INSTANCE);
        if (longestId.length() > lowerId.length()) {
            longIdReplacement = new IdReplacement(longestId);
        } else {
            longIdReplacement = neutralIdReplacement;
        }
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
        content = content.replace(fullyQualifiedClassName, intermediate.className);
        content = classNameP.matcher(content).replaceAll(intermediate.className);
        content = standaloneP.matcher(content).replaceAll(intermediate.standalone);
        content = intermediateReplaceId(content, neutralIdReplacement,
                intermediate.neutralIdVariant);
        if (shortIdReplacement != neutralIdReplacement) {
            content = intermediateReplaceId(content, shortIdReplacement,
                    intermediate.shortIdVariant);
        }
        if (longIdReplacement != neutralIdReplacement) {
            content = intermediateReplaceId(content, longIdReplacement, intermediate.longIdVariant);
        }
        return content;
    }

    private static String intermediateReplaceId(String content, IdReplacement idReplacement,
            IntermediateOption.IdVariant idVariant) {
        content = idReplacement.lowerP.matcher(content).replaceAll(idVariant.lower);
        content = idReplacement.titleP.matcher(content).replaceAll(idVariant.title);
        content = idReplacement.upperP.matcher(content).replaceAll(idVariant.upper);
        return content;
    }

    @Override
    public String finalReplace(String content, String dim) {
        IntermediateOption intermediate = IntermediateOption.of(dim);
        content = intermediate.classNameP.matcher(content).replaceAll(className);
        content = intermediate.standaloneP.matcher(content).replaceAll(standalone);
        content = finalReplaceId(content, intermediate.neutralIdVariant, neutralIdReplacement);
        content = finalReplaceId(content, intermediate.shortIdVariant, shortIdReplacement);
        content = finalReplaceId(content, intermediate.longIdVariant, longIdReplacement);
        return content;
    }

    private static String finalReplaceId(String content, IntermediateOption.IdVariant idVariant,
            IdReplacement idReplacement) {
        content = idVariant.lowerP.matcher(content).replaceAll(idReplacement.lower);
        content = idVariant.titleP.matcher(content).replaceAll(idReplacement.title);
        content = idVariant.upperP.matcher(content).replaceAll(idReplacement.upper);
        return content;
    }

    @Override
    public String toString() {
        return neutralIdReplacement.title;
    }
}
