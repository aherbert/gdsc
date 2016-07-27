package gdsc.foci;

import java.awt.AWTEvent;
import java.awt.Label;

import org.apache.commons.math3.exception.NumberIsTooSmallException;
import org.apache.commons.math3.stat.correlation.PearsonsCorrelation;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.apache.commons.math3.stat.inference.TestUtils;

import gdsc.UsageTracker;
import gdsc.core.threshold.AutoThreshold;
import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.WindowManager;
import ij.gui.DialogListener;
import ij.gui.GenericDialog;
import ij.gui.Roi;
import ij.measure.ResultsTable;
import ij.plugin.filter.ExtendedPlugInFilter;
import ij.plugin.filter.GaussianBlur;
import ij.plugin.filter.ParticleAnalyzer;
import ij.plugin.filter.PlugInFilterRunner;
import ij.process.Blitter;
import ij.process.ByteProcessor;
import ij.process.ImageProcessor;
import ij.text.TextWindow;

/**
 * Analyses the intensity of each channel within the brightest spot of the
 * selected channel.
 * <P>
 * Identifies cells using thresholding. For each cell the region of the brightest spot is identified. The intensity of
 * each channel inside and outside the spot are then compared.
 */
public class SpotAnalyser implements ExtendedPlugInFilter, DialogListener
{
	public static final String TITLE = "Spot Analyser";
	private static String[] maskOptions = new String[] { "Threshold", "Use ROI" };

	private static final int INSIDE = 0;
	private static final int OUTSIDE = 1;

	private int flags = DOES_16 + DOES_8G + SNAPSHOT + FINAL_PROCESSING;
	private ImagePlus imp;
	private ImageProcessor maskIp;
	private Label label;
	private double min = 0, max = 0;
	private int spotChannel = 0, thresholdChannel = 0;
	private int noOfParticles = 0;
	private boolean containsRoiMask = false;

	private static TextWindow results = null;
	private static boolean writeHeader = true;

	private static int maskOption = 0;
	private static double blur = 3;
	private static String thresholdMethod = AutoThreshold.Method.OTSU.name;
	private static double minSize = 50;
	private static boolean showParticles = false;
	private static int maxPeaks = 1;
	private static double fraction = 0.9;
	private static boolean showSpots = false;

	/*
	 * (non-Javadoc)
	 * 
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
		ImageProcessor ip = imp.getProcessor();
		if (arg.equals("final"))
		{
			analyseImage();
			return DONE;
		}
		min = ip.getMin();
		max = ip.getMax();
		return flags;
	}

	private void resetImage()
	{
		ImageProcessor ip = imp.getProcessor();
		ip.reset();
		ip.setMinAndMax(min, max);
		imp.updateAndDraw();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see ij.plugin.filter.ExtendedPlugInFilter#showDialog(ij.ImagePlus,
	 * java.lang.String, ij.plugin.filter.PlugInFilterRunner)
	 */
	public int showDialog(ImagePlus imp, String command, PlugInFilterRunner pfr)
	{
		GenericDialog gd = new GenericDialog(TITLE);

		spotChannel = imp.getChannel();
		thresholdChannel = spotChannel;
		Roi roi = imp.getRoi();
		if (roi != null && roi.isArea())
		{
			containsRoiMask = true;
			gd.addChoice("Threshold_Channel", maskOptions, maskOptions[maskOption]);

			// Set up the mask using the ROI
			maskIp = new ByteProcessor(imp.getWidth(), imp.getHeight());
			maskIp.setColor(255);
			maskIp.fill(roi);
			maskIp.setThreshold(0, 254, ImageProcessor.NO_LUT_UPDATE);
		}

		String[] channels = new String[imp.getNChannels()];
		for (int i = 0; i < channels.length; i++)
			channels[i] = Integer.toString(i + 1);

		gd.addChoice("Threshold_Channel", channels, channels[thresholdChannel - 1]);
		gd.addSlider("Blur", 0.01, 5, blur);
		gd.addChoice("Threshold_method", AutoThreshold.getMethods(), thresholdMethod);
		gd.addChoice("Spot_Channel", channels, channels[spotChannel - 1]);
		gd.addSlider("Min_size", 50, 10000, minSize);
		gd.addCheckbox("Show_particles", showParticles);
		gd.addSlider("Max_peaks", 1, 10, maxPeaks);
		gd.addSlider("Fraction", 0.01, 1, fraction);
		gd.addCheckbox("Show_spots", showSpots);
		gd.addMessage("");
		label = (Label) gd.getMessage();

		gd.addHelp(gdsc.help.URL.UTILITY);
		gd.addPreviewCheckbox(pfr);
		gd.addDialogListener(this);
		gd.showDialog();

		if (gd.wasCanceled() || !dialogItemChanged(gd, null))
			return DONE;

		return flags;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see ij.gui.DialogListener#dialogItemChanged(ij.gui.GenericDialog,
	 * java.awt.AWTEvent)
	 */
	public boolean dialogItemChanged(GenericDialog gd, AWTEvent e)
	{
		if (containsRoiMask)
			maskOption = gd.getNextChoiceIndex();
		thresholdChannel = gd.getNextChoiceIndex() + 1;
		blur = gd.getNextNumber();
		if (gd.invalidNumber())
			return false;
		thresholdMethod = gd.getNextChoice();
		spotChannel = gd.getNextChoiceIndex() + 1;
		minSize = gd.getNextNumber();
		if (gd.invalidNumber())
			minSize = 50;
		showParticles = gd.getNextBoolean();
		maxPeaks = (int) gd.getNextNumber();
		if (gd.invalidNumber() || maxPeaks < 1)
			maxPeaks = 1;
		fraction = gd.getNextNumber();
		if (gd.invalidNumber())
			fraction = 0.9;
		showSpots = gd.getNextBoolean();

		// Preview checkbox will be null if running headless
		if (gd.getPreviewCheckbox() != null && !gd.getPreviewCheckbox().getState())
		{
			// Reset preview
			label.setText("");
			resetImage();
		}

		return true;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see ij.plugin.filter.ExtendedPlugInFilter#setNPasses(int)
	 */
	public void setNPasses(int nPasses)
	{
		// Do nothing
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see ij.plugin.filter.PlugInFilter#run(ij.process.ImageProcessor)
	 */
	public void run(ImageProcessor inputIp)
	{
		// Just run the particle analyser
		noOfParticles = 0;

		// Avoid destructively modifying the image. Work on the selected channel for thresholding
		int index = imp.getStackIndex(thresholdChannel, imp.getSlice(), imp.getFrame());
		ImageProcessor ip = imp.getImageStack().getProcessor(index).duplicate();

		if (!checkData(ip))
		{
			IJ.error(TITLE, "Channel has no data range");
			resetImage();
			return;
		}

		if (containsRoiMask && maskOption == 1)
		{
			ip = maskIp.duplicate();
			ip.setThreshold(254, 255, ImageProcessor.NO_LUT_UPDATE);
		}
		else
		{
			// Blur the image
			if (blur > 0)
			{
				GaussianBlur gb = new GaussianBlur();
				gb.blurGaussian(ip, blur, blur, 0.002);
			}

			// Threshold
			int t = AutoThreshold.getThreshold(thresholdMethod, ip.getHistogram());
			ip.setThreshold(t, 65536, ImageProcessor.NO_LUT_UPDATE);
		}

		// Analyse particles
		ImagePlus particlesImp = new ImagePlus(imp.getTitle() + " Particles", ip);
		int analyserOptions = ParticleAnalyzer.SHOW_ROI_MASKS + ParticleAnalyzer.IN_SITU_SHOW +
				ParticleAnalyzer.EXCLUDE_EDGE_PARTICLES;
		int measurements = 0;
		ResultsTable rt = new ResultsTable();

		double maxSize = Double.POSITIVE_INFINITY;
		ParticleAnalyzer pa = new ParticleAnalyzer(analyserOptions, measurements, rt, minSize, maxSize);
		pa.analyze(particlesImp, ip);

		ImageProcessor particlesIp = particlesImp.getProcessor();
		noOfParticles = rt.getCounter();
		if (label != null)
			label.setText(String.format("%d particle%s", noOfParticles, (noOfParticles == 1) ? "" : "s"));

		// Show the particles
		inputIp.copyBits(particlesIp, 0, 0, Blitter.COPY);
		inputIp.setMinAndMax(0, noOfParticles);
		imp.updateAndDraw();
	}

	private boolean checkData(ImageProcessor ip)
	{
		final int firstValue = ip.get(0);
		for (int i = 1; i < ip.getPixelCount(); i++)
		{
			if (ip.get(i) != firstValue)
				return true;
		}
		return false;
	}

	private void analyseImage()
	{

		if (noOfParticles == 0)
		{
			resetImage(); // Restore the original image
			return;
		}

		// The particles have already been identified in the run() method.
		// Copy the pixels.
		ImageProcessor particlesIp = imp.getProcessor().duplicate();

		resetImage(); // Restore the original image

		// Get the channel for spot analysis
		ImagePlus tmpImp;
		{
			int index = imp.getStackIndex(spotChannel, imp.getSlice(), imp.getFrame());
			ImageProcessor ip = imp.getImageStack().getProcessor(index);
			tmpImp = new ImagePlus("Dummy", ip);
		}

		if (showParticles)
		{
			ImageProcessor tmpIp = particlesIp.duplicate();
			tmpIp.setMinAndMax(0, noOfParticles);
			showImage(this.imp.getTitle() + " Particles", tmpIp);
		}

		ImageProcessor spotsIp = particlesIp.createProcessor(particlesIp.getWidth(), particlesIp.getHeight());

		// For each particle:
		for (int particle = 1; particle <= noOfParticles; particle++)
		{
			// Run FindFoci to find the single highest spot
			FindFoci ff = new FindFoci();
			ImagePlus mask = createMask(particlesIp, particle);
			int backgroundMethod = FindFoci.BACKGROUND_MEAN;
			double backgroundParameter = 0;
			String autoThresholdMethod = "";
			int searchMethod = FindFoci.SEARCH_ABOVE_BACKGROUND;
			double searchParameter = 0;
			int minSize = 5;
			int peakMethod = FindFoci.PEAK_RELATIVE_ABOVE_BACKGROUND;
			double peakParameter = 0.5;
			int outputType = FindFoci.OUTPUT_MASK | FindFoci.OUTPUT_MASK_FRACTION_OF_HEIGHT |
					FindFoci.OUTPUT_MASK_NO_PEAK_DOTS;
			int sortIndex = FindFoci.SORT_MAX_VALUE;
			int options = FindFoci.OPTION_STATS_INSIDE;
			int centreMethod = FindFoci.CENTRE_MAX_VALUE_ORIGINAL;
			double centreParameter = 0;
			double fractionParameter = fraction;

			FindFociResult result = ff.findMaxima(tmpImp, mask, backgroundMethod, backgroundParameter, autoThresholdMethod,
					searchMethod, searchParameter, maxPeaks, minSize, peakMethod, peakParameter, outputType, sortIndex,
					options, blur, centreMethod, centreParameter, fractionParameter);
			if (result == null)
				continue;

			ImagePlus maximaImp = result.mask;
			ImageProcessor spotIp = maximaImp.getProcessor();

			// Renumber to the correct particle value
			for (int j = 0; j < spotIp.getPixelCount(); j++)
				if (spotIp.get(j) != 0)
					spotIp.set(j, particle);
			spotsIp.copyBits(spotIp, 0, 0, Blitter.ADD);
		}

		if (showSpots)
		{
			spotsIp.setMinAndMax(0, noOfParticles);
			showImage(this.imp.getTitle() + " Spots", spotsIp);
		}

		// Now we have:
		// particlesIp => Particles
		// spotsIp => The largest spot within each particle

		// Create a mask of the particles
		byte[] mask = new byte[particlesIp.getPixelCount()];
		for (int i = 0; i < particlesIp.getPixelCount(); i++)
		{
			if (particlesIp.get(i) != 0)
				mask[i] = 1;
		}

		// Subtract the spots from the particles
		particlesIp.copyBits(spotsIp, 0, 0, Blitter.SUBTRACT);

		createResultsWindow();

		// Create a statistical summary for [channel][inside/outside][particle]
		DescriptiveStatistics[][][] stats = new DescriptiveStatistics[imp.getNChannels() + 1][2][noOfParticles + 1];

		ImageStack stack = imp.getImageStack();
		for (int channel = 1; channel <= imp.getNChannels(); channel++)
		{
			int index = imp.getStackIndex(channel, imp.getSlice(), imp.getFrame());
			ImageProcessor channelIp = stack.getProcessor(index);

			for (int particle = 0; particle <= noOfParticles; particle++)
			{
				stats[channel][INSIDE][particle] = new DescriptiveStatistics();
				stats[channel][OUTSIDE][particle] = new DescriptiveStatistics();
			}

			for (int i = 0; i < mask.length; i++)
			{
				if (mask[i] != 0)
				{
					int v = channelIp.get(i);
					stats[channel][INSIDE][spotsIp.get(i)].addValue(v);
					stats[channel][OUTSIDE][particlesIp.get(i)].addValue(v);
				}
			}
		}

		// Add the counts inside and outside
		for (int particle = 1; particle <= noOfParticles; particle++)
		{
			// Just choose the first channel (all are the same)
			addResult(particle, stats[1][INSIDE][particle].getN(), stats[1][OUTSIDE][particle].getN());
		}

		// Add the statistics inside and outside for each channel
		for (int channel = 1; channel <= imp.getNChannels(); channel++)
		{
			for (int particle = 1; particle <= noOfParticles; particle++)
			{
				addResult(channel, particle, stats);
			}
		}
	}

	private ImagePlus showImage(String title, ImageProcessor ip)
	{
		ImagePlus imp = WindowManager.getImage(title);
		if (imp == null)
		{
			imp = new ImagePlus(title, ip);
			imp.show();
		}
		else
		{
			imp.setProcessor(ip);
			imp.updateAndDraw();
		}
		return imp;
	}

	/**
	 * Zero all pixels that are not the given value
	 * 
	 * @param ip
	 * @param value
	 * @return
	 */
	private ImagePlus createMask(ImageProcessor ip, int value)
	{
		ip = ip.duplicate();
		for (int i = 0; i < ip.getPixelCount(); i++)
		{
			if (ip.get(i) != value)
				ip.set(i, 0);
		}
		return new ImagePlus("Mask", ip);
	}

	private void createResultsWindow()
	{
		if (!java.awt.GraphicsEnvironment.isHeadless())
		{
			if (results == null || !results.isShowing())
			{
				results = new TextWindow(TITLE + " Results",
						"Image\tChannel\tParticle\tInside Sum\tAv\tSD\tR\tOutside Sum\tAv\tSD\tR\tIncrease\tp-value",
						"", 800, 600);
				results.setVisible(true);
			}
		}
		else
		{
			if (writeHeader)
			{
				writeHeader = false;
				IJ.log("Image\tChannel\tParticle\tInside Sum\tAv\tSD\tR\tOutside Sum\tAv\tSD\tR\tIncrease\tp-value");
			}
		}
	}

	private void addResult(int particle, long countInside, long countOutside)
	{
		StringBuilder sb = new StringBuilder();
		sb.append(imp.getTitle()).append("\t\t");
		sb.append(particle).append("\t");
		sb.append(countInside).append("\t\t\t\t");
		sb.append(countOutside).append("\t\t\t\t");
		if (java.awt.GraphicsEnvironment.isHeadless())
		{
			IJ.log(sb.toString());
		}
		else
		{
			results.append(sb.toString());
		}
	}

	private void addResult(int channel, int particle, DescriptiveStatistics[][][] stats)
	{
		StringBuilder sb = new StringBuilder();
		sb.append(imp.getTitle()).append("\t");
		sb.append(channel).append("\t");
		sb.append(particle).append("\t");

		double sx = stats[channel][INSIDE][particle].getSum();
		double sd = stats[channel][INSIDE][particle].getStandardDeviation();
		long n = stats[channel][INSIDE][particle].getN();
		double av = sx / n;
		double sx2 = stats[channel][OUTSIDE][particle].getSum();
		double sd2 = stats[channel][OUTSIDE][particle].getStandardDeviation();
		long n2 = stats[channel][OUTSIDE][particle].getN();
		double av2 = sx2 / n2;

		double p = 0;
		try
		{
			p = TestUtils.tTest(stats[channel][INSIDE][particle], stats[channel][OUTSIDE][particle]);
		}
		catch (NumberIsTooSmallException e)
		{
			// Ignore
		}

		// Correlate inside & outside spot with the principle channel
		double r = 1;
		double r2 = 1;
		if (channel != spotChannel) // Principle channel => No test required
		{
			r = new PearsonsCorrelation().correlation(stats[channel][INSIDE][particle].getValues(),
					stats[spotChannel][INSIDE][particle].getValues());
			r2 = new PearsonsCorrelation().correlation(stats[channel][OUTSIDE][particle].getValues(),
					stats[spotChannel][OUTSIDE][particle].getValues());
		}

		sb.append(IJ.d2s(sx, 0)).append("\t").append(IJ.d2s(av, 2)).append("\t").append(IJ.d2s(sd, 2)).append("\t")
				.append(IJ.d2s(r, 3)).append("\t");
		sb.append(IJ.d2s(sx2, 0)).append("\t").append(IJ.d2s(av2, 2)).append("\t").append(IJ.d2s(sd2, 2)).append("\t")
				.append(IJ.d2s(r2, 3)).append("\t");
		sb.append(IJ.d2s(av / av2, 2)).append("\t");
		sb.append(String.format("%.3g", p));

		if (java.awt.GraphicsEnvironment.isHeadless())
		{
			IJ.log(sb.toString());
		}
		else
		{
			results.append(sb.toString());
		}
	}
}
