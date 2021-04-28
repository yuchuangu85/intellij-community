// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.updateSettings.impl.pluginsAdvertisement;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.plugins.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.updateSettings.impl.DetectedPluginsPanel;
import com.intellij.openapi.updateSettings.impl.PluginDownloader;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.IOException;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * @author anna
 */
public final class PluginsAdvertiserDialog extends DialogWrapper {
  private static final Logger LOG = Logger.getInstance(PluginsAdvertiserDialog.class);

  private final SortedSet<PluginDownloader> myPluginToInstall = new TreeSet<>(Comparator.comparing(PluginDownloader::getPluginName, String::compareToIgnoreCase));
  private final @Nullable Project myProject;
  private final @NotNull List<PluginNode> myCustomPlugins;
  private final @Nullable Consumer<? super Boolean> myFinishFunction;
  private @Nullable DetectedPluginsPanel myPanel;

  PluginsAdvertiserDialog(@Nullable Project project,
                          @NotNull Collection<PluginDownloader> pluginsToInstall,
                          @NotNull List<PluginNode> customPlugins,
                          @Nullable Consumer<? super Boolean> finishFunction) {
    super(project);
    myProject = project;
    myPluginToInstall.addAll(pluginsToInstall);
    myCustomPlugins = customPlugins;
    myFinishFunction = finishFunction;
    setTitle(IdeBundle.message("dialog.title.choose.plugins.to.install.or.enable"));
    init();
  }

  PluginsAdvertiserDialog(@Nullable Project project,
                          @NotNull Collection<PluginDownloader> pluginsToInstall,
                          @NotNull List<PluginNode> customPlugins) {
    this(project, pluginsToInstall, customPlugins, null);
  }

  @Override
  protected @NotNull JComponent createCenterPanel() {
    if (myPanel == null) {
      myPanel = new DetectedPluginsPanel();
      myPanel.addAll(myPluginToInstall);
    }
    return myPanel;
  }

  @Override
  protected void doOKAction() {
    assert myPanel != null;
    if (doInstallPlugins(myPanel::isChecked)) {
      super.doOKAction();
    }
  }

  public void doInstallPlugins(boolean showDialog) {
    if (showDialog) {
      showAndGet();
    }
    else {
      doInstallPlugins(__ -> true);
    }
  }

  private boolean doInstallPlugins(@NotNull Predicate<? super PluginDownloader> predicate) {
    ArrayList<IdeaPluginDescriptor> pluginsToEnable = new ArrayList<>();
    ArrayList<PluginNode> nodes = new ArrayList<>();
    for (PluginDownloader downloader : myPluginToInstall) {
      if (predicate.test(downloader)) {
        IdeaPluginDescriptor plugin = downloader.getDescriptor();
        pluginsToEnable.add(plugin);
        if (plugin.isEnabled()) {
          nodes.add(downloader.toPluginNode());
        }
      }
    }

    if (!PluginManagerMain.checkThirdPartyPluginsAllowed(nodes)) {
      return false;
    }

    PluginManagerMain.suggestToEnableInstalledDependantPlugins(PluginEnabler.HEADLESS, nodes);

    Runnable notifyRunnable = () -> {
      boolean notInstalled = nodes.stream()
        .map(PluginNode::getPluginId)
        .map(PluginManagerCore::getPlugin)
        .anyMatch(Objects::isNull);
      if (notInstalled) {
        PluginManagerMain.notifyPluginsUpdated(myProject);
      }
    };

    PluginEnabler.HEADLESS.enablePlugins(pluginsToEnable);
    if (!nodes.isEmpty()) {
      try {
        PluginManagerMain.downloadPlugins(nodes,
                                          myCustomPlugins,
                                          true,
                                          notifyRunnable,
                                          PluginEnabler.HEADLESS,
                                          myFinishFunction);
      }
      catch (IOException e) {
        LOG.error(e);
      }
    }
    else {
      if (!pluginsToEnable.isEmpty()) {
        notifyRunnable.run();
      }
    }
    return true;
  }
}
