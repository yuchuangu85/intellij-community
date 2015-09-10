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

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;

public class ConcurrencyGraphView extends JComponent {
  private final ConcurrencyGraphPresentationModel myGraphPresentation;
  private int myPadding;

  public ConcurrencyGraphView(ConcurrencyGraphPresentationModel graphPresentation) {
    myGraphPresentation = graphPresentation;
    myGraphPresentation.registerListener(new ConcurrencyGraphPresentationModel.PresentationListener() {
      @Override
      public void graphChanged(int leftPadding) {
        myPadding = leftPadding;
        update();
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
        String text = String.format("%d ms", myGraphPresentation.visualSettings.getMillisPerCell() *
                                             myGraphPresentation.visualSettings.getCellsPerRulerUnit() * i);
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
    ArrayList<ConcurrencyGraphBlock> elements = myGraphPresentation.getVisibleGraph();
    int paddingInsideBlock = 0;
    for (ConcurrencyGraphBlock block: elements) {
      ConcurrencyRenderingUtil.paintBlock(g, ConcurrencyGraphSettings.CELL_WIDTH * (myPadding + paddingInsideBlock), block);
      paddingInsideBlock += block.numberOfCells;
    }
    paintRuler(g);
  }
}
