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
package gdsc.colocalisation.cda;

import java.util.Arrays;

import ij.process.ImageProcessor;

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
	private int[][] horizontalROI;
	private int[][] verticalROI;

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
		this.horizontalROI = new int[h][];

		int[] sites = new int[w];

		if (roiIP != null)
		{
			for (int y = 0; y < h; ++y)
			{
				int size = 0;
				for (int x = 0, index = y * roiIP.getWidth(); x < w; x++, index++)
				{
					if (roiIP.get(index) != 0)
					{
						sites[size++] = x;
					}
				}

				// This is the array for height position 'y'
				horizontalROI[y] = Arrays.copyOf(sites, size);
			}
		}
		else
		{
			for (int x = 0; x < w; x++)
			{
				sites[x] = x;
			}

			for (int y = 0; y < h; ++y)
			{
				// This is the array for height position 'y'
				horizontalROI[y] = sites;
			}
		}
	}

	private void buildVerticalROIArrays()
	{
		this.verticalROI = new int[w][];

		int[] sites = new int[h];

		if (roiIP != null)
		{
			for (int x = 0; x < w; ++x)
			{
				int size = 0;
				for (int y = 0; y < h; ++y)
				{
					if (roiIP.get(x, y) != 0)
					{
						sites[size++] = y;
					}
				}

				// This is the array for width position 'x'
				verticalROI[x] = Arrays.copyOf(sites, size);
			}
		}
		else
		{
			for (int y = 0; y < h; ++y)
			{
				sites[y] = y;
			}

			for (int x = 0; x < w; ++x)
			{
				// This is the array for width position 'x'
				verticalROI[x] = sites;
			}
		}
	}

	private void createTempArrays()
	{
		int max = 0;
		for (int[] list : horizontalROI)
			if (max < list.length)
				max = list.length;
		for (int[] list : verticalROI)
			if (max < list.length)
				max = list.length;
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
		if (xShift == 0)
			return;

		for (int y = 0; y < h; ++y)
		{
			// Find all the locations in this strip that need to be shifted
			int[] sites = horizontalROI[y];

			if (sites.length == 0)
			{
				continue;
			}

			// Extract the values 
			int index = y * resultIP.getWidth();
			for (int i = 0; i < sites.length; i++)
			{
				t1[i] = resultIP.get(index + sites[i]);
				t2[i] = result2IP.get(index + sites[i]);
			}

			// Perform a shift
			rotateArrays(t1, t2, t3, t4, xShift, sites.length);

			// Write back the values
			for (int i = 0; i < sites.length; i++)
			{
				resultIP.set(index + sites[i], t3[i]);
				result2IP.set(index + sites[i], t4[i]);
			}
		}
	}

	private void shiftY()
	{
		if (yShift == 0)
			return;

		for (int x = 0; x < w; ++x)
		{
			// Find all the locations in this strip that need to be shifted
			int[] sites = verticalROI[x];

			if (sites.length == 0)
			{
				continue;
			}

			// Extract the values 
			for (int i = 0; i < sites.length; i++)
			{
				int index = sites[i] * resultIP.getWidth() + x;
				t1[i] = resultIP.get(index);
				t2[i] = result2IP.get(index);
			}

			// Perform a shift
			rotateArrays(t1, t2, t3, t4, yShift, sites.length);

			// Write back the values
			for (int i = 0; i < sites.length; i++)
			{
				int index = sites[i] * resultIP.getWidth() + x;
				resultIP.set(index, t3[i]);
				result2IP.set(index, t4[i]);
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
