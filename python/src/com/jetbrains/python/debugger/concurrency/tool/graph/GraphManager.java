
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

import com.intellij.util.containers.HashSet;
import com.intellij.util.containers.hash.HashMap;
import com.jetbrains.python.debugger.PyConcurrencyEvent;
import com.jetbrains.python.debugger.PyThreadEvent;
import com.jetbrains.python.debugger.concurrency.PyConcurrencyLogManager;
import com.jetbrains.python.debugger.concurrency.tool.graph.elements.DrawElement;
import com.jetbrains.python.debugger.concurrency.tool.graph.elements.EventDrawElement;
import com.jetbrains.python.debugger.concurrency.tool.graph.elements.SimpleDrawElement;
import com.jetbrains.python.debugger.concurrency.tool.graph.states.*;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class GraphManager {
  private final PyConcurrencyLogManager myLogManager;
  private ArrayList<ArrayList<DrawElement>> myGraphScheme;
  private ArrayList<Integer> myThreadCountForRow;
  private Map<String, Integer> threadIndexToId;
  private final Object myUpdateObject = new Object();
  private int myCurrentMaxThread = 0;
  private int[][] relations;
  private List<GraphListener> myListeners = new ArrayList<GraphListener>();
  private GraphAnalyser myGraphAnalyser;

  public GraphManager(PyConcurrencyLogManager logManager) {
    myLogManager = logManager;
    threadIndexToId = new HashMap<String, Integer>();
    createGraph();
    updateGraph();

    myLogManager.registerListener(new PyConcurrencyLogManager.LogListener() {
      @Override
      public void logChanged(boolean isNewSession) {
        if (isNewSession) {
          createGraph();
        }
        updateGraph();
      }
    });
  }

  public interface GraphListener {
    void graphChanged();
  }

  public void registerListener(@NotNull GraphListener logListener) {
    synchronized (myUpdateObject) {
      myListeners.add(logListener);
    }
  }

  public void notifyListeners() {
    synchronized (myUpdateObject) {
      for (GraphListener logListener : myListeners) {
        logListener.graphChanged();
      }
    }
  }

  public String getStringForRow(int row) {
    synchronized (myUpdateObject) {
      StringBuilder sb = new StringBuilder();
      for (int i = 0; i < myThreadCountForRow.get(row); ++i) {
        sb.append(myGraphScheme.get(row).get(i).toString()).append(" ");
      }
      return sb.toString();
    }
  }

  public int getSize() {
    return myGraphScheme.size();
  }

  public PyConcurrencyEvent getEventAt(int index) {
    return myLogManager.getEventAt(index);
  }

  public ArrayList<DrawElement> getDrawElementsForRow(int row) {
    return new ArrayList<DrawElement>(myGraphScheme.get(row));
  }

  private DrawElement getDrawElementForEvent(PyConcurrencyEvent event, DrawElement previousElement, int index) {
    switch (event.getType()) {
      case CREATE:
        return new EventDrawElement(previousElement.getAfter(), previousElement.getAfter());
      case START:
        return new EventDrawElement(new StoppedThreadState(), new RunThreadState());
      case JOIN:
        return new EventDrawElement(previousElement.getAfter(), previousElement.getAfter());
      case STOP:
        return new EventDrawElement(previousElement.getAfter(), new StoppedThreadState());
      case ACQUIRE_BEGIN:
        return new EventDrawElement(previousElement.getAfter(), new LockWaitThreadState());
      case ACQUIRE_END:
        return new EventDrawElement(previousElement.getAfter(), new LockOwnThreadState());
      case RELEASE:
        return new EventDrawElement(previousElement.getAfter(), myGraphAnalyser.getThreadStateAt(index, event.getThreadId()));
      default:
        return new SimpleDrawElement(new StoppedThreadState(), new StoppedThreadState());
    }
  }

  private void addRelation(int index, int parent, int child) {
    relations[index][0] = parent;
    relations[index][1] = child;
  }

  public int[] getRelationForRow(int row) {
    return relations[row];
  }

  private void createGraph() {
    synchronized (myUpdateObject) {
      myGraphScheme = new ArrayList<ArrayList<DrawElement>>(myLogManager.getSize());
      myThreadCountForRow = new ArrayList<Integer>();
      myGraphAnalyser = new GraphAnalyser(myLogManager);
      myCurrentMaxThread = 0;
      notifyListeners();
    }
  }

  private void updateGraph() {
    synchronized (myUpdateObject) {
      relations = new int[myLogManager.getSize() + 1][2];
      for (int i = myGraphScheme.size(); i < myLogManager.getSize(); ++i) {
        PyConcurrencyEvent event = myLogManager.getEventAt(i);
        String eventThreadId = event.getThreadId();

        if (event.isThreadEvent() && event.getType() == PyConcurrencyEvent.EventType.START) {
          DrawElement element;
          element = new EventDrawElement(new StoppedThreadState(), new RunThreadState());
          myCurrentMaxThread++;
          threadIndexToId.put(eventThreadId, myCurrentMaxThread - 1);
          String parentId = ((PyThreadEvent)event).getParentThreadId();
          if (parentId != null) {
            if (threadIndexToId.containsKey(parentId)) {
              int parentNum = threadIndexToId.get(parentId);
              int eventNum = myCurrentMaxThread - 1;
              addRelation(i, parentNum, eventNum);
            }
          }
          myGraphScheme.add(i, new ArrayList<DrawElement>(myCurrentMaxThread));
          for (int j = 0; j < myCurrentMaxThread - 1; ++j) {
            myGraphScheme.get(i).add(j, myGraphScheme.get(i - 1).get(j).getNextElement());
          }
          myGraphScheme.get(i).add(myCurrentMaxThread - 1, element);
        } else {

          int eventThreadIdInt = threadIndexToId.containsKey(eventThreadId) ? threadIndexToId.get(eventThreadId) : 0;
          if (event instanceof PyThreadEvent) {
            String parentId = ((PyThreadEvent)event).getParentThreadId();
            if ((parentId != null) && (threadIndexToId.containsKey(parentId))) {
              int parentNum = threadIndexToId.get(((PyThreadEvent)event).getParentThreadId());
              addRelation(i, parentNum, eventThreadIdInt);
            }
          }

          myGraphScheme.add(i, new ArrayList<DrawElement>());
          for (int j = 0; j < myCurrentMaxThread; ++j) {
            if (j != eventThreadIdInt) {
              myGraphScheme.get(i).add(j, myGraphScheme.get(i - 1).get(j).getNextElement());
            } else {
              myGraphScheme.get(i).add(eventThreadIdInt, getDrawElementForEvent(event, myGraphScheme.get(i - 1).get(eventThreadIdInt), i));
            }
          }

          if (event.getType() == PyConcurrencyEvent.EventType.ACQUIRE_BEGIN) {
            HashSet<String> deadlocked = myGraphAnalyser.checkForDeadlocks(i);
            if (deadlocked != null) {
              for (String threadId : deadlocked) {
                myGraphScheme.get(i).get(threadIndexToId.get(threadId)).setAfter(new DeadlockState());
              }
            }
          }
        }
        myThreadCountForRow.add(i, myCurrentMaxThread);
      }
    }
    notifyListeners();
  }
}
