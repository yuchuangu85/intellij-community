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

import com.intellij.util.containers.HashSet;
import com.intellij.util.containers.hash.HashMap;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebugSessionAdapter;
import com.jetbrains.python.debugger.PyConcurrencyEvent;
import com.jetbrains.python.debugger.PyLockEvent;
import com.jetbrains.python.debugger.PyThreadEvent;
import com.jetbrains.python.debugger.concurrency.tool.ConcurrencyGraphAnalyser;
import com.jetbrains.python.debugger.concurrency.tool.ConcurrencyStat;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.*;
import java.util.List;

public class ConcurrencyGraphModel {
  private @NotNull List<PyConcurrencyEvent> myLog;
  private final Object myLogObject = new Object();
  private ArrayList<ArrayList<ConcurrencyGraphElement>> myGraphScheme;
  private Map<String, Integer> threadIndexToId;
  private ArrayList<String> threadNames;
  private final Object myUpdateObject = new Object();
  private int myCurrentMaxThread = 0;
  private @Nullable HashMap<Integer, Point> relations;
  private @NotNull final List<GraphListener> myListeners = new ArrayList<GraphListener>();
  private final Object myListenersObject = new Object();
  private ConcurrencyGraphAnalyser myGraphAnalyser;
  private @Nullable XDebugSession lastSession;
  private String myFilterLockId;
  private Timer myTimer;
  private long myStartTime; //millis
  private long myFinishTime; //millis
  private long myTimerPeriod = 10; //millis
  private int myTimeCursor; //px
  private boolean myLastSessionStopped;
  private HashMap<String, ConcurrencyStat> myStatInfo;
  private int myLastEventForStat;

  public ConcurrencyGraphModel() {
    myLog = new ArrayList<PyConcurrencyEvent>();
  }

  private void addSessionListener() {
    if (lastSession == null) {
      return;
    }
    lastSession.addSessionListener(new XDebugSessionAdapter() {
      @Override
      public void sessionStopped() {
        myFinishTime = System.currentTimeMillis();
        myLastSessionStopped = true;
        updateGraph(myGraphScheme.size());
        notifyListeners();
      }
    });
  }

  private int recordToLog(PyConcurrencyEvent event) {
    int i = myLog.size() - 1;
    while ((i >= 0) && (myLog.get(i).getTime() > event.getTime())) {
      i--;
    }
    myLog.add(i + 1, event);
    return i + 1;
  }

  public void recordEvent(@NotNull XDebugSession debugSession, @Nullable PyConcurrencyEvent event) {
    synchronized (myLogObject) {
      if (((lastSession == null) || (debugSession != lastSession)) && event == null) {
        lastSession = debugSession;
        myLog = new ArrayList<PyConcurrencyEvent>();
        addSessionListener();
        createGraph();
        return;
      }
      if ((event != null) && (event.getTime() == 0)) {
        myStartTime = System.currentTimeMillis();
      }
      updateGraph(recordToLog(event));
    }
  }

  @NotNull
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

  @NotNull
  public String getStringRepresentation() {
    StringBuilder resultBuilder = new StringBuilder();
    resultBuilder.append("<html>Size: ").append(myLog.size()).append("<br>");
    for (PyConcurrencyEvent event : myLog) {
      resultBuilder.append(event.toString());
    }
    resultBuilder.append("</html>");
    return resultBuilder.toString();
  }

  private long getStartTime() {
    return myStartTime;
  }

  @NotNull
  public ConcurrencyStat getStatisticsByThreadId(@NotNull String threadId) {
    ConcurrencyStat result = myStatInfo.get(threadId);
    if (result.getFinishTime() == 0) {
      result.setPauseTime(getEventAt(getSize() - 1).getTime());
    }
    return result;
  }

  private void updateStatistics() {
    for (int i = myLastEventForStat; i < getSize(); ++i) {
      PyConcurrencyEvent event = getEventAt(i);
      String threadId = event.getThreadId();
      if (event.isThreadEvent() && event.getType() == PyConcurrencyEvent.EventType.START) {
        ConcurrencyStat stat = new ConcurrencyStat(event.getTime());
        myStatInfo.put(threadId, stat);
      }
      else if (event.getType() == PyConcurrencyEvent.EventType.STOP) {
        myStatInfo.get(threadId).setFinishTime(event.getTime());
      }
      else if (event.getType() == PyConcurrencyEvent.EventType.ACQUIRE_BEGIN) {
        myStatInfo.get(threadId).setLastAcquireStartTime(event.getTime());
      }
      else if (event.getType() == PyConcurrencyEvent.EventType.ACQUIRE_END) {
        ConcurrencyStat stat = myStatInfo.get(threadId);
        stat.incWaitTime(event.getTime() - stat.getLastAcquireStartTime());
        stat.setLastAcquireStartTime(0);
      }
    }
  }

  public int getMaxThread() {
    return myCurrentMaxThread;
  }

  @NotNull
  public ArrayList<String> getThreadNames() {
    return threadNames != null ? new ArrayList<String>(threadNames) : new ArrayList<String>();
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

  private static class FakeEvent extends PyConcurrencyEvent {
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

  @NotNull
  public ArrayList<ConcurrencyGraphElement> getDrawElementsForRow(int row) {
    synchronized (myUpdateObject) {
      if ((myGraphScheme == null) || (row >= myGraphScheme.size())) {
        return new ArrayList<ConcurrencyGraphElement>();
      }
      return new ArrayList<ConcurrencyGraphElement>(myGraphScheme.get(row));
    }
  }

  @NotNull
  private ConcurrencyGraphElement getThreadStateForEvent(@NotNull PyConcurrencyEvent event, @NotNull ConcurrencyThreadState threadState,
                                                         int index) {
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
        return myGraphAnalyser != null ? myGraphAnalyser.getThreadStateAt(index, event.getThreadId()) :
               new ConcurrencyGraphElement(threadState, index);
      default:
        return new ConcurrencyGraphElement(ConcurrencyThreadState.Stopped, index);
    }
  }

  private void addRelation(int index, int parent, int child) {
    if (relations == null) {
      relations = new HashMap<Integer, Point>();
    }
    relations.put(index, new Point(parent, child));
  }

  @Nullable
  public Point getRelationForRow(int row) {
    if (relations == null) {
      return null;
    }
    Point relation = relations.get(row);
    if ((relation == null) || (relation.x == 0) && (relation.y == 0)) {
      return null;
    }
    return relation;
  }

  public interface GraphListener {
    void graphChanged();
  }

  public void registerListener(@NotNull GraphListener logListener) {
    synchronized (myListenersObject) {
      myListeners.add(logListener);
    }
  }

  private void notifyListeners() {
    synchronized (myListenersObject) {
      for (GraphListener logListener : myListeners) {
        logListener.graphChanged();
      }
    }
  }

  @Nullable
  public ConcurrencyThreadState getThreadStateForEvent(int eventId, int threadIndex) {
    return myGraphScheme != null ? myGraphScheme.get(eventId).get(threadIndex).getThreadState() : null;
  }

  @Nullable
  public String getThreadIdByIndex(int index) {
    // terrible code! fix it!
    if (threadIndexToId == null) {
      return null;
    }
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
    updateGraph(myGraphScheme.size());
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
      myGraphAnalyser = new ConcurrencyGraphAnalyser(this);
      myCurrentMaxThread = 0;
      relations = new HashMap<Integer, Point>();
      myLastSessionStopped = false;
      myTimeCursor = 0;
      myStatInfo = new HashMap<String, ConcurrencyStat>();
      myLastEventForStat = 0;
      startTimer();
      notifyListeners();
    }
  }

  private void updateGraph(int indexInserted) {
    synchronized (myUpdateObject) {
      int oldSize = myGraphScheme.size();
      if ((indexInserted < oldSize) && (myCurrentMaxThread != 0)) {
        myCurrentMaxThread = myGraphScheme.get(indexInserted - 1).size();
        for (int i = indexInserted; i < myGraphScheme.size(); ++i) {
          if ((relations != null) && (relations.containsKey(i))) {
            relations.remove(i);
          }
        }
      }
      for (int i = indexInserted; i < myLog.size(); ++i) {
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
                                       getThreadStateForEvent(event, myGraphScheme.get(i - 1).get(eventThreadIdInt).getThreadState(), i));
            }
          }

          if (event.getType() == PyConcurrencyEvent.EventType.ACQUIRE_BEGIN) {
            int indexForCheck = indexInserted < oldSize ? 0 : i;
            HashSet<String> deadlocked = myGraphAnalyser.checkForDeadlocks(indexForCheck);
            if (deadlocked != null) {
              for (String threadId : deadlocked) {
                myGraphScheme.get(i).set(threadIndexToId.get(threadId), new ConcurrencyGraphElement(ConcurrencyThreadState.Deadlock, i));
              }
            }
          }
        }
      }
      updateStatistics();
    }
  }
}
