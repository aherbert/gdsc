package gdsc.utils;

import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.WindowManager;
import ij.gui.GenericDialog;
import ij.plugin.PlugIn;
import ij.process.ColorProcessor;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;
import ij.process.ImageStatistics;

import java.awt.Rectangle;
import java.util.ArrayList;

import gdsc.UsageTracker;

/**
 * Aligns an image stack to a reference image using XY translation to maximise the normalised correlation. Takes in:
 * 
 * - The reference image
 * - The reference image mask (states which pixels are important)
 * - The image/stack to align.
 * - Max/Min values for the X and Y translation
 * 
 * Reference image and mask must be the same width/height.
 * 
 * For each translation:
 * - Move the image to align
 * - Calculate the correlation between images (ignore pixels not in the reference mask)
 * - Report alignment stats
 * 
 * Output new stack with the best alignment with optional sub-pixel accuracy.
 */
public class Align_Images implements PlugIn
{
	private static final String TITLE = "Align Images";
	private static int myMinXShift = -10, myMaxXShift = 10;
	private static int myMinYShift = -10, myMaxYShift = 10;
	private String[] subPixelMethods = new String[] { "None", "Cubic", "Gaussian" };
	private static int subPixelMethod = 2;
	private String[] methods = ImageProcessor.getInterpolationMethods();
	private static int interpolationMethod = ImageProcessor.NONE;

	private static final String NONE = "[None]";
	private static String reference = "";
	private static String referenceMask = NONE;
	private static String target = "";
	private static boolean showCorrelationImage = false;
	private static boolean clipOutput = false;

	/** Ask for parameters and then execute. */
	public void run(String arg)
	{
		UsageTracker.recordPlugin(this.getClass(), arg);
		
		String[] imageList = getImagesList();

		if (imageList.length < 1)
		{
			IJ.showMessage("Error", "Only 8-bit and 16-bit images are supported");
			return;
		}

		if (!showDialog(imageList))
			return;

		ImagePlus refImp = WindowManager.getImage(reference);
		ImageProcessor maskIp = getProcessor(referenceMask);
		ImagePlus targetImp = WindowManager.getImage(target);

		ImagePlus alignedImp = exec(refImp, maskIp, targetImp, myMinXShift, myMaxXShift, myMinYShift, myMaxYShift,
				subPixelMethod, interpolationMethod, showCorrelationImage, clipOutput);

		if (alignedImp != null)
			alignedImp.show();
	}

	private ImageProcessor getProcessor(String title)
	{
		ImagePlus imp = WindowManager.getImage(title);
		if (imp != null)
			return imp.getProcessor();
		return null;
	}

	private String[] getImagesList()
	{
		// Find the currently open images
		ArrayList<String> newImageList = new ArrayList<String>();

		for (int id : gdsc.utils.ImageJHelper.getIDList())
		{
			ImagePlus imp = WindowManager.getImage(id);

			// Image must be 8-bit/16-bit
			if (imp != null &&
					(imp.getType() == ImagePlus.GRAY8 || imp.getType() == ImagePlus.GRAY16 || imp.getType() == ImagePlus.GRAY32))
			{
				// Check it is not one the result images
				String imageTitle = imp.getTitle();
				newImageList.add(imageTitle);
			}
		}

		return newImageList.toArray(new String[0]);
	}

	private boolean showDialog(String[] imageList)
	{
		GenericDialog gd = new GenericDialog(TITLE);

		if (!contains(imageList, reference))
		{
			reference = imageList[0];
			referenceMask = NONE;
		}

		// Add option to have no mask
		String[] maskList = new String[imageList.length + 1];
		maskList[0] = NONE;
		for (int i = 0; i < imageList.length; i++)
		{
			maskList[i + 1] = imageList[i];
		}
		if (!contains(maskList, target))
		{
			target = maskList[0];
		}

		gd.addMessage("Align target image stack to a reference using\ncorrelation within a translation range. Ignore pixels\nin the reference using a mask.");
		
		gd.addChoice("Reference_image", imageList, reference);
		gd.addChoice("Reference_mask", maskList, referenceMask);
		gd.addChoice("Target_image", maskList, target);
		gd.addNumericField("Min_X_translation", myMinXShift, 0);
		gd.addNumericField("Max_X_translation", myMaxXShift, 0);
		gd.addNumericField("Min_Y_translation", myMinYShift, 0);
		gd.addNumericField("Max_Y_translation", myMaxYShift, 0);
		gd.addChoice("Sub-pixel_method", subPixelMethods, subPixelMethods[subPixelMethod]);
		gd.addChoice("Interpolation", methods, methods[interpolationMethod]);
		gd.addCheckbox("Show_correlation_image", showCorrelationImage);
		gd.addCheckbox("Clip_output", clipOutput);
		gd.addHelp(gdsc.help.URL.UTILITY);

		gd.showDialog();

		if (gd.wasCanceled())
			return false;

		reference = gd.getNextChoice();
		referenceMask = gd.getNextChoice();
		target = gd.getNextChoice();
		myMinXShift = (int) gd.getNextNumber();
		myMaxXShift = (int) gd.getNextNumber();
		myMinYShift = (int) gd.getNextNumber();
		myMaxYShift = (int) gd.getNextNumber();
		subPixelMethod = gd.getNextChoiceIndex();
		interpolationMethod = gd.getNextChoiceIndex();
		showCorrelationImage = gd.getNextBoolean();
		clipOutput = gd.getNextBoolean();

		return true;
	}

	private boolean contains(String[] imageList, String title)
	{
		for (String t : imageList)
			if (t.equals(title))
				return true;
		return false;
	}

	public ImagePlus exec(ImagePlus refImp, ImageProcessor maskIp, ImagePlus targetImp, int minXShift,
			int maxXShift, int minYShift, int maxYShift, int subPixelMethod, int interpolationMethod,
			boolean showCorrelationImage, boolean clipOutput)
	{
		ImageProcessor refIp = refImp.getProcessor();
		if (targetImp == null)
			targetImp = refImp;
		
		// Check same size
		if (!isValid(refIp, maskIp, targetImp))
			return null;
		if (maxXShift < minXShift || maxYShift < minYShift)
			return null;

		// Note:
		// If the reference image is centred and the target image is normalised then the result
		// of the top half of the Pearson correlation equation will be the correlation produced by 
		// the Align_Images_FFT without normalisation.

		//refIp = centre(refIp);
		ImageStack outStack = new ImageStack(targetImp.getWidth(), targetImp.getHeight());
		ImageStack correlationStack = null;
		FloatProcessor fp = new FloatProcessor(maxXShift - minXShift + 1, maxYShift - minYShift + 1);
		if (showCorrelationImage)
		{
			correlationStack = new ImageStack(maxXShift - minXShift + 1, maxYShift - minYShift + 1);
		}

		ImageStack stack = targetImp.getStack();
		for (int slice = 1; slice <= stack.getSize(); slice++)
		{
			ImageProcessor targetIp = stack.getProcessor(slice);
			//targetIp = normalise(targetIp);
			outStack.addSlice(
					null,
					alignImages(refIp, maskIp, targetIp, slice, minXShift, maxXShift, minYShift, maxYShift, fp,
							subPixelMethod, interpolationMethod, clipOutput));
			if (showCorrelationImage)
			{
				correlationStack.addSlice(null, fp.duplicate());
			}
		}

		if (showCorrelationImage)
		{
			new ImagePlus(targetImp.getTitle() + " Correlation", correlationStack).show();
		}

		return new ImagePlus(targetImp.getTitle() + " Aligned", outStack);
	}

	/**
	 * Subtract mean from the image and return a float processor
	 * 
	 * @param ip
	 * @return
	 */
	public static FloatProcessor centre(ImageProcessor ip)
	{
		float[] pixels = new float[ip.getPixelCount()];

		// Subtract mean and normalise to unit length
		double sum = 0;
		for (int i = 0; i < ip.getPixelCount(); i++)
			sum += ip.getf(i);
		float av = (float) (sum / pixels.length);
		for (int i = 0; i < pixels.length; i++)
			pixels[i] = ip.getf(i) - av;

		return new FloatProcessor(ip.getWidth(), ip.getHeight(), pixels, null);
	}

	/**
	 * Convert to unit length, return a float processor
	 * 
	 * @param ip
	 * @return
	 */
	public static FloatProcessor normalise(ImageProcessor ip)
	{
		float[] pixels = new float[ip.getPixelCount()];

		// Normalise to unit length and subtract mean
		double sum = 0;
		for (int i = 0; i < pixels.length; i++)
		{
			sum += ip.getf(i) * ip.getf(i);
		}
		if (sum > 0)
		{
			double factor = 1.0 / Math.sqrt(sum);
			for (int i = 0; i < pixels.length; i++)
			{
				pixels[i] = (float) (ip.getf(i) * factor);
			}
		}

		return new FloatProcessor(ip.getWidth(), ip.getHeight(), pixels, null);
	}

	private boolean isValid(ImageProcessor refIp, ImageProcessor maskIp, ImagePlus targetImp)
	{
		if (refIp == null || targetImp == null)
			return false;
		if (maskIp != null && (refIp.getWidth() != maskIp.getWidth() || refIp.getHeight() != maskIp.getHeight()))
			return false;
		//		if (refIp.getWidth() != targetImp.getWidth() || refIp.getHeight() != targetImp.getHeight())
		//			return false;

		return true;
	}

	private ImageProcessor alignImages(ImageProcessor refIp, ImageProcessor maskIp, ImageProcessor targetIp, int slice,
			int minXShift, int maxXShift, int minYShift, int maxYShift, FloatProcessor fp, int subPixelMethod,
			int interpolationMethod, boolean clipOutput)
	{
		double scoreMax = 0;
		int xShiftMax = 0, yShiftMax = 0;
		for (int xShift = minXShift; xShift <= maxXShift; xShift++)
		{
			for (int yShift = minYShift; yShift <= maxYShift; yShift++)
			{
				double score = calculateScore(refIp, maskIp, targetIp, xShift, yShift);
				fp.setf(xShift - minXShift, yShift - minYShift, (float) score);

				//IJ.log("Slice " + slice + " x " + xShift + " y " + yShift + " = " + score);
				if (scoreMax < score)
				{
					scoreMax = score;
					xShiftMax = xShift;
					yShiftMax = yShift;
				}
			}
		}

		double xOffset = xShiftMax;
		double yOffset = yShiftMax;

		String estimatedScore = "";
		if (subPixelMethod > 0 && scoreMax != 1.00)
		{
			double[] centre;
			int i = xShiftMax - minXShift;
			int j = yShiftMax - minYShift;
			if (subPixelMethod == 1)
			{
				centre = performCubicFit(fp, i, j);
			}
			else
			{
				centre = performGaussianFit(fp, i, j);
			}

			if (centre != null)
			{
				xOffset = centre[0] + minXShift;
				yOffset = centre[1] + minYShift;

				double score = fp.getBicubicInterpolatedPixel(centre[0], centre[1], fp);
				if (score < -1)
					score = -1;
				if (score > 1)
					score = 1;
				estimatedScore = String.format(" (interpolated score %g)", score);

				//IJ.log(String.format("Fitted slice %d  x %d -> %g (%g)   y %d -> %g (%g)", slice, xShiftMax, xOffset,
				//		centre[0], yShiftMax, yOffset, centre[1]));
			}
		}

		String warning = "";
		if (xOffset == minXShift || xOffset == maxXShift || yOffset == minYShift || yOffset == maxYShift)
		{
			warning = "***";
		}
		IJ.log(String.format("Best Slice%s %d  x %g  y %g = %g%s", warning, slice, xOffset, yOffset, scoreMax,
				estimatedScore));

		return translate(interpolationMethod, targetIp, xOffset, yOffset, clipOutput);
	}

	/**
	 * Duplicate and translate the image processor
	 * 
	 * @param interpolationMethod
	 * @param ip
	 * @param xOffset
	 * @param yOffset
	 * @param clipOutput
	 *            Set to true to ensure the output image has the same max as the input. Applies to bicubic
	 *            interpolation
	 * @return New translated processor
	 */
	public static ImageProcessor translate(int interpolationMethod, ImageProcessor ip, double xOffset, double yOffset,
			boolean clipOutput)
	{
		ImageProcessor newIp = ip.duplicate();
		translateProcessor(interpolationMethod, newIp, xOffset, yOffset, clipOutput);
		return newIp;
	}

	/**
	 * Translate the image processor in place
	 * 
	 * @param interpolationMethod
	 * @param ip
	 * @param xOffset
	 * @param yOffset
	 * @param clipOutput
	 *            Set to true to ensure the output image has the same max as the input. Applies to bicubic
	 *            interpolation
	 */
	public static void translateProcessor(int interpolationMethod, ImageProcessor ip, double xOffset, double yOffset,
			boolean clipOutput)
	{
		// Check if interpolation is needed
		if (xOffset == (int)xOffset && yOffset == (int)yOffset)
		{
			interpolationMethod = ImageProcessor.NONE;
		}
		
		// Bicubic interpolation can generate values outside the input range. 
		// Optionally clip these. This is not applicable for ColorProcessors.
		ImageStatistics stats = null;
		if (interpolationMethod == ImageProcessor.BICUBIC && clipOutput && !(ip instanceof ColorProcessor))
			stats = ImageStatistics.getStatistics(ip, ImageStatistics.MIN_MAX, null);
		
		ip.setInterpolationMethod(interpolationMethod);
		ip.translate(xOffset, yOffset);

		if (interpolationMethod == ImageProcessor.BICUBIC && clipOutput && !(ip instanceof ColorProcessor))
		{
			float max = (float) stats.max;
			for (int i = ip.getPixelCount(); i-- > 0;)
			{
				if (ip.getf(i) > max)
					ip.setf(i, max);
			}
		}
	}

	/**
	 * Iteratively search the cubic spline surface around the given pixel
	 * to maximise the value.
	 * 
	 * @param fp
	 *            Float processor containing a peak surface
	 * @param i
	 *            The peak x position
	 * @param j
	 *            The peak y position
	 * @return The peak location with sub-pixel accuracy
	 */
	public static double[] performCubicFit(FloatProcessor fp, int i, int j)
	{
		double[] centre = new double[] { i, j };
		// This value will be progressively halved. 
		// Start with a value that allows the number of iterations to fully cover the region +/- 1 pixel
		// TODO - Test if 0.67 is better as this can cover +/- 1 pixel in 2 iterations
		double range = 0.5;    
		for (int c = 10; c-- > 0;)
		{
			centre = performCubicFit(fp, centre[0], centre[1], range);
			range /= 2;
		}
		return centre;
	}

	private static double[] performCubicFit(FloatProcessor fp, double x, double y, double range)
	{
		double[] centre = new double[2];
		double peakValue = Double.NEGATIVE_INFINITY;
		for (double x0 : new double[] { x - range, x, x + range })
		{
			for (double y0 : new double[] { y - range, y, y + range })
			{
				double v = fp.getBicubicInterpolatedPixel(x0, y0, fp);
				if (peakValue < v)
				{
					peakValue = v;
					centre[0] = x0;
					centre[1] = y0;
				}
			}
		}
		return centre;
	}

	/**
	 * Perform an interpolated Gaussian fit.
	 * <p>
	 * The following functions for peak finding using Gaussian fitting have been extracted from Jpiv:
	 * http://www.jpiv.vennemann-online.de/
	 * 
	 * @param fp
	 *            Float processor containing a peak surface
	 * @param i
	 *            The peak x position
	 * @param j
	 *            The peak y position
	 * @return The peak location with sub-pixel accuracy
	 */
	public static double[] performGaussianFit(FloatProcessor fp, int i, int j)
	{
		// Extract Pixel block
		float[][] pixelBlock = new float[fp.getWidth()][fp.getHeight()];
		for (int x = pixelBlock.length; x-- > 0;)
		{
			for (int y = pixelBlock[0].length; y-- > 0;)
			{
				if (Float.isNaN(fp.getf(x, y)))
				{
					pixelBlock[x][y] = -1;
				}
				else
				{
					pixelBlock[x][y] = fp.getf(x, y);
				}
			}
		}

		// Extracted as per the code in Jpiv2.PivUtils:
		int x = 0, y = 0, w = fp.getWidth(), h = fp.getHeight();
		int[] iCoord = new int[2];
		double[] dCoord = new double[2];
		// This will weight the function more towards the centre of the correlation pixels.
		// I am not sure if this is necessary.
		//pixelBlock = divideByWeightingFunction(pixelBlock, x, y, w, h);
		iCoord = getPeak(pixelBlock);
		dCoord = gaussianPeakFit(pixelBlock, iCoord[0], iCoord[1]);
		double[] ret = null;
		// more or less acceptable peak fit
		if (Math.abs(dCoord[0] - iCoord[0]) < w / 2 && Math.abs(dCoord[1] - iCoord[1]) < h / 2)
		{
			dCoord[0] += x;
			dCoord[1] += y;
			// Jpiv block is in [Y,X] format (not [X,Y])
			ret = new double[] { dCoord[1], dCoord[0] };

			//    		IJ.log(String.format("Fitted x %d -> %g   y %d -> %g",  
			//    				i, ret[0],
			//    				j, ret[1]));
		}
		return (ret);
	}

	/**
	 * Divides the correlation matrix by a pyramid weighting function.
	 * 
	 * @param subCorrMat
	 *            The biased correlation function
	 * @param xOffset
	 *            If this matrix is merely a search area within a larger
	 *            correlation matrix, this is the offset of the search area.
	 * @param yOffset
	 *            If this matrix is merely a search area within a larger
	 *            correlation matrix, this is the offset of the search area.
	 * @param w
	 *            Width of the original correlation matrix.
	 * @param h
	 *            Height of the original correlation matrix.
	 * @return The corrected correlation function
	 */
	@SuppressWarnings("unused")
	private static float[][] divideByWeightingFunction(float[][] subCorrMat, int xOffset, int yOffset, int w, int h)
	{
		for (int i = 0; i < subCorrMat.length; i++)
		{
			for (int j = 0; j < subCorrMat[0].length; j++)
			{
				subCorrMat[i][j] = subCorrMat[i][j] *
						(Math.abs(j + xOffset - w / 2) / w * 2 + Math.abs(i + yOffset - h / 2) / h * 2 + 1);
			}
		}
		return subCorrMat;
	}

	/**
	 * Finds the highest value in a correlation matrix.
	 * 
	 * @param subCorrMat
	 *            A single correlation matrix.
	 * @return The indices of the highest value {i,j} or {y,x}.
	 */
	private static int[] getPeak(float[][] subCorrMat)
	{
		int[] coord = new int[2];
		float peakValue = 0;
		for (int i = 0; i < subCorrMat.length; ++i)
		{
			for (int j = 0; j < subCorrMat[0].length; ++j)
			{
				if (subCorrMat[i][j] > peakValue)
				{
					peakValue = subCorrMat[i][j];
					coord[0] = j;
					coord[1] = i;
				}
			}
		}
		return (coord);
	}

	/**
	 * Gaussian peak fit.
	 * See Raffel, Willert, Kompenhans;
	 * Particle Image Velocimetry;
	 * 3rd printing;
	 * S. 131
	 * for details
	 * 
	 * @param subCorrMat
	 *            some two dimensional data containing a correlation peak
	 * @param x
	 *            the horizontal peak position
	 * @param y
	 *            the vertical peak position
	 * @return a double array containing the peak position
	 *         with sub pixel accuracy
	 */
	private static double[] gaussianPeakFit(float[][] subCorrMat, int x, int y)
	{
		double[] coord = new double[2];
		// border values
		if (x == 0 || x == subCorrMat[0].length - 1 || y == 0 || y == subCorrMat.length - 1)
		{
			coord[0] = x;
			coord[1] = y;
		}
		else
		{
			coord[0] = x +
					(Math.log(subCorrMat[y][x - 1]) - Math.log(subCorrMat[y][x + 1])) /
					(2 * Math.log(subCorrMat[y][x - 1]) - 4 * Math.log(subCorrMat[y][x]) + 2 * Math
							.log(subCorrMat[y][x + 1]));
			coord[1] = y +
					(Math.log(subCorrMat[y - 1][x]) - Math.log(subCorrMat[y + 1][x])) /
					(2 * Math.log(subCorrMat[y - 1][x]) - 4 * Math.log(subCorrMat[y][x]) + 2 * Math
							.log(subCorrMat[y + 1][x]));
		}
		return (coord);
	}

	private double calculateScore(ImageProcessor refIp, ImageProcessor maskIp, ImageProcessor targetIp, int xShift,
			int yShift)
	{
		// Same dimensions at current
		double sumX = 0;
		double sumXY = 0;
		double sumXX = 0;
		double sumYY = 0;
		double sumY = 0;
		int n = 0;
		float ch1;
		float ch2;

		int refMax = getBitClippedMax(refIp);
		int targetMax = getBitClippedMax(targetIp);

		//int width = targetIp.getWidth();
		//int height = targetIp.getHeight();

		// Set the bounds for the search in the reference image:
		// - x,y needs to be within reference
		// - x,y shifted needs to be within target

		// Bounds of the reference image
		Rectangle bRef = new Rectangle(0, 0, refIp.getWidth(), refIp.getHeight());

		// This is the smallest of the two images.
		Rectangle region = new Rectangle(0, 0, 
				Math.min(refIp.getWidth(), targetIp.getWidth()), 
				Math.min(refIp.getHeight(), targetIp.getHeight()));

		// Shift the region
		region.x += xShift;
		region.y += yShift;

		// Constrain search to the reference coordinates that overlap the region
		Rectangle intersect = bRef.intersection(region);

		int xMin, xMax, yMin, yMax;

		xMin = intersect.x;
		xMax = intersect.x + intersect.width;

		yMin = intersect.y;
		yMax = intersect.y + intersect.height;

		for (int y = yMax; y-- > yMin;)
		{
			int y2 = y - yShift;
			//if (y2 < 0 || y2 >= height)
			//	continue;

			for (int x = xMax; x-- > xMin;)
			{
				// Check if this is within the mask
				if (maskIp == null || maskIp.get(x, y) > 0)
				{
					int x2 = x - xShift;
					//if (x2 < 0 || x2 >= width)
					//	continue;

					ch1 = refIp.getf(x, y);
					ch2 = targetIp.getf(x2, y2);

					// Ignore clipped values
					if (ch1 == 0 || ch1 == refMax || ch2 == 0 || ch2 == targetMax)
						continue;

					sumX += ch1;
					sumXY += (ch1 * ch2);
					sumXX += (ch1 * ch1);
					sumYY += (ch2 * ch2);
					sumY += ch2;

					n++;
				}
			}
		}

		double r = Double.NaN;

		//IJ.log(String.format("%d  %g  %g  %g  %g  %g", n, sumX, sumY, sumXY, sumXX, sumYY));

		if (n > 0)
		{
			double pearsons1 = sumXY - (1.0 * sumX * sumY / n);
			double pearsons2 = sumXX - (1.0 * sumX * sumX / n);
			double pearsons3 = sumYY - (1.0 * sumY * sumY / n);

			if (pearsons2 == 0 || pearsons3 == 0)
			{
				// If there is data and all the variances are the same then correlation is perfect
				if (sumXX == sumYY && sumXX == sumXY && sumXX > 0)
				{
					r = 1;
				}
				else
				{
					r = 0;
				}
			}
			else
			{
				r = pearsons1 / (Math.sqrt(pearsons2 * pearsons3));
			}

			// Note:
			// If the reference image is centred and the target image is normalised then the result
			// of the top half of the Pearson correlation equation will be the correlation produced by 
			// the Align_Images_FFT without normalisation.
			//r = pearsons1;

			//IJ.log(String.format("%g  %g  %g  %g", pearsons1, pearsons2, pearsons3, r));
		}

		return r;
	}

	private int getBitClippedMax(ImageProcessor ip)
	{
		int max = (int) ip.getMax();
		// Check for bit clipped maximum values
		for (int bit : new int[] { 8, 16, 10, 12 })
		{
			int bitClippedMax = (int) (Math.pow(2.0, bit) - 1);
			if (max == bitClippedMax)
				return max;
		}
		return Integer.MAX_VALUE;
	}
}
