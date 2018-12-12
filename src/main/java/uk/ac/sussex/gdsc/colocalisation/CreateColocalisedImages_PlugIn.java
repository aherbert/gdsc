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

package uk.ac.sussex.gdsc.colocalisation;

import uk.ac.sussex.gdsc.UsageTracker;

import ij.IJ;
import ij.ImagePlus;
import ij.gui.GenericDialog;
import ij.plugin.PlugIn;
import ij.plugin.filter.GaussianBlur;
import ij.process.ByteProcessor;
import ij.process.ImageProcessor;
import ij.process.ShortProcessor;

import org.apache.commons.rng.UniformRandomProvider;
import org.apache.commons.rng.simple.RandomSource;

/**
 * Create some dummy images to test for colocalisation.
 */
public class CreateColocalisedImages_PlugIn implements PlugIn {
  private static final String TITLE = "Colocalisaed Images";
  private static final int NUMBER_OF_POINTS = 20;

  private static final int WIDTH = 256;
  private static final int HEIGHT = 256;
  private static final int PADDING = 40;
  private static final int MIN_SIZE = 5;
  private static final int MAX_EXPANSION_SIZE = 15;
  private static final int BACKGROUND = 0;
  private static final int FOREGROUND = 255;

  private static int sequenceNumber;
  private static int bitDepth;
  private static boolean createMasks;

  private ByteProcessor roi;
  private int channelMax;
  private int channelMin;

  @Override
  public void run(String arg) {
    UsageTracker.recordPlugin(this.getClass(), arg);

    if (!getBitDepth()) {
      return;
    }

    sequenceNumber++;

    createRoi();

    createColorChannel("A" + sequenceNumber);
    createColorChannel("B" + sequenceNumber);

    if (createMasks) {
      final ImagePlus imRoi = new ImagePlus("roi" + sequenceNumber, roi);
      imRoi.updateAndDraw();
      imRoi.show();
    }
    IJ.showProgress(1);
  }

  private boolean getBitDepth() {
    final GenericDialog gd = new GenericDialog(TITLE, IJ.getInstance());
    final String[] bitDepthChoice = {"8bit", "12bit", "16bit"};// bit depth of images
    gd.addChoice("Create ...", bitDepthChoice, bitDepthChoice[bitDepth]);
    gd.addCheckbox("Create masks", createMasks);
    gd.showDialog();

    if (gd.wasCanceled()) {
      return false;
    }

    bitDepth = gd.getNextChoiceIndex();
    switch (bitDepth) {
      case 2: // 16-bit
        channelMax = Short.MAX_VALUE;
        channelMin = 640;
        break;

      case 1: // 12-bit
        channelMax = 4095;
        channelMin = 80;
        break;

      default: // 8-bit
        channelMax = 255;
        channelMin = 10;
    }

    createMasks = gd.getNextBoolean();

    return true;
  }

  private void createRoi() {
    if (!createMasks) {
      return;
    }

    roi = new ByteProcessor(WIDTH, HEIGHT);
    roi.add(BACKGROUND);

    for (int x = PADDING; x < WIDTH - PADDING; x++) {
      for (int y = 0; y < HEIGHT; y++) {
        roi.set(x, y, FOREGROUND);
      }
    }
  }

  private void createColorChannel(String title) {
    final ImageProcessor cp = getImageProcessor();
    ByteProcessor bp = null;

    final UniformRandomProvider rng = RandomSource.create(RandomSource.SPLIT_MIX_64);

    for (int point = 0; point < NUMBER_OF_POINTS; point++) {
      final int x = rng.nextInt(WIDTH - 2 * PADDING) + PADDING;
      final int y =
          MIN_SIZE + MAX_EXPANSION_SIZE + rng.nextInt(HEIGHT - 2 * (MIN_SIZE + MAX_EXPANSION_SIZE));

      final int xSize = MIN_SIZE + rng.nextInt(MAX_EXPANSION_SIZE);
      final int ySize = MIN_SIZE + rng.nextInt(MAX_EXPANSION_SIZE);

      final int value = rng.nextInt(channelMax - channelMin) + channelMin;
      cp.set(x, y, value);

      for (int i = -xSize; i < xSize; i++) {
        for (int j = -ySize; j < ySize; j++) {
          cp.set(x + i, y + j, value);
        }
      }
    }

    final GaussianBlur gb = new GaussianBlur();
    gb.blurGaussian(cp, 20, 20, 0.02);

    // Get all values above zero as the ROI
    if (createMasks) {
      bp = new ByteProcessor(WIDTH, HEIGHT);
      bp.add(BACKGROUND);

      for (int i = cp.getPixelCount(); i-- > 0;) {
        if (cp.get(i) > channelMin) {
          bp.set(i, FOREGROUND);
          roi.set(i, FOREGROUND);
        }
      }
    }

    // Add some noise to the image
    cp.noise(channelMax / 16.0);

    // Show the images
    final ImagePlus im = new ImagePlus(title, cp);
    im.show();

    if (bp != null) {
      final ImagePlus imRoi = new ImagePlus(title + "roi" + sequenceNumber, bp);
      imRoi.show();
    }
  }

  private static ImageProcessor getImageProcessor() {
    if (bitDepth == 0) {
      // 8-bit
      return new ByteProcessor(WIDTH, HEIGHT);
    }
    // 12 or 16-bit
    return new ShortProcessor(WIDTH, HEIGHT);
  }
}
