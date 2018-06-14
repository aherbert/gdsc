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
package gdsc.utils;

import java.util.ArrayList;

import gdsc.UsageTracker;
import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.WindowManager;
import ij.plugin.filter.PlugInFilter;
import ij.process.ImageProcessor;

/**
 * Create an average of all the open stacks with the same dimensions and bit-depth as the active stack.
 */
public class Stack_Average implements PlugInFilter
{
	private ImagePlus imp;

	/*
	 * (non-Javadoc)
	 * 
	 * @see ij.plugin.filter.PlugInFilter#setup(java.lang.String, ij.ImagePlus)
	 */
	@Override
	public int setup(String arg, ImagePlus imp)
	{
		UsageTracker.recordPlugin(this.getClass(), arg);
		if (imp == null)
		{
			IJ.noImage();
			return DONE;
		}
		this.imp = imp;
		return DOES_8G + DOES_16 + DOES_32 + NO_CHANGES;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see ij.plugin.filter.PlugInFilter#run(ij.process.ImageProcessor)
	 */
	@Override
	public void run(ImageProcessor ip)
	{
		ArrayList<ImagePlus> images = getImages();

		int bufferSize = imp.getWidth() * imp.getHeight();
		int count = images.size();

		ImageStack result = createResult();

		// Add all the images - Process each stack slice individually
		for (int n = imp.getStackSize(); n > 0; n--)
		{
			// Sum all the images
			double[] sum = new double[bufferSize];
			for (ImagePlus imp2 : images)
			{
				ImageProcessor ip2 = imp2.getStack().getProcessor(n);
				for (int i = sum.length; i-- > 0;)
					sum[i] += ip2.get(i);
			}

			// Average
			ImageProcessor ip2 = result.getProcessor(n);
			for (int i = sum.length; i-- > 0;)
				ip2.set(i, (int) (sum[i] / count));
		}

		// Show result
		new ImagePlus("Stack Average", result).show();
	}

	private ArrayList<ImagePlus> getImages()
	{
		int[] dimensions = imp.getDimensions();
		int bitDepth = imp.getBitDepth();

		// Build a list of the images
		int[] wList = gdsc.core.ij.Utils.getIDList();

		ArrayList<ImagePlus> images = new ArrayList<ImagePlus>(wList.length);

		for (int i = 0; i < wList.length; i++)
		{
			ImagePlus imp2 = WindowManager.getImage(wList[i]);
			if (imp2 != null)
			{
				if (!imp2.getTitle().startsWith("Stack Average") && sameDimensions(dimensions, imp2.getDimensions()) &&
						bitDepth == imp2.getBitDepth())
				{
					images.add(imp2);
				}
			}
		}

		return images;
	}

	private boolean sameDimensions(int[] dimensions, int[] dimensions2)
	{
		for (int i = dimensions.length; i-- > 0;)
		{
			if (dimensions[i] != dimensions2[i])
			{
				return false;
			}
		}
		return true;
	}

	private ImageStack createResult()
	{
		int width = imp.getWidth();
		int height = imp.getHeight();

		ImageStack inStack = imp.getImageStack();
		ImageStack outStack = new ImageStack(width, height);

		for (int n = inStack.getSize(); n > 0; n--)
		{
			outStack.addSlice(null, inStack.getProcessor(n).createProcessor(width, height));
		}

		return outStack;
	}
}
