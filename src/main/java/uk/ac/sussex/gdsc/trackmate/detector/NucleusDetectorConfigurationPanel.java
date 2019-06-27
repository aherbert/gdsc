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

package uk.ac.sussex.gdsc.trackmate.detector;

import static fiji.plugin.trackmate.gui.TrackMateWizard.BIG_FONT;
import static fiji.plugin.trackmate.gui.TrackMateWizard.FONT;
import static fiji.plugin.trackmate.gui.TrackMateWizard.SMALL_FONT;

import uk.ac.sussex.gdsc.core.threshold.AutoThreshold;
import uk.ac.sussex.gdsc.core.utils.TextUtils;

import com.google.common.collect.Streams;

import fiji.plugin.trackmate.Logger;
import fiji.plugin.trackmate.Model;
import fiji.plugin.trackmate.Settings;
import fiji.plugin.trackmate.Spot;
import fiji.plugin.trackmate.SpotCollection;
import fiji.plugin.trackmate.TrackMate;
import fiji.plugin.trackmate.detection.DetectorKeys;
import fiji.plugin.trackmate.detection.SpotDetectorFactory;
import fiji.plugin.trackmate.gui.ConfigurationPanel;
import fiji.plugin.trackmate.gui.TrackMateGUIController;
import fiji.plugin.trackmate.gui.panels.components.JNumericTextField;
import fiji.plugin.trackmate.util.JLabelLogger;
import fiji.util.NumberParser;

import ij.ImagePlus;
import ij.gui.Overlay;
import ij.gui.Roi;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Panel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;

/**
 * Collect the options for the the {@link NucleusDetector}.
 */
public class NucleusDetectorConfigurationPanel extends ConfigurationPanel {
  private static final long serialVersionUID = 1L;
  private static final ImageIcon ICON_PREVIEW =
      new ImageIcon(TrackMateGUIController.class.getResource("images/flag_checked.png"));

  private JButton btnPreview;

  private JComboBox<Integer> comboChannel;
  private JComboBox<Integer> comboAnalysisChannel;

  private JTextField textFieldBlur1;
  private JTextField textFieldBlur2;
  private JComboBox<AutoThreshold.Method> comboMethod;
  private JTextField textFieldOutlierRadius;
  private JTextField textFieldOutlierThreshold;
  private JTextField textFieldMaxNucleusSize;
  private JTextField textFieldMinNucleusSize;
  private JTextField textFieldErosion;
  private JTextField textFieldExpansionInner;
  private JTextField textFieldExpansion;

  private final transient Model model;
  private final transient ImagePlus imp;
  private transient Logger localLogger;

  /** The ROIs currently added to the image overlay for the preview. */
  private Collection<Roi> roiList = Collections.emptyList();

  /**
   * Create a new instance.
   *
   * @param settings the settings
   * @param model the model
   */
  public NucleusDetectorConfigurationPanel(Settings settings, final Model model) {
    this.model = model;
    imp = settings.imp;

    initialiseLayout();
  }

  @Override
  public void setSettings(final Map<String, Object> settings) {
    // TODO - As per the TrackMate norm all units should be in image units.
    // Currently this uses both pixels (for blur and outliers) and image units (for size).
    comboChannel.setSelectedItem(settings.get(DetectorKeys.KEY_TARGET_CHANNEL));
    comboAnalysisChannel
        .setSelectedItem(settings.get(NucleusDetectorFactory.SETTING_ANALYSIS_CHANNEL));
    textFieldBlur1.setText(String.valueOf(settings.get(NucleusDetectorFactory.SETTING_BLUR1)));
    textFieldBlur2.setText(String.valueOf(settings.get(NucleusDetectorFactory.SETTING_BLUR2)));
    comboMethod.setSelectedItem(settings.get(NucleusDetectorFactory.SETTING_METHOD));
    textFieldOutlierRadius
        .setText(String.valueOf(settings.get(NucleusDetectorFactory.SETTING_OUTLIER_RADIUS)));
    textFieldOutlierThreshold
        .setText(String.valueOf(settings.get(NucleusDetectorFactory.SETTING_OUTLIER_THRESHOLD)));
    textFieldMaxNucleusSize
        .setText(String.valueOf(settings.get(NucleusDetectorFactory.SETTING_MAX_NUCLEUS_SIZE)));
    textFieldMinNucleusSize
        .setText(String.valueOf(settings.get(NucleusDetectorFactory.SETTING_MIN_NUCLEUS_SIZE)));
    textFieldErosion.setText(String.valueOf(settings.get(NucleusDetectorFactory.SETTING_EROSION)));
    textFieldExpansionInner
        .setText(String.valueOf(settings.get(NucleusDetectorFactory.SETTING_EXPANSION_INNER)));
    textFieldExpansion
        .setText(String.valueOf(settings.get(NucleusDetectorFactory.SETTING_EXPANSION)));
  }

  @Override
  public Map<String, Object> getSettings() {
    final HashMap<String, Object> map = new HashMap<>();
    final Object channel = comboChannel.getSelectedItem();
    final Object analysisChannel = comboAnalysisChannel.getSelectedItem();
    final double blur1 = NumberParser.parseDouble(textFieldBlur1.getText());
    final double blur2 = NumberParser.parseDouble(textFieldBlur2.getText());
    final Object method = comboMethod.getSelectedItem();
    final double outlierRadius = NumberParser.parseDouble(textFieldOutlierRadius.getText());
    final double outlierThreshold = NumberParser.parseDouble(textFieldOutlierThreshold.getText());
    final double maxNucleusSize = NumberParser.parseDouble(textFieldMaxNucleusSize.getText());
    final double minNucleusSize = NumberParser.parseDouble(textFieldMinNucleusSize.getText());
    final int erosion = NumberParser.parseInteger(textFieldErosion.getText());
    final int expansionInner = NumberParser.parseInteger(textFieldExpansionInner.getText());
    final int expansion = NumberParser.parseInteger(textFieldExpansion.getText());
    map.put(DetectorKeys.KEY_TARGET_CHANNEL, channel);
    map.put(NucleusDetectorFactory.SETTING_ANALYSIS_CHANNEL, analysisChannel);
    map.put(NucleusDetectorFactory.SETTING_BLUR1, blur1);
    map.put(NucleusDetectorFactory.SETTING_BLUR2, blur2);
    map.put(NucleusDetectorFactory.SETTING_METHOD, method);
    map.put(NucleusDetectorFactory.SETTING_OUTLIER_RADIUS, outlierRadius);
    map.put(NucleusDetectorFactory.SETTING_OUTLIER_THRESHOLD, outlierThreshold);
    map.put(NucleusDetectorFactory.SETTING_MAX_NUCLEUS_SIZE, maxNucleusSize);
    map.put(NucleusDetectorFactory.SETTING_MIN_NUCLEUS_SIZE, minNucleusSize);
    map.put(NucleusDetectorFactory.SETTING_EROSION, erosion);
    map.put(NucleusDetectorFactory.SETTING_EXPANSION_INNER, expansionInner);
    map.put(NucleusDetectorFactory.SETTING_EXPANSION, expansion);
    return map;
  }

  @Override
  public void clean() {
    final Overlay overlay = imp.getOverlay();
    if (null != overlay && !roiList.isEmpty()) {
      // More efficient to do the remove using an ArrayList rather that call
      // overlay.remove(...) which delegates to Vector.remove(Object)
      final List<Roi> list = new ArrayList<>(Arrays.asList(overlay.toArray()));
      list.removeAll(roiList);
      overlay.clear();
      for (final Roi roi : list) {
        overlay.add(roi);
      }
      roiList.clear();
    }
  }

  /**
   * Launch detection on the current frame.
   */
  private void preview() {
    btnPreview.setEnabled(false);
    CompletableFuture.runAsync(() -> {
      clean();

      final Settings lSettings = new Settings();
      lSettings.setFrom(imp);
      final int frame = imp.getFrame() - 1;
      lSettings.tstart = frame;
      lSettings.tend = frame;

      lSettings.detectorFactory = getDetectorFactory();
      lSettings.detectorSettings = getSettings();
      // Ignore the erosion and expansion computation in the analysis channel
      lSettings.detectorSettings.put(NucleusDetectorFactory.SETTING_ANALYSIS_CHANNEL,
          Integer.valueOf(0));

      final TrackMate trackmate = new TrackMate(lSettings);
      trackmate.getModel().setLogger(localLogger);

      final boolean detectionOk = trackmate.execDetection();
      if (!detectionOk) {
        localLogger.error(trackmate.getErrorMessage());
        return;
      }

      // Wrap new spots in a list.
      final SpotCollection newspots = trackmate.getModel().getSpots();
      final List<Spot> spotsToCopy =
          Streams.stream(newspots.iterator(frame, false)).collect(Collectors.toList());
      localLogger.log("Found " + spotsToCopy.size()
          + TextUtils.pleuralise(spotsToCopy.size(), " nucleus.", " nuclei."));

      // Pass new spot list to model.
      model.getSpots().put(frame, spotsToCopy);

      // Make them visible
      roiList = new ArrayList<>(spotsToCopy.size());
      for (final Spot spot : spotsToCopy) {
        spot.putFeature(SpotCollection.VISIBLITY, SpotCollection.ONE);
      }

      // Generate event for listener to reflect changes.
      model.setSpots(model.getSpots(), true);

      btnPreview.setEnabled(true);
    });
  }

  /**
   * Returns a new instance of the {@link SpotDetectorFactory} that this configuration panels
   * configures. The new instance will in turn be used for the preview mechanism.
   *
   * @return a new {@link SpotDetectorFactory}.
   */
  @SuppressWarnings("rawtypes")
  private static SpotDetectorFactory<?> getDetectorFactory() {
    return new NucleusDetectorFactory();
  }

  private void initialiseLayout() {
    try {
      final String units = model.getSpaceUnits();

      this.setPreferredSize(new java.awt.Dimension(300, 461));
      setLayout(new BorderLayout());
      final JPanel contentPanel = new JPanel();
      contentPanel.setBorder(new EmptyBorder(5, 5, 5, 5));
      add(contentPanel);
      final GridBagLayout layout = new GridBagLayout();
      contentPanel.setLayout(layout);
      final JLabel labelSegmenterName = new JLabel();
      labelSegmenterName.setFont(BIG_FONT);
      labelSegmenterName.setText(NucleusDetectorFactory.NAME);
      int row = 0;
      contentPanel.add(labelSegmenterName, createConstraints(2, 0, row++));
      // Deal with channels: Only make visible if multi-channel.
      final int channels = imp.getNChannels();
      final List<Integer> list =
          IntStream.range(1, channels + 1).boxed().collect(Collectors.toList());
      comboChannel = new JComboBox<>(list.toArray(new Integer[0]));
      // Add channel zero for no analysis
      final List<Integer> list2 =
          IntStream.range(0, channels + 1).boxed().collect(Collectors.toList());
      comboAnalysisChannel = new JComboBox<>(list2.toArray(new Integer[0]));
      if (channels > 1) {
        addFields(contentPanel, "Segment in channel:", comboChannel, row++);
        addFields(contentPanel, "Analysis channel:", comboAnalysisChannel, row++);
      }
      textFieldBlur1 = createNumericTextField();
      addFields(contentPanel, "Blur 1", textFieldBlur1, row++, "px");
      textFieldBlur2 = createNumericTextField();
      addFields(contentPanel, "Blur 2", textFieldBlur2, row++, "px");
      comboMethod = new JComboBox<>(AutoThreshold.Method.values());
      addFields(contentPanel, "Method", comboMethod, row++);
      textFieldOutlierRadius = createNumericTextField();
      addFields(contentPanel, "Outlier Radius", textFieldOutlierRadius, row++, "px");
      textFieldOutlierThreshold = createNumericTextField();
      addFields(contentPanel, "Outlier Threshold", textFieldOutlierThreshold, row++);
      textFieldMaxNucleusSize = createNumericTextField();
      addFields(contentPanel, "Max Nucleus Size", textFieldMaxNucleusSize, row++, units);
      textFieldMinNucleusSize = createNumericTextField();
      addFields(contentPanel, "Min Nucleus Size", textFieldMinNucleusSize, row++, units);
      textFieldErosion = createNumericTextField();
      addFields(contentPanel, "Erosion", textFieldErosion, row++, units);
      textFieldExpansionInner = createNumericTextField();
      addFields(contentPanel, "Expansion Inner", textFieldExpansionInner, row++, units);
      textFieldExpansion = createNumericTextField();
      addFields(contentPanel, "Expansion", textFieldExpansion, row++, units);
      btnPreview = new JButton("Preview", ICON_PREVIEW);
      btnPreview.setToolTipText("Preview the current settings on the current frame.");
      btnPreview.setFont(SMALL_FONT);
      btnPreview.addActionListener(e -> preview());
      contentPanel.add(btnPreview, createConstraints(1, 1, row++));
      final JLabelLogger labelLogger = new JLabelLogger();
      contentPanel.add(labelLogger, createConstraints(2, 0, row));
      localLogger = labelLogger.getLogger();
    } catch (final Exception ex) {
      java.util.logging.Logger.getLogger(NucleusDetector.class.getSimpleName()).log(Level.WARNING,
          "Failed to create configuration panel", ex);
    }
  }

  private static JNumericTextField createNumericTextField() {
    final JNumericTextField textField = new JNumericTextField();
    textField.setHorizontalAlignment(SwingConstants.CENTER);
    textField.setColumns(5);
    return textField;
  }

  private static GridBagConstraints createConstraints(int gridwidth, int gridx, int gridy) {
    final GridBagConstraints gbc = new GridBagConstraints();
    gbc.gridwidth = gridwidth;
    gbc.anchor = GridBagConstraints.WEST;
    gbc.insets = new Insets(0, 0, 5, 5);
    gbc.gridx = gridx;
    gbc.gridy = gridy;
    return gbc;
  }

  private static final void addFields(JPanel panel, String label, Component comp, int row) {
    final JLabel jlabel = new JLabel();
    jlabel.setText(label);
    addFields(panel, jlabel, comp, row);
  }

  private static final void addFields(JPanel panel, String label, Component comp, int row,
      String units) {
    final JLabel jlabel = new JLabel();
    jlabel.setText(label);
    final Panel fieldPanel = new Panel();
    fieldPanel.setLayout(new FlowLayout(FlowLayout.LEFT, 0, 0));
    comp.setFont(FONT);
    fieldPanel.add(comp);
    JLabel labelUnits = new JLabel(" " + units);
    labelUnits.setFont(FONT);
    fieldPanel.add(labelUnits);
    addFields(panel, jlabel, fieldPanel, row);
  }

  private static final void addFields(JPanel panel, JLabel jlabel, Component comp, int row) {
    jlabel.setFont(FONT);
    panel.add(jlabel, createConstraints(1, 0, row));

    comp.setFont(FONT);
    panel.add(comp, createConstraints(1, 1, row));
  }
}
