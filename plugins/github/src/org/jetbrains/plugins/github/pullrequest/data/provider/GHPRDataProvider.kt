// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.data.provider

import com.intellij.openapi.Disposable
import org.jetbrains.annotations.CalledInAwt
import org.jetbrains.plugins.github.api.data.pullrequest.timeline.GHPRTimelineItem
import org.jetbrains.plugins.github.pullrequest.data.GHListLoader

interface GHPRDataProvider {
  val detailsData: GHPRDetailsDataProvider
  val stateData: GHPRStateDataProvider
  val changesData: GHPRChangesDataProvider
  val commentsData: GHPRCommentsDataProvider
  val reviewData: GHPRReviewDataProvider
  val timelineLoader: GHListLoader<GHPRTimelineItem>?

  @CalledInAwt
  fun acquireTimelineLoader(disposable: Disposable): GHListLoader<GHPRTimelineItem>
}