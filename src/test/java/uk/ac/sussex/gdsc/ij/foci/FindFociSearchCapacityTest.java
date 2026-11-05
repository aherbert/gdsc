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

import ij.ImagePlus;
import ij.ImageStack;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;
import ij.process.ShortProcessor;
import java.util.function.IntFunction;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import uk.ac.sussex.gdsc.ij.foci.FindFociProcessorOptions.BackgroundMethod;
import uk.ac.sussex.gdsc.ij.foci.FindFociProcessorOptions.MaskMethod;
import uk.ac.sussex.gdsc.ij.foci.FindFociProcessorOptions.PeakMethod;
import uk.ac.sussex.gdsc.ij.foci.FindFociProcessorOptions.SearchMethod;

@SuppressWarnings({"javadoc"})
class FindFociSearchCapacityTest {
  /** The positions of the single pixel maxima. */
  private static final int[][] MAXIMA = {{2, 2}, {7, 7}, {2, 7}};

  private static ImagePlus createImage(boolean floatImage, int slices) {
    final ImageStack stack = new ImageStack(10, 10);
    for (int z = 0; z < slices; z++) {
      final ImageProcessor ip =
          floatImage ? new FloatProcessor(10, 10) : new ShortProcessor(10, 10);
      // Maxima on the middle slice
      if (z == slices / 2) {
        for (final int[] m : MAXIMA) {
          ip.setf(m[0], m[1], 100);
        }
      }
      stack.addSlice(ip);
    }
    return new ImagePlus("test", stack);
  }

  private static FindFociProcessorOptions createOptions() {
    final FindFociProcessorOptions options = new FindFociProcessorOptions(true);
    options.setBackgroundMethod(BackgroundMethod.ABSOLUTE);
    options.setBackgroundParameter(0);
    options.setSearchMethod(SearchMethod.ABOVE_BACKGROUND);
    options.setPeakMethod(PeakMethod.ABSOLUTE);
    options.setPeakParameter(0);
    options.setMinSize(1);
    options.setMaxPeaks(100);
    options.setMaskMethod(MaskMethod.NONE);
    return options;
  }

  private static void assertCapacity(IntFunction<FindFociBaseProcessor> factory,
      boolean floatImage) {
    for (final int slices : new int[] {1, 3}) {
      final ImagePlus imp = createImage(floatImage, slices);
      // The capacity is the maximum number of potential maxima
      final FindFociResults results =
          factory.apply(MAXIMA.length).findMaxima(imp, null, createOptions());
      Assertions.assertNotNull(results, () -> "Capacity equal to the maxima: slices=" + slices);
      Assertions.assertEquals(MAXIMA.length, results.results.size());
      Assertions.assertNull(factory.apply(MAXIMA.length - 1).findMaxima(imp, null, createOptions()),
          () -> "Capacity below the maxima: slices=" + slices);
    }
  }

  @Test
  void testFloatProcessorCapacity() {
    assertCapacity(FindFociFloatProcessor::new, true);
  }

  @Test
  void testIntProcessorCapacity() {
    assertCapacity(FindFociIntProcessor::new, false);
  }

  @Test
  void testOptimisedIntProcessorCapacity() {
    assertCapacity(FindFociOptimisedIntProcessor::new, false);
  }

  @Test
  void testToSearchCapacity() {
    Assertions.assertEquals(10, FindFoci_PlugIn.toSearchCapacity(10, 5));
    Assertions.assertEquals(10, FindFoci_PlugIn.toSearchCapacity(-10, 5));
    Assertions.assertEquals(1, FindFoci_PlugIn.toSearchCapacity(0, 5));
    Assertions.assertEquals(Integer.MAX_VALUE, FindFoci_PlugIn.toSearchCapacity(1e10, 5));
    Assertions.assertEquals(Integer.MAX_VALUE, FindFoci_PlugIn.toSearchCapacity(-1e10, 5));
    Assertions.assertEquals(5, FindFoci_PlugIn.toSearchCapacity(Double.NaN, 5));
  }
}
