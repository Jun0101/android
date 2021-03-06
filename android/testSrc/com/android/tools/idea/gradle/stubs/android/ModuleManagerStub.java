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
package com.android.tools.idea.gradle.stubs.android;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.impl.ModuleEx;
import com.intellij.openapi.module.impl.ModuleManagerImpl;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

/**
 * Stub implementation of {@link ModuleManagerImpl} for tests.
 */
public class ModuleManagerStub extends ModuleManagerImpl {

  private Module[] myModules;

  public ModuleManagerStub(Project project, Module[] modules) {
    super(project);
    myModules = modules;
  }

  @NotNull
  @Override
  protected ModuleEx createModule(@NotNull String filePath) {
    throw new UnsupportedOperationException();
  }

  @NotNull
  @Override
  protected ModuleEx createAndLoadModule(@NotNull String filePath) throws IOException {
    throw new UnsupportedOperationException();
  }

  @NotNull
  @Override
  public Module[] getModules() {
    return myModules;
  }
}
