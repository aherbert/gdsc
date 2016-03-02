package gdsc.threshold;

import gdsc.UsageTracker;

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

import gdsc.utils.ImageJHelper;
import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.WindowManager;
import ij.gui.GenericDialog;
import ij.plugin.filter.PlugInFilter;
import ij.process.ImageProcessor;

import java.util.ArrayList;

/**
 * Create a mask from a source image and apply it to a target image. All pixels outside the mask will be set to zero.
 * The mask can be created using: an existing mask; thresholding or the minimum display value. The source image for the
 * mask can be a different image but the dimensions must match.
 */
public class ApplyMask implements PlugInFilter
{
	private static final String TITLE = "Apply Mask"; 
	
	private static String selectedImage = "";
	private static int selectedOption = MaskCreater.OPTION_MASK;
	private static String selectedThresholdMethod = "Otsu";
	private static int selectedChannel = 0;
	private static int selectedSlice = 0;
	private static int selectedFrame = 0;

	private ImagePlus imp;
	private ImagePlus maskImp;
	private int option;
	private String thresholdMethod;
	private int channel = 0;
	private int slice = 0;
	private int frame = 0;

	/* (non-Javadoc)
	 * @see ij.plugin.filter.PlugInFilter#setup(java.lang.String, ij.ImagePlus)
	 */
	public int setup(String arg, ImagePlus imp)
	{
		UsageTracker.recordPlugin(this.getClass(), arg);
		
		if (imp == null)
		{
			IJ.noImage();
			return DONE;
		}
		this.imp = imp;
		if (showDialog())
		{
			applyMask();
		}
		return DONE; 
	}

	/* (non-Javadoc)
	 * @see ij.plugin.filter.PlugInFilter#run(ij.process.ImageProcessor)
	 */
	public void run(ImageProcessor ip)
	{
		// All process already done
	}
	
	private boolean showDialog()
	{
		String sourceImage = "(Use target)";
		ArrayList<String> imageList = new ArrayList<String>();
		imageList.add(sourceImage);

		for (int id : ImageJHelper.getIDList())
		{
			ImagePlus imp = WindowManager.getImage(id);
			if (imp != null)
			{
				imageList.add(imp.getTitle());
			}
		}

		GenericDialog gd = new GenericDialog(TITLE);
		gd.addMessage("Create a mask from a source image and apply it.\nPixels outside the mask will be set to zero.");
		gd.addChoice("Mask_Image", imageList.toArray(new String[0]), selectedImage);
		gd.addChoice("Option", MaskCreater.options, MaskCreater.options[selectedOption]);
		gd.addChoice("Threshold_Method", Auto_Threshold.methods, selectedThresholdMethod);
		gd.addNumericField("Channel", selectedChannel, 0);
		gd.addNumericField("Slice", selectedSlice, 0);
		gd.addNumericField("Frame", selectedFrame, 0);
		gd.addHelp(gdsc.help.URL.UTILITY);
		gd.showDialog();

		if (gd.wasCanceled())
			return false;

		selectedImage = gd.getNextChoice();
		selectedOption = gd.getNextChoiceIndex();
		selectedThresholdMethod = gd.getNextChoice();
		selectedChannel = (int) gd.getNextNumber();
		selectedSlice = (int) gd.getNextNumber();
		selectedFrame = (int) gd.getNextNumber();

		setMaskImp(getImp());
		if (!selectedImage.equals(sourceImage))
			setMaskImp(WindowManager.getImage(selectedImage));
		setOption(selectedOption);
		setThresholdMethod(selectedThresholdMethod);
		setChannel(selectedChannel);
		setSlice(selectedSlice);
		setFrame(selectedFrame);

		return true;
	}

	public ApplyMask()
	{
		init(null, MaskCreater.OPTION_MASK);
	}

	public ApplyMask(ImagePlus imp)
	{
		init(imp, MaskCreater.OPTION_MASK);
	}

	public ApplyMask(ImagePlus imp, int option)
	{
		init(imp, option);
	}

	private void init(ImagePlus imp, int option)
	{
		this.imp = imp;
		this.option = option;
	}

	/**
	 * Create a mask from a source image and apply it to a target image. All pixels outside the mask will be set to zero.
	 */
	public void applyMask()
	{
		if (imp == null)
			return;

		MaskCreater mc = new MaskCreater();
		mc.setImp(maskImp);
		mc.setOption(selectedOption);
		mc.setThresholdMethod(selectedThresholdMethod);
		mc.setChannel(selectedChannel);
		mc.setSlice(selectedSlice);
		mc.setFrame(selectedFrame);
		maskImp = mc.createMask(); 

		// Check the mask has the correct dimensions
		if (maskImp == null)
		{
			IJ.error(TITLE, "No mask calculated");
			return;
		}
		if (imp.getWidth() != maskImp.getWidth() || imp.getHeight() != maskImp.getHeight())
		{
			IJ.error(TITLE, "Calculated mask does not match the target image dimensions");
			return;
		}
		
		// Apply the mask to the correct stack dimensions
		int[] dimensions = imp.getDimensions();
		int[] channels = createArray(dimensions[2]);
		int[] slices = createArray(dimensions[3]);
		int[] frames = createArray(dimensions[4]);
		
		ImageStack imageStack = imp.getStack();
		ImageStack maskStack = maskImp.getStack();
		
		for (int frame : frames)
			for (int slice : slices)
				for (int channel : channels)
				{
					ImageProcessor ip = imageStack.getProcessor(imp.getStackIndex(channel, slice, frame));
					
					// getStackIndex will clip to the mask dimensions  
					ImageProcessor maskIp = maskStack.getProcessor(maskImp.getStackIndex(channel, slice, frame));
					
					for (int i = maskIp.getPixelCount(); i-- > 0;)
					{
						if (maskIp.get(i) == 0)
						{
							ip.set(i, 0);
						}
					}
				}
		
		imp.updateAndDraw();
	}

	private int[] createArray(int total)
	{
		int[] array = new int[total];
		for (int i = 0; i < array.length; i++)
			array[i] = i + 1;
		return array;
	}
	
	/**
	 * @param imp
	 *            the target image for the masking
	 */
	public void setImp(ImagePlus imp)
	{
		this.imp = imp;
	}

	/**
	 * @return the target image for the masking
	 */
	public ImagePlus getImp()
	{
		return imp;
	}

	/**
	 * @param imp
	 *            the source image for the mask generation
	 */
	public void setMaskImp(ImagePlus imp)
	{
		this.maskImp = imp;
	}

	/**
	 * @return the source image for the mask generation
	 */
	public ImagePlus getMaskImp()
	{
		return maskImp;
	}

	/**
	 * @param option
	 *            the option for defining the mask
	 */
	public void setOption(int option)
	{
		this.option = option;
	}

	/**
	 * @return the option for defining the mask
	 */
	public int getOption()
	{
		return option;
	}

	/**
	 * @param thresholdMethod
	 *            the thresholdMethod to set
	 */
	public void setThresholdMethod(String thresholdMethod)
	{
		this.thresholdMethod = thresholdMethod;
	}

	/**
	 * @return the thresholdMethod
	 */
	public String getThresholdMethod()
	{
		return thresholdMethod;
	}

	/**
	 * @param channel
	 *            the channel to set
	 */
	public void setChannel(int channel)
	{
		this.channel = channel;
	}

	/**
	 * @return the channel
	 */
	public int getChannel()
	{
		return channel;
	}

	/**
	 * @param frame
	 *            the frame to set
	 */
	public void setFrame(int frame)
	{
		this.frame = frame;
	}

	/**
	 * @return the frame
	 */
	public int getFrame()
	{
		return frame;
	}

	/**
	 * @param slice
	 *            the slice to set
	 */
	public void setSlice(int slice)
	{
		this.slice = slice;
	}

	/**
	 * @return the slice
	 */
	public int getSlice()
	{
		return slice;
	}
}
