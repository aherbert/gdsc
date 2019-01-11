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

import uk.ac.sussex.gdsc.foci.FindFociOptimiser_PlugIn;
import uk.ac.sussex.gdsc.foci.FindFoci_PlugIn;
import uk.ac.sussex.gdsc.foci.model.FindFociModel;

import ij.ImagePlus;
import ij.WindowManager;
import ij.gui.Roi;
import ij.plugin.frame.Recorder;

import java.util.ArrayList;
import java.util.List;

/**
 * Allows ImageJ to run the {@link uk.ac.sussex.gdsc.foci.FindFoci_PlugIn} algorithm.
 */
public class OptimiserController extends FindFociController implements Runnable {
  private final FindFociOptimiser_PlugIn optimiser = new FindFociOptimiser_PlugIn();

  /**
   * Instantiates a new optimiser controller.
   *
   * @param model the model
   */
  public OptimiserController(FindFociModel model) {
    super(model);
  }

  /** {@inheritDoc} */
  @Override
  public int getImageCount() {
    return 0;
  }

  /** {@inheritDoc} */
  @Override
  public void updateImageList() {
    final int noOfImages = WindowManager.getImageCount();
    final List<String> imageList = new ArrayList<>(noOfImages);
    for (final int id : uk.ac.sussex.gdsc.core.ij.ImageJUtils.getIdList()) {
      final ImagePlus imp = WindowManager.getImage(id);

      // Image must be 8-bit/16-bit/32-bit && only contains XYZ dimensions
      if (imp != null
          && (imp.getType() == ImagePlus.GRAY8 || imp.getType() == ImagePlus.GRAY16
              || imp.getType() == ImagePlus.GRAY32)
          && (imp.getNChannels() == 1 && imp.getNFrames() == 1)) {
        final Roi roi = imp.getRoi();
        if (roi == null || roi.getType() != Roi.POINT) {
          continue;
        }

        // Check it is not one the result images
        final String imageTitle = imp.getTitle();
        if (!imageTitle.endsWith(FindFoci_PlugIn.TITLE) && !imageTitle.endsWith("clone")
            && !imageTitle.endsWith(" TP") && !imageTitle.endsWith(" FP")
            && !imageTitle.endsWith(" FN")) {
          imageList.add(imageTitle);
        }
      }
    }

    model.setImageList(imageList);
  }

  /** {@inheritDoc} */
  @Override
  public void run() {
    final ImagePlus imp = WindowManager.getImage(model.getSelectedImage());

    // Ensure the optimiser is recorded by the Macro recorder
    Recorder.setCommand("FindFoci Optimiser");
    optimiser.run(imp);
    Recorder.saveCommand();
  }

  /** {@inheritDoc} */
  @Override
  public void preview() {
    // Ignore
  }

  /** {@inheritDoc} */
  @Override
  public void endPreview() {
    // Ignore
  }

  /** {@inheritDoc} */
  @Override
  public int[] getImageLimits(int[] limits) {
    return null;
  }
}
