/*-
 * #%L
 * Genome Damage and Stability Centre ImageJ Plugins
 *
 * Software for microscopy image analysis
 * %%
 * Copyright (C) 2011 - 2026 Alex Herbert
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
import ij.ImagePlus;
import ij.ImageStack;
import ij.Macro;
import ij.Prefs;
import ij.gui.Roi;
import ij.plugin.frame.Recorder;
import ij.process.FloatProcessor;
import ij.process.LUT;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.awt.Color;
import java.util.Collections;
import java.util.SplittableRandom;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import uk.ac.sussex.gdsc.ij.foci.FindFociProcessorOptions.AlgorithmOption;
import uk.ac.sussex.gdsc.ij.foci.FindFociProcessorOptions.BackgroundMethod;
import uk.ac.sussex.gdsc.ij.foci.FindFociProcessorOptions.CentreMethod;
import uk.ac.sussex.gdsc.ij.foci.FindFociProcessorOptions.MaskMethod;
import uk.ac.sussex.gdsc.ij.foci.FindFociProcessorOptions.PeakMethod;
import uk.ac.sussex.gdsc.ij.foci.FindFociProcessorOptions.SearchMethod;
import uk.ac.sussex.gdsc.ij.foci.FindFociProcessorOptions.SortMethod;
import uk.ac.sussex.gdsc.ij.foci.FindFociProcessorOptions.StatisticsMethod;
import uk.ac.sussex.gdsc.ij.foci.FindFociProcessorOptions.ThresholdMethod;
import uk.ac.sussex.gdsc.ij.foci.FociNeighbourAnalysis_PlugIn.FindFociResultsKey;
import uk.ac.sussex.gdsc.ij.foci.FociNeighbourAnalysis_PlugIn.Settings;

@SuppressWarnings({"javadoc"})
class FociNeighbourAnalysisPlugInTest {
  private static final int SIZE = 80;

  /**
   * Create a 2 channel image. Channel 1 has spots at (20,20) and (60,20). Channel 2 has spots at
   * (22,20) and (60,60).
   */
  private static ImagePlus createImage() {
    final ImageStack stack = new ImageStack(SIZE, SIZE);
    stack.addSlice(createSpots(new int[][] {{20, 20}, {60, 20}}));
    stack.addSlice(createSpots(new int[][] {{22, 20}, {60, 60}}));
    final ImagePlus imp = new ImagePlus("test", stack);
    imp.setDimensions(2, 1, 1);
    return imp;
  }

  private static FloatProcessor createSpots(int[][] centres) {
    final FloatProcessor fp = new FloatProcessor(SIZE, SIZE);
    for (int y = 0; y < SIZE; y++) {
      for (int x = 0; x < SIZE; x++) {
        double v = 10;
        for (final int[] c : centres) {
          final double dx = x - c[0];
          final double dy = y - c[1];
          v += 1000 * Math.exp(-(dx * dx + dy * dy) / (2 * 16));
        }
        fp.setf(x, y, (float) v);
      }
    }
    return fp;
  }

  private static Settings createSettings() {
    final Settings settings = new Settings();
    settings.primaryChannel = 1;
    settings.distance = 5;
    settings.labelOption = Settings.LABEL_ALL;
    settings.maskChannels = new boolean[3];
    settings.maskOption = 0;
    settings.maskChannel = 2;
    settings.maskMethod = ThresholdMethod.OTSU;
    for (int ch = 1; ch <= 2; ch++) {
      settings.setOptions(new FindFociResultsKey(ch, Settings.defaultOptions()));
    }
    return settings;
  }

  private static FociNeighbourAnalysis_PlugIn createPlugin(ImagePlus imp, Settings settings) {
    final FociNeighbourAnalysis_PlugIn plugin = new FociNeighbourAnalysis_PlugIn();
    plugin.initialise(imp, settings, new int[] {2});
    return plugin;
  }

  @Test
  void testInputMaskInvalidatesCachedResults() {
    final ImagePlus imp = createImage();
    final Settings settings = createSettings();
    final FociNeighbourAnalysis_PlugIn plugin = createPlugin(imp, settings);
    Assertions.assertEquals(2, plugin.findFoci().get(1).results.size());
    // Mask using channel 2: excludes the channel 1 spot at (60,20)
    settings.maskOption = Settings.MASK_CHANNEL;
    final Int2ObjectOpenHashMap<FindFociResults> results = plugin.findFoci();
    Assertions.assertEquals(1, results.get(1).results.size());
    Assertions.assertEquals(20, results.get(1).results.get(0).x);
  }

  @Test
  void testAnalysisRoiIsKeptAfterLabelling() {
    final ImagePlus imp = createImage();
    final Roi roi = new Roi(0, 0, 40, SIZE);
    imp.setRoi(roi);
    final Settings settings = createSettings();
    final FociNeighbourAnalysis_PlugIn plugin = createPlugin(imp, settings);
    FindFociResults results = plugin.findFoci().get(1);
    Assertions.assertEquals(1, results.results.size());

    // Label the results with a point ROI
    plugin.labelResults(results, Collections.emptyList());
    Assertions.assertEquals(Roi.POINT, imp.getRoi().getType());

    // Change the options to recompute the results: the area ROI is used
    final FindFociProcessorOptions options = Settings.defaultOptions();
    options.setMinSize(49);
    settings.setOptions(new FindFociResultsKey(1, options));
    results = plugin.findFoci().get(1);
    Assertions.assertEquals(1, results.results.size());

    // No labels replaces the label ROI with the analysis ROI
    plugin.labelResults(results, Collections.emptyList());
    Assertions.assertEquals(Roi.POINT, imp.getRoi().getType());
    settings.labelOption = 0;
    plugin.labelResults(results, Collections.emptyList());
    Assertions.assertSame(roi, imp.getRoi());
  }

  @Test
  void testFindFociFailureReturnsNull() {
    // Random noise has more maxima than the default search capacity
    final SplittableRandom rng = new SplittableRandom(123);
    final int size = 600;
    final ImageStack stack = new ImageStack(size, size);
    for (int c = 0; c < 2; c++) {
      final FloatProcessor fp = new FloatProcessor(size, size);
      for (int i = 0; i < fp.getPixelCount(); i++) {
        fp.setf(i, (float) rng.nextDouble());
      }
      stack.addSlice(fp);
    }
    final ImagePlus imp = new ImagePlus("noise", stack);
    imp.setDimensions(2, 1, 1);
    final Settings settings = createSettings();
    final FindFociProcessorOptions options = Settings.defaultOptions();
    options.setBackgroundMethod(BackgroundMethod.NONE);
    settings.setOptions(new FindFociResultsKey(1, options));
    final FociNeighbourAnalysis_PlugIn plugin = createPlugin(imp, settings);
    Assertions.assertNull(plugin.findFoci());
  }

  @Test
  void testGetLuts() {
    final ImagePlus imp = createImage();
    LUT[] luts = FociNeighbourAnalysis_PlugIn.getLuts(imp);
    Assertions.assertEquals(2, luts.length);
    Assertions.assertEquals(Color.red.getRGB(), luts[0].getRGB(255));
    Assertions.assertEquals(Color.green.getRGB(), luts[1].getRGB(255));

    final CompositeImage cimp = new CompositeImage(createImage(), IJ_COMPOSITE);
    cimp.setChannelLut(LUT.createLutFromColor(Color.cyan), 1);
    cimp.setChannelLut(LUT.createLutFromColor(Color.magenta), 2);
    luts = FociNeighbourAnalysis_PlugIn.getLuts(cimp);
    Assertions.assertEquals(2, luts.length);
    Assertions.assertEquals(Color.cyan.getRGB(), luts[0].getRGB(255));
    Assertions.assertEquals(Color.magenta.getRGB(), luts[1].getRGB(255));
  }

  private static final int IJ_COMPOSITE = ij.IJ.COMPOSITE;

  @Test
  void testCreateHeader() {
    final FociNeighbourAnalysis_PlugIn plugin = createPlugin(createImage(), createSettings());
    Assertions.assertEquals("Ch1 ID\tX\tY\tZ\tSize\tIntensity\tCh2 N\tCh2 Size\tCh2 Intensity",
        plugin.createHeader());
  }

  /**
   * Create options where every field differs from the defaults. Parameters cannot be represented
   * with 2 decimal places. Fields not used by the selected methods are also set.
   */
  private static FindFociProcessorOptions createNonDefaultOptions() {
    final FindFociProcessorOptions options = Settings.defaultOptions();
    options.setBackgroundMethod(BackgroundMethod.STD_DEV_ABOVE_MEAN);
    options.setBackgroundParameter(1.23456789);
    options.setThresholdMethod(ThresholdMethod.YEN);
    options.setStatisticsMethod(StatisticsMethod.OUTSIDE);
    options.setSearchMethod(SearchMethod.HALF_PEAK_VALUE);
    options.setSearchParameter(0.3456789);
    options.setMinSize(7);
    options.setMaxSize(123);
    options.setPeakMethod(PeakMethod.ABSOLUTE);
    options.setPeakParameter(12.3456789);
    options.setSortMethod(SortMethod.MAX_VALUE);
    options.setMaxPeaks(77);
    options.setMaskMethod(MaskMethod.THRESHOLD);
    options.setGaussianBlur(1.0 / 3);
    options.setCentreMethod(CentreMethod.GAUSSIAN_ORIGINAL);
    options.setCentreParameter(2.3456789);
    options.setFractionParameter(0.456789);
    options.setOption(AlgorithmOption.MINIMUM_ABOVE_SADDLE, false);
    options.setOption(AlgorithmOption.REMOVE_EDGE_MAXIMA, true);
    options.setOption(AlgorithmOption.CONTIGUOUS_ABOVE_SADDLE, true);
    return options;
  }

  private static void assertOptionsEquals(FindFociProcessorOptions expected,
      FindFociProcessorOptions actual) {
    Assertions.assertEquals(expected.getBackgroundMethod(), actual.getBackgroundMethod());
    Assertions.assertEquals(expected.getBackgroundParameter(), actual.getBackgroundParameter());
    Assertions.assertEquals(expected.getThresholdMethod(), actual.getThresholdMethod());
    Assertions.assertEquals(expected.getStatisticsMethod(), actual.getStatisticsMethod());
    Assertions.assertEquals(expected.getSearchMethod(), actual.getSearchMethod());
    Assertions.assertEquals(expected.getSearchParameter(), actual.getSearchParameter());
    Assertions.assertEquals(expected.getMinSize(), actual.getMinSize());
    Assertions.assertEquals(expected.getMaxSize(), actual.getMaxSize());
    Assertions.assertEquals(expected.getPeakMethod(), actual.getPeakMethod());
    Assertions.assertEquals(expected.getPeakParameter(), actual.getPeakParameter());
    Assertions.assertEquals(expected.getSortMethod(), actual.getSortMethod());
    Assertions.assertEquals(expected.getMaxPeaks(), actual.getMaxPeaks());
    Assertions.assertEquals(expected.getMaskMethod(), actual.getMaskMethod());
    Assertions.assertEquals(expected.getGaussianBlur(), actual.getGaussianBlur());
    Assertions.assertEquals(expected.getCentreMethod(), actual.getCentreMethod());
    Assertions.assertEquals(expected.getCentreParameter(), actual.getCentreParameter());
    Assertions.assertEquals(expected.getFractionParameter(), actual.getFractionParameter());
    Assertions.assertEquals(expected.getOptions(), actual.getOptions());
  }

  @Test
  void testSettingsStringRoundTrip() {
    final FindFociProcessorOptions options = createNonDefaultOptions();
    final String text = FociNeighbourAnalysis_PlugIn.toSettingsString(options);
    assertOptionsEquals(options, FociNeighbourAnalysis_PlugIn.fromSettingsString(text));
    // No options
    options.getOptions().clear();
    assertOptionsEquals(options, FociNeighbourAnalysis_PlugIn
        .fromSettingsString(FociNeighbourAnalysis_PlugIn.toSettingsString(options)));
  }

  @Test
  void testFromSettingsStringIgnoresInvalidFields() {
    final FindFociProcessorOptions expected = Settings.defaultOptions();
    expected.setMaxSize(42);
    final FindFociProcessorOptions options = FociNeighbourAnalysis_PlugIn.fromSettingsString(
        "maxSize=42;minSize=abc;sortMethod=UNKNOWN;unknown=1;algorithmOptions=BAD;nothing;");
    assertOptionsEquals(expected, options);
  }

  @Test
  void testOptionsSavedToPreferences() {
    final int channel = 98765;
    try {
      final FindFociProcessorOptions options = createNonDefaultOptions();
      new Settings().setOptions(new FindFociResultsKey(channel, options));
      // A new instance reads the options from the preferences
      assertOptionsEquals(options, new Settings().getOptions(channel));
    } finally {
      Prefs.set("gdsc.foci.neighbours.processorSettings" + channel, "");
    }
  }

  @Test
  void testOptionsReadFromLegacyPreferences() {
    final int channel = 98766;
    final FindFociProcessorOptions options = Settings.defaultOptions();
    options.setBackgroundMethod(BackgroundMethod.ABSOLUTE);
    options.setBackgroundParameter(100);
    options.setMaskMethod(MaskMethod.FRACTION_OF_HEIGHT);
    options.setFractionParameter(0.25);
    try {
      Prefs.set("gdsc.foci.neighbours.processor" + channel,
          new FindFociParameters(options).toString());
      Prefs.set("gdsc.foci.neighbours.processorOptions" + channel, "Fraction height above background : 0.25");
      final FindFociProcessorOptions actual = new Settings().getOptions(channel);
      Assertions.assertEquals(BackgroundMethod.ABSOLUTE, actual.getBackgroundMethod());
      Assertions.assertEquals(100, actual.getBackgroundParameter());
      Assertions.assertEquals(MaskMethod.FRACTION_OF_HEIGHT, actual.getMaskMethod());
      Assertions.assertEquals(0.25, actual.getFractionParameter());
    } finally {
      Prefs.set("gdsc.foci.neighbours.processor" + channel, "");
      Prefs.set("gdsc.foci.neighbours.processorOptions" + channel, "");
    }
  }

  @Test
  void testResultsKeyUsesAllOptions() {
    final FindFociProcessorOptions options = createNonDefaultOptions();
    final FindFociResultsKey key = new FindFociResultsKey(1, options.copy());
    Assertions.assertEquals(key, new FindFociResultsKey(1, options.copy()));
    Assertions.assertNotEquals(key, new FindFociResultsKey(2, options.copy()));
    final FindFociProcessorOptions options2 = options.copy();
    options2.setMaxSize(options.getMaxSize() + 1);
    Assertions.assertNotEquals(key, new FindFociResultsKey(1, options2));
    final FindFociProcessorOptions options3 = options.copy();
    options3.setOption(AlgorithmOption.REMOVE_EDGE_MAXIMA, false);
    Assertions.assertNotEquals(key, new FindFociResultsKey(1, options3));
    final FindFociProcessorOptions options4 = options.copy();
    options4.setPeakParameter(options.getPeakParameter() + 1e-6);
    Assertions.assertNotEquals(key, new FindFociResultsKey(1, options4));
  }

  @Test
  void testFindFociOptionsReadFromMacro() {
    final ImagePlus imp = createImage();
    final Settings settings = createSettings();
    final FociNeighbourAnalysis_PlugIn plugin = createPlugin(imp, settings);
    final FindFociProcessorOptions options = createNonDefaultOptions();
    plugin.collectFindFociOptions("distance=5 findfoci_channel_2="
        + FociNeighbourAnalysis_PlugIn.toSettingsString(options) + " opacity=0.5");
    assertOptionsEquals(Settings.defaultOptions(), settings.getOptions(1));
    assertOptionsEquals(options, settings.getOptions(2));
    // Not saved to the preferences
    assertOptionsEquals(Settings.defaultOptions(), new Settings().getOptions(2));
  }

  @Test
  void testFindFociOptionsRecorded() {
    final ImagePlus imp = createImage();
    final Settings settings = createSettings();
    final FindFociProcessorOptions options = createNonDefaultOptions();
    settings.putOptions(2, options);
    final FociNeighbourAnalysis_PlugIn plugin = createPlugin(imp, settings);
    final boolean record = Recorder.record;
    try {
      Recorder.record = true;
      Recorder.resetCommandOptions();
      plugin.collectFindFociOptions(null);
      final String recorded = Recorder.getCommandOptions();
      Assertions.assertEquals(
          FociNeighbourAnalysis_PlugIn.toSettingsString(Settings.defaultOptions()),
          Macro.getValue(recorded, "findfoci_channel_1", null));
      Assertions.assertEquals(FociNeighbourAnalysis_PlugIn.toSettingsString(options),
          Macro.getValue(recorded, "findfoci_channel_2", null));
    } finally {
      Recorder.record = record;
      Recorder.resetCommandOptions();
    }
  }

  @Test
  void testEnsureLength() {
    final boolean[] a = {false, true, false, true};
    // Unused channels are preserved
    Assertions.assertArrayEquals(new boolean[] {false, true, false, true},
        FociNeighbourAnalysis_PlugIn.ensureLength(a, 3));
    Assertions.assertArrayEquals(new boolean[] {false, true, false, true, false, false},
        FociNeighbourAnalysis_PlugIn.ensureLength(a, 6));
  }
}
