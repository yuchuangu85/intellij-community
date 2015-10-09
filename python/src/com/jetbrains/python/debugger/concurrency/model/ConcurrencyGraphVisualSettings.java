/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.python.debugger.concurrency.model;

import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

public class ConcurrencyGraphVisualSettings {
  private int myMcsPerCell = 10000;
  private int myHorizontalValue;
  private int myHorizontalExtent;
  private int myHorizontalMax;
  private int myVerticalValue;
  private int myVerticalExtent;
  private int myVerticalMax;
  private final ConcurrencyGraphPresentationModel myPresentationModel;
  public static final int NAMES_PANEL_INITIAL_WIDTH = 200;
  private static final int DIVIDER_WIDTH = 3;

  public ConcurrencyGraphVisualSettings(@NotNull ConcurrencyGraphPresentationModel presentationModel) {
    myPresentationModel = presentationModel;
  }

  public void zoomIn() {
    int prevMcsPerCell = myMcsPerCell;
    myMcsPerCell = Math.max(100, (int)(Math.round(myMcsPerCell * 0.9) - Math.round(myMcsPerCell * 0.9) % 100));
    if (myMcsPerCell != prevMcsPerCell) {
      myPresentationModel.updateTimerPeriod();
      myPresentationModel.getGraphModel().setTimeCursor(
        myPresentationModel.getGraphModel().getTimeCursor() * prevMcsPerCell / myMcsPerCell);
      myPresentationModel.updateGraphModel();
    }
  }

  public void zoomOut() {
    int prevMicrosecsPerCell = myMcsPerCell;
    myMcsPerCell = Math.max((int)(Math.round(myMcsPerCell * 1.1) - Math.round(myMcsPerCell * 1.1) % 100),
                            myMcsPerCell + 100);
    if (myMcsPerCell != prevMicrosecsPerCell) {

      myPresentationModel.updateTimerPeriod();
      myPresentationModel.getGraphModel().setTimeCursor(
        myPresentationModel.getGraphModel().getTimeCursor() * prevMicrosecsPerCell / myMcsPerCell);
      myPresentationModel.updateGraphModel();
    }
  }

  public void scrollToTheEnd() {
    if (myPresentationModel.getToolWindowPanel().getTableScrollPane() != null) {
      UIUtil.invokeLaterIfNeeded(new Runnable() {
        @Override
        public void run() {
          myPresentationModel.getToolWindowPanel().getTableScrollPane().getHorizontalScrollBar().
            setValue(myHorizontalMax - myHorizontalExtent);
        }
      });
    }
  }

  public int getMcsPerCell() {
    return myMcsPerCell;
  }

  public int getCellsPerRulerUnit() {
    return 10;
  }

  public int getDividerWidth() {
    return DIVIDER_WIDTH;
  }

  public int getVerticalValue() {
    return myVerticalValue;
  }

  public int getVerticalExtent() {
    return myVerticalExtent;
  }

  public int getVerticalMax() {
    return myVerticalMax;
  }

  public int getHorizontalValue() {
    return myHorizontalValue;
  }

  public int getHorizontalExtent() {
    return myHorizontalExtent;
  }

  public int getHorizontalMax() {
    return myHorizontalMax;
  }

  public void updateHorizontalScrollbar(int scrollbarValue, int scrollbarExtent, int scrollMax) {
    myHorizontalValue = scrollbarValue;
    myHorizontalExtent = scrollbarExtent;
    myHorizontalMax = scrollMax;
    myPresentationModel.updateGraphModel();
  }

  public void updateVerticalScrollbar(int scrollbarValue, int scrollbarExtent, int scrollMax) {
    myVerticalValue = scrollbarValue;
    myVerticalExtent = scrollbarExtent;
    myVerticalMax = scrollMax;
  }
}
