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

package uk.ac.sussex.gdsc.ij.threshold;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import uk.ac.sussex.gdsc.core.data.ComputationException;

@SuppressWarnings({"javadoc"})
class MultiOtsuThresholdPlugInTest {
  @Test
  void testCalculateThresholds() {
    // Two populations of values
    final int[] data = new int[256];
    data[50] = 100;
    data[200] = 100;
    final int[] thresholds = new MultiOtsuThreshold_PlugIn().calculateThresholds(data, 2);
    Assertions.assertEquals(2, thresholds.length);
    Assertions.assertTrue(thresholds[1] >= 50 && thresholds[1] < 200,
        () -> "Threshold should separate the values: " + thresholds[1]);
  }

  @Test
  void testCalculateThresholdsWithNoDataThrows() {
    // Zero is ignored by default so this histogram has no values
    final int[] data = new int[256];
    data[0] = 100;
    Assertions.assertThrows(ComputationException.class,
        () -> new MultiOtsuThreshold_PlugIn().calculateThresholds(data, 2));
    final int[] empty = new int[256];
    Assertions.assertThrows(ComputationException.class,
        () -> new MultiOtsuThreshold_PlugIn().calculateThresholds(empty, 2));
  }
}
