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

package uk.ac.sussex.gdsc.ij.colocalisation;

import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.Prefs;
import ij.WindowManager;
import ij.gui.GUI;
import ij.gui.GenericDialog;
import ij.gui.Plot;
import ij.gui.Roi;
import ij.macro.MacroRunner;
import ij.plugin.frame.PlugInFrame;
import ij.process.ByteProcessor;
import ij.process.ColorProcessor;
import ij.process.ImageProcessor;
import ij.process.ShortProcessor;
import ij.text.TextWindow;
import java.awt.BorderLayout;
import java.awt.Button;
import java.awt.Checkbox;
import java.awt.Choice;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Frame;
import java.awt.GridLayout;
import java.awt.Label;
import java.awt.Panel;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.TextField;
import java.awt.event.ActionEvent;
import java.awt.event.ItemEvent;
import java.awt.event.WindowEvent;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JPanel;
import uk.ac.sussex.gdsc.core.ij.ImageJUtils;
import uk.ac.sussex.gdsc.core.utils.concurrent.ConcurrencyUtils;
import uk.ac.sussex.gdsc.ij.UsageTracker;

/**
 * Compares two images for correlated pixel intensities. If the two images are correlated a search
 * is performed for the threshold below which the two images are not correlated.
 *
 * <p>Supports stacks. Only a specific channel and time frame can be used and all input images must
 * have the same number of z-sections for the chosen channel/time frame.
 */
public class ColocalisationThreshold_PlugIn extends PlugInFrame {
  private static final long serialVersionUID = 20190105L;

  private static final String PLUGIN_TITLE = "CT Plugin";

  private static final String KEY_LOCATION = "CT.location";

  private static final String KEY_CHANNEL1_INDEX = "CT.channel1SelectedIndex";
  private static final String KEY_CHANNEL2_INDEX = "CT.channel2SelectedIndex";
  private static final String KEY_ROI_INDEX = "CT.roiIndex";
  private static final String KEY_R_THRESHOLD = "CT.rThreshold";
  private static final String KEY_SEARCH_TOLERANCE = "CT.searchTolerance";
  private static final String KEY_SHOW_COLOCALISED = "CT.showColocalised";
  private static final String KEY_USE_CONSTANT_INTENSITY = "CT.useConstantIntensity";
  private static final String KEY_SHOW_SCATTER_PLOT = "CT.showScatterPlot";
  private static final String KEY_INCLUDE_ZERO_ZERO_PIXELS = "CT.includeZeroZeroPixels";
  private static final String KEY_CLOSE_WINDOWS_ON_EXIT = "CT.closeWindowsOnExit";

  private static final String KEY_SHOW_THRESHOLDS = "CT.showThresholds";
  private static final String KEY_SHOW_LINEAR_REGRESSION = "CT.showLinearRegression";
  private static final String KEY_SHOW_R_TOTAL = "CT.showRTotal";
  private static final String KEY_SHOW_R_GT_T = "CT.showRForGtT";
  private static final String KEY_SHOW_R_LT_T = "CT.showRForLtT";
  private static final String KEY_SHOW_MANDERS = "CT.showManders";
  private static final String KEY_SHOW_MANDERS_GT_T = "CT.showMandersGtT";
  private static final String KEY_SHOW_N_COLOC = "CT.showNColoc";
  private static final String KEY_SHOW_VOLUME_COLOC = "CT.showVolumeColoc";
  private static final String KEY_SHOW_VOLUME_GT_T_COLOC = "CT.showVolumeGtTColoc";
  private static final String KEY_SHOW_INTENSITY_COLOC = "CT.showIntensityColoc";
  private static final String KEY_SHOW_INTENSITY_GT_T_COLOC = "CT.showIntensityGtTColoc";
  private static final String KEY_SHOW_ROIS_AND_MASKS = "CT.showRoisAndMasks";
  private static final String KEY_EXHAUSTIVE_SEARCH = "CT.exhaustiveSearch";
  private static final String KEY_PLOT_R_VALUES = "CT.plotRValues";
  private static final String KEY_MAX_ITERATIONS = "CT.maxIterations";

  private static final double DEFAULT_R_LIMIT = 0;
  private static final double DEFAULT_SEARCH_TOLERANCE = 0.05;
  private static final int FONT_WIDTH = 12;
  private static final Font MONO_FONT = new Font("Monospaced", 0, FONT_WIDTH);

  // Image titles
  private static final String CHANNEL1_TITLE = "CT Channel 1";
  private static final String CHANNEL2_TITLE = "CT Channel 2";
  private static final String THRESHOLD1_TITLE = "CT Pixels 1";
  private static final String THRESHOLD2_TITLE = "CT Pixels 2";
  private static final String COLOCALISED_PIXELS_TITLE = "CT Colocalised Pixels";
  private static final String CORRELATION_PLOT_TITLE = "CT correlation: ";
  private static final String CORRELATION_VALUES_TITLE = "R-values";
  private static final String[] resultsTitles = new String[] {CHANNEL1_TITLE, CHANNEL2_TITLE,
      THRESHOLD1_TITLE, THRESHOLD2_TITLE, CORRELATION_PLOT_TITLE, CORRELATION_VALUES_TITLE};

  // Options titles
  private static final String CHOICE_NO_ROI = "[None]";
  private static final String CHOICE_CHANNEL1 = "Channel 1";
  private static final String CHOICE_CHANNEL2 = "Channel 2";
  private static final String CHOICE_USE_ROI = "Use ROI";
  private static final String CHOICE_CORRELATION_THRESHOLD = "Correlation limit";
  private static final String CHOICE_SEARCG_TOLERANCE = "Search tolerance";
  private static final String CHOICE_SHOW_COLOCALISED = "Show colocalised pixels";
  private static final String CHOICE_USE_CONSTANCE_INTENSITY =
      "Use constant intensity for colocalised pixels";
  private static final String CHOICE_SHOW_SCATTER_PLOT = "Show Scatter plot";
  private static final String CHOICE_INCLUDE_ZERO_ZERO_PIXELS =
      "Include zero-zero pixels in threshold calculation";
  private static final String CHOICE_CLOSE_WINDOWS_ON_EXIT = "Close windows on exit";
  private static final String CHOICE_SET_RESULTS_OPTIONS = "Set results options";
  private static final String OK_BUTTON_LABEL = "Apply";
  private static final String HELP_BUTTON_LABEL = "Help";

  private static final AtomicReference<Frame> instance = new AtomicReference<>();

  // Used to show the results
  private static AtomicReference<TextWindow> textWindowRef = new AtomicReference<>();

  private Choice channel1List;
  private Choice channel2List;
  private Choice roiList;
  private TextField correlationThresholdTextField;
  private TextField searchToleranceTextField;
  private Checkbox showColocalisedCheckbox;
  private Checkbox useConstantIntensityCheckbox;
  private Checkbox showScatterPlotCheckbox;
  private Checkbox includeZeroZeroPixelsCheckbox;
  private Checkbox closeWindowsOnExitCheckbox;
  private Checkbox setResultsOptionsCheckbox;
  private Button okButton;
  private Button helpButton;

  private int channel1SelectedIndex = (int) Prefs.get(KEY_CHANNEL1_INDEX, 0);
  private int channel2SelectedIndex = (int) Prefs.get(KEY_CHANNEL2_INDEX, 0);
  private int roiIndex = (int) Prefs.get(KEY_ROI_INDEX, 0);
  private double correlationThreshold = Prefs.get(KEY_R_THRESHOLD, DEFAULT_R_LIMIT);
  private double searchTolerance = Prefs.get(KEY_SEARCH_TOLERANCE, DEFAULT_SEARCH_TOLERANCE);
  private boolean showColocalised = Prefs.get(KEY_SHOW_COLOCALISED, false);
  private boolean useConstantIntensity = Prefs.get(KEY_USE_CONSTANT_INTENSITY, false);
  private boolean showScatterPlot = Prefs.get(KEY_SHOW_SCATTER_PLOT, false);
  private boolean includeZeroZeroPixels = Prefs.get(KEY_INCLUDE_ZERO_ZERO_PIXELS, true);
  private boolean closeWindowsOnExit = Prefs.get(KEY_CLOSE_WINDOWS_ON_EXIT, true);

  // Results options
  private boolean showThresholds = Prefs.get(KEY_SHOW_THRESHOLDS, true);
  private boolean showLinearRegression = Prefs.get(KEY_SHOW_LINEAR_REGRESSION, true);
  private boolean showRTotal = Prefs.get(KEY_SHOW_R_TOTAL, true);
  private boolean showRForGtT = Prefs.get(KEY_SHOW_R_GT_T, true);
  private boolean showRForLtT = Prefs.get(KEY_SHOW_R_LT_T, true);
  private boolean showManders = Prefs.get(KEY_SHOW_MANDERS, true);
  private boolean showMandersGtT = Prefs.get(KEY_SHOW_MANDERS_GT_T, true);
  private boolean showNColoc = Prefs.get(KEY_SHOW_N_COLOC, true);
  private boolean showVolumeColoc = Prefs.get(KEY_SHOW_VOLUME_COLOC, true);
  private boolean showVolumeGtTColoc = Prefs.get(KEY_SHOW_VOLUME_GT_T_COLOC, true);
  private boolean showIntensityColoc = Prefs.get(KEY_SHOW_INTENSITY_COLOC, true);
  private boolean showIntensityGtTColoc = Prefs.get(KEY_SHOW_INTENSITY_GT_T_COLOC, true);
  private boolean showRoisAndMasks = Prefs.get(KEY_SHOW_ROIS_AND_MASKS, true);
  private boolean exhaustiveSearch = Prefs.get(KEY_EXHAUSTIVE_SEARCH, false);
  private boolean plotRValues = Prefs.get(KEY_PLOT_R_VALUES, true);
  private int maxIterations = (int) Prefs.get(KEY_MAX_ITERATIONS, 50);

  // Windows that are opened by the plug-in.
  // These should be closed on exit.
  private int scatterPlot;
  private int channel1Rgb;
  private int channel2Rgb;
  private int segmented1Rgb;
  private int segmented2Rgb;
  private int mixChannel;

  // Store the channels and frames to use from image stacks
  private final int[] sliceOptions = new int[4];

  // Stores the list of images last used in the selection options
  private ArrayList<String> imageList;

  /**
   * Instantiates a new colocalisation threshold plugin.
   */
  public ColocalisationThreshold_PlugIn() {
    super(PLUGIN_TITLE);
  }

  @Override
  public void run(String arg) {
    UsageTracker.recordPlugin(this.getClass(), arg);

    if (WindowManager.getImageCount() == 0) {
      IJ.error(PLUGIN_TITLE, "No images opened.");
      return;
    }

    final Frame frame = instance.get();

    if (frame != null) {
      frame.toFront();
      return;
    }

    instance.set(this);
    IJ.register(ColocalisationThreshold_PlugIn.class);
    WindowManager.addWindow(this);

    createFrame();
    setup();

    addKeyListener(IJ.getInstance());
    pack();
    final Point loc = Prefs.getLocation(KEY_LOCATION);
    if (loc != null) {
      setLocation(loc);
    } else {
      GUI.center(this);
    }
    if (IJ.isMacOSX()) {
      setResizable(false);
    }
    setVisible(true);
  }

  private void setup() {
    final ImagePlus imp = WindowManager.getCurrentImage();
    if (imp == null) {
      return;
    }
    fillImagesList();
  }

  @SuppressWarnings("unused")
  private synchronized void actionPerformed(ActionEvent event) {
    final Object actioner = event.getSource();

    if (actioner == null) {
      return;
    }

    if (((Button) actioner == okButton) && (parametersReady())) {
      final Thread thread = new Thread(() -> {
        findThreshold();
        synchronized (ColocalisationThreshold_PlugIn.this) {
          super.notifyAll();
        }
      }, "ColicalisationThreshold_Plugin");
      thread.start();
    }
    if ((Button) actioner == helpButton) {
      final String macro =
          "run('URL...', 'url=" + uk.ac.sussex.gdsc.ij.help.Urls.COLOCALISATION + "');";
      new MacroRunner(macro);
    }

    super.notifyAll();
  }

  private void itemStateChanged(@SuppressWarnings("unused") ItemEvent event) {
    if (setResultsOptionsCheckbox.getState()) {
      setResultsOptionsCheckbox.setState(false);

      final GenericDialog gd = new GenericDialog("Set Results Options");

      gd.addCheckbox("Show linear regression solution", showLinearRegression);
      gd.addCheckbox("Show thresholds", showThresholds);
      gd.addCheckbox("Pearson's for whole image", showRTotal);
      gd.addCheckbox("Pearson's for image above thresholds", showRForGtT);
      gd.addCheckbox("Pearson's for image below thresholds (should be ~0)", showRForLtT);
      gd.addCheckbox("Mander's original coefficients (threshold = 0)", showManders);
      gd.addCheckbox("Mander's using thresholds", showMandersGtT);
      gd.addCheckbox("Number of colocalised voxels", showNColoc);
      gd.addCheckbox("% Volume colocalised", showVolumeColoc);
      gd.addCheckbox("% Volume above threshold colocalised", showVolumeGtTColoc);
      gd.addCheckbox("% Intensity colocalised", showIntensityColoc);
      gd.addCheckbox("% Intensity above threshold colocalised", showIntensityGtTColoc);
      gd.addCheckbox("Output ROIs/Masks", showRoisAndMasks);
      gd.addCheckbox("Exhaustive search", exhaustiveSearch);
      gd.addCheckbox("Plot R values", plotRValues);
      gd.addNumericField("Max. iterations", maxIterations, 0);

      gd.showDialog();

      if (gd.wasCanceled()) {
        return;
      }

      showThresholds = gd.getNextBoolean();
      showLinearRegression = gd.getNextBoolean();
      showRTotal = gd.getNextBoolean();
      showRForGtT = gd.getNextBoolean();
      showRForLtT = gd.getNextBoolean();
      showManders = gd.getNextBoolean();
      showMandersGtT = gd.getNextBoolean();
      showNColoc = gd.getNextBoolean();
      showVolumeColoc = gd.getNextBoolean();
      showVolumeGtTColoc = gd.getNextBoolean();
      showIntensityColoc = gd.getNextBoolean();
      showIntensityGtTColoc = gd.getNextBoolean();
      showRoisAndMasks = gd.getNextBoolean();
      exhaustiveSearch = gd.getNextBoolean();
      plotRValues = gd.getNextBoolean();
      maxIterations = (int) gd.getNextNumber();
    }
  }

  @Override
  public void close() {
    Prefs.saveLocation(KEY_LOCATION, getLocation());

    if (closeWindowsOnExit) {
      closeImagePlus(channel1Rgb);
      closeImagePlus(channel2Rgb);
      closeImagePlus(segmented1Rgb);
      closeImagePlus(segmented2Rgb);
      closeImagePlus(mixChannel);
      closeImagePlus(scatterPlot);

      final TextWindow window = textWindowRef.get();
      if (ImageJUtils.isShowing(window)) {
        window.close();
      }
    }

    instance.compareAndSet(this, null);
    super.close();
  }

  private static void closeImagePlus(int id) {
    final ImagePlus imp = WindowManager.getImage(id);
    if (imp != null) {
      imp.close();
    }
  }

  @Override
  public void windowActivated(WindowEvent event) {
    fillImagesList();

    super.windowActivated(event);
    WindowManager.setWindow(this);
  }

  private void findThreshold() {
    if (!parametersReady()) {
      return;
    }

    // Read settings
    channel1SelectedIndex = channel1List.getSelectedIndex();
    channel2SelectedIndex = channel2List.getSelectedIndex();
    roiIndex = roiList.getSelectedIndex();
    correlationThreshold = getDouble(correlationThresholdTextField.getText(), DEFAULT_R_LIMIT);
    searchTolerance = getDouble(searchToleranceTextField.getText(), DEFAULT_SEARCH_TOLERANCE);
    showColocalised = showColocalisedCheckbox.getState();
    useConstantIntensity = useConstantIntensityCheckbox.getState();
    showScatterPlot = showScatterPlotCheckbox.getState();
    includeZeroZeroPixels = includeZeroZeroPixelsCheckbox.getState();
    closeWindowsOnExit = closeWindowsOnExitCheckbox.getState();

    ImagePlus imp1 = WindowManager.getImage(channel1List.getSelectedItem());
    ImagePlus imp2 = WindowManager.getImage(channel2List.getSelectedItem());

    final int width1 = imp1.getWidth();
    final int width2 = imp2.getWidth();
    final int height1 = imp1.getHeight();
    final int height2 = imp2.getHeight();

    if ((width1 != width2) || (height1 != height2)) {
      IJ.showMessage(PLUGIN_TITLE, "Both images (stacks) must be at the same height and width");
      return;
    }

    // Check for image stacks and get the channel and frame (if applicable)
    if (!getStackOptions(imp1, imp2)) {
      return;
    }

    // This should not be a problem but leave it in for now
    if ((imp1.getType() != ImagePlus.GRAY8 && imp1.getType() != ImagePlus.GRAY16)
        || (imp2.getType() != ImagePlus.GRAY8 && imp2.getType() != ImagePlus.GRAY16)) {
      IJ.showMessage("Image Correlator", "Images must be 8-bit or 16-bit grayscale.");
      return;
    }

    // Extract images
    imp1 = createImagePlus(imp1, 0);
    imp2 = createImagePlus(imp2, 2);

    if (imp1.getNSlices() != imp2.getNSlices()) {
      IJ.showMessage(PLUGIN_TITLE, "Both images (stacks) must be at the same depth");
      return;
    }

    correlate(imp1, imp2);
  }

  private static double getDouble(String value, double defaultValue) {
    try {
      return Double.parseDouble(value);
    } catch (final Exception ex) {
      return defaultValue;
    }
  }

  private boolean getStackOptions(ImagePlus imp1, ImagePlus imp2) {
    if (isStack(imp1) || isStack(imp2)) {
      final GenericDialog gd = new GenericDialog("Slice options");
      gd.addMessage("Stacks detected. Please select the slices.");

      boolean added = false;
      added |= addOptions(gd, imp1, "Image 1", 0);
      added |= addOptions(gd, imp2, "Image 2", 2);

      if (added) {
        gd.showDialog();

        if (gd.wasCanceled()) {
          return false;
        }

        // Populate the channels and frames into an options array
        getOptions(gd, imp1, 0);
        getOptions(gd, imp2, 2);
      }
    }
    return true;
  }

  private static boolean isStack(ImagePlus imp) {
    return (imp != null && imp.getStackSize() > 1);
  }

  private boolean addOptions(GenericDialog gd, ImagePlus imp, String title, int offset) {
    boolean added = false;
    if (imp != null) {
      final String[] channels = getChannels(imp);
      final String[] frames = getFrames(imp);

      if (channels.length > 1 || frames.length > 1) {
        added = true;
        gd.addMessage(title);
      }

      setOption(gd, channels, "Channel", offset);
      setOption(gd, frames, "Frame", offset + 1);
    }
    return added;
  }

  private boolean setOption(GenericDialog gd, String[] choices, String title, int offset) {
    if (choices.length > 1) {
      // Restore previous selection
      final int c = (sliceOptions[offset] > 0 && sliceOptions[offset] <= choices.length)
          ? sliceOptions[offset] - 1
          : 0;
      gd.addChoice(title, choices, choices[c]);
      return true;
    }
    // Set to default
    sliceOptions[offset] = 1;
    return false;
  }

  private static String[] getChannels(ImagePlus imp) {
    final int c = imp.getNChannels();
    final String[] result = new String[c];
    for (int i = 0; i < c; i++) {
      result[i] = Integer.toString(i + 1);
    }
    return result;
  }

  private static String[] getFrames(ImagePlus imp) {
    final int c = imp.getNFrames();
    final String[] result = new String[c];
    for (int i = 0; i < c; i++) {
      result[i] = Integer.toString(i + 1);
    }
    return result;
  }

  private void getOptions(GenericDialog gd, ImagePlus imp, int offset) {
    if (imp != null) {
      final String[] channels = getChannels(imp);
      final String[] frames = getFrames(imp);

      if (channels.length > 1) {
        sliceOptions[offset] = gd.getNextChoiceIndex() + 1;
      }
      if (frames.length > 1) {
        sliceOptions[offset + 1] = gd.getNextChoiceIndex() + 1;
      }
    }
  }

  private ImagePlus createImagePlus(ImagePlus originalImp, int offset) {
    final int channel = sliceOptions[offset];
    final int frame = sliceOptions[offset + 1];
    final int slices = originalImp.getNSlices();

    final ImageStack stack = new ImageStack(originalImp.getWidth(), originalImp.getHeight());
    final ImageStack inputStack = originalImp.getImageStack();

    for (int slice = 1; slice <= slices; slice++) {
      // Convert to a short processor
      final ImageProcessor ip =
          inputStack.getProcessor(originalImp.getStackIndex(channel, slice, frame));
      stack.addSlice(null, ip);
    }

    final StringBuilder sb = new StringBuilder(originalImp.getTitle());
    if (slices > 1 && (originalImp.getNChannels() > 1 || originalImp.getNFrames() > 1)) {
      sb.append(" (c").append(channel).append(",t").append(frame).append(")");
    }

    final ImagePlus imp = new ImagePlus(sb.toString(), stack);
    final Roi roi = originalImp.getRoi();
    if (roi != null) {
      imp.setRoi(roi);
    }
    return imp;
  }

  /**
   * Perform correlation analysis on two images.
   *
   * @param imp1 the image 1
   * @param imp2 the image 2
   */
  public void correlate(ImagePlus imp1, ImagePlus imp2) {
    final ImageStack img1 = imp1.getStack();
    final ImageStack img2 = imp2.getStack();

    // Start regression
    IJ.showStatus("Performing regression. Press 'Esc' to abort");

    ColocalisationThreshold ct;
    try {
      ct = new ColocalisationThreshold(imp1, imp2, roiIndex);
      ct.setIncludeNullPixels(includeZeroZeroPixels);
      ct.setMaxIterations(maxIterations);
      ct.setExhaustiveSearch(exhaustiveSearch);
      ct.setCorrelationThreshold(correlationThreshold);
      ct.setSearchTolerance(searchTolerance);
      if (!ct.correlate()) {
        IJ.showMessage(PLUGIN_TITLE, "No correlation found above tolerance. Ending");
        IJ.showStatus("Done");
        return;
      }
    } catch (final Exception ex) {
      IJ.error(PLUGIN_TITLE, ex.getMessage());
      IJ.showStatus("Done");
      return;
    }

    IJ.showStatus("Generating results");

    // Set up the ROI
    ImageProcessor ipMask = null;
    Rectangle roiRect = null;

    if (roiIndex != 0) {
      final ImagePlus roiImage = (roiIndex == 1) ? imp1 : imp2;
      final Roi roi = roiImage.getRoi();

      if (roi != null) {
        roiRect = roi.getBounds();

        // Use a mask for an irregular ROI
        if (roi.getType() != Roi.RECTANGLE) {
          ipMask = roiImage.getMask();
        }
      } else {
        // Reset the choice for next time
        roiIndex = 0;
      }
    }

    saveOptions();

    final int nslices = imp1.getStackSize();
    final int width = imp1.getWidth();
    final int height = imp1.getHeight();
    int xoffset;
    int yoffset;
    int rwidth;
    int rheight;

    if (roiRect == null) {
      xoffset = 0;
      yoffset = 0;
      rwidth = width;
      rheight = height;
    } else {
      xoffset = roiRect.x;
      yoffset = roiRect.y;
      rwidth = roiRect.width;
      rheight = roiRect.height;
    }

    final ImageProcessor scatterPlotData = new ShortProcessor(256, 256);
    int scaledC1ThresholdValue = 0;
    int scaledC2ThresholdValue = 0;
    imp1.getCurrentSlice();

    final int ch1threshmax = ct.getThreshold1();
    final int ch2threshmax = ct.getThreshold2();

    long numberOfPixels = 0;
    long nch1gtT = 0;
    long nch2gtT = 0;
    long nzero = 0;
    long ncolocalised = 0;

    long sumCh1 = 0;
    long sumCh2 = 0;
    long sumCh1WhereCh2gt0 = 0;
    long sumCh2WhereCh1gt0 = 0;
    long sumCh1WhereCh2gtT = 0;
    long sumCh2WhereCh1gtT = 0;
    long sumCh1gtT = 0;
    long sumCh2gtT = 0;
    long sumCh1Coloc = 0;
    long sumCh2Coloc = 0;

    final int ch1Max = ct.getCh1Max();
    final int ch2Max = ct.getCh2Max();
    double ch1Scaling = (double) 255 / (double) ch1Max;
    double ch2Scaling = (double) 255 / (double) ch2Max;
    if (ch1Scaling > 1) {
      ch1Scaling = 1;
    }
    if (ch2Scaling > 1) {
      ch2Scaling = 1;
    }

    final ImageStack stackColoc = new ImageStack(rwidth, rheight);

    // These will be used to store the output image ROIs
    ImageStack outputStack1 = null;
    ImageStack outputStack2 = null;
    // These will be used to store the output masks
    final ImageStack outputMask1 = new ImageStack(rwidth, rheight);
    final ImageStack outputMask2 = new ImageStack(rwidth, rheight);

    // If an ROI was used then an image should be extracted
    if (roiRect != null) {
      outputStack1 = new ImageStack(rwidth, rheight);
      outputStack2 = new ImageStack(rwidth, rheight);
    }

    for (int s = 1; s <= nslices; s++) {
      final ImageProcessor ip1 = img1.getProcessor(s);
      final ImageProcessor ip2 = img2.getProcessor(s);

      final ColorProcessor ipColoc = new ColorProcessor(rwidth, rheight);
      ImageProcessor out1 = null;
      ImageProcessor out2 = null;
      final ByteProcessor mask1 = new ByteProcessor(rwidth, rheight);
      final ByteProcessor mask2 = new ByteProcessor(rwidth, rheight);

      if (outputStack1 != null) {
        // Create output processors of the same type
        out1 = createProcessor(imp1, rwidth, rheight);
        out2 = createProcessor(imp2, rwidth, rheight);
      }

      for (int y = 0; y < rheight; y++) {
        for (int x = 0; x < rwidth; x++) {
          if (ipMask == null || ipMask.get(x, y) != 0) {
            int index = (y + yoffset) * ip1.getWidth() + x + xoffset;
            final int ch1 = ip1.get(index);
            final int ch2 = ip2.get(index);

            final int outIndex = y * rwidth + x;
            if (out1 != null) {
              out1.set(outIndex, ch1);
              out2.set(outIndex, ch2);
            }

            final int scaledCh1 = (int) (ch1 * ch1Scaling);
            final int scaledCh2 = (int) (ch2 * ch2Scaling);

            // Pack a RGB
            int color = (scaledCh1 << 16) + (scaledCh2 << 8);

            ipColoc.set(outIndex, color);

            sumCh1 += ch1;
            sumCh2 += ch2;
            numberOfPixels++;

            scaledC1ThresholdValue = scaledCh1;
            scaledC2ThresholdValue = 255 - scaledCh2;
            index = scaledC2ThresholdValue * scatterPlotData.getWidth() + scaledC1ThresholdValue;
            int count = scatterPlotData.get(index);
            count++;
            scatterPlotData.set(index, count);

            if (ch1 + ch2 == 0) {
              nzero++;
            }

            if (ch1 > 0) {
              sumCh2WhereCh1gt0 += ch2;
            }
            if (ch2 > 0) {
              sumCh1WhereCh2gt0 += ch1;
            }

            if (ch1 >= ch1threshmax) {
              nch1gtT++;
              sumCh1gtT += ch1;
              sumCh2WhereCh1gtT += ch2;
              mask1.set(outIndex, 255);
            }
            if (ch2 >= ch2threshmax) {
              nch2gtT++;
              sumCh2gtT += ch2;
              sumCh1WhereCh2gtT += ch1;
              mask2.set(outIndex, 255);

              if (ch1 >= ch1threshmax) {
                sumCh1Coloc += ch1;
                sumCh2Coloc += ch2;
                ncolocalised++;

                // This is the blue component
                if (useConstantIntensity) {
                  color += 255;
                } else {
                  color += (int) Math.sqrt((double) scaledCh1 * scaledCh2);
                }
                ipColoc.set(outIndex, color);
              }
            }
          }
        }
      }

      stackColoc.addSlice(COLOCALISED_PIXELS_TITLE + "." + s, ipColoc);

      if (outputStack1 != null) {
        outputStack1.addSlice(CHANNEL1_TITLE + "." + s, out1);
        outputStack2.addSlice(CHANNEL2_TITLE + "." + s, out2);
      }
      outputMask1.addSlice(THRESHOLD1_TITLE + "." + s, mask1);
      outputMask2.addSlice(THRESHOLD2_TITLE + "." + s, mask2);
    }

    final long totalPixels = numberOfPixels;
    if (!includeZeroZeroPixels) {
      numberOfPixels -= nzero;
    }

    // Pearsons for colocalised volume -
    // Should get this directly from the Colocalisation object
    final double rColoc = ct.getCorrelationAboveThreshold();

    // Mander's original
    // [i.e. E(ch1 if ch2>0) / E(ch1total)]
    // (How much of channel 1 intensity occurs where channel 2 has signal)

    final double m1 = (double) sumCh1WhereCh2gt0 / sumCh1;
    final double m2 = (double) sumCh2WhereCh1gt0 / sumCh2;

    // Manders using threshold
    // [i.e. E(ch1 if ch2>ch2threshold) / E(ch1total)]
    // This matches other plug-ins, i.e. how much of channel 1 intensity occurs where channel 2 is
    // correlated
    final double m1threshold = (double) sumCh1WhereCh2gtT / sumCh1;
    final double m2threshold = (double) sumCh2WhereCh1gtT / sumCh2;

    // as in Coste's paper
    // [i.e. E(ch1 > ch1threshold) / E(ch1total)]
    // This appears to be wrong when compared to other plug-ins
    // m1threshold = (double) sumCh1gtT / sumCh1
    // m2threshold = (double) sumCh2gtT / sumCh2

    // Imaris percentage volume
    final double percVolCh1 = (double) ncolocalised / (double) nch1gtT;
    final double percVolCh2 = (double) ncolocalised / (double) nch2gtT;

    final double percTotCh1 = (double) sumCh1Coloc / (double) sumCh1;
    final double percTotCh2 = (double) sumCh2Coloc / (double) sumCh2;

    // Imaris percentage material
    final double percGtTCh1 = (double) sumCh1Coloc / (double) sumCh1gtT;
    final double percGtTCh2 = (double) sumCh2Coloc / (double) sumCh2gtT;

    // Create results window
    final String resultsTitle = PLUGIN_TITLE + " Results";
    final TextWindow textWindow = openResultsWindow(resultsTitle);
    final String imageTitle = createImageTitle(imp1, imp2);

    showResults(textWindow, imageTitle, ch1threshmax, ch2threshmax, numberOfPixels, nzero, nch1gtT,
        nch2gtT, ct, ncolocalised, rColoc, m1, m2, m1threshold, m2threshold, percVolCh1, percVolCh2,
        percTotCh1, percTotCh2, percGtTCh1, percGtTCh2, totalPixels);

    if (showColocalised) {
      mixChannel = ImageJUtils.display(COLOCALISED_PIXELS_TITLE, stackColoc).getID();
    }

    if (showRoisAndMasks) {
      if (outputStack1 != null) {
        channel1Rgb = ImageJUtils.display(CHANNEL1_TITLE, outputStack1).getID();
        channel2Rgb = ImageJUtils.display(CHANNEL2_TITLE, outputStack2).getID();
      }

      segmented1Rgb = ImageJUtils.display(THRESHOLD1_TITLE, outputMask1).getID();
      segmented2Rgb = ImageJUtils.display(THRESHOLD2_TITLE, outputMask2).getID();
    }

    if (showScatterPlot) {
      showScatterPlot(ct, ch1threshmax, ch2threshmax, scatterPlotData, ch1Max, ch1Scaling,
          ch2Scaling, imageTitle);
    }

    if (plotRValues) {
      plotResults(ct.getResults());
    }

    IJ.selectWindow(resultsTitle);
    IJ.showStatus("Done");
  }

  private static String createImageTitle(ImagePlus imp1, ImagePlus imp2) {
    return imp1.getTitle() + " & " + imp2.getTitle();
  }

  private static ImageProcessor createProcessor(ImagePlus imp, int rwidth, int rheight) {
    if (imp.getType() == ImagePlus.GRAY8) {
      return new ByteProcessor(rwidth, rheight);
    }
    return new ShortProcessor(rwidth, rheight);
  }

  private void showScatterPlot(ColocalisationThreshold ct, int ch1threshmax, int ch2threshmax,
      ImageProcessor scatterPlotData, int ch1Max, double ch1Scaling, double ch2Scaling,
      String fileName) {
    int scaledC1ThresholdValue;
    int scaledC2ThresholdValue;
    double plotY = 0;
    scatterPlotData.resetMinAndMax();
    final int plotmax2 = (int) (scatterPlotData.getMax());
    final int plotmax = plotmax2 / 2;

    scaledC1ThresholdValue = (int) (ch1threshmax * ch1Scaling);
    scaledC2ThresholdValue = 255 - (int) (ch2threshmax * ch2Scaling);

    final double m = ct.getM();
    final double b = ct.getB();

    // Draw regression line
    for (int c = (ch1Max < 256) ? 256 : ch1Max; c-- > 0;) {
      plotY = (c * m) + b;

      final int scaledXValue = (int) (c * ch1Scaling);
      final int scaledYValue = 255 - (int) (plotY * ch2Scaling);

      scatterPlotData.putPixel(scaledXValue, scaledYValue, plotmax);

      // Draw threshold lines
      scatterPlotData.putPixel(scaledXValue, scaledC2ThresholdValue, plotmax);
      scatterPlotData.putPixel(scaledC1ThresholdValue, scaledXValue, plotmax);
    }

    final ImagePlus scatterPlotImp =
        ImageJUtils.display(CORRELATION_PLOT_TITLE + fileName, scatterPlotData);
    scatterPlot = scatterPlotImp.getID();
    IJ.selectWindow(scatterPlotImp.getTitle());
    IJ.run("Enhance Contrast", "saturated=50 equalize");
    IJ.run("Fire");
  }

  private void showResults(TextWindow window, String fileName, int ch1threshmax, int ch2threshmax,
      long numberOfPixels, long nzero, long nch1gtT, long nch2gtT, ColocalisationThreshold ct,
      long numberColocalised, double correlationColocalised, double m1, double m2,
      double m1threshold, double m2threshold, double percVolCh1, double percVolCh2,
      double percTotCh1, double percTotCh2, double percGtTCh1, double percGtTCh2,
      double totalPixels) {
    final StringBuilder str = new StringBuilder();
    str.append(fileName).append('\t');
    switch (roiIndex) {
      case 0:
        str.append(CHOICE_NO_ROI);
        break;
      case 1:
        str.append(CHOICE_CHANNEL1);
        break;
      default:
        str.append(CHOICE_CHANNEL2);
        break;
    }

    final DecimalFormat df4 = new DecimalFormat("##0.0000");
    final DecimalFormat df3 = new DecimalFormat("##0.000");
    final DecimalFormat df2 = new DecimalFormat("##0.00");
    final DecimalFormat df1 = new DecimalFormat("##0.0");
    final DecimalFormat df0 = new DecimalFormat("##0");

    str.append((includeZeroZeroPixels) ? "\tincl.\t" : "\texcl.\t");

    if (showRTotal) {
      appendFormat(str, ct.getRTotal(), df3);
    }
    if (showLinearRegression) {
      final double m = ct.getM();
      final double b = ct.getB();
      appendFormat(str, m, df3);
      appendFormat(str, b, df1);
    }
    if (showThresholds) {
      appendFormat(str, ch1threshmax, df0);
      appendFormat(str, ch2threshmax, df0);
    }
    if (showRForGtT) {
      appendFormat(str, correlationColocalised, df4);
    }
    if (showRForLtT) {
      appendFormat(str, ct.getCorrelationBelowThreshold(), df3);
    }
    if (showManders) {
      appendFormat(str, m1, df4);
      appendFormat(str, m2, df4);
    }
    if (showMandersGtT) {
      appendFormat(str, m1threshold, df4);
      appendFormat(str, m2threshold, df4);
    }
    if (showNColoc) {
      appendFormat(str, numberOfPixels, df0);
      appendFormat(str, nzero, df0);
      appendFormat(str, nch1gtT, df0);
      appendFormat(str, nch2gtT, df0);
      appendFormat(str, numberColocalised, df0);
    }
    if (showVolumeColoc) {
      appendFormat(str, (numberColocalised * 100.0) / totalPixels, df2, "%");
    }
    if (showVolumeGtTColoc) {
      appendFormat(str, percVolCh1 * 100.0, df2, "%");
      appendFormat(str, percVolCh2 * 100.0, df2, "%");
    }
    if (showIntensityColoc) {
      appendFormat(str, percTotCh1 * 100.0, df2, "%");
      appendFormat(str, percTotCh2 * 100.0, df2, "%");
    }
    if (showIntensityGtTColoc) {
      appendFormat(str, percGtTCh1 * 100.0, df2, "%");
      appendFormat(str, percGtTCh2 * 100.0, df2, "%");
    }

    window.append(str.toString());
  }

  private TextWindow openResultsWindow(String resultsTitle) {
    final StringBuilder heading = createHeading();
    return ConcurrencyUtils.refresh(textWindowRef,
        // Test the window is showing with the same headings
        window -> ImageJUtils.isShowing(window)
            && window.getTextPanel().getColumnHeadings().contentEquals(heading),
        () -> new TextWindow(resultsTitle, heading.toString(), "", 1000, 300));
  }

  private StringBuilder createHeading() {
    final StringBuilder heading = new StringBuilder("Images\tROI\tZeroZero\t");

    if (showRTotal) {
      heading.append("Rtotal\t");
    }
    if (showLinearRegression) {
      heading.append("m\tb\t");
    }
    if (showThresholds) {
      heading.append("Ch1 thresh\tCh2 thresh\t");
    }
    if (showRForGtT) {
      heading.append("Rcoloc\t");
    }
    if (showRForLtT) {
      heading.append("R<threshold\t");
    }
    if (showManders) {
      heading.append("M1\tM2\t");
    }
    if (showMandersGtT) {
      heading.append("tM1\ttM2\t");
    }
    if (showNColoc) {
      heading.append("N\t");
      heading.append("nZero\t");
      heading.append("nCh1gtT\t");
      heading.append("nCh2gtT\t");
      heading.append("nColoc\t");
    }
    if (showVolumeColoc) {
      heading.append("%Vol Coloc\t");
    }
    if (showVolumeGtTColoc) {
      heading.append("%Ch1gtT Vol Coloc\t");
      heading.append("%Ch2gtT Vol Coloc\t");
    }
    if (showIntensityColoc) {
      heading.append("%Ch1 Int Coloc\t");
      heading.append("%Ch2 Int Coloc\t");
    }
    if (showIntensityGtTColoc) {
      heading.append("%Ch1gtT Int Coloc\t");
      heading.append("%Ch2gtT Int Coloc\t");
    }
    heading.append("\n");
    return heading;
  }

  private static void appendFormat(StringBuilder str, double value, DecimalFormat format) {
    if (Double.isNaN(value)) {
      str.append("NaN");
    } else {
      str.append(format.format(value));
    }
    str.append('\t');
  }

  private static void appendFormat(StringBuilder str, double value, DecimalFormat format,
      String units) {
    if (Double.isNaN(value)) {
      str.append("NaN");
    } else {
      str.append(format.format(value)).append(units);
    }
    str.append('\t');
  }

  private void saveOptions() {
    Prefs.set(KEY_CHANNEL1_INDEX, channel1SelectedIndex);
    Prefs.set(KEY_CHANNEL2_INDEX, channel2SelectedIndex);
    Prefs.set(KEY_ROI_INDEX, roiIndex);
    Prefs.set(KEY_R_THRESHOLD, correlationThreshold);
    Prefs.set(KEY_SEARCH_TOLERANCE, searchTolerance);
    Prefs.set(KEY_SHOW_COLOCALISED, showColocalised);
    Prefs.set(KEY_USE_CONSTANT_INTENSITY, useConstantIntensity);
    Prefs.set(KEY_SHOW_SCATTER_PLOT, showScatterPlot);
    Prefs.set(KEY_INCLUDE_ZERO_ZERO_PIXELS, includeZeroZeroPixels);
    Prefs.set(KEY_CLOSE_WINDOWS_ON_EXIT, closeWindowsOnExit);

    Prefs.set(KEY_SHOW_THRESHOLDS, showThresholds);
    Prefs.set(KEY_SHOW_LINEAR_REGRESSION, showLinearRegression);
    Prefs.set(KEY_SHOW_R_TOTAL, showRTotal);
    Prefs.set(KEY_SHOW_R_GT_T, showRForGtT);
    Prefs.set(KEY_SHOW_R_LT_T, showRForLtT);
    Prefs.set(KEY_SHOW_MANDERS, showManders);
    Prefs.set(KEY_SHOW_MANDERS_GT_T, showMandersGtT);
    Prefs.set(KEY_SHOW_N_COLOC, showNColoc);
    Prefs.set(KEY_SHOW_VOLUME_COLOC, showVolumeColoc);
    Prefs.set(KEY_SHOW_VOLUME_GT_T_COLOC, showVolumeGtTColoc);
    Prefs.set(KEY_SHOW_INTENSITY_COLOC, showIntensityColoc);
    Prefs.set(KEY_SHOW_INTENSITY_GT_T_COLOC, showIntensityGtTColoc);
    Prefs.set(KEY_SHOW_ROIS_AND_MASKS, showRoisAndMasks);
    Prefs.set(KEY_EXHAUSTIVE_SEARCH, exhaustiveSearch);
    Prefs.set(KEY_PLOT_R_VALUES, plotRValues);
    Prefs.set(KEY_MAX_ITERATIONS, maxIterations);
  }

  private boolean parametersReady() {
    if (channel1List.getItemCount() == 0) {
      IJ.showMessage(PLUGIN_TITLE,
          "No available images. Images must be 8-bit or 16-bit grayscale.");
      return false;
    }

    return ((channel1List.getSelectedIndex() != -1) && (channel2List.getSelectedIndex() != -1)
        && (channel1List.getItemCount() != 0) && (channel2List.getItemCount() != 0));
  }

  /**
   * Fill the image list with currently open images.
   */
  public void fillImagesList() {
    // Find the currently open images
    final ArrayList<String> newImageList = new ArrayList<>();

    for (final int id : uk.ac.sussex.gdsc.core.ij.ImageJUtils.getIdList()) {
      final ImagePlus imp = WindowManager.getImage(id);

      // Image must be 8-bit/16-bit
      if (imp != null && (imp.getType() == ImagePlus.GRAY8 || imp.getType() == ImagePlus.GRAY16)) {
        // Exclude previous results
        if (previousResult(imp.getTitle())) {
          continue;
        }

        newImageList.add(imp.getTitle());
      }
    }

    // Check if the image list has changed
    if (newImageList.equals(imageList)) {
      return;
    }

    imageList = newImageList;

    // Re-populate the displayed image lists
    channel1List.removeAll();
    channel2List.removeAll();

    for (final String imageTitle : newImageList) {
      channel1List.add(imageTitle);
      channel2List.add(imageTitle);
    }

    // Ensure the drop-downs are resized
    pack();

    // Restore previous selection
    if ((channel1SelectedIndex < channel1List.getItemCount()) && (channel1SelectedIndex >= 0)) {
      channel1List.select(channel1SelectedIndex);
    }
    if ((channel2SelectedIndex < channel2List.getItemCount()) && (channel2SelectedIndex >= 0)) {
      channel2List.select(channel2SelectedIndex);
    }

    roiList.select(roiIndex);
  }

  private static boolean previousResult(String title) {
    for (final String resultTitle : resultsTitles) {
      if (title.startsWith(resultTitle)) {
        return true;
      }
    }
    return false;
  }

  private void createFrame() {
    final Panel mainPanel = new Panel();
    final GridLayout mainGrid = new GridLayout(0, 1);
    mainGrid.setHgap(10);
    mainGrid.setVgap(10);
    mainPanel.setLayout(mainGrid);
    add(mainPanel);

    channel1List = new Choice();
    mainPanel.add(createChoicePanel(channel1List, CHOICE_CHANNEL1));

    channel2List = new Choice();
    mainPanel.add(createChoicePanel(channel2List, CHOICE_CHANNEL2));

    roiList = new Choice();
    mainPanel.add(createChoicePanel(roiList, CHOICE_USE_ROI));
    roiList.add(CHOICE_NO_ROI);
    roiList.add(CHOICE_CHANNEL1);
    roiList.add(CHOICE_CHANNEL2);

    searchToleranceTextField = new TextField();
    mainPanel.add(createTextPanel(searchToleranceTextField, CHOICE_SEARCG_TOLERANCE,
        String.valueOf(searchTolerance)));

    correlationThresholdTextField = new TextField();
    mainPanel.add(createTextPanel(correlationThresholdTextField, CHOICE_CORRELATION_THRESHOLD,
        String.valueOf(correlationThreshold)));

    showColocalisedCheckbox = new Checkbox();
    mainPanel.add(
        createCheckboxPanel(showColocalisedCheckbox, CHOICE_SHOW_COLOCALISED, showColocalised));

    useConstantIntensityCheckbox = new Checkbox();
    mainPanel.add(createCheckboxPanel(useConstantIntensityCheckbox, CHOICE_USE_CONSTANCE_INTENSITY,
        useConstantIntensity));

    showScatterPlotCheckbox = new Checkbox();
    mainPanel.add(
        createCheckboxPanel(showScatterPlotCheckbox, CHOICE_SHOW_SCATTER_PLOT, showScatterPlot));

    includeZeroZeroPixelsCheckbox = new Checkbox();
    mainPanel.add(createCheckboxPanel(includeZeroZeroPixelsCheckbox,
        CHOICE_INCLUDE_ZERO_ZERO_PIXELS, includeZeroZeroPixels));

    closeWindowsOnExitCheckbox = new Checkbox();
    mainPanel.add(createCheckboxPanel(closeWindowsOnExitCheckbox, CHOICE_CLOSE_WINDOWS_ON_EXIT,
        closeWindowsOnExit));

    setResultsOptionsCheckbox = new Checkbox();
    mainPanel
        .add(createCheckboxPanel(setResultsOptionsCheckbox, CHOICE_SET_RESULTS_OPTIONS, false));
    setResultsOptionsCheckbox.addItemListener(this::itemStateChanged);

    okButton = new Button(OK_BUTTON_LABEL);
    okButton.addActionListener(this::actionPerformed);
    helpButton = new Button(HELP_BUTTON_LABEL);
    helpButton.addActionListener(this::actionPerformed);

    final JPanel buttonPanel = new JPanel();
    final FlowLayout l = new FlowLayout();
    l.setVgap(0);
    buttonPanel.setLayout(l);
    buttonPanel.add(okButton, BorderLayout.CENTER);
    buttonPanel.add(helpButton, BorderLayout.CENTER);

    mainPanel.add(buttonPanel);
  }

  private static Panel createChoicePanel(Choice list, String label) {
    final Panel panel = new Panel();
    panel.setLayout(new BorderLayout());
    final Label listLabel = new Label(label, 0);
    listLabel.setFont(MONO_FONT);
    list.setSize(FONT_WIDTH * 3, FONT_WIDTH);
    panel.add(listLabel, BorderLayout.WEST);
    panel.add(list, BorderLayout.CENTER);
    return panel;
  }

  private static Panel createTextPanel(TextField textField, String label, String value) {
    final Panel panel = new Panel();
    panel.setLayout(new BorderLayout());
    final Label listLabel = new Label(label, 0);
    listLabel.setFont(MONO_FONT);
    textField.setSize(FONT_WIDTH * 3, FONT_WIDTH);
    textField.setText(value);
    panel.add(listLabel, BorderLayout.WEST);
    panel.add(textField, BorderLayout.CENTER);
    return panel;
  }

  private static Panel createCheckboxPanel(Checkbox checkbox, String label, boolean state) {
    final Panel panel = new Panel();
    panel.setLayout(new BorderLayout());
    final Label listLabel = new Label(label, 0);
    listLabel.setFont(MONO_FONT);
    checkbox.setState(state);
    panel.add(listLabel, BorderLayout.WEST);
    panel.add(checkbox, BorderLayout.EAST);
    return panel;
  }

  private static void plotResults(List<ColocalisationThreshold.ThresholdResult> results) {
    if (results == null) {
      return;
    }

    final double[] threshold = new double[results.size()];
    final double[] r1 = new double[results.size()];
    final double[] r2 = new double[results.size()];

    // Initialise to the max range of a correlation
    double ymin = 1;
    double ymax = -1;

    // Plot the zero threshold result at the minimum threshold value
    int minThreshold = Integer.MAX_VALUE;
    int zeroThresholdIndex = 0;

    for (int i = 0, j = results.size() - 1; i < results.size(); i++, j--) {
      final ColocalisationThreshold.ThresholdResult result = results.get(i);
      threshold[j] = result.getThreshold1();
      r1[j] = result.getCorrelation1();
      r2[j] = result.getCorrelation2();
      if (ymin > r1[j]) {
        ymin = r1[j];
      }
      if (ymin > r2[j]) {
        ymin = r2[j];
      }
      if (ymax < r1[j]) {
        ymax = r1[j];
      }
      if (ymax < r2[j]) {
        ymax = r2[j];
      }
      if (result.getThreshold1() == 0) {
        zeroThresholdIndex = j;
      } else if (minThreshold > result.getThreshold1()) {
        minThreshold = result.getThreshold1();
      }
    }
    threshold[zeroThresholdIndex] = (minThreshold > 0) ? minThreshold - 1 : 0;

    final Plot plot = new Plot(CORRELATION_VALUES_TITLE, "Threshold", "R");
    plot.setLimits(threshold[0], threshold[threshold.length - 1], ymin, ymax);
    plot.addPoints(threshold, r1, Plot.LINE);
    plot.setColor(Color.BLUE);
    plot.draw();
    plot.setColor(Color.RED);
    plot.addPoints(threshold, r2, Plot.CROSS);
    plot.setColor(Color.BLACK);
    plot.addLabel(0, 0, "Blue=C1+C2 above threshold; Red=Ch1/Ch2 below threshold");
  }
}
