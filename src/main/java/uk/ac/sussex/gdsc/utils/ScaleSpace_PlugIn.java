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

import com.google.common.util.concurrent.Futures;
import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.Prefs;
import ij.plugin.filter.GaussianBlur;
import ij.plugin.filter.PlugInFilter;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;
import imagescience.feature.Differentiator;
import imagescience.image.FloatImage;
import imagescience.image.Image;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.function.UnaryOperator;
import org.apache.commons.lang3.tuple.Pair;
import uk.ac.sussex.gdsc.UsageTracker;
import uk.ac.sussex.gdsc.core.ij.ImageJUtils;
import uk.ac.sussex.gdsc.core.ij.gui.ExtendedGenericDialog;
import uk.ac.sussex.gdsc.core.logging.Ticker;
import uk.ac.sussex.gdsc.core.utils.LocalList;
import uk.ac.sussex.gdsc.core.utils.MathUtils;
import uk.ac.sussex.gdsc.core.utils.concurrent.ConcurrencyUtils;

/**
 * Create a scale-space representation of an image using a 2D Gaussian filter at different scales.
 *
 * @see <a href="https://en.wikipedia.org/wiki/Scale_space">Scale space</a>
 */
public class ScaleSpace_PlugIn implements PlugInFilter {
  private static final String TITLE = "Scale Space";

  /** The flags specifying the capabilities and needs. */
  private static final int FLAGS = DOES_ALL | NO_CHANGES;

  /**
   * The flags specifying the capabilities and needs for a gradient image (Difference of Gaussians
   * or Laplacian of Gaussian).
   */
  private static final int DOG_FLAGS = DOES_8G | DOES_16 | DOES_32 | NO_CHANGES;

  private ImagePlus imp;

  /** The current settings for the plugin instance. */
  private Settings settings;

  /**
   * Contains the settings that are the re-usable state of the plugin.
   */
  private static class Settings {
    private static final String[] OUTPUT_OPTIONS =
        {"Gaussian", "Difference of Gaussians", "Laplacian of Gaussian"};
    private static final int OPT_GAUSS = 0;
    private static final int OPT_DOG = 1;
    private static final int OPT_LOG = 2;

    /** The last settings used by the plugin. This should be updated after plugin execution. */
    private static final AtomicReference<Settings> lastSettings =
        new AtomicReference<>(new Settings());

    double minScale;
    double maxScale;
    int outputOption;
    int subIntervals;

    /**
     * Default constructor.
     */
    Settings() {
      minScale = 1;
    }

    /**
     * Copy constructor.
     *
     * @param source the source
     */
    private Settings(Settings source) {
      minScale = source.minScale;
      maxScale = source.maxScale;
      outputOption = source.outputOption;
      subIntervals = source.subIntervals;
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
    this.imp = imp;
    return showDialog();
  }

  private int showDialog() {
    settings = Settings.load();

    final ExtendedGenericDialog gd = new ExtendedGenericDialog(TITLE);
    gd.addMessage("Computes a scale-space representation of an image.\n"
        + "All scales up to the minimum image dimension in X or Y are computed.");
    gd.addNumericField("Min_scale", settings.minScale, 2);
    gd.addNumericField("Max_scale", settings.maxScale, 2);
    gd.addChoice("Output", Settings.OUTPUT_OPTIONS, settings.outputOption);
    gd.addNumericField("Sub_interval_steps", settings.subIntervals, 0);
    gd.addHelp(uk.ac.sussex.gdsc.help.UrlUtils.UTILITY);
    gd.showDialog();
    settings.save();
    if (gd.wasCanceled()) {
      return DONE;
    }
    settings.minScale = gd.getNextNumber();
    settings.maxScale = gd.getNextNumber();
    settings.outputOption = gd.getNextChoiceIndex();
    settings.subIntervals = Math.max(0, (int) gd.getNextNumber());

    if (gd.invalidNumber()) {
      IJ.error(TITLE, "Bad input number");
      return DONE;
    }

    if (settings.outputOption == Settings.OPT_LOG && !ImageScienceUtils.hasImageScience()) {
      IJ.error("The Laplacian requires ImageScience version " + ImageScienceUtils.MIN_IS_VERSION
          + " or higher");
      return DONE;
    }

    return (settings.outputOption == Settings.OPT_GAUSS) ? FLAGS : DOG_FLAGS;
  }

  @Override
  public void run(ImageProcessor ip) {
    final int width = ip.getWidth();
    final int height = ip.getHeight();
    final ImageStack stack = new ImageStack(width, height);

    if (settings.outputOption == Settings.OPT_GAUSS) {
      stack.addSlice("t=0", ip);
    }

    // Default limits on the scale:
    // A Gaussian blur below 0.5 in a single dimension, or
    // a Gaussian blur with a width above the dimension size is meaningless.
    // For a more reasonable upper limit use 1/4 of the dimension size to avoid very large
    // Gaussian kernels.
    final double limitT = MathUtils.pow2(Math.min(width, height) / 4);
    final double minT = MathUtils.clip(0.25, limitT, settings.minScale);
    // If max is 0 use the computed max for auto-range
    final double maxT =
        MathUtils.clip(minT, limitT, (settings.maxScale > 0 ? settings.maxScale : limitT));

    // Multi-thread
    final ExecutorService executor = Executors.newFixedThreadPool(Prefs.getThreads());
    final List<Future<Pair<Double, ImageProcessor>>> futures = new LocalList<>();

    final BiFunction<ImageProcessor, Double, ImageProcessor> fun = createFunction();
    final Ticker ticker = ImageJUtils.createTicker(countSteps(minT, maxT), 2, "Computing ...");

    for (double scaleT = minT; scaleT <= maxT; scaleT *= 2) {
      final double step = scaleT / (settings.subIntervals + 1);
      for (int i = 0; i <= settings.subIntervals; i++) {
        final Double t = scaleT + i * step;
        futures.add(executor.submit(() -> {
          // Since it is 2D use sqrt(t) for each dimension
          final Double sigma = Math.sqrt(t);
          final ImageProcessor scaledIp = fun.apply(ip, sigma);
          ticker.tick();
          return Pair.of(t, scaledIp);
        }));
      }
    }

    ConcurrencyUtils.waitForCompletionUncheckedT(futures);

    ImageJUtils.finished();

    // Sort by scale and add to the stack.
    // Note the futures are all complete so get the result unchecked.
    futures.stream().map(Futures::getUnchecked)
        .sorted((r1, r2) -> Double.compare(r1.getLeft(), r2.getLeft())).forEachOrdered(
            pair -> stack.addSlice("t=" + MathUtils.rounded(pair.getLeft()), pair.getRight()));

    String suffix;
    ImageStack outputStack;
    if (settings.outputOption == Settings.OPT_DOG && stack.getSize() > 1) {
      suffix = "Difference of Gaussians";
      outputStack = new ImageStack(width, height);
      float[] pixels2 = (float[]) stack.getPixels(1);
      for (int slice = 2; slice <= stack.size(); slice++) {
        final float[] pixels1 = pixels2;
        pixels2 = (float[]) stack.getPixels(slice);
        // Q. Does this need to be scale normalised?
        for (int i = 0; i < pixels1.length; i++) {
          final float v = pixels1[i] - pixels2[i];
          pixels1[i] = v;
        }
        outputStack.addSlice(stack.getSliceLabel(slice) + "-" + stack.getSliceLabel(slice - 1),
            pixels1);
      }
    } else if (settings.outputOption == Settings.OPT_LOG) {
      suffix = "Laplacian of Gaussian";
      outputStack = stack;
    } else {
      suffix = TITLE;
      outputStack = stack;
    }

    ImageJUtils.display(imp.getTitle() + " " + suffix, outputStack);
  }

  /**
   * Count the number of steps between min and max T accounting for sub intervals.
   *
   * @param minT the min T
   * @param maxT the max T
   * @return the steps
   */
  private long countSteps(double minT, double maxT) {
    long steps = 0;
    for (double scaleT = minT; scaleT <= maxT; scaleT *= 2) {
      steps++;
    }
    return steps * (settings.subIntervals + 1);
  }

  /**
   * Creates the function to processor the input image processor.
   *
   * @return the function
   */
  private BiFunction<ImageProcessor, Double, ImageProcessor> createFunction() {
    final UnaryOperator<ImageProcessor> converter = createConverter();

    if (settings.outputOption == Settings.OPT_LOG) {
      return (ip, sigma) -> {
        FloatProcessor fp = (FloatProcessor) converter.apply(ip);
        final FloatImage img = (FloatImage) Image.wrap(new ImagePlus(null, fp));
        final Differentiator differentiator = new Differentiator();
        final Image Ixx = differentiator.run(img.duplicate(), sigma, 2, 0, 0);
        final Image Iyy = differentiator.run(img, sigma, 0, 2, 0);
        Iyy.add(Ixx);
        fp = (FloatProcessor) Iyy.imageplus().getProcessor();

        // Post-processing for scale normalisation and negation.
        // This makes the appearance match the Difference of Gaussians (but the scale
        // make be different).
        final double norm = -sigma * sigma;
        final float[] data = (float[]) fp.getPixels();
        for (int i = 0; i < data.length; i++) {
          data[i] *= norm;
        }

        return fp;
      };
    }

    // Others use a Gaussian blur
    return (ip, sigma) -> {
      final GaussianBlur gb = new GaussianBlur();
      gb.showProgress(false);
      ip = converter.apply(ip);
      gb.blurGaussian(ip, sigma);
      return ip;
    };
  }

  /**
   * Creates the converter to prepare the input image processor.
   *
   * @return the converter
   */
  private UnaryOperator<ImageProcessor> createConverter() {
    // Duplicate the processor.
    if (settings.outputOption == Settings.OPT_GAUSS) {
      return ImageProcessor::duplicate;
    }
    // Float-image required for correct DoG or LoG
    return ip -> {
      return (ip instanceof FloatProcessor) ? (FloatProcessor) ip.duplicate() : ip.toFloat(0, null);
    };
  }
}
