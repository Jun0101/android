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
package com.android.tools.idea.uibuilder.structure;

import com.android.tools.idea.common.model.*;
import com.android.tools.idea.common.scene.Scene;
import com.android.tools.idea.common.surface.DesignSurface;
import com.android.tools.idea.common.surface.DesignSurfaceActionHandler;
import com.android.tools.idea.common.surface.DesignSurfaceListener;
import com.android.tools.idea.uibuilder.actions.ComponentHelpAction;
import com.android.tools.idea.uibuilder.api.ViewHandler;
import com.android.tools.idea.uibuilder.graphics.NlConstants;
import com.android.tools.idea.uibuilder.model.NlComponentHelperKt;
import com.android.tools.idea.uibuilder.surface.NlDesignSurface;
import com.google.common.collect.Sets;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.ColorUtil;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.TreeSpeedSearch;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.IJSwingUtilities;
import com.intellij.util.ui.JBInsets;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.tree.TreeUtil;
import com.intellij.util.ui.update.MergingUpdateQueue;
import com.intellij.util.ui.update.Update;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;
import java.awt.*;
import java.awt.dnd.DropTarget;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.android.tools.idea.common.property.PropertiesManager.UPDATE_DELAY_MSECS;
import static com.intellij.util.Alarm.ThreadToUse.SWING_THREAD;

public class NlComponentTree extends Tree implements DesignSurfaceListener, ModelListener, SelectionListener, Disposable,
                                                     DataProvider {
  private static final Insets INSETS = new JBInsets(0, 6, 0, 6);
  private static final Color LINE_COLOR = ColorUtil.brighter(UIUtil.getTreeSelectionBackground(), 10);

  private final AtomicBoolean mySelectionIsUpdating;
  private final MergingUpdateQueue myUpdateQueue;
  private final NlTreeBadgeHandler myBadgeHandler;

  @Nullable private NlModel myModel;
  private boolean mySkipWait;
  private int myInsertAfterRow = -1;
  private int myRelativeDepthToInsertionRow = 0;
  @Nullable private Rectangle myInsertionRowBounds;
  @Nullable private Rectangle myInsertionReceiverBounds;
  @Nullable private NlDesignSurface mySurface;

  public NlComponentTree(@NotNull Project project, @Nullable NlDesignSurface designSurface) {
    mySelectionIsUpdating = new AtomicBoolean(false);
    myUpdateQueue = new MergingUpdateQueue(
      "android.layout.structure-pane", UPDATE_DELAY_MSECS, true, null, null, null, SWING_THREAD);
    myBadgeHandler = new NlTreeBadgeHandler();

    setModel(new NlComponentTreeModel());

    setBorder(new EmptyBorder(INSETS));
    setDesignSurface(designSurface);
    setName("componentTree");
    setRootVisible(true);
    setShowsRootHandles(false);
    setToggleClickCount(2);
    setCellRenderer(createCellRenderer());

    getSelectionModel().setSelectionMode(TreeSelectionModel.DISCONTIGUOUS_TREE_SELECTION);
    ToolTipManager.sharedInstance().registerComponent(this);
    TreeUtil.installActions(this);
    addTreeSelectionListener(new StructurePaneSelectionListener());
    new StructureSpeedSearch(this);


    enableDnD();

    addMouseListener(new StructurePaneMouseListener());
    addMouseListener(myBadgeHandler.getBadgeMouseAdapter());
    addMouseMotionListener(myBadgeHandler.getBadgeMouseAdapter());

    ComponentHelpAction help = new ComponentHelpAction(project, () -> {
      List<NlComponent> components = getSelectedComponents();
      return !components.isEmpty() ? components.get(0).getTagName() : null;
    });
    help.registerCustomShortcutSet(KeyEvent.VK_F1, InputEvent.SHIFT_MASK, this);
  }

  private void enableDnD() {
    if (!ApplicationManager.getApplication().isHeadlessEnvironment()) {
      setDragEnabled(true);
      setTransferHandler(new TreeTransferHandler());
      setDropTarget(new DropTarget(this, new NlDropListener(this)));
    }
  }

  public void setDesignSurface(@Nullable NlDesignSurface designSurface) {
    if (mySurface != null) {
      mySurface.getSelectionModel().removeListener(this);
    }
    mySurface = designSurface;
    if (mySurface != null) {
      mySurface.getSelectionModel().addListener(this);
      mySurface.getActionManager().registerActionsShortcuts(this);
      mySurface.getActionManager().registerActionsShortcuts(this);
    }
    setModel(designSurface != null ? designSurface.getModel() : null);
    myBadgeHandler.setIssuePanel(designSurface != null ? designSurface.getIssuePanel() : null);
  }

  @Nullable
  public Scene getScene() {
    return mySurface != null ? mySurface.getScene() : null;
  }

  private void setModel(@Nullable NlModel model) {
    if (myModel != null) {
      myModel.removeListener(this);
    }
    myModel = model;
    myBadgeHandler.setNlModel(myModel);
    if (myModel != null) {
      myModel.addListener(this);
    }

    updateHierarchy();
  }

  @Nullable
  public NlModel getDesignerModel() {
    return myModel;
  }

  @Override
  public void dispose() {
    if (mySurface != null) {
      mySurface.getSelectionModel().removeListener(this);
    }
    if (myModel != null) {
      myModel.removeListener(this);
      myModel = null;
    }
    Disposer.dispose(myUpdateQueue);
  }

  private ColoredTreeCellRenderer createCellRenderer() {
    return new ColoredTreeCellRenderer() {
      @Override
      public void customizeCellRenderer(@NotNull JTree tree,
                                        Object value,
                                        boolean selected,
                                        boolean expanded,
                                        boolean leaf,
                                        int row,
                                        boolean hasFocus) {
        setBorder(BorderFactory.createEmptyBorder(1, 1, 1, 10));

        if (value instanceof NlComponent) {
          StructureTreeDecorator.decorate(this, (NlComponent)value, tree.hasFocus() && selected);
        }
        else if (value instanceof String) {
          StructureTreeDecorator.decorate(this, (String)value);
        }
      }
    };
  }

  private void invalidateUI() {
    IJSwingUtilities.updateComponentTreeUI(this);
  }

  // ---- Methods for updating hierarchy while attempting to keep expanded nodes expanded ----

  private void updateHierarchy() {
    clearInsertionPoint();
    ApplicationManager.getApplication().assertIsDispatchThread();
    myUpdateQueue.queue(new Update("updateComponentStructure") {
      @Override
      public void run() {
        try {
          if (myModel == null) {
            return;
          }
          mySelectionIsUpdating.set(true);

          Collection<NlComponent> components = getCollapsedComponents();
          setModel(new NlComponentTreeModel(myModel));
          collapseComponents(components);

          invalidateUI();
        }
        finally {
          mySelectionIsUpdating.set(false);
        }

        updateSelection();
      }
    });
    if (mySkipWait) {
      mySkipWait = false;
      myUpdateQueue.flush();
    }
  }

  @NotNull
  private Collection<NlComponent> getCollapsedComponents() {
    int rowCount = getRowCount();
    Collection<NlComponent> components = Sets.newHashSetWithExpectedSize(rowCount);

    for (int row = 0; row < rowCount; row++) {
      if (isCollapsed(row)) {
        Object last = getPathForRow(row).getLastPathComponent();
        if (!(last instanceof NlComponent)) {
          continue;
        }
        NlComponent component = (NlComponent)last;

        if (component.getChildCount() != 0) {
          components.add(component);
        }
      }
    }

    return components;
  }

  private void collapseComponents(@NotNull Collection<NlComponent> components) {
    NlComponent root = (NlComponent)getModel().getRoot();

    if (root == null) {
      return;
    }

    expandAll(root);
    components.forEach(component -> collapsePath(newTreePath(component)));
  }

  private void expandAll(@NotNull NlComponent parent) {
    // If all the children are leaves
    if (parent.getChildren().stream().allMatch(child -> child.getChildCount() == 0)) {
      expandPath(newTreePath(parent));
    }
    else {
      // Recurse
      parent.getChildren().forEach(this::expandAll);
    }
  }

  /**
   * Normally the outline pauses for a certain delay after a model change before updating itself
   * to reflect the new hierarchy. This method can be called to skip (just) the next update delay.
   * This is used to make operations performed <b>in</b> the outline feel more immediate.
   */
  void skipNextUpdateDelay() {
    mySkipWait = true;
  }

  private void updateSelection() {
    if (!mySelectionIsUpdating.compareAndSet(false, true)) {
      return;
    }
    try {
      clearSelection();
      if (mySurface != null) {
        for (NlComponent component : mySurface.getSelectionModel().getSelection()) {
          addSelectionPath(newTreePath(component));
        }
      }
    }
    finally {
      mySelectionIsUpdating.set(false);
    }
  }

  @NotNull
  private static TreePath newTreePath(@NotNull NlComponent component) {
    List<NlComponent> components = new ArrayList<>();
    components.add(component);

    for (NlComponent parent = component.getParent(); parent != null; parent = parent.getParent()) {
      components.add(parent);
    }

    Collections.reverse(components);
    return new TreePath(components.toArray());
  }

  @Override
  public void paintComponent(Graphics g) {
    super.paintComponent(g);
    if (myInsertAfterRow >= 0) {
      paintInsertionPoint((Graphics2D)g);
    }
    myBadgeHandler.paintBadges((Graphics2D)g, this);
  }

  private void paintInsertionPoint(@NotNull Graphics2D g2D) {
    if (myInsertionReceiverBounds == null || myInsertionRowBounds == null) {
      return;
    }
    RenderingHints savedHints = g2D.getRenderingHints();
    Color savedColor = g2D.getColor();
    try {
      g2D.setColor(LINE_COLOR);
      g2D.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

      paintInsertionRectangle(g2D,
                              getX(), myInsertionReceiverBounds.y,
                              getWidth(), myInsertionReceiverBounds.height);
      paintColumnLine(g2D,
                      myInsertionReceiverBounds.x, myInsertionReceiverBounds.y + myInsertionReceiverBounds.height,
                      myInsertionRowBounds.y + myInsertionRowBounds.height);
      paintInsertionLine(g2D,
                         myInsertionReceiverBounds.x, myInsertionRowBounds.y + myInsertionRowBounds.height,
                         getWidth());
    }
    finally {
      g2D.setRenderingHints(savedHints);
      g2D.setColor(savedColor);
    }
  }

  private static void paintInsertionLine(@NotNull Graphics2D g, int x, int y, int width) {
    Polygon triangle = new Polygon();
    int indicatorSize = JBUI.scale(6);
    triangle.addPoint(x + indicatorSize, y);
    triangle.addPoint(x, y + indicatorSize / 2);
    triangle.addPoint(x, y - indicatorSize / 2);
    Stroke stroke = g.getStroke();
    g.drawLine(x, y, x + width, y);
    g.setStroke(stroke);
    g.drawPolygon(triangle);
    g.fillPolygon(triangle);
  }

  private static void paintColumnLine(@NotNull Graphics2D g, int x, int y1, int y2) {
    int columnMargin = JBUI.scale(13);
    x -= columnMargin;
    Stroke stroke = g.getStroke();
    g.setStroke(NlConstants.DASHED_STROKE);
    g.drawLine(x, y1, x, y2);
    g.drawLine(x, y2, x + columnMargin, y2);
    g.setStroke(stroke);
  }

  private static void paintInsertionRectangle(@NotNull Graphics2D g, int x, int y, int width, int height) {
    x += JBUI.scale(1);
    y += JBUI.scale(1);
    width -= JBUI.scale(3);
    height -= JBUI.scale(4);
    g.drawRect(x, y, width, height);
  }

  /**
   * @param row           The row after which the insertion line will be displayed
   * @param relativeDepth The depth of the parent relative the row
   * @see NlDropInsertionPicker#findInsertionPointAt(Point, List)
   */
  public void markInsertionPoint(int row, int relativeDepth) {
    if (row == myInsertAfterRow && relativeDepth == myRelativeDepthToInsertionRow) {
      return;
    }

    if (row < 0) {
      clearInsertionPoint();
      return;
    }

    myInsertAfterRow = row;
    myRelativeDepthToInsertionRow = relativeDepth;
    myInsertionRowBounds = getRowBounds(myInsertAfterRow);

    // Find the bounds of the parent if the insertion row is not the receiver row
    myInsertionReceiverBounds = myInsertionRowBounds;
    if (myRelativeDepthToInsertionRow < 1) {
      TreePath receiverPath = getPathForRow(myInsertAfterRow);
      for (int i = myRelativeDepthToInsertionRow; i < 1 && receiverPath != null; i++) {
        receiverPath = receiverPath.getParentPath();
      }
      if (receiverPath != null) {
        myInsertionReceiverBounds = getPathBounds(receiverPath);
      }
    }
    repaint();
  }

  public void clearInsertionPoint() {
    myInsertionReceiverBounds = null;
    myInsertionRowBounds = null;
    myInsertAfterRow = -1;
    myRelativeDepthToInsertionRow = 0;
    repaint();
  }

  @Override
  @SuppressWarnings("EmptyMethod")
  protected void clearToggledPaths() {
    super.clearToggledPaths();
  }

  @NotNull
  public List<NlComponent> getSelectedComponents() {
    List<NlComponent> selected = new ArrayList<>();
    TreePath[] paths = getSelectionPaths();
    if (paths != null) {
      for (TreePath path : paths) {
        Object last = path.getLastPathComponent();
        if (last instanceof NlComponent) {
          selected.add((NlComponent)last);
        }
      }
    }
    return selected;
  }

  // ---- Implemented SelectionListener ----
  @Override
  public void selectionChanged(@NotNull SelectionModel model, @NotNull List<NlComponent> selection) {
    UIUtil.invokeLaterIfNeeded(this::updateSelection);
  }

  // ---- Implemented ModelListener ----
  @Override
  public void modelDerivedDataChanged(@NotNull NlModel model) {
    UIUtil.invokeLaterIfNeeded(this::updateHierarchy);
  }

  @Override
  public void modelChangedOnLayout(@NotNull NlModel model, boolean animate) {
    // Do nothing
  }

  // ---- Implemented DesignSurfaceListener ----

  @Override
  public void modelChanged(@NotNull DesignSurface surface, @Nullable NlModel model) {
    setModel(model);
  }

  @Override
  public boolean activatePreferredEditor(@NotNull DesignSurface surface, @NotNull NlComponent component) {
    return false;
  }

  private class StructurePaneMouseListener extends MouseAdapter {
    @Override
    public void mouseClicked(MouseEvent e) {
      if (e.getClickCount() == 2) {
        handleDoubleClick(e);
      }
      else {
        handlePopup(e);
      }
    }

    @Override
    public void mousePressed(MouseEvent e) {
      handlePopup(e);
    }

    @Override
    public void mouseReleased(MouseEvent e) {
      handlePopup(e);
    }

    private void handlePopup(MouseEvent e) {
      if (e.isPopupTrigger()) {
        TreePath path = getPathForLocation(e.getX(), e.getY());
        if (path != null && mySurface != null) {
          Object component = path.getLastPathComponent();

          if (component instanceof NlComponent) {
            // TODO: Ensure the node is selected first
            mySurface.getActionManager().showPopup(e, (NlComponent)component);
          }
        }
      }
    }

    private void handleDoubleClick(@NotNull MouseEvent event) {
      int x = event.getX();
      int y = event.getY();
      TreePath path = getPathForLocation(x, y);

      if (path == null || mySurface == null) {
        return;
      }

      Object component = path.getLastPathComponent();

      if (!(component instanceof NlComponent)) {
        return;
      }

      ViewHandler handler = NlComponentHelperKt.getViewHandler((NlComponent)component);
      if (handler != null) {
        handler.onActivateInComponentTree((NlComponent)component);
      }
    }
  }

  private class StructurePaneSelectionListener implements TreeSelectionListener {
    @Override
    public void valueChanged(TreeSelectionEvent treeSelectionEvent) {
      if (!mySelectionIsUpdating.compareAndSet(false, true)) {
        return;
      }
      try {
        if (mySurface != null) {
          mySurface.getSelectionModel().setSelection(getSelectedComponents());
        }
      }
      finally {
        mySelectionIsUpdating.set(false);
      }
    }
  }

  private static final class StructureSpeedSearch extends TreeSpeedSearch {
    StructureSpeedSearch(@NotNull NlComponentTree tree) {
      super(tree);
    }

    @Override
    protected boolean isMatchingElement(Object element, String pattern) {
      if (pattern == null) {
        return false;
      }

      Object component = ((TreePath)element).getLastPathComponent();
      return compare(component instanceof NlComponent ? StructureTreeDecorator.toString((NlComponent)component) : "", pattern);
    }
  }

  // ---- Implements DataProvider ----
  @Override
  public Object getData(@NonNls String dataId) {
    return mySurface == null ? null : mySurface.getData(dataId);
  }

  /**
   * Handle a selection of non-NlComponent (like barrier/group)
   *
   * @param selectedPath
   */
  private void deleteNonNlComponent(TreePath[] selectedPath) {
    TreePath parent = NlTreeUtil.getUniqueParent(selectedPath);
    if (parent != null) {
      Object component = parent.getLastPathComponent();
      if (component instanceof NlComponent) {
        NlTreeUtil.delegateEvent(DelegatedTreeEvent.Type.DELETE, this, ((NlComponent)component), -1);
      }
    }
  }
}
