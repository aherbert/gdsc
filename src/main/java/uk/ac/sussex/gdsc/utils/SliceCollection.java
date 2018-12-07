/*-
 * #%L
 * Genome Damage and Stability Centre ImageJ Plugins
 *
 * Software for microscopy image analysis
 * %%
 * Copyright (C) 2011 - 2018 Alex Herbert
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

package uk.ac.sussex.gdsc.utils;

import uk.ac.sussex.gdsc.core.ij.PixelUtils;

import gnu.trove.list.array.TIntArrayList;

import ij.ImagePlus;
import ij.ImageStack;

/**
 * Provides functionality to process a collection of slices from an image stack into a sub-stack.
 *
 * <p>Contains utility functionality to generate a name for the sub-stack based on the channel,
 * z-slice or frame that is shared by the sub-stack.
 */
public class SliceCollection {
  private final int indexC;
  private final int indexZ;
  private final int indexT;
  private TIntArrayList slices = new TIntArrayList();

  private String sliceName;

  /**
   * Instantiates a new slice collection.
   *
   * <p>Set an index to zero to ignore it. The indexes will be used to generate the slice collection
   * name.
   *
   * @param indexC The channel index
   * @param indexZ The z-slice index
   * @param indexT the frame index
   */
  public SliceCollection(int indexC, int indexZ, int indexT) {
    this.indexC = indexC;
    this.indexZ = indexZ;
    this.indexT = indexT;
  }

  /**
   * Adds the stack index to the collection.
   *
   * <p>Add an stack index from an image using {@link ImagePlus#getStackIndex(int, int, int)}.
   *
   * @param stackIndex the stack index
   */
  public void add(int stackIndex) {
    slices.add(stackIndex);
  }

  /**
   * Gets the stack index at the specified offset.
   *
   * @param offset the offset
   * @return the stack index
   */
  public int get(int offset) {
    return slices.get(offset);
  }

  /**
   * Get the size of the collection.
   *
   * @return the size
   */
  public int size() {
    return slices.size();
  }

  /**
   * Gets the slice name.
   *
   * @return the slice name
   */
  public String getSliceName() {
    if (sliceName == null) {
      final StringBuilder sb = new StringBuilder();
      if (getCIndex() != 0) {
        sb.append("c").append(getCIndex());
      }
      if (getZIndex() != 0) {
        sb.append("z").append(getZIndex());
      }
      if (getTIndex() != 0) {
        sb.append("t").append(getTIndex());
      }
      sliceName = sb.toString();
    }
    return sliceName;
  }

  /**
   * Extracts the configured slices from the image into a sub-stack.
   *
   * <p>Ignores the ROI as only the raw pixel data is copied.
   *
   * @param stack the source stack
   * @return the image stack
   */
  public ImageStack createStack(ImageStack stack) {
    ImageStack imageStack = new ImageStack(stack.getWidth(), stack.getHeight());
    slices.forEach(slice -> {
      imageStack.addSlice(Integer.toString(slice), PixelUtils.copyPixels(stack.getPixels(slice)));
      return true;
    });
    return imageStack;
  }

  /**
   * Gets the channel (C) index.
   *
   * @return the channel index
   */
  public int getCIndex() {
    return indexC;
  }

  /**
   * Gets the slice (Z) index.
   *
   * @return the slice index
   */
  public int getZIndex() {
    return indexZ;
  }

  /**
   * Gets the frame (T) index.
   *
   * @return the frame index
   */
  public int getTIndex() {
    return indexT;
  }
}
