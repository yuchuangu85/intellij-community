
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
package com.jetbrains.python.debugger.concurrency.tool.asyncio;


import com.intellij.ui.ColoredTableCellRenderer;
import com.jetbrains.python.debugger.concurrency.model.ConcurrencyGraphElement;
import com.jetbrains.python.debugger.concurrency.model.ConcurrencyGraphModel;
import com.jetbrains.python.debugger.concurrency.model.ConcurrencyThreadState;
import com.jetbrains.python.debugger.concurrency.tool.ConcurrencyGraphSettings;
import com.jetbrains.python.debugger.concurrency.tool.ConcurrencyRenderingUtil;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;

public class GraphCellRenderer extends ColoredTableCellRenderer {
  private final ConcurrencyGraphModel myGraphModel;
  private int myRow;

  public GraphCellRenderer(ConcurrencyGraphModel graphModel) {
    myGraphModel = graphModel;
  }

  @Override
  protected void customizeCellRenderer(JTable table, Object value, boolean selected, boolean hasFocus, int row, int column) {
    myRow = row;
    setBorder(null);
  }

  private void drawRelation(Graphics g, int parent, int child) {
    Graphics2D g2 = (Graphics2D)g;
    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    int x_start = Math.round((parent + 0.5f) * ConcurrencyGraphSettings.CELL_WIDTH);
    int x_finish = Math.round((child + 0.5f) * ConcurrencyGraphSettings.CELL_WIDTH);
    int y = Math.round(ConcurrencyGraphSettings.CELL_HEIGHT * 0.5f);
    g2.drawLine(x_start, y, x_finish, y);
  }

  @Override
  protected void paintComponent(Graphics g) {
    super.paintComponent(g);
    ArrayList<ConcurrencyGraphElement> rowElements = myGraphModel.getDrawElementsForRow(myRow);
    int i = 0;
    //for (DrawElement element: rowElements) {
    //  element.paintInTable(g, i);
    //  ++i;
    //}
    int[] relation = myGraphModel.getRelationForRow(myRow);
    if (relation[0] != relation[1]) {
      ConcurrencyGraphElement element = rowElements.get(relation[1]);
      ConcurrencyThreadState state = element.getAfter() == ConcurrencyThreadState.Stopped ? element.getBefore() : element.getAfter();
      ConcurrencyRenderingUtil.prepareStroke(g, state);
      drawRelation(g, relation[0], relation[1]);
    }
  }
}
