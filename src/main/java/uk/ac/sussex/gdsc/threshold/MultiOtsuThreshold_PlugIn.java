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
import uk.ac.sussex.gdsc.core.data.ComputationException;

import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.gui.GenericDialog;
import ij.gui.NewImage;
import ij.gui.Plot;
import ij.plugin.filter.PlugInFilter;
import ij.process.ImageProcessor;

import java.awt.Color;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Calculate multiple Otsu thresholds on the given image.
 *
 * <p>Algorithm: PS.Liao, TS.Chen, and PC. Chung, Journal of Information Science and Engineering,
 * vol 17, 713-727 (2001)
 *
 * <p>Original Coding by Yasunari Tosa (ytosa@att.net) (Feb. 19th, 2005) and available from the
 * ImageJ plugins site:<br> http://rsb.info.nih.gov/ij/plugins/multi-otsu-threshold.html
 *
 * <p>Adapted to allow 8/16 bit stack images and used a clipped histogram to increase speed. Added
 * output options.
 */
public class MultiOtsuThreshold_PlugIn implements PlugInFilter {
  private static final String TITLE = "Multi Otsu Threshold";

  private ImagePlus imp;

  /** The current settings for the plugin instance. */
  private Settings settings;

  /**
   * Contains the settings that are the re-usable state of the plugin.
   */
  private static class Settings {
    /** The last settings used by the plugin. This should be updated after plugin execution. */
    private static final AtomicReference<Settings> lastSettings =
        new AtomicReference<>(new Settings());

    /** The number of levels. */
    int levels = 2;
    /**
     * Set to true to use the stack histogram for the channel and frame. False processes the current
     * slice.
     */
    boolean doStack = true;
    /** Set to true to ignore zero. */
    boolean ignoreZero = true;
    /** Set to true to show histogram. */
    boolean showHistogram = true;
    /** Set to true to show regions. */
    boolean showRegions = true;
    /** Set to true to show masks. */
    boolean showMasks;
    /** Set to true to log messages. */
    boolean logMessages = true;

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
      levels = source.levels;
      doStack = source.doStack;
      ignoreZero = source.ignoreZero;
      showHistogram = source.showHistogram;
      showRegions = source.showRegions;
      showMasks = source.showMasks;
      logMessages = source.logMessages;
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
    UsageTracker.recordPlugin(this.getClass(), arg);

    if (imp == null) {
      IJ.noImage();
      return DONE;
    }
    this.imp = imp;
    return DOES_8G + DOES_16 + NO_CHANGES;
  }

  /** {@inheritDoc} */
  @Override
  public void run(ImageProcessor ip) {
    settings = Settings.load();

    final GenericDialog gd = new GenericDialog(TITLE);
    gd.addMessage("Multi-level Otsu thresholding on image stack");
    final String[] items = {"2", "3", "4", "5",};
    gd.addChoice("Levels", items, items[settings.levels - 2]);
    if (imp.getStackSize() > 1) {
      gd.addCheckbox("Do_stack", settings.doStack);
    }
    gd.addCheckbox("Ignore_zero", settings.ignoreZero);
    gd.addCheckbox("Show_histogram", settings.showHistogram);
    gd.addCheckbox("Show_regions", settings.showRegions);
    gd.addCheckbox("Show_masks", settings.showMasks);
    gd.addCheckbox("Log_thresholds", settings.logMessages);
    gd.addHelp(uk.ac.sussex.gdsc.help.UrlUtils.UTILITY);
    gd.showDialog();
    if (gd.wasCanceled()) {
      return;
    }

    settings.levels = gd.getNextChoiceIndex() + 2;
    if (imp.getStackSize() > 1) {
      settings.doStack = gd.getNextBoolean();
    }
    settings.ignoreZero = gd.getNextBoolean();
    settings.showHistogram = gd.getNextBoolean();
    settings.showRegions = gd.getNextBoolean();
    settings.showMasks = gd.getNextBoolean();
    settings.logMessages = gd.getNextBoolean();

    settings.save();

    if (!(settings.showHistogram || settings.showRegions || settings.logMessages
        || settings.showMasks)) {
      IJ.error(TITLE, "No output options");
      return;
    }

    // Run on whole stack
    if (settings.doStack) {
      run(imp, settings.levels);
    } else {
      final ImagePlus newImp = imp.createImagePlus();
      newImp.setTitle(String.format("%s (c%d,z%d,t%d)", imp.getTitle(), imp.getChannel(),
          imp.getSlice(), imp.getFrame()));
      newImp.setProcessor(imp.getProcessor());
      run(newImp, settings.levels);
    }
  }

  /**
   * Calculate Otsu thresholds on the given image. Output the thresholds to the IJ log.
   *
   * <p>Optionally outputs the image histogram and the threshold regions depending on the class
   * variables.
   *
   * @param imp The image
   * @param levels The number of levels
   */
  public void run(ImagePlus imp, int levels) {
    final int[] offset = new int[1];
    final float[] histogram = buildHistogram(imp, offset);

    final float[] maxSig = new float[1];
    final int[] threshold = getThresholds(levels, maxSig, offset, histogram);

    if (settings.logMessages) {
      showThresholds(levels, maxSig, threshold);
    }
    if (settings.showHistogram) {
      showHistogram(histogram, threshold, offset[0], imp.getTitle() + " Histogram");
    }
    if (settings.showRegions) {
      showRegions(levels, threshold, imp);
    }
    if (settings.showMasks) {
      showMasks(levels, threshold, imp);
    }
  }

  /**
   * Calculate Otsu thresholds on the given image.
   *
   * @param imp The image
   * @param levels The number of levels
   * @return The thresholds
   */
  public int[] calculateThresholds(ImagePlus imp, int levels) {
    return calculateThresholds(imp, levels, null);
  }

  /**
   * Calculate Otsu thresholds on the given image.
   *
   * @param imp The image
   * @param levels The number of levels
   * @param maxSig The maximum between class significance
   * @return The thresholds
   */
  public int[] calculateThresholds(ImagePlus imp, int levels, float[] maxSig) {
    final int[] offset = new int[1];
    final float[] histogram = buildHistogram(imp, offset);

    return getThresholds(levels, maxSig, offset, histogram);
  }

  /**
   * Calculate Otsu thresholds on the given image.
   *
   * @param data The histogram data
   * @param levels The number of levels
   * @return The thresholds
   */
  public int[] calculateThresholds(int[] data, int levels) {
    return calculateThresholds(data, levels, null);
  }

  /**
   * Calculate Otsu thresholds on the given image.
   *
   * @param data The histogram data
   * @param levels The number of levels
   * @param maxSig The maximum between class significance
   * @return The thresholds
   */
  public int[] calculateThresholds(int[] data, int levels, float[] maxSig) {
    final int[] offset = new int[1];
    final float[] histogram = buildHistogram(data, offset);

    return getThresholds(levels, maxSig, offset, histogram);
  }

  private int[] getThresholds(int levels, float[] maxSig, int[] offset, float[] histogram) {
    /////////////////////////////////////////////
    // Build lookup tables from histogram
    ////////////////////////////////////////////
    final float[][] lookupTable = buildLookupTables(histogram);

    ////////////////////////////////////////////////////////
    // now M level loop levels dependent term
    ////////////////////////////////////////////////////////
    if (maxSig == null || maxSig.length < 1) {
      maxSig = new float[1];
    }
    final int[] threshold = new int[levels];
    maxSig[0] = findMaxSigma(levels, lookupTable, threshold);

    applyOffset(threshold, offset);

    return threshold;
  }

  /**
   * Create a histogram from image min to max. Normalise so integral is 1.
   *
   * <p>Return the image min in the offset variable
   *
   * @param imp Input image
   * @param offset Output image minimum (if array length >= 1)
   * @return The normalised image histogram
   */
  private float[] buildHistogram(ImagePlus imp, int[] offset) {
    // Get stack histogram - Use ImagePlus to get the ImageProcessor so maintaining the ROI
    final int currentSlice = imp.getCurrentSlice();
    imp.setSliceWithoutUpdate(1);
    final int[] data = imp.getProcessor().getHistogram();
    for (int slice = 2; slice <= imp.getStackSize(); slice++) {
      imp.setSliceWithoutUpdate(slice);
      final int[] tmp = imp.getProcessor().getHistogram();
      for (int i = 0; i < data.length; i++) {
        data[i] += tmp[i];
      }
    }
    imp.setSliceWithoutUpdate(currentSlice);

    return buildHistogram(data, offset);
  }

  /**
   * Create a histogram from image min to max. Normalise so integral is 1.
   *
   * <p>Return the image min in the offset variable
   *
   * @param data The histogram data
   * @param offset Output image minimum (if array length >= 1)
   * @return The normalised image histogram
   */
  private float[] buildHistogram(int[] data, int[] offset) {
    if (settings.ignoreZero) {
      data[0] = 0;
    }

    // Bracket the histogram to the range that holds data to make it quicker
    int minbin = 0;
    int maxbin = data.length - 1;
    while (data[minbin] == 0 && minbin < data.length) {
      minbin++;
    }
    while (data[maxbin] == 0 && maxbin > 0) {
      maxbin--;
    }
    if (maxbin < minbin) {
      throw new ComputationException("Data contains no values");
    }

    // ROI masking changes the histogram so total up the number of used pixels
    long total = 0;
    for (int i = minbin; i <= maxbin; i++) {
      total += data[i];
    }

    // This should not be possible due to histogram bracketing
    assert total != 0 : "Total is zero";

    final int[] data2 = new int[(maxbin - minbin) + 1];
    for (int i = minbin; i <= maxbin; i++) {
      data2[i - minbin] = data[i];
    }

    // note the probability of grey i is histogram[i]/(pixel count)
    final double normalisation = 1.0 / total;
    final int greyLevels = data2.length;
    final float[] histogram = new float[greyLevels];

    for (int i = 0; i < greyLevels; ++i) {
      histogram[i] = (float) (data2[i] * normalisation);
    }

    if (offset != null && offset.length > 0) {
      offset[0] = minbin;
    }
    return histogram;
  }

  /**
   * Build the required lookup table for the {@link #findMaxSigma(int, float[][], int[])} method.
   *
   * @param histogram Image histogram (length N)
   * @return The lookup table
   */
  public float[][] buildLookupTables(float[] histogram) {
    return buildLookupTables(histogram, null, null);
  }

  /**
   * Build the required lookup table for the {@link #findMaxSigma(int, float[][], int[])} method.
   *
   * <p>Working space can be provided to save reallocating memory. If null or less than
   * histogram.length they will be reallocated. The data within the working space are destroyed and
   * the lookup table is returned. This reuses the working space if possible.
   *
   * @param histogram Image histogram (length N)
   * @param w1 working space (NxN)
   * @param w2 working space (NxN)
   * @return The lookup table
   */
  public float[][] buildLookupTables(float[] histogram, float[][] w1, float[][] w2) {
    final int greyLevels = histogram.length;
    // Error if not enough memory
    try {
      w1 = initialise(w1, greyLevels);
      w2 = initialise(w2, greyLevels);
    } catch (final OutOfMemoryError ex) {
      IJ.log(TITLE + ": Out-of-memory - Try again with a smaller histogram (e.g. 8-bit image)");
      throw ex;
    }

    // w1 = P in original
    // w2 = S in original

    // w1 = sum of histogram counts
    // w2 = sum of histogram values (count * value)

    // diagonal
    for (int i = 0; i < greyLevels; ++i) {
      w1[i][i] = histogram[i];
      w2[i][i] = i * histogram[i];
    }
    // calculate first row (row 0 is all zero)
    for (int i = 1; i < greyLevels - 1; ++i) {
      w1[1][i + 1] = w1[1][i] + histogram[i + 1];
      w2[1][i + 1] = w2[1][i] + (i + 1) * histogram[i + 1];
    }
    // using row 1 to calculate others
    for (int i = 2; i < greyLevels; i++) {
      for (int j = i + 1; j < greyLevels; j++) {
        w1[i][j] = w1[1][j] - w1[1][i - 1];
        w2[i][j] = w2[1][j] - w2[1][i - 1];
      }
    }
    // now calculate H[i][j] reusing the space
    final float[][] table = w2;
    for (int i = 1; i < greyLevels; ++i) {
      for (int j = i + 1; j < greyLevels; j++) {
        if (w1[i][j] != 0) {
          table[i][j] = (w2[i][j] * w2[i][j]) / w1[i][j];
        } else {
          table[i][j] = 0;
        }
      }
    }

    return table;
  }

  private static float[][] initialise(float[][] data, int greyLevels) {
    // This does not check the second dimension are all the correct length!
    if (data == null || data.length < greyLevels) {
      data = new float[greyLevels][greyLevels];
    } else {
      // Initialise to zero
      for (int j = 0; j < greyLevels; j++) {
        for (int i = 0; i < greyLevels; ++i) {
          data[i][j] = 0;
        }
      }
    }
    return data;
  }

  /**
   * Find the threshold that maximises the between class variance.
   *
   * @param mlevel The number of thresholds
   * @param lookupTable The lookup table produced from the image histogram
   * @param thresholds The thresholds (output)
   * @return The max between class significance
   */
  public float findMaxSigma(int mlevel, float[][] lookupTable, int[] thresholds) {
    final int greyLevels = lookupTable[0].length;
    return findMaxSigma(mlevel, lookupTable, thresholds, greyLevels);
  }

  /**
   * Find the threshold that maximises the between class variance.
   *
   * @param mlevel The number of thresholds
   * @param lookupTable The lookup table produced from the image histogram
   * @param thresholds The thresholds (output)
   * @param greyLevels The number of histogram levels
   * @return The max between class significance
   */
  public float findMaxSigma(int mlevel, float[][] lookupTable, int[] thresholds, int greyLevels) {
    for (int i = 0; i < thresholds.length; i++) {
      thresholds[i] = 0;
    }
    float maxSig = -1;

    // In case of equality use the average for the threshold
    int count = 0;

    switch (mlevel) {
      case 2:
        for (int i = 1; i < greyLevels - mlevel; i++) {
          // t1
          final float Sq = lookupTable[1][i] + lookupTable[i + 1][greyLevels - 1];
          if (maxSig < Sq) {
            thresholds[1] = i;
            maxSig = Sq;
            count = 1;
          } else if (maxSig == Sq) {
            thresholds[1] += i;
            count++;
          }
        }
        break;
      case 3:
        for (int i = 1; i < greyLevels - mlevel; i++) {
          // t1
          for (int j = i + 1; j < greyLevels - mlevel + 1; j++) {
            // t2
            final float Sq =
                lookupTable[1][i] + lookupTable[i + 1][j] + lookupTable[j + 1][greyLevels - 1];
            if (maxSig < Sq) {
              thresholds[1] = i;
              thresholds[2] = j;
              maxSig = Sq;
              count = 1;
            } else if (maxSig == Sq) {
              thresholds[1] += i;
              thresholds[2] += j;
              count++;
            }
          }
        }
        break;
      case 4:
        for (int i = 1; i < greyLevels - mlevel; i++) {
          // t1
          for (int j = i + 1; j < greyLevels - mlevel + 1; j++) {
            // t2
            for (int k = j + 1; k < greyLevels - mlevel + 2; k++) {
              // t3
              final float Sq = lookupTable[1][i] + lookupTable[i + 1][j] + lookupTable[j + 1][k]
                  + lookupTable[k + 1][greyLevels - 1];
              if (maxSig < Sq) {
                thresholds[1] = i;
                thresholds[2] = j;
                thresholds[3] = k;
                maxSig = Sq;
                count = 1;
              } else if (maxSig == Sq) {
                thresholds[1] += i;
                thresholds[2] += j;
                thresholds[3] += k;
                count++;
              }
            }
          }
        }
        break;
      case 5:
        for (int i = 1; i < greyLevels - mlevel; i++) {
          // t1
          for (int j = i + 1; j < greyLevels - mlevel + 1; j++) {
            // t2
            for (int k = j + 1; k < greyLevels - mlevel + 2; k++) {
              // t3
              for (int m = k + 1; m < greyLevels - mlevel + 3; m++) {
                // t4
                final float Sq = lookupTable[1][i] + lookupTable[i + 1][j] + lookupTable[j + 1][k]
                    + lookupTable[k + 1][m] + lookupTable[m + 1][greyLevels - 1];
                if (maxSig < Sq) {
                  thresholds[1] = i;
                  thresholds[2] = j;
                  thresholds[3] = k;
                  thresholds[4] = m;
                  maxSig = Sq;
                  count = 1;
                } else if (maxSig == Sq) {
                  thresholds[1] += i;
                  thresholds[2] += j;
                  thresholds[3] += k;
                  thresholds[4] += m;
                  count++;
                }
              }
            }
          }
        }
        break;
      default:
        throw new IllegalArgumentException("Unsupported level: " + mlevel);
    }

    if (count > 1) {
      if (settings.logMessages) {
        IJ.log("Multiple optimal thresholds");
      }
      for (int i = 0; i < thresholds.length; i++) {
        thresholds[i] /= count;
      }
    }

    return (maxSig > 0) ? maxSig : 0;
  }

  /**
   * Add back the histogram offset to produce the correct thresholds.
   *
   * @param threshold output from {@link #findMaxSigma(int, float[][], int[])}
   * @param offset output from {@link #buildHistogram(ImagePlus, int[])}
   */
  private static void applyOffset(int[] threshold, int[] offset) {
    for (int i = 0; i < threshold.length; ++i) {
      threshold[i] += offset[0];
    }
  }

  private static void showThresholds(int levels, float[] maxSig, int[] threshold) {
    final StringBuilder sb = new StringBuilder();
    sb.append("Otsu thresholds: ");
    for (int i = 0; i < levels; ++i) {
      sb.append(i).append("=").append(threshold[i]).append(", ");
    }
    sb.append(" maxSig = ").append(maxSig[0]);
    IJ.log(sb.toString());
  }

  private static void showHistogram(float[] histogram, int[] thresholds, int minbin, String title) {
    final int greyLevels = histogram.length;

    // X-axis values
    final float[] bin = new float[greyLevels];

    // Calculate the maximum
    float hmax = 0;
    int mode = 0;
    for (int i = 0; i < greyLevels; ++i) {
      bin[i] = i + minbin;
      if (hmax < histogram[i]) {
        hmax = histogram[i];
        mode = i;
      }
    }

    // Clip histogram to exclude the mode count as per the ij.gui.HistogramWindow.
    // For example this removes a large peak at zero in masked images.
    float hmax2 = 0; // Second highest point
    for (int i = 0; i < histogram.length; i++) {
      if (hmax2 < histogram[i] && i != mode) {
        hmax2 = histogram[i];
      }
    }
    if ((hmax > (hmax2 * 2)) && (hmax2 != 0)) {
      // Set histogram limit to 50% higher than the second largest value
      hmax = hmax2 * 1.5f;
    }

    if (title == null) {
      title = "Histogram";
    }
    final Plot histogramPlot = new Plot(title, "Intensity", "p(Intensity)");
    histogramPlot.setLimits(minbin, (double) minbin + greyLevels, 0, hmax);
    histogramPlot.addPoints(bin, histogram, Plot.LINE);
    histogramPlot.draw();

    // Add lines for each threshold
    histogramPlot.setLineWidth(1);
    histogramPlot.setColor(Color.red);
    for (int i = 1; i < thresholds.length; i++) {
      final double x = thresholds[i];
      histogramPlot.drawLine(x, 0, x, hmax);
    }

    histogramPlot.show();
  }

  /**
   * Show new images using only pixels within the bounds of the given thresholds.
   *
   * @param mlevel The number of thresholds
   * @param thresholds The thresholds
   * @param imp The image
   */
  public void showRegions(int mlevel, int[] thresholds, ImagePlus imp) {
    final int width = imp.getWidth();
    final int height = imp.getHeight();
    final int bitDepth = imp.getBitDepth();

    final double max = imp.getDisplayRangeMax();
    final double min = imp.getDisplayRangeMin();

    final ImageStack stack = imp.getImageStack();
    final int slices = stack.getSize();
    final ImagePlus[] region = new ImagePlus[mlevel];
    final ImageStack[] rstack = new ImageStack[mlevel];
    final ImageProcessor[] rip = new ImageProcessor[mlevel];
    for (int i = 0; i < mlevel; ++i) {
      region[i] = NewImage.createImage(imp.getTitle() + " Region " + i, width, height, slices,
          bitDepth, NewImage.FILL_BLACK);
      rstack[i] = region[i].getImageStack();
    }

    final int[] newT = new int[mlevel + 1];
    System.arraycopy(thresholds, 0, newT, 0, mlevel);
    newT[mlevel] = Integer.MAX_VALUE;
    thresholds = newT;

    for (int slice = 1; slice <= slices; slice++) {
      final ImageProcessor ip = stack.getProcessor(slice);
      for (int i = 0; i < mlevel; ++i) {
        rip[i] = rstack[i].getProcessor(slice);
      }
      for (int i = 0; i < ip.getPixelCount(); ++i) {
        final int val = ip.get(i);
        int index = 0;
        while (val > thresholds[index + 1]) {
          index++;
        }
        rip[index].set(i, val);
      }
    }
    for (int i = 0; i < mlevel; i++) {
      region[i].setDisplayRange(min, max);
      region[i].show();
    }
  }

  /**
   * Show new mask images using only pixels within the bounds of the given thresholds.
   *
   * @param mlevel The number of thresholds
   * @param thresholds The thresholds
   * @param imp The image
   */
  public void showMasks(int mlevel, int[] thresholds, ImagePlus imp) {
    final int width = imp.getWidth();
    final int height = imp.getHeight();

    final ImageStack stack = imp.getImageStack();
    final int slices = stack.getSize();
    final ImagePlus[] region = new ImagePlus[mlevel];
    final ImageStack[] rstack = new ImageStack[mlevel];
    final ImageProcessor[] rip = new ImageProcessor[mlevel];
    for (int i = 0; i < mlevel; ++i) {
      region[i] = NewImage.createImage(imp.getTitle() + " Mask " + i, width, height, slices, 8,
          NewImage.FILL_BLACK);
      rstack[i] = region[i].getImageStack();
    }
    final int[] newT = new int[mlevel + 1];
    System.arraycopy(thresholds, 0, newT, 0, mlevel);
    newT[mlevel] = Integer.MAX_VALUE;
    thresholds = newT;
    for (int slice = 1; slice <= slices; slice++) {
      final ImageProcessor ip = stack.getProcessor(slice);
      for (int i = 0; i < mlevel; ++i) {
        rip[i] = rstack[i].getProcessor(slice);
      }
      for (int i = 0; i < ip.getPixelCount(); ++i) {
        final int val = ip.get(i);
        int index = 0;
        while (val > thresholds[index + 1]) {
          index++;
        }
        rip[index].set(i, 255);
      }
    }
    for (int i = 0; i < mlevel; i++) {
      region[i].setDisplayRange(0, 255);
      region[i].show();
    }
  }
}
