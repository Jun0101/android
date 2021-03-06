/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.tests.gui.framework.fixture.designer;

import com.android.tools.idea.tests.gui.framework.fixture.ComponentFixture;
import com.android.tools.idea.uibuilder.property.assistant.ComponentAssistant;
import org.fest.swing.core.Robot;

import java.awt.*;

public class ComponentAssistantFixture extends ComponentFixture<ComponentAssistantFixture, Component> {
  private final ComponentAssistant myAssistantPanel;

  public ComponentAssistantFixture(Robot robot, ComponentAssistant assistant) {
    super(ComponentAssistantFixture.class, robot, assistant);
    myAssistantPanel = assistant;
  }
}
