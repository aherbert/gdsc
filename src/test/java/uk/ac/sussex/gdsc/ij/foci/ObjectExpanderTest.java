/*-
 * #%L
 * Genome Damage and Stability Centre ImageJ Plugins
 *
 * Software for microscopy image analysis
 * %%
 * Copyright (C) 2011 - 2025 Alex Herbert
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
import org.apache.commons.rng.UniformRandomProvider;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import uk.ac.sussex.gdsc.ij.foci.ObjectExpander.FrequencySelecter;
import uk.ac.sussex.gdsc.test.junit5.SeededTest;
import uk.ac.sussex.gdsc.test.rng.RngFactory;
import uk.ac.sussex.gdsc.test.utils.RandomSeed;

@SuppressWarnings({"javadoc"})
class ObjectExpanderTest {
  @Test
  void checkFrequencySelecter() {
    final FrequencySelecter selecter = new FrequencySelecter();
    final int p5 = 0;
    Assertions.assertEquals(0, selecter.select(0, 0, 0, 0, p5, 0, 0, 0, 0));
    Assertions.assertEquals(1, selecter.select(1, 1, 1, 1, p5, 1, 1, 1, 1));
    Assertions.assertEquals(-1, selecter.select(-1, -1, -1, -1, p5, -1, -1, -1, -1));
    Assertions.assertEquals(1, selecter.select(-1, -1, -1, -1, p5, 1, 1, 1, 1));
    Assertions.assertEquals(-1, selecter.select(-1, -1, -1, -1, p5, 1, 1, 1, 0));
    Assertions.assertEquals(3, selecter.select(1, 1, 2, 0, p5, 0, 3, 3, 3));
  }

  @SeededTest
  void checkSort8(RandomSeed seed) {
    final UniformRandomProvider rng = RngFactory.create(seed.get());
    for (int i = 0; i < 100; i++) {
      final int[] v1 = rng.ints(8).toArray();
      final int[] v2 = v1.clone();
      Arrays.sort(v1);
      FrequencySelecter.sort8(v2);
      Assertions.assertArrayEquals(v1, v2);
    }
  }

  // To allow pixel array layouts to be custom formatted
  //@formatter:off

  @Test
  void checkNoImage() {
    assertExpansion(
        0, 0,
        new byte[0],
        new byte[0]
    );
  }

  @Test
  void checkSingleZeroPixel() {
    assertExpansion(
        1, 1,
        new byte[] {0},
        new byte[] {0}
    );
  }

  @Test
  void checkSingleNonZeroPixel() {
    assertExpansion(
        1, 1,
        new byte[] {1},
        new byte[] {1}
    );
  }

  @Test
  void checkSingleLine3x1End() {
    assertExpansion(
        3, 1,
        new byte[] {0, 0, 1},
        new byte[] {0, 1, 1}
    );
  }

  @Test
  void checkSingleLine3x1Start() {
    assertExpansion(
        3, 1,
        new byte[] {1, 0, 0},
        new byte[] {1, 1, 0}
    );
  }

  @Test
  void checkSingleLine1x3() {
    assertExpansion(
        1, 3,
        new byte[] {1, 1, 0},
        new byte[] {1, 1, 1}
    );
  }

  @Test
  void check4x3() {
    assertExpansion(
        4, 3,
        new byte[] {0, 0, 0, 0,
                    0, 1, 1, 0,
                    0, 0, 0, 0},
        new byte[] {1, 1, 1, 1,
                    1, 1, 1, 1,
                    1, 1, 1, 1}
    );
  }

  @Test
  void check5x5() {
    assertExpansion(
        5, 6,
        new byte[] {1, 1, 2, 2, 2,
                    1, 1, 2, 2, 2,
                    0, 0, 0, 0, 0,
                    0, 3, 3, 3, 0,
                    0, 3, 3, 3, 0,
                    0, 3, 3, 3, 0},
        new byte[] {1, 1, 2, 2, 2,
                    1, 1, 2, 2, 2,
                    1, 3, 3, 2, 2,
                    3, 3, 3, 3, 3,
                    3, 3, 3, 3, 3,
                    3, 3, 3, 3, 3}
    );
  }

  @Test
  void check5x5WithNegatives() {
    assertExpansion(
        5, 6,
        new byte[] {1, 1, 2, 2, 2,
                    1, 1, 2, 2, 2,
                    0, 0, 0, 0, 0,
                    0,-3,-3,-3, 0,
                    0,-3,-3,-3, 0,
                    0,-3,-3,-3, 0},
        new byte[] {1, 1, 2, 2, 2,
                    1, 1, 2, 2, 2,
                    1,-3,-3, 2, 2,
                   -3,-3,-3,-3,-3,
                   -3,-3,-3,-3,-3,
                   -3,-3,-3,-3,-3}
    );
  }

  @Test
  void check5x5TwoIterations() {
    assertExpansion(
        5, 6,
        new byte[] {1, 1, 1, 1, 1,
                    0, 0, 0, 0, 0,
                    0, 0, 0, 0, 0,
                    0, 0, 0, 0, 0,
                    0, 0, 0, 0, 0,
                    0, 0, 0, 3, 3},
        new byte[] {1, 1, 1, 1, 1,
                    1, 1, 1, 1, 1,
                    1, 1, 1, 1, 1,
                    0, 3, 3, 3, 3,
                    0, 3, 3, 3, 3,
                    0, 3, 3, 3, 3},
        2
    );
  }

  //@formatter:on

  /**
   * Create a byte image, perform the erosion and check the output.
   *
   * @param width the width
   * @param height the height
   * @param input the input
   * @param expectedOutput the expected output
   * @param extendOutside Flag indicating edge pixels are extended outside the image
   */
  private static void assertExpansion(int width, int height, byte[] input, byte[] expectedOutput) {
    assertExpansion(width, height, input, expectedOutput, 1);
  }

  /**
   * Create a byte image, perform the erosion and check the output.
   *
   * @param width the width
   * @param height the height
   * @param input the input
   * @param expectedOutput the expected output
   * @param iterations the iterations
   */
  private static void assertExpansion(int width, int height, byte[] input, byte[] expectedOutput,
      int iterations) {
    final ByteProcessor in = new ByteProcessor(width, height, input);
    new ObjectExpander(in).expand(iterations);
    Assertions.assertArrayEquals(expectedOutput, input);
  }
}
