// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.history.integration.ui.views;

import com.intellij.diff.DiffManager;
import com.intellij.diff.DiffRequestPanel;
import com.intellij.diff.requests.MessageDiffRequest;
import com.intellij.diff.tools.util.DiffSplitter;
import com.intellij.find.EditorSearchSession;
import com.intellij.find.SearchTextArea;
import com.intellij.history.core.LocalHistoryFacade;
import com.intellij.history.integration.IdeaGateway;
import com.intellij.history.integration.ui.models.EntireFileHistoryDialogModel;
import com.intellij.history.integration.ui.models.FileDifferenceModel;
import com.intellij.history.integration.ui.models.FileHistoryDialogModel;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.impl.EditorComponentImpl;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.ExcludingTraversalPolicy;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.event.DocumentEvent;
import java.awt.*;

public class FileHistoryDialog extends HistoryDialog<FileHistoryDialogModel> {
  private DiffRequestPanel myDiffPanel;

  public FileHistoryDialog(@NotNull Project p, IdeaGateway gw, VirtualFile f) {
    this(p, gw, f, true);
  }

  protected FileHistoryDialog(@NotNull Project p, IdeaGateway gw, VirtualFile f, boolean doInit) {
    super(p, gw, f, doInit);
  }

  @Override
  protected FileHistoryDialogModel createModel(LocalHistoryFacade vcs) {
    return new EntireFileHistoryDialogModel(myProject, myGateway, vcs, myFile);
  }

  @Override
  protected Pair<JComponent, Dimension> createDiffPanel(JPanel root, ExcludingTraversalPolicy traversalPolicy) {
    myDiffPanel = DiffManager.getInstance().createRequestPanel(myProject, this, ApplicationManager.getApplication().isUnitTestMode() ? null : getFrame());
    myDiffPanel.setRequest(new MessageDiffRequest(""));
    return Pair.create(myDiffPanel.getComponent(), null);
  }

  @Override
  protected void addExtraToolbar(JPanel toolBarPanel) {
    SearchTextArea comp = new SearchTextArea(new JTextArea(), true);
    comp.getTextArea().getDocument().addDocumentListener(new DocumentAdapter() {
      @Override
      protected void textChanged(@NotNull DocumentEvent e) {
        myRevisionsList.setFilterText(StringUtil.nullize(comp.getTextArea().getText()));
        updateEditorSearch();
      }
    });
    toolBarPanel.add(comp, BorderLayout.CENTER);
  }

  private void updateEditorSearch() {
    Editor editor = findLeftEditor();
    if (editor == null) return;
    String filter = myRevisionsList.getFilterText();
    EditorSearchSession session = EditorSearchSession.get(editor);
    if (StringUtil.isEmpty(filter)) {
      if (session != null) session.close();
      return;
    }
    if (session == null) {
      session = EditorSearchSession.start(editor, myProject);
    }
    session.setTextInField(filter);
  }

  @Nullable
  private Editor findLeftEditor() {
    DiffSplitter splitter = UIUtil.findComponentOfType(myDiffPanel.getComponent(), DiffSplitter.class);
    JComponent left = splitter == null ? null : splitter.getFirstComponent();
    EditorComponentImpl comp = left == null ? null : UIUtil.findComponentOfType(left, EditorComponentImpl.class);
    return comp == null ? null : comp.getEditor();
  }

  @Override
  protected void setDiffBorder(Border border) {
  }

  @Override
  public void dispose() {
    super.dispose();
    if (myDiffPanel != null) {
      Disposer.dispose(myDiffPanel);
    }
  }

  @Override
  protected Runnable doUpdateDiffs(final FileHistoryDialogModel model) {
    final FileDifferenceModel diffModel = model.getDifferenceModel();
    return () -> myDiffPanel.setRequest(createDifference(diffModel));
  }

  @Override
  protected String getHelpId() {
    return "reference.dialogs.showhistory";
  }
}
