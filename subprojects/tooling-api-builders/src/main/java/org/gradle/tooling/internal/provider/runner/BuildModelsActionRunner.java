/*
 * Copyright 2016 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.tooling.internal.provider.runner;

import org.gradle.api.BuildCancelledException;
import org.gradle.api.Project;
import org.gradle.api.initialization.IncludedBuild;
import org.gradle.api.internal.GradleInternal;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.composite.internal.IncludedBuildInternal;
import org.gradle.execution.ProjectConfigurer;
import org.gradle.initialization.ReportedException;
import org.gradle.internal.invocation.BuildAction;
import org.gradle.internal.invocation.BuildActionRunner;
import org.gradle.internal.invocation.BuildController;
import org.gradle.tooling.GradleConnectionException;
import org.gradle.tooling.internal.protocol.BuildExceptionVersion1;
import org.gradle.tooling.internal.protocol.InternalBuildCancelledException;
import org.gradle.tooling.internal.protocol.InternalModelResults;
import org.gradle.tooling.internal.protocol.InternalUnsupportedModelException;
import org.gradle.tooling.internal.provider.BuildActionResult;
import org.gradle.tooling.internal.provider.BuildModelsAction;
import org.gradle.tooling.internal.provider.PayloadSerializer;
import org.gradle.tooling.provider.model.ToolingModelBuilder;
import org.gradle.tooling.provider.model.ToolingModelBuilderRegistry;
import org.gradle.tooling.provider.model.UnknownModelException;
import org.gradle.tooling.provider.model.internal.ProjectToolingModelBuilder;
import org.gradle.tooling.provider.model.internal.ToolingModelBuilderContext;

import java.io.File;

public class BuildModelsActionRunner implements BuildActionRunner {

    @Override
    public void run(final BuildAction action, final BuildController buildController) {
        if (!(action instanceof BuildModelsAction)) {
            return;
        }

        BuildModelsAction buildModelsAction = (BuildModelsAction) action;
        GradleInternal gradle = buildController.getGradle();

        String modelName = buildModelsAction.getModelName();
        InternalModelResults<Object> compositeResults = new InternalModelResults<Object>();
        try {
            buildController.configure();
            forceFullConfiguration(gradle);
            collectModelsFromThisBuild(gradle, modelName, compositeResults);
            collectModelsFromIncludedBuilds(gradle, modelName, compositeResults);
        } catch (RuntimeException e) {
            compositeResults.addBuildFailure(getRootDir(gradle), transformFailure(e));
        }

        PayloadSerializer payloadSerializer = gradle.getServices().get(PayloadSerializer.class);
        BuildActionResult result = new BuildActionResult(payloadSerializer.serialize(compositeResults), null);
        buildController.setResult(result);
    }

    private void forceFullConfiguration(GradleInternal gradle) {
        gradle.getServices().get(ProjectConfigurer.class).configureHierarchy(gradle.getRootProject());
        for (Project project : gradle.getRootProject().getAllprojects()) {
            ProjectInternal projectInternal = (ProjectInternal) project;
            projectInternal.getTasks().discoverTasks();
            projectInternal.bindAllModelRules();
        }
    }

    private void collectModelsFromThisBuild(GradleInternal gradle, String modelName, InternalModelResults<Object> compositeResults) {
        ensureConnectedToRootProject(gradle);
        try {
            collectModels(gradle, modelName, compositeResults);
        } catch (RuntimeException e) {
            compositeResults.addBuildFailure(getRootDir(gradle), transformFailure(e));
        }
    }

    private void ensureConnectedToRootProject(GradleInternal gradle) {
        if (!gradle.getDefaultProject().equals(gradle.getRootProject())) {
            throw new GradleConnectionException("GradleConnection must be connected to the root project of a build.");
        }
    }

    private void collectModelsFromIncludedBuilds(GradleInternal gradle, String modelName, InternalModelResults<Object> compositeResults) {
        for (IncludedBuild includedBuild : gradle.getIncludedBuilds()) {
            collectModelsFromIncludedBuild(modelName, compositeResults, includedBuild);
        }
    }

    private void collectModelsFromIncludedBuild(String modelName, InternalModelResults<Object> compositeResults, IncludedBuild includedBuild) {
        IncludedBuildInternal includedBuildInternal = (IncludedBuildInternal) includedBuild;
        try {
            GradleInternal includedGradle = includedBuildInternal.getConfiguredBuild();
            forceFullConfiguration(includedGradle);
            collectModels(includedGradle, modelName, compositeResults);
        } catch (RuntimeException e) {
            compositeResults.addBuildFailure(includedBuild.getProjectDir(), transformFailure(e));
        }
    }

    private void collectModels(GradleInternal gradle, String modelName, InternalModelResults<Object> models) {
        ToolingModelBuilder builder = getToolingModelBuilder(gradle, modelName);
        if (builder instanceof ProjectToolingModelBuilder) {
            ToolingModelBuilderContext context = new DefaultToolingModelBuilderContext(models);
            ((ProjectToolingModelBuilder) builder).addModels(modelName, gradle.getDefaultProject(), context);
        } else {
            Object buildScopedModel = builder.buildAll(modelName, gradle.getDefaultProject());
            models.addBuildModel(getRootDir(gradle), buildScopedModel);
        }
    }

    private File getRootDir(GradleInternal gradle) {
        try {
            return gradle.getRootProject().getProjectDir();
        } catch (IllegalStateException e) {
            return gradle.getStartParameter().getProjectDir();
        }
    }

    private RuntimeException transformFailure(RuntimeException e) {
        if (e instanceof BuildCancelledException) {
            throw new InternalBuildCancelledException(e.getCause());
        }
        if (e instanceof ReportedException) {
            return unwrap((ReportedException) e);
        }
        return e;
    }

    private RuntimeException unwrap(ReportedException e) {
        Throwable t = e.getCause();
        while (t != null) {
            if (t instanceof BuildCancelledException) {
                throw new InternalBuildCancelledException(e.getCause());
            }
            t = t.getCause();
        }
        return new BuildExceptionVersion1(e.getCause());
    }

    private ToolingModelBuilder getToolingModelBuilder(GradleInternal gradle, String modelName) {
        ToolingModelBuilderRegistry builderRegistry = getToolingModelBuilderRegistry(gradle);
        try {
            return builderRegistry.getBuilder(modelName);
        } catch (UnknownModelException e) {
            throw (InternalUnsupportedModelException) new InternalUnsupportedModelException().initCause(e);
        }
    }

    private ToolingModelBuilderRegistry getToolingModelBuilderRegistry(GradleInternal gradle) {
        return gradle.getDefaultProject().getServices().get(ToolingModelBuilderRegistry.class);
    }
}
