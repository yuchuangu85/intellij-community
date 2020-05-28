// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing.diagnostic.dto

import com.intellij.util.indexing.diagnostic.*
import com.intellij.util.text.DateFormatUtil

fun TimeNano.toMillis(): TimeMillis = this / 1_000_000

fun TimeMillis.toNano(): TimeNano = this * 1_000_000

typealias PresentableTime = String

fun FileProviderIndexStatistics.toJson(): JsonFileProviderIndexStatistics {
  val statsPerFileType = aggregateStatsPerFileType()
  val allStatsPerIndexer = aggregateStatsPerIndexer()
  val (statsPerIndexer, fastIndexers) = allStatsPerIndexer.partition { it.partOfTotalIndexingTime.percentages > 0.01 }

  return JsonFileProviderIndexStatistics(
    providerDebugName,
    numberOfFiles,
    JsonTime(totalTime),
    statsPerFileType.sortedByDescending { it.partOfTotalIndexingTime.percentages },
    statsPerIndexer.sortedByDescending { it.partOfTotalIndexingTime.percentages },
    fastIndexers.map { it.indexId }.sorted()
  )
}

private fun FileProviderIndexStatistics.aggregateStatsPerFileType(): List<JsonFileProviderIndexStatistics.JsonStatsPerFileType> {
  val totalIndexingTimePerFileType = indexingStatistics.statsPerFileType.values
    .filterNot { it.indexingTime.isEmpty }
    .map { it.indexingTime.sumTime }
    .sum()

  val totalContentLoadingTimePerFileType = indexingStatistics.statsPerFileType.values
    .filterNot { it.contentLoadingTime.isEmpty }
    .map { it.contentLoadingTime.sumTime }
    .sum()

  return indexingStatistics.statsPerFileType
    .mapNotNull { (fileTypeName, stats) ->
      JsonFileProviderIndexStatistics.JsonStatsPerFileType(
        fileTypeName,
        stats.numberOfFiles,
        JsonFileSize(stats.totalBytes),
        calculatePercentages(stats.indexingTime.sumTime, totalIndexingTimePerFileType),
        calculatePercentages(stats.contentLoadingTime.sumTime, totalContentLoadingTimePerFileType),
        stats.biggestContributors.biggestElements.map { it.toJson() }.sortedByDescending { it.indexingTime.nano }
      )
    }
}

private fun FileProviderIndexStatistics.aggregateStatsPerIndexer(): List<JsonFileProviderIndexStatistics.JsonStatsPerIndexer> {
  val totalIndexingTimePerIndexer = indexingStatistics.statsPerIndexer.values
    .filterNot { it.indexingTime.isEmpty }
    .map { it.indexingTime.sumTime }
    .sum()

  return indexingStatistics.statsPerIndexer
    .mapNotNull { (indexId, stats) ->
      JsonFileProviderIndexStatistics.JsonStatsPerIndexer(
        indexId,
        calculatePercentages(stats.indexingTime.sumTime, totalIndexingTimePerIndexer)
      )
    }
}

private fun TimeMillis.toPresentableTime(): PresentableTime =
  DateFormatUtil.getIso8601Format().format(this)

fun ProjectIndexingHistory.IndexingTimes.toJson() =
  JsonProjectIndexingHistoryTimes(
    startIndexing.toPresentableTime(),
    endIndexing.toPresentableTime(),
    JsonTime((endIndexing - startIndexing).toNano()),
    startPushProperties.toPresentableTime(),
    endPushProperties.toPresentableTime(),
    JsonTime((endPushProperties - startPushProperties).toNano()),
    startIndexExtensions.toPresentableTime(),
    endIndexExtensions.toPresentableTime(),
    JsonTime((endIndexExtensions - startIndexExtensions).toNano()),
    startScanFiles.toPresentableTime(),
    endScanFiles.toPresentableTime(),
    JsonTime((endScanFiles - startScanFiles).toNano())
  )

private fun calculatePercentages(part: Long, total: Long): JsonPercentages =
  if (total == 0L) {
    JsonPercentages(1.0)
  }
  else {
    JsonPercentages(part.toDouble() / total)
  }

fun ProjectIndexingHistory.toJson() =
  JsonProjectIndexingHistory(
    projectName,
    providerStatistics.size,
    aggregateTotalNumberOfFiles(),
    times.toJson(),
    aggregateStatsPerFileType().sortedByDescending { it.partOfTotalIndexingTime.percentages },
    aggregateStatsPerIndexer().sortedByDescending { it.partOfTotalIndexingTime.percentages },
    providerStatistics.sortedByDescending { it.totalIndexingTime.nano }
  )

private fun ProjectIndexingHistory.aggregateTotalNumberOfFiles() =
  providerStatistics.map { it.totalNumberOfFiles }.sum()

private fun ProjectIndexingHistory.aggregateStatsPerFileType(): List<JsonProjectIndexingHistory.JsonStatsPerFileType> {
  val totalIndexingTime = totalStatsPerFileType.values.map { it.totalIndexingTimeInAllThreads }.sum()
  val fileTypeToIndexingTimePart = totalStatsPerFileType.mapValues {
    calculatePercentages(it.value.totalIndexingTimeInAllThreads, totalIndexingTime)
  }

  @Suppress("DuplicatedCode")
  val totalContentLoadingTime = totalStatsPerFileType.values.map { it.totalContentLoadingTimeInAllThreads }.sum()
  val fileTypeToContentLoadingTimePart = totalStatsPerFileType.mapValues {
    calculatePercentages(it.value.totalContentLoadingTimeInAllThreads,
                         totalContentLoadingTime)
  }

  val fileTypeToProcessingSpeed = totalStatsPerFileType.mapValues {
    JsonProcessingSpeed(it.value.totalBytes,
                                                                                     it.value.totalIndexingTimeInAllThreads)
  }

  return totalStatsPerFileType.map { (fileType, stats) ->
    JsonProjectIndexingHistory.JsonStatsPerFileType(
      fileType,
      fileTypeToIndexingTimePart.getValue(fileType),
      fileTypeToContentLoadingTimePart.getValue(fileType),
      stats.totalNumberOfFiles,
      JsonFileSize(stats.totalBytes),
      fileTypeToProcessingSpeed.getValue(fileType),
      stats.biggestContributors.biggestElements.map { it.toJson() }.sortedByDescending { it.indexingTime.nano }
    )
  }
}

private fun IndexedFileStat.toJson() =
  JsonIndexedFileStat(
    fileName,
    fileType,
    JsonFileSize(fileSize),
    JsonTime(indexingTime),
    JsonTime(contentLoadingTime)
  )

private fun ProjectIndexingHistory.aggregateStatsPerIndexer(): List<JsonProjectIndexingHistory.JsonStatsPerIndexer> {
  val totalIndexingTime = totalStatsPerIndexer.values.map { it.totalIndexingTimeInAllThreads }.sum()
  val indexIdToIndexingTimePart = totalStatsPerIndexer.mapValues {
    calculatePercentages(it.value.totalIndexingTimeInAllThreads, totalIndexingTime)
  }

  val indexIdToProcessingSpeed = totalStatsPerIndexer.mapValues {
    JsonProcessingSpeed(it.value.totalBytes, it.value.totalIndexingTimeInAllThreads)
  }

  return totalStatsPerIndexer.map { (indexId, stats) ->
    JsonProjectIndexingHistory.JsonStatsPerIndexer(
      indexId,
      indexIdToIndexingTimePart.getValue(indexId),
      stats.totalNumberOfFiles,
      JsonFileSize(stats.totalBytes),
      indexIdToProcessingSpeed.getValue(indexId)
    )
  }
}