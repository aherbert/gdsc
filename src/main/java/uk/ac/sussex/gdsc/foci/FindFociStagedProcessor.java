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
import uk.ac.sussex.gdsc.core.annotation.Nullable;
import uk.ac.sussex.gdsc.foci.FindFociProcessorOptions.AlgorithmOption;

/**
 * Interface describing separate stages for the FindFoci algorithm to find the peak intensity
 * regions of an image.
 *
 * <p>Note: The stages produce intermediate results can be cached for efficient results generation
 * when parameters are updated.
 */
public interface FindFociStagedProcessor extends FindFociProcessor {

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
  ImagePlus blur(ImagePlus imp, double blur);

  /**
   * This method is the initial stage of the
   * {@link #findMaxima(ImagePlus, ImagePlus, FindFociProcessorOptions)} routine.
   *
   * <p>The method initialises the system up to the point of background generation. The result
   * object can be copied and passed multiple times to later methods for further processing with
   * different options.
   *
   * @param originalImp the original image
   * @param imp the image after the blur has been applied ( see {@link #blur(ImagePlus, double)} ).
   *        This allows the blur to be pre-computed.
   * @param mask A mask image used to define the region to search for peaks
   * @param processorOptions the processor options
   * @return the initialisation results
   */
  FindFociInitResults findMaximaInit(ImagePlus originalImp, ImagePlus imp, ImagePlus mask,
      FindFociProcessorOptions processorOptions);

  /**
   * Copies the initial results for use in findMaxima staged methods. Only the elements that are
   * destructively modified by the findMaxima staged methods are duplicated. The rest are shallow
   * copied.
   *
   * @param initResults The original initialised results object
   * @param clonedInitResults A previously cloned initialised results object (avoid reallocating
   *        memory). Can be null.
   * @return the copy
   */
  FindFociInitResults copyForStagedProcessing(FindFociInitResults initResults,
      @Nullable FindFociInitResults clonedInitResults);

  /**
   * This method is the second stage of the
   * {@link #findMaxima(ImagePlus, ImagePlus, FindFociProcessorOptions)} routine.
   *
   * <p>The method performs the search for candidate maxima.
   *
   * @param initResults The output from
   *        {@link #findMaximaInit(ImagePlus, ImagePlus, ImagePlus, FindFociProcessorOptions)}.
   *        Contents are destructively modified so should be cloned before input.
   * @param processorOptions the processor options
   * @return the find foci search results
   */
  FindFociSearchResults findMaximaSearch(FindFociInitResults initResults,
      FindFociProcessorOptions processorOptions);

  /**
   * This method is the third stage of the
   * {@link #findMaxima(ImagePlus, ImagePlus, FindFociProcessorOptions)} routine.
   *
   * <p>The method performs the merging process of candidate maxima using the chosen peak method.
   *
   * @param initResults The output from
   *        {@link #findMaximaInit(ImagePlus, ImagePlus, ImagePlus, FindFociProcessorOptions)}.
   * @param searchResults The output from
   *        {@link #findMaximaSearch(FindFociInitResults, FindFociProcessorOptions) }. Contents are
   *        unchanged.
   * @param processorOptions the processor options
   * @return the find foci merge results
   */
  FindFociMergeTempResults findMaximaMergePeak(FindFociInitResults initResults,
      FindFociSearchResults searchResults, FindFociProcessorOptions processorOptions);

  /**
   * This method is the fourth stage of the
   * {@link #findMaxima(ImagePlus, ImagePlus, FindFociProcessorOptions)} routine.
   *
   * <p>The method performs the merging process of candidate maxima using the chosen minimum size.
   *
   * @param initResults The output from
   *        {@link #findMaximaInit(ImagePlus, ImagePlus, ImagePlus, FindFociProcessorOptions)}.
   * @param mergeResults The output from
   *        {@link #findMaximaMergePeak(FindFociInitResults, FindFociSearchResults, FindFociProcessorOptions)}.
   * @param processorOptions the processor options
   * @return the find foci merge results
   */
  FindFociMergeTempResults findMaximaMergeSize(FindFociInitResults initResults,
      FindFociMergeTempResults mergeResults, FindFociProcessorOptions processorOptions);

  /**
   * This method is the fifth stage of the
   * {@link #findMaxima(ImagePlus, ImagePlus, FindFociProcessorOptions)} routine.
   *
   * <p>The method performs the finalisation of the merging process of candidate maxima using the
   * minimum size, {@link AlgorithmOption#MINIMUM_ABOVE_SADDLE} and blur.
   *
   * @param initResults The output from
   *        {@link #findMaximaInit(ImagePlus, ImagePlus, ImagePlus, FindFociProcessorOptions)}.
   *        Contents are destructively modified so should be cloned before input.
   * @param mergeResults The output from
   *        {@link #findMaximaMergeSize(FindFociInitResults, FindFociMergeTempResults, FindFociProcessorOptions)}.
   * @param processorOptions the processor options
   * @return the find foci merge results
   */
  FindFociMergeResults findMaximaMergeFinal(FindFociInitResults initResults,
      FindFociMergeTempResults mergeResults, FindFociProcessorOptions processorOptions);

  /**
   * This method is the final stage of the
   * {@link #findMaxima(ImagePlus, ImagePlus, FindFociProcessorOptions)} routine without mask
   * generation.
   *
   * <p>The method performs the finalisation of the results using the max peaks, sort index, and
   * centre method.
   *
   * <p>Note: This method is intended for benchmarking where the mask is not required. For mask
   * generation use
   * {@link #findMaximaPrelimResults(FindFociInitResults, FindFociMergeResults, FindFociProcessorOptions)}
   * and
   * {@link #findMaximaMaskResults(FindFociInitResults, FindFociMergeResults, FindFociPrelimResults, FindFociProcessorOptions)}.
   *
   * @param initResults The output from
   *        {@link #findMaximaInit(ImagePlus, ImagePlus, ImagePlus, FindFociProcessorOptions)}.
   *        Contents are destructively modified so should be cloned before input.
   * @param mergeResults The output from
   *        {@link #findMaximaMergeFinal(FindFociInitResults, FindFociMergeTempResults, FindFociProcessorOptions)}
   *        Contents are unchanged.
   * @param processorOptions the processor options
   * @return the find foci results
   */
  FindFociResults findMaximaResults(FindFociInitResults initResults,
      FindFociMergeResults mergeResults, FindFociProcessorOptions processorOptions);

  /**
   * This method is the preliminary results stage of the
   * {@link #findMaxima(ImagePlus, ImagePlus, FindFociProcessorOptions)} routine.
   *
   * <p>The method performs the finalisation of the results using the max peaks, sort index, and
   * centre method.
   *
   * @param initResults The output from
   *        {@link #findMaximaInit(ImagePlus, ImagePlus, ImagePlus, FindFociProcessorOptions)}.
   *        Contents are destructively modified so should be cloned before input.
   * @param mergeResults The output from
   *        {@link #findMaximaMergeFinal(FindFociInitResults, FindFociMergeTempResults, FindFociProcessorOptions)}
   *        Contents are unchanged.
   * @param processorOptions the processor options
   * @return the find foci results
   */
  FindFociPrelimResults findMaximaPrelimResults(FindFociInitResults initResults,
      FindFociMergeResults mergeResults, FindFociProcessorOptions processorOptions);

  /**
   * This method is the second results stage of the
   * {@link #findMaxima(ImagePlus, ImagePlus, FindFociProcessorOptions)} routine.
   *
   * <p>The method performs the finalisation of the results using the max peaks, sort index, and
   * centre method.
   *
   * @param initResults The output from
   *        {@link #findMaximaInit(ImagePlus, ImagePlus, ImagePlus, FindFociProcessorOptions)}.
   *        Contents are destructively modified so should be cloned before input.
   * @param mergeResults The output from
   *        {@link #findMaximaMergeFinal(FindFociInitResults, FindFociMergeTempResults, FindFociProcessorOptions)}
   *        Contents are unchanged.
   * @param prelimResults The output from
   *        {@link #findMaximaPrelimResults(FindFociInitResults, FindFociMergeResults, FindFociProcessorOptions)}
   *        Contents are unchanged.
   * @param processorOptions the processor options
   * @return the find foci results
   */
  FindFociResults findMaximaMaskResults(FindFociInitResults initResults,
      FindFociMergeResults mergeResults, FindFociPrelimResults prelimResults,
      FindFociProcessorOptions processorOptions);
}
