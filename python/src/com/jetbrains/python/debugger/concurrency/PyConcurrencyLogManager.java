
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
package com.jetbrains.python.debugger.concurrency;

import com.intellij.openapi.project.Project;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebugSessionListener;
import com.jetbrains.python.debugger.PyConcurrencyEvent;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public abstract class PyConcurrencyLogManager {
  private List<PyConcurrencyEvent> myLog;
  private final Object myLogObject = new Object();
  private List<LogListener> myListeners = new ArrayList<LogListener>();
  private XDebugSession lastSession;
  protected Project myProject;

  public PyConcurrencyLogManager(Project project) {
    myProject = project;
    myLog = new ArrayList<PyConcurrencyEvent>();
  }

  public Integer getSize() {
    synchronized (myLogObject) {
      return myLog.size();
    }
  }

  public PyConcurrencyEvent getEventAt(int index) {
    synchronized (myLogObject) {
      return myLog.get(index);
    }
  }

  public abstract HashMap getStatistics();

  public String getStringRepresentation() {
    StringBuilder resultBuilder = new StringBuilder();
    resultBuilder.append("<html>Size: ").append(myLog.size()).append("<br>");
    for (PyConcurrencyEvent event: myLog) {
      resultBuilder.append(event.toString());
    }
    resultBuilder.append("</html>");
    return resultBuilder.toString();
  }

  public void recordEvent(@NotNull XDebugSession debugSession, PyConcurrencyEvent event) {
    synchronized (myLogObject) {
      if (((lastSession == null) || (debugSession != lastSession)) && event == null) {
        lastSession = debugSession;
        myLog = new ArrayList<PyConcurrencyEvent>();
        addSessionListener();
        return;
      }
      myLog.add(event);
      notifyListeners();
    }
  }

  public void addSessionListener() {
    lastSession.addSessionListener(new XDebugSessionListener() {
      @Override
      public void sessionPaused() {
        notifyListeners();
      }

      @Override
      public void sessionResumed() {
      }

      @Override
      public void sessionStopped() {
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

  public interface LogListener {
    void logChanged();
  }

  public void registerListener(@NotNull LogListener logListener) {
    synchronized (myLogObject) {
      myListeners.add(logListener);
    }
  }

  public void notifyListeners() {
    synchronized (myLogObject) {
      for (LogListener logListener : myListeners) {
        logListener.logChanged();
      }
    }
  }

}
