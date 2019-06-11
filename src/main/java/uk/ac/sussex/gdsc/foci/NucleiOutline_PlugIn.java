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

package uk.ac.sussex.gdsc.foci;

import uk.ac.sussex.gdsc.UsageTracker;
import uk.ac.sussex.gdsc.core.ij.BufferedTextWindow;
import uk.ac.sussex.gdsc.core.ij.ImageAdapter;
import uk.ac.sussex.gdsc.core.ij.ImageJTrackProgress;
import uk.ac.sussex.gdsc.core.ij.ImageJUtils;
import uk.ac.sussex.gdsc.core.ij.gui.NonBlockingExtendedGenericDialog;
import uk.ac.sussex.gdsc.core.logging.Ticker;
import uk.ac.sussex.gdsc.core.threshold.AutoThreshold;
import uk.ac.sussex.gdsc.core.threshold.AutoThreshold.Method;
import uk.ac.sussex.gdsc.core.utils.MathUtils;
import uk.ac.sussex.gdsc.core.utils.TurboList;
import uk.ac.sussex.gdsc.core.utils.concurrent.ConcurrencyUtils;
import uk.ac.sussex.gdsc.core.utils.concurrent.ConcurrentMonoStack;
import uk.ac.sussex.gdsc.foci.ObjectAnalyzer.ObjectCentre;

import com.google.common.util.concurrent.Futures;

import gnu.trove.list.array.TIntArrayList;
import gnu.trove.set.TIntSet;
import gnu.trove.set.hash.TIntHashSet;

import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.Prefs;
import ij.WindowManager;
import ij.gui.GenericDialog;
import ij.gui.ImageRoi;
import ij.gui.Overlay;
import ij.gui.Roi;
import ij.gui.ShapeRoi;
import ij.measure.Calibration;
import ij.plugin.PlugIn;
import ij.plugin.filter.Binary;
import ij.plugin.filter.EDM;
import ij.plugin.filter.GaussianBlur;
import ij.plugin.filter.RankFilters;
import ij.plugin.filter.ThresholdToSelection;
import ij.process.Blitter;
import ij.process.ByteProcessor;
import ij.process.ColorProcessor;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;
import ij.process.LUT;
import ij.text.TextWindow;

import org.apache.commons.lang3.tuple.Pair;

import java.awt.AWTEvent;
import java.awt.Color;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * Outline nuclei across multiple frames. Performs the following:
 *
 * <ul>
 *
 * <li>Filter the image (Gaussian blur)
 *
 * <li>Mask all the nuclei.
 *
 * <li>Divide them based on size using watershed.
 *
 * <li>Provides save options: Overlay ROI; Save to memory; and Results Table
 *
 * </ul>
 */
public class NucleiOutline_PlugIn implements PlugIn {
  private static final String TITLE = "Nuceli Outline";

  private static AtomicReference<TextWindow> resultsRef = new AtomicReference<>();

  private ImagePlus imp;
  private TIntSet slices;
  private boolean inPreview;

  /** The queue of work for the preview. */
  private ConcurrentMonoStack<PreviewSettings> queue;

  /** The current settings for the plugin instance. */
  private Settings settings;

  /**
   * The results saved from previous analysis.
   *
   * <p>TODO: Decide if this should be saved for all images or just the last analysed image
   */
  private Map<Integer, List<Pair<Integer, Nucleus[]>>> results = new ConcurrentHashMap<>();

  /**
   * Contains the settings that are the re-usable state of the plugin.
   */
  private static class Settings {
    private static final String SETTING_BLUR1 = "gdsc.nucleioutline.blur1";
    private static final String SETTING_BLUR2 = "gdsc.nucleioutline.blur2";
    private static final String SETTING_METHOD = "gdsc.nucleioutline.method";
    private static final String SETTING_OUTLIER_RADIUS = "gdsc.nucleioutline.outlierRadius";
    private static final String SETTING_OUTLIER_THRESHOLD = "gdsc.nucleioutline.outlierThreshold";
    private static final String SETTING_MAX_NUCLEUS_SIZE = "gdsc.nucleioutline.maxNucleusSize";
    private static final String SETTING_MIN_NUCLEUS_SIZE = "gdsc.nucleioutline.minNucleusSize";
    private static final String SETTING_MAX_LINK_DISTANCE = "gdsc.nucleioutline.maxLinkDistance";
    private static final String SETTING_EROSION = "gdsc.nucleioutline.erosion";
    private static final String SETTING_EXPANSION_INNER = "gdsc.nucleioutline.expansionInner";
    private static final String SETTING_EXPANSION = "gdsc.nucleioutline.expansion";
    private static final String SETTING_PREVIEW_IN_OUT = "gdsc.nucleioutline.previewInOut";
    private static final String SETTING_RESULTS_OVERLAY = "gdsc.nucleioutline.resultsOverlay";
    private static final String SETTING_RESULTS_SAVE = "gdsc.nucleioutline.resultsSave";
    private static final String SETTING_RESULTS_TABLE = "gdsc.nucleioutline.resultsTable";

    /** The last settings used by the plugin. This should be updated after plugin execution. */
    private static final AtomicReference<Settings> lastSettings =
        new AtomicReference<>(new Settings());

    double blur1;
    double blur2;
    AutoThreshold.Method method;
    double outlierRadius;
    double outlierThreshold;
    double maxNucleusSize;
    double minNucleusSize;
    double maxLinkDistance;
    int erosion;
    int expansionInner;
    int expansion;
    boolean previewInOut;
    boolean resultsOverlay;
    boolean resultsSave;
    boolean resultsTable;

    /**
     * Default constructor.
     */
    Settings() {
      // Set defaults
      blur1 = Prefs.get(SETTING_BLUR1, 0);
      blur2 = Prefs.get(SETTING_BLUR2, 0);
      method = AutoThreshold.getMethod(Prefs.get(SETTING_METHOD, Method.OTSU.toString()));
      outlierRadius = Prefs.get(SETTING_OUTLIER_RADIUS, 2.0); // px
      outlierThreshold = Prefs.get(SETTING_OUTLIER_THRESHOLD, 50.0); // 8-bit greyscale value
      maxNucleusSize = Prefs.get(SETTING_MAX_NUCLEUS_SIZE, 50); // um^2
      minNucleusSize = Prefs.get(SETTING_MIN_NUCLEUS_SIZE, 0); // um^2
      maxLinkDistance = Prefs.get(SETTING_MAX_LINK_DISTANCE, -1); // any
      erosion = (int) Prefs.get(SETTING_EROSION, 3);
      expansionInner = (int) Prefs.get(SETTING_EXPANSION_INNER, 3);
      expansion = (int) Prefs.get(SETTING_EXPANSION, 3);
      previewInOut = Prefs.get(SETTING_PREVIEW_IN_OUT, false);
      resultsOverlay = Prefs.get(SETTING_RESULTS_OVERLAY, true);
      resultsSave = Prefs.get(SETTING_RESULTS_SAVE, false);
      resultsTable = Prefs.get(SETTING_RESULTS_TABLE, false);
    }

    /**
     * Copy constructor.
     *
     * @param source the source
     */
    private Settings(Settings source) {
      blur1 = source.blur1;
      blur2 = source.blur2;
      method = source.method;
      outlierRadius = source.outlierRadius;
      outlierThreshold = source.outlierThreshold;
      maxNucleusSize = source.maxNucleusSize;
      minNucleusSize = source.minNucleusSize;
      maxLinkDistance = source.maxLinkDistance;
      erosion = source.erosion;
      expansionInner = source.expansionInner;
      expansion = source.expansion;
      previewInOut = source.previewInOut;
      resultsOverlay = source.resultsOverlay;
      resultsSave = source.resultsSave;
      resultsTable = source.resultsTable;
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
      Prefs.set(SETTING_BLUR1, blur1);
      Prefs.set(SETTING_BLUR2, blur2);
      Prefs.set(SETTING_METHOD, method.toString());
      Prefs.set(SETTING_OUTLIER_RADIUS, outlierRadius);
      Prefs.set(SETTING_OUTLIER_THRESHOLD, outlierThreshold);
      Prefs.set(SETTING_MAX_NUCLEUS_SIZE, maxNucleusSize);
      Prefs.set(SETTING_MIN_NUCLEUS_SIZE, minNucleusSize);
      Prefs.set(SETTING_MAX_LINK_DISTANCE, maxLinkDistance);
      Prefs.set(SETTING_EROSION, erosion);
      Prefs.set(SETTING_EXPANSION_INNER, expansionInner);
      Prefs.set(SETTING_EXPANSION, expansion);
      Prefs.set(SETTING_PREVIEW_IN_OUT, previewInOut);
      Prefs.set(SETTING_RESULTS_OVERLAY, resultsOverlay);
      Prefs.set(SETTING_RESULTS_SAVE, resultsSave);
      Prefs.set(SETTING_RESULTS_TABLE, resultsTable);
    }
  }

  /**
   * Define the settings for a preview.
   */
  private static class PreviewSettings {
    final Settings settings;
    final int slice;

    PreviewSettings(Settings settings, int slice) {
      this.settings = settings;
      this.slice = slice;
    }
  }

  /**
   * A worker to show the preview.
   */
  private static class PreviewWorker implements Runnable {
    ImagePlus imp;
    ImageStack stack;
    double pixelArea;
    ConcurrentMonoStack<PreviewSettings> queue;
    boolean running = true;

    PreviewWorker(ImagePlus imp, ConcurrentMonoStack<PreviewSettings> queue) {
      this.imp = imp;
      this.stack = imp.getImageStack();
      this.pixelArea = getPixelArea(imp);
      this.queue = queue;
    }

    @Override
    public void run() {
      while (running) {
        try {
          final PreviewSettings previewSettings = queue.pop();
          if (previewSettings == null) {
            continue;
          }
          final Pair<ObjectAnalyzer, ByteProcessor> result = analyseImage(previewSettings.settings,
              pixelArea, stack.getProcessor(previewSettings.slice).duplicate(), true);

          // Add the outline to the image for the preview
          final ByteProcessor bp = result.getRight();
          bp.setThreshold(255, 255, ImageProcessor.NO_LUT_UPDATE);
          final ThresholdToSelection tts = new ThresholdToSelection();
          imp.setRoi(tts.convert(bp));

          // Add dilation and erosion rings if requested.
          if (previewSettings.settings.previewInOut) {
            addOverlay(previewSettings, result);
          } else {
            imp.setOverlay(null);
          }
        } catch (final InterruptedException ex) {
          ConcurrencyUtils.interruptAndThrowUncheckedIf(running, ex);
        }
      }
    }

    private void addOverlay(PreviewSettings previewSettings,
        Pair<ObjectAnalyzer, ByteProcessor> result) {
      final ObjectAnalyzer oa = result.getLeft();
      Overlay overlay = new Overlay();
      if (previewSettings.settings.erosion > 0) {
        final int[] mask = oa.getObjectMask().clone();
        final ColorProcessor cp = new ColorProcessor(oa.getWidth(), oa.getHeight(), mask);
        new ObjectEroder(cp, true).erode(previewSettings.settings.erosion);
        // Convert non-zero pixels to a byte mask
        final ByteProcessor bp = new ByteProcessor(oa.getWidth(), oa.getHeight());
        for (int i = 0; i < mask.length; i++) {
          if (mask[i] != 0) {
            bp.set(i, 255);
          }
        }
        final ImageRoi roi = new ImageRoi(0, 0, bp);
        roi.setOpacity(0.3);
        roi.setZeroTransparent(true);
        overlay.add(roi);
      }
      if (previewSettings.settings.expansion > 0) {
        final int[] mask = oa.getObjectMask().clone();
        final ColorProcessor cp = new ColorProcessor(oa.getWidth(), oa.getHeight(), mask);
        final ObjectExpander expander = new ObjectExpander(cp);
        expander.expand(previewSettings.settings.expansionInner);
        final int[] innerMask = mask.clone();
        expander.expand(previewSettings.settings.expansion);
        // Convert non-zero pixels to a byte mask
        final ByteProcessor bp = new ByteProcessor(oa.getWidth(), oa.getHeight());
        bp.setColorModel(LUT.createLutFromColor(Color.YELLOW).getColorModel());
        for (int i = 0; i < mask.length; i++) {
          if (mask[i] > innerMask[i]) {
            bp.set(i, 255);
          }
        }
        // Zero out original
        bp.setValue(0);
        bp.fill(imp.getRoi());

        final ImageRoi roi = new ImageRoi(0, 0, bp);
        roi.setOpacity(0.3);
        roi.setZeroTransparent(true);
        overlay.add(roi);
      }
      imp.setOverlay(overlay);
    }
  }

  /**
   * Encapsulate information about a nucleus.
   */
  static class Nucleus {
    /** The information from the object analysis. */
    final ObjectCentre objectCentre;
    /** The roi outline of the nucleus. */
    final Roi roi;

    /**
     * Instantiates a new nucleus.
     *
     * @param objectCentre the object centre
     * @param roi the roi
     */
    Nucleus(ObjectCentre objectCentre, Roi roi) {
      this.objectCentre = objectCentre;
      this.roi = roi;
    }

    /**
     * Gets the estimated radius. This is computed assuming the area is a perfect circle.
     *
     * @return the estimate radius
     */
    double getEstimatedRadius() {
      return Math.sqrt(objectCentre.getSize() / Math.PI);
    }
  }

  @Override
  public void run(String arg) {
    UsageTracker.recordPlugin(this.getClass(), arg);

    if (!setup()) {
      return;
    }

    if (!showDialog()) {
      return;
    }

    analyse();
  }

  private boolean setup() {
    imp = WindowManager.getCurrentImage();
    if (imp == null || imp.getStackSize() == 1) {
      IJ.error(TITLE, "Require an input stack");
      return false;
    }

    // Get the stack slices to process
    final int frames = imp.getNFrames();
    slices = new TIntHashSet(frames);
    final int ch = imp.getChannel();
    final int slice = imp.getZ();
    for (int frame = 1; frame <= frames; frame++) {
      slices.add(imp.getStackIndex(ch, slice, frame));
    }

    return true;
  }

  private boolean showDialog() {
    settings = Settings.load();

    // Initialise preview
    queue = new ConcurrentMonoStack<>();
    final ExecutorService executor = Executors.newSingleThreadExecutor();
    final PreviewWorker worker = new PreviewWorker(imp, queue);
    executor.submit(worker);

    // Register an image listener for the preview
    final int id = imp.getID();
    ImagePlus.addImageListener(new ImageAdapter() {
      @Override
      public void imageUpdated(ImagePlus imp) {
        if (inPreview && imp.getID() == id) {
          addPreviewOverlay(imp.getCurrentSlice());
        }
      }

      @Override
      public void imageClosed(ImagePlus imp) {
        if (imp.getID() == id) {
          // Listeners are in a vector so this is synchronized
          ImagePlus.removeImageListener(this);
        }
      }
    });

    final Calibration cal = imp.getCalibration();

    // Get the user options
    NonBlockingExtendedGenericDialog gd = new NonBlockingExtendedGenericDialog(TITLE);
    gd.addMessage("Track nuclei across frames");
    gd.addSlider("Blur", 0, 4.5, settings.blur1);
    gd.addSlider("Blur2", 0, 20, settings.blur2);
    gd.addChoice("Threshold_method", AutoThreshold.getMethods(true), settings.method.toString());
    gd.addNumericField("Outlier_radius", settings.outlierRadius, 2, 6, "px");
    gd.addNumericField("Outlier_threshold", settings.outlierThreshold, 0);
    gd.addNumericField("Max_nucleus_size", settings.maxNucleusSize, 2, 6, cal.getUnit() + "^2");
    gd.addNumericField("Min_nucleus_size", settings.minNucleusSize, 2, 6, cal.getUnit() + "^2");
    gd.addCheckbox("Preview", false);
    gd.addCheckbox("Preview_In_Out", settings.previewInOut);
    gd.addNumericField("Erosion", settings.erosion, 0);
    gd.addNumericField("Expansion_inner", settings.expansionInner, 0);
    gd.addNumericField("Expansion", settings.expansion, 0);
    gd.addDialogListener(this::dialogItemChanged);
    gd.showDialog();
    settings.save();

    // Shutdown preview
    removeOverlay();
    worker.running = false;
    queue.close(true);
    executor.shutdown();

    if (gd.wasCanceled()) {
      return false;
    }
    inPreview = false;

    // Final output options
    gd = new NonBlockingExtendedGenericDialog(TITLE);
    gd.addMessage("Analysing all frames...");
    gd.addCheckbox("Results_overlay", settings.resultsOverlay);
    gd.addCheckbox("Results_save", settings.resultsSave);
    gd.addCheckbox("Results_table", settings.resultsTable);
    gd.showDialog();

    if (gd.wasCanceled()) {
      return false;
    }

    settings.resultsOverlay = gd.getNextBoolean();
    settings.resultsSave = gd.getNextBoolean();
    settings.resultsTable = gd.getNextBoolean();

    return settings.resultsOverlay || settings.resultsSave || settings.resultsTable;
  }

  private boolean dialogItemChanged(GenericDialog gd, @SuppressWarnings("unused") AWTEvent event) {
    settings.blur1 = gd.getNextNumber();
    settings.blur2 = gd.getNextNumber();
    settings.method = AutoThreshold.getMethod(gd.getNextChoice());
    settings.outlierRadius = gd.getNextNumber();
    settings.outlierThreshold = gd.getNextNumber();
    settings.maxNucleusSize = gd.getNextNumber();
    settings.minNucleusSize = gd.getNextNumber();
    inPreview = gd.getNextBoolean();
    settings.previewInOut = gd.getNextBoolean();
    settings.erosion = (int) gd.getNextNumber();
    settings.expansionInner = (int) gd.getNextNumber();
    settings.expansion = (int) gd.getNextNumber();

    if (inPreview) {
      addPreviewOverlay(imp.getCurrentSlice());
    } else {
      removeOverlay();
    }

    return true;
  }

  private void addPreviewOverlay(int slice) {
    // Only process correct slices
    if (!slices.contains(slice)) {
      return;
    }
    queue.insert(new PreviewSettings(settings.copy(), slice));
  }

  private void removeOverlay() {
    imp.killRoi();
    imp.setOverlay(null);
  }

  /**
   * Gets the pixel area.
   *
   * @param imp the image
   * @return the pixel area
   */
  private static double getPixelArea(ImagePlus imp) {
    final Calibration cal = imp.getCalibration();
    return cal.pixelWidth * cal.pixelHeight;
  }

  /**
   * Analyse the image to identify the objects.
   *
   * <p>Objects will optionally be outlined using a ROI. This will be a {@link ShapeRoi} for
   * multiple objects.
   *
   * @param settings the settings
   * @param pixelArea the pixel area
   * @param ip the image
   * @param createRoi Set to true to create the ROI
   * @return the object analyzer and the outline ROI
   */
  private static Pair<ObjectAnalyzer, ByteProcessor> analyseImage(Settings settings,
      double pixelArea, ImageProcessor ip, boolean requireByteProcessor) {
    // Process each slice to:
    // - Filter the image (Gaussian blur)
    // - Mask all the nuclei
    // - Divide them based on size using watershed

    applyBlur(ip, settings.blur1, settings.blur2);

    final ByteProcessor bp = applyThreshold(ip, settings.method);

    fillHoles(bp);

    removeOutliers(settings, bp);

    // Note:
    // Step here to create a distance map, optionally blur, then threshold again.
    // This just makes the edges of the nuclei contract. Edges are important for the
    // inside/outside nucleus analysis.
    // A distance map with a threshold applied at 2 would contract the nucleus mask
    // for analysis inside.

    // Find objects
    final int minPixelCount = (int) Math.round(settings.minNucleusSize / pixelArea);
    ObjectAnalyzer oa = createObjectAnalyzer(bp, minPixelCount);

    removeSmallObjects(bp, oa);

    final int maxPixelCount = (int) Math.round(settings.maxNucleusSize / pixelArea);
    ByteProcessor bp2 = divideLargeObjects(bp, oa, maxPixelCount);

    // Create a new analyser if needed
    if (bp2 != null) {
      oa = createObjectAnalyzer(bp2, minPixelCount);
      removeSmallObjects(bp2, oa);
    }

    // Note that the second byte processor is a reference to the first. It is
    bp2 = (requireByteProcessor) ? bp : null;

    return Pair.of(oa, bp2);
  }

  /**
   * Apply the Gaussian blur to the image. If the second blur is above the first then perform a
   * difference-of-Gaussians where the small
   *
   * @param ip the image
   * @param blur1 the first blur
   * @param blur2 the second blur
   */
  private static void applyBlur(ImageProcessor ip, double blur1, double blur2) {
    if (blur1 > 0) {
      final GaussianBlur gb = new GaussianBlur();
      gb.showProgress(false);
      if (blur2 > blur1) {
        final FloatProcessor fp1 = ip.toFloat(0, null);
        final FloatProcessor fp2 = (FloatProcessor) fp1.duplicate();
        gb.blurGaussian(fp1, blur1, blur1, 0.0002);
        gb.blurGaussian(fp2, blur2, blur2, 0.0002);
        // Subtract the local contrast (big blur) from the de-noised image (small blur)
        fp1.copyBits(fp2, 0, 0, Blitter.SUBTRACT);
        ip.insert(fp1, 0, 0);
      } else {
        gb.blurGaussian(ip, blur1, blur1, 0.0002);
      }
    }
  }

  /**
   * Apply the threshold method to create a mask.
   *
   * @param ip the image
   * @param method the method
   * @return the mask
   */
  private static ByteProcessor applyThreshold(ImageProcessor ip, Method method) {
    final int[] hist = ip.getHistogram();
    final int threshold = AutoThreshold.getThreshold(method, hist);
    ip.threshold(threshold);
    ip.setMinAndMax(0, 255);

    return ip.convertToByteProcessor();
  }

  /**
   * Fill holes in the mask.
   *
   * @param bp the mask
   */
  private static void fillHoles(ByteProcessor bp) {
    // Fill holes
    final Binary binary = new Binary();
    binary.setup("fill", null);
    binary.run(bp);
  }


  /**
   * Removes the outliers.
   *
   * @param settings the settings
   * @param bp the mask
   */
  private static void removeOutliers(Settings settings, ByteProcessor bp) {
    if (settings.outlierRadius > 0) {
      final RankFilters filter = new RankFilters();
      filter.rank(bp, settings.outlierRadius, RankFilters.OUTLIERS, RankFilters.BRIGHT_OUTLIERS,
          (float) settings.outlierThreshold);
    }
  }

  /**
   * Creates the object analyzer.
   *
   * @param bp the mask image
   * @param minPixelCount the minimum size in pixels for an object
   * @return the object analyzer
   */
  private static ObjectAnalyzer createObjectAnalyzer(ByteProcessor bp, int minPixelCount) {
    // For compatibility with ThresholdToSelection objects are 4-connected
    final ObjectAnalyzer oa = new ObjectAnalyzer(bp, false);
    oa.setMinObjectSize(minPixelCount);
    return oa;
  }

  /**
   * Divide objects that are too large. If any objects were divided then the original object mask is
   * updated in-place and returned.
   *
   * @param bp the original object mask
   * @param oa the object analyzer
   * @param maxPixelCount the maximum size in pixels for an object
   * @return the updated object mask (or null)
   */
  private static ByteProcessor divideLargeObjects(ByteProcessor bp, ObjectAnalyzer oa,
      int maxPixelCount) {
    final int maxObject = oa.getMaxObject();
    final ObjectCentre[] objectData = oa.getObjectCentres();

    // Divide those that are too large
    final TIntArrayList toDivide = new TIntArrayList();
    for (int i = 1; i <= maxObject; i++) {
      if (objectData[i].getSize() > maxPixelCount) {
        toDivide.add(i);
      }
    }

    if (!toDivide.isEmpty()) {
      // Assume black background for now
      final byte foreground = (byte) (255);

      final int[] mask = oa.getObjectMask();
      final byte[] original = (byte[]) bp.getPixels();
      final byte[] tmp = new byte[mask.length];

      // Move each object to divide from the original to a temp image
      toDivide.forEach(object -> {
        for (int i = 0; i < mask.length; i++) {
          if (mask[i] == object) {
            original[i] = 0;
            tmp[i] = foreground;
          }
        }
        return true;
      });

      // Watershed the new temp image
      final EDM edm = new EDM();
      final ByteProcessor tmpIp = new ByteProcessor(bp.getWidth(), bp.getHeight(), tmp);
      edm.setup("watershed", null);
      edm.run(tmpIp);
      IJ.showStatus("");

      // Re-create the objects
      bp.copyBits(tmpIp, 0, 0, Blitter.ADD);

      return bp;
    }
    return null;
  }

  /**
   * Remove small objects from the mask objects.
   *
   * @param bp the original mask used to create the object analyser
   * @param oa the object analyser
   */
  private static void removeSmallObjects(ByteProcessor bp, ObjectAnalyzer oa) {
    // Ensure the small objects are removed from the original
    final int[] mask = oa.getObjectMask();
    for (int i = 0; i < mask.length; i++) {
      if (mask[i] == 0) {
        bp.set(i, 0);
      }
    }
  }

  /**
   * Convert the objects found during analysis to nuclei.
   *
   * @param objectAnalyser the object analyser
   * @return the nuclei
   */
  private static Nucleus[] toNuclei(ObjectAnalyzer objectAnalyser) {
    final Nucleus[] all = new Nucleus[objectAnalyser.getMaxObject()];
    final ObjectCentre[] centres = objectAnalyser.getObjectCentres();
    final Roi[] rois = objectAnalyser.getObjectOutlines();
    for (int j = 1; j < centres.length; j++) {
      all[j - 1] = new Nucleus(centres[j], rois[j]);
    }
    return all;
  }

  /**
   * Analyse all the frames in the image.
   */
  private void analyse() {
    final int threadCount = Prefs.getThreads();
    final ExecutorService executor = Executors.newFixedThreadPool(threadCount);
    final Ticker ticker =
        Ticker.createStarted(new ImageJTrackProgress(true), slices.size(), threadCount > 1);

    final TurboList<Future<Pair<Integer, Nucleus[]>>> futures = new TurboList<>();
    final ImageStack stack = imp.getImageStack();
    final double pixelArea = getPixelArea(imp);

    slices.forEach(slice -> {
      futures.add(executor.submit(() -> {
        final Pair<ObjectAnalyzer, ByteProcessor> result =
            analyseImage(settings, pixelArea, stack.getProcessor(slice).duplicate(), false);
        final ObjectAnalyzer objectAnalyser = result.getLeft();
        final Nucleus[] nuclei = toNuclei(objectAnalyser);

        ticker.tick();
        return Pair.of(slice, nuclei);
      }));
      return true;
    });

    ConcurrencyUtils.waitForCompletionUncheckedT(futures);

    ImageJUtils.clearSlowProgress();

    // Sort by scale and add to the stack.
    // Note the futures are all complete so get the result unchecked.
    final List<Pair<Integer, Nucleus[]>> list = futures.stream().map(Futures::getUnchecked)
        .sorted((r1, r2) -> Double.compare(r1.getLeft(), r2.getLeft()))
        .collect(Collectors.toList());

    // Output options ...
    addResultsOverlay(list);
    saveResults(list);
    showResultsTable(list);
  }

  /**
   * Show all ROI on the image stack at the appropriate slice.
   *
   * @param list the list
   */
  private void addResultsOverlay(final List<Pair<Integer, Nucleus[]>> list) {
    if (settings.resultsOverlay) {
      final Overlay overlay = new Overlay();
      list.stream().forEach(pair -> {
        if (pair.getRight() != null) {
          final int slice = pair.getLeft();
          for (final Nucleus nucleus : pair.getRight()) {
            nucleus.roi.setPosition(slice);
            overlay.add(nucleus.roi);
          }
        }
      });
      imp.setOverlay(overlay);
    }
  }

  /**
   * Save centres for tracking analysis.
   *
   * @param list the list
   */
  private void saveResults(List<Pair<Integer, Nucleus[]>> list) {
    if (settings.resultsSave) {
      results.put(imp.getID(), list);
    }
  }

  /**
   * Result table of summary per frame.
   *
   * @param list the list
   */
  private void showResultsTable(List<Pair<Integer, Nucleus[]>> list) {
    if (settings.resultsTable) {
      final TextWindow textWindow =
          ImageJUtils.refresh(resultsRef, () -> new TextWindow(TITLE + " Summary",
              "Image\tFrame\tCentre X\tCentre Y\tSize\tRadius", "", 700, 250));
      try (BufferedTextWindow tw = new BufferedTextWindow(textWindow)) {
        tw.setIncrement(0);
        final StringBuilder sb = new StringBuilder();
        list.stream().forEach(pair -> {
          final int slice = pair.getLeft();
          sb.setLength(0);
          sb.append(imp.getTitle()).append('\t').append(slice).append('\t');
          final int prefixLength = sb.length();
          for (final Nucleus nucleus : pair.getRight()) {
            sb.setLength(prefixLength);
            sb.append(MathUtils.rounded(nucleus.objectCentre.getCentreX())).append('\t');
            sb.append(MathUtils.rounded(nucleus.objectCentre.getCentreY())).append('\t');
            sb.append(MathUtils.rounded(nucleus.objectCentre.getSize())).append('\t');
            sb.append(MathUtils.rounded(nucleus.getEstimatedRadius()));
            tw.append(sb.toString());
          }
        });
      }
    }
  }
}
