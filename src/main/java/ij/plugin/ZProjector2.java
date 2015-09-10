package ij.plugin;

import java.util.Arrays;

import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.WindowManager;
import ij.gui.GenericDialog;
import ij.process.ByteProcessor;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;
import ij.process.ImageStatistics;
import ij.process.ShortProcessor;

/**
 * Extend the ZProjector to support mode intensity projection.
 * 
 * Note: This class extends a copy of the default ImageJ ZProjector so that certain methods and properties can be
 * changed to protected from the private/default scope. Extending a copy allows easier update when the super class
 * changes.
 */
public class ZProjector2 extends ZProjectorCopy
{
	public static final int MODE_METHOD = 6;
	public static final int MODE_IGNORE_ZERO_METHOD = 7;
	public static final String[] METHODS = { "Average Intensity", "Max Intensity", "Min Intensity", "Sum Slices",
			"Standard Deviation", "Median", "Mode", "Mode (ignore zero)" };

	@Override
	public void run(String arg)
	{
		super.run(arg);
		if (projImage != null)
		{
			// Set the display range
			ImageProcessor ip = projImage.getProcessor();
			if (ip instanceof ByteProcessor)
			{
				// We do not want the standard 0-255 range for ByteProcessor but the min/max range 
				for (int c = 1; c <= projImage.getNChannels(); c++)
				{
					int index = projImage.getStackIndex(c, 1, 1);
					projImage.setSliceWithoutUpdate(index);
					ip = projImage.getProcessor();
					ImageStatistics stats = ImageStatistics.getStatistics(ip,
							ImageStatistics.MIN_MAX, null);
					ip.setMinAndMax(stats.min, stats.max);
				}
				projImage.setSliceWithoutUpdate(projImage.getStackIndex(1, 1, 1));
			}
			else
			{
				projImage.resetDisplayRange();
			}
			projImage.updateAndDraw();
		}
	}

	@Override
	protected GenericDialog buildControlDialog(int start, int stop)
	{
		GenericDialog gd = new GenericDialog("ZProjection", IJ.getInstance());
		gd.addNumericField("Start slice:", startSlice, 0/* digits */);
		gd.addNumericField("Stop slice:", stopSlice, 0/* digits */);
		gd.addChoice("Projection type", METHODS, METHODS[method]);
		if (isHyperstack && imp.getNFrames() > 1 && imp.getNSlices() > 1)
			gd.addCheckbox("All time frames", allTimeFrames);
		return gd;
	}

	@Override
	protected String makeTitle()
	{
		String prefix = "AVG_";
		switch (method)
		{
			case SUM_METHOD:
				prefix = "SUM_";
				break;
			case MAX_METHOD:
				prefix = "MAX_";
				break;
			case MIN_METHOD:
				prefix = "MIN_";
				break;
			case SD_METHOD:
				prefix = "STD_";
				break;
			case MEDIAN_METHOD:
				prefix = "MED_";
				break;
			case MODE_METHOD:
			case MODE_IGNORE_ZERO_METHOD:
				prefix = "MOD_";
				break;
		}
		return WindowManager.makeUniqueName(prefix + imp.getTitle());
	}

	@Override
	public void doProjection()
	{
		if (imp == null)
			return;
		sliceCount = 0;
		for (int slice = startSlice; slice <= stopSlice; slice += increment)
			sliceCount++;
		if (method >= MODE_METHOD)
		{
			projImage = doModeProjection(method == MODE_IGNORE_ZERO_METHOD);
			return;
		}
		if (method == MEDIAN_METHOD)
		{
			projImage = doMedianProjection();
			return;
		}

		super.doProjection();
	}

	private interface Projector
	{
		float value(float[] values);
	}

	private ImagePlus doProjection(String name, Projector p)
	{
		IJ.showStatus("Calculating " + name + "...");
		ImageStack stack = imp.getStack();
		// Check not an RGB stack
		if (invalidStack(stack))
			return null;
		ImageProcessor[] slices = new ImageProcessor[sliceCount];
		int index = 0;
		for (int slice = startSlice; slice <= stopSlice; slice += increment)
			slices[index++] = stack.getProcessor(slice);
		ImageProcessor ip2 = slices[0].duplicate();
		ip2 = ip2.convertToFloat();
		float[] values = new float[sliceCount];
		int width = ip2.getWidth();
		int height = ip2.getHeight();
		int inc = Math.max(height / 30, 1);
		for (int y = 0, k = 0; y < height; y++)
		{
			if (y % inc == 0)
				IJ.showProgress(y, height - 1);
			for (int x = 0; x < width; x++, k++)
			{
				for (int i = 0; i < sliceCount; i++)
					//values[i] = slices[i].getPixelValue(x, y);
					values[i] = slices[i].getf(k);
				//ip2.putPixelValue(x, y, p.value(values));
				ip2.setf(k, p.value(values));
			}
		}
		if (imp.getBitDepth() == 8)
			ip2 = ip2.convertToByte(false);
		IJ.showProgress(1, 1);
		return new ImagePlus(makeTitle(), ip2);
	}

	/**
	 * Check the stack is OK to use with getf(), i.e. not a color processor
	 * 
	 * @param stack
	 * @return True if not valid
	 */
	private boolean invalidStack(ImageStack stack)
	{
		ImageProcessor ip = stack.getProcessor(1);
		if (ip == null ||
				!(ip instanceof ByteProcessor || ip instanceof ShortProcessor || ip instanceof FloatProcessor))
		{
			IJ.error("Z Project", "Non-RGB stack required");
			return true;
		}
		return false;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see ij.plugin.ZProjectorCopy#doMedianProjection()
	 */
	@Override
	protected ImagePlus doMedianProjection()
	{
		// Override to change the method for accessing pixel values to getf() 
		return doProjection("median", new Projector()
		{
			public float value(float[] values)
			{
				return median(values);
			}
		});
	}

	protected ImagePlus doModeProjection(final boolean ignoreZero)
	{
		return doProjection("mode", new Projector()
		{
			public float value(float[] values)
			{
				return getMode(values, ignoreZero);
			}
		});
	}

	/**
	 * Return the mode of the array. Return the mode with the highest value in the event of a tie.
	 * 
	 * NaN values are ignored. The mode may be NaN only if the array is zero length or contains only NaN.
	 * 
	 * @param a
	 *            Array
	 * @param ignoreBelowZero
	 *            Ignore all values less than or equal to zero
	 * @return The mode
	 */
	public static float mode(float[] a, boolean ignoreBelowZero)
	{
		if (a == null || a.length == 0)
			return Float.NaN;
		return getMode(a, ignoreBelowZero);
	}

	/**
	 * Return the mode of the array. Return the mode with the highest value in the event of a tie.
	 * 
	 * NaN values are ignored. The mode may be NaN only if the array is zero length or contains only NaN.
	 * 
	 * @param a
	 *            Array
	 * @param ignoreBelowZero
	 *            Ignore all values less than or equal to zero
	 * @return The mode
	 */
	private static float getMode(float[] a, boolean ignoreBelowZero)
	{
		// Assume array is not null or empty

		Arrays.sort(a);

		// NaN will be placed at the end
		final int length;
		if (Float.isNaN(a[a.length - 1]))
		{
			// Ignore NaN values
			int i = a.length;
			while (i-- > 0)
			{
				if (!Float.isNaN(a[i]))
					break;
			}
			length = i + 1;
			if (length == 0)
				return Float.NaN;
		}
		else
		{
			length = a.length;
		}

		int modeCount = 0;
		float mode = 0;

		int i = 0;
		if (ignoreBelowZero)
		{
			while (i < length && a[i] <= 0)
				i++;
			if (length == i)
				return Float.NaN;
		}

		int currentCount = 1;
		float currentValue = a[i];
		while (++i < length)
		{
			if (a[i] != currentValue)
			{
				if (modeCount <= currentCount)
				{
					modeCount = currentCount;
					mode = currentValue;
				}
				currentCount = 1;
			}
			else
			{
				currentCount++;
			}
			currentValue = a[i];
		}
		// Do the final check
		if (modeCount <= currentCount)
		{
			modeCount = currentCount;
			mode = currentValue;
		}

		return mode;
	}
}
