/*-
 * #%L
 * Genome Damage and Stability Centre ImageJ Plugins
 *
 * Software for microscopy image analysis
 * %%
 * Copyright (C) 2011 - 2025 Alex Herbert
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

package uk.ac.sussex.gdsc.ij.utils;

import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.WindowManager;
import ij.plugin.filter.PlugInFilter;
import ij.process.ImageProcessor;
import java.util.ArrayList;
import uk.ac.sussex.gdsc.ij.UsageTracker;

/**
 * Create an average of all the open stacks with the same dimensions and bit-depth as the active
 * stack.
 */
public class StackAverage_PlugIn implements PlugInFilter {
  private ImagePlus imp;

  /** {@inheritDoc} */
  @Override
  public int setup(String arg, ImagePlus imp) {
    UsageTracker.recordPlugin(this.getClass(), arg);
    if (imp == null) {
      IJ.noImage();
      return DONE;
    }
    this.imp = imp;
    return DOES_8G + DOES_16 + DOES_32 + NO_CHANGES;
  }

  /** {@inheritDoc} */
  @Override
  public void run(ImageProcessor ip) {
    final ArrayList<ImagePlus> images = getImages();

    final int bufferSize = imp.getWidth() * imp.getHeight();
    final int count = images.size();

    final ImageStack result = createResult();

    // Add all the images - Process each stack slice individually
    for (int n = imp.getStackSize(); n > 0; n--) {
      // Sum all the images
      final double[] sum = new double[bufferSize];
      for (final ImagePlus imp2 : images) {
        final ImageProcessor ip2 = imp2.getStack().getProcessor(n);
        for (int i = sum.length; i-- > 0;) {
          sum[i] += ip2.get(i);
        }
      }

      // Average
      final ImageProcessor ip2 = result.getProcessor(n);
      for (int i = sum.length; i-- > 0;) {
        ip2.set(i, (int) (sum[i] / count));
      }
    }

    // Show result
    new ImagePlus("Stack Average", result).show();
  }

  private ArrayList<ImagePlus> getImages() {
    final int[] dimensions = imp.getDimensions();
    final int bitDepth = imp.getBitDepth();

    // Build a list of the images
    final int[] wList = uk.ac.sussex.gdsc.core.ij.ImageJUtils.getIdList();

    final ArrayList<ImagePlus> images = new ArrayList<>(wList.length);

    for (int i = 0; i < wList.length; i++) {
      final ImagePlus imp2 = WindowManager.getImage(wList[i]);
      if (imp2 != null && (!imp2.getTitle().startsWith("Stack Average")
          && sameDimensions(dimensions, imp2.getDimensions()) && bitDepth == imp2.getBitDepth())) {
        images.add(imp2);
      }
    }

    return images;
  }

  private static boolean sameDimensions(int[] dimensions, int[] dimensions2) {
    for (int i = dimensions.length; i-- > 0;) {
      if (dimensions[i] != dimensions2[i]) {
        return false;
      }
    }
    return true;
  }

  private ImageStack createResult() {
    final int width = imp.getWidth();
    final int height = imp.getHeight();

    final ImageStack inStack = imp.getImageStack();
    final ImageStack outStack = new ImageStack(width, height);

    for (int n = inStack.getSize(); n > 0; n--) {
      outStack.addSlice(null, inStack.getProcessor(n).createProcessor(width, height));
    }

    return outStack;
  }
}
