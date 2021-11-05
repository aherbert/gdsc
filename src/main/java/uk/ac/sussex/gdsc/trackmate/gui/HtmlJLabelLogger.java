/*-
 * #%L
 * Genome Damage and Stability Centre ImageJ Plugins
 *
 * Software for microscopy image analysis
 * %%
 * Copyright (C) 2011 - 2020 Alex Herbert
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-3.0.html>.
 * #L%
 */

package uk.ac.sussex.gdsc.trackmate.gui;

import fiji.plugin.trackmate.Logger;
import fiji.plugin.trackmate.gui.Fonts;
import java.awt.Color;
import javax.swing.JLabel;

/**
 * Class to display error messages in a label on the TrackMate GUI using surrounding {@code <html>}
 * tags. Any new line {@code '\n'} is replaced with {@code <br/>}.
 *
 * <p>Adapted from {@code fiji.plugin.trackmate.util.JLabelLogger}.
 */
public class HtmlJLabelLogger extends JLabel {
  private static final long serialVersionUID = 1L;

  private final MyLogger logger;

  /**
   * Create an instance.
   */
  public HtmlJLabelLogger() {
    this.logger = new MyLogger(this);
    setFont(Fonts.SMALL_FONT);
  }

  /**
   * Gets the logger.
   *
   * @return the logger
   */
  public Logger getLogger() {
    return logger;
  }

  /**
   * Internal logger class.
   */
  private class MyLogger extends Logger {
    private final HtmlJLabelLogger label;

    MyLogger(HtmlJLabelLogger logger) {
      this.label = logger;
    }

    @Override
    public void log(String message, Color color) {
      label.setText("<html>"
          + message.replaceAll("<", "&lt;").replaceAll(">", "&gt;").replaceAll("\n", "<br/>")
          + "</html>");
      label.setForeground(color);
    }

    @Override
    public void error(String message) {
      log(message, Logger.ERROR_COLOR);
    }

    @Override
    public void setProgress(double val) {}

    @Override
    public void setStatus(String status) {
      log(status, Logger.BLUE_COLOR);
    }
  }
}
