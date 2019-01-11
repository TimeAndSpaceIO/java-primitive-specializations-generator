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

import java.util.regex.Pattern;

import static io.timeandspace.jpsg.Condition.CONDITION;
import static java.lang.String.format;

/**
 * This class is needed solely to workaround https://youtrack.jetbrains.com/issue/KT-23307,
 * the constants defined here should belong to the {@link Generator}'s companion object.
 */
public class GeneratorConstants {
    public static final String DIMENSIONS =
            format("(?<dimensions>(\\s*%s\\s*)+)", Dimensions.DIMENSION);

    static final String COND_START = format("if\\s*(?<condition>%s)", CONDITION);
    static final Pattern COND_START_BLOCK_P = Generator.wrapBlock(COND_START);
    static final CheckingPattern COND_START_P =
            Generator.compileBlock(COND_START, "if");

    static final String COND_END = "endif";
    static final CheckingPattern COND_END_P =
            Generator.compileBlock(COND_END, COND_END);

    static final String COND_PART= format("((el)?if\\s*(?<condition>%s)|%s)", CONDITION, COND_END);
    static final Pattern COND_PART_BLOCK_P = Generator.wrapBlock(COND_PART);
    static final CheckingPattern COND_PART_P =
            Generator.compileBlock(COND_PART, "(el|end)?if");

    static final String CONTEXT_START = format("with%s", DIMENSIONS);
    static final Pattern CONTEXT_START_BLOCK_P =
            Generator.wrapBlock(CONTEXT_START);
    static final CheckingPattern CONTEXT_START_P =
            Generator.compileBlock(CONTEXT_START, "with");

    static final String CONTEXT_END = "endwith";
    static final CheckingPattern CONTEXT_END_P =
            Generator.compileBlock(CONTEXT_END, CONTEXT_END);

    static final String CONTEXT_PART = format("(%s|%s)", CONTEXT_START, CONTEXT_END);
    static final CheckingPattern CONTEXT_PART_P =
            Generator.compileBlock(CONTEXT_PART, "(end)?with");

    static final String ANY_BLOCK_PART = format("(%s|%s)", COND_PART, CONTEXT_PART);
    static final CheckingPattern ANY_BLOCK_PART_P =
            Generator.compileBlock(ANY_BLOCK_PART, "((el|end)?if|(end)?with)");
}
