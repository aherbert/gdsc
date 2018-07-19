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

import java.util.ArrayList;

import org.apache.commons.math3.random.RandomDataGenerator;
import org.apache.commons.math3.random.RandomGenerator;
import org.junit.Assert;
import org.junit.Test;

import ij.ImagePlus;
import ij.ImageStack;
import ij.plugin.filter.GaussianBlur;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;
import ij.process.ShortProcessor;
import uk.ac.sussex.gdsc.core.threshold.AutoThreshold;
import uk.ac.sussex.gdsc.test.TestComplexity;
import uk.ac.sussex.gdsc.test.TestLog;
import uk.ac.sussex.gdsc.test.TestSettings;
import uk.ac.sussex.gdsc.test.junit4.TestAssert;
import uk.ac.sussex.gdsc.test.junit4.TestAssume;

@SuppressWarnings({ "javadoc" })
public class FindFociTest
{
	static int bias = 500;
	// Offset to create negative values.
	// Power of 2 should not effect the mantissa precision
	static int offset = 1024;
	static ImagePlus[] data;
	static int numberOfTestImages = 2;
	static int numberOfTestImages3D = 2;
	static final int LOOPS = 20;

	// Allow testing different settings.
	// Note that the float processor must use absolute values as the relative ones are converted to floats
	// and this may result in different output.
	// The second method will be used with negative values so use Auto-threshold
	int[] backgroundMethod = new int[] { FindFociProcessor.BACKGROUND_ABSOLUTE,
			FindFociProcessor.BACKGROUND_AUTO_THRESHOLD };
	double[] backgroundParameter = new double[] { bias, 0 };
	String[] autoThresholdMethod = new String[] { "", AutoThreshold.Method.OTSU.name };
	int[] searchMethod = new int[] { FindFociProcessor.SEARCH_ABOVE_BACKGROUND,
			FindFociProcessor.SEARCH_ABOVE_BACKGROUND };
	double[] searchParameter = new double[] { 0.3, 0.7 };
	int[] maxPeaks = new int[] { 1000, 1000 };
	int[] minSize = new int[] { 5, 3 };
	int[] peakMethod = new int[] { FindFociProcessor.PEAK_ABSOLUTE, FindFociProcessor.PEAK_ABSOLUTE };
	double[] peakParameter = new double[] { 10, 20 };
	int[] outputType = new int[] { FindFociProcessor.OUTPUT_MASK, FindFociProcessor.OUTPUT_MASK_PEAKS };
	int[] sortIndex = new int[] { FindFociProcessor.SORT_INTENSITY, FindFociProcessor.SORT_MAX_VALUE };
	int[] options = new int[] { FindFociProcessor.OPTION_MINIMUM_ABOVE_SADDLE, 0 };
	double[] blur = new double[] { 0, 0 };
	int[] centreMethod = new int[] { FindFoci.CENTRE_MAX_VALUE_SEARCH, FindFoci.CENTRE_MAX_VALUE_ORIGINAL };
	double[] centreParameter = new double[] { 2, 2 };
	double[] fractionParameter = new double[] { 0.5, 0 };

	@Test
	public void isSameResultUsingIntProcessor()
	{
		final boolean nonContiguous = true;
		for (final ImagePlus imp : createData())
			for (int i = 0; i < backgroundMethod.length; i++)
			{
				final FindFociResults r1 = runLegacy(imp, i);
				final FindFociResults r2 = runInt(imp, i, false, nonContiguous);
				isEqual(true, r1, r2, i, nonContiguous);
			}
	}

	@Test
	public void isSameResultUsingOptimisedIntProcessor()
	{
		for (final ImagePlus imp : createData())
			for (final boolean nonContiguous : new boolean[] { true, false })
				for (int i = 0; i < backgroundMethod.length; i++)
				{
					final FindFociResults r1 = runInt(imp, i, false, nonContiguous);
					final FindFociResults r2 = runInt(imp, i, true, nonContiguous);
					isEqual(false, r1, r2, i, nonContiguous);
				}
	}

	@Test
	public void isSameResultUsingFloatProcessor()
	{
		for (final ImagePlus imp : createData())
			for (final boolean nonContiguous : new boolean[] { true, false })
				for (int i = 0; i < backgroundMethod.length; i++)
				{
					final FindFociResults r1 = runInt(imp, i, false, nonContiguous);
					final FindFociResults r2 = runFloat(imp, i, false, false, nonContiguous);
					isEqual(false, r1, r2, i, nonContiguous);
				}
	}

	@Test
	public void isSameResultUsingOptimisedFloatProcessor()
	{
		for (final ImagePlus imp : createData())
			for (final boolean nonContiguous : new boolean[] { true, false })
				for (int i = 0; i < backgroundMethod.length; i++)
				{
					final FindFociResults r1 = runFloat(imp, i, false, false, nonContiguous);
					final FindFociResults r2 = runFloat(imp, i, true, false, nonContiguous);
					isEqual(false, r1, r2, i, nonContiguous);
				}
	}

	@Test
	public void isSameResultUsingFloatProcessorWithNegativeValues()
	{
		for (final ImagePlus imp : createData())
			for (final boolean nonContiguous : new boolean[] { true, false })
				for (int i = 0; i < backgroundMethod.length; i++)
				{
					if (FindFociBaseProcessor.isSortIndexSensitiveToNegativeValues(sortIndex[i]))
						continue;
					final FindFociResults r1 = runFloat(imp, i, false, false, nonContiguous);
					final FindFociResults r2 = runFloat(imp, i, false, true, nonContiguous);
					isEqual(false, r1, r2, i, true, nonContiguous);
				}
	}

	@Test
	public void isSameResultUsingOptimisedFloatProcessorWithNegativeValues()
	{
		for (final ImagePlus imp : createData())
			for (final boolean nonContiguous : new boolean[] { true, false })
				for (int i = 0; i < backgroundMethod.length; i++)
				{
					if (FindFociBaseProcessor.isSortIndexSensitiveToNegativeValues(sortIndex[i]))
						continue;
					final FindFociResults r1 = runFloat(imp, i, true, false, nonContiguous);
					final FindFociResults r2 = runFloat(imp, i, true, true, nonContiguous);
					isEqual(false, r1, r2, i, true, nonContiguous);
				}
	}

	@Test
	public void isSameResultUsingIntProcessorWithStagedMethods()
	{
		for (final ImagePlus imp : createData())
			for (final boolean nonContiguous : new boolean[] { true, false })
				for (int i = 0; i < backgroundMethod.length; i++)
				{
					final FindFociResults r1 = runInt(imp, i, false, nonContiguous);
					final FindFociResults r2 = runIntStaged(imp, i, false, nonContiguous);
					isEqual(false, r1, r2, i, nonContiguous);
				}
	}

	@Test
	public void isSameResultUsingOptimisedIntProcessorWithStagedMethods()
	{
		for (final ImagePlus imp : createData())
			for (final boolean nonContiguous : new boolean[] { true, false })
				for (int i = 0; i < backgroundMethod.length; i++)
				{
					final FindFociResults r1 = runInt(imp, i, true, nonContiguous);
					final FindFociResults r2 = runIntStaged(imp, i, true, nonContiguous);
					isEqual(false, r1, r2, i, nonContiguous);
				}
	}

	@Test
	public void isSameResultUsingFloatProcessorWithStagedMethods()
	{
		for (final ImagePlus imp : createData())
			for (final boolean nonContiguous : new boolean[] { true, false })
				for (int i = 0; i < backgroundMethod.length; i++)
				{
					final FindFociResults r1 = runFloat(imp, i, false, false, nonContiguous);
					final FindFociResults r2 = runFloatStaged(imp, i, false, false, nonContiguous);
					isEqual(false, r1, r2, i, nonContiguous);
				}
	}

	@Test
	public void isSameResultUsingOptimisedFloatProcessorWithStagedMethods()
	{
		for (final ImagePlus imp : createData())
			for (final boolean nonContiguous : new boolean[] { true, false })
				for (int i = 0; i < backgroundMethod.length; i++)
				{
					final FindFociResults r1 = runFloat(imp, i, true, false, nonContiguous);
					final FindFociResults r2 = runFloatStaged(imp, i, true, false, nonContiguous);
					isEqual(false, r1, r2, i, nonContiguous);
				}
	}

	@Test
	public void isSameResultUsingFloatProcessorWithStagedMethodsWithNegativeValues()
	{
		for (final ImagePlus imp : createData())
			for (final boolean nonContiguous : new boolean[] { true, false })
				for (int i = 0; i < backgroundMethod.length; i++)
				{
					if (FindFociBaseProcessor.isSortIndexSensitiveToNegativeValues(sortIndex[i]))
						continue;
					final FindFociResults r1 = runFloat(imp, i, false, false, nonContiguous);
					final FindFociResults r2 = runFloatStaged(imp, i, false, true, nonContiguous);
					isEqual(false, r1, r2, i, true, nonContiguous);
				}
	}

	@Test
	public void isSameResultUsingOptimisedFloatProcessorWithStagedMethodsWithNegativeValues()
	{
		for (final ImagePlus imp : createData())
			for (final boolean nonContiguous : new boolean[] { true, false })
				for (int i = 0; i < backgroundMethod.length; i++)
				{
					if (FindFociBaseProcessor.isSortIndexSensitiveToNegativeValues(sortIndex[i]))
						continue;
					final FindFociResults r1 = runFloat(imp, i, true, false, nonContiguous);
					final FindFociResults r2 = runFloatStaged(imp, i, true, true, nonContiguous);
					isEqual(false, r1, r2, i, true, nonContiguous);
				}
	}

	@Test
	public void isFasterUsingOptimisedIntProcessor()
	{
		TestAssume.assumeLowComplexity();

		// Get settings to try for the speed test
		final int[] indices = new int[] { 1 };

		// Warm up
		createData();
		//runInt(data[0], indices[0], false);
		//runInt(data[0], indices[0], true);

		long time1 = Long.MAX_VALUE;
		for (int n = LOOPS; n-- > 0;)
		{
			start();
			for (final ImagePlus imp : data)
				for (final int i : indices)
					for (final boolean nonContiguous : new boolean[] { true, false })
						runInt(imp, i, false, nonContiguous);
			time1 = stop(time1);
		}
		long time2 = Long.MAX_VALUE;
		for (int n = LOOPS; n-- > 0;)
		{
			start();
			for (final ImagePlus imp : data)
				for (final int i : indices)
					for (final boolean nonContiguous : new boolean[] { true, false })
						runInt(imp, i, true, nonContiguous);
			time2 = stop(time2);
		}
		TestLog.info("Int %d, Opt Int %d, %fx faster\n", time1, time2, (double) time1 / time2);
		Assert.assertTrue(time2 < time1);
	}

	@Test
	public void isFasterUsingOptimisedFloatProcessor()
	{
		TestAssume.assumeSpeedTest(TestComplexity.MEDIUM);

		// Get settings to try for the speed test
		final int[] indices = new int[] { 1 };

		// Warm up
		createData();
		//runFloat(data[0], indices[0], false, false);
		//runFloat(data[0], indices[0], true, false);

		long time1 = Long.MAX_VALUE;
		for (int n = LOOPS; n-- > 0;)
		{
			start();
			for (final ImagePlus imp : data)
				for (final int i : indices)
					for (final boolean nonContiguous : new boolean[] { true, false })
						runFloat(imp, i, false, false, nonContiguous);
			time1 = stop(time1);
		}
		long time2 = Long.MAX_VALUE;
		for (int n = LOOPS; n-- > 0;)
		{
			start();
			for (final ImagePlus imp : data)
				for (final int i : indices)
					for (final boolean nonContiguous : new boolean[] { true, false })
						runFloat(imp, i, true, false, nonContiguous);
			time2 = stop(time2);
		}

		// Comment out this assertion as it sometimes fails when running all the tests.
		// When running all the tests the some code gets run more and so
		// the JVM has had time to optimise it. When running the test alone the optimised processor is comparable.
		// I am not worried the optimisation has worse performance.

		//Assert.assertTrue(time2 < time1 * 1.4); // Allow discretion so test will pass
		TestLog.logSpeedTestResult(time2 < time1, "Float %d, Opt Float %d, %fx faster\n", time1, time2,
				(double) time1 / time2);
	}

	@Test
	public void isNotSlowerthanLegacyUsingOptimisedIntProcessor()
	{
		TestAssume.assumeSpeedTest(TestComplexity.MEDIUM);

		// Get settings to try for the speed test
		final int[] indices = new int[] { 1 };

		// Warm up
		createData();
		//runLegacy(data[0], indices[0]);
		//runInt(data[0], indices[0], true);

		long time1 = Long.MAX_VALUE;
		for (int n = LOOPS; n-- > 0;)
		{
			start();
			for (final ImagePlus imp : data)
				for (final int i : indices)
					runLegacy(imp, i);
			time1 = stop(time1);
		}
		long time2 = Long.MAX_VALUE;
		for (int n = LOOPS; n-- > 0;)
		{
			start();
			for (final ImagePlus imp : data)
				for (final int i : indices)
					runInt(imp, i, true, true);
			time2 = stop(time2);
		}

		// Comment out this assertion as it sometimes fails when running all the tests.
		// When running all the tests the legacy code gets run more and so
		// the JVM has had time to optimise it. When running the test alone the two are comparable.
		// I am not worried the new code has worse performance.

		//Assert.assertTrue(time2 < time1 * 1.4); // Allow some discretion over the legacy method
		TestLog.logSpeedTestResult(time2 < time1, "Legacy %d, Opt Int %d, %fx faster\n", time1, time2,
				(double) time1 / time2);
	}

	@Test
	public void isFasterUsingOptimisedIntProcessorOverOptimisedFloatProcessor()
	{
		TestAssume.assumeLowComplexity();

		// Get settings to try for the speed test
		final int[] indices = new int[] { 1 };

		// Warm up
		createData();
		//runFloat(data[0], indices[0], true, false);
		//runInt(data[0], indices[0], true);

		final ImagePlus[] data2 = new ImagePlus[data.length];
		for (int i = 0; i < data.length; i++)
			data2[i] = toFloat(data[i], false);

		long time1 = Long.MAX_VALUE;
		for (int n = LOOPS; n-- > 0;)
		{
			start();
			for (final ImagePlus imp : data2)
				for (final int i : indices)
					for (final boolean nonContiguous : new boolean[] { true, false })
						runFloat(imp, i, true, false, nonContiguous);
			time1 = stop(time1);
		}
		long time2 = Long.MAX_VALUE;
		for (int n = LOOPS; n-- > 0;)
		{
			start();
			for (final ImagePlus imp : data)
				for (final int i : indices)
					for (final boolean nonContiguous : new boolean[] { true, false })
						runInt(imp, i, true, nonContiguous);
			time2 = stop(time2);
		}
		TestLog.info("Opt Float %d, Opt Int %d, %fx faster\n", time1, time2, (double) time1 / time2);
		Assert.assertTrue(time2 < time1);
	}

	private static void isEqual(boolean legacy, FindFociResults r1, FindFociResults r2, int set, boolean nonContiguous)
	{
		isEqual(legacy, r1, r2, set, false, nonContiguous);
	}

	private static void isEqual(boolean legacy, FindFociResults r1, FindFociResults r2, int set, boolean negativeValues,
			boolean nonContiguous)
	{
		final String setName = String.format("Set %d (%b)", set, nonContiguous);

		final ImagePlus imp1 = r1.mask;
		final ImagePlus imp2 = r2.mask;
		Assert.assertEquals(setName + " Mask", imp1 != null, imp2 != null);
		if (imp1 != null)
		{
			//Assert.assertArrayEquals(set + " Mask values", (float[]) (imp1.getProcessor().convertToFloat().getPixels()),
			//		(float[]) (imp2.getProcessor().convertToFloat().getPixels()), 0);
		}
		final ArrayList<FindFociResult> results1 = r1.results;
		final ArrayList<FindFociResult> results2 = r2.results;
		//TestLog.info("N1=%d, N2=%d\n", results1.size(), results2.size());
		Assert.assertEquals(setName + " Results Size", results1.size(), results2.size());
		int counter = 0;
		final int offset = (negativeValues) ? FindFociTest.offset : 0;
		try
		{
			for (int i = 0; i < results1.size(); i++)
			{
				counter = i;
				//@formatter:off
    			final FindFociResult o1 = results1.get(i);
    			final FindFociResult o2 = results2.get(i);
    			//TestLog.info("[%d] %d,%d %f (%d) %d vs %d,%d %f (%d) %d\n", i,
    			//		o1.x, o1.y, o1.maxValue, o1.count, o1.saddleNeighbourId,
    			//		o2.x, o2.y, o2.maxValue, o2.count, o2.saddleNeighbourId);
    			Assert.assertEquals("X", o1.x, o2.x);
    			Assert.assertEquals("Y", o1.y, o2.y);
    			Assert.assertEquals("Z", o1.z, o2.z);
    			Assert.assertEquals("ID", o1.id, o2.id);
    			Assert.assertEquals("Count", o1.count, o2.count);
    			Assert.assertEquals("Saddle ID", o1.saddleNeighbourId, o2.saddleNeighbourId);
    			Assert.assertEquals("Count >Saddle", o1.countAboveSaddle, o2.countAboveSaddle);
    			// Single/Summed values can be cast to long as they should be 16-bit integers
    			Assert.assertEquals("Max", (long)o1.maxValue, (long)o2.maxValue + offset);
    			if (o2.highestSaddleValue != Float.NEGATIVE_INFINITY && o2.highestSaddleValue != 0)
    				Assert.assertEquals("Saddle value", (long)o1.highestSaddleValue, (long)o2.highestSaddleValue + offset);
    			if (legacy)
    			{
        			// Cast to integer as this is the result format of the legacy FindFoci code
        			Assert.assertEquals("Av Intensity", (long)o1.averageIntensity, (long)o2.averageIntensity + offset);
        			Assert.assertEquals("Av Intensity >background", (long)o1.averageIntensityAboveBackground, (long)o2.averageIntensityAboveBackground);
    			}
    			else
    			{
        			// Averages cannot be cast and are compared as floating-point values
        			TestAssert.assertEqualsRelative("Av Intensity", o1.averageIntensity, o2.averageIntensity + offset, 1e-9);
        			TestAssert.assertEqualsRelative("Av Intensity >background", o1.averageIntensityAboveBackground, o2.averageIntensityAboveBackground, 1e-9);
    			}
    			if (negativeValues)
    				continue;
    			Assert.assertEquals("Intensity", (long)o1.totalIntensity, (long)o2.totalIntensity);
    			Assert.assertEquals("Intensity >background", (long)o1.totalIntensityAboveBackground, (long)o2.totalIntensityAboveBackground);
    			Assert.assertEquals("Intensity > Saddle", (long)o1.intensityAboveSaddle, (long)o2.intensityAboveSaddle);
    			//@formatter:on
			}
		}
		catch (final AssertionError e)
		{
			TestAssert.wrapAssertionError(e, "%s [%d]", setName, counter);
		}
	}

	private FindFociResults runLegacy(ImagePlus imp, int i)
	{
		final FindFociLegacy ff = new FindFociLegacy();
		final Object[] result = ff.findMaxima(imp, null, backgroundMethod[i], backgroundParameter[i],
				autoThresholdMethod[i], searchMethod[i], searchParameter[i], maxPeaks[i], minSize[i], peakMethod[i],
				peakParameter[i], outputType[i], sortIndex[i], options[i], blur[i], centreMethod[i], centreParameter[i],
				fractionParameter[i]);
		@SuppressWarnings("unchecked")
		final ArrayList<int[]> resultsArray = (ArrayList<int[]>) result[1];
		ArrayList<FindFociResult> results = null;
		if (resultsArray != null)
		{
			results = new ArrayList<>();
			for (final int[] r : resultsArray)
			{
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
				r2.averageIntensityAboveBackground = r[FindFociLegacy.RESULT_AVERAGE_INTENSITY_MINUS_BACKGROUND];
				r2.countAboveSaddle = r[FindFociLegacy.RESULT_COUNT_ABOVE_SADDLE];
				r2.intensityAboveSaddle = r[FindFociLegacy.RESULT_INTENSITY_ABOVE_SADDLE];
				results.add(r2);
			}
		}
		return new FindFociResults((ImagePlus) result[0], results, null);
	}

	private FindFociResults runInt(ImagePlus imp, int i, boolean optimised, boolean nonContiguous)
	{
		final FindFoci ff = new FindFoci();
		ff.setOptimisedProcessor(optimised);
		final int flags = (nonContiguous) ? options[i] : options[i] | FindFociProcessor.OPTION_CONTIGUOUS_ABOVE_SADDLE;
		return ff.findMaxima(imp, null, backgroundMethod[i], backgroundParameter[i], autoThresholdMethod[i],
				searchMethod[i], searchParameter[i], maxPeaks[i], minSize[i], peakMethod[i], peakParameter[i],
				outputType[i], sortIndex[i], flags, blur[i], centreMethod[i], centreParameter[i], fractionParameter[i]);
	}

	private FindFociResults runFloat(ImagePlus imp, int i, boolean optimised, boolean negative, boolean nonContiguous)
	{
		imp = toFloat(imp, negative);
		final FindFoci ff = new FindFoci();
		ff.setOptimisedProcessor(optimised);
		final int flags = (nonContiguous) ? options[i] : options[i] | FindFociProcessor.OPTION_CONTIGUOUS_ABOVE_SADDLE;
		return ff.findMaxima(imp, null, backgroundMethod[i], backgroundParameter[i], autoThresholdMethod[i],
				searchMethod[i], searchParameter[i], maxPeaks[i], minSize[i], peakMethod[i], peakParameter[i],
				outputType[i], sortIndex[i], flags, blur[i], centreMethod[i], centreParameter[i], fractionParameter[i]);
	}

	private static ImagePlus toFloat(ImagePlus imp, boolean negative)
	{
		final ImageStack stack = imp.getImageStack();
		final ImageStack newStack = new ImageStack(stack.getWidth(), stack.getHeight());
		for (int n = 1; n <= stack.getSize(); n++)
		{
			final FloatProcessor fp = (FloatProcessor) stack.getProcessor(n).convertToFloat();
			if (negative)
				fp.subtract(offset);
			newStack.addSlice(fp);
		}
		return new ImagePlus(null, newStack);
	}

	private FindFociResults runIntStaged(ImagePlus imp, int i, boolean optimised, boolean nonContiguous)
	{
		final FindFoci ff = new FindFoci();
		ff.setOptimisedProcessor(optimised);
		final int flags = (nonContiguous) ? options[i] : options[i] | FindFociProcessor.OPTION_CONTIGUOUS_ABOVE_SADDLE;
		final ImagePlus imp2 = ff.blur(imp, blur[i]);
		final FindFociInitResults initResults = ff.findMaximaInit(imp, imp2, null, backgroundMethod[i],
				autoThresholdMethod[i], flags);
		final FindFociSearchResults searchResults = ff.findMaximaSearch(initResults, backgroundMethod[i],
				backgroundParameter[i], searchMethod[i], searchParameter[i]);
		FindFociMergeTempResults mergePeakResults = ff.findMaximaMergePeak(initResults, searchResults, peakMethod[i],
				peakParameter[i]);
		mergePeakResults = ff.findMaximaMergeSize(initResults, mergePeakResults, minSize[i]);
		final FindFociMergeResults mergeResults = ff.findMaximaMergeFinal(initResults, mergePeakResults, minSize[i],
				flags, blur[i]);
		final FindFociPrelimResults prelimResults = ff.findMaximaPrelimResults(initResults, mergeResults, maxPeaks[i],
				sortIndex[i], centreMethod[i], centreParameter[i]);
		return ff.findMaximaMaskResults(initResults, mergeResults, prelimResults, outputType[i], autoThresholdMethod[i],
				"FindFociTest", fractionParameter[i]);
	}

	private FindFociResults runFloatStaged(ImagePlus imp, int i, boolean optimised, boolean negative,
			boolean nonContiguous)
	{
		imp = toFloat(imp, negative);
		final FindFoci ff = new FindFoci();
		final ImagePlus imp2 = ff.blur(imp, blur[i]);
		ff.setOptimisedProcessor(optimised);
		final int flags = (nonContiguous) ? options[i] : options[i] | FindFociProcessor.OPTION_CONTIGUOUS_ABOVE_SADDLE;
		final FindFociInitResults initResults = ff.findMaximaInit(imp, imp2, null, backgroundMethod[i],
				autoThresholdMethod[i], flags);
		final FindFociSearchResults searchResults = ff.findMaximaSearch(initResults, backgroundMethod[i],
				backgroundParameter[i], searchMethod[i], searchParameter[i]);
		FindFociMergeTempResults mergePeakResults = ff.findMaximaMergePeak(initResults, searchResults, peakMethod[i],
				peakParameter[i]);
		mergePeakResults = ff.findMaximaMergeSize(initResults, mergePeakResults, minSize[i]);
		final FindFociMergeResults mergeResults = ff.findMaximaMergeFinal(initResults, mergePeakResults, minSize[i],
				flags, blur[i]);
		final FindFociPrelimResults prelimResults = ff.findMaximaPrelimResults(initResults, mergeResults, maxPeaks[i],
				sortIndex[i], centreMethod[i], centreParameter[i]);
		return ff.findMaximaMaskResults(initResults, mergeResults, prelimResults, outputType[i], autoThresholdMethod[i],
				"FindFociTest", fractionParameter[i]);
	}

	private static synchronized ImagePlus[] createData()
	{
		if (data == null)
		{
			final RandomGenerator rg = TestSettings.getRandomGenerator();
			TestLog.infoln("Creating data ...");
			data = new ImagePlus[numberOfTestImages + numberOfTestImages3D];
			int index = 0;
			for (int i = 0; i < numberOfTestImages; i++)
				data[index++] = createImageData(rg);
			for (int i = 0; i < numberOfTestImages3D; i++)
				data[index++] = createImageData3D(rg);
			TestLog.infoln("Created data");
		}
		return data;
	}

	private static ImagePlus createImageData(RandomGenerator rg)
	{
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
		//uk.ac.sussex.gdsc.core.ij.Utils.display(title, ip);
		return new ImagePlus(title, ip);
	}

	private static short[] combine(RandomGenerator rg, float[] data1, float[] data2, float[] data3)
	{
		// Combine images and add a bias and read noise
		final RandomDataGenerator rdg = new RandomDataGenerator(rg);
		final short[] data = new short[data1.length];
		for (int i = 0; i < data.length; i++)
		{
			final double mu = data1[i] + data2[i] + data3[i];
			double v = (((mu != 0) ? rdg.nextPoisson(mu) : 0) + rdg.nextGaussian(bias, 5));
			if (v < 0)
				v = 0;
			else if (v > 65535)
				v = 65535;
			data[i] = (short) v;
		}
		return data;
	}

	private static float[] createSpots(RandomGenerator rg, int size, int n, int min, int max, double sigmaX,
			double sigmaY)
	{
		final float[] data = new float[size * size];
		// Randomly put on spots
		final RandomDataGenerator rdg = new RandomDataGenerator(rg);
		while (n-- > 0)
			data[rg.nextInt(data.length)] = rdg.nextInt(min, max);

		// Blur
		final FloatProcessor fp = new FloatProcessor(size, size, data);
		final GaussianBlur gb = new GaussianBlur();
		gb.blurFloat(fp, sigmaX, sigmaY, 0.0002);

		return (float[]) fp.getPixels();
	}

	private static ImagePlus createImageData3D(RandomGenerator rg)
	{
		// Create an image with peaks
		final int size = 64;
		final int z = 5;
		final int n = 20;
		final float[][] data1 = createSpots3D(rg, size, z, n, 5000, 10000, 2.5, 3.0);
		final float[][] data2 = createSpots3D(rg, size, z, n, 10000, 20000, 4.5, 3.5);
		final float[][] data3 = createSpots3D(rg, size, z, n, 20000, 40000, 6.5, 5);
		final ImageStack stack = new ImageStack(size, size);
		for (int i = 0; i < data1.length; i++)
		{
			final short[] data = combine(rg, data1[i], data2[i], data3[i]);
			stack.addSlice(new ShortProcessor(size, size, data, null));
		}
		// Show
		final String title = "FindFociTest3D";
		//uk.ac.sussex.gdsc.core.ij.Utils.display(title, stack);
		return new ImagePlus(title, stack);
	}

	private static float[][] createSpots3D(RandomGenerator rg, int size, int z, int n, int min, int max, double sigmaX,
			double sigmaY)
	{
		final float[] data = new float[size * size];
		// Randomly put on spots
		final RandomDataGenerator rdg = new RandomDataGenerator(rg);
		while (n-- > 0)
			data[rg.nextInt(data.length)] = rdg.nextInt(min, max);

		final int middle = z / 2;
		final float[][] result = new float[z][];
		for (int i = 0; i < z; i++)
		{
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
	 * @param z
	 *            the z
	 * @param depth
	 *            the depth
	 * @return The scale
	 */
	private static double createWidthScale(double z, double depth)
	{
		z /= depth;
		return 1.0 + z * z * 0.5;
	}

	private long time;

	private void start()
	{
		time = System.nanoTime();

	}

	private long stop(long t)
	{
		return Math.min(t, System.nanoTime() - time);
	}

}
