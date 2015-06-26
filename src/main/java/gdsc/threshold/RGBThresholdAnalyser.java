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
import ij.plugin.PlugIn;
import ij.process.ByteProcessor;
import ij.process.ColorProcessor;
import ij.process.ImageProcessor;
import ij.text.TextWindow;

import java.io.File;
import java.io.FilenameFilter;

/**
 * For all the RGB images in one directory, look for a matching image in a second directory that has 3 channels.
 * Compute the minimum value (i.e. the threshold) for pixels within the red and green channel of the RGB image.
 * Then run thresholding methods on the pixels within the mask region defined by channel 3 and output a table of
 * results.
 */
public class RGBThresholdAnalyser implements PlugIn
{
	private static String TITLE = "RGB Threshold Analyser";
	private static TextWindow resultsWindow = null;

	private static String dir1 = "", dir2 = "";

	/*
	 * (non-Javadoc)
	 * 
	 * @see ij.plugin.PlugIn#run(java.lang.String)
	 */
	public void run(String arg)
	{
		dir1 = ImageJHelper.getDirectory("RGB_Directory", dir1);
		if (dir1 == null)
			return;
		dir2 = ImageJHelper.getDirectory("Image_Directory", dir2);
		if (dir2 == null)
			return;

		File[] fileList = (new File(dir1)).listFiles(new FilenameFilter()
		{
			public boolean accept(File dir, String name)
			{
				name = name.toLowerCase();
				return name.endsWith(".tif") || name.endsWith(".tiff");
			}
		});
		if (fileList == null)
			return;

		int count = 0;
		for (File file : fileList)
		{
			count++;

			ImagePlus imp = IJ.openImage(file.getAbsolutePath());
			if (imp == null)
			{
				IJ.log("Cannot open " + file.getAbsolutePath());
				continue;
			}
			if (imp.getBitDepth() != 24)
			{
				IJ.log("File is not an RGB image " + file.getAbsolutePath());
				continue;
			}

			// Find the matching image
			String name = file.getName();
			String path = dir2 + name;
			ImagePlus imp2 = IJ.openImage(path);
			if (imp2 == null)
			{
				IJ.log("Cannot open " + path);
				continue;
			}
			if (imp2.getNChannels() != 3)
			{
				IJ.log("File does not have 3 channels " + path);
				continue;
			}
			if (imp.getWidth() != imp2.getWidth() || imp.getHeight() != imp2.getHeight())
			{
				IJ.log("Matching image is not the same dimensions " + path);
				continue;
			}

			IJ.showStatus(String.format("Processing %d / %d", count, fileList.length));

			createResultsWindow();

			// Extract the 3 channels
			ColorProcessor cp = (ColorProcessor) imp.getProcessor();
			ImageProcessor ip1 = getProcessor(imp2, 1);
			ImageProcessor ip2 = getProcessor(imp2, 2);
			ImageProcessor ip3 = getProcessor(imp2, 3);

			analyse(name, cp, 1, ip1, ip3);
			analyse(name, cp, 2, ip2, ip3);

			
			if (ImageJHelper.isInterrupted())
				return;
			
			return;
		}

		IJ.showStatus(TITLE + " Finished");
	}

	private void analyse(String name, ColorProcessor cp, int channel, ImageProcessor ip, ImageProcessor maskIp)
	{
		byte[] mask = cp.getChannel(channel);

		//ImageJHelper.display("RGB Channel " + channel, new ByteProcessor(ip.getWidth(), ip.getHeight(), mask));
		//ImageJHelper.display("Channel " + channel, ip);

		// Get the histogram for the channel
		int[] h = new int[(ip instanceof ByteProcessor) ? 256 : 65336];

		// Get the manual threshold within the color channel mask
		int manual = Integer.MAX_VALUE;
		for (int i = 0; i < mask.length; i++)
		{
			if (maskIp.get(i) != 0)
			{
				h[ip.get(i)]++;

				if (mask[i] != 0)
				{
					if (manual > ip.get(i))
						manual = ip.get(i);
				}
			}
		}

		// Check the threshold is valid
		//ImageProcessor ep = ip.createProcessor(ip.getWidth(), ip.getHeight());
		int error = 0;
		long sum = 0;
		for (int i = 0; i < mask.length; i++)
		{
			if (maskIp.get(i) != 0)
			{
				if (mask[i] == 0 && ip.get(i) >= manual)
				{
					error++;
					sum += ip.get(i) - manual;
					//ep.set(i, ip.get(i));
				}
			}
		}
		if (error != 0)
		{
			System.out.printf("%s [%d] %d error pixels (sum = %d)\n", name, channel, error, sum);
			//ImageJHelper.display("Error ch "+channel, ep);
		}

		double[] stats = getStatistics(h);

		int[] thresholds = new int[Auto_Threshold.methods.length];
		for (int i = 0; i < thresholds.length; i++)
			thresholds[i] = Auto_Threshold.getThreshold(Auto_Threshold.methods[i], h);

		addResult(name, channel, stats, manual, thresholds, h);
	}

	private ImageProcessor getProcessor(ImagePlus imp, int channel)
	{
		imp.setC(channel);
		return imp.getProcessor().duplicate();
	}

	/**
	 * Return the image statistics
	 * 
	 * @param hist
	 *            The image histogram
	 * @return Array containing: min, max, av, stdDev
	 */
	private double[] getStatistics(int[] hist)
	{
		// Get the limits
		int min = 0;
		int max = hist.length - 1;
		while ((hist[min] == 0) && (min < max))
			min++;
		while ((hist[max] == 0) && (max > min))
			max--;

		// Get the average
		int count;
		double value;
		double sum = 0.0;
		double sum2 = 0.0;
		long n = 0;
		for (int i = min; i <= max; i++)
		{
			if (hist[i] > 0)
			{
				count = hist[i];
				n += count;
				value = i;
				sum += value * count;
				sum2 += (value * value) * count;
			}
		}
		double av = sum / n;

		// Get the Std.Dev
		double stdDev;
		if (n > 0)
		{
			double d = n;
			stdDev = (d * sum2 - sum * sum) / d;
			if (stdDev > 0.0)
				stdDev = Math.sqrt(stdDev / (d - 1.0));
			else
				stdDev = 0.0;
		}
		else
			stdDev = 0.0;

		return new double[] { min, max, av, stdDev };
	}

	private void createResultsWindow()
	{
		if (resultsWindow == null || !resultsWindow.isShowing())
		{
			resultsWindow = new TextWindow(TITLE + " Results", createResultsHeader(), "", 900, 500);
		}
	}

	private String createResultsHeader()
	{
		StringBuilder sb = new StringBuilder();
		sb.append("Image\t");
		sb.append("Channel\t");
		sb.append("Min\tMax\tAv\tSD");

		addMethod(sb, "Manual");
		for (String method : Auto_Threshold.methods)
			addMethod(sb, method);
		return sb.toString();
	}

	private void addMethod(StringBuilder sb, String method)
	{
		sb.append("\t").append(method).append("\tCount\tSum");
	}

	private void addResult(String name, int channel, double[] stats, int manual, int[] thresholds, int[] h)
	{
		// The threshold levels is expressed as:
		// Absolute
		// Fraction of area
		// Fraction of intensity

		// Build a cumulative histogram for the area and intensity
		final int min = (int) stats[0];
		final int max = (int) stats[1];
		double[] area = new double[max + 1];
		double[] intensity = new double[area.length];

		// Build the cumulative to represent the total below that value
		double count = 0, sum = 0;
		for (int i = min; i < area.length; i++)
		{
			area[i] = count;
			intensity[i] = sum;
			count += h[i];
			sum += h[i] * i;
		}
		// Normalise so that the numbers represent the fraction at the threshold or above
		for (int i = min; i < area.length; i++)
		{
			area[i] = (count - area[i]) / count;
			intensity[i] = (sum - intensity[i]) / sum;
		}

		StringBuilder sb = new StringBuilder();
		sb.append(name).append("\t").append(channel);
		for (int i = 0; i < stats.length; i++)
			sb.append("\t").append(IJ.d2s(stats[i]));
		addResult(sb, manual, area, intensity);
		for (int t : thresholds)
			addResult(sb, t, area, intensity);
		resultsWindow.append(sb.toString());
	}

	private void addResult(StringBuilder sb, int t, double[] area, double[] intensity)
	{
		sb.append("\t").append(t);
		sb.append("\t").append(IJ.d2s(area[t], 5));
		sb.append("\t").append(IJ.d2s(intensity[t], 5));
	}

}
