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

import com.intellij.ui.JBColor;
import com.jetbrains.python.debugger.concurrency.tool.graph.elements.DrawElement;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;

public class GraphRenderer extends JComponent {
  private final GraphPresentation myGraphPresentation;
  private int myPadding;
  private int myFullLogSize;

  public GraphRenderer(GraphPresentation graphPresentation) {
    myGraphPresentation = graphPresentation;
    myGraphPresentation.registerListener(new GraphPresentation.PresentationListener() {
      @Override
      public void graphChanged(int leftPadding, int size) {
        myPadding = leftPadding;
        myFullLogSize = size;
        update();
      }
    });
    updateSize();
  }

  private void updateSize() {
    setPreferredSize(new Dimension(myFullLogSize * GraphSettings.CELL_WIDTH,
                                   myGraphPresentation.getVisualSettings().getHeightForPanes(myGraphPresentation.getLinesNumber())));
  }

  private void update() {
    repaint();
  }

  private void paintBackground(Graphics g) {
    updateSize();
    g.setColor(GraphSettings.BACKGROUND_COLOR);
    g.fillRect(0, 0, getWidth(), getHeight());
  }

  @Override
  public void paintComponent(Graphics g) {
    super.paintComponent(g);
    paintBackground(g);
    ArrayList<ArrayList<DrawElement>> elements = myGraphPresentation.getVisibleGraph();
    for (int i = 0; i < elements.size(); ++i) {
      ArrayList<DrawElement> row = elements.get(i);
      for (int j = 0; j < row.size(); ++j) {
        DrawElement element = row.get(j);
        element.paint(g, GraphSettings.CELL_WIDTH * (myPadding + i),
                      (GraphSettings.CELL_HEIGHT + GraphSettings.INTERVAL) * j + GraphSettings.INTERVAL);
      }
    }
  }
}
