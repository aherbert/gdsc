package gdsc.foci;

/*----------------------------------------------------------------------------- 
 * GDSC Plugins for ImageJ
 * 
 * Copyright (C) 2015 Alex Herbert
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
import ij.WindowManager;
import ij.gui.GenericDialog;
import ij.plugin.PlugIn;
import ij.process.ImageProcessor;
import ij.process.LUT;
import ij.process.ShortProcessor;

import java.util.ArrayList;

/**
 * Compares two masks created using the Mask Segregator with pixels of AB and A'B' and creates a new mask with pixels of
 * AA' AB' BA' BB'.
 */
public class DoubleMaskSegregator implements PlugIn
{
	private static String TITLE = "Double Mask Segregator";

	private static String title1 = "";
	private static String title2 = "";
	private static boolean applyLUT = false;
	private static boolean overlayOutline = true;

	private ImagePlus imp1, imp2;

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

		run();
	}

	private boolean showDialog()
	{
		String[] items = ImageJHelper.getImageList(ImageJHelper.GREY_8_16, null);

		if (items.length < 2)
		{
			IJ.error(TITLE, "Require 2 input masks (8/16-bit)");
			return false;
		}

		GenericDialog gd = new GenericDialog(TITLE);

		if (title1.equalsIgnoreCase(title2))
			title2 = (title1.equalsIgnoreCase(items[0]) || title1 == "") ? items[1] : items[0];

		gd.addMessage("Find the classes in each mask using continuous mask values\nand create an all-vs-all output combination mask");
		gd.addChoice("Input_1", items, title1);
		gd.addChoice("Input_2", items, title2);
		gd.addCheckbox("Apply_LUT", applyLUT);
		gd.addCheckbox("Overlay_outline", overlayOutline);

		gd.addHelp(gdsc.help.URL.FIND_FOCI);
		gd.showDialog();

		if (gd.wasCanceled())
			return false;

		title1 = gd.getNextChoice();
		title2 = gd.getNextChoice();
		applyLUT = gd.getNextBoolean();
		overlayOutline = gd.getNextBoolean();

		imp1 = WindowManager.getImage(title1);
		if (imp1 == null)
			return false;
		imp2 = WindowManager.getImage(title2);
		if (imp2 == null)
			return false;
		if (imp1.getWidth() != imp2.getWidth() || imp1.getHeight() != imp2.getHeight())
		{
			IJ.error(TITLE, "Input masks must be the same size");
			return false;
		}

		return true;
	}

	private void run()
	{
		// Convert to pixel arrays
		final int[] i1 = getPixels(imp1.getProcessor());
		final int[] i2 = getPixels(imp2.getProcessor());

		// Check the same pixels are non zero
		for (int i = 0; i < i1.length; i++)
		{
			if (i1[i] == 0 && i2[i] != 0 || i1[i] != 0 && i2[i] == 0)
			{
				IJ.error(TITLE, "Masks must have the same non-zero pixels");
				return;
			}
		}

		// Find the continuous blocks of incrementing pixel values
		final ArrayList<int[]> b1 = findBlocks(i1);
		final ArrayList<int[]> b2 = findBlocks(i2);

		if (b1.isEmpty() || b2.isEmpty())
		{
			// This should only happen when both are empty since the check above ensures one cannot be empty
			// without the other
			IJ.error(TITLE, String.format("Unable to combine %d and %d classes", b1.size(), b2.size()));
			return;
		}

		// Find the block size required to separate blocks
		int max = 0;
		for (int[] b : b1)
		{
			//System.out.printf("B1 : %d - %d\n", b[0], b[1]);
			int range = b[1] - b[0];
			if (max < range)
				max = range;
		}
		for (int[] b : b2)
		{
			//System.out.printf("B2 : %d - %d\n", b[0], b[1]);
			int range = b[1] - b[0];
			if (max < range)
				max = range;
		}
		final int blockSize = MaskSegregator.getBonus(max + 1);

		if ((b1.size() * b2.size() - 1) * blockSize + max > 65535)
		{
			IJ.error(TITLE, String.format("Unable to create %d classes with a separation of %d", b1.size() * b2.size(),
					blockSize));
			return;
		}

		// Create a map to find the block for each pixel value
		final int[] map2 = createMap(b2);
		final int[] map1 = createMap(b1);
		final int[] offset1 = createOffset(b1);
		final int size1 = b1.size();

		// Combine blocks to new output blocks
		final int[] out = new int[i1.length];
		final int[] h = new int[65536];
		for (int i = 0; i < i1.length; i++)
		{
			if (i1[i] != 0 && i2[i] != 0)
			{
				final int block1 = map1[i1[i]];
				final int block2 = map2[i2[i]];
				final int newBlock = block2 * size1 + block1;
				final int base = newBlock * blockSize;
				// Initially use the object value from mask 1. Which mask to use is arbitrary as  
				// mask 2 will have the same non-zero pixels, just different object numbers and
				// the numbers are later re-mapped
				out[i] = base + i1[i] - offset1[block1];
				h[out[i]]++;
			}
		}

		// Re-map the object values to be continuous within blocks
		int[] object = new int[size1 * b2.size()];
		for (int i = 0; i < h.length; i++)
		{
			if (h[i] != 0)
			{
				// Find the block this object is in
				int block = i / blockSize;
				// Increment the object count and re-map the value
				object[block]++;
				h[i] = block * blockSize + object[block];
			}
		}

		// Display
		ShortProcessor sp = new ShortProcessor(imp1.getWidth(), imp1.getHeight());
		for (int i = 0; i < out.length; i++)
			sp.set(i, h[out[i]]);
		if (applyLUT)
			sp.setLut(createLUT());
		ImagePlus imp = ImageJHelper.display(TITLE, sp);

		// Optionally outline each object
		if (overlayOutline)
			MaskSegregator.addOutline(imp);
	}

	private int[] getPixels(ImageProcessor ip)
	{
		int[] pixels = new int[ip.getPixelCount()];
		for (int i = 0; i < pixels.length; i++)
			pixels[i] = ip.get(i);
		return pixels;
	}

	private ArrayList<int[]> findBlocks(int[] image)
	{
		// Find unique values
		int max = 0;
		for (int i : image)
			if (max < i)
				max = i;
		// Histogram
		int[] h = new int[max + 1];
		for (int i : image)
			h[i]++;

		// Find contiguous blocks 
		ArrayList<int[]> blocks = new ArrayList<int[]>();
		int min = 0;
		for (int i = 1; i < h.length; i++)
		{
			if (h[i] != 0)
			{
				if (min == 0)
					min = i;
				max = i;
			}
			else
			{
				if (min != 0)
					blocks.add(new int[] { min, max });
				min = 0;
			}
		}
		if (min != 0)
			blocks.add(new int[] { min, max });

		return blocks;
	}

	private int[] createMap(ArrayList<int[]> blocks)
	{
		int max = blocks.get(blocks.size() - 1)[1];
		int[] map = new int[max + 1];
		for (int b = 0; b < blocks.size(); b++)
		{
			int[] block = blocks.get(b);
			for (int i = block[0]; i <= block[1]; i++)
				map[i] = b;
		}
		return map;
	}

	private int[] createOffset(ArrayList<int[]> blocks)
	{
		int[] offset = new int[blocks.size()];
		for (int b = 0; b < blocks.size(); b++)
		{
			offset[b] = blocks.get(b)[0] - 1;
		}
		return offset;
	}

	/**
	 * Build a custom LUT that helps show the classes
	 * 
	 * @return
	 */
	private LUT createLUT()
	{
		byte[] reds = new byte[256];
		byte[] greens = new byte[256];
		byte[] blues = new byte[256];
		int nColors = ice(reds, greens, blues);
		if (nColors < 256)
			interpolateWithZero(reds, greens, blues, nColors);
		return new LUT(reds, greens, blues);
	}

	/**
	 * Copied from ij.plugin.LutLoader
	 * 
	 * @param reds
	 * @param greens
	 * @param blues
	 * @return
	 */
	private int ice(byte[] reds, byte[] greens, byte[] blues)
	{
		int[] r = { 0, 0, 0, 0, 0, 0, 19, 29, 50, 48, 79, 112, 134, 158, 186, 201, 217, 229, 242, 250, 250, 250, 250,
				251, 250, 250, 250, 250, 251, 251, 243, 230 };
		int[] g = { 156, 165, 176, 184, 190, 196, 193, 184, 171, 162, 146, 125, 107, 93, 81, 87, 92, 97, 95, 93, 93,
				90, 85, 69, 64, 54, 47, 35, 19, 0, 4, 0 };
		int[] b = { 140, 147, 158, 166, 170, 176, 209, 220, 234, 225, 236, 246, 250, 251, 250, 250, 245, 230, 230, 222,
				202, 180, 163, 142, 123, 114, 106, 94, 84, 64, 26, 27 };
		for (int i = 0; i < r.length; i++)
		{
			reds[i] = (byte) r[i];
			greens[i] = (byte) g[i];
			blues[i] = (byte) b[i];
		}
		return r.length;
	}

	/**
	 * Copied from ij.plugin.LutLoader.
	 * Modified to set the first position to zero.
	 * 
	 * @param reds
	 * @param greens
	 * @param blues
	 * @param nColors
	 */
	private void interpolateWithZero(byte[] reds, byte[] greens, byte[] blues, int nColors)
	{
		byte[] r = new byte[nColors];
		byte[] g = new byte[nColors];
		byte[] b = new byte[nColors];
		System.arraycopy(reds, 0, r, 0, nColors);
		System.arraycopy(greens, 0, g, 0, nColors);
		System.arraycopy(blues, 0, b, 0, nColors);
		double scale = nColors / 255.0;
		int i1, i2;
		double fraction;
		reds[0] = greens[0] = blues[0] = 0;
		for (int i = 0; i < 255; i++)
		{
			i1 = (int) (i * scale);
			i2 = i1 + 1;
			if (i2 == nColors)
				i2 = nColors - 1;
			fraction = i * scale - i1;
			reds[i + 1] = (byte) ((1.0 - fraction) * (r[i1] & 255) + fraction * (r[i2] & 255));
			greens[i + 1] = (byte) ((1.0 - fraction) * (g[i1] & 255) + fraction * (g[i2] & 255));
			blues[i + 1] = (byte) ((1.0 - fraction) * (b[i1] & 255) + fraction * (b[i2] & 255));
		}
	}
}
