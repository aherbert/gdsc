package gdsc.utils;

import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.gui.GenericDialog;
import ij.gui.Plot;
import ij.gui.PlotWindow;
import ij.gui.Roi;
import ij.plugin.filter.ExtendedPlugInFilter;
import ij.plugin.filter.PlugInFilterRunner;
import ij.process.ImageProcessor;
import ij.text.TextWindow;
import ij.util.Tools;

import java.awt.Color;
import java.awt.Point;
import java.awt.Rectangle;
import java.util.Arrays;

import org.apache.commons.math3.analysis.polynomials.PolynomialFunction;
import org.apache.commons.math3.analysis.polynomials.PolynomialFunction.Parametric;
import org.apache.commons.math3.fitting.CurveFitter;
import org.apache.commons.math3.optim.nonlinear.vector.jacobian.LevenbergMarquardtOptimizer;
import org.apache.commons.math3.stat.descriptive.SummaryStatistics;

/**
 * Analyses the ROI across a stack of exposures. Exposures must be set within the slice labels.
 */
public class IntensityAnalysis implements ExtendedPlugInFilter
{
	final static String TITLE = "Intensity Analysis";
	final int flags = DOES_8G + DOES_16 + NO_CHANGES + DOES_STACKS + PARALLELIZE_STACKS + FINAL_PROCESSING;
	private static int window = 4;
	private static int bitDepth = 16;
	private static boolean debug = false;

	private static TextWindow results;

	private int commonIndex;
	private ImagePlus imp;
	private PlugInFilterRunner pfr;
	private ImageStack stack;
	private float[] exposures;
	private float[] means;
	private float saturated;
	private Rectangle bounds;
	private boolean[] mask;
	private int n;

	/*
	 * (non-Javadoc)
	 * 
	 * @see ij.plugin.filter.PlugInFilter#setup(java.lang.String, ij.ImagePlus)
	 */
	public int setup(String arg, ImagePlus imp)
	{
		if ("final".equals(arg))
		{
			showResults();
			return DONE;
		}

		if (imp == null || imp.getStackSize() == 1)
		{
			IJ.error(TITLE, "Require an input stack");
			return DONE;
		}

		Roi roi = imp.getRoi();
		if (roi == null || !roi.isArea())
		{
			IJ.error(TITLE, "Require an area ROI");
			return DONE;
		}

		// Get the stack and the slice labels
		stack = imp.getImageStack();
		if (imp.getNDimensions() > 3)
		{
			IJ.error(TITLE, "Require a 3D stack (not a hyper-stack)");
			return DONE;
		}

		// Try to determine the common prefix to the slice labels
		String master = stack.getSliceLabel(1);
		// Find the first index where the labels are different
		int index = 0;
		OUTER: while (index < master.length())
		{
			final char c = master.charAt(index);
			for (int i = 2; i <= stack.getSize(); i++)
			{
				if (c != stack.getSliceLabel(i).charAt(index))
					break OUTER;
			}
			index++;
		}
		if (index == master.length())
		{
			IJ.error(TITLE, "Unable to determine common prefix within slice labels");
			return DONE;
		}
		commonIndex = index;

		return flags;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see ij.plugin.filter.ExtendedPlugInFilter#showDialog(ij.ImagePlus, java.lang.String,
	 * ij.plugin.filter.PlugInFilterRunner)
	 */
	public int showDialog(ImagePlus imp, String command, PlugInFilterRunner pfr)
	{
		this.imp = imp;
		this.pfr = pfr;

		bitDepth = Math.min(bitDepth, imp.getBitDepth());

		// Get the user options
		GenericDialog gd = new GenericDialog(TITLE);
		gd.addMessage("Calculate the normalised intensity within an ROI.\nImages should have a linear response with respect to exposure.");
		gd.addSlider("Window", 3, stack.getSize(), window);
		gd.addSlider("Bit_depth", 4, imp.getBitDepth(), bitDepth);
		gd.addCheckbox("Debug", debug);
		gd.showDialog();
		if (gd.wasCanceled())
			return DONE;

		window = Math.abs((int) gd.getNextNumber());
		bitDepth = Math.abs((int) gd.getNextNumber());
		debug = gd.getNextBoolean();

		if (debug)
			debug("Prefix = %s\n", stack.getSliceLabel(1).substring(0, commonIndex));

		// Extract the exposure times from the labels
		exposures = new float[stack.getSize()];
		for (int i = 1; i <= stack.getSize(); i++)
		{
			String label = stack.getSliceLabel(i);
			// Find the first digit
			int startIndex = commonIndex;
			while (startIndex < label.length())
			{
				if (Character.isDigit(label.charAt(startIndex)))
					break;
				startIndex++;
			}
			if (startIndex == label.length())
			{
				IJ.error(TITLE, "Unable to determine exposure for slice label: " + label);
				return DONE;
			}
			// Move along until no more characters that could be a float value are found
			int endIndex = startIndex + 1;
			while (endIndex < label.length())
			{
				switch (label.charAt(endIndex))
				{
					case '0':
					case '1':
					case '2':
					case '3':
					case '4':
					case '5':
					case '6':
					case '7':
					case '8':
					case '9':
					case 'e':
					case 'E':
					case 'f':
					case 'F':
					case 'x':
					case '+':
					case '-':
						endIndex++;
						continue;
				}
				break;
			}
			try
			{
				exposures[i - 1] = Float.parseFloat(label.substring(startIndex, endIndex));
			}
			catch (NumberFormatException e)
			{
				IJ.error(TITLE, "Unable to determine exposure for slice label: " + label);
				return DONE;
			}
		}

		// Initialise for processing the ROI pixels
		Roi roi = imp.getRoi();
		bounds = roi.getBounds();
		ImageProcessor ip = roi.getMask();
		if (ip != null)
		{
			mask = new boolean[ip.getPixelCount()];
			n = 0;
			for (int i = 0; i < mask.length; i++)
				if (ip.get(i) != 0)
				{
					mask[i] = true;
					n++;
				}
		}
		else
		{
			n = bounds.width * bounds.height;
		}

		if (debug)
			debug("Exposures = %s ...\n", Arrays.toString(Arrays.copyOf(exposures, Math.min(10, exposures.length))));

		means = new float[exposures.length];
		saturated = (float) (Math.pow(2, bitDepth) - 1);

		return flags;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see ij.plugin.filter.PlugInFilter#run(ij.process.ImageProcessor)
	 */
	public void run(ImageProcessor ip)
	{
		// Process each slice to find the mean of the pixels in the ROI
		final int slice = pfr.getSliceNumber() - 1;

		boolean sat = false;

		double sum = 0;
		if (mask != null)
		{
			for (int y = 0, i = 0; y < bounds.height; y++)
			{
				int index = (y + bounds.y) * ip.getWidth() + bounds.x;
				for (int x = 0; x < bounds.width; x++, i++, index++)
				{
					if (mask[i])
					{
						if (ip.getf(index) >= saturated)
							sat = true;
						sum += ip.getf(index);
					}
				}
			}
		}
		else
		{
			for (int y = 0; y < bounds.height; y++)
			{
				int index = (y + bounds.y) * ip.getWidth() + bounds.x;
				for (int x = 0; x < bounds.width; x++, index++)
				{
					if (ip.getf(index) >= saturated)
						sat = true;
					sum += ip.getf(index);
				}
			}
		}

		// Use negative for means with saturated pixels
		means[slice] = ((sat) ? -1 : 1) * (float) (sum / n);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see ij.plugin.filter.ExtendedPlugInFilter#setNPasses(int)
	 */
	public void setNPasses(int nPasses)
	{
	}

	/**
	 * Show a plot of the mean for each slice against exposure. Perform linear fit on a sliding window and draw the best
	 * fit line on the plot.
	 */
	private void showResults()
	{
		int valid = 0;
		float[] means2 = new float[means.length];
		float[] exposures2 = new float[means.length];
		for (int i = 0; i < means.length; i++)
		{
			if (means[i] < 0)
			{
				debug("Saturated pixels in slice %d : %s", i + 1, stack.getShortSliceLabel(i + 1));
				means[i] = -means[i];
			}
			else
			{
				means2[valid] = means[i];
				exposures2[valid] = exposures[i];
				valid++;
			}
		}

		means2 = Arrays.copyOf(means2, valid);
		exposures2 = Arrays.copyOf(exposures2, valid);

		String title = TITLE;
		Plot plot = new Plot(title, "Exposure", "Mean");
		double[] a = Tools.getMinMax(exposures);
		double[] b = Tools.getMinMax(means);
		// Add some space to the limits for plotting
		double ra = (a[1] - a[0]) * 0.05;
		double rb = (b[1] - b[0]) * 0.05;
		plot.setLimits(a[0] - ra, a[1] + ra, b[0] - rb, b[1] + rb);
		plot.setColor(Color.blue);
		plot.addPoints(exposures, means, Plot.CIRCLE);
		PlotWindow pw = ImageJHelper.display(title, plot);

		// Report results to a table
		if (results == null || !results.isVisible())
		{
			results = new TextWindow(TITLE + " Summary",
					"Image\tx\ty\tw\th\tN\tStart\tEnd\tE1\tE2\tSS\tIntercept\tGradient", "", 800, 300);
			results.setVisible(true);
			if (ImageJHelper.isNewWindow())
			{
				Point p = results.getLocation();
				p.x = pw.getX();
				p.y = pw.getY() + pw.getHeight();
				results.setLocation(p);
			}
		}

		// Initialise result output
		StringBuilder sb = new StringBuilder();
		sb.append(imp.getTitle());
		sb.append('\t').append(bounds.x);
		sb.append('\t').append(bounds.y);
		sb.append('\t').append(bounds.width);
		sb.append('\t').append(bounds.height);
		sb.append('\t').append(n);

		if (means2.length < window)
		{
			IJ.error(TITLE, "Not enough unsaturated samples for the fit window: " + means2.length + " < " + window);
			addNullFitResult(sb);
		}
		else
		{

			// Do a linear fit using a sliding window. Find the region with the best linear fit.
			double bestSS = Double.POSITIVE_INFINITY;
			int bestStart = 0;
			double[] bestFit = null;
			for (int start = 0; start < means2.length; start++)
			{
				int end = start + window;
				if (end > means2.length)
					break;

				// Linear fit
				final CurveFitter<Parametric> fitter = new CurveFitter<Parametric>(new LevenbergMarquardtOptimizer());
				SummaryStatistics gradient = new SummaryStatistics();

				// Extract the data
				for (int i = start; i < end; i++)
				{
					fitter.addObservedPoint(exposures2[i], means2[i]);
					gradient.addValue(means2[i] / exposures2[i]);
				}

				if (gradient.getMean() > 0)
				{
					// Do linear regression to get diffusion rate
					final double[] init = { 0, gradient.getMean() }; // a + b x
					final double[] fit = fitter.fit(new PolynomialFunction.Parametric(), init);
					final PolynomialFunction fitted = new PolynomialFunction(fit);
					// Score the fit
					double ss = 0;
					for (int i = start; i < end; i++)
					{
						final double residual = fitted.value(exposures2[i]) - means2[i];
						ss += residual * residual;
					}
					debug("%d - %d = %f : y = %f + %f x t\n", start, end, ss, fit[0], fit[1]);
					// Store best fit
					if (ss < bestSS)
					{
						bestSS = ss;
						bestStart = start;
						bestFit = fit;
					}
				}
			}

			if (bestFit == null)
			{
				IJ.error(TITLE, "No valid linear fits");
				addNullFitResult(sb);
			}
			else
			{
				plot.setColor(Color.red);
				final PolynomialFunction fitted = new PolynomialFunction(bestFit);
				double x1 = exposures2[bestStart];
				double y1 = fitted.value(x1);
				double x2 = exposures2[bestStart + window - 1];
				double y2 = fitted.value(x2);
				plot.drawLine(x1, y1, x2, y2);
				pw = ImageJHelper.display(title, plot);

				sb.append('\t').append(bestStart + 1);
				sb.append('\t').append(bestStart + window);
				sb.append('\t').append(ImageJHelper.rounded(x1));
				sb.append('\t').append(ImageJHelper.rounded(x2));
				sb.append('\t').append(ImageJHelper.rounded(bestSS));
				sb.append('\t').append(ImageJHelper.rounded(bestFit[0]));
				sb.append('\t').append(ImageJHelper.rounded(bestFit[1]));
			}
		}

		results.append(sb.toString());
	}

	private void addNullFitResult(StringBuilder sb)
	{
		for (int i = 0; i < 7; i++)
			sb.append('\t');
	}

	private void debug(String format, Object... args)
	{
		if (debug)
			IJ.log(String.format(format, args));
	}
}