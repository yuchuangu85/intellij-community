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
import com.jetbrains.python.debugger.concurrency.model.ConcurrencyRelation;
import com.jetbrains.python.debugger.concurrency.model.ConcurrencyThreadState;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;

public class ConcurrencyRenderingUtil {

  private static void prepareStroke(@NotNull Graphics g, @NotNull ConcurrencyThreadState threadState) {
    Graphics2D g2 = (Graphics2D)g;
    switch (threadState) {
      case Run:
        g2.setStroke(new BasicStroke(ConcurrencyGraphSettings.STROKE_BASIC));
        g2.setColor(ConcurrencyGraphSettings.BASIC_COLOR);
        break;
      case Stopped:
        break;
      case LockWait:
        g2.setStroke(new BasicStroke(ConcurrencyGraphSettings.STROKE_BASIC));
        g2.setColor(ConcurrencyGraphSettings.LOCK_WAIT_COLOR);
        break;
      case LockWaitSelected:
        g2.setStroke(new BasicStroke(ConcurrencyGraphSettings.STROKE_BASIC));
        g2.setColor(ConcurrencyGraphSettings.LOCK_WAIT_SELECTED_COLOR);
        break;
      case LockOwn:
        g2.setStroke(new BasicStroke(ConcurrencyGraphSettings.STROKE_BASIC));
        g2.setColor(ConcurrencyGraphSettings.LOCK_OWNING_COLOR);
        break;
      case LockOwnSelected:
        g2.setStroke(new BasicStroke(ConcurrencyGraphSettings.STROKE_BASIC));
        g2.setColor(ConcurrencyGraphSettings.LOCK_OWNING_SELECTED_COLOR);
        break;
      case Deadlock:
        g2.setStroke(new BasicStroke(ConcurrencyGraphSettings.STROKE_BASIC));
        g2.setColor(ConcurrencyGraphSettings.DEADLOCK_COLOR);
        break;
    }
  }

  public static void paintRow(@NotNull Image img, int externalPadding, @NotNull ConcurrencyGraphBlock[] drawingElements,
                              int row, boolean isSelected) {
    if (img instanceof BufferedImage) {
      BufferedImage bufImage = (BufferedImage)img;
      Graphics2D g2 = bufImage.createGraphics();
      if (isSelected) {
        g2.setColor(ConcurrencyGraphSettings.BACKGROUND_SELECTED);
      }
      else {
        g2.setColor(ConcurrencyGraphSettings.BACKGOUND_COLOR);
      }
      g2.fillRect(0, 0, bufImage.getWidth(), bufImage.getHeight());
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      int paddingInsideBlock = 0;
      for (ConcurrencyGraphBlock block : drawingElements) {
        if (block != null) {
          if (row < block.getElements().size()) {
            int padding = ConcurrencyGraphSettings.CELL_WIDTH * (externalPadding + paddingInsideBlock);
            ConcurrencyThreadState drawElement = block.getElements().get(row).getThreadState();
            if (drawElement != ConcurrencyThreadState.Stopped) {
              prepareStroke(g2, drawElement);
              g2.fillRect(padding, ConcurrencyGraphSettings.INTERVAL / 2,
                          ConcurrencyGraphSettings.CELL_WIDTH * block.getNumberOfCells(), ConcurrencyGraphSettings.CELL_HEIGHT);
            }
          }
          paddingInsideBlock += block.getNumberOfCells();
        }
      }
    }
  }

  public static void paintBlockElements(@NotNull Graphics g, int externalPadding,
                                        @NotNull ArrayList<ConcurrencyGraphBlock> drawingElements) {
    Graphics2D g2 = (Graphics2D)g;
    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    int paddingInsideBlock = 0;
    for (ConcurrencyGraphBlock block : drawingElements) {
      int padding = ConcurrencyGraphSettings.CELL_WIDTH * (externalPadding + paddingInsideBlock);
      for (int j = 0; j < block.getElements().size(); ++j) {
        ConcurrencyThreadState element = block.getElements().get(j).getThreadState();
        if (element != ConcurrencyThreadState.Stopped) {
          prepareStroke(g2, element);
          g2.fillRect(padding,
                      (ConcurrencyGraphSettings.CELL_HEIGHT + ConcurrencyGraphSettings.INTERVAL) * j + ConcurrencyGraphSettings.INTERVAL,
                      ConcurrencyGraphSettings.CELL_WIDTH * block.getNumberOfCells(), ConcurrencyGraphSettings.CELL_HEIGHT);
        }
      }
      paddingInsideBlock += block.getNumberOfCells();
    }
  }

  public static void paintRelations(@NotNull Graphics g, int externalPadding, @NotNull ArrayList<ConcurrencyRelation> relations) {
    for (ConcurrencyRelation relation : relations) {
      prepareStroke(g, relation.getThreadState());
      Graphics2D g2 = (Graphics2D)g;
      int padding = externalPadding + relation.getPadding() * ConcurrencyGraphSettings.CELL_WIDTH;
      g2.drawLine(padding, (ConcurrencyGraphSettings.CELL_HEIGHT + ConcurrencyGraphSettings.INTERVAL) * relation.getParent() +
                           ConcurrencyGraphSettings.INTERVAL / 2 + ConcurrencyGraphSettings.STROKE_BASIC - relation.getParent(),
                  padding, (ConcurrencyGraphSettings.CELL_HEIGHT + ConcurrencyGraphSettings.INTERVAL) * (relation.getChild() + 1) -
                           ConcurrencyGraphSettings.INTERVAL / 2 - ConcurrencyGraphSettings.STROKE_BASIC + 1);
    }
  }

  public static int getElementIndex(int externalPadding, @NotNull ConcurrencyGraphBlock[] drawingElements, int x) {
    x = x / ConcurrencyGraphSettings.CELL_WIDTH - externalPadding;
    int paddingInsideBlock = 0;
    for (int i = 0; i < drawingElements.length; ++i) {
      ConcurrencyGraphBlock block = drawingElements[i];
      int blockWidth = block.getNumberOfCells();
      if ((paddingInsideBlock < x) && (paddingInsideBlock + blockWidth >= x)) {
        return i;
      }
      paddingInsideBlock += block.getNumberOfCells();
    }
    return -1;
  }
}
