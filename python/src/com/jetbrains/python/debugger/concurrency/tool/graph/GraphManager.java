
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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;

public class GraphManager {
  private final PyConcurrencyLogManager myLogManager;
  private DrawElement[][] myGraphScheme;
  private int[] threadCountForRow;
  private Map<String, Integer> threadIndexToId;
  private final Object myUpdateObject = new Object();
  private int currentMaxThread = 0;
  private int[][] relations;
  private GraphAnalyser myGraphAnalyser;

  public GraphManager(PyConcurrencyLogManager logManager) {
    myLogManager = logManager;
    threadIndexToId = new HashMap<String, Integer>();
    updateGraph();

    myLogManager.registerListener(new PyConcurrencyLogManager.Listener() {
      @Override
      public void logChanged() {
        updateGraph();
      }
    });
  }

  public String getStringForRow(int row) {
    synchronized (myUpdateObject) {
      StringBuilder sb = new StringBuilder();
      for (int i = 0; i < threadCountForRow[row]; ++i) {
        sb.append(myGraphScheme[row][i].toString()).append(" ");
      }
      return sb.toString();
    }
  }

  public int getSize() {
    return myLogManager.getSize();
  }

  public PyConcurrencyEvent getEventAt(int index) {
    return myLogManager.getEventAt(index);
  }

  public ArrayList<DrawElement> getDrawElementsForRow(int row) {
    synchronized (myUpdateObject) {
      return new ArrayList<DrawElement>(Arrays.asList(myGraphScheme[row]));
    }
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

  public void updateGraph() {
    synchronized (myUpdateObject) {
      myGraphScheme = new DrawElement[myLogManager.getSize() + 1][];
      threadCountForRow = new int[myLogManager.getSize() + 1];
      relations = new int[myLogManager.getSize() + 1][2];
      myGraphAnalyser = new GraphAnalyser(myLogManager);
      currentMaxThread = 0;
      for (int i = 0; i < myLogManager.getSize(); ++i) {
        PyConcurrencyEvent event = myLogManager.getEventAt(i);
        String eventThreadId = event.getThreadId();

        if (event.isThreadEvent() && event.getType() == PyConcurrencyEvent.EventType.START) {
          DrawElement element;
          element = new EventDrawElement(new StoppedThreadState(), new RunThreadState());
          currentMaxThread++;
          threadIndexToId.put(eventThreadId, currentMaxThread - 1);

          String parentId = ((PyThreadEvent)event).getParentThreadId();
          if (parentId != null) {
            if (threadIndexToId.containsKey(parentId)) {
              int parentNum = threadIndexToId.get(parentId);
              int eventNum = currentMaxThread - 1;
              addRelation(i, parentNum, eventNum);
            }
          }

          myGraphScheme[i] = new DrawElement[currentMaxThread];
          for (int j = 0; j < currentMaxThread - 1; ++j) {
            myGraphScheme[i][j] = myGraphScheme[i - 1][j].getNextElement();
          }
          myGraphScheme[i][currentMaxThread - 1] = element;

        } else {
          int eventThreadIdInt = threadIndexToId.containsKey(eventThreadId) ? threadIndexToId.get(eventThreadId) : 0;

          if (event instanceof PyThreadEvent) {
            String parentId = ((PyThreadEvent)event).getParentThreadId();
            if ((parentId != null) && (threadIndexToId.containsKey(parentId))) {
              int parentNum = threadIndexToId.get(((PyThreadEvent)event).getParentThreadId());
              int eventNum = eventThreadIdInt;
              addRelation(i, parentNum, eventNum);
            }
          }

          myGraphScheme[i] = new DrawElement[currentMaxThread];
          for (int j = 0; j < currentMaxThread; ++j) {
            if (j != eventThreadIdInt) {
              myGraphScheme[i][j] = myGraphScheme[i - 1][j].getNextElement();
            }
          }

          myGraphScheme[i][eventThreadIdInt] = getDrawElementForEvent(event, myGraphScheme[i - 1][eventThreadIdInt], i);

          if (event.getType() == PyConcurrencyEvent.EventType.ACQUIRE_BEGIN) {
            HashSet<String> deadlocked = myGraphAnalyser.checkForDeadlocks(i);
            if (deadlocked != null) {
              for (String threadId : deadlocked) {
                myGraphScheme[i][threadIndexToId.get(threadId)].setAfter(new DeadlockState());
              }
            }
          }
        }
        threadCountForRow[i] = currentMaxThread;
      }
    }
  }
}
