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

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Arrays;

import gdsc.UsageTracker;
import gdsc.core.ij.Utils;
import gdsc.core.threshold.AutoThreshold;
import gdsc.core.utils.SimpleArrayUtils;
import gdsc.foci.ObjectAnalyzer;
import gdsc.foci.ObjectAnalyzer3D;
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
import ij.process.ShortProcessor;

/**
 * Create a mask from an image.
 */
public class MaskCreater implements PlugIn
{
	private static final String TITLE = "Mask Creator";

	/** The options string. */
	public static String[] options = new String[] { "Use as mask", "Min Display Value", "Use ROI", "Threshold" };

	/** The option for using a mask. */
	public static int OPTION_MASK = 0;

	/** The option for using the min display value. */
	public static int OPTION_MIN_VALUE = 1;

	/** The option for using the ROI. */
	public static int OPTION_USE_ROI = 2;

	/** The option to perform thresholding. */
	public static int OPTION_THRESHOLD = 3;

	/** The methods. */
	public static String[] methods;
	static
	{
		// Add options for multi-level Otsu threshold
		final ArrayList<String> m = new ArrayList<>();
		m.addAll(Arrays.asList(AutoThreshold.getMethods(true)));
		m.add("Otsu_3_level");
		m.add("Otsu_4_level");
		methods = m.toArray(new String[0]);
	}

	private static String selectedImage = "";
	private static int selectedOption = OPTION_THRESHOLD;
	private static String selectedThresholdMethod = AutoThreshold.Method.OTSU.name;
	private static int selectedChannel = 0;
	private static int selectedSlice = 0;
	private static int selectedFrame = 0;
	private static boolean selectedRemoveEdgeParticles = false;
	private static int selectedMinParticleSize = 0;
	private static boolean selectedStackHistogram = true;
	private static boolean selectedAssignObjects = false;
	private static boolean selectedEightConnected = false;

	private ImagePlus imp;
	private int option;
	private String thresholdMethod;
	private int channel = 0;
	private int slice = 0;
	private int frame = 0;
	private boolean removeEdgeParticles = false;
	private int minParticleSize = 0;
	private boolean stackHistogram = true;
	private boolean assignObjects = false;
	private boolean eightConnected = false;

	/*
	 * (non-Javadoc)
	 *
	 * @see ij.plugin.PlugIn#run(java.lang.String)
	 */
	@Override
	public void run(String arg)
	{
		UsageTracker.recordPlugin(this.getClass(), arg);

		if (!showDialog())
			return;
		final ImagePlus imp = createMask();
		if (imp != null)
			imp.show();
	}

	private boolean showDialog()
	{
		final ArrayList<String> imageList = new ArrayList<>();

		for (final int id : Utils.getIDList())
		{
			final ImagePlus imp = WindowManager.getImage(id);
			if (imp != null)
				imageList.add(imp.getTitle());
		}

		if (imageList.isEmpty())
		{
			IJ.noImage();
			return false;
		}

		final GenericDialog gd = new GenericDialog(TITLE);
		gd.addMessage("Create a new mask image");
		gd.addChoice("Image", imageList.toArray(new String[0]), selectedImage);
		gd.addChoice("Option", options, options[selectedOption]);
		gd.addChoice("Threshold_Method", methods, selectedThresholdMethod);
		gd.addNumericField("Channel", selectedChannel, 0);
		gd.addNumericField("Slice", selectedSlice, 0);
		gd.addNumericField("Frame", selectedFrame, 0);
		gd.addCheckbox("Remove_edge_particles", selectedRemoveEdgeParticles);
		gd.addNumericField("Min_particle_size", selectedMinParticleSize, 0);
		gd.addCheckbox("Stack_histogram", selectedStackHistogram);
		gd.addCheckbox("Assign_objects", selectedAssignObjects);
		gd.addCheckbox("Eight_connected", selectedEightConnected);
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
		selectedStackHistogram = gd.getNextBoolean();
		selectedAssignObjects = gd.getNextBoolean();
		selectedEightConnected = gd.getNextBoolean();

		setImp(WindowManager.getImage(selectedImage));
		setOption(selectedOption);
		setThresholdMethod(selectedThresholdMethod);
		setChannel(selectedChannel);
		setSlice(selectedSlice);
		setFrame(selectedFrame);
		setRemoveEdgeParticles(selectedRemoveEdgeParticles);
		setMinParticleSize(selectedMinParticleSize);
		setStackHistogram(selectedStackHistogram);
		setAssignObjects(selectedAssignObjects);
		setEightConnected(selectedEightConnected);

		return true;
	}

	/**
	 * Instantiates a new mask creater.
	 */
	public MaskCreater()
	{
		init(null, OPTION_MASK);
	}

	/**
	 * Instantiates a new mask creater.
	 *
	 * @param imp
*            the image
	 */
	public MaskCreater(ImagePlus imp)
	{
		init(imp, OPTION_MASK);
	}

	/**
	 * Instantiates a new mask creater.
	 *
	 * @param imp
*            the image
	 * @param option
	 *            the option
	 */
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

		ImageProcessor ip;

		final ImageStack inputStack = imp.getImageStack();
		ImageStack result = null;
		final int[] dimensions = imp.getDimensions();
		final int[] channels = createArray(dimensions[2], channel);
		final int[] slices = createArray(dimensions[3], slice);
		final int[] frames = createArray(dimensions[4], frame);

		final int nChannels = channels.length;
		final int nSlices = slices.length;
		final int nFrames = frames.length;

		double[] thresholds = null;
		if (option == OPTION_THRESHOLD)
			thresholds = getThresholds(imp, channels, slices, frames);

		// Find the global min and max XY values and create a bounding rectangle
		final int w = imp.getWidth();
		final int h = imp.getHeight();
		int minx = w, maxx = 0;
		int miny = h, maxy = 0;
		int max = 0;

		if (option == OPTION_MIN_VALUE || option == OPTION_MASK || option == OPTION_THRESHOLD)
		{
			// Use the ROI image to create a mask either using:
			// - non-zero pixels (i.e. a mask)
			// - all pixels above the minimum display value
			result = new ImageStack(imp.getWidth(), imp.getHeight(), nChannels * nSlices * nFrames);

			for (int f = 0; f < frames.length; f++)
			{
				final int frame = frames[f];
				for (int c = 0; c < channels.length; c++)
				{
					final int channel = channels[c];

					ImageStack channelStack = new ImageStack(imp.getWidth(), imp.getHeight());

					for (final int slice : slices)
					{
						final int stackIndex = imp.getStackIndex(channel, slice, frame);
						final ImageProcessor roiIp = inputStack.getProcessor(stackIndex);

						ip = new ByteProcessor(w, h);
						if (option == OPTION_MASK)
						{
							for (int i = roiIp.getPixelCount(); i-- > 0;)
								if (roiIp.getf(i) != 0)
									ip.set(i, 255);
						}
						else
						{
							final double min;
							if (option == OPTION_MIN_VALUE)
							{
								min = getDisplayRangeMin(imp, channel);
								for (int i = roiIp.getPixelCount(); i-- > 0;)
									// Only display pixels equal to or above the
									// min display threshold
									if (roiIp.getf(i) >= min)
										ip.set(i, 255);
							}
							else
							// if (option == OPTION_THRESHOLD)
							{
								min = thresholds[stackIndex - 1];
								for (int i = roiIp.getPixelCount(); i-- > 0;)
									// When thresholding it is typical to only
									// display pixels above the threshold. The IJ
									// plugins compute the threshold for integer histograms
									// then call ImageProcessor.setThreshold(t+1, [upper limit]).
									if (roiIp.getf(i) > min)
										ip.set(i, 255);
							}
						}

						channelStack.addSlice(null, ip);
					}

					// Post process the entire z-stack to find objects in 3D
					channelStack = postProcess(channelStack);

					for (int slice = 1; slice <= channelStack.getSize(); slice++)
					{
						ip = channelStack.getProcessor(slice);
						// Find bounds and max value
						for (int y = 0, i = 0; y < h; y++)
							for (int x = 0; x < w; x++, i++)
							{
								final int value = ip.get(i);
								if (value != 0)
								{
									if (max < value)
										max = value;
									if (minx > x)
										minx = x;
									else if (maxx < x)
										maxx = x;
									if (miny > y)
										miny = y;
									else if (maxy < y)
										maxy = y;
								}
							}

						// Adapted from getStackIndex() to use zero-indexed frame (f) and channel (c)
						final int index = (f) * nChannels * nSlices + (slice - 1) * nChannels + c + 1;
						result.setPixels(ip.getPixels(), index);
					}

				} // End channel
			} // End frame

			if (max <= 255)
			{
				final ImageStack result2 = new ImageStack(imp.getWidth(), imp.getHeight(), result.getSize());
				for (int slice = 1; slice <= result.getSize(); slice++)
					result2.setProcessor(result.getProcessor(slice).convertToByte(false), slice);
				result = result2;
			}
		}
		else if (option == OPTION_USE_ROI)
		{
			// Use the ROI from the ROI image
			final Roi roi = imp.getRoi();

			Rectangle bounds;
			if (roi != null)
				bounds = roi.getBounds();
			else
				// If no ROI then use the entire image
				bounds = new Rectangle(w, h);

			// Use a mask for an irregular ROI
			final ImageProcessor ipMask = imp.getMask();

			// Create a mask from the ROI rectangle
			final int xOffset = bounds.x;
			final int yOffset = bounds.y;
			final int rwidth = bounds.width;
			final int rheight = bounds.height;

			result = new ImageStack(w, h);

			ip = new ByteProcessor(w, h);
			for (int y = 0; y < rheight; y++)
				for (int x = 0; x < rwidth; x++)
					if (ipMask == null || ipMask.get(x, y) != 0)
						ip.set(x + xOffset, y + yOffset, 255);

			ip = postProcess(ip);

			// Find bounds
			for (int y = 0, i = 0; y < h; y++)
				for (int x = 0; x < w; x++, i++)
				{
					final int value = ip.get(i);
					if (value != 0)
					{
						if (max < value)
							max = value;
						if (minx > x)
							minx = x;
						else if (maxx < x)
							maxx = x;
						if (miny > y)
							miny = y;
						else if (maxy < y)
							maxy = y;
					}
				}

			if (max <= 255)
				ip = ip.convertToByte(false);

			for (int frame = frames.length; frame-- > 0;)
				for (int slice = slices.length; slice-- > 0;)
					for (int channel = channels.length; channel-- > 0;)
						result.addSlice(null, ip.duplicate());
		}

		if (result == null)
			return null;

		maskImp = new ImagePlus(imp.getShortTitle() + " Mask", result);
		if (imp.isDisplayedHyperStack())
			if (nChannels * nSlices * nFrames > 1)
			{
				maskImp.setDimensions(nChannels, nSlices, nFrames);
				maskImp.setOpenAsHyperStack(true);
			}

		// Add a bounding rectangle
		if (minx < w && miny < h)
		{
			// Due to the if/else maxx/maxy may not be initialised if we only have single pixels
			if (maxx < minx)
				maxx = minx;
			if (maxy < miny)
				maxy = miny;
			maskImp.setRoi(new Rectangle(minx, miny, maxx - minx + 1, maxy - miny + 1));
		}

		maskImp.setDisplayRange(0, max);
		return maskImp;
	}

	private static int getDisplayRangeMin(ImagePlus imp, int channel)
	{
		// Composite images can have a display range for each color channel
		final LUT[] luts = imp.getLuts();
		if (luts != null && channel <= luts.length)
			return (int) luts[channel - 1].min;
		return (int) imp.getDisplayRangeMin();
	}

	private double[] getThresholds(ImagePlus imp, int[] channels, int[] slices, int[] frames)
	{
		final double[] thresholds = new double[imp.getStackSize()];
		ImageStack inputStack = imp.getImageStack();

		// 32-bit images have no histogram.
		// We convert to 16-bit using the min-max from each channel
		final float[][] min = new float[channels.length][frames.length];
		final float[][] max = new float[channels.length][frames.length];
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
				for (int j = 0; j < frames.length; j++)
					// Find the min and max per channel across the z-stack
					for (int k = 0; k < slices.length; k++)
					{
						final int stackIndex = imp.getStackIndex(channels[i], slices[k], frames[j]);
						final float[] data = (float[]) inputStack.getProcessor(stackIndex).getPixels();
						float cmin = data[0];
						float cmax = data[0];
						for (final float f : data)
							if (f < cmin)
								cmin = f;
							else if (f > cmax)
								cmax = f;
						if (cmin < min[i][j])
							min[i][j] = cmin;
						if (cmax > max[i][j])
							max[i][j] = cmax;
						//IJ.log(String.format("Channel %d, Frame %d, Slice %d : min = %f, max = %f", channels[i],
						//		frames[j], slices[k], cmin, cmax));
					}

			// Convert
			//IJ.log("Converting 32-bit image to 16-bit for thresholding");
			final ImageStack newStack = new ImageStack(imp.getWidth(), imp.getHeight(), imp.getStackSize());
			for (int i = 0; i < channels.length; i++)
				for (int j = 0; j < frames.length; j++)
				{
					final float cmin = min[i][j];
					final float cmax = max[i][j];
					//IJ.log(String.format("Channel %d, Frame %d : min = %f, max = %f", channels[i], frames[j], cmin,
					//		cmax));
					for (int k = 0; k < slices.length; k++)
					{
						final int stackIndex = imp.getStackIndex(channels[i], slices[k], frames[j]);
						newStack.setPixels(convertToShort(inputStack.getProcessor(stackIndex), cmin, cmax), stackIndex);
					}
				}
			inputStack = newStack;
		}

		for (int i = 0; i < channels.length; i++)
			for (int j = 0; j < frames.length; j++)
			{
				final float cmin = min[i][j];
				final float cmax = max[i][j];

				if (stackHistogram)
				{
					// Threshold the z-stack together
					int stackIndex = imp.getStackIndex(channels[i], slices[0], frames[j]);
					final int[] data = inputStack.getProcessor(stackIndex).getHistogram();
					for (int k = 1; k < slices.length; k++)
					{
						stackIndex = imp.getStackIndex(channels[i], slices[k], frames[j]);
						final int[] tmp = inputStack.getProcessor(stackIndex).getHistogram();
						for (int ii = tmp.length; ii-- > 0;)
							data[ii] += tmp[ii];
					}
					double threshold = getThreshold(thresholdMethod, data);
					if (imp.getBitDepth() == 32)
					{
						// Convert the 16-bit threshold back to the original 32-bit range
						final float scale = getScale(cmin, cmax);
						threshold = (threshold / scale) + cmin;
						//IJ.log(String.format("Channel %d, Frame %d : %f", channels[i], frames[j], threshold));
					}
					else
					{
						//IJ.log(String.format("Channel %d, Frame %d : %f", channels[i], frames[j], threshold));
					}

					for (int k = 0; k < slices.length; k++)
					{
						stackIndex = imp.getStackIndex(channels[i], slices[k], frames[j]);
						thresholds[stackIndex - 1] = threshold;
					}
				}
				else
					// Threshold each slice
					for (int k = 0; k < slices.length; k++)
					{
						final int stackIndex = imp.getStackIndex(channels[i], slices[k], frames[j]);
						final int[] data = inputStack.getProcessor(stackIndex).getHistogram();
						double threshold = getThreshold(thresholdMethod, data);
						if (imp.getBitDepth() == 32)
						{
							// Convert the 16-bit threshold back to the original 32-bit range
							final float scale = getScale(cmin, cmax);
							threshold = (threshold / scale) + cmin;
							//IJ.log(String.format("Channel %d, Frame %d : %f", channels[i], frames[j], threshold));
						}
						thresholds[stackIndex - 1] = threshold;
					}
			}

		return thresholds;
	}

	private static int getThreshold(String autoThresholdMethod, int[] statsHistogram)
	{
		if (autoThresholdMethod.endsWith("evel"))
		{
			final Multi_OtsuThreshold multi = new Multi_OtsuThreshold();
			multi.ignoreZero = false;
			final int level = autoThresholdMethod.contains("_3_") ? 3 : 4;
			final int[] threshold = multi.calculateThresholds(statsHistogram, level);
			return threshold[1];
		}
		return AutoThreshold.getThreshold(autoThresholdMethod, statsHistogram);
	}

	private static short[] convertToShort(ImageProcessor ip, float min, float max)
	{
		final float[] pixels32 = (float[]) ip.getPixels();
		final short[] pixels16 = new short[pixels32.length];
		final float scale = getScale(min, max);
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

	private static float getScale(float min, float max)
	{
		if ((max - min) == 0.0)
			return 1.0f;
		else
			return 65535.0f / (max - min);
	}

	private static int[] createArray(int total, int selected)
	{
		if (selected > 0 && selected <= total)
			return new int[] { selected };
		final int[] array = new int[total];
		for (int i = 0; i < array.length; i++)
			array[i] = i + 1;
		return array;
	}

	/**
	 * Sets the imp.
	 *
	 * @param imp
	 *            the source image for the mask generation
	 */
	public void setImp(ImagePlus imp)
	{
		this.imp = imp;
	}

	/**
	 * Gets the imp.
	 *
	 * @return the source image for the mask generation
	 */
	public ImagePlus getImp()
	{
		return imp;
	}

	/**
	 * Sets the option.
	 *
	 * @param option
	 *            the option for defining the mask
	 */
	public void setOption(int option)
	{
		this.option = option;
	}

	/**
	 * Gets the option.
	 *
	 * @return the option for defining the mask
	 */
	public int getOption()
	{
		return option;
	}

	/**
	 * Sets the threshold method.
	 *
	 * @param thresholdMethod
	 *            the thresholdMethod to set
	 */
	public void setThresholdMethod(String thresholdMethod)
	{
		this.thresholdMethod = thresholdMethod;
	}

	/**
	 * Gets the threshold method.
	 *
	 * @return the thresholdMethod
	 */
	public String getThresholdMethod()
	{
		return thresholdMethod;
	}

	/**
	 * Sets the channel.
	 *
	 * @param channel
	 *            the channel to set
	 */
	public void setChannel(int channel)
	{
		this.channel = channel;
	}

	/**
	 * Gets the channel.
	 *
	 * @return the channel
	 */
	public int getChannel()
	{
		return channel;
	}

	/**
	 * Sets the frame.
	 *
	 * @param frame
	 *            the frame to set
	 */
	public void setFrame(int frame)
	{
		this.frame = frame;
	}

	/**
	 * Gets the frame.
	 *
	 * @return the frame
	 */
	public int getFrame()
	{
		return frame;
	}

	/**
	 * Sets the slice.
	 *
	 * @param slice
	 *            the slice to set
	 */
	public void setSlice(int slice)
	{
		this.slice = slice;
	}

	/**
	 * Gets the slice.
	 *
	 * @return the slice
	 */
	public int getSlice()
	{
		return slice;
	}

	/**
	 * Checks if is removes the edge particles.
	 *
	 * @return the removeEdgeParticles
	 */
	public boolean isRemoveEdgeParticles()
	{
		return removeEdgeParticles;
	}

	/**
	 * Sets the removes the edge particles.
	 *
	 * @param removeEdgeParticles
	 *            the removeEdgeParticles to set
	 */
	public void setRemoveEdgeParticles(boolean removeEdgeParticles)
	{
		this.removeEdgeParticles = removeEdgeParticles;
	}

	/**
	 * Gets the min particle size.
	 *
	 * @return the minParticleSize
	 */
	public int getMinParticleSize()
	{
		return minParticleSize;
	}

	/**
	 * Sets the min particle size.
	 *
	 * @param minParticleSize
	 *            the minParticleSize to set
	 */
	public void setMinParticleSize(int minParticleSize)
	{
		this.minParticleSize = minParticleSize;
	}

	/**
	 * Checks if is stack histogram.
	 *
	 * @return the stackHistogram
	 */
	public boolean isStackHistogram()
	{
		return stackHistogram;
	}

	/**
	 * Checks if is assign objects.
	 *
	 * @return true, if is assign objects
	 */
	public boolean isAssignObjects()
	{
		return assignObjects;
	}

	/**
	 * Sets if assign objects.
	 *
	 * @param assignObjects
	 *            is assign objects
	 */
	public void setAssignObjects(boolean assignObjects)
	{
		this.assignObjects = assignObjects;
	}

	/**
	 * Checks if object assignment is eight connected.
	 *
	 * @return true, if is eight connected
	 */
	public boolean isEightConnected()
	{
		return eightConnected;
	}

	/**
	 * Sets if object assignment is eight connected.
	 *
	 * @param eightConnected
	 *            is eight connected
	 */
	public void setEightConnected(boolean eightConnected)
	{
		this.eightConnected = eightConnected;
	}

	/**
	 * Sets the stack histogram.
	 *
	 * @param stackHistogram
	 *            the stackHistogram to set
	 */
	public void setStackHistogram(boolean stackHistogram)
	{
		this.stackHistogram = stackHistogram;
	}

	private ImageProcessor postProcess(ImageProcessor ip)
	{
		if (!removeEdgeParticles && minParticleSize == 0 && !assignObjects)
			return ip;

		// Assign all particles
		final ObjectAnalyzer oa = new ObjectAnalyzer(ip, eightConnected);
		oa.setMinObjectSize(minParticleSize);
		final int[] mask = oa.getObjectMask();

		final int maxx = ip.getWidth();
		final int maxy = ip.getHeight();

		if (removeEdgeParticles)
		{
			final int xlimit = maxx - 1;
			final int ylimit = ip.getHeight() - 1;

			final int[] toZero = SimpleArrayUtils.newArray(oa.getMaxObject() + 1, 0, 1);

			for (int x1 = 0, x2 = ylimit * maxx; x1 < maxx; x1++, x2++)
			{
				if (mask[x1] != 0)
					toZero[mask[x1]] = 0;
				if (mask[x2] != 0)
					toZero[mask[x2]] = 0;
			}
			for (int y1 = 0, y2 = xlimit; y1 < mask.length; y1 += maxx, y2 += maxx)
			{
				if (mask[y1] != 0)
					toZero[mask[y1]] = 0;
				if (mask[y2] != 0)
					toZero[mask[y2]] = 0;
			}

			int newObjects = 0;
			for (int o = 1; o < toZero.length; o++)
			{
				if (toZero[o] == 0)
					continue;
				toZero[o] = ++newObjects;
				if (newObjects > 65535)
				{
					// No more objects can be stored in a 16-bit image
					while (o < toZero.length)
						toZero[o++] = 0;
					break;
				}
			}
			if (newObjects != oa.getMaxObject())
				// At least one object to be zerod
				for (int i = 0; i < mask.length; i++)
					mask[i] = toZero[mask[i]];
		}

		if (!assignObjects)
			// Create a binary mask
			for (int i = 0; i < mask.length; i++)
				if (mask[i] != 0)
					mask[i] = 255;

		final short[] newMask = new short[maxx * maxy];
		for (int i = 0; i < newMask.length; i++)
			newMask[i] = (short) mask[i++];

		return new ShortProcessor(maxx, maxy, newMask, null);
	}

	private ImageStack postProcess(ImageStack stack)
	{
		if (!removeEdgeParticles && minParticleSize == 0 && !assignObjects)
			return stack;

		final int maxx = stack.getWidth();
		final int maxy = stack.getHeight();
		final int maxz = stack.getSize();
		final int maxx_maxy = maxx * maxy;

		final int[] image = new int[maxx_maxy * maxz];
		for (int slice = 1, index = 0; slice <= maxz; slice++)
		{
			final ImageProcessor ip = stack.getProcessor(slice);
			for (int i = 0; i < maxx_maxy; i++)
				image[index++] = ip.get(i);
		}

		// Assign all particles
		final ObjectAnalyzer3D oa = new ObjectAnalyzer3D(image, maxx, maxy, maxz, eightConnected);
		oa.setMinObjectSize(minParticleSize);
		final int[] mask = oa.getObjectMask();

		if (removeEdgeParticles)
		{
			final int xlimit = maxx - 1;
			final int ylimit = stack.getHeight() - 1;

			final int[] toZero = SimpleArrayUtils.newArray(oa.getMaxObject() + 1, 0, 1);

			for (int z = 0, offset = 0; z < maxz; z++, offset += maxx_maxy)
			{
				for (int x1 = 0, x2 = ylimit * maxx; x1 < maxx; x1++, x2++)
				{
					if (mask[offset + x1] != 0)
						toZero[mask[offset + x1]] = 0;
					if (mask[offset + x2] != 0)
						toZero[mask[offset + x2]] = 0;
				}
				for (int y1 = 0, y2 = xlimit; y1 < maxx_maxy; y1 += maxx, y2 += maxx)
				{
					if (mask[offset + y1] != 0)
						toZero[mask[offset + y1]] = 0;
					if (mask[offset + y2] != 0)
						toZero[mask[offset + y2]] = 0;
				}
			}

			int newObjects = 0;
			for (int o = 1; o < toZero.length; o++)
			{
				if (toZero[o] == 0)
					continue;
				toZero[o] = ++newObjects;
				if (newObjects > 65535)
				{
					// No more objects can be stored in a 16-bit image
					while (o < toZero.length)
						toZero[o++] = 0;
					break;
				}
			}
			if (newObjects != oa.getMaxObject())
				// At least one object to be zerod
				for (int i = 0; i < mask.length; i++)
					mask[i] = toZero[mask[i]];
		}

		if (!assignObjects)
			// Create a binary mask
			for (int i = 0; i < mask.length; i++)
				if (mask[i] != 0)
					mask[i] = 255;

		final ImageStack newStack = new ImageStack(maxx, maxy, maxz);
		for (int slice = 1, index = 0; slice <= maxz; slice++)
		{
			final short[] newMask = new short[maxx_maxy];
			for (int i = 0; i < maxx_maxy; i++)
				newMask[i] = (short) mask[index++];
			newStack.setPixels(newMask, slice);
		}

		return newStack;
	}
}
