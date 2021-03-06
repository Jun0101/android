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
package com.android.tools.idea.naveditor.scene.targets;

import com.android.tools.adtui.common.SwingCoordinate;
import com.android.tools.idea.common.model.Coordinates;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.common.scene.SceneComponent;
import com.android.tools.idea.common.scene.SceneContext;
import com.android.tools.idea.common.scene.ScenePicker;
import com.android.tools.idea.common.scene.draw.ArrowDirection;
import com.android.tools.idea.common.scene.draw.DisplayList;
import com.android.tools.idea.common.scene.draw.DrawArrow;
import com.android.tools.idea.common.scene.target.BaseTarget;
import com.android.tools.idea.common.scene.target.Target;
import com.android.tools.idea.naveditor.model.ActionType;
import com.android.tools.idea.naveditor.model.NavComponentHelperKt;
import com.android.tools.idea.naveditor.model.NavCoordinate;
import com.android.tools.idea.naveditor.scene.NavColorSet;
import com.android.tools.idea.naveditor.scene.NavDrawHelperKt;
import com.android.tools.idea.naveditor.scene.NavSceneManager;
import com.android.tools.idea.naveditor.scene.draw.DrawAction;
import com.google.common.collect.ImmutableList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.List;

import static com.android.tools.idea.naveditor.scene.draw.DrawAction.DrawMode.*;
import static com.android.tools.idea.naveditor.scene.targets.ActionTarget.ConnectionDirection.*;

/**
 * An Action in the navigation editor
 */
public class ActionTarget extends BaseTarget {
  @SwingCoordinate private Rectangle mySourceRect;
  @SwingCoordinate private Rectangle myDestRect;
  private final NlComponent myNlComponent;
  private final SceneComponent myDestination;
  private boolean myHighlighted = false;

  @NavCoordinate private static final int SELF_ACTION_LENGTH_1 = 28;
  @NavCoordinate private static final int SELF_ACTION_LENGTH_2 = 26;
  @NavCoordinate private static final int SELF_ACTION_LENGTH_3 = 60;
  @NavCoordinate private static final int SELF_ACTION_LENGTH_4 = 8;

  @NavCoordinate private static final int ACTION_HORIZONTAL_PADDING = 8;
  @NavCoordinate private static final int ACTION_VERTICAL_PADDING = 8;

  public static class CurvePoints {
    @SwingCoordinate public Point p1;
    @SwingCoordinate public Point p2;
    @SwingCoordinate public Point p3;
    @SwingCoordinate public Point p4;
    public ConnectionDirection dir;
  }

  public static class SelfActionPoints {
    @SwingCoordinate public final int[] x = new int[5];
    @SwingCoordinate public final int[] y = new int[5];
    public ConnectionDirection dir;
  }

  public enum ConnectionType {NORMAL, SELF, EXIT}

  public enum ConnectionDirection {
    LEFT(-1, 0), RIGHT(1, 0), TOP(0, -1), BOTTOM(0, 1);

    static {
      LEFT.myOpposite = RIGHT;
      RIGHT.myOpposite = LEFT;
      TOP.myOpposite = BOTTOM;
      BOTTOM.myOpposite = TOP;
    }

    private ConnectionDirection myOpposite;
    private final int myDeltaX;
    private final int myDeltaY;

    ConnectionDirection(int deltaX, int deltaY) {
      myDeltaX = deltaX;
      myDeltaY = deltaY;
    }

    public int getDeltaX() {
      return myDeltaX;
    }

    public int getDeltaY() {
      return myDeltaY;
    }

    public ConnectionDirection getOpposite() {
      return myOpposite;
    }
  }

  public ActionTarget(@NotNull SceneComponent component, @NotNull SceneComponent destination, @NotNull NlComponent actionComponent) {
    setComponent(component);
    myNlComponent = actionComponent;
    myDestination = destination;
  }

  public String getId() {
    return myNlComponent.getId();
  }

  @Override
  public boolean canChangeSelection() {
    return false;
  }

  public void setHighlighted(boolean highlighted) {
    myHighlighted = highlighted;
  }

  public boolean isHighlighted() {
    return myHighlighted;
  }

  @Override
  public void mouseRelease(int x, int y, @Nullable List<Target> closestTargets) {
    myComponent.getScene().getDesignSurface().getSelectionModel().setSelection(ImmutableList.of(myNlComponent));
  }

  @Override
  public int getPreferenceLevel() {
    return 0;
  }

  @Override
  public boolean layout(@NotNull SceneContext context, int l, int t, int r, int b) {
    // TODO
    return false;
  }

  @Override
  public void render(@NotNull DisplayList list, @NotNull SceneContext sceneContext) {
    Rectangle sourceRect = Coordinates.getSwingRect(sceneContext, getComponent().fillRect(null));

    String sourceId = getComponent().getId();
    if (sourceId == null) {
      return;
    }

    String targetId = NavComponentHelperKt.getEffectiveDestinationId(myNlComponent);
    if (targetId == null) {
      // TODO: error handling
      return;
    }

    myDestRect = Coordinates.getSwingRect(sceneContext, myDestination.fillRect(null));
    mySourceRect = sourceRect;

    ConnectionType connectionType = ConnectionType.NORMAL;
    if (sourceId.equals(targetId)) {
      connectionType = ConnectionType.SELF;
    }
    else if (NavComponentHelperKt.getActionType(myNlComponent) == ActionType.EXIT) {
      connectionType = ConnectionType.EXIT;
    }

    boolean selected = getComponent().getScene().getSelection().contains(myNlComponent);
    DrawAction.buildDisplayList(list, connectionType, sourceRect, myDestRect,
                                selected ? SELECTED : mIsOver || myHighlighted ? HOVER : NORMAL);

    @SwingCoordinate Rectangle rectangle = new Rectangle();
    ArrowDirection direction;

    if (sourceId.equals(targetId)) {
      rectangle.x = myDestRect.x +
                    myDestRect.width +
                    sceneContext
                      .getSwingDimension(SELF_ACTION_LENGTH_1 - SELF_ACTION_LENGTH_3 - NavSceneManager.ACTION_ARROW_PERPENDICULAR / 2);
      rectangle.y = getConnectionY(BOTTOM, myDestRect) + sceneContext.getSwingDimension(ACTION_VERTICAL_PADDING);
      rectangle.width = sceneContext.getSwingDimension(NavSceneManager.ACTION_ARROW_PERPENDICULAR);
      rectangle.height = sceneContext.getSwingDimension(NavSceneManager.ACTION_ARROW_PARALLEL);
      direction = ArrowDirection.UP;
    }
    else {
      rectangle.x = getConnectionX(LEFT, myDestRect) + getDestinationDx(LEFT, sceneContext);
      rectangle.y = getConnectionY(LEFT, myDestRect) - sceneContext.getSwingDimension(NavSceneManager.ACTION_ARROW_PERPENDICULAR) / 2;
      rectangle.width = sceneContext.getSwingDimension(NavSceneManager.ACTION_ARROW_PARALLEL);
      rectangle.height = sceneContext.getSwingDimension(NavSceneManager.ACTION_ARROW_PERPENDICULAR);
      direction = ArrowDirection.RIGHT;
    }

    NavColorSet colorSet = (NavColorSet)sceneContext.getColorSet();
    Color color = selected ? colorSet.getSelectedActions()
                           : mIsOver || myHighlighted ? colorSet.getHighlightedActions() : colorSet.getActions();
    list.add(new DrawArrow(NavDrawHelperKt.DRAW_ACTION_LEVEL, direction, rectangle, color));
  }

  @Override
  public void addHit(@NotNull SceneContext transform, @NotNull ScenePicker picker) {
    if (mySourceRect == null || myDestRect == null) {
      return;
    }

    String sourceId = getComponent().getId();
    if (sourceId == null) {
      return;
    }

    String targetId = NavComponentHelperKt.getEffectiveDestinationId(myNlComponent);
    if (targetId == null) {
      return;
    }

    if (sourceId.equals(targetId)) {
      @SwingCoordinate SelfActionPoints points = getSelfActionPoints(mySourceRect, transform);
      for (int i = 1; i < points.x.length; i++) {
        picker.addLine(this, 5, points.x[i - 1], points.y[i - 1], points.x[i], points.y[i]);
      }

      return;
    }

    CurvePoints points = getCurvePoints(mySourceRect, myDestRect, transform);
    picker.addCurveTo(this, 5, points.p1.x, points.p1.y, points.p2.x, points.p2.y, points.p3.x, points.p3.y, points.p4.x, points.p4.y);
  }

  @NotNull
  public static CurvePoints getCurvePoints(@SwingCoordinate @NotNull Rectangle source,
                                           @SwingCoordinate @NotNull Rectangle dest,
                                           SceneContext sceneContext) {
    ConnectionDirection sourceDirection = RIGHT;
    ConnectionDirection destDirection = LEFT;
    int startx = getConnectionX(sourceDirection, source);
    int starty = getConnectionY(sourceDirection, source);
    int endx = getConnectionX(destDirection, dest);
    int endy = getConnectionY(destDirection, dest);
    int dx = getDestinationDx(destDirection, sceneContext);
    int dy = getDestinationDy(destDirection, sceneContext);
    int scale_source = sceneContext.getSwingDimension(100);
    int scale_dest = sceneContext.getSwingDimension(100);
    CurvePoints result = new CurvePoints();
    result.dir = destDirection;
    result.p1 = new Point(startx, starty);
    result.p2 = new Point(startx + scale_source * sourceDirection.getDeltaX(), starty + scale_source * sourceDirection.getDeltaY());
    result.p3 = new Point(endx + dx + scale_dest * destDirection.getDeltaX(), endy + dy + scale_dest * destDirection.getDeltaY());
    result.p4 = new Point(endx + dx, endy + dy);
    return result;
  }

  @NotNull
  public static SelfActionPoints getSelfActionPoints(@SwingCoordinate @NotNull Rectangle rect, @NotNull SceneContext sceneContext) {
    ConnectionDirection sourceDirection = RIGHT;
    ConnectionDirection destDirection = BOTTOM;

    SelfActionPoints selfActionPoints = new SelfActionPoints();
    selfActionPoints.dir = destDirection;

    selfActionPoints.x[0] = getConnectionX(sourceDirection, rect);
    selfActionPoints.x[1] = selfActionPoints.x[0] + sceneContext.getSwingDimension(SELF_ACTION_LENGTH_1);
    selfActionPoints.x[2] = selfActionPoints.x[1];
    selfActionPoints.x[3] = selfActionPoints.x[2] - sceneContext.getSwingDimension(SELF_ACTION_LENGTH_3) - 1;
    selfActionPoints.x[4] = selfActionPoints.x[3];

    selfActionPoints.y[0] = getConnectionY(sourceDirection, rect);
    selfActionPoints.y[1] = selfActionPoints.y[0];
    selfActionPoints.y[2] = selfActionPoints.y[1] + rect.height / 2 + sceneContext.getSwingDimension(SELF_ACTION_LENGTH_2);
    selfActionPoints.y[3] = selfActionPoints.y[2];
    selfActionPoints.y[4] = selfActionPoints.y[3] - sceneContext.getSwingDimension(SELF_ACTION_LENGTH_4);

    return selfActionPoints;
  }

  private static int getConnectionX(@NotNull ConnectionDirection side, @NotNull Rectangle rect) {
    return rect.x + side.getDeltaX() + (1 + side.getDeltaX()) * rect.width / 2;
  }

  private static int getConnectionY(@NotNull ConnectionDirection side, @NotNull Rectangle rect) {
    return rect.y + side.getDeltaY() + (1 + side.getDeltaY()) * rect.height / 2;
  }

  public static int getDestinationDx(@NotNull ConnectionDirection side, SceneContext sceneContext) {
    return side.getDeltaX() * sceneContext.getSwingDimension(NavSceneManager.ACTION_ARROW_PARALLEL + ACTION_HORIZONTAL_PADDING);
  }

  public static int getDestinationDy(@NotNull ConnectionDirection side, SceneContext sceneContext) {
    return side.getDeltaY() * sceneContext.getSwingDimension(NavSceneManager.ACTION_ARROW_PARALLEL + ACTION_VERTICAL_PADDING);
  }
}

