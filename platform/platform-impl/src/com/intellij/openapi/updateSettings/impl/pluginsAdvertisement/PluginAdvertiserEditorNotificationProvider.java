// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.updateSettings.impl.pluginsAdvertisement;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.internal.statistic.eventLog.FeatureUsageData;
import com.intellij.internal.statistic.service.fus.collectors.FUCounterUsageLogger;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.EditorNotificationPanel;
import com.intellij.ui.EditorNotifications;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class PluginAdvertiserEditorNotificationProvider extends EditorNotifications.Provider<EditorNotificationPanel> implements DumbAware {
  private static final Key<EditorNotificationPanel> KEY = Key.create("file.type.associations.detected");
  private static final Logger LOG = Logger.getInstance(PluginsAdvertiser.class);

  @NotNull
  @Override
  public Key<EditorNotificationPanel> getKey() {
    return KEY;
  }

  @Nullable
  @Override
  public EditorNotificationPanel createNotificationPanel(@NotNull VirtualFile file,
                                                         @NotNull FileEditor fileEditor,
                                                         @NotNull Project project) {
    final EditorNotificationPanel panel = new EditorNotificationPanel();

    PluginAdvertiserExtensionsState pluginAdvertiserExtensionsState = PluginAdvertiserExtensionsState.getInstance(project);
    String fullExtension = file.getExtension() != null ? "*." + file.getExtension() : null;
    PluginAdvertiserExtensionsData extensionsData = pluginAdvertiserExtensionsState.requestExtensionData(file.getName(), file.getFileType(), fullExtension);
    if (extensionsData == null) {
      ApplicationManager.getApplication().executeOnPooledThread(() -> {
        boolean shouldUpdateNotifications = PluginAdvertiserExtensionsState.getInstance(project).updateCache(file.getName());
        if (fullExtension != null) {
          shouldUpdateNotifications = PluginAdvertiserExtensionsState.getInstance(project).updateCache(fullExtension) || shouldUpdateNotifications;
        }
        if (shouldUpdateNotifications) {
          EditorNotifications.getInstance(project).updateNotifications(file);
        }
        LOG.debug(String.format("Tried to update extensions cache for file '%s'. shouldUpdateNotifications=%s", file.getName(), shouldUpdateNotifications));
      });
      return null;
    }
    String extensionOrFileName = extensionsData.getExtensionOrFileName();
    Set<PluginsAdvertiser.Plugin> plugins = extensionsData.getPlugins();

    panel.setText(IdeBundle.message("plugins.advertiser.plugins.found", extensionOrFileName));
    final IdeaPluginDescriptor disabledPlugin = PluginsAdvertiser.getDisabledPlugin(plugins);
    if (disabledPlugin != null) {
      panel.createActionLabel(IdeBundle.message("plugins.advertiser.action.enable.plugin", disabledPlugin.getName()), () -> {
        pluginAdvertiserExtensionsState.addEnabledExtensionOrFileNameAndInvalidateCache(extensionOrFileName);
        EditorNotifications.getInstance(project).updateAllNotifications();
        FeatureUsageData data = new FeatureUsageData()
          .addData("source", "editor")
          .addData("plugins", Collections.singletonList(disabledPlugin.getPluginId().getIdString()));
        FUCounterUsageLogger.getInstance().logEvent(PluginsAdvertiser.FUS_GROUP_ID, "enable.plugins", data);
        PluginsAdvertiser.enablePlugins(project, Collections.singletonList(disabledPlugin));
      });
    }
    else if (PluginsAdvertiser.hasBundledPluginToInstall(plugins) != null) {
      if (PropertiesComponent.getInstance().isTrueValue(PluginsAdvertiser.IGNORE_ULTIMATE_EDITION)) {
        return null;
      }
      panel.setText(IdeBundle.message("plugins.advertiser.extensions.supported.in.ultimate", extensionOrFileName));

      panel.createActionLabel(IdeBundle.message("plugins.advertiser.action.try.ultimate"), () -> {
        pluginAdvertiserExtensionsState.addEnabledExtensionOrFileNameAndInvalidateCache(extensionOrFileName);
        FeatureUsageData data = new FeatureUsageData().addData("source", "editor");
        FUCounterUsageLogger.getInstance().logEvent(PluginsAdvertiser.FUS_GROUP_ID, "open.download.page", data);
        PluginsAdvertiser.openDownloadPage();
      });

      panel.createActionLabel(IdeBundle.message("plugins.advertiser.action.ignore.ultimate"), () -> {
        FeatureUsageData data = new FeatureUsageData().addData("source", "editor");
        FUCounterUsageLogger.getInstance().logEvent(PluginsAdvertiser.FUS_GROUP_ID, "ignore.ultimate", data);
        PropertiesComponent.getInstance().setValue(PluginsAdvertiser.IGNORE_ULTIMATE_EDITION, "true");
        EditorNotifications.getInstance(project).updateAllNotifications();
      });
    }
    else if (hasNonBundledPlugin(plugins)) {
      panel.createActionLabel(IdeBundle.message("plugins.advertiser.action.install.plugins"), () -> {
        Set<PluginId> pluginIds = new HashSet<>();
        for (PluginsAdvertiser.Plugin plugin : plugins) {
          pluginIds.add(PluginId.getId(plugin.myPluginId));
        }
        FeatureUsageData data = new FeatureUsageData()
          .addData("source", "editor")
          .addData("plugins", ContainerUtil.map(pluginIds, (pluginId) -> pluginId.getIdString()));
        FUCounterUsageLogger.getInstance().logEvent(PluginsAdvertiser.FUS_GROUP_ID, "install.plugins", data);
        PluginsAdvertiser.installAndEnable(pluginIds, () -> {
          pluginAdvertiserExtensionsState.addEnabledExtensionOrFileNameAndInvalidateCache(extensionOrFileName);
          EditorNotifications.getInstance(project).updateAllNotifications();
        });
      });
    }
    else {
      return null;
    }
    panel.createActionLabel(IdeBundle.message("plugins.advertiser.action.ignore.extension"), () -> {
      FeatureUsageData data = new FeatureUsageData().addData("source", "editor");
      FUCounterUsageLogger.getInstance().logEvent(PluginsAdvertiser.FUS_GROUP_ID, "ignore.extensions", data);
      pluginAdvertiserExtensionsState.ignoreExtensionOrFileNameAndInvalidateCache(extensionOrFileName);
      EditorNotifications.getInstance(project).updateAllNotifications();
    });
    return panel;
  }

  private static boolean hasNonBundledPlugin(@NotNull Set<? extends PluginsAdvertiser.Plugin> plugins) {
    for (PluginsAdvertiser.Plugin plugin : plugins) {
      if (!plugin.myBundled) return true;
    }
    return false;
  }
}
