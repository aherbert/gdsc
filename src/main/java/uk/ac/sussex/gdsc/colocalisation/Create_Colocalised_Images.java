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
package uk.ac.sussex.gdsc.colocalisation;

import java.util.Random;

import ij.IJ;
import ij.ImagePlus;
import ij.gui.GenericDialog;
import ij.plugin.PlugIn;
import ij.plugin.filter.GaussianBlur;
import ij.process.ByteProcessor;
import ij.process.ImageProcessor;
import ij.process.ShortProcessor;
import uk.ac.sussex.gdsc.UsageTracker;

/**
 * Create some dummy images to test for colocalisation
 */
public class Create_Colocalised_Images implements PlugIn
{
	private static final String TITLE = "Colocalisaed Images";
	private ByteProcessor roi;
	private final int background = 0;
	private final int foreground = 255;

	private static int seed = 30051977;
	private static int sequenceNumber = 0;
	private static int bitDepth = 0;
	private static boolean createMasks = false;
	private int CHANNEL_MAX;
	private int CHANNEL_MIN;
	private final int NUMBER_OF_POINTS = 20;

	private final int width = 256;
	private final int height = 256;
	private final int padding = 40;
	private final int minSize = 5;
	private final int maxExpansionSize = 15;

	@Override
	public void run(String arg)
	{
		UsageTracker.recordPlugin(this.getClass(), arg);

		if (!getBitDepth())
			return;

		sequenceNumber++;

		createRoi();

		createColorChannel("A" + sequenceNumber);
		createColorChannel("B" + sequenceNumber);

		if (createMasks)
		{
			final ImagePlus imRoi = new ImagePlus("roi" + sequenceNumber, roi);
			imRoi.updateAndDraw();
			imRoi.show();
		}
		IJ.showProgress(1);
	}

	private boolean getBitDepth()
	{
		final GenericDialog param = new GenericDialog(TITLE, IJ.getInstance());
		final String bitDepthChoice[] = { "8bit", "12bit", "16bit" };// bit depth of images
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
			for (int y = 0; y < height; y++)
				roi.set(x, y, foreground);
	}

	private void createColorChannel(String title)
	{
		final ImageProcessor cp = getImageProcessor();
		ByteProcessor bp = null;

		final Random rng = new Random(seed++);

		for (int point = 0; point < NUMBER_OF_POINTS; point++)
		{
			final int x = rng.nextInt(width - 2 * padding) + padding;
			final int y = minSize + maxExpansionSize + rng.nextInt(height - 2 * (minSize + maxExpansionSize));

			final int xSize = minSize + rng.nextInt(maxExpansionSize);
			final int ySize = minSize + rng.nextInt(maxExpansionSize);

			final int value = rng.nextInt(CHANNEL_MAX - CHANNEL_MIN) + CHANNEL_MIN;
			cp.set(x, y, value);

			for (int i = -xSize; i < xSize; i++)
				for (int j = -ySize; j < ySize; j++)
					cp.set(x + i, y + j, value);
		}

		final GaussianBlur gb = new GaussianBlur();
		gb.blurGaussian(cp, 20, 20, 0.02);

		// Get all values above zero as the ROI
		if (createMasks)
		{
			bp = new ByteProcessor(width, height);
			bp.add(background);

			for (int i = cp.getPixelCount(); i-- > 0;)
				if (cp.get(i) > CHANNEL_MIN)
				{
					bp.set(i, foreground);
					roi.set(i, foreground);
				}
		}

		// Add some noise to the image
		cp.noise(CHANNEL_MAX / 16);

		// Show the images
		final ImagePlus im = new ImagePlus(title, cp);
		im.show();

		if (bp != null)
		{
			final ImagePlus imRoi = new ImagePlus(title + "roi" + sequenceNumber, bp);
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
