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
package com.jetbrains.python.debugger.concurrency.model;


public class ConcurrencyRelation {
  private final int myPadding;
  private final int myParent;
  private final int myChild;
  private final ConcurrencyThreadState myThreadState;

  public ConcurrencyRelation(int padding, int parent, int child, ConcurrencyThreadState threadState) {
    myPadding = padding;
    myParent = parent;
    myChild = child;
    myThreadState = threadState;
  }

  public int getPadding() {
    return myPadding;
  }

  public int getParent() {
    return myParent;
  }

  public int getChild() {
    return myChild;
  }

  public ConcurrencyThreadState getThreadState() {
    return myThreadState;
  }
}

