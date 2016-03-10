package com.jetbrains.edu.learning;

import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.project.Project;
import com.jetbrains.edu.courseFormat.StudyStatus;
import com.jetbrains.edu.courseFormat.Task;
import com.jetbrains.edu.learning.actions.StudyAfterCheckAction;
import com.jetbrains.edu.learning.settings.ModifiableSettingsPanel;
import com.jetbrains.edu.learning.twitter.StudyTwitterUtils;
import com.jetbrains.edu.learning.ui.StudyToolWindow;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Map;

public interface StudyPluginConfigurator {
  ExtensionPointName<StudyPluginConfigurator> EP_NAME = ExtensionPointName.create("Edu.studyPluginConfigurator");

  /**
   * Provide action group that should be placed on the tool window toolbar.
   * @param project
   * @return
   */
  @NotNull
  DefaultActionGroup getActionGroup(Project project);

  /**
   * Provide panels, that could be added to Task tool window.
   * @param project
   * @return Map from panel id, i.e. "Task description", to panel itself.
   */
  @NotNull
  Map<String, JPanel> getAdditionalPanels(Project project);

  @NotNull
  FileEditorManagerListener getFileEditorManagerListener(@NotNull final Project project, @NotNull final StudyToolWindow toolWindow);

  /**
   *
   * @return parameter for CodeMirror script. Available languages: @see <@linktourl http://codemirror.net/mode/>
   */
  @NotNull String getDefaultHighlightingMode();

  @Nullable
  StudyAfterCheckAction[] getAfterCheckActions();
  
  @NotNull String getLanguageScriptUrl();

  @Nullable
  ModifiableSettingsPanel getSettingsPanel();

  /**
   * The plugin implemented tweeting should define policy when user will be asked to tweet.
   * @param project
   * @param solvedTask
   *@param statusBeforeCheck @return 
   */
  boolean askToTweet(@NotNull final Project project, Task solvedTask, StudyStatus statusBeforeCheck);
  /**
   * Stores access token and token secret, obtained by authorizing PyCharm.
   * @param project
   * @param accessToken
   * @param tokenSecret
   */
  void storeTwitterTokens(@NotNull final Project project, @NotNull final String accessToken, @NotNull final String tokenSecret);

  /**
   * 
   * @param project
   * @return stored access token
   */
  @NotNull String getTwitterAccessToken(@NotNull Project project);

  /**
   * 
   * @param project
   * @return stored token secret
   */
  @NotNull String getTwitterTokenSecret(@NotNull Project project);
  
  @Nullable
  StudyTwitterUtils.TwitterDialogPanel getTweetDialogPanel(@NotNull Task solvedTask);
  
  boolean accept(@NotNull final Project project);
}
