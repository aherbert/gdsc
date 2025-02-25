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

package uk.ac.sussex.gdsc.ij.utils;

import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.WindowManager;
import ij.plugin.PlugIn;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.ToDoubleBiFunction;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import uk.ac.sussex.gdsc.core.ij.ImageJUtils;
import uk.ac.sussex.gdsc.core.ij.SimpleImageJTrackProgress;
import uk.ac.sussex.gdsc.core.ij.gui.ExtendedGenericDialog;
import uk.ac.sussex.gdsc.core.match.Matchings;
import uk.ac.sussex.gdsc.core.utils.SimpleArrayUtils;
import uk.ac.sussex.gdsc.ij.UsageTracker;

/**
 * Relabels mask image using overlap between z and/or t dimensions.
 */
public class MaskOverlap_PlugIn implements PlugIn {
  private static final String TITLE = "Mask Overlap";
  private ImagePlus imp;
  /** The current settings for the plugin instance. */
  private Settings settings;

  /**
   * Contains the settings that are the re-usable state of the plugin.
   */
  private static class Settings {
    /** The last settings used by the plugin. This should be updated after plugin execution. */
    private static final AtomicReference<Settings> lastSettings =
        new AtomicReference<>(new Settings());
    static final String[] METHODS = {"Nearest Neighbour", "Minimum Distance"};
    static final int MINIMUM_DISTANCE = 1;

    String title;
    boolean doC;
    boolean doZ;
    boolean doT;
    double iouThreshold;
    int method;

    /**
     * Default constructor.
     */
    Settings() {
      title = "";
      doC = true;
      doZ = true;
      doT = true;
      iouThreshold = 0.3;
    }

    /**
     * Copy constructor.
     *
     * @param source the source
     */
    private Settings(Settings source) {
      title = source.title;
      doC = source.doC;
      doZ = source.doZ;
      doT = source.doT;
      iouThreshold = source.iouThreshold;
      method = source.method;
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

  /** {@inheritDoc} */
  @Override
  public void run(String arg) {
    UsageTracker.recordPlugin(this.getClass(), arg);

    if (WindowManager.getImageCount() == 0) {
      IJ.showMessage(TITLE, "No images opened.");
      return;
    }

    if (!showDialog()) {
      return;
    }

    analyse();
  }

  private boolean showDialog() {
    final ExtendedGenericDialog gd = new ExtendedGenericDialog(TITLE);

    gd.addMessage("Relabels mask image using overlap between z and/or t dimensions.");

    final Predicate<ImagePlus> filter =
        imp -> imp.getBitDepth() == 16 && (imp.getNFrames() > 1 || imp.getNSlices() > 1);
    final String[] maskList = ImageJUtils.getImageList(filter);

    settings = Settings.load();
    gd.addChoice("Mask", maskList, settings.title);
    gd.addCheckbox("All_channels", settings.doC);
    gd.addCheckbox("Z_overlap", settings.doZ);
    gd.addCheckbox("T_overlap", settings.doT);
    gd.addSlider("IoU_threshold", 0, 0.95, settings.iouThreshold);
    gd.addChoice("Method", Settings.METHODS, settings.method);
    gd.showDialog();

    if (gd.wasCanceled()) {
      return false;
    }

    settings.title = gd.getNextChoice();
    settings.doC = gd.getNextBoolean();
    settings.doZ = gd.getNextBoolean();
    settings.doT = gd.getNextBoolean();
    settings.iouThreshold = gd.getNextNumber();
    settings.method = gd.getNextChoiceIndex();
    settings.save();

    imp = WindowManager.getImage(settings.title);

    if (imp == null) {
      IJ.error(TITLE, "No mask specified");
      return false;
    }

    return true;
  }

  private void analyse() {
    final ImageStack stack = imp.getImageStack();
    final SimpleImageJTrackProgress progress = SimpleImageJTrackProgress.getInstance();
    final int[] channels = getChannels();
    final int nz = imp.getNSlices();
    final int nt = imp.getNFrames();
    if (settings.doZ) {
      progress.status("Overlap slices");
      final int total = channels.length * imp.getNFrames() * (imp.getNSlices() - 1);
      int count = 0;
      final short[] nextId = {0};
      for (final int c : channels) {
        for (int t = 1; t <= nt; t++) {
          short[] m2 = (short[]) stack.getProcessor(imp.getStackIndex(c, 1, t)).getPixels();
          for (int z = 2; z <= nz; z++) {
            progress.progress(count++, total);
            final short[] m1 = m2;
            m2 = (short[]) stack.getProcessor(imp.getStackIndex(c, z, t)).getPixels();
            overlapAnalysis(m1, m2, nextId);
          }
        }
      }
      progress.progress(1);
    }
    if (settings.doT) {
      progress.status("Overlap frames");
      final int total = channels.length * (imp.getNFrames() - 1);
      int count = 0;
      final short[] nextId = {0};
      for (final int c : channels) {
        // Extract first mask
        short[] m2 = getTimepoint(stack, c, 1);
        for (int t = 2; t <= nt; t++) {
          progress.progress(count++, total);
          final short[] m1 = m2;
          m2 = getTimepoint(stack, c, t);
          overlapAnalysis(m1, m2, nextId);
          setTimepoint(stack, c, t, m2);
        }
      }
      progress.progress(1);
    }
  }

  /**
   * Gets the channels to process.
   *
   * @return the channels
   */
  private int[] getChannels() {
    if (settings.doC) {
      return SimpleArrayUtils.newArray(imp.getNChannels(), 1, 1);
    }
    return new int[] {imp.getChannel()};
  }

  /**
   * Gets the timepoint z-stack as a single array.
   *
   * @param stack the image stack
   * @param c the channel
   * @param t the timepoint
   * @return the image data
   */
  private short[] getTimepoint(ImageStack stack, int c, int t) {
    final int size = imp.getWidth() * imp.getHeight();
    final int nz = imp.getNSlices();
    final short[] data = new short[nz * size];
    for (int z = 0; z < nz; z++) {
      final short[] m = (short[]) stack.getProcessor(imp.getStackIndex(c, z + 1, t)).getPixels();
      System.arraycopy(m, 0, data, z * size, size);
    }
    return data;
  }

  /**
   * Sets the timepoint z-stack as a single array.
   *
   * @param stack the image stack
   * @param c the channel
   * @param t the timepoint
   * @param the image data
   */
  private void setTimepoint(ImageStack stack, int c, int t, short[] data) {
    final int size = imp.getWidth() * imp.getHeight();
    final int nz = imp.getNSlices();
    for (int z = 0; z < nz; z++) {
      final short[] m = (short[]) stack.getProcessor(imp.getStackIndex(c, z + 1, t)).getPixels();
      System.arraycopy(data, z * size, m, 0, size);
    }
  }

  /**
   * Maps overlapping objects in the second mask to the first mask. The maximum ID is the maximum of
   * all IDs from previous overlap analysis through the series.
   *
   * @param m1 the first mask
   * @param m2 the second mask
   * @param nextId the maximum id
   */
  private void overlapAnalysis(short[] m1, short[] m2, short[] nextId) {
    final int[] h1 = histogram(m1);
    final int[] h2 = histogram(m2);
    final int min1 = minNonZeroIndex(h1);
    final int min2 = minNonZeroIndex(h2);
    if ((min1 | min2) < 0) {
      // No objects in at least 1 frame
      return;
    }
    final int max1 = maxNonZeroIndex(h1);
    final int max2 = maxNonZeroIndex(h2);

    // Overlap matrix can be very large so compact using the min/max range
    // for mask 1 and compress mask 2 to contiguous sequence.

    // Compress mask 2 to contiguous sequence
    final short[] label = new short[max2 + 1];
    int id2 = 0;
    for (int i = min2; i <= max2; i++) {
      if (h2[i] != 0) {
        label[i] = (short) ++id2;
        // Update histogram for new ID
        h2[id2] = h2[i];
      }
    }
    relabel(m2, label);

    // Sparse matrix
    final int m2size = id2 + 1;
    final int[][] overlap = IntStream.rangeClosed(min1, max1)
        .mapToObj(i -> h1[i] == 0 ? null : new int[m2size]).toArray(int[][]::new);
    for (int i = 0; i < m1.length; i++) {
      if (m1[i] != 0 && m2[i] != 0) {
        overlap[m1[i] - min1][m2[i]]++;
      }
    }
    // Mask 1 indices may not be contiguous so filter zero counts
    final List<Integer> verticesA = IntStream.rangeClosed(min1, max1).filter(i -> h1[i] != 0)
        .boxed().collect(Collectors.toList());
    final List<Integer> verticesB =
        IntStream.rangeClosed(1, id2).boxed().collect(Collectors.toList());
    // a = mask1 index
    // b = mask2 index
    final ToDoubleBiFunction<Integer, Integer> edges = (a, b) -> {
      final int intersection = overlap[a - min1][b];
      final double union = (double) h1[a] + h2[b] - intersection;
      final double iou = intersection / union;
      return 1 - iou;
    };
    final short[] relabel = new short[m2size];
    final BiConsumer<Integer, Integer> matched = (a, b) -> relabel[b] = a.shortValue();
    // Any unmatched objects must have a unique ID.
    // Set to the maximum of all observed IDs.
    // This supports up to 2^16 objects if original masks are labelled with consecutive IDs.
    nextId[0] = (short) Math.max(max1, nextId[0] & 0xffff);
    final Consumer<Integer> unmatchedB = b -> relabel[b] = incrementUnsignedId(nextId);
    if (settings.method == Settings.MINIMUM_DISTANCE) {
      Matchings.minimumDistance(verticesA, verticesB, edges, 1 - settings.iouThreshold, matched,
          null, unmatchedB);
    } else {
      Matchings.nearestNeighbour(verticesA, verticesB, edges, 1 - settings.iouThreshold, matched,
          null, unmatchedB);
    }
    relabel(m2, relabel);
  }

  /**
   * Histogram the unsigned 16-bit image.
   *
   * @param image the image
   * @return the histogram
   */
  private static int[] histogram(short[] image) {
    final int[] h = new int[1 << 16];
    for (int i = 0; i < image.length; i++) {
      h[image[i] & 0xffff]++;
    }
    return h;
  }

  /**
   * Return the minimum non zero index.
   *
   * @param data the data
   * @return the index (or -1)
   */
  private static int minNonZeroIndex(int[] data) {
    final int len = data.length;
    for (int i = 1; i < len; i++) {
      if (data[i] != 0) {
        return i;
      }
    }
    return -1;
  }

  /**
   * Return the maximum non zero index.
   *
   * @param data the data
   * @return the index (or -1)
   */
  private static int maxNonZeroIndex(int[] data) {
    for (int i = data.length; --i > 0;) {
      if (data[i] != 0) {
        return i;
      }
    }
    return -1;
  }

  /**
   * Relabel the data.
   *
   * @param data the data
   * @param label the labels
   */
  private void relabel(short[] data, final short[] label) {
    for (int i = 0; i < data.length; i++) {
      data[i] = label[data[i] & 0xffff];
    }
  }

  /**
   * Increment the unsigned id.
   *
   * @param id the id
   * @return the short
   * @throws IllegalStateException if the next ID is zero
   */
  private short incrementUnsignedId(short[] id) {
    final short next = (short) (id[0] + 1);
    if (next == 0) {
      throw new IllegalStateException("Too many objects (2^16)");
    }
    id[0] = next;
    return next;
  }
}
