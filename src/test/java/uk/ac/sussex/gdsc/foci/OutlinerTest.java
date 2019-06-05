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

package uk.ac.sussex.gdsc.foci;

import uk.ac.sussex.gdsc.core.utils.MathUtils;

import ij.gui.PolygonRoi;
import ij.gui.Roi;
import ij.process.ByteProcessor;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.awt.Polygon;
import java.awt.Rectangle;

@SuppressWarnings({"javadoc"})
public class OutlinerTest {
  // Directions must match the outliner
  private static final int[] DIR_X_OFFSET = {-1, -1, 0, 1, 1, 1, 0, -1};
  private static final int[] DIR_Y_OFFSET = {0, -1, -1, -1, 0, 1, 1, 1};

  private static final int LEFT = Outliner.DIR_LEFT;
  private static final int UP = Outliner.DIR_UP;
  private static final int RIGHT = Outliner.DIR_RIGHT;
  private static final int DOWN = Outliner.DIR_DOWN;

  @Test
  public void check8ConnectedProperty() {
    final ByteProcessor bp = new ByteProcessor(1, 1);
    Outliner outliner = new Outliner(bp);
    Assertions.assertFalse(outliner.isEightConnected());
    outliner = new Outliner(bp, true);
    Assertions.assertTrue(outliner.isEightConnected());
    outliner.setEightConnected(false);
    Assertions.assertFalse(outliner.isEightConnected());
  }

  @Test
  public void checkSinglePixelOutline() {
    assertOutline(new Roi(3, 4, 1, 1));
  }

  @Test
  public void checkSinglePixelOutlineAtEdge() {
    assertSinglePixelOutline(3, 3, 0, 0);
    assertSinglePixelOutline(3, 3, 0, 1);
    assertSinglePixelOutline(3, 3, 0, 2);
    assertSinglePixelOutline(3, 3, 1, 0);
    assertSinglePixelOutline(3, 3, 1, 2);
    assertSinglePixelOutline(3, 3, 2, 0);
    assertSinglePixelOutline(3, 3, 2, 1);
    assertSinglePixelOutline(3, 3, 2, 2);
  }

  @Test
  public void checkSinglePixelInside() {
    assertSinglePixelOutline(3, 3, 1, 1);
  }

  @Test
  public void checkSquareOutline() {
    assertOutline(new Roi(3, 4, 2, 2));
  }

  @Test
  public void checkSquareOutlineFromDifferentStartOrigins() {
    assertOutline(new Roi(3, 4, 2, 2), 4, 4);
    assertOutline(new Roi(3, 4, 2, 2), 4, 5);
    assertOutline(new Roi(3, 4, 2, 2), 3, 5);
  }

  @Test
  public void checkSquareOutlineEntireImage() {
    assertOutline(new Roi(0, 0, 2, 2));
  }

  @Test
  public void checkRectangleOutline() {
    assertOutline(new Roi(3, 4, 5, 8));
  }

  @Test
  public void checkHorizontalLineOutline() {
    assertOutline(new Roi(3, 4, 5, 1));
  }

  @Test
  public void checkVerticalLineOutline() {
    assertOutline(new Roi(3, 4, 1, 5));
  }

  @Test
  public void checkDiagonalLine1Outline() {
    // @formatter:off
    // +---+
    // | X |
    // +---+---+
    //     |   |
    //     +---+
    // @formatter:on
    assertOutline(buildPolygon(3, 4, RIGHT, DOWN, RIGHT, DOWN, LEFT, UP, LEFT), true);
  }

  @Test
  public void checkDiagonalLine2Outline() {
    // @formatter:off
    //     +---+
    //     | X |
    // +---+---+
    // |   |
    // +---+
    // @formatter:on
    assertOutline(buildPolygon(3, 4, RIGHT, DOWN, LEFT, DOWN, LEFT, UP, RIGHT), true);
  }

  @Test
  public void checkDiagonalLine3Outline() {
    // @formatter:off
    //     +---+
    //     |   |
    // +---+---+
    // | X |
    // +---+
    // @formatter:on
    assertOutline(buildPolygon(3, 4, RIGHT, UP, RIGHT, DOWN, LEFT, DOWN, LEFT), true);
  }

  @Test
  public void checkDiagonalLine4Outline() {
    // @formatter:off
    // +---+
    // | X |
    // +---+---+
    //     |   |
    //     +---+
    // @formatter:on
    assertOutline(buildPolygon(0, 0, RIGHT, DOWN, RIGHT, DOWN, LEFT, UP, LEFT), true);
  }

  @Test
  public void checkDiagonalLine5Outline() {
    // @formatter:off
    //     +---+
    //     | X |
    // +---+---+
    // | X |
    // +---+
    // @formatter:on
    assertOutline(buildPolygon(0, 1, RIGHT, UP, RIGHT, DOWN, LEFT, DOWN, LEFT), true);
  }

  @Test
  public void checkShape1Outline() {
    // @formatter:off
    //     +---+---+
    //     |   |   |
    // +---+---+---+
    // | X |
    // +---+
    // @formatter:on
    assertOutline(buildPolygon(3, 4, RIGHT, UP, RIGHT, RIGHT, DOWN, LEFT, LEFT, DOWN, LEFT), true);
  }

  @Test
  public void checkShape1Outline4Connected() {
    // @formatter:off
    //     +---+---+
    //     |   |   |
    // +---+---+---+
    // | X |
    // +---+
    // @formatter:on
    assertOutline(buildPolygon(3, 4, RIGHT, UP, RIGHT, RIGHT, DOWN, LEFT, LEFT, DOWN, LEFT),
        new Roi(3, 4, 1, 1), false);
  }

  @Test
  public void checkShape2Outline() {
    // @formatter:off
    //     +---+---+
    //     | X |   |
    // +---+---+---+
    // |   |
    // +---+
    // @formatter:on
    assertOutline(buildPolygon(3, 4, RIGHT, RIGHT, DOWN, LEFT, LEFT, DOWN, LEFT, UP, RIGHT), true);
  }

  @Test
  public void checkShape2Outline4Connected() {
    // @formatter:off
    //     +---+---+
    //     | X |   |
    // +---+---+---+
    // |   |
    // +---+
    // @formatter:on
    assertOutline(buildPolygon(3, 4, RIGHT, RIGHT, DOWN, LEFT, LEFT, DOWN, LEFT, UP, RIGHT),
        new Roi(3, 4, 2, 1), false);
  }

  @Test
  public void checkShape3Outline() {
    // @formatter:off
    //     +---+---+
    //     | X |   |
    // +---+---+---+
    // |   |   |   |
    // +---+   +---+
    // @formatter:on
    assertOutline(
        buildPolygon(3, 4, RIGHT, RIGHT, DOWN, DOWN, LEFT, UP, LEFT, DOWN, LEFT, UP, RIGHT), true);
  }

  @Test
  public void checkShape3Outline4Connected() {
    // @formatter:off
    //     +---+---+
    //     | X |   |
    // +---+---+---+
    // |   |   |   |
    // +---+   +---+
    // @formatter:on
    assertOutline(
        buildPolygon(3, 4, RIGHT, RIGHT, DOWN, DOWN, LEFT, UP, LEFT, DOWN, LEFT, UP, RIGHT),
        buildPolygon(3, 4, RIGHT, RIGHT, DOWN, DOWN, LEFT, UP, LEFT), false);
  }

  @Test
  public void checkShape4Outline() {
    // This has a hole in it that will be jumped around with 8-connected.
    // @formatter:off
    // +---+---+---+
    // | X | x | x |
    // +---+---+---+
    // | x |   | x |
    // +---+---+---+
    // | x | x |
    // +---+---+
    // @formatter:on
    assertOutline(
        buildPolygon(3, 4, RIGHT, RIGHT, RIGHT, DOWN, DOWN, LEFT, UP, LEFT, DOWN, RIGHT, DOWN, LEFT,
            LEFT),
        buildPolygon(3, 4, RIGHT, RIGHT, RIGHT, DOWN, DOWN, LEFT, DOWN, LEFT, LEFT), true);
  }

  @Test
  public void checkShape4Outline4Connected() {
    // This has a hole in it that will force internal outlining with 4-connected.
    // @formatter:off
    // +---+---+---+
    // | X | x | x |
    // +---+---+---+
    // | x |   | x |
    // +---+---+---+
    // | x | x |
    // +---+---+
    // @formatter:on
    assertOutline(buildPolygon(3, 4, RIGHT, RIGHT, RIGHT, DOWN, DOWN, LEFT, UP, LEFT, DOWN, RIGHT,
        DOWN, LEFT, LEFT), false);
  }


  @Test
  public void checkShape5Outline() {
    // Have lots of edges
    // @formatter:off
    // +---+
    // | X |
    // +---+---+
    //     |   |
    //     +---+---+
    //         |   |
    //         +---+---+
    //             |   |
    //             +---+---+
    //                 |   |
    //                 +---+
    // @formatter:on
    assertOutline(buildPolygon(0, 0, RIGHT, DOWN, RIGHT, DOWN, RIGHT, DOWN, RIGHT, DOWN, RIGHT,
        DOWN, LEFT, UP, LEFT, UP, LEFT, UP, LEFT, UP, LEFT), true);
  }

  /**
   * Builds the polygon ROI starting from the origin.
   *
   * @param ox the x origin
   * @param oy the y origin
   * @param directions the directions
   * @return the roi
   */
  private static Polygon buildPolygon(int ox, int oy, int... directions) {
    final int[] xpoints = new int[directions.length + 1];
    final int[] ypoints = new int[directions.length + 1];
    int count = 0;
    int xp = ox;
    int yp = oy;
    int lastD = directions[0] - 1;
    for (final int d : directions) {
      if (lastD != d) {
        // Direction change so add the turn point
        xpoints[count] = xp;
        ypoints[count] = yp;
        count++;
        lastD = d;
      }
      xp += DIR_X_OFFSET[d];
      yp += DIR_Y_OFFSET[d];
    }
    // Final point
    xpoints[count] = xp;
    ypoints[count] = yp;
    count++;
    return new Polygon(xpoints, ypoints, count);
  }

  /**
   * Assert that an image filled with a single pixel ROI can be outlined.
   *
   * @param width the width
   * @param height the height
   * @param x the x
   * @param y the y
   */
  private static void assertSinglePixelOutline(int width, int height, int x, int y) {
    final ByteProcessor bp = new ByteProcessor(width, height);
    bp.setValue(255.0);
    final Roi roi = new Roi(x, y, 1, 1);
    bp.fill(roi);

    final Outliner outliner = new Outliner(bp);
    final Roi outline = outliner.outline(x, y);

    Assertions.assertNotNull(outline);

    Assertions.assertEquals(roi.getBounds(), outline.getBounds(), "Bounds do not match");
  }

  /**
   * Assert that an image filled with the ROI can be outlined.
   *
   * @param roi the roi
   * @param eightConnected the eight connected flag
   */
  private static void assertOutline(Roi roi) {
    final Rectangle bounds = roi.getBounds();
    assertOutline(roi, bounds.x, bounds.y);
  }

  /**
   * Assert that an image filled with the ROI can be outlined.
   *
   * @param roi the roi
   * @param ox the x origin of the outline search
   * @param oy the y origin of the outline search
   */
  private static void assertOutline(Roi roi, int ox, int oy) {
    final Rectangle bounds = roi.getBounds();
    final int width = bounds.x + bounds.width;
    final int height = bounds.y + bounds.height;

    final ByteProcessor bp = new ByteProcessor(width, height);
    bp.setValue(255.0);
    bp.fill(roi);

    final Outliner outliner = new Outliner(bp);
    final Roi outline = outliner.outline(ox, oy);

    Assertions.assertNotNull(outline);

    Assertions.assertEquals(bounds, outline.getBounds(), "Bounds do not match");
  }

  /**
   * Assert that an image filled with the polygon ROI can be outlined.
   *
   * @param poly the polygon
   * @param eightConnected the eight connected flag
   */
  private static void assertOutline(Polygon poly, boolean eightConnected) {
    assertOutline(poly, poly, eightConnected);
  }

  /**
   * Assert that an image filled with the polygon ROI can be outlined.
   *
   * @param poly the polygon
   * @param expected the expected polygon
   * @param eightConnected the eight connected flag
   */
  private static void assertOutline(Polygon poly, Polygon expected, boolean eightConnected) {
    final int width = MathUtils.max(poly.xpoints);
    final int height = MathUtils.max(poly.ypoints);

    final ByteProcessor bp = new ByteProcessor(width, height);
    bp.setValue(255.0);
    bp.fill(new PolygonRoi(poly, Roi.FREEROI));

    final Outliner outliner = new Outliner(bp, eightConnected);
    final Roi outline = outliner.outline(poly.xpoints[0], poly.ypoints[0]);

    Assertions.assertTrue(outline instanceof PolygonRoi, "Not a polygon ROI");

    final Polygon observed = outline.getPolygon();
    Assertions.assertEquals(expected.npoints, observed.npoints,
        "number of coordinates do not match");
    Assertions.assertArrayEquals(expected.xpoints, observed.xpoints, "X coordinates do not match");
    Assertions.assertArrayEquals(expected.ypoints, observed.ypoints, "Y coordinates do not match");
  }

  /**
   * Assert that an image filled with the polygon ROI can be outlined.
   *
   * @param poly the polygon
   * @param expected the expected polygon
   * @param eightConnected the eight connected flag
   */
  private static void assertOutline(Polygon poly, Roi expected, boolean eightConnected) {
    final int width = MathUtils.max(poly.xpoints);
    final int height = MathUtils.max(poly.ypoints);

    final ByteProcessor bp = new ByteProcessor(width, height);
    bp.setValue(255.0);
    bp.fill(new PolygonRoi(poly, Roi.FREEROI));

    final Outliner outliner = new Outliner(bp, eightConnected);
    final Roi outline = outliner.outline(poly.xpoints[0], poly.ypoints[0]);

    Assertions.assertNotNull(outline);

    Assertions.assertEquals(expected.getBounds(), outline.getBounds(), "Bounds do not match");
  }
}
