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

import com.jetbrains.python.debugger.concurrency.tool.ConcurrencyGraphSettings;

public class ConcurrencyGraphVisualSettings {
  private int myMicrosecsPerCell = 1000;
  private int myHorizontalValue;
  private int myHorizontalExtent;
  private int myHorizontalMax;
  private int myVerticalValue;
  private int myVerticalExtent;
  private int myVerticalMax;
  private int myNamesPanelWidth = NAMES_PANEL_INITIAL_WIDTH;
  private int myCellsPerRulerUnit = 10;
  private final ConcurrencyGraphPresentationModel myGraphModel;
  public static int NAMES_PANEL_INITIAL_WIDTH = 200;
  public static int DIVIDER_WIDTH = 5;

  public ConcurrencyGraphVisualSettings(ConcurrencyGraphPresentationModel graphModel) {
    myGraphModel = graphModel;
  }

  public void increaseScale() {
    myMicrosecsPerCell = Math.max(100, (int) (Math.round(myMicrosecsPerCell * 0.9) - Math.round(myMicrosecsPerCell * 0.9) % 100));
    myGraphModel.updateGraphModel();
  }

  public void decreaseScale() {
    myMicrosecsPerCell = Math.max((int)(Math.round(myMicrosecsPerCell * 1.1) - Math.round(myMicrosecsPerCell * 1.1) % 100),
                                  myMicrosecsPerCell + 100);
    myGraphModel.updateGraphModel();
  }

  public int getMicrosecsPerCell() {
    return myMicrosecsPerCell;
  }

  public int getCellsPerRulerUnit() {
    return myCellsPerRulerUnit;
  }

  public int getNamesPanelWidth() {
    return myNamesPanelWidth;
  }

  public void setNamesPanelWidth(int namesPanelWidth) {
    myNamesPanelWidth = namesPanelWidth;
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

  public int getHeightForPanes(int linesNumber) {
    return Math.max((ConcurrencyGraphSettings.CELL_HEIGHT + ConcurrencyGraphSettings.INTERVAL) * linesNumber +
                    2 * ConcurrencyGraphSettings.INTERVAL,
                    getVerticalValue() + getVerticalExtent());
  }


  public void updateHorizontalScrollbar(int scrollbarValue, int scrollbarExtent, int scrollMax) {
    myHorizontalValue = scrollbarValue;
    myHorizontalExtent = scrollbarExtent;
    myHorizontalMax = scrollMax;
    myGraphModel.updateGraphModel();
  }

  public void updateVerticalScrollbar(int scrollbarValue, int scrollbarExtent, int scrollMax) {
    myVerticalValue = scrollbarValue;
    myVerticalExtent = scrollbarExtent;
    myVerticalMax = scrollMax;
    myGraphModel.updateGraphModel();
  }
}
