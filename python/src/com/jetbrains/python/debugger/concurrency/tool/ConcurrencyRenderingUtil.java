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
import com.jetbrains.python.debugger.concurrency.model.ConcurrencyThreadState;

import java.awt.*;
import java.util.ArrayList;

public class ConcurrencyRenderingUtil {

  public static void prepareStroke(Graphics g, ConcurrencyThreadState threadState) {
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

  public static void paintBlockElements(Graphics g, int externalPadding, ArrayList<ConcurrencyGraphBlock> drawingElements) {
    Graphics2D g2 = (Graphics2D)g;
    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    int paddingInsideBlock = 0;
    for (ConcurrencyGraphBlock block: drawingElements) {
      int padding = ConcurrencyGraphSettings.CELL_WIDTH * (externalPadding + paddingInsideBlock);
      for (int j = 0; j < block.elements.size(); ++j) {
        ConcurrencyThreadState element = block.elements.get(j);
        if (element != ConcurrencyThreadState.Stopped) {
          prepareStroke(g2, element);
          g2.fillRect(padding,
                      (ConcurrencyGraphSettings.CELL_HEIGHT + ConcurrencyGraphSettings.INTERVAL) * j + ConcurrencyGraphSettings.INTERVAL,
                      ConcurrencyGraphSettings.CELL_WIDTH * block.numberOfCells, ConcurrencyGraphSettings.CELL_HEIGHT);
        }
      }
      paddingInsideBlock += block.numberOfCells;
    }
  }

  public static Point getElementIndex(int externalPadding, ArrayList<ConcurrencyGraphBlock> drawingElements, int x, int y) {
    x = x / ConcurrencyGraphSettings.CELL_WIDTH - externalPadding;
    int paddingInsideBlock = 0;
    for (int i = 0; i < drawingElements.size(); ++i) {
      ConcurrencyGraphBlock block = drawingElements.get(i);
      int blockWidth = block.numberOfCells;
      if ((paddingInsideBlock < x) && (paddingInsideBlock + blockWidth > x)) {
        int yRet = y / (ConcurrencyGraphSettings.CELL_HEIGHT + ConcurrencyGraphSettings.INTERVAL);
        if ((y % (ConcurrencyGraphSettings.CELL_HEIGHT + ConcurrencyGraphSettings.INTERVAL) > ConcurrencyGraphSettings.INTERVAL) &&
            yRet < block.elements.size()) {
          return new Point(i, yRet);
        } else {
          return new Point(i, -1);
        }
      }
      paddingInsideBlock += block.numberOfCells;
    }
    return new Point(-1, -1);
  }

}
