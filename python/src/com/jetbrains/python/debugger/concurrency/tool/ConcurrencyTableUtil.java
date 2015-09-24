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

import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.table.JBTable;
import com.jetbrains.python.debugger.concurrency.model.ConcurrencyGraphModel;
import com.jetbrains.python.debugger.concurrency.model.ConcurrencyGraphPresentationModel;
import com.jetbrains.python.debugger.concurrency.model.ConcurrencyGraphVisualSettings;
import com.jetbrains.python.debugger.concurrency.model.ConcurrencyTable;
import com.jetbrains.python.debugger.concurrency.tool.panels.ConcurrencyToolWindowPanel;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import java.awt.*;

public class ConcurrencyTableUtil {

  public static class GraphCell {
  }

  private static class FixedTableModel extends AbstractTableModel {
    private ConcurrencyGraphModel myGraphModel;

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
    private ConcurrencyGraphModel myGraphModel;

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

  private static void setTableSettings(JBTable table) {
    table.setRowHeight(ConcurrencyGraphSettings.TABLE_ROW_HEIGHT);
    table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
    table.setShowHorizontalLines(false);
    table.setShowVerticalLines(false);
    table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
  }


  public static JScrollPane createTables(ConcurrencyGraphModel graphModel, ConcurrencyGraphPresentationModel graphPresentation,
                                         ConcurrencyToolWindowPanel toolWindow) {
    JBTable fixedTable = new JBTable(new FixedTableModel(graphModel));
    setTableSettings(fixedTable);
    fixedTable.getColumnModel().getColumn(0).setPreferredWidth(ConcurrencyGraphVisualSettings.NAMES_PANEL_INITIAL_WIDTH);

    ConcurrencyTable graphTable = new ConcurrencyTable(graphPresentation, new ScrollableTableModel(graphModel), toolWindow);
    setTableSettings(graphTable);

    ListSelectionModel model = fixedTable.getSelectionModel();
    graphTable.setSelectionModel(model);

    JScrollPane scrollPane = ScrollPaneFactory.createScrollPane(graphTable);
    Dimension fixedSize = fixedTable.getPreferredSize();
    JViewport viewport = new JViewport();
    viewport.setView(fixedTable);
    viewport.setPreferredSize(fixedSize);
    scrollPane.setCorner(ScrollPaneConstants.UPPER_LEFT_CORNER, fixedTable.getTableHeader());
    scrollPane.setRowHeaderView(viewport);

    return scrollPane;
  }
}
