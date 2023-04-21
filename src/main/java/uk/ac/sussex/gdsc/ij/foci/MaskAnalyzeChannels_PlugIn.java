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

package uk.ac.sussex.gdsc.ij.foci;

import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.WindowManager;
import ij.gui.ImageRoi;
import ij.gui.Overlay;
import ij.gui.PolygonRoi;
import ij.gui.Roi;
import ij.plugin.filter.PlugInFilter;
import ij.process.ByteProcessor;
import ij.process.ColorProcessor;
import ij.process.FloatPolygon;
import ij.process.ImageProcessor;
import ij.text.TextWindow;
import it.unimi.dsi.fastutil.doubles.DoubleArrayList;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import java.awt.Color;
import java.awt.Rectangle;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;
import org.apache.commons.math3.stat.descriptive.rank.Percentile;
import uk.ac.sussex.gdsc.core.ij.BufferedTextWindow;
import uk.ac.sussex.gdsc.core.ij.ImageJUtils;
import uk.ac.sussex.gdsc.core.ij.gui.ExtendedGenericDialog;
import uk.ac.sussex.gdsc.core.ij.gui.OffsetPointRoi;
import uk.ac.sussex.gdsc.core.utils.LocalList;
import uk.ac.sussex.gdsc.core.utils.SimpleArrayUtils;
import uk.ac.sussex.gdsc.core.utils.TextUtils;
import uk.ac.sussex.gdsc.ij.UsageTracker;
import uk.ac.sussex.gdsc.ij.foci.ObjectAnalyzer.ObjectCentre;

/**
 * For each unique pixel value in the mask (defining an object), analyse the pixels values for each
 * channel of a target image. Estimate the background of the channel using non-mask pixels.
 */
public class MaskAnalyzeChannels_PlugIn implements PlugInFilter {
  /** The plugin title. */
  private static final String TITLE = "Mask Analyze Channels";

  private static final int FLAGS = DOES_16 + DOES_8G;

  private static AtomicReference<TextWindow> resultsWindowRef = new AtomicReference<>();

  private ImagePlus imp;

  /** The plugin settings. */
  private Settings settings;

  /**
   * Contains the settings that are the re-usable state of the plugin.
   */
  private static class Settings {
    /** The last settings used by the plugin. This should be updated after plugin execution. */
    private static final AtomicReference<Settings> lastSettings =
        new AtomicReference<>(new Settings());

    String input;
    String ignoreChannels;
    double backgroundFraction;
    String ignoreBackgroundChannels;
    boolean showHull;
    boolean showLabels;
    boolean showBackground;

    Settings() {
      input = "";
      ignoreChannels = "";
      backgroundFraction = 0.1;
    }

    Settings(Settings source) {
      input = source.input;
      ignoreChannels = source.ignoreChannels;
      backgroundFraction = source.backgroundFraction;
      ignoreBackgroundChannels = source.ignoreBackgroundChannels;
      showHull = source.showHull;
      showLabels = source.showLabels;
      showBackground = source.showBackground;
    }

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
  public int setup(String arg, ImagePlus imp) {
    UsageTracker.recordPlugin(this.getClass(), arg);

    if (imp == null) {
      IJ.noImage();
      return DONE;
    }
    this.imp = imp;
    if (!showDialog()) {
      return DONE;
    }
    return FLAGS;
  }

  private boolean showDialog() {
    final ExtendedGenericDialog gd = new ExtendedGenericDialog(TITLE);

    gd.addMessage(TextUtils.wrap("For each object defined with a unique pixel value, "
        + "analyze the channels in a target image.", 80));

    settings = Settings.load();
    final String[] images = ImageJUtils.getImageList(0);
    gd.addChoice("Input", images, settings.input);
    gd.addStringField("Ignore_channels", settings.ignoreChannels);
    gd.addMessage(TextUtils.wrap("The background is estimated using non-object pixels within "
        + "the convex hull of the objects. Pixels are combined across channels after "
        + "converting each channel [min, max] to [0, 1]; the lowest combined pixels are "
        + "used as the background pixels.", 80));
    gd.addSlider("Background_fraction", 0.01, 0.5, settings.backgroundFraction);
    gd.addStringField("Ignore_background_channels", settings.ignoreBackgroundChannels);
    gd.addCheckbox("Show_hull", settings.showHull);
    gd.addCheckbox("Show_labels", settings.showLabels);
    gd.addCheckbox("Show_background", settings.showBackground);

    // gd.addHelp(uk.ac.sussex.gdsc.ij.help.Urls.FIND_FOCI);
    gd.showDialog();

    if (gd.wasCanceled()) {
      return false;
    }

    settings.input = gd.getNextChoice();
    settings.ignoreChannels = gd.getNextString();
    settings.backgroundFraction = gd.getNextNumber();
    settings.ignoreBackgroundChannels = gd.getNextString();
    settings.showHull = gd.getNextBoolean();
    settings.showLabels = gd.getNextBoolean();
    settings.showBackground = gd.getNextBoolean();
    settings.save();

    return true;
  }

  /** {@inheritDoc} */
  @Override
  public void run(ImageProcessor inputProcessor) {
    final ImagePlus imp = WindowManager.getImage(settings.input);
    if (imp == null) {
      IJ.error(TITLE, "No image: " + settings.input);
      return;
    }

    final int[] channels = getChannels(imp, settings.ignoreChannels);
    if (channels.length == 0) {
      IJ.error(TITLE, "No channels: " + settings.input);
      return;
    }
    final int[] backgroundChannels = getChannels(imp, settings.ignoreBackgroundChannels);
    if (backgroundChannels.length == 0) {
      IJ.error(TITLE, "No background channels: " + settings.input);
      return;
    }

    // Find objects
    final ObjectAnalyzer oa = new ObjectAnalyzer(inputProcessor, true);
    final int max = oa.getMaxObject();
    if (max == 0) {
      IJ.error(TITLE, "No objects in mask");
      return;
    }

    final FloatPolygon fp = createConvexHull(oa);
    final Roi roi = new PolygonRoi(fp, Roi.POLYGON);

    final IntArrayList objects = new IntArrayList();
    final IntArrayList nonObjects = new IntArrayList();
    findIndices(oa, roi, objects, nonObjects);

    final int[] mask = oa.getObjectMask();

    final ImageStack stack = imp.getImageStack();
    final LocalList<double[]> data = new LocalList<>();

    // Count object sizes
    final int[] count = new int[max + 1];
    objects.forEach(i -> {
      count[mask[i]]++;
    });

    // Sum objects in each channel
    for (final int c : channels) {
      final ImageProcessor ip =
          stack.getProcessor(imp.getStackIndex(c, imp.getZ(), imp.getFrame()));
      final double[] sum = new double[max + 1];
      objects.forEach(i -> {
        final int object = mask[i];
        sum[object] += ip.getf(i);
      });
      data.add(sum);
    }

    final IntArrayList backgroundIndices =
        extractBackgroundIndices(imp, backgroundChannels, nonObjects);

    // Compute backgrounds
    final DoubleArrayList backgrounds = new DoubleArrayList(channels.length);
    for (final int c : channels) {
      final ImageProcessor ip =
          stack.getProcessor(imp.getStackIndex(c, imp.getZ(), imp.getFrame()));
      backgrounds.add(backgroundIndices.intStream().mapToDouble(ip::getf).average().orElse(0));
    }

    // Tabulate results
    try (BufferedTextWindow tw = new BufferedTextWindow(createResultsWindow(channels))) {
      final StringBuilder sb = new StringBuilder(512);
      sb.append("Background").append('\t').append(backgroundIndices.size());
      backgrounds.forEach(x -> sb.append('\t').append(x));
      tw.append(sb.toString());
      for (int i = 1; i <= max; i++) {
        final int object = i;
        sb.setLength(0);
        sb.append(object).append('\t').append(count[object]);
        data.forEach(a -> sb.append('\t').append(a[object] / count[object]));
        tw.append(sb.toString());
      }
    }

    final Overlay overlay = new Overlay();
    if (settings.showHull) {
      overlay.add(roi);
    }
    if (settings.showLabels) {
      overlay.add(createLabelsRoi(oa));
    }
    if (settings.showBackground) {
      overlay.add(createBackgroundRoi(oa, backgroundIndices));
    }
    if (overlay.size() != 0) {
      this.imp.setOverlay(overlay);
    }
  }

  /**
   * Gets the channels to process.
   *
   * @param imp the image
   * @param ignoreChannels the channel to ignore
   * @return the channels
   */
  private static int[] getChannels(ImagePlus imp, String ignoreChannels) {
    final IntSet set = new IntOpenHashSet(imp.getNChannels());
    for (final String ch : ignoreChannels.split("\\D+")) {
      if (ch.isEmpty()) {
        continue;
      }
      try {
        set.add(Integer.parseInt(ch));
      } catch (final NumberFormatException ex) {
        IJ.log("Invalid channel. " + ex.getMessage());
        return new int[0];
      }
    }
    return IntStream.rangeClosed(1, imp.getNChannels()).filter(i -> !set.contains(i)).toArray();
  }

  /**
   * Creates a convex hull containing all the objects.
   *
   * @param oa the object analyzer
   * @return the convex hull
   */
  private static FloatPolygon createConvexHull(ObjectAnalyzer oa) {
    final int[] mask = oa.getObjectMask();
    final int w = oa.getWidth();
    final int h = oa.getHeight();
    // Create a FloatPolygon using the min/max x value for each y in the mask
    final IntArrayList xp = new IntArrayList();
    final IntArrayList yp = new IntArrayList();
    for (int y = 0; y < h; y++) {
      int i = y * w;
      for (int x = 0; x < w; x++, i++) {
        if (mask[i] != 0) {
          yp.add(y);
          xp.add(x);
          for (int j = i - x + w - 1; j >= i; j--) {
            if (mask[j] != 0) {
              yp.add(y);
              xp.add(j - i + x);
            }
          }
          break;
        }
      }
    }
    return new FloatPolygon(toFloatArray(xp), toFloatArray(yp)).getConvexHull();
  }

  /**
   * Convert the list to a float array.
   *
   * @param a the list
   * @return the array
   */
  private static float[] toFloatArray(IntArrayList a) {
    final float[] b = new float[a.size()];
    for (int i = 0; i < b.length; i++) {
      b[i] = a.getInt(i);
    }
    return b;
  }

  /**
   * Find the indices of objects and non-objects within the ROI.
   *
   * @param oa the object analyzer
   * @param roi the roi
   * @param objects the indices of the objects
   * @param nonObjects the indices of the non objects
   */
  private static void findIndices(ObjectAnalyzer oa, Roi roi, IntArrayList objects,
      IntArrayList nonObjects) {
    final int[] mask = oa.getObjectMask();
    final int w = oa.getWidth();
    final int h = oa.getHeight();
    final ByteProcessor bp = new ByteProcessor(w, h);
    bp.setValue(255);
    bp.fill(roi);
    final Rectangle bounds = roi.getBounds();
    final int bw = bounds.width;
    final int bh = bounds.height;
    for (int y = 0; y < bh; y++) {
      int i = (y + bounds.y) * w + bounds.x;
      for (int x = 0; x < bw; x++, i++) {
        if (bp.get(i) != 0) {
          if (mask[i] == 0) {
            nonObjects.add(i);
          } else {
            objects.add(i);
          }
        }
      }
    }
  }

  /**
   * Extracts the background indices using the lowest combined pixels.
   *
   * @param imp the image
   * @param backgroundChannels the background channels
   * @param nonObjects the indices of non-objects
   * @return the indices
   */
  private IntArrayList extractBackgroundIndices(ImagePlus imp, int[] backgroundChannels,
      IntArrayList nonObjects) {
    final IntArrayList backgroundIndices = new IntArrayList();
    if (nonObjects.isEmpty()) {
      return backgroundIndices;
    }

    final ImageStack stack = imp.getImageStack();

    // Combine pixels after normalising [min, max] to [0, 1]
    final double[] backgroundPixels = new double[nonObjects.size()];
    for (final int c : backgroundChannels) {
      final ImageProcessor ip =
          stack.getProcessor(imp.getStackIndex(c, imp.getZ(), imp.getFrame()));
      // Normalise the pixel values inside the hull that are not a mask pixel
      if (backgroundPixels.length != 0) {
        final double min = nonObjects.intStream().mapToDouble(ip::getf).min().getAsDouble();
        final double range = nonObjects.intStream().mapToDouble(ip::getf).max().getAsDouble() - min;
        // Add to background sum.
        if (range != 0) {
          final int[] j = {0};
          nonObjects.intStream()
              .forEach(i -> backgroundPixels[j[0]++] += (ip.getf(i) - min) / range);
        }
      }
    }

    final double threshold =
        new Percentile(settings.backgroundFraction * 100).evaluate(backgroundPixels);
    final int[] j = {0};
    nonObjects.intStream().forEach(i -> {
      if (backgroundPixels[j[0]++] <= threshold) {
        backgroundIndices.add(i);
      }
    });
    return backgroundIndices;
  }

  private static TextWindow createResultsWindow(int[] channels) {
    final String labels = createResultsHeader(channels);
    final TextWindow tw = ImageJUtils.refresh(resultsWindowRef,
        () -> new TextWindow(TITLE + " Results", labels, "", 900, 600));
    tw.getTextPanel().setColumnHeadings(labels);
    return tw;
  }

  private static String createResultsHeader(int[] channels) {
    final StringBuilder sb = new StringBuilder();
    sb.append("Object\tSize");
    for (final int c : channels) {
      sb.append('\t').append(c);
    }
    return sb.toString();
  }

  private static Roi createLabelsRoi(ObjectAnalyzer oa) {
    final ObjectCentre[] centres = oa.getObjectCentres();
    final OffsetPointRoi labels = new OffsetPointRoi(
        SimpleArrayUtils.toFloat(
            Arrays.stream(centres).skip(1).mapToDouble(ObjectCentre::getCentreX).toArray()),
        SimpleArrayUtils.toFloat(
            Arrays.stream(centres).skip(1).mapToDouble(ObjectCentre::getCentreY).toArray()));
    labels.setShowLabels(true);
    return labels;
  }

  private static ImageRoi createBackgroundRoi(ObjectAnalyzer oa, IntArrayList backgroundIndices) {
    final ColorProcessor cp = new ColorProcessor(oa.getWidth(), oa.getHeight());
    final int value = Color.YELLOW.getRGB();
    backgroundIndices.intStream().forEach(i -> cp.set(i, value));
    final ImageRoi r = new ImageRoi(0, 0, cp);
    r.setZeroTransparent(true);
    r.setOpacity(0.5);
    return r;
  }
}
