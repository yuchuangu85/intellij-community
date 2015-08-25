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
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class GraphPresentation {
  private final GraphManager myGraphManager;
  private GraphVisualSettings myVisualSettings;
  private List<PresentationListener> myListeners = new ArrayList<PresentationListener>();

  public GraphPresentation(final GraphManager graphManager, GraphVisualSettings visualSettings) {
    myGraphManager = graphManager;
    myVisualSettings = visualSettings;

    myVisualSettings.registerListener(new GraphVisualSettings.SettingsListener() {
      @Override
      public void settingsChanged() {
        updateGraphModel();
        notifyListeners();
      }
    });

    myGraphManager.registerListener(new GraphManager.GraphListener() {
      @Override
      public void graphChanged() {
        updateGraphModel();
        notifyListeners();
      }
    });
  }

  private void updateGraphModel() {
  }

  public ArrayList<ArrayList<DrawElement>> getVisibleGraph() {
    int val = myVisualSettings.getScrollbarValue();
    int first = val * myGraphManager.getSize() / myVisualSettings.getScrollbarMax();
    int last = Math.min(first + myVisualSettings.getScrollbarExtent() / GraphSettings.CELL_WIDTH + 2, myGraphManager.getSize());
    ArrayList<ArrayList<DrawElement>> ret = new ArrayList<ArrayList<DrawElement>>();
    for (int i = first; i < last; ++i) {
      ret.add(myGraphManager.getDrawElementsForRow(i));
    }
    return ret;
  }


  public interface PresentationListener {
    void graphChanged(int padding, int size);
  }

  public void registerListener(@NotNull PresentationListener logListener) {
    myListeners.add(logListener);
  }

  public void notifyListeners() {
    for (PresentationListener logListener : myListeners) {
      logListener.graphChanged(myVisualSettings.getScrollbarMax() == 0? myVisualSettings.getScrollbarValue():
                               myVisualSettings.getScrollbarValue() * myGraphManager.getSize() / myVisualSettings.getScrollbarMax(),
                               myGraphManager.getSize());
    }
  }

}
