package com.jetbrains.edu.learning;

import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.project.Project;
import com.jetbrains.edu.courseFormat.Course;
import com.jetbrains.edu.courseFormat.StudyStatus;
import com.jetbrains.edu.courseFormat.Task;
import com.jetbrains.edu.learning.actions.StudyAfterCheckAction;
import com.jetbrains.edu.learning.settings.ModifiableSettingsPanel;
import com.jetbrains.edu.learning.settings.PySettingsPanel;
import com.jetbrains.edu.learning.settings.PyStudySettings;
import com.jetbrains.edu.learning.twitter.StudyTwitterAction;
import com.jetbrains.edu.learning.twitter.StudyTwitterConnectorBundle;
import com.jetbrains.edu.learning.twitter.StudyTwitterUtils;
import com.jetbrains.edu.learning.twitter.TwitterDialogPanel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PyStudyPluginConfigurator extends StudyBasePluginConfigurator {
  
  @NotNull
  @Override
  public DefaultActionGroup getActionGroup(Project project) {
    final DefaultActionGroup baseGroup = super.getActionGroup(project);
    final DefaultActionGroup group = new DefaultActionGroup();
    group.add(new PyStudyCheckAction());
    group.addAll(baseGroup);
    return group;
  }

  @NotNull
  @Override
  public String getDefaultHighlightingMode() {
    return "python";
  }

  @NotNull
  @Override
  public String getLanguageScriptUrl() {
    return getClass().getResource("/python.js").toExternalForm();
  }

  @Nullable
  @Override
  public StudyAfterCheckAction[] getAfterCheckActions() {
    return new StudyAfterCheckAction[]{new StudyTwitterAction()};
  }

  @Override
  public boolean accept(@NotNull Project project) {
    StudyTaskManager taskManager = StudyTaskManager.getInstance(project);
    if (taskManager == null) return false;
    Course course = taskManager.getCourse();
    return course != null && "Python".equals(course.getLanguage()) && "PyCharm".equals(course.getCourseType());
  }

  @Nullable
  @Override
  public ModifiableSettingsPanel getSettingsPanel() {
    return new PySettingsPanel();
  }

  @Override
  public void storeTwitterTokens(@NotNull final Project project, @NotNull String accessToken, @NotNull String tokenSecret) {
    PyStudySettings.getInstance(project).setAccessToken(accessToken);
    PyStudySettings.getInstance(project).setTokenSecret(tokenSecret);
  }

  @NotNull
  @Override
  public String getTwitterTokenSecret(@NotNull Project project) {
    return PyStudySettings.getInstance(project).getTokenSecret();
  }

  @Nullable
  @Override
  public StudyTwitterUtils.TwitterDialogPanel getTweetDialogPanel(@NotNull Task solvedTask) {
    return new TwitterDialogPanel(solvedTask);
  }

  @NotNull
  @Override
  public String getTwitterAccessToken(@NotNull Project project) {
    return PyStudySettings.getInstance(project).getAccessToken();
  }

  @Override
  public boolean askToTweet(@NotNull Project project, Task solvedTask, StudyStatus statusBeforeCheck) {
    return solvedTask.getStatus() == StudyStatus.Solved && 
        (statusBeforeCheck == StudyStatus.Failed || statusBeforeCheck == StudyStatus.Unchecked);
  }

  @NotNull
  @Override
  public String getConsumerKey(@NotNull Project project) {
    return StudyTwitterConnectorBundle.message("consumer_key");
  }

  @NotNull
  @Override
  public String getConsumerSecret(@NotNull Project project) {
    return StudyTwitterConnectorBundle.message("consumer_secret");
  }
}
