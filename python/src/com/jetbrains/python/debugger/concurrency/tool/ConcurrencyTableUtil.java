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

import com.intellij.ui.JBColor;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.table.JBTable;
import com.jetbrains.python.debugger.concurrency.model.ConcurrencyGraphModel;
import com.jetbrains.python.debugger.concurrency.model.ConcurrencyGraphPresentationModel;
import com.jetbrains.python.debugger.concurrency.model.ConcurrencyGraphVisualSettings;
import com.jetbrains.python.debugger.concurrency.model.ConcurrencyTable;
import com.jetbrains.python.debugger.concurrency.tool.panels.ConcurrencyToolWindowPanel;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import java.awt.*;

public class ConcurrencyTableUtil {

  public static class GraphCell {
  }

  private static class FixedTableModel extends AbstractTableModel {
    private final ConcurrencyGraphModel myGraphModel;

    public FixedTableModel(ConcurrencyGraphModel graphModel) {
      myGraphModel = graphModel;
    }

    @Override
    public int getRowCount() {
      return myGraphModel.getMaxThread();
    }

    @Override
    public int getColumnCount() {
      return 1;
    }

    @Override
    public String getColumnName(int column) {
      return "Name";
    }

    @Override
    public Class<?> getColumnClass(int columnIndex) {
      return String.class;
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
      return myGraphModel.getThreadNames().get(rowIndex);
    }
  }

  private static class ScrollableTableModel extends AbstractTableModel {
    private final ConcurrencyGraphModel myGraphModel;

    public ScrollableTableModel(ConcurrencyGraphModel graphModel) {
      myGraphModel = graphModel;
    }

    @Override
    public int getRowCount() {
      return myGraphModel.getMaxThread();
    }

    @Override
    public int getColumnCount() {
      return 1;
    }

    @Override
    public String getColumnName(int column) {
      return "States";
    }

    @Override
    public Class<?> getColumnClass(int columnIndex) {
      return GraphCell.class;
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
      return new GraphCell();
    }
  }

  private static class StatTableModel extends AbstractTableModel {
    private final ConcurrencyGraphModel myGraphModel;

    public StatTableModel(ConcurrencyGraphModel graphModel) {
      myGraphModel = graphModel;
    }

    @Override
    public int getRowCount() {
      return myGraphModel.getMaxThread();
    }

    @Override
    public int getColumnCount() {
      return 1;
    }

    @Override
    public String getColumnName(int column) {
      return "Waiting time (s)";
    }

    @Override
    public Class<?> getColumnClass(int columnIndex) {
      return String.class;
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
      double waitTime;
      long percents;
      String threadId = myGraphModel.getThreadIdByIndex(rowIndex);
      if (threadId == null) {
        waitTime = 0;
        percents = 0;
      }
      else {
        ConcurrencyStat threadStatistics = myGraphModel.getStatisticsByThreadId(threadId);
        waitTime = (double)threadStatistics.getWaitTime() / 1000000; // convert to seconds
        percents = threadStatistics.getWaitTime() * 100 / threadStatistics.getWorkTime(); // convert to percents
      }
      return String.format("%.2f (%d%%)", waitTime, percents);
    }
  }

  private static void setTableSettings(JBTable table) {
    table.setRowHeight(ConcurrencyGraphSettings.TABLE_ROW_HEIGHT);
    table.setShowHorizontalLines(false);
    table.setShowVerticalLines(false);
    table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
  }

  @NotNull
  public static JPanel createTables(@NotNull ConcurrencyGraphModel graphModel, @NotNull ConcurrencyGraphPresentationModel presentationModel,
                                    @NotNull ConcurrencyToolWindowPanel toolWindow) {
    JPanel tablePanel = new JPanel(new BorderLayout());

    JBTable fixedTable = new JBTable(new FixedTableModel(graphModel));
    toolWindow.setFixedTable(fixedTable);
    setTableSettings(fixedTable);
    JScrollPane namesScrollPane = ScrollPaneFactory.createScrollPane(fixedTable);
    namesScrollPane.setPreferredSize(new Dimension(ConcurrencyGraphVisualSettings.NAMES_PANEL_INITIAL_WIDTH, toolWindow.getHeight()));
    namesScrollPane.setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, JBColor.border()));
    tablePanel.add(namesScrollPane, BorderLayout.LINE_START);

    ConcurrencyTable graphTable = new ConcurrencyTable(presentationModel, new ScrollableTableModel(graphModel), toolWindow);
    setTableSettings(graphTable);
    graphTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
    graphTable.setSelectionModel(fixedTable.getSelectionModel());
    JScrollPane graphScrollPane = ScrollPaneFactory.createScrollPane(graphTable);
    graphScrollPane.setBorder(BorderFactory.createEmptyBorder());
    graphScrollPane.getVerticalScrollBar().setModel(namesScrollPane.getVerticalScrollBar().getModel());
    toolWindow.setTableScrollPane(graphScrollPane);
    tablePanel.add(graphScrollPane, BorderLayout.CENTER);

    JBTable statTable = new JBTable(new StatTableModel(graphModel));
    setTableSettings(statTable);
    statTable.setSelectionModel(graphTable.getSelectionModel());
    toolWindow.setStatTable(statTable);
    JScrollPane statScrollPane = ScrollPaneFactory.createScrollPane(statTable);
    statScrollPane.setPreferredSize(new Dimension(ConcurrencyGraphSettings.NAMES_PANEL_WIDTH, toolWindow.getHeight()));
    statScrollPane.setBorder(BorderFactory.createMatteBorder(0, 1, 0, 0, JBColor.border()));
    statScrollPane.getVerticalScrollBar().setModel(graphScrollPane.getVerticalScrollBar().getModel());
    tablePanel.add(statScrollPane, BorderLayout.LINE_END);

    return tablePanel;
  }
}
