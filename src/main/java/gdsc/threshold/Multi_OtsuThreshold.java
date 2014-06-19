package gdsc.threshold;

import java.awt.Color;

import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.gui.GenericDialog;
import ij.gui.NewImage;
import ij.gui.Plot;
import ij.plugin.filter.PlugInFilter;
import ij.process.ImageProcessor;

/**
 * Calculate multiple Otsu thresholds on the given image.
 * <p>
 * Algorithm: PS.Liao, TS.Chen, and PC. Chung, Journal of Information Science and Engineering, vol 17, 713-727 (2001)
 * <p>
 * Original Coding by Yasunari Tosa (ytosa@att.net) (Feb. 19th, 2005) and available from the ImageJ plugins site:<br/>
 * http://rsb.info.nih.gov/ij/plugins/multi-otsu-threshold.html
 * <p>
 * Adapted to allow 8/16 bit stack images and used a clipped histogram to increase speed. Added output options.
 */
public class Multi_OtsuThreshold implements PlugInFilter
{
	String TITLE = "Multi Otsu Threshold";
	ImagePlus imp;

	// Static to maintain state between plugin calls 
	private static int MLEVEL = 2;
	private static boolean s_doStack = true;
	private static boolean s_ignoreZero = true;
	private static boolean s_showHistogram = true;
	private static boolean s_ShowRegions = true;
	private static boolean s_ShowMasks = false;
	private static boolean s_LogMessages = true;

	// Instance variables to control plugin. Allow use of plugin from other code without GUI.
	public boolean ignoreZero = false;
	public boolean showHistogram = false;
	public boolean showRegions = false;
	public boolean showMasks = false;
	public boolean logMessages = false;

	/*
	 * (non-Javadoc)
	 * 
	 * @see ij.plugin.filter.PlugInFilter#setup(java.lang.String, ij.ImagePlus)
	 */
	public int setup(String arg, ImagePlus imp)
	{
		if (imp == null)
		{
			IJ.noImage();
			return DONE;
		}
		this.imp = imp;
		return DOES_8G + DOES_16 + NO_CHANGES;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see ij.plugin.filter.PlugInFilter#run(ij.process.ImageProcessor)
	 */
	public void run(ImageProcessor ip)
	{
		GenericDialog gd = new GenericDialog(TITLE);
		gd.addMessage("Multi-level Otsu thresholding on image stack");
		String[] items = { "2", "3", "4", "5", };
		gd.addChoice("Levels", items, items[MLEVEL - 2]);
		if (imp.getStackSize() > 1)
			gd.addCheckbox("Do_stack", s_doStack);
		gd.addCheckbox("Ignore_zero", s_ignoreZero);
		gd.addCheckbox("Show_histogram", s_showHistogram);
		gd.addCheckbox("Show_regions", s_ShowRegions);
		gd.addCheckbox("Show_masks", s_ShowMasks);
		gd.addCheckbox("Log_thresholds", s_LogMessages);
		gd.addHelp(gdsc.help.URL.UTILITY);
		gd.showDialog();
		if (gd.wasCanceled())
			return;

		MLEVEL = gd.getNextChoiceIndex() + 2;
		if (imp.getStackSize() > 1)
			s_doStack = gd.getNextBoolean();
		s_ignoreZero = gd.getNextBoolean();
		s_showHistogram = gd.getNextBoolean();
		s_ShowRegions = gd.getNextBoolean();
		s_ShowMasks = gd.getNextBoolean();
		s_LogMessages = gd.getNextBoolean();

		ignoreZero = s_ignoreZero;
		showHistogram = s_showHistogram;
		showRegions = s_ShowRegions;
		showMasks = s_ShowMasks;
		logMessages = s_LogMessages;

		if (!(showHistogram || showRegions || logMessages || showMasks))
		{
			IJ.error(TITLE, "No output options");
			return;
		}
		
		// Run on whole stack
		if (s_doStack)
			run(imp, MLEVEL);
		else
		{
			ImagePlus newImp = imp.createImagePlus();
			newImp.setTitle(String.format("%s (c%d,z%d,t%d)", imp.getTitle(), imp.getChannel(), imp.getSlice(),
					imp.getFrame()));
			newImp.setProcessor(imp.getProcessor());
			run(newImp, MLEVEL);
		}
	}

	/**
	 * Calculate Otsu thresholds on the given image. Output the thresholds to the IJ log.
	 * <p>
	 * Optionally outputs the image histogram and the threshold regions depending on the class variables.
	 * 
	 * @param imp
	 *            The image
	 * @param MLEVEL
	 *            The number of levels
	 */
	public void run(ImagePlus imp, int MLEVEL)
	{
		int[] offset = new int[1];
		float[] h = buildHistogram(imp, offset);
		
		float[] maxSig = new float[1];
		int[] threshold = getThresholds(MLEVEL, maxSig, offset, h);

		if (logMessages)
			showThresholds(MLEVEL, maxSig, threshold);
		if (showHistogram)
			showHistogram(h, threshold, offset[0], imp.getTitle() + " Histogram");
		if (showRegions)
			showRegions(MLEVEL, threshold, imp);
		if (showMasks)
			showMasks(MLEVEL, threshold, imp);
	}

	/**
	 * Calculate Otsu thresholds on the given image.
	 * 
	 * @param imp
	 *            The image
	 * @param MLEVEL
	 *            The number of levels
	 * @return The thresholds
	 */
	public int[] calculateThresholds(ImagePlus imp, int MLEVEL)
	{
		return calculateThresholds(imp, MLEVEL, null);
	}

	/**
	 * Calculate Otsu thresholds on the given image.
	 * 
	 * @param imp
	 *            The image
	 * @param MLEVEL
	 *            The number of levels
	 * @param maxSig
	 *            The maximum between class significance
	 * @return The thresholds
	 */
	public int[] calculateThresholds(ImagePlus imp, int MLEVEL, float[] maxSig)
	{
		int[] offset = new int[1];
		float[] h = buildHistogram(imp, offset);

		return getThresholds(MLEVEL, maxSig, offset, h);
	}

	private int[] getThresholds(int MLEVEL, float[] maxSig, int[] offset, float[] h)
	{
		/////////////////////////////////////////////
		// Build lookup tables from h
		////////////////////////////////////////////
		float[][] H = buildLookupTables(h);

		////////////////////////////////////////////////////////
		// now M level loop   MLEVEL dependent term
		////////////////////////////////////////////////////////
		if (maxSig == null || maxSig.length < 1)
			maxSig = new float[1];
		int[] threshold = new int[MLEVEL];
		maxSig[0] = findMaxSigma(MLEVEL, H, threshold);

		applyOffset(threshold, offset);

		return threshold;
	}

	/**
	 * Create a histogram from image min to max. Normalise so integral is 1.
	 * <p>
	 * Return the image min in the offset variable
	 * 
	 * @param imp
	 *            Input image
	 * @param offset
	 *            Output image minimum (if array length >= 1)
	 * @return The normalised image histogram
	 */
	public float[] buildHistogram(ImagePlus imp, int[] offset)
	{
		// Get stack histogram - Use ImagePlus to get the ImageProcessor so maintaining the ROI
		int currentSlice = imp.getCurrentSlice();
		imp.setSliceWithoutUpdate(1);
		int[] data = imp.getProcessor().getHistogram();
		for (int slice = 2; slice <= imp.getStackSize(); slice++)
		{
			imp.setSliceWithoutUpdate(slice);
			int[] tmp = imp.getProcessor().getHistogram();
			for (int i = 0; i < data.length; i++)
				data[i] += tmp[i];
		}
		imp.setSliceWithoutUpdate(currentSlice);

		if (ignoreZero)
			data[0] = 0;		
		
		// Bracket the histogram to the range that holds data to make it quicker
		int minbin = 0, maxbin = data.length-1;
		while (data[minbin] == 0 && minbin < data.length)
			minbin++;
		while (data[maxbin] == 0 && maxbin > 0)
			maxbin--;
		if (maxbin < minbin) // No data at all
			minbin = maxbin;
		
		// ROI masking changes the histogram so total up the number of used pixels
		long total = 0;
		for (int d : data)
			total += d;
		
		int[] data2 = new int[(maxbin - minbin) + 1];
		for (int i = minbin; i <= maxbin; i++)
		{
			data2[i - minbin] = data[i];
		}

		// note the probability of grey i is h[i]/(pixel count)
		double normalisation = 1.0 / total;
		int NGRAY = data2.length;
		float[] h = new float[NGRAY];

		for (int i = 0; i < NGRAY; ++i)
			h[i] = (float) (data2[i] * normalisation);

		if (offset != null && offset.length > 0)
			offset[0] = minbin;
		return h;
	}

	/**
	 * Build the required lookup table for the {@link #findMaxSigma(int, float[][], int[])} method.
	 * 
	 * @param h
	 *            Image histogram (length N)
	 * @return The lookup table
	 */
	public float[][] buildLookupTables(float[] h)
	{
		return buildLookupTables(h, null, null);
	}

	/**
	 * Build the required lookup table for the {@link #findMaxSigma(int, float[][], int[])} method.
	 * <p>
	 * P and S can be provided to save reallocating memory. If null or less than h.length they will be reallocated. P
	 * and S are destroyed and the lookup table is returned.
	 * 
	 * @param h
	 *            Image histogram (length N)
	 * @param P
	 *            working space (NxN)
	 * @param S
	 *            working space (NxN)
	 * @return The lookup table
	 */
	public float[][] buildLookupTables(float[] h, float[][] P, float[][] S)
	{
		int NGRAY = h.length;
		P = initialise(P, NGRAY);
		S = initialise(S, NGRAY);

		// diagonal 
		for (int i = 0; i < NGRAY; ++i)
		{
			P[i][i] = h[i];
			S[i][i] = ((float) i) * h[i];
		}
		// calculate first row (row 0 is all zero)
		for (int i = 1; i < NGRAY - 1; ++i)
		{
			P[1][i + 1] = P[1][i] + h[i + 1];
			S[1][i + 1] = S[1][i] + ((float) (i + 1)) * h[i + 1];
		}
		// using row 1 to calculate others
		for (int i = 2; i < NGRAY; i++)
			for (int j = i + 1; j < NGRAY; j++)
			{
				P[i][j] = P[1][j] - P[1][i - 1];
				S[i][j] = S[1][j] - S[1][i - 1];
			}
		// now calculate H[i][j]
		for (int i = 1; i < NGRAY; ++i)
			for (int j = i + 1; j < NGRAY; j++)
			{
				if (P[i][j] != 0)
					S[i][j] = (S[i][j] * S[i][j]) / P[i][j];
				else
					S[i][j] = 0.f;
			}

		return S;
	}

	private float[][] initialise(float[][] P, int NGRAY)
	{
		if (P == null || P.length < NGRAY)
		{
			P = new float[NGRAY][NGRAY];
		}
		else
		{
			// initialize to zero
			for (int j = 0; j < NGRAY; j++)
				for (int i = 0; i < NGRAY; ++i)
				{
					P[i][j] = 0.f;
				}
		}
		return P;
	}

	/**
	 * Find the threshold that maximises the between class variance
	 * 
	 * @param mlevel
	 *            The number of thresholds
	 * @param H
	 *            The lookup table produced from the image histogram
	 * @param t
	 *            The thresholds (output)
	 * @return The max between class significance
	 */
	public float findMaxSigma(int mlevel, float[][] H, int[] t)
	{
		int NGRAY = H[0].length;
		return findMaxSigma(mlevel, H, t, NGRAY);
	}

	/**
	 * Find the threshold that maximises the between class variance
	 * 
	 * @param mlevel
	 *            The number of thresholds
	 * @param H
	 *            The lookup table produced from the image histogram
	 * @param t
	 *            The thresholds (output)
	 * @param NGRAY
	 *            The number of histogram levels
	 * @return The max between class significance
	 */
	public float findMaxSigma(int mlevel, float[][] H, int[] t, int NGRAY)
	{
		for (int i = 0; i < t.length; i++)
			t[i] = 0;
		float maxSig = -1;

		// In case of equality use the average for the threshold
		int n = 0;

		switch (mlevel)
		{
			case 2:
				for (int i = 1; i < NGRAY - mlevel; i++) // t1
				{
					float Sq = H[1][i] + H[i + 1][NGRAY - 1];
					if (maxSig < Sq)
					{
						t[1] = i;
						maxSig = Sq;
						n = 1;
					}
					else if (maxSig == Sq)
					{
						t[1] += i;
						n++;
					}
				}
				break;
			case 3:
				for (int i = 1; i < NGRAY - mlevel; i++)
					// t1
					for (int j = i + 1; j < NGRAY - mlevel + 1; j++) // t2
					{
						float Sq = H[1][i] + H[i + 1][j] + H[j + 1][NGRAY - 1];
						if (maxSig < Sq)
						{
							t[1] = i;
							t[2] = j;
							maxSig = Sq;
							n = 1;
						}
						else if (maxSig == Sq)
						{
							t[1] += i;
							t[2] += j;
							n++;
						}
					}
				break;
			case 4:
				for (int i = 1; i < NGRAY - mlevel; i++)
					// t1
					for (int j = i + 1; j < NGRAY - mlevel + 1; j++)
						// t2
						for (int k = j + 1; k < NGRAY - mlevel + 2; k++) // t3
						{
							float Sq = H[1][i] + H[i + 1][j] + H[j + 1][k] + H[k + 1][NGRAY - 1];
							if (maxSig < Sq)
							{
								t[1] = i;
								t[2] = j;
								t[3] = k;
								maxSig = Sq;
								n = 1;
							}
							else if (maxSig == Sq)
							{
								t[1] += i;
								t[2] += j;
								t[3] += k;
								n++;
							}
						}
				break;
			case 5:
				for (int i = 1; i < NGRAY - mlevel; i++)
					// t1
					for (int j = i + 1; j < NGRAY - mlevel + 1; j++)
						// t2
						for (int k = j + 1; k < NGRAY - mlevel + 2; k++)
							// t3
							for (int m = k + 1; m < NGRAY - mlevel + 3; m++) // t4
							{
								float Sq = H[1][i] + H[i + 1][j] + H[j + 1][k] + H[k + 1][m] + H[m + 1][NGRAY - 1];
								if (maxSig < Sq)
								{
									t[1] = i;
									t[2] = j;
									t[3] = k;
									t[4] = m;
									maxSig = Sq;
									n = 1;
								}
								else if (maxSig == Sq)
								{
									t[1] += i;
									t[2] += j;
									t[3] += k;
									t[4] += m;
									n++;
								}
							}
				break;
		}

		if (n > 1)
		{
			if (logMessages)
				IJ.log("Multiple optimal thresholds");
			for (int i = 0; i < t.length; i++)
				t[i] /= n;
		}

		return (maxSig > 0) ? maxSig : 0;
	}

	/**
	 * Add back the histogram offset to produce the correct thresholds
	 * 
	 * @param threshold
	 *            output from {@link #findMaxSigma(int, float[][], int[])}
	 * @param offset
	 *            output from {@link #buildHistogram(ImageProcessor, int[])}
	 */
	public void applyOffset(int[] threshold, int[] offset)
	{
		for (int i = 0; i < threshold.length; ++i)
			threshold[i] += offset[0];
	}

	private void showThresholds(int MLEVEL, float[] maxSig, int[] threshold)
	{
		StringBuilder sb = new StringBuilder();
		sb.append("Otsu thresholds: ");
		for (int i = 0; i < MLEVEL; ++i)
			sb.append(i).append("=").append(threshold[i]).append(", ");
		sb.append(" maxSig = ").append(maxSig[0]);
		IJ.log(sb.toString());
	}
	
	private void showHistogram(float[] h, int[] thresholds, int minbin, String title)
	{
		int NGRAY = h.length;
		
		// X-axis values
		float[] bin = new float[NGRAY];
		
		// Calculate the maximum
		float hmax = 0.f;
		int mode = 0;
		for (int i = 0; i < NGRAY; ++i)
		{
			bin[i] = i + minbin;
			if (hmax < h[i])
			{
				hmax = h[i];
				mode = i;
			}
		}

		// Clip histogram to exclude the mode count as per the ij.gui.HistogramWindow.
		// For example this removes a large peak at zero in masked images.
		float hmax2 = 0; // Second highest point
		for (int i = 0; i < h.length; i++)
		{
			if (hmax2 < h[i] && i != mode)
			{
				hmax2 = h[i];
			}
		}
		if ((hmax > (hmax2 * 2)) && (hmax2 != 0))
		{
			// Set histogram limit to 50% higher than the second largest value
			hmax = hmax2 * 1.5f;
		}

		if (title == null)
			title = "Histogram";
		Plot histogram = new Plot(title, "Intensity", "p(Intensity)", bin, h);
		histogram.setLimits(minbin, minbin + NGRAY, 0.f, hmax);
		histogram.draw();
		
		// Add lines for each threshold
		histogram.setLineWidth(1);
		histogram.setColor(Color.red);
		for (int i=1; i<thresholds.length; i++)
		{
			double x = thresholds[i];
			histogram.drawLine(x, 0, x, hmax);
		}		
		
		histogram.show();
	}

	/**
	 * Show new images using only pixels within the bounds of the given thresholds
	 * 
	 * @param mlevel
	 *            The number of thresholds
	 * @param t
	 *            The thresholds
	 * @param imp
	 *            The image
	 */
	public void showRegions(int mlevel, int[] t, ImagePlus imp)
	{
		int width = imp.getWidth();
		int height = imp.getHeight();
		int bitDepth = imp.getBitDepth();

		double max = imp.getDisplayRangeMax();
		double min = imp.getDisplayRangeMin();

		ImageStack stack = imp.getImageStack();
		int slices = stack.getSize();
		ImagePlus[] region = new ImagePlus[mlevel];
		ImageStack[] rstack = new ImageStack[mlevel];
		ImageProcessor[] rip = new ImageProcessor[mlevel];
		for (int i = 0; i < mlevel; ++i)
		{
			region[i] = NewImage.createImage(imp.getTitle() + " Region " + i, width, height, slices, bitDepth,
					NewImage.FILL_BLACK);
			rstack[i] = region[i].getImageStack();
		}
		
		int[] newT = new int[mlevel +1];
		System.arraycopy(t, 0, newT, 0, mlevel);
		newT[mlevel] = Integer.MAX_VALUE;
		t = newT;
		
		for (int slice = 1; slice <= slices; slice++)
		{
			ImageProcessor ip = stack.getProcessor(slice);
			for (int i = 0; i < mlevel; ++i)
			{
				rip[i] = rstack[i].getProcessor(slice);
			}
			for (int i = 0; i < ip.getPixelCount(); ++i)
			{
				int val = ip.get(i);
				int k=0;
				while (val > t[k+1])
					k++;
				rip[k].set(i, val);
			}
		}
		for (int i = 0; i < mlevel; i++)
		{
			region[i].setDisplayRange(min, max);
			region[i].show();
		}
	}

	/**
	 * Show new mask images using only pixels within the bounds of the given thresholds
	 * 
	 * @param mlevel
	 *            The number of thresholds
	 * @param t
	 *            The thresholds
	 * @param imp
	 *            The image
	 */
	public void showMasks(int mlevel, int[] t, ImagePlus imp)
	{
		int width = imp.getWidth();
		int height = imp.getHeight();

		ImageStack stack = imp.getImageStack();
		int slices = stack.getSize();
		ImagePlus[] region = new ImagePlus[mlevel];
		ImageStack[] rstack = new ImageStack[mlevel];
		ImageProcessor[] rip = new ImageProcessor[mlevel];
		for (int i = 0; i < mlevel; ++i)
		{
			region[i] = NewImage.createImage(imp.getTitle() + " Mask " + i, width, height, slices, 8,
					NewImage.FILL_BLACK);
			rstack[i] = region[i].getImageStack();
		}
		int[] newT = new int[mlevel +1];
		System.arraycopy(t, 0, newT, 0, mlevel);
		newT[mlevel] = Integer.MAX_VALUE;
		t = newT;
		for (int slice = 1; slice <= slices; slice++)
		{
			ImageProcessor ip = stack.getProcessor(slice);
			for (int i = 0; i < mlevel; ++i)
			{
				rip[i] = rstack[i].getProcessor(slice);
			}
			for (int i = 0; i < ip.getPixelCount(); ++i)
			{
				int val = ip.get(i);
				int k=0;
				while (val > t[k+1])
					k++;
				rip[k].set(i, 255);
			}
		}
		for (int i = 0; i < mlevel; i++)
		{
			region[i].setDisplayRange(0, 255);
			region[i].show();
		}
	}
}