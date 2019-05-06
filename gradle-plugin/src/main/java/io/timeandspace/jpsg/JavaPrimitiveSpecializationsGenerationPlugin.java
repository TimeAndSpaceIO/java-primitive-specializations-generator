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

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.DependencySet;
import org.gradle.api.file.SourceDirectorySet;
import org.gradle.api.internal.tasks.DefaultSourceSet;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.plugins.JavaPluginConvention;
import org.gradle.api.tasks.SourceSet;

import javax.inject.Inject;
import java.io.File;
import java.util.Set;


/**
 * Follows the implementation of the Antlr Gradle Plugin: https://github.com/gradle/gradle/blob/
 * ea975c0bde023b6df76bc22eda2a594e9ae28d44/subprojects/antlr/src/main/java/org/gradle/api/plugins/
 * antlr/AntlrPlugin.java
 */
public final class JavaPrimitiveSpecializationsGenerationPlugin implements Plugin<Project> {
    public static final String JPSG_CONFIGURATION_NAME = "jpsg";

    private static final String VERSION = JavaPrimitiveSpecializationsGenerationPlugin.class
            .getPackage().getImplementationVersion();

    private enum Flavor {
        JAVA("java") {
            @Override
            String sourceSetNameEnd() {
                return " Java primitive specialization sources";
            }

            @Override
            SourceDirectorySet getSourceDirectorySet(SourceSet sourceSet) {
                return sourceSet.getJava();
            }

            @Override
            String dependentTaskName(SourceSet sourceSet) {
                return sourceSet.getCompileJavaTaskName();
            }
        },

        RESOURCES("resource") {
            @Override
            String sourceSetNameEnd() {
                return " primitive specializations of resources";
            }

            @Override
            String generatedPart() {
                return "resources";
            }

            @Override
            SourceDirectorySet getSourceDirectorySet(SourceSet sourceSet) {
                return sourceSet.getResources();
            }

            @Override
            String dependentTaskName(SourceSet sourceSet) {
                return sourceSet.getProcessResourcesTaskName();
            }
        };

        private final String word;

        Flavor(String word) {
            this.word = word;
        }

        abstract String sourceSetNameEnd();
        String templatesPart() {
            return word + "Templates";
        }
        String taskNameSuffix() {
            return word.substring(0, 1).toUpperCase() + word.substring(1) + "Specializations";
        }
        String taskDescriptionFormat() {
            return "Processes the %s " + word + " specialization sources.";
        }
        String generatedPart() {
            return word;
        }
        abstract SourceDirectorySet getSourceDirectorySet(SourceSet sourceSet);
        abstract String dependentTaskName(SourceSet sourceSet);
    }

    private final ObjectFactory objectFactory;

    @Inject
    public JavaPrimitiveSpecializationsGenerationPlugin(ObjectFactory objectFactory) {
        this.objectFactory = objectFactory;
    }

    @Override
    public void apply(final Project project) {
        project.getPlugins().apply(JavaPlugin.class);

        // set up a configuration named 'jpsg' for the user to specify the jpsg libs to use in case
        // they want a specific version etc.
        Configuration jpsgConfiguration = project.getConfigurations()
                .create(JPSG_CONFIGURATION_NAME)
                .setVisible(false)
                .setTransitive(false)
                .setDescription("The Jpsg libraries to be used for this project.");

        jpsgConfiguration.defaultDependencies((DependencySet dependencies) -> {
            String jpsgDependency = "io.timeandspace:jpsg-core:" + VERSION + "@jar";
            dependencies.add(project.getDependencies().create(jpsgDependency));
        });

        project.getConfigurations()
                .getByName(JavaPlugin.COMPILE_CONFIGURATION_NAME)
                .extendsFrom(jpsgConfiguration);

        project.getConvention().getPlugin(JavaPluginConvention.class).getSourceSets().all(
                sourceSet -> {
                    setupTask(project, sourceSet, Flavor.JAVA);
                    setupTask(project, sourceSet, Flavor.RESOURCES);
                });
    }

    private void setupTask(Project project, SourceSet sourceSet, Flavor f) {
        // for each source set we will:
        // 1) Add a new 'javaTemplates' or 'resourceTemplates' source dirs
        String sourceSetName = ((DefaultSourceSet) sourceSet).getDisplayName();
        SourceDirectorySet templates = objectFactory.sourceDirectorySet(sourceSetName + ".jpsg",
                sourceSetName + f.sourceSetNameEnd());
        String dirPath = String.format("src/%s/%s", sourceSet.getName(), f.templatesPart());
        templates.srcDir(dirPath);
        sourceSet.getAllSource().source(templates);

        Set<File> srcDirs = templates.getSrcDirs();
        if (srcDirs.size() != 1) {
            throw new RuntimeException("Expected " + srcDirs + " to contain only one directory");
        }
        File srcDir = srcDirs.iterator().next();
        if (!srcDir.exists()) {
            return;
        }

        // 2) create an JpsgTask for this sourceSet following the gradle
        //    naming conventions via call to sourceSet.getTaskName()
        final String taskName = sourceSet.getTaskName("generate", f.taskNameSuffix());

        // 3) Set up the JPSG output directory (adding to javac inputs!)
        final String outputDirectoryName = String.format("%s/generated-src/jpsg/%s/%s",
                project.getBuildDir(), sourceSet.getName(), f.generatedPart());
        final File outputDirectory = new File(outputDirectoryName);
        f.getSourceDirectorySet(sourceSet).srcDir(outputDirectory);

        project.getTasks().register(taskName, JpsgTask.class, jpsgTask -> {
            jpsgTask.setDescription(String.format(f.taskDescriptionFormat(), sourceSet.getName()));
            // 4) set up convention mapping for default sources
            // (allows user to not have to specify)
            jpsgTask.setSource(srcDir);
            jpsgTask.setTarget(outputDirectoryName);
        });

        // 5) register fact that JPSG should be run before compiling
        project.getTasks().named(f.dependentTaskName(sourceSet), task -> task.dependsOn(taskName));
    }
}
