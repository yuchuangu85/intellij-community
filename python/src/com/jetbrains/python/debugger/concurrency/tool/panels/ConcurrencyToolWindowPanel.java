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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.AdjustmentEvent;
import java.awt.event.AdjustmentListener;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.util.ArrayList;
import java.util.List;

public class ConcurrencyToolWindowPanel extends SimpleToolWindowPanel implements Disposable {
  private final @NotNull ConcurrencyGraphModel myGraphModel;
  private final @NotNull ConcurrencyGraphPresentationModel myPresentationModel;
  private final @NotNull Project myProject;
  private final String myType;
  private JLabel myLabel;
  private @Nullable ConcurrencyStackTracePanel myStackTracePanel;
  private @Nullable JScrollPane tableScrollPane;
  private @Nullable JTable myFixedTable;
  private @Nullable JTable myStatTable;
  private @Nullable JTable myNamesTable;
  private @Nullable ActionToolbar myToolbar;
  private @Nullable JPanel myTablePanel;
  private @Nullable JPanel myNotes;

  public ConcurrencyToolWindowPanel(@NotNull Project project, @NotNull ConcurrencyGraphModel graphModel, String type) {
    super(false);
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

  private JPanel createToolbarPanel() {
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

  private static class ScrollToTheEndToolbarAction extends ToggleAction implements DumbAware {
    private final ConcurrencyGraphPresentationModel myPresentationModel;

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
      myPresentationModel.getVisualSettings().zoomIn();
    }
  }

  private class ZoomOutAction extends AnAction implements DumbAware {
    public ZoomOutAction() {
      super("Zoom Out", "Zoom Out", AllIcons.Graph.ZoomOut);
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      myPresentationModel.getVisualSettings().zoomOut();
    }
  }

  private void initMessage() {
    removeAll();
    myLabel = new JLabel();
    myLabel.setHorizontalAlignment(SwingConstants.CENTER);
    myLabel.setVerticalAlignment(SwingConstants.CENTER);
    myLabel.setText("<html>The " + myType + " log is empty </html>");
    add(myLabel);
  }

  private class GraphAdjustmentListener implements AdjustmentListener {
    public void adjustmentValueChanged(AdjustmentEvent evt) {
      Adjustable source = evt.getAdjustable();
      int orient = source.getOrientation();
      if ((orient == Adjustable.HORIZONTAL) && (tableScrollPane != null)) {
        JScrollBar bar = tableScrollPane.getHorizontalScrollBar();
        myPresentationModel.getVisualSettings().updateHorizontalScrollbar(bar.getValue(), bar.getVisibleAmount(), bar.getMaximum());
      }
      if (orient == Adjustable.VERTICAL) {
        JScrollBar bar = tableScrollPane.getVerticalScrollBar();
        myPresentationModel.getVisualSettings().updateVerticalScrollbar(bar.getValue(), bar.getVisibleAmount(), bar.getMaximum());
      }
    }
  }

  @Nullable
  public JTable getStatTable() {
    return myStatTable;
  }

  public void setStatTable(@NotNull JTable statTable) {
    myStatTable = statTable;
  }

  @Nullable
  public JTable getNamesTable() {
    return myNamesTable;
  }

  public void setNamesTable(@Nullable JTable namesTable) {
    myNamesTable = namesTable;
  }

  public void setTableScrollPane(@NotNull JScrollPane tableScrollPane) {
    this.tableScrollPane = tableScrollPane;
  }

  @Nullable
  public JScrollPane getTableScrollPane() {
    return tableScrollPane;
  }


  public void setToolbar(@NotNull ActionToolbar toolbar) {
    myToolbar = toolbar;
  }

  public void setFixedTable(@NotNull JTable fixedTable) {
    this.myFixedTable = fixedTable;
  }

  public int getGraphPaneWidth() {
    int result = 0;
    result += myFixedTable == null ? 0 : myFixedTable.getWidth();
    result += myStatTable == null ? 0 : myStatTable.getWidth();
    result += myToolbar == null ? 0 : myToolbar.getComponent().getWidth();
    return Math.max(0, getWidth() - result - 3);
  }

  public void showStackTrace(@Nullable PyConcurrencyEvent event) {
    List<PyStackFrameInfo> frames = event == null ? new ArrayList<PyStackFrameInfo>(0) : event.getFrames();
    if (myStackTracePanel == null) {
      myStackTracePanel = new ConcurrencyStackTracePanel(myProject);
      myStackTracePanel.buildStackTrace(frames);
      splitWindow(myStackTracePanel);
    }
    else {
      myStackTracePanel.buildStackTrace(frames);
    }
  }

  private void splitWindow(@NotNull JComponent component) {
    if ((myTablePanel == null) || (myNotes == null)) {
      return;
    }
    removeAll();
    JSplitPane graphPanel = new JSplitPane(JSplitPane.VERTICAL_SPLIT, true);
    myTablePanel.add(myNotes, BorderLayout.SOUTH);
    graphPanel.add(myTablePanel, JSplitPane.LEFT);
    graphPanel.add(component, JSplitPane.RIGHT);
    graphPanel.setDividerLocation(getHeight() * 2 / 3);
    graphPanel.setDividerSize(myPresentationModel.getVisualSettings().getDividerWidth());
    add(graphPanel, BorderLayout.CENTER);
    setToolbar(createToolbarPanel());
    validate();
    repaint();
  }

  private void initTable() {
    myTablePanel = ConcurrencyTableUtil.createTables(myGraphModel, myPresentationModel, this);
    add(myTablePanel);
    myNotes = new ConcurrencyNotesPanel(this);
    myTablePanel.add(myNotes, BorderLayout.SOUTH);

    AdjustmentListener listener = new GraphAdjustmentListener();
    if (tableScrollPane != null) {
      tableScrollPane.addMouseWheelListener(new MouseWheelListener() {
        @Override
        public void mouseWheelMoved(MouseWheelEvent e) {
          if (e.isControlDown()) {
            if (e.getWheelRotation() < 0) {
              myPresentationModel.getVisualSettings().zoomIn();
            }
            else {
              myPresentationModel.getVisualSettings().zoomOut();
            }
          }
        }
      });
      tableScrollPane.setWheelScrollingEnabled(false);
      tableScrollPane.getHorizontalScrollBar().addAdjustmentListener(listener);
      tableScrollPane.getVerticalScrollBar().addAdjustmentListener(listener);
    }
  }

  private void updateContent() {
    if (myGraphModel.getSize() == 0) {
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
