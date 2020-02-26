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

package uk.ac.sussex.gdsc.foci;

import gnu.trove.list.array.TIntArrayList;
import gnu.trove.set.hash.TIntHashSet;
import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.gui.Roi;
import ij.plugin.filter.GaussianBlur;
import ij.process.ImageProcessor;
import java.awt.Rectangle;
import java.awt.geom.RoundRectangle2D;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.function.Supplier;
import java.util.logging.Logger;
import uk.ac.sussex.gdsc.core.annotation.Nullable;
import uk.ac.sussex.gdsc.core.ij.ImageJUtils;
import uk.ac.sussex.gdsc.core.threshold.FloatHistogram;
import uk.ac.sussex.gdsc.core.threshold.Histogram;
import uk.ac.sussex.gdsc.core.utils.MathUtils;
import uk.ac.sussex.gdsc.core.utils.TextUtils;
import uk.ac.sussex.gdsc.foci.FindFociProcessorOptions.AlgorithmOption;
import uk.ac.sussex.gdsc.foci.FindFociProcessorOptions.BackgroundMethod;
import uk.ac.sussex.gdsc.foci.FindFociProcessorOptions.CentreMethod;
import uk.ac.sussex.gdsc.foci.FindFociProcessorOptions.MaskMethod;
import uk.ac.sussex.gdsc.foci.FindFociProcessorOptions.PeakMethod;
import uk.ac.sussex.gdsc.foci.FindFociProcessorOptions.SearchMethod;
import uk.ac.sussex.gdsc.foci.FindFociProcessorOptions.SortMethod;
import uk.ac.sussex.gdsc.foci.FindFociProcessorOptions.StatisticsMethod;
import uk.ac.sussex.gdsc.foci.FindFociProcessorOptions.ThresholdMethod;
import uk.ac.sussex.gdsc.utils.GaussianFit_PlugIn;

/**
 * Find the peak intensity regions of an image.
 *
 * <p>All local maxima above threshold are identified. For all other pixels the direction to the
 * highest neighbour pixel is stored (steepest gradient). In order of highest local maxima, regions
 * are only grown down the steepest gradient to a lower pixel. Provides many configuration options
 * for regions growing thresholds.
 *
 * <p>This plugin was based on {@link ij.plugin.filter.MaximumFinder}. Options have been changed to
 * only support greyscale 2D images and 3D stacks and to perform region growing using configurable
 * thresholds. Support for Watershed, Previewing, and Euclidian Distance Map (EDM) have been
 * removed.
 *
 * <p>Stopping criteria for region growing routines are partly based on the options in PRIISM
 * (http://www.msg.ucsf.edu/IVE/index.html).
 */
public abstract class FindFociBaseProcessor implements FindFociStagedProcessor {
  /**
   * The largest number that can be displayed in a 16-bit image.
   *
   * <p>Note searching for maxima uses 32-bit integers but ImageJ can only display 16-bit images.
   */
  private static final int MAXIMA_CAPCITY = 65535;

  /**
   * The default search capacity. This is {@link Integer#MAX_VALUE}. The algorithm will be limited
   * by the amount of memory available.
   */
  private static final int DEFAULT_SEARCH_CAPACITY = Integer.MAX_VALUE;

  /** The new line string from System.lineSeparator(). */
  private static final String NEW_LINE = System.lineSeparator();

  // The following arrays are built for a 3D search through the following z-order: (0,-1,1)
  // Each 2D plane is built for a search round a pixel in an anti-clockwise direction.
  // Note the x==y==z==0 element is not present. Thus there are blocks of 8,9,9 for each plane.
  // This preserves the isWithin() functionality of ij.plugin.filter.MaximumFinder.

  //@formatter:off
  /** The x-coordinate direction offsets for a 3D 26-connected search. */
  private static final int[] DIR_X_OFFSET = {
       0, 1, 1, 1, 0,-1,-1,-1, 0, 1, 1, 1, 0,-1,-1,-1, 0, 0, 1, 1, 1, 0,-1,-1,-1, 0 };
  /** The y-coordinate direction offsets for a 3D 26-connected search. */
  private static final int[] DIR_Y_OFFSET = {
      -1,-1, 0, 1, 1, 1, 0,-1,-1,-1, 0, 1, 1, 1, 0,-1, 0,-1,-1, 0, 1, 1, 1, 0,-1, 0 };
  /** The z-coordinate direction offsets for a 3D 26-connected search. */
  private static final int[] DIR_Z_OFFSET = {
       0, 0, 0, 0, 0, 0, 0, 0,-1,-1,-1,-1,-1,-1,-1,-1,-1, 1, 1, 1, 1, 1, 1, 1, 1, 1 };
  // Half-neighbours
  /** The x-coordinate direction offsets for half the neighbours of a 3D 26-connected search. */
  private static final int[] DIR_X_OFFSET2 = { 0, 1, 1, 1, 0, 1, 1, 1, 0,-1,-1,-1, 0 };
  /** The y-coordinate direction offsets for half the neighbours of a 3D 26-connected search. */
  private static final int[] DIR_Y_OFFSET2 = {-1,-1, 0, 1,-1,-1, 0, 1, 1, 1, 0,-1, 0 };
  /** The z-coordinate direction offsets for half the neighbours of a 3D 26-connected search. */
  private static final int[] DIR_Z_OFFSET2 = { 0, 0, 0, 0,-1,-1,-1,-1,-1,-1,-1,-1,-1 };
  //@formatter:on

  // The following constants are used to set bits corresponding to pixel types

  /** marks points outside the ROI. */
  protected static final byte EXCLUDED = (byte) 1;
  /** marks local maxima (irrespective of noise tolerance). */
  protected static final byte MAXIMUM = (byte) 2;
  /** marks points currently in the list. */
  protected static final byte LISTED = (byte) 4;
  /** marks areas near a maximum, within the tolerance. */
  protected static final byte MAX_AREA = (byte) 8;
  /** marks a potential saddle between maxima. */
  protected static final byte SADDLE = (byte) 16;
  /** marks a saddle between maxima. */
  protected static final byte SADDLE_POINT = (byte) 32;
  /** marks a point within a maxima next to a saddle. */
  protected static final byte SADDLE_WITHIN = (byte) 64;
  /** marks a point as a plateau region. */
  protected static final byte PLATEAU = (byte) 128;
  /** marks a point as not a maximum. */
  protected static final byte NOT_MAXIMUM = (byte) 32;
  /** marks a point to use in the saddle search. */
  protected static final byte SADDLE_SEARCH = (byte) 32;
  /** marks a point as falling below the highest saddle point. */
  protected static final byte BELOW_SADDLE = (byte) 128;
  /** The max in the x dimension. */
  protected int maxx;

  /** The max in the y dimension. */
  protected int maxy;
  /** The max in the z dimension. */
  protected int maxz;
  /** The limit in the x dimension (max-1). */
  protected int xlimit;
  /** The limit in the y dimension (max-1). */
  protected int ylimit;
  /** The limit in the z dimension (max-1). */
  protected int zlimit;
  /**
   * {@link #maxx} * {@link #maxy}.
   */
  protected int maxxByMaxy;
  /**
   * {@link #maxx} * {@link #maxy} * {@link #maxz}.
   */
  protected int maxxByMaxyByMaxz;
  /**
   * The index offset table using for 26-connected search. Computed using the image dimensions for
   * the 3D image packed as a single array.
   */
  protected int[] offset;
  /**
   * The index offset table using for half of a 26-connected search. Computed using the image
   * dimensions for the 3D image packed as a single array.
   */
  protected int[] offset2;

  /** Array storing a flag for each offset if the total shift is 1, e.g. 1,0,0 or 0,1,0. */
  protected boolean[] flatEdge;
  private Rectangle bounds;

  private boolean showStatus = true;
  private Logger logger;

  /** marks point to be ignored in stage 1. */
  protected static final byte IGNORE = EXCLUDED | LISTED;
  /**
   * The value to use for no saddle (usually zero or -Inf when the background is zero).
   */
  protected float noSaddleValue;

  /** The timestamp for the start of a section of the algorithm. */
  private long sectionTimestamp;
  /** The timestamp for the start of the algorithm. */
  private long startTimestamp;

  private float[] highestSaddleValues;

  /**
   * The search capacity.
   *
   * <p>This is the maximum number of potential maxima to support (i.e. the upper limit on the ID
   * for a candidate maxima).
   */
  protected final int searchCapacity;

  /**
   * Instantiates a new find foci base processor.
   */
  public FindFociBaseProcessor() {
    this(DEFAULT_SEARCH_CAPACITY);
  }

  /**
   * Instantiates a new find foci base processor.
   *
   * @param searchCapacity The search capacity. This is the maximum number of potential maxima to
   *        support (i.e. the upper limit on the ID for a candidate maxima).
   */
  public FindFociBaseProcessor(int searchCapacity) {
    this.searchCapacity = searchCapacity;
  }

  @Override
  public FindFociResults findMaxima(ImagePlus imp, ImagePlus mask,
      FindFociProcessorOptions processorOptions) {
    final boolean isLogging = isLogging();

    if (isLogging) {
      log(() -> "---" + NEW_LINE + FindFoci_PlugIn.TITLE + " : " + imp.getTitle());
    }

    // Call first to set up the processing for isWithin
    initialise(imp);
    IJ.resetEscape();
    final long start = System.currentTimeMillis();
    timingStart();

    showStatus("Initialising memory...");

    // Support int[] or float[] image
    final Object originalImage = extractImage(imp);
    // Will be a notepad for pixel types
    final byte[] types = createTypesArray(originalImage);
    // Contains the maxima Id assigned for each point
    final int[] maxima = new int[maxxByMaxyByMaxz];
    final FindFociStatistics stats = new FindFociStatistics();
    stats.imageMinimum = getImageMin(originalImage, types);

    final Object image;
    if (processorOptions.getGaussianBlur() > 0) {
      // Apply a Gaussian pre-processing step
      image = extractImage(applyBlur(imp, processorOptions.getGaussianBlur()));
    } else {
      // The images are the same so just copy the reference
      image = originalImage;
    }

    showStatus("Initialising ROI...");

    // Mark any point outside the ROI as processed
    int exclusion = excludeOutsideRoi(imp, types, isLogging);
    exclusion += excludeOutsideMask(mask, types, isLogging);

    // The histogram is used to process the levels in the assignpointsToMaxima() routine.
    // So only use those that have not been excluded.
    showStatus("Building histogram...");

    final Histogram histogram =
        buildHistogram(imp.getBitDepth(), image, types, StatisticsMethod.INSIDE);
    getStatistics(histogram, stats);

    Histogram statsHistogram = histogram;

    // Allow the threshold to be set using pixels outside the mask/ROI, or both inside and outside.
    if (exclusion > 0 && processorOptions.getStatisticsMethod() != StatisticsMethod.INSIDE) {
      if (processorOptions.getStatisticsMethod() == StatisticsMethod.ALL) {
        // Both inside and outside
        statsHistogram = buildHistogram(imp.getBitDepth(), image);
      } else {
        statsHistogram = buildHistogram(imp.getBitDepth(), image, types, StatisticsMethod.OUTSIDE);
      }
      getBackgroundStatistics(statsHistogram, stats);
    }

    if (isLogging) {
      recordStatistics(stats, exclusion, processorOptions);
    }

    if (ImageJUtils.isInterrupted()) {
      return null;
    }

    if (isLogging) {
      timingSplit("Initialised");
    }

    // Calculate the auto-threshold if necessary
    if (processorOptions.getBackgroundMethod() == BackgroundMethod.AUTO_THRESHOLD) {
      stats.background = getThreshold(processorOptions.getThresholdMethod(), statsHistogram);
    }

    showStatus("Getting sorted maxima...");
    stats.background = getSearchThreshold(processorOptions.getBackgroundMethod(),
        processorOptions.getBackgroundParameter(), stats);
    if (isLogging) {
      log(() -> "Background level = " + getFormat(stats.background));
    }
    final Coordinate[] maxpoints =
        getSortedMaxpoints(image, maxima, types, stats.regionMinimum, stats.background);

    if (ImageJUtils.isInterrupted() || maxpoints == null) {
      return null;
    }

    if (isLogging) {
      log(() -> "Number of potential maxima = " + maxpoints.length);
    }
    showStatus("Analyzing maxima...");

    final FindFociResult[] initialResultsArray = assignMaxima(maxima, maxpoints, stats);

    assignpointsToMaxima(image, histogram, types, stats, maxima);

    if (ImageJUtils.isInterrupted()) {
      return null;
    }

    if (isLogging) {
      timingSplit("Assigned maxima");
    }

    // Remove points below the peak growth criteria
    pruneMaxima(image, types, processorOptions.getSearchMethod(),
        processorOptions.getSearchParameter(), stats, initialResultsArray, maxima);

    // Calculate the initial results (peak size and intensity)
    calculateInitialResults(image, maxima, initialResultsArray);

    if (isLogging) {
      timingSplit("Calculated initial results");
    }

    showStatus("Finding saddle points...");

    // Calculate the highest saddle point for each peak
    final FindFociSaddleList[] saddlePoints =
        findSaddlePoints(image, types, initialResultsArray, maxima);

    if (ImageJUtils.isInterrupted()) {
      return null;
    }

    // Find the peak sizes above their saddle points.
    analysePeaks(initialResultsArray, image, maxima, types);

    if (isLogging) {
      timingSplit("Mapped saddle points");
    }

    showStatus("Merging peaks...");

    // Combine maxima below the minimum peak criteria to adjacent peaks (or eliminate if no
    // neighbours)
    final int originalNumberOfPeaks = initialResultsArray.length;
    final boolean restrictAboveSaddle =
        processorOptions.isOption(AlgorithmOption.MINIMUM_ABOVE_SADDLE);
    final boolean nonContiguous =
        !processorOptions.isOption(AlgorithmOption.CONTIGUOUS_ABOVE_SADDLE);
    FindFociResult[] resultsArray =
        mergeSubPeaks(initialResultsArray, image, maxima, types, processorOptions.getMinSize(),
            processorOptions.getPeakMethod(), processorOptions.getPeakParameter(), stats,
            saddlePoints, isLogging, restrictAboveSaddle, nonContiguous);

    if (resultsArray == null || ImageJUtils.isInterrupted()) {
      return null;
    }

    if (processorOptions.isOption(AlgorithmOption.REMOVE_EDGE_MAXIMA)) {
      resultsArray = removeEdgeMaxima(resultsArray, maxima);
    }

    final int totalPeaks = resultsArray.length;

    CentreMethod centreMethod = processorOptions.getCentreMethod();
    if (processorOptions.getGaussianBlur() > 0) {
      // Recalculate the totals but do not update the saddle values
      // (since basing these on the blur image should give smoother saddle results).
      calculateNativeResults(originalImage, maxima, resultsArray, originalNumberOfPeaks);
    } else if (centreMethod == CentreMethod.MAX_VALUE_ORIGINAL) {
      // If no blur was applied, then the centre using the original image will be the same as
      // using the search
      centreMethod = CentreMethod.MAX_VALUE_SEARCH;
    }

    // Calculate the peaks centre and maximum value.
    locateMaxima(originalImage, image, maxima, types, resultsArray, centreMethod,
        processorOptions.getCentreParameter());

    // Calculate the average intensity and values minus background
    calculateFinalResults(resultsArray, stats.background, stats.backgroundRegionMinimum);

    // Reorder the results
    sortDescResults(resultsArray, processorOptions.getSortMethod(), stats);

    // Only return the best results
    resultsArray = trim(resultsArray, processorOptions.getMaxPeaks());

    final int nMaxima = resultsArray.length;
    if (isLogging) {
      log(() -> {
        final StringBuilder message = new StringBuilder("Final number of peaks = ").append(nMaxima);
        if (nMaxima < totalPeaks) {
          message.append(" / ").append(totalPeaks);
        }
        return message.toString();
      });
    }

    // Compute this only when we know we have some results (to avoid wasted CPU)
    if (nMaxima != 0) {
      getIntensityAboveBackgrounds(originalImage, types, stats);
    }

    if (isLogging) {
      timingSplit("Calculated results");
    }

    // Build the output mask
    ImagePlus outImp = null;
    if (processorOptions.getMaskMethod() != MaskMethod.NONE) {
      showStatus("Generating mask image...");

      outImp = generateOutputMask(processorOptions, imp, image, types, maxima, stats, resultsArray,
          nMaxima);

      if (outImp == null) {
        IJ.error(FindFoci_PlugIn.TITLE, "Too many maxima to display in a 16-bit image: " + nMaxima);
      }

      if (isLogging) {
        timingSplit("Calculated output mask");
      }
    }

    renumberPeaks(resultsArray, originalNumberOfPeaks);

    IJ.showTime(imp, start, "Done ", maxz);

    return new FindFociResults(outImp, resultsArray, stats);
  }

  private static float getThreshold(ThresholdMethod thresholdMethod, Histogram histogram) {
    if (histogram instanceof FloatHistogram) {
      // Convert to a smaller histogram
      // Use the limit for 16-bit integer data. This ensure compatibility
      // between the Integer and Float processor, i.e. if a 16-bit image
      // is converted to a float image it will achieve the same results.
      histogram = histogram.compact(65536);
    }
    final int[] statsHistogram = histogram.histogramCounts;
    final int t = thresholdMethod.getThreshold(statsHistogram);
    // Convert back to an image value
    return histogram.getValue(t);
  }

  private void timingStart() {
    sectionTimestamp = startTimestamp = System.nanoTime();
  }

  private void timingSplit(String sectionName) {
    final long newTimestamp = System.nanoTime();
    log(() -> String.format("%s : %s : %s", sectionName,
        TextUtils.nanosToString(newTimestamp - sectionTimestamp),
        TextUtils.nanosToString(newTimestamp - startTimestamp)));
    sectionTimestamp = newTimestamp;
  }

  /**
   * Extract the image into a linear array stacked in zyx order.
   *
   * @param imp the image
   * @return the image object
   */
  protected abstract Object extractImage(ImagePlus imp);

  /**
   * Create a byte array used to flag pixels during processing.
   *
   * @param pixels the pixels
   * @return the byte array
   */
  protected abstract byte[] createTypesArray(Object pixels);

  /**
   * Gets the image min.
   *
   * @param pixels the pixels
   * @param types the types
   * @return the image min
   */
  protected abstract float getImageMin(Object pixels, byte[] types);

  /**
   * Apply a Gaussian blur to the image and returns a new image. Returns the original image if
   * {@code blur <= 0}.
   *
   * <p>Only blurs the current channel and frame for use in the FindFoci algorithm.
   *
   * @param imp the image
   * @param blur The blur standard deviation
   * @return the blurred image
   */
  public static ImagePlus applyBlur(ImagePlus imp, double blur) {
    if (blur > 0) {
      // Note: imp.duplicate() crops the image if there is an ROI selection
      // so duplicate each ImageProcessor instead.
      final GaussianBlur gb = new GaussianBlur();
      final ImageStack stack = imp.getImageStack();
      final ImageStack newStack =
          new ImageStack(stack.getWidth(), stack.getHeight(), stack.getSize());
      final int channel = imp.getChannel();
      final int frame = imp.getFrame();
      final int[] dim = imp.getDimensions();
      // Copy the entire stack
      for (int slice = 1; slice <= stack.getSize(); slice++) {
        newStack.setPixels(stack.getProcessor(slice).getPixels(), slice);
      }
      // Now blur the current channel and frame
      for (int slice = 1; slice <= dim[3]; slice++) {
        final int stackIndex = imp.getStackIndex(channel, slice, frame);
        final ImageProcessor ip = stack.getProcessor(stackIndex).duplicate();
        gb.blurGaussian(ip, blur, blur, 0.0002);
        newStack.setPixels(ip.getPixels(), stackIndex);
      }
      imp = new ImagePlus(null, newStack);
      imp.setDimensions(dim[2], dim[3], dim[4]);
      imp.setC(channel);
      imp.setT(frame);
    }
    return imp;
  }

  /** {@inheritDoc} */
  @Override
  public ImagePlus blur(ImagePlus imp, double blur) {
    return applyBlur(imp, blur);
  }

  @Override
  public FindFociInitResults findMaximaInit(ImagePlus originalImp, ImagePlus imp, ImagePlus mask,
      FindFociProcessorOptions processorOptions) {
    // Call first to set up the processing for isWithin
    initialise(imp);

    final Object originalImage = extractImage(originalImp);
    // Will be a notepad for pixel types
    final byte[] types = createTypesArray(originalImage);
    // Contains the maxima Id assigned for each point
    final int[] maxima = new int[maxxByMaxyByMaxz];
    final FindFociStatistics stats = new FindFociStatistics();
    stats.imageMinimum = getImageMin(originalImage, types);

    final Object image = extractImage(imp);

    // Mark any point outside the ROI as processed
    int exclusion = excludeOutsideRoi(originalImp, types, false);
    exclusion += excludeOutsideMask(mask, types, false);

    final Histogram histogram =
        buildHistogram(imp.getBitDepth(), image, types, StatisticsMethod.INSIDE);
    getStatistics(histogram, stats);

    Histogram statsHistogram = histogram;

    // Allow the threshold to be set using pixels outside the mask/ROI, or both inside and outside.
    if (exclusion > 0 && processorOptions.getStatisticsMethod() != StatisticsMethod.INSIDE) {
      if (processorOptions.getStatisticsMethod() == StatisticsMethod.ALL) {
        // Both inside and outside
        statsHistogram = buildHistogram(imp.getBitDepth(), image);
      } else {
        statsHistogram = buildHistogram(imp.getBitDepth(), image, types, StatisticsMethod.OUTSIDE);
      }
      getBackgroundStatistics(statsHistogram, stats);
    }

    // Calculate the auto-threshold if necessary
    if (processorOptions.getBackgroundMethod() == BackgroundMethod.AUTO_THRESHOLD) {
      stats.background = getThreshold(processorOptions.getThresholdMethod(), statsHistogram);
    }

    // Do this here since we now have the background and image min.
    // This saves having to do it repeated later during multiple calls with the same init state.
    getIntensityAboveBackgrounds(originalImage, types, stats);

    return new FindFociInitResults(image, types, maxima, histogram, stats, originalImage,
        originalImp);
  }

  /** {@inheritDoc} */
  @Override
  public FindFociInitResults copyForStagedProcessing(FindFociInitResults initResults,
      @Nullable FindFociInitResults clonedInitResults) {
    final Object image = initResults.image;
    final byte[] types = initResults.types;
    final int[] maxima = initResults.maxima;
    final Histogram histogram = initResults.histogram;
    final FindFociStatistics stats = initResults.stats;
    final Object originalImage = initResults.originalImage;
    final ImagePlus originalImp = initResults.originalImp;

    byte[] types2 = null;
    int[] maxima2 = null;

    // These are not destructively modified
    final Histogram histogram2 = histogram;
    final FindFociStatistics stats2 = stats;

    if (clonedInitResults == null) {
      types2 = new byte[types.length];
      maxima2 = new int[maxima.length];
    } else {
      // Re-use arrays
      types2 = clonedInitResults.types;
      maxima2 = clonedInitResults.maxima;
    }

    // Copy the arrays that are destructively modified
    System.arraycopy(types, 0, types2, 0, types.length);
    System.arraycopy(maxima, 0, maxima2, 0, maxima.length);

    // Note: Image is unchanged so this is not copied

    if (clonedInitResults == null) {
      return new FindFociInitResults(image, types2, maxima2, histogram2, stats2, originalImage,
          originalImp);
    }

    clonedInitResults.image = image;
    clonedInitResults.types = types2;
    clonedInitResults.maxima = maxima2;
    clonedInitResults.histogram = histogram2;
    clonedInitResults.stats = stats2;
    clonedInitResults.originalImage = originalImage;
    clonedInitResults.originalImp = originalImp;

    return clonedInitResults;
  }

  @Override
  public FindFociSearchResults findMaximaSearch(FindFociInitResults initResults,
      FindFociProcessorOptions processorOptions) {
    final Object image = initResults.image;
    final byte[] types = initResults.types; // Will be a notepad for pixel types
    final int[] maxima = initResults.maxima; // Contains the maxima Id assigned for each point
    final Histogram histogram = initResults.histogram;
    final FindFociStatistics stats = initResults.stats;

    setPixels(image);
    stats.background = getSearchThreshold(processorOptions.getBackgroundMethod(),
        processorOptions.getBackgroundParameter(), stats);
    final Coordinate[] maxpoints =
        getSortedMaxpoints(image, maxima, types, stats.regionMinimum, stats.background);
    if (maxpoints == null) {
      return null;
    }
    final FindFociResult[] resultsArray = assignMaxima(maxima, maxpoints, stats);

    assignpointsToMaxima(image, histogram, types, stats, maxima);

    // Remove points below the peak growth criteria
    pruneMaxima(image, types, processorOptions.getSearchMethod(),
        processorOptions.getSearchParameter(), stats, resultsArray, maxima);

    // Calculate the initial results (peak size and intensity)
    calculateInitialResults(image, maxima, resultsArray);

    // Calculate the highest saddle point for each peak
    final FindFociSaddleList[] saddlePoints = findSaddlePoints(image, types, resultsArray, maxima);

    // Find the peak sizes above their saddle points.
    analysePeaks(resultsArray, image, maxima, types);

    return new FindFociSearchResults(resultsArray, saddlePoints);
  }

  @Override
  public FindFociMergeTempResults findMaximaMergePeak(FindFociInitResults initResults,
      FindFociSearchResults searchResults, FindFociProcessorOptions processorOptions) {
    final FindFociResult[] resultsArray = copyFindFociResults(searchResults.resultsArray);
    final FindFociSaddleList[] saddlePoints = copyFindFociSaddleLists(searchResults.saddlePoints);

    // Combine maxima below the minimum peak criteria to adjacent peaks (or eliminate if no
    // neighbours)
    return mergeUsingHeight(resultsArray, initResults.image, initResults.maxima,
        processorOptions.getPeakMethod(), processorOptions.getPeakParameter(), initResults.stats,
        saddlePoints);
  }

  private static FindFociSaddleList[]
      copyFindFociSaddleLists(final FindFociSaddleList[] originalSaddlePoints) {
    final FindFociSaddleList[] saddlePoints = new FindFociSaddleList[originalSaddlePoints.length];
    // Ignore first position
    for (int i = 1; i < originalSaddlePoints.length; i++) {
      saddlePoints[i] = originalSaddlePoints[i].copy();
    }
    return saddlePoints;
  }

  private static FindFociResult[] copyFindFociResults(final FindFociResult[] originalResultsArray) {
    final FindFociResult[] resultsArray = new FindFociResult[originalResultsArray.length];
    // Ignore first position
    for (int i = 0; i < originalResultsArray.length; i++) {
      resultsArray[i] = originalResultsArray[i].copy();
    }
    return resultsArray;
  }

  @Override
  public FindFociMergeTempResults findMaximaMergeSize(FindFociInitResults initResults,
      FindFociMergeTempResults mergeResults, FindFociProcessorOptions processorOptions) {
    // Clone input destructively modified
    final FindFociResult[] resultsArray = copyFindFociResults(mergeResults.resultsArray);
    final FindFociSaddleList[] saddlePoints = copyFindFociSaddleLists(mergeResults.saddlePoints);
    final int[] peakIdMap = mergeResults.peakIdMap.clone();
    final FindFociResult[] resultList = updateResultList(resultsArray);

    mergeResults = new FindFociMergeTempResults(resultsArray, saddlePoints, peakIdMap, resultList);
    if (processorOptions.getMinSize() > 1) {
      mergeUsingSize(mergeResults, initResults.maxima, processorOptions.getMinSize());
    }
    return mergeResults;
  }

  /**
   * The results list is a sorted reference to the all the results in the results array. When that
   * is cloned the resultsList has to be updated with new refeneces.
   *
   * @param resultsArray the results array
   * @return The new result list
   */
  private static FindFociResult[] updateResultList(FindFociResult[] resultsArray) {
    final FindFociResult[] list = new FindFociResult[resultsArray.length + 1];
    for (int i = 0; i < resultsArray.length; i++) {
      list[resultsArray[i].id] = resultsArray[i];
    }
    return list;
  }

  @Override
  public FindFociMergeResults findMaximaMergeFinal(FindFociInitResults initResults,
      FindFociMergeTempResults mergeResults, FindFociProcessorOptions processorOptions) {
    final int[] maxima = initResults.maxima; // Contains the maxima Id assigned for each point
    final Object originalImage = initResults.originalImage;

    final boolean restrictAboveSaddle =
        processorOptions.isOption(AlgorithmOption.MINIMUM_ABOVE_SADDLE);

    FindFociResult[] resultsArray = copyFindFociResults(mergeResults.resultsArray);
    int[] peakIdMap = mergeResults.peakIdMap;

    if (processorOptions.getMinSize() > 1 && restrictAboveSaddle) {
      final boolean nonContiguous =
          !processorOptions.isOption(AlgorithmOption.CONTIGUOUS_ABOVE_SADDLE);

      // Clone input destructively modified
      final FindFociSaddleList[] saddlePoints = copyFindFociSaddleLists(mergeResults.saddlePoints);
      peakIdMap = peakIdMap.clone();
      final FindFociResult[] resultList = updateResultList(resultsArray);

      mergeResults =
          new FindFociMergeTempResults(resultsArray, saddlePoints, peakIdMap, resultList);
      mergeAboveSaddle(mergeResults, initResults.image, maxima, initResults.types,
          processorOptions.getMinSize(), nonContiguous);
    }

    resultsArray = copyFindFociResults(mergeResults.resultsArray);

    final int originalNumberOfPeaks = resultsArray.length;

    resultsArray = mergeFinal(resultsArray, peakIdMap, maxima);

    if (processorOptions.isOption(AlgorithmOption.REMOVE_EDGE_MAXIMA)) {
      resultsArray = removeEdgeMaxima(resultsArray, maxima);
    }

    if (processorOptions.getGaussianBlur() > 0) {
      // Recalculate the totals using the original image
      calculateNativeResults(originalImage, maxima, resultsArray, originalNumberOfPeaks);
    }

    return new FindFociMergeResults(resultsArray, originalNumberOfPeaks);
  }

  @Override
  public FindFociResults findMaximaResults(FindFociInitResults initResults,
      FindFociMergeResults mergeResults, FindFociProcessorOptions processorOptions) {
    final Object searchImage = initResults.image;
    final byte[] types = initResults.types;
    final int[] maxima = initResults.maxima;
    final FindFociStatistics stats = initResults.stats;
    final Object originalImage = initResults.originalImage;

    final FindFociResult[] originalResultsArray = mergeResults.resultsArray;
    final int originalNumberOfPeaks = mergeResults.originalNumberOfPeaks;

    FindFociResult[] resultsArray = copyFindFociResults(originalResultsArray);

    // If no blur was applied, then the centre using the original image will be the same as using
    // the search
    CentreMethod centreMethod = processorOptions.getCentreMethod();
    if (processorOptions.getGaussianBlur() <= 0
        && centreMethod == CentreMethod.MAX_VALUE_ORIGINAL) {
      // If no blur was applied, then the centre using the original image will be the same as
      // using the search
      centreMethod = CentreMethod.MAX_VALUE_SEARCH;
    }

    // Calculate the peaks centre and maximum value.
    locateMaxima(originalImage, searchImage, maxima, types, resultsArray, centreMethod,
        processorOptions.getCentreParameter());

    // Calculate the average intensity and values minus background
    calculateFinalResults(resultsArray, stats.background, stats.backgroundRegionMinimum);

    // Reorder the results
    sortDescResults(resultsArray, processorOptions.getSortMethod(), stats);

    // Only return the best results
    resultsArray = trim(resultsArray, processorOptions.getMaxPeaks());

    renumberPeaks(resultsArray, originalNumberOfPeaks);

    return new FindFociResults(null, resultsArray, stats);
  }

  @Override
  public FindFociPrelimResults findMaximaPrelimResults(FindFociInitResults initResults,
      FindFociMergeResults mergeResults, FindFociProcessorOptions processorOptions) {
    final Object searchImage = initResults.image;
    final byte[] types = initResults.types;
    final int[] maxima = initResults.maxima;
    final FindFociStatistics stats = initResults.stats;
    final Object originalImage = initResults.originalImage;

    final FindFociResult[] originalResultsArray = mergeResults.resultsArray;

    FindFociResult[] resultsArray = copyFindFociResults(originalResultsArray);

    // If no blur was applied, then the centre using the original image will be the same as using
    // the search
    CentreMethod centreMethod = processorOptions.getCentreMethod();
    if (processorOptions.getGaussianBlur() <= 0
        && centreMethod == CentreMethod.MAX_VALUE_ORIGINAL) {
      // If no blur was applied, then the centre using the original image will be the same as
      // using the search
      centreMethod = CentreMethod.MAX_VALUE_SEARCH;
    }

    // Calculate the peaks centre and maximum value.
    locateMaxima(originalImage, searchImage, maxima, types, resultsArray, centreMethod,
        processorOptions.getCentreParameter());

    // Calculate the average intensity and values minus background
    calculateFinalResults(resultsArray, stats.background, stats.regionMinimum);

    // Reorder the results
    sortDescResults(resultsArray, processorOptions.getSortMethod(), stats);

    // Only return the best results
    resultsArray = trim(resultsArray, processorOptions.getMaxPeaks());

    return new FindFociPrelimResults(null, resultsArray, stats);
  }

  @Override
  public FindFociResults findMaximaMaskResults(FindFociInitResults initResults,
      FindFociMergeResults mergeResults, FindFociPrelimResults prelimResults,
      FindFociProcessorOptions processorOptions) {
    final Object image = initResults.image;
    final byte[] types = initResults.types;
    final int[] maxima = initResults.maxima;
    final FindFociStatistics stats = initResults.stats;

    final FindFociResult[] originalResultsArray = prelimResults.results;
    final int originalNumberOfPeaks = mergeResults.originalNumberOfPeaks;

    final FindFociResult[] resultsArray = copyFindFociResults(originalResultsArray);

    final int nMaxima = resultsArray.length;

    // Build the output mask
    ImagePlus outImp = null;
    if (processorOptions.getMaskMethod() != MaskMethod.NONE) {
      final ImagePlus imp = initResults.originalImp;
      outImp = generateOutputMask(processorOptions, imp, image, types, maxima, stats, resultsArray,
          nMaxima);
    }

    renumberPeaks(resultsArray, originalNumberOfPeaks);

    return new FindFociResults(outImp, resultsArray, stats);
  }

  private ImagePlus generateOutputMask(FindFociProcessorOptions processorOptions, ImagePlus imp,
      Object pixels, byte[] types, int[] maxima, FindFociStatistics stats,
      FindFociResult[] resultsArray, int numberOfMaxima) {
    final String imageTitle = imp.getTitle();
    // Rebuild the mask: all maxima have value 1, the remaining peak area are numbered sequentially
    // starting with value 2.
    // First create byte values to use in the mask for each maxima
    final int[] maximaValues = new int[numberOfMaxima];
    final int[] maximaPeakIds = new int[numberOfMaxima];
    final float[] displayValues = new float[numberOfMaxima];

    final MaskMethod maskMethod = processorOptions.getMaskMethod();
    final boolean maskAboveSaddle = EnumSet
        .of(MaskMethod.PEAKS_ABOVE_SADDLE, MaskMethod.THRESHOLD_ABOVE_SADDLE).contains(maskMethod);

    double fractionParameter = processorOptions.getFractionParameter();
    boolean resetBelowSaddle;
    if (maskAboveSaddle || EnumSet
        .of(MaskMethod.FRACTION_OF_INTENSITY, MaskMethod.FRACTION_OF_HEIGHT).contains(maskMethod)) {
      if (maskMethod == MaskMethod.FRACTION_OF_HEIGHT) {
        fractionParameter = MathUtils.clip(0, 1, 1 - processorOptions.getFractionParameter());
      }

      // Reset unneeded flags in the types array since new flags are required to mark pixels below
      // the cut-off height.
      final byte resetFlag = (byte) (SADDLE_POINT | MAX_AREA);
      for (int i = types.length; i-- > 0;) {
        types[i] &= resetFlag;
      }
      resetBelowSaddle = true;
    } else {
      // Ensure no pixels are below the saddle height
      Arrays.fill(displayValues, stats.regionMinimum - 1);
      resetBelowSaddle = false;
    }

    setPixels(pixels);
    final boolean floatImage = pixels instanceof float[];

    for (int i = 0; i < numberOfMaxima; i++) {
      maximaValues[i] = numberOfMaxima - i;
      final FindFociResult result = resultsArray[i];
      maximaPeakIds[i] = result.id;
      if (maskAboveSaddle) {
        displayValues[i] = result.highestSaddleValue;
      } else if (maskMethod == MaskMethod.FRACTION_OF_HEIGHT) {
        displayValues[i] =
            (float) (fractionParameter * (result.maxValue - stats.background) + stats.background);
        if (!floatImage) {
          displayValues[i] = round(displayValues[i]);
        }
      }
    }

    if (maskMethod == MaskMethod.FRACTION_OF_INTENSITY) {
      calculateFractionOfIntensityDisplayValues(fractionParameter, pixels, maxima, stats,
          maximaPeakIds, displayValues);
    }

    // Now assign the output mask
    for (int index = maxima.length; index-- > 0;) {
      if ((types[index] & MAX_AREA) != 0) {
        // Find the maxima in the list of maxima Ids.
        int maximaIndex = 0;
        while (maximaIndex < numberOfMaxima && maximaPeakIds[maximaIndex] != maxima[index]) {
          maximaIndex++;
        }
        if (maximaIndex < numberOfMaxima) {
          if ((getf(index) <= displayValues[maximaIndex])) {
            types[index] |= BELOW_SADDLE;
          }
          maxima[index] = maximaValues[maximaIndex];
          continue;
        }
      }

      // Fall through condition, reset the value
      maxima[index] = 0;
      types[index] = 0;
    }

    int maxValue = numberOfMaxima;

    if (EnumSet.of(MaskMethod.THRESHOLD, MaskMethod.THRESHOLD_ABOVE_SADDLE).contains(maskMethod)) {
      // Perform thresholding on the peak regions
      findBorders(maxima, types);
      for (int i = 0; i < numberOfMaxima; i++) {
        thresholdMask(pixels, maxima, maximaValues[i], processorOptions.getThresholdMethod(),
            stats);
      }
      invertMask(maxima);
      addBorders(maxima, types);

      // Adjust the values used to create the output mask
      maxValue = 3;
    }

    // Blank any pixels below the saddle height
    if (resetBelowSaddle) {
      for (int i = maxima.length; i-- > 0;) {
        if ((types[i] & BELOW_SADDLE) != 0) {
          maxima[i] = 0;
        }
      }
    }

    // Set maxima to a high value
    if (processorOptions.isOption(AlgorithmOption.OUTPUT_MASK_PEAK_DOTS)) {
      maxValue++;
      for (int i = 0; i < numberOfMaxima; i++) {
        final FindFociResult result = resultsArray[i];
        maxima[getIndex(result.x, result.y, result.z)] = maxValue;
      }
    }

    // Check the maxima can be displayed
    if (maxValue > MAXIMA_CAPCITY) {
      log(() -> "The number of maxima exceeds the 16-bit capacity used for diplay: "
          + MAXIMA_CAPCITY);
      return null;
    }

    // Output the mask
    // The index is '(maxx_maxy) * z + maxx * y + x' so we can simply iterate over the array if we
    // use z, y, x order
    final ImageStack stack = new ImageStack(maxx, maxy, maxz);
    if (maxValue > 255) {
      for (int z = 0, index = 0; z < maxz; z++) {
        final short[] pixels2 = new short[maxxByMaxy];
        for (int i = 0; i < maxxByMaxy; i++, index++) {
          pixels2[i] = (short) maxima[index];
        }
        stack.setPixels(pixels2, z + 1);
      }
    } else {
      for (int z = 0, index = 0; z < maxz; z++) {
        final byte[] pixels2 = new byte[maxxByMaxy];
        for (int i = 0; i < maxxByMaxy; i++, index++) {
          pixels2[i] = (byte) maxima[index];
        }
        stack.setPixels(pixels2, z + 1);
      }
    }

    final ImagePlus result = new ImagePlus(imageTitle + " " + FindFoci_PlugIn.TITLE, stack);
    result.setCalibration(imp.getCalibration());
    return result;
  }

  private void calculateFractionOfIntensityDisplayValues(double fractionParameter, Object pixels,
      int[] maxima, FindFociStatistics stats, int[] maximaPeakIds, float[] displayValues) {
    // For each maxima
    for (int i = 0; i < maximaPeakIds.length; i++) {
      // Histogram all the pixels above background
      final Histogram hist = buildHistogram(pixels, maxima, maximaPeakIds[i], stats.regionMaximum);

      if (hist instanceof FloatHistogram) {
        final float background = stats.background;

        // Sum above background
        double sum = 0;
        for (int bin = hist.minBin; bin <= hist.maxBin; bin++) {
          sum += hist.histogramCounts[bin] * (hist.getValue(bin) - background);
        }

        // Determine the cut-off using fraction of cumulative intensity
        final double total = sum * fractionParameter;

        // Find the point in the histogram that exceeds the fraction
        sum = 0;
        int bin = hist.maxBin;
        while (bin >= hist.minBin) {
          sum += hist.histogramCounts[bin] * (hist.getValue(bin) - background);
          if (sum > total) {
            break;
          }
          bin--;
        }

        displayValues[i] = hist.getValue(bin);
      } else {
        final int background = (int) Math.floor(stats.background);

        // Sum above background
        long sum = 0;
        for (int bin = hist.minBin; bin <= hist.maxBin; bin++) {
          sum += hist.histogramCounts[bin] * (bin - background);
        }

        // Determine the cut-off using fraction of cumulative intensity
        final long total = (long) (sum * fractionParameter);

        // Find the point in the histogram that exceeds the fraction
        sum = 0;
        int bin = hist.maxBin;
        while (bin >= hist.minBin) {
          sum += hist.histogramCounts[bin] * (bin - background);
          if (sum > total) {
            break;
          }
          bin--;
        }

        displayValues[i] = bin;
      }
    }
  }

  /**
   * Update the peak Ids to use the sorted order.
   *
   * @param resultsArray the results array
   * @param originalNumberOfPeaks the original number of peaks
   */
  private static void renumberPeaks(FindFociResult[] resultsArray, int originalNumberOfPeaks) {
    // Build a map between the original peak number and the new sorted order
    final int[] peakIdMap = new int[originalNumberOfPeaks + 1];
    int index = 1;
    for (int i = 0; i < resultsArray.length; i++) {
      peakIdMap[resultsArray[i].id] = index++;
    }

    // Update the Ids
    for (int i = 0; i < resultsArray.length; i++) {
      final FindFociResult result = resultsArray[i];
      result.id = peakIdMap[result.id];
      result.saddleNeighbourId = peakIdMap[result.saddleNeighbourId];
    }
  }

  /**
   * Finds the borders of peak regions.
   *
   * @param maxima the maxima
   * @param types the types
   */
  private void findBorders(int[] maxima, byte[] types) {
    // TODO - This is not perfect. There is a problem with regions marked as saddles
    // between 3 or more peaks. This can results in large blocks of saddle regions that
    // are eroded from the outside in. In this case they should be eroded from the inside out.
    // .......Peaks..Correct..Wrong
    // .......1.22....+.+......+.+
    // ........12......+........+
    // ........12......+........+
    // .......1332....+.+.......+
    // .......3332....+..+......+
    // .......3332....+..+......+
    // (Dots inserted to prevent auto-formatting removing spaces)
    // It also only works on the XY plane. However it is fine for an approximation of the peak
    // boundaries.

    final int[] xyz = new int[3];

    for (int index = maxima.length; index-- > 0;) {
      if (maxima[index] == 0) {
        types[index] = 0;
        continue;
      }

      // If a saddle, search around to check if it still a saddle
      if ((types[index] & SADDLE) != 0) {
        // reset unneeded flags
        types[index] &= BELOW_SADDLE;

        getXy(index, xyz);
        final int x = xyz[0];
        final int y = xyz[1];
        final boolean isInnerXy = (y != 0 && y != ylimit) && (x != 0 && x != xlimit);

        // Process the neighbours
        for (int d = 8; d-- > 0;) {
          if (isInnerXy || isWithinXy(x, y, d)) {
            final int index2 = index + offset[d];

            // Check if neighbour is a different peak
            if (maxima[index] != maxima[index2] && maxima[index2] > 0
                && (types[index2] & SADDLE_POINT) != SADDLE_POINT) {
              types[index] |= SADDLE_POINT;
            }
          }
        }
      }

      // If it is not a saddle point then mark it as within the saddle
      if ((types[index] & SADDLE_POINT) == 0) {
        types[index] |= SADDLE_WITHIN;
      }
    }

    for (int z = maxz; z-- > 0;) {
      cleanupExtraLines(types, z);
      cleanupExtraCornerPixels(types, z);
    }
  }

  /**
   * For each saddle pixel, check the 2 adjacent non-diagonal neighbour pixels in clockwise fashion.
   * If they are both saddle pixels then this pixel can be removed (since they form a diagonal
   * line).
   */
  private int cleanupExtraCornerPixels(byte[] types, int z) {
    int removed = 0;
    final int[] xyz = new int[3];

    for (int i = maxxByMaxy, index = maxxByMaxy * z; i-- > 0; index++) {
      if ((types[index] & SADDLE_POINT) != 0) {
        getXy(index, xyz);
        final int x = xyz[0];
        final int y = xyz[1];

        final boolean isInner = (y != 0 && y != ylimit) && (x != 0 && x != xlimit);

        final boolean[] edgesSet = new boolean[8];
        for (int d = 8; d-- > 0;) {
          // analyze 4 flat-edge neighbours
          if (isInner || isWithinXy(x, y, d)) {
            edgesSet[d] = ((types[index + offset[d]] & SADDLE_POINT) != 0);
          }
        }

        for (int d = 0; d < 8; d += 2) {
          if ((edgesSet[d] && edgesSet[(d + 2) % 8]) && !edgesSet[(d + 5) % 8]) {
            removed++;
            types[index] &= ~SADDLE_POINT;
            types[index] |= SADDLE_WITHIN;
          }
        }
      }
    }

    return removed;
  }

  /**
   * Delete saddle lines that do not divide two peak areas. Adapted from
   * {@link ij.plugin.filter.MaximumFinder}
   *
   * @param types the types
   * @param z the z
   */
  private void cleanupExtraLines(byte[] types, int z) {
    for (int i = maxxByMaxy, index = maxxByMaxy * z; i-- > 0; index++) {
      if ((types[index] & SADDLE_POINT) != 0) {
        final int nRadii = countRadii(types, index); // number of lines radiating
        if (nRadii == 0) {
          // single point or foreground patch?
          types[index] &= ~SADDLE_POINT;
          types[index] |= SADDLE_WITHIN;
        } else if (nRadii == 1) {
          removeLineFrom(types, index);
        }
      }
    }
  }

  /**
   * Delete a line starting at x, y up to the next (4-connected) vertex.
   *
   * @param types the types
   * @param index the index
   */
  private void removeLineFrom(byte[] types, int index) {
    types[index] &= ~SADDLE_POINT;
    types[index] |= SADDLE_WITHIN;
    final int[] xyz = new int[3];
    boolean continues;
    do {
      getXy(index, xyz);
      final int x = xyz[0];
      final int y = xyz[1];

      continues = false;
      final boolean isInner = (y != 0 && y != maxy - 1) && (x != 0 && x != maxx - 1);
      for (int d = 0; d < 8; d += 2) {
        if (isInner || isWithinXy(x, y, d)) {
          final int index2 = index + offset[d];
          final byte v = types[index2];
          if ((v & SADDLE_WITHIN) != SADDLE_WITHIN && (v & SADDLE_POINT) == SADDLE_POINT) {
            final int radii = countRadii(types, index2);
            if (radii <= 1) { // found a point or line end
              index = index2;
              types[index] &= ~SADDLE_POINT;
              types[index] |= SADDLE_WITHIN; // delete the point
              continues = radii == 1; // continue along that line
              break;
            }
          }
        }
      }
    } while (continues);
  }

  /**
   * Analyze the neighbors of a pixel (x, y) in a byte image; pixels <255 ("non-white") are
   * considered foreground. Edge pixels are considered foreground.
   *
   * @param types The byte image
   * @param index coordinate of the point
   * @return Number of 4-connected lines emanating from this point. Zero if the point is embedded in
   *         either foreground or background
   */
  private int countRadii(byte[] types, int index) {
    int countTransitions = 0;
    boolean prevPixelSet = true;
    boolean firstPixelSet = true; // initialize to make the compiler happy
    final int[] xyz = new int[3];
    getXy(index, xyz);
    final int x = xyz[0];
    final int y = xyz[1];

    final boolean isInner = (y != 0 && y != maxy - 1) && (x != 0 && x != maxx - 1);
    for (int d = 0; d < 8; d++) { // walk around the point and note every no-line->line transition
      boolean pixelSet = prevPixelSet;
      if (isInner || isWithinXy(x, y, d)) {
        final boolean isSet = ((types[index + offset[d]] & SADDLE_WITHIN) == SADDLE_WITHIN);
        if ((d & 1) == 0) {
          pixelSet = isSet; // non-diagonal directions: always regarded
        } else if (!isSet) {
          pixelSet = false; // but are insufficient for a 4-connected line
        }
      } else {
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
   * For each peak in the maxima image, perform thresholding using the specified method.
   *
   * @param pixels the pixels
   * @param maxima the maxima
   * @param peakValue the peak value
   * @param thresholdMethod the threshold method
   * @param stats the stats
   */
  private void thresholdMask(Object pixels, int[] maxima, int peakValue,
      ThresholdMethod thresholdMethod, FindFociStatistics stats) {
    final Histogram histogram = buildHistogram(pixels, maxima, peakValue, stats.regionMaximum);
    final float t = getThreshold(thresholdMethod, histogram);

    if (pixels instanceof float[]) {
      final float[] image = (float[]) pixels;
      for (int i = maxima.length; i-- > 0;) {
        if (maxima[i] == peakValue) {
          // Use negative to allow use of image in place
          maxima[i] = ((image[i] > t) ? -3 : -2);
        }
      }
    } else {
      final int[] image = (int[]) pixels;
      final int threshold = (int) t;
      for (int i = maxima.length; i-- > 0;) {
        if (maxima[i] == peakValue) {
          // Use negative to allow use of image in place
          maxima[i] = ((image[i] > threshold) ? -3 : -2);
        }
      }
    }
  }

  /**
   * Changes all negative value to positive.
   *
   * @param maxima the maxima
   */
  private static void invertMask(int[] maxima) {
    for (int i = maxima.length; i-- > 0;) {
      if (maxima[i] < 0) {
        maxima[i] = -maxima[i];
      }
    }
  }

  /**
   * Adds the borders to the peaks.
   *
   * @param maxima the maxima
   * @param types the types
   */
  private static void addBorders(int[] maxima, byte[] types) {
    for (int i = maxima.length; i-- > 0;) {
      if ((types[i] & SADDLE_POINT) != 0) {
        maxima[i] = 1;
      }
    }
  }

  /**
   * Records the image statistics to the log window.
   *
   * @param stats The statistics
   * @param exclusion non-zero if pixels have been excluded
   * @param processorOptions the processor options
   */
  private void recordStatistics(FindFociStatistics stats, int exclusion,
      FindFociProcessorOptions processorOptions) {
    final boolean floatImage = isFloatProcessor();

    final StringBuilder sb = new StringBuilder();
    sb.append("Image min = ").append(FindFoci_PlugIn.getFormat(stats.imageMinimum, floatImage));
    if (exclusion > 0) {
      sb.append("\nImage stats (inside mask/ROI) : Min = ");
    } else {
      sb.append("\nImage stats : Min = ");
    }
    sb.append(FindFoci_PlugIn.getFormat(stats.regionMinimum, floatImage));
    sb.append(", Max = ").append(FindFoci_PlugIn.getFormat(stats.regionMaximum, floatImage));
    sb.append(", Mean = ").append(FindFoci_PlugIn.getFormat(stats.regionAverage));
    sb.append(", StdDev = ").append(FindFoci_PlugIn.getFormat(stats.regionStdDev));
    log(sb::toString);

    sb.setLength(0);
    if (exclusion > 0) {
      sb.append("Background stats (mode=").append(processorOptions.getStatisticsMethod())
          .append(") : Min = ");
    } else {
      sb.append("Background stats : Min = ");
    }
    sb.append(FindFoci_PlugIn.getFormat(stats.backgroundRegionMinimum, floatImage));
    sb.append(", Max = ")
        .append(FindFoci_PlugIn.getFormat(stats.backgroundRegionMaximum, floatImage));
    sb.append(", Mean = ").append(FindFoci_PlugIn.getFormat(stats.backgroundRegionAverage));
    sb.append(", StdDev = ").append(FindFoci_PlugIn.getFormat(stats.backgroundRegionStdDev));
    if (stats.imageMinimum < 0
        && isSortMethodSensitiveToNegativeValues(processorOptions.getSortMethod())) {
      sb.append("\nWARNING: Image minimum is below zero and the chosen sort index is sensitive to "
          + "negative values: ").append(processorOptions.getSortMethod());
    }
    log(sb::toString);
  }

  private String getFormat(double value) {
    return FindFoci_PlugIn.getFormat(value, isFloatProcessor());
  }

  /**
   * Checks if is sort method sensitive to negative values.
   *
   * @param sortIndex the sort method
   * @return true, if is sort method sensitive to negative values
   */
  public static boolean isSortMethodSensitiveToNegativeValues(SortMethod sortIndex) {
    switch (sortIndex) {
      case INTENSITY:
      case INTENSITY_MINUS_BACKGROUND:
      case AVERAGE_INTENSITY:
      case AVERAGE_INTENSITY_MINUS_BACKGROUND:
      case INTENSITY_ABOVE_SADDLE:
        return true;

      case COUNT:
      case MAX_VALUE:
      case X:
      case Y:
      case Z:
      case SADDLE_HEIGHT:
      case COUNT_ABOVE_SADDLE:
      case ABSOLUTE_HEIGHT:
      case RELATIVE_HEIGHT_ABOVE_BACKGROUND:
      case PEAK_ID:
      case XYZ:
      case INTENSITY_MINUS_MIN:
      case AVERAGE_INTENSITY_MINUS_MIN:
      default:
        return false;
    }
  }

  /**
   * Gets the absolute height above the highest saddle or the floor, whichever is higher.
   *
   * @param result the result
   * @param floor the floor
   * @return the absolute height
   */
  double getAbsoluteHeight(FindFociResult result, double floor) {
    double absoluteHeight = 0;
    if (result.highestSaddleValue > floor) {
      absoluteHeight = result.maxValue - result.highestSaddleValue;
    } else {
      absoluteHeight = result.maxValue - floor;
    }
    return absoluteHeight;
  }

  /**
   * Gets the relative height.
   *
   * @param result the result
   * @param floor the floor
   * @param absoluteHeight the absolute height
   * @return the relative height
   */
  static double getRelativeHeight(FindFociResult result, double floor, double absoluteHeight) {
    return absoluteHeight / (result.maxValue - floor);
  }

  /**
   * Set all pixels outside the ROI to EXCLUDED.
   *
   * @param imp The input image
   * @param types The types array used within the peak finding routine (same size as imp)
   * @param isLogging True if logging
   * @return 1 if masking was performed, else 0
   */
  private int excludeOutsideRoi(ImagePlus imp, byte[] types, boolean isLogging) {
    final Roi roi = imp.getRoi();

    if (roi != null && roi.isArea()) {
      final Rectangle roiBounds = roi.getBounds();

      // Check if this ROI covers the entire image
      if (roi.getType() == Roi.RECTANGLE && roiBounds.width == maxx && roiBounds.height == maxy) {
        return 0;
      }

      // Store the bounds of the ROI for the edge object analysis
      bounds = roiBounds;

      ImageProcessor ipMask = null;
      RoundRectangle2D rr = null;

      // Use the ROI mask if present
      if (roi.getMask() != null) {
        ipMask = roi.getMask();
        if (isLogging) {
          log(() -> "ROI = Mask");
        }
      } else if (roi.getType() == Roi.FREEROI) {
        // Use a mask for an irregular ROI
        ipMask = imp.getMask();
        if (isLogging) {
          log(() -> "ROI = Freehand ROI");
        }
      } else if (roi.getRoundRectArcSize() != 0) {
        // Use a round rectangle if necessary
        rr = new RoundRectangle2D.Float(roiBounds.x, roiBounds.y, roiBounds.width, roiBounds.height,
            roi.getRoundRectArcSize(), roi.getRoundRectArcSize());
        if (isLogging) {
          log(() -> "ROI = Round ROI");
        }
      }

      // Set everything as excluded
      for (int i = types.length; i-- > 0;) {
        types[i] = EXCLUDED;
      }

      // Now unset the ROI region

      // Create a mask from the ROI rectangle
      final int xOffset = roiBounds.x;
      final int yOffset = roiBounds.y;
      final int rwidth = roiBounds.width;
      final int rheight = roiBounds.height;

      for (int y = 0; y < rheight; y++) {
        for (int x = 0; x < rwidth; x++) {
          boolean mask = true;
          if (ipMask != null) {
            mask = (ipMask.get(x, y) > 0);
          } else if (rr != null) {
            mask = rr.contains(x + xOffset, y + yOffset);
          }

          if (mask) {
            // Set each z-slice as excluded
            for (int index = getIndex(x + xOffset, y + yOffset, 0); index < maxxByMaxyByMaxz;
                index += maxxByMaxy) {
              types[index] &= ~EXCLUDED;
            }
          }
        }
      }

      return 1;
    }
    return 0;
  }

  /**
   * Set all pixels outside the Mask to EXCLUDED.
   *
   * @param mask the mask
   * @param types The types array used within the peak finding routine
   * @param isLogging the is logging
   * @return 1 if masking was performed, else 0
   */
  private int excludeOutsideMask(ImagePlus mask, byte[] types, boolean isLogging) {
    if (mask == null) {
      return 0;
    }

    // Check sizes in X & Y
    if (mask.getWidth() != maxx || mask.getHeight() != maxy
        || (mask.getNSlices() != maxz && mask.getNSlices() != 1)) {
      if (isLogging) {
        log(() -> "Mask dimensions do not match the image");
      }
      return 0;
    }

    if (isLogging) {
      log(() -> "Mask image = " + mask.getTitle());
    }

    if (mask.getNSlices() == 1) {
      // If a single plane then duplicate through the image
      final ImageProcessor ipMask = mask.getProcessor();

      for (int i = maxxByMaxy; i-- > 0;) {
        if (ipMask.get(i) == 0) {
          for (int index = i; index < maxxByMaxyByMaxz; index += maxxByMaxy) {
            types[index] |= EXCLUDED;
          }
        }
      }
    } else {
      // If the same stack size then process through the image
      final ImageStack stack = mask.getStack();
      final int c = mask.getChannel();
      final int f = mask.getFrame();
      for (int slice = 1; slice <= mask.getNSlices(); slice++) {
        final int stackIndex = mask.getStackIndex(c, slice, f);
        final ImageProcessor ipMask = stack.getProcessor(stackIndex);

        int index = maxxByMaxy * slice;
        for (int i = maxxByMaxy; i-- > 0;) {
          index--;
          if (ipMask.get(i) == 0) {
            types[index] |= EXCLUDED;
          }
        }
      }
    }

    return 1;
  }

  /**
   * Build a histogram using only the specified peak area.
   *
   * @param pixels the pixels
   * @param maxima the maxima
   * @param peakValue the peak value
   * @param maxValue the max value
   * @return the histogram
   */
  protected abstract Histogram buildHistogram(Object pixels, int[] maxima, int peakValue,
      float maxValue);

  /**
   * Build a histogram using all pixels not marked as EXCLUDED.
   *
   * @param bitDepth the bit depth
   * @param pixels the image
   * @param types A byte image, same size as image, where the points can be marked as EXCLUDED
   * @param statisticsMethod the statistics method
   * @return The image histogram
   */
  protected abstract Histogram buildHistogram(int bitDepth, Object pixels, byte[] types,
      StatisticsMethod statisticsMethod);

  /**
   * Build a histogram using all pixels.
   *
   * @param bitDepth the bit depth
   * @param pixels The image
   * @return The image histogram
   */
  protected abstract Histogram buildHistogram(int bitDepth, Object pixels);

  private void getIntensityAboveBackgrounds(final Object originalImage, final byte[] types,
      final FindFociStatistics stats) {
    stats.totalAboveBackground = getIntensityAboveFloor(originalImage, types, stats.background);
    stats.totalAboveImageMinimum = getIntensityAboveFloor(originalImage, types, stats.imageMinimum);
  }

  /**
   * Gets the intensity above floor.
   *
   * @param pixels the pixels
   * @param types the types
   * @param floor the floor
   * @return the intensity above floor
   */
  protected double getIntensityAboveFloor(Object pixels, byte[] types, final float floor) {
    setPixels(pixels);

    double sum = 0;
    for (int i = types.length; i-- > 0;) {
      if ((types[i] & EXCLUDED) == 0) {
        final float v = getf(i);
        if (v > floor) {
          sum += (v - floor);
        }
      }
    }
    return sum;
  }

  /**
   * Return the image statistics.
   *
   * @param histogram The image histogram
   * @return Array containing: min, max, av, stdDev, sum
   */
  private static double[] getStatistics(Histogram histogram) {
    // Check for an empty histogram
    if (histogram.minBin == histogram.maxBin && histogram.histogramCounts[histogram.maxBin] == 0) {
      return new double[5];
    }

    // Get the average
    double value;
    double sum = 0.0;
    double sum2 = 0.0;
    long total = 0;
    for (int i = histogram.minBin; i <= histogram.maxBin; i++) {
      if (histogram.histogramCounts[i] != 0) {
        final int count = histogram.histogramCounts[i];
        total += count;
        value = histogram.getValue(i);
        sum += value * count;
        sum2 += (value * value) * count;
      }
    }

    // Get the Std.Dev
    double stdDev;
    double av;
    if (total > 0) {
      av = sum / total;
      final double d = total;
      stdDev = (d * sum2 - sum * sum) / d;
      if (stdDev > 0.0) {
        stdDev = Math.sqrt(stdDev / (d - 1.0));
      } else {
        stdDev = 0.0;
      }
    } else {
      av = 0;
      stdDev = 0.0;
    }

    final double min = histogram.getValue(histogram.minBin);
    final double max = histogram.getValue(histogram.maxBin);
    return new double[] {min, max, av, stdDev, sum};
  }

  /**
   * Compute the image statistics.
   *
   * @param histogram The image histogram
   * @param stats the stats
   */
  private static void getStatistics(Histogram histogram, FindFociStatistics stats) {
    final double[] newStats = getStatistics(histogram);
    stats.regionMinimum = stats.backgroundRegionMinimum = (float) newStats[0];
    stats.regionMaximum = stats.backgroundRegionMaximum = (float) newStats[1];
    stats.regionAverage = stats.backgroundRegionAverage = newStats[2];
    stats.regionStdDev = stats.backgroundRegionStdDev = newStats[3];
    stats.regionTotal = newStats[4];
  }

  /**
   * Compute the image statistics for the background.
   *
   * @param histogram The image histogram
   * @param stats the stats
   */
  private static void getBackgroundStatistics(Histogram histogram, FindFociStatistics stats) {
    final double[] newStats = getStatistics(histogram);
    stats.backgroundRegionMinimum = (float) newStats[0];
    stats.backgroundRegionMaximum = (float) newStats[1];
    stats.backgroundRegionAverage = newStats[2];
    stats.backgroundRegionStdDev = newStats[3];
  }

  /**
   * Get the threshold for searching for maxima.
   *
   * @param backgroundMethod The background thresholding method
   * @param backgroundParameter The method thresholding parameter
   * @param stats The image statistics
   * @return The threshold
   */
  protected abstract float getSearchThreshold(BackgroundMethod backgroundMethod,
      double backgroundParameter, FindFociStatistics stats);

  /**
   * Get the minimum height for this peak above the highest saddle point.
   *
   * @param peakMethod The method
   * @param peakParameter The method parameter
   * @param stats The image statistics
   * @param v0 The current maxima value
   * @return The minimum height
   */
  protected abstract double getPeakHeight(PeakMethod peakMethod, double peakParameter,
      FindFociStatistics stats, float v0);

  /**
   * Find all local maxima (irrespective whether they finally qualify as maxima or not).
   *
   * @param pixels The image to be analyzed
   * @param maxima the maxima
   * @param types A byte image, same size as ip, where the maximum points are marked as MAXIMUM
   * @param globalMin The image global minimum
   * @param threshold The threshold below which no pixels are processed.
   * @return Maxima sorted by value.
   */
  @Nullable
  protected Coordinate[] getSortedMaxpoints(Object pixels, int[] maxima, byte[] types,
      float globalMin, float threshold) {
    final ArrayList<Coordinate> maxpoints = new ArrayList<>(500);
    // working list for expanding local plateaus
    final TIntArrayList pointList = new TIntArrayList();

    int id = 0;
    final int[] xyz = new int[3];

    setPixels(pixels);
    if (is2D()) {
      for (int i = maxxByMaxyByMaxz; i-- > 0;) {
        if ((types[i] & (EXCLUDED | MAX_AREA | PLATEAU | NOT_MAXIMUM)) != 0) {
          continue;
        }
        final float v = getf(i);
        if (v < threshold || v == globalMin) {
          continue;
        }

        getXy(i, xyz);

        final int x = xyz[0];
        final int y = xyz[1];

        // Check whether we have a local maximum.
        final boolean isInnerXy = (y != 0 && y != ylimit) && (x != 0 && x != xlimit);
        boolean isMax = true;
        boolean equalNeighbour = false;

        // It is more likely that the z stack will be out-of-bounds.
        // Adopt the xy limit lookup and process z lookup separately

        for (int d = 8; d-- > 0;) {
          if (isInnerXy || isWithinXy(x, y, d)) {
            final float vNeighbor = getf(i + offset[d]);
            if (vNeighbor > v) {
              isMax = false;
              break;
            } else if (vNeighbor == v) {
              // Neighbour is equal, this is a potential plateau maximum
              equalNeighbour = true;
            } else {
              // This is lower so cannot be a maxima
              types[i + offset[d]] |= NOT_MAXIMUM;
            }
          }
        }

        if (isMax) {
          id++;
          if (id >= searchCapacity) {
            log(() -> "The number of potential maxima exceeds the search capacity: "
                + searchCapacity
                + ". Try using a denoising/smoothing filter or increase the capacity.");
            return null;
          }

          if (equalNeighbour) {
            // Search the local area marking all equal neighbour points as maximum
            if (!expandMaximum(maxima, types, globalMin, threshold, i, v, id, maxpoints,
                pointList)) {
              // Not a true maximum, ignore this
              id--;
            }
          } else {
            types[i] |= MAXIMUM | MAX_AREA;
            maxima[i] = id;
            maxpoints.add(new Coordinate(getIndex(x, y), id, v));
          }
        }
      }
    } else {
      for (int i = maxxByMaxyByMaxz; i-- > 0;) {
        if ((types[i] & (EXCLUDED | MAX_AREA | PLATEAU | NOT_MAXIMUM)) != 0) {
          continue;
        }
        final float v = getf(i);
        if (v < threshold || v == globalMin) {
          continue;
        }

        getXyz(i, xyz);

        final int x = xyz[0];
        final int y = xyz[1];
        final int z = xyz[2];

        // Check whether we have a local maximum.
        final boolean isInnerXy = (y != 0 && y != ylimit) && (x != 0 && x != xlimit);
        final boolean isInnerXyz = (zlimit == 0) ? isInnerXy : isInnerXy && (z != 0 && z != zlimit);
        boolean isMax = true;
        boolean equalNeighbour = false;

        // It is more likely that the z stack will be out-of-bounds.
        // Adopt the xy limit lookup and process z lookup separately

        for (int d = 26; d-- > 0;) {
          if (isInnerXyz || (isInnerXy && isWithinZ(z, d)) || isWithinXyz(x, y, z, d)) {
            final float vNeighbor = getf(i + offset[d]);
            if (vNeighbor > v) {
              isMax = false;
              break;
            } else if (vNeighbor == v) {
              // Neighbour is equal, this is a potential plateau maximum
              equalNeighbour = true;
            } else {
              // This is lower so cannot be a maxima
              types[i + offset[d]] |= NOT_MAXIMUM;
            }
          }
        }

        if (isMax) {
          id++;
          if (id >= searchCapacity) {
            log(() -> "The number of potential maxima exceeds the search capacity: "
                + searchCapacity
                + ". Try using a denoising/smoothing filter or increase the capacity.");
            return null;
          }

          if (equalNeighbour) {
            // Search the local area marking all equal neighbour points as maximum
            if (!expandMaximum(maxima, types, globalMin, threshold, i, v, id, maxpoints,
                pointList)) {
              // Not a true maximum, ignore this
              id--;
            }
          } else {
            types[i] |= MAXIMUM | MAX_AREA;
            maxima[i] = id;
            maxpoints.add(new Coordinate(getIndex(x, y, z), id, v));
          }
        }
      }
    }

    if (ImageJUtils.isInterrupted()) {
      return null;
    }

    for (int i = maxxByMaxyByMaxz; i-- > 0;) {
      types[i] &= ~NOT_MAXIMUM; // reset attributes no longer needed
    }

    Collections.sort(maxpoints, Coordinate::compare);

    // Build a map between the original id and the new id following the sort
    final int[] idMap = new int[maxpoints.size() + 1];

    // Label the points
    for (int i = 0; i < maxpoints.size(); i++) {
      final int newId = (i + 1);
      final Coordinate c = maxpoints.get(i);
      final int oldId = c.id;
      idMap[oldId] = newId;
      c.setId(newId);
    }

    reassignMaxima(maxima, idMap);

    return maxpoints.toArray(new Coordinate[maxpoints.size()]);
  }

  /**
   * Sets the pixels.
   *
   * @param pixels the new pixels
   */
  protected abstract void setPixels(Object pixels);

  /**
   * Gets the float value of the pixels.
   *
   * @param index the index
   * @return the float value
   */
  protected abstract float getf(int index);

  /**
   * Searches from the specified point to find all coordinates of the same value and determines the
   * centre of the plateau maximum.
   *
   * @param maxima the maxima
   * @param types the types
   * @param globalMin the global min
   * @param threshold the threshold
   * @param index0 the index0
   * @param v0 the v0
   * @param id the id
   * @param maxpoints the max points
   * @param pointList the list
   * @return True if this is a true plateau, false if the plateau reaches a higher point
   */
  protected boolean expandMaximum(int[] maxima, byte[] types, float globalMin, float threshold,
      int index0, float v0, int id, ArrayList<Coordinate> maxpoints, TIntArrayList pointList) {
    types[index0] |= LISTED | PLATEAU; // mark first point as listed
    int listI = 0; // index of current search element in the list

    // we create a list of connected points and start the list at the current maximum
    pointList.resetQuick();
    pointList.add(index0);

    // Calculate the center of plateau
    boolean isPlateau = true;
    final int[] xyz = new int[3];

    if (is2D()) {
      do {
        final int index1 = pointList.getQuick(listI);
        getXy(index1, xyz);
        final int x1 = xyz[0];
        final int y1 = xyz[1];

        // It is more likely that the z stack will be out-of-bounds.
        // Adopt the xy limit lookup and process z lookup separately

        final boolean isInnerXy = (y1 != 0 && y1 != ylimit) && (x1 != 0 && x1 != xlimit);

        for (int d = 8; d-- > 0;) {
          if (isInnerXy || isWithinXy(x1, y1, d)) {
            final int index2 = index1 + offset[d];
            if ((types[index2] & IGNORE) != 0) {
              // This has been done already, ignore this point
              continue;
            }

            final float v2 = getf(index2);

            if (v2 > v0) {
              isPlateau = false;
            } else if (v2 == v0) {
              // Add this to the search
              pointList.add(index2);
              types[index2] |= LISTED | PLATEAU;
            } else {
              types[index2] |= NOT_MAXIMUM;
            }
          }
        }

        listI++;

      } while (listI < pointList.size() && isPlateau);
    } else {
      do {
        final int index1 = pointList.getQuick(listI);
        getXyz(index1, xyz);
        final int x1 = xyz[0];
        final int y1 = xyz[1];
        final int z1 = xyz[2];

        // It is more likely that the z stack will be out-of-bounds.
        // Adopt the xy limit lookup and process z lookup separately

        final boolean isInnerXy = (y1 != 0 && y1 != ylimit) && (x1 != 0 && x1 != xlimit);
        final boolean isInnerXyz =
            (zlimit == 0) ? isInnerXy : isInnerXy && (z1 != 0 && z1 != zlimit);

        for (int d = 26; d-- > 0;) {
          if (isInnerXyz || (isInnerXy && isWithinZ(z1, d)) || isWithinXyz(x1, y1, z1, d)) {
            final int index2 = index1 + offset[d];
            if ((types[index2] & IGNORE) != 0) {
              // This has been done already, ignore this point
              continue;
            }

            final float v2 = getf(index2);

            if (v2 > v0) {
              isPlateau = false;
            } else if (v2 == v0) {
              // Add this to the search
              pointList.add(index2);
              types[index2] |= LISTED | PLATEAU;
            } else {
              types[index2] |= NOT_MAXIMUM;
            }
          }
        }

        listI++;

      } while (listI < pointList.size() && isPlateau);
    }

    // reset attributes no longer needed
    for (int i = pointList.size(); i-- > 0;) {
      final int index = pointList.getQuick(i);
      types[index] &= ~LISTED;
    }

    if (!isPlateau) {
      // This is not a maximum as a higher point was reached
      return false;
    }

    // Find the centre
    double cx = 0;
    double cy = 0;
    double cz = 0;
    for (int i = pointList.size(); i-- > 0;) {
      getXyz(pointList.getQuick(i), xyz);
      cx += xyz[0];
      cy += xyz[1];
      cz += xyz[2];
    }
    cx /= pointList.size();
    cy /= pointList.size();
    cz /= pointList.size();

    double maxDistance = Double.MAX_VALUE;
    int maxIndex = 0;

    // Calculate the maxima origin as the closest pixel to the centre-of-mass
    for (int i = pointList.size(); i-- > 0;) {
      final int index = pointList.getQuick(i);

      getXyz(index, xyz);
      final int x = xyz[0];
      final int y = xyz[1];
      final int z = xyz[2];

      final double distance = MathUtils.distance2(x, y, z, cx, cy, cz);

      if (distance < maxDistance) {
        maxDistance = distance;
        maxIndex = i;
      }

      types[index] |= MAX_AREA;
      maxima[index] = id;
    }

    // Assign the maximum
    final int index = pointList.getQuick(maxIndex);
    types[index] |= MAXIMUM;
    maxpoints.add(new Coordinate(index, id, v0));

    // This is a maximum
    return true;
  }

  private void setNoSaddleValue(FindFociStatistics stats) {
    if (stats.background >= 0) {
      noSaddleValue = 0;
    } else {
      noSaddleValue = Float.NEGATIVE_INFINITY;
    }
  }

  /**
   * Initialises the maxima image using the maxima Id for each point.
   *
   * @param maxima the maxima
   * @param maxpoints the max points
   * @param stats the stats
   * @return the find foci result[]
   */
  private FindFociResult[] assignMaxima(int[] maxima, Coordinate[] maxpoints,
      FindFociStatistics stats) {
    final int[] xyz = new int[3];
    setNoSaddleValue(stats);

    final FindFociResult[] resultsArray = new FindFociResult[maxpoints.length];

    for (int i = 0; i < maxpoints.length; i++) {
      final Coordinate maximum = maxpoints[i];
      getXyz(maximum.getIndex(), xyz);

      maxima[maximum.getIndex()] = maximum.id;

      final FindFociResult result = new FindFociResult();
      result.x = xyz[0];
      result.y = xyz[1];
      result.z = xyz[2];
      result.id = maximum.id;
      result.maxValue = maximum.value;
      result.totalIntensity = maximum.getValue();
      result.count = 1;
      result.highestSaddleValue = noSaddleValue;

      resultsArray[i] = result;
    }

    return resultsArray;
  }

  /**
   * Assigns points to their maxima using the steepest uphill gradient. Processes points in order of
   * height, progressively building peaks in a top-down fashion.
   *
   * @param pixels the pixels
   * @param hist the histogram
   * @param types the types
   * @param stats the statistics
   * @param maxima the maxima
   */
  protected void assignpointsToMaxima(Object pixels, Histogram hist, byte[] types,
      FindFociStatistics stats, int[] maxima) {
    setPixels(pixels);
    // This is modified so clone it
    final int[] histogram = hist.histogramCounts.clone();

    final int minBin = getBackgroundBin(hist, stats.background);
    final int maxBin = hist.maxBin;

    // Create an array with the coordinates of all points between the threshold value and the max-1
    // value (since maximum values should already have been processed)
    int arraySize = 0;
    for (int bin = minBin; bin < maxBin; bin++) {
      arraySize += histogram[bin];
    }

    if (arraySize == 0) {
      return;
    }

    // Store a single index for each pixel
    final int[] coordinates = new int[arraySize];
    int highestBin = 0;
    int count = 0;
    final int[] levelStart = new int[maxBin + 1];
    for (int bin = minBin; bin < maxBin; bin++) {
      levelStart[bin] = count;
      count += histogram[bin];
      if (histogram[bin] != 0) {
        highestBin = bin;
      }
    }
    final int[] levelOffset = new int[highestBin + 1];
    for (int i = types.length; i-- > 0;) {
      if ((types[i] & EXCLUDED) != 0) {
        continue;
      }

      final int v = getBin(hist, i);
      if (v >= minBin && v < maxBin) {
        count = levelStart[v] + levelOffset[v];
        coordinates[count] = i;
        levelOffset[v]++;
      }
    }

    // Process down through the levels
    int processedLevel = 0; // Counter incremented when work is done
    for (int level = highestBin; level >= minBin; level--) {
      int remaining = histogram[level];

      if (remaining == 0) {
        continue;
      }

      // Use the idle counter to ensure that we exit the loop if no pixels have been processed for
      // two cycles
      while (remaining > 0) {
        processedLevel++;
        final int n =
            processLevel(types, maxima, levelStart[level], remaining, coordinates, minBin);
        remaining -= n; // number of points processed

        // If nothing was done then stop
        if (n == 0) {
          break;
        }
      }

      if ((processedLevel % 64 == 0) && ImageJUtils.isInterrupted()) {
        return;
      }

      if (remaining > 0 && level > minBin) {
        // any pixels that we have not reached?
        // It could happen if there is a large area of flat pixels => no local maxima.
        // Add to the next level.

        int nextLevel = level; // find the next level to process
        do {
          nextLevel--;
        } while (nextLevel > 1 && histogram[nextLevel] == 0);

        // Add all unprocessed pixels of this level to the tasklist of the next level.
        // This could make it slow for some images, however.
        if (nextLevel > 0) {
          int newNextLevelEnd = levelStart[nextLevel] + histogram[nextLevel];
          for (int i = 0, p = levelStart[level]; i < remaining; i++, p++) {
            final int index = coordinates[p];
            coordinates[newNextLevelEnd++] = index;
          }
          // tasklist for the next level to process becomes longer by this:
          histogram[nextLevel] = newNextLevelEnd - levelStart[nextLevel];
        }
      }
    }
  }

  /**
   * Gets the background bin for the given background.
   *
   * @param histogram the histogram
   * @param background the background
   * @return the background bin
   */
  protected abstract int getBackgroundBin(Histogram histogram, float background);

  /**
   * Gets the bin for the given image position.
   *
   * @param histogram the histogram
   * @param index the index
   * @return the bin
   */
  protected abstract int getBin(Histogram histogram, int index);

  /**
   * Processes points in order of height, progressively building peaks in a top-down fashion.
   *
   * @param types The image pixel types
   * @param maxima The image maxima
   * @param levelStart offsets of the level in pixelPointers[]
   * @param levelNPoints number of points in the current level
   * @param coordinates list of xyz coordinates (should be offset by levelStart)
   * @param background The background intensity
   * @return number of pixels that have been changed
   */
  protected int processLevel(byte[] types, int[] maxima, int levelStart, int levelNPoints,
      int[] coordinates, int background) {
    int numberChanged = 0;
    int numberUnchanged = 0;
    final int[] xyz = new int[3];

    if (is2D()) {
      for (int i = 0, p = levelStart; i < levelNPoints; i++, p++) {
        final int index = coordinates[p];

        if ((types[index] & (EXCLUDED | MAX_AREA)) != 0) {
          // This point can be ignored
          numberChanged++;
          continue;
        }

        getXy(index, xyz);

        // Extract the point coordinate
        final int x = xyz[0];
        final int y = xyz[1];

        final float value = getf(index);

        // It is more likely that the z stack will be out-of-bounds.
        // Adopt the xy limit lookup and process z lookup separately
        final boolean isInnerXy = (y != 0 && y != ylimit) && (x != 0 && x != xlimit);

        // Check for the highest neighbour

        // TODO - Try out using a Sobel operator to assign the gradient direction. Follow the
        // steepest gradient.

        int maxDirection = -1;
        float maxValue = value;
        for (int d = 8; d-- > 0;) {
          if (isInnerXy || isWithinXy(x, y, d)) {
            final int index2 = index + offset[d];
            final float vNeighbor = getf(index2);
            if (maxValue < vNeighbor) {
              // Higher neighbour
              maxValue = vNeighbor;
              maxDirection = d;
            } else if (maxValue == vNeighbor) {
              // Check if the neighbour is higher than this point (i.e. an equal higher neighbour
              // has been found)
              if (value != vNeighbor) {
                // Favour flat edges over diagonals in the case of equal neighbours
                if (flatEdge[d]) {
                  maxDirection = d;
                }

                // The neighbour is the same height, check if it is a maxima
              } else if ((types[index2] & MAX_AREA) != 0 && (maxDirection < 0 || flatEdge[d])) {
                maxDirection = d;
              }
            }
          }
        }

        if (maxDirection < 0) {
          // This could happen if all neighbours are the same height and none are maxima.
          // Since plateau maxima should be handled in the initial maximum finding stage, any equal
          // neighbours
          // should be processed eventually.
          coordinates[levelStart + (numberUnchanged++)] = index;
          continue;
        }

        types[index] |= MAX_AREA;
        maxima[index] = maxima[index + offset[maxDirection]];
        numberChanged++;
      } // for pixel i
    } else {
      for (int i = 0, p = levelStart; i < levelNPoints; i++, p++) {
        final int index = coordinates[p];

        if ((types[index] & (EXCLUDED | MAX_AREA)) != 0) {
          // This point can be ignored
          numberChanged++;
          continue;
        }

        getXyz(index, xyz);

        // Extract the point coordinate
        final int x = xyz[0];
        final int y = xyz[1];
        final int z = xyz[2];

        final float value = getf(index);

        // It is more likely that the z stack will be out-of-bounds.
        // Adopt the xy limit lookup and process z lookup separately
        final boolean isInnerXy = (y != 0 && y != ylimit) && (x != 0 && x != xlimit);
        final boolean isInnerXyz = (zlimit == 0) ? isInnerXy : isInnerXy && (z != 0 && z != zlimit);

        // Check for the highest neighbour

        // TODO - Try out using a Sobel operator to assign the gradient direction. Follow the
        // steepest gradient.

        int maxDirection = -1;
        float maxValue = value;
        for (int d = 26; d-- > 0;) {
          if (isInnerXyz || (isInnerXy && isWithinZ(z, d)) || isWithinXyz(x, y, z, d)) {
            final int index2 = index + offset[d];
            final float vNeighbor = getf(index2);
            if (maxValue < vNeighbor) {
              // Higher neighbour
              maxValue = vNeighbor;
              maxDirection = d;
            } else if (maxValue == vNeighbor) {
              // Check if the neighbour is higher than this point (i.e. an equal higher neighbour
              // has been found)
              if (value != vNeighbor) {
                // Favour flat edges over diagonals in the case of equal neighbours
                if (flatEdge[d]) {
                  maxDirection = d;
                }

                // The neighbour is the same height, check if it is a maxima
              } else if ((types[index2] & MAX_AREA) != 0 && (maxDirection < 0 || flatEdge[d])) {
                maxDirection = d;
              }
            }
          }
        }

        if (maxDirection < 0) {
          // This could happen if all neighbours are the same height and none are maxima.
          // Since plateau maxima should be handled in the initial maximum finding stage, any equal
          // neighbours
          // should be processed eventually.
          coordinates[levelStart + (numberUnchanged++)] = index;
          continue;
        }

        final int index2 = index + offset[maxDirection];

        // TODO.
        // The code below can be uncommented to flood fill a plateau with the first maxima that
        // touches it.
        // However this can lead to striping artifacts where diagonals are at the same level but
        // adjacent cells are not, e.g:
        // 1122
        // 1212
        // 2122
        // 1222
        // Consequently the code has been commented out and the default behaviour fills plateaus
        // from the
        // edges inwards with a bias in the direction of the sweep across the pixels.
        // A better method may be to assign pixels to the nearest maxima using a distance measure
        // (Euclidian, City-Block, etc). This would involve:
        // - Mark all plateau edges that touch a maxima
        // - for each maxima edge:
        // -- Measure distance for each plateau point to the nearest touching edge
        // - Compare distance maps for each maxima and assign points to nearest maxima

        // Flood fill
        // // A higher point has been found. Check if this position is a plateau
        // if ((types[index] & PLATEAU) == PLATEAU)
        // {
        // log(()->String.format("Plateau merge to higher level: %d @ [%d,%d] : %d", image[index],
        // x, y,
        // image[index2]));
        //
        // // Initialise the list to allow all points on this level to be processed.
        // if (list.length < levelNPoints)
        // {
        // list = new int[levelNPoints];
        // }
        //
        // expandPlateau(maxima, types, index, v, maxima[index2], list);
        // }
        // else
        // {

        types[index] |= MAX_AREA;
        maxima[index] = maxima[index2];
        numberChanged++;

        // }
      } // for pixel i
    }

    return numberChanged;
  }

  /**
   * Searches from the specified point to find all coordinates of the same value and assigns them to
   * given maximum.
   *
   * @param maxima the maxima
   * @param types the types
   * @param index0 the index 0
   * @param v0 the v 0
   * @param id the id
   * @param pointList the list
   */
  protected void expandPlateau(int[] maxima, byte[] types, int index0, float v0, int id,
      int[] pointList) {
    types[index0] |= LISTED; // mark first point as listed
    int listI = 0; // index of current search element in the list
    int listLen = 1; // number of elements in the list

    // we create a list of connected points and start the list at the current maximum
    pointList[listI] = index0;

    final int[] xyz = new int[3];

    if (is2D()) {
      do {
        final int index1 = pointList[listI];
        getXy(index1, xyz);
        final int x1 = xyz[0];
        final int y1 = xyz[1];

        // It is more likely that the z stack will be out-of-bounds.
        // Adopt the xy limit lookup and process z lookup separately

        final boolean isInnerXy = (y1 != 0 && y1 != ylimit) && (x1 != 0 && x1 != xlimit);

        for (int d = 8; d-- > 0;) {
          if (isInnerXy || isWithinXy(x1, y1, d)) {
            final int index2 = index1 + offset[d];
            if ((types[index2] & IGNORE) != 0) {
              // This has been done already, ignore this point
              continue;
            }

            final float v2 = getf(index2);

            if (v2 == v0) {
              // Add this to the search
              pointList[listLen++] = index2;
              types[index2] |= LISTED;
            }
          }
        }

        listI++;

      } while (listI < listLen);
    } else {
      do {
        final int index1 = pointList[listI];
        getXyz(index1, xyz);
        final int x1 = xyz[0];
        final int y1 = xyz[1];
        final int z1 = xyz[2];

        // It is more likely that the z stack will be out-of-bounds.
        // Adopt the xy limit lookup and process z lookup separately

        final boolean isInnerXy = (y1 != 0 && y1 != ylimit) && (x1 != 0 && x1 != xlimit);
        final boolean isInnerXyz =
            (zlimit == 0) ? isInnerXy : isInnerXy && (z1 != 0 && z1 != zlimit);

        for (int d = 26; d-- > 0;) {
          if (isInnerXyz || (isInnerXy && isWithinZ(z1, d)) || isWithinXyz(x1, y1, z1, d)) {
            final int index2 = index1 + offset[d];
            if ((types[index2] & IGNORE) != 0) {
              // This has been done already, ignore this point
              continue;
            }

            final float v2 = getf(index2);

            if (v2 == v0) {
              // Add this to the search
              pointList[listLen++] = index2;
              types[index2] |= LISTED;
            }
          }
        }

        listI++;

      } while (listI < listLen);
    }

    for (int i = listLen; i-- > 0;) {
      final int index = pointList[i];
      types[index] &= ~LISTED; // reset attributes no longer needed

      // Assign to the given maximum
      types[index] |= MAX_AREA;
      maxima[index] = id;
    }
  }

  /**
   * Loop over all points that have been assigned to a peak area and clear any pixels below the peak
   * growth threshold.
   *
   * @param pixels the pixels
   * @param types the types
   * @param searchMethod the search method
   * @param searchParameter the search parameter
   * @param stats the stats
   * @param resultsArray the results array
   * @param maxima the maxima
   */
  protected void pruneMaxima(Object pixels, byte[] types, SearchMethod searchMethod,
      double searchParameter, FindFociStatistics stats, FindFociResult[] resultsArray,
      int[] maxima) {
    setPixels(pixels);

    // Build an array containing the threshold for each peak.
    // Note that maxima are numbered from 1
    final int nMaxima = resultsArray.length;
    final float[] peakThreshold = new float[nMaxima + 1];
    for (int i = 1; i < peakThreshold.length; i++) {
      peakThreshold[i] =
          getTolerance(searchMethod, searchParameter, stats, resultsArray[i - 1].maxValue);
    }

    for (int i = maxima.length; i-- > 0;) {
      final int id = maxima[i];
      if (id != 0 && getf(i) < peakThreshold[id]) {
        // Unset this pixel as part of the peak
        maxima[i] = 0;
        types[i] &= ~MAX_AREA;
      }
    }
  }

  /**
   * Get the threshold that limits the maxima region growing.
   *
   * @param searchMethod The thresholding method
   * @param searchParameter The method thresholding parameter
   * @param stats The image statistics
   * @param v0 The current maxima value
   * @return The threshold
   */
  protected abstract float getTolerance(SearchMethod searchMethod, double searchParameter,
      FindFociStatistics stats, float v0);

  /**
   * Round the number.
   *
   * @param value the value
   * @return the rounded value
   */
  protected int round(double value) {
    return (int) Math.round(value);
  }

  /**
   * Round the number.
   *
   * @param value the value
   * @return the rounded value
   */
  protected int round(float value) {
    return Math.round(value);
  }

  /**
   * Loop over the image and sum the intensity and size of each peak area, storing this into the
   * results array.
   *
   * @param pixels the pixels
   * @param maxima the maxima
   * @param resultsArray the results array
   */
  protected void calculateInitialResults(Object pixels, int[] maxima,
      FindFociResult[] resultsArray) {
    setPixels(pixels);
    final int nMaxima = resultsArray.length;

    // Maxima are numbered from 1
    final int[] count = new int[nMaxima + 1];
    final double[] intensity = new double[nMaxima + 1];

    for (int i = maxima.length; i-- > 0;) {
      final int id = maxima[i];
      if (id != 0) {
        count[id]++;
        intensity[id] += getf(i);
      }
    }

    for (final FindFociResult result : resultsArray) {
      result.count = count[result.id];
      result.totalIntensity = intensity[result.id];
      result.averageIntensity = result.totalIntensity / result.count;
    }
  }

  /**
   * Loop over the image and sum the intensity of each peak area using the original image, storing
   * this into the results array.
   *
   * @param pixels the pixels
   * @param maxima the maxima
   * @param resultsArray the results array
   * @param originalNumberOfPeaks the original number of peaks
   */
  protected void calculateNativeResults(Object pixels, int[] maxima, FindFociResult[] resultsArray,
      int originalNumberOfPeaks) {
    setPixels(pixels);

    // Maxima are numbered from 1
    final double[] intensity = new double[originalNumberOfPeaks + 1];
    final float[] max = new float[originalNumberOfPeaks + 1];

    for (int i = maxima.length; i-- > 0;) {
      final int id = maxima[i];
      if (id != 0) {
        final float v = getf(i);
        intensity[id] += v;
        if (max[id] < v) {
          max[id] = v;
        }
      }
    }

    for (int i = 0; i < resultsArray.length; i++) {
      final FindFociResult result = resultsArray[i];
      final int id = result.id;
      if (intensity[id] != 0) {
        result.totalIntensity = intensity[id];
        result.maxValue = max[id];
      }
    }
  }

  /**
   * Calculate the peaks centre and maximum value. This could be done in many ways: - Max value -
   * Centre-of-mass (within a bounding box of max value defined by the centreParameter) - Gaussian
   * fit (Using a 2D projection defined by the centreParameter: (1) Maximum value; (other) Average
   * value)
   *
   * @param pixels the pixels
   * @param searchPixels the search pixels
   * @param maxima the maxima
   * @param types the types
   * @param resultsArray the results array
   * @param centreMethod the centre method
   * @param centreParameter the centre parameter
   */
  private void locateMaxima(Object pixels, Object searchPixels, int[] maxima, byte[] types,
      FindFociResult[] resultsArray, CentreMethod centreMethod, double centreParameter) {
    if (centreMethod == CentreMethod.MAX_VALUE_SEARCH) {
      return; // This is the current value so just return
    }

    // Swap to the search image for processing if necessary
    if (EnumSet.of(CentreMethod.GAUSSIAN_SEARCH, CentreMethod.CENTRE_OF_MASS_SEARCH)
        .contains(centreMethod)) {
      pixels = searchPixels;
    }

    setPixels(pixels);

    // Working list of peak coordinates
    int[] pointList = new int[0];

    // For each peak, compute the centre
    for (int i = 0; i < resultsArray.length; i++) {
      final FindFociResult result = resultsArray[i];
      // Ensure list is large enough
      if (pointList.length < result.count) {
        pointList = new int[result.count];
      }

      // Find the peak coords above the saddle
      final int maximaId = result.id;
      final int index = getIndex(result.x, result.y, result.z);
      final int listLen =
          findMaximaCoords(maxima, types, index, maximaId, result.highestSaddleValue, pointList);

      // Find the boundaries of the coordinates
      final int[] minXyz = new int[] {maxx, maxy, maxz};
      final int[] maxXyz = new int[] {0, 0, 0};
      final int[] xyz = new int[3];
      for (int listI = listLen; listI-- > 0;) {
        final int index1 = pointList[listI];
        getXyz(index1, xyz);
        for (int ii = 3; ii-- > 0;) {
          if (minXyz[ii] > xyz[ii]) {
            minXyz[ii] = xyz[ii];
          }
          if (maxXyz[ii] < xyz[ii]) {
            maxXyz[ii] = xyz[ii];
          }
        }
      }

      // Extract sub image
      final int[] dimensions = new int[3];
      for (int ii = 3; ii-- > 0;) {
        dimensions[ii] = maxXyz[ii] - minXyz[ii] + 1;
      }

      final float[] subImage =
          extractSubImage(maxima, minXyz, dimensions, maximaId, result.highestSaddleValue);

      int[] centre = null;
      switch (centreMethod) {
        case GAUSSIAN_SEARCH:
        case GAUSSIAN_ORIGINAL:
          centre = findCentreGaussianFit(subImage, dimensions, round(centreParameter));
          break;

        case CENTRE_OF_MASS_SEARCH:
        case CENTRE_OF_MASS_ORIGINAL:
          centre = findCentreOfMass(subImage, dimensions, round(centreParameter));
          break;

        case MAX_VALUE_ORIGINAL:
        default:
          centre = findCentreMaxValue(subImage, dimensions);
      }

      if (centre != null) {
        if (isLogging() && IJ.debugMode) {
          final int[] finalCentre = centre;
          log(() -> {
            final int[] shift = new int[3];
            double distance = 0;
            final int[] coords = result.getCoordinates();
            for (int ii = 3; ii-- > 0;) {
              shift[ii] = coords[ii] - (finalCentre[ii] + minXyz[ii]);
              distance += shift[ii] * shift[ii];
            }
            return "Moved centre: " + shift[0] + " , " + shift[1] + " , " + shift[2] + " = "
                + IJ.d2s(Math.sqrt(distance), 2);
          });
        }

        // RESULT_[XYZ] are 0, 1, 2
        result.x = centre[0] + minXyz[0];
        result.y = centre[1] + minXyz[1];
        result.z = centre[2] + minXyz[2];
      }
    }
  }

  /**
   * Search for all connected points in the maxima above the saddle value.
   *
   * @return The number of points
   */
  private int findMaximaCoords(int[] maxima, byte[] types, int index0, int maximaId,
      float saddleValue, int[] pointList) {
    types[index0] |= LISTED; // mark first point as listed
    int listI = 0; // index of current search element in the list
    int listLen = 1; // number of elements in the list

    // we create a list of connected points and start the list at the current maximum
    pointList[listI] = index0;

    final int[] xyz = new int[3];

    if (is2D()) {
      do {
        final int index1 = pointList[listI];
        getXy(index1, xyz);
        final int x1 = xyz[0];
        final int y1 = xyz[1];

        final boolean isInnerXy = (y1 != 0 && y1 != ylimit) && (x1 != 0 && x1 != xlimit);

        for (int d = 8; d-- > 0;) {
          if (isInnerXy || isWithinXy(x1, y1, d)) {
            final int index2 = index1 + offset[d];
            if ((types[index2] & IGNORE) != 0 || maxima[index2] != maximaId) {
              // This has been done already, ignore this point
              continue;
            }

            final float v2 = getf(index2);

            if (v2 >= saddleValue) {
              // Add this to the search
              pointList[listLen++] = index2;
              types[index2] |= LISTED;
            }
          }
        }

        listI++;

      } while (listI < listLen);
    } else {
      do {
        final int index1 = pointList[listI];
        getXyz(index1, xyz);
        final int x1 = xyz[0];
        final int y1 = xyz[1];
        final int z1 = xyz[2];

        final boolean isInnerXy = (y1 != 0 && y1 != ylimit) && (x1 != 0 && x1 != xlimit);
        final boolean isInnerXyz =
            (zlimit == 0) ? isInnerXy : isInnerXy && (z1 != 0 && z1 != zlimit);

        for (int d = 26; d-- > 0;) {
          if (isInnerXyz || (isInnerXy && isWithinZ(z1, d)) || isWithinXyz(x1, y1, z1, d)) {
            final int index2 = index1 + offset[d];
            if ((types[index2] & IGNORE) != 0 || maxima[index2] != maximaId) {
              // This has been done already, ignore this point
              continue;
            }

            final float v2 = getf(index2);

            if (v2 >= saddleValue) {
              // Add this to the search
              pointList[listLen++] = index2;
              types[index2] |= LISTED;
            }
          }
        }

        listI++;

      } while (listI < listLen);
    }

    for (int i = listLen; i-- > 0;) {
      final int index = pointList[i];
      types[index] &= ~LISTED; // reset attributes no longer needed
    }

    return listLen;
  }

  /**
   * Extract a sub-image from the given input image using the specified boundaries. The minValue is
   * subtracted from all pixels. All pixels below the minValue are ignored (set to zero).
   *
   * @param maxima the maxima
   * @param minXyz the min xyz
   * @param dimensions the dimensions
   * @param maximaId the maxima id
   * @param minValue the min value
   * @return the float[]
   */
  private float[] extractSubImage(int[] maxima, int[] minXyz, int[] dimensions, int maximaId,
      float minValue) {
    final float[] subImage = new float[dimensions[0] * dimensions[1] * dimensions[2]];

    int imageIndex = 0;
    for (int z = 0; z < dimensions[2]; z++) {
      for (int y = 0; y < dimensions[1]; y++) {
        int index = getIndex(minXyz[0], y + minXyz[1], z + minXyz[2]);
        for (int x = 0; x < dimensions[0]; x++, index++, imageIndex++) {
          if (maxima[index] == maximaId) {
            final float v = getf(index);
            if (v > minValue) {
              subImage[imageIndex] = v - minValue;
            }
          }
        }
      }
    }

    return subImage;
  }

  /**
   * Finds the centre of the image using the maximum pixel value. If many pixels have the same value
   * the closest pixel to the geometric mean of the coordinates is returned.
   */
  private int[] findCentreMaxValue(float[] image, int[] dimensions) {
    // Find the maximum value in the image
    float maxValue = 0;
    int count = 0;
    int index = 0;
    for (int i = image.length; i-- > 0;) {
      if (maxValue < image[i]) {
        maxValue = image[i];
        index = i;
        count = 1;
      } else if (maxValue == image[i]) {
        count++;
      }
    }

    // Used to map index back to XYZ
    final int blockSize = dimensions[0] * dimensions[1];

    if (count == 1) {
      // There is only one maximum pixel
      final int[] xyz = new int[3];
      xyz[2] = index / (blockSize);
      final int mod = index % (blockSize);
      xyz[1] = mod / dimensions[0];
      xyz[0] = mod % dimensions[0];
      return xyz;
    }

    // Find geometric mean
    final double[] centre = new double[3];
    for (int i = image.length; i-- > 0;) {
      if (maxValue == image[i]) {
        final int[] xyz = new int[3];
        xyz[2] = i / (blockSize);
        final int mod = i % (blockSize);
        xyz[1] = mod / dimensions[0];
        xyz[0] = mod % dimensions[0];
        for (int j = 3; j-- > 0;) {
          centre[j] += xyz[j];
        }
      }
    }
    for (int j = 3; j-- > 0;) {
      centre[j] /= count;
    }

    // Find nearest point
    double distanceMin = Double.MAX_VALUE;
    int[] closest = new int[] {round(centre[0]), round(centre[1]), round(centre[2])};
    for (int i = image.length; i-- > 0;) {
      if (maxValue == image[i]) {
        final int[] xyz = new int[3];
        xyz[2] = i / (blockSize);
        final int mod = i % (blockSize);
        xyz[1] = mod / dimensions[0];
        xyz[0] = mod % dimensions[0];
        final double distance = Math.pow(xyz[0] - centre[0], 2) + Math.pow(xyz[1] - centre[1], 2)
            + Math.pow(xyz[2] - centre[2], 2);
        if (distanceMin > distance) {
          distanceMin = distance;
          closest = xyz;
        }
      }
    }

    return closest;
  }

  /**
   * Finds the centre of the image using the centre of mass within the given range of the maximum
   * pixel value.
   */
  private int[] findCentreOfMass(float[] subImage, int[] dimensions, int range) {
    final int[] centre = findCentreMaxValue(subImage, dimensions);
    double[] com = new double[] {centre[0], centre[1], centre[2]};

    // Iterate until convergence
    double distance;
    int iter = 0;
    do {
      final double[] newCom = findCentreOfMass(subImage, dimensions, range, com);
      distance = Math.pow(newCom[0] - com[0], 2) + Math.pow(newCom[1] - com[1], 2)
          + Math.pow(newCom[2] - com[2], 2);
      com = newCom;
      iter++;
    } while (distance > 1 && iter < 10);

    return convertCentre(com);
  }

  /**
   * Finds the centre of the image using the centre of mass within the given range of the specified
   * centre-of-mass.
   */
  private double[] findCentreOfMass(float[] subImage, int[] dimensions, int range, double[] com) {
    final int[] centre = convertCentre(com);

    final int[] min = new int[3];
    final int[] max = new int[3];
    if (range < 1) {
      range = 1;
    }
    for (int i = 3; i-- > 0;) {
      min[i] = centre[i] - range;
      max[i] = centre[i] + range;
      if (min[i] < 0) {
        min[i] = 0;
      }
      if (max[i] >= dimensions[i] - 1) {
        max[i] = dimensions[i] - 1;
      }
    }

    final int blockSize = dimensions[0] * dimensions[1];

    final double[] newCom = new double[3];
    double sum = 0;
    for (int z = min[2]; z <= max[2]; z++) {
      for (int y = min[1]; y <= max[1]; y++) {
        int index = blockSize * z + dimensions[0] * y + min[0];
        for (int x = min[0]; x <= max[0]; x++, index++) {
          final float value = subImage[index];
          if (value > 0) {
            sum += value;
            newCom[0] += x * value;
            newCom[1] += y * value;
            newCom[2] += z * value;
          }
        }
      }
    }

    assert sum != 0 : "No values in sub-image";

    for (int i = 3; i-- > 0;) {
      newCom[i] /= sum;
    }

    return newCom;
  }

  /**
   * Finds the centre of the image using a 2D Gaussian fit to projection along the Z-axis.
   *
   * @param subImage the sub image
   * @param dimensions the dimensions
   * @param projectionMethod (0) Average value; (1) Maximum value
   * @return the centre of the image
   */
  @Nullable
  private int[] findCentreGaussianFit(float[] subImage, int[] dimensions, int projectionMethod) {
    if (FindFoci_PlugIn.IS_GAUSSIAN_FIT_ENABLED < 1) {
      return null;
    }

    final int blockSize = dimensions[0] * dimensions[1];
    final float[] projection = new float[blockSize];

    if (projectionMethod == 1) {
      // Maximum value
      for (int z = dimensions[2]; z-- > 0;) {
        int index = blockSize * z;
        for (int i = 0; i < blockSize; i++, index++) {
          if (projection[i] < subImage[index]) {
            projection[i] = subImage[index];
          }
        }
      }
    } else {
      // Average value
      for (int z = dimensions[2]; z-- > 0;) {
        int index = blockSize * z;
        for (int i = 0; i < blockSize; i++, index++) {
          projection[i] += subImage[index];
        }
      }
      for (int i = blockSize; i-- > 0;) {
        projection[i] /= dimensions[2];
      }
    }

    final GaussianFit_PlugIn gf = new GaussianFit_PlugIn();
    final double[] fitParams = gf.fit(projection, dimensions[0], dimensions[1]);

    int[] centre = null;
    if (fitParams != null) {
      // Find the centre of mass along the z-axis
      centre = convertCentre(new double[] {fitParams[2], fitParams[3]});

      // Use the centre of mass along the projection axis
      double com = 0;
      long sum = 0;
      for (int z = dimensions[2]; z-- > 0;) {
        final int index = blockSize * z;
        final float value = subImage[index];
        if (value > 0) {
          com += z * value;
          sum += value;
        }
      }
      if (sum == 0) {
        centre[2] = dimensions[2] / 2;
      } else {
        // Avoid clipping
        centre[2] = MathUtils.clip(0, dimensions[2] - 1, round(com / sum));
      }
    }

    return centre;
  }

  /**
   * Convert the centre from double to int. Handles input arrays of length 2 or 3.
   */
  private int[] convertCentre(double[] centre) {
    final int[] newCentre = new int[3];
    for (int i = centre.length; i-- > 0;) {
      newCentre[i] = round(centre[i]);
    }
    return newCentre;
  }

  /**
   * Loop over the results array and calculate the average intensity and the intensity above the
   * background and min level.
   *
   * @param resultsArray the results array
   * @param background the background
   * @param min the min
   */
  private static void calculateFinalResults(FindFociResult[] resultsArray, double background,
      double min) {
    for (int i = 0; i < resultsArray.length; i++) {
      final FindFociResult result = resultsArray[i];
      result.totalIntensityAboveBackground = result.totalIntensity - background * result.count;
      result.totalIntensityAboveImageMinimum = result.totalIntensity - min * result.count;
      result.averageIntensity = result.totalIntensity / result.count;
      result.averageIntensityAboveBackground = result.totalIntensityAboveBackground / result.count;
      result.averageIntensityAboveImageMinimum =
          result.totalIntensityAboveImageMinimum / result.count;
    }
  }

  /**
   * Finds the highest saddle point for each peak.
   *
   * @param pixels the pixels
   * @param types the types
   * @param resultsArray the results array
   * @param maxima the maxima
   * @return The saddle points. Contains an entry for each peak indexed from 1. The entry is a
   *         linked list of saddle points. Each saddle point is an array containing the neighbouring
   *         peak ID and the saddle value.
   */
  private FindFociSaddleList[] findSaddlePoints(Object pixels, byte[] types,
      FindFociResult[] resultsArray, int[] maxima) {
    setPixels(pixels);

    final FindFociSaddleList[] saddlePoints = new FindFociSaddleList[resultsArray.length + 1];

    // Initialise the saddle points
    final int nMaxima = resultsArray.length;

    final int[] xyz = new int[3];

    // Do an initial sweep of half-neighbours to mark pixels for a saddle search
    if (is2D()) {
      for (int i = maxxByMaxyByMaxz; i-- > 0;) {
        final int id = maxima[i];
        if (id == 0) {
          continue;
        }

        getXy(i, xyz);

        final int x = xyz[0];
        final int y = xyz[1];

        final boolean isInnerXy = (y != 0 && y != ylimit) && (x != 0 && x != xlimit);

        boolean saddle = false;
        for (int d = 4; d-- > 0;) {
          if (isInnerXy || isWithinXy2(x, y, d)) {
            final int index = i + offset2[d];
            final int id2 = maxima[index];
            if (id2 != 0 && id2 != id) {
              // This is saddle search point between two touching maxima
              saddle = true;
              types[index] |= SADDLE_SEARCH;
            }
          }
        }
        if (saddle) {
          types[i] |= SADDLE_SEARCH;
        }
      }
    } else {
      for (int i = maxxByMaxyByMaxz; i-- > 0;) {
        final int id = maxima[i];
        if (id == 0) {
          continue;
        }

        getXyz(i, xyz);

        final int x = xyz[0];
        final int y = xyz[1];
        final int z = xyz[2];

        final boolean isInnerXy = (y != 0 && y != ylimit) && (x != 0 && x != xlimit);
        final boolean isInnerXyz = (zlimit == 0) ? isInnerXy : isInnerXy && (z != 0 && z != zlimit);

        // It is more likely that the z stack will be out-of-bounds.
        // Adopt the xy limit lookup and process z lookup separately

        boolean saddle = false;
        for (int d = 13; d-- > 0;) {
          if (isInnerXyz || (isInnerXy && isWithinZ2(z, d)) || isWithinXyz2(x, y, z, d)) {
            final int index = i + offset2[d];
            final int id2 = maxima[index];
            if (id2 != 0 && id2 != id) {
              // This is saddle search point between two touching maxima
              saddle = true;
              types[index] |= SADDLE_SEARCH;
            }
          }
        }
        if (saddle) {
          types[i] |= SADDLE_SEARCH;
        }
      }
    }

    // Find the bounding dimensions for each peak which are saddle search points
    // and search within that
    findBounds(resultsArray, maxima, types);

    setupFindHighestSaddleValues(nMaxima);

    for (int i = 0; i < resultsArray.length; i++) {
      final FindFociResult result = resultsArray[i];
      // Skip if no saddles
      if (result.maxx < 0) {
        saddlePoints[i + 1] = new FindFociSaddleList();
        continue;
      }

      findHighestSaddleValues(result, maxima, types, saddlePoints);
    }

    finaliseFindHighestSaddleValues();

    // Reset attributes no longer needed
    for (int i = maxxByMaxyByMaxz; i-- > 0;) {
      types[i] &= ~SADDLE_SEARCH;
    }

    return saddlePoints;
  }

  /**
   * Set up processing for
   * {@link #findHighestSaddleValues(FindFociResult, int[], byte[], FindFociSaddleList[])}.
   *
   * @param numberOfMaxima the number of maxima
   */
  protected void setupFindHighestSaddleValues(int numberOfMaxima) {
    final int size = numberOfMaxima + 1;
    if (highestSaddleValues == null || highestSaddleValues.length < size) {
      highestSaddleValues = new float[size];
    }
  }

  /**
   * Find highest saddle values for each maxima touching the given result and add them to the saddle
   * points.
   *
   * @param result the result
   * @param maxima the maxima
   * @param types the types
   * @param saddlePoints the saddle points
   */
  protected void findHighestSaddleValues(FindFociResult result, int[] maxima, byte[] types,
      FindFociSaddleList[] saddlePoints) {
    Arrays.fill(highestSaddleValues, noSaddleValue);
    final int id = result.id;

    final boolean alwaysInnerY = (result.miny != 0 && result.maxy != maxy);
    final boolean alwaysInnerX = (result.minx != 0 && result.maxx != maxx);

    if (is2D()) {
      for (int y = result.miny; y < result.maxy; y++) {
        final boolean isInnerY = alwaysInnerY || (y != 0 && y != ylimit);
        for (int x = result.minx, index1 = getIndex(result.minx, y); x < result.maxx;
            x++, index1++) {
          if ((types[index1] & SADDLE_SEARCH) == 0) {
            continue;
          }
          if (maxima[index1] == id) {
            final float v1 = getf(index1);

            final boolean isInnerXy = isInnerY && (alwaysInnerX || (x != 0 && x != xlimit));

            for (int d = 8; d-- > 0;) {
              if (isInnerXy || isWithinXy(x, y, d)) {
                // Get the coords
                final int index2 = index1 + offset[d];
                final int id2 = maxima[index2];

                if (id2 == id || id2 == 0) {
                  // Same maxima, or no maxima, do nothing
                  continue;
                }

                // This is another peak, see if it a saddle highpoint
                final float v2 = getf(index2);

                // Take the lower of the two points as the saddle
                final float minV;
                if (v1 < v2) {
                  types[index1] |= SADDLE;
                  minV = v1;
                } else {
                  types[index2] |= SADDLE;
                  minV = v2;
                }

                if (highestSaddleValues[id2] < minV) {
                  highestSaddleValues[id2] = minV;
                }
              }
            }
          }
        }
      }
    } else {
      for (int z = result.minz; z < result.maxz; z++) {
        final boolean isInnerZ = (zlimit == 0) || (z != 0 && z != zlimit);
        for (int y = result.miny; y < result.maxy; y++) {
          final boolean isInnerY = alwaysInnerY || (y != 0 && y != ylimit);
          for (int x = result.minx, index1 = getIndex(result.minx, y, z); x < result.maxx;
              x++, index1++) {
            if ((types[index1] & SADDLE_SEARCH) == 0) {
              continue;
            }
            if (maxima[index1] == id) {
              final float v1 = getf(index1);

              final boolean isInnerXy = isInnerY && (alwaysInnerX || (x != 0 && x != xlimit));
              final boolean isInnerXyz = isInnerXy && isInnerZ;

              for (int d = 26; d-- > 0;) {
                if (isInnerXyz || (isInnerXy && isWithinZ(z, d)) || isWithinXyz(x, y, z, d)) {
                  // Get the coords
                  final int index2 = index1 + offset[d];
                  final int id2 = maxima[index2];

                  if (id2 == id || id2 == 0) {
                    // Same maxima, or no maxima, do nothing
                    continue;
                  }

                  // This is another peak, see if it a saddle highpoint
                  final float v2 = getf(index2);

                  // Take the lower of the two points as the saddle
                  final float minV;
                  if (v1 < v2) {
                    types[index1] |= SADDLE;
                    minV = v1;
                  } else {
                    types[index2] |= SADDLE;
                    minV = v2;
                  }

                  if (highestSaddleValues[id2] < minV) {
                    highestSaddleValues[id2] = minV;
                  }
                }
              }
            }
          }
        }
      }
    }

    // Find the highest saddle
    int highestNeighbourPeakId = 0;
    float highestNeighbourValue = noSaddleValue;
    int count = 0;
    for (int id2 = 1; id2 < highestSaddleValues.length; id2++) {
      if (highestSaddleValues[id2] != noSaddleValue) {
        count++;
        if (highestNeighbourValue < highestSaddleValues[id2]) {
          highestNeighbourValue = highestSaddleValues[id2];
          highestNeighbourPeakId = id2;
        }
      }
    }
    final FindFociSaddleList list = new FindFociSaddleList(count);
    if (count != 0) {
      for (int id2 = 1; id2 < highestSaddleValues.length; id2++) {
        if (highestSaddleValues[id2] != noSaddleValue) {
          list.add(new FindFociSaddle(id2, highestSaddleValues[id2]));
        }
      }
      list.sort(FindFociSaddle::compare);
    }
    saddlePoints[id] = list;

    // Set the saddle point
    if (highestNeighbourPeakId != 0) {
      result.saddleNeighbourId = highestNeighbourPeakId;
      result.highestSaddleValue = highestNeighbourValue;
    }
  }

  /**
   * Called after all calls to
   * {@link #findHighestSaddleValues(FindFociResult, int[], byte[], FindFociSaddleList[])}.
   */
  protected void finaliseFindHighestSaddleValues() {
    // Do nothing
  }

  /**
   * Gets the max peak size. The default implementation uses the {@link FindFociResult#count}.
   *
   * @param resultsArray the results array
   * @return the max peak size
   */
  protected int getMaxPeakSize(FindFociResult[] resultsArray) {
    int maxPeakSize = 0;
    for (int i = 0; i < resultsArray.length; i++) {
      final FindFociResult result = resultsArray[i];
      if (maxPeakSize < result.count) {
        maxPeakSize = result.count;
      }
    }
    return maxPeakSize;
  }

  /**
   * Find the size and intensity of peaks above their saddle heights.
   *
   * @param resultsArray the results array
   * @param pixels the pixels
   * @param maxima the maxima
   * @param types the types
   */
  private void analysePeaks(FindFociResult[] resultsArray, Object pixels, int[] maxima,
      byte[] types) {
    setPixels(pixels);
    // TODO - Determine which of these is faster
    analyseNonContiguousPeaks(resultsArray, maxima);
    // analyseContiguousPeaks(resultsArray, pixels, maxima, types)
  }

  /**
   * Find the size and intensity of peaks above their saddle heights.
   *
   * @param resultsArray the results array
   * @param pixels the pixels
   * @param maxima the maxima
   * @param types the types
   * @return the working list
   */
  private int[] analyseContiguousPeaks(FindFociResult[] resultsArray, Object pixels, int[] maxima,
      byte[] types) {
    setPixels(pixels);

    int[] pointList = new int[0];

    for (int i = 0; i < resultsArray.length; i++) {
      final FindFociResult result = resultsArray[i];
      if (result.highestSaddleValue == noSaddleValue) {
        continue;
      }
      if (result.count == 1) {
        result.countAboveSaddle = 1;
        result.intensityAboveSaddle = result.totalIntensity;
      }
      pointList = analyseContiguousPeak(maxima, types, result, pointList);
    }

    for (int i = types.length; i-- > 0;) {
      types[i] &= ~LISTED; // reset attributes no longer needed
    }

    return pointList;
  }

  /**
   * Find the size and intensity of peaks above their saddle heights.
   *
   * @param resultsArray the results array
   * @param maxima the maxima
   */
  protected void analyseNonContiguousPeaks(FindFociResult[] resultsArray, int[] maxima) {
    // Create an array of the size/intensity of each peak above the highest saddle
    final double[] peakIntensity = new double[resultsArray.length + 1];
    final int[] peakSize = new int[peakIntensity.length];

    // Store all the saddle heights
    final float[] saddleHeight = new float[peakIntensity.length];
    for (int i = 0; i < resultsArray.length; i++) {
      final FindFociResult result = resultsArray[i];
      saddleHeight[result.id] = result.highestSaddleValue;
    }

    for (int i = maxima.length; i-- > 0;) {
      final int id = maxima[i];
      if (id != 0) {
        final float v = getf(i);
        if (v > saddleHeight[id]) {
          peakIntensity[id] += v;
          peakSize[id]++;
        }
      }
    }

    for (int i = 0; i < resultsArray.length; i++) {
      final FindFociResult result = resultsArray[i];
      result.countAboveSaddle = peakSize[result.id];
      result.intensityAboveSaddle = peakIntensity[result.id];
    }
  }

  /**
   * Searches from the specified maximum to find all contiguous points above the saddle.
   *
   * @param maxima the maxima
   * @param types the types
   * @param result the result
   * @param pointList the list
   * @return True if this is a true plateau, false if the plateau reaches a higher point
   */
  protected int[] analyseContiguousPeak(int[] maxima, byte[] types, FindFociResult result,
      int[] pointList) {
    final int index0 = getIndex(result.x, result.y, result.z);
    final int peakId = result.id;
    final float v0 = result.highestSaddleValue;

    if (pointList.length < result.count) {
      pointList = new int[result.count];
    }

    types[index0] |= LISTED; // mark first point as listed
    int listI = 0; // index of current search element in the list
    int listLen = 1; // number of elements in the list

    // we create a list of connected points and start the list at the current maximum
    pointList[listI] = index0;

    final int[] xyz = new int[3];

    double sum = 0;

    if (is2D()) {
      do {
        final int index1 = pointList[listI];
        getXy(index1, xyz);
        final int x1 = xyz[0];
        final int y1 = xyz[1];

        // It is more likely that the z stack will be out-of-bounds.
        // Adopt the xy limit lookup and process z lookup separately

        final boolean isInnerXy = (y1 != 0 && y1 != ylimit) && (x1 != 0 && x1 != xlimit);

        for (int d = 8; d-- > 0;) {
          if (isInnerXy || isWithinXy(x1, y1, d)) {
            final int index2 = index1 + offset[d];
            if ((types[index2] & LISTED) != 0 || maxima[index2] != peakId) {
              // Different peak or already done
              continue;
            }

            final float v1 = getf(index2);
            if (v1 > v0) {
              pointList[listLen++] = index2;
              types[index2] |= LISTED;
              sum += v1;
            }
          }
        }

        listI++;

      } while (listI < listLen);
    } else {
      do {
        final int index1 = pointList[listI];
        getXyz(index1, xyz);
        final int x1 = xyz[0];
        final int y1 = xyz[1];
        final int z1 = xyz[2];

        // It is more likely that the z stack will be out-of-bounds.
        // Adopt the xy limit lookup and process z lookup separately

        final boolean isInnerXy = (y1 != 0 && y1 != ylimit) && (x1 != 0 && x1 != xlimit);
        final boolean isInnerXyz =
            (zlimit == 0) ? isInnerXy : isInnerXy && (z1 != 0 && z1 != zlimit);

        for (int d = 26; d-- > 0;) {
          if (isInnerXyz || (isInnerXy && isWithinZ(z1, d)) || isWithinXyz(x1, y1, z1, d)) {
            final int index2 = index1 + offset[d];
            if ((types[index2] & LISTED) != 0 || maxima[index2] != peakId) {
              // Different peak or already done
              continue;
            }

            final float v1 = getf(index2);
            if (v1 > v0) {
              pointList[listLen++] = index2;
              types[index2] |= LISTED;
              sum += v1;
            }
          }
        }

        listI++;

      } while (listI < listLen);
    }

    result.countAboveSaddle = listI;
    result.intensityAboveSaddle = sum;

    return pointList;
  }

  /**
   * Searches from the specified maximum to find all contiguous points above the saddle.
   *
   * @param maxima the maxima
   * @param types the types
   * @param result the result
   * @param pointList the list
   * @param peakIdMap the peak id map
   * @param peakId the peak id
   * @return True if this is a true plateau, false if the plateau reaches a higher point
   */
  protected int[] analyseContiguousPeak(int[] maxima, byte[] types, FindFociResult result,
      int[] pointList, final int[] peakIdMap, final int peakId) {
    final int index0 = getIndex(result.x, result.y, result.z);
    final float v0 = result.highestSaddleValue;

    if (pointList.length < result.count) {
      pointList = new int[result.count];
    }

    types[index0] |= LISTED; // mark first point as listed
    int listI = 0; // index of current search element in the list
    int listLen = 1; // number of elements in the list

    // we create a list of connected points and start the list at the current maximum
    pointList[listI] = index0;

    final int[] xyz = new int[3];

    double sum = 0;

    if (is2D()) {
      do {
        final int index1 = pointList[listI];
        getXy(index1, xyz);
        final int x1 = xyz[0];
        final int y1 = xyz[1];

        // It is more likely that the z stack will be out-of-bounds.
        // Adopt the xy limit lookup and process z lookup separately

        final boolean isInnerXy = (y1 != 0 && y1 != ylimit) && (x1 != 0 && x1 != xlimit);

        for (int d = 8; d-- > 0;) {
          if (isInnerXy || isWithinXy(x1, y1, d)) {
            final int index2 = index1 + offset[d];
            if ((types[index2] & LISTED) != 0 || peakIdMap[maxima[index2]] != peakId) {
              // Already done or different peak
              continue;
            }

            final float v1 = getf(index2);
            if (v1 > v0) {
              pointList[listLen++] = index2;
              types[index2] |= LISTED;
              sum += v1;
            }
          }
        }

        listI++;

      } while (listI < listLen);
    } else {
      do {
        final int index1 = pointList[listI];
        getXyz(index1, xyz);
        final int x1 = xyz[0];
        final int y1 = xyz[1];
        final int z1 = xyz[2];

        // It is more likely that the z stack will be out-of-bounds.
        // Adopt the xy limit lookup and process z lookup separately

        final boolean isInnerXy = (y1 != 0 && y1 != ylimit) && (x1 != 0 && x1 != xlimit);
        final boolean isInnerXyz =
            (zlimit == 0) ? isInnerXy : isInnerXy && (z1 != 0 && z1 != zlimit);

        for (int d = 26; d-- > 0;) {
          if (isInnerXyz || (isInnerXy && isWithinZ(z1, d)) || isWithinXyz(x1, y1, z1, d)) {
            final int index2 = index1 + offset[d];
            if ((types[index2] & LISTED) != 0 || peakIdMap[maxima[index2]] != peakId) {
              // Already done or different peak
              continue;
            }

            final float v1 = getf(index2);
            if (v1 > v0) {
              pointList[listLen++] = index2;
              types[index2] |= LISTED;
              sum += v1;
            }
          }
        }

        listI++;

      } while (listI < listLen);
    }

    for (int i = listLen; i-- > 0;) {
      types[pointList[i]] &= ~LISTED; // reset attributes no longer needed
    }

    result.countAboveSaddle = listI;
    result.intensityAboveSaddle = sum;

    return pointList;
  }

  /**
   * Find the bounds of peaks.
   */
  private void findBounds(FindFociResult[] resultsArray, int[] maxima, byte[] types) {
    // Store the xyz limits for each peak.
    // This speeds up re-computation of the height above the min saddle.

    final int size = resultsArray.length + 1;
    final int[] lx = new int[size];
    final int[] ly = new int[size];
    Arrays.fill(lx, this.maxx);
    Arrays.fill(ly, this.maxy);
    final int[] ux = new int[size];
    final int[] uy = new int[size];
    Arrays.fill(ux, -2);

    if (is2D()) {
      for (int y = 0, i = 0; y < this.maxy; y++) {
        for (int x = 0; x < this.maxx; x++, i++) {
          if ((types[i] & SADDLE_SEARCH) != 0) {
            final int id = maxima[i];
            if (id != 0) {
              // Get bounds
              //@formatter:off
              if (lx[id] > x) { lx[id] = x; }
              if (ly[id] > y) { ly[id] = y; }
              if (ux[id] < x) { ux[id] = x; }
              if (uy[id] < y) { uy[id] = y; }
              //@formatter:on
            }
          }
        }
      }

      for (int i = 0; i < resultsArray.length; i++) {
        final FindFociResult result = resultsArray[i];
        result.minx = lx[result.id];
        result.miny = ly[result.id];
        result.minz = 0;
        // Allow iterating i=min; i<max; i++
        result.maxx = ux[result.id] + 1;
        result.maxy = uy[result.id] + 1;
        result.maxz = 1;
      }
    } else {
      final int[] lz = new int[size];
      Arrays.fill(lz, this.maxz);
      final int[] uz = new int[size];

      for (int z = 0, i = 0; z < this.maxz; z++) {
        for (int y = 0; y < this.maxy; y++) {
          for (int x = 0; x < this.maxx; x++, i++) {
            if ((types[i] & SADDLE_SEARCH) != 0) {
              final int id = maxima[i];
              if (id != 0) {
                // Get bounds
                //@formatter:off
                if (lx[id] > x) { lx[id] = x; }
                if (ly[id] > y) { ly[id] = y; }
                if (lz[id] > z) { lz[id] = z; }
                if (ux[id] < x) { ux[id] = x; }
                if (uy[id] < y) { uy[id] = y; }
                if (uz[id] < z) { uz[id] = z; }
                //@formatter:on
              }
            }
          }
        }
      }

      for (int i = 0; i < resultsArray.length; i++) {
        final FindFociResult result = resultsArray[i];
        result.minx = lx[result.id];
        result.miny = ly[result.id];
        result.minz = lz[result.id];
        // Allow iterating i=min; i<max; i++
        result.maxx = ux[result.id] + 1;
        result.maxy = uy[result.id] + 1;
        result.maxz = uz[result.id] + 1;
      }
    }
  }

  /**
   * Find the size and intensity of peaks above their saddle heights.
   *
   * @param resultsArray the results array
   * @param pixels the pixels
   * @param maxima the maxima
   */
  protected void analysePeaksWithBounds(FindFociResult[] resultsArray, Object pixels,
      int[] maxima) {
    setPixels(pixels);

    // Create an array of the size/intensity of each peak above the highest saddle
    final double[] peakIntensity = new double[resultsArray.length + 1];
    final int[] peakSize = new int[resultsArray.length + 1];

    // Store all the saddle heights
    final float[] saddleHeight = new float[resultsArray.length + 1];
    for (int i = 0; i < resultsArray.length; i++) {
      final FindFociResult result = resultsArray[i];
      saddleHeight[result.id] = result.highestSaddleValue;
    }

    // Store the xyz limits for each peak.
    // This speeds up re-computation of the height above the min saddle.
    final int[] lx = new int[peakIntensity.length];
    final int[] ly = new int[peakIntensity.length];
    final int[] lz = new int[peakIntensity.length];
    Arrays.fill(lx, this.maxx);
    Arrays.fill(ly, this.maxy);
    Arrays.fill(lz, this.maxz);
    final int[] ux = new int[peakIntensity.length];
    final int[] uy = new int[peakIntensity.length];
    final int[] uz = new int[peakIntensity.length];

    for (int z = 0, i = 0; z < this.maxz; z++) {
      for (int y = 0; y < this.maxy; y++) {
        for (int x = 0; x < this.maxx; x++, i++) {
          final int id = maxima[i];
          if (id != 0) {
            final float v = getf(i);
            if (v > saddleHeight[id]) {
              peakIntensity[id] += v;
              peakSize[id]++;
            }

            // Get bounds
            //@formatter:off
            if (lx[id] > x) { lx[id] = x; }
            if (ly[id] > y) { ly[id] = y; }
            if (lz[id] > z) { lz[id] = z; }
            if (ux[id] < x) { ux[id] = x; }
            if (uy[id] < y) { uy[id] = y; }
            if (uz[id] < z) { uz[id] = z; }
            //@formatter:on
          }
        }
      }
    }

    for (int i = 0; i < resultsArray.length; i++) {
      final FindFociResult result = resultsArray[i];
      result.countAboveSaddle = peakSize[result.id];
      result.intensityAboveSaddle = peakIntensity[result.id];
      result.minx = lx[result.id];
      result.miny = ly[result.id];
      result.minz = lz[result.id];
      // Allow iterating i=min; i<max; i++
      result.maxx = ux[result.id] + 1;
      result.maxy = uy[result.id] + 1;
      result.maxz = uz[result.id] + 1;
    }
  }

  /**
   * Merge sub-peaks into their highest neighbour peak using the highest saddle point.
   */
  @Nullable
  private FindFociResult[] mergeSubPeaks(FindFociResult[] resultsArray, Object pixels, int[] maxima,
      byte[] types, int minSize, PeakMethod peakMethod, double peakParameter,
      FindFociStatistics stats, FindFociSaddleList[] saddlePoints, boolean isLogging,
      boolean restrictAboveSaddle, boolean nonContiguous) {
    setPixels(pixels);
    setNoSaddleValue(stats);

    final FindFociMergeTempResults mergeResults = mergeUsingHeight(resultsArray, pixels, maxima,
        peakMethod, peakParameter, stats, saddlePoints);

    if (isLogging) {
      timingSplit("Height filter : Number of peaks = " + countPeaks(mergeResults.peakIdMap));
    }
    if (ImageJUtils.isInterrupted()) {
      return null;
    }

    if (minSize > 1) {
      mergeUsingSize(mergeResults, maxima, minSize);
    }

    if (isLogging) {
      timingSplit("Size filter : Number of peaks = " + countPeaks(mergeResults.peakIdMap));
    }
    if (ImageJUtils.isInterrupted()) {
      return null;
    }

    // This can be intensive due to the requirement to recount the peak size above the saddle, so it
    // is optional
    if (minSize > 1 && restrictAboveSaddle) {
      mergeAboveSaddle(mergeResults, pixels, maxima, types, minSize, nonContiguous);

      if (isLogging) {
        timingSplit(
            "Size above saddle filter : Number of peaks = " + countPeaks(mergeResults.peakIdMap));
      }
      if (ImageJUtils.isInterrupted()) {
        return null;
      }

      // TODO - Add an intensity above saddle filter.
      // All code is in place so this should be a copy of the code above.
      // However what should the intensity above the saddle be relative to?
      // - It could be an absolute value. This is image specific.
      // - It could be relative to the total peak intensity.
    }

    return mergeFinal(resultsArray, mergeResults.peakIdMap, maxima);
  }

  /**
   * Merge sub-peaks into their highest neighbour peak using the highest saddle point based on a min
   * height criteria.
   */
  private FindFociMergeTempResults mergeUsingHeight(FindFociResult[] resultsArray, Object pixels,
      int[] maxima, PeakMethod peakMethod, double peakParameter, FindFociStatistics stats,
      FindFociSaddleList[] saddlePoints) {
    setPixels(pixels);
    setNoSaddleValue(stats);

    // Create an array containing the mapping between the original peak Id and the current Id that
    // the peak has been
    // mapped to.
    final int[] peakIdMap = new int[resultsArray.length + 1];

    // Used for fast look-up of peaks when the order has been changed
    final FindFociResult[] resultList = new FindFociResult[peakIdMap.length];
    for (int i = 1; i < peakIdMap.length; i++) {
      peakIdMap[i] = i;
      resultList[i] = resultsArray[i - 1];
    }

    if (peakParameter > 0) {
      // Process all the peaks for the minimum height. Process in order of saddle height
      sortDescResults(resultsArray, SortMethod.SADDLE_HEIGHT, stats);

      for (int i = 0; i < resultsArray.length; i++) {
        final FindFociResult result = resultsArray[i];
        final int peakId = result.id;

        // Check if this peak has been reassigned or has no neighbours
        if (peakId != peakIdMap[peakId]) {
          continue;
        }

        final FindFociSaddleList saddles = saddlePoints[peakId];
        consolidateSaddles(result, saddles, peakIdMap);

        final FindFociSaddle highestSaddle = (saddles.size == 0) ? null
            // Equivalent to findHighestSaddle(saddles)
            : saddles.list[0];

        final float peakBase = (highestSaddle == null) ? stats.background : highestSaddle.value;

        final double threshold = getPeakHeight(peakMethod, peakParameter, stats, result.maxValue);

        if (result.maxValue - peakBase < threshold) {
          // This peak is not high enough, merge into the neighbour peak
          if (highestSaddle == null) {
            removePeak(maxima, peakIdMap, result, saddles, peakId);
          } else {
            // Find the neighbour peak (use the map because the neighbour may have been merged)
            final int neighbourPeakId = highestSaddle.id;
            // Equivalent to findResult(resultsArray, neighbourPeakId)
            final FindFociResult neighbourResult = resultList[neighbourPeakId];

            mergePeak(maxima, peakIdMap, peakId, result, neighbourPeakId, neighbourResult, saddles,
                saddlePoints[neighbourPeakId], highestSaddle);
          }
        }
      }
    }

    return new FindFociMergeTempResults(resultsArray, saddlePoints, peakIdMap, resultList);
  }

  /**
   * Merge sub-peaks into their highest neighbour peak using the highest saddle point based on a min
   * size criteria.
   */
  private void mergeUsingSize(FindFociMergeTempResults mergeResult, int[] maxima, int minSize) {
    final FindFociResult[] resultsArray = mergeResult.resultsArray;
    final FindFociSaddleList[] saddlePoints = mergeResult.saddlePoints;
    final int[] peakIdMap = mergeResult.peakIdMap;
    final FindFociResult[] resultList = mergeResult.resultList;

    // Process all the peaks for the minimum size. Process in order of smallest first
    sortAscResults(resultsArray, SortMethod.COUNT, null);

    for (int i = 0; i < resultsArray.length; i++) {
      final FindFociResult result = resultsArray[i];
      final int peakId = result.id;

      // Check if this peak has been reassigned or has no neighbours
      if (peakId != peakIdMap[peakId]) {
        continue;
      }

      if (result.count < minSize) {
        // This peak is not large enough, merge into the neighbour peak
        final FindFociSaddleList saddles = saddlePoints[peakId];
        consolidateSaddles(result, saddles, peakIdMap);
        if (saddles.size == 0) {
          removePeak(maxima, peakIdMap, result, saddles, peakId);
        } else {
          // Equivalent to findHighestSaddle(saddles)
          final FindFociSaddle highestSaddle = saddles.list[0];

          // Find the neighbour peak (use the map because the neighbour may have been merged)
          final int neighbourPeakId = highestSaddle.id;
          // Equivalent to findResult(resultsArray, neighbourPeakId)
          final FindFociResult neighbourResult = resultList[neighbourPeakId];

          mergePeak(maxima, peakIdMap, peakId, result, neighbourPeakId, neighbourResult, saddles,
              saddlePoints[neighbourPeakId], highestSaddle);
        }
      }
    }
  }

  /**
   * Merge sub-peaks into their highest neighbour peak using the highest saddle point.
   */
  private void mergeAboveSaddle(FindFociMergeTempResults mergeResult, Object pixels, int[] maxima,
      byte[] types, int minSize, boolean nonContiguous) {
    final FindFociResult[] resultsArray = mergeResult.resultsArray;
    final FindFociSaddleList[] saddlePoints = mergeResult.saddlePoints;
    final int[] peakIdMap = mergeResult.peakIdMap;
    final FindFociResult[] resultList = mergeResult.resultList;

    updateSaddleDetails(resultsArray, peakIdMap);
    reassignMaxima(maxima, peakIdMap);
    int[] pointList = null;

    if (nonContiguous) {
      // Note: In the legacy code we find the number of pixels above the highest saddle value,
      // irrespective of
      // whether the pixels are contiguous. This is wrong and the height above the saddle should
      // refer to
      // the mass of the contiguous pixels above the saddle.
      analysePeaksWithBounds(resultsArray, pixels, maxima);
    } else {
      pointList = analyseContiguousPeaks(resultsArray, pixels, maxima, types);
    }

    // Process all the peaks for the minimum size above the saddle points. Process in order of
    // smallest first
    sortAscResults(resultsArray, SortMethod.COUNT_ABOVE_SADDLE, null);

    for (int i = 0; i < resultsArray.length; i++) {
      final FindFociResult result = resultsArray[i];
      final int peakId = result.id;

      // Check if this peak has been reassigned
      if (peakId != peakIdMap[peakId]) {
        continue;
      }

      if (result.countAboveSaddle < minSize) {
        // This peak is not large enough, merge into the neighbour peak

        final FindFociSaddleList saddles = saddlePoints[peakId];
        consolidateSaddles(result, saddles, peakIdMap);
        if (saddles.size == 0) {
          // No neighbour so just remove
          removePeak(maxima, peakIdMap, result, saddles, peakId);
        } else {
          // Equivalent to findHighestSaddle(saddles)
          final FindFociSaddle highestSaddle = saddles.list[0];

          // Find the neighbour peak (use the map because the neighbour may have been merged)
          final int neighbourPeakId = highestSaddle.id;
          // Equivalent to findResult(resultsArray, neighbourPeakId)
          final FindFociResult neighbourResult = resultList[neighbourPeakId];

          // Note: Ensure the peak counts above the saddle are updated.
          mergePeak(maxima, peakIdMap, peakId, result, neighbourPeakId, neighbourResult, saddles,
              saddlePoints[neighbourPeakId], highestSaddle, true, nonContiguous, types, pointList);
        }
      }
    }
  }

  /**
   * Finalised the merge of sub-peaks into their highest neighbour peak using the highest saddle
   * point.
   */
  private FindFociResult[] mergeFinal(FindFociResult[] resultsArray, int[] peakIdMap,
      int[] maxima) {
    resultsArray = removeFlaggedResults(resultsArray);

    reassignMaxima(maxima, peakIdMap);

    updateSaddleDetails(resultsArray, peakIdMap);

    return resultsArray;
  }

  private FindFociResult[] removeFlaggedResults(FindFociResult[] resultsArray) {
    // Remove merged peaks from the results
    sortDescResults(resultsArray, SortMethod.INTENSITY, null);

    int size = 0;
    while (size < resultsArray.length
        && resultsArray[size].totalIntensity != Double.NEGATIVE_INFINITY) {
      size++;
    }
    return trim(resultsArray, size);
  }

  private static FindFociResult[] trim(FindFociResult[] resultsArray, int size) {
    if (size < resultsArray.length) {
      return Arrays.copyOf(resultsArray, size);
    }
    return resultsArray;
  }

  private void removePeak(int[] maxima, int[] peakIdMap, FindFociResult result,
      FindFociSaddleList saddles, int peakId) {
    // No neighbour so just remove
    mergePeak(maxima, peakIdMap, peakId, result, 0, null, saddles, null, null);
  }

  private static int countPeaks(int[] peakIdMap) {
    int count = 0;
    for (int i = 1; i < peakIdMap.length; i++) {
      if (peakIdMap[i] == i) {
        count++;
      }
    }
    return count;
  }

  private void updateSaddleDetails(FindFociResult[] resultsArray, int[] peakIdMap) {
    for (int i = 0; i < resultsArray.length; i++) {
      final FindFociResult result = resultsArray[i];
      int neighbourPeakId = peakIdMap[result.saddleNeighbourId];

      // Ensure the peak is not marked as a saddle with itself
      if (neighbourPeakId == result.id) {
        neighbourPeakId = 0;
      }

      if (neighbourPeakId == 0) {
        clearSaddle(result);
      } else {
        result.saddleNeighbourId = neighbourPeakId;
      }
    }
  }

  private void clearSaddle(FindFociResult result) {
    result.countAboveSaddle = result.count;
    result.intensityAboveSaddle = result.totalIntensity;
    result.saddleNeighbourId = 0;
    result.highestSaddleValue = noSaddleValue;
  }

  /**
   * Find the highest saddle that has been assigned to the specified peak.
   *
   * @param saddles the saddles
   * @param peakId the peak id
   * @return the find foci saddle
   */
  private static FindFociSaddle findHighestSaddle(FindFociSaddleList saddles, int peakId) {
    for (int i = 0; i < saddles.size; i++) {
      final FindFociSaddle saddle = saddles.list[i];
      if (saddle.id == peakId) {
        // This works if the saddles are sorted by value
        return saddle;
      }
    }
    return null;
  }

  /**
   * Find the highest saddle. In the even of multiple saddles with the same value the one with the
   * lowest Id is returned.
   *
   * <p>The Ids must be consolidated before calling this method.
   *
   * @param saddles the saddles
   * @return the find foci saddle
   */
  @SuppressWarnings("unused")
  private static FindFociSaddle findHighestSaddle(FindFociSaddleList saddles) {
    FindFociSaddle highestSaddle = saddles.list[0];
    for (int i = 1; i < saddles.size && saddles.list[i].value == highestSaddle.value; i++) {
      if (highestSaddle.id > saddles.list[i].id) {
        highestSaddle = saddles.list[i];
      }
    }
    return highestSaddle;
  }

  /**
   * Consolidate the saddles to update their ids. Remove invalid saddles.
   *
   * @param result the result
   * @param saddles the saddles
   * @param peakIdMap the peak id map
   */
  private static void consolidateSaddles(FindFociResult result, FindFociSaddleList saddles,
      int[] peakIdMap) {
    final int peakId = result.id;
    int size = 0;
    final FindFociSaddle[] list = saddles.list;
    for (int i = 0; i < saddles.size; i++) {
      final FindFociSaddle saddle = list[i];
      // Consolidate the id
      final int newId = peakIdMap[saddle.id];
      if (newId == 0 || newId == peakId) {
        // Ignore saddle that is now invalid
        continue;
      }
      saddle.id = newId;
      list[size++] = saddle;
    }
    saddles.clear(size);
  }

  /**
   * Assigns the peak to the neighbour. Flags the peak as merged by setting the intensity to zero.
   * No computation of the size/intensity above the saddle is performed.
   *
   * @param maxima the maxima
   * @param peakIdMap the peak id map
   * @param peakId the peak id
   * @param result the result
   * @param neighbourPeakId the neighbour peak id
   * @param neighbourResult the neighbour result (can be null)
   * @param peakSaddles the peak saddles
   * @param neighbourSaddles the neighbour saddles
   * @param highestSaddle the highest saddle
   */
  private void mergePeak(int[] maxima, int[] peakIdMap, int peakId, FindFociResult result,
      int neighbourPeakId, FindFociResult neighbourResult, FindFociSaddleList peakSaddles,
      FindFociSaddleList neighbourSaddles, FindFociSaddle highestSaddle) {
    mergePeak(maxima, peakIdMap, peakId, result, neighbourPeakId, neighbourResult, peakSaddles,
        neighbourSaddles, highestSaddle, false, false, null, null);
  }

  /**
   * Assigns the peak to the neighbour. Flags the peak as merged by setting the intensity to zero.
   * If the highest saddle is lowered then recomputes the size/intensity above the saddle.
   *
   * @param maxima the maxima
   * @param peakIdMap the peak id map
   * @param peakId the peak id
   * @param result the result
   * @param neighbourPeakId the neighbour peak id
   * @param neighbourResult the neighbour result (can be null)
   * @param peakSaddles the peak saddles
   * @param neighbourSaddles the neighbour saddles
   * @param highestSaddle the highest saddle
   * @param updatePeakAboveSaddle Set to true to update peak above saddle
   * @param types the types
   * @param pointList the list
   */
  private void mergePeak(int[] maxima, int[] peakIdMap, int peakId, FindFociResult result,
      int neighbourPeakId, FindFociResult neighbourResult, FindFociSaddleList peakSaddles,
      FindFociSaddleList neighbourSaddles, FindFociSaddle highestSaddle,
      boolean updatePeakAboveSaddle, boolean nonContiguous, byte[] types, int[] pointList) {
    if (neighbourResult != null) {
      // Assign this peak's statistics to the neighbour
      neighbourResult.totalIntensity += result.totalIntensity;
      neighbourResult.count += result.count;

      neighbourResult.averageIntensity = neighbourResult.totalIntensity / neighbourResult.count;

      // Update the bounds
      if (updatePeakAboveSaddle) {
        neighbourResult.updateBounds(result);
      }

      // Check if the neighbour is higher and reassign the maximum point
      if (neighbourResult.maxValue < result.maxValue) {
        neighbourResult.maxValue = result.maxValue;
        neighbourResult.x = result.x;
        neighbourResult.y = result.y;
        neighbourResult.z = result.z;
      }

      // We work directly with the saddle lists to avoid memory allocation overhead

      // Note: For compatibility with the legacy code we have to keep the saddles in order
      // they were created (height then original Id) until they are merged with another peak.
      // Then they are sorted by height and current Id.

      // Consolidate the saddles of the neighbour. This should speed up processing.
      // 1. Remove all saddles with the peak that is being merged.
      int size = 0;
      final FindFociSaddle[] newNeighbourSaddles = neighbourSaddles.list;
      for (int i = 0; i < neighbourSaddles.size; i++) {
        final FindFociSaddle saddle = newNeighbourSaddles[i];
        // Consolidate the id
        final int newId = peakIdMap[saddle.id];
        if (newId == 0 || newId == peakId || newId == neighbourPeakId) {
          // Ignore saddle with peak that is being merged or has been removed
          continue;
        }
        saddle.id = newId;
        newNeighbourSaddles[size++] = saddle;
      }
      neighbourSaddles.clear(size);

      // The peak saddles will already have been consolidated for Id
      // but we can remove the saddle with the neighbour.
      size = 0;
      final FindFociSaddle[] newPeakSaddles = peakSaddles.list;
      for (int i = 0; i < peakSaddles.size; i++) {
        final FindFociSaddle saddle = newPeakSaddles[i];
        if (saddle.id == neighbourPeakId) {
          // Ignore saddle with peak that is being merged
          continue;
        }
        newPeakSaddles[size++] = saddle;
      }
      peakSaddles.clear(size);

      // Merge the saddles
      boolean capacityCheck = true;
      final boolean setMerge = size > 1; // Determine when the sort becomes too expensive

      if (setMerge) {
        final TIntHashSet set = new TIntHashSet(size);
        for (int i = 0; i < size; i++) {
          if (!set.add(newPeakSaddles[i].id)) {
            continue;
          }

          final FindFociSaddle peakSaddle = newPeakSaddles[i];
          final FindFociSaddle neighbourSaddle = findHighestSaddle(neighbourSaddles, peakSaddle.id);
          if (neighbourSaddle == null) {
            // The neighbour peak does not touch this peak, add to the list
            if (capacityCheck) {
              neighbourSaddles.ensureExtraCapacity(size - i);
              capacityCheck = false;
            }
            neighbourSaddles.add(peakSaddle);

            // Check if the saddle is higher
          } else if (neighbourSaddle.value < peakSaddle.value) {
            neighbourSaddle.value = peakSaddle.value;
          }
        }
      } else {
        peakSaddles.sortById();

        int lastId = 0;
        for (int i = 0; i < size; i++) {
          if (lastId == newPeakSaddles[i].id) {
            continue;
          }
          lastId = newPeakSaddles[i].id;

          final FindFociSaddle peakSaddle = newPeakSaddles[i];
          final FindFociSaddle neighbourSaddle = findHighestSaddle(neighbourSaddles, lastId);
          if (neighbourSaddle == null) {
            // The neighbour peak does not touch this peak, add to the list
            if (capacityCheck) {
              neighbourSaddles.ensureExtraCapacity(size - i);
              capacityCheck = false;
            }
            neighbourSaddles.add(peakSaddle);

            // Check if the saddle is higher
          } else if (neighbourSaddle.value < peakSaddle.value) {
            neighbourSaddle.value = peakSaddle.value;
          }
        }
      }

      // Note: For compatibility with the legacy code we have to keep the saddles in order
      // they were created (height then original Id) until they are merged with another peak.
      // Then they are sorted by height and current Id.
      neighbourSaddles.removeDuplicates();
    }

    // Clone the map if we need it for non-contiguous pixel counting
    final int[] peakIdMapClone =
        (updatePeakAboveSaddle && nonContiguous) ? peakIdMap.clone() : null;

    // Map anything previously mapped to this peak to the new neighbour
    for (int i = peakIdMap.length; i-- > 0;) {
      if (peakIdMap[i] == peakId) {
        peakIdMap[i] = neighbourPeakId;
      }
    }

    // Flag this result as merged using the intensity flag. This will be used later to eliminate
    // peaks
    result.totalIntensity = Double.NEGATIVE_INFINITY;
    // Free memory
    peakSaddles.free();

    // Update the count and intensity above the highest neighbour saddle
    if (neighbourResult != null) {
      if (neighbourSaddles.size != 0) {
        final FindFociSaddle newHighestSaddle = neighbourSaddles.list[0];

        // We only need to update if the highest saddle value has been changed
        if (updatePeakAboveSaddle && newHighestSaddle.value == neighbourResult.highestSaddleValue) {
          // The highest saddle for the neighbour is the same.
          // We do not require a full analysis.
          updatePeakAboveSaddle = false;

          // Check if the saddle we just merged was the same height
          if (highestSaddle.value == newHighestSaddle.value) {
            neighbourResult.countAboveSaddle += result.countAboveSaddle;
            neighbourResult.intensityAboveSaddle += result.intensityAboveSaddle;

            // The saddle we just merged was lower.
          } else if (nonContiguous) {
            // In legacy mode we count the intensity of non-contiguous pixels above the saddle.
            // Since the height is the same we only need to count the intensity above the current
            // saddle height for the old peak pixels.
            // This requires a copy of the peakIdMap before it was updated.
            computeIntensityAboveSaddle(maxima, peakIdMapClone, peakId, result,
                newHighestSaddle.value);

            neighbourResult.countAboveSaddle += result.countAboveSaddle;
            neighbourResult.intensityAboveSaddle += result.intensityAboveSaddle;
          } else {
            // This can be ignored as the pixels are non contiguous
          }
        }
        reanalysePeak(maxima, peakIdMap, neighbourPeakId, newHighestSaddle, neighbourResult,
            updatePeakAboveSaddle, nonContiguous, types, pointList);
      } else {
        clearSaddle(neighbourResult);
      }
    }
  }

  /**
   * Reassign the maxima using the peak Id map and recounts all pixels above the saddle height.
   *
   * @param maxima the maxima
   * @param peakIdMap the peak id map
   * @param peakId the peak id
   * @param saddle the saddle
   * @param result the result
   * @param updatePeakAboveSaddle Set to true to update the peak above saddle
   * @param types the types
   * @param pointList the list
   * @return the list
   */
  private int[] reanalysePeak(final int[] maxima, final int[] peakIdMap, final int peakId,
      final FindFociSaddle saddle, final FindFociResult result, final boolean updatePeakAboveSaddle,
      boolean nonContiguous, byte[] types, int[] pointList) {
    if (updatePeakAboveSaddle) {
      if (nonContiguous) {
        computeIntensityAboveSaddle(maxima, peakIdMap, peakId, result, saddle.value);
      } else {
        pointList = analyseContiguousPeak(maxima, types, result, pointList, peakIdMap, peakId);
      }
    }

    result.saddleNeighbourId = peakIdMap[saddle.id];
    result.highestSaddleValue = saddle.value;

    return pointList;
  }

  /**
   * Compute the intensity of the peak above the saddle height.
   *
   * @param maxima the maxima
   * @param peakIdMap the peak id map
   * @param peakId the peak id
   * @param result the result
   * @param saddleHeight the saddle height
   */
  protected void computeIntensityAboveSaddle(final int[] maxima, final int[] peakIdMap,
      final int peakId, final FindFociResult result, final float saddleHeight) {
    int peakSize = 0;
    double peakIntensity = 0;

    // Search using the bounds
    for (int z = result.minz; z < result.maxz; z++) {
      for (int y = result.miny; y < result.maxy; y++) {
        for (int x = result.minx, i = getIndex(result.minx, y, z); x < result.maxx; x++, i++) {
          final int id = maxima[i];
          if (id != 0 && peakIdMap[id] == peakId) {
            // maxima[i] = peakId; // Remap
            final float v = getf(i);
            if (v > saddleHeight) {
              peakIntensity += v;
              peakSize++;
            }
          }
        }
      }
    }

    result.countAboveSaddle = peakSize;
    result.intensityAboveSaddle = peakIntensity;
  }

  /**
   * Reassign the maxima using the peak Id map.
   *
   * @param maxima the maxima
   * @param peakIdMap the peak id map
   */
  protected void reassignMaxima(int[] maxima, int[] peakIdMap) {
    for (int i = maxima.length; i-- > 0;) {
      if (maxima[i] != 0) {
        maxima[i] = peakIdMap[maxima[i]];
      }
    }
  }

  /**
   * Removes any maxima that have pixels that touch the edge.
   *
   * @param resultsArray the results array
   * @param maxima the maxima
   * @return the results array
   */
  private FindFociResult[] removeEdgeMaxima(FindFociResult[] resultsArray, int[] maxima) {
    // Build a look-up table for all the peak IDs
    int maxId = 0;
    for (int i = 0; i < resultsArray.length; i++) {
      final FindFociResult result = resultsArray[i];
      if (maxId < result.id) {
        maxId = result.id;
      }
    }

    final int[] peakIdMap = new int[maxId + 1];
    for (int i = 0; i < peakIdMap.length; i++) {
      peakIdMap[i] = i;
    }

    // Support the ROI bounds used to create the analysis region
    final int lowerx;
    final int upperx;
    final int lowery;
    final int uppery;
    if (bounds != null) {
      lowerx = bounds.x;
      lowery = bounds.y;
      upperx = bounds.x + bounds.width;
      uppery = bounds.y + bounds.height;
    } else {
      lowerx = 0;
      upperx = maxx;
      lowery = 0;
      uppery = maxy;
    }

    // Set the look-up to zero if the peak contains edge pixels
    for (int z = maxz; z-- > 0;) {
      // Look at top and bottom column
      for (int y = uppery, i = getIndex(lowerx, lowery, z), ii = getIndex(upperx - 1, lowery, z);
          y-- > lowery; i += maxx, ii += maxx) {
        peakIdMap[maxima[i]] = 0;
        peakIdMap[maxima[ii]] = 0;
      }
      // Look at top and bottom row
      for (int x = upperx, i = getIndex(lowerx, lowery, z), ii = getIndex(lowerx, uppery - 1, z);
          x-- > lowerx; i++, ii++) {
        peakIdMap[maxima[i]] = 0;
        peakIdMap[maxima[ii]] = 0;
      }
    }

    // Mark maxima to be removed
    for (int i = 0; i < resultsArray.length; i++) {
      final FindFociResult result = resultsArray[i];
      final int peakId = result.id;
      if (peakIdMap[peakId] == 0) {
        result.totalIntensity = Double.NEGATIVE_INFINITY;
      }
    }

    resultsArray = removeFlaggedResults(resultsArray);

    reassignMaxima(maxima, peakIdMap);

    updateSaddleDetails(resultsArray, peakIdMap);

    return resultsArray;
  }

  /**
   * Sort the results using the specified method in descending order.
   *
   * @param resultsArray the results array
   * @param sortMethod the sort method
   * @param stats the stats
   */
  void sortDescResults(FindFociResult[] resultsArray, SortMethod sortMethod,
      FindFociStatistics stats) {
    customSort(resultsArray, sortMethod, stats);
    Arrays.sort(resultsArray, ResultDescComparator.INSTANCE);
  }

  /**
   * Sort the results using the specified method in ascending order.
   *
   * @param resultsArray the results array
   * @param sortMethod the sort method
   * @param stats the stats
   */
  void sortAscResults(FindFociResult[] resultsArray, SortMethod sortMethod,
      FindFociStatistics stats) {
    customSort(resultsArray, sortMethod, stats);
    Arrays.sort(resultsArray, ResultAscComparator.INSTANCE);
  }

  /**
   * Sort the results using the specified method in ascending order.
   *
   * @param resultsArray the results array
   * @param sortMethod the sort method
   * @param stats the stats
   */
  void sortAscResults(List<FindFociResult> resultsArray, SortMethod sortMethod,
      FindFociStatistics stats) {
    customSort(resultsArray, sortMethod, stats);
    Collections.sort(resultsArray, ResultAscComparator.INSTANCE);
  }

  private void customSort(FindFociResult[] resultsArray, SortMethod sortMethod,
      FindFociStatistics stats) {
    switch (sortMethod) {
      case XYZ:
        customSortXyz(resultsArray);
        return;

      case INTENSITY:
      case INTENSITY_MINUS_BACKGROUND:
      case COUNT:
      case MAX_VALUE:
      case AVERAGE_INTENSITY:
      case AVERAGE_INTENSITY_MINUS_BACKGROUND:
      case X:
      case Y:
      case Z:
      case SADDLE_HEIGHT:
      case COUNT_ABOVE_SADDLE:
      case INTENSITY_ABOVE_SADDLE:
      case ABSOLUTE_HEIGHT:
      case RELATIVE_HEIGHT_ABOVE_BACKGROUND:
      case PEAK_ID:
      case INTENSITY_MINUS_MIN:
      case AVERAGE_INTENSITY_MINUS_MIN:
        for (int i = 0; i < resultsArray.length; i++) {
          final FindFociResult result = resultsArray[i];
          result.sortValue = getSortValue(result, sortMethod, stats);
        }
        return;

      default:
        throw new IllegalArgumentException("Unknown sort method method " + sortMethod);
    }
  }

  private void customSort(List<FindFociResult> resultsArray, SortMethod sortMethod,
      FindFociStatistics stats) {
    switch (sortMethod) {
      case XYZ:
        customSortXyz(resultsArray);
        return;

      case INTENSITY:
      case INTENSITY_MINUS_BACKGROUND:
      case COUNT:
      case MAX_VALUE:
      case AVERAGE_INTENSITY:
      case AVERAGE_INTENSITY_MINUS_BACKGROUND:
      case X:
      case Y:
      case Z:
      case SADDLE_HEIGHT:
      case COUNT_ABOVE_SADDLE:
      case INTENSITY_ABOVE_SADDLE:
      case ABSOLUTE_HEIGHT:
      case RELATIVE_HEIGHT_ABOVE_BACKGROUND:
      case PEAK_ID:
      case INTENSITY_MINUS_MIN:
      case AVERAGE_INTENSITY_MINUS_MIN:
        for (int i = 0; i < resultsArray.size(); i++) {
          final FindFociResult result = resultsArray.get(i);
          result.sortValue = getSortValue(result, sortMethod, stats);
        }
        return;

      default:
        throw new IllegalArgumentException("Unknown sort method method " + sortMethod);
    }
  }

  private double getSortValue(FindFociResult result, SortMethod sortMethod,
      FindFociStatistics stats) {
    switch (sortMethod) {
      case INTENSITY:
        return result.totalIntensity;
      case INTENSITY_MINUS_BACKGROUND:
        return result.totalIntensityAboveBackground;
      case COUNT:
        return result.count;
      case MAX_VALUE:
        return result.maxValue;
      case AVERAGE_INTENSITY:
        return result.averageIntensity;
      case AVERAGE_INTENSITY_MINUS_BACKGROUND:
        return result.averageIntensityAboveBackground;
      case X:
        return result.x;
      case Y:
        return result.y;
      case Z:
        return result.z;
      case SADDLE_HEIGHT:
        return result.highestSaddleValue;
      case COUNT_ABOVE_SADDLE:
        return result.countAboveSaddle;
      case INTENSITY_ABOVE_SADDLE:
        return result.intensityAboveSaddle;
      case ABSOLUTE_HEIGHT:
        return getAbsoluteHeight(result, stats.background);
      case RELATIVE_HEIGHT_ABOVE_BACKGROUND:
        final double absoluteHeight = getAbsoluteHeight(result, stats.background);
        return getRelativeHeight(result, stats.background, absoluteHeight);
      case PEAK_ID:
        return result.id;
      case INTENSITY_MINUS_MIN:
        return result.totalIntensityAboveImageMinimum;
      case AVERAGE_INTENSITY_MINUS_MIN:
        return result.averageIntensityAboveImageMinimum;
      default:
        throw new IllegalArgumentException("Unknown sort method method " + sortMethod);
    }
  }

  private void customSortXyz(FindFociResult[] resultsArray) {
    final int a = maxy * maxz;
    final int b = maxz;
    for (int i = 0; i < resultsArray.length; i++) {
      final FindFociResult result = resultsArray[i];
      final int x = result.x;
      final int y = result.y;
      final int z = result.z;
      result.sortValue = (double) x * a + y * b + z;
    }
  }

  private void customSortXyz(List<FindFociResult> resultsArray) {
    final int a = maxy * maxz;
    final int b = maxz;
    for (int i = 0; i < resultsArray.size(); i++) {
      final FindFociResult result = resultsArray.get(i);
      final int x = result.x;
      final int y = result.y;
      final int z = result.z;
      result.sortValue = (double) x * a + y * b + z;
    }
  }

  /**
   * Initialises the global width, height and depth variables. Creates the direction offset tables.
   */
  private void initialise(ImagePlus imp) {
    maxx = imp.getWidth();
    maxy = imp.getHeight();
    maxz = imp.getNSlices();

    // Used to look-up x,y,z from a single index
    maxxByMaxy = maxx * maxy;
    maxxByMaxyByMaxz = maxxByMaxy * maxz;

    xlimit = maxx - 1;
    ylimit = maxy - 1;
    zlimit = maxz - 1;

    // Create the offset table (for single array 3D neighbour comparisons)
    offset = new int[DIR_X_OFFSET.length];
    flatEdge = new boolean[DIR_X_OFFSET.length];
    for (int d = offset.length; d-- > 0;) {
      offset[d] = getIndex(DIR_X_OFFSET[d], DIR_Y_OFFSET[d], DIR_Z_OFFSET[d]);
      flatEdge[d] =
          (Math.abs(DIR_X_OFFSET[d]) + Math.abs(DIR_Y_OFFSET[d]) + Math.abs(DIR_Z_OFFSET[d]) == 1);
    }

    // Create the offset table for half-neighbours (for single array 3D neighbour comparisons)
    offset2 = new int[DIR_X_OFFSET2.length];
    for (int d = offset2.length; d-- > 0;) {
      offset2[d] = getIndex(DIR_X_OFFSET2[d], DIR_Y_OFFSET2[d], DIR_Z_OFFSET2[d]);
    }
  }

  /**
   * Checks if is a 2D image.
   *
   * @return true, if is 2D image
   */
  protected boolean is2D() {
    return maxz == 1;
  }

  /**
   * Return the single index associated with the x,y,z coordinates.
   *
   * @param x the x
   * @param y the y
   * @param z the z
   * @return The index
   */
  protected int getIndex(int x, int y, int z) {
    return (maxxByMaxy) * z + maxx * y + x;
  }

  /**
   * Return the single index associated with the x,y coordinates.
   *
   * @param x the x
   * @param y the y
   * @return The index
   */
  protected int getIndex(int x, int y) {
    return maxx * y + x;
  }

  /**
   * Convert the single index into x,y,z coords, Input array must be length &gt;= 3.
   *
   * @param index the index
   * @param xyz the xyz
   * @return The xyz array
   */
  protected int[] getXyz(int index, int[] xyz) {
    xyz[2] = index / (maxxByMaxy);
    final int mod = index % (maxxByMaxy);
    xyz[1] = mod / maxx;
    xyz[0] = mod % maxx;
    return xyz;
  }

  /**
   * Convert the single index into x,y,z coords, Input array must be length &gt;= 3.
   *
   * @param index the index
   * @param xyz the xyz
   * @return The xyz array
   */
  protected int[] getXy(int index, int[] xyz) {
    final int mod = index % (maxxByMaxy);
    xyz[1] = mod / maxx;
    xyz[0] = mod % maxx;
    return xyz;
  }

  /**
   * Debugging method.
   *
   * @param index the single x,y,z index
   * @return The x coordinate
   */
  protected int getX(int index) {
    final int mod = index % (maxxByMaxy);
    return mod % maxx;
  }

  /**
   * Debugging method.
   *
   * @param index the single x,y,z index
   * @return The x coordinate
   */
  protected int getY(int index) {
    final int mod = index % (maxxByMaxy);
    return mod / maxx;
  }

  /**
   * Debugging method.
   *
   * @param index the single x,y,z index
   * @return The x coordinate
   */
  protected int getZ(int index) {
    return index / (maxxByMaxy);
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
  protected boolean isWithinXy(int x, int y, int direction) {
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
   * Returns whether the neighbour in a given direction is within the image. NOTE: it is assumed
   * that the pixel x,y,z itself is within the image! Uses class variables xlimit, ylimit, zlimit:
   * (dimensions of the image)-1
   *
   * @param x x-coordinate of the pixel that has a neighbour in the given direction
   * @param y y-coordinate of the pixel that has a neighbour in the given direction
   * @param z z-coordinate of the pixel that has a neighbour in the given direction
   * @param direction the direction from the pixel towards the neighbour
   * @return true if the neighbour is within the image (provided that x, y, z is within)
   */
  protected boolean isWithinXyz(int x, int y, int z, int direction) {
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
      case 8:
        return (z > 0 && y > 0);
      case 9:
        return (z > 0 && y > 0 && x < xlimit);
      case 10:
        return (z > 0 && x < xlimit);
      case 11:
        return (z > 0 && y < ylimit && x < xlimit);
      case 12:
        return (z > 0 && y < ylimit);
      case 13:
        return (z > 0 && y < ylimit && x > 0);
      case 14:
        return (z > 0 && x > 0);
      case 15:
        return (z > 0 && y > 0 && x > 0);
      case 16:
        return (z > 0);
      case 17:
        return (z < zlimit && y > 0);
      case 18:
        return (z < zlimit && y > 0 && x < xlimit);
      case 19:
        return (z < zlimit && x < xlimit);
      case 20:
        return (z < zlimit && y < ylimit && x < xlimit);
      case 21:
        return (z < zlimit && y < ylimit);
      case 22:
        return (z < zlimit && y < ylimit && x > 0);
      case 23:
        return (z < zlimit && x > 0);
      case 24:
        return (z < zlimit && y > 0 && x > 0);
      case 25:
        return (z < zlimit);
      default:
        return false;
    }
  }

  /**
   * Returns whether the neighbour in a given direction is within the image when using the
   * half-neighbour look-up table. NOTE: it is assumed that the pixel x,y,z itself is within the
   * image! Uses class variables xlimit, ylimit, zlimit: (dimensions of the image)-1
   *
   * @param x x-coordinate of the pixel that has a neighbour in the given direction
   * @param y y-coordinate of the pixel that has a neighbour in the given direction
   * @param direction the direction from the pixel towards the neighbour
   * @return true if the neighbour is within the image (provided that x, y, z is within)
   */
  protected boolean isWithinXy2(int x, int y, int direction) {
    switch (direction) {
      case 0:
        return (y > 0);
      case 1:
        return (y > 0 && x < xlimit);
      case 2:
        return (x < xlimit);
      case 3:
        return (y < ylimit && x < xlimit);
      default:
        return false;
    }
  }

  /**
   * Returns whether the neighbour in a given direction is within the image when using the
   * half-neighbour look-up table. NOTE: it is assumed that the pixel x,y,z itself is within the
   * image! Uses class variables xlimit, ylimit, zlimit: (dimensions of the image)-1
   *
   * @param x x-coordinate of the pixel that has a neighbour in the given direction
   * @param y y-coordinate of the pixel that has a neighbour in the given direction
   * @param z z-coordinate of the pixel that has a neighbour in the given direction
   * @param direction the direction from the pixel towards the neighbour
   * @return true if the neighbour is within the image (provided that x, y, z is within)
   */
  protected boolean isWithinXyz2(int x, int y, int z, int direction) {
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
        return (z > 0 && y > 0);
      case 5:
        return (z > 0 && y > 0 && x < xlimit);
      case 6:
        return (z > 0 && x < xlimit);
      case 7:
        return (z > 0 && y < ylimit && x < xlimit);
      case 8:
        return (z > 0 && y < ylimit);
      case 9:
        return (z > 0 && y < ylimit && x > 0);
      case 10:
        return (z > 0 && x > 0);
      case 11:
        return (z > 0 && y > 0 && x > 0);
      case 12:
        return (z > 0);
      default:
        return false;
    }
  }

  /**
   * returns whether the neighbour in a given direction is within the image. NOTE: it is assumed
   * that the pixel z itself is within the image! Uses class variables zlimit: (dimensions of the
   * image)-1
   *
   * @param z z-coordinate of the pixel that has a neighbour in the given direction
   * @param direction the direction from the pixel towards the neighbour
   * @return true if the neighbour is within the image (provided that z is within)
   */
  protected boolean isWithinZ(int z, int direction) {
    // z = 0
    if (direction < 8) {
      return true;
    }
    // z = -1
    if (direction < 17) {
      return (z > 0);
    }
    // z = 1
    return z < zlimit;
  }

  /**
   * returns whether the neighbour in a given direction is within the image when using the
   * half-neighbour look-up table. NOTE: it is assumed that the pixel z itself is within the image!
   * Uses class variables zlimit: (dimensions of the image)-1
   *
   * @param z z-coordinate of the pixel that has a neighbour in the given direction
   * @param direction the direction from the pixel towards the neighbour
   * @return true if the neighbour is within the image (provided that z is within)
   */
  protected boolean isWithinZ2(int z, int direction) {
    // z = 0
    if (direction < 4) {
      return true;
    }
    // z = -1
    return (z > 0);
  }

  /**
   * Stores the details of a pixel position.
   */
  protected static class Coordinate {
    /** The index. */
    private final int index;

    /** The id. */
    private int id;

    /** The value. */
    private final float value;

    /**
     * Instantiates a new coordinate.
     *
     * @param index the index
     * @param id the id
     * @param value the value
     */
    public Coordinate(int index, int id, float value) {
      this.index = index;
      this.setId(id);
      this.value = value;
    }

    /**
     * Compare the two coordinates by value, highest first.
     *
     * @param c1 the first coordinate
     * @param c2 the second coordinate
     * @return [-1, 0, 1]
     */
    static int compare(Coordinate c1, Coordinate c2) {
      // Require the sort to rank the highest peak as first.
      // Since sort works in ascending order return a negative for a higher value.
      return Float.compare(c2.value, c1.value);
    }

    /**
     * Gets the index.
     *
     * @return the index
     */
    public int getIndex() {
      return index;
    }

    /**
     * Gets the id.
     *
     * @return the id
     */
    public int getId() {
      return id;
    }

    /**
     * Sets the id.
     *
     * @param id the new id
     */
    public void setId(int id) {
      this.id = id;
    }

    /**
     * Gets the value.
     *
     * @return the value
     */
    public float getValue() {
      return value;
    }
  }

  /**
   * Provides the ability to sort the results arrays in descending order.
   */
  private static class ResultComparator implements Comparator<FindFociResult>, Serializable {
    private static final long serialVersionUID = 1L;

    /** {@inheritDoc} */
    @Override
    public int compare(FindFociResult o1, FindFociResult o2) {
      if (o1.maxValue > o2.maxValue) {
        return -1;
      }
      if (o1.maxValue < o2.maxValue) {
        return 1;
      }
      if (o1.count > o2.count) {
        return -1;
      }
      if (o1.count < o2.count) {
        return 1;
      }
      if (o1.x > o2.x) {
        return 1;
      }
      if (o1.x < o2.x) {
        return -1;
      }
      if (o1.y > o2.y) {
        return 1;
      }
      if (o1.y < o2.y) {
        return -1;
      }
      if (o1.z > o2.z) {
        return 1;
      }
      if (o1.z < o2.z) {
        return -1;
      }
      // This should not happen as two maxima will be in the same position
      throw new IllegalArgumentException("Unable to sort the results");
    }
  }

  /**
   * Provides the ability to sort the results arrays in descending order.
   */
  private static class ResultDescComparator extends ResultComparator {
    private static final long serialVersionUID = 1L;
    static final ResultDescComparator INSTANCE = new ResultDescComparator();

    /** {@inheritDoc} */
    @Override
    public int compare(FindFociResult o1, FindFociResult o2) {
      // Require the highest is first
      if (o1.sortValue > o2.sortValue) {
        return -1;
      }
      if (o1.sortValue < o2.sortValue) {
        return 1;
      }
      // Avoid bad draws
      return super.compare(o1, o2);
    }
  }

  /**
   * Provides the ability to sort the results arrays in ascending order.
   */
  private static class ResultAscComparator extends ResultComparator {
    private static final long serialVersionUID = 1L;
    static final ResultAscComparator INSTANCE = new ResultAscComparator();

    /** {@inheritDoc} */
    @Override
    public int compare(FindFociResult o1, FindFociResult o2) {
      // Require the lowest is first
      if (o1.sortValue > o2.sortValue) {
        return 1;
      }
      if (o1.sortValue < o2.sortValue) {
        return -1;
      }
      // Avoid bad draws. This is an ascending sort so reverse the order.
      return super.compare(o2, o1);
    }
  }

  /**
   * Used to store the number of objects and their original mask value (state).
   */
  static class ObjectAnalysisResult {
    /** The number of objects. */
    int numberOfObjects;

    /** The object state. */
    int[] objectState;

    /** The foci count. */
    int[] fociCount;
  }

  /**
   * Identify all non-zero pixels in the mask image as potential objects. Mark connected pixels with
   * the same value as a single object. For each maxima identify the object and original mask value
   * for the maxima location.
   *
   * @param mask The mask containing objects
   * @param maximaImp the maxima imp
   * @param resultsArray the results array
   * @param createObjectMask the create object mask
   * @param objectAnalysisResult the object analysis result
   * @return The mask image if created
   */
  ImagePlus doObjectAnalysis(ImagePlus mask, ImagePlus maximaImp, List<FindFociResult> resultsArray,
      boolean createObjectMask, ObjectAnalysisResult objectAnalysisResult) {
    if ((resultsArray == null || resultsArray.isEmpty())
        // Allow the analysis to continue if we are creating the object mask or
        // storing the analysis results
        && (!createObjectMask && objectAnalysisResult == null)) {
      return null;
    }

    final int[] maskImage = extractMask(mask);
    if (maskImage == null) {
      return null;
    }

    // Track all the objects. Allow more than the 16-bit capacity for counting objects.
    final int[] objects = new int[maskImage.length];
    int id = 0;
    int[] objectState = new int[10];
    // Label for 2D/3D processing. This is for the mask not the input image!
    final boolean is2D = mask.getNSlices() == 1;

    int[] pointList = new int[100];

    for (int i = 0; i < maskImage.length; i++) {
      if (maskImage[i] != 0 && objects[i] == 0) {
        id++;

        // Store the original mask value of new object
        if (objectState.length <= id) {
          objectState = Arrays.copyOf(objectState, (int) (objectState.length * 1.5));
        }
        objectState[id] = maskImage[i];

        if (is2D) {
          pointList = expandObjectXy(maskImage, objects, i, id, pointList);
        } else {
          pointList = expandObjectXyz(maskImage, objects, i, id, pointList);
        }
      }
    }

    // For each maximum, mark the object and original mask value (state).
    // Count the number of foci in each object.
    final int[] fociCount = new int[id + 1];
    if (resultsArray != null) {
      for (int i = 0; i < resultsArray.size(); i++) {
        final FindFociResult result = resultsArray.get(i);
        final int x = result.x;
        final int y = result.y;
        final int z = (is2D) ? 0 : result.z;
        final int index = getIndex(x, y, z);
        final int objectId = objects[index];
        result.object = objectId;
        result.state = objectState[objectId];
        fociCount[objectId]++;
      }
    }

    // Store the number of objects and their original mask value
    if (objectAnalysisResult != null) {
      objectAnalysisResult.numberOfObjects = id;
      objectAnalysisResult.objectState = objectState;
      objectAnalysisResult.fociCount = fociCount;
    }

    // Show the object mask
    ImagePlus maskImp = null;
    if (createObjectMask) {
      // Check we do not exceed capcity
      if (id > MAXIMA_CAPCITY) {
        log(() -> "The number of objects exceeds the 16-bit capacity used for diplay: "
            + MAXIMA_CAPCITY);
        return null;
      }

      final int n = (is2D) ? 1 : maxz;
      final ImageStack stack = new ImageStack(maxx, maxy, n);
      for (int z = 0, index = 0; z < n; z++) {
        final short[] pixels = new short[maxxByMaxy];
        for (int i = 0; i < pixels.length; i++, index++) {
          pixels[i] = (short) objects[index];
        }
        stack.setPixels(pixels, z + 1);
      }
      // Create a new ImagePlus so that the stack and calibration can be set
      final ImagePlus imp = new ImagePlus("", stack);
      imp.setCalibration(maximaImp.getCalibration());
      maskImp = FindFoci_PlugIn.showImage(imp, mask.getTitle() + " Objects", false);
    }

    return maskImp;
  }

  /**
   * Extract the mask image.
   *
   * @param mask the mask
   * @return The mask image array
   */
  @Nullable
  private int[] extractMask(ImagePlus mask) {
    if (mask == null) {
      return null;
    }

    // Check sizes in X & Y
    if (mask.getWidth() != maxx || mask.getHeight() != maxy
        || (mask.getNSlices() != maxz && mask.getNSlices() != 1)) {
      return null;
    }

    final int maxx_maxy = maxx * maxy;
    final int[] image;
    if (mask.getNSlices() == 1) {
      // Extract a single plane
      final ImageProcessor ipMask = mask.getProcessor();

      image = new int[maxx_maxy];
      for (int i = maxx_maxy; i-- > 0;) {
        image[i] = ipMask.get(i);
      }
    } else {
      final int maxx_maxy_maxz = maxx * maxy * maxz;
      // If the same stack size then process through the image
      final ImageStack stack = mask.getStack();
      final int c = mask.getChannel();
      final int f = mask.getFrame();
      image = new int[maxx_maxy_maxz];
      for (int slice = 1; slice <= mask.getNSlices(); slice++) {
        final int stackIndex = mask.getStackIndex(c, slice, f);
        final ImageProcessor ipMask = stack.getProcessor(stackIndex);

        int index = maxx_maxy * slice;
        for (int i = maxx_maxy; i-- > 0;) {
          index--;
          image[index] = ipMask.get(i);
        }
      }
    }

    return image;
  }

  /**
   * Searches from the specified point to find all coordinates of the same value and assigns them to
   * given maximum ID.
   */
  private int[] expandObjectXyz(final int[] image, final int[] maxima, final int index0,
      final int id, int[] pointList) {
    maxima[index0] = id; // mark first point
    int listI = 0; // index of current search element in the list
    int listLen = 1; // number of elements in the list

    // we create a list of connected points and start the list at the current point
    pointList[listI] = index0;

    final int[] xyz = new int[3];
    final int v0 = image[index0];

    do {
      final int index1 = pointList[listI];
      getXyz(index1, xyz);
      final int x1 = xyz[0];
      final int y1 = xyz[1];
      final int z1 = xyz[2];

      // It is more likely that the z stack will be out-of-bounds.
      // Adopt the xy limit lookup and process z lookup separately

      final boolean isInnerXy = (y1 != 0 && y1 != ylimit) && (x1 != 0 && x1 != xlimit);
      final boolean isInnerXyz = (zlimit == 0) ? isInnerXy : isInnerXy && (z1 != 0 && z1 != zlimit);

      for (int d = 26; d-- > 0;) {
        if (isInnerXyz || (isInnerXy && isWithinZ(z1, d)) || isWithinXyz(x1, y1, z1, d)) {
          final int index2 = index1 + offset[d];
          if (maxima[index2] != 0) {
            // This has been done already, ignore this point
            continue;
          }

          final int v2 = image[index2];

          if (v2 == v0) {
            // Add this to the search
            pointList[listLen++] = index2;
            maxima[index2] = id;
            if (pointList.length == listLen) {
              pointList = Arrays.copyOf(pointList, (int) (listLen * 1.5));
            }
          }
        }
      }

      listI++;

    } while (listI < listLen);

    return pointList;
  }

  /**
   * Searches from the specified point to find all coordinates of the same value and assigns them to
   * given maximum ID.
   */
  private int[] expandObjectXy(final int[] image, final int[] maxima, final int index0,
      final int id, int[] pointList) {
    maxima[index0] = id; // mark first point
    int listI = 0; // index of current search element in the list
    int listLen = 1; // number of elements in the list

    // we create a list of connected points and start the list at the current point
    pointList[listI] = index0;

    final int[] xyz = new int[2];
    final int v0 = image[index0];

    do {
      final int index1 = pointList[listI];
      getXy(index1, xyz);
      final int x1 = xyz[0];
      final int y1 = xyz[1];

      final boolean isInnerXy = (y1 != 0 && y1 != ylimit) && (x1 != 0 && x1 != xlimit);

      for (int d = 8; d-- > 0;) {
        if (isInnerXy || isWithinXy(x1, y1, d)) {
          final int index2 = index1 + offset[d];
          if (maxima[index2] != 0) {
            // This has been done already, ignore this point
            continue;
          }

          final int v2 = image[index2];

          if (v2 == v0) {
            // Add this to the search
            pointList[listLen++] = index2;
            maxima[index2] = id;
            if (pointList.length == listLen) {
              pointList = Arrays.copyOf(pointList, (int) (listLen * 1.5));
            }
          }
        }
      }

      listI++;

    } while (listI < listLen);

    return pointList;
  }

  /**
   * Checks if is float processor.
   *
   * @return true, if is float processor
   */
  public abstract boolean isFloatProcessor();

  /**
   * Checks if showing progress in the ImageJ status bar.
   *
   * @return Set to true if showing progress in the ImageJ status bar.
   */
  public boolean isShowStatus() {
    return showStatus;
  }

  /**
   * Set if showing progress in the ImageJ status bar.
   *
   * @param showStatus Set to true to show progress in the ImageJ status bar
   */
  public void setShowStatus(boolean showStatus) {
    this.showStatus = showStatus;
  }

  private void showStatus(String status) {
    if (showStatus) {
      IJ.showStatus(status);
    }
  }

  /**
   * Gets the logger.
   *
   * @return the logger.
   */
  public Logger getLogger() {
    return logger;
  }

  /**
   * Set the logger. If this is null then logging will go to the ImageJ log window.
   *
   * @param logger the logger to set
   */
  public void setLogger(Logger logger) {
    this.logger = logger;
  }

  private void log(Supplier<String> message) {
    if (logger != null) {
      logger.info(message);
    }
  }

  /**
   * Check if the logger exists.
   *
   * @return True if logging
   */
  private boolean isLogging() {
    return logger != null;
  }
}
