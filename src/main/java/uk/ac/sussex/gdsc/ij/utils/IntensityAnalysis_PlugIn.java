/*-
 * #%L
 * Genome Damage and Stability Centre ImageJ Plugins
 *
 * Software for microscopy image analysis
 * %%
 * Copyright (C) 2011 - 2026 Alex Herbert
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

package uk.ac.sussex.gdsc.ij.utils;

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
import java.util.concurrent.atomic.AtomicReference;
import org.apache.commons.math3.analysis.polynomials.PolynomialFunction;
import org.apache.commons.math3.fitting.PolynomialCurveFitter;
import org.apache.commons.math3.fitting.WeightedObservedPoints;
import org.apache.commons.math3.stat.regression.SimpleRegression;
import uk.ac.sussex.gdsc.core.ij.ImageJUtils;
import uk.ac.sussex.gdsc.core.ij.plugin.WindowOrganiser;
import uk.ac.sussex.gdsc.core.utils.MathUtils;
import uk.ac.sussex.gdsc.ij.UsageTracker;
import uk.ac.sussex.gdsc.ij.help.HelpUrls;

/**
 * Analyses the ROI across a stack of exposures. Exposures must be set within the slice labels.
 */
public class IntensityAnalysis_PlugIn implements ExtendedPlugInFilter {
  private static final String TITLE = "Intensity Analysis";
  private static final int FLAGS =
      DOES_8G + DOES_16 + NO_CHANGES + DOES_STACKS + PARALLELIZE_STACKS + FINAL_PROCESSING;

  private static AtomicReference<TextWindow> resultsRef = new AtomicReference<>();

  private int commonIndex;
  private ImagePlus imp;
  private PlugInFilterRunner pfr;
  private ImageStack stack;
  private float[] exposures;
  private float[] means;
  private float saturated;
  private Rectangle bounds;
  private boolean[] mask;
  private int pixelCount;

  /** The current settings for the plugin instance. */
  private Settings settings;

  /**
   * Contains the settings that are the re-usable state of the plugin.
   */
  private static class Settings {
    /** The last settings used by the plugin. This should be updated after plugin execution. */
    private static final AtomicReference<Settings> lastSettings =
        new AtomicReference<>(new Settings());

    int window = 4;
    int bitDepth = 16;
    boolean debug;

    /**
     * Default constructor.
     */
    Settings() {
      // Do nothing
    }

    /**
     * Copy constructor.
     *
     * @param source the source
     */
    private Settings(Settings source) {
      window = source.window;
      bitDepth = source.bitDepth;
      debug = source.debug;
    }

    /**
     * Copy the settings.
     *
     * @return the settings
     */
    Settings copy() {
      return new Settings(this);
    }

    /**
     * Load a copy of the settings.
     *
     * @return the settings
     */
    static Settings load() {
      return lastSettings.get().copy();
    }

    /**
     * Save the settings.
     */
    void save() {
      lastSettings.set(this);
    }
  }

  /** {@inheritDoc} */
  @Override
  public int setup(String arg, ImagePlus imp) {
    if ("final".equals(arg)) {
      showResults();
      return DONE;
    }

    UsageTracker.recordPlugin(this.getClass(), arg);

    if (imp == null || imp.getStackSize() == 1) {
      IJ.error(TITLE, "Require an input stack");
      return DONE;
    }

    final Roi roi = imp.getRoi();
    if (roi == null || !roi.isArea()) {
      IJ.error(TITLE, "Require an area ROI");
      return DONE;
    }

    // Get the stack and the slice labels
    stack = imp.getImageStack();
    if (imp.getNDimensions() > 3) {
      IJ.error(TITLE, "Require a 3D stack (not a hyper-stack)");
      return DONE;
    }

    // Try to determine the common prefix to the slice labels
    final String[] labels = new String[stack.getSize()];
    for (int i = 0; i < labels.length; i++) {
      labels[i] = stack.getSliceLabel(i + 1);
      if (labels[i] == null) {
        IJ.error(TITLE, "Missing slice label: " + (i + 1));
        return DONE;
      }
    }
    if (Arrays.stream(labels).allMatch(labels[0]::equals)) {
      IJ.error(TITLE, "Unable to determine exposures: slice labels are identical");
      return DONE;
    }
    commonIndex = getCommonPrefixLength(labels);

    return FLAGS;
  }

  /**
   * Gets the length of the common prefix of the labels. The prefix does not end within a number,
   * e.g. the labels {@code exp_100ms} and {@code exp_150ms} have the common prefix {@code exp_}.
   *
   * @param labels the labels
   * @return the length
   */
  static int getCommonPrefixLength(String[] labels) {
    final String master = labels[0];
    int length = master.length();
    for (int i = 1; i < labels.length; i++) {
      final String label = labels[i];
      final int max = Math.min(length, label.length());
      int index = 0;
      while (index < max && master.charAt(index) == label.charAt(index)) {
        index++;
      }
      length = index;
    }
    // Do not split a number: move back to the start of a number at the end of the prefix
    while (length > 0 && isNumberCharacter(master.charAt(length - 1))) {
      length--;
    }
    return length;
  }

  /**
   * Checks if the character is part of a decimal number.
   *
   * @param ch the character
   * @return true if a digit or decimal point
   */
  private static boolean isNumberCharacter(char ch) {
    return ch == '.' || Character.isDigit(ch);
  }

  /**
   * Gets the exposure from the label. This is the first number in the label after the start index.
   *
   * @param label the label
   * @param startIndex the start index
   * @return the exposure
   * @throws NumberFormatException if no number is found
   */
  static float getExposure(String label, int startIndex) {
    // Find the first digit
    int start = startIndex;
    while (start < label.length() && !Character.isDigit(label.charAt(start))) {
      start++;
    }
    if (start == label.length()) {
      throw new NumberFormatException("No exposure in label: " + label);
    }
    // Move along until no more characters that could be a float value are found
    int end = start + 1;
    while (end < label.length() && isFloatCharacter(label.charAt(end))) {
      end++;
    }
    return Float.parseFloat(label.substring(start, end));
  }

  /** {@inheritDoc} */
  @Override
  public int showDialog(ImagePlus imp, String command, PlugInFilterRunner pfr) {
    this.imp = imp;
    this.pfr = pfr;

    settings = Settings.load();

    settings.bitDepth = Math.min(settings.bitDepth, imp.getBitDepth());

    // Get the user options
    final GenericDialog gd = new GenericDialog(TITLE);
    gd.addHelp(HelpUrls.getUrl("intensity-analysis"));
    gd.addMessage("Calculate the normalised intensity within an ROI.\n"
        + "Images should have a linear response with respect to exposure.");
    gd.addSlider("Window", 3, stack.getSize(), settings.window);
    gd.addSlider("Bit_depth", 4, imp.getBitDepth(), settings.bitDepth);
    gd.addCheckbox("Debug", settings.debug);
    gd.showDialog();
    if (gd.wasCanceled()) {
      return DONE;
    }

    settings.window = Math.abs((int) gd.getNextNumber());
    settings.bitDepth = Math.abs((int) gd.getNextNumber());
    settings.debug = gd.getNextBoolean();

    settings.save();

    if (settings.debug) {
      debug("Prefix = %s\n", stack.getSliceLabel(1).substring(0, commonIndex));
    }

    // Extract the exposure times from the labels
    exposures = new float[stack.getSize()];
    for (int i = 1; i <= stack.getSize(); i++) {
      final String label = stack.getSliceLabel(i);
      try {
        exposures[i - 1] = getExposure(label, commonIndex);
      } catch (final NumberFormatException ex) {
        IJ.error(TITLE, "Unable to determine exposure for slice label: " + label);
        return DONE;
      }
    }

    // Initialise for processing the ROI pixels
    final Roi roi = imp.getRoi();
    bounds = roi.getBounds();
    final ImageProcessor ip = roi.getMask();
    if (ip != null) {
      mask = new boolean[ip.getPixelCount()];
      pixelCount = 0;
      for (int i = 0; i < mask.length; i++) {
        if (ip.get(i) != 0) {
          mask[i] = true;
          pixelCount++;
        }
      }
    } else {
      pixelCount = bounds.width * bounds.height;
    }

    if (settings.debug) {
      debug("Exposures = %s ...\n",
          Arrays.toString(Arrays.copyOf(exposures, Math.min(10, exposures.length))));
    }

    means = new float[exposures.length];
    saturated = (float) (Math.pow(2, settings.bitDepth) - 1);

    return FLAGS;
  }

  private static boolean isFloatCharacter(char ch) {
    switch (ch) {
      // Look for valid characters for a float, e.g. 1.0e-78, 1.0e+78, 1.0f
      case 'e':
      case 'E':
      case 'f':
      case 'F':
      case 'x':
      case '+':
      case '-':
      case '.':
        return true;
      default:
        return Character.isDigit(ch);
    }
  }

  /** {@inheritDoc} */
  @Override
  public void run(ImageProcessor ip) {
    // Process each slice to find the mean of the pixels in the ROI
    final int slice = pfr.getSliceNumber() - 1;

    boolean sat = false;

    double sum = 0;
    if (mask != null) {
      for (int y = 0, i = 0; y < bounds.height; y++) {
        int index = (y + bounds.y) * ip.getWidth() + bounds.x;
        for (int x = 0; x < bounds.width; x++, i++, index++) {
          if (mask[i]) {
            if (ip.getf(index) >= saturated) {
              sat = true;
            }
            sum += ip.getf(index);
          }
        }
      }
    } else {
      for (int y = 0; y < bounds.height; y++) {
        int index = (y + bounds.y) * ip.getWidth() + bounds.x;
        for (int x = 0; x < bounds.width; x++, index++) {
          if (ip.getf(index) >= saturated) {
            sat = true;
          }
          sum += ip.getf(index);
        }
      }
    }

    // Use negative for means with saturated pixels
    means[slice] = ((sat) ? -1 : 1) * (float) (sum / pixelCount);
  }

  /** {@inheritDoc} */
  @Override
  public void setNPasses(int passes) {
    // Ignore
  }

  /**
   * Show a plot of the mean for each slice against exposure. Perform linear fit on a sliding window
   * and draw the best fit line on the plot.
   */
  private void showResults() {
    int valid = 0;
    float[] means2 = new float[means.length];
    float[] exposures2 = new float[means.length];
    // The slice number of each unsaturated slice
    final int[] slices2 = new int[means.length];
    for (int i = 0; i < means.length; i++) {
      if (means[i] < 0) {
        debug("Saturated pixels in slice %d : %s", i + 1, stack.getShortSliceLabel(i + 1));
        means[i] = -means[i];
      } else {
        means2[valid] = means[i];
        exposures2[valid] = exposures[i];
        slices2[valid] = i + 1;
        valid++;
      }
    }

    means2 = Arrays.copyOf(means2, valid);
    exposures2 = Arrays.copyOf(exposures2, valid);

    final String title = TITLE;
    final Plot plot = new Plot(title, "Exposure", "Mean");
    final double[] a = Tools.getMinMax(exposures);
    final double[] b = Tools.getMinMax(means);
    // Add some space to the limits for plotting
    final double ra = (a[1] - a[0]) * 0.05;
    final double rb = (b[1] - b[0]) * 0.05;
    plot.setLimits(a[0] - ra, a[1] + ra, b[0] - rb, b[1] + rb);
    plot.setColor(Color.blue);
    plot.addPoints(exposures, means, Plot.CIRCLE);
    // Used to determine if the window is new
    final WindowOrganiser wo = new WindowOrganiser();
    final PlotWindow pw = ImageJUtils.display(title, plot, wo);

    // Report results to a table
    final TextWindow results = createResultsWindow(wo, pw);

    // Initialise result output
    final StringBuilder sb = new StringBuilder();
    sb.append(imp.getTitle());
    sb.append('\t').append(bounds.x);
    sb.append('\t').append(bounds.y);
    sb.append('\t').append(bounds.width);
    sb.append('\t').append(bounds.height);
    sb.append('\t').append(pixelCount);

    if (means2.length < settings.window) {
      IJ.error(TITLE, "Not enough unsaturated samples for the fit window: " + means2.length + " < "
          + settings.window);
      addNullFitResult(sb);
    } else {

      // Do a linear fit using a sliding window. Find the region with the best linear fit.
      double bestSumOfSquares = Double.POSITIVE_INFINITY;
      int bestStart = 0;
      double[] bestFit = null;
      for (int start = 0; start < means2.length; start++) {
        final int end = start + settings.window;
        if (end > means2.length) {
          break;
        }

        // Linear fit
        final WeightedObservedPoints obs = new WeightedObservedPoints();
        final SimpleRegression r = new SimpleRegression();

        // Extract the data
        for (int i = start; i < end; i++) {
          obs.add(exposures2[i], means2[i]);
          r.addData(exposures2[i], means2[i]);
        }

        if (r.getN() > 0) {
          // Linear regression
          final double[] init = {r.getIntercept(), r.getSlope()}; // a + b x
          final PolynomialCurveFitter fitter = PolynomialCurveFitter.create(2).withStartPoint(init);
          final double[] fit = fitter.fit(obs.toList());
          final PolynomialFunction fitted = new PolynomialFunction(fit);
          // Score the fit
          double ss = 0;
          for (int i = start; i < end; i++) {
            final double residual = fitted.value(exposures2[i]) - means2[i];
            ss += residual * residual;
          }
          debug("%d - %d = %f : y = %f + %f x t\n", start, end, ss, fit[0], fit[1]);
          // Store best fit
          if (ss < bestSumOfSquares) {
            bestSumOfSquares = ss;
            bestStart = start;
            bestFit = fit;
          }
        }
      }

      if (bestFit == null) {
        IJ.error(TITLE, "No valid linear fits");
        addNullFitResult(sb);
      } else {
        plot.setColor(Color.red);
        final PolynomialFunction fitted = new PolynomialFunction(bestFit);
        final double x1 = exposures2[bestStart];
        final double y1 = fitted.value(x1);
        final double x2 = exposures2[bestStart + settings.window - 1];
        final double y2 = fitted.value(x2);
        plot.drawLine(x1, y1, x2, y2);
        ImageJUtils.display(title, plot);

        // Report the slice numbers of the window
        sb.append('\t').append(slices2[bestStart]);
        sb.append('\t').append(slices2[bestStart + settings.window - 1]);
        sb.append('\t').append(MathUtils.rounded(x1));
        sb.append('\t').append(MathUtils.rounded(x2));
        sb.append('\t').append(MathUtils.rounded(bestSumOfSquares));
        sb.append('\t').append(MathUtils.rounded(bestFit[0]));
        sb.append('\t').append(MathUtils.rounded(bestFit[1]));
      }
    }

    results.append(sb.toString());
  }

  private static TextWindow createResultsWindow(final WindowOrganiser wo, PlotWindow pw) {
    TextWindow results = resultsRef.get();
    if (results == null || !results.isVisible()) {
      results = new TextWindow(TITLE + " Summary",
          "Image\tx\ty\tw\th\tN\tStart\tEnd\tE1\tE2\tSS\tIntercept\tGradient", "", 800, 300);
      results.setVisible(true);
      // Locate result window under the plot window if the plot window was new
      if (wo.isNotEmpty()) {
        final Point p = results.getLocation();
        p.x = pw.getX();
        p.y = pw.getY() + pw.getHeight();
        results.setLocation(p);
      }
      resultsRef.set(results);
    }
    return results;
  }

  private static void addNullFitResult(StringBuilder sb) {
    for (int i = 0; i < 7; i++) {
      sb.append('\t');
    }
  }

  private void debug(String format, Object... args) {
    if (settings.debug) {
      IJ.log(String.format(format, args));
    }
  }
}
