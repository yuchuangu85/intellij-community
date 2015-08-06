
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
package com.jetbrains.python.debugger.concurrency.tool.graph;


import com.intellij.ui.ColoredTableCellRenderer;
import com.jetbrains.python.debugger.concurrency.tool.GraphSettings;
import com.jetbrains.python.debugger.concurrency.tool.graph.elements.DrawElement;
import com.jetbrains.python.debugger.concurrency.tool.graph.states.StoppedThreadState;
import com.jetbrains.python.debugger.concurrency.tool.graph.states.ThreadState;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;

public class GraphCellRenderer extends ColoredTableCellRenderer {
  private final GraphManager myGraphManager;
  private int myRow;

  public GraphCellRenderer(GraphManager graphManager) {
    myGraphManager = graphManager;
  }

  @Override
  protected void customizeCellRenderer(JTable table, Object value, boolean selected, boolean hasFocus, int row, int column) {
    myRow = row;
    setBorder(null);
  }

  private void drawRelation(Graphics g, int parent, int child) {
    Graphics2D g2 = (Graphics2D)g;
    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    int x_start = Math.round((parent + 0.5f) * GraphSettings.NODE_WIDTH);
    int x_finish = Math.round((child + 0.5f) * GraphSettings.NODE_WIDTH);
    int y = Math.round(GraphSettings.CELL_HEIGH * 0.5f);
    g2.drawLine(x_start, y, x_finish, y);
  }

  @Override
  protected void paintComponent(Graphics g) {
    super.paintComponent(g);
    ArrayList<DrawElement> rowElements = myGraphManager.getDrawElementsForRow(myRow);
    int i = 0;
    for (DrawElement element: rowElements) {
      element.drawElement(g, i);
      ++i;
    }
    int[] relation = myGraphManager.getRelationForRow(myRow);
    if (relation[0] != relation[1]) {
      DrawElement element = rowElements.get(relation[1]);
      ThreadState state = element.getAfter() instanceof StoppedThreadState ? element.getBefore() : element.getAfter();
      state.prepareStroke(g);
      drawRelation(g, relation[0], relation[1]);
    }
  }
}
