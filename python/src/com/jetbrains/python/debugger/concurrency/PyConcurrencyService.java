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
package com.jetbrains.python.debugger.concurrency;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.jetbrains.python.debugger.concurrency.model.ConcurrencyGraphModel;

public class PyConcurrencyService {
  private ConcurrencyGraphModel myThreadingGraphModel;
  private ConcurrencyGraphModel myAsyncioGraphModel;

  public PyConcurrencyService(Project project) {
    myThreadingGraphModel = new ConcurrencyGraphModel(project);
    myAsyncioGraphModel = new ConcurrencyGraphModel(project);
  }

  public static PyConcurrencyService getInstance(Project project) {
    return ServiceManager.getService(project, PyConcurrencyService.class);
  }

  public ConcurrencyGraphModel getThreadingInstance() {
    return myThreadingGraphModel;
  }

  public ConcurrencyGraphModel getAsyncioInstance() {
    return myAsyncioGraphModel;
  }
}