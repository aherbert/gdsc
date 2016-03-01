package gdsc.utils;

import gdsc.ImageJTracker;
import gdsc.threshold.Auto_Threshold;
import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.WindowManager;
import ij.gui.GenericDialog;
import ij.plugin.HyperStackReducer;
import ij.plugin.PlugIn;
import ij.plugin.ZProjector;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;
import ij.process.ShortProcessor;

import java.awt.Rectangle;
import java.util.ArrayList;

/**
 * Aligns open image stacks to a reference stack using XY translation to maximise the correlation. Takes in:
 * 
 * - The reference image
 * - Z-stack projection (maximum/average)
 * - Optional Max/Min values for the X and Y translation
 * - Optional sub-pixel accuracy
 * 
 * Accepts multi-dimensional stacks. For each timepoint a maximum/average intensity projection
 * is performed per channel. The channels are tiled to a composite image. The composite is then
 * aligned using the maximum correlation between the images. The translation is applied to the
 * entire stack for that timepoint.
 */
public class Align_Stacks implements PlugIn
{
	private static final String TITLE = "Align Stacks";
	private static final String SELF_ALIGN = "selfAlign";

	private static String reference = "";
	private static boolean selfAlign = false;
	private static int projectionMethod = ZProjector.MAX_METHOD;
	private static int myWindowFunction = 3; // Tukey
	private static boolean restrictTranslation = false;
	private static int minXShift = -20, maxXShift = 20;
	private static int minYShift = -20, maxYShift = 20;
	private String[] subPixelMethods = Align_Images_FFT.subPixelMethods;
	private static int subPixelMethod = 2;
	private String[] methods = Align_Images_FFT.methods;
	private static int interpolationMethod = ImageProcessor.NONE;
	private static boolean clipOutput = false;

	/** Ask for parameters and then execute. */
	public void run(String arg)
	{
		ImageJTracker.recordPlugin(this.getClass(), arg);
		
		String[] imageList = getImagesList();

		if (imageList.length < 1)
		{
			IJ.showMessage("Error", "Only 8-bit and 16-bit images are supported (2 images required)");
			return;
		}

		boolean selfAlignMode = (arg != null && arg.equals(SELF_ALIGN));

		if (!showDialog(imageList, selfAlignMode))
			return;

		ImagePlus refImp = WindowManager.getImage(reference);
		if (refImp == null)
		{
			IJ.log("Failed to find: " + reference);
			return;
		}

		Rectangle bounds = null;
		if (restrictTranslation)
		{
			bounds = createBounds(minXShift, maxXShift, minYShift, maxYShift);
		}

		if (selfAlign)
		{
			exec(refImp, refImp, projectionMethod, myWindowFunction, bounds, subPixelMethod, interpolationMethod,
					clipOutput);
		}
		else
		{
			for (ImagePlus targetImp : getTargetImages(refImp))
			{
				exec(refImp, targetImp, projectionMethod, myWindowFunction, bounds, subPixelMethod,
						interpolationMethod, clipOutput);
			}
		}
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

	private boolean showDialog(String[] imageList, boolean selfAlignMode)
	{
		GenericDialog gd = new GenericDialog(TITLE);

		if (selfAlignMode)
		{
			if (WindowManager.getCurrentImage() != null)
				reference = WindowManager.getCurrentImage().getTitle();
			selfAlign = true;
			//projectionMethod = ZProjector.MAX_METHOD;
			//myWindowFunction = 3; // Tukey
			//restrictTranslation = true; // Translation is restricted to image half-max anyway
			//minXShift = minYShift = -20;
			//maxXShift = maxYShift = 20;
			interpolationMethod = ImageProcessor.NONE;
			clipOutput = false;
		}

		String msg = (selfAlignMode) ? "Align all frames to the current frame." : "Align all open stacks with the same XYC dimensions as the reference.";
		msg += "\nZ stacks per time frame are projected and multiple channels\nare tiled to a single image used to define the offset for\nthe frame.\n";
		if (!selfAlignMode)
			msg += "Optionally align a single stack to the currect frame";
		gd.addMessage(msg);
			

		gd.addChoice("Reference_image", imageList, reference);
		if (!selfAlignMode)
			gd.addCheckbox("Self-align", selfAlign);
		gd.addChoice("Projection", ZProjector.METHODS, ZProjector.METHODS[projectionMethod]);
		gd.addChoice("Window_function", Align_Images_FFT.windowFunctions,
				Align_Images_FFT.windowFunctions[myWindowFunction]);
		gd.addCheckbox("Restrict_translation", restrictTranslation);
		gd.addNumericField("Min_X_translation", minXShift, 0);
		gd.addNumericField("Max_X_translation", maxXShift, 0);
		gd.addNumericField("Min_Y_translation", minYShift, 0);
		gd.addNumericField("Max_Y_translation", maxYShift, 0);
		gd.addChoice("Sub-pixel_method", subPixelMethods, subPixelMethods[subPixelMethod]);
		gd.addChoice("Interpolation", methods, methods[interpolationMethod]);
		gd.addCheckbox("Clip_output", clipOutput);
		gd.addHelp(gdsc.help.URL.UTILITY);

		gd.showDialog();

		if (!gd.wasOKed())
			return false;

		reference = gd.getNextChoice();
		if (!selfAlignMode)
			selfAlign = gd.getNextBoolean();
		projectionMethod = gd.getNextChoiceIndex();
		myWindowFunction = gd.getNextChoiceIndex();
		restrictTranslation = gd.getNextBoolean();
		minXShift = (int) gd.getNextNumber();
		maxXShift = (int) gd.getNextNumber();
		minYShift = (int) gd.getNextNumber();
		maxYShift = (int) gd.getNextNumber();
		subPixelMethod = gd.getNextChoiceIndex();
		interpolationMethod = gd.getNextChoiceIndex();
		clipOutput = gd.getNextBoolean();

		return true;
	}

	private ArrayList<ImagePlus> getTargetImages(ImagePlus referenceImp)
	{
		ArrayList<ImagePlus> imageList = new ArrayList<ImagePlus>();

		int[] referenceDimensions = referenceImp.getDimensions();

		for (int id : WindowManager.getIDList())
		{
			ImagePlus imp = WindowManager.getImage(id);

			// Image must be the same type and dimensions
			if (imp != null && imp.getType() == referenceImp.getType() && imp.getID() != referenceImp.getID() &&
					isMatchingDimensions(referenceDimensions, imp.getDimensions()))
			{
				imageList.add(imp);
			}
		}

		return imageList;
	}

	private boolean isMatchingDimensions(int[] referenceDimensions, int[] dimensions)
	{
		// Allow aligning a stack to an image of the same dimensions XYC if the reference has only one frame
		if (referenceDimensions[4] == 1)
		{
			for (int i = 0; i < 3; i++)
				if (dimensions[i] != referenceDimensions[i])
					return false;
			return true;
		}

		// Check for complete match
		for (int i = 0; i < referenceDimensions.length; i++)
			if (dimensions[i] != referenceDimensions[i])
				return false;
		return true;
	}

	public void exec(ImagePlus refImp, ImagePlus targetImp, int projectionMethod, int windowFunction, Rectangle bounds,
			int subPixelMethod, int interpolationMethod, boolean clipOutput)
	{
		// Check same size
		if (!isValid(refImp, targetImp))
			return;

		// Store initial positions
		int c1 = refImp.getChannel();
		int z1 = refImp.getSlice();
		int t1 = refImp.getFrame();
		int c2 = targetImp.getChannel();
		int z2 = targetImp.getSlice();
		int t2 = targetImp.getFrame();

		if (refImp.getNFrames() == 1 || selfAlign)
		{
			ImageProcessor ip1 = createComposite(refImp, t1, projectionMethod, windowFunction);
			ImagePlus imp1 = new ImagePlus("ip1", ip1);
			Align_Images_FFT align = new Align_Images_FFT();
			align.setDoTranslation(false);
			align.init(imp1, windowFunction, true);

			// For each time point
			for (int frame = 1; frame <= targetImp.getNFrames(); frame++)
			{
				if (selfAlign && frame == t1)
					continue;

				IJ.showProgress(frame, targetImp.getNFrames());

				// Build composite image for the timepoint
				ImageProcessor ip2 = createComposite(targetImp, frame, projectionMethod, windowFunction);

				// Align the image
				ImagePlus imp2 = new ImagePlus("ip2", ip2);
				align.align(imp2, 0, bounds, subPixelMethod, interpolationMethod, clipOutput);

				// Transform original stack
				ImageStack stack = targetImp.getImageStack();
				for (int channel = 1; channel <= targetImp.getNChannels(); channel++)
				{
					for (int slice = 1; slice <= targetImp.getNSlices(); slice++)
					{
						int index = targetImp.getStackIndex(channel, slice, frame);
						ImageProcessor ip = stack.getProcessor(index);
						Align_Images.translateProcessor(interpolationMethod, ip, align.getLastXOffset(),
								align.getLastYOffset(), clipOutput);
					}
				}

				if (ImageJHelper.isInterrupted())
					return;
			}
		}
		else
		{
			// For each time point
			for (int frame = 1; frame <= targetImp.getNFrames(); frame++)
			{
				IJ.showProgress(frame, targetImp.getNFrames());

				// Build composite image for the timepoint
				ImageProcessor ip1 = createComposite(refImp, frame, projectionMethod, windowFunction);
				ImageProcessor ip2 = createComposite(targetImp, frame, projectionMethod, windowFunction);

				// Align the image
				ImagePlus imp1 = new ImagePlus("ip1", ip1);
				ImagePlus imp2 = new ImagePlus("ip2", ip2);
				Align_Images_FFT align = new Align_Images_FFT();
				align.exec(imp1, imp2, 0, bounds, subPixelMethod, interpolationMethod, true, false, false, clipOutput);

				// Transform original stack
				ImageStack stack = targetImp.getImageStack();
				for (int channel = 1; channel <= targetImp.getNChannels(); channel++)
				{
					for (int slice = 1; slice <= targetImp.getNSlices(); slice++)
					{
						int index = targetImp.getStackIndex(channel, slice, frame);
						ImageProcessor ip = stack.getProcessor(index);
						Align_Images.translateProcessor(interpolationMethod, ip, align.getLastXOffset(),
								align.getLastYOffset(), clipOutput);
					}
				}

				if (ImageJHelper.isInterrupted())
					return;
			}
		}

		// Reset input images
		refImp.setPosition(c1, z1, t1);
		targetImp.setPosition(c2, z2, t2);
		targetImp.updateAndDraw();
	}

	public static Rectangle createBounds(int minXShift, int maxXShift, int minYShift, int maxYShift)
	{
		int w = maxXShift - minXShift;
		int h = maxYShift - minYShift;
		Rectangle bounds = new Rectangle(minXShift, minYShift, w, h);
		return bounds;
	}

	private boolean isValid(ImagePlus refImp, ImagePlus targetImp)
	{
		if (refImp == null || targetImp == null)
			return false;
		if (refImp.getType() != targetImp.getType())
			return false;
		return isMatchingDimensions(refImp.getDimensions(), targetImp.getDimensions());
	}

	private ImageProcessor createComposite(ImagePlus imp, int frame, int projectionMethod, int windowFunction)
	{
		// Extract the channels using the specified projection method
		FloatProcessor[] tiles = new FloatProcessor[imp.getNChannels()];
		for (int channel = 1; channel <= imp.getNChannels(); channel++)
		{
			tiles[channel - 1] = extractTile(imp, frame, channel, projectionMethod);
			tiles[channel - 1] = Align_Images_FFT.applyWindowSeparable(tiles[channel - 1], windowFunction);

			// Normalise so each image contributes equally to the alignment
			normalise(tiles[channel - 1]);
		}

		// Build a composite image
		int w = imp.getWidth();
		int h = imp.getHeight();

		// Calculate total dimensions by tiling always along the smallest dimension
		// to produce the smallest possible output image.
		int w2 = w;
		int h2 = h;
		int horizontalTiles = 1;
		int verticalTiles = 1;
		do
		{
			while (w2 <= h2 && horizontalTiles * verticalTiles < imp.getNChannels())
			{
				horizontalTiles++;
				w2 += w;
			}
			while (h2 <= w2 && horizontalTiles * verticalTiles < imp.getNChannels())
			{
				verticalTiles++;
				h2 += h;
			}
		} while (horizontalTiles * verticalTiles < imp.getNChannels());

		// Create output composite
		FloatProcessor ip = new FloatProcessor(w2, h2);

		for (int channel = 1; channel <= imp.getNChannels(); channel++)
		{
			int x = (channel - 1) % horizontalTiles;
			int y = (channel - 1) / horizontalTiles;

			ip.insert(tiles[channel - 1], x * w, y * h);
		}

		return ip;
	}

	private FloatProcessor extractTile(ImagePlus imp, int frame, int channel, int projectionMethod)
	{
		return ImageJHelper.extractTile(imp, frame, channel, projectionMethod).toFloat(1, null);
	}

	/**
	 * Normalises the image. Performs a thresholding on the image using the Otsu method.
	 * Then scales the pixels above the threshold from 0 to 255.
	 * 
	 * @param ip
	 *            The input image
	 */
	private void normalise(FloatProcessor ip)
	{
		float[] pixels = (float[]) ip.getPixels();

		ShortProcessor sp = (ShortProcessor) ip.convertToShort(true);
		int[] data = sp.getHistogram();
		int threshold = Auto_Threshold.Otsu(data);
		float minf = (float) threshold;
		float maxf = (float) sp.getMax();

		float scaleFactor = 255.0f / (maxf - minf);

		for (int i = pixels.length; i-- > 0;)
		{
			pixels[i] = (Math.max((float) sp.get(i), minf) - minf) * scaleFactor;
		}
	}

	/**
	 * Extracts the specified frame from the hyperstack
	 * 
	 * @param imp
	 * @param frame
	 * @return
	 */
	public static ImagePlus extractFrame(ImagePlus imp, int frame)
	{
		imp.setPositionWithoutUpdate(1, 1, frame);

		// Extract the timepoint into a new stack
		HyperStackReducer reducer = new HyperStackReducer(imp);
		int channels = imp.getNFrames();
		int slices = imp.getNSlices();
		ImagePlus imp1 = imp.createHyperStack("", channels, slices, 1, imp.getBitDepth());
		reducer.reduce(imp1);

		return imp1;
	}

	/**
	 * Run the plugin with self-alignment parameters
	 */
	public static void selfAlign()
	{
		new Align_Stacks().run(SELF_ALIGN);
	}
}
