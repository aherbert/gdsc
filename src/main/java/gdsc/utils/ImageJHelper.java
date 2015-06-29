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

import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.Prefs;
import ij.WindowManager;
import ij.gui.Plot;
import ij.gui.PlotWindow;
import ij.io.DirectoryChooser;
import ij.io.OpenDialog;
import ij.plugin.HyperStackReducer;
import ij.plugin.ZProjector;
import ij.process.ImageProcessor;
import ij.text.TextWindow;

import java.awt.Frame;
import java.io.File;
import java.math.BigDecimal;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

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

	private static boolean newWindow = false;

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
	 * Open a file selection dialog using the given title (and optionally the default path)
	 * 
	 * @param title
	 *            The dialog title
	 * @param filename
	 *            The default path to start with
	 * @return The path (or null if the dialog is cancelled)
	 */
	public static String getFilename(String title, String filename)
	{
		String[] path = decodePath(filename);
		OpenDialog chooser = new OpenDialog(title, path[0], path[1]);
		if (chooser.getFileName() != null)
		{
			return chooser.getDirectory() + chooser.getFileName();
		}
		return null;
	}

	/**
	 * Open a directory selection dialog using the given title (and optionally the default directory)
	 * 
	 * @param title
	 *            The dialog title
	 * @param directory
	 *            The default directory to start in
	 * @return The directory (or null if the dialog is cancelled)
	 */
	public static String getDirectory(String title, String directory)
	{
		String defaultDir = OpenDialog.getDefaultDirectory();
		if (directory != null && directory.length() > 0)
			OpenDialog.setDefaultDirectory(directory);
		DirectoryChooser chooser = new DirectoryChooser(title);
		directory = chooser.getDirectory();
		OpenDialog.setDefaultDirectory(defaultDir);
		return directory;
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

	/**
	 * Waits for all threads to complete computation.
	 * 
	 * @param futures
	 */
	public static void waitForCompletion(List<Future<?>> futures)
	{
		try
		{
			for (Future<?> f : futures)
			{
				f.get();
			}
		}
		catch (ExecutionException ex)
		{
			ex.printStackTrace();
		}
		catch (InterruptedException e)
		{
			e.printStackTrace();
		}
	}

	/**
	 * Show the image. Replace a currently open image with the specified title or else create a new image.
	 * 
	 * @param title
	 * @param ip
	 * @return the
	 */
	public static ImagePlus display(String title, ImageProcessor ip)
	{
		newWindow = false;
		ImagePlus imp = WindowManager.getImage(title);
		if (imp == null)
		{
			imp = new ImagePlus(title, ip);
			imp.show();
			newWindow = true;
		}
		else
		{
			imp.setProcessor(ip);
		}
		return imp;
	}

	/**
	 * Show the image. Replace a currently open image with the specified title or else create a new image.
	 * 
	 * @param title
	 * @param slices
	 * @return the image
	 */
	public static ImagePlus display(String title, ImageStack slices)
	{
		newWindow = false;
		ImagePlus imp = WindowManager.getImage(title);
		if (imp == null)
		{
			imp = new ImagePlus(title, slices);
			imp.show();
			newWindow = true;
		}
		else
		{
			imp.setStack(slices);
		}
		return imp;
	}

	/**
	 * Show the plot. Replace a currently open plot with the specified title or else create a new plot window.
	 * 
	 * @param title
	 * @param plot
	 * @return the plot window
	 */
	public static PlotWindow display(String title, Plot plot)
	{
		newWindow = false;
		Frame plotWindow = null;
		int[] wList = WindowManager.getIDList();
		int len = wList != null ? wList.length : 0;
		for (int i = 0; i < len; i++)
		{
			ImagePlus imp = WindowManager.getImage(wList[i]);
			if (imp != null && imp.getWindow() instanceof PlotWindow)
			{
				if (imp.getTitle().equals(title))
				{
					plotWindow = imp.getWindow();
					break;
				}
			}
		}
		PlotWindow p;
		if (plotWindow == null)
		{
			p = plot.show();
			newWindow = true;
		}
		else
		{
			p = (PlotWindow) plotWindow;
			p.drawPlot(plot);
		}
		return p;
	}

	/**
	 * @return True is the last call to display created a new window
	 */
	public static boolean isNewWindow()
	{
		return newWindow;
	}

	/**
	 * Add the platform specific file separator character to the directory (if missing)
	 * 
	 * @param directory
	 * @return The directory
	 */
	public static String addFileSeparator(String directory)
	{
		if (directory.length() > 0 && !(directory.endsWith("/") || directory.endsWith("\\")))
			directory += Prefs.separator;
		return directory;
	}

	/**
	 * Return "s" if the size is not 1 otherwise returns an empty string. This can be used to add an s where necessary
	 * to adjectives:
	 * 
	 * <pre>
	 * System.out.printf(&quot;Created %s\n&quot;, Utils.pleural(n, &quot;thing&quot;));
	 * </pre>
	 * 
	 * @param n
	 *            The number of things
	 * @param name
	 *            The name of the thing
	 * @return "s" or empty string
	 */
	public static String pleural(int n, String name)
	{
		return n + " " + name + ((Math.abs(n) == 1) ? "" : "s");
	}
}
