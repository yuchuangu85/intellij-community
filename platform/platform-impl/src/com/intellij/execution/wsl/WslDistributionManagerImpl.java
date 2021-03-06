// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.wsl;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.ProcessOutput;
import com.intellij.execution.util.ExecUtil;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.TimeoutUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public final class WslDistributionManagerImpl extends WslDistributionManager {

  // Distributions created by tools, e.g. Docker. Not suitable for running user apps.
  private static final Set<String> INTERNAL_DISTRIBUTIONS = Set.of("docker-desktop-data", "docker-desktop");

  @Override
  protected @NotNull List<String> loadInstalledDistributionMsIds() {
    checkEdtAndReadAction();
    if (!new WSLCommandLineOptions().isLaunchWithWslExe()) {
      return Collections.emptyList();
    }
    try {
      long startNano = System.nanoTime();
      Pair<GeneralCommandLine, List<String>> result = doFetchDistributionsFromWslCli();
      if (result == null) return Collections.emptyList();

      LOG.info("Fetched WSL distributions: " + result.second +
               " (\"" + result.first.getCommandLineString() + "\" done in " + TimeoutUtil.getDurationMillis(startNano) + " ms)");
      return result.second;
    }
    catch (IOException e) {
      LOG.info("Cannot parse WSL distributions", e);
      return Collections.emptyList();
    }
  }

  @Override
  public @NotNull List<WslDistributionAndVersion> loadInstalledDistributionsWithVersions() throws IOException {
    checkEdtAndReadAction();
    Path wslExe = WSLDistribution.findWslExe();
    if (wslExe == null) {
      throw new IOException("Cannot load WSL distributions with versions: wsl.exe is not found in %PATH%");
    }

    GeneralCommandLine commandLine = new GeneralCommandLine(wslExe.toString(), "-l", "-v").withCharset(StandardCharsets.UTF_16LE);

    ProcessOutput output;
    try {
      output = ExecUtil.execAndGetOutput(commandLine, WSLDistribution.DEFAULT_TIMEOUT);
    }
    catch (ExecutionException e) {
      throw new IOException("Failed to run " + commandLine.getCommandLineString(), e);
    }
    if (output.isTimeout() || output.getExitCode() != 0 || !output.getStderr().isEmpty()) {
      String details = StringUtil.join(ContainerUtil.newArrayList(
        "timeout: " + output.isTimeout(),
        "exitCode: " + output.getExitCode(),
        "stdout: " + output.getStdout(),
        "stderr: " + output.getStderr()
      ), ", ");
      throw new IOException("Failed to run " + commandLine.getCommandLineString() + ": " + details);
    }
    return parseWslVerboseListOutput(output.getStdoutLines());
  }

  static @NotNull List<WslDistributionAndVersion> parseWslVerboseListOutput(@NotNull List<String> stdoutLines) {
    return stdoutLines.stream().skip(1).map(l -> {
      List<String> words = StringUtil.split(l, " ");
      int size = words.size();
      if (size >= 3) {
        String distributionName = words.get(size - 3);
        if (!INTERNAL_DISTRIBUTIONS.contains(distributionName)) {
          return new WslDistributionAndVersion(distributionName, StringUtil.parseInt(words.get(size - 1), -1));
        }
      }
      return null;
    }).filter(v -> v != null).collect(Collectors.toList());
  }

  private static @Nullable Pair<GeneralCommandLine, List<String>> doFetchDistributionsFromWslCli() throws IOException {
    Path wslExe = WSLDistribution.findWslExe();
    if (wslExe == null) {
      LOG.info("Cannot parse WSL distributions: wsl.exe is not found in %PATH%");
      return null;
    }

    GeneralCommandLine commandLine = new GeneralCommandLine(wslExe.toString(), "--list", "--quiet").withCharset(StandardCharsets.UTF_16LE);

    ProcessOutput output;
    try {
      output = ExecUtil.execAndGetOutput(commandLine, WSLDistribution.DEFAULT_TIMEOUT);
    }
    catch (ExecutionException e) {
      throw new IOException("Failed to run " + commandLine.getCommandLineString(), e);
    }
    if (output.isTimeout() || output.getExitCode() != 0 || !output.getStderr().isEmpty()) {
      String details = StringUtil.join(ContainerUtil.newArrayList(
        "timeout: " + output.isTimeout(),
        "exitCode: " + output.getExitCode(),
        "stdout: " + output.getStdout(),
        "stderr: " + output.getStderr()
      ), ", ");
      throw new IOException("Failed to run " + commandLine.getCommandLineString() + ": " + details);
    }
    List<@NlsSafe String> msIds = ContainerUtil.filter(output.getStdoutLines(true), distribution -> {
      return !INTERNAL_DISTRIBUTIONS.contains(distribution);
    });
    return Pair.create(commandLine, msIds);
  }

  private static void checkEdtAndReadAction() {
    Application application = ApplicationManager.getApplication();
    if (application == null || !application.isInternal() || application.isHeadlessEnvironment()) {
      return;
    }
    if (application.isReadAccessAllowed()) {
      LOG.error("Please call WslDistributionManager.getInstalledDistributions on a background thread and " +
                "not under read action as it runs a potentially long operation.");
    }
  }
}
