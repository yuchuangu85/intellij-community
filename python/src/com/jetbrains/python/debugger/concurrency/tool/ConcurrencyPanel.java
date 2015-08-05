
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
package com.jetbrains.python.debugger.concurrency.tool;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.SimpleToolWindowPanel;
import com.jetbrains.python.debugger.PyConcurrencyEvent;
import com.jetbrains.python.debugger.concurrency.PyConcurrencyLogManager;

import javax.swing.*;
import java.awt.*;

public abstract class ConcurrencyPanel extends SimpleToolWindowPanel implements Disposable {
  private final Project myProject;
  protected PyConcurrencyLogManager logManager;
  protected JLabel myLabel;
  protected StackTracePanel myStackTracePanel;
  protected JScrollPane myPane;

  public ConcurrencyPanel(boolean vertical, Project project) {
    super(vertical);
    myProject = project;
  }

  public abstract void initMessage();

  protected abstract JPanel createToolbarPanel();

  public void showStackTrace(PyConcurrencyEvent event) {
    if (myStackTracePanel == null) {
      myStackTracePanel = new StackTracePanel(false, myProject);
      myStackTracePanel.buildStackTrace(event.getFrames());
      splitWindow(myStackTracePanel);
    } else {
      myStackTracePanel.buildStackTrace(event.getFrames());
    }
  }

  public void splitWindow(JComponent component) {
    removeAll();
    JSplitPane p = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, true);
    p.add(myPane, JSplitPane.LEFT);
    p.add(component, JSplitPane.RIGHT);
    p.setDividerLocation((int)getSize().getWidth() * 2 / 3);
    add(p, BorderLayout.CENTER);
    setToolbar(createToolbarPanel());
    validate();
    repaint();
  }

}
