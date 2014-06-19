package gdsc.colocalisation;

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

import java.util.Random;

import ij.IJ;
import ij.ImagePlus;
import ij.gui.GenericDialog;
import ij.plugin.PlugIn;
import ij.plugin.filter.GaussianBlur;
import ij.process.ByteProcessor;
import ij.process.ImageProcessor;
import ij.process.ShortProcessor;

/**
 * Create some dummy images to test for colocalisation 
 */
public class Create_Colocalised_Images implements PlugIn
{
	ByteProcessor roi;
	int background = 0;
	int foreground = 255;

	static int seed = 30051977;
	static int sequenceNumber = 0;
	static int bitDepth = 0;
	static boolean createMasks = false;
	int CHANNEL_MAX;
	int CHANNEL_MIN;
	int NUMBER_OF_POINTS = 20;

	int width = 256;
	int height = 256;
	int padding = 40;
	int minSize = 5;
	int maxExpansionSize = 15;

	public void run(String arg)
	{
		if (!getBitDepth())
			return;

		sequenceNumber++;

		createRoi();

		createColorChannel("A" + sequenceNumber);
		createColorChannel("B" + sequenceNumber);

		if (createMasks)
		{
			ImagePlus imRoi = new ImagePlus("roi" + sequenceNumber, roi);
			imRoi.updateAndDraw();
			imRoi.show();
		}
		IJ.showProgress(1);
	}

	private boolean getBitDepth()
	{
		GenericDialog param = new GenericDialog("Colocalisaed Images", IJ.getInstance());
		String bitDepthChoice[] = { "8bit", "12bit", "16bit" };// bit depth of images
		param.addChoice("Create ...", bitDepthChoice, bitDepthChoice[bitDepth]);
		param.addCheckbox("Create masks", createMasks);
		param.showDialog();

		if (param.wasCanceled())
			return false;

		bitDepth = param.getNextChoiceIndex();
		switch (bitDepth)
		{
			case 2: // 16-bit
				CHANNEL_MAX = Short.MAX_VALUE;
				CHANNEL_MIN = 640;
				break;

			case 1: // 12-bit
				CHANNEL_MAX = 4095;
				CHANNEL_MIN = 80;
				break;

			default: // 8-bit
				CHANNEL_MAX = 255;
				CHANNEL_MIN = 10;
		}

		createMasks = param.getNextBoolean();

		return true;
	}

	private void createRoi()
	{
		if (!createMasks)
			return;

		roi = new ByteProcessor(width, height);
		roi.add(background);

		for (int x = padding; x < width - padding; x++)
		{
			for (int y = 0; y < height; y++)
			{
				roi.set(x, y, foreground);
			}
		}
	}

	private void createColorChannel(String title)
	{
		ImageProcessor cp = getImageProcessor();
		ByteProcessor bp = null;

		Random rng = new Random(seed++);

		for (int point = 0; point < NUMBER_OF_POINTS; point++)
		{
			int x = rng.nextInt(width - 2 * padding) + padding;
			int y = minSize + maxExpansionSize + rng.nextInt(height - 2 * (minSize + maxExpansionSize));

			int xSize = minSize + rng.nextInt(maxExpansionSize);
			int ySize = minSize + rng.nextInt(maxExpansionSize);

			int value = rng.nextInt(CHANNEL_MAX - CHANNEL_MIN) + CHANNEL_MIN;
			cp.set(x, y, value);

			for (int i = -xSize; i < xSize; i++)
			{
				for (int j = -ySize; j < ySize; j++)
				{
					cp.set(x + i, y + j, value);
				}
			}
		}

		GaussianBlur gb = new GaussianBlur();
		gb.setNPasses(3);
		gb.blur(cp, 20);

		// Get all values above zero as the ROI
		if (createMasks)
		{
			bp = new ByteProcessor(width, height);
			bp.add(background);

			for (int i = cp.getPixelCount(); i-- > 0;)
			{
				if (cp.get(i) > CHANNEL_MIN)
				{
					bp.set(i, foreground);
					roi.set(i, foreground);
				}
			}
		}

		// Add some noise to the image
		cp.noise(CHANNEL_MAX / 16);

		// Show the images
		ImagePlus im = new ImagePlus(title, cp);
		im.show();

		if (bp != null)
		{
			ImagePlus imRoi = new ImagePlus(title + "roi" + sequenceNumber, bp);
			imRoi.show();
		}
	}

	private ImageProcessor getImageProcessor()
	{
		switch (bitDepth)
		{
			case 0: // 8-bit
				return new ByteProcessor(width, height);

			default: // 12 or 16-bit
				return new ShortProcessor(width, height);
		}
	}
}
