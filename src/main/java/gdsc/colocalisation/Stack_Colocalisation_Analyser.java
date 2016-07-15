package gdsc.colocalisation;

/*----------------------------------------------------------------------------- 
 * GDSC Plugins for ImageJ
 * 
 * Copyright (C) 2011 Alex Herbert
 * Genome Damage and Stability Centre
 * University of Sussex, UK
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *---------------------------------------------------------------------------*/

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;

import gdsc.UsageTracker;
import gdsc.colocalisation.cda.TwinStackShifter;
import gdsc.core.utils.Correlator;
import gdsc.core.utils.Random;
import gdsc.threshold.Auto_Threshold;
import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.gui.GenericDialog;
import ij.plugin.filter.PlugInFilter;
import ij.process.ByteProcessor;
import ij.process.ImageProcessor;
import ij.text.TextWindow;

/**
 * Processes a stack image with multiple channels. Requires three channels. Each frame is processed separately. Extracts
 * all the channels (collating z-stacks) and performs:
 * (1). Thresholding to create a mask for each channel
 * (2). CDA analysis of channel 1 vs channel 2 within the region defined by channel 3.
 */
public class Stack_Colocalisation_Analyser implements PlugInFilter
{
	// Store a reference to the current working image
	private ImagePlus imp;

	private static TextWindow tw;
	private static String TITLE = "Stack Colocalisation Analyser";
	private static String NONE = "None";
	private boolean firstResult;

	// ImageJ indexes for the dimensions array
	// private final int X = 0;
	// private final int Y = 1;
	private final int C = 2;
	private final int Z = 3;
	private final int T = 4;

	private static String methodOption = "Otsu";
	private static int channel1 = 1;
	private static int channel2 = 2;
	private static int channel3 = 0;

	// Options flags
	private static boolean logThresholds = false;
	private static boolean logResults = false;
	private static boolean showMask = false;
	private static boolean subtractThreshold = false;

	private static int permutations = 100;
	private static int minimumRadius = 9;
	private static int maximumRadius = 16;
	private static double pCut = 0.05;

	private Correlator c;

	/*
	 * (non-Javadoc)
	 * 
	 * @see ij.plugin.filter.PlugInFilter#setup(java.lang.String, ij.ImagePlus)
	 */
	public int setup(String arg, ImagePlus imp)
	{
		UsageTracker.recordPlugin(this.getClass(), arg);
		
		if (imp == null)
		{
			return DONE;
		}

		int[] dimensions = imp.getDimensions();

		if (dimensions[2] < 2 || imp.getStackSize() < 2 || (imp.getBitDepth() != 8 && imp.getBitDepth() != 16))
		{
			if (IJ.isMacro())
				IJ.log("Multi-channel stack required (8-bit or 16-bit): " + imp.getTitle());
			else
				IJ.showMessage("Multi-channel stack required (8-bit or 16-bit)");
			return DONE;
		}

		this.imp = imp;

		return DOES_16 + DOES_8G + NO_CHANGES;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see ij.plugin.filter.PlugInFilter#run(ij.process.ImageProcessor)
	 */
	public void run(ImageProcessor inputProcessor)
	{
		int[] dimensions = imp.getDimensions();
		int currentSlice = imp.getCurrentSlice();

		String[] methods = getMethods();
		// channel3 is set within getMethods()
		int nChannels = (channel3 > 0) ? 3 : 2;

		c = new Correlator(dimensions[0] * dimensions[1]);

		for (String method : methods)
		{
			if (logThresholds || logResults)
			{
				IJ.log("Stack colocalisation (" + method + ") : " + imp.getTitle());
			}

			ImageStack maskStack = null;
			ImagePlus maskImage = null;
			if (showMask)
			{
				// The stack will only have 3 channels
				maskStack = new ImageStack(imp.getWidth(), imp.getHeight(), nChannels * dimensions[Z] * dimensions[T]);

				// Ensure empty layers are filled to avoid ImageJ error creating ImagePlus
				byte[] empty = new byte[maskStack.getWidth() * maskStack.getHeight()];
				Arrays.fill(empty, (byte) 255);
				maskStack.setPixels(empty, 1);

				maskImage = new ImagePlus(imp.getTitle() + ":" + method, maskStack);
				maskImage.setDimensions(nChannels, dimensions[Z], dimensions[T]);
			}

			for (int t = 1; t <= dimensions[T]; t++)
			{
				ArrayList<SliceCollection> sliceCollections = new ArrayList<SliceCollection>();

				// Extract the channels
				for (int c = 1; c <= dimensions[C]; c++)
				{
					// Process all slices together
					SliceCollection sliceCollection = new SliceCollection(c);
					for (int z = 1; z <= dimensions[Z]; z++)
					{
						sliceCollection.add(imp.getStackIndex(c, z, t));
					}
					sliceCollections.add(sliceCollection);
				}

				// Get the channels:
				SliceCollection s1 = sliceCollections.get(channel1 - 1);
				SliceCollection s2 = sliceCollections.get(channel2 - 1);

				// Create masks
				extractImageAndCreateOutputMask(method, maskImage, 1, t, s1);
				extractImageAndCreateOutputMask(method, maskImage, 2, t, s2);

				// Note that channel 3 is offset by 1 because it contains the [none] option
				SliceCollection s3;
				if (channel3 > 0)
				{
					s3 = sliceCollections.get(channel3 - 1);
					extractImageAndCreateOutputMask(method, maskImage, 3, t, s3);
				}
				else
				{
					s3 = new SliceCollection(channel3);
				}

				double[] results = correlate(s1, s2, s3);

				reportResult(method, t, s1.getSliceName(), s2.getSliceName(), s3.getSliceName(), results);
			}

			if (showMask)
			{
				maskImage.show();
				IJ.run("Stack to Hyperstack...", "order=xyczt(default) channels=" + nChannels + " slices=" +
						dimensions[Z] + " frames=" + dimensions[T] + " display=Color");
			}
		}
		imp.setSlice(currentSlice);
	}

	private void extractImageAndCreateOutputMask(String method, ImagePlus maskImage, int c, int t,
			SliceCollection sliceCollection)
	{
		sliceCollection.createStack(imp);
		sliceCollection.createMask(method);
		if (logThresholds)
			IJ.log("t" + t + sliceCollection.getSliceName() + " threshold = " + sliceCollection.threshold);

		if (showMask)
		{
			ImageStack maskStack = maskImage.getImageStack();
			for (int s = 1; s <= sliceCollection.maskStack.getSize(); s++)
			{
				int originalSliceNumber = sliceCollection.slices.get(s - 1);
				int newSliceNumber = maskImage.getStackIndex(c, s, t);
				maskStack.setSliceLabel(method + ":" + imp.getStack().getSliceLabel(originalSliceNumber),
						newSliceNumber);
				maskStack.setPixels(sliceCollection.maskStack.getPixels(s), newSliceNumber);
			}
		}
	}

	private String[] getMethods()
	{
		GenericDialog gd = new GenericDialog(TITLE);
		gd.addMessage(TITLE);
		firstResult = true;

		String[] indices = new String[imp.getNChannels()];
		for (int i = 1; i <= indices.length; i++)
		{
			indices[i - 1] = "" + i;
		}

		gd.addChoice("Channel_1", indices, indices[channel1 - 1]);
		gd.addChoice("Channel_2", indices, indices[channel2 - 1]);
		indices = addNoneOption(indices);
		gd.addChoice("Channel_3", indices, indices[channel3]);

		// Commented out the methods that take a long time on 16-bit images.
		String[] methods = { "Try all", "Default",
				// "Huang",
				// "Intermodes",
				// "IsoData",
				"Li", "MaxEntropy", "Mean", "MinError(I)",
				// "Minimum",
				"Moments", "Otsu", "Percentile", "RenyiEntropy",
				// "Shanbhag",
				"Triangle", "Yen", NONE };

		gd.addChoice("Method", methods, methodOption);
		gd.addCheckbox("Log_thresholds", logThresholds);
		gd.addCheckbox("Log_results", logResults);
		gd.addCheckbox("Show_mask", showMask);
		gd.addCheckbox("Subtract threshold", subtractThreshold);
		gd.addNumericField("Permutations", permutations, 0);
		gd.addNumericField("Minimum_shift", minimumRadius, 0);
		gd.addNumericField("Maximum_shift", maximumRadius, 0);
		gd.addNumericField("Significance", pCut, 3);
		gd.addHelp(gdsc.help.URL.COLOCALISATION);

		gd.showDialog();
		if (gd.wasCanceled())
			return new String[0];

		channel1 = gd.getNextChoiceIndex() + 1;
		channel2 = gd.getNextChoiceIndex() + 1;
		channel3 = gd.getNextChoiceIndex();
		methodOption = gd.getNextChoice();
		logThresholds = gd.getNextBoolean();
		logResults = gd.getNextBoolean();
		showMask = gd.getNextBoolean();
		subtractThreshold = gd.getNextBoolean();
		permutations = (int) gd.getNextNumber();
		minimumRadius = (int) gd.getNextNumber();
		maximumRadius = (int) gd.getNextNumber();
		pCut = gd.getNextNumber();

		// Check parameters
		if (minimumRadius > maximumRadius)
			return failed("Minimum radius cannot be above maximum radius");
		if (maximumRadius > 255)
			return failed("Maximum radius cannot be above 255");
		if (pCut < 0 || pCut > 1)
			return failed("Significance must be between 0-1");

		if (!methodOption.equals("Try all"))
		{
			// Ensure that the string contains known methods (to avoid passing bad macro arguments)
			methods = extractMethods(methodOption.split(" "), methods);
		}
		else
		{
			// Shift the array to remove the try all option
			String[] newMethods = new String[methods.length - 1];
			for (int i = 0; i < newMethods.length; i++)
				newMethods[i] = methods[i + 1];
			methods = newMethods;
		}

		if (methods.length == 0)
			return failed("No valid thresholding method(s) specified");

		return methods;
	}

	private String[] addNoneOption(String[] indices)
	{
		String[] newIndices = new String[indices.length + 1];
		newIndices[0] = NONE;
		for (int i = 0; i < indices.length; i++)
		{
			newIndices[i + 1] = indices[i];
		}
		return newIndices;
	}

	private String[] failed(String message)
	{
		IJ.error(TITLE, message);
		return new String[0];
	}

	/**
	 * Filtered the set of options using the allowed methods array.
	 * 
	 * @param options
	 * @param allowedMethods
	 * @return filtered options
	 */
	private String[] extractMethods(String[] options, String[] allowedMethods)
	{
		ArrayList<String> methods = new ArrayList<String>();
		for (String option : options)
		{
			for (String allowedMethod : allowedMethods)
			{
				if (option.equals(allowedMethod))
				{
					methods.add(option);
					break;
				}
			}
		}
		return methods.toArray(new String[0]);
	}

	/**
	 * Create the intersect of the two masks
	 * 
	 * @param maskStack
	 * @param maskStack2
	 * @return the new mask
	 */
	private ImageStack intersectMask(ImageStack maskStack, ImageStack maskStack2)
	{
		ImageStack newStack = new ImageStack(maskStack.getWidth(), maskStack.getHeight());
		for (int s = 1; s <= maskStack.getSize(); s++)
		{
			newStack.addSlice(
					null,
					intersectMask((ByteProcessor) maskStack.getProcessor(s), (ByteProcessor) maskStack2.getProcessor(s)));
		}
		return newStack;
	}

	/**
	 * Create the intersect of the two masks
	 * 
	 * @param mask1
	 * @param mask2
	 * @return the new mask
	 */
	private ByteProcessor intersectMask(ByteProcessor mask1, ByteProcessor mask2)
	{
		ByteProcessor bp = new ByteProcessor(mask1.getWidth(), mask1.getHeight());
		byte on = (byte) 255;
		byte[] m1 = (byte[]) mask1.getPixels();
		byte[] m2 = (byte[]) mask2.getPixels();
		byte[] b = (byte[]) bp.getPixels();
		for (int i = m1.length; i-- > 0;)
		{
			if (m1[i] == on && m2[i] == on)
			{
				b[i] = on;
			}
		}
		return bp;
	}

	/**
	 * Calculate the Mander's coefficients and Pearson correlation coefficient (R) between the two input channels within
	 * the intersect of
	 * their masks.
	 * 
	 * @param s1
	 * @param s2
	 * @param s3
	 * @return an array containing: M1, M2, R, the number of overlapping pixels; the % total area for the overlap;
	 * 
	 */
	private double[] correlate(SliceCollection s1, SliceCollection s2, SliceCollection s3)
	{
		double m1Significant = 0, m2Significant = 0, rSignificant = 0;

		// Calculate the total intensity within the channels, only counting regions in the channel mask and the ROI
		double totalIntensity1 = getTotalIntensity(s1.imageStack, s1.maskStack, s3.maskStack);
		double totalIntensity2 = getTotalIntensity(s2.imageStack, s2.maskStack, s3.maskStack);

		// Get the standard result
		CalculationResult result = correlate(s1.imageStack, s1.maskStack, s2.imageStack, s2.maskStack, s3.maskStack, 0,
				totalIntensity1, totalIntensity2);

		if (permutations > 0)
		{
			// Circularly permute the s2 stack and compute the M1,M2,R stats.
			// Perform permutations within given distance limits, random n trials from all combinations.

			// Count the number of permutations
			int maximumRadius2 = maximumRadius * maximumRadius;
			int minimumRadius2 = minimumRadius * minimumRadius;
			int totalPermutations = 0;
			for (int i = -maximumRadius; i <= maximumRadius; ++i)
			{
				for (int j = -maximumRadius; j <= maximumRadius; ++j)
				{
					int distance2 = i * i + j * j;
					if (distance2 > maximumRadius2 || distance2 < minimumRadius2)
						continue;
					totalPermutations++;
				}
			}

			// There should always be total permutations since minimumRadius <= maximumRadius

			// Randomise the permutations
			int[] indices = new int[totalPermutations];
			totalPermutations = 0;
			for (int i = -maximumRadius; i <= maximumRadius; ++i)
			{
				for (int j = -maximumRadius; j <= maximumRadius; ++j)
				{
					int distance2 = i * i + j * j;
					if (distance2 > maximumRadius2 || distance2 < minimumRadius2)
						continue;
					// Pack the magnitude of the shift into the first 4 bytes and then pack the signs
					int index = (Math.abs(i) & 0xff) << 8 | Math.abs(j) & 0xff;
					if (i < 0)
						index |= 0x00010000;
					if (j < 0)
						index |= 0x00020000;

					indices[totalPermutations++] = index;
				}
			}

			// Fisher-Yates shuffle
			Random rand = new Random(30051977);
			rand.shuffle(indices);

			TwinStackShifter stackShifter = new TwinStackShifter(s2.imageStack, s2.maskStack, s3.maskStack);

			// Process only the specified number of permutations
			int n = (permutations < totalPermutations) ? permutations : totalPermutations;
			ArrayList<CalculationResult> results = new ArrayList<CalculationResult>(n);

			while (n-- > 0)
			{
				int index = indices[n];
				int j = index & 0xff;
				int i = (index & 0xff00) >> 8;
				if ((index & 0x00010000) == 0x00010000)
					i = -i;
				if ((index & 0x00020000) == 0x00020000)
					j = -j;

				double distance = Math.sqrt(i * i + j * j);

				stackShifter.setShift(i, j);
				stackShifter.run();

				results.add(correlate(s1.imageStack, s1.maskStack, stackShifter.getResultImage().getStack(),
						stackShifter.getResultImage2().getStack(), s3.maskStack, distance, totalIntensity1,
						totalIntensity2));
			}

			// Output if significant at given confidence level. Avoid bounds errors.
			int index = (int) Math.min(results.size() - 1, Math.ceil(results.size() * (1 - pCut)));

			Collections.sort(results, new M1Comparator());
			m1Significant = result.m1 - results.get(index).m1;
			Collections.sort(results, new M2Comparator());
			m2Significant = result.m2 - results.get(index).m2;
			Collections.sort(results, new RComparator());
			rSignificant = result.r - results.get(index).r;
		}

		return new double[] { result.m1, result.m2, result.r, result.n, result.area, m1Significant, m2Significant,
				rSignificant };
	}

	private double getTotalIntensity(ImageStack imageStack, ImageStack maskStack, ImageStack maskStack2)
	{
		byte on = (byte) 255;
		double total = 0;
		for (int s = 1; s <= imageStack.getSize(); s++)
		{
			ImageProcessor ip1 = imageStack.getProcessor(s);
			ImageProcessor ip2 = maskStack.getProcessor(s);
			byte[] mask = (byte[]) ip2.getPixels();

			if (maskStack2 != null)
			{
				ImageProcessor ip3 = maskStack2.getProcessor(s);
				byte[] mask2 = (byte[]) ip3.getPixels();

				for (int i = mask.length; i-- > 0;)
				{
					if (mask[i] == on && mask2[i] == on)
					{
						total += ip1.get(i);
					}
				}
			}
			else
			{
				for (int i = mask.length; i-- > 0;)
				{
					if (mask[i] == on)
					{
						total += ip1.get(i);
					}
				}
			}
		}
		return total;
	}

	/**
	 * Calculate the Mander's coefficients and Pearson correlation coefficient (R) between the two input channels within
	 * the intersect of their masks. Only use the pixels within the roi mask.
	 */
	private CalculationResult correlate(ImageStack image1, ImageStack mask1, ImageStack image2, ImageStack mask2,
			ImageStack roi, double distance, double totalIntensity1, double totalIntensity2)
	{
		ImageStack overlapStack = intersectMask(mask1, mask2);

		final byte on = (byte) 255;

		int nTotal = 0;

		c.clear();

		for (int s = 1; s <= overlapStack.getSize(); s++)
		{
			ImageProcessor ip1 = image1.getProcessor(s);
			ImageProcessor ip2 = image2.getProcessor(s);

			ByteProcessor overlap = (ByteProcessor) overlapStack.getProcessor(s);
			byte[] b = (byte[]) overlap.getPixels();

			if (roi != null)
			{
				// Calculate correlation within a specified region
				ImageProcessor ip3 = roi.getProcessor(s);
				byte[] mask = (byte[]) ip3.getPixels();

				for (int i = mask.length; i-- > 0;)
				{
					if (mask[i] == on)
					{
						nTotal++;

						if (b[i] == on)
							c.add(ip1.get(i), ip2.get(i));
					}
				}
			}
			else
			{
				// Calculate correlation for entire image
				for (int i = ip1.getPixelCount(); i-- > 0;)
				{
					nTotal++;

					if (b[i] == on)
						c.add(ip1.get(i), ip2.get(i));
				}
			}
		}

		final double m1 = c.getSumX() / totalIntensity1;
		final double m2 = c.getSumY() / totalIntensity2;
		final double r = c.getCorrelation();

		return new CalculationResult(distance, m1, m2, r, c.getN(), (100.0 * c.getN() / nTotal));
	}

	/**
	 * Reports the results for the correlation to the IJ log window
	 * 
	 * @param t
	 *            The timeframe
	 * @param c1
	 *            Channel 1 title
	 * @param c2
	 *            Channel 2 title
	 * @param c3
	 *            Channel 3 title
	 * @param results
	 *            The correlation results
	 */
	private void reportResult(String method, int t, String c1, String c2, String c3, double[] results)
	{
		createResultsWindow();

		double m1 = results[0];
		double m2 = results[1];
		double r = results[2];
		int n = (int) results[3];
		double area = results[4];
		boolean m1Significant = results[5] > 0;
		boolean m2Significant = results[6] > 0;
		boolean rSignificant = results[7] > 0;

		char spacer = (logResults) ? ',' : '\t';

		StringBuffer sb = new StringBuffer();
		sb.append(imp.getTitle()).append(spacer);
		sb.append(IJ.d2s(pCut, 4)).append(spacer);
		sb.append(method).append(spacer);
		sb.append(t).append(spacer);
		sb.append(c1).append(spacer);
		sb.append(c2).append(spacer);
		sb.append(c3).append(spacer);
		sb.append(n).append(spacer);
		sb.append(IJ.d2s(area, 2)).append("%").append(spacer);
		sb.append(IJ.d2s(m1, 4)).append(spacer);
		sb.append(m1Significant).append(spacer);
		sb.append(IJ.d2s(m2, 4)).append(spacer);
		sb.append(m2Significant).append(spacer);
		sb.append(IJ.d2s(r, 4)).append(spacer);
		sb.append(rSignificant);

		if (logResults)
		{
			IJ.log(sb.toString());
		}
		else
		{
			tw.append(sb.toString());
		}
	}

	private void createResultsWindow()
	{
		if (logResults)
		{
			if (firstResult)
			{
				firstResult = false;
				IJ.log("Image,p,Method,Frame,Ch1,Ch2,Ch3,n,Area,M1,Sig,M2,Sig,R,Sig");
			}
		}
		else if (tw == null || !tw.isShowing())
		{
			tw = new TextWindow(TITLE + " Results",
					"Image\tp\tMethod\tFrame\tCh1\tCh2\tCh3\tn\tArea\tM1\tSig\tM2\tSig\tR\tSig", "", 700, 300);
		}
	}

	/**
	 * Provides functionality to process a collection of slices from an Image
	 */
	public class SliceCollection
	{
		public int c;
		public int z;
		public ArrayList<Integer> slices;

		private String sliceName = null;

		public ImageStack imageStack = null;
		public ImageStack maskStack = null;
		public int threshold = 0;

		/**
		 * @param c
		 *            The channel
		 * @param z
		 *            The z dimension
		 */
		SliceCollection(int c, int z)
		{
			this.c = c;
			this.z = z;
			slices = new ArrayList<Integer>(1);
		}

		/**
		 * @param c
		 *            The channel
		 */
		SliceCollection(int c)
		{
			this.c = c;
			this.z = 0;
			slices = new ArrayList<Integer>();
		}

		/**
		 * Utility method
		 * 
		 * @param i
		 */
		public void add(Integer i)
		{
			slices.add(i);
		}

		public String getSliceName()
		{
			if (sliceName == null || sliceName == NONE)
			{
				if (slices.isEmpty())
				{
					sliceName = NONE;
				}
				else
				{
					StringBuffer sb = new StringBuffer();
					sb.append("c").append(c);
					if (z != 0)
					{
						sb.append("z").append(z);
					}
					sliceName = sb.toString();
				}
			}
			return sliceName;
		}

		/**
		 * Extracts the configured slices from the image into a stack
		 * 
		 * @param imp
		 */
		public void createStack(ImagePlus imp)
		{
			if (slices.isEmpty())
				return;

			imageStack = new ImageStack(imp.getWidth(), imp.getHeight());
			for (int slice : slices)
			{
				imp.setSliceWithoutUpdate(slice);
				imageStack.addSlice(Integer.toString(slice), imp.getProcessor().duplicate());
			}
		}

		/**
		 * Creates a mask using the specified thresholding method
		 * 
		 * @param ip
		 * @param method
		 * @return the mask
		 */
		private void createMask(String method)
		{
			if (slices.isEmpty())
				return;

			final boolean mySubtractThreshold;
			if (method != NONE)
			{
				// Create an aggregate histogram
				int[] data = imageStack.getProcessor(1).getHistogram();
				int[] temp = new int[data.length];
				for (int s = 2; s <= imageStack.getSize(); s++)
				{
					temp = imageStack.getProcessor(s).getHistogram();
					for (int i = 0; i < data.length; i++)
					{
						data[i] += temp[i];
					}
				}

				threshold = Auto_Threshold.getThreshold(method, data);
				mySubtractThreshold = subtractThreshold && (threshold > 0);
			}
			else
				mySubtractThreshold = false;

			// Create a mask for each image in the stack
			maskStack = new ImageStack(imageStack.getWidth(), imageStack.getHeight());
			for (int s = 1; s <= imageStack.getSize(); s++)
			{
				ByteProcessor bp = new ByteProcessor(imageStack.getWidth(), imageStack.getHeight());
				ImageProcessor ip = imageStack.getProcessor(s);
				for (int i = bp.getPixelCount(); i-- > 0;)
				{
					final int value = ip.get(i);
					if (value > threshold)
					{
						bp.set(i, 255);
					}
					if (mySubtractThreshold)
					{
						ip.set(i, value - threshold);
					}
				}
				maskStack.addSlice(null, bp);
			}
		}
	}

	/**
	 * Used to store the calculation results of the intersection of two images.
	 */
	public class CalculationResult
	{
		public double distance; // Shift distance
		public double m1; // Mander's 1
		public double m2; // Mander's 2
		public double r; // Correlation
		public int n; // the number of overlapping pixels; 
		public double area; // the % total area for the overlap;

		public CalculationResult(double distance, double m1, double m2, double r, int n, double area)
		{
			this.distance = distance;
			this.m1 = m1;
			this.m2 = m2;
			this.r = r;
			this.n = n;
			this.area = area;
		}
	}

	/**
	 * Compare the results using the Mander's 1 coefficient
	 */
	private class M1Comparator implements Comparator<CalculationResult>
	{
		public int compare(CalculationResult o1, CalculationResult o2)
		{
			if (o1.m1 < o2.m1)
				return -1;
			if (o1.m1 > o2.m1)
				return 1;
			return 0;
		}
	}

	/**
	 * Compare the results using the Mander's 2 coefficient
	 */
	private class M2Comparator implements Comparator<CalculationResult>
	{
		public int compare(CalculationResult o1, CalculationResult o2)
		{
			if (o1.m2 < o2.m2)
				return -1;
			if (o1.m2 > o2.m2)
				return 1;
			return 0;
		}
	}

	/**
	 * Compare the results using the correlation coefficient
	 */
	private class RComparator implements Comparator<CalculationResult>
	{
		public int compare(CalculationResult o1, CalculationResult o2)
		{
			if (o1.r < o2.r)
				return -1;
			if (o1.r > o2.r)
				return 1;
			return 0;
		}
	}
}
