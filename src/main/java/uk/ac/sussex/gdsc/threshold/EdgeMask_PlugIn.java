/*-
 * #%L
 * Genome Damage and Stability Centre ImageJ Plugins
 *
 * Software for microscopy image analysis
 * %%
 * Copyright (C) 2011 - 2019 Alex Herbert
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

package uk.ac.sussex.gdsc.threshold;

import uk.ac.sussex.gdsc.UsageTracker;
import uk.ac.sussex.gdsc.core.threshold.AutoThreshold;
import uk.ac.sussex.gdsc.core.threshold.AutoThreshold.Method;

import ij.IJ;
import ij.ImageJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.Prefs;
import ij.gui.DialogListener;
import ij.gui.GenericDialog;
import ij.gui.Roi;
import ij.measure.Measurements;
import ij.plugin.filter.ExtendedPlugInFilter;
import ij.plugin.filter.GaussianBlur;
import ij.plugin.filter.PlugInFilterRunner;
import ij.process.FloatProcessor;
import ij.process.FloodFiller;
import ij.process.ImageProcessor;
import ij.process.ImageStatistics;

import imagescience.ImageScience;
import imagescience.feature.Edges;
import imagescience.feature.Laplacian;
import imagescience.image.Aspects;
import imagescience.image.FloatImage;
import imagescience.image.Image;
import imagescience.segment.Thresholder;
import imagescience.segment.ZeroCrosser;
import imagescience.utility.VersionChecker;

import java.awt.AWTEvent;
import java.awt.Rectangle;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Create an edge mask from an image.
 *
 * <p>Computes the Laplacian zero crossing points of an image (i.e. where the gradient is steepest).
 * Optionally allows: the edge lines to be pruned to leave only closed loops; and filling of loops
 * to create a mask.
 *
 * <p>Requires the ImageScience library that supports the Laplacian plugin of FeatureJ. The
 * imagescience.jar must be installed in the ImageJ plugins folder.
 *
 * @see <a href=
 *      "http://www.imagescience.org/meijering/software/featurej">http://www.imagescience.org/meijering/software/featurej/</a>
 */
public class EdgeMask_PlugIn implements ExtendedPlugInFilter, DialogListener {
  private static final String TITLE = "Edge Mask Creator";
  private static final String MIN_IS_VERSION = "3.0.0";

  private static final String[] METHODS =
      {"Above background", "Laplacian edges", "Gradient edges", "Maximum gradient edges"};

  private static final byte TYPE_NONE = 0x00;
  private static final byte TYPE_EDGE = 0x01;
  private static final byte TYPE_SINGLE = 0x02;
  private static final byte TYPE_FILL = 0x04;

  private static final int[] DIR_X_OFFSET = {0, 1, 1, 1, 0, -1, -1, -1};
  private static final int[] DIR_Y_OFFSET = {-1, -1, 0, 1, 1, 1, 0, -1};

  private double minDisplayValue;
  private double maxDisplayValue;
  private static double background;
  private static double smooth = 1;
  private static int method;
  private static double lowerPercentile = 99.0;
  private static double upperPercentile = 99.7;
  private static boolean prune;
  private static boolean fill;
  private static boolean replaceImage;
  private static double percent = Prefs.getDouble("gdsc.EdgeMaskPercent", 99);

  private static final int FLAGS = DOES_8G | DOES_16 | DOES_32 | FINAL_PROCESSING;
  private int flags2;

  // image dimensions
  private int maxx;
  private int maxy;
  private int xlimit;
  private int ylimit;
  private int[] offset;

  /** {@inheritDoc} */
  @Override
  public int setup(String arg, ImagePlus imp) {
    UsageTracker.recordPlugin(this.getClass(), arg);

    if (imp == null) {
      IJ.noImage();
      return DONE;
    }
    try {
      // Entire block in try-catch as the library may not be present
      if (VersionChecker.compare(ImageScience.version(), MIN_IS_VERSION) < 0) {
        throw new IllegalStateException();
      }
    } catch (final Throwable ex) {
      IJ.error("This plugin requires ImageScience version " + MIN_IS_VERSION + " or higher");
      return DONE;
    }

    if (arg.equals("final")) {
      final boolean doesStacks = ((flags2 & DOES_STACKS) != 0);

      ImageProcessor maskIp = (doesStacks) ? null : imp.getProcessor();
      if (!replaceImage) {
        // Copy the mask (before it is reset) if we are not processing the entire stack
        if (!doesStacks) {
          maskIp = maskIp.duplicate();
        }

        // Reset the main image
        final ImageProcessor ip = imp.getProcessor();
        ip.reset();
        ip.setMinAndMax(minDisplayValue, maxDisplayValue);
        imp.updateAndDraw();
      }

      if (doesStacks) {
        // Process all slices of the stack

        // Disable the progress bar for the blur.
        // This does not effect IJ.showProgress(int, int), only IJ.showProgress(double)
        // Allows the progress to be correctly reported.
        final ImageJ ij = IJ.getInstance();
        if (ij != null) {
          ij.getProgressBar().setBatchMode(true);
        }

        final Roi roi = imp.getRoi();
        // Multi-thread for speed
        final ExecutorService threadPool = Executors.newCachedThreadPool();
        final List<Future<?>> futures = new LinkedList<>();
        final ImageStack stack = imp.getImageStack();
        final ImageStack newStack =
            new ImageStack(stack.getWidth(), stack.getHeight(), stack.getSize());
        IJ.showStatus("Processing stack ...");
        for (int slice = 1; slice <= stack.getSize(); slice++) {
          IJ.showProgress(slice - 1, stack.getSize());
          final ImageProcessor ip = stack.getProcessor(slice).duplicate();
          ip.setRoi(roi);
          futures.add(threadPool.submit(new MaskCreator(ip, newStack, slice, this)));
        }
        waitForCompletion(futures);
        IJ.showStatus("");
        IJ.showProgress(stack.getSize(), stack.getSize());

        // Check the final stack for errors
        final Object[] images = newStack.getImageArray();
        if (images == null) {
          IJ.log(TITLE + " Error: The output stack is empty");
        } else {
          boolean error = false;
          for (int i = 0; i < images.length; i++) {
            if (images[i] == null) {
              IJ.log(TITLE + " Error: Output stack is empty at slice " + (i + 1));
              error = true;
            }
          }
          if (error) {
            return DONE;
          }
        }

        if (replaceImage) {
          imp.setStack(newStack);
          imp.updateAndDraw();
        } else {
          new ImagePlus(imp.getTitle() + " Edge Mask", newStack).show();
        }
      } else if (replaceImage) {
        // If it is a single frame then we can convert to a byte processor
        if (imp.getStackSize() == 1) {
          imp.setProcessor(maskIp.convertToByte(false));
          // Otherwise we have a strange mask image in the middle of a stack
        }
      } else {
        new ImagePlus(imp.getTitle() + " Edge Mask", maskIp.convertToByte(false)).show();
      }
    }

    return FLAGS;
  }

  /** {@inheritDoc} */
  @Override
  public void run(ImageProcessor ip) {
    createMask(ip);
  }

  /** {@inheritDoc} */
  @Override
  public int showDialog(ImagePlus imp, String command, PlugInFilterRunner pfr) {
    final ImageProcessor ip = imp.getProcessor();
    ip.snapshot();
    minDisplayValue = ip.getMin();
    maxDisplayValue = ip.getMax();
    final double[] limits = getLimits(ip);
    final double minValue = limits[0];
    final double maxValue = limits[1];

    if (background > maxValue) {
      background = 0;
    }

    // Show the Otsu threshold for the image
    final String threshold = (limits[2] > 0) ? ".\nOtsu threshold = " + (int) limits[2] : "";

    final GenericDialog gd = new GenericDialog(TITLE);
    gd.addMessage("Create a new mask image" + threshold);

    gd.addChoice("Method", METHODS, METHODS[method]);
    gd.addSlider("Background", minValue, maxValue, background);
    gd.addMessage("Image smoothing");
    gd.addSlider("Smooth", 0.0, 4.5, smooth);
    gd.addMessage("Limit for gradient edges");
    gd.addNumericField("Lower_percentile", lowerPercentile, 2);
    gd.addNumericField("Upper_percentile", upperPercentile, 2);
    gd.addMessage("Edge options");
    gd.addCheckbox("Prune", prune);
    gd.addCheckbox("Fill", fill);
    gd.addCheckbox("Replace_image", replaceImage);
    // gd.addNumericField("Background_Percent", percent, 1);

    gd.addPreviewCheckbox(pfr);
    gd.addDialogListener(this);
    gd.addHelp(uk.ac.sussex.gdsc.help.UrlUtils.UTILITY);
    gd.showDialog();

    if (gd.wasCanceled() || !dialogItemChanged(gd, null)) {
      return DONE;
    }

    flags2 = IJ.setupDialog(imp, FLAGS);

    return FLAGS;
  }

  private static double[] getLimits(ImageProcessor ip) {
    final ImageStatistics stats = ImageStatistics.getStatistics(ip, Measurements.MIN_MAX, null);
    final double[] limits = new double[] {stats.min, stats.max, 0};

    // Use histogram to cover x% of the data
    final int[] data = ip.getHistogram();

    if (data == null) {
      return limits;
    }

    // Only RGB/8/16 bit greyscale image have a histogram

    // Get a suggested background threshold
    limits[2] = AutoThreshold.getThreshold(Method.OTSU, data);

    // Get the upper limit using a fraction of the data
    final int limit = (int) (percent * ip.getPixelCount() / 100.0);
    int count = 0;
    int index = 0;
    while (index < data.length) {
      count += data[index];
      if (count > limit) {
        break;
      }
      index++;
    }

    limits[1] = index;

    return limits;
  }

  /** Listener to modifications of the input fields of the dialog. */
  @Override
  public boolean dialogItemChanged(GenericDialog gd, AWTEvent event) {
    method = gd.getNextChoiceIndex();
    background = gd.getNextNumber();
    if (gd.invalidNumber()) {
      return false;
    }
    smooth = gd.getNextNumber();
    if (gd.invalidNumber()) {
      return false;
    }
    lowerPercentile = gd.getNextNumber();
    if (gd.invalidNumber()) {
      return false;
    }
    upperPercentile = gd.getNextNumber();
    if (gd.invalidNumber()) {
      return false;
    }
    prune = gd.getNextBoolean();
    fill = gd.getNextBoolean();
    replaceImage = gd.getNextBoolean();
    // percent = gd.getNextNumber();
    // Prefs.set("gdsc.EdgeMaskPercent", percent);
    return true;
  }

  /** {@inheritDoc} */
  @Override
  public void setNPasses(int passes) {
    // Ignore
  }

  /**
   * Create a mask using the configured source image.
   *
   * @param ip the image
   */
  public void createMask(ImageProcessor ip) {
    if (ip == null) {
      return;
    }
    initialise(ip.getWidth(), ip.getHeight());

    // Add image smoothing before thresholding
    ImageProcessor smoothIp = ip;
    if (smooth > 0) {
      final GaussianBlur gb = new GaussianBlur();
      smoothIp = ip.duplicate();
      gb.blurGaussian(smoothIp, smooth, smooth, 0.0002);
    }

    if (method == 2 || method == 3) {
      // Compute the gradient image using the ImageScience library
      final Image img = Image.wrap(new ImagePlus(null, ip.duplicate()));
      Image newimg = new FloatImage(img);

      // Compute the Gradient image
      final Aspects aspects = newimg.aspects();
      final Edges edges = new Edges();
      final boolean nonmaxsup = method == 3;
      newimg = edges.run(newimg, (smooth > 0) ? smooth : 1, nonmaxsup);
      newimg.aspects(aspects);

      FloatProcessor gradientIp = (FloatProcessor) newimg.imageplus().getProcessor();

      // Keep all gradients above the configured percentile
      final boolean lowthres = lowerPercentile > 0;
      final boolean highthres = upperPercentile > 0;
      final int thresholdMode = (lowthres ? 10 : 0) + (highthres ? 1 : 0);
      if (thresholdMode > 0) {
        float[] data = (float[]) gradientIp.getPixels();
        data = Arrays.copyOf(data, data.length);
        Arrays.sort(data);
        final double highval = getLimit(data, upperPercentile);
        final double lowval = getLimit(data, lowerPercentile);

        final Thresholder thres = new Thresholder();
        switch (thresholdMode) {
          case 1:
            thres.hard(newimg, highval);
            break;
          case 10:
            thres.hard(newimg, lowval);
            break;
          default:
            thres.hysteresis(newimg, lowval, highval);
            break;
        }

        gradientIp = (FloatProcessor) newimg.imageplus().getProcessor();
      }

      // Get the mask
      final ImageProcessor maskIp = gradientIp.convertToByte(false);

      // Keep objects above a threshold value
      final int threshold = (int) background;
      for (int i = ip.getPixelCount(); i-- > 0;) {
        if (smoothIp.get(i) < threshold) {
          maskIp.set(i, 0);
        }
      }

      markExtraLines(maskIp, prune);

      for (int i = ip.getPixelCount(); i-- > 0;) {
        ip.set(i, (maskIp.get(i) > 0) ? 255 : 0);
      }
    } else if (method == 1) {
      // Compute the Laplacian zero-crossing image using the ImageScience library
      final Image img = Image.wrap(new ImagePlus(null, ip.duplicate()));
      Image newimg = new FloatImage(img);

      // Compute the Laplacian image
      final Aspects aspects = newimg.aspects();
      final Laplacian laplace = new Laplacian();
      newimg = laplace.run(newimg, (smooth > 0) ? smooth : 1);
      newimg.aspects(aspects);

      ImageProcessor laplacianIp = null;
      // new ImagePlus("laplace", newimg.imageplus().getProcessor().duplicate()).show();
      if (fill) {
        laplacianIp = newimg.imageplus().getProcessor().duplicate();
      }

      // Find the zero crossings
      final ZeroCrosser zc = new ZeroCrosser();
      zc.run(newimg);

      // Get the mask
      final ImageProcessor maskIp = newimg.imageplus().getProcessor().convertToByte(false);

      // Keep objects above a threshold value
      final int threshold = (int) background;
      for (int i = ip.getPixelCount(); i-- > 0;) {
        if (smoothIp.get(i) < threshold) {
          maskIp.set(i, 0);
        }
      }

      markExtraLines(maskIp, prune);

      // Fill image on negative Laplacian side of zero boundary pixels
      if (fill) {
        fill(maskIp, laplacianIp);
      }

      for (int i = ip.getPixelCount(); i-- > 0;) {
        ip.set(i, (maskIp.get(i) > 0) ? 255 : 0);
      }
    } else {
      // Simple mask using the background level
      final int threshold = (int) background;

      // Keep objects above a threshold value
      for (int i = ip.getPixelCount(); i-- > 0;) {
        ip.set(i, (smoothIp.get(i) >= threshold) ? 255 : 0);
      }
    }

    // Support masking
    final Rectangle roi = ip.getRoi();
    if (roi != null && roi.width < maxx && roi.height < maxy) {
      // Blank outside ROI
      ip.setColor(0);
      ip.fillOutside(new Roi(roi));

      final byte[] mask = ip.getMaskArray();
      if (mask != null) {
        int maskIndex = 0;
        for (int y = 0; y < roi.height; y++) {
          int index = (roi.y + y) * maxx + roi.x;
          for (int x = 0; x < roi.width; x++, index++) {
            if (mask[maskIndex] == 0) {
              ip.set(index, 0);
            }
            maskIndex++;
          }
        }
      }
    }

    ip.setMinAndMax(0, 255);
  }

  private static double getLimit(float[] data, double percentile) {
    if (percentile < 0 || percentile >= 100) {
      return 0;
    }

    return data[(int) (data.length * percentile / 100)];
  }

  /**
   * Mark lines that do form closed loops. Adapted from {@link ij.plugin.filter.MaximumFinder}
   * Optionally prune these lines
   *
   * @param ip the ip
   * @param prune the prune flag
   */
  void markExtraLines(ImageProcessor ip, boolean prune) {
    final byte[] types = (byte[]) ip.getPixels();

    // Mark edges
    for (int index = types.length; index-- > 0;) {
      if (types[index] != TYPE_NONE) {
        types[index] = TYPE_EDGE;
      }
    }

    // Mark single lines
    for (int index = types.length; index-- > 0;) {
      if ((types[index] & TYPE_EDGE) == TYPE_EDGE && (types[index] & TYPE_SINGLE) != TYPE_SINGLE) {
        final int nRadii = countRadii(types, index); // number of lines radiating
        if (nRadii == 0) {
          types[index] |= TYPE_SINGLE;
        } else if (nRadii == 1) {
          removeLineFrom(types, index);
        }
      }
    }

    // Prune single lines/points
    if (prune) {
      for (int index = types.length; index-- > 0;) {
        if ((types[index] & TYPE_SINGLE) == TYPE_SINGLE) {
          types[index] = 0;
        }
      }
    }
  }

  /**
   * Delete a line starting at (x,y) up to the next (8-connected) vertex.
   *
   * @param types the types
   * @param index the index
   */
  void removeLineFrom(byte[] types, int index) {
    types[index] |= TYPE_SINGLE;
    boolean continues;
    do {
      final int y = index / maxx;
      final int x = index % maxx;

      continues = false;
      final boolean isInner = (y != 0 && y != ylimit) && (x != 0 && x != xlimit);
      for (int d = 0; d < 8; d++) {
        if (isInner || isWithinXy(x, y, d)) {
          final int index2 = index + offset[d];
          if ((types[index2] & TYPE_EDGE) == TYPE_EDGE
              && (types[index2] & TYPE_SINGLE) != TYPE_SINGLE) {
            final int radii = countRadii(types, index2);
            if (radii <= 1) { // found a point or line end
              index = index2;
              types[index] |= TYPE_SINGLE; // delete the point
              continues = radii == 1; // continue along that line
              break;
            }
          }
        }
      }
    }
    while (continues);
  }

  /**
   * Analyze the neighbors of a pixel (x, y) in a byte image; pixels != 0 are considered foreground.
   * Edge pixels are considered foreground.
   *
   * @param types the byte image
   * @param index coordinate of the point
   * @return Number of lines emanating from this point. Zero if the point is embedded in either
   *         foreground or background
   */
  int countRadii(byte[] types, int index) {
    int countTransitions = 0;
    boolean prevPixelSet = true;
    boolean firstPixelSet = true; // initialize to make the compiler happy
    final int y = index / maxx;
    final int x = index % maxx;

    final boolean isInner = (y != 0 && y != ylimit) && (x != 0 && x != xlimit);
    for (int d = 0; d < 8; d++) { // walk around the point and note every no-line->line transition
      boolean pixelSet;
      if (isInner || isWithinXy(x, y, d)) {
        pixelSet = ((types[index + offset[d]] & TYPE_EDGE) == TYPE_EDGE
            && (types[index + offset[d]] & TYPE_SINGLE) != TYPE_SINGLE);
      } else {
        // Outside of boundary - count as foreground so lines touching the egde are not pruned.
        pixelSet = true;
      }
      if (pixelSet && !prevPixelSet) {
        countTransitions++;
      }
      prevPixelSet = pixelSet;
      if (d == 0) {
        firstPixelSet = pixelSet;
      }
    }
    if (firstPixelSet && !prevPixelSet) {
      countTransitions++;
    }
    return countTransitions;
  }

  /**
   * Fill the image processor closed loops. Only fill background regions defined by the Laplacian
   * image.
   *
   * @param maskIp The mask image
   * @param laplacianIp The original image laplacian
   */
  void fill(ImageProcessor maskIp, ImageProcessor laplacianIp) {

    // Adapted from ij.plugin.binary.Binary.fill(...)
    final int tyepBackground = TYPE_NONE;
    final int width = maskIp.getWidth();
    final int height = maskIp.getHeight();
    final FloodFiller ff = new FloodFiller(maskIp);
    maskIp.setColor(TYPE_FILL);
    for (int y = 0; y < height; y++) {
      if (maskIp.get(0, y) == tyepBackground && laplacianIp.get(0, y) < 0) {
        ff.fill(0, y);
      }
      if (maskIp.get(width - 1, y) == tyepBackground && laplacianIp.get(width - 1, y) < 0) {
        ff.fill(width - 1, y);
      }
    }
    for (int x = 0; x < width; x++) {
      if (maskIp.get(x, 0) == tyepBackground && laplacianIp.get(x, 0) < 0) {
        ff.fill(x, 0);
      }
      if (maskIp.get(x, height - 1) == tyepBackground && laplacianIp.get(x, height - 1) < 0) {
        ff.fill(x, height - 1);
      }
    }
  }

  /**
   * Initialises the global width and height variables. Creates the direction offset tables.
   *
   * @param width the width
   * @param height the height
   */
  public void initialise(int width, int height) {
    if (maxx == width && maxy == height) {
      return;
    }

    maxx = width;
    maxy = height;

    xlimit = maxx - 1;
    ylimit = maxy - 1;

    // Create the offset table (for single array 3D neighbour comparisons)
    offset = new int[DIR_X_OFFSET.length];
    for (int d = offset.length; d-- > 0;) {
      offset[d] = getIndex(DIR_X_OFFSET[d], DIR_Y_OFFSET[d]);
    }
  }

  /**
   * Return the single index associated with the x,y coordinates.
   *
   * @param x the x
   * @param y the y
   * @return The index
   */
  private int getIndex(int x, int y) {
    return maxx * y + x;
  }

  /**
   * returns whether the neighbour in a given direction is within the image. NOTE: it is assumed
   * that the pixel x,y itself is within the image! Uses class variables xlimit, ylimit: (dimensions
   * of the image)-1
   *
   * @param x x-coordinate of the pixel that has a neighbour in the given direction
   * @param y y-coordinate of the pixel that has a neighbour in the given direction
   * @param direction the direction from the pixel towards the neighbour
   * @return true if the neighbour is within the image (provided that x, y is within)
   */
  private boolean isWithinXy(int x, int y, int direction) {
    switch (direction) {
      case 0:
        return (y > 0);
      case 1:
        return (y > 0 && x < xlimit);
      case 2:
        return (x < xlimit);
      case 3:
        return (y < ylimit && x < xlimit);
      case 4:
        return (y < ylimit);
      case 5:
        return (y < ylimit && x > 0);
      case 6:
        return (x > 0);
      case 7:
        return (y > 0 && x > 0);
      default:
        return false;
    }
  }

  /**
   * Waits for all threads to complete computation.
   *
   * @param futures the futures
   */
  public static void waitForCompletion(List<Future<?>> futures) {
    try {
      for (final Future<?> f : futures) {
        f.get();
      }
    } catch (final ExecutionException ex) {
      ex.printStackTrace();
    } catch (final InterruptedException ex) {
      ex.printStackTrace();
    }
  }

  /**
   * Use a runnable for the image generation to allow multi-threaded operation.
   */
  private static class MaskCreator implements Runnable {
    ImageProcessor ip;
    ImageStack outputStack;
    int slice;
    EdgeMask_PlugIn mask;

    MaskCreator(ImageProcessor ip, ImageStack outputStack, int slice, EdgeMask_PlugIn mask) {
      this.ip = ip;
      this.outputStack = outputStack;
      this.slice = slice;
      this.mask = mask;
    }

    /** {@inheritDoc} */
    @Override
    public void run() {
      if (mask == null) {
        mask = new EdgeMask_PlugIn();
      }
      mask.createMask(ip);
      ip = ip.convertToByte(false);
      outputStack.setPixels(ip.getPixels(), slice);
    }
  }
}
