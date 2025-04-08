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

package uk.ac.sussex.gdsc.ij.foci;

import ij.IJ;
import ij.ImagePlus;
import ij.WindowManager;
import ij.gui.DialogListener;
import ij.gui.GenericDialog;
import ij.gui.ImageRoi;
import ij.gui.Overlay;
import ij.measure.Measurements;
import ij.plugin.filter.ExtendedPlugInFilter;
import ij.plugin.filter.PlugInFilterRunner;
import ij.process.ByteProcessor;
import ij.process.ColorProcessor;
import ij.process.ImageProcessor;
import ij.process.ImageStatistics;
import ij.process.ShortProcessor;
import java.awt.AWTEvent;
import java.awt.Checkbox;
import java.awt.Label;
import java.awt.Scrollbar;
import java.awt.TextField;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReference;
import uk.ac.sussex.gdsc.core.ij.ImageJUtils;
import uk.ac.sussex.gdsc.core.threshold.AutoThreshold;
import uk.ac.sussex.gdsc.core.threshold.AutoThreshold.Method;
import uk.ac.sussex.gdsc.ij.UsageTracker;

/**
 * Overlay a mask on the channel. For each unique pixel value in the mask (defining an object),
 * analyse the pixels values from the image and segregate the objects into two classes. Objects must
 * be contiguous pixels, allowing segregation of ordinary binary masks.
 */
public class MaskSegregator_PlugIn implements ExtendedPlugInFilter, DialogListener {
  private static final String TITLE = "Mask Segregator";

  private static final int FLAGS = DOES_16 + DOES_8G + FINAL_PROCESSING;
  private ImagePlus imp;
  private ImageProcessor maskIp;
  private int[] objectMask;

  private Checkbox autoCheckbox;
  private Scrollbar cutoffSlider;
  private TextField cutoffText;
  private Label label;
  private Label label2;

  private String lastMaskTitle;
  private boolean lastEightConnected;
  private double[][] objects;
  private double[] stats;
  private int defaultCutoff = -1;
  private int maxObject;

  /** The current settings for the plugin instance. */
  private Settings settings;

  /**
   * Contains the settings that are the re-usable state of the plugin.
   */
  private static class Settings {
    /** The last settings used by the plugin. This should be updated after plugin execution. */
    private static final AtomicReference<Settings> lastSettings =
        new AtomicReference<>(new Settings());

    String maskTitle = "";
    boolean autoCutoff = true;
    boolean eightConnected;
    double cutoff;
    boolean splitMask;
    boolean overlayOutline = true;

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
      maskTitle = source.maskTitle;
      autoCutoff = source.autoCutoff;
      eightConnected = source.eightConnected;
      cutoff = source.cutoff;
      splitMask = source.splitMask;
      overlayOutline = source.overlayOutline;
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
  public int setup(String arg, ImagePlus imp) {
    if ("final".equals(arg)) {
      segregateMask();
      return DONE;
    }

    UsageTracker.recordPlugin(this.getClass(), arg);

    if (imp == null) {
      IJ.noImage();
      return DONE;
    }
    this.imp = imp;
    return FLAGS;
  }

  /** {@inheritDoc} */
  @Override
  public int showDialog(ImagePlus imp, String command, PlugInFilterRunner pfr) {
    final String[] names = getMasks(imp);
    if (names.length == 0) {
      IJ.error(TITLE, "No masks match the image dimensions");
      return DONE;
    }

    settings = Settings.load();

    final GenericDialog gd = new GenericDialog(TITLE);

    gd.addMessage("Overlay a mask on the current image and segregate objects into two classes.\n \n"
        + "Objects are defined with contiguous pixels of the same value.\n"
        + "The mean image value for each object is used for segregation.");

    final ImageStatistics statistics =
        ImageStatistics.getStatistics(imp.getProcessor(), Measurements.MIN_MAX, null);
    if (settings.cutoff < statistics.min) {
      settings.cutoff = statistics.min;
    }
    if (settings.cutoff > statistics.max) {
      settings.cutoff = statistics.max;
    }

    gd.addChoice("Mask", names, settings.maskTitle);
    gd.addMessage("");
    label = (Label) gd.getMessage();
    gd.addMessage("");
    label2 = (Label) gd.getMessage();
    gd.addCheckbox("Auto_cutoff", settings.autoCutoff);
    gd.addCheckbox("8-connected", settings.eightConnected);
    gd.addSlider("Cut-off", statistics.min, statistics.max, settings.cutoff);
    gd.addCheckbox("Split_mask", settings.splitMask);
    gd.addCheckbox("Overlay_outline", settings.overlayOutline);

    autoCheckbox = (Checkbox) gd.getCheckboxes().get(0);
    cutoffSlider = (Scrollbar) gd.getSliders().get(0);
    cutoffText = (TextField) gd.getNumericFields().get(0);

    gd.addHelp(uk.ac.sussex.gdsc.ij.help.Urls.FIND_FOCI);
    gd.addPreviewCheckbox(pfr);
    gd.addDialogListener(this);
    gd.showDialog();

    final boolean cancelled = gd.wasCanceled() || !dialogItemChanged(gd, null);
    settings.save();
    if (cancelled) {
      imp.setOverlay(null);
      return DONE;
    }

    return FLAGS;
  }

  /**
   * Build a list of 8/16 bit images that match the width and height of the input image.
   *
   * @param inputImp the input imp
   * @return the masks
   */
  private static String[] getMasks(ImagePlus inputImp) {
    final String[] names = new String[WindowManager.getImageCount()];
    int count = 0;
    for (final int id : ImageJUtils.getIdList()) {
      final ImagePlus imp = WindowManager.getImage(id);
      if (imp == null) {
        continue;
      }
      if ((imp.getBitDepth() == 24 || imp.getBitDepth() == 32)) {
        continue;
      }
      if (imp.getWidth() != inputImp.getWidth() || imp.getHeight() != inputImp.getHeight()) {
        continue;
      }
      if (imp.getTitle().equals(inputImp.getTitle())) {
        continue;
      }

      names[count++] = imp.getTitle();
    }
    return Arrays.copyOf(names, count);
  }

  /** {@inheritDoc} */
  @Override
  public boolean dialogItemChanged(GenericDialog gd, AWTEvent event) {
    // Preview checkbox will be null if running headless
    final boolean isPreview =
        (gd.getPreviewCheckbox() != null && gd.getPreviewCheckbox().getState());

    if (!isPreview) {
      // Turn off the preview
      imp.setOverlay(null);
      label.setText("");
      label2.setText("");
    }

    settings.maskTitle = gd.getNextChoice();
    settings.autoCutoff = gd.getNextBoolean();
    settings.eightConnected = gd.getNextBoolean();
    settings.cutoff = gd.getNextNumber();
    settings.splitMask = gd.getNextBoolean();
    settings.overlayOutline = gd.getNextBoolean();

    // Check if this is a change to the settings during a preview and update the
    // auto threshold property
    if (isPreview && event.getSource() != null && event.getSource() != autoCheckbox
        && event.getSource() != gd.getPreviewCheckbox()
        // Check we have computed the threshold
        && defaultCutoff >= 0) {
      settings.autoCutoff = (settings.cutoff == defaultCutoff);
      autoCheckbox.setState(settings.autoCutoff);
    }

    return true;
  }

  /** {@inheritDoc} */
  @Override
  public void setNPasses(int passes) {
    // Do nothing
  }

  /** {@inheritDoc} */
  @Override
  public void run(ImageProcessor inputIp) {
    analyseObjects();

    label.setText(String.format("N = %d, Min = %.2f, Max = %.2f, Av = %.2f", objects.length,
        stats[0], stats[1], stats[2]));

    final int localCutoff = getCutoff();

    // Segregate using the object means
    final int[] color = new int[maxObject + 1];
    final int exclude = getRgb(255, 0, 0);
    final int include = getRgb(0, 255, 0);
    int excluded = 0;
    for (int i = 0; i < objects.length; i++) {
      final int maskValue = (int) objects[i][0];
      final double av = objects[i][2];
      if (av < localCutoff) {
        color[maskValue] = exclude;
        excluded++;
      } else {
        color[maskValue] = include;
      }
    }
    final int included = objects.length - excluded;
    label2.setText(String.format("Include = %d, Exclude = %d", included, excluded));

    final ColorProcessor cp = new ColorProcessor(inputIp.getWidth(), inputIp.getHeight());
    for (int i = 0; i < objectMask.length; i++) {
      final int maskValue = objectMask[i];
      if (maskValue != 0) {
        cp.set(i, color[maskValue]);
      }
    }

    // Overlay the segregated mask objects on the image
    final ImageRoi roi = new ImageRoi(0, 0, cp);
    roi.setZeroTransparent(true);
    roi.setOpacity(0.5);
    final Overlay overlay = new Overlay();
    overlay.add(roi);
    imp.setOverlay(overlay);
  }

  private static int getRgb(int red, int green, int blue) {
    return ((red << 16) + (green << 8) + blue);
  }

  private void analyseObjects() {
    // Check if we already have the objects
    if (lastMaskTitle != null && lastMaskTitle.equals(settings.maskTitle)
        && lastEightConnected == settings.eightConnected) {
      return;
    }

    defaultCutoff = -1;

    // Get the mask
    final ImagePlus maskImp = WindowManager.getImage(settings.maskTitle);
    if (maskImp == null) {
      return;
    }

    maskIp = maskImp.getProcessor();
    final int[] maskImage = new int[maskIp.getPixelCount()];
    for (int i = 0; i < maskImage.length; i++) {
      maskImage[i] = maskIp.get(i);
    }

    // Perform a search for objects.
    final ObjectAnalyzer oa = new ObjectAnalyzer(maskIp, settings.eightConnected);
    objectMask = oa.getObjectMask();
    maxObject = oa.getMaxObject();

    // Analyse the objects
    final int[] count = new int[maxObject + 1];
    final long[] sum = new long[count.length];

    final ImageProcessor ch = imp.getProcessor();
    for (int i = 0; i < maskImage.length; i++) {
      final int value = objectMask[i];
      if (value != 0) {
        count[value]++;
        sum[value] += ch.get(i);
      }
    }

    final ArrayList<double[]> tmpObjects = new ArrayList<>();
    for (int i = 0; i < count.length; i++) {
      if (count[i] > 0) {
        tmpObjects.add(new double[] {i, count[i], sum[i] / (double) count[i]});
      }
    }
    objects = tmpObjects.toArray(new double[0][0]);

    // Get the min, max and average pixel value
    double min = Double.POSITIVE_INFINITY;
    double max = Double.NEGATIVE_INFINITY;
    double average = 0;
    for (int i = 0; i < objects.length; i++) {
      final double av = objects[i][2];
      if (min > av) {
        min = av;
      }
      if (max < av) {
        max = av;
      }
      average += av;
    }
    average /= objects.length;
    stats = new double[] {min, max, average};

    final int minimum = (int) min;
    final int maximum = (int) Math.ceil(max) + 1;
    int value = cutoffSlider.getValue();
    if (value < minimum) {
      value = minimum;
    }

    cutoffSlider.setValues(value, 1, minimum, maximum);

    lastMaskTitle = settings.maskTitle;
    lastEightConnected = settings.eightConnected;
  }

  private int getCutoff() {
    return (settings.autoCutoff) ? getAutoCutoff() : (int) settings.cutoff;
  }

  private int getAutoCutoff() {
    if (defaultCutoff < 0) {
      // Build a histogram using the average pixel values per object
      final int[] h = new int[65336];
      for (int i = 0; i < objects.length; i++) {
        final int count = (int) objects[i][1];
        final int av = (int) Math.round(objects[i][2]);
        h[av] += count;
      }

      defaultCutoff = AutoThreshold.getThreshold(Method.OTSU, h);
    }

    // Reset the position on the slider for the dialog
    if (cutoffSlider != null) {
      final String newValue = "" + defaultCutoff;
      if (!cutoffText.getText().equals(newValue)) {
        cutoffText.setText(newValue);
      }
    }

    return defaultCutoff;
  }

  /**
   * Do the final processing to create a new mask using the object segregation.
   */
  private void segregateMask() {
    // Remove the overlay
    imp.setOverlay(null);

    // Create a new mask using the segregated objects
    analyseObjects();
    if (objects == null) {
      return;
    }

    // No need to update this in the getAutoCutoff method
    cutoffSlider = null;

    // Obtaining the cutoff here allows all the input to be obtained from the configuration
    // that may have been set in the preview dialog or in a macro
    final int cutoff = getCutoff();

    final int maxx = maskIp.getWidth();
    final int maxy = maskIp.getHeight();

    if (settings.splitMask) {
      // Create a look-up table of objects to include or exclude
      final boolean[] exclude = new boolean[maxObject + 1];
      for (int i = 0; i < objects.length; i++) {
        final int maskValue = (int) objects[i][0];
        final double av = objects[i][2];
        exclude[maskValue] = (av < cutoff);
      }

      // Create two masks for the segregated objects
      final ImageProcessor excludeIp = maskIp.createProcessor(maxx, maxy);
      final ImageProcessor includeIp = maskIp.createProcessor(maxx, maxy);

      for (int i = 0; i < objectMask.length; i++) {
        final int maskValue = objectMask[i];
        if (maskValue != 0) {
          if (exclude[maskValue]) {
            excludeIp.set(i, maskIp.get(i));
          } else {
            includeIp.set(i, maskIp.get(i));
          }
        }
      }

      ImageJUtils.display(settings.maskTitle + " Include", includeIp);
      ImageJUtils.display(settings.maskTitle + " Exclude", excludeIp);
    } else {
      // Create a lookup table for the new mask objects.
      // Q. Should we maintain the old mask value? This version uses new numbering.
      final int[] newMaskValue = new int[maxObject + 1];
      int include = -1;
      int exclude = 1;
      for (int i = 0; i < objects.length; i++) {
        final int maskValue = (int) objects[i][0];
        final double av = objects[i][2];
        newMaskValue[maskValue] = (av < cutoff) ? exclude++ : include--;
      }

      // Add the bonus to the new mask value for the include objects
      final int bonus = getBonus(include);
      for (int i = 1; i < newMaskValue.length; i++) {
        if (newMaskValue[i] < 0) {
          newMaskValue[i] = bonus - newMaskValue[i];
        }
      }

      final ImageProcessor ip = new ShortProcessor(maxx, maxy);
      for (int i = 0; i < objectMask.length; i++) {
        final int maskValue = objectMask[i];
        if (maskValue != 0) {
          ip.set(i, newMaskValue[maskValue]);
        }
      }
      ip.setMinAndMax(0, exclude);

      final ImagePlus segImp = ImageJUtils.display(settings.maskTitle + " Segregated", ip);

      if (settings.overlayOutline) {
        addOutline(segImp);
      }
    }
  }

  /**
   * Gets the bonus to add to a number so that it exceeds the include level.
   *
   * @param include the include
   * @return the bonus
   */
  static int getBonus(int include) {
    int bonus = 1000;
    while (bonus < include) {
      bonus += 1000;
    }
    return bonus;
  }

  /**
   * Adds the outline.
   *
   * @param imp the imp
   */
  static void addOutline(ImagePlus imp) {
    final ByteProcessor bp = new ByteProcessor(imp.getWidth(), imp.getHeight());
    final ImageProcessor ip = imp.getProcessor();
    for (int i = ip.getPixelCount(); i-- > 0;) {
      if (ip.get(i) == 0) {
        bp.set(i, 255);
      }
    }
    bp.outline();
    bp.invert();
    final ImageRoi roi = new ImageRoi(0, 0, bp);
    roi.setZeroTransparent(true);
    imp.setOverlay(new Overlay(roi));
  }
}
