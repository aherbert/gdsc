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

package uk.ac.sussex.gdsc.foci.gui;

import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.WindowManager;
import ij.gui.ImageCanvas;
import ij.gui.PointRoi;
import ij.gui.Roi;
import ij.measure.Calibration;
import ij.text.TextWindow;
import java.awt.Color;
import java.awt.EventQueue;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Polygon;
import java.awt.Rectangle;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFormattedTextField;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JToggleButton;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;
import org.jdesktop.beansbinding.AutoBinding;
import org.jdesktop.beansbinding.AutoBinding.UpdateStrategy;
import org.jdesktop.beansbinding.BeanProperty;
import org.jdesktop.beansbinding.Bindings;
import org.jdesktop.swingbinding.JComboBoxBinding;
import org.jdesktop.swingbinding.SwingBindings;
import uk.ac.sussex.gdsc.core.ij.BufferedTextWindow;
import uk.ac.sussex.gdsc.core.ij.ImageJUtils;
import uk.ac.sussex.gdsc.core.utils.LocalList;
import uk.ac.sussex.gdsc.core.utils.MathUtils;
import uk.ac.sussex.gdsc.core.utils.TextUtils;
import uk.ac.sussex.gdsc.foci.AssignedFindFociResult;
import uk.ac.sussex.gdsc.foci.AssignedFindFociResultSearchIndex;
import uk.ac.sussex.gdsc.foci.AssignedFindFociResultSearchIndex.SearchMode;
import uk.ac.sussex.gdsc.foci.AssignedPoint;
import uk.ac.sussex.gdsc.foci.AssignedPointUtils;
import uk.ac.sussex.gdsc.foci.FindFociProcessorOptions.BackgroundMethod;
import uk.ac.sussex.gdsc.foci.FindFociProcessorOptions.CentreMethod;
import uk.ac.sussex.gdsc.foci.FindFociProcessorOptions.PeakMethod;
import uk.ac.sussex.gdsc.foci.FindFociProcessorOptions.SearchMethod;
import uk.ac.sussex.gdsc.foci.FindFociProcessorOptions.SortMethod;
import uk.ac.sussex.gdsc.foci.FindFociProcessorOptions.StatisticsMethod;
import uk.ac.sussex.gdsc.foci.FindFociProcessorOptions.ThresholdMethod;
import uk.ac.sussex.gdsc.foci.FindFociResult;
import uk.ac.sussex.gdsc.foci.FindFociResults;
import uk.ac.sussex.gdsc.foci.Match_PlugIn;
import uk.ac.sussex.gdsc.foci.controller.FindMaximaController;
import uk.ac.sussex.gdsc.foci.converter.SearchModeConverter;
import uk.ac.sussex.gdsc.foci.converter.StringToBooleanConverter;
import uk.ac.sussex.gdsc.foci.converter.ValidImagesConverter;
import uk.ac.sussex.gdsc.foci.model.FindFociModel;
import uk.ac.sussex.gdsc.format.LimitedNumberFormat;

/**
 * Provides a permanent form front-end that allows the user to pick ROI points and have them mapped
 * to the closest maxima found by the FindFoci algorithm.
 *
 * <p>Although this class extends {@link java.awt.Component} it is not {@link Serializable}.
 */
public class FindFociHelperView extends JFrame
    implements WindowListener, MouseListener, MouseMotionListener {
  // -----------------
  // Note:
  // This class has been created using the Eclipse WindowBuilder.
  // The init() and initDataBindings() methods are generated code.
  // Additional events have been manually added to some components to trigger
  // changes for the model data bindings.
  // See: https://www.eclipse.org/windowbuilder/
  // -----------------

  // There are custom objects that are not Serializable so serialisation would not work.
  private static final long serialVersionUID = -3550748049045647859L;

  private final transient Object updatingPointsLock = new Object();

  // Flags used to control the enabled status of the run button.
  // The button should be enabled when there are images in the list.
  private boolean runEnabled;
  private int potentialMaxima;
  private int resolution = 10;
  private boolean logging = true;
  private int searchMode;
  private boolean assignDragged = true;
  private String activeImage = "";
  private int mappedPoints;
  private int unmappedPoints;
  private boolean showOverlay;

  private boolean counterEvents = true;

  /**
   * The instance. This is required for the auto-binding properties used by the BeansBinding
   * framework.
   */
  private final FindFociHelperView instance = this;

  private transient FindFociModel model;
  private transient FindMaximaController controller;

  private transient ImagePlus activeImp;
  private transient AssignedFindFociResultSearchIndex index;
  private int currentRoiPoints;
  private boolean dragging;
  private static AtomicReference<TextWindow> resultsWindow = new AtomicReference<>();
  private Roi savedRoi;

  private JPanel contentPane;
  private JLabel lblImage;
  private JComboBox<String> comboImageList;
  private JButton btnRun;
  private JLabel labelPotentialMaxima;
  private JLabel lblNumberOfPotential;
  private JLabel lblResolution;
  private JFormattedTextField txtResolution;
  private JLabel lblLogMessages;
  private JCheckBox chckbxLogmessages;
  private JLabel lblActiveImage;
  private JLabel txtActiveImage;
  private JLabel txtMappedPoints;
  private JLabel lblMappedPoints;
  private JLabel txtUnmappedPoints;
  private JLabel lblUnmappedPoints;
  private JLabel lblPoints;
  private JLabel lblTotal;
  private JLabel txtTotal;
  private JButton btnStop;
  private JLabel lblSearchMode;
  private JComboBox<String> comboSearchMode;
  private JCheckBox chckbxAssigndragged;
  private JLabel lblAssignDragged;
  private JButton btnSaveResults;
  private JToggleButton tglbtnOverlay;
  private JButton btnHelp;
  private JLabel lblMaskImage;
  private JComboBox<String> comboMaskImageList;

  /**
   * A mapping between a point and a result.
   */
  private static class Mapping {
    final AssignedPoint point;
    final AssignedFindFociResult assignedResult;
    final double distance;

    /**
     * Instantiates a new mapping.
     *
     * @param point the point
     * @param assignedResult the assigned result
     * @param distance the distance
     */
    Mapping(AssignedPoint point, AssignedFindFociResult assignedResult, double distance) {
      this.point = point;
      this.assignedResult = assignedResult;
      this.distance = distance;
    }
  }

  /**
   * Launch the application.
   *
   * @param args the arguments
   */
  public static void main(String[] args) {
    EventQueue.invokeLater(() -> {
      try {
        final FindFociHelperView frame = new FindFociHelperView();
        frame.setVisible(true);
      } catch (final Exception ex) {
        Logger.getLogger(FindFociHelperView.class.getName()).log(Level.SEVERE,
            "Error showing the frame", ex);
      }
    });
  }

  /**
   * Create the frame.
   */
  public FindFociHelperView() {
    init();
  }

  private void init() {
    createFindMaximaModel();
    controller = new FindMaximaController(model);

    addWindowListener(new WindowAdapter() {
      @Override
      public void windowActivated(WindowEvent event) {
        controller.updateImageList();
      }
    });

    setTitle("FindFoci Helper");
    setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    setBounds(100, 100, 450, 332);
    contentPane = new JPanel();
    contentPane.setBorder(new EmptyBorder(5, 5, 5, 5));
    setContentPane(contentPane);
    final GridBagLayout gbl_contentPane = new GridBagLayout();
    gbl_contentPane.columnWidths = new int[] {62, 0, 0, 0, 0};
    gbl_contentPane.rowHeights = new int[] {0, 0, 0, 0, 0, 31, 0, 0, 0, 0, 0, 0, 0};
    gbl_contentPane.columnWeights = new double[] {0.0, 1.0, 1.0, 1.0, Double.MIN_VALUE};
    gbl_contentPane.rowWeights =
        new double[] {0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, Double.MIN_VALUE};
    contentPane.setLayout(gbl_contentPane);

    lblImage = new JLabel("Image");
    final GridBagConstraints gbc_lblImage = new GridBagConstraints();
    gbc_lblImage.anchor = GridBagConstraints.EAST;
    gbc_lblImage.insets = new Insets(0, 0, 5, 5);
    gbc_lblImage.gridx = 0;
    gbc_lblImage.gridy = 0;
    contentPane.add(lblImage, gbc_lblImage);

    comboImageList = new JComboBox<>();
    comboImageList.setToolTipText("Select the input image");
    comboImageList
        .addItemListener(event -> comboImageList.firePropertyChange("selectedItem", 0, 1));
    comboImageList.addMouseListener(new MouseAdapter() {
      @Override
      public void mousePressed(MouseEvent event) {
        controller.updateImageList();
      }
    });
    final GridBagConstraints gbc_comboImageList = new GridBagConstraints();
    gbc_comboImageList.gridwidth = 3;
    gbc_comboImageList.insets = new Insets(0, 0, 5, 0);
    gbc_comboImageList.fill = GridBagConstraints.HORIZONTAL;
    gbc_comboImageList.gridx = 1;
    gbc_comboImageList.gridy = 0;
    contentPane.add(comboImageList, gbc_comboImageList);

    lblMaskImage = new JLabel("Mask Image");
    final GridBagConstraints gbc_lblMaskImage = new GridBagConstraints();
    gbc_lblMaskImage.anchor = GridBagConstraints.EAST;
    gbc_lblMaskImage.insets = new Insets(0, 0, 5, 5);
    gbc_lblMaskImage.gridx = 0;
    gbc_lblMaskImage.gridy = 1;
    contentPane.add(lblMaskImage, gbc_lblMaskImage);

    comboMaskImageList = new JComboBox<>();
    comboMaskImageList.setToolTipText("Select the input mask image");
    comboMaskImageList
        .addItemListener(event -> comboMaskImageList.firePropertyChange("selectedItem", 0, 1));
    comboMaskImageList.addMouseListener(new MouseAdapter() {
      @Override
      public void mousePressed(MouseEvent event) {
        controller.updateImageList();
      }
    });
    final GridBagConstraints gbc_comboMaskImageList = new GridBagConstraints();
    gbc_comboMaskImageList.gridwidth = 3;
    gbc_comboMaskImageList.insets = new Insets(0, 0, 5, 0);
    gbc_comboMaskImageList.fill = GridBagConstraints.HORIZONTAL;
    gbc_comboMaskImageList.gridx = 1;
    gbc_comboMaskImageList.gridy = 1;
    contentPane.add(comboMaskImageList, gbc_comboMaskImageList);

    lblResolution = new JLabel("Resolution (px)");
    final GridBagConstraints gbc_lblResolution = new GridBagConstraints();
    gbc_lblResolution.anchor = GridBagConstraints.EAST;
    gbc_lblResolution.insets = new Insets(0, 0, 5, 5);
    gbc_lblResolution.gridx = 0;
    gbc_lblResolution.gridy = 2;
    contentPane.add(lblResolution, gbc_lblResolution);

    txtResolution = new JFormattedTextField();
    txtResolution = new JFormattedTextField(new LimitedNumberFormat(0));
    txtResolution.addPropertyChangeListener(event -> {
      if (event.getPropertyName() == "value") {
        txtResolution.firePropertyChange("text", 0, 1);
      }
    });
    txtResolution.addKeyListener(new KeyAdapter() {
      @Override
      public void keyReleased(KeyEvent event) {
        txtResolution.firePropertyChange("text", 0, 1);
      }
    });
    txtResolution.setHorizontalAlignment(SwingConstants.RIGHT);
    txtResolution.setText("0");
    final GridBagConstraints gbc_txtResolution = new GridBagConstraints();
    gbc_txtResolution.gridwidth = 3;
    gbc_txtResolution.insets = new Insets(0, 0, 5, 0);
    gbc_txtResolution.fill = GridBagConstraints.HORIZONTAL;
    gbc_txtResolution.gridx = 1;
    gbc_txtResolution.gridy = 2;
    contentPane.add(txtResolution, gbc_txtResolution);

    btnSaveResults = new JButton("Save Results");
    btnSaveResults.addActionListener(event -> {
      if (isActive()) {
        saveResults();
      }
    });
    final GridBagConstraints gbc_btnSaveResults = new GridBagConstraints();
    gbc_btnSaveResults.insets = new Insets(0, 0, 5, 0);
    gbc_btnSaveResults.gridx = 3;
    gbc_btnSaveResults.gridy = 3;
    contentPane.add(btnSaveResults, gbc_btnSaveResults);

    lblLogMessages = new JLabel("Log Messages");
    final GridBagConstraints gbc_lblLogMessages = new GridBagConstraints();
    gbc_lblLogMessages.anchor = GridBagConstraints.EAST;
    gbc_lblLogMessages.insets = new Insets(0, 0, 5, 5);
    gbc_lblLogMessages.gridx = 0;
    gbc_lblLogMessages.gridy = 4;
    contentPane.add(lblLogMessages, gbc_lblLogMessages);

    chckbxLogmessages = new JCheckBox("");
    final GridBagConstraints gbc_chckbxLogmessages = new GridBagConstraints();
    gbc_chckbxLogmessages.gridwidth = 2;
    gbc_chckbxLogmessages.anchor = GridBagConstraints.WEST;
    gbc_chckbxLogmessages.insets = new Insets(0, 0, 5, 5);
    gbc_chckbxLogmessages.gridx = 1;
    gbc_chckbxLogmessages.gridy = 4;
    contentPane.add(chckbxLogmessages, gbc_chckbxLogmessages);

    lblSearchMode = new JLabel("Search Mode");
    final GridBagConstraints gbc_lblSearchMode = new GridBagConstraints();
    gbc_lblSearchMode.anchor = GridBagConstraints.EAST;
    gbc_lblSearchMode.insets = new Insets(0, 0, 5, 5);
    gbc_lblSearchMode.gridx = 0;
    gbc_lblSearchMode.gridy = 5;
    contentPane.add(lblSearchMode, gbc_lblSearchMode);

    comboSearchMode = new JComboBox<>();
    // Force the BeansBinding framework to pick up the state change
    comboSearchMode
        .addItemListener(event -> comboSearchMode.firePropertyChange("selectedItem", 0, 1));
    final GridBagConstraints gbc_comboSearchMode = new GridBagConstraints();
    gbc_comboSearchMode.gridwidth = 3;
    gbc_comboSearchMode.insets = new Insets(0, 0, 5, 0);
    gbc_comboSearchMode.fill = GridBagConstraints.HORIZONTAL;
    gbc_comboSearchMode.gridx = 1;
    gbc_comboSearchMode.gridy = 5;
    comboSearchMode.setModel(new DefaultComboBoxModel<>(SearchModeConverter.getSearchModes()));
    contentPane.add(comboSearchMode, gbc_comboSearchMode);

    btnRun = new JButton("Start");
    btnRun.addActionListener(event -> initialisePicker());
    final GridBagConstraints gbc_btnRun = new GridBagConstraints();
    gbc_btnRun.insets = new Insets(0, 0, 5, 5);
    gbc_btnRun.anchor = GridBagConstraints.NORTH;
    gbc_btnRun.gridx = 1;
    gbc_btnRun.gridy = 3;
    contentPane.add(btnRun, gbc_btnRun);

    btnStop = new JButton("Stop");
    btnStop.addActionListener(event -> killPicker());
    final GridBagConstraints gbc_btnStop = new GridBagConstraints();
    gbc_btnStop.insets = new Insets(0, 0, 5, 5);
    gbc_btnStop.gridx = 2;
    gbc_btnStop.gridy = 3;
    contentPane.add(btnStop, gbc_btnStop);

    lblAssignDragged = new JLabel("Assign Dragged");
    final GridBagConstraints gbc_lblAssignDragged = new GridBagConstraints();
    gbc_lblAssignDragged.anchor = GridBagConstraints.EAST;
    gbc_lblAssignDragged.insets = new Insets(0, 0, 5, 5);
    gbc_lblAssignDragged.gridx = 0;
    gbc_lblAssignDragged.gridy = 6;
    contentPane.add(lblAssignDragged, gbc_lblAssignDragged);

    chckbxAssigndragged = new JCheckBox("");
    final GridBagConstraints gbc_chckbxAssigndragged = new GridBagConstraints();
    gbc_chckbxAssigndragged.anchor = GridBagConstraints.WEST;
    gbc_chckbxAssigndragged.insets = new Insets(0, 0, 5, 5);
    gbc_chckbxAssigndragged.gridx = 1;
    gbc_chckbxAssigndragged.gridy = 6;
    contentPane.add(chckbxAssigndragged, gbc_chckbxAssigndragged);

    lblActiveImage = new JLabel("Active Image");
    final GridBagConstraints gbc_lblActiveImage = new GridBagConstraints();
    gbc_lblActiveImage.anchor = GridBagConstraints.EAST;
    gbc_lblActiveImage.insets = new Insets(0, 0, 5, 5);
    gbc_lblActiveImage.gridx = 0;
    gbc_lblActiveImage.gridy = 7;
    contentPane.add(lblActiveImage, gbc_lblActiveImage);

    txtActiveImage = new JLabel("ActiveImage");
    final GridBagConstraints gbc_txtActiveImage = new GridBagConstraints();
    gbc_txtActiveImage.anchor = GridBagConstraints.WEST;
    gbc_txtActiveImage.gridwidth = 3;
    gbc_txtActiveImage.insets = new Insets(0, 0, 5, 0);
    gbc_txtActiveImage.gridx = 1;
    gbc_txtActiveImage.gridy = 7;
    contentPane.add(txtActiveImage, gbc_txtActiveImage);

    lblNumberOfPotential = new JLabel("Potential Maxima");
    final GridBagConstraints gbc_lblNumberOfPotential = new GridBagConstraints();
    gbc_lblNumberOfPotential.anchor = GridBagConstraints.EAST;
    gbc_lblNumberOfPotential.insets = new Insets(0, 0, 5, 5);
    gbc_lblNumberOfPotential.gridx = 0;
    gbc_lblNumberOfPotential.gridy = 8;
    contentPane.add(lblNumberOfPotential, gbc_lblNumberOfPotential);

    labelPotentialMaxima = new JLabel("0");
    final GridBagConstraints gbc_labelPotentialMaxima = new GridBagConstraints();
    gbc_labelPotentialMaxima.insets = new Insets(0, 0, 5, 0);
    gbc_labelPotentialMaxima.gridwidth = 3;
    gbc_labelPotentialMaxima.anchor = GridBagConstraints.WEST;
    gbc_labelPotentialMaxima.gridx = 1;
    gbc_labelPotentialMaxima.gridy = 8;
    contentPane.add(labelPotentialMaxima, gbc_labelPotentialMaxima);

    lblMappedPoints = new JLabel("Mapped");
    final GridBagConstraints gbc_lblMappedPoints = new GridBagConstraints();
    gbc_lblMappedPoints.anchor = GridBagConstraints.WEST;
    gbc_lblMappedPoints.insets = new Insets(0, 0, 5, 5);
    gbc_lblMappedPoints.gridx = 1;
    gbc_lblMappedPoints.gridy = 9;
    contentPane.add(lblMappedPoints, gbc_lblMappedPoints);

    lblUnmappedPoints = new JLabel("Unmapped");
    final GridBagConstraints gbc_lblUnmappedPoints = new GridBagConstraints();
    gbc_lblUnmappedPoints.anchor = GridBagConstraints.WEST;
    gbc_lblUnmappedPoints.insets = new Insets(0, 0, 5, 5);
    gbc_lblUnmappedPoints.gridx = 2;
    gbc_lblUnmappedPoints.gridy = 9;
    contentPane.add(lblUnmappedPoints, gbc_lblUnmappedPoints);

    lblTotal = new JLabel("Total");
    final GridBagConstraints gbc_lblTotal = new GridBagConstraints();
    gbc_lblTotal.anchor = GridBagConstraints.WEST;
    gbc_lblTotal.insets = new Insets(0, 0, 5, 0);
    gbc_lblTotal.gridx = 3;
    gbc_lblTotal.gridy = 9;
    contentPane.add(lblTotal, gbc_lblTotal);

    lblPoints = new JLabel("Points");
    final GridBagConstraints gbc_lblPoints = new GridBagConstraints();
    gbc_lblPoints.anchor = GridBagConstraints.EAST;
    gbc_lblPoints.insets = new Insets(0, 0, 5, 5);
    gbc_lblPoints.gridx = 0;
    gbc_lblPoints.gridy = 10;
    contentPane.add(lblPoints, gbc_lblPoints);

    txtMappedPoints = new JLabel("0");
    final GridBagConstraints gbc_txtMappedPoints = new GridBagConstraints();
    gbc_txtMappedPoints.anchor = GridBagConstraints.WEST;
    gbc_txtMappedPoints.insets = new Insets(0, 0, 5, 5);
    gbc_txtMappedPoints.gridx = 1;
    gbc_txtMappedPoints.gridy = 10;
    contentPane.add(txtMappedPoints, gbc_txtMappedPoints);

    txtUnmappedPoints = new JLabel("0");
    final GridBagConstraints gbc_txtUnmappedPoints = new GridBagConstraints();
    gbc_txtUnmappedPoints.anchor = GridBagConstraints.WEST;
    gbc_txtUnmappedPoints.insets = new Insets(0, 0, 5, 5);
    gbc_txtUnmappedPoints.gridx = 2;
    gbc_txtUnmappedPoints.gridy = 10;
    contentPane.add(txtUnmappedPoints, gbc_txtUnmappedPoints);

    txtTotal = new JLabel("0");
    final GridBagConstraints gbc_txtTotal = new GridBagConstraints();
    gbc_txtTotal.insets = new Insets(0, 0, 5, 0);
    gbc_txtTotal.anchor = GridBagConstraints.WEST;
    gbc_txtTotal.gridx = 3;
    gbc_txtTotal.gridy = 10;
    contentPane.add(txtTotal, gbc_txtTotal);

    tglbtnOverlay = new JToggleButton("Show Overlay");
    // Force the BeansBinding framework to pick up the state change
    tglbtnOverlay.addItemListener(event -> tglbtnOverlay.firePropertyChange("selected",
        tglbtnOverlay.isSelected(), !tglbtnOverlay.isSelected()));
    final GridBagConstraints gbc_tglbtnOverlay = new GridBagConstraints();
    gbc_tglbtnOverlay.gridwidth = 2;
    gbc_tglbtnOverlay.insets = new Insets(0, 0, 0, 5);
    gbc_tglbtnOverlay.gridx = 1;
    gbc_tglbtnOverlay.gridy = 11;
    contentPane.add(tglbtnOverlay, gbc_tglbtnOverlay);

    btnHelp = new JButton("Help");
    btnHelp.addMouseListener(FindFociHelpMouseListener.INSTANCE);
    final GridBagConstraints gbc_btnHelp = new GridBagConstraints();
    gbc_btnHelp.gridx = 3;
    gbc_btnHelp.gridy = 11;
    contentPane.add(btnHelp, gbc_btnHelp);
    initDataBindings();

    this.pack();
  }

  private void createFindMaximaModel() {
    model = new FindFociModel();

    model.setMaskImage(null);
    // Find points above the mean. This is a good start for finding maxima.
    model.setBackgroundMethod(BackgroundMethod.STD_DEV_ABOVE_MEAN.ordinal());
    model.setBackgroundParameter(0);
    model.setThresholdMethod(ThresholdMethod.NONE.ordinal());
    model.setStatisticsMode(StatisticsMethod.ALL.ordinal());
    model.setSearchMethod(SearchMethod.ABOVE_BACKGROUND.ordinal());
    model.setSearchParameter(0);
    model.setMaxPeaks(33000);
    model.setMinSize(1);
    model.setMaxSize(0);
    model.setMinimumAboveSaddle(false);
    model.setPeakMethod(PeakMethod.RELATIVE.ordinal());
    model.setPeakParameter(0);
    model.setShowMask(0);
    model.setShowTable(true); // We need to get the results table
    model.setMarkMaxima(false);
    model.setMarkRoiMaxima(false);
    model.setShowLogMessages(false);
    model.setSortMethod(SortMethod.MAX_VALUE.ordinal());
    model.setGaussianBlur(0);
    model.setCentreMethod(CentreMethod.MAX_VALUE_ORIGINAL.ordinal());
    model.setCentreParameter(0);
  }

  /**
   * Sets the run enabled.
   *
   * @param runEnabled the runEnabled to set
   */
  public void setRunEnabled(boolean runEnabled) {
    final boolean oldValue = this.runEnabled;
    this.runEnabled = runEnabled;
    this.firePropertyChange("runEnabled", oldValue, runEnabled);
    if (!runEnabled) {
      killPicker();
    }
  }

  /**
   * Checks if is run enabled.
   *
   * @return the runEnabled
   */
  public boolean isRunEnabled() {
    return runEnabled;
  }

  /**
   * Sets the logging.
   *
   * @param logging the logging to set
   */
  public void setLogging(boolean logging) {
    final boolean oldValue = this.logging;
    this.logging = logging;
    this.firePropertyChange("logging", oldValue, logging);
  }

  /**
   * Checks if is logging.
   *
   * @return the logging
   */
  public boolean isLogging() {
    return logging;
  }

  /**
   * Sets the search mode.
   *
   * @param searchMode the searchMode to set
   */
  public void setSearchMode(int searchMode) {
    final int oldValue = this.searchMode;
    this.searchMode = searchMode;
    this.firePropertyChange("searchMode", oldValue, searchMode);
    if (index != null) {
      index.setSearchMode(SearchMode.forOrdinal(searchMode));
    }
  }

  /**
   * Gets the search mode.
   *
   * @return the searchMode
   */
  public int getSearchMode() {
    return searchMode;
  }

  /**
   * Sets the assign dragged.
   *
   * @param assignDragged the assignDragged to set
   */
  public void setAssignDragged(boolean assignDragged) {
    final boolean oldValue = this.assignDragged;
    this.assignDragged = assignDragged;
    this.firePropertyChange("assignDragged", oldValue, assignDragged);
  }

  /**
   * Checks if is assign dragged.
   *
   * @return the assignDragged
   */
  public boolean isAssignDragged() {
    return assignDragged;
  }

  /**
   * Sets the potential maxima.
   *
   * @param potentialMaxima the new potential maxima
   */
  private void setPotentialMaxima(int potentialMaxima) {
    final int oldValue = this.potentialMaxima;
    this.potentialMaxima = potentialMaxima;
    this.firePropertyChange("potentialMaxima", oldValue, potentialMaxima);
  }

  /**
   * Gets the potential maxima.
   *
   * @return the potentialMaxima
   */
  public int getPotentialMaxima() {
    return potentialMaxima;
  }

  /**
   * Sets the resolution.
   *
   * @param resolution the resolution to set
   */
  public void setResolution(int resolution) {
    final int oldValue = this.resolution;
    this.resolution = resolution;
    this.firePropertyChange("resolution", oldValue, resolution);
    if (index != null) {
      index.setSearchDistance(Math.max(0, resolution));
    }
  }

  /**
   * Gets the resolution.
   *
   * @return the resolution
   */
  public int getResolution() {
    return resolution;
  }

  /**
   * Sets the mapped points.
   *
   * @param mappedPoints the mappedPoints to set
   */
  public void setMappedPoints(int mappedPoints) {
    final int oldValue = this.mappedPoints;
    this.mappedPoints = mappedPoints;
    if (counterEvents) {
      this.firePropertyChange("mappedPoints", oldValue, mappedPoints);
      this.firePropertyChange("totalPoints", oldValue, mappedPoints);
    }
  }

  /**
   * Gets the mapped points.
   *
   * @return the mappedPoints
   */
  public int getMappedPoints() {
    return mappedPoints;
  }

  /**
   * Sets the unmapped points.
   *
   * @param unmappedPoints the unmappedPoints to set
   */
  public void setUnmappedPoints(int unmappedPoints) {
    final int oldValue = this.unmappedPoints;
    this.unmappedPoints = unmappedPoints;
    if (counterEvents) {
      this.firePropertyChange("unmappedPoints", oldValue, unmappedPoints);
      this.firePropertyChange("totalPoints", oldValue, unmappedPoints);
    }
  }

  /**
   * Gets the unmapped points.
   *
   * @return the unmappedPoints
   */
  public int getUnmappedPoints() {
    return unmappedPoints;
  }

  /**
   * Gets the total points.
   *
   * @return the unmappedPoints
   */
  public int getTotalPoints() {
    return unmappedPoints + mappedPoints;
  }

  /**
   * Sets the show overlay.
   *
   * @param showOverlay the showOverlay to set
   */
  public void setShowOverlay(boolean showOverlay) {
    final boolean oldValue = this.showOverlay;
    this.showOverlay = showOverlay;
    this.firePropertyChange("showOverlay", oldValue, showOverlay);

    if (oldValue != showOverlay) {
      if (showOverlay) {
        showOverlay();
      } else {
        hideOverlay();
      }
    }
  }

  /**
   * Checks if is show overlay.
   *
   * @return the showOverlay
   */
  public boolean isShowOverlay() {
    return showOverlay;
  }

  /**
   * Sets the active image.
   *
   * @param activeImage the new active image
   */
  private void setActiveImage(String activeImage) {
    final String oldValue = this.activeImage;
    this.activeImage = activeImage;
    this.firePropertyChange("activeImage", oldValue, activeImage);
  }

  /**
   * Gets the active image.
   *
   * @return the activeImage
   */
  public String getActiveImage() {
    return activeImage;
  }

  /**
   * Initialise the active image. Map all the potential maxima and enable the image mouse event
   * listener.
   */
  private void initialisePicker() {
    killPicker();

    activeImp = WindowManager.getImage(model.getSelectedImage());
    if (isPickerActive()) {
      setActiveImage(activeImp.getTitle());
      logMessage("Analysing image %s (mask = %s)", activeImage, model.getMaskImage());

      // Find foci
      controller.run();
      final List<FindFociResult> results = controller.getResultsArray();
      setPotentialMaxima(results.size());

      // Update the z position to be 1-based, not 0-based as per FindFoci. This simplifies
      // comparisons between stack positions for ROIs and the results.
      FindFociResults.incrementZ(results);

      // Create the search index of maxima
      try {
        final Calibration cal = activeImp.getCalibration();
        index = new AssignedFindFociResultSearchIndex(results, cal.pixelWidth, cal.pixelHeight,
            cal.pixelDepth);
        logMessage("Distance scaling: 1.0 x %s x %s",
            MathUtils.round(cal.pixelHeight / cal.pixelWidth),
            MathUtils.rounded(cal.pixelDepth / cal.pixelWidth));
        logMessage("Identified %d potential maxima", results.size());
        index.setSearchMode(SearchMode.forOrdinal(searchMode));
        index.setSearchDistance(resolution);
        assignRoiPoints();

        // Register mouse events from the image canvas
        activeImp.getWindow().addWindowListener(this);
        activeImp.getCanvas().addMouseListener(this);
        activeImp.getCanvas().addMouseMotionListener(this);
      } catch (final Exception ex) {
        logMessage("Failed to create the search index of potential maxima: %s", ex.getMessage());
        killPicker();
      }
    }
  }

  /**
   * Processes all the ROI points and assigns the results using the current search method. Should be
   * called to initialise the system.
   */
  private void assignRoiPoints() {
    synchronized (updatingPointsLock) {
      disableCounterEvents();

      setMappedPoints(0);
      setUnmappedPoints(0);

      AssignedPoint[] points = extractRoiPoints(activeImp);
      points = AssignedPointUtils.eliminateDuplicates(points);
      if (points.length > 0) {
        logMessage("Assigning %d point%s to %s", points.length, (points.length != 1) ? "s" : "",
            activeImage);

        if (searchMode == SearchMode.HIGHEST.ordinal()) {
          assignToHighest(points);
        } else {
          points = assignToClosest(points);
        }
        activeImp.setRoi(AssignedPointUtils.createRoi(activeImp, controller.getActiveChannel(),
            controller.getActiveFrame(), points));
      }
      currentRoiPoints = points.length;

      enableCounterEvents();
    }
  }

  private void disableCounterEvents() {
    counterEvents = false;
  }

  private void enableCounterEvents() {
    counterEvents = true;
    this.firePropertyChange("unmappedPoints", unmappedPoints - 1, unmappedPoints);
    this.firePropertyChange("mappedPoints", mappedPoints - 1, mappedPoints);
    this.firePropertyChange("totalPoints", mappedPoints - 1, mappedPoints);
  }

  /**
   * Process the points in descending height order, assigning to the highest unassigned peak.
   */
  private void assignToHighest(AssignedPoint[] points) {
    // If searchMode = 'highest' then the points should be processed in height order
    sortPoints(points, controller.getActiveImageStack());

    for (int i = 0; i < points.length; i++) {
      final int x = points[i].getXint();
      final int y = points[i].getYint();
      final int z = points[i].getZint();
      final AssignedFindFociResult assignedResult = index.findHighest(x, y, z, false);
      if (assignedResult != null) {
        assignedResult.setAssigned(true);
        final FindFociResult result = assignedResult.getResult();
        addMappedPoint(x, y, z, result, i + 1);

        // Update points
        points[i] =
            new AssignedPoint(result.getX(), result.getY(), result.getZ(), points[i].getId());
      } else {
        addUnmappedPoint(x, y, z, i + 1);
      }
    }
  }

  /**
   * Sort the points using the value from the image stack for each xyz point position.
   *
   * @param points the points
   * @param impStack the image stack
   */
  private static void sortPoints(AssignedPoint[] points, ImageStack impStack) {
    final float[] values = new float[points.length];

    // Do this in descending height order
    for (final AssignedPoint point : points) {
      final int x = point.getXint();
      final int y = point.getYint();
      final int z = point.getZint();
      values[point.getId()] = impStack.getProcessor(z).getf(x, y);
    }
    Arrays.sort(points, (o1, o2) -> Float.compare(values[o2.getId()], values[o1.getId()]));
  }

  /**
   * Process the points by assigning to the closest unassigned peak. Points are allocated
   * iteratively by first determining the distance to the closest unassigned peak for each
   * unprocessed point and then assigning them in ascending distance order. Any peak that conflicts
   * with a closer point for the same peak will be processed in the next iteration.
   */
  private AssignedPoint[] assignToClosest(AssignedPoint[] points) {
    // If searchMode = 'closest' then the points should be processed in distance order

    // List of new points (mapped/unmapped) for the ROI
    final ArrayList<AssignedPoint> roiPoints = new ArrayList<>(points.length);

    // Repeat until all peaks have been processed
    while (roiPoints.size() < points.length) {
      int mapped = 0;
      int unmapped = 0;

      // Used to store the potential mappings
      final LocalList<Mapping> potentialMappedPoints = new LocalList<>(points.length);

      for (final AssignedPoint point : points) {
        // Skip if assigned
        if (point.getAssignedId() != 0) {
          continue;
        }

        // Find peak to assign
        final int x = point.getXint();
        final int y = point.getYint();
        final int z = point.getZint();
        final AssignedFindFociResult assignedResult = index.findClosest(x, y, z, false);
        if (assignedResult == null) {
          // No available peak so just add this point as unmapped
          addUnmappedPoint(x, y, z, roiPoints.size() + 1);
          addNewRoiPoint(roiPoints, point);
          unmapped++;
        } else {
          // Build a list of distances to unassigned peaks.
          potentialMappedPoints.add(
              new Mapping(point, assignedResult, distance(x, y, z, assignedResult.getResult())));
        }
      }

      // Sort by distance
      potentialMappedPoints.sort((o1, o2) -> Double.compare(o1.distance, o2.distance));

      // Process points assigning to the peak if it is unassigned and matches the stored coords
      for (final Mapping mapping : potentialMappedPoints) {
        if (!mapping.assignedResult.isAssigned()) {
          mapping.assignedResult.setAssigned(true);
          final int x = mapping.point.getXint();
          final int y = mapping.point.getYint();
          final int z = mapping.point.getZint();
          final int id = mapping.point.getId();
          final FindFociResult result = mapping.assignedResult.getResult();
          addMappedPoint(x, y, z, result, roiPoints.size() + 1);
          points[id] = new AssignedPoint(result.getX(), result.getY(), result.getZ(), id);
          addNewRoiPoint(roiPoints, points[id]);
          mapped++;
        }
      }

      logMessage("Processed %d / %d. +%d mapped, +%d unmapped", roiPoints.size(), points.length,
          mapped, unmapped);
    }
    return roiPoints.toArray(new AssignedPoint[0]);
  }

  private void addNewRoiPoint(ArrayList<AssignedPoint> roiPoints, AssignedPoint point) {
    roiPoints.add(point);
    point.setAssignedId(this.getTotalPoints());
  }

  /**
   * Processes all the ROI points and assigns the results that are an exact match. This should be
   * called when an ROI point has been removed. All other points should align exactly to a result or
   * are unmapped.
   */
  private void reassignRoiPoints() {
    synchronized (updatingPointsLock) {
      disableCounterEvents();

      index.setAssigned(false);
      setMappedPoints(0);
      setUnmappedPoints(0);

      final AssignedPoint[] points = extractRoiPoints(activeImp);
      if (points.length > 0) {
        logMessage("Re-assigning %s to %s", TextUtils.pleural(points.length, "point"), activeImage);

        for (int i = 0; i < points.length; i++) {
          final int x = points[i].getXint();
          final int y = points[i].getYint();
          final int z = points[i].getZint();
          final AssignedFindFociResult assignedResult = index.findExact(x, y, z, false);
          if (assignedResult != null) {
            assignedResult.setAssigned(true);
            addMappedPoint(x, y, z, assignedResult.getResult(), i + 1);
          } else {
            addUnmappedPoint(x, y, z, i + 1);
          }
        }
      }
      currentRoiPoints = points.length;

      enableCounterEvents();
    }
  }

  private void addMappedPoint(int x, int y, int z, FindFociResult point, int index) {
    if (logging) {
      if (x == point.getX() && y == point.getY() && z == point.getZ()) {
        logMessage("%d: Mapped (%d,%d,%d)", index, x, y, z);
      } else {
        logMessage("%d: Mapped (%d,%d,%d) => (%d,%d,%d) (%spx)", index, x, y, z, point.getX(),
            point.getY(), point.getZ(), MathUtils.rounded(Math.sqrt(distance(x, y, z, point))));
      }
    }
    setMappedPoints(this.mappedPoints + 1);
  }

  /**
   * Compute the distance used by the index. This will be a squared Euclidean distance.
   *
   * @param x the x
   * @param y the y
   * @param z the z
   * @param point the point
   * @return the distance
   */
  private double distance(int x, int y, int z, FindFociResult point) {
    return index.getDistanceFunction().distance(new double[] {x, y, z},
        new double[] {point.getX(), point.getY(), point.getZ()});
  }

  private void addUnmappedPoint(int x, int y, int z, int index) {
    logMessage("%d: Unmapped (%d,%d,%d)", index, x, y, z);
    setUnmappedPoints(this.unmappedPoints + 1);
  }

  private boolean isPickerActive() {
    return activeImp != null;
  }

  /**
   * Stop the interaction with the active image.
   */
  private void killPicker() {
    if (isPickerActive()) {
      setShowOverlay(false);
      try {
        // Unregister from the image canvas
        activeImp.getWindow().removeWindowListener(this);
        activeImp.getCanvas().removeMouseListener(this);
        activeImp.getCanvas().removeMouseMotionListener(this);
      } catch (final RuntimeException ex) {
        logMessage("Failed to unregister from image %s", activeImp.getTitle());
      }
    }
    setActiveImage("");
    activeImp = null;
    setPotentialMaxima(0);
    setMappedPoints(0);
    setUnmappedPoints(0);
    currentRoiPoints = 0;
    index = null;
    dragging = false;
  }

  /** {@inheritDoc} */
  @Override
  public void windowOpened(WindowEvent event) {
    // Ignore
  }

  /** {@inheritDoc} */
  @Override
  public void windowClosing(WindowEvent event) {
    killPicker();
  }

  /** {@inheritDoc} */
  @Override
  public void windowClosed(WindowEvent event) {
    // Ignore
  }

  /** {@inheritDoc} */
  @Override
  public void windowIconified(WindowEvent event) {
    // Ignore
  }

  /** {@inheritDoc} */
  @Override
  public void windowDeiconified(WindowEvent event) {
    // Ignore
  }

  /** {@inheritDoc} */
  @Override
  public void windowActivated(WindowEvent event) {
    // Ignore
  }

  /** {@inheritDoc} */
  @Override
  public void windowDeactivated(WindowEvent event) {
    // Ignore
  }

  /** {@inheritDoc} */
  @Override
  public void mouseClicked(MouseEvent event) {
    setShowOverlay(false);

    final Roi roi = activeImp.getRoi();
    if (roi != null && roi.getType() == Roi.POINT) {
      final PointRoi pointRoi = (PointRoi) roi;
      final Polygon p = pointRoi.getNonSplineCoordinates();

      if (p.npoints < currentRoiPoints || p.npoints == 1) {
        // The ROI has had a point removed or has been restarted at 1 point

        if (p.npoints == 1) {
          // Picking has restarted so allow update
          index.setAssigned(false);
          assignRoiPoints();
        } else {
          // This is a removal of a point, all other points should already be located at maxima
          reassignRoiPoints();
        }
      } else if (p.npoints > currentRoiPoints) {
        mapRoiPoint(pointRoi, p.npoints - 1);
      }
    }
  }

  /**
   * Maps the given roi point to the best unassigned result using the search index.
   *
   * @param roi the roi
   * @param roiIndex the roi index
   */
  private void mapRoiPoint(PointRoi roi, int roiIndex) {
    synchronized (updatingPointsLock) {
      final Polygon p = roi.getNonSplineCoordinates();
      final Rectangle bounds = roi.getBounds();
      final int x = bounds.x + p.xpoints[roiIndex];
      final int y = bounds.y + p.ypoints[roiIndex];
      final int z = activeImp.getZ();

      // Extracted from an old version of the user manual:
      // "Note that if the Search mode is set to highest then a check is made for the highest
      // peak within the search radius. If present then the search resolution is updated to
      // the distance to the highest assigned peak. This means that the plugin requires the
      // user to click closer to a second unassigned peak than to an existing assigned high
      // peak in close proximity. This prevents the plugin moving the clicked point past an
      // already assigned peak to reach an unassigned peak."
      //
      // This functionality is not present and the manual text is out-of-date.
      // The idea could be reinstated in the future.

      final AssignedFindFociResult assignedResult = index.find(x, y, z, false);

      if (assignedResult != null) {
        assignedResult.setAssigned(true);
        addMappedPoint(x, y, z, assignedResult.getResult(), roiIndex + 1);

        // Update the ROI if the peak was re-mapped.
        // We extract all the points to preserve the z information.
        final AssignedPoint[] points = extractRoiPoints(activeImp, roi);
        // Add the new position
        points[roiIndex] =
            new AssignedPoint(assignedResult.getResult().getX(), assignedResult.getResult().getY(),
                assignedResult.getResult().getZ(), points[roiIndex].getId());

        activeImp.setRoi(AssignedPointUtils.createRoi(activeImp, controller.getActiveChannel(),
            controller.getActiveFrame(), points), true);
      } else {
        addUnmappedPoint(x, y, z, roiIndex + 1);
      }
      currentRoiPoints = p.npoints;
    }
  }

  /**
   * Add the given roi point without mapping.
   *
   * @param roi the roi
   * @param roiIndex the roi index
   */
  private void addRoiPoint(PointRoi roi, int roiIndex) {
    synchronized (updatingPointsLock) {
      final Polygon p = roi.getNonSplineCoordinates();
      final Rectangle bounds = roi.getBounds();
      final int x = bounds.x + p.xpoints[roiIndex];
      final int y = bounds.y + p.ypoints[roiIndex];
      final int z = activeImp.getZ();

      addUnmappedPoint(x, y, z, roiIndex + 1);
      currentRoiPoints = p.npoints;
    }
  }

  /** {@inheritDoc} */
  @Override
  public void mousePressed(MouseEvent event) {
    // Ignore
  }

  /** {@inheritDoc} */
  /*
   * If the user has dragged an ROI point then it should be reassigned when it is dropped.
   */
  @Override
  public void mouseReleased(MouseEvent event) {
    if (dragging) {
      dragging = false;
      final Roi roi = activeImp.getRoi();
      if (roi != null && roi.getType() == Roi.POINT && roi.getState() == Roi.NORMAL) {
        // Find the image x,y coords
        final ImageCanvas ic = activeImp.getCanvas();
        final int ox = ic.offScreenX(event.getX());
        final int oy = ic.offScreenY(event.getY());

        final int index = findRoiPointIndex((PointRoi) roi, ox, oy);

        if (index >= 0) {
          if (assignDragged) {
            mapRoiPoint((PointRoi) roi, index);
          } else {
            addRoiPoint((PointRoi) roi, index);
          }
        }
      }
    }
  }

  private static int findRoiPointIndex(PointRoi roi, int ox, int oy) {
    final Polygon p = roi.getNonSplineCoordinates();
    final int n = p.npoints;
    final Rectangle bounds = roi.getBounds();

    for (int i = 0; i < n; i++) {
      final int x = bounds.x + p.xpoints[i];
      final int y = bounds.y + p.ypoints[i];
      if (x == ox && y == oy) {
        return i;
      }
    }

    return -1;
  }

  /** {@inheritDoc} */
  @Override
  public void mouseEntered(MouseEvent event) {
    // Ignore
  }

  /** {@inheritDoc} */
  @Override
  public void mouseExited(MouseEvent event) {
    // Ignore
  }

  /** {@inheritDoc} */
  /*
   * If the user is dragging a multi-point ROI position then this method will detect the point and
   * set it to unassigned. This is done once at the start of the drag.
   */
  @Override
  public void mouseDragged(MouseEvent event) {
    setShowOverlay(false);

    final Roi roi = activeImp.getRoi();
    if (roi != null && roi.getType() == Roi.POINT && roi.getState() == Roi.MOVING_HANDLE
        && !dragging) {
      dragging = true;

      // Find the image x,y coords
      final ImageCanvas ic = activeImp.getCanvas();
      final int ox = ic.offScreenX(event.getX());
      final int oy = ic.offScreenY(event.getY());
      final int oz = activeImp.getZ();

      // Check if an assigned point is being moved
      final AssignedFindFociResult assignedResult = index.findClosest(ox, oy, oz, true);

      final double mag = activeImp.getCanvas().getMagnification();

      // Distance for the ROI is dependent on magnification.
      // Approximate distance for mouse to change from cross to finger in X/Y:
      // 50% = 10px
      // 100% = 5px
      // 200% = 2px
      // Given that the drag could move away from the current ROI centre a bit more
      // tolerance could be added if this limit is too strict.
      final int distanceLimit = (int) Math.ceil(5 / mag);

      // Check the point is within reasonable distance of the mouse
      if (assignedResult != null
          && Math.abs(assignedResult.getResult().getX() - ox) <= distanceLimit
          && Math.abs(assignedResult.getResult().getY() - oy) <= distanceLimit) {
        assignedResult.setAssigned(false);
        setMappedPoints(mappedPoints - 1);
      } else {
        setUnmappedPoints(unmappedPoints - 1);
      }
    }
  }

  /** {@inheritDoc} */
  @Override
  public void mouseMoved(MouseEvent event) {
    // Ignore
  }

  private void logMessage(String format, Object... args) {
    if (logging) {
      IJ.log(String.format(format, args));
    }
  }

  /**
   * Saves the current ROI points to a results table.
   */
  private void saveResults() {
    final Calibration cal = activeImp.getCalibration();

    final String labels = createResultsHeader();
    final TextWindow window = ImageJUtils.refresh(resultsWindow,
        () -> new TextWindow("FindFoci Helper Results", createResultsHeader(), "", 300, 500));
    window.getTextPanel().updateColumnHeadings(labels);

    final ImageStack impStack = controller.getActiveImageStack();
    final AssignedPoint[] points = getRoiPoints();
    final StringBuilder sb = new StringBuilder();
    try (final BufferedTextWindow tw = new BufferedTextWindow(window)) {
      for (final AssignedPoint p : points) {
        final int x = p.getXint();
        final int y = p.getYint();
        final int z = p.getZint();
        final int height = impStack.getProcessor(z).get(x, y);
        final boolean assigned = (p.getAssignedId() != -1);
        // Note: ImageJ reports the current z-slice in the status bar
        // using 0-based indexing so we do the same here to make mouse over
        // look-up of pixel values match.
        createResult(sb, p.getId() + 1, x, y, z - 1, x * cal.pixelWidth, y * cal.pixelHeight,
            (z - 1) * cal.pixelDepth, height, assigned);
        tw.append(sb.toString());
      }
      tw.append("");
    }
  }

  /**
   * Get the list of ROI points. Any that are unmapped have the assigned id set to -1.
   *
   * @return the roi points
   */
  private AssignedPoint[] getRoiPoints() {
    final AssignedPoint[] points = extractRoiPoints(activeImp);
    for (final AssignedPoint p : points) {
      final int x = p.getXint();
      final int y = p.getYint();
      final int z = p.getZint();
      final AssignedFindFociResult assignedResult = index.findExact(x, y, z, true);
      if (assignedResult == null) {
        p.setAssignedId(-1);
      }
    }
    return points;
  }

  private String createResultsHeader() {
    final Calibration cal = activeImp.getCalibration();
    final StringBuilder sb = new StringBuilder();
    sb.append("Id\tX\tY\tZ\t");
    sb.append("X (").append(cal.getXUnit()).append(")\t");
    sb.append("Y (").append(cal.getYUnit()).append(")\t");
    sb.append("Z (").append(cal.getZUnit()).append(")\t");
    sb.append("Height\t");
    sb.append("Assigned");
    return sb.toString();
  }

  private static void createResult(final StringBuilder sb, int index, int x, int y, int z,
      double xx, double yy, double zz, int height, boolean assigned) {
    sb.setLength(0);
    sb.append(index).append('\t');
    sb.append(x).append('\t');
    sb.append(y).append('\t');
    sb.append(z).append('\t');
    sb.append(MathUtils.rounded(xx)).append('\t');
    sb.append(MathUtils.rounded(yy)).append('\t');
    sb.append(MathUtils.rounded(zz)).append('\t');
    sb.append(height).append('\t');
    sb.append(assigned);
  }

  /**
   * Adds the mapped/unmapped points to the image using an overlay.
   */
  private void showOverlay() {
    // Build lists of the mapped and unmapped points
    final AssignedPoint[] points = getRoiPoints();

    final List<AssignedPoint> mapped = new LinkedList<>();
    final List<AssignedPoint> unmapped = new LinkedList<>();
    for (final AssignedPoint p : points) {
      if (p.getAssignedId() < 0) {
        unmapped.add(p);
      } else {
        mapped.add(p);
      }
    }

    // Add the overlay
    activeImp.setOverlay(null);
    Match_PlugIn.addOverlay(activeImp, mapped, Color.green);
    Match_PlugIn.addOverlay(activeImp, unmapped, Color.yellow);

    // Save the ROI and remove it
    savedRoi = activeImp.getRoi();
    if (savedRoi != null && savedRoi.getType() != Roi.POINT) {
      savedRoi = null;
    } else {
      // Clone this so killRoi does not delete the position counters
      savedRoi = (Roi) savedRoi.clone();
    }
    activeImp.killRoi();
  }

  /**
   * Hides the overlay and restores the ROI.
   */
  private void hideOverlay() {
    // Kill the overlay
    activeImp.setOverlay(null);

    // Check if new ROI points have been added while the overlay is displayed
    final AssignedPoint[] points = extractRoiPoints(activeImp);

    if (points.length != 0) {
      // If this is a new point then merge it into the saved Point ROI
      final AssignedPoint[] oldPoints = extractRoiPoints(activeImp, savedRoi);
      if (oldPoints.length != 0) {
        // Merge the current ROI and the saved one
        final LocalList<AssignedPoint> all = new LocalList<>(points.length + oldPoints.length);
        all.addAll(Arrays.asList(oldPoints));
        all.addAll(Arrays.asList(points));
        activeImp.setRoi(AssignedPointUtils.createRoi(activeImp, controller.getActiveChannel(),
            controller.getActiveFrame(), all), true);
      }
    } else {
      // No new ROI so just restore from the old one
      activeImp.restoreRoi();
    }

    savedRoi = null;
  }

  /**
   * Extract the ROI points. 2D images will have the z coordinate set to 1.
   *
   * @param imp the image
   * @return the assigned points
   */
  private static AssignedPoint[] extractRoiPoints(ImagePlus imp) {
    final AssignedPoint[] points = AssignedPointUtils.extractRoiPoints(imp);
    if (imp.getNSlices() == 1) {
      AssignedPointUtils.incrementZ(points);
    }
    return points;
  }

  /**
   * Extract the ROI points. 2D images will have the z coordinate set to 1.
   *
   * @param imp the image
   * @param roi the roi
   * @return the assigned points
   */
  private static AssignedPoint[] extractRoiPoints(ImagePlus imp, Roi roi) {
    final AssignedPoint[] points = AssignedPointUtils.extractRoiPoints(imp, roi);
    if (imp.getNSlices() == 1) {
      AssignedPointUtils.incrementZ(points);
    }
    return points;
  }

  /**
   * Inits the data bindings.
   */
  @SuppressWarnings("rawtypes")
  protected void initDataBindings() {
    final BeanProperty<FindFociModel, List<String>> findFociModelBeanProperty =
        BeanProperty.create("imageList");
    final JComboBoxBinding<String, FindFociModel, JComboBox> jComboBinding =
        SwingBindings.createJComboBoxBinding(UpdateStrategy.READ, model, findFociModelBeanProperty,
            comboImageList);
    jComboBinding.bind();
    //
    final BeanProperty<FindFociModel, String> findFociModelBeanProperty_1 =
        BeanProperty.create("selectedImage");
    final BeanProperty<JComboBox, Object> jComboBoxBeanProperty =
        BeanProperty.create("selectedItem");
    final AutoBinding<FindFociModel, String, JComboBox, Object> autoBinding =
        Bindings.createAutoBinding(UpdateStrategy.READ_WRITE, model, findFociModelBeanProperty_1,
            comboImageList, jComboBoxBeanProperty);
    autoBinding.bind();
    //
    final BeanProperty<FindFociHelperView, Integer> findFociPickerViewBeanProperty =
        BeanProperty.create("potentialMaxima");
    final BeanProperty<JLabel, String> jLabelBeanProperty = BeanProperty.create("text");
    final AutoBinding<FindFociHelperView, Integer, JLabel, String> autoBinding_2 =
        Bindings.createAutoBinding(UpdateStrategy.READ, instance, findFociPickerViewBeanProperty,
            labelPotentialMaxima, jLabelBeanProperty);
    autoBinding_2.bind();
    //
    final BeanProperty<FindFociHelperView, Boolean> findFociPickerViewBeanProperty_1 =
        BeanProperty.create("runEnabled");
    final AutoBinding<FindFociModel, List<String>, FindFociHelperView, Boolean> autoBinding_3 =
        Bindings.createAutoBinding(UpdateStrategy.READ, model, findFociModelBeanProperty, instance,
            findFociPickerViewBeanProperty_1);
    autoBinding_3.setConverter(new ValidImagesConverter());
    autoBinding_3.bind();
    //
    final BeanProperty<JButton, Boolean> jButtonBeanProperty = BeanProperty.create("enabled");
    final AutoBinding<FindFociHelperView, Boolean, JButton, Boolean> autoBinding_4 =
        Bindings.createAutoBinding(UpdateStrategy.READ, instance, findFociPickerViewBeanProperty_1,
            btnRun, jButtonBeanProperty);
    autoBinding_4.bind();
    //
    final BeanProperty<FindFociHelperView, Integer> findFociPickerViewBeanProperty_2 =
        BeanProperty.create("resolution");
    final BeanProperty<JFormattedTextField, String> jFormattedTextFieldBeanProperty =
        BeanProperty.create("text");
    final AutoBinding<FindFociHelperView, Integer, JFormattedTextField, String> autoBinding_1 =
        Bindings.createAutoBinding(UpdateStrategy.READ_WRITE, instance,
            findFociPickerViewBeanProperty_2, txtResolution, jFormattedTextFieldBeanProperty);
    autoBinding_1.bind();
    //
    final BeanProperty<FindFociHelperView, Boolean> findFociPickerViewBeanProperty_3 =
        BeanProperty.create("logging");
    final BeanProperty<JCheckBox, Boolean> jCheckBoxBeanProperty = BeanProperty.create("selected");
    final AutoBinding<FindFociHelperView, Boolean, JCheckBox, Boolean> autoBinding_5 =
        Bindings.createAutoBinding(UpdateStrategy.READ_WRITE, instance,
            findFociPickerViewBeanProperty_3, chckbxLogmessages, jCheckBoxBeanProperty);
    autoBinding_5.bind();
    //
    final BeanProperty<FindFociHelperView, String> findFociPickerViewBeanProperty_4 =
        BeanProperty.create("activeImage");
    final AutoBinding<FindFociHelperView, String, JLabel, String> autoBinding_6 =
        Bindings.createAutoBinding(UpdateStrategy.READ, instance, findFociPickerViewBeanProperty_4,
            txtActiveImage, jLabelBeanProperty);
    autoBinding_6.bind();
    //
    final BeanProperty<FindFociHelperView, Integer> findFociPickerViewBeanProperty_5 =
        BeanProperty.create("mappedPoints");
    final AutoBinding<FindFociHelperView, Integer, JLabel, String> autoBinding_7 =
        Bindings.createAutoBinding(UpdateStrategy.READ, instance, findFociPickerViewBeanProperty_5,
            txtMappedPoints, jLabelBeanProperty);
    autoBinding_7.bind();
    //
    final BeanProperty<FindFociHelperView, Integer> findFociPickerViewBeanProperty_6 =
        BeanProperty.create("unmappedPoints");
    final AutoBinding<FindFociHelperView, Integer, JLabel, String> autoBinding_8 =
        Bindings.createAutoBinding(UpdateStrategy.READ, instance, findFociPickerViewBeanProperty_6,
            txtUnmappedPoints, jLabelBeanProperty);
    autoBinding_8.bind();
    //
    final BeanProperty<FindFociHelperView, Integer> findFociPickerViewBeanProperty_7 =
        BeanProperty.create("totalPoints");
    final AutoBinding<FindFociHelperView, Integer, JLabel, String> autoBinding_9 =
        Bindings.createAutoBinding(UpdateStrategy.READ, instance, findFociPickerViewBeanProperty_7,
            txtTotal, jLabelBeanProperty);
    autoBinding_9.bind();
    //
    final AutoBinding<FindFociHelperView, String, JButton, Boolean> autoBinding_10 =
        Bindings.createAutoBinding(UpdateStrategy.READ, instance, findFociPickerViewBeanProperty_4,
            btnStop, jButtonBeanProperty);
    autoBinding_10.setConverter(new StringToBooleanConverter());
    autoBinding_10.bind();
    //
    final BeanProperty<FindFociHelperView, Integer> findFociPickerViewBeanProperty_8 =
        BeanProperty.create("searchMode");
    final AutoBinding<FindFociHelperView, Integer, JComboBox, Object> autoBinding_12 =
        Bindings.createAutoBinding(UpdateStrategy.READ_WRITE, instance,
            findFociPickerViewBeanProperty_8, comboSearchMode, jComboBoxBeanProperty);
    autoBinding_12.setConverter(new SearchModeConverter());
    autoBinding_12.bind();
    //
    final BeanProperty<FindFociHelperView, Boolean> findFociPickerViewBeanProperty_9 =
        BeanProperty.create("assignDragged");
    final AutoBinding<FindFociHelperView, Boolean, JCheckBox, Boolean> autoBinding_11 =
        Bindings.createAutoBinding(UpdateStrategy.READ_WRITE, instance,
            findFociPickerViewBeanProperty_9, chckbxAssigndragged, jCheckBoxBeanProperty);
    autoBinding_11.bind();
    //
    final AutoBinding<FindFociHelperView, String, JButton, Boolean> autoBinding_13 =
        Bindings.createAutoBinding(UpdateStrategy.READ, instance, findFociPickerViewBeanProperty_4,
            btnSaveResults, jButtonBeanProperty);
    autoBinding_13.setConverter(new StringToBooleanConverter());
    autoBinding_13.bind();
    //
    final BeanProperty<FindFociHelperView, Boolean> FindFociHelperView2BeanProperty =
        BeanProperty.create("showOverlay");
    final BeanProperty<JToggleButton, Boolean> jToggleButtonBeanProperty =
        BeanProperty.create("selected");
    final AutoBinding<FindFociHelperView, Boolean, JToggleButton, Boolean> autoBinding_14 =
        Bindings.createAutoBinding(UpdateStrategy.READ_WRITE, instance,
            FindFociHelperView2BeanProperty, tglbtnOverlay, jToggleButtonBeanProperty);
    autoBinding_14.bind();
    //
    final BeanProperty<JToggleButton, Boolean> jToggleButtonBeanProperty_1 =
        BeanProperty.create("enabled");
    final AutoBinding<FindFociHelperView, String, JToggleButton, Boolean> autoBinding_15 =
        Bindings.createAutoBinding(UpdateStrategy.READ, instance, findFociPickerViewBeanProperty_4,
            tglbtnOverlay, jToggleButtonBeanProperty_1);
    autoBinding_15.setConverter(new StringToBooleanConverter());
    autoBinding_15.bind();
    //
    final BeanProperty<FindFociModel, List<String>> findFociModelBeanProperty_2 =
        BeanProperty.create("maskImageList");
    final JComboBoxBinding<String, FindFociModel, JComboBox> jComboBinding_1 =
        SwingBindings.createJComboBoxBinding(UpdateStrategy.READ, model,
            findFociModelBeanProperty_2, comboMaskImageList);
    jComboBinding_1.bind();
    //
    final BeanProperty<FindFociModel, String> findFociModelBeanProperty_3 =
        BeanProperty.create("maskImage");
    final AutoBinding<FindFociModel, String, JComboBox, Object> autoBinding_16 =
        Bindings.createAutoBinding(UpdateStrategy.READ_WRITE, model, findFociModelBeanProperty_3,
            comboMaskImageList, jComboBoxBeanProperty);
    autoBinding_16.bind();
  }
}
