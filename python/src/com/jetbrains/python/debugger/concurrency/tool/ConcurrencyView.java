
/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.python.debugger.concurrency.tool;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import com.intellij.ui.content.ContentManager;
import com.jetbrains.python.debugger.concurrency.tool.asyncio.AsyncioLogToolWindowPanel;
import com.jetbrains.python.debugger.concurrency.tool.threading.ThreadingLogToolWindowPanel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ConcurrencyView implements PersistentStateComponent<ConcurrencyView.State>, Disposable {
  private final Project myProject;

  public ConcurrencyView(Project project) {
    myProject = project;
  }

  @Override
  public void dispose() {

  }

  @Nullable
  @Override
  public State getState() {
    return null;
  }

  @Override
  public void loadState(State state) {

  }

  static class State {

  }

  public void initToolWindow(@NotNull ToolWindow toolWindow) {
    ContentFactory contentFactory = ContentFactory.SERVICE.getInstance();
    ConcurrencyPanel graphPanel = new ThreadingLogToolWindowPanel(myProject);
    Content mainContent = contentFactory.createContent(graphPanel, null, false);
    mainContent.setComponent(graphPanel);
    mainContent.setDisplayName("Threading graph");
    Disposer.register(myProject, mainContent);
    ContentManager myContentManager = toolWindow.getContentManager();
    myContentManager.addContent(mainContent);

    ConcurrencyPanel asyncioPanel = new AsyncioLogToolWindowPanel(myProject);
    Content lockPanelContent = contentFactory.createContent(asyncioPanel, null, false);
    lockPanelContent.setComponent(asyncioPanel);
    lockPanelContent.setDisplayName("Asyncio graph");
    Disposer.register(myProject, lockPanelContent);
    myContentManager = toolWindow.getContentManager();
    myContentManager.addContent(lockPanelContent);
  }
}
