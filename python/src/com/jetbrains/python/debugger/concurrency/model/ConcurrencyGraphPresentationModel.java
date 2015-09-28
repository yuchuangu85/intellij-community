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

import com.jetbrains.python.debugger.PyConcurrencyEvent;
import com.jetbrains.python.debugger.PyLockEvent;
import com.jetbrains.python.debugger.concurrency.tool.ConcurrencyGraphSettings;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class ConcurrencyGraphPresentationModel {
  public ConcurrencyGraphModel myGraphModel;
  public ConcurrencyGraphVisualSettings visualSettings;
  private List<PresentationListener> myListeners = new ArrayList<PresentationListener>();
  private final Object myListenersObject = new Object();
  private ArrayList<ConcurrencyGraphBlock> myVisibleGraph;
  private final Object myVisibleGraphObject = new Object();
  private float myTimeCursor;

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

    updateTimerPeriod();
  }

  public void updateTimerPeriod() {
    myGraphModel.setTimerPeriod(visualSettings.getCellsPerRulerUnit() * visualSettings.getMicrosecsPerCell() / 1000);
  }

  public float getTimeCursor() {
    return myTimeCursor;
  }

  public void setTimeCursor(float timeCursor) {
    myTimeCursor = timeCursor;
  }

  public void updateGraphModel() {
    updateVisibleGraph();
    notifyListeners();
  }

  public int getCellsNumber() {
    return (int)myGraphModel.getDuration() / visualSettings.getMicrosecsPerCell();
  }

  private long roundForCell(long time) {
    long millisPerCell = visualSettings.getMicrosecsPerCell();
    return time - (time % millisPerCell);
  }

  private void updateVisibleGraph() {
    synchronized (myVisibleGraphObject) {
      if ((visualSettings.getHorizontalMax() == 0) || (myGraphModel.getSize() == 0)) {
        myVisibleGraph = new ArrayList<ConcurrencyGraphBlock>();
        return;
      }
      long startTime = roundForCell(visualSettings.getHorizontalValue() * myGraphModel.getDuration() /
                                    visualSettings.getHorizontalMax());
      myVisibleGraph = new ArrayList<ConcurrencyGraphBlock>();
      int curEventId = myGraphModel.getLastEventIndexBeforeMoment(startTime);
      long curTime, nextTime = startTime;
      int i = 0;
      int numberOfCells = visualSettings.getHorizontalExtent() / ConcurrencyGraphSettings.CELL_WIDTH + 2;
      while ((i < numberOfCells) && (curEventId < myGraphModel.getSize() - 1)) {
        curTime = nextTime;
        nextTime = roundForCell(myGraphModel.getEventAt(curEventId + 1).getTime());
        long period = nextTime - curTime;
        int cellsInPeriod = (int)(period / visualSettings.getMicrosecsPerCell());
        if (cellsInPeriod != 0) {
          myVisibleGraph.add(new ConcurrencyGraphBlock(myGraphModel.getDrawElementsForRow(curEventId),
                                                       cellsInPeriod, myGraphModel.getRelationForRow(curEventId)));
          i += cellsInPeriod;
        }
        curEventId += 1;
      }
    }
  }

  public ArrayList<ConcurrencyGraphBlock> getVisibleGraph() {
    updateVisibleGraph();
    return myVisibleGraph;
  }

  public void applySelectionFilter(int eventId) {
    PyConcurrencyEvent event = myGraphModel.getEventAt(eventId);
    if ((event instanceof PyLockEvent) && (event.getType() != PyConcurrencyEvent.EventType.RELEASE)) {
      PyLockEvent lockEvent = (PyLockEvent)event;
      String lockId = lockEvent.getLockId();
      myGraphModel.setFilterLockId(lockId);
    } else if (event.getType() == PyConcurrencyEvent.EventType.STOP) {
      myGraphModel.setFilterLockId(null);
    }
  }

  public interface PresentationListener {
    void graphChanged(int padding);
  }

  public void registerListener(@NotNull PresentationListener logListener) {
    synchronized (myListenersObject) {
      myListeners.add(logListener);
    }
  }

  public int getPadding() {
    int horizontalMax = visualSettings.getHorizontalMax();
    int horizontalValue = visualSettings.getHorizontalValue();
    return horizontalMax == 0 ? horizontalValue: horizontalValue * getCellsNumber() / horizontalMax;
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
