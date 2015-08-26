
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
package com.jetbrains.python.debugger.concurrency.tool.threading;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.util.ui.UIUtil;
import com.jetbrains.python.debugger.concurrency.PyConcurrencyLogManager;
import com.jetbrains.python.debugger.concurrency.tool.ConcurrencyNamesPanel;
import com.jetbrains.python.debugger.concurrency.tool.ConcurrencyPanel;
import com.jetbrains.python.debugger.concurrency.tool.ConcurrencyStatisticsTable;
import com.jetbrains.python.debugger.concurrency.tool.graph.*;

import javax.swing.*;
import java.awt.*;
import java.awt.event.AdjustmentEvent;
import java.awt.event.AdjustmentListener;

public class ThreadingLogToolWindowPanel extends ConcurrencyPanel {
  private final Project myProject;
  private GraphRenderer myRenderer;
  private GraphVisualSettings myVisualSettings;
  private GraphPresentation myGraphPresentation;

  public ThreadingLogToolWindowPanel(Project project) {
    super(false, project);
    myProject = project;
    logManager = PyThreadingLogManagerImpl.getInstance(project);
    myVisualSettings = new GraphVisualSettings();
    myGraphPresentation = new GraphPresentation(new GraphManager(logManager), myVisualSettings);
    myRenderer = new GraphRenderer(myGraphPresentation);

    myGraphPresentation.registerListener(new GraphPresentation.PresentationListener() {
      @Override
      public void graphChanged(int padding, int size) {
        UIUtil.invokeLaterIfNeeded(new Runnable() {
          @Override
          public void run() {
            updateContent();
          }
        });
      }
    });

    UIUtil.invokeLaterIfNeeded(new Runnable() {
      @Override
      public void run() {
        initMessage();
        updateContent();
      }
    });
  }

  @Override
  protected JPanel createToolbarPanel() {
    final DefaultActionGroup group = new DefaultActionGroup();
    group.add(new StatisticsAction());

    final ActionToolbar actionToolBar = ActionManager.getInstance().createActionToolbar("Toolbar", group, false);
    final JPanel buttonsPanel = new JPanel(new BorderLayout());
    buttonsPanel.add(actionToolBar.getComponent(), BorderLayout.CENTER);
    return buttonsPanel;
  }

  private class StatisticsAction extends AnAction implements DumbAware {
    public StatisticsAction() {
      super("Statistical info", "Show threading statistics", AllIcons.ToolbarDecorator.Analyze);
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      final PyConcurrencyLogManager logManager = PyThreadingLogManagerImpl.getInstance(myProject);
      UIUtil.invokeLaterIfNeeded(new Runnable() {
        @Override
        public void run() {
          ConcurrencyStatisticsTable frame = new ConcurrencyStatisticsTable(logManager);
          frame.pack();
          frame.setLocationRelativeTo(null);
          frame.setVisible(true);
        }
      });
    }
  }

  @Override
  public void initMessage() {
    removeAll();
    myLabel = new JLabel();
    myLabel.setHorizontalAlignment(JLabel.CENTER);
    myLabel.setVerticalAlignment(JLabel.CENTER);
    myLabel.setText("<html>The Threading log is empty. <br>" +
                    "Check the box \"Build diagram for concurrent programs\" " +
                    "in Settings | Build, Execution, Deployment | Python debugger</html>");
    add(myLabel);
  }

  private class MyAdjustmentListener implements AdjustmentListener {
    public void adjustmentValueChanged(AdjustmentEvent evt) {
      Adjustable source = evt.getAdjustable();
      int orient = source.getOrientation();
      if (orient == Adjustable.HORIZONTAL) {
        JScrollBar bar = myGraphPane.getHorizontalScrollBar();
        myVisualSettings.updateHorizontalScrollbar(bar.getValue(), bar.getVisibleAmount(), bar.getMaximum());
      }
      if (orient == Adjustable.VERTICAL) {
        JScrollBar bar = myGraphPane.getVerticalScrollBar();
        myVisualSettings.updateVerticalScrollbar(bar.getValue(), bar.getVisibleAmount(), bar.getMaximum());
      }
    }
  }

  private void initGraphPane() {
    myGraphPane = ScrollPaneFactory.createScrollPane(myRenderer);
    AdjustmentListener listener = new MyAdjustmentListener();
    myGraphPane.getHorizontalScrollBar().addAdjustmentListener(listener);
    myGraphPane.getVerticalScrollBar().addAdjustmentListener(listener);
  }

  public void updateContent() {
    if (logManager.getSize() == 0) {
      myVisualSettings.setNamesPanelWidth(myNamesPanel == null? myVisualSettings.getNamesPanelWidth(): myNamesPanel.getWidth());
      myGraphPane = null;
      initMessage();
      return;
    }

    if (myGraphPane == null) {
      myLabel.setVisible(false);
      initGraphPane();
      myNamesPanel = ScrollPaneFactory.createScrollPane(new ConcurrencyNamesPanel(myGraphPresentation));

      JSplitPane p = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, true);
      p.add(myNamesPanel, JSplitPane.LEFT);
      p.add(myGraphPane, JSplitPane.RIGHT);
      p.setDividerLocation(myVisualSettings.getNamesPanelWidth());
      p.setDividerSize(myVisualSettings.getDividerWidth());
      add(p, BorderLayout.CENTER);
      setToolbar(createToolbarPanel());
      validate();
      repaint();
    }
  }

  @Override
  public void dispose() {

  }
}
