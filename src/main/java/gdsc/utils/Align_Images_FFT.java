package gdsc.utils;

import java.awt.Rectangle;
import java.util.ArrayList;

import gdsc.ImageJTracker;
import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.WindowManager;
import ij.gui.GenericDialog;
import ij.plugin.PlugIn;
import ij.process.FHT;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;
import ij.util.Tools;

/**
 * Aligns an image stack to a reference image using XY translation to maximise the correlation. Takes in:
 * <ul>
 * <li>The reference image
 * <li>The image/stack to align.
 * <li>Optional Max/Min values for the X and Y translation
 * <li>Window function to reduce edge artifacts in frequency space
 * </ul>
 * <p>
 * The alignment is calculated using the maximum correlation between the images. The correlation is computed using the
 * frequency domain (note that conjugate multiplication in the frequency domain is equivalent to correlation in the
 * space domain).
 * <p>
 * Output new stack with the best alignment with optional sub-pixel accuracy.
 * <p>
 * By default restricts translation so that at least half of the smaller image width/height is within the larger image
 * (half-max translation). This can be altered by providing a translation bounds. Note that when using normalised
 * correlation all scores are set to zero outside the half-max translation due to potential floating-point summation
 * error during normalisation.
 */
// This code is based on the Fast Normalised Cross-Correlation algorithm by J.P.Lewis
// http://scribblethink.org/Work/nvisionInterface/nip.pdf
public class Align_Images_FFT implements PlugIn
{
	private static final String TITLE = "Align Images FFT";
	public static final String[] windowFunctions = new String[] { "None", "Hanning", "Cosine", "Tukey" };
	private static int myWindowFunction = 3;
	private static boolean restrictTranslation = false;
	private static int myMinXShift = -20, myMaxXShift = 20;
	private static int myMinYShift = -20, myMaxYShift = 20;
	public static final String[] subPixelMethods = new String[] { "None", "Cubic", "Gaussian" };
	private static int subPixelMethod = 2;
	public static final String[] methods = ImageProcessor.getInterpolationMethods();
	private static int interpolationMethod = ImageProcessor.NONE;

	private static final String NONE = "[Reference stack]";
	private static String reference = "";
	private static String target = "";
	private static boolean normalised = true;
	private static boolean showCorrelationImage = false;
	private static boolean showNormalisedImage = false;
	private static boolean clipOutput = false;

	private double lastXOffset = 0;
	private double lastYOffset = 0;
	private boolean doTranslation = true;

	/** Ask for parameters and then execute. */
	public void run(String arg)
	{
		ImageJTracker.recordPlugin(this.getClass(), arg);
		
		String[] imageList = getImagesList();

		if (imageList.length < 1)
		{
			IJ.showMessage("Error", "Only 8-bit and 16-bit images are supported");
			return;
		}

		if (!showDialog(imageList))
			return;

		ImagePlus refImp = WindowManager.getImage(reference);
		ImagePlus targetImp = WindowManager.getImage(target);

		Rectangle bounds = null;
		if (restrictTranslation)
		{
			bounds = createBounds(myMinXShift, myMaxXShift, myMinYShift, myMaxYShift);
		}

		ImagePlus alignedImp = exec(refImp, targetImp, myWindowFunction, bounds, subPixelMethod, interpolationMethod,
				normalised, showCorrelationImage, showNormalisedImage, clipOutput);

		if (alignedImp != null)
			alignedImp.show();
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
			reference = imageList[0];

		// Add option to have no target
		String[] targetList = new String[imageList.length + 1];
		targetList[0] = NONE;
		for (int i = 0; i < imageList.length; i++)
		{
			targetList[i + 1] = imageList[i];
		}
		if (!contains(targetList, target))
			target = targetList[0];

		gd.addMessage("Align target image stack to a reference using\ncorrelation in the frequency domain. Edge artifacts\ncan be reduced using a window function or by\nrestricting the translation.");

		gd.addChoice("Reference_image", imageList, reference);
		gd.addChoice("Target_image", targetList, target);
		gd.addChoice("Window_function", windowFunctions, windowFunctions[myWindowFunction]);
		gd.addCheckbox("Restrict_translation", restrictTranslation);
		gd.addNumericField("Min_X_translation", myMinXShift, 0);
		gd.addNumericField("Max_X_translation", myMaxXShift, 0);
		gd.addNumericField("Min_Y_translation", myMinYShift, 0);
		gd.addNumericField("Max_Y_translation", myMaxYShift, 0);
		gd.addChoice("Sub-pixel_method", subPixelMethods, subPixelMethods[subPixelMethod]);
		gd.addChoice("Interpolation", methods, methods[interpolationMethod]);
		gd.addCheckbox("Normalised", normalised);
		gd.addCheckbox("Show_correlation_image", showCorrelationImage);
		gd.addCheckbox("Show_normalised_image", showNormalisedImage);
		gd.addCheckbox("Clip_output", clipOutput);
		gd.addHelp(gdsc.help.URL.UTILITY);

		gd.showDialog();

		if (gd.wasCanceled())
			return false;

		reference = gd.getNextChoice();
		target = gd.getNextChoice();
		myWindowFunction = gd.getNextChoiceIndex();
		restrictTranslation = gd.getNextBoolean();
		myMinXShift = (int) gd.getNextNumber();
		myMaxXShift = (int) gd.getNextNumber();
		myMinYShift = (int) gd.getNextNumber();
		myMaxYShift = (int) gd.getNextNumber();
		subPixelMethod = gd.getNextChoiceIndex();
		interpolationMethod = gd.getNextChoiceIndex();
		normalised = gd.getNextBoolean();
		showCorrelationImage = gd.getNextBoolean();
		showNormalisedImage = gd.getNextBoolean();
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

	public ImagePlus exec(ImagePlus refImp, ImagePlus targetImp, int windowFunction, int minXShift, int maxXShift,
			int minYShift, int maxYShift, int subPixelMethod, int interpolationMethod, boolean normalised,
			boolean showCorrelationImage, boolean showNormalisedImage, boolean clipOutput)
	{
		if (maxXShift < minXShift || maxYShift < minYShift)
			return null;
		Rectangle bounds = createBounds(minXShift, maxXShift, minYShift, maxYShift);
		return exec(refImp, targetImp, windowFunction, bounds, subPixelMethod, interpolationMethod, normalised,
				showCorrelationImage, showNormalisedImage, clipOutput);
	}

	// This is used for debugging the normalisation
	private FloatProcessor normalisedRefIp;
	// The location where the reference/target was inserted into the normalised FFT image
	private Rectangle refImageBounds = new Rectangle();
	private Rectangle targetImageBounds = new Rectangle();

	/**
	 * Aligns all images in the target stack to the current processor in the reference.
	 * <p>
	 * If no target is provided then all slices are aligned to the current processor in the reference.
	 * 
	 * @param refImp
	 * @param targetImp
	 * @param windowFunction
	 * @param bounds
	 * @param subPixelMethod
	 * @param interpolationMethod
	 * @param normalised
	 * @param showCorrelationImage
	 * @param showNormalisedImage
	 * @param clipOutput
	 * @return
	 */
	public ImagePlus exec(ImagePlus refImp, ImagePlus targetImp, int windowFunction, Rectangle bounds,
			int subPixelMethod, int interpolationMethod, boolean normalised, boolean showCorrelationImage,
			boolean showNormalisedImage, boolean clipOutput)
	{
		ImageProcessor refIp = refImp.getProcessor();
		if (targetImp == null)
			targetImp = refImp;

		// Check same size
		if (!isValid(refIp, targetImp))
			return null;

		// Fourier transforms use the largest power-two dimension that covers both images
		int maxN = Math.max(refIp.getWidth(), refIp.getHeight());
		int maxM = Math.max(targetImp.getWidth(), targetImp.getHeight());
		maxN = Math.max(maxN, maxM);

		this.normalisedRefIp = padAndZero(refIp, maxN, windowFunction, refImageBounds);
		if (showNormalisedImage)
			new ImagePlus(refImp.getTitle() + " Normalised Ref", normalisedRefIp).show();
		maxN = normalisedRefIp.getWidth(); // Update with the power-two result

		// Set up the output stack
		ImageStack outStack = new ImageStack(targetImp.getWidth(), targetImp.getHeight());
		ImageStack correlationStack = null;
		ImageStack normalisedStack = null;
		FloatProcessor fpCorrelation = null;
		FloatProcessor fpNormalised = null;
		if (showCorrelationImage)
		{
			correlationStack = new ImageStack(maxN, maxN);
			fpCorrelation = new FloatProcessor(maxN, maxN);
		}
		if (showNormalisedImage)
		{
			normalisedStack = new ImageStack(maxN, maxN);
			fpNormalised = new FloatProcessor(maxN, maxN);
		}

		// Subtract mean to normalise the numerator of the cross-correlation.
		// ---
		// The effectively normalises the numerator of the correlation but does not address the denominator.
		// The denominator should be calculated using rolling sums for each offset position.
		// See: http://www.idiom.com/~zilla/Papers/nvisionInterface/nip.html
		// Following the computation of the correlation each offset (u,v) position should then be divided
		// by the energy of the reference image under the target image. This equates to:
		//   Sum(x,y) [ f(x,y) - f_(u,v) ]^2
		// where f_(u,v) is the mean of the region under the target feature
		// ---

		// Calculate rolling sum of squares
		double[] s = null;
		double[] ss = null;
		if (normalised)
		{
			s = new double[normalisedRefIp.getPixelCount()];
			ss = new double[s.length];
			calculateRollingSums(normalisedRefIp, s, ss);
		}

		FHT refFHT = fft(normalisedRefIp, maxN);

		if (bounds == null)
		{
			bounds = createHalfMaxBounds(refImp.getWidth(), refImp.getHeight(), targetImp.getWidth(),
					targetImp.getHeight());
		}

		// Process each image in the target stack
		ImageStack stack = targetImp.getStack();
		for (int slice = 1; slice <= stack.getSize(); slice++)
		{
			ImageProcessor targetIp = stack.getProcessor(slice);
			outStack.addSlice(
					null,
					alignImages(refFHT, s, ss, targetIp, slice, windowFunction, bounds, fpCorrelation, fpNormalised,
							subPixelMethod, interpolationMethod, clipOutput));
			if (showCorrelationImage)
				correlationStack.addSlice(null, fpCorrelation.duplicate());
			if (showNormalisedImage)
				normalisedStack.addSlice(null, fpNormalised.duplicate());
			if (ImageJHelper.isInterrupted())
				return null;
		}

		if (showCorrelationImage)
			new ImagePlus(targetImp.getTitle() + " Correlation", correlationStack).show();
		if (showNormalisedImage)
			new ImagePlus(targetImp.getTitle() + " Normalised Target", normalisedStack).show();

		return new ImagePlus(targetImp.getTitle() + " Aligned", outStack);
	}

	private ImageProcessor refIp = null;
	private double[] s = null;
	private double[] ss = null;
	private FHT refFHT = null;

	/**
	 * Initialises the reference image for batch alignment. All target images should be equal or smaller than the
	 * reference.
	 * 
	 * @param refImp
	 * @param windowFunction
	 */
	public void init(ImagePlus refImp, int windowFunction, boolean normalised)
	{
		refIp = null;
		s = null;
		ss = null;
		refFHT = null;

		if (refImp == null || refImp.getProcessor() == null || noValue(refImp.getProcessor()))
			return;

		refIp = refImp.getProcessor();

		// Fourier transforms use the largest power-two dimension that covers both images
		int maxN = Math.max(refIp.getWidth(), refIp.getHeight());

		this.normalisedRefIp = padAndZero(refIp, maxN, windowFunction, refImageBounds);

		// Subtract mean to normalise the numerator of the cross-correlation.
		// ---
		// The effectively normalises the numerator of the correlation but does not address the denominator.
		// The denominator should be calculated using rolling sums for each offset position.
		// See: http://www.idiom.com/~zilla/Papers/nvisionInterface/nip.html
		// Following the computation of the correlation each offset (u,v) position should then be divided
		// by the energy of the reference image under the target image. This equates to:
		//   Sum(x,y) [ f(x,y) - f_(u,v) ]^2
		// where f_(u,v) is the mean of the region under the target feature
		// ---

		// Calculate rolling sum of squares
		s = null;
		ss = null;
		if (normalised)
		{
			s = new double[normalisedRefIp.getPixelCount()];
			ss = new double[s.length];
			calculateRollingSums(normalisedRefIp, s, ss);
		}

		refFHT = fft(normalisedRefIp, maxN);
	}

	/**
	 * Aligns all images in the target stack to the pre-initialised reference.
	 * 
	 * @param targetImp
	 * @param windowFunction
	 * @param bounds
	 * @param subPixelMethod
	 * @param interpolationMethod
	 * @param showCorrelationImage
	 * @param showNormalisedImage
	 * @param clipOutput
	 * @return
	 */
	public ImagePlus align(ImagePlus targetImp, int windowFunction, Rectangle bounds, int subPixelMethod,
			int interpolationMethod, boolean clipOutput)
	{
		if (refFHT == null || targetImp == null)
			return null;

		int maxN = refFHT.getWidth();

		// Check correct size
		if (targetImp.getWidth() > maxN || targetImp.getHeight() > maxN)
			return null;

		// Set up the output stack
		ImageStack outStack = new ImageStack(targetImp.getWidth(), targetImp.getHeight());

		if (bounds == null)
		{
			bounds = createHalfMaxBounds(refIp.getWidth(), refIp.getHeight(), targetImp.getWidth(),
					targetImp.getHeight());
		}

		// Process each image in the target stack
		ImageStack stack = targetImp.getStack();
		for (int slice = 1; slice <= stack.getSize(); slice++)
		{
			ImageProcessor targetIp = stack.getProcessor(slice);
			outStack.addSlice(
					null,
					alignImages(refFHT, s, ss, targetIp, slice, windowFunction, bounds, null, null, subPixelMethod,
							interpolationMethod, clipOutput));
			if (ImageJHelper.isInterrupted())
				return null;
		}

		return new ImagePlus(targetImp.getTitle() + " Aligned", outStack);
	}

	private void calculateRollingSums(FloatProcessor ip, double[] s_, double[] ss)
	{
		// Compute the rolling sum and sum of squares
		// s(u,v) = f(u,v) + s(u-1,v) + s(u,v-1) - s(u-1,v-1) 
		// ss(u,v) = f(u,v) * f(u,v) + ss(u-1,v) + ss(u,v-1) - ss(u-1,v-1)
		// where s(u,v) = ss(u,v) = 0 when either u,v < 0

		int maxx = ip.getWidth();
		int maxy = ip.getHeight();
		float[] originalData = (float[]) ip.getPixels();
		double[] data = Tools.toDouble(originalData);

		// First row
		double cs_ = 0; // Column sum
		double css = 0; // Column sum-squares
		for (int i = 0; i < maxx; i++)
		{
			cs_ += data[i];
			css += data[i] * data[i];
			s_[i] = cs_;
			ss[i] = css;
		}

		//		// Remaining rows
		//		for (int y = 1; y < maxy; y++)
		//		{
		//			// First column
		//			int i = y * maxx;
		//			s_[i] = s_[i - maxx] + data[i];
		//			ss[i] = ss[i - maxx] + data[i] * data[i];
		//			i++;
		//
		//			// Remaining columns
		//			for (int x = 1; x < maxx; x++, i++)
		//			{
		//				s_[i] = s_[i - 1] + s_[i - maxx] - s_[i - maxx - 1] + data[i];
		//				ss[i] = ss[i - 1] + ss[i - maxx] - ss[i - maxx - 1] + data[i] * data[i];
		//			}
		//		}

		// Remaining rows:
		// sum = rolling sum of row + sum of row above
		for (int y = 1; y < maxy; y++)
		{
			int i = y * maxx;
			cs_ = 0;
			css = 0;

			// Remaining columns
			for (int x = 0; x < maxx; x++, i++)
			{
				cs_ += data[i];
				css += data[i] * data[i];

				s_[i] = s_[i - maxx] + cs_;
				ss[i] = ss[i - maxx] + css;
			}
		}

		//		float[] sum = Tools.toFloat(s_);
		//		float[] sumsq = Tools.toFloat(ss);
		//		new ImagePlus("Sum", new FloatProcessor(maxx, maxy, sum, null)).show();
		//		new ImagePlus("SumSq", new FloatProcessor(maxx, maxy, sumsq, null)).show();
	}

	/**
	 * Normalise the correlation matrix using the standard deviation of the region from the reference that is covered by
	 * the target
	 * 
	 * @param subCorrMat
	 * @param s
	 * @param ss
	 * @param targetIp
	 */
	private void normalise(FloatProcessor subCorrMat, double[] s, double[] ss, ImageProcessor targetIp)
	{
		int maxx = subCorrMat.getWidth();
		int maxy = subCorrMat.getHeight();
		Rectangle imageBounds = new Rectangle(0, 0, maxx, maxy); //refImageBounds;

		int NU = targetIp.getWidth();
		int NV = targetIp.getHeight();

		// Locate where the target image was inserted when padding
		int x = targetImageBounds.x; // (maxx - NU) / 2;
		int y = targetImageBounds.y; // (maxy - NV) / 2;

		//IJ.log(String.format("maxx=%d,  maxy=%d, NU=%d, NV=%d, x=%d, y=%d", maxx, maxy, NU, NV, x, y));

		// Calculate overlap:
		// Assume a full size target image relative to the reference and then compensate with the insert location
		int halfNU = maxx / 2 - x;
		int halfNV = maxy / 2 - y;

		// Normalise within the bounds of the largest image (i.e. only allow translation 
		// up to half of the longest edge from the reference or target).
		// The further the translation from the half-max translation the more likely there 
		// can be errors in the normalisation score due to floating point summation errors. 
		// This is observed mainly at the very last pixel overlap between images.
		// To see this set: 
		// union = imageBounds;
		// TODO - More analysis to determine under what conditions this occurs.
		Rectangle union = refImageBounds.union(targetImageBounds);

		// Normalise using the denominator
		float[] data = (float[]) subCorrMat.getPixels();
		float[] newData = new float[data.length];
		for (int yyy = union.y; yyy < union.y + union.height; yyy++)
		{
			int i = yyy * maxx + union.x;
			for (int xxx = union.x; xxx < union.x + union.width; xxx++, i++)
			{
				double sum = 0;
				double sumSquares = 0;

				int minU = xxx - halfNU - 1;
				int maxU = Math.min(minU + NU, maxx - 1);
				int minV = yyy - halfNV - 1;
				int maxV = Math.min(minV + NV, maxy - 1);

				// Compute sum from rolling sum using:
				// sum(u,v) = 
				// + s(u+N-1,v+N-1) 
				// - s(u-1,v+N-1)
				// - s(u+N-1,v-1)
				// + s(u-1,v-1)
				// Note: 
				// s(u,v) = 0 when either u,v < 0
				// s(u,v) = s(umax,v) when u>umax
				// s(u,v) = s(u,vmax) when v>vmax
				// s(u,v) = s(umax,vmax) when u>umax,v>vmax
				// Likewise for ss

				// + s(u+N-1,v+N-1) 
				int index = maxV * maxx + maxU;
				sum += s[index];
				sumSquares += ss[index];

				if (minU >= 0)
				{
					// - s(u-1,v+N-1)
					index = maxV * maxx + minU;
					sum -= s[index];
					sumSquares -= ss[index];
				}
				if (minV >= 0)
				{
					// - s(u+N-1,v-1)
					index = minV * maxx + maxU;
					sum -= s[index];
					sumSquares -= ss[index];

					if (minU >= 0)
					{
						// + s(u-1,v-1)
						index = minV * maxx + minU;
						sum += s[index];
						sumSquares += ss[index];
					}
				}

				// Reset to bounds to calculate the number of pixels
				if (minU < 0)
					minU = 0;
				if (minV < 0)
					minV = 0;

				Rectangle regionBounds = new Rectangle(xxx - halfNU, yyy - halfNV, NU, NV);
				Rectangle r = imageBounds.intersection(regionBounds);

				//int n = (maxU - minU + 1) * (maxV - minV + 1);
				int n = r.width * r.height;

				if (n < 1)
					continue;

				// Get the sum of squared differences
				double residuals = sumSquares - sum * sum / n;

				//				// Check using the original data
				//				double sx = 0;
				//				double ssx = 0;
				//				int nn = 0;
				//				for (int yy = yyy - halfNV; yy < yyy - halfNV + NV; yy++)
				//					for (int xx = xxx - halfNU; xx < xxx - halfNU + NU; xx++)
				//					{
				//						if (xx >= 0 && xx < maxx && yy >= 0 && yy < maxy)
				//						{
				//							float value = normalisedRefIp.getf(xx, yy);
				//							sx += value;
				//							ssx += value * value;
				//							nn++;
				//						}
				//					}
				//				gdsc.fitting.utils.DoubleEquality eq = new gdsc.fitting.utils.DoubleEquality(8, 1e-16);
				//				if (n != nn)
				//				{
				//					System.out.printf("Wrong @ %d,%d %d <> %d\n", xxx, yyy, n, nn);
				//					residuals = ssx - sx * sx / nn;
				//				}
				//				else if (!eq.almostEqualComplement(sx, sum) || !eq.almostEqualComplement(ssx, sumSquares))
				//				{
				//					System.out.printf("Wrong @ %d,%d %g <> %g : %g <> %g\n", xxx, yyy, sx, sum, ssx, sumSquares);
				//					residuals = ssx - sx * sx / nn;
				//				}

				double normalisation = (residuals > 0) ? Math.sqrt(residuals) : 0;

				if (normalisation > 0)
				{
					newData[i] = (float) (data[i] / normalisation);
					// Watch out for normalisation errors which cause problems when displaying the image data.
					if (newData[i] < -1.1f)
						newData[i] = -1.1f;
					if (newData[i] > 1.1f)
						newData[i] = 1.1f;
				}
			}
		}
		subCorrMat.setPixels(newData);
	}

	public static Rectangle createHalfMaxBounds(int width1, int height1, int width2, int height2)
	{
		// Restrict translation so that at least half of the smaller image width/height 
		// is within the larger image (half-max translation)
		int maxx = Math.max(width1, width2);
		int maxy = Math.max(height1, height2);
		maxx /= 2;
		maxy /= 2;
		return new Rectangle(-maxx, -maxy, 2 * maxx, 2 * maxy);
	}

	public static Rectangle createBounds(int minXShift, int maxXShift, int minYShift, int maxYShift)
	{
		int w = maxXShift - minXShift;
		int h = maxYShift - minYShift;
		Rectangle bounds = new Rectangle(minXShift, minYShift, w, h);
		return bounds;
	}

	private boolean isValid(ImageProcessor refIp, ImagePlus targetImp)
	{
		if (refIp == null || targetImp == null)
			return false;

		// Check images have values. No correlation is possible with 
		if (noValue(refIp))
			return false;

		return true;
	}

	/**
	 * @param ip
	 * @return true if the image has not pixels with a value
	 */
	private boolean noValue(ImageProcessor ip)
	{
		for (int i = 0; i < ip.getPixelCount(); i++)
			if (ip.getf(i) != 0)
				return false;
		return true;
	}

	private ImageProcessor alignImages(FHT refFHT, double[] s, double[] ss, ImageProcessor targetIp, int slice,
			int windowFunction, Rectangle bounds, FloatProcessor fpCorrelation, FloatProcessor fpNormalised,
			int subPixelMethod, int interpolationMethod, boolean clipOutput)
	{
		lastXOffset = lastYOffset = 0;

		if (noValue(targetIp))
		{
			// Zero correlation with empty image
			IJ.log(String.format("Best Slice %d  x %g  y %g = %g", slice, 0, 0, 0));
			if (fpCorrelation != null)
				fpCorrelation.setPixels(new float[refFHT.getPixelCount()]);
			if (fpNormalised != null)
				fpNormalised.setPixels(new float[refFHT.getPixelCount()]);
			return targetIp.duplicate();
		}

		// Perform correlation analysis in Fourier space (A and B transform to F and G) 
		// using the complex conjugate of G multiplied by F:
		//   C(u,v) = F(u,v) G*(u,v)		

		int maxN = refFHT.getWidth();

		ImageProcessor paddedTargetIp = padAndZero(targetIp, maxN, windowFunction, targetImageBounds);
		FloatProcessor normalisedTargetIp = Align_Images.normalise(paddedTargetIp);
		FHT targetFHT = fft(normalisedTargetIp, maxN);
		FloatProcessor subCorrMat = correlate(refFHT, targetFHT);

		//new ImagePlus("Unnormalised correlation", subCorrMat.duplicate()).show();

		int originX = (maxN / 2);
		int originY = (maxN / 2);

		// Normalise using the denominator
		if (s != null)
			normalise(subCorrMat, s, ss, targetIp);

		// Copy back result images
		if (fpCorrelation != null)
			fpCorrelation.setPixels(subCorrMat.getPixels());
		if (fpNormalised != null)
			fpNormalised.setPixels(normalisedTargetIp.getPixels());

		Rectangle intersect = new Rectangle(0, 0, subCorrMat.getWidth(), subCorrMat.getHeight());

		// Restrict the translation
		if (bounds != null)
		{
			// Restrict bounds to image limits
			intersect = intersect.intersection(new Rectangle(originX + bounds.x, originY + bounds.y, bounds.width,
					bounds.height));
		}

		int[] iCoord = getPeak(subCorrMat, intersect.x, intersect.y, intersect.width, intersect.height);
		float scoreMax = subCorrMat.getf(iCoord[0], iCoord[1]);
		double[] dCoord = new double[] { iCoord[0], iCoord[1] };

		String estimatedScore = "";
		if (subPixelMethod > 0)
		{
			double[] centre = null;
			if (subPixelMethod == 1)
			{
				centre = Align_Images.performCubicFit(subCorrMat, iCoord[0], iCoord[1]);
			}
			else
			{
				// Perform sub-peak analysis using the method taken from Jpiv
				centre = Align_Images.performGaussianFit(subCorrMat, iCoord[0], iCoord[1]);
				// Check the centre has not moved too far
				if (!(Math.abs(dCoord[0] - iCoord[0]) < intersect.width / 2 && Math.abs(dCoord[1] - iCoord[1]) < intersect.height / 2))
				{
					centre = null;
				}
			}

			if (centre != null)
			{
				dCoord[0] = centre[0];
				dCoord[1] = centre[1];

				double score = subCorrMat.getBicubicInterpolatedPixel(centre[0], centre[1], subCorrMat);
				//				if (score < -1)
				//					score = -1;
				//				if (score > 1)
				//					score = 1;
				estimatedScore = String.format(" (interpolated score %g)", score);
			}
		}
		else if (IJ.debugMode)
		{
			// Used for debugging - Check if interpolation rounds to a different integer 
			double[] centre = Align_Images.performCubicFit(subCorrMat, iCoord[0], iCoord[1]);
			if (centre != null)
			{
				centre[0] = Math.round(centre[0]);
				centre[1] = Math.round(centre[1]);

				if (centre[0] != iCoord[0] || centre[1] != iCoord[1])
					IJ.log(String.format("Cubic rounded to different integer: %d,%d => %d,%d", iCoord[0], iCoord[1],
							(int) centre[0], (int) centre[1]));
			}

			centre = Align_Images.performGaussianFit(subCorrMat, iCoord[0], iCoord[1]);
			if (centre != null)
			{
				centre[0] = Math.round(centre[0]);
				centre[1] = Math.round(centre[1]);

				if (centre[0] != iCoord[0] || centre[1] != iCoord[1])
					IJ.log(String.format("Gaussian rounded to different integer: %d,%d => %d,%d", iCoord[0], iCoord[1],
							(int) centre[0], (int) centre[1]));
			}
		}

		// The correlation image is the size of the reference.
		// Offset from centre of reference
		lastXOffset = dCoord[0] - originX;
		lastYOffset = dCoord[1] - originY;

		IJ.log(String.format("Best Slice %d  x %g  y %g = %g%s", slice, lastXOffset, lastYOffset, scoreMax,
				estimatedScore));

		// Translate the result and crop to the original size
		if (!doTranslation)
			return targetIp;

		ImageProcessor resultIp = Align_Images.translate(interpolationMethod, targetIp, lastXOffset, lastYOffset,
				clipOutput);
		return resultIp;
	}

	private int[] getPeak(FloatProcessor subCorrMat, int minX, int minY, int w, int h)
	{
		int width = subCorrMat.getWidth();
		float max = Float.NEGATIVE_INFINITY;
		int maxi = 0;
		float[] data = (float[]) subCorrMat.getPixels();
		for (int y = minY; y < minY + h; y++)
			for (int x = 0, i = y * width + minX; x < w; x++, i++)
			{
				if (max < data[i])
				{
					max = data[i];
					maxi = i;
				}
			}
		return new int[] { maxi % width, maxi / width };
	}

	private FloatProcessor correlate(FHT refComplex, FHT targetComplex)
	{
		FHT fht = refComplex.conjugateMultiply(targetComplex);
		fht.setShowProgress(false);
		fht.inverseTransform();
		fht.swapQuadrants();
		fht.resetMinAndMax();
		ImageProcessor ip = fht;
		return ip.toFloat(0, null);
	}

	// The following Fast Fourier Transform routines have been extracted from the ij.plugins.FFT class
	FHT fft(ImageProcessor ip, int maxN)
	{
		FHT fht = new FHT(ip);
		fht.setShowProgress(false);
		fht.transform();
		return fht;
	}

	/**
	 * Centre image on zero, padding if necessary to next square power-two above the given max dimension.
	 * <p>
	 * Optionally apply a window function so the image blends smoothly to zero background.
	 * 
	 * @param ip
	 * @param maxN
	 * @return
	 */
	FloatProcessor padAndZero(ImageProcessor ip, int maxN, int windowFunction, Rectangle padBounds)
	{
		boolean pad = true;
		int i = 2;
		while (i < maxN)
			i *= 2;
		if (i == maxN && ip.getWidth() == maxN && ip.getHeight() == maxN)
		{
			pad = false;
		}
		maxN = i;

		// This should shift the image so it smoothly blends with a zero background
		// Ideally this would window the image so that the result has an average of zero with smooth edges transitions.
		// However this involves shifting the image and windowing. The average depends on both
		// and so would have to be solved iteratively.		

		if (windowFunction > 0)
		{
			// Use separable for speed. 
			//ip = applyWindow(ip, windowFunction);
			ip = applyWindowSeparable(ip, windowFunction);
		}

		// Get average
		double sum = 0;
		for (int ii = 0; ii < ip.getPixelCount(); ii++)
			sum += ip.getf(ii);
		double av = sum / ip.getPixelCount();

		// Create the result image
		FloatProcessor ip2 = new FloatProcessor(maxN, maxN);
		float[] data = (float[]) ip2.getPixels();

		padBounds.width = ip.getWidth();
		padBounds.height = ip.getHeight();
		if (pad)
		{
			// Place into middle of image => Correlation is centre-to-centre alignment
			int x = (maxN - ip.getWidth()) / 2;
			int y = (maxN - ip.getHeight()) / 2;

			padBounds.x = x;
			padBounds.y = y;

			for (int yy = 0, index = 0; yy < ip.getHeight(); yy++)
			{
				int ii = (yy + y) * maxN + x;
				for (int xx = 0; xx < ip.getWidth(); xx++, index++, ii++)
					data[ii] = (float) (ip.getf(index) - av);
			}
		}
		else
		{
			padBounds.x = 0;
			padBounds.y = 0;

			// Copy pixels
			for (int ii = 0; ii < ip.getPixelCount(); ii++)
				data[ii] = (float) (ip.getf(ii) - av);
		}

		return ip2;
	}

	/**
	 * @return the lastXOffset
	 */
	public double getLastXOffset()
	{
		return lastXOffset;
	}

	/**
	 * @return the lastYOffset
	 */
	public double getLastYOffset()
	{
		return lastYOffset;
	}

	/**
	 * Apply a window function to reduce edge artifacts.
	 * <p>
	 * Applied as two 1-dimensional window functions. Faster than the nonseparable form but has direction dependent
	 * corners.
	 * 
	 * @param ip
	 * @param windowFunction
	 * @return
	 */
	public static FloatProcessor applyWindowSeparable(ImageProcessor ip, int windowFunction)
	{
		int maxx = ip.getWidth();
		int maxy = ip.getHeight();
		double[] wx = null;
		double[] wy = null;

		switch (windowFunction)
		{
			case 1:
				wx = hanning(maxx);
				wy = hanning(maxy);
				break;
			case 2:
				wx = cosine(maxx);
				wy = cosine(maxy);
				break;
			case 3:
				wx = tukey(maxx, ALPHA);
				wy = tukey(maxy, ALPHA);
				break;
		}

		if (wx == null)
			return ip.toFloat(0, null);

		float[] data = new float[ip.getPixelCount()];

		// Calculate total signal of window function applied to image (t1).
		// Calculate total signal of window function applied to a flat signal of intensity 1 (t2).
		// Divide t1/t2 => Result is the mean shift for image so that the average is zero.

		double sumWindowFunction = 0;
		double sumImage = 0;
		for (int y = 0, i = 0; y < maxy; y++)
		{
			for (int x = 0; x < maxx; x++, i++)
			{
				double w = wx[x] * wy[y];
				sumWindowFunction += w;
				sumImage += ip.getf(i) * w;
			}
		}

		// Shift to zero
		double shift = sumImage / sumWindowFunction;
		//double sum = 0;
		for (int y = 0, i = 0; y < maxy; y++)
		{
			for (int x = 0; x < maxx; x++, i++)
			{
				double value = (ip.getf(i) - shift) * wx[x] * wy[y];
				//sum += value;
				data[i] = (float) value;
			}
		}

		return new FloatProcessor(maxx, maxy, data, null);
	}

	/**
	 * Apply a window function to reduce edge artifacts
	 * <p>
	 * Applied as a nonseparable form.
	 * 
	 * @param ip
	 * @param windowFunction
	 * @return
	 */
	public static FloatProcessor applyWindow(ImageProcessor ip, int windowFunction)
	{
		//if (true)
		//	return applyWindowSeparable(ip, windowFunction, duplicate);

		int maxx = ip.getWidth();
		int maxy = ip.getHeight();

		WindowFunction wf = null;
		switch (windowFunction)
		{
			case 1: //
				wf = instance.new Hanning();
				break;
			case 2:
				wf = instance.new Cosine();
				break;
			case 3:
				wf = instance.new Tukey(ALPHA);
		}

		if (wf == null)
			return ip.toFloat(0, null);

		float[] data = new float[ip.getPixelCount()];

		double cx = maxx * 0.5;
		double cy = maxy * 0.5;
		double maxDistance = Math.sqrt(maxx * maxx + maxy * maxy);

		// Calculate total signal of window function applied to image (t1).
		// Calculate total signal of window function applied to a flat signal of intensity 1 (t2).
		// Divide t1/t2 => Result is the mean shift for image so that the average is zero.

		double sumWindowFunction = 0;
		double sumImage = 0;
		for (int y = 0, i = 0; y < maxy; y++)
		{
			for (int x = 0; x < maxx; x++, i++)
			{
				double distance = Math.sqrt((x - cx) * (x - cx) + (y - cy) * (y - cy));
				double w = wf.weight(0.5 - (distance / maxDistance));
				sumWindowFunction += w;
				sumImage += ip.getf(i) * w;
			}
		}

		// Shift to zero
		double shift = sumImage / sumWindowFunction;
		//double sum = 0;
		for (int y = 0, i = 0; y < maxy; y++)
		{
			for (int x = 0; x < maxx; x++, i++)
			{
				double distance = Math.sqrt((x - cx) * (x - cx) + (y - cy) * (y - cy));
				double w = wf.weight(0.5 - (distance / maxDistance));
				double value = (ip.getf(i) - shift) * w;
				//sum += value;
				data[i] = (float) value;
			}
		}

		return new FloatProcessor(maxx, maxy, data, null);
	}

	private static Align_Images_FFT instance = new Align_Images_FFT();
	private static double ALPHA = 0.5;

	interface WindowFunction
	{
		/**
		 * Return the weight for the window at a fraction of the distance from the edge of the window.
		 * 
		 * @param fractionDistance
		 *            (range 0-1)
		 * @return
		 */
		double weight(double fractionDistance);
	}

	class Hanning implements WindowFunction
	{
		public double weight(double fractionDistance)
		{
			return 0.5 * (1 - Math.cos(Math.PI * 2 * fractionDistance));
		}
	}

	class Cosine implements WindowFunction
	{
		public double weight(double fractionDistance)
		{
			return Math.sin(Math.PI * fractionDistance);
		}
	}

	class Tukey implements WindowFunction
	{
		final double alpha;

		public Tukey(double alpha)
		{
			this.alpha = alpha;
		}

		public double weight(double fractionDistance)
		{
			if (fractionDistance < alpha / 2)
				return 0.5 * (1 + Math.cos(Math.PI * (2 * fractionDistance / alpha - 1)));
			if (fractionDistance > 1 - alpha / 2)
				return 0.5 * (1 + Math.cos(Math.PI * (2 * fractionDistance / alpha - 2 / alpha + 1)));
			return 1;
		}
	}

	// Should these be replaced with periodic functions as per use in spectral analysis:
	// http://en.wikipedia.org/wiki/Window_function

	private static double[] window(WindowFunction wf, int N)
	{
		double N_1 = N - 1;
		double[] w = new double[N];
		for (int i = 0; i < N; i++)
			w[i] = wf.weight(i / N_1);
		return w;
	}

	private static double[] hanning(int N)
	{
		return window(instance.new Hanning(), N);
	}

	private static double[] cosine(int N)
	{
		return window(instance.new Cosine(), N);
	}

	private static double[] tukey(int N, double alpha)
	{
		return window(instance.new Tukey(alpha), N);
	}

	/**
	 * @return if false the image will not be translated
	 */
	public boolean isDoTranslation()
	{
		return doTranslation;
	}

	/**
	 * Set to false to prevent the image processor from being translated. The translation can be retrieved using the
	 * lastOffset properties.
	 * 
	 * @param doTranslation
	 *            if false the image will not be translated
	 */
	public void setDoTranslation(boolean doTranslation)
	{
		this.doTranslation = doTranslation;
	}
}
