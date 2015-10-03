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
package com.jetbrains.python.debugger.concurrency.tool.panels;

import com.intellij.ui.JBColor;
import com.jetbrains.python.debugger.concurrency.tool.ConcurrencyGraphSettings;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

public class ConcurrencyNotesPanel extends JPanel {
  private static final int HEIGHT = 30;
  private static final int VERTICAL_INTERVAL = 10;
  private static final int HORIZONTAL_INTERVAL = 10;
  private static final int RECT_EDGE = 10;
  private final String[] COLOR_NAMES = {"Running", "Waiting for lock", "Under lock", "Deadlock"};
  private final Color[] COLORS = {ConcurrencyGraphSettings.BASIC_COLOR, ConcurrencyGraphSettings.LOCK_WAIT_COLOR,
    ConcurrencyGraphSettings.LOCK_OWNING_COLOR, ConcurrencyGraphSettings.DEADLOCK_COLOR};

  public ConcurrencyNotesPanel(JPanel panel) {
    setPreferredSize(new Dimension(panel.getWidth(), HEIGHT));
    setBackground(JBColor.WHITE);
    setBorder(BorderFactory.createLineBorder(JBColor.border()));
  }

  @Override
  protected void paintComponent(@NotNull Graphics g) {
    super.paintComponent(g);
    Graphics2D g2 = (Graphics2D)g;
    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    FontMetrics metrics = g.getFontMetrics(getFont());
    int padding = HORIZONTAL_INTERVAL;
    for (int i = 0; i < COLOR_NAMES.length; ++i) {
      String text = COLOR_NAMES[i];
      int textWidth = metrics.stringWidth(text);
      int textHeight = metrics.getHeight();
      g2.setStroke(new BasicStroke(ConcurrencyGraphSettings.STROKE_BASIC));
      g2.setColor(COLORS[i]);
      g2.fillRect(padding, VERTICAL_INTERVAL, RECT_EDGE, RECT_EDGE);
      padding += RECT_EDGE + HORIZONTAL_INTERVAL;
      g2.setColor(JBColor.BLACK);
      g.drawString(text, padding, VERTICAL_INTERVAL + textHeight / 2);
      padding += textWidth + HORIZONTAL_INTERVAL;
    }
  }
}
