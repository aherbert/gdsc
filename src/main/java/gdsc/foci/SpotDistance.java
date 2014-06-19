package gdsc.foci;

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
import ij.gui.Overlay;
import ij.gui.Roi;
import ij.measure.Calibration;
import ij.plugin.MacroInstaller;
import ij.plugin.PlugIn;
import ij.plugin.filter.DifferenceOfGaussians;
import ij.plugin.filter.ThresholdToSelection;
import ij.process.ByteProcessor;
import ij.process.ImageProcessor;
import ij.process.ShortProcessor;
import ij.text.TextPanel;
import ij.text.TextWindow;

import java.awt.Color;
import java.awt.Point;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

/**
 * Output the distances between spots within a mask region.
 * <P>
 * For each mask region a Difference of Gaussians is performed to enhance spot features. The spots are then identified
 * and the distances between spots are calculated.
 * <p>
 * A mask can be defined using a freehand ROI or provided as an image. All pixels with the same non-zero value are used
 * to define a mask region. The image can thus be used to define multiple regions using pixel values 1,2,3, etc.
 */
public class SpotDistance implements PlugIn
{
	public static String FRAME_TITLE = "Spot Distance";
	private static TextWindow resultsWindow = null;
	private static TextWindow summaryWindow = null;

	private static int lastResultLineCount = 0;
	private static int lastSummaryLineCount = 0;
	private static boolean allowUndo = false;

	private static String maskImage = "";
	private static double smoothingSize = 1.5;
	private static double featureSize = 4.5;

	private static double stdDevAboveBackground = 3;
	private static int minPeakSize = 15;
	private static double minPeakHeight = 5;

	private static double circularityLimit = 0.7;
	private static int showOverlay = 2;
	private static String[] OVERLAY = new String[] { "None", "Slice position", "Entire stack" };
	private static boolean processFrames = false;
	private static boolean showProjection = false;
	private static int regionCounter = 1;
	private static boolean debug = false;

	private static ImagePlus debugSpotImp = null;
	private static ImagePlus debugMaskImp = null;

	private ImagePlus imp;
	private ImagePlus maskImp;
	private ImagePlus projectionImp;
	private String resultEntry = null;
	private Calibration cal;

	// Used to cache the mask region
	private int[] regions = null;
	private ImagePlus regionImp = null;
	private Rectangle bounds = new Rectangle();

	public void run(String arg)
	{
		if (arg != null && arg.equals("undo"))
		{
			undoLastResult();
			return;
		}

		imp = WindowManager.getCurrentImage();

		if (null == imp)
		{
			IJ.noImage();
			return;
		}

		if (imp.getBitDepth() != 8 && imp.getBitDepth() != 16)
		{
			IJ.showMessage("Error", "Only 8-bit and 16-bit images are supported");
			return;
		}

		boolean installOption = false; // TODO - Determine if the tool is already installed

		// Build a list of the open images
		String[] maskImageList = buildMaskList(imp);

		Roi roi = imp.getRoi();
		if (maskImageList.length == 1 && (roi == null || !roi.isArea()))
		{
			IJ.showMessage("Error", "No mask images and no area ROI");
			return;
		}

		if (arg == null || !arg.equals("redo")) // Allow repeat with same parameters
		{
			GenericDialog gd = new GenericDialog(FRAME_TITLE);

			gd.addMessage("Detects spots within a mask/ROI region.\n(Hold shift for extra options)");

			boolean extraOptions = IJ.shiftKeyDown();

			gd.addChoice("Mask", maskImageList, maskImage);
			gd.addNumericField("Smoothing", smoothingSize, 2);
			gd.addNumericField("Feature_Size", featureSize, 2);
			if (extraOptions)
			{
				gd.addNumericField("Background (SD > Av)", stdDevAboveBackground, 1);
				gd.addNumericField("Min_peak_size", minPeakSize, 0);
				gd.addNumericField("Min_peak_height", minPeakHeight, 2);
			}
			gd.addNumericField("Ciruclarity_limit", circularityLimit, 2);
			if (imp.getNFrames() != 1)
				gd.addCheckbox("Process_frames", processFrames);
			gd.addCheckbox("Show_projection", showProjection);
			gd.addChoice("Show_overlay", OVERLAY, OVERLAY[showOverlay]);
			gd.addNumericField("Region_counter", regionCounter, 0);
			if (extraOptions)
				gd.addCheckbox("Show_spot_image (debugging)", debug);
			if (installOption)
				gd.addCheckbox("Install_tool", false);
			gd.addHelp(gdsc.help.URL.FIND_FOCI);

			gd.showDialog();
			if (!gd.wasOKed())
				return;

			maskImage = gd.getNextChoice();
			smoothingSize = gd.getNextNumber();
			featureSize = gd.getNextNumber();
			if (extraOptions)
			{
				stdDevAboveBackground = gd.getNextNumber();
				minPeakSize = (int) gd.getNextNumber();
				minPeakHeight = (int) gd.getNextNumber();
			}
			circularityLimit = gd.getNextNumber();
			if (imp.getNFrames() != 1)
				processFrames = gd.getNextBoolean();
			showProjection = gd.getNextBoolean();
			showOverlay = gd.getNextChoiceIndex();
			regionCounter = (int) gd.getNextNumber();
			if (extraOptions)
				debug = gd.getNextBoolean();
			else
				debug = false;
			if (installOption)
			{
				if (gd.getNextBoolean())
					installTool();
			}
		}

		maskImp = checkMask(imp, maskImage);

		if (maskImp == null)
			return;

		initialise();

		int channel = imp.getChannel();
		ImageStack imageStack = imp.getImageStack();
		ImageStack maskStack = maskImp.getImageStack();

		// Cache the mask image. Only recreate the mask if using multiple frames
		ImageStack s2 = null;

		int[] frames = buildFrameList();

		createProjection(frames);

		for (int i = 0; i < frames.length; i++)
		{
			int frame = frames[i];

			// Build an image and mask for the frame
			ImageStack s1 = new ImageStack(imp.getWidth(), imp.getHeight(), imp.getNSlices());

			for (int slice = 1; slice <= imp.getNSlices(); slice++)
			{
				int stackIndex = imp.getStackIndex(channel, slice, frame);
				ImageProcessor ip = imageStack.getProcessor(stackIndex);
				s1.setPixels(ip.getPixels(), slice);
			}

			if (s2 == null)
			{
				s2 = new ImageStack(imp.getWidth(), imp.getHeight(), maskImp.getNSlices());

				// Only create a mask with 1 slice if necessary
				for (int slice = 1; slice <= maskImp.getNSlices(); slice++)
				{
					int maskChannel = (maskImp.getNChannels() > 1) ? channel : 0;
					int maskFrame = (maskImp.getNFrames() > 1) ? frame : 1;
					int maskSlice = (maskImp.getNSlices() > 1) ? slice : 1;

					int maskStackIndex = maskImp.getStackIndex(maskChannel, maskSlice, maskFrame);
					ImageProcessor maskIp = maskStack.getProcessor(maskStackIndex);

					s2.setPixels(maskIp.getPixels(), slice);
				}
			}

			if (ImageJHelper.isInterrupted())
				return;

			if (showProjection)
				projectionImp.setSlice(i + 1);

			exec(frame, s1, s2);
			IJ.showProgress(i + 1, frames.length);

			if (ImageJHelper.isInterrupted())
				return;

			// Recreate the mask if using multiple frames
			if (maskImp.getNFrames() > 1)
				s2 = null;
		}
		resultsWindow.append("");
	}

	private void undoLastResult()
	{
		if (allowUndo)
		{
			allowUndo = false;
			removeAfterLine(resultsWindow, lastResultLineCount);
			removeAfterLine(summaryWindow, lastSummaryLineCount);
		}
	}

	private void removeAfterLine(TextWindow window, int line)
	{
		if (window != null)
		{
			TextPanel tp = window.getTextPanel();
			int nLines = tp.getLineCount();
			if (line < nLines)
			{
				tp.setSelection(line, nLines);
				tp.clearSelection();
			}
		}
	}

	private void initialise()
	{
		regions = null;
		regionImp = null;
		bounds = new Rectangle();
		allowUndo = false;
		if (showOverlay > 0)
			imp.setOverlay(null);
		createResultsWindow();
	}

	private int[] buildFrameList()
	{
		int[] frames;
		if (processFrames)
		{
			frames = new int[imp.getNFrames()];
			for (int i = 0; i < imp.getNFrames(); i++)
				frames[i] = i + 1;
		}
		else
		{
			frames = new int[] { imp.getFrame() };
		}
		return frames;
	}

	private void createProjection(int[] frames)
	{
		if (!showProjection)
			return;

		ImageStack stack = imp.createEmptyStack();
		ImageProcessor ip = imp.getProcessor();
		for (int f : frames)
			stack.addSlice("Frame " + f, ip.createProcessor(ip.getWidth(), ip.getHeight()));

		String title = imp.getTitle() + " Spots Projection";
		projectionImp = WindowManager.getImage(title);
		if (projectionImp == null)
		{
			projectionImp = new ImagePlus(title, stack);
		}
		else
		{
			projectionImp.setStack(stack);
		}
		projectionImp.setSlice(1);
		projectionImp.setOverlay(null);
		projectionImp.show();
	}

	private String[] buildMaskList(ImagePlus imp)
	{
		ArrayList<String> newImageList = new ArrayList<String>();
		newImageList.add("[None]");

		for (int id : gdsc.utils.ImageJHelper.getIDList())
		{
			ImagePlus maskImp = WindowManager.getImage(id);
			// Mask image must:
			// - Not be the same image
			// - Match XY dimensions of the input image
			// - Math Z dimensions of input or else be a single image
			if (maskImp != null && maskImp.getID() != imp.getID() && maskImp.getWidth() == imp.getWidth() &&
					maskImp.getHeight() == imp.getHeight() &&
					(maskImp.getNSlices() == imp.getNSlices() || maskImp.getNSlices() == 1) &&
					(maskImp.getNChannels() == imp.getNChannels() || maskImp.getNChannels() == 1) &&
					(maskImp.getNFrames() == imp.getNFrames() || maskImp.getNFrames() == 1))
			{
				newImageList.add(maskImp.getTitle());
			}
		}

		return newImageList.toArray(new String[0]);
	}

	private ImagePlus checkMask(ImagePlus imp, String maskImage)
	{
		ImagePlus maskImp = WindowManager.getImage(maskImage);

		if (maskImp == null)
		{
			// Build a mask image using the input image ROI
			Roi roi = imp.getRoi();
			if (roi == null || !roi.isArea())
			{
				IJ.showMessage("Error", "No region defined (use an area ROI or an input mask)");
				return null;
			}
			ImageProcessor ip = (regionCounter > 255) ? new ShortProcessor(imp.getWidth(), imp.getHeight())
					: new ByteProcessor(imp.getWidth(), imp.getHeight());
			ip.setValue(regionCounter++);
			ip.setRoi(roi);
			ip.fill(roi);
			maskImp = new ImagePlus("Mask", ip);
		}

		if (imp.getNSlices() > 1 && maskImp.getNSlices() != 1 && maskImp.getNSlices() != imp.getNSlices())
		{
			IJ.showMessage("Error", "Mask region has incorrect slice dimension");
			return null;
		}
		if (imp.getNChannels() > 1 && maskImp.getNChannels() != 1 && maskImp.getNChannels() != imp.getNChannels())
		{
			IJ.showMessage("Error", "Mask region has incorrect channel dimension");
			return null;
		}
		if (imp.getNFrames() > 1 && processFrames && maskImp.getNFrames() != 1 &&
				maskImp.getNFrames() != imp.getNFrames())
		{
			IJ.showMessage("Error", "Mask region has incorrect frame dimension");
			return null;
		}

		return maskImp;
	}

	/**
	 * Create the result window (if it is not available)
	 */
	private void createResultsWindow()
	{
		if (resultsWindow == null || !resultsWindow.isShowing())
		{
			resultsWindow = new TextWindow(FRAME_TITLE + " Positions", createResultsHeader(), "", 700, 300);
		}
		if (summaryWindow == null || !summaryWindow.isShowing())
		{
			summaryWindow = new TextWindow(FRAME_TITLE + " Summary", createSummaryHeader(), "", 700, 300);
			Point p = resultsWindow.getLocation();
			p.y += resultsWindow.getHeight();
			summaryWindow.setLocation(p);
		}

		lastResultLineCount = resultsWindow.getTextPanel().getLineCount();
		lastSummaryLineCount = summaryWindow.getTextPanel().getLineCount();

		// Create the image result prefix
		StringBuilder sb = new StringBuilder();
		sb.append(imp.getTitle()).append("\t");
		cal = imp.getCalibration();
		sb.append(cal.getXUnit());
		if (!(cal.getYUnit().equalsIgnoreCase(cal.getXUnit()) && cal.getZUnit().equalsIgnoreCase(cal.getXUnit())))
		{
			sb.append(" ").append(cal.getYUnit()).append(" ").append(cal.getZUnit()).append(" ");
		}
		sb.append("\t");
		resultEntry = sb.toString();
	}

	private String createResultsHeader()
	{
		StringBuilder sb = new StringBuilder();
		sb.append("Image\t");
		sb.append("Units\t");
		sb.append("Frame\t");
		sb.append("Region\t");
		sb.append("x (px)\t");
		sb.append("y (px)\t");
		sb.append("z (px)\t");
		sb.append("X\t");
		sb.append("Y\t");
		sb.append("Z\t");
		sb.append("Circularity\t");
		return sb.toString();
	}

	private String createSummaryHeader()
	{
		StringBuilder sb = new StringBuilder();
		sb.append("Image\t");
		sb.append("Units\t");
		sb.append("Frame\t");
		sb.append("Region\t");
		sb.append("Spots\t");
		sb.append("Min\t");
		sb.append("Max\t");
		sb.append("Av\t");
		return sb.toString();
	}

	private void exec(int frame, ImageStack s1, ImageStack s2)
	{
		//new ImagePlus("image " + frame, s1).show();
		//new ImagePlus("mask " + frame, s2).show();

		// Check the mask we are using. 
		// If only one region we can use a ROI bounds for the Gaussian blur to increase speed.
		Rectangle blurBounds = null;
		if (regions == null)
			regions = findRegions(s2);

		if (regions.length == 1)
		{
			if (regionImp == null)
			{
				regionImp = extractRegion(s2, regions[0], bounds);
				regionImp = crop(regionImp, bounds);
			}
			blurBounds = bounds;
		}

		if (showProjection)
		{
			// Do Z-projection into the current image processor of the stack
			ImageProcessor max = projectionImp.getProcessor();
			max.insert(s1.getProcessor(1), 0, 0);
			for (int n = 2; n <= s1.getSize(); n++)
			{
				ImageProcessor ip = s1.getProcessor(n);
				for (int i = 0; i < ip.getPixelCount(); i++)
					if (max.get(i) < ip.get(i))
						max.set(i, ip.get(i));
			}
		}

		// Perform Difference of Gaussians to enhance the spot features if two radii are provided
		if (featureSize > 0)
		{
			ImageStack newS1 = new ImageStack(s1.getWidth(), s1.getHeight(), s1.getSize());
			for (int slice = 1; slice <= s1.getSize(); slice++)
			{
				ImageProcessor ip = s1.getProcessor(slice).toFloat(0, null);
				if (blurBounds != null)
					ip.setRoi(blurBounds);
				DifferenceOfGaussians.run(ip, featureSize, smoothingSize);
				newS1.setPixels(ip.getPixels(), slice);
			}
			s1 = newS1;
		}
		// Just perform image smoothing
		else if (smoothingSize > 0)
		{
			DifferenceOfGaussians filter = new DifferenceOfGaussians();
			filter.noProgress = true;
			ImageStack newS1 = new ImageStack(s1.getWidth(), s1.getHeight(), s1.getSize());
			for (int slice = 1; slice <= s1.getSize(); slice++)
			{
				ImageProcessor ip = s1.getProcessor(slice).toFloat(0, null);
				if (blurBounds != null)
				{
					ip.setRoi(blurBounds);
					ip.snapshot(); // Needed to reset region outside ROI
				}
				filter.blurGaussian(ip, smoothingSize);
				newS1.setPixels(ip.getPixels(), slice);
			}
			s1 = newS1;
		}

		ImagePlus spotImp = new ImagePlus(null, s1);

		// Process each region with FindFoci

		// TODO - Optimise these settings
		// - Maybe need a global thresholding on the whole DoG image
		// - Try Float IP for the DoG image
		// - Bigger feature size for DoG?

		FindFoci fp = new FindFoci();
		int backgroundMethod = FindFoci.BACKGROUND_STD_DEV_ABOVE_MEAN;
		double backgroundParameter = stdDevAboveBackground;
		String autoThresholdMethod = "Otsu";
		int searchMethod = FindFoci.SEARCH_ABOVE_BACKGROUND;
		double searchParameter = 0;
		int maxPeaks = 50;
		int minSize = minPeakSize;
		int peakMethod = FindFoci.PEAK_ABSOLUTE;
		int outputType = FindFoci.OUTPUT_RESULTS_TABLE | FindFoci.OUTPUT_MASK_PEAKS |
				FindFoci.OUTPUT_MASK_ABOVE_SADDLE | FindFoci.OUTPUT_MASK_NO_PEAK_DOTS;
		int sortIndex = FindFoci.SORT_MAX_VALUE;
		int options = FindFoci.OPTION_MINIMUM_ABOVE_SADDLE; // | FindFoci.OPTION_STATS_INSIDE;
		double blur = 0;
		int centreMethod = FindFoci.CENTRE_OF_MASS_ORIGINAL;
		double centreParameter = 2;

		Overlay overlay = null;
		if (showOverlay > 0)
		{
			overlay = new Overlay();
			overlay.setFillColor(Color.magenta);
			overlay.setStrokeColor(Color.magenta);
		}

		for (int region : regions)
		{
			// Cache the cropped mask if it has only one region
			if (regions.length != 1)
				regionImp = null;

			if (regionImp == null)
			{
				regionImp = extractRegion(s2, region, bounds);
				regionImp = crop(regionImp, bounds);
			}

			ImagePlus croppedImp = crop(spotImp, bounds);
			ImageStack stack = croppedImp.getImageStack();
			float scale = scaleImage(stack);
			croppedImp.setStack(stack); // Updates the image bit depth
			double peakParameter = Math.round(minPeakHeight * scale);

			if (debug)
				showSpotImage(croppedImp, region);

			Object[] results = fp.findMaxima(croppedImp, regionImp, backgroundMethod, backgroundParameter,
					autoThresholdMethod, searchMethod, searchParameter, maxPeaks, minSize, peakMethod, peakParameter,
					outputType, sortIndex, options, blur, centreMethod, centreParameter, 1);

			if (ImageJHelper.isInterrupted())
				return;
			if (results == null)
				continue;

			ArrayList<float[]> resultsArray = analyseResults(croppedImp, results, frame, overlay);

			for (float[] result : resultsArray)
			{
				addResult(frame, region, bounds, result);
			}

			if (resultsArray.size() < 2)
			{
				addSummaryResult(frame, region, resultsArray.size(), 0, 0, 0);
				continue;
			}

			double minD = Double.POSITIVE_INFINITY;
			double maxD = 0;
			double sumD = 0;
			int count = 0;
			for (int i = 0; i < resultsArray.size(); i++)
			{
				float[] r1 = resultsArray.get(i);
				for (int j = i + 1; j < resultsArray.size(); j++)
				{
					float[] r2 = resultsArray.get(j);
					double[] diff = new double[] { (r1[0] - r2[0]) * cal.pixelWidth, (r1[1] - r2[1]) * cal.pixelHeight,
							(r1[2] - r2[2]) * cal.pixelDepth };

					// TODO - This will not be valid if the units are not the same for all dimensions
					double d = Math.sqrt(diff[0] * diff[0] + diff[1] * diff[1] + diff[2] * diff[2]);
					if (d < minD)
						minD = d;
					if (d > maxD)
						maxD = d;
					sumD += d;
					count++;
				}
			}

			addSummaryResult(frame, region, resultsArray.size(), minD, maxD, sumD / count);
		}

		// Append to image overlay
		if (showOverlay > 0)
		{
			Overlay impOverlay = imp.getOverlay();
			if (impOverlay == null)
				impOverlay = new Overlay();
			for (Roi roi : overlay.toArray())
			{
				impOverlay.add(roi);
			}
			imp.setOverlay(impOverlay);
			imp.updateAndDraw();
		}

		if (showProjection)
		{
			int t = (projectionImp.getStackSize() > 1) ? projectionImp.getCurrentSlice() : 0;

			// Add all the ROIs of the overlay to the slice
			Overlay projOverlay = projectionImp.getOverlay();
			if (projOverlay == null)
				projOverlay = new Overlay();

			overlay = overlay.duplicate();
			Roi[] rois = overlay.toArray();
			for (Roi roi : rois)
			{
				roi.setPosition(t);
				projOverlay.add(roi);
			}

			projectionImp.setOverlay(projOverlay);
			projectionImp.updateAndDraw();
		}

		// Cache the mask only if it has one frame
		if (maskImp.getNFrames() > 1)
		{
			regions = null;
			regionImp = null;
		}
	}

	/**
	 * Scale the image to maximise the information available (since the FindFoci routine
	 * uses integer values).
	 * <p>
	 * Only scale the image if the input image stack is a FloatProcessor. The stack will be converted to a
	 * ShortProcessor.
	 * 
	 * @param s1
	 *            The image
	 * @return The scale
	 */
	private float scaleImage(ImageStack s1)
	{
		if (!(s1.getPixels(1) instanceof float[]))
			return 1;

		// Find the range
		float min = Float.MAX_VALUE;
		float max = 0;
		for (int n = 1; n <= s1.getSize(); n++)
		{
			float[] pixels = (float[]) s1.getPixels(n);
			for (float f : pixels)
			{
				if (min > f)
					min = f;
				if (max < f)
					max = f;
			}
		}

		// Convert pixels to short[]
		float range = max - min;
		float scale = (range > 0) ? 65334 / range : 1;
		if (debug)
			IJ.log(String.format("Scaling transformed image by %f (range: %f - %f)", scale, min, max));
		for (int n = 1; n <= s1.getSize(); n++)
		{
			float[] pixels = (float[]) s1.getPixels(n);
			short[] newPixels = new short[pixels.length];
			for (int i = 0; i < pixels.length; i++)
				newPixels[i] = (short) Math.round((pixels[i] - min) * scale);
			s1.setPixels(newPixels, n);
		}
		return scale;
	}

	private void showSpotImage(ImagePlus croppedImp, int region)
	{
		debugSpotImp = createImage(croppedImp, debugSpotImp, FRAME_TITLE + " Pixels", 0);
		debugMaskImp = createImage(regionImp, debugMaskImp, FRAME_TITLE + " Region", 1);
	}

	private ImagePlus createImage(ImagePlus sourceImp, ImagePlus debugImp, String title, int region)
	{
		if (debugImp == null || !debugImp.isVisible())
		{
			debugImp = new ImagePlus();
			debugImp.setTitle(title);
		}
		updateImage(debugImp, sourceImp, region);
		return debugImp;
	}

	private void updateImage(ImagePlus debugImp, ImagePlus sourceImp, int region)
	{
		debugImp.setStack(sourceImp.getImageStack(), 1, sourceImp.getStackSize(), 1);
		if (region > 0)
			debugImp.setDisplayRange(region - 1, region);
		else
			debugImp.resetDisplayRange();
		debugImp.updateAndDraw();
		debugImp.setCalibration(cal);
		debugImp.show();
	}

	private int[] findRegions(ImageStack s2)
	{
		// Build a histogram. Any non-zero value defines a region.
		int[] histogram = s2.getProcessor(1).getHistogram();
		for (int slice = 2; slice <= maskImp.getNSlices(); slice++)
		{
			int[] tmp = s2.getProcessor(slice).getHistogram();
			for (int i = 1; i < tmp.length; i++)
				histogram[i] += tmp[i];
		}

		int count = 0;
		for (int i = 1; i < histogram.length; i++)
			if (histogram[i] != 0)
				count++;

		int[] regions = new int[count];
		count = 0;
		for (int i = 1; i < histogram.length; i++)
			if (histogram[i] != 0)
				regions[count++] = i;
		return regions;
	}

	private ImagePlus extractRegion(ImageStack inputStack, int region, Rectangle bounds)
	{
		int minX = Integer.MAX_VALUE;
		int minY = Integer.MAX_VALUE;
		int maxX = 0;
		int maxY = 0;
		ImageStack outputStack = new ImageStack(inputStack.getWidth(), inputStack.getHeight(), inputStack.getSize());
		for (int slice = 1; slice <= inputStack.getSize(); slice++)
		{
			ImageProcessor ip = inputStack.getProcessor(slice);
			byte[] mask = new byte[inputStack.getWidth() * inputStack.getHeight()];
			for (int i = 0; i < ip.getPixelCount(); i++)
				if (ip.get(i) == region)
				{
					mask[i] = 1;
					// Calculate bounding rectangle
					int x = i % inputStack.getWidth();
					int y = i / inputStack.getWidth();
					if (minX > x)
						minX = x;
					if (minY > y)
						minY = y;
					if (maxX < x)
						maxX = x;
					if (maxY < y)
						maxY = y;
				}
			outputStack.setPixels(mask, slice);
		}

		bounds.x = minX;
		bounds.y = minY;
		bounds.width = maxX - minX + 1;
		bounds.height = maxY - minY + 1;
		return new ImagePlus(null, outputStack);
	}

	/**
	 * Crop by duplication within the bounds
	 * 
	 * @param imp
	 * @param bounds
	 * @return
	 */
	private ImagePlus crop(ImagePlus imp, Rectangle bounds)
	{
		imp.setRoi(bounds);
		ImagePlus croppedImp = imp.duplicate();
		return croppedImp;
	}

	/**
	 * Check the peak circularity. Add an overlay of the spots if requested.
	 * 
	 * @param croppedImp
	 * @param results
	 */
	private ArrayList<float[]> analyseResults(ImagePlus croppedImp, Object[] results, int frame, Overlay overlay)
	{
		//if (circularityLimit <= 0)
		//	return;

		ImagePlus peaksImp = (ImagePlus) results[0];
		@SuppressWarnings("unchecked")
		ArrayList<int[]> resultsArray = (ArrayList<int[]>) results[1];

		//		int width = croppedImp.getWidth();
		//		int height = croppedImp.getHeight();
		//		FloatProcessor fp = new FloatProcessor(width, height);

		ArrayList<float[]> newResultsArray = new ArrayList<float[]>(resultsArray.size());

		// Process in Z order
		Collections.sort(resultsArray, new Comparator<int[]>()
		{
			public int compare(int[] o1, int[] o2)
			{
				if (o1[FindFoci.RESULT_Z] < o2[FindFoci.RESULT_Z])
					return -1;
				if (o1[FindFoci.RESULT_Z] > o2[FindFoci.RESULT_Z])
					return 1;
				return 0;
			}
		});
		//		int lastZ = -1;
		//		ImageStack imgStack = croppedImp.getImageStack();
		ImageStack maskStack = peaksImp.getImageStack();

		for (int[] result : resultsArray)
		{
			int x = result[FindFoci.RESULT_X];
			int y = result[FindFoci.RESULT_Y];
			int z = result[FindFoci.RESULT_Z];

			// Filter peaks on circularity (spots should be circles)
			// C = 4*pi*A / P^2 
			// where A = Area, P = Perimeter
			// Q. Not sure if this will be valid for small spots.

			// Extract the data if it is a new slice
			//			if (z != lastZ)
			//				imgStack.getProcessor(z + 1).toFloat(0, fp);
			//			lastZ = z;

			// Extract the peak.
			// This could extract by a % volume. At current just use the mask region.

			ImageProcessor maskIp = maskStack.getProcessor(z + 1);

			//new ImagePlus("peak", fp.duplicate()).show();
			//new ImagePlus("mask", maskIp).show();

			int peakId = result[FindFoci.RESULT_PEAK_ID];
			int maskId = resultsArray.size() - peakId + 1;
			Roi roi = extractSelection(maskIp, maskId, (showOverlay == 1) ? z + 1 : 0, frame);
			double perimeter = roi.getLength();
			double area = getArea(maskIp, maskId);

			double circularity = perimeter == 0.0 ? 0.0 : 4.0 * Math.PI * (area / (perimeter * perimeter));

			if (circularity < circularityLimit)
			{
				IJ.log(String.format("Excluded non-circular peak @ x%d,y%d,z%d : %g (4pi * %g / %g^2)", bounds.x + x,
						bounds.y + y, z, circularity, area, perimeter));
				continue;
			}

			if (overlay != null)
			{
				//System.out.printf("%d, %d, %d\n", roi.getCPosition(), roi.getZPosition(), roi.getTPosition());
				overlay.add(roi);
			}

			newResultsArray.add(new float[] { x, y, z, (float) circularity });
		}

		return newResultsArray;
	}

	private Roi extractSelection(ImageProcessor maskIp, int maskId, int slice, int frame)
	{
		maskIp.setThreshold(maskId, maskId, ImageProcessor.NO_LUT_UPDATE);
		ThresholdToSelection ts = new ThresholdToSelection();
		Roi roi = ts.convert(maskIp);
		if (imp.getStackSize() == 1)
		{
			roi.setPosition(0);
		}
		else if (imp.isDisplayedHyperStack())
		{
			roi.setPosition(imp.getChannel(), slice, frame);
		}
		else
		{
			// Image could still have multiple frames or slices so set the position using
			// the appropriate multi-valued dimension
			roi.setPosition((imp.getNSlices() > 1) ? slice : frame);
		}
		Rectangle roiBounds = roi.getBounds();
		roi.setLocation(bounds.x + roiBounds.x, bounds.y + roiBounds.y);
		return roi;
	}

	@SuppressWarnings("unused")
	private double getPerimeter(ImageProcessor maskIp, int maskId, Overlay overlay, int z)
	{
		maskIp.setThreshold(maskId, maskId, ImageProcessor.NO_LUT_UPDATE);
		ThresholdToSelection ts = new ThresholdToSelection();
		Roi roi = ts.convert(maskIp);
		if (overlay != null)
		{
			roi.setPosition(z);
			roi.setLocation(bounds.x, bounds.y);
			overlay.add(roi);
		}
		double perimeter = roi.getLength();
		return perimeter;
	}

	private double getArea(ImageProcessor maskIp, int maskId)
	{
		int area = 0;
		for (int i = 0; i < maskIp.getPixelCount(); i++)
			if (maskIp.get(i) == maskId)
				area++;
		return area;
	}

	private void addResult(int frame, int region, Rectangle bounds, float[] result)
	{
		int x = bounds.x + (int) result[0];
		int y = bounds.y + (int) result[1];
		int z = (int) result[2];
		StringBuilder sb = new StringBuilder();
		sb.append(resultEntry);
		sb.append(frame).append("\t");
		sb.append(region).append("\t");
		sb.append(x).append("\t");
		sb.append(y).append("\t");
		sb.append(z).append("\t");
		sb.append(String.format("%.2f\t%.2f\t%.2f\t%.4f", x * cal.pixelWidth, y * cal.pixelHeight, z * cal.pixelDepth,
				result[3]));
		resultsWindow.append(sb.toString());
		allowUndo = true;
	}

	private void addSummaryResult(int frame, int region, int size, double minD, double maxD, double d)
	{
		StringBuilder sb = new StringBuilder();
		sb.append(resultEntry);
		sb.append(frame).append("\t");
		sb.append(region).append("\t").append(size).append("\t");
		sb.append(String.format("%.2f\t%.2f\t%.2f", minD, maxD, d));
		summaryWindow.append(sb.toString());
		allowUndo = true;
	}

	private void installTool()
	{
		StringBuffer sb = new StringBuffer();
		sb.append("macro 'Spot Distance Action Tool - C00fo4233o6922oa644Cf00O00ff' {\n");
		sb.append("   call('").append(this.getClass().getName()).append(".run');\n");
		sb.append("};\n");
		sb.append("macro 'Spot Distance Action Tool Options' {\n");
		sb.append("   call('").append(this.getClass().getName()).append(".setOptions');\n");
		sb.append("};\n");
		new MacroInstaller().install(sb.toString());
	}

	public static void run()
	{
		new SpotDistance().run(null);
	}

	public static void redo()
	{
		new SpotDistance().run("redo");
	}

	public static void undo()
	{
		new SpotDistance().run("undo");
	}

	public static void setOptions()
	{
		IJ.showMessage(FRAME_TITLE, "Identify spots within marked regions and measure the inter-spot distances");
	}
}
