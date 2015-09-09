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

import com.jetbrains.python.debugger.concurrency.model.ConcurrencyThreadState;

import java.awt.*;

public class ConcurrencyRenderingUtil {

  public static void prepareStroke(Graphics g, ConcurrencyThreadState threadState) {
    Graphics2D g2 = (Graphics2D)g;
    switch (threadState) {
      case Run:
        g2.setStroke(new BasicStroke(ConcurrencyGraphSettings.STROKE_BASIC));
        g2.setColor(ConcurrencyGraphSettings.BASIC_COLOR);
        break;
      case Stopped:
        break;
      case LockWait:
        g2.setStroke(new BasicStroke(ConcurrencyGraphSettings.STROKE_BASIC));
        g2.setColor(ConcurrencyGraphSettings.LOCK_WAIT_COLOR);
        break;
      case LockOwn:
        g2.setStroke(new BasicStroke(ConcurrencyGraphSettings.STROKE_BASIC));
        g2.setColor(ConcurrencyGraphSettings.LOCK_OWNING_COLOR);
        break;
      case Deadlock:
        g2.setStroke(new BasicStroke(ConcurrencyGraphSettings.STROKE_BASIC));
        g2.setColor(ConcurrencyGraphSettings.DEADLOCK_COLOR);
        break;
    }
  }
}
