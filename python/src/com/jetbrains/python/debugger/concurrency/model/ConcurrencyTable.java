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

import com.intellij.ui.table.JBTable;
import com.jetbrains.python.debugger.PyConcurrencyEvent;
import com.jetbrains.python.debugger.concurrency.tool.ConcurrencyGraphCellRenderer;
import com.jetbrains.python.debugger.concurrency.tool.ConcurrencyGraphSettings;
import com.jetbrains.python.debugger.concurrency.tool.ConcurrencyRenderingUtil;
import com.jetbrains.python.debugger.concurrency.tool.ConcurrencyTableUtil;
import com.jetbrains.python.debugger.concurrency.tool.panels.ConcurrencyToolWindowPanel;
import org.jetbrains.annotations.NotNull;

import javax.swing.table.TableModel;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;

public class ConcurrencyTable extends JBTable {
  protected ConcurrencyGraphPresentationModel myPresentationModel;
  private ConcurrencyGraphCellRenderer myRenderer;
  private ConcurrencyToolWindowPanel myPanel;

  public ConcurrencyTable(ConcurrencyGraphPresentationModel model, TableModel tableModel, ConcurrencyToolWindowPanel panel) {
    super();
    myPresentationModel = model;
    myPanel = panel;
    setModel(tableModel);
    myRenderer = new ConcurrencyGraphCellRenderer(myPresentationModel, this, myPanel);
    setDefaultRenderer(ConcurrencyTableUtil.GraphCell.class, myRenderer);

    addMouseListener(new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent e) {
        Point cursorPoint = e.getPoint();
        myPresentationModel.graphModel.setTimeCursor(cursorPoint.x);
        int row = rowAtPoint(cursorPoint) >= 0 ? rowAtPoint(cursorPoint) : getSelectedRow();
        if (row >= 0) {
          int eventIndex = getEventIndex(cursorPoint, row);
          PyConcurrencyEvent event = eventIndex != -1 ? myPresentationModel.graphModel.getEventAt(eventIndex) : null;
          myPanel.showStackTrace(event);
        }
      }
    });
  }

  private int getEventIndex(Point clickPoint, int row) {
    int index = ConcurrencyRenderingUtil.getElementIndex(myRenderer.getPadding(), myPresentationModel.getVisibleGraph(), clickPoint.x);
    if (index != -1) {
      ArrayList<ConcurrencyGraphElement> elements = myPresentationModel.getVisibleGraph().get(index).elements;
      if (row < elements.size()) {
        return elements.get(row).eventIndex;
      }
    }
    return -1;
  }

  private void drawTimeCursor(@NotNull Graphics g) {
    int cursorPosition = Math.max(0, myPresentationModel.getCellsNumber() * ConcurrencyGraphSettings.CELL_WIDTH - 2);
    if (myPresentationModel.graphModel.getTimeCursor() > 0) {
      cursorPosition = myPresentationModel.graphModel.getTimeCursor();
    }
    Graphics2D g2 = (Graphics2D)g;
    g2.setStroke(new BasicStroke(ConcurrencyGraphSettings.TIME_CURSOR_WIDTH));
    g2.setColor(ConcurrencyGraphSettings.TIME_CURSOR_COLOR);
    g2.drawLine(cursorPosition, 0, cursorPosition, myPresentationModel.visualSettings.getVerticalMax());
    repaint();
  }

  private void paintRuler(@NotNull Graphics g) {
    Graphics2D g2 = (Graphics2D)g;
    g2.setStroke(new BasicStroke(ConcurrencyGraphSettings.RULER_STROKE_WIDTH));
    g2.setColor(ConcurrencyGraphSettings.RULER_COLOR);
    ConcurrencyGraphVisualSettings settings = myPresentationModel.visualSettings;
    int y = settings.getVerticalValue() + settings.getVerticalExtent() - ConcurrencyGraphSettings.RULER_STROKE_WIDTH;
    g.drawLine(0, y, getWidth(), y);
    FontMetrics metrics = g.getFontMetrics(getFont());
    int rulerUnitWidth = ConcurrencyGraphSettings.CELL_WIDTH * myPresentationModel.visualSettings.getCellsPerRulerUnit();

    for (int i = 0; i < getWidth() / rulerUnitWidth; ++i) {
      int markY = i % ConcurrencyGraphSettings.RULER_SUBUNITS_PER_UNIT == 0 ? y - ConcurrencyGraphSettings.RULER_UNIT_MARK :
                  y - ConcurrencyGraphSettings.RULER_SUBUNIT_MARK;
      g.drawLine(i * rulerUnitWidth, markY, i * rulerUnitWidth, y);
      if ((i != 0) && (i % ConcurrencyGraphSettings.RULER_SUBUNITS_PER_UNIT == 0)) {
        int ms = myPresentationModel.visualSettings.getMicrosecsPerCell() *
                 myPresentationModel.visualSettings.getCellsPerRulerUnit() * i / 1000;
        String text = String.format("%.2f s", ms / 1000f);
        int textWidth = metrics.stringWidth(text);
        int textHeight = metrics.getHeight();
        g.drawString(text, i * rulerUnitWidth - textWidth / 2, markY - textHeight / 3);
      }
    }
  }

  @Override
  protected void paintComponent(@NotNull Graphics g) {
    super.paintComponent(g);
    paintRuler(g);
    drawTimeCursor(g);
  }
}
