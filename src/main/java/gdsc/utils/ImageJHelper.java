package gdsc.utils;

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

import java.io.File;
import java.math.BigDecimal;
import java.math.MathContext;
import java.util.ArrayList;

import ij.IJ;
import ij.ImagePlus;
import ij.WindowManager;
import ij.io.OpenDialog;
import ij.plugin.HyperStackReducer;
import ij.plugin.ZProjector;
import ij.process.ImageProcessor;
import ij.text.TextWindow;

/**
 * Adds helper functionality for ImageJ
 */
public class ImageJHelper
{
	// Flags for buildImageList

	public final static int SINGLE = 1; // Single plane (2D image)
	public final static int BINARY = 2; // Binary image
	public final static int GREY_SCALE = 4; // Greyscale image (8, 16, 32 bit)
	public final static int GREY_8_16 = 8; // Greyscale image (8, 16 bit)
	public final static int NO_IMAGE = 16; // Add no image option
	public final static String NO_IMAGE_TITLE = "[None]";

	/**
	 * Returns a list of the IDs of open images. Returns
	 * an empty array if no windows are open.
	 * 
	 * @see {@link ij.WindowManager#getIDList() }
	 * 
	 * @return List of IDs
	 */
	public static int[] getIDList()
	{
		int[] list = WindowManager.getIDList();
		return (list != null) ? list : new int[0];
	}

	/**
	 * Build a list of all the image names.
	 * 
	 * @param flags
	 *            Specify the types of image to collate
	 * @param ignoreSuffix
	 *            A list of title suffixes to ignore
	 * @return The list of images
	 */
	public static String[] getImageList(final int flags, String[] ignoreSuffix)
	{
		ArrayList<String> newImageList = new ArrayList<String>();

		if ((flags & NO_IMAGE) == NO_IMAGE)
			newImageList.add(NO_IMAGE_TITLE);
		if (ignoreSuffix == null)
			ignoreSuffix = new String[0];

		for (int id : gdsc.utils.ImageJHelper.getIDList())
		{
			ImagePlus imp = WindowManager.getImage(id);
			if (imp == null)
				continue;
			// Check flags
			if ((flags & SINGLE) == SINGLE && imp.getNDimensions() > 2)
				continue;
			if ((flags & BINARY) == BINARY && !imp.getProcessor().isBinary())
				continue;
			if ((flags & GREY_SCALE) == GREY_SCALE && imp.getBitDepth() == 24)
				continue;
			if ((flags & GREY_8_16) == GREY_8_16 && (imp.getBitDepth() == 24 || imp.getBitDepth() == 32))
				continue;
			if (ignoreImage(ignoreSuffix, imp))
				continue;
			
			newImageList.add(imp.getTitle());
		}

		return newImageList.toArray(new String[0]);
	}

	public static boolean ignoreImage(String[] ignoreSuffix, ImagePlus imp)
	{
		for (String suffix : ignoreSuffix)
			if (imp.getTitle().endsWith(suffix))
				return true;
		return false;
	}

	/**
	 * Combine the arguments into a complete file path
	 * 
	 * @param paths
	 * @return The file path
	 */
	public static String combinePath(String... paths)
	{
		File file = new File(paths[0]);

		for (int i = 1; i < paths.length; i++)
		{
			file = new File(file, paths[i]);
		}

		return file.getPath();
	}

	/**
	 * Perform an either/or operator
	 * 
	 * @param a
	 * @param b
	 * @return true if one or the other is true but not both
	 */
	public static boolean xor(boolean a, boolean b)
	{
		return (a && !b) || (b && !a);
	}

	/**
	 * Extracts a single tile image processor from a hyperstack using the given projection method from the ZProjector
	 * 
	 * @param imp
	 *            Image hyperstack
	 * @param frame
	 *            The frame to extract
	 * @param channel
	 *            The channel to extract
	 * @param projectionMethod
	 * @return A new image processor
	 * 
	 * @see {@link ij.plugin.ZProjector }
	 */
	public static ImageProcessor extractTile(ImagePlus imp, int frame, int channel, int projectionMethod)
	{
		int c = imp.getChannel();
		int s = imp.getSlice();
		int f = imp.getFrame();

		imp.setPositionWithoutUpdate(channel, 1, frame);

		// Extract the timepoint/channel z-stack
		HyperStackReducer reducer = new HyperStackReducer(imp);
		int slices = imp.getNSlices();
		ImagePlus imp1 = imp.createHyperStack("", 1, slices, 1, imp.getBitDepth());
		reducer.reduce(imp1);

		// Perform projectionMethod
		ZProjector projector = new ZProjector(imp1);
		projector.setMethod(projectionMethod);
		projector.doProjection();

		imp.setPositionWithoutUpdate(c, s, f);

		return projector.getProjection().getProcessor();
	}

	/**
	 * Check if the escape key has been pressed. Show a status aborted message if true.
	 * 
	 * @return True if aborted
	 */
	public static boolean isInterrupted()
	{
		if (IJ.escapePressed())
		{
			IJ.beep();
			IJ.showStatus("Aborted");
			return true;
		}
		return false;
	}

	/**
	 * Check if the current window has the given headings, refreshing the headings if necessary.
	 * Only works if the window is showing.
	 * 
	 * @param textWindow
	 * @param headings
	 * @param preserve
	 *            Preserve the current data (note that is may not match the new headings)
	 * @return True if the window headings were changed
	 */
	public static boolean refreshHeadings(TextWindow textWindow, String headings, boolean preserve)
	{
		if (textWindow != null && textWindow.isShowing())
		{
			if (!textWindow.getTextPanel().getColumnHeadings().equals(headings))
			{
				StringBuffer sb = new StringBuffer();
				if (preserve)
					for (int i = 0; i < textWindow.getTextPanel().getLineCount(); i++)
						sb.append(textWindow.getTextPanel().getLine(i)).append("\n");

				textWindow.getTextPanel().setColumnHeadings(headings);

				if (preserve)
					textWindow.append(sb.toString());

				return true;
			}
		}
		return false;
	}
	
	/**
	 * Round the double to the specified significant digits
	 * 
	 * @param d
	 * @param significantDigits
	 * @return
	 */
	public static String rounded(double d, int significantDigits)
	{
		if (Double.isInfinite(d) || Double.isNaN(d))
			return "" + d;
		BigDecimal bd = new BigDecimal(d);
		bd = bd.round(new MathContext(significantDigits));
		return "" + bd.doubleValue();
	}

	/**
	 * Round the double to 4 significant digits
	 * 
	 * @param d
	 * @return
	 */
	public static String rounded(double d)
	{
		return rounded(d, 4);
	}

	/**
	 * Splits a full path into the directory and filename
	 * 
	 * @param path
	 * @return directory and filename
	 */
	public static String[] decodePath(String path)
	{
		String[] result = new String[2];
		if (path == null)
			path = "";
		int i = path.lastIndexOf('/');
		if (i == -1)
			i = path.lastIndexOf('\\');
		if (i > 0)
		{
			result[0] = path.substring(0, i + 1);
			result[1] = path.substring(i + 1);
		}
		else
		{
			result[0] = OpenDialog.getDefaultDirectory();
			result[1] = path;
		}
		return result;
	}
}
