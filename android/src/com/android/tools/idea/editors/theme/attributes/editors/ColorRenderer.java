/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.editors.theme.attributes.editors;

import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.editors.theme.EditedStyleItem;
import com.android.tools.idea.editors.theme.ThemeEditorUtils;
import com.android.tools.idea.rendering.ResourceHelper;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.util.*;
import java.util.List;

public class ColorRenderer implements TableCellRenderer {
  private static final Logger LOG = Logger.getInstance(ColorRenderer.class);

  private final Configuration myConfiguration;

  private final ColorComponent myComponent;
  private final Border mySelectedBorder;
  private final Border myUnselectedBorder;

  public ColorRenderer(@NotNull Configuration configuration, @NotNull JTable table) {
    myConfiguration = configuration;

    myComponent = new ColorComponent(table.getBackground(), table.getFont().deriveFont(Font.BOLD));
    mySelectedBorder = ColorComponent.getBorder(table.getSelectionBackground());
    myUnselectedBorder = ColorComponent.getBorder(table.getBackground());
  }

  @Override
  public Component getTableCellRendererComponent(JTable table, Object obj, boolean isSelected, boolean hasFocus, int row, int column) {
    if (obj instanceof EditedStyleItem) {
      final EditedStyleItem item = (EditedStyleItem) obj;
      final List<Color> colors = ResourceHelper.resolveMultipleColors(myConfiguration.getResourceResolver(), item.getItemResourceValue());

      myComponent.configure(item, colors);
      myComponent.setBorder(isSelected ? mySelectedBorder : myUnselectedBorder);
    } else {
      LOG.error(String.format("Object passed to ColorRendererEditor has class %1$s instead of ItemResourceValueWrapper", obj.getClass().getName()));
    }

    return myComponent;
  }
}
