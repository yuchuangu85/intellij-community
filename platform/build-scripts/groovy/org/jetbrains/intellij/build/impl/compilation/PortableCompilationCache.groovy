// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.impl.compilation

import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import groovy.transform.CompileStatic
import org.jetbrains.intellij.build.CompilationContext
import org.jetbrains.intellij.build.CompilationTasks
import org.jetbrains.intellij.build.impl.CompilationContextImpl
import org.jetbrains.intellij.build.impl.JpsCompilationRunner
import org.jetbrains.intellij.build.impl.compilation.cache.CommitsHistory
import org.jetbrains.jps.backwardRefs.JavaBackwardReferenceIndexWriter
import org.jetbrains.jps.incremental.storage.ProjectStamps

@CompileStatic
class PortableCompilationCache {
  private final CompilationContext context
  /**
   * Read-only JPS remote cache url
   */
  private static final String REMOTE_CACHE_URL_PROPERTY = 'intellij.jps.remote.cache.url'
  /**
   * Read/Write JPS remote cache url
   */
  private static final String CACHE_UPLOAD_URL_PROPERTY = 'intellij.jps.remote.cache.upload.url'
  /**
   * IntelliJ repository git remote url
   */
  private static final String GIT_REPOSITORY_URL_PROPERTY = 'intellij.remote.url'
  /**
   * If true then JPS caches for head commit are expected to exist and search in
   * {@link org.jetbrains.intellij.build.impl.compilation.cache.CommitsHistory#JSON_FILE} is skipped.
   * Required for temporary branch caches which are uploaded but not published in
   * {@link org.jetbrains.intellij.build.impl.compilation.cache.CommitsHistory#JSON_FILE}.
   */
  private static final String AVAILABLE_FOR_HEAD_PROPERTY = 'intellij.jps.cache.availableForHeadCommit'
  /**
   * Download JPS remote caches even if there are caches available locally
   */
  private static final String FORCE_DOWNLOAD_PROPERTY = 'intellij.jps.cache.download.force'
  /**
   * JPS caches archive upload may be skipped if only hot compile outputs are required
   * without any incremental compilation (for tests execution as an example)
   */
  private static final String UPLOAD_COMPILATION_OUTPUTS_PROPERTY = 'intellij.jps.remote.cache.compilationOutputsOnly'
  /**
   * If true then JPS caches and compilation outputs will be rebuilt from scratch
   */
  private static final String FORCE_REBUILD_PROPERTY = 'intellij.jps.cache.rebuild.force'
  /**
   * Folder to store JPS caches and compilation outputs for later upload to AWS S3 bucket.
   * Upload performed in a separate process on CI.
   */
  private static final String AWS_SYNC_FOLDER_PROPERTY = 'jps.caches.aws.sync.folder'
  /**
   * Commit hash for which JPS caches are to be built/downloaded
   */
  private static final String COMMIT_HASH_PROPERTY = 'build.vcs.number'
  /**
   * System properties to be passed to child JVM process (like tests process) to enable JPS caches there
   */
  static final List<String> PROPERTIES = [
    COMMIT_HASH_PROPERTY, REMOTE_CACHE_URL_PROPERTY, GIT_REPOSITORY_URL_PROPERTY,
    AVAILABLE_FOR_HEAD_PROPERTY, FORCE_DOWNLOAD_PROPERTY,
    JavaBackwardReferenceIndexWriter.PROP_KEY,
    ProjectStamps.PORTABLE_CACHES_PROPERTY
  ]
  private boolean forceDownload = bool(FORCE_DOWNLOAD_PROPERTY, false)
  private File cacheDir = context.compilationData.dataStorageRoot
  private boolean forceRebuild = bool(FORCE_REBUILD_PROPERTY, false)
  /**
   * If true then JPS remote cache is configured to be used
   */
  boolean canBeUsed = ProjectStamps.PORTABLE_CACHES && !StringUtil.isEmptyOrSpaces(System.getProperty(REMOTE_CACHE_URL_PROPERTY))
  @Lazy
  private String remoteCacheUrl = { require(REMOTE_CACHE_URL_PROPERTY, "JPS remote cache url") }()

  @Lazy
  private String remoteGitUrl = {
    require(GIT_REPOSITORY_URL_PROPERTY, "Repository url").tap {
      context.messages.info("Git remote url $it")
    }
  }()
  @Lazy
  private CompilationOutputsDownloader downloader = {
    def availableForHeadCommit = bool(AVAILABLE_FOR_HEAD_PROPERTY, false)
    new CompilationOutputsDownloader(context, remoteCacheUrl, remoteGitUrl, availableForHeadCommit)
  }()
  @Lazy
  private CompilationOutputsUploader uploader = {
    def remoteCacheUploadUrl = require(CACHE_UPLOAD_URL_PROPERTY, "JPS remote cache upload url")
    def syncFolder = require(AWS_SYNC_FOLDER_PROPERTY, "AWS sync folder")
    def uploadCompilationOutputsOnly = bool(UPLOAD_COMPILATION_OUTPUTS_PROPERTY, false)
    def commitHash = require(COMMIT_HASH_PROPERTY, "Repository commit")
    context.messages.buildStatus(commitHash)
    new CompilationOutputsUploader(
      context, remoteCacheUploadUrl, remoteGitUrl, commitHash, syncFolder, uploadCompilationOutputsOnly
    )
  }()

  PortableCompilationCache(CompilationContext context) {
    this.context = context
  }

  /**
   * Download latest available compilation cache from remote cache and perform compilation if necessary
   */
  def downloadCacheAndCompileProject() {
    if (forceRebuild) {
      clearJpsOutputs()
    }
    else if (forceDownload || !cacheDir.isDirectory() || !cacheDir.list()) {
      downloadCachesAndOutput()
    }
    // ensure that all Maven dependencies are resolved before compilation
    CompilationTasks.create(context).resolveProjectDependencies()
    if (forceRebuild || !downloader.availableForHeadCommit || downloader.anyLocalChanges() || !forceDownload) {
      // When force rebuilding incrementalCompilation has to be set to false otherwise backward-refs won't be created.
      // During rebuild JPS checks {@code CompilerReferenceIndex.exists(buildDir) || isRebuild} and if
      // incremental compilation enabled JPS won't create {@link JavaBackwardReferenceIndexWriter}.
      // For more details see {@link JavaBackwardReferenceIndexWriter#initialize}
      context.options.incrementalCompilation = !forceRebuild
      compileProject()
    }
    context.options.incrementalCompilation = false
    context.options.useCompiledClassesFromProjectOutput = true
  }

  /**
   * Upload local compilation cache to remote cache
   */
  def upload() {
    if (!forceRebuild && downloader.availableForHeadCommit) {
      context.messages.info('Nothing new to upload')
    }
    else {
      uploader.upload()
    }
  }

  /**
   * Publish already uploaded compilation cache to remote cache
   */
  def publish() {
    uploader.updateCommitHistory()
  }

  def buildCompilationCacheZip() {
    uploader.buildCompilationCacheZip()
  }

  /**
   * Publish already uploaded compilation cache to remote cache overriding existing commit history.
   * Used in force rebuild and cleanup.
   */
  def overrideCommitHistory(Set<String> forceRebuiltCommits) {
    def newCommitHistory = new CommitsHistory([(remoteGitUrl): forceRebuiltCommits])
    uploader.updateCommitHistory(newCommitHistory, true)
  }

  private def clearJpsOutputs() {
    [cacheDir, new File(context.paths.buildOutputRoot, 'classes')].each {
      context.messages.info("Cleaning $it")
      FileUtil.delete(it)
    }
  }

  private def compileProject() {
    // ensure that JBR and Kotlin plugin are downloaded before compilation
    CompilationContextImpl.setupCompilationDependencies(context.gradle, context.options)
    def jps = new JpsCompilationRunner(context)
    try {
      jps.buildAll()
    }
    catch (Exception e) {
      if (context.options.incrementalCompilation && !forceDownload) {
        // JPS caches are rebuilt from scratch on CI and re-published every night to avoid possible incremental compilation issues.
        // If JPS cache download isn't forced then locally available cache will be used which may suffer from those issues.
        // Hence compilation failure. Replacing local cache with remote one may help.
        context.messages.warning('Incremental compilation using locally available caches failed. ' +
                                 'Re-trying using JPS remote caches.')
        downloadCachesAndOutput()
        jps.buildAll()
      }
      else {
        throw e
      }
    }
  }

  private def downloadCachesAndOutput() {
    try {
      downloader.downloadCachesAndOutput()
    }
    finally {
      downloader.close()
    }
  }

  private String require(String systemProperty, String description) {
    def value = System.getProperty(systemProperty)
    if (StringUtil.isEmptyOrSpaces(value)) {
      context.messages.error("$description is not defined. Please set '$systemProperty' system property.")
    }
    return value
  }

  private static boolean bool(String systemProperty, boolean defaultValue) {
    System.getProperty(systemProperty, "$defaultValue").toBoolean()
  }
}
