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
import ij.process.ShortProcessor;

import java.awt.AWTEvent;
import java.awt.Checkbox;
import java.awt.Label;
import java.awt.Scrollbar;
import java.awt.TextField;
import java.util.ArrayList;
import java.util.Arrays;

/**
 * Overlay a mask on the channel. For each unique pixel value in the mask (defining an object), analyse the pixels
 * values from the image and segregate the objects into two classes. Objects must be contiguous pixels, allowing
 * segregation of ordinary binary masks.
 */
public class MaskSegregator implements ExtendedPlugInFilter, DialogListener
{
	public static String FRAME_TITLE = "Mask Segregator";

	private int flags = DOES_16 + DOES_8G + FINAL_PROCESSING;
	private ImagePlus imp;
	private ImageProcessor maskIp;
	private int[] objectMask;

	private static String maskTitle = "";
	private static boolean autoCutoff = true;
	private static double cutoff = 0;
	private static boolean splitMask = false;

	private Checkbox autoCheckbox;
	private Scrollbar cutoffSlider;
	private TextField cutoffText;
	private Label label, label2;

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
		String[] names = getMasks(imp);
		if (names.length == 0)
		{
			IJ.error(FRAME_TITLE, "No masks match the image dimensions");
			return DONE;
		}

		GenericDialog gd = new GenericDialog(FRAME_TITLE);

		gd.addMessage("Overlay a mask on the current image and segregate objects into two classes.\n \nObjects are defined with contiguous pixels of the same value.\nThe mean image value for each object is used for segregation.");

		ImageStatistics stats = ImageStatistics.getStatistics(imp.getProcessor(), ImageStatistics.MIN_MAX, null);
		if (cutoff < stats.min)
			cutoff = stats.min;
		if (cutoff > stats.max)
			cutoff = stats.max;

		gd.addChoice("Mask", names, maskTitle);
		gd.addMessage("");
		label = (Label) gd.getMessage();
		gd.addMessage("");
		label2 = (Label) gd.getMessage();
		gd.addCheckbox("Auto_cutoff", autoCutoff);
		gd.addSlider("Cut-off", stats.min, stats.max, cutoff);
		gd.addCheckbox("Split_mask", splitMask);

		autoCheckbox = (Checkbox) gd.getCheckboxes().get(0);
		cutoffSlider = (Scrollbar) gd.getSliders().get(0);
		cutoffText = (TextField) gd.getNumericFields().get(0);

		gd.addHelp(gdsc.help.URL.FIND_FOCI);
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
			label2.setText("");
		}

		maskTitle = gd.getNextChoice();
		autoCutoff = gd.getNextBoolean();
		cutoff = gd.getNextNumber();
		splitMask = gd.getNextBoolean();

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
		int nExclude = 0;
		for (int i = 0; i < objects.length; i++)
		{
			final int maskValue = (int) objects[i][0];
			final double av = objects[i][2];
			if (av < cutoff)
			{
				color[maskValue] = exclude;
				nExclude++;
			}
			else
			{
				color[maskValue] = include;
			}
		}
		final int nInclude = objects.length - nExclude;
		label2.setText(String.format("Include = %d, Exclude = %d", nInclude, nExclude));

		ColorProcessor cp = new ColorProcessor(inputIp.getWidth(), inputIp.getHeight());
		for (int i = 0; i < objectMask.length; i++)
		{
			final int maskValue = objectMask[i];
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
		final int[] maskImage = new int[maskIp.getPixelCount()];
		for (int i = 0; i < maskImage.length; i++)
			maskImage[i] = maskIp.get(i);

		// Perform a search for objects. 
		// Expand any non-zero pixel value into all 8-connected pixels of the same value.
		objectMask = new int[maskImage.length];
		maxObject = 0;

		int[] pList = new int[100];
		initialise(maskIp);

		for (int i = 0; i < maskImage.length; i++)
		{
			// Look for non-zero values that are not already in an object
			if (maskImage[i] != 0 && objectMask[i] == 0)
			{
				maxObject++;
				pList = expandObjectXY(maskImage, objectMask, i, maxObject, pList);
			}
		}

		// Analyse the objects
		int[] count = new int[maxObject + 1];
		long[] sum = new long[count.length];

		ImageProcessor ch = imp.getProcessor();
		for (int i = 0; i < maskImage.length; i++)
		{
			final int value = objectMask[i];
			if (value != 0)
			{
				count[value]++;
				sum[value] += ch.get(i);
			}
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

		final int minimum = (int) min;
		final int maximum = (int) Math.ceil(max) + 1;
		int value = cutoffSlider.getValue();
		if (value < minimum)
			value = minimum;

		cutoffSlider.setValues(value, 1, minimum, maximum);

		lastMaskTitle = maskTitle;
	}

	/**
	 * Searches from the specified point to find all coordinates of the same value and assigns them to given maximum ID.
	 */
	private int[] expandObjectXY(final int[] image, final int[] objectMask, final int index0, final int id, int[] pList)
	{
		objectMask[index0] = id; // mark first point
		int listI = 0; // index of current search element in the list
		int listLen = 1; // number of elements in the list

		// we create a list of connected points and start the list at the current point
		pList[listI] = index0;

		final int v0 = image[index0];

		do
		{
			final int index1 = pList[listI];
			final int x1 = index1 % maxx;
			final int y1 = index1 / maxx;

			boolean isInnerXY = (y1 != 0 && y1 != ylimit) && (x1 != 0 && x1 != xlimit);

			for (int d = 8; d-- > 0;)
			{
				if (isInnerXY || isWithinXY(x1, y1, d))
				{
					int index2 = index1 + offset[d];
					if (objectMask[index2] != 0)
					{
						// This has been done already, ignore this point
						continue;
					}

					int v2 = image[index2];

					if (v2 == v0)
					{
						// Add this to the search
						pList[listLen++] = index2;
						objectMask[index2] = id;
						if (pList.length == listLen)
							pList = Arrays.copyOf(pList, (int) (listLen * 1.5));
					}
				}
			}

			listI++;

		} while (listI < listLen);

		return pList;
	}

	private int maxx, maxy;
	private int xlimit, ylimit;
	private int[] offset;
	private final int[] DIR_X_OFFSET = new int[] { 0, 1, 1, 1, 0, -1, -1, -1 };
	private final int[] DIR_Y_OFFSET = new int[] { -1, -1, 0, 1, 1, 1, 0, -1 };

	/**
	 * Creates the direction offset tables.
	 */
	private void initialise(ImageProcessor ip)
	{
		maxx = ip.getWidth();
		maxy = ip.getHeight();

		xlimit = maxx - 1;
		ylimit = maxy - 1;

		// Create the offset table (for single array 3D neighbour comparisons)
		offset = new int[DIR_X_OFFSET.length];
		for (int d = offset.length; d-- > 0;)
		{
			offset[d] = maxx * DIR_Y_OFFSET[d] + DIR_X_OFFSET[d];
		}
	}

	/**
	 * returns whether the neighbour in a given direction is within the image. NOTE: it is assumed that the pixel x,y
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
	private boolean isWithinXY(int x, int y, int direction)
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
			case 8:
				return true;
		}
		return false;
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

		if (splitMask)
		{
			// Create a look-up table of objects to include or exclude
			boolean[] exclude = new boolean[maxObject + 1];
			for (int i = 0; i < objects.length; i++)
			{
				final int maskValue = (int) objects[i][0];
				final double av = objects[i][2];
				exclude[maskValue] = (av < cutoff);
			}

			// Create two masks for the segregated objects
			ImageProcessor excludeIp = maskIp.createProcessor(maxx, maxy);
			ImageProcessor includeIp = maskIp.createProcessor(maxx, maxy);

			for (int i = 0; i < objectMask.length; i++)
			{
				final int maskValue = objectMask[i];
				if (maskValue != 0)
				{
					if (exclude[maskValue])
						excludeIp.set(i, maskIp.get(i));
					else
						includeIp.set(i, maskIp.get(i));
				}
			}

			ImageJHelper.display(maskTitle + " Include", includeIp);
			ImageJHelper.display(maskTitle + " Exclude", excludeIp);
		}
		else
		{
			// Create a lookup table for the new mask objects.
			// Q. Should we maintain the old mask value? This version uses new numbering.
			int[] newMaskValue = new int[maxObject + 1];
			int include = -1;
			int exclude = 1;
			for (int i = 0; i < objects.length; i++)
			{
				final int maskValue = (int) objects[i][0];
				final double av = objects[i][2];
				newMaskValue[maskValue] = (av < cutoff) ? exclude++ : include--;
			}

			// Add the bonus to the new mask value for the include objects
			final int bonus = getBonus(include);
			for (int i = 1; i < newMaskValue.length; i++)
			{
				if (newMaskValue[i] < 0)
					newMaskValue[i] = bonus - newMaskValue[i];
			}

			ImageProcessor ip = new ShortProcessor(maxx, maxy);
			for (int i = 0; i < objectMask.length; i++)
			{
				final int maskValue = objectMask[i];
				if (maskValue != 0)
				{
					ip.set(i, newMaskValue[maskValue]);
				}
			}
			ip.setMinAndMax(0, exclude);

			ImageJHelper.display(maskTitle + " Segregated", ip);
		}
	}

	private int getBonus(int include)
	{
		int bonus = 1000;
		while (bonus < include)
			bonus += 1000;
		return bonus;
	}
}
