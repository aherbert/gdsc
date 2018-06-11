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

import ij.ImagePlus;
import ij.ImageStack;

public class TwinStackShifter
{
	private ImageStack result1;
	private ImageStack result2;
	private int xShift = 0;
	private int yShift = 0;
	private int w;
	private int h;
	private int s;
	private TwinImageShifter[] imageShifters;

	public TwinStackShifter(ImagePlus imageImp, ImagePlus image2Imp, ImagePlus roiImp)
	{
		initialise(imageImp.getImageStack(), image2Imp.getImageStack(),
				(roiImp != null) ? roiImp.getImageStack() : null);
	}

	public TwinStackShifter(ImageStack imageStack, ImageStack image2Stack, ImageStack roiStack)
	{
		initialise(imageStack, image2Stack, roiStack);
	}

	private void initialise(ImageStack s1, ImageStack s2, ImageStack s3)
	{
		w = s1.getWidth();
		h = s1.getHeight();
		s = s1.getSize();

		if (s2.getWidth() != w || s2.getHeight() != h || s2.getSize() != s)
		{
			throw new RuntimeException("The first and second stack dimensions do not match");
		}
		if (s3 != null)
		{
			if (s3.getWidth() != w || s3.getHeight() != h || s3.getSize() != s)
			{
				throw new RuntimeException("The first and third stack dimensions do not match");
			}
		}

		imageShifters = new TwinImageShifter[s];

		for (int n = 1; n <= s; n++)
		{
			imageShifters[n - 1] = new TwinImageShifter(s1.getProcessor(n), s2.getProcessor(n),
					(s3 != null) ? s3.getProcessor(n) : null);
		}
	}

	public void run(int x, int y)
	{
		setShift(x, y);
		run();
	}

	public void run()
	{
		result1 = new ImageStack(w, h, s);
		result2 = new ImageStack(w, h, s);

		for (int n = 1; n <= s; n++)
		{
			TwinImageShifter shifter = imageShifters[n - 1];
			shifter.setShift(xShift, yShift);
			shifter.run();
			result1.setPixels(shifter.getResultImage().getPixels(), n);
			result2.setPixels(shifter.getResultImage2().getPixels(), n);
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

	public ImagePlus getResultImage()
	{
		return new ImagePlus(null, result1);
	}

	public ImagePlus getResultImage2()
	{
		return new ImagePlus(null, result2);
	}

	public ImageStack getResultStack()
	{
		return this.result1;
	}

	public ImageStack getResultStack2()
	{
		return this.result2;
	}
}
