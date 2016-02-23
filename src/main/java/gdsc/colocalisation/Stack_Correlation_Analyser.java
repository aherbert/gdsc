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

import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.gui.GenericDialog;
import ij.plugin.filter.PlugInFilter;
import ij.process.ByteProcessor;
import ij.process.ImageProcessor;

import java.util.ArrayList;

import gdsc.ImageJTracker;
import gdsc.threshold.Auto_Threshold;

/**
 * Processes a stack image with multiple channels. Each frame and z-slice are processed together. Extracts all the
 * channels and performs:
 * (1) Thresholding to create a mask for each channel
 * (2) All-vs-all channel correlation within the union/intersect of the channel masks
 * 
 * Can optionally aggregate the channel z-stack before processing.
 */
public class Stack_Correlation_Analyser implements PlugInFilter
{
	// Store a reference to the current working image
	private ImagePlus imp;

	private final String TITLE = "Stack Correlation Analyser";

	// ImageJ indexes for the dimensions array
	// private final int X = 0;
	// private final int Y = 1;
	private final int C = 2;
	private final int Z = 3;
	private final int T = 4;

	// Options
	private static String methodOption = "Otsu";
	private static boolean useIntersect = true;
	private static boolean aggregateZstack = true;
	private static boolean logThresholds = false;
	private static boolean showMask = false;
	private static boolean subtractThreshold = false;

	private Correlator c;

	/*
	 * (non-Javadoc)
	 * 
	 * @see ij.plugin.filter.PlugInFilter#setup(java.lang.String, ij.ImagePlus)
	 */
	public int setup(String arg, ImagePlus imp)
	{
		ImageJTracker.recordPlugin(TITLE, arg);
		
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
		c = new Correlator(dimensions[0] * dimensions[1]);
		for (String method : getMethods())
		{
			IJ.log("Stack correlation (" + method + ") : " + imp.getTitle());
			ImageStack maskStack = null;
			if (showMask)
			{
				maskStack = new ImageStack(imp.getWidth(), imp.getHeight(), imp.getStackSize());
			}

			for (int t = 1; t <= dimensions[T]; t++)
			{
				ArrayList<SliceCollection> sliceCollections = new ArrayList<SliceCollection>();

				if (aggregateZstack)
				{
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
				}
				else
				{
					// Process each slice independently
					for (int z = 1; z <= dimensions[Z]; z++)
					{
						// Extract the channels
						for (int c = 1; c <= dimensions[C]; c++)
						{
							SliceCollection sliceCollection = new SliceCollection(c, z);
							sliceCollection.add(imp.getStackIndex(c, z, t));
							sliceCollections.add(sliceCollection);
						}
					}
				}

				// Create masks
				for (SliceCollection sliceCollection : sliceCollections)
				{
					sliceCollection.createStack(imp);
					sliceCollection.createMask(method);
					if (logThresholds)
						IJ.log("t" + t + sliceCollection.getSliceName() + " threshold = " + sliceCollection.threshold);

					if (showMask)
					{
						for (int s = 1; s <= sliceCollection.maskStack.getSize(); s++)
						{
							int originalSliceNumber = sliceCollection.slices.get(s - 1);
							maskStack.setSliceLabel(method + ":" + imp.getStack().getSliceLabel(originalSliceNumber),
									originalSliceNumber);
							maskStack.setPixels(sliceCollection.maskStack.getPixels(s), originalSliceNumber);
						}
					}
				}

				// Process the collections:
				for (int i = 0; i < sliceCollections.size(); i++)
				{
					SliceCollection s1 = sliceCollections.get(i);
					for (int j = i + 1; j < sliceCollections.size(); j++)
					{
						SliceCollection s2 = sliceCollections.get(j);
						if (s1.z != s2.z)
							continue;

						double[] results = correlate(s1, s2);

						reportResult(t, s1.getSliceName(), s2.getSliceName(), results);
					}
				}
			}

			if (showMask)
			{
				ImagePlus imp2 = new ImagePlus(imp.getTitle() + ":" + method, maskStack);
				imp2.setDimensions(imp.getNChannels(), imp.getNSlices(), imp.getNFrames());
				if (imp.isDisplayedHyperStack())
				{
					imp2.setOpenAsHyperStack(true);
				}
				imp2.show();
			}
		}
		imp.setSlice(currentSlice);
	}

	private String[] getMethods()
	{
		GenericDialog gd = new GenericDialog(TITLE);
		gd.addMessage(TITLE);
		// Commented out the methods that take a long time on 16-bit images.
		String[] methods = { "Try all", "Default",
				// "Huang",
				// "Intermodes",
				// "IsoData",
				"Li", "MaxEntropy", "Mean", "MinError(I)",
				// "Minimum",
				"Moments", "None", "Otsu", "Percentile", "RenyiEntropy",
				// "Shanbhag",
				"Triangle", "Yen" };

		gd.addChoice("Method", methods, methodOption);
		gd.addMessage("Correlation uses union/intersect of the masks");
		gd.addCheckbox("Intersect", useIntersect);
		gd.addCheckbox("Aggregate Z-stack", aggregateZstack);
		gd.addCheckbox("Log thresholds", logThresholds);
		gd.addCheckbox("Show mask", showMask);
		gd.addCheckbox("Subtract threshold", subtractThreshold);
		gd.addHelp(gdsc.help.URL.COLOCALISATION);

		gd.showDialog();
		if (gd.wasCanceled())
			return new String[0];

		methodOption = gd.getNextChoice();
		useIntersect = gd.getNextBoolean();
		aggregateZstack = gd.getNextBoolean();
		logThresholds = gd.getNextBoolean();
		showMask = gd.getNextBoolean();
		subtractThreshold = gd.getNextBoolean();

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

		if (methods == null || methods.length == 0)
		{
			IJ.error(TITLE, "No valid thresholding method(s) specified");
		}

		return methods;
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
	 * Create the union of the two masks
	 * 
	 * @param maskStack
	 * @param maskStack2
	 * @return the new mask
	 */
	private ImageStack unionMask(ImageStack maskStack, ImageStack maskStack2)
	{
		ImageStack newStack = new ImageStack(maskStack.getWidth(), maskStack.getHeight());
		for (int s = 1; s <= maskStack.getSize(); s++)
		{
			newStack.addSlice(null,
					unionMask((ByteProcessor) maskStack.getProcessor(s), (ByteProcessor) maskStack2.getProcessor(s)));
		}
		return newStack;
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
	 * Create the union of the two masks
	 * 
	 * @param mask1
	 * @param mask2
	 * @return the new mask
	 */
	private ByteProcessor unionMask(ByteProcessor mask1, ByteProcessor mask2)
	{
		ByteProcessor bp = new ByteProcessor(mask1.getWidth(), mask1.getHeight());
		byte on = (byte) 255;
		byte[] m1 = (byte[]) mask1.getPixels();
		byte[] m2 = (byte[]) mask2.getPixels();
		byte[] b = (byte[]) bp.getPixels();
		for (int i = m1.length; i-- > 0;)
		{
			if (m1[i] == on || m2[i] == on)
			{
				b[i] = on;
			}
		}
		return bp;
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
	 * Calculate the Pearson correlation coefficient (R) between the two input channels within the union/intersect of
	 * their masks.
	 * 
	 * @param s1
	 * @param s2
	 * @return an array containing: the number of overlapping pixels; the % total area for the overlap; R; M1; M2
	 */
	private double[] correlate(SliceCollection s1, SliceCollection s2)
	{
		ImageStack overlapStack = (useIntersect) ? intersectMask(s1.maskStack, s2.maskStack) : unionMask(s1.maskStack,
				s2.maskStack);

		final byte on = (byte) 255;

		int nTotal = 0;

		c.clear();

		for (int s = 1; s <= overlapStack.getSize(); s++)
		{
			ImageProcessor ip1 = s1.imageStack.getProcessor(s);
			ImageProcessor ip2 = s2.imageStack.getProcessor(s);
			ByteProcessor overlap = (ByteProcessor) overlapStack.getProcessor(s);

			final byte[] b = (byte[]) overlap.getPixels();
			nTotal += b.length;
			for (int i = b.length; i-- > 0;)
			{
				if (b[i] == on)
				{
					c.add(ip1.get(i), ip2.get(i));
				}
			}
		}

		// We can calculate the Mander's coefficient if we are using the intersect
		double m1 = 0, m2 = 0;
		if (useIntersect)
		{
			long sum1 = c.getSumX();
			long sum2 = c.getSumY();
			long sum1A = s1.sum;
			long sum2A = s2.sum;
			
			if (subtractThreshold)
			{
				sum1 -= c.getN() * s1.threshold;
				sum1A -= s1.count * s1.threshold;
				sum2 -= c.getN() * s2.threshold;
				sum2A -= s2.count * s2.threshold;
			}
			
			m1 = (double) sum1 / sum1A;
			m2 = (double) sum2 / sum2A;
		}

		return new double[] { c.getN(), (100.0 * c.getN() / nTotal), c.getCorrelation(), m1, m2 };
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
	 * @param results
	 *            The correlation results
	 */
	private void reportResult(int t, String c1, String c2, double[] results)
	{
		int n = (int) results[0];
		double area = results[1];
		double r = results[2];
		StringBuffer sb = new StringBuffer();
		sb.append("t").append(t).append(",");
		sb.append(c1).append(",");
		sb.append(c2).append(",");
		sb.append(n).append(",");
		sb.append(IJ.d2s(area, 2)).append("%,");
		sb.append(IJ.d2s(r, 4));
		if (useIntersect)
		{
			// Mander's coefficients 
			sb.append(",").append(IJ.d2s(results[3], 4));
			sb.append(",").append(IJ.d2s(results[4], 4));
		}
		IJ.log(sb.toString());
	}

	/**
	 * Provides functionality to process a collection of slices from an Image
	 */
	public class SliceCollection
	{
		public int c;
		public int z;
		public ArrayList<Integer> slices;
		public long sum;
		public int count;

		private String sliceName = null;

		public ImageStack imageStack;
		public ImageStack maskStack;
		public int threshold;

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
			if (sliceName == null)
			{
				StringBuffer sb = new StringBuffer();
				sb.append("c").append(c);
				if (z != 0)
				{
					sb.append("z").append(z);
				}
				sliceName = sb.toString();
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

			// Create a mask for each image in the stack
			sum = 0;
			count = 0;
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
						sum += value;
						count++;
						bp.set(i, 255);
					}
				}
				maskStack.addSlice(null, bp);
			}
		}
	}
}
