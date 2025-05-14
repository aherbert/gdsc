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

import ij.CompositeImage;
import ij.IJ;
import ij.ImagePlus;
import ij.Prefs;
import ij.gui.DialogListener;
import ij.gui.GenericDialog;
import ij.measure.Calibration;
import ij.plugin.filter.ExtendedPlugInFilter;
import ij.plugin.filter.PlugInFilterRunner;
import ij.process.ImageProcessor;
import ij.text.TextWindow;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import java.awt.AWTEvent;
import java.awt.event.ActionEvent;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.logging.Logger;
import java.util.stream.IntStream;
import uk.ac.sussex.gdsc.core.ij.BufferedTextWindow;
import uk.ac.sussex.gdsc.core.ij.ImageJPluginLoggerHelper;
import uk.ac.sussex.gdsc.core.ij.ImageJUtils;
import uk.ac.sussex.gdsc.core.ij.gui.ExtendedGenericDialog;
import uk.ac.sussex.gdsc.core.ij.gui.NonBlockingExtendedGenericDialog;
import uk.ac.sussex.gdsc.core.trees.DoubleDistanceFunctions;
import uk.ac.sussex.gdsc.core.trees.IntDoubleKdTree;
import uk.ac.sussex.gdsc.core.trees.KdTrees;
import uk.ac.sussex.gdsc.core.utils.LocalList;
import uk.ac.sussex.gdsc.core.utils.MathUtils;
import uk.ac.sussex.gdsc.core.utils.TextUtils;
import uk.ac.sussex.gdsc.ij.UsageTracker;
import uk.ac.sussex.gdsc.ij.ext.gui.LabelledPointRoi;
import uk.ac.sussex.gdsc.ij.foci.FindFociProcessorOptions.AlgorithmOption;
import uk.ac.sussex.gdsc.ij.foci.FindFociProcessorOptions.BackgroundMethod;
import uk.ac.sussex.gdsc.ij.foci.FindFociProcessorOptions.CentreMethod;
import uk.ac.sussex.gdsc.ij.foci.FindFociProcessorOptions.MaskMethod;
import uk.ac.sussex.gdsc.ij.foci.FindFociProcessorOptions.PeakMethod;
import uk.ac.sussex.gdsc.ij.foci.FindFociProcessorOptions.SearchMethod;
import uk.ac.sussex.gdsc.ij.foci.FindFociProcessorOptions.SortMethod;
import uk.ac.sussex.gdsc.ij.foci.FindFociProcessorOptions.StatisticsMethod;
import uk.ac.sussex.gdsc.ij.foci.FindFociProcessorOptions.ThresholdMethod;

/**
 * Identifies foci in a primary channel and computes summary statistics of the neighbouring foci in
 * secondary channels.
 */
public class FociNeighbourAnalysis_PlugIn implements ExtendedPlugInFilter, DialogListener {
  private static final String TITLE = "Foci Neighbour Analysis";
  private static final int FLAGS =
      DOES_8G + DOES_16 + DOES_32 + FINAL_PROCESSING + KEEP_PREVIEW + NO_CHANGES;

  private static AtomicReference<TextWindow> RESULTS_TABLE = new AtomicReference<>();

  private ImagePlus imp;
  /** Original position of the image hyperstack. Defines the time frame for analysis. */
  private int[] position;
  private int[] secondaryChannels;
  private Logger logger;

  /** The current settings for the plugin instance. */
  private Settings settings;

  /**
   * Contains the settings that are the re-usable state of the plugin.
   */
  private static class Settings {
    private static final String SETTING_PR_CHANNEL = "gdsc.foci.neighbours.primaryChannel";
    private static final String SETTING_SE_CHANNEL = "gdsc.foci.neighbours.secondaryChannel";
    private static final String SETTING_DISTANCE = "gdsc.foci.neighbours.distance";
    private static final String SETTING_RESULT_DIR = "gdsc.foci.neighbours.resultsDirectory";
    private static final String SETTING_PROCESSOR = "gdsc.foci.neighbours.processor";
    private static final String SETTING_DIGITS = "gdsc.foci.neighbours.digits";

    /** The last settings used by the plugin. This should be updated after plugin execution. */
    private static final AtomicReference<Settings> lastSettings =
        new AtomicReference<>(new Settings());

    int primaryChannel;
    boolean[] secondaryChannels;
    double distance;
    String resultsDirectory;
    private final Int2ObjectOpenHashMap<FindFociProcessorOptions> options;
    int digits;

    /**
     * Default constructor.
     */
    Settings() {
      // Do nothing
      primaryChannel = (int) Prefs.get(SETTING_PR_CHANNEL, 1);
      distance = Prefs.get(SETTING_DISTANCE, 1);
      resultsDirectory = Prefs.get(SETTING_RESULT_DIR, "");
      options = new Int2ObjectOpenHashMap<>();
      digits = 4;

      // Convert chars to boolean
      final String se = Prefs.get(SETTING_SE_CHANNEL, "");
      secondaryChannels = new boolean[se.length()];
      for (int i = 0; i < secondaryChannels.length; i++) {
        secondaryChannels[i] = se.charAt(i) == '1';
      }
    }

    /**
     * Copy constructor.
     *
     * @param source the source
     */
    private Settings(Settings source) {
      primaryChannel = source.primaryChannel;
      secondaryChannels = source.secondaryChannels.clone();
      distance = source.distance;
      resultsDirectory = source.resultsDirectory;
      options = source.options.clone();
      digits = source.digits;
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
     * Save the settings for plugin set-up.
     */
    void save() {
      lastSettings.set(this);
      Prefs.set(SETTING_PR_CHANNEL, primaryChannel);

      // Convert boolean array to string
      final char[] c = new char[secondaryChannels.length];
      for (int i = 0; i < c.length; i++) {
        c[i] = secondaryChannels[i] ? '1' : '0';
      }
      Prefs.set(SETTING_SE_CHANNEL, new String(c));
    }

    /**
     * Save the settings for analysis.
     */
    void saveAnalysisOptions() {
      Prefs.set(SETTING_DISTANCE, distance);
      Prefs.set(SETTING_RESULT_DIR, resultsDirectory);
      Prefs.set(SETTING_DIGITS, digits);
    }

    FindFociProcessorOptions getOptions(int channel) {
      return options.computeIfAbsent(channel, Settings::loadOptions);
    }

    private static FindFociProcessorOptions loadOptions(int channel) {
      final String opt = Prefs.get(SETTING_PROCESSOR + channel, "");
      if (opt.isEmpty()) {
        return defaultOptions();
      }
      return FindFociParameters.fromString(opt).processorOptions;
    }

    static FindFociProcessorOptions defaultOptions() {
      final FindFociProcessorOptions processorOptions = new FindFociProcessorOptions();
      // TODO: Set defaults
      processorOptions.setMaxPeaks(10000);
      processorOptions.setMinSize(50);
      processorOptions.setMaskMethod(MaskMethod.NONE);
      return processorOptions;
    }

    void setOptions(int channel, FindFociProcessorOptions processorOptions) {
      this.options.put(channel, processorOptions);
      Prefs.set(SETTING_PROCESSOR + channel, new FindFociParameters(processorOptions).toString());
    }
  }

  /** {@inheritDoc} */
  @Override
  public int setup(String arg, ImagePlus imp) {
    if ("final".equals(arg)) {
      finalProcessing(imp);
      return DONE;
    }

    UsageTracker.recordPlugin(this.getClass(), arg);

    if (imp == null) {
      IJ.noImage();
      return DONE;
    }

    final int channels = imp.getNChannels();

    if (channels == 1) {
      IJ.error(TITLE, "Multiple channels required");
      return DONE;
    }
    if (imp.getBitDepth() == 24) {
      IJ.error(TITLE, "Greyscale image required");
      return DONE;
    }

    settings = Settings.load();

    // Select the channels
    final ExtendedGenericDialog gd = new ExtendedGenericDialog(TITLE);
    gd.addMessage("Select channels for foci neighbour analysis");
    final String[] primaryChannels =
        IntStream.rangeClosed(1, channels).mapToObj(Integer::toString).toArray(String[]::new);
    gd.addChoice("Primary_Channel", primaryChannels, settings.primaryChannel - 1);
    gd.addMessage("Secondary channels:");
    final boolean[] secondaryChannels = Arrays.copyOf(settings.secondaryChannels, channels);
    secondaryChannels[settings.primaryChannel - 1] = false;
    for (int i = 0; i < channels; i++) {
      gd.addCheckbox("Secondary_Channel_" + (i + 1), secondaryChannels[i]);
    }
    gd.showDialog();
    if (gd.wasCanceled()) {
      return DONE;
    }
    settings.primaryChannel = 1 + gd.getNextChoiceIndex();
    for (int i = 0; i < channels; i++) {
      secondaryChannels[i] = gd.getNextBoolean();
    }
    secondaryChannels[settings.primaryChannel - 1] = false;
    // Record the settings; preserve additional unused channels from previous settings
    if (settings.secondaryChannels.length >= channels) {
      System.arraycopy(secondaryChannels, 0, settings.secondaryChannels, 0, channels);
    } else {
      settings.secondaryChannels = secondaryChannels;
    }

    settings.save();

    // Check number of secondary channels
    final IntArrayList ch = new IntArrayList(channels);
    for (int i = 0; i < channels; i++) {
      if (secondaryChannels[i]) {
        ch.add(i + 1);
      }
    }

    if (ch.isEmpty()) {
      IJ.error(TITLE, "Require at least 1 secondary channel");
      return DONE;
    }

    this.imp = imp;
    this.position = new int[] {imp.getC(), imp.getZ(), imp.getT()};
    this.secondaryChannels = ch.toIntArray();
    logger = ImageJPluginLoggerHelper.getLogger(this.getClass());

    return FLAGS;
  }

  private void finalProcessing(ImagePlus imp) {
    final TextWindow table = RESULTS_TABLE.get();
    if (table != null && TextUtils.isNotEmpty(settings.resultsDirectory)
        && Files.isDirectory(Paths.get(settings.resultsDirectory))) {
      final String path = Paths.get(settings.resultsDirectory, imp.getTitle() + ".csv").toString();
      logger.info(() -> "Saving results: " + path);
      table.getTextPanel().saveAs(path);
    }
  }

  /** {@inheritDoc} */
  @Override
  public void run(ImageProcessor ip) {
    doAnalysis();
  }

  /** {@inheritDoc} */
  @Override
  public int showDialog(ImagePlus imp, String command, PlugInFilterRunner pfr) {
    final NonBlockingExtendedGenericDialog gd = new NonBlockingExtendedGenericDialog(TITLE);
    gd.addMessage(TextUtils.wrap("Identifies foci in a primary channel and computes summary "
        + "statistics of the neighbouring foci in secondary channels.", 80));

    // Calibrated distance. This ignore anisotropy.
    final Calibration cal = imp.getCalibration();
    gd.addNumericField("Distance", settings.distance, 2, 6, cal.scaled() ? cal.getUnit() : "px");
    gd.addDirectoryField("Results_directory", settings.resultsDirectory);
    gd.addSlider("Table_Digits", 2, 10, settings.digits);

    // Add dialogs to control FindFoci settings for each channel
    addFindFociSettings(gd, settings.primaryChannel);
    for (final int ch : secondaryChannels) {
      addFindFociSettings(gd, ch);
    }

    if (imp != null && imp.isComposite() && ((CompositeImage) imp).getMode() == IJ.COMPOSITE) {
      logger.warning("Preview not available on composite images");
    }

    gd.addPreviewCheckbox(pfr);
    gd.addDialogListener(this);

    // Do not lock the image to allow slider update during the preview
    imp.unlock();

    gd.showDialog();

    final boolean cancelled = gd.wasCanceled() || !dialogItemChanged(gd, null);
    if (cancelled) {
      return DONE;
    }

    return FLAGS;
  }

  private void addFindFociSettings(ExtendedGenericDialog parentGd, int channel) {
    parentGd.addButton("FindFoci Channel " + channel, e -> {
      final ExtendedGenericDialog gd =
          new ExtendedGenericDialog("Foci Options: Channel " + channel);
      FindFociProcessorOptions processorOptions = settings.getOptions(channel);

      // Copy of FindFoci plugin layout
      gd.addMessage("Background options ...");
      gd.addChoice(FindFoci_PlugIn.OPTION_BACKGROUND_METHOD, BackgroundMethod.getDescriptions(),
          processorOptions.getBackgroundMethod().ordinal());
      gd.addNumericField(FindFoci_PlugIn.OPTION_BACKGROUND_PARAMETER,
          processorOptions.getBackgroundParameter(), 0);
      gd.addChoice(FindFoci_PlugIn.OPTION_AUTO_THRESHOLD, ThresholdMethod.getDescriptions(),
          processorOptions.getThresholdMethod().ordinal());
      gd.addChoice(FindFoci_PlugIn.OPTION_STASTISTICS_MODE, StatisticsMethod.getDescriptions(),
          processorOptions.getStatisticsMethod().ordinal());
      gd.addMessage("Search options ...");
      gd.addChoice(FindFoci_PlugIn.OPTION_SEARCH_METHOD, SearchMethod.getDescriptions(),
          processorOptions.getSearchMethod().ordinal());
      gd.addNumericField(FindFoci_PlugIn.OPTION_SEARCH_PARAMETER,
          processorOptions.getSearchParameter(), 2);
      gd.addMessage("Merge options ...");
      gd.addChoice(FindFoci_PlugIn.OPTION_MINIMUM_PEAK_HEIGHT, PeakMethod.getDescriptions(),
          processorOptions.getPeakMethod().ordinal());
      gd.addNumericField(FindFoci_PlugIn.OPTION_PEAK_PARAMETER, processorOptions.getPeakParameter(),
          2);
      gd.addNumericField(FindFoci_PlugIn.OPTION_MINIMUM_SIZE, processorOptions.getMinSize(), 0);
      gd.addCheckbox(FindFoci_PlugIn.OPTION_MINIMUM_SIZE_ABOVE_SADDLE,
          processorOptions.isOption(AlgorithmOption.MINIMUM_ABOVE_SADDLE));
      gd.addCheckbox(FindFoci_PlugIn.OPTION_CONNECTED_ABOVE_SADDLE,
          processorOptions.isOption(AlgorithmOption.CONTIGUOUS_ABOVE_SADDLE));
      gd.addMessage("Results options ...");
      gd.addChoice(FindFoci_PlugIn.OPTION_SORT_METHOD, SortMethod.getDescriptions(),
          processorOptions.getSortMethod().ordinal());
      gd.addNumericField(FindFoci_PlugIn.OPTION_MAXIMUM_PEAKS, processorOptions.getMaxPeaks(), 0);
      // No configuration of the mask
      // gd.addChoice(FindFoci_PlugIn.OPTION_SHOW_MASK, MaskMethod.getDescriptions(),
      // processorOptions.getMaskMethod().ordinal());
      // gd.addSlider(FindFoci_PlugIn.OPTION_FRACTION_PARAMETER, 0.05, 1,
      // processorOptions.getFractionParameter());
      // gd.addCheckbox(FindFoci_PlugIn.OPTION_SHOW_PEAK_MAXIMA_AS_DOTS,
      // processorOptions.isOption(AlgorithmOption.OUTPUT_MASK_PEAK_DOTS));
      gd.addCheckbox(FindFoci_PlugIn.OPTION_REMOVE_EDGE_MAXIMA,
          processorOptions.isOption(AlgorithmOption.REMOVE_EDGE_MAXIMA));
      gd.addNumericField(FindFoci_PlugIn.OPTION_MAXIMUM_SIZE, processorOptions.getMaxSize(), 0);
      gd.addMessage("Advanced options ...");
      gd.addNumericField(FindFoci_PlugIn.OPTION_GAUSSIAN_BLUR, processorOptions.getGaussianBlur(),
          1);
      gd.addChoice(FindFoci_PlugIn.OPTION_CENTRE_METHOD, FindFoci_PlugIn.getCentreMethods(),
          processorOptions.getCentreMethod().ordinal());
      gd.addNumericField(FindFoci_PlugIn.OPTION_CENTRE_PARAMETER,
          processorOptions.getCentreParameter(), 0);
      // GDSC intranet help page does not exist
      // gd.addHelp(uk.ac.sussex.gdsc.ij.help.Urls.FIND_FOCI);

      // Allow reset of options
      gd.enableYesNoCancel("OK", "Reset");

      gd.showDialog();
      if (gd.wasCanceled()) {
        return;
      }
      if (gd.wasOKed()) {
        // Read settings
        processorOptions.setBackgroundMethod(BackgroundMethod.fromOrdinal(gd.getNextChoiceIndex()));
        processorOptions.setBackgroundParameter(gd.getNextNumber());
        processorOptions.setThresholdMethod(ThresholdMethod.fromOrdinal(gd.getNextChoiceIndex()));
        processorOptions.setStatisticsMethod(StatisticsMethod.fromOrdinal(gd.getNextChoiceIndex()));
        processorOptions.setSearchMethod(SearchMethod.fromOrdinal(gd.getNextChoiceIndex()));
        processorOptions.setSearchParameter(gd.getNextNumber());
        processorOptions.setPeakMethod(PeakMethod.fromOrdinal(gd.getNextChoiceIndex()));
        processorOptions.setPeakParameter(gd.getNextNumber());
        processorOptions.setMinSize((int) gd.getNextNumber());
        processorOptions.setOption(AlgorithmOption.MINIMUM_ABOVE_SADDLE, gd.getNextBoolean());
        processorOptions.setOption(AlgorithmOption.CONTIGUOUS_ABOVE_SADDLE, gd.getNextBoolean());
        processorOptions.setSortMethod(SortMethod.fromOrdinal(gd.getNextChoiceIndex()));
        processorOptions.setMaxPeaks((int) gd.getNextNumber());
        // processorOptions.setMaskMethod(MaskMethod.fromOrdinal(gd.getNextChoiceIndex()));
        // processorOptions.setFractionParameter(gd.getNextNumber());
        // processorOptions.setOption(AlgorithmOption.OUTPUT_MASK_PEAK_DOTS, gd.getNextBoolean());
        processorOptions.setOption(AlgorithmOption.REMOVE_EDGE_MAXIMA, gd.getNextBoolean());
        processorOptions.setMaxSize((int) gd.getNextNumber());
        processorOptions.setGaussianBlur(gd.getNextNumber());
        processorOptions.setCentreMethod(CentreMethod.fromOrdinal(gd.getNextChoiceIndex()));
        processorOptions.setCentreParameter(gd.getNextNumber());

      } else {
        // Reset
        processorOptions = Settings.defaultOptions();
      }

      settings.setOptions(channel, processorOptions);

      // Trigger an event to read the dialog and run the preview if applicable
      parentGd
          .actionPerformed(new ActionEvent(gd, ActionEvent.ACTION_PERFORMED, "Options updated"));
    });
  }

  /** Listener to modifications of the input fields of the dialog. */
  @Override
  public boolean dialogItemChanged(GenericDialog gd, AWTEvent event) {
    settings.distance = gd.getNextNumber();
    settings.resultsDirectory = gd.getNextString();
    settings.digits = (int) gd.getNextNumber();
    settings.saveAnalysisOptions();
    return settings.distance > 0;
  }

  /** {@inheritDoc} */
  @Override
  public void setNPasses(int passes) {
    // Ignore
  }

  /**
   * Perform the foci neighbour analysis.
   */
  private void doAnalysis() {
    // Run FindFoci on each channel
    logger.info(() -> String.format("Finding foci in primary channel %d", settings.primaryChannel));
    final FindFociResults pResults = runFindFoci(settings.primaryChannel);
    logger.info(() -> String.format("Primary channel %d: %d foci", settings.primaryChannel,
        pResults.results.size()));

    final Int2ObjectOpenHashMap<FindFociResults> chResults =
        new Int2ObjectOpenHashMap<>(secondaryChannels.length);
    for (final int ch : secondaryChannels) {
      logger.info(() -> String.format("Finding foci in secondary channel %d", ch));
      final FindFociResults r = runFindFoci(ch);
      chResults.put(ch, r);
      logger.info(() -> String.format("Secondary channel %d: %d foci", ch, r.results.size()));
    }
    imp.setPositionWithoutUpdate(position[0], position[1], position[2]);

    // TODO: Combine primary channel foci?
    // At present it is assumed FindFoci will do this correctly.

    // Greedy one-to-many matching.
    // Create a KD-Tree of points for the primary channel.
    final Function<FindFociResult, double[]> coords = createCoordinateFunction();
    final IntDoubleKdTree tree = KdTrees.newIntDoubleKdTree(3);
    for (int i = pResults.results.size(); --i >= 0;) {
      tree.add(coords.apply(pResults.results.get(i)), i);
    }

    // For each secondary channel find the nearest neighbour in the primary channel.
    final List<Int2ObjectOpenHashMap<List<FindFociResult>>> allNeighbours = new LocalList<>();
    final double distanceThreshold = settings.distance * settings.distance;
    for (final int ch : secondaryChannels) {
      // Store 1-to-many mapping for primary channel to secondary channel
      final Int2ObjectOpenHashMap<List<FindFociResult>> neighbours = new Int2ObjectOpenHashMap<>();
      allNeighbours.add(neighbours);

      final BiConsumer<FindFociResult, FindFociResult> matched = (a, b) -> {
        logger.info(() -> String.format("Ch %d: %d,%d,%d (%d) -> %d,%d,%d (%d)", ch, b.x, b.y, b.z,
            b.count, a.x, a.y, a.z, a.count));
        neighbours.computeIfAbsent(a.id, x -> new LocalList<>()).add(b);
      };
      for (final FindFociResult b : chResults.get(ch).results) {
        tree.nearestNeighbour(coords.apply(b), DoubleDistanceFunctions.SQUARED_EUCLIDEAN_3D,
            (i, d) -> {
              if (d < distanceThreshold) {
                matched.accept(pResults.results.get(i), b);
              }
            });
      }
    }

    showResults(pResults, allNeighbours);

    overlayResults(pResults, allNeighbours);
  }

  private Function<FindFociResult, double[]> createCoordinateFunction() {
    final Calibration cal = imp.getCalibration();
    final double sx = cal.pixelWidth;
    final double sy = cal.pixelHeight;
    final double sz = cal.pixelDepth;
    return r -> new double[] {r.x * sx, r.y * sy, r.z * sz};
  }

  private FindFociResults runFindFoci(int channel) {
    final FindFoci_PlugIn ff = new FindFoci_PlugIn();
    final FindFociProcessorOptions processorOptions = settings.getOptions(channel);
    // TODO: Optionally compute the mask to allow overlay preview
    // processorOptions.setMaskMethod(MaskMethod.NONE);
    imp.setPositionWithoutUpdate(channel, 1, position[2]);
    return ff.createFindFociProcessor(imp).findMaxima(imp, null, processorOptions);
  }

  /**
   * Report the summary stats of the neighbours in a table. For each primary channel foci, summarise
   * the count and stats of the neighbours.
   *
   * @param pResults the results
   * @param allNeighbours all the neighbours for each secondary channel
   */
  private void showResults(FindFociResults pResults,
      List<Int2ObjectOpenHashMap<List<FindFociResult>>> allNeighbours) {
    // Re-use the results table
    final String header = createHeader();
    TextWindow table = RESULTS_TABLE.get();
    if (ImageJUtils.isShowing(table)) {
      if (!ImageJUtils.refreshHeadings(table, header, false)) {
        // Same header so clear the panel
        table.getTextPanel().clear();
      }
    } else {
      table = new TextWindow(TITLE + " Results", header, "", 800, 600);
    }
    RESULTS_TABLE.set(table);
    // Add results
    try (BufferedTextWindow results = new BufferedTextWindow(table)) {
      final StringBuilder sb = new StringBuilder(256);
      for (final FindFociResult r : pResults.results) {
        sb.setLength(0);
        sb.append(r.id).append('\t');
        sb.append(r.x).append('\t');
        sb.append(r.y).append('\t');
        sb.append(r.z).append('\t');
        sb.append(r.count).append('\t');
        sb.append(MathUtils.rounded(r.totalIntensity, settings.digits));
        for (int i = 0; i < secondaryChannels.length; i++) {
          final List<FindFociResult> neighbours =
              allNeighbours.get(i).getOrDefault(r.id, Collections.emptyList());
          if (neighbours.isEmpty()) {
            sb.append("\t0\t0\t0.0");
          } else {
            long size = 0;
            double intensity = 0;
            for (final FindFociResult n : neighbours) {
              size += n.count;
              intensity += n.totalIntensity;
            }
            sb.append('\t').append(neighbours.size());
            sb.append('\t').append(size);
            sb.append('\t').append(MathUtils.rounded(intensity, settings.digits));
          }
        }
        results.append(sb.toString());
      }
    }
  }

  private String createHeader() {
    final StringBuilder sb = new StringBuilder("Ch ").append(settings.primaryChannel)
        .append(" ID\tX\tY\tZ\tSize\tIntensity");
    for (final int ch : secondaryChannels) {
      final String prefix = "\tCh" + ch + " ";
      sb.append(prefix).append('N');
      sb.append(prefix).append("Size");
      sb.append(prefix).append("Intensity");
    }
    return sb.toString();
  }

  private void overlayResults(FindFociResults pResults,
      List<Int2ObjectOpenHashMap<List<FindFociResult>>> allNeighbours) {
    final int[] x = new int[pResults.results.size()];
    final int[] y = new int[x.length];
    final int[] id = new int[x.length];
    int c = 0;
    for (final FindFociResult r : pResults.results) {
      // Check for any neighbours
      if (allNeighbours.stream().anyMatch(m -> m.containsKey(r.id))) {
        x[c] = r.x;
        y[c] = r.y;
        id[c] = r.id;
        c++;
      }
    }
    final LabelledPointRoi roi = new LabelledPointRoi(x, y, c);
    roi.setLabels(id);
    roi.setShowLabels(true);
    imp.setRoi(roi);
  }
}
