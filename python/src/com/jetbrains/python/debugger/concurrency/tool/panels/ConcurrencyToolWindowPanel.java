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
import com.jetbrains.python.debugger.concurrency.tool.ConcurrencyTableUtil;
import com.sun.istack.internal.Nullable;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.AdjustmentEvent;
import java.awt.event.AdjustmentListener;
import java.util.ArrayList;
import java.util.List;

public class ConcurrencyToolWindowPanel extends SimpleToolWindowPanel implements Disposable {
  private ConcurrencyGraphModel myGraphModel;
  private ConcurrencyGraphPresentationModel myPresentationModel;
  private String myType;
  private final Project myProject;
  protected JLabel myLabel;
  protected StackTracePanel myStackTracePanel;
  protected JScrollPane myNamesPanel;
  private JScrollPane tableScrollPane;
  private JTable myFixedTable;
  private JTable myStatTable;
  private ActionToolbar myToolbar;
  private JPanel myTablePanel;

  public ConcurrencyToolWindowPanel(boolean vertical, Project project, ConcurrencyGraphModel graphModel, String type) {
    super(vertical);
    myProject = project;
    myGraphModel = graphModel;
    myType = type;
    myPresentationModel = new ConcurrencyGraphPresentationModel(myGraphModel, this);

    myPresentationModel.registerListener(new ConcurrencyGraphPresentationModel.PresentationListener() {
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
    group.add(new ZoomInAction());
    group.add(new ZoomOutAction());
    group.add(new ScrollToTheEndToolbarAction(myPresentationModel));

    final ActionToolbar actionToolBar = ActionManager.getInstance().createActionToolbar("Toolbar", group, false);
    myToolbar = actionToolBar;
    final JPanel buttonsPanel = new JPanel(new BorderLayout());
    buttonsPanel.add(actionToolBar.getComponent(), BorderLayout.CENTER);
    return buttonsPanel;
  }

  private class ScrollToTheEndToolbarAction extends ToggleAction implements DumbAware {
    private ConcurrencyGraphPresentationModel myPresentationModel;

    public ScrollToTheEndToolbarAction(@NotNull final ConcurrencyGraphPresentationModel presentationModel) {
      super();
      myPresentationModel = presentationModel;
      final String message = "Scroll to the end";
      getTemplatePresentation().setDescription(message);
      getTemplatePresentation().setText(message);
      getTemplatePresentation().setIcon(AllIcons.RunConfigurations.Scroll_down);
    }

    @Override
    public boolean isSelected(AnActionEvent e) {
      return myPresentationModel.isScrollToTheEnd();
    }

    @Override
    public void setSelected(AnActionEvent e, boolean state) {
      myPresentationModel.setScrollToTheEnd(state);
    }
  }

  private class ZoomInAction extends AnAction implements DumbAware {
    public ZoomInAction() {
      super("Zoom In", "Zoom In", AllIcons.Graph.ZoomIn);
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      UIUtil.invokeLaterIfNeeded(new Runnable() {
        @Override
        public void run() {
          myPresentationModel.visualSettings.zoomIn();
        }
      });
    }
  }

  private class ZoomOutAction extends AnAction implements DumbAware {
    public ZoomOutAction() {
      super("Zoom Out", "Zoom Out", AllIcons.Graph.ZoomOut);
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      UIUtil.invokeLaterIfNeeded(new Runnable() {
        @Override
        public void run() {
          myPresentationModel.visualSettings.zoomOut();
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
        JScrollBar bar = tableScrollPane.getHorizontalScrollBar();
        myPresentationModel.visualSettings.updateHorizontalScrollbar(bar.getValue(), bar.getVisibleAmount(), bar.getMaximum());
      }
      if (orient == Adjustable.VERTICAL) {
        JScrollBar bar = tableScrollPane.getVerticalScrollBar();
        myPresentationModel.visualSettings.updateVerticalScrollbar(bar.getValue(), bar.getVisibleAmount(), bar.getMaximum());
      }
    }
  }

  public JTable getStatTable() {
    return myStatTable;
  }

  public void setStatTable(JTable statTable) {
    myStatTable = statTable;
  }

  public void setTableScrollPane(JScrollPane tableScrollPane) {
    this.tableScrollPane = tableScrollPane;
  }

  public JScrollPane getTableScrollPane() {
    return tableScrollPane;
  }


  public void setToolbar(ActionToolbar toolbar) {
    this.myToolbar = toolbar;
  }

  public void setFixedTable(JTable fixedTable) {
    this.myFixedTable = fixedTable;
  }

  public int getGraphPaneWidth() {
    return getWidth() - myFixedTable.getWidth() - myToolbar.getComponent().getWidth() - 3;
  }

  public void showStackTrace(@Nullable PyConcurrencyEvent event) {
    List<PyStackFrameInfo> frames = event == null ? new ArrayList<PyStackFrameInfo>(0) : event.getFrames();
    if (myStackTracePanel == null) {
      myStackTracePanel = new StackTracePanel(false, myProject);
      myStackTracePanel.buildStackTrace(frames);
      splitWindow(myStackTracePanel);
    }
    else {
      myStackTracePanel.buildStackTrace(frames);
    }
  }

  public void splitWindow(JComponent component) {
    removeAll();
    JSplitPane graphPanel = new JSplitPane(JSplitPane.VERTICAL_SPLIT, true);
    graphPanel.add(myTablePanel, JSplitPane.LEFT);
    graphPanel.add(component, JSplitPane.RIGHT);
    graphPanel.setDividerLocation(getHeight() * 2 / 3);
    graphPanel.setDividerSize(myPresentationModel.visualSettings.getDividerWidth());
    add(graphPanel, BorderLayout.CENTER);
    setToolbar(createToolbarPanel());
    validate();
    repaint();
  }

  private void initTable() {
    myTablePanel = ConcurrencyTableUtil.createTables(myGraphModel, myPresentationModel, this);
    add(myTablePanel);

    AdjustmentListener listener = new GraphAdjustmentListener();
    tableScrollPane.getHorizontalScrollBar().addAdjustmentListener(listener);
    tableScrollPane.getVerticalScrollBar().addAdjustmentListener(listener);
  }

  public void updateContent() {
    if (myGraphModel.getSize() == 0) {
      myPresentationModel.visualSettings.setNamesPanelWidth(myNamesPanel == null ? myPresentationModel.visualSettings.getNamesPanelWidth() :
                                                            myNamesPanel.getWidth());
      myTablePanel = null;
      myStackTracePanel = null;
      initMessage();
      return;
    }

    if (myTablePanel == null) {
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
