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

package uk.ac.sussex.gdsc.threshold;

import gnu.trove.list.array.TIntArrayList;
import ij.IJ;
import ij.ImageListener;
import ij.ImagePlus;
import ij.ImageStack;
import ij.Prefs;
import ij.WindowManager;
import ij.gui.DialogListener;
import ij.gui.GenericDialog;
import ij.gui.ImageRoi;
import ij.gui.Overlay;
import ij.gui.PolygonRoi;
import ij.gui.Roi;
import ij.plugin.filter.ExtendedPlugInFilter;
import ij.plugin.filter.PlugInFilterRunner;
import ij.process.ByteProcessor;
import ij.process.ColorProcessor;
import ij.process.ImageProcessor;
import java.awt.AWTEvent;
import java.awt.Color;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.IntConsumer;
import java.util.function.Predicate;
import org.apache.commons.lang3.ArrayUtils;
import uk.ac.sussex.gdsc.UsageTracker;
import uk.ac.sussex.gdsc.core.clustering.optics.LoOp;
import uk.ac.sussex.gdsc.core.ij.ImageAdapter;
import uk.ac.sussex.gdsc.core.ij.ImageJUtils;
import uk.ac.sussex.gdsc.core.ij.gui.NonBlockingExtendedGenericDialog;
import uk.ac.sussex.gdsc.core.math.hull.ConvexHull2d;
import uk.ac.sussex.gdsc.core.math.hull.DiggingConcaveHull2d;
import uk.ac.sussex.gdsc.core.math.hull.Hull;
import uk.ac.sussex.gdsc.core.math.hull.Hull2d;
import uk.ac.sussex.gdsc.core.math.hull.KnnConcaveHull2d;
import uk.ac.sussex.gdsc.core.threshold.AutoThreshold;
import uk.ac.sussex.gdsc.core.threshold.AutoThreshold.Method;
import uk.ac.sussex.gdsc.core.utils.LocalList;
import uk.ac.sussex.gdsc.core.utils.MathUtils;
import uk.ac.sussex.gdsc.core.utils.SortUtils;
import uk.ac.sussex.gdsc.foci.ObjectAnalyzer;

/**
 * Outlines foreground pixel regions using a concave hull. Foreground pixel regions can be auto or
 * manually thresholded.
 */
public class ThresholdOutliner_PlugIn implements ExtendedPlugInFilter, DialogListener {
  private static final String TITLE = "Threshold Outliner";
  private static final int FLAGS = DOES_8G | DOES_16 | NO_CHANGES | FINAL_PROCESSING;

  private ImagePlus imp;
  private int flags2;

  private int lastIndex = -1;
  private int[][] lastObjects = null;

  private ExecutorService es;
  private boolean isPreview;

  /** The current settings for the plugin instance. */
  private Settings settings;

  /**
   * Contains the settings that are the re-usable state of the plugin.
   */
  private static class Settings {
    /** The hull methods. */
    static final String[] HULL_METHODS = {"Convex", "Digging concave", "Knn concave"};
    static final int HULL_DIGGING = 1;
    static final int HULL_CONCAVE = 2;

    /** The last settings used by the plugin. This should be updated after plugin execution. */
    private static final AtomicReference<Settings> lastSettings =
        new AtomicReference<>(new Settings());

    String maskImage;
    int thresholdMethod;
    double percentile;
    // Outlier detection
    int outlierNeighbours;
    double outlierThreshold;
    // For concave hull algorithms
    int hullMethod;
    double diggingThreshold;
    int neighbours;
    int colour;
    double opacity;

    /**
     * Default constructor.
     */
    Settings() {
      percentile = 0.9;
      outlierNeighbours = 10;
      outlierThreshold = 0.9;
      hullMethod = HULL_DIGGING;
      diggingThreshold = 1.5;
      neighbours = 3;
      colour = ArrayUtils.indexOf(ColorHolder.COLORS, Color.YELLOW);
      opacity = 0.5;
    }

    /**
     * Copy constructor.
     *
     * @param source the source
     */
    private Settings(Settings source) {
      maskImage = source.maskImage;
      thresholdMethod = source.thresholdMethod;
      percentile = source.percentile;
      outlierNeighbours = source.outlierNeighbours;
      outlierThreshold = source.outlierThreshold;
      hullMethod = source.hullMethod;
      diggingThreshold = source.diggingThreshold;
      neighbours = source.neighbours;
      colour = source.colour;
      opacity = source.opacity;
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

  /**
   * Provide lazy loading of the methods.
   */
  private static class MethodHolder {
    /**
     * The threshold method names.
     */
    static final String[] METHOD_NAMES;
    /**
     * The threshold methods.
     */
    static final Method[] METHODS;

    static {
      METHOD_NAMES = AutoThreshold.getMethods(false);

      // Rename the none method
      METHOD_NAMES[0] = "Manual percentile";
      // In case the order does not match the enum order
      METHODS = new Method[METHOD_NAMES.length];
      for (int i = 1; i < METHODS.length; i++) {
        METHODS[i] = AutoThreshold.getMethod(METHOD_NAMES[i]);
      }
    }
  }

  /**
   * Provide lazy loading of the colors.
   */
  private static class ColorHolder {
    /**
     * The color names.
     */
    static final String[] COLOR_NAMES = {"Red", "Green", "Blue", "Cyan", "Magenta", "Yellow"};

    /**
     * The colors.
     */
    static final Color[] COLORS =
        {Color.RED, Color.GREEN, Color.BLUE, Color.CYAN, Color.MAGENTA, Color.YELLOW};
  }

  /**
   * Compute the threshold using the given percentile of the histogram data. The threshold is the
   * closest point in the histogram that covers the specified percentile of the data:
   *
   * <pre>
   * sum(data[0] .. data[threshold]) / sum(data) ~ percentile
   * </pre>
   *
   * @param data the data
   * @param percentile the percentile (in [0, 1])
   * @return the threshold
   */
  private static int percentile(int[] data, double percentile) {
    final double total = MathUtils.sum(data);
    if (total == 0) {
      return 0;
    }
    // Distance between the current sum and the percentile
    double distance = 0;
    long sum = 0;
    for (int i = 0; i < data.length; i++) {

      sum += data[i];
      // Fraction of the data
      final double f = sum / total;

      if (f < percentile) {
        // Less than target. Update the distance.
        distance = percentile - f;
      } else {
        // Above target. If closer return this level, otherwise the previous.
        if (f - percentile < distance) {
          return i;
        }
        return i - 1;
      }
    }
    // Reached the end. Percentile must be 1.
    return data.length - 1;
  }

  @Override
  public int setup(String arg, ImagePlus imp) {
    if ("final".equals(arg)) {
      finalProcessing(imp);
      es.shutdown();
      return DONE;
    }

    UsageTracker.recordPlugin(this.getClass(), arg);

    if (imp == null) {
      IJ.noImage();
      return DONE;
    }

    settings = Settings.load();
    settings.save();
    this.imp = imp;

    return FLAGS;
  }

  /**
   * Final processing on the image. This outlines objects on each slice, fills the ROI to create a
   * mask and builds a mask image.
   *
   * @param imp the image
   */
  private void finalProcessing(ImagePlus imp) {
    final boolean doesStacks = ((flags2 & DOES_STACKS) != 0);

    final ImagePlus maskImp = WindowManager.getImage(settings.maskImage);
    final int channel = imp.getChannel();
    final int frame = imp.getFrame();

    int start = imp.getSlice();
    int end = start;
    if (doesStacks) {
      start = 1;
      end = imp.getNSlices();
    }

    final Overlay overlay = new Overlay();
    final Color color = ColorHolder.COLORS[settings.colour];

    final ImageStack impStack = imp.getImageStack();
    final ImageStack mask = new ImageStack(imp.getWidth(), imp.getHeight());
    for (int slice = start; slice <= end; slice++) {
      final int[][] objects = identifyObjects(maskImp, channel, slice, frame);
      final List<Roi> list =
          outlineObjects(impStack.getProcessor(imp.getStackIndex(channel, slice, frame)),
              objects[0], objects[1], i -> {
                /* no-op */ });
      // Fill the outlines to create a new mask.
      final ByteProcessor bp = new ByteProcessor(imp.getWidth(), imp.getHeight());
      bp.setValue(255);
      list.forEach(roi -> {
        bp.fill(roi);
      });
      mask.addSlice(bp);
      final int outputSlice = slice;
      list.forEach(roi -> {
        roi.setPosition(channel, outputSlice, frame);
        roi.setStrokeColor(color);
        overlay.add(roi);
      });
    }
    imp.setOverlay(overlay);
    ImageJUtils.display(imp.getTitle() + " Mask", mask);
  }

  @Override
  public void run(ImageProcessor ip) {
    if (!isPreview) {
      return;
    }

    // This only runs when in the preview.
    // Identify objects in the image mask
    final ImagePlus maskImp = WindowManager.getImage(settings.maskImage);
    final int[][] objects =
        identifyObjects(maskImp, imp.getChannel(), imp.getSlice(), imp.getFrame());

    // Show a coloured overlay of the pixels above the threshold.
    final ColorProcessor cp = new ColorProcessor(imp.getWidth(), imp.getHeight());
    final Color color = ColorHolder.COLORS[settings.colour];
    final int value = color.getRGB();

    final List<Roi> list = outlineObjects(ip, objects[0], objects[1], i -> cp.set(i, value));

    if (Thread.currentThread().isInterrupted()) {
      // The preview was interrupted
      return;
    }

    final Overlay overlay = new Overlay();
    // Colour the foreground pixels
    final ImageRoi roi = new ImageRoi(0, 0, cp);
    roi.setZeroTransparent(true);
    roi.setOpacity(settings.opacity);
    overlay.add(roi);
    list.forEach(r -> {
      r.setStrokeColor(color);
      overlay.add(r);
    });
    imp.setOverlay(overlay);
  }

  /**
   * Identify objects in the mask image. The returned object Ids are sorted in ascending order.
   *
   * @param maskImp the mask image
   * @param cc the channel
   * @param zz the slice
   * @param tt the frame
   * @return {objectIds, indices}
   */
  private synchronized int[][] identifyObjects(ImagePlus maskImp, int cc, int zz, int tt) {
    final int index = maskImp.getStackIndex(cc, zz, tt);
    // Cache the analysis for the preview mode
    if (index == lastIndex) {
      return lastObjects;
    }

    final ObjectAnalyzer oa = new ObjectAnalyzer(maskImp.getImageStack().getProcessor(index));
    final int[] mask = oa.getObjectMask();
    // Extract the indices of non-zero mask positions
    int[] objects = new int[mask.length];
    int[] indices = new int[mask.length];
    int count = 0;
    for (int i = 0; i < mask.length; i++) {
      if (mask[i] != 0) {
        objects[count] = mask[i];
        indices[count] = i;
        count++;
      }
    }

    // Compact and sort
    objects = Arrays.copyOf(objects, count);
    indices = Arrays.copyOf(indices, count);
    SortUtils.sortData(indices, objects, true, false);
    final int[][] result = {objects, indices};
    lastIndex = index;
    lastObjects = result;
    return result;
  }

  /**
   * Run on the input image.
   *
   * @param ip the image
   * @param objects the object Ids (sorted ascending)
   * @param indices the indices for each object Id
   * @param foreground will be called with the index of each foreground pixel
   * @return the list
   */
  private List<Roi> outlineObjects(ImageProcessor ip, int[] objects, int[] indices,
      IntConsumer foreground) {
    if (objects.length == 0) {
      return Collections.emptyList();
    }
    final int maxObject = objects[objects.length - 1];
    final LocalList<Roi> list = new LocalList<>(maxObject);

    // For each object:
    final int[] h = new int[ip.getBitDepth() == 8 ? 256 : 65536];
    final int maxx = ip.getWidth();
    Hull.Builder hb;
    if (settings.hullMethod == Settings.HULL_DIGGING) {
      hb = DiggingConcaveHull2d.newBuilder().setThreshold(settings.diggingThreshold);
    } else if (settings.hullMethod == Settings.HULL_CONCAVE) {
      hb = KnnConcaveHull2d.newBuilder().setK(settings.neighbours);
    } else {
      hb = ConvexHull2d.newBuilder();
    }

    final TIntArrayList pixels = new TIntArrayList(100);

    int end = 0;
    for (int object = 1; object <= maxObject; object++) {
      // The preview can be interrupted
      if (Thread.currentThread().isInterrupted()) {
        return Collections.emptyList();
      }

      // Find the start and end of the object
      final int start = end;
      while (end < objects.length && object == objects[end]) {
        end++;
      }
      // - Extract pixels from the object image into a histogram.
      Arrays.fill(h, 0);
      for (int i = start; i < end; i++) {
        h[ip.get(indices[i])]++;
      }

      // - Threshold
      int threshold;
      if (settings.thresholdMethod == 0) {
        threshold = percentile(h, settings.percentile);
      } else {
        threshold = AutoThreshold.getThreshold(MethodHolder.METHODS[settings.thresholdMethod], h);
      }

      // Any pixel above the threshold is to be extracted into a coordinate
      pixels.resetQuick();
      for (int i = start; i < end; i++) {
        final int index = indices[i];
        if (ip.get(index) > threshold) {
          pixels.add(index);
        }
      }

      // Perform local outlier probability (LoOP) threshold
      double[] scores;
      // Run if threshold in (0, 1)
      if (Math.abs(settings.outlierThreshold - 0.5) < 0.5) {
        scores = runLoop(pixels, maxx);
      } else {
        scores = null;
      }

      // Build hull
      hb.clear();
      for (int i = 0; i < pixels.size(); i++) {
        final int index = pixels.getQuick(i);
        // Test if an outlier
        if (scores == null || scores[i] <= settings.outlierThreshold) {
          foreground.accept(index);
          hb.add(index % maxx, index / maxx);
        }
      }
      // - Create hull
      final Hull2d hull = (Hull2d) hb.build();
      if (hull == null) {
        continue;
      }

      // - Show polygon ROI
      final int n = hull.getNumberOfVertices();
      final float[] x = new float[n];
      final float[] y = new float[n];
      final double[][] v = hull.getVertices();
      for (int i = 0; i < v.length; i++) {
        x[i] = (float) v[i][0];
        y[i] = (float) v[i][1];
      }

      list.add(new PolygonRoi(x, y, Roi.POLYGON));
    }

    return list;
  }

  private double[] runLoop(TIntArrayList pixels, int maxx) {
    final int size = pixels.size();
    if (size <= 1) {
      // No neighbours for single (or zero) point
      return new double[size];
    }
    final float[] x = new float[size];
    final float[] y = new float[size];
    for (int i = 0; i < pixels.size(); i++) {
      final int index = pixels.getQuick(i);
      x[i] = index % maxx;
      y[i] = index / maxx;
    }
    final LoOp loop = new LoOp(x, y);
    loop.setExecutorService(es);
    try {
      final int safeNumberOfNeighbours = MathUtils.clip(1, size - 1, settings.outlierNeighbours);
      return loop.run(safeNumberOfNeighbours, 3.0);
    } catch (final InterruptedException e) {
      // Restore interrupted state...
      Thread.currentThread().interrupt();
      // This is done by the preview if the parameters change
      if (isPreview) {
        return new double[size];
      }
      throw new RuntimeException("Interrupted LoOp", e);
    } catch (final ExecutionException e) {
      throw new RuntimeException("Failed to run LoOp", e);
    }
  }

  @Override
  public int showDialog(ImagePlus imp, String command, PlugInFilterRunner pfr) {
    // Filter the mask options based on the image size.
    // XY must match; z can be 1 or must match; bit depth must be grey 8/16
    final Predicate<ImagePlus> filter = ImageJUtils.createImageFilter(ImageJUtils.GREY_8_16)
        .and(i -> i.getWidth() == imp.getWidth() && i.getHeight() == imp.getHeight()
            && (i.getNSlices() == 1 || i.getNSlices() == imp.getNSlices()))
        .and(i -> i.getID() != imp.getID());

    final String[] items = ImageJUtils.getImageList(filter);
    if (items.length == 0) {
      IJ.error(TITLE, "No suitable object masks for the input image");
      return DONE;
    }

    // Q. Can this be a non-blocking dialog?

    final NonBlockingExtendedGenericDialog gd = new NonBlockingExtendedGenericDialog(TITLE);
    gd.addMessage("Create a new mask image for each object by thresholding and outlining.");

    // Add more thresholding options

    gd.addChoice("Mask_image", items, settings.maskImage);
    gd.addChoice("Threshold_method", MethodHolder.METHOD_NAMES, settings.thresholdMethod);
    gd.addSlider("Percentile", 0.8, 1, settings.percentile);
    gd.addMessage("Remove outliers using local outlier probability (LoOP)");
    gd.addSlider("Outlier_neighbours", 4, 20, settings.outlierNeighbours);
    gd.addSlider("Outlier_threshold", 0.5, 1, settings.outlierThreshold);
    gd.addMessage("Outline options");
    gd.addChoice("Hull_method", Settings.HULL_METHODS, settings.hullMethod);
    gd.addSlider("Digging_threshold", 0.1, 5, settings.diggingThreshold);
    gd.addSlider("K_neighbours", 3, 20, settings.neighbours);
    gd.addMessage("Overlay");
    gd.addChoice("Colour", ColorHolder.COLOR_NAMES, settings.colour);
    gd.addSlider("Opacity", 0.1, 1, settings.opacity);

    gd.addPreviewCheckbox(pfr);
    gd.addDialogListener(this);
    gd.addHelp(uk.ac.sussex.gdsc.help.UrlUtils.UTILITY);

    // Do not lock the image to allow slider update during the preview
    imp.unlock();

    // Allow updating the outline when the image slice changes
    final ImageListener listener = new ImageAdapter() {
      private final int id = imp.getID();
      private int currentSlice = imp.getCurrentSlice();

      @Override
      public void imageUpdated(ImagePlus imp) {
        if (id == imp.getID()) {
          final int slice = imp.getCurrentSlice();
          if (slice != currentSlice) {
            currentSlice = slice;
            // Run analysis again
            ThresholdOutliner_PlugIn.this.run(imp.getProcessor());
          }
        }
      }
    };

    ImagePlus.addImageListener(listener);

    // For outlier detection
    es = Executors.newFixedThreadPool(Prefs.getThreads());

    try {
      // This will block
      gd.showDialog();
    } finally {
      ImagePlus.removeImageListener(listener);
    }

    final boolean cancelled = gd.wasCanceled() || !dialogItemChanged(gd, null);
    if (cancelled) {
      // Kill preview overlay
      imp.setOverlay(null);
      es.shutdown();
      return DONE;
    }

    // Fake stack to get the correct stack size message for only this channel and frame
    final ImageStack fakeStack = imp.createEmptyStack();
    final Object pixels = imp.getProcessor().getPixels();
    for (int i = 1; i <= imp.getNSlices(); i++) {
      fakeStack.addSlice(null, pixels);
    }
    flags2 = IJ.setupDialog(new ImagePlus(null, fakeStack), FLAGS);

    return FLAGS;
  }

  /** Listener to modifications of the input fields of the dialog. */
  @Override
  public boolean dialogItemChanged(GenericDialog gd, AWTEvent event) {
    isPreview = gd.isPreviewActive();
    settings.maskImage = gd.getNextChoice();
    settings.thresholdMethod = gd.getNextChoiceIndex();
    settings.percentile = MathUtils.clip(0, 1, gd.getNextNumber());
    settings.outlierNeighbours = (int) gd.getNextNumber();
    settings.outlierThreshold = gd.getNextNumber();
    settings.hullMethod = gd.getNextChoiceIndex();
    settings.diggingThreshold = gd.getNextNumber();
    settings.neighbours = (int) gd.getNextNumber();
    settings.colour = gd.getNextChoiceIndex();
    settings.opacity = gd.getNextNumber();
    return !gd.invalidNumber();
  }

  @Override
  public void setNPasses(int passes) {
    // Ignore
  }
}
