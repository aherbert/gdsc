/*-
 * #%L
 * Genome Damage and Stability Centre ImageJ Plugins
 *
 * Software for microscopy image analysis
 * %%
 * Copyright (C) 2011 - 2018 Alex Herbert
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

import uk.ac.sussex.gdsc.core.threshold.AutoThreshold;
import uk.ac.sussex.gdsc.core.utils.rng.GaussianSamplerUtils;
import uk.ac.sussex.gdsc.test.api.TestAssertions;
import uk.ac.sussex.gdsc.test.api.TestHelper;
import uk.ac.sussex.gdsc.test.api.function.DoubleDoubleBiPredicate;
import uk.ac.sussex.gdsc.test.junit5.RandomSeed;
import uk.ac.sussex.gdsc.test.junit5.SeededTest;
import uk.ac.sussex.gdsc.test.junit5.SpeedTag;
import uk.ac.sussex.gdsc.test.rng.RngUtils;
import uk.ac.sussex.gdsc.test.utils.TestComplexity;
import uk.ac.sussex.gdsc.test.utils.TestLogUtils;
import uk.ac.sussex.gdsc.test.utils.TestSettings;
import uk.ac.sussex.gdsc.test.utils.TestUtils;
import uk.ac.sussex.gdsc.test.utils.TimingResult;
import uk.ac.sussex.gdsc.test.utils.functions.FunctionUtils;

import ij.ImagePlus;
import ij.ImageStack;
import ij.plugin.filter.GaussianBlur;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;
import ij.process.ShortProcessor;

import org.apache.commons.rng.UniformRandomProvider;
import org.apache.commons.rng.sampling.distribution.GaussianSampler;
import org.apache.commons.rng.sampling.distribution.PoissonSampler;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.opentest4j.AssertionFailedError;

import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.logging.Logger;

@SuppressWarnings({"javadoc"})
public class FindFociTest implements Function<RandomSeed, ImagePlus[]> {
  private static Logger logger;
  static ConcurrentHashMap<RandomSeed, ImagePlus[]> dataCache;

  @BeforeAll
  public static void beforeAll() {
    logger = Logger.getLogger(FindFociTest.class.getName());
    dataCache = new ConcurrentHashMap<>();
  }

  @AfterAll
  public static void afterAll() {
    dataCache.clear();
    dataCache = null;
    logger = null;
  }

  final int bias = 500;
  // Offset to create negative values.
  // Power of 2 should not effect the mantissa precision
  final int offset = 1024;
  final int numberOfTestImages = 2;
  final int numberOfTestImages3D = 2;
  final int LOOPS = 20;

  // Allow testing different settings.
  // Note that the float processor must use absolute values as the relative ones are converted to
  // floats
  // and this may result in different output.
  // The second method will be used with negative values so use Auto-threshold
  final int[] backgroundMethod = new int[] {FindFociProcessor.BACKGROUND_ABSOLUTE,
      FindFociProcessor.BACKGROUND_AUTO_THRESHOLD};
  final double[] backgroundParameter = new double[] {bias, 0};
  final String[] autoThresholdMethod = new String[] {"", AutoThreshold.Method.OTSU.toString()};
  final int[] searchMethod = new int[] {FindFociProcessor.SEARCH_ABOVE_BACKGROUND,
      FindFociProcessor.SEARCH_ABOVE_BACKGROUND};
  final double[] searchParameter = new double[] {0.3, 0.7};
  final int[] maxPeaks = new int[] {1000, 1000};
  final int[] minSize = new int[] {5, 3};
  final int[] peakMethod =
      new int[] {FindFociProcessor.PEAK_ABSOLUTE, FindFociProcessor.PEAK_ABSOLUTE};
  final double[] peakParameter = new double[] {10, 20};
  final int[] outputType =
      new int[] {FindFociProcessor.OUTPUT_MASK, FindFociProcessor.OUTPUT_MASK_PEAKS};
  final int[] sortIndex =
      new int[] {FindFociProcessor.SORT_INTENSITY, FindFociProcessor.SORT_MAX_VALUE};
  final int[] options = new int[] {FindFociProcessor.OPTION_MINIMUM_ABOVE_SADDLE, 0};
  final double[] blur = new double[] {0, 0};
  final int[] centreMethod =
      new int[] {FindFoci.CENTRE_MAX_VALUE_SEARCH, FindFoci.CENTRE_MAX_VALUE_ORIGINAL};
  final double[] centreParameter = new double[] {2, 2};
  final double[] fractionParameter = new double[] {0.5, 0};

  @SeededTest
  public void isSameResultUsingIntProcessor(RandomSeed seed) {
    final boolean nonContiguous = true;
    for (final ImagePlus imp : dataCache.computeIfAbsent(seed, this)) {
      for (int i = 0; i < backgroundMethod.length; i++) {
        final FindFociResults r1 = runLegacy(imp, i);
        final FindFociResults r2 = runInt(imp, i, false, nonContiguous);
        isEqual(true, r1, r2, i, nonContiguous);
      }
    }
  }

  @SeededTest
  public void isSameResultUsingOptimisedIntProcessor(RandomSeed seed) {
    for (final ImagePlus imp : dataCache.computeIfAbsent(seed, this)) {
      for (final boolean nonContiguous : new boolean[] {true, false}) {
        for (int i = 0; i < backgroundMethod.length; i++) {
          final FindFociResults r1 = runInt(imp, i, false, nonContiguous);
          final FindFociResults r2 = runInt(imp, i, true, nonContiguous);
          isEqual(false, r1, r2, i, nonContiguous);
        }
      }
    }
  }

  @SeededTest
  public void isSameResultUsingFloatProcessor(RandomSeed seed) {
    for (final ImagePlus imp : dataCache.computeIfAbsent(seed, this)) {
      for (final boolean nonContiguous : new boolean[] {true, false}) {
        for (int i = 0; i < backgroundMethod.length; i++) {
          final FindFociResults r1 = runInt(imp, i, false, nonContiguous);
          final FindFociResults r2 = runFloat(imp, i, false, false, nonContiguous);
          isEqual(false, r1, r2, i, nonContiguous);
        }
      }
    }
  }

  @SeededTest
  public void isSameResultUsingOptimisedFloatProcessor(RandomSeed seed) {
    for (final ImagePlus imp : dataCache.computeIfAbsent(seed, this)) {
      for (final boolean nonContiguous : new boolean[] {true, false}) {
        for (int i = 0; i < backgroundMethod.length; i++) {
          final FindFociResults r1 = runFloat(imp, i, false, false, nonContiguous);
          final FindFociResults r2 = runFloat(imp, i, true, false, nonContiguous);
          isEqual(false, r1, r2, i, nonContiguous);
        }
      }
    }
  }

  @SeededTest
  public void isSameResultUsingFloatProcessorWithNegativeValues(RandomSeed seed) {
    for (final ImagePlus imp : dataCache.computeIfAbsent(seed, this)) {
      for (final boolean nonContiguous : new boolean[] {true, false}) {
        for (int i = 0; i < backgroundMethod.length; i++) {
          if (FindFociBaseProcessor.isSortIndexSensitiveToNegativeValues(sortIndex[i])) {
            continue;
          }
          final FindFociResults r1 = runFloat(imp, i, false, false, nonContiguous);
          final FindFociResults r2 = runFloat(imp, i, false, true, nonContiguous);
          isEqual(false, r1, r2, i, true, nonContiguous);
        }
      }
    }
  }

  @SeededTest
  public void isSameResultUsingOptimisedFloatProcessorWithNegativeValues(RandomSeed seed) {
    for (final ImagePlus imp : dataCache.computeIfAbsent(seed, this)) {
      for (final boolean nonContiguous : new boolean[] {true, false}) {
        for (int i = 0; i < backgroundMethod.length; i++) {
          if (FindFociBaseProcessor.isSortIndexSensitiveToNegativeValues(sortIndex[i])) {
            continue;
          }
          final FindFociResults r1 = runFloat(imp, i, true, false, nonContiguous);
          final FindFociResults r2 = runFloat(imp, i, true, true, nonContiguous);
          isEqual(false, r1, r2, i, true, nonContiguous);
        }
      }
    }
  }

  @SeededTest
  public void isSameResultUsingIntProcessorWithStagedMethods(RandomSeed seed) {
    for (final ImagePlus imp : dataCache.computeIfAbsent(seed, this)) {
      for (final boolean nonContiguous : new boolean[] {true, false}) {
        for (int i = 0; i < backgroundMethod.length; i++) {
          final FindFociResults r1 = runInt(imp, i, false, nonContiguous);
          final FindFociResults r2 = runIntStaged(imp, i, false, nonContiguous);
          isEqual(false, r1, r2, i, nonContiguous);
        }
      }
    }
  }

  @SeededTest
  public void isSameResultUsingOptimisedIntProcessorWithStagedMethods(RandomSeed seed) {
    for (final ImagePlus imp : dataCache.computeIfAbsent(seed, this)) {
      for (final boolean nonContiguous : new boolean[] {true, false}) {
        for (int i = 0; i < backgroundMethod.length; i++) {
          final FindFociResults r1 = runInt(imp, i, true, nonContiguous);
          final FindFociResults r2 = runIntStaged(imp, i, true, nonContiguous);
          isEqual(false, r1, r2, i, nonContiguous);
        }
      }
    }
  }

  @SeededTest
  public void isSameResultUsingFloatProcessorWithStagedMethods(RandomSeed seed) {
    for (final ImagePlus imp : dataCache.computeIfAbsent(seed, this)) {
      for (final boolean nonContiguous : new boolean[] {true, false}) {
        for (int i = 0; i < backgroundMethod.length; i++) {
          final FindFociResults r1 = runFloat(imp, i, false, false, nonContiguous);
          final FindFociResults r2 = runFloatStaged(imp, i, false, false, nonContiguous);
          isEqual(false, r1, r2, i, nonContiguous);
        }
      }
    }
  }

  @SeededTest
  public void isSameResultUsingOptimisedFloatProcessorWithStagedMethods(RandomSeed seed) {
    for (final ImagePlus imp : dataCache.computeIfAbsent(seed, this)) {
      for (final boolean nonContiguous : new boolean[] {true, false}) {
        for (int i = 0; i < backgroundMethod.length; i++) {
          final FindFociResults r1 = runFloat(imp, i, true, false, nonContiguous);
          final FindFociResults r2 = runFloatStaged(imp, i, true, false, nonContiguous);
          isEqual(false, r1, r2, i, nonContiguous);
        }
      }
    }
  }

  @SeededTest
  public void isSameResultUsingFloatProcessorWithStagedMethodsWithNegativeValues(RandomSeed seed) {
    for (final ImagePlus imp : dataCache.computeIfAbsent(seed, this)) {
      for (final boolean nonContiguous : new boolean[] {true, false}) {
        for (int i = 0; i < backgroundMethod.length; i++) {
          if (FindFociBaseProcessor.isSortIndexSensitiveToNegativeValues(sortIndex[i])) {
            continue;
          }
          final FindFociResults r1 = runFloat(imp, i, false, false, nonContiguous);
          final FindFociResults r2 = runFloatStaged(imp, i, false, true, nonContiguous);
          isEqual(false, r1, r2, i, true, nonContiguous);
        }
      }
    }
  }

  @SeededTest
  public void isSameResultUsingOptimisedFloatProcessorWithStagedMethodsWithNegativeValues(
      RandomSeed seed) {
    for (final ImagePlus imp : dataCache.computeIfAbsent(seed, this)) {
      for (final boolean nonContiguous : new boolean[] {true, false}) {
        for (int i = 0; i < backgroundMethod.length; i++) {
          if (FindFociBaseProcessor.isSortIndexSensitiveToNegativeValues(sortIndex[i])) {
            continue;
          }
          final FindFociResults r1 = runFloat(imp, i, true, false, nonContiguous);
          final FindFociResults r2 = runFloatStaged(imp, i, true, true, nonContiguous);
          isEqual(false, r1, r2, i, true, nonContiguous);
        }
      }
    }
  }

  @SpeedTag
  @SeededTest
  public void isFasterUsingOptimisedIntProcessor(RandomSeed seed) {
    Assumptions.assumeTrue(TestSettings.allow(TestComplexity.LOW));

    // Get settings to try for the speed test
    final int[] indices = new int[] {1};

    // Warm up
    final ImagePlus[] data = dataCache.computeIfAbsent(seed, this);
    // runInt(data[0], indices[0], false);
    // runInt(data[0], indices[0], true);

    long time1 = Long.MAX_VALUE;
    for (int n = LOOPS; n-- > 0;) {
      start();
      for (final ImagePlus imp : data) {
        for (final int i : indices) {
          for (final boolean nonContiguous : new boolean[] {true, false}) {
            runInt(imp, i, false, nonContiguous);
          }
        }
      }
      time1 = stop(time1);
    }
    long time2 = Long.MAX_VALUE;
    for (int n = LOOPS; n-- > 0;) {
      start();
      for (final ImagePlus imp : data) {
        for (final int i : indices) {
          for (final boolean nonContiguous : new boolean[] {true, false}) {
            runInt(imp, i, true, nonContiguous);
          }
        }
      }
      time2 = stop(time2);
    }
    logger.log(TestLogUtils.getTimingRecord(new TimingResult("Int", time1),
        new TimingResult("Opt Int", time2)));
    Assertions.assertTrue(time2 < time1);
  }

  @SpeedTag
  @SeededTest
  public void isFasterUsingOptimisedFloatProcessor(RandomSeed seed) {
    Assumptions.assumeTrue(TestSettings.allow(TestComplexity.MEDIUM));

    // Get settings to try for the speed test
    final int[] indices = new int[] {1};

    // Warm up
    final ImagePlus[] data = dataCache.computeIfAbsent(seed, this);
    // runFloat(data[0], indices[0], false, false);
    // runFloat(data[0], indices[0], true, false);

    long time1 = Long.MAX_VALUE;
    for (int n = LOOPS; n-- > 0;) {
      start();
      for (final ImagePlus imp : data) {
        for (final int i : indices) {
          for (final boolean nonContiguous : new boolean[] {true, false}) {
            runFloat(imp, i, false, false, nonContiguous);
          }
        }
      }
      time1 = stop(time1);
    }
    long time2 = Long.MAX_VALUE;
    for (int n = LOOPS; n-- > 0;) {
      start();
      for (final ImagePlus imp : data) {
        for (final int i : indices) {
          for (final boolean nonContiguous : new boolean[] {true, false}) {
            runFloat(imp, i, true, false, nonContiguous);
          }
        }
      }
      time2 = stop(time2);
    }

    // Comment out this assertion as it sometimes fails when running all the tests.
    // When running all the tests the some code gets run more and so
    // the JVM has had time to optimise it. When running the test alone the optimised processor is
    // comparable.
    // I am not worried the optimisation has worse performance.

    // Assertions.assertTrue(time2 < time1 * 1.4); // Allow discretion so test will pass
    logger.log(TestLogUtils.getTimingRecord(new TimingResult("Float", time1),
        new TimingResult("Opt Float", time2)));
  }

  @SpeedTag
  @SeededTest
  public void isNotSlowerthanLegacyUsingOptimisedIntProcessor(RandomSeed seed) {
    Assumptions.assumeTrue(TestSettings.allow(TestComplexity.MEDIUM));

    // Get settings to try for the speed test
    final int[] indices = new int[] {1};

    // Warm up
    final ImagePlus[] data = dataCache.computeIfAbsent(seed, this);
    // runLegacy(data[0], indices[0]);
    // runInt(data[0], indices[0], true);

    long time1 = Long.MAX_VALUE;
    for (int n = LOOPS; n-- > 0;) {
      start();
      for (final ImagePlus imp : data) {
        for (final int i : indices) {
          runLegacy(imp, i);
        }
      }
      time1 = stop(time1);
    }
    long time2 = Long.MAX_VALUE;
    for (int n = LOOPS; n-- > 0;) {
      start();
      for (final ImagePlus imp : data) {
        for (final int i : indices) {
          runInt(imp, i, true, true);
        }
      }
      time2 = stop(time2);
    }

    // Comment out this assertion as it sometimes fails when running all the tests.
    // When running all the tests the legacy code gets run more and so
    // the JVM has had time to optimise it. When running the test alone the two are comparable.
    // I am not worried the new code has worse performance.

    // Assertions.assertTrue(time2 < time1 * 1.4); // Allow some discretion over the legacy method
    logger.log(TestLogUtils.getTimingRecord(new TimingResult("Legacy", time1),
        new TimingResult("Opt Int", time2)));
  }

  @SeededTest
  public void isFasterUsingOptimisedIntProcessorOverOptimisedFloatProcessor(RandomSeed seed) {
    Assumptions.assumeTrue(TestSettings.allow(TestComplexity.LOW));

    // Get settings to try for the speed test
    final int[] indices = new int[] {1};

    // Warm up
    final ImagePlus[] data = dataCache.computeIfAbsent(seed, this);
    // runFloat(data[0], indices[0], true, false);
    // runInt(data[0], indices[0], true);

    final ImagePlus[] data2 = new ImagePlus[data.length];
    for (int i = 0; i < data.length; i++) {
      data2[i] = toFloat(data[i], false);
    }

    long time1 = Long.MAX_VALUE;
    for (int n = LOOPS; n-- > 0;) {
      start();
      for (final ImagePlus imp : data2) {
        for (final int i : indices) {
          for (final boolean nonContiguous : new boolean[] {true, false}) {
            runFloat(imp, i, true, false, nonContiguous);
          }
        }
      }
      time1 = stop(time1);
    }
    long time2 = Long.MAX_VALUE;
    for (int n = LOOPS; n-- > 0;) {
      start();
      for (final ImagePlus imp : data) {
        for (final int i : indices) {
          for (final boolean nonContiguous : new boolean[] {true, false}) {
            runInt(imp, i, true, nonContiguous);
          }
        }
      }
      time2 = stop(time2);
    }
    logger.log(TestLogUtils.getTimingRecord(new TimingResult("Opt Float", time1),
        new TimingResult("Opt Int", time2)));
    Assertions.assertTrue(time2 < time1);
  }

  private void isEqual(boolean legacy, FindFociResults r1, FindFociResults r2, int set,
      boolean nonContiguous) {
    isEqual(legacy, r1, r2, set, false, nonContiguous);
  }

  private void isEqual(boolean legacy, FindFociResults r1, FindFociResults r2, int set,
      boolean negativeValues, boolean nonContiguous) {
    final String setName = String.format("Set %d (%b)", set, nonContiguous);

    final ImagePlus imp1 = r1.mask;
    final ImagePlus imp2 = r2.mask;
    Assertions.assertEquals(imp1 != null, imp2 != null, setName + " Mask");
    if (imp1 != null) {
      // Assertions.assertArrayEquals(set + " Mask values", (float[])
      // (imp1.getProcessor().convertToFloat().getPixels()),
      // (float[]) (imp2.getProcessor().convertToFloat().getPixels()), 0);
    }
    final ArrayList<FindFociResult> results1 = r1.results;
    final ArrayList<FindFociResult> results2 = r2.results;
    // logger.info(FunctionUtils.getSupplier("N1=%d, N2=%d", results1.size(), results2.size());
    Assertions.assertEquals(results1.size(), results2.size(), setName + " Results Size");
    int counter = 0;
    final int offset = (negativeValues) ? this.offset : 0;
    final DoubleDoubleBiPredicate predictate = TestHelper.doublesAreClose(1e-9, 1e-16);
    try {
      for (int i = 0; i < results1.size(); i++) {
        counter = i;
        //@formatter:off
          final FindFociResult o1 = results1.get(i);
          final FindFociResult o2 = results2.get(i);
          //logger.info(FunctionUtils.getSupplier("[%d] %d,%d %f (%d) %d vs %d,%d %f (%d) %d", i,
          //    o1.x, o1.y, o1.maxValue, o1.count, o1.saddleNeighbourId,
          //    o2.x, o2.y, o2.maxValue, o2.count, o2.saddleNeighbourId);
          Assertions.assertEquals(o1.x, o2.x, "X");
          Assertions.assertEquals(o1.y, o2.y, "Y");
          Assertions.assertEquals(o1.z, o2.z, "Z");
          Assertions.assertEquals(o1.id, o2.id,"ID");
          Assertions.assertEquals(o1.count, o2.count, "Count");
          Assertions.assertEquals(o1.saddleNeighbourId, o2.saddleNeighbourId, "Saddle ID");
          Assertions.assertEquals(o1.countAboveSaddle, o2.countAboveSaddle, "Count >Saddle");
          // Single/Summed values can be cast to long as they should be 16-bit integers
          Assertions.assertEquals((long)o1.maxValue, (long)o2.maxValue + offset, "Max");
          if (o2.highestSaddleValue != Float.NEGATIVE_INFINITY && o2.highestSaddleValue != 0) {
            Assertions.assertEquals((long)o1.highestSaddleValue, (long)o2.highestSaddleValue + offset, "Saddle value");
          }
          if (legacy)
          {
              // Cast to integer as this is the result format of the legacy FindFoci code
              Assertions.assertEquals((long)o1.averageIntensity, (long)o2.averageIntensity + offset, "Av Intensity");
              Assertions.assertEquals((long)o1.averageIntensityAboveBackground, (long)o2.averageIntensityAboveBackground, "Av Intensity >background");
          }
          else
          {
              // Averages cannot be cast and are compared as floating-point values
              TestAssertions.assertTest(o1.averageIntensity, o2.averageIntensity + offset, predictate, "Av Intensity");
              TestAssertions.assertTest(o1.averageIntensityAboveBackground, o2.averageIntensityAboveBackground, predictate, "Av Intensity >background");
          }
          if (negativeValues) {
            continue;
          }
          Assertions.assertEquals((long)o1.totalIntensity, (long)o2.totalIntensity, "Intensity");
          Assertions.assertEquals((long)o1.totalIntensityAboveBackground, (long)o2.totalIntensityAboveBackground, "Intensity >background");
          Assertions.assertEquals((long)o1.intensityAboveSaddle, (long)o2.intensityAboveSaddle, "Intensity > Saddle");
          //@formatter:on
      }
    } catch (final AssertionFailedError e) {
      TestUtils.wrapAssertionFailedError(e, FunctionUtils.getSupplier("%s [%d]", setName, counter));
    }
  }

  private FindFociResults runLegacy(ImagePlus imp, int i) {
    final FindFociLegacy ff = new FindFociLegacy();
    final Object[] result = ff.findMaxima(imp, null, backgroundMethod[i], backgroundParameter[i],
        autoThresholdMethod[i], searchMethod[i], searchParameter[i], maxPeaks[i], minSize[i],
        peakMethod[i], peakParameter[i], outputType[i], sortIndex[i], options[i], blur[i],
        centreMethod[i], centreParameter[i], fractionParameter[i]);
    @SuppressWarnings("unchecked")
    final ArrayList<int[]> resultsArray = (ArrayList<int[]>) result[1];
    ArrayList<FindFociResult> results = null;
    if (resultsArray != null) {
      results = new ArrayList<>();
      for (final int[] r : resultsArray) {
        final FindFociResult r2 = new FindFociResult();
        r2.averageIntensity = r[FindFociLegacy.RESULT_AVERAGE_INTENSITY];
        r2.x = r[FindFociLegacy.RESULT_X];
        r2.y = r[FindFociLegacy.RESULT_Y];
        r2.z = r[FindFociLegacy.RESULT_Z];
        r2.id = r[FindFociLegacy.RESULT_PEAK_ID];
        r2.count = r[FindFociLegacy.RESULT_COUNT];
        r2.totalIntensity = r[FindFociLegacy.RESULT_INTENSITY];
        r2.maxValue = r[FindFociLegacy.RESULT_MAX_VALUE];
        r2.highestSaddleValue = r[FindFociLegacy.RESULT_HIGHEST_SADDLE_VALUE];
        r2.saddleNeighbourId = r[FindFociLegacy.RESULT_SADDLE_NEIGHBOUR_ID];
        r2.averageIntensity = r[FindFociLegacy.RESULT_AVERAGE_INTENSITY];
        r2.totalIntensityAboveBackground = r[FindFociLegacy.RESULT_INTENSITY_MINUS_BACKGROUND];
        r2.averageIntensityAboveBackground =
            r[FindFociLegacy.RESULT_AVERAGE_INTENSITY_MINUS_BACKGROUND];
        r2.countAboveSaddle = r[FindFociLegacy.RESULT_COUNT_ABOVE_SADDLE];
        r2.intensityAboveSaddle = r[FindFociLegacy.RESULT_INTENSITY_ABOVE_SADDLE];
        results.add(r2);
      }
    }
    return new FindFociResults((ImagePlus) result[0], results, null);
  }

  private FindFociResults runInt(ImagePlus imp, int i, boolean optimised, boolean nonContiguous) {
    final FindFoci ff = new FindFoci();
    ff.setOptimisedProcessor(optimised);
    final int flags = (nonContiguous) ? options[i]
        : options[i] | FindFociProcessor.OPTION_CONTIGUOUS_ABOVE_SADDLE;
    return ff.findMaxima(imp, null, backgroundMethod[i], backgroundParameter[i],
        autoThresholdMethod[i], searchMethod[i], searchParameter[i], maxPeaks[i], minSize[i],
        peakMethod[i], peakParameter[i], outputType[i], sortIndex[i], flags, blur[i],
        centreMethod[i], centreParameter[i], fractionParameter[i]);
  }

  private FindFociResults runFloat(ImagePlus imp, int i, boolean optimised, boolean negative,
      boolean nonContiguous) {
    imp = toFloat(imp, negative);
    final FindFoci ff = new FindFoci();
    ff.setOptimisedProcessor(optimised);
    final int flags = (nonContiguous) ? options[i]
        : options[i] | FindFociProcessor.OPTION_CONTIGUOUS_ABOVE_SADDLE;
    return ff.findMaxima(imp, null, backgroundMethod[i], backgroundParameter[i],
        autoThresholdMethod[i], searchMethod[i], searchParameter[i], maxPeaks[i], minSize[i],
        peakMethod[i], peakParameter[i], outputType[i], sortIndex[i], flags, blur[i],
        centreMethod[i], centreParameter[i], fractionParameter[i]);
  }

  private ImagePlus toFloat(ImagePlus imp, boolean negative) {
    final ImageStack stack = imp.getImageStack();
    final ImageStack newStack = new ImageStack(stack.getWidth(), stack.getHeight());
    for (int n = 1; n <= stack.getSize(); n++) {
      final FloatProcessor fp = (FloatProcessor) stack.getProcessor(n).convertToFloat();
      if (negative) {
        fp.subtract(offset);
      }
      newStack.addSlice(fp);
    }
    return new ImagePlus(null, newStack);
  }

  private FindFociResults runIntStaged(ImagePlus imp, int i, boolean optimised,
      boolean nonContiguous) {
    final FindFoci ff = new FindFoci();
    ff.setOptimisedProcessor(optimised);
    final int flags = (nonContiguous) ? options[i]
        : options[i] | FindFociProcessor.OPTION_CONTIGUOUS_ABOVE_SADDLE;
    final ImagePlus imp2 = ff.blur(imp, blur[i]);
    final FindFociInitResults initResults =
        ff.findMaximaInit(imp, imp2, null, backgroundMethod[i], autoThresholdMethod[i], flags);
    final FindFociSearchResults searchResults = ff.findMaximaSearch(initResults,
        backgroundMethod[i], backgroundParameter[i], searchMethod[i], searchParameter[i]);
    FindFociMergeTempResults mergePeakResults =
        ff.findMaximaMergePeak(initResults, searchResults, peakMethod[i], peakParameter[i]);
    mergePeakResults = ff.findMaximaMergeSize(initResults, mergePeakResults, minSize[i]);
    final FindFociMergeResults mergeResults =
        ff.findMaximaMergeFinal(initResults, mergePeakResults, minSize[i], flags, blur[i]);
    final FindFociPrelimResults prelimResults = ff.findMaximaPrelimResults(initResults,
        mergeResults, maxPeaks[i], sortIndex[i], centreMethod[i], centreParameter[i]);
    return ff.findMaximaMaskResults(initResults, mergeResults, prelimResults, outputType[i],
        autoThresholdMethod[i], "FindFociTest", fractionParameter[i]);
  }

  private FindFociResults runFloatStaged(ImagePlus imp, int i, boolean optimised, boolean negative,
      boolean nonContiguous) {
    imp = toFloat(imp, negative);
    final FindFoci ff = new FindFoci();
    final ImagePlus imp2 = ff.blur(imp, blur[i]);
    ff.setOptimisedProcessor(optimised);
    final int flags = (nonContiguous) ? options[i]
        : options[i] | FindFociProcessor.OPTION_CONTIGUOUS_ABOVE_SADDLE;
    final FindFociInitResults initResults =
        ff.findMaximaInit(imp, imp2, null, backgroundMethod[i], autoThresholdMethod[i], flags);
    final FindFociSearchResults searchResults = ff.findMaximaSearch(initResults,
        backgroundMethod[i], backgroundParameter[i], searchMethod[i], searchParameter[i]);
    FindFociMergeTempResults mergePeakResults =
        ff.findMaximaMergePeak(initResults, searchResults, peakMethod[i], peakParameter[i]);
    mergePeakResults = ff.findMaximaMergeSize(initResults, mergePeakResults, minSize[i]);
    final FindFociMergeResults mergeResults =
        ff.findMaximaMergeFinal(initResults, mergePeakResults, minSize[i], flags, blur[i]);
    final FindFociPrelimResults prelimResults = ff.findMaximaPrelimResults(initResults,
        mergeResults, maxPeaks[i], sortIndex[i], centreMethod[i], centreParameter[i]);
    return ff.findMaximaMaskResults(initResults, mergeResults, prelimResults, outputType[i],
        autoThresholdMethod[i], "FindFociTest", fractionParameter[i]);
  }

  private ImagePlus createImageData(UniformRandomProvider rg) {
    // Create an image with peaks
    final int size = 256;
    final int n = 80;
    final float[] data1 = createSpots(rg, size, n, 5000, 10000, 2.5, 3.0);
    final float[] data2 = createSpots(rg, size, n, 10000, 20000, 4.5, 3.5);
    final float[] data3 = createSpots(rg, size, n, 20000, 40000, 6.5, 5);
    final short[] data = combine(rg, data1, data2, data3);
    // Show
    final String title = "FindFociTest";
    final ImageProcessor ip = new ShortProcessor(size, size, data, null);
    // uk.ac.sussex.gdsc.core.ij.Utils.display(title, ip);
    return new ImagePlus(title, ip);
  }

  private short[] combine(UniformRandomProvider rg, float[] data1, float[] data2, float[] data3) {
    // Combine images and add a bias and read noise
    final GaussianSampler g = GaussianSamplerUtils.createGaussianSampler(rg, bias, 5);
    final short[] data = new short[data1.length];
    for (int i = 0; i < data.length; i++) {
      final double mu = data1[i] + data2[i] + data3[i];
      double v = g.sample();
      if (mu != 0) {
        v += new PoissonSampler(rg, mu).sample();
      }
      if (v < 0) {
        v = 0;
      } else if (v > 65535) {
        v = 65535;
      }
      data[i] = (short) v;
    }
    return data;
  }

  private static float[] createSpots(UniformRandomProvider rg, int size, int n, int min, int max,
      double sigmaX, double sigmaY) {
    final float[] data = new float[size * size];
    // Randomly put on spots
    final int range = max - min;
    while (n-- > 0) {
      data[rg.nextInt(data.length)] = min + rg.nextInt(range);
    }

    // Blur
    final FloatProcessor fp = new FloatProcessor(size, size, data);
    final GaussianBlur gb = new GaussianBlur();
    gb.blurFloat(fp, sigmaX, sigmaY, 0.0002);

    return (float[]) fp.getPixels();
  }

  private ImagePlus createImageData3D(UniformRandomProvider rg) {
    // Create an image with peaks
    final int size = 64;
    final int z = 5;
    final int n = 20;
    final float[][] data1 = createSpots3D(rg, size, z, n, 5000, 10000, 2.5, 3.0);
    final float[][] data2 = createSpots3D(rg, size, z, n, 10000, 20000, 4.5, 3.5);
    final float[][] data3 = createSpots3D(rg, size, z, n, 20000, 40000, 6.5, 5);
    final ImageStack stack = new ImageStack(size, size);
    for (int i = 0; i < data1.length; i++) {
      final short[] data = combine(rg, data1[i], data2[i], data3[i]);
      stack.addSlice(new ShortProcessor(size, size, data, null));
    }
    // Show
    final String title = "FindFociTest3D";
    // uk.ac.sussex.gdsc.core.ij.Utils.display(title, stack);
    return new ImagePlus(title, stack);
  }

  private static float[][] createSpots3D(UniformRandomProvider rg, int size, int z, int n, int min,
      int max, double sigmaX, double sigmaY) {
    final float[] data = new float[size * size];
    // Randomly put on spots
    final int range = max - min;
    while (n-- > 0) {
      data[rg.nextInt(data.length)] = min + rg.nextInt(range);
    }

    final int middle = z / 2;
    final float[][] result = new float[z][];
    for (int i = 0; i < z; i++) {
      final FloatProcessor fp = new FloatProcessor(size, size, data.clone());
      final GaussianBlur gb = new GaussianBlur();
      // Increase blur when out-of-focus
      final double scale = createWidthScale(Math.abs(middle - i), middle);
      gb.blurFloat(fp, sigmaX * scale, sigmaY * scale, 0.0002);
      result[i] = (float[]) fp.getPixels();
    }
    return result;
  }

  /**
   * Generate a scale so that at the configured zDepth the scale is 1.5.
   *
   * @param z the z
   * @param depth the depth
   * @return The scale
   */
  private static double createWidthScale(double z, double depth) {
    z /= depth;
    return 1.0 + z * z * 0.5;
  }

  private long time;

  private void start() {
    time = System.nanoTime();

  }

  private long stop(long t) {
    return Math.min(t, System.nanoTime() - time);
  }

  // TODO fix this to use a function reference

  @Override
  public ImagePlus[] apply(RandomSeed seed) {
    final UniformRandomProvider rg = RngUtils.create(seed.getSeed());
    final ImagePlus[] images = new ImagePlus[numberOfTestImages + numberOfTestImages3D];
    int index = 0;
    for (int i = 0; i < numberOfTestImages; i++) {
      images[index++] = createImageData(rg);
    }
    for (int i = 0; i < numberOfTestImages3D; i++) {
      images[index++] = createImageData3D(rg);
    }
    return images;
  }
}
