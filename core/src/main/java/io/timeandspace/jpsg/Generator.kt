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

import io.timeandspace.jpsg.CheckingPattern.compile
import io.timeandspace.jpsg.Dimensions.Parser.Companion.parseOptions
import io.timeandspace.jpsg.GeneratorConstants.*
import io.timeandspace.jpsg.MalformedTemplateException.Companion.near
import io.timeandspace.jpsg.RegexpUtils.removeSubGroupNames
import io.timeandspace.jpsg.concurrent.ForkJoinTaskShim
import io.timeandspace.jpsg.concurrent.ForkJoinTasks
import io.timeandspace.jpsg.function.Predicate
import io.timeandspace.jpsg.function.UnaryOperator
import org.slf4j.LoggerFactory
import java.io.File
import java.io.IOException
import java.lang.String.format
import java.util.*
import java.util.concurrent.Callable
import java.util.regex.Pattern


class Generator {

    private var isInit = false
    private var source: File? = null
    internal var target: File? = null

    private var defaultTypes: MutableList<Option> =
            ArrayList(PrimitiveType.NUMERIC_TYPES_WITH_SHORT_IDS)
    private var dimensionsParser: Dimensions.Parser? = null

    private val processors = mutableListOf(
            // in chronological order
            OptionProcessor(),
            ConstProcessor(),
            BlocksProcessor(),
            GenericsProcessor(),
            DefinitionProcessor(),
            FloatingWrappingProcessor(),
            RawModifierProcessor(),
            BitsModifierPreProcessor(),
            BitsModifierPostProcessor(),
            AAnProcessor(),
            OverviewProcessor(),
            PrintProcessor()
    )

    private class UnparsedDimensions(
            val dimensions: String,
            val parse: (Dimensions.Parser, String) -> Dimensions)

    private fun Dimensions.Parser.parse(unparsedDimensions: UnparsedDimensions): Dimensions {
        return unparsedDimensions.parse(this, unparsedDimensions.dimensions)
    }

    private val with = ArrayList<UnparsedDimensions>()
    private var defaultContext: Context? = null

    private val never = ArrayList<String>()
    private var excludedTypes: List<Option>? = null

    private val included = ArrayList<UnparsedDimensions>()
    private var permissiveConditions: List<Dimensions>? = null

    private val excluded = ArrayList<UnparsedDimensions>()
    private var prohibitingConditions: List<Dimensions>? = null

    private var firstProcessor: TemplateProcessor? = null

    fun setDefaultTypes(defaultTypes: String): Generator {
        val defaultTypes = ArrayList(parseOptions(defaultTypes))
        for (option in defaultTypes) {
            if (option is SimpleOption) {
                throw IllegalArgumentException(
                        "Simple options like $option are not allowed in defaultTypes configuration")
            }
        }
        this.defaultTypes = defaultTypes
        return this
    }

    fun withCLI(defaultContext: Iterable<String>) : Generator {
        for (cxt in defaultContext) {
            with.add(UnparsedDimensions(cxt, Dimensions.Parser::parseCLI))
        }
        return this
    }

    fun with(defaultContext: Iterable<String>): Generator {
        for (cxt in defaultContext) {
            val withDimensions: Dimensions = checkingDimensionsParser.parseForContext(cxt)
            for ((dim, options) in withDimensions.dimensions) {
                if (options.size > 1) {
                    throw IllegalArgumentException(
                            "with() accepts only dimensions with a single option, $dim has $options"
                    )
                }
            }
            with.add(UnparsedDimensions(cxt, Dimensions.Parser::parseForContext))
        }
        return this
    }

    fun with(vararg defaultContext: String): Generator {
        return with(Arrays.asList(*defaultContext))
    }

    fun addProcessor(processor: TemplateProcessor): Generator {
        processors.add(processor)
        return this
    }

    fun addPrimitiveTypeModifierProcessors(
            keyword: String, typeMapper: UnaryOperator<PrimitiveType>,
            dimFilter: Predicate<String>): Generator {
        addProcessor(PrimitiveTypeModifierPreProcessor(keyword, typeMapper, dimFilter))
        addProcessor(PrimitiveTypeModifierPostProcessor(keyword, typeMapper, dimFilter))
        return this
    }

    fun addProcessor(processorClass: Class<out TemplateProcessor>): Generator {
        try {
            return addProcessor(processorClass.newInstance())
        } catch (e: InstantiationException) {
            throw IllegalArgumentException("$processorClass template processor class should have public no-arg constructor")
        } catch (e: IllegalAccessException) {
            throw IllegalArgumentException("$processorClass template processor class should have public no-arg constructor")
        }

    }

    fun addProcessor(processorClassName: String): Generator {
        try {
            // noinspection unchecked
            return addProcessor(
                    Class.forName(processorClassName) as Class<out TemplateProcessor>)
        } catch (e: ClassNotFoundException) {
            throw IllegalArgumentException("Template processor class with $processorClassName name is not found")
        }

    }

    fun never(options: Iterable<String>): Generator {
        for (opts in options) {
            never.add(opts)
        }
        return this
    }

    fun never(vararg options: String): Generator {
        return never(Arrays.asList(*options))
    }

    fun includeCLI(conditions: Iterable<String>): Generator {
        for (condition in conditions) {
            included.add(UnparsedDimensions(condition, Dimensions.Parser::parseCLI))
        }
        return this
    }

    fun include(conditions: Iterable<String>): Generator {
        for (condition in conditions) {
            included.add(UnparsedDimensions(condition, Dimensions.Parser::parseForContext))
        }
        return this
    }

    fun include(vararg conditions: String): Generator {
        return include(Arrays.asList(*conditions))
    }

    fun excludeCLI(conditions: Iterable<String>): Generator {
        for (condition in conditions) {
            excluded.add(UnparsedDimensions(condition, Dimensions.Parser::parseCLI))
        }
        return this
    }

    fun exclude(conditions: Iterable<String>): Generator {
        for (condition in conditions) {
            excluded.add(UnparsedDimensions(condition, Dimensions.Parser::parseForContext))
        }
        return this
    }

    fun exclude(vararg conditions: String): Generator {
        return exclude(Arrays.asList(*conditions))
    }


    fun setSource(source: File): Generator {
        this.source = source
        return this
    }

    fun setSource(source: String): Generator {
        this.source = File(source)
        return this
    }

    fun getSource(): File {
        return source!!
    }

    fun setTarget(target: File): Generator {
        this.target = target
        return this
    }

    fun setTarget(target: String): Generator {
        this.target = File(target)
        return this
    }

    fun getTarget(): File {
        return target!!
    }

    @Throws(IOException::class)
    fun generate() {
        log.debug("Generator source: {}", source)
        log.debug("Generator target: {}", target)
        if (!source!!.exists()) {
            return
        }
        if (!target!!.exists()) {
            target!!.mkdirs()
        } else if (!target!!.isDirectory) {
            log.error("Target {} should be a dir", target)
            throw IllegalArgumentException("$target generation destination should be a dir")
        }
        init()
        if (source!!.isDirectory) {
            class DirGeneration(val dir: File) : Callable<Unit> {

                override fun call() {
                    try {
                        val subTasks = ArrayList<ForkJoinTaskShim<Unit>>()
                        val targetDir = target!!.resolve(dir.relativeTo(this@Generator.source!!))
                        try {
                            dir.copyTo(targetDir)
                        } catch (e: IOException) {
                            if (!targetDir.isDirectory)
                                throw e
                        }

                        dir.walkTopDown().onEnter { d ->
                            if (dir != d) {
                                subTasks.add(ForkJoinTasks.adapt(DirGeneration(d)))
                                false
                            } else {
                                true
                            }
                        }.filter { it.isFile }.forEach { f ->
                            val targetFile = target!!.resolve(f.relativeTo(source!!))
                            if (f.lastModified() < targetFile.lastModified()) {
                                log.info("File {} is up to date, not processing source",
                                        targetFile)
                            }
                            subTasks.add(ForkJoinTasks.adapt(Callable<Unit> {
                                doGenerate(f, File(targetFile.parent))
                            }))
                        }
                        ForkJoinTasks.invokeAll(subTasks)
                    } catch (e: IOException) {
                        throw RuntimeException(e)
                    }

                }
            }
            ForkJoinTasks.adapt(DirGeneration(source!!)).forkAndGet()
        } else {
            ForkJoinTasks.adapt(Callable<Unit> { doGenerate(source!!, target!!) }).forkAndGet()
        }
    }


    @Synchronized fun init() {
        if (isInit)
            return
        isInit = true
        dimensionsParser = Dimensions.Parser(defaultTypes)

        defaultContext = Context.Builder().makeContext()
        for (context in with) {
            val contexts = dimensionsParser!!.parse(context).generateContexts()
            if (contexts.size > 1) {
                throw IllegalArgumentException(
                        "Default context should have only trivial dimensions")
            }
            defaultContext = defaultContext!!.join(contexts[0])
        }

        excludedTypes = never.flatMap { options -> parseOptions(options) }.toList()

        permissiveConditions = included.map { dimensionsParser!!.parse(it) }.toList()

        prohibitingConditions = excluded.map { dimensionsParser!!.parse(it) }.toList()

        initProcessors()
    }

    private fun initProcessors() {
        processors.sortWith(compareBy(TemplateProcessor::priority))
        var prev: TemplateProcessor? = null
        for (processor in processors) {
            processor.dimensionsParser = dimensionsParser
            processor.setNext(prev)
            prev = processor
        }
        firstProcessor = processors[processors.size - 1]
    }

    @Throws(IOException::class)
    private fun doGenerate(sourceFile: File, targetDir: File) {
        setCurrentGenerator(this)
        setCurrentSourceFile(sourceFile)
        log.info("Processing file: {}", sourceFile)
        val sourceFileName = sourceFile.name
        var targetDims: Dimensions = dimensionsParser!!.parseClassName(sourceFileName)
        var rawContent = sourceFile.readText()
        val fileDimsM = CONTEXT_START_P.matcher(rawContent)
        if (fileDimsM.find() && fileDimsM.start() == 0) {
            val explicitContext = fileDimsM.group()
            targetDims = parseAndCheckExplicitContext(explicitContext, sourceFile)
            rawContent = rawContent.substring(fileDimsM.end()).trim { it <= ' ' } + "\n"
        }
        log.info("Target dimensions: {}", targetDims)
        val targetContexts: List<Context> = targetDims.generateContexts()
        val mainContext = defaultContext!!.join(targetContexts[0])
        val fileCondM = COND_START_P.matcher(rawContent)
        var fileCond: Condition? = null
        if (fileCondM.find() && fileCondM.start() == 0) {
            fileCond = Condition.parseCheckedCondition(
                    getBlockGroup(fileCondM.group(), COND_START_BLOCK_P, "condition"),
                    dimensionsParser!!, mainContext,
                    rawContent, fileCondM.start())
            rawContent = rawContent.substring(fileCondM.end()).trim { it <= ' ' } + "\n"
        }
        val content = rawContent

        val contextGenerationTasks = ArrayList<ForkJoinTaskShim<Unit>>()
        for (tc in targetContexts) {
            if (!checkContext(tc)) {
                log.debug("Context filtered by generator: {}", tc)
                continue
            }
            val target = defaultContext!!.join(tc)
            if (fileCond != null && !fileCond.check(target)) {
                log.debug("Context filtered by file condition: {}", target)
                continue
            }
            var generatedFileName = generate(mainContext, target, sourceFileName)
            var generatedFile = targetDir.resolve(generatedFileName)
            contextGenerationTasks.add(ForkJoinTasks.adapt(Callable<Unit> {
                setCurrentGenerator(this@Generator)
                setCurrentSourceFile(sourceFile)
                setRedefinedClassName(null)
                val generatedContent = generate(mainContext, target, content)
                val redefinedClassName: String? = getRedefinedClassName()
                // `substringAfterLast('.')` in order to support service file names in resources:
                // META-INF/services/com.mypackage.ByteShortType
                val generatedClassName =
                        generatedFileName.removeSuffix(".java").substringAfterLast('.')
                if (redefinedClassName != null && generatedClassName != redefinedClassName) {
                    log.info("Class name redefined: {} -> {}",
                            generatedClassName, redefinedClassName)
                    generatedFileName =
                            generatedFileName.replace(generatedClassName, redefinedClassName)
                    generatedFile = targetDir.resolve(generatedFileName)
                }
                if (generatedFile.exists()) {
                    if (generatedFile.isDirectory) {
                        throw IllegalStateException(
                                "$generatedFileName in $targetDir is a directory, " +
                                        "$mainContext, $target, $sourceFileName")
                    }
                    val targetContent = generatedFile.readText()
                    if (generatedContent == targetContent) {
                        log.warn("Already generated: {}", generatedFileName)
                        return@Callable
                    }
                }
                writeFile(generatedFile, generatedContent)
                log.info("Wrote: {}", generatedFileName)
            }))
        }
        ForkJoinTasks.invokeAll(contextGenerationTasks)
    }

    private fun parseAndCheckExplicitContext(explicitContext: String, sourceFile: File):
            Dimensions {
        val targetDims: Dimensions = dimensionsParser!!.parseForContext(
                getBlockGroup(explicitContext, CONTEXT_START_BLOCK_P, "dimensions"))
        val mainExplicitContext: Context = targetDims.generateContexts()[0]
        for ((dim, option) in mainExplicitContext) {
            if (!Context.stringIncludesOption(sourceFile.name, option)) {
                throw RuntimeException(
                        "Dimension $dim with options ${targetDims.dimensions[dim]} specified " +
                                "explicitly in $sourceFile is not found in the file name")
            }
        }
        return targetDims
    }

    @Throws(IOException::class)
    internal fun writeFile(file: File, content: String) {
        file.writeText(content)
    }

    fun generate(source: Context, target: Context, template: String): String {
        return firstProcessor!!.generate(source, target, template)
    }

    private fun checkContext(target: Context): Boolean {
        for ((_, option) in target) {
            if (excludedTypes!!.contains(option))
                return false
        }
        if (!checkPermissive(target))
            return false
        if (prohibitingConditions!!.isNotEmpty()) {
            for (prohibitingCondition in prohibitingConditions!!) {
                if (prohibitingCondition.checkAsCondition(target))
                    return false
            }
        }
        return true
    }

    private fun checkPermissive(target: Context): Boolean {
        if (permissiveConditions!!.isNotEmpty()) {
            for (permissiveCondition in permissiveConditions!!) {
                if (permissiveCondition.checkAsCondition(target))
                    return true
            }
            // context isn't permitted by any condition
            return false
        }
        return true
    }

    inner class BlocksProcessor : TemplateProcessor() {

        override fun priority(): Int {
            return BLOCKS_PROCESSOR_PRIORITY
        }

        override fun process(sb: StringBuilder, source: Context, target: Context, template: String) {
            val blockStartMatcher = ANY_BLOCK_PART_P.matcher(template)
            var prevBlockEndPos = 0
            blockSearch@ while (blockStartMatcher.find()) {
                val blockDefPos = blockStartMatcher.start()
                val linearBlock = template.substring(prevBlockEndPos, blockDefPos)
                postProcess(sb, source, target, linearBlock)
                val blockStart = blockStartMatcher.group()
                var condM = COND_START_P.matcher(blockStart)
                if (condM.matches()) {
                    var prevBranchCond = Condition.parseCheckedCondition(
                            getBlockGroup(condM.group(), COND_PART_BLOCK_P, "condition"),
                            dimensionsParser!!, source,
                            template, blockStartMatcher.start())
                    var branchStartPos = blockStartMatcher.end()
                    var nest = 0
                    condM = COND_PART_P.matcher(template)
                    condM.region(branchStartPos, template.length)
                    while (condM.find()) {
                        var condPart = condM.group()
                        if (COND_START_P.matcher(condPart).matches()) {
                            nest++
                        } else if (nest != 0) {
                            if (COND_END_P.matcher(condPart).matches()) {
                                nest--
                            }
                            // no special processing of nested `elif` branches
                        } else {
                            if (prevBranchCond.check(target)) {
                                // condition of the previous `if` or `elif` branch
                                // triggers on the context
                                val branchEndPos = condM.start()
                                val block = template.substring(branchStartPos, branchEndPos)
                                process(sb, source, target, block)
                                if (COND_END_P.matcher(condPart).matches()) {
                                    prevBlockEndPos = condM.end()
                                    blockStartMatcher.region(prevBlockEndPos, template.length)
                                    continue@blockSearch
                                } else {
                                    // skip the rest `elif` branches
                                    while (condM.find()) {
                                        condPart = condM.group()
                                        if (COND_END_P.matcher(condPart).matches()) {
                                            if (nest == 0) {
                                                prevBlockEndPos = condM.end()
                                                blockStartMatcher.region(
                                                        prevBlockEndPos, template.length)
                                                continue@blockSearch
                                            } else {
                                                nest--
                                            }
                                        } else if (COND_START_P.matcher(condPart).matches()) {
                                            nest++
                                        }
                                    }
                                    throw near(template, blockDefPos, "`if` block is not closed")
                                }
                            } else {
                                if (COND_END_P.matcher(condPart).matches()) {
                                    prevBlockEndPos = condM.end()
                                    blockStartMatcher.region(prevBlockEndPos, template.length)
                                    continue@blockSearch
                                } else {
                                    // `elif` branch
                                    prevBranchCond = Condition.parseCheckedCondition(
                                            getBlockGroup(condM.group(), COND_PART_BLOCK_P,
                                                    "condition"),
                                            dimensionsParser!!, source,
                                            template, condM.start())
                                    branchStartPos = condM.end()
                                }
                            }
                        }
                    }
                    throw near(template, blockDefPos, "`if` block is not closed")
                } else {
                    // `with` block
                    var contextM = CONTEXT_START_P.matcher(blockStart)
                    if (contextM.matches()) {
                        val additionalDims = dimensionsParser!!.parseForContext(
                                getBlockGroup(contextM.group(), CONTEXT_START_BLOCK_P,
                                        "dimensions"))
                        val blockStartPos = blockStartMatcher.end()
                        var nest = 0
                        contextM = CONTEXT_PART_P.matcher(template)
                        contextM.region(blockStartPos, template.length)
                        while (contextM.find()) {
                            val contextPart = contextM.group()
                            if (CONTEXT_END_P.matcher(contextPart).matches()) {
                                if (nest == 0) {
                                    val blockEndPos = contextM.start()
                                    val block = template.substring(blockStartPos, blockEndPos)
                                    val addContexts = additionalDims.generateContexts()
                                    val newSource = source.join(addContexts[0])
                                    for (addCxt in addContexts) {
                                        val newTarget = target.join(addCxt)
                                        // if addContext size is 1, this context is not
                                        // for generation. For example to prevent
                                        // unwanted generation:
                                        // /*with int|long dim*/ generated int 1 /*with int elem*/
                                        // always int /*endwith*/ generated int 2 /*endwith*/
                                        // -- for example. We don't filter such contexts.
                                        if (addContexts.size == 1 || checkContext(newTarget)) {
                                            process(sb, newSource, newTarget, block)
                                        }
                                    }
                                    prevBlockEndPos = contextM.end()
                                    blockStartMatcher.region(prevBlockEndPos, template.length)
                                    continue@blockSearch
                                } else {
                                    // nesting `with` end
                                    nest--
                                }
                            } else {
                                // nesting `with` start
                                nest++
                            }
                        }
                    } else {
                        throw near(template, blockDefPos,
                                "Block end or `elif` branch without start")
                    }
                }
            }
            val tail = template.substring(prevBlockEndPos)
            postProcess(sb, source, target, tail)
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(Generator::class.java)

        @JvmStatic
        private val checkingDimensionsParser: Dimensions.Parser = Dimensions.Parser(emptyList())

        private val currentSource = ThreadLocal<File>()
        fun setCurrentSourceFile(source: File) {
            currentSource.set(source)
        }

        @JvmStatic
        fun currentSourceFile(): File? {
            return currentSource.get()
        }

        private val currentGenerator = ThreadLocal<Generator>()
        private fun setCurrentGenerator(generator: Generator) {
            currentGenerator.set(generator)
        }

        @JvmStatic
        fun currentGenerator(): Generator {
            return currentGenerator.get()
        }

        private val redefinedClassName = ThreadLocal<String?>()

        @JvmStatic
        fun setRedefinedClassName(className: String?) {
            redefinedClassName.set(className)
        }

        private fun getRedefinedClassName(): String? {
            return redefinedClassName.get()
        }

        @JvmStatic
        fun compileBlock(insideBlockRegex: String, keyword: String): CheckingPattern {
            val checkingBlockBlockCommentOpening = "/\\*\\s*$keyword[^/*]*+[*/]/"
            // Don't allow blocks starting with `//` to be multiline (the only difference of the
            // checkingBlockDoubleSlash regex from checkingBlockBlockCommentOpening is exclusion of
            // '\n'). This is to reduce false-positive "Malformed template near" errors by JPSG on
            // normal // comments that happen to start with "if" or "with".
            val checkingBlockDoubleSlash = "//\\s*$keyword[^/*\\n]*+[*/]/"
            val checkingBlock = "($checkingBlockBlockCommentOpening|$checkingBlockDoubleSlash)"
            val block = "/[*/]\\s*" + removeSubGroupNames(insideBlockRegex) + "\\s*[*/]/"
            return compile(justBlockOrWholeLine(checkingBlock), justBlockOrWholeLine(block))
        }

        private fun justBlockOrWholeLine(block: String): String {
            return format("^[^\\S\\r\\n]*%s[^\\S\\n]*?\\n|%s", block, block)
        }

        @JvmStatic
        fun wrapBlock(insideBlockRegex: String): Pattern {
            return RegexpUtils.compile("\\s*/[*/]\\s*$insideBlockRegex\\s*[*/]/\\s*")
        }

        private fun getBlockGroup(block: String, pattern: Pattern, group: String): String {
            val m = pattern.matcher(block)
            if (m.matches()) {
                return m.group(group)
            } else {
                throw AssertionError()
            }
        }


        const val BLOCKS_PROCESSOR_PRIORITY: Int = TemplateProcessor.DEFAULT_PRIORITY + 100
    }
}
