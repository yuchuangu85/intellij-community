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

import com.jetbrains.python.debugger.concurrency.model.ConcurrencyGraphBlock;
import com.jetbrains.python.debugger.concurrency.model.ConcurrencyGraphPresentationModel;
import com.jetbrains.python.debugger.concurrency.model.ConcurrencyGraphVisualSettings;
import com.jetbrains.python.debugger.concurrency.tool.panels.ConcurrencyToolWindowPanel;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;

public class ConcurrencyGraphView extends JComponent {
  private final ConcurrencyGraphPresentationModel myGraphPresentation;
  private int myPadding;
  private ArrayList<ConcurrencyGraphBlock> myDrawingElements;
  private ConcurrencyToolWindowPanel myToolWindow;

  public ConcurrencyGraphView(ConcurrencyGraphPresentationModel graphPresentation, ConcurrencyToolWindowPanel toolWindow) {
    myGraphPresentation = graphPresentation;
    myGraphPresentation.registerListener(new ConcurrencyGraphPresentationModel.PresentationListener() {
      @Override
      public void graphChanged(int leftPadding) {
        myPadding = leftPadding;
        update();
      }
    });
    myToolWindow = toolWindow;

    addMouseListener(new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent e) {
        super.mouseClicked(e);
        int eventIndex = getEventIndex(e.getPoint());
        if (e.getClickCount() == 2) {
          if (eventIndex >= 0) {
            myGraphPresentation.applySelectionFilter(eventIndex);
            myToolWindow.showStackTrace(myGraphPresentation.myGraphModel.getEventAt(eventIndex));
            return;
          }
        }
        myGraphPresentation.myGraphModel.setFilterLockId(null);
        myToolWindow.showStackTrace(null);
      }
    });

    updateSize();
  }

  private void updateSize() {
    int width = myGraphPresentation.getCellsNumber() * ConcurrencyGraphSettings.CELL_WIDTH;
    int height = myGraphPresentation.visualSettings.getHeightForPanes(myGraphPresentation.getLinesNumber());
    setSize(new Dimension(width, height));
    setPreferredSize(new Dimension(width, height));
  }

  private int getEventIndex(Point clickPoint) {
    Point p = ConcurrencyRenderingUtil.getElementIndex(myPadding, myDrawingElements, clickPoint.x, clickPoint.y);
    int elementIndex = p.x;
    int threadIndex = p.y;
    if ((elementIndex != -1) && (threadIndex != -1)) {
      return myDrawingElements.get(elementIndex).elements.get(threadIndex).eventIndex;
    } else {
      return -1;
    }
  }

  private void update() {
    repaint();
  }

  private void paintBackground(Graphics g) {
    updateSize();
    g.setColor(ConcurrencyGraphSettings.BACKGROUND_COLOR);
    g.fillRect(0, 0, getWidth(), getHeight());
  }

  private static void prepareRulerStroke(Graphics g) {
    Graphics2D g2 = (Graphics2D)g;
    g2.setStroke(new BasicStroke(ConcurrencyGraphSettings.RULER_STROKE_WIDTH));
    g2.setColor(ConcurrencyGraphSettings.RULER_COLOR);
  }

  private void paintRuler(Graphics g) {
    prepareRulerStroke(g);
    ConcurrencyGraphVisualSettings settings = myGraphPresentation.visualSettings;
    int y = settings.getVerticalValue() + settings.getVerticalExtent() - ConcurrencyGraphSettings.RULER_STROKE_WIDTH;
    g.drawLine(0, y, getWidth(), y);
    FontMetrics metrics = g.getFontMetrics(getFont());
    int rulerUnitWidth = ConcurrencyGraphSettings.CELL_WIDTH * myGraphPresentation.visualSettings.getCellsPerRulerUnit();

    for (int i = 0; i < getWidth() / rulerUnitWidth; ++i) {
      int markY = i % 10 == 0 ? y - ConcurrencyGraphSettings.RULER_UNIT_MARK : y - ConcurrencyGraphSettings.RULER_SUBUNIT_MARK;
      g.drawLine(i * rulerUnitWidth, markY, i * rulerUnitWidth, y);
      if ((i != 0) && (i % 10 == 0)) {
        int ms = myGraphPresentation.visualSettings.getMicrosecsPerCell() *
                 myGraphPresentation.visualSettings.getCellsPerRulerUnit() * i / 1000;
        String text = String.format("%.2f s", ms / 1000f);
        int textWidth = metrics.stringWidth(text);
        int textHeight = metrics.getHeight();
        g.drawString(text, i * rulerUnitWidth - textWidth / 2, markY - textHeight);
      }
    }
  }

  @Override
  public void paintComponent(Graphics g) {
    super.paintComponent(g);
    paintBackground(g);
    myDrawingElements = myGraphPresentation.getVisibleGraph();
    ConcurrencyRenderingUtil.paintBlockElements(g, myPadding, myDrawingElements);
    paintRuler(g);
  }
}
