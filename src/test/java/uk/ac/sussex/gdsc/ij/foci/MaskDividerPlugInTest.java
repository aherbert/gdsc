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

import java.util.Arrays;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@SuppressWarnings({"javadoc"})
class MaskDividerPlugInTest {
  @ParameterizedTest
  @CsvSource({"1, 1, 1001", "999, 2, 1001", "1000, 2, 1001", "1001, 2, 2001", "2500, 3, 3001"})
  void testCreateSegregatedValues(int excluded, int included, int firstInclude) {
    // Objects: mask value, count, mean. Excluded objects have a mean below the cut-off.
    final int cutoff = 10;
    final int size = excluded + included;
    final double[][] objects = new double[size][];
    for (int i = 0; i < size; i++) {
      objects[i] = new double[] {i + 1, 1, i < excluded ? cutoff - 1 : cutoff};
    }
    final int[] exclude = {-1};
    final int[] values = MaskDivider_PlugIn.createSegregatedValues(objects, size, cutoff, exclude);
    Assertions.assertEquals(excluded, exclude[0]);
    // Excluded objects numbered from 1
    for (int i = 1; i <= excluded; i++) {
      Assertions.assertEquals(i, values[i]);
    }
    // Included objects numbered from the offset
    for (int i = 1; i <= included; i++) {
      Assertions.assertEquals(firstInclude + i - 1, values[excluded + i]);
    }
    // All values are unique
    Assertions.assertEquals(size, Arrays.stream(values).skip(1).distinct().count());
  }
}
