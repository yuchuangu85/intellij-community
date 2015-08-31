
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


import com.intellij.ui.JBColor;

import java.awt.*;

public class GraphSettings {
  public static int CELL_WIDTH = 1;
  public static int CELL_HEIGHT = 20;
  public static int INTERVAL = 20;
  public static int STROKE_WITH_LOCK = 4;
  public static int STROKE_BASIC = 4;

  public static int RULER_STROKE_WIDTH = 1;
  public static Color RULER_COLOR = JBColor.BLACK;
  public static int RULER_UNIT_WIDTH = 10;
  public static int RULER_UNIT_MARK = 10;
  public static int RULER_SUBUNIT_MARK = 5;


  public static Color BASIC_COLOR = new Color(125, 125, 125);
  public static Color LOCK_WAIT_COLOR = new Color(255, 179, 3);
  public static Color LOCK_OWNING_COLOR = new Color(120, 255, 0);
  public static Color DEADLOCK_COLOR = Color.RED;
  public static Color BACKGROUND_COLOR = JBColor.WHITE;
}
