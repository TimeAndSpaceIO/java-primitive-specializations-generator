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


public final class SimpleOption implements Option {

    private static boolean isContextOption(String title) {
        boolean hasLow = false, hasUp = false;
        for (int i = 0; i < title.length(); i++) {
            char c = title.charAt(i);
            if (!Character.isLetterOrDigit(c))
                return true;
            if (Character.isLowerCase(c)) {
                hasLow = true;
            }
            if (Character.isUpperCase(c)) {
                hasUp = true;
            }
        }
        return !hasLow || !hasUp;
    }
    // for options used only to enrich context, not for replacing
    private final boolean contextOption;
    private final String opt;
    String title;
    Pattern titleP;

    String lower;
    Pattern lowerP;

    String upper;
    Pattern upperP;

    public SimpleOption(String opt) {
        this.opt = opt;
        if (contextOption = isContextOption(opt)) {
            return;
        }
        this.title = StringUtils.capitalize(opt);
        titleP = Pattern.compile(title + "(?![a-rt-z].|s[a-z])");

        lower = StringUtils.uncapitalize(opt);
        lowerP = Pattern.compile("(?<![A-Za-z])" + lower + "(?![a-rt-z].|s[a-z])");

        upper = StringUtils.toUnderscoredCase(title);
        upperP = Pattern.compile("(?<![A-Z])" + upper + "(?![A-RT-Z].|S[A-Z])");
    }

    @Override
    public String intermediateReplace(String content, String dim) {
        if (contextOption)
            return content;
        IntermediateOption intermediate = IntermediateOption.of(dim);
        content = lowerP.matcher(content).replaceAll(intermediate.neutralIdVariant.lower);
        content = titleP.matcher(content).replaceAll(intermediate.neutralIdVariant.title);
        content = upperP.matcher(content).replaceAll(intermediate.neutralIdVariant.upper);
        return content;
    }

    @Override
    public String finalReplace(String content, String dim) {
        if (contextOption)
            return content;
        IntermediateOption intermediate = IntermediateOption.of(dim);
        content = intermediate.neutralIdVariant.lowerP.matcher(content).replaceAll(lower);
        content = intermediate.neutralIdVariant.titleP.matcher(content).replaceAll(title);
        content = intermediate.neutralIdVariant.upperP.matcher(content).replaceAll(upper);
        return content;
    }

    @Override
    public String defaultValue() {
        throw new UnsupportedOperationException();
    }

    @Override
    public String toString() {
        return opt;
    }

    @Override
    public int hashCode() {
        return opt.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        return this == obj ||
                obj instanceof SimpleOption && opt.equals(((SimpleOption) obj).opt);
    }
}
