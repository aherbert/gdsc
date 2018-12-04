/*-
 * #%L
 * Genome Damage and Stability Centre ImageJ Plugins
 *
 * Software for microscopy image analysis
 * %%
 * Copyright (C) 2011 - 2018 Alex Herbert
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

package uk.ac.sussex.gdsc.foci.gui;

import uk.ac.sussex.gdsc.core.ij.ImageJUtils;
import uk.ac.sussex.gdsc.foci.FindFoci_PlugIn;
import uk.ac.sussex.gdsc.foci.converter.CentreMethodConverter;
import uk.ac.sussex.gdsc.foci.converter.CentreParamEnabledConverter;
import uk.ac.sussex.gdsc.foci.converter.DoubleConverter;
import uk.ac.sussex.gdsc.foci.converter.SliderDoubleConverter;
import uk.ac.sussex.gdsc.foci.model.FindFociModel;
import uk.ac.sussex.gdsc.format.LimitedNumberFormat;

import org.jdesktop.beansbinding.AutoBinding;
import org.jdesktop.beansbinding.AutoBinding.UpdateStrategy;
import org.jdesktop.beansbinding.BeanProperty;
import org.jdesktop.beansbinding.Bindings;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFormattedTextField;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.WindowConstants;
import javax.swing.border.EmptyBorder;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

/**
 * Provides additional options for the {@link FindFociView}.
 */
public class FindFociAdvancedOptions extends JDialog {
  private static final long serialVersionUID = -217510642094281899L;
  private final FindFociModel model;

  private final JPanel contentPanel = new JPanel();
  private JCheckBox chckbxShowTable;
  private JCheckBox chckbxMarkMaxima;
  private JCheckBox chckbxMarkPeakMaxima;
  private JCheckBox chckbxShowLogMessages;
  private JCheckBox chckbxSaveResults;
  private JTextField txtResultsDirectory;
  private JLabel lblResultsDirectory;
  private JButton btnDirectoryPicker;
  private JLabel lblCentreMethod;
  private JComboBox<String> comboBoxCentreMethod;
  private JSlider sliderCentreParam;
  private JLabel lblCentreParam;
  private JFormattedTextField txtCentreParam;
  private JCheckBox chckbxShowMaskMaxima;
  private JCheckBox chckbxRemoveEdgeMaxima;
  private JCheckBox chckbxObjectAnalysis;
  private JCheckBox chckbxShowObjectMask;
  private JCheckBox chckbxClearTable;
  private JCheckBox chckbxSaveToMemory;
  private JCheckBox chckbxHideLabels;
  private JCheckBox chckbxOverlayMask;
  private JCheckBox chckbxMarkUsingOverlay;

  /**
   * Launch the application.
   *
   * @param args the arguments
   */
  public static void main(String[] args) {
    try {
      final FindFociAdvancedOptions dialog = new FindFociAdvancedOptions();
      dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
      dialog.setVisible(true);
    } catch (final Exception ex) {
      ex.printStackTrace();
    }
  }

  /**
   * Create the dialog.
   *
   * @param model the model
   */
  public FindFociAdvancedOptions(FindFociModel model) {
    this.model = model;
    init();
  }

  /**
   * Create the dialog.
   */
  public FindFociAdvancedOptions() {
    setTitle("FindFoci Options");
    this.model = new FindFociModel();
    init();
  }

  private void init() {
    setBounds(100, 100, 450, 589);
    getContentPane().setLayout(new BorderLayout());
    contentPanel.setBorder(new EmptyBorder(5, 5, 5, 5));
    getContentPane().add(contentPanel, BorderLayout.CENTER);
    final GridBagLayout gbl_contentPanel = new GridBagLayout();
    gbl_contentPanel.columnWidths = new int[] {0, 182, 50, 0};
    gbl_contentPanel.rowHeights =
        new int[] {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0};
    gbl_contentPanel.columnWeights = new double[] {0.0, 1.0, 0.0, Double.MIN_VALUE};
    gbl_contentPanel.rowWeights = new double[] {0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0,
        0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, Double.MIN_VALUE};
    contentPanel.setLayout(gbl_contentPanel);
    {

      {
        chckbxOverlayMask = new JCheckBox("Overlay mask");
        chckbxOverlayMask.setToolTipText("Overlay the mask of the foci on the image");
        chckbxOverlayMask.addItemListener(new ItemListener() {
          @Override
          public void itemStateChanged(ItemEvent e) {
            chckbxOverlayMask.firePropertyChange("selected", 0, 1);
          }
        });
        final GridBagConstraints gbc_chckbxOverlayMask = new GridBagConstraints();
        gbc_chckbxOverlayMask.gridwidth = 2;
        gbc_chckbxOverlayMask.anchor = GridBagConstraints.WEST;
        gbc_chckbxOverlayMask.insets = new Insets(0, 0, 5, 5);
        gbc_chckbxOverlayMask.gridx = 0;
        gbc_chckbxOverlayMask.gridy = 0;
        contentPanel.add(chckbxOverlayMask, gbc_chckbxOverlayMask);
      }
      {
        chckbxShowTable = new JCheckBox("Show table");
        chckbxShowTable.setToolTipText("Display a table of results");
        chckbxShowTable.addItemListener(new ItemListener() {
          @Override
          public void itemStateChanged(ItemEvent e) {
            chckbxShowTable.firePropertyChange("selected", 0, 1);
          }
        });
        chckbxShowTable.setMargin(new Insets(2, 2, 2, 0));
        final GridBagConstraints gbc_chckbxShowTable = new GridBagConstraints();
        gbc_chckbxShowTable.gridwidth = 2;
        gbc_chckbxShowTable.anchor = GridBagConstraints.WEST;
        gbc_chckbxShowTable.insets = new Insets(0, 0, 5, 5);
        gbc_chckbxShowTable.gridx = 0;
        gbc_chckbxShowTable.gridy = 1;
        contentPanel.add(chckbxShowTable, gbc_chckbxShowTable);
      }
      {
        chckbxClearTable = new JCheckBox("Clear table");
        chckbxClearTable.setToolTipText("Clear the current results from the results table");
        chckbxClearTable.addItemListener(new ItemListener() {
          @Override
          public void itemStateChanged(ItemEvent e) {
            chckbxClearTable.firePropertyChange("selected", 0, 1);
          }
        });
        chckbxClearTable.setMargin(new Insets(2, 2, 2, 0));
        final GridBagConstraints gbc_chckbxClearTable = new GridBagConstraints();
        gbc_chckbxClearTable.gridwidth = 2;
        gbc_chckbxClearTable.anchor = GridBagConstraints.WEST;
        gbc_chckbxClearTable.insets = new Insets(0, 0, 5, 5);
        gbc_chckbxClearTable.gridx = 0;
        gbc_chckbxClearTable.gridy = 2;
        contentPanel.add(chckbxClearTable, gbc_chckbxClearTable);
      }
      {
        chckbxMarkMaxima = new JCheckBox("Mark maxima");
        chckbxMarkMaxima.setToolTipText("Mark the peaks on the original image");
        chckbxMarkMaxima.addItemListener(new ItemListener() {
          @Override
          public void itemStateChanged(ItemEvent e) {
            chckbxMarkMaxima.firePropertyChange("selected", 0, 1);
          }
        });
        chckbxMarkMaxima.setMargin(new Insets(2, 2, 2, 0));
        final GridBagConstraints gbc_chckbxMarkMaxima = new GridBagConstraints();
        gbc_chckbxMarkMaxima.gridwidth = 2;
        gbc_chckbxMarkMaxima.anchor = GridBagConstraints.WEST;
        gbc_chckbxMarkMaxima.insets = new Insets(0, 0, 5, 5);
        gbc_chckbxMarkMaxima.gridx = 0;
        gbc_chckbxMarkMaxima.gridy = 3;
        contentPanel.add(chckbxMarkMaxima, gbc_chckbxMarkMaxima);
      }
      {
        chckbxMarkPeakMaxima = new JCheckBox("Mark peak maxima");
        chckbxMarkPeakMaxima.setToolTipText("Mark the peaks on the mask image");
        chckbxMarkPeakMaxima.addItemListener(new ItemListener() {
          @Override
          public void itemStateChanged(ItemEvent e) {
            chckbxMarkPeakMaxima.firePropertyChange("selected", 0, 1);
          }
        });
        chckbxMarkPeakMaxima.setMargin(new Insets(2, 2, 2, 0));
        final GridBagConstraints gbc_chckbxMarkPeakMaxima = new GridBagConstraints();
        gbc_chckbxMarkPeakMaxima.gridwidth = 2;
        gbc_chckbxMarkPeakMaxima.anchor = GridBagConstraints.WEST;
        gbc_chckbxMarkPeakMaxima.insets = new Insets(0, 0, 5, 5);
        gbc_chckbxMarkPeakMaxima.gridx = 0;
        gbc_chckbxMarkPeakMaxima.gridy = 4;
        contentPanel.add(chckbxMarkPeakMaxima, gbc_chckbxMarkPeakMaxima);
      }
      {
        chckbxMarkUsingOverlay = new JCheckBox("Mark using overlay");
        chckbxMarkUsingOverlay
            .setToolTipText("Mark peaks using an overlay (supports slice z-position)");
        chckbxMarkUsingOverlay.addItemListener(new ItemListener() {
          @Override
          public void itemStateChanged(ItemEvent e) {
            chckbxMarkUsingOverlay.firePropertyChange("selected", 0, 1);
          }
        });
        final GridBagConstraints gbc_chckbxMarkUsingOverlay = new GridBagConstraints();
        gbc_chckbxMarkUsingOverlay.anchor = GridBagConstraints.WEST;
        gbc_chckbxMarkUsingOverlay.gridwidth = 2;
        gbc_chckbxMarkUsingOverlay.insets = new Insets(0, 0, 5, 5);
        gbc_chckbxMarkUsingOverlay.gridx = 0;
        gbc_chckbxMarkUsingOverlay.gridy = 5;
        contentPanel.add(chckbxMarkUsingOverlay, gbc_chckbxMarkUsingOverlay);
      }
      {
        chckbxHideLabels = new JCheckBox("Hide labels");
        chckbxHideLabels.setToolTipText("Hide the labels on the marked maxima");
        chckbxHideLabels.addItemListener(new ItemListener() {
          @Override
          public void itemStateChanged(ItemEvent e) {
            chckbxHideLabels.firePropertyChange("selected", 0, 1);
          }
        });
        final GridBagConstraints gbc_chckbxHideLabels = new GridBagConstraints();
        gbc_chckbxHideLabels.anchor = GridBagConstraints.WEST;
        gbc_chckbxHideLabels.insets = new Insets(0, 0, 5, 5);
        gbc_chckbxHideLabels.gridx = 0;
        gbc_chckbxHideLabels.gridy = 6;
        contentPanel.add(chckbxHideLabels, gbc_chckbxHideLabels);
      }
      {
        chckbxShowMaskMaxima = new JCheckBox("Show mask maxima as dots");
        chckbxShowMaskMaxima.setToolTipText(
            "Mark maxima locations in the mask using a value above all other mask values");
        chckbxShowMaskMaxima.addItemListener(new ItemListener() {
          @Override
          public void itemStateChanged(ItemEvent e) {
            chckbxShowMaskMaxima.firePropertyChange("selected", 0, 1);
          }
        });
        final GridBagConstraints gbc_chckbxShowMaskMaxima = new GridBagConstraints();
        gbc_chckbxShowMaskMaxima.anchor = GridBagConstraints.WEST;
        gbc_chckbxShowMaskMaxima.gridwidth = 2;
        gbc_chckbxShowMaskMaxima.insets = new Insets(0, 0, 5, 5);
        gbc_chckbxShowMaskMaxima.gridx = 0;
        gbc_chckbxShowMaskMaxima.gridy = 7;
        contentPanel.add(chckbxShowMaskMaxima, gbc_chckbxShowMaskMaxima);
      }
      {
        chckbxShowLogMessages = new JCheckBox("Show log messages");
        chckbxShowLogMessages.setToolTipText("Show algorithm information in the log window");
        chckbxShowLogMessages.addItemListener(new ItemListener() {
          @Override
          public void itemStateChanged(ItemEvent e) {
            chckbxShowLogMessages.firePropertyChange("selected", 0, 1);
          }
        });
        chckbxShowLogMessages.setMargin(new Insets(2, 2, 2, 0));
        final GridBagConstraints gbc_chckbxShowLogMessages = new GridBagConstraints();
        gbc_chckbxShowLogMessages.gridwidth = 2;
        gbc_chckbxShowLogMessages.insets = new Insets(0, 0, 5, 5);
        gbc_chckbxShowLogMessages.anchor = GridBagConstraints.WEST;
        gbc_chckbxShowLogMessages.gridx = 0;
        gbc_chckbxShowLogMessages.gridy = 8;
        contentPanel.add(chckbxShowLogMessages, gbc_chckbxShowLogMessages);
      }
    }
    {
      lblCentreMethod = new JLabel("Centre method");
      final GridBagConstraints gbc_lblCentreMethod = new GridBagConstraints();
      gbc_lblCentreMethod.insets = new Insets(0, 0, 5, 5);
      gbc_lblCentreMethod.anchor = GridBagConstraints.EAST;
      gbc_lblCentreMethod.gridx = 0;
      gbc_lblCentreMethod.gridy = 9;
      contentPanel.add(lblCentreMethod, gbc_lblCentreMethod);
    }
    {
      comboBoxCentreMethod = new JComboBox<>();
      comboBoxCentreMethod.setToolTipText("The method used to mark the origin of each peak");
      comboBoxCentreMethod.addItemListener(new ItemListener() {
        @Override
        public void itemStateChanged(ItemEvent e) {
          comboBoxCentreMethod.firePropertyChange("selectedItem", 0, 1);
        }
      });
      comboBoxCentreMethod.setModel(new DefaultComboBoxModel<>(FindFoci_PlugIn.getCentreMethods()));
      final GridBagConstraints gbc_comboBoxCentreMethod = new GridBagConstraints();
      gbc_comboBoxCentreMethod.gridwidth = 2;
      gbc_comboBoxCentreMethod.insets = new Insets(0, 0, 5, 0);
      gbc_comboBoxCentreMethod.fill = GridBagConstraints.HORIZONTAL;
      gbc_comboBoxCentreMethod.gridx = 1;
      gbc_comboBoxCentreMethod.gridy = 9;
      contentPanel.add(comboBoxCentreMethod, gbc_comboBoxCentreMethod);
    }
    {
      lblCentreParam = new JLabel("Centre param");
      final GridBagConstraints gbc_lblCentreParam = new GridBagConstraints();
      gbc_lblCentreParam.anchor = GridBagConstraints.EAST;
      gbc_lblCentreParam.insets = new Insets(0, 0, 5, 5);
      gbc_lblCentreParam.gridx = 0;
      gbc_lblCentreParam.gridy = 10;
      contentPanel.add(lblCentreParam, gbc_lblCentreParam);
    }
    {
      sliderCentreParam = new JSlider();
      sliderCentreParam.addMouseListener(new MouseAdapter() {
        @Override
        public void mouseClicked(MouseEvent e) {
          if (e.getClickCount() > 1) {
            SliderLimitHelper.updateRangeLimits(sliderCentreParam, "Centre parameter", 1, 0,
                Double.POSITIVE_INFINITY);
          }
        }
      });
      sliderCentreParam.setToolTipText("Controls the selected centre method");
      sliderCentreParam.addChangeListener(new ChangeListener() {
        @Override
        public void stateChanged(ChangeEvent e) {
          sliderCentreParam.firePropertyChange("value", 0, 1);
        }
      });
      sliderCentreParam.setMaximum(10);
      final GridBagConstraints gbc_sliderCentreParam = new GridBagConstraints();
      gbc_sliderCentreParam.fill = GridBagConstraints.HORIZONTAL;
      gbc_sliderCentreParam.insets = new Insets(0, 0, 5, 5);
      gbc_sliderCentreParam.gridx = 1;
      gbc_sliderCentreParam.gridy = 10;
      contentPanel.add(sliderCentreParam, gbc_sliderCentreParam);
    }
    {
      txtCentreParam = new JFormattedTextField(new LimitedNumberFormat(0));
      txtCentreParam.addPropertyChangeListener(new PropertyChangeListener() {
        @Override
        public void propertyChange(PropertyChangeEvent evt) {
          if (evt.getPropertyName() == "value") {
            txtCentreParam.firePropertyChange("text", 0, 1);
          }
        }
      });
      txtCentreParam.addKeyListener(new KeyAdapter() {
        @Override
        public void keyReleased(KeyEvent e) {
          txtCentreParam.firePropertyChange("text", 0, 1);
        }
      });
      txtCentreParam.setHorizontalAlignment(SwingConstants.TRAILING);
      txtCentreParam.setText("0");
      final GridBagConstraints gbc_txtCentreParam = new GridBagConstraints();
      gbc_txtCentreParam.fill = GridBagConstraints.HORIZONTAL;
      gbc_txtCentreParam.insets = new Insets(0, 0, 5, 0);
      gbc_txtCentreParam.gridx = 2;
      gbc_txtCentreParam.gridy = 10;
      contentPanel.add(txtCentreParam, gbc_txtCentreParam);
    }
    {
      chckbxRemoveEdgeMaxima = new JCheckBox("Remove edge maxima");
      chckbxRemoveEdgeMaxima
          .setToolTipText("Remove maxima touching the edge of the analysis region");
      chckbxRemoveEdgeMaxima.addItemListener(new ItemListener() {
        @Override
        public void itemStateChanged(ItemEvent e) {
          chckbxRemoveEdgeMaxima.firePropertyChange("selected", 0, 1);
        }
      });
      final GridBagConstraints gbc_chckbxRemoveEdgeMaxima = new GridBagConstraints();
      gbc_chckbxRemoveEdgeMaxima.anchor = GridBagConstraints.WEST;
      gbc_chckbxRemoveEdgeMaxima.gridwidth = 2;
      gbc_chckbxRemoveEdgeMaxima.insets = new Insets(0, 0, 5, 5);
      gbc_chckbxRemoveEdgeMaxima.gridx = 0;
      gbc_chckbxRemoveEdgeMaxima.gridy = 11;
      contentPanel.add(chckbxRemoveEdgeMaxima, gbc_chckbxRemoveEdgeMaxima);
    }
    {
      chckbxSaveResults = new JCheckBox("Save results");
      chckbxSaveResults.setToolTipText("Save the results to a directory");
      chckbxSaveResults.addItemListener(new ItemListener() {
        @Override
        public void itemStateChanged(ItemEvent e) {
          chckbxSaveResults.firePropertyChange("selected", 0, 1);
        }
      });
      chckbxSaveResults.setMargin(new Insets(2, 2, 2, 0));
      final GridBagConstraints gbc_chckbxSaveResults = new GridBagConstraints();
      gbc_chckbxSaveResults.insets = new Insets(0, 0, 5, 5);
      gbc_chckbxSaveResults.anchor = GridBagConstraints.WEST;
      gbc_chckbxSaveResults.gridx = 0;
      gbc_chckbxSaveResults.gridy = 12;
      contentPanel.add(chckbxSaveResults, gbc_chckbxSaveResults);
    }
    {
      lblResultsDirectory = new JLabel("Results directory:");
      final GridBagConstraints gbc_lblResultsDirectory = new GridBagConstraints();
      gbc_lblResultsDirectory.insets = new Insets(0, 0, 5, 5);
      gbc_lblResultsDirectory.anchor = GridBagConstraints.WEST;
      gbc_lblResultsDirectory.gridx = 0;
      gbc_lblResultsDirectory.gridy = 13;
      contentPanel.add(lblResultsDirectory, gbc_lblResultsDirectory);
    }
    {
      txtResultsDirectory = new JTextField();
      txtResultsDirectory.setToolTipText("Sepcify the results directory");
      txtResultsDirectory.addKeyListener(new KeyAdapter() {
        @Override
        public void keyReleased(KeyEvent e) {
          txtResultsDirectory.firePropertyChange("text", 0, 1);
        }
      });
      final GridBagConstraints gbc_txtResultsDirectory = new GridBagConstraints();
      gbc_txtResultsDirectory.insets = new Insets(0, 0, 5, 5);
      gbc_txtResultsDirectory.gridwidth = 2;
      gbc_txtResultsDirectory.fill = GridBagConstraints.HORIZONTAL;
      gbc_txtResultsDirectory.gridx = 0;
      gbc_txtResultsDirectory.gridy = 14;
      contentPanel.add(txtResultsDirectory, gbc_txtResultsDirectory);
      txtResultsDirectory.setColumns(10);
    }
    {
      btnDirectoryPicker = new JButton("...");
      btnDirectoryPicker.setToolTipText("Open a directory picker");
      btnDirectoryPicker.addMouseListener(new MouseAdapter() {
        @Override
        public void mouseClicked(MouseEvent e) {
          final String dir = ImageJUtils.getDirectory(getTitle(), model.getResultsDirectory());
          if (dir != null) {
            model.setResultsDirectory(dir);
          }
        }
      });
      btnDirectoryPicker.setMargin(new Insets(2, 2, 2, 2));
      final GridBagConstraints gbc_btnDirectoryPicker = new GridBagConstraints();
      gbc_btnDirectoryPicker.fill = GridBagConstraints.HORIZONTAL;
      gbc_btnDirectoryPicker.insets = new Insets(0, 0, 5, 0);
      gbc_btnDirectoryPicker.gridx = 2;
      gbc_btnDirectoryPicker.gridy = 14;
      contentPanel.add(btnDirectoryPicker, gbc_btnDirectoryPicker);
    }
    {
      chckbxObjectAnalysis = new JCheckBox("Object analysis");
      chckbxObjectAnalysis
          .setToolTipText("Compute objects within the mask and label maxima within each object");
      chckbxObjectAnalysis.addItemListener(new ItemListener() {
        @Override
        public void itemStateChanged(ItemEvent e) {
          chckbxObjectAnalysis.firePropertyChange("selected", 0, 1);
        }
      });
      chckbxObjectAnalysis.setMargin(new Insets(2, 2, 2, 0));
      final GridBagConstraints gbc_chckbxObjectAnalysis = new GridBagConstraints();
      gbc_chckbxObjectAnalysis.anchor = GridBagConstraints.WEST;
      gbc_chckbxObjectAnalysis.insets = new Insets(0, 0, 5, 5);
      gbc_chckbxObjectAnalysis.gridx = 0;
      gbc_chckbxObjectAnalysis.gridy = 15;
      contentPanel.add(chckbxObjectAnalysis, gbc_chckbxObjectAnalysis);
    }
    {
      chckbxShowObjectMask = new JCheckBox("Show object mask");
      chckbxShowObjectMask.setToolTipText("Show the mask of the computed objects");
      chckbxShowObjectMask.addItemListener(new ItemListener() {
        @Override
        public void itemStateChanged(ItemEvent e) {
          chckbxShowObjectMask.firePropertyChange("selected", 0, 1);
        }
      });
      chckbxShowObjectMask.setMargin(new Insets(2, 2, 2, 0));
      final GridBagConstraints gbc_chckbxShowObjectMask = new GridBagConstraints();
      gbc_chckbxShowObjectMask.insets = new Insets(0, 0, 5, 5);
      gbc_chckbxShowObjectMask.gridx = 0;
      gbc_chckbxShowObjectMask.gridy = 16;
      contentPanel.add(chckbxShowObjectMask, gbc_chckbxShowObjectMask);
    }
    {
      chckbxSaveToMemory = new JCheckBox("Save to memory");
      chckbxSaveToMemory
          .setToolTipText("Save the result to memory (allows other plugins to access the results)");
      chckbxSaveToMemory.addItemListener(new ItemListener() {
        @Override
        public void itemStateChanged(ItemEvent e) {
          chckbxSaveToMemory.firePropertyChange("selected", 0, 1);
        }
      });
      chckbxSaveToMemory.setMargin(new Insets(2, 2, 2, 0));
      final GridBagConstraints gbc_chckbxSaveToMemory = new GridBagConstraints();
      gbc_chckbxSaveToMemory.anchor = GridBagConstraints.WEST;
      gbc_chckbxSaveToMemory.insets = new Insets(0, 0, 0, 5);
      gbc_chckbxSaveToMemory.gridx = 0;
      gbc_chckbxSaveToMemory.gridy = 17;
      contentPanel.add(chckbxSaveToMemory, gbc_chckbxSaveToMemory);
    }
    {
      final JPanel buttonPane = new JPanel();
      buttonPane.setLayout(new FlowLayout(FlowLayout.RIGHT));
      getContentPane().add(buttonPane, BorderLayout.SOUTH);
      {
        final JButton okButton = new JButton("OK");
        okButton.setToolTipText("Close this window");
        okButton.addMouseListener(new MouseAdapter() {
          @Override
          public void mouseClicked(MouseEvent e) {
            setVisible(false);
          }
        });
        okButton.setActionCommand("OK");
        buttonPane.add(okButton);
        getRootPane().setDefaultButton(okButton);
      }
    }
    initDataBindings();

    this.pack();
  }

  /**
   * Inits the data bindings.
   */
  protected void initDataBindings() {
    final BeanProperty<FindFociModel, Boolean> findFociModelBeanProperty =
        BeanProperty.create("showTable");
    final BeanProperty<JCheckBox, Boolean> jCheckBoxBeanProperty = BeanProperty.create("selected");
    final AutoBinding<FindFociModel, Boolean, JCheckBox, Boolean> autoBinding =
        Bindings.createAutoBinding(UpdateStrategy.READ_WRITE, model, findFociModelBeanProperty,
            chckbxShowTable, jCheckBoxBeanProperty);
    autoBinding.bind();
    //
    final BeanProperty<FindFociModel, Boolean> findFociModelBeanProperty_1 =
        BeanProperty.create("markMaxima");
    final AutoBinding<FindFociModel, Boolean, JCheckBox, Boolean> autoBinding_1 =
        Bindings.createAutoBinding(UpdateStrategy.READ_WRITE, model, findFociModelBeanProperty_1,
            chckbxMarkMaxima, jCheckBoxBeanProperty);
    autoBinding_1.bind();
    //
    final BeanProperty<FindFociModel, Boolean> findFociModelBeanProperty_2 =
        BeanProperty.create("markROIMaxima");
    final AutoBinding<FindFociModel, Boolean, JCheckBox, Boolean> autoBinding_2 =
        Bindings.createAutoBinding(UpdateStrategy.READ_WRITE, model, findFociModelBeanProperty_2,
            chckbxMarkPeakMaxima, jCheckBoxBeanProperty);
    autoBinding_2.bind();
    //
    final BeanProperty<FindFociModel, Boolean> findFociModelBeanProperty_3 =
        BeanProperty.create("showLogMessages");
    final AutoBinding<FindFociModel, Boolean, JCheckBox, Boolean> autoBinding_3 =
        Bindings.createAutoBinding(UpdateStrategy.READ_WRITE, model, findFociModelBeanProperty_3,
            chckbxShowLogMessages, jCheckBoxBeanProperty);
    autoBinding_3.bind();
    //
    final BeanProperty<FindFociModel, Boolean> findFociModelBeanProperty_4 =
        BeanProperty.create("saveResults");
    final AutoBinding<FindFociModel, Boolean, JCheckBox, Boolean> autoBinding_4 =
        Bindings.createAutoBinding(UpdateStrategy.READ_WRITE, model, findFociModelBeanProperty_4,
            chckbxSaveResults, jCheckBoxBeanProperty);
    autoBinding_4.bind();
    //
    final BeanProperty<FindFociModel, String> findFociModelBeanProperty_5 =
        BeanProperty.create("resultsDirectory");
    final BeanProperty<JTextField, String> jTextFieldBeanProperty = BeanProperty.create("text");
    final AutoBinding<FindFociModel, String, JTextField, String> autoBinding_5 =
        Bindings.createAutoBinding(UpdateStrategy.READ_WRITE, model, findFociModelBeanProperty_5,
            txtResultsDirectory, jTextFieldBeanProperty);
    autoBinding_5.bind();
    //
    final BeanProperty<FindFociModel, Integer> findFociModelBeanProperty_6 =
        BeanProperty.create("centreMethod");
    final BeanProperty<JComboBox<String>, Object> jComboBoxBeanProperty =
        BeanProperty.create("selectedItem");
    final AutoBinding<FindFociModel, Integer, JComboBox<String>, Object> autoBinding_6 =
        Bindings.createAutoBinding(UpdateStrategy.READ_WRITE, model, findFociModelBeanProperty_6,
            comboBoxCentreMethod, jComboBoxBeanProperty);
    autoBinding_6.setConverter(new CentreMethodConverter());
    autoBinding_6.bind();
    //
    final BeanProperty<FindFociModel, Double> findFociModelBeanProperty_7 =
        BeanProperty.create("centreParameter");
    final BeanProperty<JSlider, Integer> jSliderBeanProperty = BeanProperty.create("value");
    final AutoBinding<FindFociModel, Double, JSlider, Integer> autoBinding_7 =
        Bindings.createAutoBinding(UpdateStrategy.READ_WRITE, model, findFociModelBeanProperty_7,
            sliderCentreParam, jSliderBeanProperty);
    autoBinding_7.setConverter(new SliderDoubleConverter());
    autoBinding_7.bind();
    //
    final BeanProperty<JFormattedTextField, Boolean> jFormattedTextFieldBeanProperty_1 =
        BeanProperty.create("enabled");
    final AutoBinding<FindFociModel, Integer, JFormattedTextField, Boolean> autoBinding_9 =
        Bindings.createAutoBinding(UpdateStrategy.READ, model, findFociModelBeanProperty_6,
            txtCentreParam, jFormattedTextFieldBeanProperty_1);
    autoBinding_9.setConverter(new CentreParamEnabledConverter());
    autoBinding_9.bind();
    //
    final BeanProperty<JSlider, Boolean> jSliderBeanProperty_1 = BeanProperty.create("enabled");
    final AutoBinding<FindFociModel, Integer, JSlider, Boolean> autoBinding_10 =
        Bindings.createAutoBinding(UpdateStrategy.READ, model, findFociModelBeanProperty_6,
            sliderCentreParam, jSliderBeanProperty_1);
    autoBinding_10.setConverter(new CentreParamEnabledConverter());
    autoBinding_10.bind();
    //
    final BeanProperty<JFormattedTextField, String> jFormattedTextFieldBeanProperty_2 =
        BeanProperty.create("text");
    final AutoBinding<FindFociModel, Double, JFormattedTextField, String> autoBinding_11 =
        Bindings.createAutoBinding(UpdateStrategy.READ_WRITE, model, findFociModelBeanProperty_7,
            txtCentreParam, jFormattedTextFieldBeanProperty_2);
    autoBinding_11.setConverter(new DoubleConverter());
    autoBinding_11.bind();
    //
    final BeanProperty<FindFociModel, Boolean> findFociModelBeanProperty_8 =
        BeanProperty.create("showMaskMaximaAsDots");
    final AutoBinding<FindFociModel, Boolean, JCheckBox, Boolean> autoBinding_8 =
        Bindings.createAutoBinding(UpdateStrategy.READ_WRITE, model, findFociModelBeanProperty_8,
            chckbxShowMaskMaxima, jCheckBoxBeanProperty);
    autoBinding_8.bind();
    //
    final BeanProperty<FindFociModel, Boolean> findFociModelBeanProperty_9 =
        BeanProperty.create("removeEdgeMaxima");
    final AutoBinding<FindFociModel, Boolean, JCheckBox, Boolean> autoBinding_12 =
        Bindings.createAutoBinding(UpdateStrategy.READ_WRITE, model, findFociModelBeanProperty_9,
            chckbxRemoveEdgeMaxima, jCheckBoxBeanProperty);
    autoBinding_12.bind();
    //
    final BeanProperty<FindFociModel, Boolean> findFociModelBeanProperty_10 =
        BeanProperty.create("objectAnalysis");
    final AutoBinding<FindFociModel, Boolean, JCheckBox, Boolean> autoBinding_13 =
        Bindings.createAutoBinding(UpdateStrategy.READ_WRITE, model, findFociModelBeanProperty_10,
            chckbxObjectAnalysis, jCheckBoxBeanProperty);
    autoBinding_13.bind();
    //
    final BeanProperty<FindFociModel, Boolean> findFociModelBeanProperty_11 =
        BeanProperty.create("showObjectMask");
    final AutoBinding<FindFociModel, Boolean, JCheckBox, Boolean> autoBinding_14 =
        Bindings.createAutoBinding(UpdateStrategy.READ_WRITE, model, findFociModelBeanProperty_11,
            chckbxShowObjectMask, jCheckBoxBeanProperty);
    autoBinding_14.bind();
    //
    final BeanProperty<FindFociModel, Boolean> findFociModelBeanProperty_12 =
        BeanProperty.create("clearTable");
    final AutoBinding<FindFociModel, Boolean, JCheckBox, Boolean> autoBinding_15 =
        Bindings.createAutoBinding(UpdateStrategy.READ_WRITE, model, findFociModelBeanProperty_12,
            chckbxClearTable, jCheckBoxBeanProperty);
    autoBinding_15.bind();
    //
    final BeanProperty<FindFociModel, Boolean> findFociModelBeanProperty_13 =
        BeanProperty.create("saveToMemory");
    final AutoBinding<FindFociModel, Boolean, JCheckBox, Boolean> autoBinding_16 =
        Bindings.createAutoBinding(UpdateStrategy.READ_WRITE, model, findFociModelBeanProperty_13,
            chckbxSaveToMemory, jCheckBoxBeanProperty);
    autoBinding_16.bind();
    //
    final BeanProperty<FindFociModel, Boolean> findFociModelBeanProperty_14 =
        BeanProperty.create("hideLabels");
    final AutoBinding<FindFociModel, Boolean, JCheckBox, Boolean> autoBinding_17 =
        Bindings.createAutoBinding(UpdateStrategy.READ_WRITE, model, findFociModelBeanProperty_14,
            chckbxHideLabels, jCheckBoxBeanProperty);
    autoBinding_17.bind();
    //
    final BeanProperty<FindFociModel, Boolean> findFociModelBeanProperty_15 =
        BeanProperty.create("overlayMask");
    final AutoBinding<FindFociModel, Boolean, JCheckBox, Boolean> autoBinding_18 =
        Bindings.createAutoBinding(UpdateStrategy.READ_WRITE, model, findFociModelBeanProperty_15,
            chckbxOverlayMask, jCheckBoxBeanProperty);
    autoBinding_18.bind();
    //
    final BeanProperty<FindFociModel, Boolean> findFociModelBeanProperty_16 =
        BeanProperty.create("markUsingOverlay");
    final AutoBinding<FindFociModel, Boolean, JCheckBox, Boolean> autoBinding_19 =
        Bindings.createAutoBinding(UpdateStrategy.READ_WRITE, model, findFociModelBeanProperty_16,
            chckbxMarkUsingOverlay, jCheckBoxBeanProperty);
    autoBinding_19.bind();
  }
}
