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

package uk.ac.sussex.gdsc.foci.controller;

import uk.ac.sussex.gdsc.foci.model.FindFociModel;

import org.apache.commons.rng.UniformRandomProvider;
import org.apache.commons.rng.simple.RandomSource;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * Dummy controller that provides stub functionality to
 * {@link uk.ac.sussex.gdsc.foci.gui.FindFociView}.
 */
public class NullController extends FindFociController {
  private static Logger logger = Logger.getLogger(NullController.class.getName());

  private int lowerLimit = 15;
  private int upperLimit = 220;
  private int updateCounter = 0;

  private static class LazyRng {
    static final UniformRandomProvider rng = RandomSource.create(RandomSource.SPLIT_MIX_64);
  }

  /**
   * Instantiates a new null controller.
   *
   * @param model the model
   */
  public NullController(FindFociModel model) {
    super(model);
  }

  /** {@inheritDoc} */
  @Override
  public int getImageCount() {
    return 3;
  }

  /** {@inheritDoc} */
  @Override
  public void updateImageList() {
    // Note: Increment the updateCounter to ensure the list is refreshed
    updateCounter++;

    final List<String> imageList = new ArrayList<>();
    imageList.add(updateCounter + " : One");
    imageList.add(updateCounter + " : Two");
    imageList.add(updateCounter + " : Three");
    model.setImageList(imageList);

    // Make up some random limits
    final int base = 25;
    lowerLimit = LazyRng.rng.nextInt(base);
    upperLimit = LazyRng.rng.nextInt(255 - base) + base;
  }

  /** {@inheritDoc} */
  @Override
  public void run() {
    model.setUnchanged();
  }

  /** {@inheritDoc} */
  @Override
  public void preview() {
    logger.info("FindFoci Preview");
  }

  /** {@inheritDoc} */
  @Override
  public void endPreview() {
    logger.info("FindFoci EndPreview");
  }

  /** {@inheritDoc} */
  @Override
  public int[] getImageLimits(int[] limits) {
    logger.info("getImageLimits");
    if (limits == null || limits.length < 2) {
      limits = new int[2];
    }
    limits[0] = lowerLimit;
    limits[1] = upperLimit;
    return limits;
  }
}
