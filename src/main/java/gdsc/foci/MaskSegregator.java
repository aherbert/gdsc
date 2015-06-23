package gdsc.foci;

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

import gdsc.threshold.Auto_Threshold;
import gdsc.utils.ImageJHelper;
import ij.IJ;
import ij.ImagePlus;
import ij.WindowManager;
import ij.gui.DialogListener;
import ij.gui.GenericDialog;
import ij.gui.ImageRoi;
import ij.gui.Overlay;
import ij.plugin.filter.ExtendedPlugInFilter;
import ij.plugin.filter.PlugInFilterRunner;
import ij.process.ColorProcessor;
import ij.process.ImageProcessor;
import ij.process.ImageStatistics;

import java.awt.AWTEvent;
import java.awt.Checkbox;
import java.awt.Label;
import java.awt.Scrollbar;
import java.awt.TextField;
import java.util.ArrayList;
import java.util.Arrays;

/**
 * Overlay a mask on the channel. For each unique pixel value in the mask (defining an object), analyse the pixels
 * values
 * from the image and segregate the objects into two classes.
 */
public class MaskSegregator implements ExtendedPlugInFilter, DialogListener
{
	public static String FRAME_TITLE = "Mask Segregator";

	private int flags = DOES_16 + DOES_8G + FINAL_PROCESSING;
	private ImagePlus imp;
	private ImageProcessor maskIp;

	private static String maskTitle = "";
	private static boolean autoCutoff = true;
	private static double cutoff = 0;

	private Checkbox autoCheckbox;
	private Scrollbar cutoffSlider;
	private TextField cutoffText;
	private Label label;

	/*
	 * (non-Javadoc)
	 * 
	 * @see ij.plugin.filter.PlugInFilter#setup(java.lang.String, ij.ImagePlus)
	 */
	public int setup(String arg, ImagePlus imp)
	{
		if (imp == null)
		{
			IJ.noImage();
			return DONE;
		}
		this.imp = imp;
		if (arg.equals("final"))
		{
			segregateMask();
			return DONE;
		}
		return flags;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see ij.plugin.filter.ExtendedPlugInFilter#showDialog(ij.ImagePlus,
	 * java.lang.String, ij.plugin.filter.PlugInFilterRunner)
	 */
	public int showDialog(ImagePlus imp, String command, PlugInFilterRunner pfr)
	{
		GenericDialog gd = new GenericDialog(FRAME_TITLE);

		String[] names = getMasks(imp);
		if (names.length == 0)
			return DONE;

		ImageStatistics stats = ImageStatistics.getStatistics(imp.getProcessor(), ImageStatistics.MIN_MAX, null);
		if (cutoff < stats.min)
			cutoff = stats.min;
		if (cutoff > stats.max)
			cutoff = stats.max;

		gd.addChoice("Mask", names, maskTitle);
		gd.addCheckbox("Auto_cutoff", autoCutoff);
		gd.addSlider("Cut-off", stats.min, stats.max, cutoff);
		gd.addMessage("");

		autoCheckbox = (Checkbox) gd.getCheckboxes().get(0);
		cutoffSlider = (Scrollbar) gd.getSliders().get(0);
		cutoffText = (TextField) gd.getNumericFields().get(0);
		label = (Label) gd.getMessage();

		gd.addHelp(gdsc.help.URL.UTILITY);
		gd.addPreviewCheckbox(pfr);
		gd.addDialogListener(this);
		gd.showDialog();

		if (gd.wasCanceled() || !dialogItemChanged(gd, null))
		{
			imp.setOverlay(null);
			return DONE;
		}

		return flags;
	}

	/**
	 * Build a list of 8/16 bit images that match the width and height of the input image
	 * 
	 * @param inputImp
	 * @return
	 */
	private String[] getMasks(ImagePlus inputImp)
	{
		String[] names = new String[WindowManager.getImageCount()];
		int count = 0;
		for (int id : gdsc.utils.ImageJHelper.getIDList())
		{
			ImagePlus imp = WindowManager.getImage(id);
			if (imp == null)
				continue;
			if ((imp.getBitDepth() == 24 || imp.getBitDepth() == 32))
				continue;
			if (imp.getWidth() != inputImp.getWidth() || imp.getHeight() != inputImp.getHeight())
				continue;
			if (imp.getTitle().equals(inputImp.getTitle()))
				continue;

			names[count++] = imp.getTitle();
		}
		return Arrays.copyOf(names, count);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see ij.gui.DialogListener#dialogItemChanged(ij.gui.GenericDialog,
	 * java.awt.AWTEvent)
	 */
	public boolean dialogItemChanged(GenericDialog gd, AWTEvent e)
	{
		// Preview checkbox will be null if running headless
		boolean isPreview = (gd.getPreviewCheckbox() != null && gd.getPreviewCheckbox().getState());

		if (!isPreview)
		{
			// Turn off the preview
			imp.setOverlay(null);
			label.setText("");
		}

		maskTitle = gd.getNextChoice();
		autoCutoff = gd.getNextBoolean();
		cutoff = gd.getNextNumber();

		// Check if this is a change to the settings during a preview and update the 
		// auto threshold property
		if (isPreview && e.getSource() != null && e.getSource() != autoCheckbox &&
				e.getSource() != gd.getPreviewCheckbox())
		{
			if (defaultCutoff >= 0) // Check we have computed the threshold
			{
				autoCutoff = (cutoff == defaultCutoff);
				autoCheckbox.setState(autoCutoff);
			}
		}

		return true;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see ij.plugin.filter.ExtendedPlugInFilter#setNPasses(int)
	 */
	public void setNPasses(int nPasses)
	{
		// Do nothing
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see ij.plugin.filter.PlugInFilter#run(ij.process.ImageProcessor)
	 */
	public void run(ImageProcessor inputIp)
	{
		analyseObjects();

		label.setText(String.format("N = %d, Min = %.2f, Max = %.2f, Av = %.2f", objects.length, stats[0], stats[1],
				stats[2]));

		int cutoff = getCutoff();

		// Segregate using the object means
		int[] color = new int[maxObject + 1];
		final int exclude = getRGB(255, 0, 0);
		final int include = getRGB(0, 255, 0);
		for (int i = 0; i < objects.length; i++)
		{
			final int maskValue = (int) objects[i][0];
			final double av = objects[i][2];
			color[maskValue] = (av < cutoff) ? exclude : include;
		}

		ColorProcessor cp = new ColorProcessor(inputIp.getWidth(), inputIp.getHeight());
		for (int i = 0; i < maskIp.getPixelCount(); i++)
		{
			final int maskValue = maskIp.get(i);
			if (maskValue != 0)
				cp.set(i, color[maskValue]);
		}

		// Overlay the segregated mask objects on the image
		ImageRoi roi = new ImageRoi(0, 0, cp);
		roi.setZeroTransparent(true);
		roi.setOpacity(0.5);
		Overlay overlay = new Overlay();
		overlay.add(roi);
		imp.setOverlay(overlay);
	}

	private int getRGB(int r, int g, int b)
	{
		return ((r << 16) + (g << 8) + b);
	}

	private String lastMaskTitle = null;
	private double[][] objects = null;
	private double[] stats = null;
	private int defaultCutoff = -1;
	private int maxObject;
	
	private void analyseObjects()
	{
		// Check if we already have the objects
		if (lastMaskTitle != null && lastMaskTitle.equals(maskTitle))
			return;

		defaultCutoff = -1;

		// Get the mask
		ImagePlus maskImp = WindowManager.getImage(maskTitle);
		if (maskImp == null)
			return;

		maskIp = maskImp.getProcessor();

		// Find the maximum value
		maxObject = 0;
		for (int i = 0; i < maskIp.getPixelCount(); i++)
			if (maxObject < maskIp.get(i))
				maxObject = maskIp.get(i);

		// Analyse the objects
		int[] count = new int[maxObject + 1];
		long[] sum = new long[count.length];

		ImageProcessor ch = imp.getProcessor();
		for (int i = 0; i < maskIp.getPixelCount(); i++)
		{
			final int value = maskIp.get(i);
			count[value]++;
			sum[value] += ch.get(i);
		}

		ArrayList<double[]> tmpObjects = new ArrayList<double[]>();
		for (int i = 0; i < count.length; i++)
		{
			if (count[i] > 0)
				tmpObjects.add(new double[] { i, count[i], sum[i] / (double) count[i] });
		}
		objects = tmpObjects.toArray(new double[0][0]);

		// Get the min, max and average pixel value
		double min = Double.POSITIVE_INFINITY;
		double max = Double.NEGATIVE_INFINITY;
		double average = 0;
		for (int i = 0; i < objects.length; i++)
		{
			final double av = objects[i][2];
			if (min > av)
				min = av;
			if (max < av)
				max = av;
			average += av;
		}
		average /= objects.length;
		stats = new double[] { min, max, average };

		cutoffSlider.setMinimum((int) min);
		cutoffSlider.setMaximum((int) Math.ceil(max));

		lastMaskTitle = maskTitle;
	}

	private int getCutoff()
	{
		return (autoCutoff) ? getAutoCutoff() : (int) MaskSegregator.cutoff;
	}

	private int getAutoCutoff()
	{
		if (defaultCutoff < 0)
		{
			// Build a histogram using the average pixel values per object
			int[] h = new int[65336];
			for (int i = 0; i < objects.length; i++)
			{
				final int count = (int) objects[i][1];
				final int av = (int) Math.round(objects[i][2]);
				h[av] += count;
			}

			defaultCutoff = Auto_Threshold.getThreshold("Otsu", h);
		}

		// Reset the position on the slider for the dialog
		if (cutoffSlider != null)
		{
			String newValue = "" + defaultCutoff;
			if (!cutoffText.getText().equals(newValue))
			{
				//cutoffSlider.setValue(cutoff);
				cutoffText.setText(newValue);
			}
		}

		return defaultCutoff;
	}

	/**
	 * Do the final processing to create a new mask using the object segregation
	 */
	private void segregateMask()
	{
		// Remove the overlay
		imp.setOverlay(null);

		// Create a new mask using the segregated objects
		analyseObjects();
		if (objects == null)
			return;

		// No need to update this in the getAutoCutoff method
		cutoffSlider = null;

		// Obtaining the cutoff here allows all the input to be obtained from the configuration
		// that may have been set in the preview dialog or in a macro
		final int cutoff = getCutoff();

		// Create a look-up table of objects to include or exclude
		boolean[] exclude = new boolean[maxObject + 1];
		for (int i = 0; i < objects.length; i++)
		{
			final int maskValue = (int) objects[i][0];
			final double av = objects[i][2];
			exclude[maskValue] = (av < cutoff);
		}

		// Create two masks for the segregated objects
		ImageProcessor excludeIp = maskIp.createProcessor(maskIp.getWidth(), maskIp.getHeight());
		ImageProcessor includeIp = maskIp.createProcessor(maskIp.getWidth(), maskIp.getHeight());

		for (int i = 0; i < maskIp.getPixelCount(); i++)
		{
			final int maskValue = maskIp.get(i);
			if (maskValue != 0)
			{
				if (exclude[maskValue])
					excludeIp.set(i, maskValue);
				else
					includeIp.set(i, maskValue);
			}
		}

		ImageJHelper.display(maskTitle + " Include", includeIp);
		ImageJHelper.display(maskTitle + " Exclude", excludeIp);
	}
}
