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
package gdsc.threshold;

import java.util.ArrayList;

import gdsc.UsageTracker;
import gdsc.core.threshold.AutoThreshold;
import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.gui.GenericDialog;
import ij.plugin.filter.PlugInFilter;
import ij.process.ByteProcessor;
import ij.process.ImageProcessor;

/**
 * Processes an image stack and applies thresholding to create a mask for each channel+frame combination.
 */
public class Stack_Threshold implements PlugInFilter
{
	// Store a reference to the current working image
	private ImagePlus imp;

	private static String TITLE = "Stack Threshold";

	// ImageJ indexes for the dimensions array
	// private final int X = 0;
	// private final int Y = 1;
	private final int C = 2;
	private final int Z = 3;
	private final int T = 4;

	private static String methodOption = AutoThreshold.Method.OTSU.name;

	// Options flags
	private static boolean logThresholds = false;
	private static boolean compositeColour = false;
	private static boolean newImage = true;

	/*
	 * (non-Javadoc)
	 *
	 * @see ij.plugin.filter.PlugInFilter#setup(java.lang.String, ij.ImagePlus)
	 */
	@Override
	public int setup(String arg, ImagePlus imp)
	{
		UsageTracker.recordPlugin(this.getClass(), arg);

		if (imp == null)
		{
			IJ.noImage();
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
	@Override
	public void run(ImageProcessor inputProcessor)
	{
		final int[] dimensions = imp.getDimensions();
		final int currentSlice = imp.getCurrentSlice();
		for (final String method : getMethods())
		{
			final ImageStack maskStack = new ImageStack(imp.getWidth(), imp.getHeight(), imp.getStackSize());

			// Process each frame
			for (int t = 1; t <= dimensions[T]; t++)
			{
				final ArrayList<SliceCollection> sliceCollections = new ArrayList<>();

				// Extract the channels
				for (int c = 1; c <= dimensions[C]; c++)
				{
					// Process all slices together
					final SliceCollection sliceCollection = new SliceCollection(c);
					for (int z = 1; z <= dimensions[Z]; z++)
						sliceCollection.add(imp.getStackIndex(c, z, t));
					sliceCollections.add(sliceCollection);
				}

				// Create masks
				for (final SliceCollection s : sliceCollections)
					createMask(method, maskStack, t, s);
			}

			if (newImage)
			{
				final ImagePlus newImg = new ImagePlus(imp.getTitle() + ":" + method, maskStack);
				newImg.setDimensions(dimensions[C], dimensions[Z], dimensions[T]);
				if (imp.getNDimensions() > 3)
					newImg.setOpenAsHyperStack(true);
				newImg.show();
				if (compositeColour)
					IJ.run("Make Composite", "display=Color");
			}
			else
			{
				imp.setStack(maskStack, imp.getNChannels(), imp.getNSlices(), imp.getNFrames());

				for (int slice = 1; slice <= imp.getStackSize(); slice++)
				{
					imp.setSliceWithoutUpdate(slice);
					imp.resetDisplayRange();
				}

				imp.updateAndRepaintWindow();
			}
		}
		imp.setSlice(currentSlice);
	}

	private void createMask(String method, ImageStack maskStack, int t, SliceCollection sliceCollection)
	{
		sliceCollection.createStack(imp);
		sliceCollection.createMask(method);
		if (logThresholds)
			IJ.log("t" + t + sliceCollection.getSliceName() + " threshold = " + sliceCollection.threshold);

		for (int s = 1; s <= sliceCollection.maskStack.getSize(); s++)
		{
			final int originalSliceNumber = sliceCollection.slices.get(s - 1);
			maskStack.setSliceLabel(method + ":" + imp.getStack().getSliceLabel(originalSliceNumber),
					originalSliceNumber);
			maskStack.setPixels(sliceCollection.maskStack.getPixels(s), originalSliceNumber);
		}
	}

	private String[] getMethods()
	{
		final GenericDialog gd = new GenericDialog(TITLE);
		gd.addMessage(TITLE);

		// Commented out the methods that take a long time on 16-bit images.
		String[] methods = { "Try all", AutoThreshold.Method.DEFAULT.name,
				// "Huang",
				// "Intermodes",
				// "IsoData",
				AutoThreshold.Method.LI.name, AutoThreshold.Method.MAX_ENTROPY.name, AutoThreshold.Method.MEAN.name,
				AutoThreshold.Method.MIN_ERROR_I.name,
				// "Minimum",
				AutoThreshold.Method.MOMENTS.name, AutoThreshold.Method.OTSU.name, AutoThreshold.Method.PERCENTILE.name,
				AutoThreshold.Method.RENYI_ENTROPY.name,
				// "Shanbhag",
				AutoThreshold.Method.TRIANGLE.name, AutoThreshold.Method.YEN.name };

		gd.addChoice("Method", methods, methodOption);
		gd.addCheckbox("Log_thresholds", logThresholds);
		if (imp.getDimensions()[C] > 1)
			gd.addCheckbox("Composite_colour", compositeColour);
		gd.addCheckbox("New_image", newImage);
		gd.addHelp(gdsc.help.URL.FIND_FOCI);

		gd.showDialog();
		if (gd.wasCanceled())
			return new String[0];

		methodOption = gd.getNextChoice();
		logThresholds = gd.getNextBoolean();
		if (imp.getDimensions()[C] > 1)
			compositeColour = gd.getNextBoolean();
		newImage = gd.getNextBoolean();

		if (!methodOption.equals("Try all"))
			// Ensure that the string contains known methods (to avoid passing bad macro arguments)
			methods = extractMethods(methodOption.split(" "), methods);
		else
		{
			// Shift the array to remove the try all option
			final String[] newMethods = new String[methods.length - 1];
			for (int i = 0; i < newMethods.length; i++)
				newMethods[i] = methods[i + 1];
			methods = newMethods;
		}

		if (methods.length == 0)
			return failed("No valid thresholding method(s) specified");

		// Cannot update original with more than one method
		if (methods.length > 1)
			newImage = true;

		return methods;
	}

	private static String[] failed(String message)
	{
		IJ.error(TITLE, message);
		return new String[0];
	}

	/**
	 * Filtered the set of options using the allowed methods array.
	 *
	 * @param options
	 *            the options
	 * @param allowedMethods
	 *            the allowed methods
	 * @return filtered options
	 */
	private static String[] extractMethods(String[] options, String[] allowedMethods)
	{
		final ArrayList<String> methods = new ArrayList<>();
		for (final String option : options)
			for (final String allowedMethod : allowedMethods)
				if (option.equals(allowedMethod))
				{
					methods.add(option);
					break;
				}
		return methods.toArray(new String[0]);
	}

	/**
	 * Provides functionality to process a collection of slices from an Image
	 */
	class SliceCollection
	{
		int c;
		int z;
		ArrayList<Integer> slices;

		private String sliceName = null;

		ImageStack imageStack;
		ImageStack maskStack;
		int threshold;

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
			slices = new ArrayList<>(1);
		}

		/**
		 * @param c
		 *            The channel
		 */
		SliceCollection(int c)
		{
			this.c = c;
			this.z = 0;
			slices = new ArrayList<>();
		}

		/**
		 * Utility method.
		 *
		 * @param i
		 *            the i
		 */
		void add(Integer i)
		{
			slices.add(i);
		}

		/**
		 * Gets the slice name.
		 *
		 * @return the slice name
		 */
		String getSliceName()
		{
			if (sliceName == null)
			{
				final StringBuilder sb = new StringBuilder();
				sb.append("c").append(c);
				if (z != 0)
					sb.append("z").append(z);
				sliceName = sb.toString();
			}
			return sliceName;
		}

		/**
		 * Extracts the configured slices from the image into a stack.
		 *
		 * @param imp
		 *            the imp
		 */
		void createStack(ImagePlus imp)
		{
			imageStack = new ImageStack(imp.getWidth(), imp.getHeight());
			for (final int slice : slices)
			{
				imp.setSliceWithoutUpdate(slice);
				imageStack.addSlice(Integer.toString(slice), imp.getProcessor().duplicate());
			}
		}

		/**
		 * Creates a mask using the specified thresholding method.
		 *
		 * @param method
		 *            the method
		 */
		private void createMask(String method)
		{
			// Create an aggregate histogram
			final int[] data = imageStack.getProcessor(1).getHistogram();
			int[] temp = new int[data.length];
			for (int s = 2; s <= imageStack.getSize(); s++)
			{
				temp = imageStack.getProcessor(s).getHistogram();
				for (int i = 0; i < data.length; i++)
					data[i] += temp[i];
			}

			threshold = AutoThreshold.getThreshold(method, data);

			// Create a mask for each image in the stack
			maskStack = new ImageStack(imageStack.getWidth(), imageStack.getHeight());
			for (int s = 1; s <= imageStack.getSize(); s++)
			{
				final ByteProcessor bp = new ByteProcessor(imageStack.getWidth(), imageStack.getHeight());
				final ImageProcessor ip = imageStack.getProcessor(s);
				for (int i = bp.getPixelCount(); i-- > 0;)
					if (ip.get(i) > threshold)
						bp.set(i, 255);
				maskStack.addSlice(null, bp);
			}
		}
	}
}
