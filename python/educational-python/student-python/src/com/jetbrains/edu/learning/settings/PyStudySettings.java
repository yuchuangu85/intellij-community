package com.jetbrains.edu.learning.settings;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@SuppressWarnings("MethodMayBeStatic")
@State(name = "PyStudySettings", storages = @Storage("study_twitter_settings.xml"))
public class PyStudySettings implements PersistentStateComponent<PyStudySettings.State> {

  private State myState = new State();


  public static class State {
    public boolean askToTweet = true;
    public String accessToken = "";
    public String tokenSecret = "";
  }

  public static PyStudySettings getInstance(@NotNull final Project project) {
    return ServiceManager.getService(project, PyStudySettings.class);
  }
  @Nullable
  @Override
  public State getState() {
    return myState;
  }

  @Override
  public void loadState(State state) {
    myState = state;
  }
  
  public boolean askToTweet() {
    return myState.askToTweet;
  }
  
  public void setAskToTweet(final boolean askToTweet) {
    myState.askToTweet = askToTweet;
  }
  
  @NotNull
  public String getAccessToken() {
    return myState.accessToken;
  }

  public void setAccessToken(@NotNull String accessToken) {
    myState.accessToken = accessToken;
  }
  
  @NotNull
  public String getTokenSecret() {
    return myState.tokenSecret;
  }
  
  public void setTokenSecret(@NotNull String tokenSecret) {
    myState.tokenSecret = tokenSecret;
  }
}