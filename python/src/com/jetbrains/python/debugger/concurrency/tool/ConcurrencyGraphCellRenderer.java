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
import com.jetbrains.python.debugger.concurrency.model.ConcurrencyGraphPresentationModel;
import com.jetbrains.python.debugger.concurrency.tool.panels.ConcurrencyToolWindowPanel;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

public class ConcurrencyGraphCellRenderer extends ColoredTableCellRenderer {
  private ConcurrencyGraphPresentationModel myPresentationModel;
  private int myRow;
  private int myPadding;
  private JTable myTable;
  private ConcurrencyToolWindowPanel myPanel;

  public ConcurrencyGraphCellRenderer(ConcurrencyGraphPresentationModel presentationModel,
                                      final JTable table,
                                      ConcurrencyToolWindowPanel panel) {
    myPresentationModel = presentationModel;
    myTable = table;
    myPanel = panel;

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
  protected void paintComponent(Graphics g) {
    super.paintComponent(g);
    ConcurrencyRenderingUtil.paintRow(g, myPadding, myPresentationModel.getVisibleGraph(), myRow);
  }

  @Override
  protected void customizeCellRenderer(JTable table, @Nullable Object value, boolean selected, boolean hasFocus, int row, int column) {
    myRow = row;
    int width = Math.max(myPresentationModel.getCellsNumber() * ConcurrencyGraphSettings.CELL_WIDTH, myPanel.getGraphPaneWidth());
    table.setSize(new Dimension(width, table.getHeight() - 1));
    table.setPreferredSize(new Dimension(width, table.getHeight() - 1));
    myPadding = myPresentationModel.getPadding();
  }
}
