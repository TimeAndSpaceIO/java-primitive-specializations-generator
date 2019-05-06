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

package io.timeandspace.jpsg

import java.util.*


class Dimensions private constructor(internal val dimensions: LinkedHashMap<String, List<Option>>) {

    class Parser internal constructor(private val defaultTypes: List<Option>) {

        internal fun parseClassName(className: String): Dimensions {
            val defaultTypesFoundInClassName: MutableList<Pair<Option, Int>> = arrayListOf()
            for (defaultType in defaultTypes) {
                val replaced = defaultType.intermediateReplace(className, "dummy")
                val firstIntermediateReplaceIndex = replaced.indexOf('#')
                if (firstIntermediateReplaceIndex >= 0) {
                    defaultTypesFoundInClassName.add(
                            Pair(defaultType, firstIntermediateReplaceIndex)
                    )
                }
            }
            // Sort by firstIntermediateReplaceIndex
            defaultTypesFoundInClassName.sortBy { it.second }

            val dimensions = LinkedHashMap<String, List<Option>>()
            for ((i, typeInClassName) in defaultTypesFoundInClassName.withIndex()) {
                // Dimension names are conventional generic variable names in Java,
                // starting with 't'.
                addClassNameDim(dimensions, Character.toString('t' + i), typeInClassName.first)
            }

            return Dimensions(dimensions)
        }

        /**
         * @param descriptor in format "opt1|opt2 dim1 opt3|opt4 dim2"
         */
        fun parseForContext(descriptor: String): Dimensions {
            try {
                return parse(descriptor, null, false)
            } catch (e: NonexistentDimensionException) {
                throw AssertionError(e)
            }

        }

        @Throws(NonexistentDimensionException::class)
        fun parseForCondition(descriptor: String, context: Context): Dimensions {
            return parse(descriptor, context, true)
        }

        @Throws(NonexistentDimensionException::class)
        private fun parse(descriptor: String, context: Context?, checkContext: Boolean):
                Dimensions {
            if (!DIMENSIONS_P.matcher(descriptor).matches()) {
                throw MalformedTemplateException.near(descriptor, 0,
                        "Expected a String in <dimensions> format, see the JPSG Tutorial")
            }
            val m = DIMENSION_P.matcher(descriptor)
            val dimensions = LinkedHashMap<String, List<Option>>()
            while (m.find()) {
                val dim = m.group("dim")
                if (checkContext && context!!.getOption(dim) == null)
                    throw NonexistentDimensionException()
                val opts = parseOptions(m.group("options"))
                dimensions[dim] = opts
            }
            return Dimensions(dimensions)
        }

        /**
         * @param descriptor in format "dim1=opt1|opt2,dim2=opt3|opt4"
         */
        internal fun parseCLI(descriptor: String): Dimensions {
            val dimDescriptors = descriptor.split(",".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            val dimensions = LinkedHashMap<String, List<Option>>()
            for (dimDescriptor in dimDescriptors) {
                val parts = dimDescriptor.split("=".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                dimensions[parts[0]] = parseOptions(parts[1])
            }
            return Dimensions(dimensions)
        }

        private fun addClassNameDim(dimensions: LinkedHashMap<String, List<Option>>, dim: String, main: Option) {
            val keyOptions = ArrayList<Option>()
            keyOptions.add(main)
            defaultTypes.filter { type -> !keyOptions.contains(type) }.forEach { keyOptions.add(it) }
            dimensions[dim] = keyOptions
        }

        companion object {

            internal fun parseOptions(options: String): List<Option> {
                val opts = options.split("\\|".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                val result = ArrayList<Option>()
                for (option in opts) {
                    result.add(parseOption(option))
                }
                return result
            }

            private fun parseOption(opt: String): Option {
                return when (val upperCaseOpt = opt.toUpperCase()) {
                    in PrimitiveType.UPPER_CASE_NAME_TO_TYPE ->
                        PrimitiveType.UPPER_CASE_NAME_TO_TYPE[upperCaseOpt]!!
                    "OBJ", "OBJECT" ->
                        ObjectType.get(ObjectType.IdStyle.valueOf(upperCaseOpt))
                    else -> SimpleOption(opt)
                }
            }
        }
    }

    fun generateContexts(): List<Context> {
        var totalCombinations = 1
        for (options in dimensions.values) {
            totalCombinations *= options.size
        }
        val contexts = ArrayList<Context>(totalCombinations)
        for (comb in 0 until totalCombinations) {
            val cb = Context.builder()
            var combRem = comb
            for (e in dimensions.entries) {
                val options = e.value
                val index = combRem % options.size
                combRem /= options.size
                val option = options[index]
                cb.put(e.key, option)
            }
            contexts.add(cb.makeContext())
        }
        return contexts
    }

    fun checkAsCondition(context: Context): Boolean {
        for ((dim, conditionOptions) in dimensions) {
            val contextOption = context.getOption(dim)
            if (!conditionOptions.contains(contextOption))
                return false
        }
        return true
    }


    override fun toString(): String {
        return dimensions.toString()
    }

    companion object {

        private const val OPTIONS = "([\\w]+)((\\|[\\w]+)+)?"

        private const val DIMENSION = "(?<options>$OPTIONS)\\s+(?<dim>\\w+)"

        internal const val DIMENSIONS = "(?<dimensions>(\\s*$DIMENSION\\s*)+)"

        private val DIMENSION_P = RegexpUtils.compile(DIMENSION)

        internal val DIMENSIONS_P = RegexpUtils.compile(DIMENSIONS)
    }
}
