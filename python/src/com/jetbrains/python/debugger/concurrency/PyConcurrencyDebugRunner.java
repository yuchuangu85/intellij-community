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

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.RunProfile;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowAnchor;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebugSessionAdapter;
import com.intellij.xdebugger.XDebuggerManager;
import com.intellij.xdebugger.breakpoints.XBreakpoint;
import com.jetbrains.python.debugger.PyDebugRunner;
import com.jetbrains.python.debugger.PyDebuggerOptionsProvider;
import com.jetbrains.python.debugger.concurrency.tool.ConcurrencyLogToolWindowFactory;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;

public class PyConcurrencyDebugRunner extends PyDebugRunner {
  public static final String WINDOW_ID = "Concurrent Activities Diagram";
  private Project myProject;
  private ArrayList<XBreakpoint> myDisabledBreakpoints;

  @Override
  protected XDebugSession createSession(@NotNull RunProfileState state, @NotNull ExecutionEnvironment environment)
    throws ExecutionException {
    myProject = environment.getProject();
    PyDebuggerOptionsProvider.getInstance(myProject).setSaveThreadingLog(true);
    XDebugSession session = super.createSession(state, environment);
    final ToolWindowManager toolWindowManager = ToolWindowManager.getInstance(myProject);

    disableBreakpoints();
    session.addSessionListener(new XDebugSessionAdapter() {
      @Override
      public void sessionStopped() {
        ApplicationManager.getApplication().runReadAction(new Runnable() {
          @Override
          public void run() {
            for (XBreakpoint breakpoint : myDisabledBreakpoints) {
              breakpoint.setEnabled(true);
            }
          }
        });
        ApplicationManager.getApplication().invokeLater(new Runnable() {
          @Override
          public void run() {
            toolWindowManager.getToolWindow(ToolWindowId.DEBUG).setAvailable(true, null);
          }
        });
      }
    });

    PyDebuggerOptionsProvider.getInstance(myProject).setSaveThreadingLog(false);
    PyConcurrencyService.getInstance(myProject).getThreadingInstance().recordEvent(session, null);
    PyConcurrencyService.getInstance(myProject).getAsyncioInstance().recordEvent(session, null);
    initToolWindow();
    return session;
  }

  private void disableBreakpoints() {
    myDisabledBreakpoints = new ArrayList<XBreakpoint>();
    final XDebuggerManager manager = XDebuggerManager.getInstance(myProject);
    for (XBreakpoint breakpoint : manager.getBreakpointManager().getAllBreakpoints()) {
      if (breakpoint.isEnabled()) {
        breakpoint.setEnabled(false);
        myDisabledBreakpoints.add(breakpoint);
      }
    }
  }

  private void initToolWindow() {
    final ToolWindowManager toolWindowManager = ToolWindowManager.getInstance(myProject);
    toolWindowManager.getToolWindow(ToolWindowId.DEBUG).setAvailable(false, null);
    final ToolWindow toolWindow = toolWindowManager.getToolWindow(WINDOW_ID);
    if (toolWindow == null) {
      toolWindowManager.invokeLater(new Runnable() {
        @Override
        public void run() {
          createToolWindow(myProject, toolWindowManager).show(null);
        }
      });
    }
    else {
      toolWindow.show(null);
    }
  }

  private static ToolWindow createToolWindow(Project project, ToolWindowManager toolWindowManager) {
    ToolWindow toolWindow = toolWindowManager.registerToolWindow(WINDOW_ID, false, ToolWindowAnchor.BOTTOM);
    toolWindow.setIcon(AllIcons.Toolwindows.ToolWindowStructure);
    new ConcurrencyLogToolWindowFactory().createToolWindowContent(project, toolWindow);
    return toolWindow;
  }

  @Override
  public boolean canRun(@NotNull String executorId, @NotNull RunProfile profile) {
    return executorId.equals(PyConcurrencyExecutor.ID);
  }
}
