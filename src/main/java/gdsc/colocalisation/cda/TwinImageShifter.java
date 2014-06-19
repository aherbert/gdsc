package gdsc.colocalisation.cda;

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

import ij.process.ImageProcessor;

import java.util.ArrayList;

public class TwinImageShifter
{
	private ImageProcessor imageIP;
	private ImageProcessor image2IP;
	private ImageProcessor roiIP;
	private ImageProcessor resultIP;
	private ImageProcessor result2IP;
	private int xShift = 0;
	private int yShift = 0;
	private int w;
	private int h;
	private ArrayList<ArrayList<Integer>> horizontalROI;
	private ArrayList<ArrayList<Integer>> verticalROI;

	// Used as working space
	private int[] t1 = null, t2 = null, t3 = null, t4 = null;

	public TwinImageShifter(ImageProcessor imageIP, ImageProcessor image2IP, ImageProcessor roiIP)
	{
		this.imageIP = imageIP;
		this.image2IP = image2IP;
		this.roiIP = roiIP;

		setup();
	}

	private void setup()
	{
		this.w = this.imageIP.getWidth();
		this.h = this.imageIP.getHeight();

		if (image2IP.getWidth() != w || image2IP.getHeight() != h)
		{
			throw new RuntimeException("The first and second channel image dimensions do not match");
		}
		if (roiIP != null)
		{
			if (roiIP.getWidth() != w || roiIP.getHeight() != h)
			{
				throw new RuntimeException("The channel image and confined image dimensions do not match");
			}
		}

		buildHorizontalROIArrays();
		buildVerticalROIArrays();

		createTempArrays();
	}

	private void buildHorizontalROIArrays()
	{
		this.horizontalROI = new ArrayList<ArrayList<Integer>>(h);

		for (int y = 0; y < h; ++y)
		{
			ArrayList<Integer> sites = new ArrayList<Integer>();

			if (roiIP != null)
			{
				for (int x = 0, index = y * roiIP.getWidth(); x < w; x++, index++)
				{
					if (roiIP.get(index) != 0)
					{
						sites.add(x);
					}
				}
			}
			else
			{
				for (int x = 0; x < w; x++)
				{
					sites.add(x);
				}
			}

			// This is the array for height position 'y'
			horizontalROI.add(sites);
		}
	}

	private void buildVerticalROIArrays()
	{
		this.verticalROI = new ArrayList<ArrayList<Integer>>(w);

		for (int x = 0; x < w; ++x)
		{
			ArrayList<Integer> sites = new ArrayList<Integer>();

			if (roiIP != null)
			{
				for (int y = 0; y < h; ++y)
				{
					if (roiIP.get(x, y) != 0)
					{
						sites.add(y);
					}
				}
			}
			else
			{
				for (int y = 0; y < h; ++y)
				{
					sites.add(y);
				}
			}

			// This is the array for width position 'x'
			verticalROI.add(sites);
		}
	}

	private void createTempArrays()
	{
		int max = 0;
		for (ArrayList<Integer> list : horizontalROI)
			if (max < list.size())
				max = list.size();
		for (ArrayList<Integer> list : verticalROI)
			if (max < list.size())
				max = list.size();
		t1 = new int[max];
		t2 = new int[max];
		t3 = new int[max];
		t4 = new int[max];
	}

	public void run()
	{
		// Duplicate the image to ensure the same return type. 
		// This will ensure get and put pixel sets the correct value.
		this.resultIP = imageIP.duplicate();
		this.result2IP = image2IP.duplicate();

		// shift and wrap the pixel values in the X-direction
		// (stores the result in the resultImage)
		shiftX();

		// Shift and wrap the pixel values in the Y-direction.
		// (stores the result in the resultImage)
		shiftY();
	}

	private void shiftX()
	{
		for (int y = 0; y < h; ++y)
		{
			// Find all the locations in this strip that need to be shifted
			ArrayList<Integer> sites = horizontalROI.get(y);

			if (sites.isEmpty())
			{
				continue;
			}

			// Extract the values 
			int i = 0;
			int index = y * resultIP.getWidth();
			for (int x : sites)
			{
				t1[i] = resultIP.get(index + x);
				t2[i] = result2IP.get(index + x);
				i++;
			}

			// Perform a shift
			rotateArrays(t1, t2, t3, t4, xShift, sites.size());

			// Write back the values
			i = 0;
			for (int x : sites)
			{
				resultIP.set(index + x, t3[i]);
				result2IP.set(index + x, t4[i]);
				i++;
			}
		}
	}

	private void shiftY()
	{
		for (int x = 0; x < w; ++x)
		{
			// Find all the locations in this strip that need to be shifted
			ArrayList<Integer> sites = verticalROI.get(x);

			if (sites.isEmpty())
			{
				continue;
			}

			// Extract the values 
			int i = 0;
			for (int y : sites)
			{
				int index = y * resultIP.getWidth() + x;
				t1[i] = resultIP.get(index);
				t2[i] = result2IP.get(index);
				i++;
			}

			// Perform a shift
			rotateArrays(t1, t2, t3, t4, yShift, sites.size());

			// Write back the values
			i = 0;
			for (int y : sites)
			{
				int index = y * resultIP.getWidth() + x;
				resultIP.set(index, t3[i]);
				result2IP.set(index, t4[i]);
				i++;
			}
		}
	}

	public void setShiftX(int x)
	{
		this.xShift = x;
	}

	public void setShiftY(int y)
	{
		this.yShift = y;
	}

	public void setShift(int x, int y)
	{
		this.xShift = x;
		this.yShift = y;
	}

	public ImageProcessor getResultImage()
	{
		return this.resultIP;
	}

	public ImageProcessor getResultImage2()
	{
		return this.result2IP;
	}

	private void rotateArrays(int[] array1, int[] array2, int[] array3, int[] array4, int shift, int size)
	{
		while (shift < 0)
		{
			shift += size;
		}

		for (int i = 0; i < size; ++i)
		{
			array3[(i + shift) % size] = array1[i];
			array4[(i + shift) % size] = array2[i];
		}
	}
}
