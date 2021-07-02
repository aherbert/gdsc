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
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.IntFunction;
import uk.ac.sussex.gdsc.UsageTracker;
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
import uk.ac.sussex.gdsc.core.utils.ImageWindow.WindowMethod;
import uk.ac.sussex.gdsc.core.utils.LocalList;
import uk.ac.sussex.gdsc.core.utils.MathUtils;
import uk.ac.sussex.gdsc.core.utils.SimpleArrayUtils;
import uk.ac.sussex.gdsc.core.utils.TextUtils;
import uk.ac.sussex.gdsc.foci.ObjectAnalyzer;
import uk.ac.sussex.gdsc.foci.ObjectAnalyzer.ObjectCentre;

/**
 * Analyses an image stack to detect pixel regions that have been photobleached. A plot of the
 * intensity of the region is created over time.
 *
 * @see <a href="https://en.wikipedia.org/wiki/Scale_space">Scale space</a>
 */
public class PhotobleachAnalysis_PlugIn implements PlugInFilter {
  private static final String TITLE = "Photobleach Analysis";

  /** The flags specifying the capabilities and needs. */
  private static final int FLAGS = DOES_8G | DOES_16 | DOES_32 | NO_CHANGES | STACK_REQUIRED;

  private ImagePlus imp;
  private Rectangle intersect;

  /** The current settings for the plugin instance. */
  private Settings settings;

  /**
   * Contains the settings that are the re-usable state of the plugin.
   */
  private static class Settings {
    /** The last settings used by the plugin. This should be updated after plugin execution. */
    private static final AtomicReference<Settings> lastSettings =
        new AtomicReference<>(new Settings());

    int maxShift;
    boolean showAlignedImage;
    int emaWindowSize;
    double significance;
    int minRegionSize;
    boolean showBleachingEvents;
    boolean showBleachedRegions;

    /**
     * Default constructor.
     */
    Settings() {
      maxShift = 20;
      emaWindowSize = 10;
      significance = 10;
      minRegionSize = 25;
    }

    /**
     * Copy constructor.
     *
     * @param source the source
     */
    private Settings(Settings source) {
      maxShift = source.maxShift;
      showAlignedImage = source.showAlignedImage;
      emaWindowSize = source.emaWindowSize;
      significance = source.significance;
      minRegionSize = source.minRegionSize;
      showBleachingEvents = source.showBleachingEvents;
      showBleachedRegions = source.showBleachedRegions;
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
    gd.addNumericField("Max_shift", settings.maxShift, 0);
    gd.addCheckbox("Show_aligned", settings.showAlignedImage);
    gd.addSlider("Window_size", 3, 20, settings.significance);
    gd.addSlider("Significance", 5, 15, settings.emaWindowSize);
    gd.addNumericField("Min_region_size", settings.minRegionSize, 0);
    gd.addCheckbox("Show_bleaching_events", settings.showBleachingEvents);
    gd.addCheckbox("Show_bleached_regions", settings.showBleachedRegions);
    gd.showDialog();
    settings.save();
    if (gd.wasCanceled()) {
      return DONE;
    }
    settings.maxShift = (int) gd.getNextNumber();
    settings.showAlignedImage = gd.getNextBoolean();
    settings.emaWindowSize = (int) gd.getNextNumber();
    settings.significance = gd.getNextNumber();
    settings.minRegionSize = (int) gd.getNextNumber();
    settings.showBleachingEvents = gd.getNextBoolean();
    settings.showBleachedRegions = gd.getNextBoolean();

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

    final LocalList<Roi> rois = detectBleachedRegions(events);

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

    // Process the image stack.
    // For each frame get the mean of the region.
    final int n = rois.size() + 1;
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

    // Plot the mean over time.
    final float[] x = SimpleArrayUtils.newArray(aligned.size(), 1f, 1f);
    final Plot plot = new Plot(TITLE, "Frame", "Mean Intensity");
    plot.addPoints(x, data[n - 1], null, Plot.LINE, "Foreground");
    final LUT lut = LutHelper.createLut(LutColour.RED_BLUE);
    for (int j = 1; j < n; j++) {
      plot.setColor(LutHelper.getColour(lut, j - 1, 0, n - 1));
      plot.addPoints(x, data[j - 1], null, Plot.LINE, "Region" + j);
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

    // Align to the average intensity projection
    final ZProjector projector = new ZProjector(imp2);
    projector.setMethod(ZProjector.AVG_METHOD);
    projector.doProjection();
    align.initialiseReference(projector.getProjection().getProcessor(), WindowMethod.TUKEY, true);

    align.initialiseReference(imp2.getProcessor(), WindowMethod.TUKEY, true);

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
      // ip.translate(x, y);
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
    // S_t = alpha * Y_t + (1-alpha) * S_t-1
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

  private LocalList<Roi> detectBleachedRegions(final ImageStack events) {
    // Post-process the events to join contiguous regions and remove speckles
    IJ.showStatus("!Detecting bleached regions...");
    final LocalList<Roi> rois = new LocalList<>();
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
          rois.add(outlines[j]);
        }
      }
    }

    IJ.protectStatusBar(false);
    return rois;
  }

  private ImageProcessor createRegionsMask(final ByteProcessor mask, final LocalList<Roi> rois) {
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
      regions.fill(rois.unsafeGet(0));
    }

    if (settings.showBleachedRegions) {
      final ImagePlus regionsImp = ImageJUtils.display("Regions", regions);
      regionsImp.setDisplayRange(0, n);
      regionsImp.updateAndDraw();
    }
    return regions;
  }

  private static void addRoisToImage(ImagePlus alignedImp, final LocalList<Roi> rois) {
    // Add rois to the aligned image.
    // Adding to the original image requires an offset for each aligned frame and positioning
    // the roi on each slice.
    if (alignedImp != null) {
      final Overlay o = new Overlay();
      rois.forEach(o::add);
      alignedImp.setOverlay(o);
    }
  }
}
