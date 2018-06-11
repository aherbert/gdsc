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
package gdsc.foci;

import java.util.ArrayList;

import org.apache.commons.math3.random.RandomDataGenerator;
import org.apache.commons.math3.random.RandomGenerator;
import org.apache.commons.math3.random.Well19937c;
import org.junit.Assert;
import org.junit.Test;

import gdsc.core.threshold.AutoThreshold;
import ij.ImagePlus;
import ij.ImageStack;
import ij.plugin.filter.GaussianBlur;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;
import ij.process.ShortProcessor;

public class FindFociTest
{
	static int bias = 500;
	static int offset = bias + 100;
	static RandomGenerator rand = new Well19937c(30051977);
	static ImagePlus[] data;
	static int numberOfTestImages = 2;
	static int numberOfTestImages3D = 2;
	static final int LOOPS = 20;

	// Allow testing different settings.
	// Note that the float processor must use absolute values as the relative ones are converted to floats
	// and this may result in different output.
	// The second method will be used with negative values so use Auto-threshold
	int[] backgroundMethod = new int[] { FindFoci.BACKGROUND_ABSOLUTE, FindFoci.BACKGROUND_AUTO_THRESHOLD };
	double[] backgroundParameter = new double[] { bias, 0 };
	String[] autoThresholdMethod = new String[] { "", AutoThreshold.Method.OTSU.name };
	int[] searchMethod = new int[] { FindFoci.SEARCH_ABOVE_BACKGROUND, FindFoci.SEARCH_ABOVE_BACKGROUND };
	double[] searchParameter = new double[] { 0.3, 0.7 };
	int[] maxPeaks = new int[] { 1000, 1000 };
	int[] minSize = new int[] { 5, 3 };
	int[] peakMethod = new int[] { FindFoci.PEAK_ABSOLUTE, FindFoci.PEAK_ABSOLUTE };
	double[] peakParameter = new double[] { 10, 20 };
	int[] outputType = new int[] { FindFoci.OUTPUT_MASK, FindFoci.OUTPUT_MASK_PEAKS };
	int[] sortIndex = new int[] { FindFoci.SORT_INTENSITY, FindFoci.SORT_MAX_VALUE };
	int[] options = new int[] { FindFoci.OPTION_MINIMUM_ABOVE_SADDLE, 0 };
	double[] blur = new double[] { 0, 0 };
	int[] centreMethod = new int[] { FindFoci.CENTRE_MAX_VALUE_SEARCH, FindFoci.CENTRE_MAX_VALUE_ORIGINAL };
	double[] centreParameter = new double[] { 2, 2 };
	double[] fractionParameter = new double[] { 0.5, 0 };

	@Test
	public void isSameResultUsingIntProcessor()
	{
		boolean nonContiguous = true;
		for (ImagePlus imp : createData())
		{
			for (int i = 0; i < backgroundMethod.length; i++)
			{
				FindFociResults r1 = runLegacy(imp, i);
				FindFociResults r2 = runInt(imp, i, false, nonContiguous);
				isEqual(r1, r2, i, nonContiguous);
			}
		}
	}

	@Test
	public void isSameResultUsingOptimisedIntProcessor()
	{
		for (ImagePlus imp : createData())
		{
			for (boolean nonContiguous : new boolean[] { true, false })
				for (int i = 0; i < backgroundMethod.length; i++)
				{
					FindFociResults r1 = runInt(imp, i, false, nonContiguous);
					FindFociResults r2 = runInt(imp, i, true, nonContiguous);
					isEqual(r1, r2, i, nonContiguous);
				}
		}
	}

	@Test
	public void isSameResultUsingFloatProcessor()
	{
		for (ImagePlus imp : createData())
		{
			for (boolean nonContiguous : new boolean[] { true, false })
				for (int i = 0; i < backgroundMethod.length; i++)
				{
					FindFociResults r1 = runInt(imp, i, false, nonContiguous);
					FindFociResults r2 = runFloat(imp, i, false, false, nonContiguous);
					isEqual(r1, r2, i, nonContiguous);
				}
		}
	}

	@Test
	public void isSameResultUsingOptimisedFloatProcessor()
	{
		for (ImagePlus imp : createData())
		{
			for (boolean nonContiguous : new boolean[] { true, false })
				for (int i = 0; i < backgroundMethod.length; i++)
				{
					FindFociResults r1 = runFloat(imp, i, false, false, nonContiguous);
					FindFociResults r2 = runFloat(imp, i, true, false, nonContiguous);
					isEqual(r1, r2, i, nonContiguous);
				}
		}
	}

	@Test
	public void isSameResultUsingFloatProcessorWithNegativeValues()
	{
		for (ImagePlus imp : createData())
		{
			for (boolean nonContiguous : new boolean[] { true, false })
				for (int i = 0; i < backgroundMethod.length; i++)
				{
					if (FindFociBaseProcessor.isSortIndexSenstiveToNegativeValues(sortIndex[i]))
						continue;
					FindFociResults r1 = runFloat(imp, i, false, false, nonContiguous);
					FindFociResults r2 = runFloat(imp, i, false, true, nonContiguous);
					isEqual(r1, r2, i, true, nonContiguous);
				}
		}
	}

	@Test
	public void isSameResultUsingOptimisedFloatProcessorWithNegativeValues()
	{
		for (ImagePlus imp : createData())
		{
			for (boolean nonContiguous : new boolean[] { true, false })
				for (int i = 0; i < backgroundMethod.length; i++)
				{
					if (FindFociBaseProcessor.isSortIndexSenstiveToNegativeValues(sortIndex[i]))
						continue;
					FindFociResults r1 = runFloat(imp, i, true, false, nonContiguous);
					FindFociResults r2 = runFloat(imp, i, true, true, nonContiguous);
					isEqual(r1, r2, i, true, nonContiguous);
				}
		}
	}

	@Test
	public void isSameResultUsingIntProcessorWithStagedMethods()
	{
		for (ImagePlus imp : createData())
		{
			for (boolean nonContiguous : new boolean[] { true, false })
				for (int i = 0; i < backgroundMethod.length; i++)
				{
					FindFociResults r1 = runInt(imp, i, false, nonContiguous);
					FindFociResults r2 = runIntStaged(imp, i, false, nonContiguous);
					isEqual(r1, r2, i, nonContiguous);
				}
		}
	}

	@Test
	public void isSameResultUsingOptimisedIntProcessorWithStagedMethods()
	{
		for (ImagePlus imp : createData())
		{
			for (boolean nonContiguous : new boolean[] { true, false })
				for (int i = 0; i < backgroundMethod.length; i++)
				{
					FindFociResults r1 = runInt(imp, i, true, nonContiguous);
					FindFociResults r2 = runIntStaged(imp, i, true, nonContiguous);
					isEqual(r1, r2, i, nonContiguous);
				}
		}
	}

	@Test
	public void isSameResultUsingFloatProcessorWithStagedMethods()
	{
		for (ImagePlus imp : createData())
		{
			for (boolean nonContiguous : new boolean[] { true, false })
				for (int i = 0; i < backgroundMethod.length; i++)
				{
					FindFociResults r1 = runFloat(imp, i, false, false, nonContiguous);
					FindFociResults r2 = runFloatStaged(imp, i, false, false, nonContiguous);
					isEqual(r1, r2, i, nonContiguous);
				}
		}
	}

	@Test
	public void isSameResultUsingOptimisedFloatProcessorWithStagedMethods()
	{
		for (ImagePlus imp : createData())
		{
			for (boolean nonContiguous : new boolean[] { true, false })
				for (int i = 0; i < backgroundMethod.length; i++)
				{
					FindFociResults r1 = runFloat(imp, i, true, false, nonContiguous);
					FindFociResults r2 = runFloatStaged(imp, i, true, false, nonContiguous);
					isEqual(r1, r2, i, nonContiguous);
				}
		}
	}

	@Test
	public void isSameResultUsingFloatProcessorWithStagedMethodsWithNegativeValues()
	{
		for (ImagePlus imp : createData())
		{
			for (boolean nonContiguous : new boolean[] { true, false })
				for (int i = 0; i < backgroundMethod.length; i++)
				{
					if (FindFociBaseProcessor.isSortIndexSenstiveToNegativeValues(sortIndex[i]))
						continue;
					FindFociResults r1 = runFloat(imp, i, false, false, nonContiguous);
					FindFociResults r2 = runFloatStaged(imp, i, false, true, nonContiguous);
					isEqual(r1, r2, i, true, nonContiguous);
				}
		}
	}

	@Test
	public void isSameResultUsingOptimisedFloatProcessorWithStagedMethodsWithNegativeValues()
	{
		for (ImagePlus imp : createData())
		{
			for (boolean nonContiguous : new boolean[] { true, false })
				for (int i = 0; i < backgroundMethod.length; i++)
				{
					if (FindFociBaseProcessor.isSortIndexSenstiveToNegativeValues(sortIndex[i]))
						continue;
					FindFociResults r1 = runFloat(imp, i, true, false, nonContiguous);
					FindFociResults r2 = runFloatStaged(imp, i, true, true, nonContiguous);
					isEqual(r1, r2, i, true, nonContiguous);
				}
		}
	}

	@Test
	public void isFasterUsingOptimisedIntProcessor()
	{
		// Get settings to try for the speed test
		int[] indices = new int[] { 1 };

		// Warm up
		createData();
		//runInt(data[0], indices[0], false);
		//runInt(data[0], indices[0], true);

		long time1 = Long.MAX_VALUE;
		for (int n = LOOPS; n-- > 0;)
		{
			start();
			for (ImagePlus imp : data)
			{
				for (int i : indices)
				{
					for (boolean nonContiguous : new boolean[] { true, false })
						runInt(imp, i, false, nonContiguous);
				}
			}
			time1 = stop(time1);
		}
		long time2 = Long.MAX_VALUE;
		for (int n = LOOPS; n-- > 0;)
		{
			start();
			for (ImagePlus imp : data)
			{
				for (int i : indices)
				{
					for (boolean nonContiguous : new boolean[] { true, false })
						runInt(imp, i, true, nonContiguous);
				}
			}
			time2 = stop(time2);
		}
		System.out.printf("Int %d, Opt Int %d, %fx faster\n", time1, time2, (double) time1 / time2);
		Assert.assertTrue(time2 < time1);
	}

	@Test
	public void isFasterUsingOptimisedFloatProcessor()
	{
		// Get settings to try for the speed test
		int[] indices = new int[] { 1 };

		// Warm up
		createData();
		//runFloat(data[0], indices[0], false, false);
		//runFloat(data[0], indices[0], true, false);

		long time1 = Long.MAX_VALUE;
		for (int n = LOOPS; n-- > 0;)
		{
			start();
			for (ImagePlus imp : data)
			{
				for (int i : indices)
				{
					for (boolean nonContiguous : new boolean[] { true, false })
						runFloat(imp, i, false, false, nonContiguous);
				}
			}
			time1 = stop(time1);
		}
		long time2 = Long.MAX_VALUE;
		for (int n = LOOPS; n-- > 0;)
		{
			start();
			for (ImagePlus imp : data)
			{
				for (int i : indices)
				{
					for (boolean nonContiguous : new boolean[] { true, false })
						runFloat(imp, i, true, false, nonContiguous);
				}
			}
			time2 = stop(time2);
		}
		System.out.printf("Float %d, Opt Float %d, %fx faster\n", time1, time2, (double) time1 / time2);

		// Comment out this assertion as it sometimes fails when running all the tests. 
		// When running all the tests the some code gets run more and so
		// the JVM has had time to optimise it. When running the test alone the optimised processor is comparable.
		// I am not worried the optimisation has worse performance.

		//Assert.assertTrue(time2 < time1 * 1.4); // Allow discretion so test will pass
	}

	@Test
	public void isNotSlowerthanLegacyUsingOptimisedIntProcessor()
	{
		// Get settings to try for the speed test
		int[] indices = new int[] { 1 };

		// Warm up
		createData();
		//runLegacy(data[0], indices[0]);
		//runInt(data[0], indices[0], true);

		long time1 = Long.MAX_VALUE;
		for (int n = LOOPS; n-- > 0;)
		{
			start();
			for (ImagePlus imp : data)
			{
				for (int i : indices)
				{
					runLegacy(imp, i);
				}
			}
			time1 = stop(time1);
		}
		long time2 = Long.MAX_VALUE;
		for (int n = LOOPS; n-- > 0;)
		{
			start();
			for (ImagePlus imp : data)
			{
				for (int i : indices)
				{
					runInt(imp, i, true, true);
				}
			}
			time2 = stop(time2);
		}
		System.out.printf("Legacy %d, Opt Int %d, %fx faster\n", time1, time2, (double) time1 / time2);

		// Comment out this assertion as it sometimes fails when running all the tests. 
		// When running all the tests the legacy code gets run more and so
		// the JVM has had time to optimise it. When running the test alone the two are comparable.
		// I am not worried the new code has worse performance.

		//Assert.assertTrue(time2 < time1 * 1.4); // Allow some discretion over the legacy method
	}

	@Test
	public void isFasterUsingOptimisedIntProcessorOverOptimisedFloatProcessor()
	{
		// Get settings to try for the speed test
		int[] indices = new int[] { 1 };

		// Warm up
		createData();
		//runFloat(data[0], indices[0], true, false);
		//runInt(data[0], indices[0], true);

		ImagePlus[] data2 = new ImagePlus[data.length];
		for (int i = 0; i < data.length; i++)
		{
			data2[i] = toFloat(data[i], false);
		}

		long time1 = Long.MAX_VALUE;
		for (int n = LOOPS; n-- > 0;)
		{
			start();
			for (ImagePlus imp : data2)
			{
				for (int i : indices)
				{
					for (boolean nonContiguous : new boolean[] { true, false })
						runFloat(imp, i, true, false, nonContiguous);
				}
			}
			time1 = stop(time1);
		}
		long time2 = Long.MAX_VALUE;
		for (int n = LOOPS; n-- > 0;)
		{
			start();
			for (ImagePlus imp : data)
			{
				for (int i : indices)
				{
					for (boolean nonContiguous : new boolean[] { true, false })
						runInt(imp, i, true, nonContiguous);
				}
			}
			time2 = stop(time2);
		}
		System.out.printf("Opt Float %d, Opt Int %d, %fx faster\n", time1, time2, (double) time1 / time2);
		Assert.assertTrue(time2 < time1);
	}

	private void isEqual(FindFociResults r1, FindFociResults r2, int set, boolean nonContiguous)
	{
		isEqual(r1, r2, set, false, nonContiguous);
	}

	private void isEqual(FindFociResults r1, FindFociResults r2, int set, boolean negativeValues, boolean nonContiguous)
	{
		String setName = String.format("Set %d (%b)", set, nonContiguous);

		ImagePlus imp1 = r1.mask;
		ImagePlus imp2 = r2.mask;
		Assert.assertEquals(setName + " Mask", imp1 != null, imp2 != null);
		if (imp1 != null)
		{
			//Assert.assertArrayEquals(set + " Mask values", (float[]) (imp1.getProcessor().convertToFloat().getPixels()),
			//		(float[]) (imp2.getProcessor().convertToFloat().getPixels()), 0);
		}
		ArrayList<FindFociResult> results1 = r1.results;
		ArrayList<FindFociResult> results2 = r2.results;
		//System.out.printf("N1=%d, N2=%d\n", results1.size(), results2.size());
		Assert.assertEquals(setName + " Results Size", results1.size(), results2.size());
		int counter = 0;
		final int offset = (negativeValues) ? FindFociTest.offset : 0;
		try
		{
			for (int i = 0; i < results1.size(); i++)
			{
				counter = i;
				//@formatter:off
    			FindFociResult o1 = results1.get(i);
    			FindFociResult o2 = results2.get(i);
    			//System.out.printf("[%d] %d,%d %f (%d) %d vs %d,%d %f (%d) %d\n", i, 
    			//		o1.x, o1.y, o1.maxValue, o1.count, o1.saddleNeighbourId, 
    			//		o2.x, o2.y, o2.maxValue, o2.count, o2.saddleNeighbourId);
    			Assert.assertEquals("X", o1.x, o2.x);
    			Assert.assertEquals("Y", o1.y, o2.y);
    			Assert.assertEquals("Z", o1.z, o2.z);
    			Assert.assertEquals("ID", o1.id, o2.id);
    			Assert.assertEquals("Count", o1.count, o2.count);
    			Assert.assertEquals("Saddle ID", o1.saddleNeighbourId, o2.saddleNeighbourId);
    			Assert.assertEquals("Count >Saddle", o1.countAboveSaddle, o2.countAboveSaddle);
    			Assert.assertEquals("Max", (int)o1.maxValue, (int)o2.maxValue + offset);
    			if (o2.highestSaddleValue != Float.NEGATIVE_INFINITY && o2.highestSaddleValue != 0)
    				Assert.assertEquals("Saddle value", (int)o1.highestSaddleValue, (int)o2.highestSaddleValue + offset);
    			Assert.assertEquals("Av Intensity", (int)o1.averageIntensity, (int)o2.averageIntensity + offset);
    			Assert.assertEquals("Av Intensity >background", (int)o1.averageIntensityAboveBackground, (int)o2.averageIntensityAboveBackground);
    			if (negativeValues)
    				continue;
    			Assert.assertEquals("Intensity", (int)o1.totalIntensity, (int)o2.totalIntensity);
    			Assert.assertEquals("Intensity >background", (int)o1.totalIntensityAboveBackground, (int)o2.totalIntensityAboveBackground);
    			Assert.assertEquals("Intensity > Saddle", (int)o1.intensityAboveSaddle, (int)o2.intensityAboveSaddle);
    			//@formatter:on
			}
		}
		catch (AssertionError e)
		{
			throw new AssertionError(String.format("%s [%d]: " + e.getMessage(), setName, counter), e);
		}
	}

	private FindFociResults runLegacy(ImagePlus imp, int i)
	{
		FindFociLegacy ff = new FindFociLegacy();
		Object[] result = ff.findMaxima(imp, null, backgroundMethod[i], backgroundParameter[i], autoThresholdMethod[i],
				searchMethod[i], searchParameter[i], maxPeaks[i], minSize[i], peakMethod[i], peakParameter[i],
				outputType[i], sortIndex[i], options[i], blur[i], centreMethod[i], centreParameter[i],
				fractionParameter[i]);
		@SuppressWarnings("unchecked")
		ArrayList<int[]> resultsArray = (ArrayList<int[]>) result[1];
		ArrayList<FindFociResult> results = null;
		if (resultsArray != null)
		{
			results = new ArrayList<FindFociResult>();
			for (int[] r : resultsArray)
			{
				FindFociResult r2 = new FindFociResult();
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
		FindFoci ff = new FindFoci();
		ff.setOptimisedProcessor(optimised);
		final int flags = (nonContiguous) ? options[i] : options[i] | FindFociProcessor.OPTION_CONTIGUOUS_ABOVE_SADDLE;
		return ff.findMaxima(imp, null, backgroundMethod[i], backgroundParameter[i], autoThresholdMethod[i],
				searchMethod[i], searchParameter[i], maxPeaks[i], minSize[i], peakMethod[i], peakParameter[i],
				outputType[i], sortIndex[i], flags, blur[i], centreMethod[i], centreParameter[i], fractionParameter[i]);
	}

	private FindFociResults runFloat(ImagePlus imp, int i, boolean optimised, boolean negative, boolean nonContiguous)
	{
		imp = toFloat(imp, negative);
		FindFoci ff = new FindFoci();
		ff.setOptimisedProcessor(optimised);
		final int flags = (nonContiguous) ? options[i] : options[i] | FindFociProcessor.OPTION_CONTIGUOUS_ABOVE_SADDLE;
		return ff.findMaxima(imp, null, backgroundMethod[i], backgroundParameter[i], autoThresholdMethod[i],
				searchMethod[i], searchParameter[i], maxPeaks[i], minSize[i], peakMethod[i], peakParameter[i],
				outputType[i], sortIndex[i], flags, blur[i], centreMethod[i], centreParameter[i], fractionParameter[i]);
	}

	private ImagePlus toFloat(ImagePlus imp, boolean negative)
	{
		ImageStack stack = imp.getImageStack();
		ImageStack newStack = new ImageStack(stack.getWidth(), stack.getHeight());
		for (int n = 1; n <= stack.getSize(); n++)
		{
			FloatProcessor fp = (FloatProcessor) stack.getProcessor(n).convertToFloat();
			if (negative)
			{
				fp.subtract(offset);
			}
			newStack.addSlice(fp);
		}
		return new ImagePlus(null, newStack);
	}

	private FindFociResults runIntStaged(ImagePlus imp, int i, boolean optimised, boolean nonContiguous)
	{
		FindFoci ff = new FindFoci();
		ff.setOptimisedProcessor(optimised);
		final int flags = (nonContiguous) ? options[i] : options[i] | FindFociProcessor.OPTION_CONTIGUOUS_ABOVE_SADDLE;
		ImagePlus imp2 = ff.blur(imp, blur[i]);
		FindFociInitResults initResults = ff.findMaximaInit(imp, imp2, null, backgroundMethod[i],
				autoThresholdMethod[i], flags);
		FindFociSearchResults searchResults = ff.findMaximaSearch(initResults, backgroundMethod[i],
				backgroundParameter[i], searchMethod[i], searchParameter[i]);
		FindFociMergeTempResults mergePeakResults = ff.findMaximaMergePeak(initResults, searchResults, peakMethod[i],
				peakParameter[i]);
		mergePeakResults = ff.findMaximaMergeSize(initResults, mergePeakResults, minSize[i]);
		FindFociMergeResults mergeResults = ff.findMaximaMergeFinal(initResults, mergePeakResults, minSize[i], flags,
				blur[i]);
		FindFociPrelimResults prelimResults = ff.findMaximaPrelimResults(initResults, mergeResults, maxPeaks[i],
				sortIndex[i], centreMethod[i], centreParameter[i]);
		return ff.findMaximaMaskResults(initResults, mergeResults, prelimResults, outputType[i], autoThresholdMethod[i],
				"FindFociTest", fractionParameter[i]);
	}

	private FindFociResults runFloatStaged(ImagePlus imp, int i, boolean optimised, boolean negative,
			boolean nonContiguous)
	{
		imp = toFloat(imp, negative);
		FindFoci ff = new FindFoci();
		ImagePlus imp2 = ff.blur(imp, blur[i]);
		ff.setOptimisedProcessor(optimised);
		final int flags = (nonContiguous) ? options[i] : options[i] | FindFociProcessor.OPTION_CONTIGUOUS_ABOVE_SADDLE;
		FindFociInitResults initResults = ff.findMaximaInit(imp, imp2, null, backgroundMethod[i],
				autoThresholdMethod[i], flags);
		FindFociSearchResults searchResults = ff.findMaximaSearch(initResults, backgroundMethod[i],
				backgroundParameter[i], searchMethod[i], searchParameter[i]);
		FindFociMergeTempResults mergePeakResults = ff.findMaximaMergePeak(initResults, searchResults, peakMethod[i],
				peakParameter[i]);
		mergePeakResults = ff.findMaximaMergeSize(initResults, mergePeakResults, minSize[i]);
		FindFociMergeResults mergeResults = ff.findMaximaMergeFinal(initResults, mergePeakResults, minSize[i], flags,
				blur[i]);
		FindFociPrelimResults prelimResults = ff.findMaximaPrelimResults(initResults, mergeResults, maxPeaks[i],
				sortIndex[i], centreMethod[i], centreParameter[i]);
		return ff.findMaximaMaskResults(initResults, mergeResults, prelimResults, outputType[i], autoThresholdMethod[i],
				"FindFociTest", fractionParameter[i]);
	}

	private static ImagePlus[] createData()
	{
		if (data == null)
		{
			System.out.println("Creating data ...");
			data = new ImagePlus[numberOfTestImages + numberOfTestImages3D];
			int index = 0;
			for (int i = 0; i < numberOfTestImages; i++)
				data[index++] = createImageData();
			for (int i = 0; i < numberOfTestImages3D; i++)
				data[index++] = createImageData3D();
			System.out.println("Created data");
		}
		return data;
	}

	private static ImagePlus createImageData()
	{
		// Create an image with peaks
		int size = 256;
		int n = 80;
		float[] data1 = createSpots(size, n, 5000, 10000, 2.5, 3.0);
		float[] data2 = createSpots(size, n, 10000, 20000, 4.5, 3.5);
		float[] data3 = createSpots(size, n, 20000, 40000, 6.5, 5);
		short[] data = combine(data1, data2, data3);
		// Show
		String title = "FindFociTest";
		ImageProcessor ip = new ShortProcessor(size, size, data, null);
		//gdsc.core.ij.Utils.display(title, ip);
		return new ImagePlus(title, ip);
	}

	private static short[] combine(float[] data1, float[] data2, float[] data3)
	{
		// Combine images and add a bias and read noise
		RandomDataGenerator rg = new RandomDataGenerator(rand);
		short[] data = new short[data1.length];
		for (int i = 0; i < data.length; i++)
		{
			final double mu = data1[i] + data2[i] + data3[i];
			data[i] = (short) (((mu != 0) ? rg.nextPoisson(mu) : 0) + rg.nextGaussian(bias, 5));
		}
		return data;
	}

	private static float[] createSpots(int size, int n, int min, int max, double sigmaX, double sigmaY)
	{
		float[] data = new float[size * size];
		// Randomly put on spots
		RandomDataGenerator rg = new RandomDataGenerator(rand);
		while (n-- > 0)
		{
			data[rand.nextInt(data.length)] = rg.nextInt(min, max);
		}

		// Blur
		FloatProcessor fp = new FloatProcessor(size, size, data);
		GaussianBlur gb = new GaussianBlur();
		gb.blurFloat(fp, sigmaX, sigmaY, 0.0002);

		return (float[]) fp.getPixels();
	}

	private static ImagePlus createImageData3D()
	{
		// Create an image with peaks
		int size = 64;
		int z = 5;
		int n = 20;
		float[][] data1 = createSpots3D(size, z, n, 5000, 10000, 2.5, 3.0);
		float[][] data2 = createSpots3D(size, z, n, 10000, 20000, 4.5, 3.5);
		float[][] data3 = createSpots3D(size, z, n, 20000, 40000, 6.5, 5);
		ImageStack stack = new ImageStack(size, size);
		for (int i = 0; i < data1.length; i++)
		{
			short[] data = combine(data1[i], data2[i], data3[i]);
			stack.addSlice(new ShortProcessor(size, size, data, null));
		}
		// Show
		String title = "FindFociTest3D";
		//gdsc.core.ij.Utils.display(title, stack);
		return new ImagePlus(title, stack);
	}

	private static float[][] createSpots3D(int size, int z, int n, int min, int max, double sigmaX, double sigmaY)
	{
		float[] data = new float[size * size];
		// Randomly put on spots
		RandomDataGenerator rg = new RandomDataGenerator(rand);
		while (n-- > 0)
		{
			data[rand.nextInt(data.length)] = rg.nextInt(min, max);
		}

		int middle = z / 2;
		float[][] result = new float[z][];
		for (int i = 0; i < z; i++)
		{
			FloatProcessor fp = new FloatProcessor(size, size, data.clone());
			GaussianBlur gb = new GaussianBlur();
			// Increase blur when out-of-focus
			double scale = createWidthScale(Math.abs(middle - i), middle);
			gb.blurFloat(fp, sigmaX * scale, sigmaY * scale, 0.0002);
			result[i] = (float[]) fp.getPixels();
		}
		return result;
	}

	/**
	 * Generate a scale so that at the configured zDepth the scale is 1.5.
	 * 
	 * @param z
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
