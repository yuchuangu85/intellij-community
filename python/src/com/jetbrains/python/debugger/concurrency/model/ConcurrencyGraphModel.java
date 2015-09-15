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

import com.intellij.openapi.project.Project;
import com.intellij.util.containers.HashSet;
import com.intellij.util.containers.hash.HashMap;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebugSessionListener;
import com.jetbrains.python.debugger.PyConcurrencyEvent;
import com.jetbrains.python.debugger.PyLockEvent;
import com.jetbrains.python.debugger.PyThreadEvent;
import com.jetbrains.python.debugger.concurrency.tool.ConcurrencyGraphAnalyser;
import com.jetbrains.python.debugger.concurrency.tool.ConcurrencyStat;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ConcurrencyGraphModel {
  protected Project myProject;
  private List<PyConcurrencyEvent> myLog;
  private final Object myLogObject = new Object();
  private ArrayList<ArrayList<ConcurrencyThreadState>> myGraphScheme;
  private ArrayList<Integer> myThreadCountForRow;
  private Map<String, Integer> threadIndexToId;
  private ArrayList<String> threadNames;
  private final Object myUpdateObject = new Object();
  private int myCurrentMaxThread = 0;
  private int[][] relations;
  private List<GraphListener> myListeners = new ArrayList<GraphListener>();
  private final Object myListenersObject = new Object();
  private ConcurrencyGraphAnalyser myGraphAnalyser;
  private XDebugSession lastSession;
  private long myStartTime; //millis
  private long myPauseTime; //millis
  private String myFilterLockId;

  public ConcurrencyGraphModel(Project project) {
    myProject = project;
    myLog = new ArrayList<PyConcurrencyEvent>();
    createGraph();
  }

  public void addSessionListener() {
    lastSession.addSessionListener(new XDebugSessionListener() {
      @Override
      public void sessionPaused() {
        setPauseTime(System.currentTimeMillis());
        updateGraph();
      }

      @Override
      public void sessionResumed() {
        setPauseTime(0);
      }

      @Override
      public void sessionStopped() {
        updateGraph();
      }

      @Override
      public void stackFrameChanged() {

      }

      @Override
      public void beforeSessionResume() {

      }
    });
  }

  public void recordEvent(@NotNull XDebugSession debugSession, PyConcurrencyEvent event) {
    synchronized (myLogObject) {
      if (((lastSession == null) || (debugSession != lastSession)) && event == null) {
        lastSession = debugSession;
        myLog = new ArrayList<PyConcurrencyEvent>();
        addSessionListener();
        createGraph();
        return;
      }
      if (event.getTime() == 0) {
        myStartTime = System.currentTimeMillis();
      }
      myLog.add(event);
      updateGraph();
    }
  }

  public PyConcurrencyEvent getEventAt(int index) {
    if (index == getSize()) {
      return new FakeEvent((getPauseTime() - getStartTime()) * 1000); // convert from millis to microseconds
    }
    return myLog.get(index);
  }

  public int getSize() {
    return myLog.size();
  }

  public String getStringRepresentation() {
    StringBuilder resultBuilder = new StringBuilder();
    resultBuilder.append("<html>Size: ").append(myLog.size()).append("<br>");
    for (PyConcurrencyEvent event: myLog) {
      resultBuilder.append(event.toString());
    }
    resultBuilder.append("</html>");
    return resultBuilder.toString();
  }


  public long getPauseTime() {
    return myPauseTime;
  }

  public void setPauseTime(long pauseTime) {
    this.myPauseTime = pauseTime;
  }

  public long getStartTime() {
    return myStartTime;
  }

  public java.util.HashMap getStatistics() {
    java.util.HashMap<String, ConcurrencyStat> result = new java.util.HashMap<String, ConcurrencyStat>();
    for (int i = 0; i < getSize(); ++i) {
      PyConcurrencyEvent event = getEventAt(i);
      String threadId = event.getThreadName();
      if (event.isThreadEvent() && event.getType() == PyConcurrencyEvent.EventType.START) {
        ConcurrencyStat stat = new ConcurrencyStat(event.getTime());
        result.put(threadId, stat);
      } else if (event.getType() == PyConcurrencyEvent.EventType.STOP) {
        ConcurrencyStat stat = new ConcurrencyStat(event.getTime());
        stat.myFinishTime = event.getTime();
      } else if (event.getType() == PyConcurrencyEvent.EventType.ACQUIRE_BEGIN) {
        ConcurrencyStat stat = result.get(threadId);
        stat.myLockCount++;
        stat.myLastAcquireStartTime = event.getTime();
      } else if (event.getType() == PyConcurrencyEvent.EventType.ACQUIRE_END) {
        ConcurrencyStat stat = result.get(threadId);
        stat.myWaitTime += (event.getTime() - stat.myLastAcquireStartTime);
        stat.myLastAcquireStartTime = 0;
      }
    }
    PyConcurrencyEvent lastEvent = getEventAt(getSize() - 1);
    long lastTime = lastEvent.getTime();
    //set last time for stopping on a breakpoint
    for (ConcurrencyStat stat: result.values()) {
      if (stat.myFinishTime == 0) {
        stat.myFinishTime = lastTime;
      }
    }
    return result;
  }

  public int getMaxThread() {
    return myCurrentMaxThread;
  }

  public ArrayList<String> getThreadNames() {
    return new ArrayList<String>(threadNames);
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

  public int getLastEventIndexBeforeMoment(long time) {
    for (int i = 0; i < getSize(); ++i) {
      PyConcurrencyEvent event = getEventAt(i);
      if (event.getTime() >= time) {
        return Math.max(0, i - 1);
      }
    }
    return getSize() - 1;
  }

  public long getDuration() {
    if (getSize() > 0) {
      return getPauseTime() == 0? getEventAt(getSize() - 1).getTime(): (getPauseTime() - getStartTime());
    }
    return 0;
  }

  private class FakeEvent extends PyConcurrencyEvent {
    public FakeEvent(long time) {
      super(time, "", "", false);
    }

    @Override
    public String getEventActionName() {
      return null;
    }

    @Override
    public boolean isThreadEvent() {
      return false;
    }
  }

  public ArrayList<ConcurrencyThreadState> getDrawElementsForRow(int row) {
    return new ArrayList<ConcurrencyThreadState>(myGraphScheme.get(row));
  }

  private ConcurrencyThreadState getThreadStateForEvent(PyConcurrencyEvent event, ConcurrencyThreadState threadState, int index) {
    switch (event.getType()) {
      case CREATE:
        return threadState;
      case START:
        return ConcurrencyThreadState.Run;
      case JOIN:
        return threadState;
      case STOP:
        return ConcurrencyThreadState.Stopped;
      case ACQUIRE_BEGIN:
        if ((myFilterLockId != null) && (event instanceof PyLockEvent)) {
          PyLockEvent lockEvent = (PyLockEvent)event;
          if (lockEvent.getLockId().equals(myFilterLockId)) {
            return ConcurrencyThreadState.LockWaitSelected;
          }
        }
        return ConcurrencyThreadState.LockWait;
      case ACQUIRE_END:
        if ((myFilterLockId != null) && (event instanceof PyLockEvent)) {
          PyLockEvent lockEvent = (PyLockEvent)event;
          if (lockEvent.getLockId().equals(myFilterLockId)) {
            return ConcurrencyThreadState.LockOwnSelected;
          }
        }
        return ConcurrencyThreadState.LockOwn;
      case RELEASE:
        return myGraphAnalyser.getThreadStateAt(index, event.getThreadId());
      default:
        return ConcurrencyThreadState.Stopped;
    }
  }

  private void addRelation(int index, int parent, int child) {
    relations[index][0] = parent;
    relations[index][1] = child;
  }

  public int[] getRelationForRow(int row) {
    return relations[row];
  }

  public interface GraphListener {
    void graphChanged();
  }

  public void registerListener(@NotNull GraphListener logListener) {
    synchronized (myListenersObject) {
      myListeners.add(logListener);
    }
  }

  public void notifyListeners() {
    synchronized (myListenersObject) {
      for (GraphListener logListener : myListeners) {
        logListener.graphChanged();
      }
    }
  }

  public String getThreadIdByIndex(int index) {
    // terrible code! fix it!
    for (Map.Entry<String, Integer> entry: threadIndexToId.entrySet()) {
      if (entry.getValue() == index) {
        return entry.getKey();
      }
    }
    return null;
  }

  public void setFilterLockId(String filterLockId) {
    myFilterLockId = filterLockId;
    createGraph();
    updateGraph();
  }

  private void createGraph() {
    synchronized (myUpdateObject) {
      threadIndexToId = new HashMap<String, Integer>();
      threadNames = new ArrayList<String>();
      myGraphScheme = new ArrayList<ArrayList<ConcurrencyThreadState>>(getSize());
      myThreadCountForRow = new ArrayList<Integer>();
      myGraphAnalyser = new ConcurrencyGraphAnalyser(this);
      myCurrentMaxThread = 0;
      notifyListeners();
    }
  }

  private void updateGraph() {
    synchronized (myUpdateObject) {
      relations = new int[getSize() + 1][2];
      for (int i = myGraphScheme.size(); i < getSize(); ++i) {
        PyConcurrencyEvent event = getEventAt(i);
        String eventThreadId = event.getThreadId();

        if (event.isThreadEvent() && event.getType() == PyConcurrencyEvent.EventType.START) {
          ConcurrencyThreadState element = ConcurrencyThreadState.Run;
          myCurrentMaxThread++;
          threadIndexToId.put(eventThreadId, myCurrentMaxThread - 1);
          threadNames.add(myCurrentMaxThread - 1, event.getThreadName());
          String parentId = ((PyThreadEvent)event).getParentThreadId();
          if (parentId != null) {
            if (threadIndexToId.containsKey(parentId)) {
              int parentNum = threadIndexToId.get(parentId);
              int eventNum = myCurrentMaxThread - 1;
              addRelation(i, parentNum, eventNum);
            }
          }
          myGraphScheme.add(i, new ArrayList<ConcurrencyThreadState>(myCurrentMaxThread));
          for (int j = 0; j < myCurrentMaxThread - 1; ++j) {
            myGraphScheme.get(i).add(j, myGraphScheme.get(i - 1).get(j));
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

          myGraphScheme.add(i, new ArrayList<ConcurrencyThreadState>());
          for (int j = 0; j < myCurrentMaxThread; ++j) {
            if (j != eventThreadIdInt) {
              myGraphScheme.get(i).add(j, myGraphScheme.get(i - 1).get(j));
            } else {
              myGraphScheme.get(i).add(eventThreadIdInt, getThreadStateForEvent(event, myGraphScheme.get(i - 1).get(eventThreadIdInt), i));
            }
          }

          if (event.getType() == PyConcurrencyEvent.EventType.ACQUIRE_BEGIN) {
            HashSet<String> deadlocked = myGraphAnalyser.checkForDeadlocks(i);
            if (deadlocked != null) {
              for (String threadId : deadlocked) {
                myGraphScheme.get(i).set(threadIndexToId.get(threadId), ConcurrencyThreadState.Deadlock);
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
