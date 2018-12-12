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

package uk.ac.sussex.gdsc.colocalisation.cda;

import ij.ImagePlus;
import ij.ImageStack;

/**
 * A class to shift two stacks simultaneously within a mask.
 */
public class TwinStackShifter {
  private ImageStack result1;
  private ImageStack result2;
  private int xShift;
  private int yShift;
  private int width;
  private int height;
  private int size;
  private TwinImageShifter[] imageShifters;

  /**
   * Instantiates a new twin stack shifter.
   *
   * @param image1 the first input image
   * @param image2 the second input image
   * @param mask the mask image
   * @throws IllegalArgumentException If the dimensions do not match
   */
  public TwinStackShifter(ImagePlus image1, ImagePlus image2, ImagePlus mask) {
    initialise(image1.getImageStack(), image2.getImageStack(),
        (mask != null) ? mask.getImageStack() : null);
  }

  /**
   * Instantiates a new twin stack shifter.
   *
   * @param image1 the first input image
   * @param image2 the second input image
   * @param mask the mask image
   */
  public TwinStackShifter(ImageStack image1, ImageStack image2, ImageStack mask) {
    initialise(image1, image2, mask);
  }

  private void initialise(ImageStack s1, ImageStack s2, ImageStack s3) {
    width = s1.getWidth();
    height = s1.getHeight();
    size = s1.getSize();

    if (s2.getWidth() != width || s2.getHeight() != height || s2.getSize() != size) {
      throw new IllegalArgumentException("The first and second stack dimensions do not match");
    }
    if (s3 != null
        && (s3.getWidth() != width || s3.getHeight() != height || s3.getSize() != size)) {
      throw new IllegalArgumentException("The first and third stack dimensions do not match");
    }

    imageShifters = new TwinImageShifter[size];

    for (int n = 1; n <= size; n++) {
      imageShifters[n - 1] = new TwinImageShifter(s1.getProcessor(n), s2.getProcessor(n),
          (s3 != null) ? s3.getProcessor(n) : null);
    }
  }

  /**
   * Run with the given shift.
   *
   * @param x the x
   * @param y the y
   */
  public void run(int x, int y) {
    setShift(x, y);
    run();
  }

  /**
   * Run.
   */
  public void run() {
    result1 = new ImageStack(width, height, size);
    result2 = new ImageStack(width, height, size);

    for (int n = 1; n <= size; n++) {
      final TwinImageShifter shifter = imageShifters[n - 1];
      shifter.setShift(xShift, yShift);
      shifter.run();
      result1.setPixels(shifter.getResultImage().getPixels(), n);
      result2.setPixels(shifter.getResultImage2().getPixels(), n);
    }
  }

  /**
   * Sets the shift X.
   *
   * @param x the new shift X
   */
  public void setShiftX(int x) {
    this.xShift = x;
  }

  /**
   * Sets the shift Y.
   *
   * @param y the new shift Y
   */
  public void setShiftY(int y) {
    this.yShift = y;
  }

  /**
   * Sets the shift.
   *
   * @param x the x
   * @param y the y
   */
  public void setShift(int x, int y) {
    this.xShift = x;
    this.yShift = y;
  }

  /**
   * Gets the result image.
   *
   * @return the result image
   */
  public ImagePlus getResultImage() {
    return new ImagePlus(null, result1);
  }

  /**
   * Gets the result image 2.
   *
   * @return the result image 2
   */
  public ImagePlus getResultImage2() {
    return new ImagePlus(null, result2);
  }

  /**
   * Gets the result stack.
   *
   * @return the result stack
   */
  public ImageStack getResultStack() {
    return this.result1;
  }

  /**
   * Gets the result stack 2.
   *
   * @return the result stack 2
   */
  public ImageStack getResultStack2() {
    return this.result2;
  }
}
