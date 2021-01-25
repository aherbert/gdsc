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

import ij.IJ;
import ij.ImagePlus;
import ij.measure.Calibration;
import java.util.function.ToDoubleBiFunction;
import java.util.stream.IntStream;
import org.apache.commons.rng.UniformRandomProvider;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import uk.ac.sussex.gdsc.core.match.Coordinate;
import uk.ac.sussex.gdsc.foci.FindFociOptimiser_PlugIn.Parameters;
import uk.ac.sussex.gdsc.foci.FindFociProcessorOptions.AlgorithmOption;
import uk.ac.sussex.gdsc.foci.FindFociProcessorOptions.BackgroundMethod;
import uk.ac.sussex.gdsc.foci.FindFociProcessorOptions.CentreMethod;
import uk.ac.sussex.gdsc.foci.FindFociProcessorOptions.PeakMethod;
import uk.ac.sussex.gdsc.foci.FindFociProcessorOptions.SearchMethod;
import uk.ac.sussex.gdsc.foci.FindFociProcessorOptions.SortMethod;
import uk.ac.sussex.gdsc.foci.FindFociProcessorOptions.StatisticsMethod;
import uk.ac.sussex.gdsc.foci.FindFociProcessorOptions.ThresholdMethod;
import uk.ac.sussex.gdsc.test.junit5.RandomSeed;
import uk.ac.sussex.gdsc.test.junit5.SeededTest;
import uk.ac.sussex.gdsc.test.rng.RngUtils;

@SuppressWarnings({"javadoc"})
class FindFociOptimiserPlugInTest {

  @Test
  void checkTheParameterToFromString() {

    Assertions.assertThrows(IllegalArgumentException.class,
        () -> Parameters.fromString("89\t8989\t"), "Bad string should not be parsed");

    final FindFociProcessorOptions processorOptions = new FindFociProcessorOptions(true);
    checkTheParameterToFromString(processorOptions);
    processorOptions.setBackgroundMethod(BackgroundMethod.AUTO_THRESHOLD);
    processorOptions.setThresholdMethod(ThresholdMethod.HUANG);
    processorOptions.setStatisticsMethod(StatisticsMethod.INSIDE);
    checkTheParameterToFromString(processorOptions);
    processorOptions.setThresholdMethod(ThresholdMethod.LI);
    processorOptions.setStatisticsMethod(StatisticsMethod.OUTSIDE);
    processorOptions.setOption(AlgorithmOption.MINIMUM_ABOVE_SADDLE, true);
    processorOptions.setSearchMethod(SearchMethod.FRACTION_OF_PEAK_MINUS_BACKGROUND);
    processorOptions.setSearchParameter(0.67);
    processorOptions.setPeakMethod(PeakMethod.ABSOLUTE);
    processorOptions.setPeakParameter(98);
    processorOptions.setSortMethod(SortMethod.AVERAGE_INTENSITY);
    processorOptions.setCentreMethod(CentreMethod.MAX_VALUE_ORIGINAL);
    processorOptions.setCentreParameter(1);
    checkTheParameterToFromString(processorOptions);
    processorOptions.setBackgroundMethod(BackgroundMethod.ABSOLUTE);
    processorOptions.setBackgroundParameter(99);
    processorOptions.setBackgroundMethod(BackgroundMethod.NONE);
    processorOptions.setOption(AlgorithmOption.CONTIGUOUS_ABOVE_SADDLE, true);
    processorOptions.setSearchMethod(SearchMethod.ABOVE_BACKGROUND);
    processorOptions.setPeakMethod(PeakMethod.RELATIVE);
    processorOptions.setPeakParameter(0.3);
    processorOptions.setSortMethod(SortMethod.COUNT);
    processorOptions.setCentreMethod(CentreMethod.CENTRE_OF_MASS_SEARCH);
    processorOptions.setCentreParameter(2);
    checkTheParameterToFromString(processorOptions);
  }

  private static void checkTheParameterToFromString(FindFociProcessorOptions processorOptions) {
    final Parameters parameters = new Parameters(processorOptions);

    final String text = parameters.toString();
    // Choudl be cached
    Assertions.assertSame(text, parameters.toString(), "toString is not cached");

    // Convert from and then back again
    final Parameters parameters2 = Parameters.fromString(text);
    Assertions.assertNotNull(parameters2, () -> "Failed to parse string: " + text);
    Assertions.assertEquals(text, parameters2.toString(), "Convert from and then to is different");
  }

  @SeededTest
  void canGetDistanceFunction(RandomSeed seed) {
    final UniformRandomProvider rng = RngUtils.create(seed.getSeed());
    final Coordinate[] coords = IntStream.range(0, 5)
        .mapToObj(i -> new uk.ac.sussex.gdsc.core.match.BasePoint(rng.nextInt(32), rng.nextInt(32),
            rng.nextInt(32)))
        .toArray(Coordinate[]::new);

    // 2D image, no calibration
    assertGetDistanceFunction(coords, 1, 1, 0);
    // 2D image, different XY distances
    assertGetDistanceFunction(coords, 2, 3, 0);
    // 3D image, no calibration
    assertGetDistanceFunction(coords, 1, 1, 1);
    // 3D image, difference Z distances
    assertGetDistanceFunction(coords, 2, 2, 4);
    // 3D image, difference XYZ distances
    assertGetDistanceFunction(coords, 2, 3, 4);
  }

  private static void assertGetDistanceFunction(Coordinate[] coords, double width, double height,
      double depth) {
    final double sy = height / width;
    final double sz = depth / width;
    final ToDoubleBiFunction<Coordinate, Coordinate> df1 = (c1, c2) -> {
      final double dx = c1.getX() - c2.getX();
      final double dy = (c1.getY() - c2.getY()) * sy;
      final double dz = (c1.getZ() - c2.getZ()) * sz;
      return dx * dx + dy * dy + dz * dz;
    };
    final Calibration cal = new Calibration();
    cal.pixelWidth = width;
    cal.pixelHeight = height;
    cal.pixelDepth = depth;
    final boolean is3d = depth != 0;
    final ImagePlus imp = IJ.createImage(null, 16, 8, is3d ? 2 : 1, 8);
    imp.setCalibration(cal);
    final ToDoubleBiFunction<Coordinate, Coordinate> df2 =
        FindFociOptimiser_PlugIn.getDistanceFunction(imp, is3d);
    for (int i = 0; i < coords.length; i++) {
      final Coordinate c1 = coords[i];
      for (int j = i; j < coords.length; j++) {
        final Coordinate c2 = coords[j];
        Assertions.assertEquals(df1.applyAsDouble(c1, c2), df2.applyAsDouble(c1, c2));
      }
    }
  }
}
