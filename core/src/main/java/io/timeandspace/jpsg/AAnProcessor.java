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

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Specified in the section "A/An" in the tutorial.
 */
public final class AAnProcessor extends TemplateProcessor {
    public static final int PRIORITY = PrimitiveTypeModifierPostProcessor.getPRIORITY() - 10;

    private static final Pattern ARTICLE_PATTERN = RegexpUtils.compile("/[\\*/]an?[\\*/]/");
    private static final Pattern LETTER_PATTERN = RegexpUtils.compile("[a-zA-Z]");

    @Override
    protected int priority() {
        return PRIORITY;
    }

    @Override
    protected void process(StringBuilder builder, Context source, Context target, String template) {
        Matcher articleM = ARTICLE_PATTERN.matcher(template);
        StringBuffer sb = new StringBuffer();
        while (articleM.find()) {
            String textAfterArticle = template.substring(articleM.end());
            Matcher letterAfterArticle = LETTER_PATTERN.matcher(textAfterArticle);
            char letter;
            do {
                if (!letterAfterArticle.find())
                    throw MalformedTemplateException.near(template, articleM.end());
                letter = textAfterArticle.charAt(letterAfterArticle.start());
                if (containsAtIndex(textAfterArticle, letterAfterArticle.start(), "code") ||
                        containsAtIndex(textAfterArticle, letterAfterArticle.start(), "link")) {
                    // Skips "ode" or "ink" and finds the next letter.
                    for (int i = 0; i < 3; i++) {
                        letterAfterArticle.find();
                    }
                } else {
                    break;
                }
            } while (true);
            String article = isVowel(letter) ? "an" : "a";
            articleM.appendReplacement(sb, article);
        }
        articleM.appendTail(sb);
        postProcess(builder, source, target, sb.toString());
    }

    private static boolean containsAtIndex(String s, int startIndex, String part) {
        return s.indexOf(part, startIndex) == startIndex;
    }

    private static boolean isVowel(char c) {
        return "aeiouAEIOU".indexOf(c) >= 0;
    }
}
