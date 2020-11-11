/*-
 * #%L
 * Genome Damage and Stability Centre ImageJ Plugins
 *
 * Software for microscopy image analysis
 * %%
 * Copyright (C) 2011 - 2020 Alex Herbert
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

import com.google.common.collect.Streams;
import fiji.plugin.trackmate.Logger;
import fiji.plugin.trackmate.Model;
import fiji.plugin.trackmate.Settings;
import fiji.plugin.trackmate.Spot;
import fiji.plugin.trackmate.SpotCollection;
import fiji.plugin.trackmate.TrackMate;
import fiji.plugin.trackmate.detection.SpotDetectorFactory;
import fiji.plugin.trackmate.gui.ConfigurationPanel;
import fiji.plugin.trackmate.gui.TrackMateGUIController;
import fiji.plugin.trackmate.gui.panels.components.JNumericTextField;
import fiji.util.NumberParser;
import ij.ImagePlus;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.stream.Collectors;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;
import uk.ac.sussex.gdsc.core.ij.ImageJUtils;
import uk.ac.sussex.gdsc.core.utils.TextUtils;
import uk.ac.sussex.gdsc.trackmate.gui.HtmlJLabelLogger;

/**
 * Collect the options for the the {@link PrecomputedDetector}.
 */
public class PrecomputedDetectorConfigurationPanel extends ConfigurationPanel {
  private static final long serialVersionUID = 1L;
  private static final ImageIcon ICON_PREVIEW =
      new ImageIcon(TrackMateGUIController.class.getResource("images/flag_checked.png"));

  private JButton btnPreview;

  private JTextField textFieldInputFile;
  private JTextField textFieldHeaderLines;
  private JTextField textFieldCommentChar;
  private JTextField textFieldDelimiter;
  private JTextField textFieldColumnId;
  private JTextField textFieldColumnFrame;
  private JTextField textFieldColumnX;
  private JTextField textFieldColumnY;
  private JTextField textFieldColumnZ;
  private JTextField textFieldRadius;
  private JTextField textFieldCategory;
  private JTextField textFieldCategoryFile;

  private final transient Model model;
  private final transient ImagePlus imp;
  private transient Logger localLogger;

  /**
   * Create a new instance.
   *
   * @param settings the settings
   * @param model the model
   */
  public PrecomputedDetectorConfigurationPanel(Settings settings, final Model model) {
    this.model = model;
    imp = settings.imp;

    initialiseLayout();
  }

  @Override
  public void setSettings(final Map<String, Object> settings) {
    textFieldInputFile
        .setText(String.valueOf(settings.get(PrecomputedDetectorFactory.SETTING_INPUT_FILE)));
    textFieldHeaderLines
        .setText(String.valueOf(settings.get(PrecomputedDetectorFactory.SETTING_HEADER_LINES)));
    textFieldCommentChar
        .setText(String.valueOf(settings.get(PrecomputedDetectorFactory.SETTING_COMMENT_CHAR)));
    textFieldDelimiter
        .setText(String.valueOf(settings.get(PrecomputedDetectorFactory.SETTING_DELIMITER)));
    textFieldColumnId
        .setText(String.valueOf(settings.get(PrecomputedDetectorFactory.SETTING_COLUMN_ID)));
    textFieldColumnFrame
        .setText(String.valueOf(settings.get(PrecomputedDetectorFactory.SETTING_COLUMN_FRAME)));
    textFieldColumnX
        .setText(String.valueOf(settings.get(PrecomputedDetectorFactory.SETTING_COLUMN_X)));
    textFieldColumnY
        .setText(String.valueOf(settings.get(PrecomputedDetectorFactory.SETTING_COLUMN_Y)));
    textFieldColumnZ
        .setText(String.valueOf(settings.get(PrecomputedDetectorFactory.SETTING_COLUMN_Z)));
    textFieldRadius
        .setText(String.valueOf(settings.get(PrecomputedDetectorFactory.SETTING_COLUMN_RADIUS)));
    textFieldCategory
        .setText(String.valueOf(settings.get(PrecomputedDetectorFactory.SETTING_COLUMN_CATEGORY)));
    textFieldCategoryFile
        .setText(String.valueOf(settings.get(PrecomputedDetectorFactory.SETTING_CATEGORY_FILE)));
  }

  @Override
  public Map<String, Object> getSettings() {
    final HashMap<String, Object> map = new HashMap<>();
    final String inputFile = textFieldInputFile.getText();
    final int headerLines = NumberParser.parseInteger(textFieldHeaderLines.getText());
    final String commentChar = textFieldCommentChar.getText();
    final String delimiter = textFieldDelimiter.getText();
    final int columnId = NumberParser.parseInteger(textFieldColumnId.getText());
    final int columnFrame = NumberParser.parseInteger(textFieldColumnFrame.getText());
    final int columnX = NumberParser.parseInteger(textFieldColumnX.getText());
    final int columnY = NumberParser.parseInteger(textFieldColumnY.getText());
    final int columnZ = NumberParser.parseInteger(textFieldColumnZ.getText());
    final int columnRadius = NumberParser.parseInteger(textFieldRadius.getText());
    final int columnCategory = NumberParser.parseInteger(textFieldCategory.getText());
    final String categoryFile = textFieldCategoryFile.getText();
    map.put(PrecomputedDetectorFactory.SETTING_INPUT_FILE, inputFile);
    map.put(PrecomputedDetectorFactory.SETTING_HEADER_LINES, headerLines);
    map.put(PrecomputedDetectorFactory.SETTING_COMMENT_CHAR, commentChar);
    map.put(PrecomputedDetectorFactory.SETTING_DELIMITER, delimiter);
    map.put(PrecomputedDetectorFactory.SETTING_COLUMN_ID, columnId);
    map.put(PrecomputedDetectorFactory.SETTING_COLUMN_FRAME, columnFrame);
    map.put(PrecomputedDetectorFactory.SETTING_COLUMN_X, columnX);
    map.put(PrecomputedDetectorFactory.SETTING_COLUMN_Y, columnY);
    map.put(PrecomputedDetectorFactory.SETTING_COLUMN_Z, columnZ);
    map.put(PrecomputedDetectorFactory.SETTING_COLUMN_RADIUS, columnRadius);
    map.put(PrecomputedDetectorFactory.SETTING_COLUMN_CATEGORY, columnCategory);
    map.put(PrecomputedDetectorFactory.SETTING_CATEGORY_FILE, categoryFile);
    return map;
  }

  @Override
  public void clean() {
    // Do nothing
  }

  /**
   * Launch detection on the current frame.
   */
  private void preview() {
    btnPreview.setEnabled(false);
    CompletableFuture.runAsync(() -> {
      try {
        final Settings lSettings = new Settings();
        lSettings.setFrom(imp);
        final int frame = imp.getFrame() - 1;
        lSettings.tstart = frame;
        lSettings.tend = frame;

        lSettings.detectorFactory = getDetectorFactory();
        lSettings.detectorSettings = getSettings();

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
        for (final Spot spot : spotsToCopy) {
          spot.putFeature(SpotCollection.VISIBLITY, SpotCollection.ONE);
        }

        // Generate event for listener to reflect changes.
        model.setSpots(model.getSpots(), true);

      } finally {
        btnPreview.setEnabled(true);
      }
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
    return new PrecomputedDetectorFactory();
  }

  private void initialiseLayout() {
    try {
      this.setPreferredSize(new java.awt.Dimension(300, 461));
      setLayout(new BorderLayout());
      final JPanel contentPanel = new JPanel();
      contentPanel.setBorder(new EmptyBorder(5, 5, 5, 5));
      add(contentPanel);
      final GridBagLayout layout = new GridBagLayout();
      contentPanel.setLayout(layout);
      final JLabel labelSegmenterName = new JLabel();
      labelSegmenterName.setFont(BIG_FONT);
      labelSegmenterName.setText(PrecomputedDetectorFactory.NAME);
      int row = 0;
      contentPanel.add(labelSegmenterName, createConstraints(2, 0, row++));
      textFieldInputFile = createTextField(15);
      addFields(contentPanel, "Input file", textFieldInputFile, row++);
      // layout.getConstraints(textFieldInputFile).fill = GridBagConstraints.HORIZONTAL;

      // Add button to select the file
      final JButton btnInputSelect = new JButton("File open");
      btnInputSelect.setToolTipText("Select the input file.");
      btnInputSelect.setFont(SMALL_FONT);
      btnInputSelect.addActionListener(e -> {
        final String newName =
            ImageJUtils.getFilename("Select input file", textFieldInputFile.getText());
        if (newName != null) {
          textFieldInputFile.setText(newName);
        }
      });
      contentPanel.add(btnInputSelect, createConstraints(1, 1, row++));

      textFieldHeaderLines = createIntegerTextField();
      addFields(contentPanel, "Header lines", textFieldHeaderLines, row++);
      textFieldCommentChar = createTextField(5);
      addFields(contentPanel, "Comment chars", textFieldCommentChar, row++);
      textFieldDelimiter = createTextField(5);
      addFields(contentPanel, "Delimiter", textFieldDelimiter, row++);
      textFieldColumnId = createIntegerTextField();
      addFields(contentPanel, "Column ID", textFieldColumnId, row++);
      textFieldColumnFrame = createIntegerTextField();
      addFields(contentPanel, "Column Frame", textFieldColumnFrame, row++);
      textFieldColumnX = createIntegerTextField();
      addFields(contentPanel, "Column X", textFieldColumnX, row++);
      textFieldColumnY = createIntegerTextField();
      addFields(contentPanel, "Column Y", textFieldColumnY, row++);
      // Only make visible if contains a z dimension
      textFieldColumnZ = createIntegerTextField();
      if (imp.getNSlices() > 1) {
        addFields(contentPanel, "Column Z", textFieldColumnZ, row++);
      }
      textFieldRadius = createIntegerTextField();
      addFields(contentPanel, "Column radius", textFieldRadius, row++);
      textFieldCategory = createIntegerTextField();
      addFields(contentPanel, "Column category", textFieldCategory, row++);

      textFieldCategoryFile = createTextField(15);
      addFields(contentPanel, "Category file", textFieldCategoryFile, row++);
      // layout.getConstraints(textFieldInputFile).fill = GridBagConstraints.HORIZONTAL;

      // Add button to select the file
      final JButton btnCategorySelect = new JButton("File open");
      btnCategorySelect.setToolTipText("Select the category file.");
      btnCategorySelect.setFont(SMALL_FONT);
      btnCategorySelect.addActionListener(e -> {
        final String newName =
            ImageJUtils.getFilename("Select category file", textFieldCategoryFile.getText());
        if (newName != null) {
          textFieldCategoryFile.setText(newName);
        }
      });
      contentPanel.add(btnCategorySelect, createConstraints(1, 1, row++));

      btnPreview = new JButton("Preview", ICON_PREVIEW);
      btnPreview.setToolTipText("Preview the current settings on the current frame.");
      btnPreview.setFont(SMALL_FONT);
      btnPreview.addActionListener(e -> preview());
      contentPanel.add(btnPreview, createConstraints(1, 1, row++));
      final HtmlJLabelLogger labelLogger = new HtmlJLabelLogger();
      contentPanel.add(labelLogger, createConstraints(2, 0, row));
      localLogger = labelLogger.getLogger();
    } catch (final Exception ex) {
      java.util.logging.Logger.getLogger(PrecomputedDetector.class.getSimpleName())
          .log(Level.WARNING, "Failed to create configuration panel", ex);
    }
  }

  private static JTextField createTextField(int columns) {
    final JTextField textField = new JTextField();
    textField.setHorizontalAlignment(SwingConstants.LEFT);
    textField.setColumns(columns);
    return textField;
  }

  private static JNumericTextField createIntegerTextField() {
    final JNumericTextField textField = new JNumericTextField();
    textField.setHorizontalAlignment(SwingConstants.CENTER);
    textField.setColumns(5);
    textField.setFormat("%.0f");
    return textField;
  }

  private static GridBagConstraints createConstraints(int gridwidth, int gridx, int gridy) {
    final GridBagConstraints gbc = new GridBagConstraints();
    gbc.gridwidth = gridwidth;
    gbc.anchor = GridBagConstraints.WEST;
    gbc.insets = new Insets(0, 0, 5, 5);
    gbc.gridx = gridx;
    gbc.gridy = gridy;
    // gbc.fill = GridBagConstraints.HORIZONTAL;
    return gbc;
  }

  private static final void addFields(JPanel panel, String label, Component comp, int row) {
    final JLabel jlabel = new JLabel();
    jlabel.setText(label);
    addFields(panel, jlabel, comp, row);
  }

  private static final void addFields(JPanel panel, JLabel jlabel, Component comp, int row) {
    jlabel.setFont(FONT);
    panel.add(jlabel, createConstraints(1, 0, row));

    comp.setFont(FONT);
    panel.add(comp, createConstraints(1, 1, row));
  }
}
