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

package uk.ac.sussex.gdsc.foci;

import uk.ac.sussex.gdsc.UsageTracker;
import uk.ac.sussex.gdsc.core.ij.ImageJUtils;

import ij.IJ;
import ij.ImagePlus;
import ij.WindowManager;
import ij.gui.GenericDialog;
import ij.plugin.PlugIn;
import ij.process.ImageProcessor;
import ij.process.LUT;
import ij.process.ShortProcessor;

import java.util.ArrayList;

/**
 * Compares two masks created using the Mask Segregator with pixels of AB and A'B' and creates a new
 * mask with pixels of AA' AB' BA' BB'.
 */
public class DoubleMaskSegregator_PlugIn implements PlugIn {
  private static final String TITLE = "Double Mask Segregator";

  private static String title1 = "";
  private static String title2 = "";
  private static boolean applyLUT;
  private static boolean overlayOutline = true;

  private ImagePlus imp1;
  private ImagePlus imp2;

  /** {@inheritDoc} */
  @Override
  public void run(String arg) {
    UsageTracker.recordPlugin(this.getClass(), arg);

    if (!showDialog(this)) {
      return;
    }

    analyse();
  }

  private static boolean showDialog(DoubleMaskSegregator_PlugIn plugin) {
    final String[] items = ImageJUtils.getImageList(ImageJUtils.GREY_8_16, null);

    if (items.length < 2) {
      IJ.error(TITLE, "Require 2 input masks (8/16-bit)");
      return false;
    }

    final GenericDialog gd = new GenericDialog(TITLE);

    if (title1.equalsIgnoreCase(title2)) {
      title2 = (title1.equalsIgnoreCase(items[0]) || title1 == "") ? items[1] : items[0];
    }

    gd.addMessage(
        "Find the classes in each mask using continuous mask values\nand create an all-vs-all "
            + "output combination mask");
    gd.addChoice("Input_1", items, title1);
    gd.addChoice("Input_2", items, title2);
    gd.addCheckbox("Apply_LUT", applyLUT);
    gd.addCheckbox("Overlay_outline", overlayOutline);

    gd.addHelp(uk.ac.sussex.gdsc.help.UrlUtils.FIND_FOCI);
    gd.showDialog();

    if (gd.wasCanceled()) {
      return false;
    }

    title1 = gd.getNextChoice();
    title2 = gd.getNextChoice();
    applyLUT = gd.getNextBoolean();
    overlayOutline = gd.getNextBoolean();

    plugin.imp1 = WindowManager.getImage(title1);
    if (plugin.imp1 == null) {
      return false;
    }
    plugin.imp2 = WindowManager.getImage(title2);
    if (plugin.imp2 == null) {
      return false;
    }
    if (plugin.imp1.getWidth() != plugin.imp2.getWidth()
        || plugin.imp1.getHeight() != plugin.imp2.getHeight()) {
      IJ.error(TITLE, "Input masks must be the same size");
      return false;
    }

    return true;
  }

  private void analyse() {
    // Convert to pixel arrays
    final int[] i1 = getPixels(imp1.getProcessor());
    final int[] i2 = getPixels(imp2.getProcessor());

    // Check the same pixels are non zero
    for (int i = 0; i < i1.length; i++) {
      if (i1[i] == 0 && i2[i] != 0 || i1[i] != 0 && i2[i] == 0) {
        IJ.error(TITLE, "Masks must have the same non-zero pixels");
        return;
      }
    }

    // Find the continuous blocks of incrementing pixel values
    final ArrayList<int[]> b1 = findBlocks(i1);
    final ArrayList<int[]> b2 = findBlocks(i2);

    if (b1.isEmpty() || b2.isEmpty()) {
      // This should only happen when both are empty since the check above ensures one cannot be
      // empty
      // without the other
      IJ.error(TITLE, String.format("Unable to combine %d and %d classes", b1.size(), b2.size()));
      return;
    }

    // Find the block size required to separate blocks
    int max = 0;
    for (final int[] b : b1) {
      final int range = b[1] - b[0];
      if (max < range) {
        max = range;
      }
    }
    for (final int[] b : b2) {
      final int range = b[1] - b[0];
      if (max < range) {
        max = range;
      }
    }
    final int blockSize = MaskSegregator_PlugIn.getBonus(max + 1);

    if ((b1.size() * b2.size() - 1) * blockSize + max > 65535) {
      IJ.error(TITLE, String.format("Unable to create %d classes with a separation of %d",
          b1.size() * b2.size(), blockSize));
      return;
    }

    // Create a map to find the block for each pixel value
    final int[] map2 = createMap(b2);
    final int[] map1 = createMap(b1);
    final int[] offset1 = createOffset(b1);
    final int size1 = b1.size();

    // Combine blocks to new output blocks
    final int[] out = new int[i1.length];
    final int[] h = new int[65536];
    for (int i = 0; i < i1.length; i++) {
      if (i1[i] != 0 && i2[i] != 0) {
        final int block1 = map1[i1[i]];
        final int block2 = map2[i2[i]];
        final int newBlock = block2 * size1 + block1;
        final int base = newBlock * blockSize;
        // Initially use the object value from mask 1. Which mask to use is arbitrary as
        // mask 2 will have the same non-zero pixels, just different object numbers and
        // the numbers are later re-mapped
        out[i] = base + i1[i] - offset1[block1];
        h[out[i]]++;
      }
    }

    // Re-map the object values to be continuous within blocks
    final int[] object = new int[size1 * b2.size()];
    for (int i = 0; i < h.length; i++) {
      if (h[i] != 0) {
        // Find the block this object is in
        final int block = i / blockSize;
        // Increment the object count and re-map the value
        object[block]++;
        h[i] = block * blockSize + object[block];
      }
    }

    // Display
    final ShortProcessor sp = new ShortProcessor(imp1.getWidth(), imp1.getHeight());
    for (int i = 0; i < out.length; i++) {
      sp.set(i, h[out[i]]);
    }
    if (applyLUT) {
      sp.setLut(createLut());
    }
    final ImagePlus imp = ImageJUtils.display(TITLE, sp);

    // Optionally outline each object
    if (overlayOutline) {
      MaskSegregator_PlugIn.addOutline(imp);
    }
  }

  private static int[] getPixels(ImageProcessor ip) {
    final int[] pixels = new int[ip.getPixelCount()];
    for (int i = 0; i < pixels.length; i++) {
      pixels[i] = ip.get(i);
    }
    return pixels;
  }

  private static ArrayList<int[]> findBlocks(int[] image) {
    // Find unique values
    int max = 0;
    for (final int i : image) {
      if (max < i) {
        max = i;
      }
    }
    // Histogram
    final int[] h = new int[max + 1];
    for (final int i : image) {
      h[i]++;
    }

    // Find contiguous blocks
    final ArrayList<int[]> blocks = new ArrayList<>();
    int min = 0;
    for (int i = 1; i < h.length; i++) {
      if (h[i] != 0) {
        if (min == 0) {
          min = i;
        }
        max = i;
      } else {
        if (min != 0) {
          blocks.add(new int[] {min, max});
        }
        min = 0;
      }
    }
    if (min != 0) {
      blocks.add(new int[] {min, max});
    }

    return blocks;
  }

  private static int[] createMap(ArrayList<int[]> blocks) {
    final int max = blocks.get(blocks.size() - 1)[1];
    final int[] map = new int[max + 1];
    for (int b = 0; b < blocks.size(); b++) {
      final int[] block = blocks.get(b);
      for (int i = block[0]; i <= block[1]; i++) {
        map[i] = b;
      }
    }
    return map;
  }

  private static int[] createOffset(ArrayList<int[]> blocks) {
    final int[] offset = new int[blocks.size()];
    for (int b = 0; b < blocks.size(); b++) {
      offset[b] = blocks.get(b)[0] - 1;
    }
    return offset;
  }

  /**
   * Build a custom LUT that helps show the classes.
   *
   * @return the lut
   */
  private static LUT createLut() {
    final byte[] reds = new byte[256];
    final byte[] greens = new byte[256];
    final byte[] blues = new byte[256];
    final int nColors = ice(reds, greens, blues);
    if (nColors < 256) {
      interpolateWithZero(reds, greens, blues, nColors);
    }
    return new LUT(reds, greens, blues);
  }

  /**
   * Copied from ij.plugin.LutLoader.
   *
   * @param reds the reds
   * @param greens the greens
   * @param blues the blues
   * @return the number of colours
   */
  private static int ice(byte[] reds, byte[] greens, byte[] blues) {
    final int[] r = {0, 0, 0, 0, 0, 0, 19, 29, 50, 48, 79, 112, 134, 158, 186, 201, 217, 229, 242,
        250, 250, 250, 250, 251, 250, 250, 250, 250, 251, 251, 243, 230};
    final int[] g = {156, 165, 176, 184, 190, 196, 193, 184, 171, 162, 146, 125, 107, 93, 81, 87,
        92, 97, 95, 93, 93, 90, 85, 69, 64, 54, 47, 35, 19, 0, 4, 0};
    final int[] b = {140, 147, 158, 166, 170, 176, 209, 220, 234, 225, 236, 246, 250, 251, 250, 250,
        245, 230, 230, 222, 202, 180, 163, 142, 123, 114, 106, 94, 84, 64, 26, 27};
    for (int i = 0; i < r.length; i++) {
      reds[i] = (byte) r[i];
      greens[i] = (byte) g[i];
      blues[i] = (byte) b[i];
    }
    return r.length;
  }

  /**
   * Copied from ij.plugin.LutLoader. Modified to set the first position to zero.
   *
   * @param reds the reds
   * @param greens the greens
   * @param blues the blues
   * @param numberOfColors the number of colors
   */
  private static void interpolateWithZero(byte[] reds, byte[] greens, byte[] blues,
      int numberOfColors) {
    final byte[] r = new byte[numberOfColors];
    final byte[] g = new byte[numberOfColors];
    final byte[] b = new byte[numberOfColors];
    System.arraycopy(reds, 0, r, 0, numberOfColors);
    System.arraycopy(greens, 0, g, 0, numberOfColors);
    System.arraycopy(blues, 0, b, 0, numberOfColors);
    final double scale = numberOfColors / 255.0;
    int i1;
    int i2;
    double fraction;
    reds[0] = greens[0] = blues[0] = 0;
    for (int i = 0; i < 255; i++) {
      i1 = (int) (i * scale);
      i2 = i1 + 1;
      if (i2 == numberOfColors) {
        i2 = numberOfColors - 1;
      }
      fraction = i * scale - i1;
      reds[i + 1] = (byte) ((1.0 - fraction) * (r[i1] & 255) + fraction * (r[i2] & 255));
      greens[i + 1] = (byte) ((1.0 - fraction) * (g[i1] & 255) + fraction * (g[i2] & 255));
      blues[i + 1] = (byte) ((1.0 - fraction) * (b[i1] & 255) + fraction * (b[i2] & 255));
    }
  }
}
