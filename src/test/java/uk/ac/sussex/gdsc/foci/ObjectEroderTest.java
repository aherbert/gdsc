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

package uk.ac.sussex.gdsc.foci;

import ij.process.ByteProcessor;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

@SuppressWarnings({"javadoc"})
class ObjectEroderTest {
  @Test
  void checkIsDifferent() {
    Assertions.assertFalse(ObjectEroder.isDifferent(1, 1, 1, 1, 1, 1, 1, 1, 1));
    Assertions.assertTrue(ObjectEroder.isDifferent(0, 1, 1, 1, 1, 1, 1, 1, 1));
    Assertions.assertTrue(ObjectEroder.isDifferent(1, 0, 1, 1, 1, 1, 1, 1, 1));
    Assertions.assertTrue(ObjectEroder.isDifferent(1, 1, 0, 1, 1, 1, 1, 1, 1));
    Assertions.assertTrue(ObjectEroder.isDifferent(1, 1, 1, 0, 1, 1, 1, 1, 1));
    Assertions.assertTrue(ObjectEroder.isDifferent(1, 1, 1, 1, 1, 0, 1, 1, 1));
    Assertions.assertTrue(ObjectEroder.isDifferent(1, 1, 1, 1, 1, 1, 0, 1, 1));
    Assertions.assertTrue(ObjectEroder.isDifferent(1, 1, 1, 1, 1, 1, 1, 0, 1));
    Assertions.assertTrue(ObjectEroder.isDifferent(1, 1, 1, 1, 1, 1, 1, 1, 0));
  }

  // To allow pixel array layouts to be custom formatted
  //@formatter:off

  @Test
  void checkNoImage() {
    assertErosion(
        0, 0,
        new byte[0],
        new byte[0],
        true
    );
  }

  @Test
  void checkSinglePixel() {
    assertErosion(
        1, 1,
        new byte[] {1},
        new byte[] {0},
        false
    );
  }

  @Test
  void checkSinglePixelExtended() {
    assertErosion(
        1, 1,
        new byte[] {1},
        new byte[] {1},
        true
    );
  }

  @Test
  void checkSingleLine3x1() {
    assertErosion(
        3, 1,
        new byte[] {1, 1, 1},
        new byte[] {0, 0, 0},
        false
    );
  }

  @Test
  void checkSingleLine3x1Extended() {
    assertErosion(
        3, 1,
        new byte[] {1, 1, 1},
        new byte[] {1, 1, 1},
        true
    );
  }

  @Test
  void checkSingleLine3x1ExtendedEnd() {
    assertErosion(
        3, 1,
        new byte[] {0, 1, 1},
        new byte[] {0, 0, 1},
        true
    );
  }

  @Test
  void checkSingleLine3x1ExtendedStart() {
    assertErosion(
        3, 1,
        new byte[] {1, 1, 0},
        new byte[] {1, 0, 0},
        true
    );
  }

  @Test
  void checkSingleLine1x3() {
    assertErosion(
        1, 3,
        new byte[] {1, 1, 1},
        new byte[] {0, 0, 0},
        false
    );
  }

  @Test
  void checkSingleLine1x3Extended() {
    assertErosion(
        1, 3,
        new byte[] {1, 1, 1},
        new byte[] {1, 1, 1},
        true
    );
  }

  @Test
  void check4x3() {
    assertErosion(
        4, 3,
        new byte[] {1, 1, 1, 1,
                    1, 1, 1, 1,
                    1, 1, 1, 1},
        new byte[] {0, 0, 0, 0,
                    0, 1, 1, 0,
                    0, 0, 0, 0},
        false
    );
  }

  @Test
  void check4x3Extended() {
    assertErosion(
        4, 3,
        new byte[] {1, 1, 1, 1,
                    1, 1, 1, 1,
                    1, 1, 1, 1},
        new byte[] {1, 1, 1, 1,
                    1, 1, 1, 1,
                    1, 1, 1, 1},
        true
    );
  }

  @Test
  void check5x5Extended() {
    assertErosion(
        5, 6,
        new byte[] {1, 1, 2, 2, 2,
                    1, 1, 2, 2, 2,
                    0, 0, 0, 0, 0,
                    0, 3, 3, 3, 0,
                    0, 3, 3, 3, 0,
                    0, 3, 3, 3, 0},
        new byte[] {1, 0, 0, 2, 2,
                    0, 0, 0, 0, 0,
                    0, 0, 0, 0, 0,
                    0, 0, 0, 0, 0,
                    0, 0, 3, 0, 0,
                    0, 0, 3, 0, 0},
        true
    );
  }


  @Test
  void check5x5ExtendedTwoIterations() {
    assertErosion(
        5, 6,
        new byte[] {1, 1, 1, 1, 1,
                    1, 1, 1, 1, 1,
                    1, 1, 1, 1, 1,
                    0, 3, 3, 3, 3,
                    0, 3, 3, 3, 3,
                    0, 3, 3, 3, 3},
        new byte[] {1, 1, 1, 1, 1,
                    0, 0, 0, 0, 0,
                    0, 0, 0, 0, 0,
                    0, 0, 0, 0, 0,
                    0, 0, 0, 0, 0,
                    0, 0, 0, 3, 3},
        true, 2
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
  private static void assertErosion(int width, int height, byte[] input, byte[] expectedOutput,
      boolean extendOutside) {
    assertErosion(width, height, input, expectedOutput, extendOutside, 1);
  }

  /**
   * Create a byte image, perform the erosion and check the output.
   *
   * @param width the width
   * @param height the height
   * @param input the input
   * @param expectedOutput the expected output
   * @param extendOutside Flag indicating edge pixels are extended outside the image
   * @param iterations the iterations
   */
  private static void assertErosion(int width, int height, byte[] input, byte[] expectedOutput,
      boolean extendOutside, int iterations) {
    final ByteProcessor in = new ByteProcessor(width, height, input);
    new ObjectEroder(in, extendOutside).erode(iterations);
    Assertions.assertArrayEquals(expectedOutput, input);
  }
}
