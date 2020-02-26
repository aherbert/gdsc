/*-
 * #%L
 * Genome Damage and Stability Centre ImageJ Plugins
 *
 * Software for microscopy image analysis
 * %%
 * Copyright (C) 2011 - 2019 Alex Herbert
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

package uk.ac.sussex.gdsc.ij.gui;

import ij.ImagePlus;
import ij.gui.PointRoi;
import ij.gui.Roi;
import ij.process.FloatPolygon;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Polygon;
import java.util.Arrays;
import java.util.Objects;

/**
 * Extend the {@link PointRoi} class to allow custom number labels for each point.
 *
 * <p>This has limited functionality for drawing ROI points. They are drawn using the default size
 * and appearance no matter what shape has been specified.
 */
public class LabelledPointRoi extends PointRoi {
  private static final long serialVersionUID = 20181207;
  private static final int FONT_SIZE = 9;
  private static final Font font = new Font("SansSerif", Font.PLAIN, FONT_SIZE);

  private int[] labels;

  /**
   * Instantiates a new point roi 2.
   *
   * @param ox the ox
   * @param oy the oy
   * @param points the points
   */
  public LabelledPointRoi(int[] ox, int[] oy, int points) {
    super(ox, oy, points);
  }

  /**
   * Creates a new PointRoi2 using the specified float arrays of offscreen coordinates.
   *
   * @param ox the ox
   * @param oy the oy
   * @param points the points
   */
  public LabelledPointRoi(float[] ox, float[] oy, int points) {
    super(ox, oy, points);
  }

  /**
   * Creates a new PointRoi2 from a FloatPolygon.
   *
   * @param poly the polygon
   */
  public LabelledPointRoi(FloatPolygon poly) {
    super(poly);
  }

  /**
   * Creates a new PointRoi2 from a Polygon.
   *
   * @param poly the polygon
   */
  public LabelledPointRoi(Polygon poly) {
    super(poly);
  }

  /**
   * Creates a new PointRoi2 using the specified offscreen int coordinates.
   *
   * @param ox the ox
   * @param oy the oy
   */
  public LabelledPointRoi(int ox, int oy) {
    super(ox, oy);
  }

  /**
   * Creates a new PointRoi2 using the specified offscreen double coordinates.
   *
   * @param ox the ox
   * @param oy the oy
   */
  public LabelledPointRoi(double ox, double oy) {
    super(ox, oy);
  }

  /**
   * Creates a new PointRoi2 using the specified screen coordinates.
   *
   * @param sx the sx
   * @param sy the sy
   * @param imp the image
   */
  public LabelledPointRoi(int sx, int sy, ImagePlus imp) {
    super(sx, sy, imp);
  }

  /**
   * Draws the points on the image.
   *
   * @param graphics the graphics
   */
  @Override
  public void draw(Graphics graphics) {
    updatePolygon();
    final int size2 = HANDLE_SIZE / 2;
    final boolean showLabels = getShowLabels() && nPoints > 1;
    if (showLabels) {
      graphics.setFont(font);
    }
    for (int i = 0; i < nPoints; i++) {
      final int label = (showLabels) ? getLabel(i) : 0;
      drawSimplePoint(graphics, xp2[i] - size2, yp2[i] - size2, label);
    }
    if (updateFullWindow) {
      updateFullWindow = false;
      imp.draw();
    }
  }

  private int getLabel(int index) {
    return (labels != null) ? labels[index] : index + 1;
  }

  /**
   * Draw the point.
   *
   * <p>The label is only drawn if non zero.
   *
   * @param graphics the graphics
   * @param x the x
   * @param y the y
   * @param label the label
   */
  private void drawSimplePoint(Graphics graphics, int x, int y, int label) {
    graphics.setColor(fillColor != null ? fillColor : Color.white);
    graphics.drawLine(x - 4, y + 2, x + 8, y + 2);
    graphics.drawLine(x + 2, y - 4, x + 2, y + 8);
    graphics.setColor(strokeColor != null ? strokeColor : ROIColor);
    graphics.fillRect(x + 1, y + 1, 3, 3);
    if (label > 0) {
      graphics.drawString("" + label, x + 6, y + FONT_SIZE + 4);
    }
    graphics.setColor(Color.black);
    graphics.drawRect(x, y, 4, 4);
  }

  /**
   * Set the labels to use for each point. Default uses numbers counting from 1.
   *
   * @param labels the new labels
   */
  public void setLabels(int[] labels) {
    if (labels != null && labels.length >= nPoints) {
      this.labels = Arrays.copyOf(labels, nPoints);
    }
  }

  @Override
  public boolean equals(Object object) {
    // At present only ij.gui.Roi implements the equals() method.
    // It's logic is repeated here for clarity.
    // It tests the type, bounds and length.
    if (object == this) {
      return true;
    }
    if (!(object instanceof Roi)) {
      return false;
    }

    final Roi roi2 = (Roi) object;
    if (getType() != roi2.getType()) {
      return false;
    }
    if (!getBounds().equals(roi2.getBounds())) {
      return false;
    }
    return getLength() == roi2.getLength();
  }

  @Override
  public int hashCode() {
    // Added to comply with the java contract.
    // Note: ij.gui.Roi does not implement hashCode().
    return Objects.hash(getType(), getBounds(), getLength());
  }
}
