/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.ui;

import com.intellij.ui.paint.RectanglePainter;
import com.intellij.util.ui.JBEmptyBorder;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.font.FontRenderContext;

public class LabelUtil {
  @NotNull
  public static JLabel createLabelWithRoundCorners(@NotNull String text,
                                                   @Nullable Color fg,
                                                   @Nullable final Color bg,
                                                   float fontScale,
                                                   int roundWidth,
                                                   int borderVOffset,
                                                   int borderHOffset) {
    JBEmptyBorder emptyBorder = JBUI.Borders.empty(borderVOffset, borderHOffset);
    JLabel label = new JLabel(text) {
      @Override
      protected void paintComponent(Graphics g) {
        if (g instanceof Graphics2D) {
          Graphics2D g2d = (Graphics2D)g;
          g2d.setColor(bg);
          int textHeight = LabelUtil.getHeight(getText(), g2d);
          Insets borderInsets = emptyBorder.getBorderInsets();
          int baseline = getBaseline(getWidth(), getHeight());
          RectanglePainter.FILL.paint(g2d, 0, baseline - textHeight - borderInsets.top, getWidth(),
                                      textHeight + borderInsets.top + borderInsets.bottom, roundWidth);
          super.paintComponent(g);
        }
      }
    };
    Font labelFont = label.getFont();
    label.setFont(labelFont.deriveFont(labelFont.getSize() * fontScale));
    label.setForeground(fg);
    label.setBorder(emptyBorder);
    return label;
  }

  private static int getHeight(@NotNull String text, @NotNull Graphics2D g2d) {
    FontRenderContext frc = g2d.getFontRenderContext();
    int height = g2d.getFont().createGlyphVector(frc, text).getPixelBounds(frc, 0, 0).height;
    return UIUtil.isRetina(g2d) ? (int)Math.ceil(height / 2) : height;
  }
}
