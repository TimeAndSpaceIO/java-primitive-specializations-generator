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


import java.util.HashMap;
import java.util.Map;

public final class FloatingWrappingProcessor extends TemplateProcessor {

    private static final String WRAPPING_PREFIX = "/[\\*/]\\s*(?<op>wrap|unwrap|unwrapRaw)";
    private static final CheckingPattern WRAPPING_P = CheckingPattern.compile(WRAPPING_PREFIX,
            WRAPPING_PREFIX + "\\s+(?<dim>\\w+)\\s*[\\*/]/" +
            "((?<closed>(?<closedBody>[^/]+)/[\\*/][\\*/]/)|(?<openBody>[^\\s\\{\\};/\\*]+))");

    private static final Map<String, String> opsToFloatMethods = new HashMap<>();
    private static final Map<String, String> opsToDoubleMethods = new HashMap<>();
    static {
        opsToFloatMethods.put("wrap", "intBitsToFloat");
        opsToFloatMethods.put("unwrap", "floatToIntBits");
        opsToFloatMethods.put("unwrapRaw", "floatToRawIntBits");

        opsToDoubleMethods.put("wrap", "longBitsToDouble");
        opsToDoubleMethods.put("unwrap", "doubleToLongBits");
        opsToDoubleMethods.put("unwrapRaw", "doubleToRawLongBits");
    }

    @Override
    protected void process(StringBuilder builder, Context source, Context target, String template) {
        StringBuilder sb = new StringBuilder();
        CheckingMatcher m = WRAPPING_P.matcher(template);
        while (m.find()) {
            String body = m.group(m.group("closed") != null ? "closedBody" : "openBody");
            Option targetType = target.getOption(m.group("dim"));
            String repl = body;
            if (targetType == PrimitiveType.FLOAT) {
                repl = "Float." + opsToFloatMethods.get(m.group("op")) + "(" + repl + ")";
            } else if (targetType == PrimitiveType.DOUBLE) {
                repl = "Double." + opsToDoubleMethods.get(m.group("op")) + "(" + repl + ")";
            }
            m.appendSimpleReplacement(sb, repl);
        }
        m.appendTail(sb);
        postProcess(builder, source, target, sb.toString());
    }
}
