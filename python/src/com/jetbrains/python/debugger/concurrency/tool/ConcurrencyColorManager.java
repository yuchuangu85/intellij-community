
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


import java.awt.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

public class ConcurrencyColorManager {
  private final Map<String, Color> myColors;

  public ConcurrencyColorManager() {
    myColors = new HashMap<String, Color>();
  }

  private Color generateColor() {
    Random rand = new Random();
    float r = rand.nextFloat();
    float g = rand.nextFloat();
    float b = rand.nextFloat();
    return new Color(r, g, b);
  }

  public Color getItemColor(String itemId) {
    if (myColors.containsKey(itemId)) {
      return myColors.get(itemId);
    } else {
      Color color = generateColor();
      myColors.put(itemId, color);
      return color;
    }
  }
}
