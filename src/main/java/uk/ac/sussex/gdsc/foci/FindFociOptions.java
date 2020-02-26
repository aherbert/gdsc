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

package uk.ac.sussex.gdsc.foci;

import java.util.EnumSet;
import java.util.Set;
import uk.ac.sussex.gdsc.core.annotation.Nullable;
import uk.ac.sussex.gdsc.core.utils.SimpleArrayUtils;
import uk.ac.sussex.gdsc.core.utils.ValidationUtils;

/**
 * Provides the options for the {@link FindFoci_PlugIn}.
 */
public class FindFociOptions {

  /** The message when the default value for an enum is null. */
  private static final String MSG_DEFAULT_IS_NULL = "Default value is null";

  /** The results directory. */
  private String resultsDirectory;

  /** The options. */
  private Set<OutputOption> options;

  /**
   * The output option for reporting results.
   */
  public enum OutputOption {
    /**
     * Output the peak statistics to a results window.
     */
    RESULTS_TABLE("Show table"),
    /**
     * Mark the peak locations on the input ImagePlus using point ROIs.
     */
    ROI_SELECTION("Mark maxima"),
    /**
     * Mark the peak locations on the mask ImagePlus using point ROIs.
     */
    MASK_ROI_SELECTION("Mark peak maxima"),
    /**
     * Output the peak statistics to a results window.
     */
    CLEAR_RESULTS_TABLE("Clear table"),
    /**
     * When marking the peak locations on the input ImagePlus using point ROIs hide the number
     * labels.
     */
    HIDE_LABELS("Hide labels"),
    /**
     * Overlay the mask on the image.
     */
    OVERLAY_MASK("Overlay mask"),
    /**
     * Overlay the ROI points on the image (preserving any current ROI).
     */
    ROI_USING_OVERLAY("Mark using overlay"),
    /**
     * Identify all connected non-zero mask pixels with the same value as objects and label the
     * maxima as belonging to each object.
     */
    OBJECT_ANALYSIS("Object analysis"),
    /**
     * Show the object mask calculated during the object analysis.
     */
    SHOW_OBJECT_MASK("Show object mask"),
    /**
     * Save the results to memory (allows other plugins to obtain the results).
     */
    SAVE_TO_MEMORY("Save to memory");

    /** The Constant values. */
    private static final OutputOption[] values;

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
    OutputOption(String description) {
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
    public static OutputOption fromDescription(String description) {
      for (final OutputOption value : values) {
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
    public static OutputOption fromOrdinal(int ordinal) {
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
    public static OutputOption fromOrdinal(int ordinal, OutputOption defaultValue) {
      return SimpleArrayUtils.getIndex(ordinal, values,
          ValidationUtils.checkNotNull(defaultValue, MSG_DEFAULT_IS_NULL));
    }
  }

  /**
   * Default constructor.
   */
  public FindFociOptions() {
    this(false);
  }

  /**
   * Constructor with option to set all values to uninitialised.
   *
   * @param reset the reset flag
   */
  public FindFociOptions(boolean reset) {
    if (reset) {
      // Set empty options
      options = EnumSet.noneOf(OutputOption.class);
    } else {
      // Set default options ...
      options = EnumSet.of(OutputOption.OVERLAY_MASK, OutputOption.RESULTS_TABLE,
          OutputOption.CLEAR_RESULTS_TABLE, OutputOption.ROI_SELECTION);
    }
  }

  /**
   * Copy constructor.
   *
   * @param source the source
   */
  public FindFociOptions(FindFociOptions source) {
    // Copy properties
    resultsDirectory = source.resultsDirectory;
    options = EnumSet.copyOf(source.options);
  }

  /**
   * Create a copy.
   *
   * @return A copy
   */
  public FindFociOptions copy() {
    return new FindFociOptions(this);
  }


  /**
   * Gets the results directory.
   *
   * @return the results directory
   */
  public String getResultsDirectory() {
    return resultsDirectory;
  }

  /**
   * Sets the results directory.
   *
   * @param resultsDirectory the new results directory (can be null)
   */
  public void setResultsDirectory(@Nullable String resultsDirectory) {
    this.resultsDirectory = resultsDirectory;
  }

  /**
   * Gets the options. This is a reference to the options (not a copy).
   *
   * @return the options
   */
  public Set<OutputOption> getOptions() {
    return options;
  }

  /**
   * Sets the options.
   *
   * @param options the new options
   * @throws NullPointerException if the argument is null
   */
  public void setOptions(Set<OutputOption> options) {
    this.options = ValidationUtils.checkNotNull(options);
  }

  /**
   * Set the algorithm option.
   *
   * @param option the option
   * @param enabled true if enabled
   * @return true, if the option was changed
   */
  public boolean setOption(OutputOption option, boolean enabled) {
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
  public boolean isOption(OutputOption option) {
    return options.contains(option);
  }
}
