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
  private final ConcurrencyGraphPresentationModel myPresentationModel;
  private final ConcurrencyGraphCellRenderer myRenderer;
  private final ConcurrencyToolWindowPanel myPanel;

  public ConcurrencyTable(ConcurrencyGraphPresentationModel presentationModel, TableModel tableModel, ConcurrencyToolWindowPanel panel) {
    super();
    myPresentationModel = presentationModel;
    myPanel = panel;
    setModel(tableModel);
    myRenderer = new ConcurrencyGraphCellRenderer(myPresentationModel, this, myPanel);
    setDefaultRenderer(ConcurrencyTableUtil.GraphCell.class, myRenderer);

    addMouseListener(new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent e) {
        Point cursorPoint = e.getPoint();
        myPresentationModel.getGraphModel().setTimeCursor(cursorPoint.x);
        int row = rowAtPoint(cursorPoint) >= 0 ? rowAtPoint(cursorPoint) : getSelectedRow();
        if (row >= 0) {
          int eventIndex = getEventIndex(cursorPoint, row);
          PyConcurrencyEvent event = eventIndex != -1 ? myPresentationModel.getGraphModel().getEventAt(eventIndex) : null;
          myPanel.showStackTrace(event);
        }
      }
    });
  }

  private int getEventIndex(Point clickPoint, int row) {
    int index = ConcurrencyRenderingUtil.getElementIndex(myRenderer.getPadding(), myPresentationModel.getVisibleGraph(), clickPoint.x);
    if (index != -1) {
      ArrayList<ConcurrencyGraphElement> elements = myPresentationModel.getVisibleGraph()[index].getElements();
      if (row < elements.size()) {
        return elements.get(row).getEventIndex();
      }
    }
    return -1;
  }

  private void drawTimeCursor(@NotNull Graphics g) {
    int cursorPosition = Math.max(0, myPresentationModel.getCellsNumber() * ConcurrencyGraphSettings.CELL_WIDTH);
    if (myPresentationModel.getGraphModel().getTimeCursor() > 0) {
      cursorPosition = myPresentationModel.getGraphModel().getTimeCursor();
    }
    Graphics2D g2 = (Graphics2D)g;
    g2.setStroke(new BasicStroke(ConcurrencyGraphSettings.TIME_CURSOR_WIDTH));
    g2.setColor(ConcurrencyGraphSettings.TIME_CURSOR_COLOR);
    g2.drawLine(cursorPosition, 0, cursorPosition, myPresentationModel.getVisualSettings().getVerticalMax());
    repaint();
  }

  private void paintRuler(@NotNull Graphics g) {
    Graphics2D g2 = (Graphics2D)g;
    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    g2.setStroke(new BasicStroke(ConcurrencyGraphSettings.RULER_STROKE_WIDTH));
    g2.setColor(ConcurrencyGraphSettings.RULER_COLOR);
    ConcurrencyGraphVisualSettings settings = myPresentationModel.getVisualSettings();
    int y = settings.getVerticalValue() + settings.getVerticalExtent() - ConcurrencyGraphSettings.RULER_STROKE_WIDTH;
    g.drawLine(0, y, getWidth(), y);
    FontMetrics metrics = g.getFontMetrics(getFont());
    int rulerUnitWidth = ConcurrencyGraphSettings.CELL_WIDTH * myPresentationModel.getVisualSettings().getCellsPerRulerUnit();
    int horizontalValue = myPresentationModel.getVisualSettings().getHorizontalValue();
    for (int i = horizontalValue / rulerUnitWidth;
         i < (horizontalValue + myPresentationModel.getVisualSettings().getHorizontalExtent()) / rulerUnitWidth; ++i) {
      int markY = i % ConcurrencyGraphSettings.RULER_SUBUNITS_PER_UNIT == 0 ? y - ConcurrencyGraphSettings.RULER_UNIT_MARK :
                  y - ConcurrencyGraphSettings.RULER_SUBUNIT_MARK;
      g.drawLine(i * rulerUnitWidth, markY, i * rulerUnitWidth, y);
      if ((i != 0) && (i % ConcurrencyGraphSettings.RULER_SUBUNITS_PER_UNIT == 0)) {
        int ms = myPresentationModel.getVisualSettings().getMcsPerCell() *
                 myPresentationModel.getVisualSettings().getCellsPerRulerUnit() * i / 1000;
        String text = String.format("%.2f s", ms / 1000f);
        int textWidth = metrics.stringWidth(text);
        int textHeight = metrics.getHeight();
        g.drawString(text, i * rulerUnitWidth - textWidth / 2, markY - textHeight / 3);
      }
    }
  }

  private void painRelations(@NotNull Graphics g) {
    // we have to draw relations upon the table in order to draw over the horizontal lines
    ConcurrencyRenderingUtil.paintRelations(g, myPresentationModel.getPadding(), myPresentationModel.getRelations());
  }

  @Override
  protected void paintComponent(@NotNull Graphics g) {
    super.paintComponent(g);
    painRelations(g);
    paintRuler(g);
    drawTimeCursor(g);
  }
}
