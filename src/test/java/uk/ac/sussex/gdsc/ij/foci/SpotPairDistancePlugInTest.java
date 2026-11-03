/*-
 * #%L
 * Genome Damage and Stability Centre ImageJ Plugins
 *
 * Software for microscopy image analysis
 * %%
 * Copyright (C) 2011 - 2026 Alex Herbert
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

package uk.ac.sussex.gdsc.ij.foci;

import ij.process.FloatProcessor;
import java.awt.Rectangle;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import uk.ac.sussex.gdsc.ij.foci.SpotPairDistance_PlugIn.SpotPairDistancePluginTool;

@SuppressWarnings({"javadoc"})
class SpotPairDistancePlugInTest {
  @ParameterizedTest
  @CsvSource({
      // x, y, range, expected region x, y, width, height
      "2, 2, 1, 1, 1, 3, 3", "2, 2, 5, 0, 0, 5, 5", "0, 2, 2, 0, 0, 1, 5", "4, 0, 2, 4, 0, 1, 1",
      // Narrow image: both edges are within range
      "0, 2, 3, 0, 0, 1, 5", "1, 2, 3, 0, 0, 3, 5"})
  void testComRegion(int x, int y, int range, int rx, int ry, int rw, int rh) {
    final int width = x == 1 ? 3 : 5;
    final FloatProcessor fp = new FloatProcessor(width, 5);
    fp.setf(x, y, 1);
    final Rectangle rect = new Rectangle();
    final double[] com = SpotPairDistancePluginTool.com(fp, y * width + x, range, rect);
    Assertions.assertEquals(new Rectangle(rx, ry, rw, rh), rect);
    Assertions.assertArrayEquals(new double[] {x + 0.5, y + 0.5}, com);
  }

  @Test
  void testComRegionInNarrowImage() {
    // Image is narrower than 2 * range + 1 and the maximum is at the left edge
    final FloatProcessor fp = new FloatProcessor(2, 3);
    fp.setf(0, 1, 1);
    fp.setf(1, 1, 1);
    final Rectangle rect = new Rectangle();
    final double[] com = SpotPairDistancePluginTool.com(fp, 1 * 2 + 0, 2, rect);
    Assertions.assertEquals(new Rectangle(0, 0, 1, 3), rect);
    Assertions.assertArrayEquals(new double[] {0.5, 1.5}, com);
  }

  @ParameterizedTest
  @CsvSource({
      // com2 x, y, expected angle, relX, relY
      "3, 0, 0, 3, 0", "-3, 0, 180, -3, 0", "0, 2, 90, 0, -2", "0, -2, -90, 0, 2",
      "1, 1, 45, 1, -1"})
  void testComputeRelativeOrientation(double x, double y, double angle, double relX,
      double relY) {
    // Orientation line along the x-axis from com1
    final double[] line = {0, 0, 1, 0};
    final double[] rel = SpotPairDistancePluginTool.computeRelativeOrientation(line,
        new double[] {0, 0}, new double[] {x, y});
    Assertions.assertEquals(angle, rel[0], 1e-10, "angle");
    Assertions.assertEquals(relX, rel[1], 1e-10, "relX");
    Assertions.assertEquals(relY, rel[2], 1e-10, "relY");
  }
}
