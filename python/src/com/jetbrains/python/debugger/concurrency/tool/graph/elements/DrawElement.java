
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
package com.jetbrains.python.debugger.concurrency.tool.graph.elements;

import com.jetbrains.python.debugger.concurrency.tool.graph.states.ThreadState;

import java.awt.*;

public abstract class DrawElement {

  protected Color myColor;
  protected ThreadState myBefore;
  protected ThreadState myAfter;

  public DrawElement(Color color, ThreadState before, ThreadState after) {
    myColor = color;
    myBefore = before;
    myAfter = after;
  }

  public ThreadState getBefore() {
    return myBefore;
  }

  public ThreadState getAfter() {
    return myAfter;
  }

  @Override
  public boolean equals(Object element) {
    if (!(element instanceof DrawElement)) {
      return false;
    }
    DrawElement other = (DrawElement)element;
    return (this.getBefore().getClass() == other.getBefore().getClass()) &&
           (this.getAfter().getClass() == other.getAfter().getClass());
  }

  public void setAfter(ThreadState state) {
    myAfter = state;
  }

  public abstract DrawElement getNextElement();

  public void drawElement(Graphics g, int padding) {
  }

  public Color getColor() {
    return myColor;
  }

  public void setColor(Color color) {
    myColor = color;
  }
}
