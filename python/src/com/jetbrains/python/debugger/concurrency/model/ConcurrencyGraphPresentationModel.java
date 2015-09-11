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

import com.jetbrains.python.debugger.concurrency.tool.ConcurrencyGraphSettings;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class ConcurrencyGraphPresentationModel {
  private final ConcurrencyGraphModel myGraphModel;
  private List<PresentationListener> myListeners = new ArrayList<PresentationListener>();
  private final Object myListenersObject = new Object();
  public ConcurrencyGraphVisualSettings visualSettings;

  public ConcurrencyGraphPresentationModel(final ConcurrencyGraphModel graphModel) {
    myGraphModel = graphModel;
    visualSettings = new ConcurrencyGraphVisualSettings(this);

    myGraphModel.registerListener(new ConcurrencyGraphModel.GraphListener() {
      @Override
      public void graphChanged() {
        updateGraphModel();
        notifyListeners();
      }
    });
  }

  public void updateGraphModel() {
    notifyListeners();
  }

  public int getLinesNumber() {
    return myGraphModel.getMaxThread();
  }

  public int getCellsNumber() {
    return (int)myGraphModel.getDuration() / visualSettings.getMicrosecsPerCell();
  }

  public ArrayList<String> getThreadNames() {
    return myGraphModel.getThreadNames();
  }

  private long roundForCell(long time) {
    long millisPerCell = visualSettings.getMicrosecsPerCell();
    return time - (time % millisPerCell);
  }

  public ArrayList<ConcurrencyGraphBlock> getVisibleGraph() {
    if (visualSettings.getHorizontalMax() == 0) {
      return new ArrayList<ConcurrencyGraphBlock>();
    }
    long startTime = roundForCell(visualSettings.getHorizontalValue() * myGraphModel.getDuration() /
                                  visualSettings.getHorizontalMax());
    ArrayList<ConcurrencyGraphBlock> ret = new ArrayList<ConcurrencyGraphBlock>();
    int curEventId = myGraphModel.getLastEventIndexBeforeMoment(startTime);
    long curTime, nextTime = startTime;
    int i = 0;
    int numberOfCells = visualSettings.getHorizontalExtent() / ConcurrencyGraphSettings.CELL_WIDTH + 2;
    while ((i < numberOfCells) && (curEventId < myGraphModel.getSize())) {
      curTime = nextTime;
      nextTime = roundForCell(myGraphModel.getEventAt(curEventId + 1).getTime());
      long period = nextTime - curTime;
      int cellsInPeriod = (int)(period / visualSettings.getMicrosecsPerCell());
      if (cellsInPeriod != 0) {
        ret.add(new ConcurrencyGraphBlock(myGraphModel.getDrawElementsForRow(curEventId), cellsInPeriod));
        i += cellsInPeriod;
      }
      curEventId += 1;
    }
    return ret;
  }

  public interface PresentationListener {
    void graphChanged(int padding);
  }

  public void registerListener(@NotNull PresentationListener logListener) {
    synchronized (myListenersObject) {
      myListeners.add(logListener);
    }
  }

  public void notifyListeners() {
    int horizontalMax = visualSettings.getHorizontalMax();
    int horizontalValue = visualSettings.getHorizontalValue();
    synchronized (myListenersObject) {
      for (PresentationListener logListener : myListeners) {
        logListener.graphChanged(horizontalMax == 0 ? horizontalValue: horizontalValue * getCellsNumber() / horizontalMax);
      }
    }
  }

}
