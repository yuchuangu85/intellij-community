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
import com.jetbrains.python.debugger.concurrency.tool.ConcurrencyGraphCellRenderer;
import com.jetbrains.python.debugger.concurrency.tool.ConcurrencyGraphSettings;
import com.jetbrains.python.debugger.concurrency.tool.ConcurrencyTableUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.table.TableModel;
import java.awt.*;

public class ConcurrencyTable extends JBTable {
  protected ConcurrencyGraphPresentationModel myPresentationModel;

  public ConcurrencyTable(ConcurrencyGraphPresentationModel model, TableModel tableModel) {
    super();
    myPresentationModel = model;
    setModel(tableModel);
    setDefaultRenderer(ConcurrencyTableUtil.GraphCell.class, new ConcurrencyGraphCellRenderer(myPresentationModel, this));
  }

  private static void prepareRulerStroke(Graphics g) {
    Graphics2D g2 = (Graphics2D)g;
    g2.setStroke(new BasicStroke(ConcurrencyGraphSettings.RULER_STROKE_WIDTH));
    g2.setColor(ConcurrencyGraphSettings.RULER_COLOR);
  }

  private void paintRuler(Graphics g) {
    prepareRulerStroke(g);
    ConcurrencyGraphVisualSettings settings = myPresentationModel.visualSettings;
    int y = settings.getVerticalValue() + settings.getVerticalExtent() - ConcurrencyGraphSettings.RULER_STROKE_WIDTH;
    g.drawLine(0, y, getWidth(), y);
    FontMetrics metrics = g.getFontMetrics(getFont());
    int rulerUnitWidth = ConcurrencyGraphSettings.CELL_WIDTH * myPresentationModel.visualSettings.getCellsPerRulerUnit();

    for (int i = 0; i < getWidth() / rulerUnitWidth; ++i) {
      int markY = i % 10 == 0 ? y - ConcurrencyGraphSettings.RULER_UNIT_MARK : y - ConcurrencyGraphSettings.RULER_SUBUNIT_MARK;
      g.drawLine(i * rulerUnitWidth, markY, i * rulerUnitWidth, y);
      if ((i != 0) && (i % 10 == 0)) {
        int ms = myPresentationModel.visualSettings.getMicrosecsPerCell() *
                 myPresentationModel.visualSettings.getCellsPerRulerUnit() * i / 1000;
        String text = String.format("%.2f s", ms / 1000f);
        int textWidth = metrics.stringWidth(text);
        int textHeight = metrics.getHeight();
        g.drawString(text, i * rulerUnitWidth - textWidth / 2, markY - textHeight);
      }
    }
  }

  @Override
  protected void paintComponent(@NotNull Graphics g) {
    super.paintComponent(g);
    paintRuler(g);
  }
}
