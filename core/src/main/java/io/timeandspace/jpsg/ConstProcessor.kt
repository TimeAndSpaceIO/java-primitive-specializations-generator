/*
 * Copyright 2014-2018 the original author or authors.
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

package io.timeandspace.jpsg

import io.timeandspace.jpsg.RegexpUtils.JAVA_ID_OR_CONST


class ConstProcessor : TemplateProcessor() {

    override fun process(builder: StringBuilder, source: Context, target: Context, template: String) {
        var template = template
        val valueM = CONST_PATTERN.matcher(template)
        val sb = StringBuilder()
        while (valueM.find()) {
            val dim = valueM.group("dim")
            val option = target.getOption(dim)
            if (option != null) {
                if (option is SimpleOption) {
                    throw MalformedTemplateException.near(template, valueM.start(),
                            "Constant values are not supported for simple options, " +
                                    "$dim option: $option")
                }
                val value = valueM.group("value")
                val replacementValue: String
                replacementValue = if ("default".equals(value!!, ignoreCase = true)) {
                    option.defaultValue()
                } else {
                    if (option !is PrimitiveType || !option.isNumeric) {
                        throw MalformedTemplateException.near(template, valueM.start(),
                                "Constant values other than 'default' are supported only " +
                                        "for primitive numeric types, " +
                                        "value: $value, $dim option: $option")
                    }
                    when {
                        "min".equals(value, ignoreCase = true) -> option.minValue()
                        "max".equals(value, ignoreCase = true) -> option.maxValue()
                        else -> option.formatValue(value)
                    }
                }
                valueM.appendSimpleReplacement(sb, replacementValue)
            } else {
                throw MalformedTemplateException.near(template, valueM.start(),
                        "Nonexistent dimension: $dim, available dims: $target")
            }
        }
        valueM.appendTail(sb)
        template = sb.toString()
        postProcess(builder, source, target, template)
    }

    override fun priority(): Int {
        return PRIORITY
    }

    companion object {
        // after option processor because constant "(char) 0" is generated
        val PRIORITY = OptionProcessor.PRIORITY - 10

        private val PREFIX = "/[\\*/]\\s*const\\s+(?<dim>[a-zA-Z]+)"
        private val CONST_PATTERN = CheckingPattern.compile(PREFIX,
                PREFIX + "\\s+(?<value>-?\\d+|min|max|default)\\s*[\\*/]/" +
                        "([^/]*?/[\\*/]\\s*endconst\\s*[\\*/]/|" +
                        "\\s*+" + JAVA_ID_OR_CONST + ")")
    }
}
