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

import com.google.common.io.Resources
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.reflections.Reflections
import org.reflections.scanners.ResourcesScanner
import java.io.File
import java.nio.file.Path
import java.nio.file.Paths


internal class ExamplesTest {

    @Test
    fun testExamples() {
        val reflections = Reflections("examples", ResourcesScanner())
        val exampleResourcePaths: Set<String> = reflections.getResources {true}
        val exampleSourcePaths: List<String> =
                exampleResourcePaths.filter { name -> !name.contains("generated") }
        exampleSourcePaths.forEach { testExample(it) }
    }

    private fun testExample(exampleSourcePath: String) {
        val exampleSource = Paths.get(exampleSourcePath)
        val exampleSourceFileName: Path = exampleSource.fileName
        println(exampleSourceFileName)

        val generator = Generator()
        generator.init()
        Generator.setCurrentSourceFile(File(exampleSourcePath))
        val template: String = resourceToString(exampleSourcePath)
        val context = Context.builder().makeContext()
        val generatedActual: String = generator.generate(context, context, template)
        val generatedExpectedPath = exampleSource.resolveSibling(
                Paths.get("generated", exampleSourceFileName.toString()))
        val generatedExpected: String = resourceToString(generatedExpectedPath.toString())
        Assertions.assertEquals(generatedExpected, generatedActual)
    }

    private fun resourceToString(path: String): String =
            Resources.toString(Resources.getResource(path), Charsets.UTF_8)
}
