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
import java.util.Arrays;

import gdsc.UsageTracker;
import gdsc.core.data.procedures.FValueProcedure;
import gdsc.core.data.procedures.IValueProcedure;
import gdsc.core.data.utils.Rounder;
import gdsc.core.data.utils.RounderFactory;
import gdsc.core.ij.Utils;
import gdsc.core.ij.roi.RoiHelper;
import gdsc.core.threshold.AutoThreshold;
import gdsc.core.threshold.FloatHistogram;
import gdsc.core.threshold.IntHistogram;
import gdsc.core.utils.Statistics;
import gnu.trove.list.array.TFloatArrayList;
import ij.ImagePlus;
import ij.ImageStack;
import ij.gui.ExtendedGenericDialog;
import ij.gui.Roi;
import ij.plugin.filter.PlugInFilter;
import ij.process.ImageProcessor;
import ij.text.TextWindow;

/**
 * Analyses the foreground pixels in an image.
 */
public class ForegroundAnalyser implements PlugInFilter
{
	private static String TITLE = "Foreground Analyser";
	private static TextWindow resultsWindow = null;

	private static String method = AutoThreshold.Method.OTSU.name;
	private static boolean showMask = false;
	private static boolean doStack = true;
	private static String[] BINS = { "256", "512", "1024", "2048", "4096", "8192", "16384" };
	private static int histogramBins = 4;

	private static int getHistogramBins()
	{
		// Check range
		if (histogramBins < 0)
			histogramBins = 0;
		else if (histogramBins >= BINS.length)
			histogramBins = BINS.length - 1;
		// Just parse to an int
		return Integer.parseInt(BINS[histogramBins]);
	}

	private ImagePlus imp;
	private boolean isMultiZ;
	private boolean is32bit;

	/*
	 * (non-Javadoc)
	 * 
	 * @see ij.plugin.filter.PlugInFilter#setup(java.lang.String, ij.ImagePlus)
	 */
	public int setup(String arg, ImagePlus imp)
	{
		UsageTracker.recordPlugin(this.getClass(), arg);
		int flags = DOES_8G | DOES_16 | DOES_32 | NO_UNDO | NO_CHANGES;
		this.imp = imp;
		return flags;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see ij.plugin.filter.PlugInFilter#run(ij.process.ImageProcessor)
	 */
	public void run(ImageProcessor ip)
	{
		if (!showDialog())
			return;

		analyse(ip);
	}

	/**
	 * Show an ImageJ Dialog and get the parameters
	 * 
	 * @return False if the user cancelled
	 */
	private boolean showDialog()
	{
		ExtendedGenericDialog gd = new ExtendedGenericDialog(TITLE);

		isMultiZ = imp.getNSlices() > 1;
		is32bit = imp.getBitDepth() == 32;

		gd.addMessage("Threshold pixels inside an ROI and analyse foreground pixels");
		gd.addChoice("Threshold_method", AutoThreshold.getMethods(true), method);
		gd.addCheckbox("Show_mask", showMask);
		if (isMultiZ)
			gd.addCheckbox("Do_stack", doStack);
		if (is32bit)
			gd.addChoice("Histogram_bins", BINS, histogramBins);
		gd.addHelp(gdsc.help.URL.UTILITY);

		gd.showDialog();

		if (gd.wasCanceled())
			return false;

		method = gd.getNextChoice();
		showMask = gd.getNextBoolean();
		if (isMultiZ)
			doStack = gd.getNextBoolean();
		if (is32bit)
			histogramBins = gd.getNextChoiceIndex();

		return true;
	}

	private void analyse(ImageProcessor ip)
	{
		// Build a stack of pixels to analyse
		ImageStack stack = new ImageStack(imp.getWidth(), imp.getHeight());
		int channel = imp.getChannel();
		int slice = imp.getSlice();
		int frame = imp.getFrame();
		if (isMultiZ && doStack)
		{
			ImageStack inputStack = imp.getImageStack();
			slice = 0;
			for (int n = 1, nSlices = imp.getNSlices(); n <= nSlices; n++)
			{
				stack.addSlice(null, inputStack.getPixels(imp.getStackIndex(channel, n, frame)));
			}
		}
		else
		{
			stack.addSlice(ip);
		}

		Statistics stats = new Statistics();
		int n;
		float t;

		Roi roi = imp.getRoi();
		if (imp.getBitDepth() == 32)
		{
			// Get the pixel values
			final TFloatArrayList data = new TFloatArrayList(ip.getPixelCount());
			FValueProcedure p = new FValueProcedure()
			{
				public void execute(float value)
				{
					data.add(value);
				}
			};
			RoiHelper.forEach(roi, stack, p);

			// Count values
			float[] values = data.toArray();
			n = values.length;

			// Threshold
			Arrays.sort(values);
			FloatHistogram h = FloatHistogram.buildHistogram(values, false);

			t = h.getThreshold(AutoThreshold.getMethod(method), getHistogramBins());

			// Analyse all pixels above the threshold
			int i = Arrays.binarySearch(values, t);
			if (i < 0)
				i = -(i + 1);
			stats.add(values, i, values.length);
		}
		else
		{
			// Integer histogram
			final int[] data = new int[(imp.getBitDepth() == 8) ? 256 : 65336];
			IValueProcedure p = new IValueProcedure()
			{
				public void execute(int value)
				{
					data[value]++;
				}
			};
			RoiHelper.forEach(roi, stack, p);

			// Count values
			n = 0;
			for (int i = 0; i < data.length; i++)
				n += data[i];

			// Threshold
			IntHistogram h = new IntHistogram(data, 0);

			t = h.getThreshold(AutoThreshold.getMethod(method));

			// Analyse all pixels above the threshold
			for (int i = (int) t; i < data.length; i++)
			{
				int c = data[i];
				if (c != 0)
					stats.add(c, i);
			}
		}

		// Show results
		createResultsWindow();
		addResult(channel, slice, frame, roi, n, t, stats);

		// Show foreground mask...
		if (showMask)
			showMask(roi, stack, t);
	}

	private void createResultsWindow()
	{
		if (resultsWindow == null || !resultsWindow.isShowing())
		{
			resultsWindow = new TextWindow(TITLE + " Results", createResultsHeader(), "", 900, 300);
		}
	}

	private String createResultsHeader()
	{
		StringBuilder sb = new StringBuilder();
		sb.append("Image\t");
		sb.append("C\t");
		sb.append("Z\t");
		sb.append("T\t");
		sb.append("Roi\t");
		sb.append("Pixels\t");
		sb.append("Threshold\t");
		sb.append("Sum\t");
		sb.append("N\t");
		sb.append("Mean\t");
		sb.append("SD\t");
		return sb.toString();
	}

	private void addResult(int channel, int slice, int frame, Roi roi, int n, float t, Statistics stats)
	{
		StringBuilder sb = new StringBuilder();
		sb.append(imp.getTitle()).append('\t');
		sb.append(channel).append('\t');
		if (slice == 0)
		{
			sb.append("1-").append(imp.getNSlices()).append('\t');
		}
		else
			sb.append(slice).append('\t');
		sb.append(frame).append('\t');
		if (roi == null)
			sb.append('\t');
		else
		{
			Rectangle bounds = roi.getBounds();
			sb.append(bounds.x).append(',').append(bounds.y).append(' ');
			sb.append(bounds.width).append('x').append(bounds.height).append('\t');
		}
		sb.append(n).append('\t');
		Rounder r = RounderFactory.create(4);
		sb.append(r.toString(t)).append('\t');
		double sum = stats.getSum();
		long lsum = (long) sum;
		if (lsum == sum)
			sb.append(lsum).append('\t');
		else
			sb.append(Double.toString(sum)).append('\t');
		sb.append(stats.getN()).append('\t');
		sb.append(r.toString(stats.getMean())).append('\t');
		sb.append(r.toString(stats.getStandardDeviation())).append('\t');
		resultsWindow.append(sb.toString());
	}

	private void showMask(Roi roi, ImageStack stack, float t)
	{
		final int maxx = stack.getWidth();
		final int maxy = stack.getHeight();
		ImageStack maskStack = new ImageStack(maxx, maxy);
		final int n = maxx * maxy;
		if (roi == null)
		{
			for (int slice = 1; slice <= stack.getSize(); slice++)
			{
				byte[] pixels = new byte[n];
				ImageProcessor ip = stack.getProcessor(slice);
				for (int i = 0; i < n; i++)
					if (ip.getf(i) >= t)
						pixels[i] = -1;
				maskStack.addSlice(null, pixels);
			}
		}
		else
		{
			final Rectangle roiBounds = roi.getBounds();
			final int xOffset = roiBounds.x;
			final int yOffset = roiBounds.y;
			final int rwidth = roiBounds.width;
			final int rheight = roiBounds.height;

			ImageProcessor mask = roi.getMask();
			if (mask == null)
			{
				for (int slice = 1; slice <= stack.getSize(); slice++)
				{
					byte[] pixels = new byte[n];
					ImageProcessor ip = stack.getProcessor(slice);
					for (int y = 0; y < rheight; y++)
					{
						for (int x = 0, i = (y + yOffset) * maxx + xOffset; x < rwidth; x++, i++)
						{
							if (ip.getf(i) >= t)
								pixels[i] = -1;
						}
					}
					maskStack.addSlice(null, pixels);
				}
			}
			else
			{
				for (int slice = 1; slice <= stack.getSize(); slice++)
				{
					byte[] pixels = new byte[n];
					ImageProcessor ip = stack.getProcessor(slice);
					for (int y = 0, j = 0; y < rheight; y++)
					{
						for (int x = 0, i = (y + yOffset) * maxx + xOffset; x < rwidth; x++, i++, j++)
						{
							if (ip.getf(i) >= t && mask.get(j) != 0)
								pixels[i] = -1;
						}
					}
					maskStack.addSlice(null, pixels);
				}
			}
		}

		Utils.display(TITLE, maskStack);
	}
}
