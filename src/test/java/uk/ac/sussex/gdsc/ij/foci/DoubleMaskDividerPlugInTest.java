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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@SuppressWarnings({"javadoc"})
class DoubleMaskDividerPlugInTest {
  @Test
  void testCombineMasks() {
    // Mask 1: two classes; mask 2: two classes
    final int[] i1 = {0, 1, 2, 1001, 1002, 0};
    final int[] i2 = {0, 1, 1001, 1, 1001, 0};
    final int[] out = DoubleMaskDivider_PlugIn.combineMasks(i1, i2);
    Assertions.assertArrayEquals(new int[] {0, 1, 2001, 1001, 3001, 0}, out);
  }

  @Test
  void testCombineMasksWithBlockOf1000Values() {
    // Mask 1: class A has 1000 consecutive values; class B has a single value.
    // Mask 2: a single class.
    final int[] i1 = new int[1001];
    for (int i = 0; i < 1000; i++) {
      i1[i] = i + 1;
    }
    i1[1000] = 2000;
    final int[] i2 = new int[i1.length];
    Arrays.fill(i2, 1);
    final int[] out = DoubleMaskDivider_PlugIn.combineMasks(i1, i2);
    // Class A objects are within the first block
    for (int i = 0; i < 1000; i++) {
      Assertions.assertEquals(i + 1, out[i]);
    }
    // Class B is in the next block
    Assertions.assertEquals(2001, out[1000]);
  }

  @ParameterizedTest
  @CsvSource({"534, true", "535, false"})
  void testCombineMasksAtMaximumValue(int range, boolean valid) {
    // Mask 1: 65 classes with a single value and a class with values 200 to (200 + range).
    // Mask 2: a single class.
    // This creates 66 blocks of size 1000.
    final int[] i1 = new int[65 + range + 1];
    for (int i = 0; i < 65; i++) {
      i1[i] = 2 * i + 1;
    }
    for (int i = 0; i <= range; i++) {
      i1[65 + i] = 200 + i;
    }
    final int[] i2 = new int[i1.length];
    Arrays.fill(i2, 1);
    if (valid) {
      final int[] out = DoubleMaskDivider_PlugIn.combineMasks(i1, i2);
      Assertions.assertEquals(65535, Arrays.stream(out).max().getAsInt());
    } else {
      Assertions.assertThrows(IllegalArgumentException.class,
          () -> DoubleMaskDivider_PlugIn.combineMasks(i1, i2));
    }
  }
}
