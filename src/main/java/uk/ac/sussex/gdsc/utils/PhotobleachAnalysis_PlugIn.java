/*-
 * #%L
 * Genome Damage and Stability Centre ImageJ Plugins
 *
 * Software for microscopy image analysis
 * %%
 * Copyright (C) 2011 - 2020 Alex Herbert
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

package uk.ac.sussex.gdsc.utils;

import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.gui.Overlay;
import ij.gui.Plot;
import ij.gui.Roi;
import ij.plugin.ZProjector;
import ij.plugin.filter.PlugInFilter;
import ij.process.ByteProcessor;
import ij.process.ImageProcessor;
import ij.process.LUT;
import java.awt.Color;
import java.awt.Rectangle;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.IntFunction;
import org.apache.commons.math3.exception.ConvergenceException;
import org.apache.commons.math3.exception.TooManyIterationsException;
import org.apache.commons.math3.fitting.leastsquares.LeastSquaresFactory;
import org.apache.commons.math3.fitting.leastsquares.LeastSquaresOptimizer.Optimum;
import org.apache.commons.math3.fitting.leastsquares.LeastSquaresProblem;
import org.apache.commons.math3.fitting.leastsquares.LeastSquaresProblem.Evaluation;
import org.apache.commons.math3.fitting.leastsquares.LevenbergMarquardtOptimizer;
import org.apache.commons.math3.fitting.leastsquares.MultivariateJacobianFunction;
import org.apache.commons.math3.fitting.leastsquares.ParameterValidator;
import org.apache.commons.math3.linear.Array2DRowRealMatrix;
import org.apache.commons.math3.linear.ArrayRealVector;
import org.apache.commons.math3.linear.DiagonalMatrix;
import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.linear.RealVector;
import org.apache.commons.math3.optim.ConvergenceChecker;
import org.apache.commons.math3.util.Pair;
import uk.ac.sussex.gdsc.UsageTracker;
import uk.ac.sussex.gdsc.core.data.VisibleForTesting;
import uk.ac.sussex.gdsc.core.ij.AlignImagesFft;
import uk.ac.sussex.gdsc.core.ij.AlignImagesFft.SubPixelMethod;
import uk.ac.sussex.gdsc.core.ij.ImageJTrackProgress;
import uk.ac.sussex.gdsc.core.ij.ImageJUtils;
import uk.ac.sussex.gdsc.core.ij.gui.ExtendedGenericDialog;
import uk.ac.sussex.gdsc.core.ij.process.LutHelper;
import uk.ac.sussex.gdsc.core.ij.process.LutHelper.LutColour;
import uk.ac.sussex.gdsc.core.logging.Ticker;
import uk.ac.sussex.gdsc.core.threshold.AutoThreshold;
import uk.ac.sussex.gdsc.core.threshold.AutoThreshold.Method;
import uk.ac.sussex.gdsc.core.utils.DoubleEquality;
import uk.ac.sussex.gdsc.core.utils.FileUtils;
import uk.ac.sussex.gdsc.core.utils.ImageWindow.WindowMethod;
import uk.ac.sussex.gdsc.core.utils.LocalList;
import uk.ac.sussex.gdsc.core.utils.MathUtils;
import uk.ac.sussex.gdsc.core.utils.RegressionUtils;
import uk.ac.sussex.gdsc.core.utils.SimpleArrayUtils;
import uk.ac.sussex.gdsc.core.utils.TextUtils;
import uk.ac.sussex.gdsc.foci.ObjectAnalyzer;
import uk.ac.sussex.gdsc.foci.ObjectAnalyzer.ObjectCentre;

/**
 * Analyses an image stack to detect pixel regions that have been photobleached. A plot of the
 * intensity of the region is created over time and fit using equations for Fluorescence Recovery
 * After Photobleaching (FRAP) to determine diffusion kinetics.
 *
 * @see <a
 *      href="https://en.wikipedia.org/wiki/Fluorescence_recovery_after_photobleaching">Fluorescence
 *      recovery after photobleaching</a>
 */
public class PhotobleachAnalysis_PlugIn implements PlugInFilter {
  private static final String TITLE = "Photobleach Analysis";
  private static final int MAX_BORDER = 5;
  private static final double LN2 = Math.log(2);

  /** The flags specifying the capabilities and needs. */
  private static final int FLAGS = DOES_8G | DOES_16 | DOES_32 | NO_CHANGES | STACK_REQUIRED;

  private ImagePlus imp;
  private Rectangle intersect;
  private boolean extraOptions;

  /** The current settings for the plugin instance. */
  private Settings settings;

  /**
   * Contains the settings that are the re-usable state of the plugin.
   */
  private static class Settings {
    /** The last settings used by the plugin. This should be updated after plugin execution. */
    private static final AtomicReference<Settings> lastSettings =
        new AtomicReference<>(new Settings());

    int alignmentSlice;
    int maxShift;
    boolean showAlignmentOffsets;
    boolean showAlignedImage;
    int emaWindowSize;
    double significance;
    int minRegionSize;
    boolean showBleachingEvents;
    int bleachedBorder;
    boolean showBleachedRegions;
    boolean nestedModels;
    String resultsDir;

    /**
     * Default constructor.
     */
    Settings() {
      maxShift = 40;
      emaWindowSize = 10;
      significance = 10;
      minRegionSize = 100;
      bleachedBorder = 1;
      resultsDir = "";
    }

    /**
     * Copy constructor.
     *
     * @param source the source
     */
    private Settings(Settings source) {
      alignmentSlice = source.alignmentSlice;
      maxShift = source.maxShift;
      showAlignmentOffsets = source.showAlignmentOffsets;
      showAlignedImage = source.showAlignedImage;
      emaWindowSize = source.emaWindowSize;
      significance = source.significance;
      minRegionSize = source.minRegionSize;
      showBleachingEvents = source.showBleachingEvents;
      bleachedBorder = source.bleachedBorder;
      showBleachedRegions = source.showBleachedRegions;
      nestedModels = source.nestedModels;
      resultsDir = source.resultsDir;
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

  @Override
  public int setup(String arg, ImagePlus imp) {
    UsageTracker.recordPlugin(this.getClass(), arg);
    if (imp == null) {
      IJ.noImage();
      return DONE;
    }
    this.imp = imp;
    extraOptions = ImageJUtils.isExtraOptions();
    return showDialog();
  }

  private int showDialog() {
    settings = Settings.load();

    final ExtendedGenericDialog gd = new ExtendedGenericDialog(TITLE);
    gd.addMessage("Analyses an image stack to detect pixel regions that have been photobleached.\n"
        + "The stack is aligned to correct drift.\n"
        + "A sliding window moving average is used to detect significant jumps in intensity\n"
        + "using a jump of n * StdDev (i.e. bleaching events).\n"
        + "A plot of the intensity of each bleached region is created over time.");
    gd.addSlider("Alignment_slice", 0, imp.getStackSize(), settings.alignmentSlice);
    gd.addNumericField("Max_shift", settings.maxShift, 0);
    gd.addCheckbox("Show_alignment_offsets", settings.showAlignmentOffsets);
    gd.addCheckbox("Show_aligned", settings.showAlignedImage);
    gd.addSlider("Window_size", 3, 20, settings.significance);
    gd.addSlider("Significance", 5, 15, settings.emaWindowSize);
    gd.addNumericField("Min_region_size", settings.minRegionSize, 0);
    gd.addCheckbox("Show_bleaching_events", settings.showBleachingEvents);
    gd.addSlider("Bleach_border", 0, MAX_BORDER, settings.bleachedBorder);
    gd.addCheckbox("Show_bleached_regions", settings.showBleachedRegions);
    if (extraOptions) {
      gd.addCheckbox("Fit_nested_models", settings.nestedModels);
    }
    gd.addDirectoryField("Results_dir", settings.resultsDir, 30);
    gd.showDialog();
    settings.save();
    if (gd.wasCanceled()) {
      return DONE;
    }
    settings.alignmentSlice = (int) gd.getNextNumber();
    settings.maxShift = (int) gd.getNextNumber();
    settings.showAlignmentOffsets = gd.getNextBoolean();
    settings.showAlignedImage = gd.getNextBoolean();
    settings.emaWindowSize = (int) gd.getNextNumber();
    settings.significance = gd.getNextNumber();
    settings.minRegionSize = (int) gd.getNextNumber();
    settings.showBleachingEvents = gd.getNextBoolean();
    settings.bleachedBorder = (int) gd.getNextNumber();
    settings.showBleachedRegions = gd.getNextBoolean();
    if (extraOptions) {
      settings.nestedModels = gd.getNextBoolean();
    }
    settings.resultsDir = gd.getNextString();

    if (gd.invalidNumber()) {
      IJ.error(TITLE, "Bad input number");
      return DONE;
    }

    return FLAGS;
  }

  @Override
  public void run(ImageProcessor ip) {
    final ImageStack aligned = alignImage(imp);

    ImagePlus alignedImp = null;
    if (settings.showAlignedImage) {
      alignedImp = ImageJUtils.display("Aligned", aligned);
    }

    final ByteProcessor mask = createMask(aligned);

    final ImageStack events = detectBleachingEvents(aligned, mask);

    final LocalList<Pair<Integer, Roi>> rois = detectBleachedRegions(events);

    if (settings.showBleachingEvents) {
      ImageJUtils.display("Events", events);
    }

    if (rois.isEmpty()) {
      return;
    }

    if (rois.size() >= 255) {
      IJ.error(TITLE, "Too many bleached regions: " + rois.size());
      return;
    }

    addRoisToImage(alignedImp, rois);

    final ImageProcessor regions = createRegionsMask(mask, rois);

    // Count pixels in each region [1, n] (n == foreground)
    final int[] count = regions.getHistogram();

    final int n = rois.size() + 1;
    if (count[n] == 0) {
      IJ.error(TITLE, "No foreground (entire image is bleached regions)");
      return;
    }
    ImageJUtils.log("Foregound = %s pixels", count[n]);

    // Process the image stack.
    // For each frame get the mean of the region.
    final double[] sum = new double[n + 1];
    final float[][] data = new float[n][aligned.size()];
    final int size2 = aligned.getWidth() * aligned.getHeight();
    for (int i = 0; i < aligned.size(); i++) {
      Arrays.fill(sum, 0);
      final ImageProcessor ip2 = aligned.getProcessor(i + 1);
      for (int index = 0; index < size2; index++) {
        final int region = regions.get(index);
        if (region != 0) {
          sum[region] += ip2.getf(index);
        }
      }
      // Store the mean
      for (int j = 1; j <= n; j++) {
        data[j - 1][i] = (float) (sum[j] / count[j]);
      }
    }

    // Save curves
    saveData(data, count);

    // Fit foreground to estimate the bleaching kinetics
    final double[] ffit = fitBleaching(data[n - 1]);
    if (ffit == null) {
      return;
    }
    ImageJUtils.log("Foregound decay: f(t) = %s + %s * exp(-%s t); Half-life = %s",
        MathUtils.rounded(ffit[0]), MathUtils.rounded(ffit[1]), MathUtils.rounded(ffit[2]),
        MathUtils.rounded(LN2 / ffit[2]));

    // Plot the mean over time.
    final float[] x = SimpleArrayUtils.newArray(aligned.size(), 1f, 1f);
    final Plot plot = new Plot(TITLE, "Frame", "Mean Intensity");
    plot.addPoints(x, data[n - 1], null, Plot.LINE, "Foreground");

    // Add fitted curve
    plot.setColor(Color.GRAY);
    plot.addPoints(x, SimpleArrayUtils.toFloat(
        new DecayFunction(x.length).value(new ArrayRealVector(ffit, false)).getFirst().toArray()),
        null, Plot.DOT, null);

    final LUT lut = LutHelper.createLut(LutColour.RED_BLUE);
    for (int j = 1; j < n; j++) {
      plot.setColor(LutHelper.getColour(lut, j - 1, 0, n - 1));
      plot.addPoints(x, data[j - 1], null, Plot.LINE, "Region" + j);
      // Fit each region FRAP curve
      final int bleachingEvent = rois.unsafeGet(j - 1).getFirst();
      final double[] fit = fitRecovery(j, data[j - 1], ffit[2], bleachingEvent);
      if (fit != null) {
        if (fit.length == 3) {
          plot.addPoints(Arrays.copyOfRange(x, bleachingEvent, x.length),
              SimpleArrayUtils.toFloat(new SimpleRecoveryFunction(x.length - bleachingEvent)
                  .value(new ArrayRealVector(fit, false)).getFirst().toArray()),
              null, Plot.DOT, null);
          ImageJUtils.log("Region [%d] recovery: f(t) = %s + %s(1 - exp(-%s t)); Half-life = %s", j,
              MathUtils.rounded(fit[0]), MathUtils.rounded(fit[1]), MathUtils.rounded(fit[2]),
              MathUtils.rounded(LN2 / fit[2]));
        } else if (fit.length == 4) {
          plot.addPoints(Arrays.copyOfRange(x, bleachingEvent, x.length),
              SimpleArrayUtils.toFloat(new RecoveryFunction(x.length - bleachingEvent)
                  .value(new ArrayRealVector(fit, false)).getFirst().toArray()),
              null, Plot.DOT, null);
          ImageJUtils.log(
              "Region [%d] recovery: f(t) = %s + %s(1 - exp(-%s t))exp(-%s t); Half-life1 = %s; Half-life2 = %s",
              j, MathUtils.rounded(fit[0]), MathUtils.rounded(fit[1]), MathUtils.rounded(fit[2]),
              MathUtils.rounded(fit[3]), MathUtils.rounded(LN2 / fit[2]),
              MathUtils.rounded(LN2 / fit[3]));
        } else {
          plot.addPoints(Arrays.copyOfRange(x, bleachingEvent, x.length),
              SimpleArrayUtils.toFloat(new RecoveryFunctionB(x.length - bleachingEvent)
                  .value(new ArrayRealVector(fit, false)).getFirst().toArray()),
              null, Plot.DOT, null);
          ImageJUtils.log(
              "Region [%d] recovery: f(t) = %s + %s(1 - exp(-%s t))exp(-%s t) + %s exp(-%s t); Half-life1 = %s; Half-life2 = %s",
              j, MathUtils.rounded(fit[0]), MathUtils.rounded(fit[1]), MathUtils.rounded(fit[2]),
              MathUtils.rounded(fit[4]), MathUtils.rounded(fit[3]), MathUtils.rounded(fit[4]),
              MathUtils.rounded(LN2 / fit[2]), MathUtils.rounded(LN2 / fit[4]));
        }
      }
    }
    plot.setColor(Color.BLACK);
    // Options must be empty string for auto-position
    plot.addLegend(null, "");
    ImageJUtils.display(plot.getTitle(), plot);
    plot.setLimitsToFit(true); // Seems to only work after drawing
  }

  private ImageStack alignImage(ImagePlus imp) {
    // Support cropped analysis as global drift correction is not perfect for live cells
    final Roi roi = imp.getRoi();
    final ImagePlus imp2 = imp.crop("stack");

    // Initialise output stack
    final int w = imp2.getWidth();
    final int h = imp2.getHeight();
    ImageStack aligned = new ImageStack(w, h);

    // Set-up alignment
    final AlignImagesFft align = new AlignImagesFft();
    align.setProgress(new ImageJTrackProgress());
    final int shift = settings.maxShift;
    final Rectangle bounds = shift > 0 ? new Rectangle(-shift, -shift, 2 * shift, 2 * shift) : null;

    IJ.showStatus("!Creating projection...");

    // TODO:
    // Alignment can be iterated. Align to centre.
    // Create stack.
    // Align to avg. project of the aligned stack.
    // Repeat.

    // // Average intensity
    // final ZProjector projector = new ZProjector(imp2);
    // projector.setMethod(ZProjector.AVG_METHOD);
    // projector.doProjection();
    // align.initialiseReference(projector.getProjection().getProcessor(), WindowMethod.TUKEY,
    // true);

    // Center if the slice is not set.
    // Note that indices are 1-based so add 1 to the stack size for the middle.
    final int middle =
        settings.alignmentSlice == 0 ? (imp2.getStackSize() + 1) / 2 : settings.alignmentSlice;
    align.initialiseReference(imp2.getImageStack().getProcessor(middle), WindowMethod.TUKEY, true);

    IJ.showStatus("!Aligning image");

    // Align the rest of the stack
    // Save the maximum shift.
    int minx = 0;
    int miny = 0;
    int maxx = 0;
    int maxy = 0;
    final ImageStack stack = imp2.getImageStack();
    final Ticker ticker = ImageJUtils.createTicker(stack.getSize(), 1);
    for (int slice = 1; slice <= stack.getSize(); slice++) {
      final ImageProcessor ip = stack.getProcessor(slice).duplicate();
      final double[] offset = align.align(ip, WindowMethod.TUKEY, bounds, SubPixelMethod.NONE);
      final int x = (int) offset[0];
      final int y = (int) offset[1];
      ip.setInterpolationMethod(ImageProcessor.NONE);
      ip.translate(x, y);
      if (settings.showAlignmentOffsets) {
        ImageJUtils.log("  [%d] %d,%d", slice, x, y);
      }
      aligned.addSlice(ip);
      // Save translation limit
      if (minx > x) {
        minx = x;
      } else if (maxx < x) {
        maxx = x;
      }
      if (miny > y) {
        miny = y;
      } else if (maxy < y) {
        maxy = y;
      }
      ticker.tick();
    }

    if (settings.maxShift == MathUtils.max(maxx, maxy, -miny, -minx)) {
      ImageJUtils.log("Maximum shift limit reached: %d,%d to %d,%d", minx, miny, maxx, maxy);
    }

    // Create an ROI for the intersect of all shifted frames (i.e. exclude black border).
    intersect = new Rectangle(w, h).intersection(new Rectangle(minx, miny, w, h))
        .intersection(new Rectangle(maxx, maxy, w, h));
    aligned = aligned.crop(intersect.x, intersect.y, 0, intersect.width, intersect.height,
        aligned.getSize());
    // Add the origin offset back the crop region bounds
    if (roi != null && roi.isArea()) {
      intersect.x += roi.getXBase();
      intersect.y += roi.getYBase();
    }
    return aligned;
  }

  /**
   * Creates the mask from the foreground pixels of the average intensity projection.
   *
   * @param stack the stack
   * @return the mask
   */
  private static ByteProcessor createMask(ImageStack stack) {
    IJ.showStatus("!Creating mask...");

    // Align to the average intensity projection
    final ZProjector projector = new ZProjector(new ImagePlus(null, stack));
    projector.setMethod(ZProjector.AVG_METHOD);
    projector.doProjection();

    final ImageProcessor ip = projector.getProjection().getProcessor().convertToShortProcessor();
    final int t = AutoThreshold.getThreshold(Method.OTSU, ip.getHistogram());
    ip.threshold(t);
    return ip.convertToByteProcessor(false);
  }

  private ImageStack detectBleachingEvents(final ImageStack aligned, final ByteProcessor mask) {
    // For each pixel create an intensity trace over time.
    IJ.showStatus("!Detecting bleaching events...");
    final IntFunction<float[]> traceFunction = createTraceFunction(aligned);

    // Detect bleaching events in the image using an exponential moving average (EMA).
    // This down-weights the history in favour of the most recent observations.
    // The following code computes the alpha weighting factor to cover k observations
    // with 99.9% of the total weight.

    final int k = settings.emaWindowSize;
    final double alpha = 1 - Math.exp(Math.log(0.001) / k);
    final double eps = 1 - alpha;

    // The number of standard deviations from the mean for a significant jump.
    // This is squared for convenience during processing.
    final double scoreThreshold = MathUtils.pow2(settings.significance);

    // Create a mask stack with bleaching events marked on it
    final ImageStack events = IJ.createImage("Events", "8-bit black", aligned.getWidth(),
        aligned.getHeight(), aligned.getSize()).getImageStack();

    final int size = aligned.getWidth() * aligned.getHeight();
    final Ticker ticker = ImageJUtils.createTicker(size, 1);
    for (int i = 0; i < size; i++) {
      if (mask.get(i) == 0) {
        continue;
      }
      final float[] trace = traceFunction.apply(i);
      // Detect large drops in intensity as a bleaching event.
      // Try using a top-hat filter and detecting
      final int event = detectBleachingEvent(trace, alpha, eps, k, scoreThreshold);
      if (event != -1) {
        events.getProcessor(event + 1).set(i, 255);
      }
      ticker.tick();
    }
    return events;
  }

  /**
   * Creates the trace function.
   *
   * @param stack the stack
   * @return the trace function
   */
  private static IntFunction<float[]> createTraceFunction(ImageStack stack) {
    final Object[] imageArray = stack.getImageArray();
    final int size = stack.size();
    // Detect pixels type
    final Object pixels = imageArray[0];
    if (pixels instanceof byte[]) {
      final byte[][] image = new byte[size][];
      for (int i = 0; i < size; i++) {
        image[i] = (byte[]) imageArray[i];
      }
      return index -> {
        final float[] trace = new float[size];
        for (int i = 0; i < size; i++) {
          trace[i] = image[i][index] & 0xff;
        }
        return trace;
      };
    } else if (pixels instanceof short[]) {
      final short[][] image = new short[size][];
      for (int i = 0; i < size; i++) {
        image[i] = (short[]) imageArray[i];
      }
      return index -> {
        final float[] trace = new float[size];
        for (int i = 0; i < size; i++) {
          trace[i] = image[i][index] & 0xffff;
        }
        return trace;
      };
    } else if (pixels instanceof float[]) {
      final float[][] image = new float[size][];
      for (int i = 0; i < size; i++) {
        image[i] = (float[]) imageArray[i];
      }
      return index -> {
        final float[] trace = new float[size];
        for (int i = 0; i < size; i++) {
          trace[i] = image[i][index];
        }
        return trace;
      };
    }
    throw new IllegalStateException("Unsupported pixels type");
  }

  /**
   * Detect a bleaching event. This is the first significant jump in intensity from an exponential
   * moving average (EMA) of the trace intensity processed in reverse order. If detected then
   * further bleaching events in the same trace should start analysis again from with a trace
   * truncated to the event index.
   *
   * <p>The event is returned as the frame where the intensity of the pixel has dropped.
   *
   * @param trace the trace
   * @param alpha the alpha used to update the EMA
   * @param eps (1 - alpha)
   * @param k the number of steps to use as the 'spin-up' interval for the moving average
   * @param scoreThreshold the score threshold for (x-mean)^2 / variance
   * @return the index of the bleaching event (or -1)
   */
  private static int detectBleachingEvent(float[] trace, double alpha, double eps, int k,
      double scoreThreshold) {
    // Compute an exponential moving average (EMA).
    // https://en.wikipedia.org/wiki/Moving_average#Exponential_moving_average

    // Update the EMA
    // S_1 = Y_1
    // S_t = alpha * Y_t + (1 - alpha) * S_t-1
    // Initialise Y1 as the first observation.
    // Discard initial iterations as 'spin-up' interval using the window size k.
    // Compute rolling variance using Welford's algorithm

    // Process from the tail end.
    // Detect a bleaching event as a large increase in the observation value compared
    // to the rolling mean using the standard score:
    // (x - mean) / sd > threshold
    // (x - mean)^2 / var > threshold^2

    final int end = trace.length - 1;
    double ema = trace[end];
    double var = 0;
    for (int i = 1; i <= end; i++) {
      final double delta = trace[end - i] - ema;
      final double delta2 = delta * delta;
      // Check for significant positive deviations from the mean (after the spin-up interval)
      if (i > k && delta > 0) {
        if (delta2 / var > scoreThreshold) {
          return end - i + 1;
        }
      }
      // Update
      ema += alpha * delta;
      var = eps * (var + alpha * delta2);
    }

    // Nothing significant
    return -1;
  }

  private LocalList<Pair<Integer, Roi>> detectBleachedRegions(final ImageStack events) {
    // Post-process the events to join contiguous regions and remove speckles
    IJ.showStatus("!Detecting bleached regions...");
    final LocalList<Pair<Integer, Roi>> rois = new LocalList<>();
    for (int i = 1; i <= events.size(); i++) {
      final ByteProcessor bp = (ByteProcessor) events.getProcessor(i);
      // Like the close- command
      bp.dilate(1, 0);
      bp.erode(1, 0);
      // Remove speckles
      bp.erode(8, 0);

      // Find objects in each frame
      final ObjectAnalyzer oa = new ObjectAnalyzer(bp);
      oa.setMinObjectSize(settings.minRegionSize);
      if (oa.getMaxObject() != 0) {
        ImageJUtils.log("Detected %s on frame %d:", TextUtils.pleural(oa.getMaxObject(), "region"),
            i);
        final Roi[] outlines = oa.getObjectOutlines();
        final ObjectCentre[] centres = oa.getObjectCentres();
        for (int j = 1; j < outlines.length; j++) {
          ImageJUtils.log("  [%d] (%.2f,%.2f) = %s pixels", j,
              intersect.x + centres[j].getCentreX(), intersect.y + centres[j].getCentreY(),
              centres[j].getSize());
          // Store 0-based index for the bleaching event
          rois.add(Pair.create(i - 1, outlines[j]));
        }
      }
    }

    IJ.protectStatusBar(false);
    return rois;
  }

  private ImageProcessor createRegionsMask(final ByteProcessor mask,
      final LocalList<Pair<Integer, Roi>> rois) {
    // Create a mask with each region a new number
    final ImageProcessor regions = new ByteProcessor(mask.getWidth(), mask.getHeight());

    // Set all foreground pixels as region n (the last region)
    final int n = rois.size() + 1;
    for (int i = mask.getPixelCount(); i-- > 0;) {
      if (mask.get(i) != 0) {
        regions.set(i, n);
      }
    }

    // Fill the regions processor with each ROI
    for (int i = 0; i < rois.size(); i++) {
      regions.setValue(i + 1);
      regions.fill(rois.unsafeGet(i).getValue());
    }

    // Dilate the regions to erode the foreground
    if (settings.bleachedBorder > 0) {
      final ByteProcessor bp = new ByteProcessor(mask.getWidth(), mask.getHeight());
      for (int i = mask.getPixelCount(); i-- > 0;) {
        final int v = regions.get(i);
        if (v != 0 && v != n) {
          bp.set(i, 255);
        }
      }
      // Not too much
      for (int i = Math.min(MAX_BORDER, settings.bleachedBorder); i-- > 0;) {
        bp.filter(ImageProcessor.MAX);
      }
      // Remove foreground
      for (int i = mask.getPixelCount(); i-- > 0;) {
        if (regions.get(i) == n && bp.get(i) != 0) {
          regions.set(i, 0);
        }
      }
    }

    if (settings.showBleachedRegions) {
      final ImagePlus regionsImp = ImageJUtils.display("Regions", regions);
      regionsImp.setDisplayRange(0, n);
      regionsImp.updateAndDraw();
    }
    return regions;
  }

  private static void addRoisToImage(ImagePlus alignedImp,
      final LocalList<Pair<Integer, Roi>> rois) {
    // Add rois to the aligned image.
    // Adding to the original image requires an offset for each aligned frame and positioning
    // the roi on each slice.
    if (alignedImp != null) {
      final Overlay o = new Overlay();
      rois.forEach(p -> o.add(p.getValue()));
      alignedImp.setOverlay(o);
    }
  }

  /**
   * Save data to a directory.
   * 
   * <p>The data of each region {@code n} in {@code data[n - 1]}. The final entry is the foreground.
   * 
   * <p>The size of each region {@code n} in {@code countHistogram[n]}.
   *
   * @param data the data
   * @param countHistogram the count histogram
   */
  private void saveData(float[][] data, int[] countHistogram) {
    if (!Files.isDirectory(Paths.get(settings.resultsDir))) {
      return;
    }
    int n = data.length;
    int size = data[0].length;
    String[] frames = new String[size];
    for (int t = 0; t < size; t++) {
      frames[t] = (t + 1) + ",";
    }

    String prefix = FileUtils.removeExtension(imp.getTitle()).replace(' ', '_');

    for (int i = 0; i < n; i++) {
      Path path = i == n - 1 ? Paths.get(settings.resultsDir, prefix + "_foreground.csv")
          : Paths.get(settings.resultsDir, prefix + "_region" + i + ".csv");
      try (BufferedWriter out = Files.newBufferedWriter(path)) {
        out.write("# Size = ");
        out.write(Integer.toString(countHistogram[i + 1]));
        out.newLine();

        out.write("Frame,Mean");
        out.newLine();

        for (int t = 0; t < size; t++) {
          out.write(frames[t]);
          out.write(Float.toString(data[i][t]));
          out.newLine();
        }
      } catch (IOException e) {
        ImageJUtils.log("Failed to save data: " + e.getMessage());
        break;
      }
    }
  }

  /**
   * Fit the bleaching curve {@code f(t) = y0 + B exp(-tau * t)}.
   *
   * @param y the data
   * @return {y0, B, tau}
   */
  private static double[] fitBleaching(float[] y) {
    // Initial estimates
    final float[] limits = MathUtils.limits(y);
    final double y0 = limits[0];
    // Find point where the decay is half
    final float half = limits[0] + (limits[1] - limits[0]) / 2;
    int t = 1;
    while (t < y.length && y[t] > half) {
      t++;
    }
    // half-life (median of an exponential) = ln(2) / tau
    // tau = ln(2) / half-life
    final double tau = LN2 / t;
    // Solve for B
    final double b = (half - y0) / Math.exp(-tau * t);

    final LevenbergMarquardtOptimizer optimizer = new LevenbergMarquardtOptimizer();
    final RealVector observed = new ArrayRealVector(SimpleArrayUtils.toDouble(y), false);
    final ConvergenceChecker<Evaluation> checker = (iteration, previous,
        current) -> DoubleEquality.relativeError(previous.getCost(), current.getCost()) < 1e-6;

    final MultivariateJacobianFunction model1 = new DecayFunction(y.length);
    final RealVector start1 = new ArrayRealVector(new double[] {y0, b, tau}, false);
    final ParameterValidator paramValidator1 = point -> {
      // Do not use MIN_VALUE here to avoid sub-normal numbers
      for (int i = 0; i < 3; i++) {
        if (point.getEntry(i) < Double.MIN_NORMAL) {
          point.setEntry(i, Double.MIN_NORMAL);
        }
      }
      return point;
    };
    final RealMatrix weightMatrix =
        new DiagonalMatrix(SimpleArrayUtils.newDoubleArray(y.length, 1.0), false);
    final int maxEvaluations = Integer.MAX_VALUE;
    final int maxIterations = 3000;
    final boolean lazyEvaluation = false;

    final LeastSquaresProblem problem1 = LeastSquaresFactory.create(model1, observed, start1,
        weightMatrix, checker, maxEvaluations, maxIterations, lazyEvaluation, paramValidator1);
    try {
      final Optimum lvmSolution1 = optimizer.optimize(problem1);
      final RealVector fit1 = lvmSolution1.getPoint();
      return fit1.toArray();
    } catch (TooManyIterationsException | ConvergenceException ex) {
      ImageJUtils.log("Failed to fit bleaching curve: ", ex.getMessage());
      return null;
    }
  }

  /**
   * Fit the bleaching curve {@code f(t) = A(1 - exp(-tau1 * t))(y0 + B exp(-tau2 * t)}.
   *
   * @param region the region
   * @param y the data
   * @param tau2 the initial estimate for the general image bleaching
   * @param bleachingEvent the index in y for the bleaching event (low point of curve)
   * @return {A, tau1, y0, B, tau2}
   */
  private double[] fitRecovery(int region, float[] y, double tau2, int bleachingEvent) {
    final boolean nested = extraOptions && settings.nestedModels;

    // Initial estimates
    // @formatter:off
    //
    // ---+
    //    |
    //    |         ---------    A
    //    |     ---/
    //    |   -/
    //    |  /
    //    | /
    //    |/
    //
    // y0
    //
    // A is the magnitude of the recovery.
    // tau1 is the recovery rate.
    // B is all the magnitude that will still bleach over time.
    // y0 is the baseline for the intensity.
    // tau2 is the bleaching rate over time.
    // @formatter:on

    final double after = y[bleachingEvent];
    double a = y[y.length - 1] - after;
    double y0 = after;

    // Find point where the recovery is half
    final float half = (float) (y0 + a / 2);
    int t = bleachingEvent + 1;
    while (t < y.length && y[t] < half) {
      t++;
    }
    // half-life (median of an exponential) = ln(2) / tau
    // tau = ln(2) / half-life
    double tau1 = LN2 / (t - bleachingEvent);

    // Extract curve to fit
    final double[] yy = new double[y.length - bleachingEvent];
    for (int i = 0; i < yy.length; i++) {
      yy[i] = y[i + bleachingEvent];
    }

    final LevenbergMarquardtOptimizer optimizer = new LevenbergMarquardtOptimizer();
    final RealVector observed = new ArrayRealVector(yy, false);
    final ConvergenceChecker<Evaluation> checker = (iteration, previous,
        current) -> DoubleEquality.relativeError(previous.getCost(), current.getCost()) < 1e-6;

    // Fit the simple version too (which ignores residual bleaching): y0 + A(1 - exp(-tau1 * t))
    final MultivariateJacobianFunction model1 = new SimpleRecoveryFunction(yy.length);
    final RealVector start1 = new ArrayRealVector(new double[] {y0, a, tau1}, false);
    final ParameterValidator paramValidator = point -> {
      // Do not use MIN_VALUE here to avoid sub-normal numbers
      for (int i = point.getDimension(); i-- > 0;) {
        if (point.getEntry(i) < Double.MIN_NORMAL) {
          point.setEntry(i, Double.MIN_NORMAL);
        }
      }
      return point;
    };
    final RealMatrix weightMatrix =
        new DiagonalMatrix(SimpleArrayUtils.newDoubleArray(yy.length, 1.0), false);
    final int maxEvaluations = Integer.MAX_VALUE;
    final int maxIterations = 3000;
    final boolean lazyEvaluation = false;

    final LeastSquaresProblem problem1 = LeastSquaresFactory.create(model1, observed, start1,
        weightMatrix, checker, maxEvaluations, maxIterations, lazyEvaluation, paramValidator);
    Optimum best;
    try {
      best = optimizer.optimize(problem1);
      final double[] fit = best.getPoint().toArray();
      if (nested) {
        ImageJUtils.log("  Region [%d] recovery: f(t) = %s + %s(1 - exp(-%s t)); Half-life = %s",
            region, MathUtils.rounded(fit[0]), MathUtils.rounded(fit[1]), MathUtils.rounded(fit[2]),
            MathUtils.rounded(LN2 / fit[2]));
      }
    } catch (TooManyIterationsException | ConvergenceException ex) {
      ImageJUtils.log("Failed to fit simple recovery curve: ", ex.getMessage());
      return null;
    }

    if (nested) {
      // Use the fit as the start point for nested models
      y0 = best.getPoint().getEntry(0);
      a = best.getPoint().getEntry(1);
      tau1 = best.getPoint().getEntry(2);

      // Fit the simple version but with a general decay of the recovered signal.
      final MultivariateJacobianFunction model2 = new RecoveryFunction(yy.length);
      final RealVector start2 = new ArrayRealVector(new double[] {y0, a, tau1, tau2}, false);

      final LeastSquaresProblem problem2 = LeastSquaresFactory.create(model2, observed, start2,
          weightMatrix, checker, maxEvaluations, maxIterations, lazyEvaluation, paramValidator);
      try {
        final Optimum lvmSolution = optimizer.optimize(problem2);
        // Check for model improvement
        final double rss1 = getResidualSumOfSquares(best);
        final double rss2 = getResidualSumOfSquares(lvmSolution);
        double pValue;
        double f;
        if (rss1 < rss2) {
          f = 0;
          pValue = 1;
        } else {
          f = RegressionUtils.residualsFStatistic(rss1, start1.getDimension(), rss2,
              start2.getDimension(), yy.length);
          pValue = RegressionUtils.residualsFTest(rss1, start1.getDimension(), rss2,
              start2.getDimension(), yy.length);
        }
        // Optionally log no improvement here...
        final double[] fit = lvmSolution.getPoint().toArray();
        ImageJUtils.log(
            "  Region [%d] recovery: f(t) = %s + %s(1 - exp(-%s t))exp(-%s t); Half-life1 = %s; Half-life2 = %s",
            region, MathUtils.rounded(fit[0]), MathUtils.rounded(fit[1]), MathUtils.rounded(fit[2]),
            MathUtils.rounded(fit[3]), MathUtils.rounded(LN2 / fit[2]),
            MathUtils.rounded(LN2 / fit[3]));
        ImageJUtils.log("  Region [%d] : rss1=%s, rss2=%s, p(F-Test=%s) = %s; ", region,
            MathUtils.rounded(rss1), MathUtils.rounded(rss2), MathUtils.rounded(f),
            MathUtils.rounded(pValue));
        if (pValue < 0.01) {
          // reject null hypothesis that model 2 is not better
          best = lvmSolution;
        }
      } catch (TooManyIterationsException | ConvergenceException ex) {
        ImageJUtils.log("Failed to fit recovery curve: ", ex.getMessage());
      }

      // Fit the simple version but with a general decay of the recovered signal and any signal
      // not bleached in the bleaching event.
      // Initialise with a small amount of the baseline as residual unbleached intensity.
      final double b = y0 / 100;
      y0 -= b;

      final MultivariateJacobianFunction model3 = new RecoveryFunctionB(yy.length);
      final RealVector start3 = new ArrayRealVector(new double[] {y0, a, tau1, b, tau2}, false);

      final LeastSquaresProblem problem3 = LeastSquaresFactory.create(model3, observed, start3,
          weightMatrix, checker, maxEvaluations, maxIterations, lazyEvaluation, paramValidator);
      try {
        final Optimum lvmSolution = optimizer.optimize(problem3);
        // Check for model improvement
        final double rss1 = getResidualSumOfSquares(best);
        final double rss2 = getResidualSumOfSquares(lvmSolution);
        double pValue;
        double f;
        if (rss1 < rss2) {
          f = 0;
          pValue = 1;
        } else {
          f = RegressionUtils.residualsFStatistic(rss1, best.getPoint().getDimension(), rss2,
              start3.getDimension(), yy.length);
          pValue = RegressionUtils.residualsFTest(rss1, start1.getDimension(), rss2,
              start3.getDimension(), yy.length);
        }
        // Optionally log no improvement here...
        final double[] fit = lvmSolution.getPoint().toArray();
        ImageJUtils.log(
            "  Region [%d] recovery: f(t) = %s + %s(1 - exp(-%s t))exp(-%s t) + %s exp(-%s t); Half-life1 = %s; Half-life2 = %s",
            region, MathUtils.rounded(fit[0]), MathUtils.rounded(fit[1]), MathUtils.rounded(fit[2]),
            MathUtils.rounded(fit[4]), MathUtils.rounded(fit[3]), MathUtils.rounded(fit[4]),
            MathUtils.rounded(LN2 / fit[2]), MathUtils.rounded(LN2 / fit[4]));
        ImageJUtils.log("  Region [%d] : rss1=%s, rss2=%s, p(F-Test=%s) = %s; ", region,
            MathUtils.rounded(rss1), MathUtils.rounded(rss2), MathUtils.rounded(f),
            MathUtils.rounded(pValue));
        if (pValue < 0.01) {
          // reject null hypothesis that model 2 is not better
          best = lvmSolution;
        }
      } catch (TooManyIterationsException | ConvergenceException ex) {
        ImageJUtils.log("Failed to fit recovery curve: ", ex.getMessage());
      }
    }

    return best.getPoint().toArray();
  }

  /**
   * Gets the residual sum of squares.
   *
   * @param lvmSolution1 the lvm solution
   * @return the residual sum of squares
   */
  private static double getResidualSumOfSquares(final Optimum lvmSolution1) {
    final RealVector res = lvmSolution1.getResiduals();
    return res.dotProduct(res);
  }

  /**
   * Exponential decay function {@code f(t) = y0 + B exp(-tau * t)}.
   */
  @VisibleForTesting
  static final class DecayFunction implements MultivariateJacobianFunction {
    private final int size;

    /**
     * @param size the size
     */
    DecayFunction(int size) {
      this.size = size;
    }

    /**
     * {@inheritDoc}
     *
     * @param point {y0, B, tau}
     */
    @Override
    public Pair<RealVector, RealMatrix> value(RealVector point) {
      final double[] value = new double[size];
      final double[][] jacobian = new double[size][3];
      final double y = point.getEntry(0);
      final double b = point.getEntry(1);
      final double tau = point.getEntry(2);
      // f(t) = y + b exp(-tau * t)
      // df_dy = 1
      // df_db = exp(-tau * t)
      // df_dtau = -b * t * exp(-tau * t)

      for (int t = 0; t < size; t++) {
        final double x = Math.exp(-tau * t);
        value[t] = y + b * x;
        jacobian[t][0] = 1;
        jacobian[t][1] = x;
        jacobian[t][2] = -b * t * x;
      }
      return new Pair<>(new ArrayRealVector(value, false),
          new Array2DRowRealMatrix(jacobian, false));
    }
  }

  /**
   * Exponential recovery function {@code f(t) = y0 + A(1 - exp(-tau * t))}.
   */
  @VisibleForTesting
  static final class SimpleRecoveryFunction implements MultivariateJacobianFunction {
    private final int size;

    /**
     * @param size the size
     */
    SimpleRecoveryFunction(int size) {
      this.size = size;
    }

    /**
     * {@inheritDoc}
     *
     * @param point {y0, A, tau}
     */
    @Override
    public Pair<RealVector, RealMatrix> value(RealVector point) {
      final double[] value = new double[size];
      final double[][] jacobian = new double[size][3];
      final double y = point.getEntry(0);
      final double a = point.getEntry(1);
      final double tau = point.getEntry(2);
      // f(t) = y0 + A(1 - exp(-tau * t))
      //
      // df_dy = 1
      // df_dA = (1 - exp(-tau * t))
      // df_dtau = A * (t * exp(-tau * t))

      for (int t = 0; t < size; t++) {
        final double x1 = Math.exp(-tau * t);
        value[t] = y + a * (1 - x1);
        jacobian[t][0] = 1;
        jacobian[t][1] = (1 - x1);
        jacobian[t][2] = a * (t * x1);
      }
      return new Pair<>(new ArrayRealVector(value, false),
          new Array2DRowRealMatrix(jacobian, false));
    }
  }

  /**
   * Exponential recovery function {@code f(t) = y0 + A(1 - exp(-tau1 * t))exp(-tau2 * t)}.
   */
  @VisibleForTesting
  static final class RecoveryFunction implements MultivariateJacobianFunction {
    private final int size;

    /**
     * @param size the size
     */
    RecoveryFunction(int size) {
      this.size = size;
    }

    /**
     * {@inheritDoc}
     *
     * @param point {y0, A, tau1, tau2}
     */
    @Override
    public Pair<RealVector, RealMatrix> value(RealVector point) {
      final double[] value = new double[size];
      final double[][] jacobian = new double[size][4];
      final double y = point.getEntry(0);
      final double a = point.getEntry(1);
      final double tau1 = point.getEntry(2);
      final double tau2 = point.getEntry(3);
      // f(t) = y0 + A(1 - exp(-tau1 * t))exp(-tau2 * t)
      //
      // f(t) = u(t) * v(t)
      // f'(t) = u'(t) * v(t) + u(t) * v'(t)
      //
      // df_dy = 1
      // df_dA = (1 - exp(-tau1 * t))exp(-tau2 * t)
      // df_dtau1 = A * (t * exp(-tau1 * t)) * exp(-tau2 * t)
      // df_dtau2 = A(1 - exp(-tau1 * t)) * -t * exp(-tau2 * t) - B * t * exp(-tau2 * t)

      for (int t = 0; t < size; t++) {
        final double x1 = Math.exp(-tau1 * t);
        final double x2 = Math.exp(-tau2 * t);
        final double ut = a * (1 - x1);
        value[t] = y + ut * x2;
        jacobian[t][0] = 1;
        jacobian[t][1] = (1 - x1) * x2;
        jacobian[t][2] = a * t * x1 * x2;
        jacobian[t][3] = ut * -t * x2;
      }
      return new Pair<>(new ArrayRealVector(value, false),
          new Array2DRowRealMatrix(jacobian, false));
    }
  }

  /**
   * Exponential recovery function
   * {@code f(t) = y0 + A(1 - exp(-tau1 * t))exp(-tau2 * t) + B exp(-tau2 * t)}.
   */
  @VisibleForTesting
  static final class RecoveryFunctionB implements MultivariateJacobianFunction {
    private final int size;

    /**
     * @param size the size
     */
    RecoveryFunctionB(int size) {
      this.size = size;
    }

    /**
     * {@inheritDoc}
     *
     * @param point {y0, A, tau1, B, tau2}
     */
    @Override
    public Pair<RealVector, RealMatrix> value(RealVector point) {
      final double[] value = new double[size];
      final double[][] jacobian = new double[size][5];
      final double y = point.getEntry(0);
      final double a = point.getEntry(1);
      final double tau1 = point.getEntry(2);
      final double b = point.getEntry(3);
      final double tau2 = point.getEntry(4);
      // f(t) = y0 + A(1 - exp(-tau1 * t))exp(-tau2 * t) + B exp(-tau2 * t)
      //
      // f(t) = u(t) * v(t)
      // f'(t) = u'(t) * v(t) + u(t) * v'(t)
      //
      // df_dy = 1
      // df_dA = (1 - exp(-tau1 * t))exp(-tau2 * t)
      // df_dtau1 = A * (t * exp(-tau1 * t)) * exp(-tau2 * t)
      // df_dB = exp(-tau2 * t)
      // df_dtau2 = A(1 - exp(-tau1 * t)) * -t * exp(-tau2 * t) - B * t * exp(-tau2 * t)

      for (int t = 0; t < size; t++) {
        final double x1 = Math.exp(-tau1 * t);
        final double x2 = Math.exp(-tau2 * t);
        final double ut = a * (1 - x1);
        value[t] = y + ut * x2 + b * x2;
        jacobian[t][0] = 1;
        jacobian[t][1] = (1 - x1) * x2;
        jacobian[t][2] = a * t * x1 * x2;
        jacobian[t][3] = x2;
        jacobian[t][4] = ut * -t * x2 - b * t * x2;
      }
      return new Pair<>(new ArrayRealVector(value, false),
          new Array2DRowRealMatrix(jacobian, false));
    }
  }

  /**
   * Exponential recovery function {@code f(t) = A(1 - exp(-tau1 * t))(y0 + B exp(-tau2 * t)}.
   *
   * <p>This does not seem correct to include y0 in the product of the recovery curve.
   *
   * @see <a href="https://www.embl.de/eamnet/downloads/courses/FRAP2004/frap_intro_miura.pdf">FRAP
   *      intro</a>
   */
  @VisibleForTesting
  static final class RecoveryFunction1 implements MultivariateJacobianFunction {
    private final int size;

    /**
     * @param size the size
     */
    RecoveryFunction1(int size) {
      this.size = size;
    }

    /**
     * {@inheritDoc}
     *
     * @param point {y0, A, tau1, B, tau2}
     */
    @Override
    public Pair<RealVector, RealMatrix> value(RealVector point) {
      final double[] value = new double[size];
      final double[][] jacobian = new double[size][5];
      final double y = point.getEntry(0);
      final double a = point.getEntry(1);
      final double tau1 = point.getEntry(2);
      final double b = point.getEntry(3);
      final double tau2 = point.getEntry(4);
      // f(t) = A(1 - exp(-tau1 * t))(y0 + B exp(-tau2 * t)
      //
      // f(t) = u(t) * v(t)
      // f'(t) = u'(t) * v(t) + u(t) * v'(t)
      //
      // df_dy = A(1 - exp(-tau1 * t))
      // df_dA = (1 - exp(-tau1 * t))(y0 + B exp(-tau2 * t)
      // df_dtau1 = A * (t * exp(-tau1 * t)) * vt
      // df_dB = A(1 - exp(-tau1 * t)) * exp(-tau2 * t)
      // df_dtau2 = A(1 - exp(-tau1 * t)) * (-B * t * exp(-tau2 * t))

      for (int t = 0; t < size; t++) {
        final double x1 = Math.exp(-tau1 * t);
        final double x2 = Math.exp(-tau2 * t);
        final double ut = a * (1 - x1);
        final double vt = y + b * x2;
        value[t] = ut * vt;
        jacobian[t][0] = ut;
        jacobian[t][1] = (1 - x1) * vt;
        jacobian[t][2] = a * (t * x1) * vt;
        jacobian[t][3] = ut * x2;
        jacobian[t][4] = ut * (-b * t * x2);
      }
      return new Pair<>(new ArrayRealVector(value, false),
          new Array2DRowRealMatrix(jacobian, false));
    }
  }
}
