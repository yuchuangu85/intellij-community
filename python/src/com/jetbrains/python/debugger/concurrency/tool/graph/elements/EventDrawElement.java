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
package com.jetbrains.python.debugger.concurrency.tool.graph.elements;

import com.jetbrains.python.debugger.concurrency.tool.graph.GraphSettings;
import com.jetbrains.python.debugger.concurrency.tool.graph.states.StoppedThreadState;
import com.jetbrains.python.debugger.concurrency.tool.graph.states.ThreadState;

import java.awt.*;

public class EventDrawElement extends DrawElement {

  public EventDrawElement(ThreadState before, ThreadState after) {
    super(before, after);
  }

  @Override
  public DrawElement getNextElement() {
    return new SimpleDrawElement(myAfter, myAfter);
  }

  @Override
  public void paint(Graphics g, int x, int y, int numberOfCells) {
    Graphics2D g2 = (Graphics2D)g;
    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    if (!(myBefore instanceof StoppedThreadState)) {
      myBefore.prepareStroke(g2);
    }
    if (!(myAfter instanceof StoppedThreadState)) {
      myAfter.prepareStroke(g2);
      g2.fillRect(x, y, GraphSettings.CELL_WIDTH * numberOfCells, GraphSettings.CELL_HEIGHT);
    }
  }
}
