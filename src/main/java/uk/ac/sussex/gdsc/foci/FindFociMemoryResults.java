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

import ij.ImagePlus;
import ij.measure.Calibration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Contains the results of the FindFoci algorithm saved to memory.
 */
public class FindFociMemoryResults {
  /** The image Id. */
  private final int imageId;

  /** The image calibration. */
  private final Calibration calibration;

  /** The results. */
  private final List<FindFociResult> results;

  /**
   * Instantiates a new find foci result.
   *
   * @param imp the image
   * @param results the results
   */
  public FindFociMemoryResults(ImagePlus imp, List<FindFociResult> results) {
    this.imageId = imp.getID();
    this.calibration = imp.getCalibration();
    this.results = results;
  }

  /**
   * Instantiates a new find foci results.
   *
   * @param imp the image
   * @param results the results
   */
  public FindFociMemoryResults(ImagePlus imp, FindFociResult[] results) {
    this(imp, (results == null) ? new ArrayList<FindFociResult>(0) : Arrays.asList(results));
  }

  /**
   * Copy constructor.
   *
   * @param source the source
   */
  private FindFociMemoryResults(FindFociMemoryResults source) {
    this.imageId = source.imageId;
    this.calibration = source.calibration;
    this.results = source.results;
  }

  /**
   * Returns a shallow copy of this set of results.
   *
   * @return the find foci results
   */
  public FindFociMemoryResults copy() {
    return new FindFociMemoryResults(this);
  }

  /**
   * Gets the image id.
   *
   * @return the image id
   */
  public int getImageId() {
    return imageId;
  }

  /**
   * Gets the calibration.
   *
   * @return the calibration
   */
  public Calibration getCalibration() {
    return calibration;
  }

  /**
   * Gets the results.
   *
   * @return the results
   */
  public List<FindFociResult> getResults() {
    return results;
  }
}
