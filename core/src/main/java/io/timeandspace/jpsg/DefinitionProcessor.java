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

import org.intellij.lang.annotations.RegExp;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

import static java.util.regex.Pattern.DOTALL;
import static java.util.regex.Pattern.MULTILINE;


public final class DefinitionProcessor extends TemplateProcessor {
    public static final int PRIORITY = Generator.BLOCKS_PROCESSOR_PRIORITY + 100;

    private static final String DEF_PREFIX = "/[\\*/]\\s*define";
    private static final CheckingPattern DEF_P = CheckingPattern.compile(DEF_PREFIX,
            DEF_PREFIX +
                    "\\s+" +
                    "(?<name>[a-z_0-9]+)" +
                    "(\\s+(?<param>[a-z_0-9]+))?" +
                    "\\s*" +
                    "[\\*/]/" +
            "(?<body>.+?)" +
            "/[\\*/]\\s*enddefine\\s*[\\*/]/"
    );

    static CheckingPattern makeDefinitionUsePattern(String definitionName) {
        @RegExp String defPrefix = "/[*/]\\s*" + definitionName + "\\b";
        return CheckingPattern.compile(defPrefix,
                defPrefix + "(\\s++(?<argument>[^/* ]+)\\s*+|\\s*+)" +
                        "[*/]/" +
                        "(" +
                            "([^/]+?)" +
                            "/[*/][*/]/" +
                        ")?+",
                // Unlike the standard RegexpUtils.STANDARD_TEMPLATE_FLAGS, we don't want this
                // pattern to be case-insensitive.
                DOTALL | MULTILINE
        );
    }

    private static class Definition {
        private final String name;
        private final String body;
        private final @Nullable String param;

        Definition(String template, CheckingMatcher defineMatcher) {
            this.name = defineMatcher.group("name");
            if (name == null) {
                // Assertion rather than MalformedTemplateException because `name` group is
                // guaranteed to be present by the regex
                throw new AssertionError();
            }
            this.body = defineMatcher.group("body").trim();
            this.param = defineMatcher.group("param");
            if (name.equals("ClassName") && param != null) {
                throw MalformedTemplateException.near(template, defineMatcher.start(),
                        "ClassName must not have a parameter, " + param + " specified");
            }
        }

        Definition(String name, String body) {
            this.name = name;
            this.body = body;
            this.param = null;
        }

        String replaceParameter(String bodyWithoutNestedDefinitions,
                String template, CheckingMatcher definitionUseMatcher) {
            // "argument" as per makeDefinitionUsePattern() above
            String argument = definitionUseMatcher.group("argument");
            if (param != null && argument == null) {
                throw MalformedTemplateException.near(template, definitionUseMatcher.start(),
                        "Definition " + name + " requires an argument " + param);
            }
            if (param == null && argument != null) {
                // Better be using definitionUseMatcher.start("argument"), but start/end of named
                // groups is unsupported in Java 7.
                throw MalformedTemplateException.near(template, definitionUseMatcher.start(),
                        "Definition " + name + " don't have a parameter, " + argument + " given");
            }
            if (param == null) {
                return bodyWithoutNestedDefinitions;
            }
            // Replace all occurrences of param, literally, not as regex. Respect word boundaries.
            String paramRegex = "\\b" + Pattern.quote(param) + "\\b";
            return bodyWithoutNestedDefinitions.replaceAll(paramRegex, argument);
        }
    }

    private static final Definition COMMENT_DEFINITION = new Definition("comment", "");

    @Override
    protected int priority() {
        return PRIORITY;
    }

    @Override
    protected void process(StringBuilder builder, Context source, Context target, String template) {
        CheckingMatcher matcher = DEF_P.matcher(template);
        StringBuilder sb = new StringBuilder();
        Map<String, Definition> definitions = makeDefaultDefinitions();
        while (matcher.find()) {
            String defName = matcher.group("name");
            if (definitions.containsKey(defName)) {
                throw MalformedTemplateException.near(template, matcher.start(),
                        "Definition with name " + defName + " already exists in this context");
            }
            definitions.put(defName, new Definition(template, matcher));
            matcher.appendSimpleReplacement(sb, "");
        }
        matcher.appendTail(sb);
        String withoutDefinitions = replaceDefinitions(definitions, source, target, sb.toString());
        postProcess(builder, source, target, withoutDefinitions);
    }

    private Map<String, Definition> makeDefaultDefinitions() {
        Map<String, Definition> definitions = new HashMap<>(4);
        definitions.put("comment", COMMENT_DEFINITION);
        return definitions;
    }

    private String replaceDefinitions(Map<String, Definition> definitions,
            Context source, Context target, String template) {
        for (Map.Entry<String, Definition> e : definitions.entrySet()) {
            String defName = e.getKey();
            Definition definition = e.getValue();

            String bodyWithoutNestedDefinitions = replaceDefinitions(
                    without(definitions, defName), source, target, definition.body);
            if (defName.equals("ClassName")) {
                // ClassName is guaranteed to not have a parameter, therefore
                // bodyWithoutNestedDefinitions doesn't need to be passed to replaceParameter()
                // method, as below.
                String className =
                        postGenerate(source, target, bodyWithoutNestedDefinitions).trim();
                Generator.setRedefinedClassName(className);
            }

            CheckingPattern defUsePattern = makeDefinitionUsePattern(defName);
            CheckingMatcher defUseMatcher = defUsePattern.matcher(template);
            StringBuilder sb = new StringBuilder();
            while (defUseMatcher.find()) {
                String replacement = definition.replaceParameter(
                        bodyWithoutNestedDefinitions, template, defUseMatcher);
                defUseMatcher.appendSimpleReplacement(sb, replacement);
            }
            defUseMatcher.appendTail(sb);
            template = sb.toString();
        }
        return template;
    }

    private Map<String, Definition> without(Map<String, Definition> definitions, String defName) {
        Map<String, Definition> definitionsWithout = new HashMap<>(definitions);
        definitionsWithout.remove(defName);
        return definitionsWithout;
    }

    private String postGenerate(Context source, Context target, String template) {
        StringBuilder sb = new StringBuilder();
        postProcess(sb, source, target, template);
        return sb.toString();
    }
}
