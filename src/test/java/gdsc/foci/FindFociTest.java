package gdsc.foci;

import java.util.ArrayList;

import org.apache.commons.math3.random.RandomDataGenerator;
import org.apache.commons.math3.random.RandomGenerator;
import org.apache.commons.math3.random.Well19937c;
import org.junit.Assert;
import org.junit.Test;

import gdsc.core.threshold.AutoThreshold;
import ij.ImagePlus;
import ij.plugin.filter.GaussianBlur;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;
import ij.process.ShortProcessor;

public class FindFociTest
{
	static int bias = 500;
	static RandomGenerator rand = new Well19937c(30051977);
	static ImagePlus[] data;
	static int numberOfTestImages = 2;

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
		for (ImagePlus imp : createData())
		{
			for (int i = 0; i < backgroundMethod.length; i++)
			{
				FindFociResults r1 = runLegacy(imp, i);
				FindFociResults r2 = runInt(imp, i, false);
				isEqual(r1, r2, i);
			}
		}
	}

	@Test
	public void isSameResultUsingOptimisedIntProcessor()
	{
		for (ImagePlus imp : createData())
		{
			for (int i = 0; i < backgroundMethod.length; i++)
			{
				FindFociResults r1 = runLegacy(imp, i);
				FindFociResults r2 = runInt(imp, i, true);
				isEqual(r1, r2, i);
			}
		}
	}

	@Test
	public void isSameResultUsingFloatProcessor()
	{
		for (ImagePlus imp : createData())
		{
			for (int i = 0; i < backgroundMethod.length; i++)
			{
				FindFociResults r1 = runLegacy(imp, i);
				FindFociResults r2 = runFloat(imp, i, false, false);
				isEqual(r1, r2, i);
			}
		}
	}

	@Test
	public void isSameResultUsingOptimisedFloatProcessor()
	{
		for (ImagePlus imp : createData())
		{
			for (int i = 0; i < backgroundMethod.length; i++)
			{
				FindFociResults r1 = runLegacy(imp, i);
				FindFociResults r2 = runFloat(imp, i, true, false);
				isEqual(r1, r2, i);
			}
		}
	}

	@Test
	public void isSameResultUsingFloatProcessorWithNegativeValues()
	{
		for (ImagePlus imp : createData())
		{
			for (int i = 0; i < backgroundMethod.length; i++)
			{
				if (FindFociBaseProcessor.isSortIndexSenstiveToNegativeValues(sortIndex[i]))
					continue;
				FindFociResults r1 = runLegacy(imp, i);
				FindFociResults r2 = runFloat(imp, i, false, true);
				isEqual(r1, r2, i, true);
			}
		}
	}

	@Test
	public void isSameResultUsingOptimisedFloatProcessorWithNegativeValues()
	{
		for (ImagePlus imp : createData())
		{
			for (int i = 0; i < backgroundMethod.length; i++)
			{
				if (FindFociBaseProcessor.isSortIndexSenstiveToNegativeValues(sortIndex[i]))
					continue;
				FindFociResults r1 = runLegacy(imp, i);
				FindFociResults r2 = runFloat(imp, i, true, true);
				isEqual(r1, r2, i, true);
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
		runInt(data[0], indices[0], false);
		runInt(data[0], indices[0], true);

		long time1 = System.nanoTime();
		for (ImagePlus imp : data)
		{
			for (int i : indices)
			{
				runInt(imp, i, false);
			}
		}
		time1 = System.nanoTime() - time1;
		long time2 = System.nanoTime();
		for (ImagePlus imp : data)
		{
			for (int i : indices)
			{
				runInt(imp, i, true);
			}
		}
		time2 = System.nanoTime() - time2;
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
		runInt(data[0], indices[0], false);
		runInt(data[0], indices[0], true);

		long time1 = System.nanoTime();
		for (ImagePlus imp : data)
		{
			for (int i : indices)
			{
				runFloat(imp, i, false, false);
			}
		}
		time1 = System.nanoTime() - time1;
		long time2 = System.nanoTime();
		for (ImagePlus imp : data)
		{
			for (int i : indices)
			{
				runFloat(imp, i, true, false);
			}
		}
		time2 = System.nanoTime() - time2;
		System.out.printf("Float %d, Opt Float %d, %fx faster\n", time1, time2, (double) time1 / time2);
		Assert.assertTrue(time2 < time1);
	}

	private void isEqual(FindFociResults r1, FindFociResults r2, int set)
	{
		isEqual(r1, r2, set, false);
	}

	private void isEqual(FindFociResults r1, FindFociResults r2, int set, boolean negativeValues)
	{
		ImagePlus imp1 = r1.mask;
		ImagePlus imp2 = r2.mask;
		Assert.assertEquals(set + " Mask", imp1 != null, imp2 != null);
		if (imp1 != null)
		{
			//			Assert.assertArrayEquals(set + " Mask values", (float[]) (imp1.getProcessor().convertToFloat().getPixels()),
			//					(float[]) (imp2.getProcessor().convertToFloat().getPixels()), 0);
		}
		ArrayList<FindFociResult> results1 = r1.results;
		ArrayList<FindFociResult> results2 = r2.results;
		//System.out.printf("N1=%d, N2=%d\n", results1.size(), results2.size());
		Assert.assertEquals(set + " Results Size", results1.size(), results2.size());
		for (int i = 0; i < results1.size(); i++)
		{
			//@formatter:off
			FindFociResult o1 = results1.get(i);
			FindFociResult o2 = results2.get(i);
			//System.out.printf("[%d] %d,%d %f (%d)\n", i, o2.RESULT_X, o2.RESULT_Y, o2.RESULT_MAX_VALUE, o2.RESULT_COUNT);
			Assert.assertEquals(set + " X: " +i, o1.RESULT_X, o2.RESULT_X);
			Assert.assertEquals(set + " Y: " +i, o1.RESULT_Y, o2.RESULT_Y);
			Assert.assertEquals(set + " Z: " +i, o1.RESULT_Z, o2.RESULT_Z);
			Assert.assertEquals(set + " ID: " +i, o1.RESULT_PEAK_ID, o2.RESULT_PEAK_ID);
			Assert.assertEquals(set + " Count: " +i, o1.RESULT_COUNT, o2.RESULT_COUNT);
			Assert.assertEquals(set + " Saddle ID: " +i, o1.RESULT_SADDLE_NEIGHBOUR_ID, o2.RESULT_SADDLE_NEIGHBOUR_ID);
			Assert.assertEquals(set + " Count >Saddle: " +i, o1.RESULT_COUNT_ABOVE_SADDLE, o2.RESULT_COUNT_ABOVE_SADDLE);
			if (negativeValues)
				continue;
			Assert.assertEquals(set + " Intensity: " +i, (int)o1.RESULT_INTENSITY, (int)o2.RESULT_INTENSITY);
			Assert.assertEquals(set + " Max: " +i, (int)o1.RESULT_MAX_VALUE, (int)o2.RESULT_MAX_VALUE);
			Assert.assertEquals(set + " Saddle value: " +i, (int)o1.RESULT_HIGHEST_SADDLE_VALUE, (int)o2.RESULT_HIGHEST_SADDLE_VALUE);
			Assert.assertEquals(set + " Av Intensity: " +i, (int)o1.RESULT_AVERAGE_INTENSITY, (int)o2.RESULT_AVERAGE_INTENSITY);
			Assert.assertEquals(set + " Intensity >background: " +i, (int)o1.RESULT_INTENSITY_MINUS_BACKGROUND, (int)o2.RESULT_INTENSITY_MINUS_BACKGROUND);
			Assert.assertEquals(set + " Av Intensity >background: " +i, (int)o1.RESULT_AVERAGE_INTENSITY_MINUS_BACKGROUND, (int)o2.RESULT_AVERAGE_INTENSITY_MINUS_BACKGROUND);
			Assert.assertEquals(set + " Intensity > Saddle: " +i, (int)o1.RESULT_INTENSITY_ABOVE_SADDLE, (int)o2.RESULT_INTENSITY_ABOVE_SADDLE);
			//@formatter:on
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
				r2.RESULT_AVERAGE_INTENSITY = r[FindFociLegacy.RESULT_AVERAGE_INTENSITY];
				r2.RESULT_X = r[FindFociLegacy.RESULT_X];
				r2.RESULT_Y = r[FindFociLegacy.RESULT_Y];
				r2.RESULT_Z = r[FindFociLegacy.RESULT_Z];
				r2.RESULT_PEAK_ID = r[FindFociLegacy.RESULT_PEAK_ID];
				r2.RESULT_COUNT = r[FindFociLegacy.RESULT_COUNT];
				r2.RESULT_INTENSITY = r[FindFociLegacy.RESULT_INTENSITY];
				r2.RESULT_MAX_VALUE = r[FindFociLegacy.RESULT_MAX_VALUE];
				r2.RESULT_HIGHEST_SADDLE_VALUE = r[FindFociLegacy.RESULT_HIGHEST_SADDLE_VALUE];
				r2.RESULT_SADDLE_NEIGHBOUR_ID = r[FindFociLegacy.RESULT_SADDLE_NEIGHBOUR_ID];
				r2.RESULT_AVERAGE_INTENSITY = r[FindFociLegacy.RESULT_AVERAGE_INTENSITY];
				r2.RESULT_INTENSITY_MINUS_BACKGROUND = r[FindFociLegacy.RESULT_INTENSITY_MINUS_BACKGROUND];
				r2.RESULT_AVERAGE_INTENSITY_MINUS_BACKGROUND = r[FindFociLegacy.RESULT_AVERAGE_INTENSITY_MINUS_BACKGROUND];
				r2.RESULT_COUNT_ABOVE_SADDLE = r[FindFociLegacy.RESULT_COUNT_ABOVE_SADDLE];
				r2.RESULT_INTENSITY_ABOVE_SADDLE = r[FindFociLegacy.RESULT_INTENSITY_ABOVE_SADDLE];
				results.add(r2);
			}
		}
		return new FindFociResults((ImagePlus) result[0], results, null);
	}

	private FindFociResults runInt(ImagePlus imp, int i, boolean optimised)
	{
		FindFoci ff = new FindFoci();
		ff.setOptimisedProcessor(optimised);
		return ff.findMaxima(imp, null, backgroundMethod[i], backgroundParameter[i], autoThresholdMethod[i],
				searchMethod[i], searchParameter[i], maxPeaks[i], minSize[i], peakMethod[i], peakParameter[i],
				outputType[i], sortIndex[i], options[i], blur[i], centreMethod[i], centreParameter[i],
				fractionParameter[i]);
	}

	private FindFociResults runFloat(ImagePlus imp, int i, boolean optimised, boolean negative)
	{
		FloatProcessor fp = (FloatProcessor) imp.getProcessor().convertToFloat();
		if (negative)
		{
			fp.subtract(bias + 100);
		}
		FindFoci ff = new FindFoci();
		ff.setOptimisedProcessor(optimised);
		return ff.findMaxima(new ImagePlus(null, fp), null, backgroundMethod[i], backgroundParameter[i],
				autoThresholdMethod[i], searchMethod[i], searchParameter[i], maxPeaks[i], minSize[i], peakMethod[i],
				peakParameter[i], outputType[i], sortIndex[i], options[i], blur[i], centreMethod[i], centreParameter[i],
				fractionParameter[i]);
	}

	private static ImagePlus[] createData()
	{
		if (data == null)
		{
			System.out.println("Creating data ...");
			data = new ImagePlus[numberOfTestImages];
			for (int i = 0; i < data.length; i++)
				data[i] = createImageData();
			System.out.println("Created data");
		}
		return data;
	}

	private static ImagePlus createImageData()
	{
		// Create an image with peaks
		int size = 512;
		int n = 80;
		float[] data1 = createSpots(size, n, 10000, 2.5, 3.0);
		float[] data2 = createSpots(size, n, 10000, 4.5, 3.5);
		float[] data3 = createSpots(size, n, 10000, 6.5, 5);
		// Combine images and add a bias and read noise
		RandomDataGenerator rg = new RandomDataGenerator(rand);
		short[] data = new short[data1.length];
		for (int i = 0; i < data.length; i++)
		{
			final double mu = data1[i] + data2[i] + data3[i];
			data[i] = (short) (((mu != 0) ? rg.nextPoisson(mu) : 0) + rg.nextGaussian(bias, 5));
		}
		// Show
		String title = "FindFociTest";
		ImageProcessor ip = new ShortProcessor(size, size, data, null);
		//gdsc.core.ij.Utils.display(title, ip);
		return new ImagePlus(title, ip);
	}

	private static float[] createSpots(int size, int n, float value, double sigmaX, double sigmaY)
	{
		float[] data = new float[size * size];
		// Randomly put on spots
		RandomDataGenerator rg = new RandomDataGenerator(rand);
		while (n-- > 0)
		{
			data[rand.nextInt(data.length)] = rg.nextPoisson(value);
		}

		// Blur
		FloatProcessor fp = new FloatProcessor(size, size, data);
		GaussianBlur gb = new GaussianBlur();
		gb.blurFloat(fp, sigmaX, sigmaY, 0.0002);

		return (float[]) fp.getPixels();
	}
}
