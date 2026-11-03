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
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@SuppressWarnings({"javadoc"})
class SpotDensityPlugInTest {
  @ParameterizedTest
  @CsvSource({"10, 1, 10", "10, 3, 4", "1, 1, 1", "1, 0.1, 10", "0.3, 0.1, 3", "0.7, 0.1, 7",
      "2.1, 0.7, 3", "2.1, 0.3, 7", "10.001, 1, 10", "10.0011, 1, 11"})
  void testGetNumberOfBins(double distance, double interval, int expected) {
    Assertions.assertEquals(expected, SpotDensity_PlugIn.getNumberOfBins(distance, interval));
  }

  @Test
  void testGetBin() {
    Assertions.assertEquals(0, SpotDensity_PlugIn.getBin(0, 3, 4));
    Assertions.assertEquals(2, SpotDensity_PlugIn.getBin(8.99, 3, 4));
    Assertions.assertEquals(3, SpotDensity_PlugIn.getBin(9.99, 3, 4));
    // Clipped to the last bin
    Assertions.assertEquals(3, SpotDensity_PlugIn.getBin(12, 3, 4));
  }

  @Test
  void testGetEdgeDistance() {
    final FloatProcessor fp = new FloatProcessor(3, 2);
    fp.setf(2, 1, 5);
    Assertions.assertEquals(5, SpotDensity_PlugIn.getEdgeDistance(fp, 2, 1));
    Assertions.assertEquals(0, SpotDensity_PlugIn.getEdgeDistance(fp, 3, 1));
    Assertions.assertEquals(0, SpotDensity_PlugIn.getEdgeDistance(fp, 2, 2));
    Assertions.assertEquals(0, SpotDensity_PlugIn.getEdgeDistance(fp, -1, 0));
    Assertions.assertEquals(0, SpotDensity_PlugIn.getEdgeDistance(fp, 0, -1));
  }

  @Test
  void testComputePairCorrelationWithPartialRing() {
    // Points on an integer lattice have a density of 1. Count the lattice points around the
    // origin in rings of width 3 up to a distance of 10.
    final double distance = 10;
    final double interval = 3;
    final int nbins = SpotDensity_PlugIn.getNumberOfBins(distance, interval);
    final int[] counts = new int[nbins];
    for (int y = -10; y <= 10; y++) {
      for (int x = -10; x <= 10; x++) {
        final double d = Math.sqrt(x * x + y * y);
        if (d > 0 && d < distance) {
          counts[SpotDensity_PlugIn.getBin(d, interval, nbins)]++;
        }
      }
    }
    final double[] radii = new double[nbins + 1];
    for (int i = 0; i < nbins; i++) {
      radii[i] = i * interval;
    }
    radii[nbins] = distance;
    final double[] pc = SpotDensity_PlugIn.computePairCorrelation(counts, 1, radii);
    // The outer rings approximate the uniform density; the final ring is truncated at the
    // distance and is not biased low.
    for (int i = 1; i < nbins; i++) {
      final double g = pc[i];
      Assertions.assertEquals(1, g, 0.15, () -> "g(r) = " + g);
    }
  }
}
