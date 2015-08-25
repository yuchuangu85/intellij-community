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
package com.jetbrains.python.debugger.concurrency.tool.graph;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class GraphVisualSettings {
  private int myScale = 1;
  private int myScrollbarValue = 0;
  private int myScrollbarExtent;
  private int myScrollbarMax;
  private List<SettingsListener> myListeners = new ArrayList<SettingsListener>();


  public int getScrollbarValue() {
    return myScrollbarValue;
  }

  public int getScrollbarExtent() {
    return myScrollbarExtent;
  }

  public int getScrollbarMax() {
    return myScrollbarMax;
  }

  public int getScale() {
    return myScale;
  }


  public void updateScrollbarValues(int scrollbarValue, int scrollbarExtent, int scrollMax) {
    myScrollbarValue = scrollbarValue;
    myScrollbarExtent = scrollbarExtent;
    myScrollbarMax = scrollMax;
    notifyListeners();
  }

  public interface SettingsListener {
    void settingsChanged();
  }

  public void registerListener(@NotNull SettingsListener logListener) {
    myListeners.add(logListener);
  }

  public void notifyListeners() {
    for (SettingsListener logListener : myListeners) {
      logListener.settingsChanged();
    }
  }
}
