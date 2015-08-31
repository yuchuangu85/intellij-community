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

import com.jetbrains.python.debugger.concurrency.tool.graph.elements.DrawElement;

import java.awt.*;
import java.util.ArrayList;

public class GraphBlock {
  private ArrayList<DrawElement> myElements;

  public int getNumberOfCells() {
    return numberOfCells;
  }

  private int numberOfCells;

  public GraphBlock(ArrayList<DrawElement> elements, int numberOfCells) {
    myElements = elements;
    this.numberOfCells = numberOfCells;
  }

  public void paint(Graphics g, int x) {
    for (int j = 0; j < myElements.size(); ++j) {
      myElements.get(j).paint(g, x, (GraphSettings.CELL_HEIGHT + GraphSettings.INTERVAL) * j + GraphSettings.INTERVAL, numberOfCells);
    }
  }
}
