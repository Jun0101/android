/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.sync.setup.post;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class ProjectSetupStep {
  private static final ExtensionPointName<ProjectSetupStep>
    EXTENSION_POINT_NAME = ExtensionPointName.create("com.android.gradle.sync.postSyncProjectSetupStep");

  @NotNull
  public static ProjectSetupStep[] getExtensions() {
    return EXTENSION_POINT_NAME.getExtensions();
  }

  public abstract void setUpProject(@NotNull Project project, @Nullable ProgressIndicator indicator);

  public boolean invokeOnFailedSync() {
    return false;
  }
}
