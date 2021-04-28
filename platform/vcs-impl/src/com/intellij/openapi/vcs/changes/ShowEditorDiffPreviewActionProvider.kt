// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.AnActionExtensionProvider
import com.intellij.openapi.components.service
import com.intellij.openapi.vcs.changes.EditorTabDiffPreviewManager.Companion.EDITOR_TAB_DIFF_PREVIEW

open class ShowEditorDiffPreviewActionProvider : AnActionExtensionProvider {
  override fun isActive(e: AnActionEvent): Boolean {
    val project = e.project

    return project != null &&
           getDiffPreview(e) != null &&
           project.service<EditorTabDiffPreviewManager>().isEditorDiffPreviewAvailable()
  }

  override fun update(e: AnActionEvent) {}

  override fun actionPerformed(e: AnActionEvent) {
    val diffPreview = getDiffPreview(e)!!

    val previewManager = e.project!!.service<EditorTabDiffPreviewManager>()
    previewManager.showDiffPreview(diffPreview)
  }

  open fun getDiffPreview(e: AnActionEvent) = e.getData(EDITOR_TAB_DIFF_PREVIEW)
}
