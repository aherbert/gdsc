/*-
 * #%L
 * Genome Damage and Stability Centre ImageJ Plugins
 *
 * Software for microscopy image analysis
 * %%
 * Copyright (C) 2011 - 2022 Alex Herbert
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

import java.util.EnumSet;
import java.util.Set;
import uk.ac.sussex.gdsc.core.threshold.AutoThreshold;
import uk.ac.sussex.gdsc.core.threshold.AutoThreshold.Method;
import uk.ac.sussex.gdsc.core.utils.SimpleArrayUtils;
import uk.ac.sussex.gdsc.core.utils.ValidationUtils;
import uk.ac.sussex.gdsc.threshold.MultiOtsuThreshold_PlugIn;

/**
 * Provides the options for the {@link FindFociProcessor}.
 */
public class FindFociProcessorOptions {

  /** The message when the default value for an enum is null. */
  private static final String MSG_DEFAULT_IS_NULL = "Default value is null";

  /** The background method. */
  private BackgroundMethod backgroundMethod;

  /** The background parameter. */
  private double backgroundParameter;

  /** The threshold method. */
  private ThresholdMethod thresholdMethod;

  /** The statistics method. */
  private StatisticsMethod statisticsMethod; // enum this

  /** The search method. */
  private SearchMethod searchMethod;

  /** The search parameter. */
  private double searchParameter;

  /** The min size. */
  private int minSize;

  /** The max size. */
  private int maxSize;

  /** The peak method. */
  private PeakMethod peakMethod;

  /** The peak parameter. */
  private double peakParameter;

  /** The sort method. */
  private SortMethod sortMethod;

  /** The max peaks. */
  private int maxPeaks;

  /** The show mask. */
  private MaskMethod maskMethod;

  /** The gaussian blur. */
  private double gaussianBlur;

  /** The centre method. */
  private CentreMethod centreMethod;

  /** The centre parameter. */
  private double centreParameter;

  /** The fraction parameter. */
  private double fractionParameter;

  /** The options. */
  private Set<AlgorithmOption> options;

  /**
   * The background method.
   */
  public enum BackgroundMethod {
    /**
     * The background intensity is set using the input value.
     */
    ABSOLUTE("Absolute"),
    /**
     * The background intensity is set using the mean.
     */
    MEAN("Mean"),
    /**
     * The background intensity is set as the threshold value field times the standard deviation
     * plus the mean.
     */
    STD_DEV_ABOVE_MEAN("Std.Dev above mean"),
    /**
     * The background intensity is set using the input auto-threshold method.
     */
    AUTO_THRESHOLD("Auto threshold"),
    /**
     * The background intensity is set as the minimum image intensity within the ROI or mask.
     */
    MIN_MASK_OR_ROI("Min Mask/ROI"),
    /**
     * The background intensity is set as 0. Equivalent to using {@link #ABSOLUTE} with a value of
     * zero.
     */
    NONE("None");

    /** The Constant values. */
    private static final BackgroundMethod[] values;

    /** The Constant descriptions. */
    private static final String[] descriptions;

    static {
      values = values();
      descriptions = new String[values.length];
      for (int i = 0; i < values.length; i++) {
        descriptions[i] = values[i].getDescription();
      }
    }

    /** The description. */
    private final String description;

    /**
     * Instantiates a new background method.
     *
     * @param description the description
     */
    BackgroundMethod(String description) {
      this.description = description;
    }

    /**
     * Gets the description.
     *
     * @return the description
     */
    public String getDescription() {
      return description;
    }

    @Override
    public String toString() {
      return getDescription();
    }

    /**
     * Gets the descriptions for all of the values.
     *
     * @return the descriptions
     */
    public static String[] getDescriptions() {
      return descriptions.clone();
    }

    /**
     * Create from the description.
     *
     * @param description the description
     * @return the background method
     * @see #getDescription()
     */
    public static BackgroundMethod fromDescription(String description) {
      for (final BackgroundMethod value : values) {
        if (value.getDescription().equals(description)) {
          return value;
        }
      }
      return null;
    }

    /**
     * Create from the enum {@link #ordinal()}.
     *
     * @param ordinal the ordinal
     * @return the background method
     * @throws IndexOutOfBoundsException if the index is invalid
     */
    public static BackgroundMethod fromOrdinal(int ordinal) {
      return values[ValidationUtils.checkIndex(ordinal, values)];
    }

    /**
     * Create from the enum {@link #ordinal()}.
     *
     * @param ordinal the ordinal
     * @param defaultValue the default value (must not be null)
     * @return the background method
     * @throws NullPointerException if the default value is null
     */
    public static BackgroundMethod fromOrdinal(int ordinal, BackgroundMethod defaultValue) {
      return SimpleArrayUtils.getIndex(ordinal, values,
          ValidationUtils.checkNotNull(defaultValue, MSG_DEFAULT_IS_NULL));
    }
  }

  /**
   * The threshold method.
   *
   * <p>This provides the methods defined by
   * {@link uk.ac.sussex.gdsc.core.threshold.AutoThreshold.Method} but without the methods using the
   * mean since they are explicit options in the FindFoci algorithm.
   *
   * <p>Extra methods are provided for multi-level Otsu thresholding.
   */
  public enum ThresholdMethod {
    /** The none method. */
    NONE("None", Method.NONE),
    /** The default method. */
    DEFAULT("Default", Method.DEFAULT),
    /** The huang method. */
    HUANG("Huang", Method.HUANG),
    /** The intermodes method. */
    INTERMODES("Intermodes", Method.INTERMODES),
    /** The iso data method. */
    ISO_DATA("IsoData", Method.ISO_DATA),
    /** The li method. */
    LI("Li", Method.LI),
    /** The max entropy method. */
    MAX_ENTROPY("MaxEntropy", Method.MAX_ENTROPY),
    /** The min error i method. */
    MIN_ERROR_I("MinError(I)", Method.MIN_ERROR_I),
    /** The minimum method. */
    MINIMUM("Minimum", Method.MINIMUM),
    /** The moments method. */
    MOMENTS("Moments", Method.MOMENTS),
    /** The Otsu method. */
    OTSU("Otsu", Method.OTSU),
    /**
     * The multi-level Otsu method using 3 levels. The threshold is the value of the lowest level.
     */
    OTSU_3_LEVEL("Otsu_3_Level", null),
    /**
     * The multi-level Otsu method using 4 levels. The threshold is the value of the lowest level.
     */
    OTSU_4_LEVEL("Otsu_4_Level", null),
    /** The percentile method. */
    PERCENTILE("Percentile", Method.PERCENTILE),
    /** The renyi entropy method. */
    RENYI_ENTROPY("RenyiEntropy", Method.RENYI_ENTROPY),
    /** The shanbhag method. */
    SHANBHAG("Shanbhag", Method.SHANBHAG),
    /** The triangle method. */
    TRIANGLE("Triangle", Method.TRIANGLE),
    /** The yen method. */
    YEN("Yen", Method.YEN);

    /** The Constant values. */
    private static final ThresholdMethod[] values;

    /** The Constant descriptions. */
    private static final String[] descriptions;

    static {
      values = values();
      descriptions = new String[values.length];
      for (int i = 0; i < values.length; i++) {
        descriptions[i] = values[i].getDescription();
      }
    }

    /** The description. */
    private final String description;

    /** The method. */
    private final Method method;

    /**
     * Instantiates a new threshold method.
     *
     * @param description the description
     * @param method the method
     */
    ThresholdMethod(String description, Method method) {
      this.description = description;
      this.method = method;
    }

    /**
     * Gets the description.
     *
     * @return the description
     */
    public String getDescription() {
      return description;
    }

    @Override
    public String toString() {
      return getDescription();
    }

    /**
     * Gets the descriptions for all of the values.
     *
     * @return the descriptions
     */
    public static String[] getDescriptions() {
      return descriptions.clone();
    }

    /**
     * Create from the description.
     *
     * @param description the description
     * @return the threshold method
     * @see #getDescription()
     */
    public static ThresholdMethod fromDescription(String description) {
      for (final ThresholdMethod value : values) {
        if (value.getDescription().equals(description)) {
          return value;
        }
      }
      return null;
    }

    /**
     * Create from the enum {@link #ordinal()}.
     *
     * @param ordinal the ordinal
     * @return the threshold method
     * @throws IndexOutOfBoundsException if the index is invalid
     */
    public static ThresholdMethod fromOrdinal(int ordinal) {
      return values[ValidationUtils.checkIndex(ordinal, values)];
    }

    /**
     * Create from the enum {@link #ordinal()}.
     *
     * @param ordinal the ordinal
     * @param defaultValue the default value (must not be null)
     * @return the threshold method
     * @throws NullPointerException if the default value is null
     */
    public static ThresholdMethod fromOrdinal(int ordinal, ThresholdMethod defaultValue) {
      return SimpleArrayUtils.getIndex(ordinal, values,
          ValidationUtils.checkNotNull(defaultValue, MSG_DEFAULT_IS_NULL));
    }

    /**
     * Gets the threshold for the histogram data.
     *
     * @param histogram the histogram
     * @return the threshold
     */
    public int getThreshold(int[] histogram) {
      if (method != null) {
        return AutoThreshold.getThreshold(method, histogram);
      }
      // Currently this must be the multi-otsu threshold
      final MultiOtsuThreshold_PlugIn multi = new MultiOtsuThreshold_PlugIn();
      // Configure the algorithm to ignore zero
      final int zeroValue = histogram[0];
      histogram[0] = 0;
      final int level = description.indexOf('3') != -1 ? 3 : 4;
      // Run the algorithm
      final int[] threshold = multi.calculateThresholds(histogram, level);
      histogram[0] = zeroValue;
      return threshold[1];
    }
  }

  /**
   * The statistics method.
   */
  public enum StatisticsMethod {
    /**
     * Calculate the statistics using all the pixels (both inside and outside the ROI/Mask).
     */
    ALL("Both"),
    /**
     * Calculate the statistics using the pixels inside the ROI/Mask.
     */
    INSIDE("Inside"),
    /**
     * Calculate the statistics using the pixels outside the ROI/Mask.
     */
    OUTSIDE("Outside"),;

    /** The Constant values. */
    private static final StatisticsMethod[] values;

    /** The Constant descriptions. */
    private static final String[] descriptions;

    static {
      values = values();
      descriptions = new String[values.length];
      for (int i = 0; i < values.length; i++) {
        descriptions[i] = values[i].getDescription();
      }
    }

    /** The description. */
    private final String description;

    /**
     * Instantiates a new statistics method.
     *
     * @param description the description
     */
    StatisticsMethod(String description) {
      this.description = description;
    }

    /**
     * Gets the description.
     *
     * @return the description
     */
    public String getDescription() {
      return description;
    }

    @Override
    public String toString() {
      return getDescription();
    }

    /**
     * Gets the descriptions for all of the values.
     *
     * @return the descriptions
     */
    public static String[] getDescriptions() {
      return descriptions.clone();
    }

    /**
     * Create from the description.
     *
     * @param description the description
     * @return the statistics method
     * @see #getDescription()
     */
    public static StatisticsMethod fromDescription(String description) {
      for (final StatisticsMethod value : values) {
        if (value.getDescription().equals(description)) {
          return value;
        }
      }
      return null;
    }

    /**
     * Create from the enum {@link #ordinal()}.
     *
     * @param ordinal the ordinal
     * @return the statistics method
     * @throws IndexOutOfBoundsException if the index is invalid
     */
    public static StatisticsMethod fromOrdinal(int ordinal) {
      return values[ValidationUtils.checkIndex(ordinal, values)];
    }

    /**
     * Create from the enum {@link #ordinal()}.
     *
     * @param ordinal the ordinal
     * @param defaultValue the default value (must not be null)
     * @return the statistics method
     * @throws NullPointerException if the default value is null
     */
    public static StatisticsMethod fromOrdinal(int ordinal, StatisticsMethod defaultValue) {
      return SimpleArrayUtils.getIndex(ordinal, values,
          ValidationUtils.checkNotNull(defaultValue, MSG_DEFAULT_IS_NULL));
    }
  }

  /**
   * The search method.
   */
  public enum SearchMethod {
    /**
     * A region is grown until the intensity drops below the background.
     */
    ABOVE_BACKGROUND("Above background"),
    /**
     * A region is grown until the intensity drops to: background + (parameter value) * (peak
     * intensity - background).
     */
    FRACTION_OF_PEAK_MINUS_BACKGROUND("Fraction of peak - background"),
    /**
     * A region is grown until the intensity drops to halfway between the value at the peak (the
     * seed for the region) and the background level. This is equivalent to using the "fraction of
     * peak - background" option with the threshold value set to 0.5.
     */
    HALF_PEAK_VALUE("Half peak value");

    /** The Constant values. */
    private static final SearchMethod[] values;

    /** The Constant descriptions. */
    private static final String[] descriptions;

    static {
      values = values();
      descriptions = new String[values.length];
      for (int i = 0; i < values.length; i++) {
        descriptions[i] = values[i].getDescription();
      }
    }

    /** The description. */
    private final String description;

    /**
     * Instantiates a new search method.
     *
     * @param description the description
     */
    SearchMethod(String description) {
      this.description = description;
    }

    /**
     * Gets the description.
     *
     * @return the description
     */
    public String getDescription() {
      return description;
    }

    @Override
    public String toString() {
      return getDescription();
    }

    /**
     * Gets the descriptions for all of the values.
     *
     * @return the descriptions
     */
    public static String[] getDescriptions() {
      return descriptions.clone();
    }

    /**
     * Create from the description.
     *
     * @param description the description
     * @return the search method
     * @see #getDescription()
     */
    public static SearchMethod fromDescription(String description) {
      for (final SearchMethod value : values) {
        if (value.getDescription().equals(description)) {
          return value;
        }
      }
      return null;
    }

    /**
     * Create from the enum {@link #ordinal()}.
     *
     * @param ordinal the ordinal
     * @return the search method
     * @throws IndexOutOfBoundsException if the index is invalid
     */
    public static SearchMethod fromOrdinal(int ordinal) {
      return values[ValidationUtils.checkIndex(ordinal, values)];
    }

    /**
     * Create from the enum {@link #ordinal()}.
     *
     * @param ordinal the ordinal
     * @param defaultValue the default value (must not be null)
     * @return the search method
     * @throws NullPointerException if the default value is null
     */
    public static SearchMethod fromOrdinal(int ordinal, SearchMethod defaultValue) {
      return SimpleArrayUtils.getIndex(ordinal, values,
          ValidationUtils.checkNotNull(defaultValue, MSG_DEFAULT_IS_NULL));
    }
  }

  /**
   * The peak method.
   */
  public enum PeakMethod {
    /**
     * The peak must be an absolute height above the highest saddle point.
     */
    ABSOLUTE("Absolute height"),
    /**
     * The peak must be a relative height above the highest saddle point. The height is calculated
     * as peak intensity * threshold value. The threshold value should be between 0 and 1.
     */
    RELATIVE("Relative height"),
    /**
     * The peak must be a relative height above the highest saddle point. The height is calculated
     * as (peak intensity - background) * threshold value. The threshold value should be between 0
     * and 1.
     */
    RELATIVE_ABOVE_BACKGROUND("Relative above background");

    /** The Constant values. */
    private static final PeakMethod[] values;

    /** The Constant descriptions. */
    private static final String[] descriptions;

    static {
      values = values();
      descriptions = new String[values.length];
      for (int i = 0; i < values.length; i++) {
        descriptions[i] = values[i].getDescription();
      }
    }

    /** The description. */
    private final String description;

    /**
     * Instantiates a new peak method.
     *
     * @param description the description
     */
    PeakMethod(String description) {
      this.description = description;
    }

    /**
     * Gets the description.
     *
     * @return the description
     */
    public String getDescription() {
      return description;
    }

    @Override
    public String toString() {
      return getDescription();
    }

    /**
     * Gets the descriptions for all of the values.
     *
     * @return the descriptions
     */
    public static String[] getDescriptions() {
      return descriptions.clone();
    }

    /**
     * Create from the description.
     *
     * @param description the description
     * @return the peak method
     * @see #getDescription()
     */
    public static PeakMethod fromDescription(String description) {
      for (final PeakMethod value : values) {
        if (value.getDescription().equals(description)) {
          return value;
        }
      }
      return null;
    }

    /**
     * Create from the enum {@link #ordinal()}.
     *
     * @param ordinal the ordinal
     * @return the peak method
     * @throws IndexOutOfBoundsException if the index is invalid
     */
    public static PeakMethod fromOrdinal(int ordinal) {
      return values[ValidationUtils.checkIndex(ordinal, values)];
    }

    /**
     * Create from the enum {@link #ordinal()}.
     *
     * @param ordinal the ordinal
     * @param defaultValue the default value (must not be null)
     * @return the peak method
     * @throws NullPointerException if the default value is null
     */
    public static PeakMethod fromOrdinal(int ordinal, PeakMethod defaultValue) {
      return SimpleArrayUtils.getIndex(ordinal, values,
          ValidationUtils.checkNotNull(defaultValue, MSG_DEFAULT_IS_NULL));
    }
  }

  /**
   * The mask method.
   */
  public enum MaskMethod {
    /**
     * Create an output mask with values corresponding to the peak Ids.
     */
    NONE("None"),
    /**
     * Create an output mask with values corresponding to the peak Ids.
     */
    PEAKS("Peaks"),
    /**
     * Create an output mask with each peak region thresholded using the auto-threshold method.
     */
    THRESHOLD("Threshold"),
    /**
     * Create an output mask showing only pixels above the peak's highest saddle value.
     */
    PEAKS_ABOVE_SADDLE("Peaks above saddle"),
    /**
     * Create an output mask with each peak region thresholded using the auto-threshold method using
     * only pixels above the peak's highest saddle value.
     */
    THRESHOLD_ABOVE_SADDLE("Threshold above saddle"),
    /**
     * Create an output mask showing only the pixels contributing to a cumulative fraction of the
     * peak's total intensity.
     */
    FRACTION_OF_INTENSITY("Fraction of intensity"),
    /**
     * Create an output mask showing only pixels above a fraction of the peak's highest value.
     */
    FRACTION_OF_HEIGHT("Fraction height above background");

    /** The Constant values. */
    private static final MaskMethod[] values;

    /** The Constant descriptions. */
    private static final String[] descriptions;

    static {
      values = values();
      descriptions = new String[values.length];
      for (int i = 0; i < values.length; i++) {
        descriptions[i] = values[i].getDescription();
      }
    }

    /** The description. */
    private final String description;

    /**
     * Instantiates a new mask method.
     *
     * @param description the description
     */
    MaskMethod(String description) {
      this.description = description;
    }

    /**
     * Gets the description.
     *
     * @return the description
     */
    public String getDescription() {
      return description;
    }

    @Override
    public String toString() {
      return getDescription();
    }

    /**
     * Gets the descriptions for all of the values.
     *
     * @return the descriptions
     */
    public static String[] getDescriptions() {
      return descriptions.clone();
    }

    /**
     * Create from the description.
     *
     * @param description the description
     * @return the mask method
     * @see #getDescription()
     */
    public static MaskMethod fromDescription(String description) {
      for (final MaskMethod value : values) {
        if (value.getDescription().equals(description)) {
          return value;
        }
      }
      return null;
    }

    /**
     * Create from the enum {@link #ordinal()}.
     *
     * @param ordinal the ordinal
     * @return the mask method
     * @throws IndexOutOfBoundsException if the index is invalid
     */
    public static MaskMethod fromOrdinal(int ordinal) {
      return values[ValidationUtils.checkIndex(ordinal, values)];
    }

    /**
     * Create from the enum {@link #ordinal()}.
     *
     * @param ordinal the ordinal
     * @param defaultValue the default value (must not be null)
     * @return the mask method
     * @throws NullPointerException if the default value is null
     */
    public static MaskMethod fromOrdinal(int ordinal, MaskMethod defaultValue) {
      return SimpleArrayUtils.getIndex(ordinal, values,
          ValidationUtils.checkNotNull(defaultValue, MSG_DEFAULT_IS_NULL));
    }
  }

  /**
   * The sort method.
   */
  public enum SortMethod {
    /**
     * Sort the peaks using the pixel count.
     */
    COUNT("Size"),
    /**
     * Sort the peaks using the sum of pixel intensity.
     */
    INTENSITY("Total intensity"),
    /**
     * Sort the peaks using the maximum pixel value.
     */
    MAX_VALUE("Max value"),
    /**
     * Sort the peaks using the average pixel value.
     */
    AVERAGE_INTENSITY("Average intensity"),
    /**
     * Sort the peaks using the sum of pixel intensity (minus the background).
     */
    INTENSITY_MINUS_BACKGROUND("Total intensity minus background"),
    /**
     * Sort the peaks using the average pixel value (minus the background).
     */
    AVERAGE_INTENSITY_MINUS_BACKGROUND("Average intensity minus background"),
    /**
     * Sort the peaks using the X coordinate.
     */
    X("X"),
    /**
     * Sort the peaks using the Y coordinate.
     */
    Y("Y"),
    /**
     * Sort the peaks using the Z coordinate.
     */
    Z("Z"),
    /**
     * Sort the peaks using the saddle height.
     */
    SADDLE_HEIGHT("Saddle height"),
    /**
     * Sort the peaks using the pixel count above the saddle height.
     */
    COUNT_ABOVE_SADDLE("Size above saddle"),
    /**
     * Sort the peaks using the sum of pixel intensity above the saddle height.
     */
    INTENSITY_ABOVE_SADDLE("Intensity above saddle"),
    /**
     * Sort the peaks using the absolute height above the highest saddle.
     */
    ABSOLUTE_HEIGHT("Absolute height"),
    /**
     * Sort the peaks using the relative height above the background.
     */
    RELATIVE_HEIGHT_ABOVE_BACKGROUND("Absolute height above background"),
    /**
     * Sort the peaks using the peak Id.
     */
    PEAK_ID("Peak ID"),
    /**
     * Sort the peaks using the XYZ coordinates (in order).
     */
    XYZ("XYZ"),
    /**
     * Sort the peaks using the sum of pixel intensity (minus the minimum in the image).
     */
    INTENSITY_MINUS_MIN("Total intensity minus min"),
    /**
     * Sort the peaks using the average pixel value (minus the minimum in the image).
     */
    AVERAGE_INTENSITY_MINUS_MIN("Average intensity minus min");

    /** The Constant values. */
    private static final SortMethod[] values;

    /** The Constant descriptions. */
    private static final String[] descriptions;

    static {
      values = values();
      descriptions = new String[values.length];
      for (int i = 0; i < values.length; i++) {
        descriptions[i] = values[i].getDescription();
      }
    }

    /** The description. */
    private final String description;

    /**
     * Instantiates a new sort method.
     *
     * @param description the description
     */
    SortMethod(String description) {
      this.description = description;
    }

    /**
     * Gets the description.
     *
     * @return the description
     */
    public String getDescription() {
      return description;
    }

    @Override
    public String toString() {
      return getDescription();
    }

    /**
     * Gets the descriptions for all of the values.
     *
     * @return the descriptions
     */
    public static String[] getDescriptions() {
      return descriptions.clone();
    }

    /**
     * Create from the description.
     *
     * @param description the description
     * @return the sort method
     * @see #getDescription()
     */
    public static SortMethod fromDescription(String description) {
      for (final SortMethod value : values) {
        if (value.getDescription().equals(description)) {
          return value;
        }
      }
      return null;
    }

    /**
     * Create from the enum {@link #ordinal()}.
     *
     * @param ordinal the ordinal
     * @return the sort method
     * @throws IndexOutOfBoundsException if the index is invalid
     */
    public static SortMethod fromOrdinal(int ordinal) {
      return values[ValidationUtils.checkIndex(ordinal, values)];
    }

    /**
     * Create from the enum {@link #ordinal()}.
     *
     * @param ordinal the ordinal
     * @param defaultValue the default value (must not be null)
     * @return the sort method
     * @throws NullPointerException if the default value is null
     */
    public static SortMethod fromOrdinal(int ordinal, SortMethod defaultValue) {
      return SimpleArrayUtils.getIndex(ordinal, values,
          ValidationUtils.checkNotNull(defaultValue, MSG_DEFAULT_IS_NULL));
    }
  }

  /**
   * The centre method.
   */
  public enum CentreMethod {
    /**
     * Define the peak centre using the highest pixel value of the search image (default). In the
     * case of multiple highest value pixels, the closest pixel to the geometric mean of their
     * coordinates is used.
     */
    MAX_VALUE_SEARCH("Max value (search image)"),
    /**
     * Re-map peak centre using the highest pixel value of the original image.
     */
    MAX_VALUE_ORIGINAL("Max value (original image)"),
    /**
     * Re-map peak centre using the peak centre of mass (COM) around the search image. The COM is
     * computed within a given volume of the highest pixel value. Only pixels above the saddle
     * height are used to compute the fit. The volume is specified using 2xN+1 where N is the centre
     * parameter.
     */
    CENTRE_OF_MASS_SEARCH("Centre of mass (search image)"),
    /**
     * Re-map peak centre using the peak centre of mass (COM) around the original image.
     */
    CENTRE_OF_MASS_ORIGINAL("Centre of mass (original image)"),
    /**
     * Re-map peak centre using a Gaussian fit on the search image. Only pixels above the saddle
     * height are used to compute the fit. The fit is performed in 2D using a projection along the
     * z-axis. If the centre parameter is 1 a maximum intensity projection is used; else an average
     * intensity project is used. The z-coordinate is computed using the centre of mass along the
     * projection axis located at the xy centre.
     */
    GAUSSIAN_SEARCH("Gaussian (search image)"),
    /**
     * Re-map peak centre using a Gaussian fit on the original image.
     */
    GAUSSIAN_ORIGINAL("Gaussian (original image)");

    /** The Constant values. */
    private static final CentreMethod[] values;

    /** The Constant descriptions. */
    private static final String[] descriptions;

    static {
      values = values();
      descriptions = new String[values.length];
      for (int i = 0; i < values.length; i++) {
        descriptions[i] = values[i].getDescription();
      }
    }

    /** The description. */
    private final String description;

    /**
     * Instantiates a new centre method.
     *
     * @param description the description
     */
    CentreMethod(String description) {
      this.description = description;
    }

    /**
     * Gets the description.
     *
     * @return the description
     */
    public String getDescription() {
      return description;
    }

    @Override
    public String toString() {
      return getDescription();
    }

    /**
     * Gets the descriptions for all of the values.
     *
     * @return the descriptions
     */
    public static String[] getDescriptions() {
      return descriptions.clone();
    }

    /**
     * Create from the description.
     *
     * @param description the description
     * @return the centre method
     * @see #getDescription()
     */
    public static CentreMethod fromDescription(String description) {
      for (final CentreMethod value : values) {
        if (value.getDescription().equals(description)) {
          return value;
        }
      }
      return null;
    }

    /**
     * Create from the enum {@link #ordinal()}.
     *
     * @param ordinal the ordinal
     * @return the centre method
     * @throws IndexOutOfBoundsException if the index is invalid
     */
    public static CentreMethod fromOrdinal(int ordinal) {
      return values[ValidationUtils.checkIndex(ordinal, values)];
    }

    /**
     * Create from the enum {@link #ordinal()}.
     *
     * @param ordinal the ordinal
     * @param defaultValue the default value (must not be null)
     * @return the centre method
     * @throws NullPointerException if the default value is null
     */
    public static CentreMethod fromOrdinal(int ordinal, CentreMethod defaultValue) {
      return SimpleArrayUtils.getIndex(ordinal, values,
          ValidationUtils.checkNotNull(defaultValue, MSG_DEFAULT_IS_NULL));
    }
  }

  /**
   * The algorithm option.
   */
  public enum AlgorithmOption {
    /**
     * Apply the minimum size criteria to the peak size above the highest saddle point.
     */
    MINIMUM_ABOVE_SADDLE("Minimum above saddle"),
    /**
     * Mark each peak location on the output mask using a single pixel dot. The dot uses the
     * brightest available value, which is the number of maxima + 1.
     *
     * <p>If not enabled the mask has the same value for all pixels of the same peak.
     */
    OUTPUT_MASK_PEAK_DOTS("Show mask maxima as dots"),
    /**
     * Remove any maxima that touch the edge of the image.
     */
    REMOVE_EDGE_MAXIMA("Remove edge maxima"),
    /**
     * The peak above the highest saddle point must be contiguous. The legacy algorithm used
     * non-contiguous pixels above the saddle.
     */
    CONTIGUOUS_ABOVE_SADDLE("Connected above saddle");

    /** The Constant values. */
    private static final AlgorithmOption[] values;

    /** The Constant descriptions. */
    private static final String[] descriptions;

    static {
      values = values();
      descriptions = new String[values.length];
      for (int i = 0; i < values.length; i++) {
        descriptions[i] = values[i].getDescription();
      }
    }

    /** The description. */
    private final String description;

    /**
     * Instantiates a new algorithm option.
     *
     * @param description the description
     */
    AlgorithmOption(String description) {
      this.description = description;
    }

    /**
     * Gets the description.
     *
     * @return the description
     */
    public String getDescription() {
      return description;
    }

    @Override
    public String toString() {
      return getDescription();
    }

    /**
     * Gets the descriptions for all of the values.
     *
     * @return the descriptions
     */
    public static String[] getDescriptions() {
      return descriptions.clone();
    }

    /**
     * Create from the description.
     *
     * @param description the description
     * @return the algorithm option
     * @see #getDescription()
     */
    public static AlgorithmOption fromDescription(String description) {
      for (final AlgorithmOption value : values) {
        if (value.getDescription().equals(description)) {
          return value;
        }
      }
      return null;
    }

    /**
     * Create from the enum {@link #ordinal()}.
     *
     * @param ordinal the ordinal
     * @return the algorithm option
     * @throws IndexOutOfBoundsException if the index is invalid
     */
    public static AlgorithmOption fromOrdinal(int ordinal) {
      return values[ValidationUtils.checkIndex(ordinal, values)];
    }

    /**
     * Create from the enum {@link #ordinal()}.
     *
     * @param ordinal the ordinal
     * @param defaultValue the default value (must not be null)
     * @return the algorithm option
     * @throws NullPointerException if the default value is null
     */
    public static AlgorithmOption fromOrdinal(int ordinal, AlgorithmOption defaultValue) {
      return SimpleArrayUtils.getIndex(ordinal, values,
          ValidationUtils.checkNotNull(defaultValue, MSG_DEFAULT_IS_NULL));
    }
  }

  /**
   * Default constructor.
   */
  public FindFociProcessorOptions() {
    this(false);
  }

  /**
   * Constructor with option to set all values to uninitialised. Enums will have the first value in
   * the enumeration. Primitive types will be their Java default (zero).
   *
   * @param reset the reset flag
   */
  public FindFociProcessorOptions(boolean reset) {
    if (reset) {
      // Set empty options
      backgroundMethod = BackgroundMethod.ABSOLUTE;
      thresholdMethod = ThresholdMethod.NONE;
      statisticsMethod = StatisticsMethod.ALL;
      searchMethod = SearchMethod.ABOVE_BACKGROUND;
      peakMethod = PeakMethod.ABSOLUTE;
      sortMethod = SortMethod.COUNT;
      maskMethod = MaskMethod.NONE;
      centreMethod = CentreMethod.MAX_VALUE_SEARCH;
      options = EnumSet.noneOf(AlgorithmOption.class);
    } else {
      // Set default options
      backgroundMethod = BackgroundMethod.AUTO_THRESHOLD;
      backgroundParameter = 3;
      thresholdMethod = ThresholdMethod.OTSU;
      statisticsMethod = StatisticsMethod.ALL;
      searchMethod = SearchMethod.ABOVE_BACKGROUND;
      searchParameter = 0.3;
      minSize = 5;
      peakMethod = PeakMethod.RELATIVE_ABOVE_BACKGROUND;
      peakParameter = 0.5;
      sortMethod = SortMethod.INTENSITY;
      maxPeaks = 50;
      maskMethod = MaskMethod.PEAKS_ABOVE_SADDLE;
      centreMethod = CentreMethod.MAX_VALUE_SEARCH;
      centreParameter = 2;
      fractionParameter = 0.5;
      options = EnumSet.of(AlgorithmOption.MINIMUM_ABOVE_SADDLE);
    }
  }

  /**
   * Copy constructor.
   *
   * @param source the source
   */
  public FindFociProcessorOptions(FindFociProcessorOptions source) {
    // Copy properties
    backgroundMethod = source.backgroundMethod;
    backgroundParameter = source.backgroundParameter;
    thresholdMethod = source.thresholdMethod;
    statisticsMethod = source.statisticsMethod;
    searchMethod = source.searchMethod;
    searchParameter = source.searchParameter;
    minSize = source.minSize;
    maxSize = source.maxSize;
    peakMethod = source.peakMethod;
    peakParameter = source.peakParameter;
    sortMethod = source.sortMethod;
    maxPeaks = source.maxPeaks;
    maskMethod = source.maskMethod;
    gaussianBlur = source.gaussianBlur;
    centreMethod = source.centreMethod;
    centreParameter = source.centreParameter;
    fractionParameter = source.fractionParameter;
    options = EnumSet.copyOf(source.options);
  }

  /**
   * Create a copy.
   *
   * @return A copy
   */
  public FindFociProcessorOptions copy() {
    return new FindFociProcessorOptions(this);
  }

  /**
   * Sets the background method.
   *
   * @param backgroundMethod the new background method
   * @throws NullPointerException if the argument is null
   */
  public void setBackgroundMethod(BackgroundMethod backgroundMethod) {
    this.backgroundMethod = ValidationUtils.checkNotNull(backgroundMethod);
  }

  /**
   * Gets the background method.
   *
   * @return the background method
   */
  public BackgroundMethod getBackgroundMethod() {
    return backgroundMethod;
  }

  /**
   * Sets the background parameter.
   *
   * @param backgroundParameter the new background parameter
   */
  public void setBackgroundParameter(double backgroundParameter) {
    this.backgroundParameter = backgroundParameter;
  }

  /**
   * Gets the background parameter.
   *
   * @return the background parameter
   */
  public double getBackgroundParameter() {
    return backgroundParameter;
  }

  /**
   * Sets the threshold method.
   *
   * @param thresholdMethod the new threshold method
   * @throws NullPointerException if the argument is null
   */
  public void setThresholdMethod(ThresholdMethod thresholdMethod) {
    this.thresholdMethod = ValidationUtils.checkNotNull(thresholdMethod);
  }

  /**
   * Gets the threshold method.
   *
   * @return the threshold method
   */
  public ThresholdMethod getThresholdMethod() {
    return thresholdMethod;
  }

  /**
   * Sets the statistics mode.
   *
   * @param statisticsMethod the new statistics mode
   * @throws NullPointerException if the argument is null
   */
  public void setStatisticsMethod(StatisticsMethod statisticsMethod) {
    this.statisticsMethod = ValidationUtils.checkNotNull(statisticsMethod);
  }

  /**
   * Gets the statistics mode.
   *
   * @return the statistics method
   */
  public StatisticsMethod getStatisticsMethod() {
    return statisticsMethod;
  }

  /**
   * Sets the search method.
   *
   * @param searchMethod the new search method
   * @throws NullPointerException if the argument is null
   */
  public void setSearchMethod(SearchMethod searchMethod) {
    this.searchMethod = ValidationUtils.checkNotNull(searchMethod);
  }

  /**
   * Gets the search method.
   *
   * @return the search method
   */
  public SearchMethod getSearchMethod() {
    return searchMethod;
  }

  /**
   * Sets the search parameter.
   *
   * @param searchParameter the new search parameter
   */
  public void setSearchParameter(double searchParameter) {
    this.searchParameter = searchParameter;
  }

  /**
   * Gets the search parameter.
   *
   * @return the search parameter
   */
  public double getSearchParameter() {
    return searchParameter;
  }

  /**
   * Sets the min size.
   *
   * @param minSize the new min size
   */
  public void setMinSize(int minSize) {
    this.minSize = minSize;
  }

  /**
   * Gets the min size.
   *
   * @return the min size
   */
  public int getMinSize() {
    return minSize;
  }

  /**
   * Sets the max size.
   *
   * <p>This is used as a filter on the final results after peak merging. Thus it does not prevent
   * merging of two peaks to create one peak if they are above the maximum size. Instead the peaks
   * will be merged as normal and any peak will be removed from the final results if they are above
   * the maximum size.
   *
   * <p>Set to zero to ignore.
   *
   * @param maxSize the new max size
   */
  public void setMaxSize(int maxSize) {
    this.maxSize = maxSize;
  }

  /**
   * Gets the max size.
   *
   * @return the max size
   */
  public int getMaxSize() {
    return maxSize;
  }

  /**
   * Sets the peak method.
   *
   * @param peakMethod the new peak method
   * @throws NullPointerException if the argument is null
   */
  public void setPeakMethod(PeakMethod peakMethod) {
    this.peakMethod = ValidationUtils.checkNotNull(peakMethod);
  }

  /**
   * Gets the peak method.
   *
   * @return the peak method
   */
  public PeakMethod getPeakMethod() {
    return peakMethod;
  }

  /**
   * Sets the peak parameter.
   *
   * @param peakParameter the new peak parameter
   */
  public void setPeakParameter(double peakParameter) {
    this.peakParameter = peakParameter;
  }

  /**
   * Gets the peak parameter.
   *
   * @return the peak parameter
   */
  public double getPeakParameter() {
    return peakParameter;
  }

  /**
   * Sets the sort method.
   *
   * @param sortMethod the new sort method
   * @throws NullPointerException if the argument is null
   */
  public void setSortMethod(SortMethod sortMethod) {
    this.sortMethod = ValidationUtils.checkNotNull(sortMethod);
  }

  /**
   * Gets the sort method.
   *
   * @return the sort method
   */
  public SortMethod getSortMethod() {
    return sortMethod;
  }

  /**
   * Sets the max peaks.
   *
   * @param maxPeaks the new max peaks
   */
  public void setMaxPeaks(int maxPeaks) {
    this.maxPeaks = maxPeaks;
  }

  /**
   * Gets the max peaks.
   *
   * @return the max peaks
   */
  public int getMaxPeaks() {
    return maxPeaks;
  }

  /**
   * Sets the mask method.
   *
   * @param maskMethod the new mask method
   * @throws NullPointerException if the argument is null
   */
  public void setMaskMethod(MaskMethod maskMethod) {
    this.maskMethod = ValidationUtils.checkNotNull(maskMethod);
  }

  /**
   * Gets the mask method.
   *
   * @return the mask method
   */
  public MaskMethod getMaskMethod() {
    return maskMethod;
  }

  /**
   * Sets the gaussian blur.
   *
   * @param gaussianBlur the new gaussian blur
   */
  public void setGaussianBlur(double gaussianBlur) {
    this.gaussianBlur = gaussianBlur;
  }

  /**
   * Gets the gaussian blur.
   *
   * @return the gaussian blur
   */
  public double getGaussianBlur() {
    return gaussianBlur;
  }

  /**
   * Sets the centre method.
   *
   * @param centreMethod the new centre method
   * @throws NullPointerException if the argument is null
   */
  public void setCentreMethod(CentreMethod centreMethod) {
    this.centreMethod = ValidationUtils.checkNotNull(centreMethod);
  }

  /**
   * Gets the centre method.
   *
   * @return the centre method
   */
  public CentreMethod getCentreMethod() {
    return centreMethod;
  }

  /**
   * Sets the centre parameter.
   *
   * @param centreParameter the new centre parameter
   */
  public void setCentreParameter(double centreParameter) {
    this.centreParameter = centreParameter;
  }

  /**
   * Gets the centre parameter.
   *
   * @return the centre parameter
   */
  public double getCentreParameter() {
    return centreParameter;
  }

  /**
   * Sets the fraction parameter.
   *
   * @param fractionParameter the new fraction parameter
   */
  public void setFractionParameter(double fractionParameter) {
    this.fractionParameter = fractionParameter;
  }

  /**
   * Gets the fraction parameter.
   *
   * @return the fraction parameter
   */
  public double getFractionParameter() {
    return fractionParameter;
  }

  /**
   * Gets the options. This is a reference to the options (not a copy).
   *
   * @return the options
   */
  public Set<AlgorithmOption> getOptions() {
    return options;
  }

  /**
   * Sets the options.
   *
   * @param options the new options
   * @throws NullPointerException if the argument is null
   */
  public void setOptions(Set<AlgorithmOption> options) {
    this.options = ValidationUtils.checkNotNull(options);
  }

  /**
   * Set the algorithm option.
   *
   * @param option the option
   * @param enabled true if enabled
   * @return true, if the option was changed
   */
  public boolean setOption(AlgorithmOption option, boolean enabled) {
    if (enabled) {
      return options.add(option);
    }
    return options.remove(option);
  }

  /**
   * Checks if the algorithm option is enabled.
   *
   * @param option the option
   * @return true if enabled
   */
  public boolean isOption(AlgorithmOption option) {
    return options.contains(option);
  }
}
