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

import ij.process.ByteProcessor;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import uk.ac.sussex.gdsc.core.utils.Statistics;
import uk.ac.sussex.gdsc.ij.foci.SpotRadialIntensity_PlugIn.Foci;

@SuppressWarnings({"javadoc"})
class SpotRadialIntensityPlugInTest {
  @ParameterizedTest
  @CsvSource({"10, 1, true", "10, 5, true", "10, 6, false", "10, 20, false", "10, 0, false",
      "10, -1, false", "0, 1, false", "10, NaN, false", "10, Infinity, false"})
  void testIsValidDistance(int distance, double interval, boolean expected) {
    Assertions.assertEquals(expected,
        SpotRadialIntensity_PlugIn.isValidDistance(distance, interval));
  }

  @Test
  void testCreateResultsHeader() {
    final List<String> header =
        Arrays.asList(SpotRadialIntensity_PlugIn.createResultsHeader(10, 3).split("\t"));
    Assertions.assertEquals(Arrays.asList("Image", "ID", "Object", "Background", "x", "y",
        "Av 0.0-3.0", "Av 3.0-6.0", "Av 6.0-10.0", "N 0.0-3.0", "N 3.0-6.0", "N 6.0-10.0",
        "D 0.0-3.0", "D 3.0-6.0", "D 6.0-10.0"),
        header);
  }

  @Test
  void testCreateFoci() {
    final ByteProcessor bp = new ByteProcessor(5, 4);
    bp.set(1, 2, 3);
    final ObjectAnalyzer oa = new ObjectAnalyzer(bp);
    final Foci f = SpotRadialIntensity_PlugIn.createFoci(oa, 7, 1, 2);
    Assertions.assertNotNull(f);
    Assertions.assertEquals(7, f.id);
    Assertions.assertEquals(1, f.object);
    Assertions.assertNull(SpotRadialIntensity_PlugIn.createFoci(oa, 7, 0, 0));
    Assertions.assertNull(SpotRadialIntensity_PlugIn.createFoci(oa, 7, -1, 2));
    Assertions.assertNull(SpotRadialIntensity_PlugIn.createFoci(oa, 7, 5, 2));
    Assertions.assertNull(SpotRadialIntensity_PlugIn.createFoci(oa, 7, 1, 4));
    Assertions.assertNull(SpotRadialIntensity_PlugIn.createFoci(oa, 7, 30, 30));
  }

  @Test
  void testSegmentMaskWithFociAtObjectCentre() {
    // Object 1: 5x5 square with a spot at the centre (no segment).
    // Object 2: 5x5 square with a spot at the edge.
    final ByteProcessor bp = new ByteProcessor(20, 10);
    bp.setValue(1);
    bp.setRoi(1, 1, 5, 5);
    bp.fill();
    bp.setValue(2);
    bp.setRoi(10, 1, 5, 5);
    bp.fill();
    bp.resetRoi();
    final ObjectAnalyzer oa = new ObjectAnalyzer(bp);
    final int[] mask = oa.getObjectMask().clone();
    final Foci[] foci = {SpotRadialIntensity_PlugIn.createFoci(oa, 1, 3, 3),
        SpotRadialIntensity_PlugIn.createFoci(oa, 2, 10, 3)};
    SpotRadialIntensity_PlugIn.segmentMask(mask, oa, oa.getObjectCentres(), foci, 1);
    final int[] original = oa.getObjectMask();
    int count1 = 0;
    int count2 = 0;
    for (int i = 0; i < mask.length; i++) {
      if (original[i] == 1) {
        // Object 1 is not restricted
        Assertions.assertEquals(1, mask[i]);
        count1++;
      } else if (mask[i] == 2) {
        count2++;
      }
    }
    Assertions.assertEquals(25, count1);
    // Object 2 is restricted to the segment
    final int size2 = count2;
    Assertions.assertTrue(size2 > 0 && size2 < 25, () -> "Object 2 size " + size2);
  }

  @Test
  void testGetMean() {
    final Statistics stats = new Statistics();
    Assertions.assertEquals(0, SpotRadialIntensity_PlugIn.getMean(stats));
    stats.add(2);
    stats.add(-5);
    Assertions.assertEquals(-1.5, SpotRadialIntensity_PlugIn.getMean(stats));
  }

  @Test
  void testCreateResultsTitle() {
    Assertions.assertEquals("Spot Radial Intensity Summary (Distance=10, Interval=1.0)",
        SpotRadialIntensity_PlugIn.createResultsTitle(10, 1));
    Assertions.assertEquals("Spot Radial Intensity Summary (Distance=10, Interval=2.5)",
        SpotRadialIntensity_PlugIn.createResultsTitle(10, 2.5));
    Assertions.assertNotEquals(SpotRadialIntensity_PlugIn.createResultsTitle(10, 1),
        SpotRadialIntensity_PlugIn.createResultsTitle(20, 1));
  }
}
