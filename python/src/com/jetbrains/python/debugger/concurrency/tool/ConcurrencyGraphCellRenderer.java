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
package com.jetbrains.python.debugger.concurrency.tool;


import com.intellij.ui.ColoredTableCellRenderer;
import com.intellij.util.containers.hash.HashMap;
import com.intellij.util.ui.UIUtil;
import com.jetbrains.python.debugger.concurrency.model.ConcurrencyGraphBlock;
import com.jetbrains.python.debugger.concurrency.model.ConcurrencyGraphPresentationModel;
import com.jetbrains.python.debugger.concurrency.tool.panels.ConcurrencyToolWindowPanel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;

public class ConcurrencyGraphCellRenderer extends ColoredTableCellRenderer {
  private final @NotNull ConcurrencyGraphPresentationModel myPresentationModel;
  private int myRow;
  private int myPadding;
  private final @NotNull JTable myTable;
  private final @NotNull ConcurrencyToolWindowPanel myPanel;
  private int mySelected = -1;
  private ConcurrencyGraphBlock[] myGraph;
  private HashMap<Integer, Image> myImages;

  public ConcurrencyGraphCellRenderer(@NotNull ConcurrencyGraphPresentationModel presentationModel,
                                      @NotNull JTable table,
                                      @NotNull ConcurrencyToolWindowPanel panel) {
    myPresentationModel = presentationModel;
    myTable = table;
    myPanel = panel;
    myImages = new HashMap<Integer, Image>();

    myPresentationModel.registerListener(new ConcurrencyGraphPresentationModel.PresentationListener() {
      @Override
      public void graphChanged(int leftPadding) {
        myPadding = leftPadding;
        myTable.repaint();
      }
    });
  }

  public int getPadding() {
    return myPadding;
  }

  @Override
  protected void paintComponent(@NotNull Graphics g) {
    super.paintComponent(g);
    if ((myGraph == null) || (myGraph != myPresentationModel.getVisibleGraph())) {
      myGraph = myPresentationModel.getVisibleGraph();
      myImages = new HashMap<Integer, Image>();
    }
    if (!myImages.containsKey(myRow)) {
      Image image = UIUtil.createImage(myPresentationModel.getVisualSettings().getHorizontalExtent(),
                                       ConcurrencyGraphSettings.TABLE_ROW_HEIGHT, BufferedImage.TYPE_INT_RGB);
      ConcurrencyRenderingUtil.paintRow(image, 0, myGraph, myRow, mySelected == myRow);
      myImages.put(myRow, image);
    }
    Image imageForDrawing = myImages.get(myRow);
    if (imageForDrawing != null) {
      UIUtil.drawImage(g, imageForDrawing, myPadding, 0, null);
    }
  }

  @Override
  protected void customizeCellRenderer(JTable table, @Nullable Object value, boolean selected, boolean hasFocus, int row, int column) {
    myRow = row;
    if (selected) {
      if (mySelected != row) {
        myImages.remove(mySelected);
        myImages.remove(row);
        mySelected = row;
      }
    }
    int width = Math.max(myPresentationModel.getCellsNumber() * ConcurrencyGraphSettings.CELL_WIDTH, myPanel.getGraphPaneWidth());
    int height = Math.max(myPresentationModel.getGraphModel().getMaxThread() * ConcurrencyGraphSettings.TABLE_ROW_HEIGHT,
                          table.getHeight() - 1);
    table.setSize(new Dimension(width, height));
    table.setPreferredSize(new Dimension(width, height));
    myPadding = myPresentationModel.getPadding();
  }
}
