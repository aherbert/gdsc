/*-
 * #%L
 * Genome Damage and Stability Centre ImageJ Plugins
 *
 * Software for microscopy image analysis
 * %%
 * Copyright (C) 2011 - 2020 Alex Herbert
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

import ij.IJ;
import ij.ImagePlus;
import ij.gui.GenericDialog;
import ij.plugin.PlugIn;
import ij.plugin.filter.GaussianBlur;
import ij.process.ByteProcessor;
import ij.process.ImageProcessor;
import ij.process.ShortProcessor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.commons.rng.UniformRandomProvider;
import uk.ac.sussex.gdsc.UsageTracker;
import uk.ac.sussex.gdsc.core.utils.rng.UniformRandomProviders;

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

  private static final AtomicInteger sequenceNumber = new AtomicInteger();

  private ByteProcessor roi;
  private int channelMax;
  private int channelMin;

  /** The current settings for the plugin instance. */
  private Settings settings;

  /**
   * Contains the settings that are the re-usable state of the plugin.
   */
  private static class Settings {
    /** The last settings used by the plugin. This should be updated after plugin execution. */
    private static final AtomicReference<Settings> lastSettings =
        new AtomicReference<>(new Settings());

    int bitDepth;
    boolean createMasks;

    /**
     * Default constructor.
     */
    Settings() {
      // Do nothing
    }

    /**
     * Copy constructor.
     *
     * @param source the source
     */
    private Settings(Settings source) {
      bitDepth = source.bitDepth;
      createMasks = source.createMasks;
    }

    /**
     * Copy the settings.
     *
     * @return the settings
     */
    Settings copy() {
      return new Settings(this);
    }

    /**
     * Load a copy of the settings.
     *
     * @return the settings
     */
    static Settings load() {
      return lastSettings.get().copy();
    }

    /**
     * Save the settings.
     */
    void save() {
      lastSettings.set(this);
    }
  }

  @Override
  public void run(String arg) {
    UsageTracker.recordPlugin(this.getClass(), arg);

    if (!showDialog()) {
      return;
    }

    final int number = sequenceNumber.incrementAndGet();

    createRoi();

    createColorChannel("A" + number);
    createColorChannel("B" + number);

    if (settings.createMasks) {
      final ImagePlus imRoi = new ImagePlus("roi" + number, roi);
      imRoi.updateAndDraw();
      imRoi.show();
    }
    IJ.showProgress(1);
  }

  private boolean showDialog() {
    settings = Settings.load();

    final GenericDialog gd = new GenericDialog(TITLE, IJ.getInstance());
    final String[] bitDepthChoice = {"8bit", "12bit", "16bit"};// bit depth of images
    gd.addChoice("Create ...", bitDepthChoice, bitDepthChoice[settings.bitDepth]);
    gd.addCheckbox("Create masks", settings.createMasks);
    gd.showDialog();

    if (gd.wasCanceled()) {
      return false;
    }

    settings.bitDepth = gd.getNextChoiceIndex();
    switch (settings.bitDepth) {
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

    settings.createMasks = gd.getNextBoolean();

    settings.save();

    return true;
  }

  private void createRoi() {
    if (!settings.createMasks) {
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

    final UniformRandomProvider rng = UniformRandomProviders.create();

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
    if (settings.createMasks) {
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

  private ImageProcessor getImageProcessor() {
    if (settings.bitDepth == 0) {
      // 8-bit
      return new ByteProcessor(WIDTH, HEIGHT);
    }
    // 12 or 16-bit
    return new ShortProcessor(WIDTH, HEIGHT);
  }
}
