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

import io.timeandspace.jpsg.function.Predicate;
import io.timeandspace.jpsg.function.UnaryOperator;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.ConventionTask;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;


public class JpsgTask extends ConventionTask {

    private final Generator g = new Generator();
    private FileCollection inputFiles;

    public JpsgTask setDefaultTypes(String defaultTypes) {
        g.setDefaultTypes(defaultTypes);
        return this;
    }

    public JpsgTask with(Iterable<String> defaultContext) {
        g.with(defaultContext);
        return this;
    }

    public JpsgTask with(String... defaultContext) {
        g.with(defaultContext);
        return this;
    }

    public JpsgTask addToDefaultContext(String... defaultContext) {
        return with(defaultContext);
    }

    public JpsgTask addProcessor(TemplateProcessor processor) {
        g.addProcessor(processor);
        return this;
    }

    public JpsgTask addProcessor(Class<? extends TemplateProcessor> processorClass) {
        g.addProcessor(processorClass);
        return this;
    }

    public JpsgTask addProcessor(String processorClassName) {
        g.addProcessor(processorClassName);
        return this;
    }

    public JpsgTask addPrimitiveTypeModifierProcessors(String keyword,
            UnaryOperator<PrimitiveType> typeMapper, Predicate<String> dimFilter) {
        g.addPrimitiveTypeModifierProcessors(keyword, typeMapper, dimFilter);
        return this;
    }

    public JpsgTask never(Iterable<String> options) {
        g.never(options);
        return this;
    }

    public JpsgTask never(String... options) {
        g.never(options);
        return this;
    }

    public JpsgTask include(Iterable<String> conditions) {
        g.include(conditions);
        return this;
    }

    public JpsgTask include(String... conditions) {
        g.include(conditions);
        return this;
    }

    public JpsgTask exclude(Iterable<String> conditions) {
        g.exclude(conditions);
        return this;
    }

    public JpsgTask exclude(String... conditions) {
        g.exclude(conditions);
        return this;
    }

    public JpsgTask setSource(File sourceDir) {
        g.setSource(sourceDir);
        inputFiles = getProject().fileTree(sourceDir);
        return this;
    }

    public JpsgTask setSource(Path source) {
        return setSource(source.toFile());
    }

    public JpsgTask setSource(String source) {
        return setSource(new File(source));
    }

    @InputDirectory
    public File getSource() {
        return g.getSource();
    }

    @InputFiles
    public FileCollection getInputFiles() {
        return inputFiles;
    }

    public JpsgTask setTarget(File target) {
        g.setTarget(target);
        return this;
    }

    public JpsgTask setTarget(Path target) {
        g.setTarget(target.toFile());
        return this;
    }

    public JpsgTask setTarget(String target) {
        g.setTarget(target);
        return this;
    }

    @OutputDirectory
    public File getTarget() {
        return g.getTarget();
    }

    @TaskAction
    public void generate() throws IOException {
        g.generate();
    }
}
