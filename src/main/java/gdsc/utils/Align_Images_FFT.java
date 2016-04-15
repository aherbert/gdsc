package gdsc.utils;

import java.awt.Rectangle;
import java.util.ArrayList;

import gdsc.UsageTracker;
import gdsc.core.ij.AlignImagesFFT;
import gdsc.core.ij.AlignImagesFFT.SubPixelMethod;
import gdsc.core.ij.AlignImagesFFT.WindowMethod;
import ij.IJ;
import ij.ImagePlus;
import ij.WindowManager;
import ij.gui.GenericDialog;
import ij.plugin.PlugIn;
import ij.process.ImageProcessor;

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
	public static final String[] windowFunctions;
	private static int myWindowFunction = 3;
	private static boolean restrictTranslation = false;
	private static int myMinXShift = -20, myMaxXShift = 20;
	private static int myMinYShift = -20, myMaxYShift = 20;
	public static final String[] subPixelMethods;
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

	static
	{
		WindowMethod[] m = AlignImagesFFT.WindowMethod.values();
		windowFunctions = new String[m.length];
		for (int i = 0; i < m.length; i++)
			windowFunctions[i] = m[i].toString();

		SubPixelMethod[] m2 = AlignImagesFFT.SubPixelMethod.values();
		subPixelMethods = new String[m2.length];
		for (int i = 0; i < m2.length; i++)
			subPixelMethods[i] = m2[i].toString();
	}

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
		ImagePlus targetImp = WindowManager.getImage(target);

		Rectangle bounds = null;
		if (restrictTranslation)
		{
			bounds = createBounds(myMinXShift, myMaxXShift, myMinYShift, myMaxYShift);
		}

		AlignImagesFFT align = new AlignImagesFFT();
		ImagePlus alignedImp = align.align(refImp, targetImp, WindowMethod.values()[myWindowFunction], bounds,
				SubPixelMethod.values()[subPixelMethod], interpolationMethod, normalised, showCorrelationImage,
				showNormalisedImage, clipOutput);

		if (alignedImp != null)
			alignedImp.show();
	}

	private String[] getImagesList()
	{
		// Find the currently open images
		ArrayList<String> newImageList = new ArrayList<String>();

		for (int id : gdsc.core.ij.Utils.getIDList())
		{
			ImagePlus imp = WindowManager.getImage(id);

			// Image must be 8-bit/16-bit
			if (imp != null && (imp.getType() == ImagePlus.GRAY8 || imp.getType() == ImagePlus.GRAY16 ||
					imp.getType() == ImagePlus.GRAY32))
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

		gd.addMessage(
				"Align target image stack to a reference using\ncorrelation in the frequency domain. Edge artifacts\ncan be reduced using a window function or by\nrestricting the translation.");

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
	

	public static Rectangle createBounds(int minXShift, int maxXShift, int minYShift, int maxYShift)
	{
		int w = maxXShift - minXShift;
		int h = maxYShift - minYShift;
		Rectangle bounds = new Rectangle(minXShift, minYShift, w, h);
		return bounds;
	}
}
