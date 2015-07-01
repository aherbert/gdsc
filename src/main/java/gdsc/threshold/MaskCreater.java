package gdsc.threshold;

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

import gdsc.utils.ImageJHelper;
import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.WindowManager;
import ij.gui.GenericDialog;
import ij.gui.Roi;
import ij.plugin.PlugIn;
import ij.process.ByteProcessor;
import ij.process.ImageProcessor;
import ij.process.LUT;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Arrays;

/**
 * Create a mask from an image
 */
public class MaskCreater implements PlugIn
{
	public static String[] options = new String[] { "Use as mask", "Min Display Value", "Use ROI", "Threshold" };
	public static int OPTION_MASK = 0;
	public static int OPTION_MIN_VALUE = 1;
	public static int OPTION_USE_ROI = 2;
	public static int OPTION_THRESHOLD = 3;

	public static String[] methods;
	static
	{
		// Add options for multi-level Otsu threshold
		ArrayList<String> m = new ArrayList<String>();
		m.addAll(Arrays.asList(Auto_Threshold.methods));
		m.add("Otsu_3_level");
		m.add("Otsu_4_level");
		methods = m.toArray(new String[0]);
	}

	private static String selectedImage = "";
	private static int selectedOption = OPTION_THRESHOLD;
	private static String selectedThresholdMethod = "Otsu";
	private static int selectedChannel = 0;
	private static int selectedSlice = 0;
	private static int selectedFrame = 0;
	private static boolean selectedRemoveEdgeParticles = false;
	private static int selectedMinParticleSize = 0;

	private ImagePlus imp;
	private int option;
	private String thresholdMethod;
	private int channel = 0;
	private int slice = 0;
	private int frame = 0;
	private boolean removeEdgeParticles = false;
	private int minParticleSize = 0;

	/*
	 * (non-Javadoc)
	 * 
	 * @see ij.plugin.PlugIn#run(java.lang.String)
	 */
	public void run(String arg)
	{
		if (!showDialog())
		{
			return;
		}
		ImagePlus imp = createMask();
		if (imp != null)
		{
			imp.show();
		}
	}

	private boolean showDialog()
	{
		ArrayList<String> imageList = new ArrayList<String>();

		for (int id : ImageJHelper.getIDList())
		{
			ImagePlus imp = WindowManager.getImage(id);
			if (imp != null)
			{
				imageList.add(imp.getTitle());
			}
		}

		if (imageList.isEmpty())
		{
			IJ.noImage();
			return false;
		}

		GenericDialog gd = new GenericDialog("Mask Creator");
		gd.addMessage("Create a new mask image");
		gd.addChoice("Image", imageList.toArray(new String[0]), selectedImage);
		gd.addChoice("Option", options, options[selectedOption]);
		gd.addChoice("Threshold_Method", methods, selectedThresholdMethod);
		gd.addNumericField("Channel", selectedChannel, 0);
		gd.addNumericField("Slice", selectedSlice, 0);
		gd.addNumericField("Frame", selectedFrame, 0);
		gd.addCheckbox("Remove_edge_particles", selectedRemoveEdgeParticles);
		gd.addNumericField("Min_particle_size", selectedMinParticleSize, 0);
		gd.addHelp(gdsc.help.URL.UTILITY);
		gd.showDialog();

		if (gd.wasCanceled())
			return false;

		selectedImage = gd.getNextChoice();
		selectedOption = gd.getNextChoiceIndex();
		selectedThresholdMethod = gd.getNextChoice();
		selectedChannel = (int) gd.getNextNumber();
		selectedSlice = (int) gd.getNextNumber();
		selectedFrame = (int) gd.getNextNumber();
		selectedRemoveEdgeParticles = gd.getNextBoolean();
		selectedMinParticleSize = (int) gd.getNextNumber();

		setImp(WindowManager.getImage(selectedImage));
		setOption(selectedOption);
		setThresholdMethod(selectedThresholdMethod);
		setChannel(selectedChannel);
		setSlice(selectedSlice);
		setFrame(selectedFrame);
		setRemoveEdgeParticles(selectedRemoveEdgeParticles);
		setMinParticleSize(selectedMinParticleSize);

		return true;
	}

	public MaskCreater()
	{
		init(null, OPTION_MASK);
	}

	public MaskCreater(ImagePlus imp)
	{
		init(imp, OPTION_MASK);
	}

	public MaskCreater(ImagePlus imp, int option)
	{
		init(imp, option);
	}

	private void init(ImagePlus imp, int option)
	{
		this.imp = imp;
		this.option = option;
	}

	/**
	 * Create a mask using the configured source image.
	 * 
	 * @return The mask image
	 */
	public ImagePlus createMask()
	{
		ImagePlus maskImp = null;

		if (imp == null)
			return maskImp;

		ByteProcessor bp;

		ImageStack inputStack = imp.getImageStack();
		ImageStack result = null;
		int[] dimensions = imp.getDimensions();
		int[] channels = createArray(dimensions[2], channel);
		int[] slices = createArray(dimensions[3], slice);
		int[] frames = createArray(dimensions[4], frame);

		double[] thresholds = null;
		if (option == OPTION_THRESHOLD)
		{
			thresholds = getThresholds(imp, channels, slices, frames);
		}

		if (option == OPTION_MIN_VALUE || option == OPTION_MASK || option == OPTION_THRESHOLD)
		{
			// Use the ROI image to create a mask either using:
			// - non-zero pixels (i.e. a mask)
			// - all pixels above the minimum display value
			result = new ImageStack(imp.getWidth(), imp.getHeight());

			for (int frame : frames)
				for (int slice : slices)
					for (int channel : channels)
					{
						int stackIndex = imp.getStackIndex(channel, slice, frame);
						ImageProcessor roiIp = inputStack.getProcessor(stackIndex);

						bp = new ByteProcessor(imp.getWidth(), imp.getHeight());
						if (option == OPTION_MASK)
						{
							for (int i = roiIp.getPixelCount(); i-- > 0;)
							{
								if (roiIp.getf(i) != 0)
								{
									bp.set(i, 255);
								}
							}
						}
						else
						{
							final double min;
							if (option == OPTION_MIN_VALUE)
							{
								min = getDisplayRangeMin(imp, channel);
							}
							else
							// if (option == OPTION_THRESHOLD)
							{
								min = thresholds[stackIndex - 1];
							}

							for (int i = roiIp.getPixelCount(); i-- > 0;)
							{
								if (roiIp.getf(i) >= min)
								{
									bp.set(i, 255);
								}
							}
						}

						postProcess(bp);

						result.addSlice(null, bp);
					}
		}
		else if (option == OPTION_USE_ROI)
		{
			// Use the ROI from the ROI image
			Roi roi = imp.getRoi();

			Rectangle bounds;
			if (roi != null)
				bounds = roi.getBounds();
			else
				// If no ROI then use the entire image
				bounds = new Rectangle(imp.getWidth(), imp.getHeight());

			// Use a mask for an irregular ROI
			ImageProcessor ipMask = imp.getMask();

			// Create a mask from the ROI rectangle
			int xOffset = bounds.x;
			int yOffset = bounds.y;
			int rwidth = bounds.width;
			int rheight = bounds.height;

			result = new ImageStack(imp.getWidth(), imp.getHeight());

			bp = new ByteProcessor(imp.getWidth(), imp.getHeight());
			for (int y = 0; y < rheight; y++)
			{
				for (int x = 0; x < rwidth; x++)
				{
					if (ipMask == null || ipMask.get(x, y) != 0)
					{
						bp.set(x + xOffset, y + yOffset, 255);
					}
				}
			}

			postProcess(bp);

			for (int frame = frames.length; frame-- > 0;)
				for (int slice = slices.length; slice-- > 0;)
					for (int channel = channels.length; channel-- > 0;)
					{
						result.addSlice(null, bp.duplicate());
					}
		}

		if (result != null)
		{
			maskImp = new ImagePlus(imp.getShortTitle() + " Mask", result);
			if (imp.isDisplayedHyperStack())
			{
				int nChannels = channels.length;
				int nSlices = slices.length;
				int nFrames = frames.length;
				if (nChannels * nSlices * nFrames > 1)
				{
					maskImp.setDimensions(nChannels, nSlices, nFrames);
					maskImp.setOpenAsHyperStack(true);
				}
			}
		}

		return maskImp;
	}

	private int getDisplayRangeMin(ImagePlus imp, int channel)
	{
		// Composite images can have a display range for each color channel
		LUT[] luts = imp.getLuts();
		if (luts != null && channel <= luts.length)
			return (int) luts[channel - 1].min;
		return (int) imp.getDisplayRangeMin();
	}

	private double[] getThresholds(ImagePlus imp, int[] channels, int[] slices, int[] frames)
	{
		double[] thresholds = new double[imp.getStackSize()];
		ImageStack inputStack = imp.getImageStack();

		// 32-bit images have no histogram.
		// We convert to 16-bit using the min-max from each channel
		float[][] min = new float[channels.length][frames.length];
		float[][] max = new float[channels.length][frames.length];
		if (imp.getBitDepth() == 32)
		{
			// Convert the image to 16-bit

			// Find the min and max per channel
			for (int i = 0; i < channels.length; i++)
			{
				Arrays.fill(min[i], Float.POSITIVE_INFINITY);
				Arrays.fill(max[i], Float.NEGATIVE_INFINITY);
			}

			for (int i = 0; i < channels.length; i++)
			{
				for (int j = 0; j < frames.length; j++)
				{
					// Find the min and max per channel across the z-stack
					for (int k = 0; k < slices.length; k++)
					{
						int stackIndex = imp.getStackIndex(channels[i], slices[k], frames[j]);
						float[] data = (float[]) inputStack.getProcessor(stackIndex).getPixels();
						float cmin = data[0];
						float cmax = data[0];
						for (float f : data)
						{
							if (f < cmin)
								cmin = f;
							else if (f > cmax)
								cmax = f;
						}
						if (cmin < min[i][j])
							min[i][j] = cmin;
						if (cmax > max[i][j])
							max[i][j] = cmax;
						//IJ.log(String.format("Channel %d, Frame %d, Slice %d : min = %f, max = %f", channels[i],
						//		frames[j], slices[k], cmin, cmax));
					}
				}
			}

			// Convert
			//IJ.log("Converting 32-bit image to 16-bit for thresholding");
			ImageStack newStack = new ImageStack(imp.getWidth(), imp.getHeight(), imp.getStackSize());
			for (int i = 0; i < channels.length; i++)
			{
				for (int j = 0; j < frames.length; j++)
				{
					final float cmin = min[i][j];
					final float cmax = max[i][j];
					//IJ.log(String.format("Channel %d, Frame %d : min = %f, max = %f", channels[i], frames[j], cmin,
					//		cmax));
					for (int k = 0; k < slices.length; k++)
					{
						int stackIndex = imp.getStackIndex(channels[i], slices[k], frames[j]);
						newStack.setPixels(convertToShort(inputStack.getProcessor(stackIndex), cmin, cmax), stackIndex);
					}
				}
			}
			inputStack = newStack;
		}

		for (int i = 0; i < channels.length; i++)
		{
			for (int j = 0; j < frames.length; j++)
			{
				final float cmin = min[i][j];
				final float cmax = max[i][j];

				// Threshold the z-stack together
				int stackIndex = imp.getStackIndex(channels[i], slices[0], frames[j]);
				final int[] data = inputStack.getProcessor(stackIndex).getHistogram();
				for (int k = 1; k < slices.length; k++)
				{
					stackIndex = imp.getStackIndex(channels[i], slices[k], frames[j]);
					int[] tmp = inputStack.getProcessor(stackIndex).getHistogram();
					for (int ii = tmp.length; ii-- > 0;)
						data[ii] += tmp[ii];
				}
				double threshold = getThreshold(thresholdMethod, data);
				if (imp.getBitDepth() == 32)
				{
					// Convert the 16-bit threshold back to the original 32-bit range
					float scale = getScale(cmin, cmax);
					threshold = (threshold / scale) + cmin;
					//IJ.log(String.format("Channel %d, Frame %d : %f", channels[i], frames[j], threshold));
				}

				for (int k = 0; k < slices.length; k++)
				{
					stackIndex = imp.getStackIndex(channels[i], slices[k], frames[j]);
					thresholds[stackIndex - 1] = threshold;
				}
			}
		}

		return thresholds;
	}

	private int getThreshold(String autoThresholdMethod, int[] statsHistogram)
	{
		if (autoThresholdMethod.endsWith("evel"))
		{
			Multi_OtsuThreshold multi = new Multi_OtsuThreshold();
			multi.ignoreZero = false;
			int level = autoThresholdMethod.contains("_3_") ? 3 : 4;
			int[] threshold = multi.calculateThresholds(statsHistogram, level);
			return threshold[1];
		}
		return Auto_Threshold.getThreshold(autoThresholdMethod, statsHistogram);
	}

	private short[] convertToShort(ImageProcessor ip, float min, float max)
	{
		float[] pixels32 = (float[]) ip.getPixels();
		short[] pixels16 = new short[pixels32.length];
		float scale = getScale(min, max);
		for (int i = 0; i < pixels16.length; i++)
		{
			double value = (pixels32[i] - min) * scale;
			if (value < 0.0)
				value = 0.0;
			if (value > 65535.0)
				value = 65535.0;
			pixels16[i] = (short) (value + 0.5);
		}
		return pixels16;
	}

	private float getScale(float min, float max)
	{
		if ((max - min) == 0.0)
			return 1.0f;
		else
			return 65535.0f / (max - min);
	}

	private int[] createArray(int total, int selected)
	{
		if (selected > 0 && selected <= total)
		{
			return new int[] { selected };
		}
		int[] array = new int[total];
		for (int i = 0; i < array.length; i++)
			array[i] = i + 1;
		return array;
	}

	/**
	 * @param imp
	 *            the source image for the mask generation
	 */
	public void setImp(ImagePlus imp)
	{
		this.imp = imp;
	}

	/**
	 * @return the source image for the mask generation
	 */
	public ImagePlus getImp()
	{
		return imp;
	}

	/**
	 * @param option
	 *            the option for defining the mask
	 */
	public void setOption(int option)
	{
		this.option = option;
	}

	/**
	 * @return the option for defining the mask
	 */
	public int getOption()
	{
		return option;
	}

	/**
	 * @param thresholdMethod
	 *            the thresholdMethod to set
	 */
	public void setThresholdMethod(String thresholdMethod)
	{
		this.thresholdMethod = thresholdMethod;
	}

	/**
	 * @return the thresholdMethod
	 */
	public String getThresholdMethod()
	{
		return thresholdMethod;
	}

	/**
	 * @param channel
	 *            the channel to set
	 */
	public void setChannel(int channel)
	{
		this.channel = channel;
	}

	/**
	 * @return the channel
	 */
	public int getChannel()
	{
		return channel;
	}

	/**
	 * @param frame
	 *            the frame to set
	 */
	public void setFrame(int frame)
	{
		this.frame = frame;
	}

	/**
	 * @return the frame
	 */
	public int getFrame()
	{
		return frame;
	}

	/**
	 * @param slice
	 *            the slice to set
	 */
	public void setSlice(int slice)
	{
		this.slice = slice;
	}

	/**
	 * @return the slice
	 */
	public int getSlice()
	{
		return slice;
	}

	/**
	 * @return the removeEdgeParticles
	 */
	public boolean isRemoveEdgeParticles()
	{
		return removeEdgeParticles;
	}

	/**
	 * @param removeEdgeParticles
	 *            the removeEdgeParticles to set
	 */
	public void setRemoveEdgeParticles(boolean removeEdgeParticles)
	{
		this.removeEdgeParticles = removeEdgeParticles;
	}

	/**
	 * @return the minParticleSize
	 */
	public int getMinParticleSize()
	{
		return minParticleSize;
	}

	/**
	 * @param minParticleSize
	 *            the minParticleSize to set
	 */
	public void setMinParticleSize(int minParticleSize)
	{
		this.minParticleSize = minParticleSize;
	}

	private void postProcess(ByteProcessor bp)
	{
		if (!removeEdgeParticles && minParticleSize == 0)
			return;

		// Assign all particles
		int[] mask = findParticles(bp);

		boolean changed = false;

		if (removeEdgeParticles)
		{
			for (int x1 = 0, x2 = ylimit * maxx; x1 < bp.getWidth(); x1++, x2++)
			{
				if (mask[x1] != 0)
				{
					// Fill with zero all connected points
					zero(mask, x1);
					changed = true;
				}
				if (mask[x2] != 0)
				{
					// Fill with zero all connected points
					zero(mask, x2);
					changed = true;
				}
			}
			for (int y1 = 0, y2 = xlimit; y1 < mask.length; y1 += maxx, y2 += maxx)
			{
				if (mask[y1] != 0)
				{
					// Fill with zero all connected points
					zero(mask, y1);
					changed = true;
				}
				if (mask[y2] != 0)
				{
					// Fill with zero all connected points
					zero(mask, y2);
					changed = true;
				}
			}
		}

		if (minParticleSize > 0)
		{
			// Count particle size and store an index to the particle location
			int[] count = new int[particles + 1];
			int[] index = new int[particles + 1];
			for (int i = 0; i < mask.length; i++)
				if (mask[i] != 0)
				{
					count[mask[i]]++;
					index[mask[i]] = i;
				}

			// Remove small particles
			for (int i = 1; i < count.length; i++)
			{
				if (count[i] > 0 && count[i] < minParticleSize)
				{
					zero(mask, index[i]);
					changed = true;
				}
			}
		}

		// Copy the mask back
		if (changed)
		{
			byte[] originalMask = (byte[]) bp.getPixels();
			for (int i = 0; i < mask.length; i++)
			{
				if (mask[i] == 0)
					originalMask[i] = 0;
			}
			// Reset
			bp.setPixels(originalMask);
		}
	}

	// Used for a particle search
	private static final int[] DIR_X_OFFSET = new int[] { 0, 1, 1, 1, 0, -1, -1, -1 };
	private static final int[] DIR_Y_OFFSET = new int[] { -1, -1, 0, 1, 1, 1, 0, -1 };
	private int maxx = 0, maxy;
	private int xlimit, ylimit;
	private int[] offset = null;
	private int[] pList = null;
	private int particles;

	private void initialise(int maxx, int maxy)
	{
		// Check if already initialised for these dimensions
		if (maxx == this.maxx && maxy == this.maxy)
			return;

		this.maxx = maxx;
		this.maxy = maxy;
		pList = new int[maxx * maxy];

		xlimit = maxx - 1;
		ylimit = maxy - 1;

		// Create the offset table (for single array 2D neighbour comparisons)
		offset = new int[DIR_X_OFFSET.length];
		for (int d = offset.length; d-- > 0;)
		{
			offset[d] = maxx * DIR_Y_OFFSET[d] + DIR_X_OFFSET[d];
		}
	}

	/**
	 * Convert the mask to connected particles, each with a unique number.
	 */
	private int[] findParticles(ByteProcessor bp)
	{
		initialise(bp.getWidth(), bp.getHeight());

		byte[] originalMask = (byte[]) bp.getPixels();
		int[] mask = new int[originalMask.length];
		boolean[] binaryMask = new boolean[originalMask.length];

		// Store all the non-zero positions
		for (int i = 0; i < mask.length; i++)
			binaryMask[i] = (originalMask[i] != 0);

		// Find particles 
		particles = 0;
		for (int i = 0; i < binaryMask.length; i++)
		{
			if (binaryMask[i])
			{
				expandParticle(binaryMask, mask, i, ++particles);
			}
		}

		return mask;
	}

	/**
	 * Searches from the specified point to find all connected points and assigns them to given particle.
	 */
	private void expandParticle(boolean[] binaryMask, int[] mask, int index0, final int particle)
	{
		binaryMask[index0] = false; // mark as processed
		int listI = 0; // index of current search element in the list
		int listLen = 1; // number of elements in the list

		// we create a list of connected points and start the list at the particle start position
		pList[listI] = index0;

		do
		{
			final int index1 = pList[listI];
			// Mark this position as part of the particle
			mask[index1] = particle;

			// Search the 8-connected neighbours 
			final int x1 = index1 % maxx;
			final int y1 = index1 / maxx;

			final boolean isInnerXY = (x1 != 0 && x1 != xlimit) && (y1 != 0 && y1 != ylimit);

			if (isInnerXY)
			{
				for (int d = 8; d-- > 0;)
				{
					int index2 = index1 + offset[d];
					if (binaryMask[index2])
					{
						binaryMask[index2] = false; // mark as processed
						// Add this to the search
						pList[listLen++] = index2;
					}
				}
			}
			else
			{
				for (int d = 8; d-- > 0;)
				{
					if (isInside(x1, y1, d))
					{
						int index2 = index1 + offset[d];
						if (binaryMask[index2])
						{
							binaryMask[index2] = false; // mark as processed
							// Add this to the search
							pList[listLen++] = index2;
						}
					}
				}
			}

			listI++;

		} while (listI < listLen);
	}

	/**
	 * Returns whether the neighbour in a given direction is within the image. NOTE: it is assumed that the pixel x,y
	 * itself is within the image! Uses class variables xlimit, ylimit: (dimensions of the image)-1
	 * 
	 * @param x
	 *            x-coordinate of the pixel that has a neighbour in the given direction
	 * @param y
	 *            y-coordinate of the pixel that has a neighbour in the given direction
	 * @param direction
	 *            the direction from the pixel towards the neighbour
	 * @return true if the neighbour is within the image (provided that x, y is within)
	 */
	private boolean isInside(int x, int y, int direction)
	{
		switch (direction)
		{
			case 0:
				return (y > 0);
			case 1:
				return (y > 0 && x < xlimit);
			case 2:
				return (x < xlimit);
			case 3:
				return (y < ylimit && x < xlimit);
			case 4:
				return (y < ylimit);
			case 5:
				return (y < ylimit && x > 0);
			case 6:
				return (x > 0);
			case 7:
				return (y > 0 && x > 0);
		}
		return false;
	}

	/**
	 * Searches from the specified point to find all connected points with the same value and set them to zero
	 */
	private void zero(int[] mask, int index0)
	{
		int value = mask[index0];
		int listI = 0; // index of current search element in the list
		int listLen = 1; // number of elements in the list

		// we create a list of connected points and start the list at the particle start position
		pList[listI] = index0;

		do
		{
			final int index1 = pList[listI];
			// Zero this position
			mask[index1] = 0;

			// Search the 8-connected neighbours 
			final int x1 = index1 % maxx;
			final int y1 = index1 / maxx;

			final boolean isInnerXY = (x1 != 0 && x1 != xlimit) && (y1 != 0 && y1 != ylimit);

			if (isInnerXY)
			{
				for (int d = 8; d-- > 0;)
				{
					int index2 = index1 + offset[d];
					if (mask[index2] == value)
					{
						// Zero and add this to the search
						mask[index2] = 0;
						pList[listLen++] = index2;
					}
				}
			}
			else
			{
				for (int d = 8; d-- > 0;)
				{
					if (isInside(x1, y1, d))
					{
						int index2 = index1 + offset[d];
						if (mask[index2] == value)
						{
							// Zero and add this to the search
							mask[index2] = 0;
							pList[listLen++] = index2;
						}
					}
				}
			}

			listI++;

		} while (listI < listLen);
	}
}
