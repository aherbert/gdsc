/*-
 * #%L
 * Genome Damage and Stability Centre ImageJ Plugins
 *
 * Software for microscopy image analysis
 * %%
 * Copyright (C) 2011 - 2019 Alex Herbert
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

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import uk.ac.sussex.gdsc.foci.FindFociOptimiser_PlugIn.Parameters;
import uk.ac.sussex.gdsc.foci.FindFociProcessorOptions.AlgorithmOption;
import uk.ac.sussex.gdsc.foci.FindFociProcessorOptions.BackgroundMethod;
import uk.ac.sussex.gdsc.foci.FindFociProcessorOptions.CentreMethod;
import uk.ac.sussex.gdsc.foci.FindFociProcessorOptions.PeakMethod;
import uk.ac.sussex.gdsc.foci.FindFociProcessorOptions.SearchMethod;
import uk.ac.sussex.gdsc.foci.FindFociProcessorOptions.SortMethod;
import uk.ac.sussex.gdsc.foci.FindFociProcessorOptions.StatisticsMethod;
import uk.ac.sussex.gdsc.foci.FindFociProcessorOptions.ThresholdMethod;

@SuppressWarnings({"javadoc"})
public class FindFociOptimiserPlugInTest {

  @Test
  public void checkTheParameterToFromString() {

    Assertions.assertThrows(IllegalArgumentException.class,
        () -> Parameters.fromString("89\t8989\t"), "Bad string should not be parsed");

    FindFociProcessorOptions processorOptions = new FindFociProcessorOptions(true);
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
    Parameters parameters = new Parameters(processorOptions);

    String text = parameters.toString();
    // Choudl be cached
    Assertions.assertSame(text, parameters.toString(), "toString is not cached");

    // Convert from and then back again
    Parameters parameters2 = Parameters.fromString(text);
    Assertions.assertNotNull(parameters2, () -> "Failed to parse string: " + text);
    Assertions.assertEquals(text, parameters2.toString(), "Convert from and then to is different");
  }
}
