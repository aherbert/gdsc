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

import ij.ImagePlus;

/**
 * Interface describing the methods for the FindFoci algorithm to find the peak intensity regions of
 * an image.
 */
public interface FindFociProcessor {
  /**
   * Find the maxima of an image.
   *
   * <p>Local maxima are processed in order, highest first. Regions are grown from local maxima
   * until a saddle point is found or the stopping criteria are met (based on pixel intensity). If a
   * peak does not meet the peak criteria (min size) it is absorbed into the highest peak that
   * touches it (if a neighbour peak exists). Only a single iteration is performed and consequently
   * peak absorption could produce sub-optimal results due to greedy peak growth.
   *
   * <p>Peak expansion stopping criteria are defined using the search method parameter.
   *
   * @param imp the image
   * @param mask A mask image used to define the region to search for peaks
   * @param processorOptions the processor options
   * @return The results (null if processing was cancelled)
   */
  FindFociResults findMaxima(ImagePlus imp, ImagePlus mask,
      FindFociProcessorOptions processorOptions);
}
