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

import java.awt.*;
import java.util.*;
import java.util.List;

public class ConcurrencyGraphModel {
  protected Project myProject;
  private List<PyConcurrencyEvent> myLog;
  private final Object myLogObject = new Object();
  private ArrayList<ArrayList<ConcurrencyGraphElement>> myGraphScheme;
  private ArrayList<Integer> myThreadCountForRow;
  private Map<String, Integer> threadIndexToId;
  private ArrayList<String> threadNames;
  private final Object myUpdateObject = new Object();
  private int myCurrentMaxThread = 0;
  private HashMap<Integer, Point> relations;
  private List<GraphListener> myListeners = new ArrayList<GraphListener>();
  private final Object myListenersObject = new Object();
  private ConcurrencyGraphAnalyser myGraphAnalyser;
  private XDebugSession lastSession;
  private String myFilterLockId;
  private Timer myTimer;
  private long myStartTime; //millis
  private long myFinishTime; //millis
  private long myTimerPeriod = 10; //millis
  private int myTimeCursor; //px
  private boolean myLastSessionStopped;

  public ConcurrencyGraphModel(Project project) {
    myProject = project;
    myLog = new ArrayList<PyConcurrencyEvent>();
  }

  public void addSessionListener() {
    lastSession.addSessionListener(new XDebugSessionListener() {
      @Override
      public void sessionPaused() {
      }

      @Override
      public void sessionResumed() {
      }

      @Override
      public void sessionStopped() {
        myFinishTime = System.currentTimeMillis();
        myLastSessionStopped = true;
        updateGraph();
        notifyListeners();
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
    if (index == myLog.size()) {
      PyConcurrencyEvent lastEvent = myLog.get(myLog.size() - 1);
      // add a fake event with current time
      return new FakeEvent((myFinishTime - getStartTime()) * 1000, lastEvent); // convert from millis to microseconds
    }
    return myLog.get(index);
  }

  public int getSize() {
    // we add a fake event with current time
    return myLog.size() > 0 ? myLog.size() + 1 : myLog.size();
  }

  public String getStringRepresentation() {
    StringBuilder resultBuilder = new StringBuilder();
    resultBuilder.append("<html>Size: ").append(myLog.size()).append("<br>");
    for (PyConcurrencyEvent event : myLog) {
      resultBuilder.append(event.toString());
    }
    resultBuilder.append("</html>");
    return resultBuilder.toString();
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
      }
      else if (event.getType() == PyConcurrencyEvent.EventType.STOP) {
        ConcurrencyStat stat = new ConcurrencyStat(event.getTime());
        stat.myFinishTime = event.getTime();
      }
      else if (event.getType() == PyConcurrencyEvent.EventType.ACQUIRE_BEGIN) {
        ConcurrencyStat stat = result.get(threadId);
        stat.myLockCount++;
        stat.myLastAcquireStartTime = event.getTime();
      }
      else if (event.getType() == PyConcurrencyEvent.EventType.ACQUIRE_END) {
        ConcurrencyStat stat = result.get(threadId);
        stat.myWaitTime += (event.getTime() - stat.myLastAcquireStartTime);
        stat.myLastAcquireStartTime = 0;
      }
    }
    PyConcurrencyEvent lastEvent = getEventAt(getSize() - 1);
    long lastTime = lastEvent.getTime();
    //set last time for stopping on a breakpoint
    for (ConcurrencyStat stat : result.values()) {
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
      return (myFinishTime - getStartTime()) * 1000; // convert to microseconds
    }
    return 0;
  }

  private class FakeEvent extends PyConcurrencyEvent {
    public FakeEvent(long time, PyConcurrencyEvent previousEvent) {
      super(time, previousEvent.getThreadId(), previousEvent.getThreadName(), previousEvent.isThreadEvent());
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

  public ArrayList<ConcurrencyGraphElement> getDrawElementsForRow(int row) {
    return new ArrayList<ConcurrencyGraphElement>(myGraphScheme.get(row));
  }

  private ConcurrencyGraphElement getThreadStateForEvent(PyConcurrencyEvent event, ConcurrencyThreadState threadState, int index) {
    switch (event.getType()) {
      case CREATE:
        return new ConcurrencyGraphElement(threadState, index);
      case START:
        return new ConcurrencyGraphElement(ConcurrencyThreadState.Run, index);
      case JOIN:
        return new ConcurrencyGraphElement(threadState, index);
      case STOP:
        return new ConcurrencyGraphElement(ConcurrencyThreadState.Stopped, index);
      case ACQUIRE_BEGIN:
        if ((myFilterLockId != null) && (event instanceof PyLockEvent)) {
          PyLockEvent lockEvent = (PyLockEvent)event;
          if (lockEvent.getLockId().equals(myFilterLockId)) {
            return new ConcurrencyGraphElement(ConcurrencyThreadState.LockWaitSelected, index);
          }
        }
        return new ConcurrencyGraphElement(ConcurrencyThreadState.LockWait, index);
      case ACQUIRE_END:
        if ((myFilterLockId != null) && (event instanceof PyLockEvent)) {
          PyLockEvent lockEvent = (PyLockEvent)event;
          if (lockEvent.getLockId().equals(myFilterLockId)) {
            return new ConcurrencyGraphElement(ConcurrencyThreadState.LockOwnSelected, index);
          }
        }
        return new ConcurrencyGraphElement(ConcurrencyThreadState.LockOwn, index);
      case RELEASE:
        return myGraphAnalyser.getThreadStateAt(index, event.getThreadId());
      default:
        return new ConcurrencyGraphElement(ConcurrencyThreadState.Stopped, index);
    }
  }

  private void addRelation(int index, int parent, int child) {
    relations.put(index, new Point(parent, child));
  }

  public Point getRelationForRow(int row) {
    return relations.get(row);
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
    for (Map.Entry<String, Integer> entry : threadIndexToId.entrySet()) {
      if (entry.getValue() == index) {
        return entry.getKey();
      }
    }
    return null;
  }

  public int getTimeCursor() {
    return myTimeCursor;
  }

  public void setTimeCursor(int timeCursor) {
    myTimeCursor = timeCursor;
  }

  public void setFilterLockId(String filterLockId) {
    myFilterLockId = filterLockId;
    createGraph();
    updateGraph();
  }

  public void setTimerPeriod(long timerPeriod) {
    myTimerPeriod = timerPeriod;
  }

  private void startTimer() {
    myTimer = new Timer();
    TimerTask task = new TimerTask() {
      @Override
      public void run() {
        if (myLastSessionStopped) {
          myTimer.cancel();
          notifyListeners();
          return;
        }
        myFinishTime = System.currentTimeMillis();
        notifyListeners();
      }
    };
    myTimer.schedule(task, new Date(), myTimerPeriod);
  }

  private void createGraph() {
    synchronized (myUpdateObject) {
      threadIndexToId = new HashMap<String, Integer>();
      threadNames = new ArrayList<String>();
      myGraphScheme = new ArrayList<ArrayList<ConcurrencyGraphElement>>(getSize());
      myThreadCountForRow = new ArrayList<Integer>();
      myGraphAnalyser = new ConcurrencyGraphAnalyser(this);
      myCurrentMaxThread = 0;
      relations = new HashMap<Integer, Point>();
      myLastSessionStopped = false;
      myTimeCursor = 0;
      startTimer();
      notifyListeners();
    }
  }

  private void updateGraph() {
    synchronized (myUpdateObject) {
      for (int i = myGraphScheme.size(); i < myLog.size(); ++i) {
        PyConcurrencyEvent event = getEventAt(i);
        String eventThreadId = event.getThreadId();

        if (event.isThreadEvent() && event.getType() == PyConcurrencyEvent.EventType.START) {
          ConcurrencyGraphElement element = new ConcurrencyGraphElement(ConcurrencyThreadState.Run, i);
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
          myGraphScheme.add(i, new ArrayList<ConcurrencyGraphElement>(myCurrentMaxThread));
          for (int j = 0; j < myCurrentMaxThread - 1; ++j) {
            myGraphScheme.get(i).add(j, myGraphScheme.get(i - 1).get(j));
          }
          myGraphScheme.get(i).add(myCurrentMaxThread - 1, element);
        }
        else {
          int eventThreadIdInt = threadIndexToId.containsKey(eventThreadId) ? threadIndexToId.get(eventThreadId) : 0;
          if (event instanceof PyThreadEvent) {
            String parentId = ((PyThreadEvent)event).getParentThreadId();
            if ((parentId != null) && (threadIndexToId.containsKey(parentId))) {
              int parentNum = threadIndexToId.get(((PyThreadEvent)event).getParentThreadId());
              addRelation(i, parentNum, eventThreadIdInt);
            }
          }

          myGraphScheme.add(i, new ArrayList<ConcurrencyGraphElement>());
          for (int j = 0; j < myCurrentMaxThread; ++j) {
            if (j != eventThreadIdInt) {
              myGraphScheme.get(i).add(j, myGraphScheme.get(i - 1).get(j));
            }
            else {
              myGraphScheme.get(i).add(eventThreadIdInt,
                                       getThreadStateForEvent(event, myGraphScheme.get(i - 1).get(eventThreadIdInt).threadState, i));
            }
          }

          if (event.getType() == PyConcurrencyEvent.EventType.ACQUIRE_BEGIN) {
            HashSet<String> deadlocked = myGraphAnalyser.checkForDeadlocks(i);
            if (deadlocked != null) {
              for (String threadId : deadlocked) {
                myGraphScheme.get(i).set(threadIndexToId.get(threadId), new ConcurrencyGraphElement(ConcurrencyThreadState.Deadlock, i));
              }
            }
          }
        }
        myThreadCountForRow.add(i, myCurrentMaxThread);
      }
    }
  }
}
