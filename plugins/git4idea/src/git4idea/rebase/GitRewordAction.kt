// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.rebase

import com.intellij.dvcs.repo.Repository
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.vcs.log.VcsCommitMetadata
import com.intellij.vcs.log.util.VcsUserUtil.getShortPresentation
import git4idea.i18n.GitBundle
import git4idea.rebase.log.GitNewCommitMessageActionDialog
import git4idea.rebase.log.getOrLoadDetails
import git4idea.repo.GitRepository

internal class GitRewordAction : GitSingleCommitEditingAction() {
  override val prohibitRebaseDuringRebasePolicy = ProhibitRebaseDuringRebasePolicy.Prohibit(
    GitBundle.getString("rebase.log.action.operation.reword.name")
  )

  override fun actionPerformedAfterChecks(commitEditingData: SingleCommitEditingData) {
    val details = getOrLoadDetails(commitEditingData.project, commitEditingData.logData, listOf(commitEditingData.selectedCommit))
    val commit = details.first()
    val dialog = GitNewCommitMessageActionDialog(
      commitEditingData,
      commit.fullMessage,
      GitBundle.getString("rebase.log.reword.dialog.title"),
      GitBundle.message(
        "rebase.log.reword.dialog.description.label",
        commit.id.toShortString(),
        getShortPresentation(commit.author)
      )
    )
    dialog.show { newMessage ->
      rewordInBackground(commitEditingData.project, commit, commitEditingData.repository, newMessage)
    }
  }

  override fun getFailureTitle(): String = GitBundle.getString("rebase.log.reword.action.failure.title")

  private fun rewordInBackground(project: Project, commit: VcsCommitMetadata, repository: GitRepository, newMessage: String) {
    object : Task.Backgroundable(project, GitBundle.getString("rebase.log.reword.action.progress.indicator.title")) {
      override fun run(indicator: ProgressIndicator) {
        GitRewordOperation(repository, commit, newMessage).execute()
      }
    }.queue()
  }

  override fun getProhibitedStateMessage(commitEditingData: SingleCommitEditingData, operation: String): String? {
    if (commitEditingData.repository.state == Repository.State.REBASING && commitEditingData.isHeadCommit) {
      return null
    }
    return super.getProhibitedStateMessage(commitEditingData, operation)
  }
}