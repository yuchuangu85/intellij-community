
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
package com.jetbrains.python.debugger.concurrency.tool.panels;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.SimpleToolWindowPanel;
import com.intellij.util.ui.UIUtil;
import com.jetbrains.python.debugger.PyConcurrencyEvent;
import com.jetbrains.python.debugger.PyStackFrameInfo;
import com.jetbrains.python.debugger.concurrency.model.ConcurrencyGraphModel;
import com.jetbrains.python.debugger.concurrency.model.ConcurrencyGraphPresentationModel;
import com.jetbrains.python.debugger.concurrency.tool.ConcurrencyStatisticsTable;
import com.jetbrains.python.debugger.concurrency.tool.ConcurrencyTableUtil;
import com.sun.istack.internal.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.AdjustmentEvent;
import java.awt.event.AdjustmentListener;
import java.util.ArrayList;
import java.util.List;

public class ConcurrencyToolWindowPanel extends SimpleToolWindowPanel implements Disposable {
  private ConcurrencyGraphModel myGraphModel;
  private ConcurrencyGraphPresentationModel myGraphPresentation;
  private String myType;
  private final Project myProject;
  protected JLabel myLabel;
  protected StackTracePanel myStackTracePanel;
  protected JScrollPane myNamesPanel;
  private JScrollPane myTableScrollPane;

  public ConcurrencyToolWindowPanel(boolean vertical, Project project, ConcurrencyGraphModel graphModel, String type) {
    super(vertical);
    myProject = project;
    myGraphModel = graphModel;
    myGraphPresentation = new ConcurrencyGraphPresentationModel(myGraphModel);
    myType = type;

    myGraphPresentation.registerListener(new ConcurrencyGraphPresentationModel.PresentationListener() {
      @Override
      public void graphChanged(int padding) {
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

  protected JPanel createToolbarPanel() {
    final DefaultActionGroup group = new DefaultActionGroup();
    group.add(new StatisticsAction());
    group.add(new ScaleIncrementAction());
    group.add(new ScaleDecrementAction());

    final ActionToolbar actionToolBar = ActionManager.getInstance().createActionToolbar("Toolbar", group, false);
    final JPanel buttonsPanel = new JPanel(new BorderLayout());
    buttonsPanel.add(actionToolBar.getComponent(), BorderLayout.CENTER);
    return buttonsPanel;
  }

  private class StatisticsAction extends AnAction implements DumbAware {
    public StatisticsAction() {
      super("Statistical info", "Show " + myType + " statistics", AllIcons.ToolbarDecorator.Analyze);
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      final ConcurrencyGraphModel graphModel = myGraphModel;
      UIUtil.invokeLaterIfNeeded(new Runnable() {
        @Override
        public void run() {
          ConcurrencyStatisticsTable frame = new ConcurrencyStatisticsTable(graphModel);
          frame.pack();
          frame.setLocationRelativeTo(null);
          frame.setVisible(true);
        }
      });
    }
  }

  private class ScaleIncrementAction extends AnAction implements DumbAware {
    public ScaleIncrementAction() {
      super("Zoom In", "Zoom In", AllIcons.Actions.SortAsc);
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      UIUtil.invokeLaterIfNeeded(new Runnable() {
        @Override
        public void run() {
          myGraphPresentation.visualSettings.increaseScale();
        }
      });
    }
  }

  private class ScaleDecrementAction extends AnAction implements DumbAware {
    public ScaleDecrementAction() {
      super("Zoom Out", "Zoom Out", AllIcons.Actions.SortDesc);
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      final ConcurrencyGraphModel graphModel = myGraphModel;
      UIUtil.invokeLaterIfNeeded(new Runnable() {
        @Override
        public void run() {
          myGraphPresentation.visualSettings.decreaseScale();
        }
      });
    }
  }

  public void initMessage() {
    removeAll();
    myLabel = new JLabel();
    myLabel.setHorizontalAlignment(SwingConstants.CENTER);
    myLabel.setVerticalAlignment(SwingConstants.CENTER);
    myLabel.setText("<html>The " + myType + " log is empty. <br>" +
                    "Check the box \"Build diagram for concurrent programs\" " +
                    "in Settings | Build, Execution, Deployment | Python debugger</html>");
    add(myLabel);
  }

  private class GraphAdjustmentListener implements AdjustmentListener {
    public void adjustmentValueChanged(AdjustmentEvent evt) {
      Adjustable source = evt.getAdjustable();
      int orient = source.getOrientation();
      if (orient == Adjustable.HORIZONTAL) {
        JScrollBar bar = myTableScrollPane.getHorizontalScrollBar();
        myGraphPresentation.visualSettings.updateHorizontalScrollbar(bar.getValue(), bar.getVisibleAmount(), bar.getMaximum());
      }
      if (orient == Adjustable.VERTICAL) {
        JScrollBar bar = myTableScrollPane.getVerticalScrollBar();
        myGraphPresentation.visualSettings.updateVerticalScrollbar(bar.getValue(), bar.getVisibleAmount(), bar.getMaximum());
      }
    }
  }

  public void showStackTrace(@Nullable PyConcurrencyEvent event) {
    List<PyStackFrameInfo> frames = event == null ? new ArrayList<PyStackFrameInfo>(0) : event.getFrames();
    if (myStackTracePanel == null) {
      myStackTracePanel = new StackTracePanel(false, myProject);
      myStackTracePanel.buildStackTrace(frames);
      splitWindow(myStackTracePanel);
    } else {
      myStackTracePanel.buildStackTrace(frames);
    }
  }

  public void splitWindow(JComponent component) {
    removeAll();
    JSplitPane graphPanel = new JSplitPane(JSplitPane.VERTICAL_SPLIT, true);
    graphPanel.add(myTableScrollPane, JSplitPane.LEFT);
    graphPanel.add(component, JSplitPane.RIGHT);
    graphPanel.setDividerLocation(getHeight() * 2 / 3);
    graphPanel.setDividerSize(myGraphPresentation.visualSettings.getDividerWidth());
    add(graphPanel, BorderLayout.CENTER);
    setToolbar(createToolbarPanel());
    validate();
    repaint();
  }

  private void initTable() {
    myTableScrollPane = ConcurrencyTableUtil.createTables(myGraphModel, myGraphPresentation, this);
    add(myTableScrollPane);

    AdjustmentListener listener = new GraphAdjustmentListener();
    myTableScrollPane.getHorizontalScrollBar().addAdjustmentListener(listener);
    myTableScrollPane.getVerticalScrollBar().addAdjustmentListener(listener);
  }

  public void updateContent() {
    if (myGraphModel.getSize() == 0) {
      myGraphPresentation.visualSettings.setNamesPanelWidth(myNamesPanel == null? myGraphPresentation.visualSettings.getNamesPanelWidth():
                                                                 myNamesPanel.getWidth());
      myTableScrollPane = null;
      myStackTracePanel = null;
      initMessage();
      return;
    }

    if (myTableScrollPane == null) {
      myLabel.setVisible(false);
      initTable();
      setToolbar(createToolbarPanel());
      validate();
      repaint();
    }
  }

  @Override
  public void dispose() {

  }
}
