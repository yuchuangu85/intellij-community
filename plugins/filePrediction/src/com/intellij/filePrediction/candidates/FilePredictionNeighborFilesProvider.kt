// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.filePrediction.candidates

import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.FileIndexFacade
import com.intellij.openapi.vfs.VirtualFile

internal class FilePredictionNeighborFilesProvider : FilePredictionBaseCandidateProvider(20) {
  override fun provideCandidates(project: Project, file: VirtualFile?, refs: Set<VirtualFile>, limit: Int): Collection<FilePredictionCandidateFile> {
    if (file == null) {
      return emptySet()
    }

    val result = HashSet<FilePredictionCandidateFile>()
    val fileIndex = FileIndexFacade.getInstance(project)
    var parent = file.parent
    while (parent != null && parent.isDirectory && result.size < limit && fileIndex.isInProjectScope(parent)) {
      addWithLimit(parent.children.iterator(), result, "neighbor", file, limit)
      parent = parent.parent
    }
    return result
  }
}