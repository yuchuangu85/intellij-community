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

import com.intellij.util.ui.UIUtil;
import com.jetbrains.python.debugger.PyConcurrencyEvent;
import com.jetbrains.python.debugger.PyLockEvent;
import com.jetbrains.python.debugger.concurrency.tool.ConcurrencyGraphSettings;
import com.jetbrains.python.debugger.concurrency.tool.panels.ConcurrencyToolWindowPanel;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class ConcurrencyGraphPresentationModel {
  private final ConcurrencyGraphModel graphModel;
  private final ConcurrencyGraphVisualSettings visualSettings;
  private final ConcurrencyToolWindowPanel myToolWindowPanel;
  private final List<PresentationListener> myListeners = new ArrayList<PresentationListener>();
  private final Object myListenersObject = new Object();
  private ArrayList<ConcurrencyGraphBlock> myVisibleGraph;
  private final Object myVisibleGraphObject = new Object();
  private ArrayList<ConcurrencyRelation> myRelations;
  private boolean myScrollToTheEnd = true;
  private boolean isGraphChanged = false;
  private ConcurrencyGraphBlock[] myGraphForDrawing;

  public ConcurrencyGraphPresentationModel(@NotNull final ConcurrencyGraphModel graphModel, @NotNull ConcurrencyToolWindowPanel panel) {
    this.graphModel = graphModel;
    visualSettings = new ConcurrencyGraphVisualSettings(this);
    myToolWindowPanel = panel;

    this.graphModel.registerListener(new ConcurrencyGraphModel.GraphListener() {
      @Override
      public void graphChanged() {
        if (myScrollToTheEnd) {
          visualSettings.scrollToTheEnd();
        }
        UIUtil.invokeLaterIfNeeded(new Runnable() {
          @Override
          public void run() {
            JTable table = myToolWindowPanel.getStatTable();
            if (table != null) {
              ((AbstractTableModel)table.getModel()).fireTableDataChanged();
            }
            table = myToolWindowPanel.getNamesTable();
            if (table != null) {
              ((AbstractTableModel)table.getModel()).fireTableDataChanged();
            }
          }
        });
        updateGraphModel();
        notifyListeners();
      }
    });

    updateTimerPeriod();
  }

  public boolean isScrollToTheEnd() {
    return myScrollToTheEnd;
  }

  public void setScrollToTheEnd(boolean scrollToTheEnd) {
    myScrollToTheEnd = scrollToTheEnd;
  }

  public void updateTimerPeriod() {
    int rulerUnitWidth = ConcurrencyGraphSettings.CELL_WIDTH * visualSettings.getCellsPerRulerUnit();
    graphModel.setTimerPeriod(Math.max(1, visualSettings.getCellsPerRulerUnit() * visualSettings.getMcsPerCell() /
                                          (rulerUnitWidth * 1000))); //convert from mcs to millis
  }

  @NotNull
  public ConcurrencyToolWindowPanel getToolWindowPanel() {
    return myToolWindowPanel;
  }

  public void updateGraphModel() {
    updateVisibleGraph();
    notifyListeners();
  }

  public int getCellsNumber() {
    return (int)graphModel.getDuration() / visualSettings.getMcsPerCell();
  }

  private long roundForCell(long time) {
    long millisPerCell = visualSettings.getMcsPerCell();
    return time - (time % millisPerCell);
  }

  @NotNull
  public ArrayList<ConcurrencyRelation> getRelations() {
    return new ArrayList<ConcurrencyRelation>(myRelations);
  }

  @NotNull
  public ConcurrencyGraphModel getGraphModel() {
    return graphModel;
  }

  @NotNull
  public ConcurrencyGraphVisualSettings getVisualSettings() {
    return visualSettings;
  }

  private void updateVisibleGraph() {
    synchronized (myVisibleGraphObject) {
      if ((visualSettings.getHorizontalMax() == 0) || (graphModel.getSize() == 0)) {
        myVisibleGraph = new ArrayList<ConcurrencyGraphBlock>();
        return;
      }
      long startTime = roundForCell(visualSettings.getHorizontalValue() * graphModel.getDuration() /
                                    visualSettings.getHorizontalMax());
      myVisibleGraph = new ArrayList<ConcurrencyGraphBlock>();
      myRelations = new ArrayList<ConcurrencyRelation>();
      int curEventId = graphModel.getLastEventIndexBeforeMoment(startTime);
      long curTime, nextTime = startTime;
      int i = 0;
      int numberOfCells = visualSettings.getHorizontalExtent() / ConcurrencyGraphSettings.CELL_WIDTH + 2;
      while ((i < numberOfCells) && (curEventId < graphModel.getSize() - 1)) {
        curTime = nextTime;
        Point relation = graphModel.getRelationForRow(curEventId);
        if (relation != null) {
          myRelations.add(new ConcurrencyRelation(i, relation.x, relation.y, graphModel.getThreadStateForEvent(curEventId, relation.x)));
        }
        nextTime = roundForCell(graphModel.getEventAt(curEventId + 1).getTime());
        long period = nextTime - curTime;
        int cellsInPeriod = (int)(period / visualSettings.getMcsPerCell());
        if (cellsInPeriod != 0) {
          myVisibleGraph.add(new ConcurrencyGraphBlock(graphModel.getDrawElementsForRow(curEventId), cellsInPeriod));
          i += cellsInPeriod;
        }
        curEventId += 1;
      }
      isGraphChanged = true;
    }
  }

  public ConcurrencyGraphBlock[] getVisibleGraph() {
    synchronized (myVisibleGraphObject) {
      if (isGraphChanged) {
        myGraphForDrawing = new ConcurrencyGraphBlock[myVisibleGraph.size()];
        myGraphForDrawing = myVisibleGraph.toArray(myGraphForDrawing);
        isGraphChanged = false;
      }
    }
    return myGraphForDrawing;
  }

  public void applySelectionFilter(int eventId) {
    PyConcurrencyEvent event = graphModel.getEventAt(eventId);
    if ((event instanceof PyLockEvent) && (event.getType() != PyConcurrencyEvent.EventType.RELEASE)) {
      PyLockEvent lockEvent = (PyLockEvent)event;
      String lockId = lockEvent.getLockId();
      graphModel.setFilterLockId(lockId);
    }
    else if (event.getType() == PyConcurrencyEvent.EventType.STOP) {
      graphModel.setFilterLockId(null);
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
    return visualSettings.getHorizontalValue();
  }

  private void notifyListeners() {
    synchronized (myListenersObject) {
      for (PresentationListener logListener : myListeners) {
        logListener.graphChanged(getPadding());
      }
    }
  }
}
