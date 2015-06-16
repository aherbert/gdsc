package gdsc.foci.gui;

/*----------------------------------------------------------------------------- 
 * GDSC Plugins for ImageJ
 * 
 * Copyright (C) 2011 Alex Herbert
 * Genome Damage and Stability Centre
 * University of Sussex, UK
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *---------------------------------------------------------------------------*/

import gdsc.foci.FindFoci;
import gdsc.foci.GridException;
import gdsc.foci.GridPoint;
import gdsc.foci.GridPointManager;
import gdsc.foci.AssignedPoint;
import gdsc.foci.MatchPlugin;
import gdsc.foci.PointAlignerPlugin;
import gdsc.foci.PointManager;
import gdsc.foci.controller.FindMaximaController;
import gdsc.foci.converter.ValidImagesConverter;
import gdsc.foci.model.FindFociModel;
import gdsc.format.LimitedNumberFormat;
import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.WindowManager;
import ij.gui.ImageCanvas;
import ij.gui.PointRoi;
import ij.gui.PolygonRoi;
import ij.gui.Roi;
import ij.macro.MacroRunner;
import ij.measure.Calibration;
import ij.text.TextWindow;

import java.awt.Color;
import java.awt.EventQueue;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Polygon;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;

import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFormattedTextField;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;

import org.jdesktop.beansbinding.AutoBinding;
import org.jdesktop.beansbinding.AutoBinding.UpdateStrategy;
import org.jdesktop.beansbinding.BeanProperty;
import org.jdesktop.beansbinding.Bindings;
import org.jdesktop.swingbinding.JComboBoxBinding;
import org.jdesktop.swingbinding.SwingBindings;

import javax.swing.JCheckBox;

import gdsc.foci.converter.StringToBooleanConverter;
import gdsc.foci.converter.SearchModeConverter;
import gdsc.utils.ImageJHelper;

import javax.swing.JToggleButton;

/**
 * Provides a permanent form front-end that allows the user to pick ROI points and have them mapped to the closest
 * maxima found by the FindFoci algorithm.
 */
public class FindFociHelperView extends JFrame implements WindowListener, MouseListener, MouseMotionListener
{
	private static final long serialVersionUID = -3550748049045647859L;

	private Object updatingPointsLock = new Object();

	// Flags used to control the enabled status of the run button.
	// The button should be enabled when there are images in the list.
	private boolean runEnabled = false;
	private int potentialMaxima = 0;
	private int resolution = 10;
	private boolean logging = true;
	private int searchMode = 0;
	private boolean assignDragged = true;
	private String activeImage = "";
	private int mappedPoints = 0;
	private int unmappedPoints = 0;
	private boolean showOverlay = false;

	private boolean counterEvents = true;

	private FindFociHelperView instance = this;

	private FindFociModel model;
	private FindMaximaController controller;

	private ImagePlus activeImp = null;
	private GridPointManager manager = null;
	private int currentRoiPoints = 0;
	private boolean dragging = false;
	private static TextWindow resultsWindow = null;
	private Roi savedRoi = null;

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
	 * Launch the application.
	 */
	public static void main(String[] args)
	{
		EventQueue.invokeLater(new Runnable()
		{
			public void run()
			{
				try
				{
					FindFociHelperView frame = new FindFociHelperView();
					frame.setVisible(true);
				}
				catch (Exception e)
				{
					e.printStackTrace();
				}
			}
		});
	}

	/**
	 * Create the frame.
	 */
	public FindFociHelperView()
	{
		init();
	}

	private void init()
	{
		createFindMaximaModel();
		controller = new FindMaximaController(model);

		addWindowListener(new WindowAdapter()
		{
			@Override
			public void windowActivated(WindowEvent e)
			{
				controller.updateImageList();
			}
		});

		setTitle("FindFoci Helper");
		setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		setBounds(100, 100, 450, 332);
		contentPane = new JPanel();
		contentPane.setBorder(new EmptyBorder(5, 5, 5, 5));
		setContentPane(contentPane);
		GridBagLayout gbl_contentPane = new GridBagLayout();
		gbl_contentPane.columnWidths = new int[] { 62, 0, 0, 0, 0 };
		gbl_contentPane.rowHeights = new int[] { 0, 0, 0, 0, 0, 31, 0, 0, 0, 0, 0, 0, 0 };
		gbl_contentPane.columnWeights = new double[] { 0.0, 1.0, 1.0, 1.0, Double.MIN_VALUE };
		gbl_contentPane.rowWeights = new double[] { 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0,
				Double.MIN_VALUE };
		contentPane.setLayout(gbl_contentPane);

		lblImage = new JLabel("Image");
		GridBagConstraints gbc_lblImage = new GridBagConstraints();
		gbc_lblImage.anchor = GridBagConstraints.EAST;
		gbc_lblImage.insets = new Insets(0, 0, 5, 5);
		gbc_lblImage.gridx = 0;
		gbc_lblImage.gridy = 0;
		contentPane.add(lblImage, gbc_lblImage);

		comboImageList = new JComboBox<String>();
		comboImageList.setToolTipText("Select the input image");
		comboImageList.addItemListener(new ItemListener()
		{
			public void itemStateChanged(ItemEvent e)
			{
				comboImageList.firePropertyChange("selectedItem", 0, 1);
			}
		});
		comboImageList.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mousePressed(MouseEvent e)
			{
				controller.updateImageList();
			}
		});
		GridBagConstraints gbc_comboImageList = new GridBagConstraints();
		gbc_comboImageList.gridwidth = 3;
		gbc_comboImageList.insets = new Insets(0, 0, 5, 0);
		gbc_comboImageList.fill = GridBagConstraints.HORIZONTAL;
		gbc_comboImageList.gridx = 1;
		gbc_comboImageList.gridy = 0;
		contentPane.add(comboImageList, gbc_comboImageList);
		
		lblMaskImage = new JLabel("Mask Image");
		GridBagConstraints gbc_lblMaskImage = new GridBagConstraints();
		gbc_lblMaskImage.anchor = GridBagConstraints.EAST;
		gbc_lblMaskImage.insets = new Insets(0, 0, 5, 5);
		gbc_lblMaskImage.gridx = 0;
		gbc_lblMaskImage.gridy = 1;
		contentPane.add(lblMaskImage, gbc_lblMaskImage);
		
		comboMaskImageList = new JComboBox<String>();
		comboMaskImageList.setToolTipText("Select the input mask image");
		comboMaskImageList.addItemListener(new ItemListener()
		{
			public void itemStateChanged(ItemEvent e)
			{
				comboMaskImageList.firePropertyChange("selectedItem", 0, 1);
			}
		});
		comboMaskImageList.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mousePressed(MouseEvent e)
			{
				controller.updateImageList();
			}
		});
		GridBagConstraints gbc_comboMaskImageList = new GridBagConstraints();
		gbc_comboMaskImageList.gridwidth = 3;
		gbc_comboMaskImageList.insets = new Insets(0, 0, 5, 0);
		gbc_comboMaskImageList.fill = GridBagConstraints.HORIZONTAL;
		gbc_comboMaskImageList.gridx = 1;
		gbc_comboMaskImageList.gridy = 1;
		contentPane.add(comboMaskImageList, gbc_comboMaskImageList);

		lblResolution = new JLabel("Resolution (px)");
		GridBagConstraints gbc_lblResolution = new GridBagConstraints();
		gbc_lblResolution.anchor = GridBagConstraints.EAST;
		gbc_lblResolution.insets = new Insets(0, 0, 5, 5);
		gbc_lblResolution.gridx = 0;
		gbc_lblResolution.gridy = 2;
		contentPane.add(lblResolution, gbc_lblResolution);

		txtResolution = new JFormattedTextField();
		txtResolution = new JFormattedTextField(new LimitedNumberFormat(0));
		txtResolution.addPropertyChangeListener(new PropertyChangeListener()
		{
			public void propertyChange(PropertyChangeEvent evt)
			{
				if (evt.getPropertyName() == "value")
				{
					txtResolution.firePropertyChange("text", 0, 1);
				}
			}
		});
		txtResolution.addKeyListener(new KeyAdapter()
		{
			@Override
			public void keyReleased(KeyEvent e)
			{
				txtResolution.firePropertyChange("text", 0, 1);
			}
		});
		txtResolution.setHorizontalAlignment(SwingConstants.RIGHT);
		txtResolution.setText("0");
		GridBagConstraints gbc_txtResolution = new GridBagConstraints();
		gbc_txtResolution.gridwidth = 3;
		gbc_txtResolution.insets = new Insets(0, 0, 5, 0);
		gbc_txtResolution.fill = GridBagConstraints.HORIZONTAL;
		gbc_txtResolution.gridx = 1;
		gbc_txtResolution.gridy = 2;
		contentPane.add(txtResolution, gbc_txtResolution);

		btnSaveResults = new JButton("Save Results");
		btnSaveResults.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent e)
			{
				if (isActive())
					saveResults();
			}
		});
		GridBagConstraints gbc_btnSaveResults = new GridBagConstraints();
		gbc_btnSaveResults.insets = new Insets(0, 0, 5, 0);
		gbc_btnSaveResults.gridx = 3;
		gbc_btnSaveResults.gridy = 3;
		contentPane.add(btnSaveResults, gbc_btnSaveResults);

		lblLogMessages = new JLabel("Log Messages");
		GridBagConstraints gbc_lblLogMessages = new GridBagConstraints();
		gbc_lblLogMessages.anchor = GridBagConstraints.EAST;
		gbc_lblLogMessages.insets = new Insets(0, 0, 5, 5);
		gbc_lblLogMessages.gridx = 0;
		gbc_lblLogMessages.gridy = 4;
		contentPane.add(lblLogMessages, gbc_lblLogMessages);

		chckbxLogmessages = new JCheckBox("");
		GridBagConstraints gbc_chckbxLogmessages = new GridBagConstraints();
		gbc_chckbxLogmessages.gridwidth = 2;
		gbc_chckbxLogmessages.anchor = GridBagConstraints.WEST;
		gbc_chckbxLogmessages.insets = new Insets(0, 0, 5, 5);
		gbc_chckbxLogmessages.gridx = 1;
		gbc_chckbxLogmessages.gridy = 4;
		contentPane.add(chckbxLogmessages, gbc_chckbxLogmessages);

		lblSearchMode = new JLabel("Search Mode");
		GridBagConstraints gbc_lblSearchMode = new GridBagConstraints();
		gbc_lblSearchMode.anchor = GridBagConstraints.EAST;
		gbc_lblSearchMode.insets = new Insets(0, 0, 5, 5);
		gbc_lblSearchMode.gridx = 0;
		gbc_lblSearchMode.gridy = 5;
		contentPane.add(lblSearchMode, gbc_lblSearchMode);

		comboSearchMode = new JComboBox<String>();
		comboSearchMode.addItemListener(new ItemListener()
		{
			public void itemStateChanged(ItemEvent e)
			{
				// Force the BeansBinding framework to pick up the state change
				comboSearchMode.firePropertyChange("selectedItem", 0, 1);
			}
		});
		GridBagConstraints gbc_comboSearchMode = new GridBagConstraints();
		gbc_comboSearchMode.gridwidth = 3;
		gbc_comboSearchMode.insets = new Insets(0, 0, 5, 0);
		gbc_comboSearchMode.fill = GridBagConstraints.HORIZONTAL;
		gbc_comboSearchMode.gridx = 1;
		gbc_comboSearchMode.gridy = 5;
		comboSearchMode.setModel(new DefaultComboBoxModel<String>(GridPointManager.SEARCH_MODES));
		contentPane.add(comboSearchMode, gbc_comboSearchMode);

		btnRun = new JButton("Start");
		btnRun.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent e)
			{
				initialisePicker();
			}
		});
		GridBagConstraints gbc_btnRun = new GridBagConstraints();
		gbc_btnRun.insets = new Insets(0, 0, 5, 5);
		gbc_btnRun.anchor = GridBagConstraints.NORTH;
		gbc_btnRun.gridx = 1;
		gbc_btnRun.gridy = 3;
		contentPane.add(btnRun, gbc_btnRun);

		btnStop = new JButton("Stop");
		btnStop.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent e)
			{
				killPicker();
			}
		});
		GridBagConstraints gbc_btnStop = new GridBagConstraints();
		gbc_btnStop.insets = new Insets(0, 0, 5, 5);
		gbc_btnStop.gridx = 2;
		gbc_btnStop.gridy = 3;
		contentPane.add(btnStop, gbc_btnStop);

		lblAssignDragged = new JLabel("Assign Dragged");
		GridBagConstraints gbc_lblAssignDragged = new GridBagConstraints();
		gbc_lblAssignDragged.anchor = GridBagConstraints.EAST;
		gbc_lblAssignDragged.insets = new Insets(0, 0, 5, 5);
		gbc_lblAssignDragged.gridx = 0;
		gbc_lblAssignDragged.gridy = 6;
		contentPane.add(lblAssignDragged, gbc_lblAssignDragged);

		chckbxAssigndragged = new JCheckBox("");
		GridBagConstraints gbc_chckbxAssigndragged = new GridBagConstraints();
		gbc_chckbxAssigndragged.anchor = GridBagConstraints.WEST;
		gbc_chckbxAssigndragged.insets = new Insets(0, 0, 5, 5);
		gbc_chckbxAssigndragged.gridx = 1;
		gbc_chckbxAssigndragged.gridy = 6;
		contentPane.add(chckbxAssigndragged, gbc_chckbxAssigndragged);

		lblActiveImage = new JLabel("Active Image");
		GridBagConstraints gbc_lblActiveImage = new GridBagConstraints();
		gbc_lblActiveImage.anchor = GridBagConstraints.EAST;
		gbc_lblActiveImage.insets = new Insets(0, 0, 5, 5);
		gbc_lblActiveImage.gridx = 0;
		gbc_lblActiveImage.gridy = 7;
		contentPane.add(lblActiveImage, gbc_lblActiveImage);

		txtActiveImage = new JLabel("ActiveImage");
		GridBagConstraints gbc_txtActiveImage = new GridBagConstraints();
		gbc_txtActiveImage.anchor = GridBagConstraints.WEST;
		gbc_txtActiveImage.gridwidth = 3;
		gbc_txtActiveImage.insets = new Insets(0, 0, 5, 0);
		gbc_txtActiveImage.gridx = 1;
		gbc_txtActiveImage.gridy = 7;
		contentPane.add(txtActiveImage, gbc_txtActiveImage);

		lblNumberOfPotential = new JLabel("Potential Maxima");
		GridBagConstraints gbc_lblNumberOfPotential = new GridBagConstraints();
		gbc_lblNumberOfPotential.anchor = GridBagConstraints.EAST;
		gbc_lblNumberOfPotential.insets = new Insets(0, 0, 5, 5);
		gbc_lblNumberOfPotential.gridx = 0;
		gbc_lblNumberOfPotential.gridy = 8;
		contentPane.add(lblNumberOfPotential, gbc_lblNumberOfPotential);

		labelPotentialMaxima = new JLabel("0");
		GridBagConstraints gbc_labelPotentialMaxima = new GridBagConstraints();
		gbc_labelPotentialMaxima.insets = new Insets(0, 0, 5, 0);
		gbc_labelPotentialMaxima.gridwidth = 3;
		gbc_labelPotentialMaxima.anchor = GridBagConstraints.WEST;
		gbc_labelPotentialMaxima.gridx = 1;
		gbc_labelPotentialMaxima.gridy = 8;
		contentPane.add(labelPotentialMaxima, gbc_labelPotentialMaxima);

		lblMappedPoints = new JLabel("Mapped");
		GridBagConstraints gbc_lblMappedPoints = new GridBagConstraints();
		gbc_lblMappedPoints.anchor = GridBagConstraints.WEST;
		gbc_lblMappedPoints.insets = new Insets(0, 0, 5, 5);
		gbc_lblMappedPoints.gridx = 1;
		gbc_lblMappedPoints.gridy = 9;
		contentPane.add(lblMappedPoints, gbc_lblMappedPoints);

		lblUnmappedPoints = new JLabel("Unmapped");
		GridBagConstraints gbc_lblUnmappedPoints = new GridBagConstraints();
		gbc_lblUnmappedPoints.anchor = GridBagConstraints.WEST;
		gbc_lblUnmappedPoints.insets = new Insets(0, 0, 5, 5);
		gbc_lblUnmappedPoints.gridx = 2;
		gbc_lblUnmappedPoints.gridy = 9;
		contentPane.add(lblUnmappedPoints, gbc_lblUnmappedPoints);

		lblTotal = new JLabel("Total");
		GridBagConstraints gbc_lblTotal = new GridBagConstraints();
		gbc_lblTotal.anchor = GridBagConstraints.WEST;
		gbc_lblTotal.insets = new Insets(0, 0, 5, 0);
		gbc_lblTotal.gridx = 3;
		gbc_lblTotal.gridy = 9;
		contentPane.add(lblTotal, gbc_lblTotal);

		lblPoints = new JLabel("Points");
		GridBagConstraints gbc_lblPoints = new GridBagConstraints();
		gbc_lblPoints.anchor = GridBagConstraints.EAST;
		gbc_lblPoints.insets = new Insets(0, 0, 5, 5);
		gbc_lblPoints.gridx = 0;
		gbc_lblPoints.gridy = 10;
		contentPane.add(lblPoints, gbc_lblPoints);

		txtMappedPoints = new JLabel("0");
		GridBagConstraints gbc_txtMappedPoints = new GridBagConstraints();
		gbc_txtMappedPoints.anchor = GridBagConstraints.WEST;
		gbc_txtMappedPoints.insets = new Insets(0, 0, 5, 5);
		gbc_txtMappedPoints.gridx = 1;
		gbc_txtMappedPoints.gridy = 10;
		contentPane.add(txtMappedPoints, gbc_txtMappedPoints);

		txtUnmappedPoints = new JLabel("0");
		GridBagConstraints gbc_txtUnmappedPoints = new GridBagConstraints();
		gbc_txtUnmappedPoints.anchor = GridBagConstraints.WEST;
		gbc_txtUnmappedPoints.insets = new Insets(0, 0, 5, 5);
		gbc_txtUnmappedPoints.gridx = 2;
		gbc_txtUnmappedPoints.gridy = 10;
		contentPane.add(txtUnmappedPoints, gbc_txtUnmappedPoints);

		txtTotal = new JLabel("0");
		GridBagConstraints gbc_txtTotal = new GridBagConstraints();
		gbc_txtTotal.insets = new Insets(0, 0, 5, 0);
		gbc_txtTotal.anchor = GridBagConstraints.WEST;
		gbc_txtTotal.gridx = 3;
		gbc_txtTotal.gridy = 10;
		contentPane.add(txtTotal, gbc_txtTotal);

		tglbtnOverlay = new JToggleButton("Show Overlay");
		tglbtnOverlay.addItemListener(new ItemListener()
		{
			public void itemStateChanged(ItemEvent e)
			{
				// Force the BeansBinding framework to pick up the state change
				tglbtnOverlay.firePropertyChange("selected", tglbtnOverlay.isSelected(), !tglbtnOverlay.isSelected());
			}
		});
		GridBagConstraints gbc_tglbtnOverlay = new GridBagConstraints();
		gbc_tglbtnOverlay.gridwidth = 2;
		gbc_tglbtnOverlay.insets = new Insets(0, 0, 0, 5);
		gbc_tglbtnOverlay.gridx = 1;
		gbc_tglbtnOverlay.gridy = 11;
		contentPane.add(tglbtnOverlay, gbc_tglbtnOverlay);

		btnHelp = new JButton("Help");
		btnHelp.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				String macro = "run('URL...', 'url=" + gdsc.help.URL.FIND_FOCI + "');";
				new MacroRunner(macro);
			}
		});
		GridBagConstraints gbc_btnHelp = new GridBagConstraints();
		gbc_btnHelp.gridx = 3;
		gbc_btnHelp.gridy = 11;
		contentPane.add(btnHelp, gbc_btnHelp);
		initDataBindings();
	}

	private void createFindMaximaModel()
	{
		model = new FindFociModel();
		
		model.setMaskImage(null);
		// Find points above the mean. This is a good start for finding maxima.
		model.setBackgroundMethod(FindFoci.BACKGROUND_STD_DEV_ABOVE_MEAN);
		model.setBackgroundParameter(0);
		model.setThresholdMethod("");
		model.setSearchMethod(FindFoci.SEARCH_ABOVE_BACKGROUND);
		model.setSearchParameter(0);
		model.setMaxPeaks(33000);
		model.setMinSize(1);
		model.setMinimumAboveSaddle(false);
		model.setPeakMethod(FindFoci.PEAK_RELATIVE);
		model.setPeakParameter(0);
		model.setShowMask(0);
		model.setShowTable(true); // We need to get the results table
		model.setMarkMaxima(false);
		model.setMarkROIMaxima(false);
		model.setShowLogMessages(false);
		model.setSortMethod(FindFoci.SORT_MAX_VALUE);
		model.setGaussianBlur(0);
		model.setCentreMethod(FindFoci.CENTRE_MAX_VALUE_ORIGINAL);
		model.setCentreParameter(0);
	}

	/**
	 * @param runEnabled
	 *            the runEnabled to set
	 */
	public void setRunEnabled(boolean runEnabled)
	{
		boolean oldValue = this.runEnabled;
		this.runEnabled = runEnabled;
		this.firePropertyChange("runEnabled", oldValue, runEnabled);
		if (!runEnabled)
		{
			killPicker();
		}
	}

	/**
	 * @return the runEnabled
	 */
	public boolean isRunEnabled()
	{
		return runEnabled;
	}

	/**
	 * @param logging
	 *            the logging to set
	 */
	public void setLogging(boolean logging)
	{
		boolean oldValue = this.logging;
		this.logging = logging;
		this.firePropertyChange("logging", oldValue, logging);
	}

	/**
	 * @return the logging
	 */
	public boolean isLogging()
	{
		return logging;
	}

	/**
	 * @param searchMode
	 *            the searchMode to set
	 */
	public void setSearchMode(int searchMode)
	{
		int oldValue = this.searchMode;
		this.searchMode = searchMode;
		this.firePropertyChange("searchMode", oldValue, searchMode);
		if (manager != null)
		{
			manager.setSearchMode(searchMode);
		}
	}

	/**
	 * @return the searchMode
	 */
	public int getSearchMode()
	{
		return searchMode;
	}

	/**
	 * @param assignDragged
	 *            the assignDragged to set
	 */
	public void setAssignDragged(boolean assignDragged)
	{
		boolean oldValue = this.assignDragged;
		this.assignDragged = assignDragged;
		this.firePropertyChange("assignDragged", oldValue, assignDragged);
	}

	/**
	 * @return the assignDragged
	 */
	public boolean isAssignDragged()
	{
		return assignDragged;
	}

	/**
	 * @param potentialMaxima
	 *            the potentialMaxima to set
	 */
	private void setPotentialMaxima(int potentialMaxima)
	{
		int oldValue = this.potentialMaxima;
		this.potentialMaxima = potentialMaxima;
		this.firePropertyChange("potentialMaxima", oldValue, potentialMaxima);
	}

	/**
	 * @return the potentialMaxima
	 */
	public int getPotentialMaxima()
	{
		return potentialMaxima;
	}

	/**
	 * @param resolution
	 *            the resolution to set
	 */
	public void setResolution(int resolution)
	{
		int oldValue = this.resolution;
		this.resolution = resolution;
		this.firePropertyChange("resolution", oldValue, resolution);
	}

	/**
	 * @return the resolution
	 */
	public int getResolution()
	{
		return resolution;
	}

	/**
	 * @param mappedPoints
	 *            the mappedPoints to set
	 */
	public void setMappedPoints(int mappedPoints)
	{
		int oldValue = this.mappedPoints;
		this.mappedPoints = mappedPoints;
		if (counterEvents)
		{
			this.firePropertyChange("mappedPoints", oldValue, mappedPoints);
			this.firePropertyChange("totalPoints", oldValue, mappedPoints);
		}
	}

	/**
	 * @return the mappedPoints
	 */
	public int getMappedPoints()
	{
		return mappedPoints;
	}

	/**
	 * @param unmappedPoints
	 *            the unmappedPoints to set
	 */
	public void setUnmappedPoints(int unmappedPoints)
	{
		int oldValue = this.unmappedPoints;
		this.unmappedPoints = unmappedPoints;
		if (counterEvents)
		{
			this.firePropertyChange("unmappedPoints", oldValue, unmappedPoints);
			this.firePropertyChange("totalPoints", oldValue, unmappedPoints);
		}
	}

	/**
	 * @return the unmappedPoints
	 */
	public int getUnmappedPoints()
	{
		return unmappedPoints;
	}

	/**
	 * @return the unmappedPoints
	 */
	public int getTotalPoints()
	{
		return unmappedPoints + mappedPoints;
	}

	/**
	 * @param showOverlay
	 *            the showOverlay to set
	 */
	public void setShowOverlay(boolean showOverlay)
	{
		boolean oldValue = this.showOverlay;
		this.showOverlay = showOverlay;
		this.firePropertyChange("showOverlay", oldValue, showOverlay);

		if (oldValue != showOverlay)
		{
			if (showOverlay)
			{
				showOverlay();
			}
			else
			{
				hideOverlay();
			}
		}
	}

	/**
	 * @return the showOverlay
	 */
	public boolean isShowOverlay()
	{
		return showOverlay;
	}

	/**
	 * @param activeImage
	 *            the activeImage to set
	 */
	private void setActiveImage(String activeImage)
	{
		String oldValue = this.activeImage;
		this.activeImage = activeImage;
		this.firePropertyChange("activeImage", oldValue, activeImage);
	}

	/**
	 * @return the activeImage
	 */
	public String getActiveImage()
	{
		return activeImage;
	}

	/**
	 * Initialise the active image. Map all the potential maxima and enable the image mouse event listener.
	 */
	private void initialisePicker()
	{
		killPicker();

		activeImp = WindowManager.getImage(model.getSelectedImage());
		if (isPickerActive())
		{
			setActiveImage(activeImp.getTitle());
			logMessage("Analysing image %s (mask = %s)", activeImage, model.getMaskImage());

			// Find foci and create the grid of maxima
			controller.run();
			setPotentialMaxima(controller.getResultsArray().size());

			// Create the grid of maxima
			try
			{
				List<GridPoint> points = extractGridPoints(controller.getResultsArray());
				manager = new GridPointManager(points, this.resolution);
				logMessage("Identified %d potential maxima", points.size());
				manager.setSearchMode(searchMode);
				assignRoiPoints();

				// Register mouse events from the image canvas
				activeImp.getWindow().addWindowListener(this);
				activeImp.getCanvas().addMouseListener(this);
				activeImp.getCanvas().addMouseMotionListener(this);
			}
			catch (GridException e)
			{
				logMessage("Failed to create the grid of potential maxima: %s", e.getMessage());
				killPicker();
			}
		}
	}

	/**
	 * Processes all the ROI points and assigns the grid points using the current search method.
	 * Should be called to initialise the system.
	 */
	private void assignRoiPoints()
	{
		synchronized (updatingPointsLock)
		{
			disableCounterEvents();

			setMappedPoints(0);
			setUnmappedPoints(0);

			AssignedPoint[] points = PointManager.extractRoiPoints(activeImp.getRoi());
			points = PointManager.eliminateDuplicates(points);
			if (points.length > 0)
			{
				logMessage("Assigning %d point%s to %s", points.length, (points.length != 1) ? "s" : "", activeImage);

				if (searchMode == GridPointManager.HIGHEST)
				{
					assignToHighest(points);
				}
				else
				{
					points = assignToClosest(points);
				}
				activeImp.setRoi(PointManager.createROI(points));
			}
			currentRoiPoints = points.length;

			enableCounterEvents();
		}
	}

	private void disableCounterEvents()
	{
		counterEvents = false;
	}

	private void enableCounterEvents()
	{
		counterEvents = true;
		this.firePropertyChange("unmappedPoints", unmappedPoints - 1, unmappedPoints);
		this.firePropertyChange("mappedPoints", mappedPoints - 1, mappedPoints);
		this.firePropertyChange("totalPoints", mappedPoints - 1, mappedPoints);
	}

	/**
	 * Process the points in descending height order, assigning to the highest unassigned peak
	 */
	private void assignToHighest(AssignedPoint[] points)
	{
		// If searchMode = 'highest' then the points should be processed in height order
		PointAlignerPlugin aligner = new PointAlignerPlugin();
		aligner.sortPoints(points, controller.getActiveImageStack());

		for (int i = 0; i < points.length; i++)
		{
			int x = points[i].getX();
			int y = points[i].getY();
			GridPoint gridPoint = manager.findUnassignedPoint(x, y);
			if (gridPoint != null)
			{
				addMappedPoint(x, y, gridPoint, i + 1);

				// Update points
				points[i] = new AssignedPoint(gridPoint.getX(), gridPoint.getY(), points[i].getZ(), points[i].getId());
			}
			else
			{
				addUnmappedPoint(x, y, i + 1);
			}
		}
	}

	/**
	 * Process the points by assigning to the closest unassigned peak. Points are allocated iteratively
	 * by first determining the distance to the closest unassigned peak for each unprocessed point and then
	 * assigning them in ascending distance order. Any peak that conflicts with a closer point for the
	 * same peak will be processed in the next iteration.
	 */
	private AssignedPoint[] assignToClosest(AssignedPoint[] points)
	{
		// If searchMode = 'closest' then the points should be processed in distance order

		// List of new points (mapped/unmapped) for the ROI
		ArrayList<AssignedPoint> roiPoints = new ArrayList<AssignedPoint>(points.length);

		// Repeat until all peaks have been processed
		while (roiPoints.size() < points.length)
		{
			int mapped = 0;
			int unmapped = 0;

			// Used to store the potential mappings
			LinkedList<int[]> potentialMappedPoints = new LinkedList<int[]>();

			for (AssignedPoint point : points)
			{
				// Skip if assigned
				if (point.getAssignedId() != 0)
					continue;

				// Find peak to assign
				int x = point.getX();
				int y = point.getY();
				GridPoint gridPoint = manager.findUnassignedPoint(x, y);
				if (gridPoint == null)
				{
					// No available peak so just add this point as unmapped
					addUnmappedPoint(x, y, roiPoints.size() + 1);
					addNewRoiPoint(roiPoints, point);
					unmapped++;
				}
				else
				{
					// Do not assign yet
					gridPoint.setAssigned(false);

					// Build a list of distances to unassigned peaks. Store distance and x,y coords.
					// Multiply the distance to allow double precision to be approximately compared with integers.
					potentialMappedPoints.add(new int[] { x, y, point.getZ(), point.getId(), gridPoint.getX(),
							gridPoint.getY(), (int) (gridPoint.distance2(x, y) * 100) });
				}
			}

			// Sort by distance
			Collections.sort(potentialMappedPoints, new DistanceComparator());

			// Process points assigning to the peak if it is unassigned and matches the stored coords
			for (int[] mapping : potentialMappedPoints)
			{
				int x = mapping[0];
				int y = mapping[1];
				int z = mapping[2];
				int id = mapping[3];
				int newx = mapping[4];
				int newy = mapping[5];

				GridPoint gridPoint = manager.findExactUnassignedPoint(newx, newy);

				if (gridPoint != null)
				{
					addMappedPoint(x, y, gridPoint, roiPoints.size() + 1);
					points[id] = new AssignedPoint(newx, newy, z, id);
					addNewRoiPoint(roiPoints, points[id]);
					mapped++;
				}
			}

			logMessage("Processed %d / %d. +%d mapped, +%d unmapped", roiPoints.size(), points.length, mapped, unmapped);
		}
		points = roiPoints.toArray(new AssignedPoint[0]);
		return points;
	}

	private void addNewRoiPoint(ArrayList<AssignedPoint> roiPoints, AssignedPoint point)
	{
		roiPoints.add(point);
		point.setAssignedId(this.getTotalPoints());
	}

	/**
	 * Processes all the ROI points and assigns the grid points that are an exact match.
	 * This should be called when an ROI point has been removed. All other points should
	 * align exactly to a grid point or are unmapped.
	 */
	private void reassignRoiPoints()
	{
		synchronized (updatingPointsLock)
		{
			disableCounterEvents();

			manager.resetAssigned();
			setMappedPoints(0);
			setUnmappedPoints(0);

			AssignedPoint[] points = PointManager.extractRoiPoints(activeImp.getRoi());
			if (points.length > 0)
			{
				logMessage("Re-assigning %d point%s to %s", points.length, (points.length != 1) ? "s" : "", activeImage);

				for (int i = 0; i < points.length; i++)
				{
					int x = points[i].getX();
					int y = points[i].getY();
					GridPoint gridPoint = manager.findExactUnassignedPoint(x, y);
					if (gridPoint != null)
					{
						addMappedPoint(x, y, gridPoint, i + 1);
					}
					else
					{
						addUnmappedPoint(x, y, i + 1);
					}
				}
			}
			currentRoiPoints = points.length;

			enableCounterEvents();
		}
	}

	private void addMappedPoint(int x, int y, GridPoint gridPoint, int index)
	{
		if (logging)
		{
			if (x == gridPoint.getX() && y == gridPoint.getY())
			{
				logMessage("%d: Mapped (%d,%d)", index, x, y);
			}
			else
			{
				logMessage("%d: Mapped (%d,%d) => (%d,%d) (%spx)", index, x, y, gridPoint.getX(), gridPoint.getY(),
						IJ.d2s(gridPoint.distance(x, y), 1));
			}
		}
		setMappedPoints(this.mappedPoints + 1);
	}

	private void addUnmappedPoint(int x, int y, int index)
	{
		logMessage("%d: Unmapped (%d,%d)", index, x, y);
		setUnmappedPoints(this.unmappedPoints + 1);
	}

	private boolean isPickerActive()
	{
		return activeImp != null;
	}

	private List<GridPoint> extractGridPoints(ArrayList<int[]> resultsArray)
	{
		List<GridPoint> points = new ArrayList<GridPoint>(resultsArray.size());
		for (int[] result : resultsArray)
		{
			points.add(new GridPoint(result[FindFoci.RESULT_X], result[FindFoci.RESULT_Y],
					result[FindFoci.RESULT_Z], result[FindFoci.RESULT_MAX_VALUE]));
		}
		return points;
	}

	/**
	 * Stop the interaction with the active image.
	 */
	private void killPicker()
	{
		if (isPickerActive())
		{
			setShowOverlay(false);
			try
			{
				// Unregister from the image canvas
				activeImp.getWindow().removeWindowListener(this);
				activeImp.getCanvas().removeMouseListener(this);
				activeImp.getCanvas().removeMouseMotionListener(this);
			}
			catch (Exception e)
			{
				logMessage("Failed to unregister from image %s", activeImp.getTitle());
			}
		}
		setActiveImage("");
		activeImp = null;
		setPotentialMaxima(0);
		setMappedPoints(0);
		setUnmappedPoints(0);
		currentRoiPoints = 0;
		manager = null;
		dragging = false;
	}

	public void windowOpened(WindowEvent e)
	{
	}

	public void windowClosing(WindowEvent e)
	{
		killPicker();
	}

	public void windowClosed(WindowEvent e)
	{
	}

	public void windowIconified(WindowEvent e)
	{
	}

	public void windowDeiconified(WindowEvent e)
	{
	}

	public void windowActivated(WindowEvent e)
	{
	}

	public void windowDeactivated(WindowEvent e)
	{
	}

	public void mouseClicked(MouseEvent e)
	{
		setShowOverlay(false);

		Roi roi = activeImp.getRoi();
		if (roi != null && roi.getType() == Roi.POINT)
		{
			PointRoi pointRoi = (PointRoi) roi;
			Polygon p = pointRoi.getNonSplineCoordinates();

			if (p.npoints < currentRoiPoints || p.npoints == 1)
			{
				// The ROI has had a point removed or has been restarted at 1 point

				if (p.npoints == 1)
				{
					// Picking has restarted so allow update 
					manager.resetAssigned();
					assignRoiPoints();
				}
				else
				{
					// This is a removal of a point, all other points should already be located at maxima
					reassignRoiPoints();
				}
			}
			else if (p.npoints > currentRoiPoints)
			{
				mapRoiPoint(pointRoi, p.npoints - 1);
			}
		}
	}

	/**
	 * Maps the given roi point to the highest unassigned grid point using the GridPointManager
	 * 
	 * @param roi
	 * @param roiIndex
	 */
	private void mapRoiPoint(PointRoi roi, int roiIndex)
	{
		synchronized (updatingPointsLock)
		{
			Polygon p = ((PolygonRoi) roi).getNonSplineCoordinates();
			Rectangle bounds = roi.getBounds();
			int x = bounds.x + p.xpoints[roiIndex];
			int y = bounds.y + p.ypoints[roiIndex];

			GridPoint gridPoint = manager.findUnassignedPoint(x, y);

			if (gridPoint != null)
			{
				addMappedPoint(x, y, gridPoint, roiIndex + 1);

				// Update the ROI if the peak was re-mapped
				// First update the existing points using the current bounds 
				for (int i = 0; i < p.npoints; i++)
				{
					if (i != roiIndex)
					{
						p.xpoints[i] += bounds.x;
						p.ypoints[i] += bounds.y;
					}
				}
				// Add the new position
				p.xpoints[roiIndex] = gridPoint.getX();
				p.ypoints[roiIndex] = gridPoint.getY();

				activeImp.setRoi(new PointRoi(p.xpoints, p.ypoints, p.npoints));
			}
			else
			{
				addUnmappedPoint(x, y, roiIndex + 1);
			}
			currentRoiPoints = p.npoints;
		}
	}

	/**
	 * Add the given roi point without mapping
	 * 
	 * @param roi
	 * @param roiIndex
	 */
	private void addRoiPoint(PointRoi roi, int roiIndex)
	{
		synchronized (updatingPointsLock)
		{
			Polygon p = ((PolygonRoi) roi).getNonSplineCoordinates();
			Rectangle bounds = roi.getBounds();
			int x = bounds.x + p.xpoints[roiIndex];
			int y = bounds.y + p.ypoints[roiIndex];

			addUnmappedPoint(x, y, roiIndex + 1);
			currentRoiPoints = p.npoints;
		}
	}

	public void mousePressed(MouseEvent e)
	{
	}

	/*
	 * If the user has dragged an ROI point then it should be reassigned when it is dropped.
	 */
	public void mouseReleased(MouseEvent e)
	{
		if (dragging)
		{
			dragging = false;
			Roi roi = activeImp.getRoi();
			if (roi != null && roi.getType() == Roi.POINT && roi.getState() == Roi.NORMAL)
			{
				// Find the image x,y coords
				ImageCanvas ic = activeImp.getCanvas();
				int ox = ic.offScreenX(e.getX());
				int oy = ic.offScreenY(e.getY());

				//logMessage("Dropped coords " + ox + "," + oy);

				int index = findRoiPointIndex((PointRoi) roi, ox, oy);

				if (index >= 0)
				{
					if (assignDragged)
					{
						mapRoiPoint((PointRoi) roi, index);
					}
					else
					{
						addRoiPoint((PointRoi) roi, index);
					}
				}
			}
		}
	}

	private int findRoiPointIndex(PointRoi roi, int ox, int oy)
	{
		Polygon p = ((PolygonRoi) roi).getNonSplineCoordinates();
		int n = p.npoints;
		Rectangle bounds = roi.getBounds();

		for (int i = 0; i < n; i++)
		{
			int x = bounds.x + p.xpoints[i];
			int y = bounds.y + p.ypoints[i];
			if (x == ox && y == oy)
				return i;
		}

		return -1;
	}

	public void mouseEntered(MouseEvent e)
	{
	}

	public void mouseExited(MouseEvent e)
	{
	}

	/*
	 * If the user is dragging a multi-point ROI position then this method will detect the
	 * point and set it to unassigned. This is done once at the start of the drag.
	 */
	public void mouseDragged(MouseEvent e)
	{
		setShowOverlay(false);

		Roi roi = activeImp.getRoi();
		if (roi != null && roi.getType() == Roi.POINT && roi.getState() == Roi.MOVING_HANDLE)
		{
			if (!dragging)
			{
				dragging = true;

				// Find the image x,y coords
				ImageCanvas ic = activeImp.getCanvas();
				int ox = ic.offScreenX(e.getX());
				int oy = ic.offScreenY(e.getY());

				//logMessage("Image coords " + ox + "," + oy);

				// Check if an assigned point is being moved
				GridPoint movedPoint = manager.findClosestAssignedPoint(ox, oy);

				double mag = activeImp.getCanvas().getMagnification();

				// Distance for the ROI is dependent on magnification.  
				// Approximate distance for mouse to change from cross to finger in X/Y:
				// 50% = 10px
				// 100% = 5px
				// 200% = 2px 
				// Given that the drag could move away from the current ROI centre a bit more 
				// tolerance could be added if this limit is too strict.
				int distanceLimit = (int) Math.ceil(5 / mag);

				// Check the point is within reasonable distance of the mouse
				if (movedPoint != null && Math.abs(movedPoint.getX() - ox) <= distanceLimit &&
						Math.abs(movedPoint.getY() - oy) <= distanceLimit)
				{
					//logMessage("Dragging point " + movedPoint.getX() + "," + movedPoint.getY());
					movedPoint.setAssigned(false);
					setMappedPoints(mappedPoints - 1);
				}
				else
				{
					setUnmappedPoints(unmappedPoints - 1);
				}
			}
		}
	}

	public void mouseMoved(MouseEvent e)
	{
	}

	private void logMessage(String format, Object... args)
	{
		if (logging)
		{
			IJ.log(String.format(format, args));
		}
	}

	/**
	 * Allows the mapped points to be ranked in order of the distance to the mapped peak
	 */
	private class DistanceComparator implements Comparator<int[]>
	{
		public int compare(int[] o1, int[] o2)
		{
			int diff = o1[6] - o2[6];
			if (diff > 0)
				return 1;
			if (diff < 0)
				return -1;
			// A second comparison could be added using for example the peak height or
			// the distance to the next unassigned peak.
			return 0;
		}
	}

	/**
	 * Saves the current ROI points to a results table
	 */
	private void saveResults()
	{
		Calibration cal = activeImp.getCalibration();
		
		if (resultsWindow == null || !resultsWindow.isShowing())
		{
			resultsWindow = new TextWindow("FindFoci Helper Results", createResultsHeader(), "", 300, 500);
		}

		ImageStack impStack = controller.getActiveImageStack();
		AssignedPoint[] points = getRoiPoints();
		for (AssignedPoint p : points)
		{
			int x = p.getX();
			int y = p.getY();
			int z = p.getZ();
			int height = impStack.getProcessor(z + 1).get(x, y);
			boolean assigned = (p.getAssignedId() != -1);
			addResult(p.getId() + 1, x*cal.pixelWidth, y*cal.pixelHeight, height, assigned);
		}
		resultsWindow.append("");
	}

	/**
	 * Get the list of ROI points. Any that are unmapped have the assigned id set to -1
	 * 
	 * @return
	 */
	private AssignedPoint[] getRoiPoints()
	{
		AssignedPoint[] points = PointManager.extractRoiPoints(activeImp.getRoi());
		for (AssignedPoint p : points)
		{
			int x = p.getX();
			int y = p.getY();
			GridPoint gridPoint = manager.findExactAssignedPoint(x, y);
			if (gridPoint == null)
			{
				p.setAssignedId(-1);
			}
		}
		return points;
	}

	private String createResultsHeader()
	{
		Calibration cal = activeImp.getCalibration();
		StringBuilder sb = new StringBuilder();
		sb.append("Id\t");
		sb.append("X (").append(cal.getXUnit()).append(")\t");
		sb.append("Y (").append(cal.getYUnit()).append(")\t");
		sb.append("Height\t");
		sb.append("Assigned\t");
		return sb.toString();
	}

	private void addResult(int index, double x, double y, int height, boolean assigned)
	{
		StringBuilder sb = new StringBuilder();
		sb.append(index).append("\t");
		sb.append(ImageJHelper.rounded(x)).append("\t");
		sb.append(ImageJHelper.rounded(y)).append("\t");
		sb.append(height).append("\t");
		sb.append(assigned).append("\t");
		resultsWindow.append(sb.toString());
	}

	/**
	 * Adds the mapped/unmapped points to the image using an overlay
	 */
	private void showOverlay()
	{
		// Build lists of the mapped and unmapped points
		AssignedPoint[] points = getRoiPoints();

		List<AssignedPoint> mapped = new LinkedList<AssignedPoint>();
		List<AssignedPoint> unmapped = new LinkedList<AssignedPoint>();
		for (AssignedPoint p : points)
		{
			if (p.getAssignedId() < 0)
				unmapped.add(p);
			else
				mapped.add(p);
		}

		// Add the overlay
		activeImp.setOverlay(null);
		MatchPlugin.addOverlay(activeImp, mapped, Color.green);
		MatchPlugin.addOverlay(activeImp, unmapped, Color.yellow);

		// Save the ROI and remove it
		savedRoi = activeImp.getRoi();
		if (savedRoi != null && savedRoi.getType() != Roi.POINT)
		{
			savedRoi = null;
		}
		activeImp.killRoi();
	}

	/**
	 * Hides the overlay and restores the ROI
	 */
	private void hideOverlay()
	{
		// Kill the overlay
		activeImp.setOverlay(null);

		Roi roi = activeImp.getRoi();
		if (roi != null)
		{
			// If this is a new point then merge it into the saved Point ROI
			if (roi.getType() == Roi.POINT && savedRoi != null)
			{
				// Merge the current ROI and the saved one
				PointRoi pointRoi = (PointRoi) savedRoi;
				AssignedPoint[] newPoints = PointManager.extractRoiPoints(roi);
				for (AssignedPoint p : newPoints)
				{
					//pointRoi = pointRoi.addPoint(p.getX() - pointRoi.getBounds().x, p.getY() - pointRoi.getBounds().y);
					pointRoi = pointRoi.addPoint(p.getX(), p.getY());
				}
				activeImp.setRoi(pointRoi, true);
			}
		}
		else
		{
			// No new ROI so just restore from the old one 
			activeImp.restoreRoi();
		}

		savedRoi = null;
	}
	@SuppressWarnings("rawtypes")
	protected void initDataBindings() {
		BeanProperty<FindFociModel, List<String>> findFociModelBeanProperty = BeanProperty.create("imageList");
		JComboBoxBinding<String, FindFociModel, JComboBox> jComboBinding = SwingBindings.createJComboBoxBinding(UpdateStrategy.READ, model, findFociModelBeanProperty, comboImageList);
		jComboBinding.bind();
		//
		BeanProperty<FindFociModel, String> findFociModelBeanProperty_1 = BeanProperty.create("selectedImage");
		BeanProperty<JComboBox, Object> jComboBoxBeanProperty = BeanProperty.create("selectedItem");
		AutoBinding<FindFociModel, String, JComboBox, Object> autoBinding = Bindings.createAutoBinding(UpdateStrategy.READ_WRITE, model, findFociModelBeanProperty_1, comboImageList, jComboBoxBeanProperty);
		autoBinding.bind();
		//
		BeanProperty<FindFociHelperView, Integer> findFociPickerViewBeanProperty = BeanProperty.create("potentialMaxima");
		BeanProperty<JLabel, String> jLabelBeanProperty = BeanProperty.create("text");
		AutoBinding<FindFociHelperView, Integer, JLabel, String> autoBinding_2 = Bindings.createAutoBinding(UpdateStrategy.READ, instance, findFociPickerViewBeanProperty, labelPotentialMaxima, jLabelBeanProperty);
		autoBinding_2.bind();
		//
		BeanProperty<FindFociHelperView, Boolean> findFociPickerViewBeanProperty_1 = BeanProperty.create("runEnabled");
		AutoBinding<FindFociModel, List<String>, FindFociHelperView, Boolean> autoBinding_3 = Bindings.createAutoBinding(UpdateStrategy.READ, model, findFociModelBeanProperty, instance, findFociPickerViewBeanProperty_1);
		autoBinding_3.setConverter(new ValidImagesConverter());
		autoBinding_3.bind();
		//
		BeanProperty<JButton, Boolean> jButtonBeanProperty = BeanProperty.create("enabled");
		AutoBinding<FindFociHelperView, Boolean, JButton, Boolean> autoBinding_4 = Bindings.createAutoBinding(UpdateStrategy.READ, instance, findFociPickerViewBeanProperty_1, btnRun, jButtonBeanProperty);
		autoBinding_4.bind();
		//
		BeanProperty<FindFociHelperView, Integer> findFociPickerViewBeanProperty_2 = BeanProperty.create("resolution");
		BeanProperty<JFormattedTextField, String> jFormattedTextFieldBeanProperty = BeanProperty.create("text");
		AutoBinding<FindFociHelperView, Integer, JFormattedTextField, String> autoBinding_1 = Bindings.createAutoBinding(UpdateStrategy.READ_WRITE, instance, findFociPickerViewBeanProperty_2, txtResolution, jFormattedTextFieldBeanProperty);
		autoBinding_1.bind();
		//
		BeanProperty<FindFociHelperView, Boolean> findFociPickerViewBeanProperty_3 = BeanProperty.create("logging");
		BeanProperty<JCheckBox, Boolean> jCheckBoxBeanProperty = BeanProperty.create("selected");
		AutoBinding<FindFociHelperView, Boolean, JCheckBox, Boolean> autoBinding_5 = Bindings.createAutoBinding(UpdateStrategy.READ_WRITE, instance, findFociPickerViewBeanProperty_3, chckbxLogmessages, jCheckBoxBeanProperty);
		autoBinding_5.bind();
		//
		BeanProperty<FindFociHelperView, String> findFociPickerViewBeanProperty_4 = BeanProperty.create("activeImage");
		AutoBinding<FindFociHelperView, String, JLabel, String> autoBinding_6 = Bindings.createAutoBinding(UpdateStrategy.READ, instance, findFociPickerViewBeanProperty_4, txtActiveImage, jLabelBeanProperty);
		autoBinding_6.bind();
		//
		BeanProperty<FindFociHelperView, Integer> findFociPickerViewBeanProperty_5 = BeanProperty.create("mappedPoints");
		AutoBinding<FindFociHelperView, Integer, JLabel, String> autoBinding_7 = Bindings.createAutoBinding(UpdateStrategy.READ, instance, findFociPickerViewBeanProperty_5, txtMappedPoints, jLabelBeanProperty);
		autoBinding_7.bind();
		//
		BeanProperty<FindFociHelperView, Integer> findFociPickerViewBeanProperty_6 = BeanProperty.create("unmappedPoints");
		AutoBinding<FindFociHelperView, Integer, JLabel, String> autoBinding_8 = Bindings.createAutoBinding(UpdateStrategy.READ, instance, findFociPickerViewBeanProperty_6, txtUnmappedPoints, jLabelBeanProperty);
		autoBinding_8.bind();
		//
		BeanProperty<FindFociHelperView, Integer> findFociPickerViewBeanProperty_7 = BeanProperty.create("totalPoints");
		AutoBinding<FindFociHelperView, Integer, JLabel, String> autoBinding_9 = Bindings.createAutoBinding(UpdateStrategy.READ, instance, findFociPickerViewBeanProperty_7, txtTotal, jLabelBeanProperty);
		autoBinding_9.bind();
		//
		AutoBinding<FindFociHelperView, String, JButton, Boolean> autoBinding_10 = Bindings.createAutoBinding(UpdateStrategy.READ, instance, findFociPickerViewBeanProperty_4, btnStop, jButtonBeanProperty);
		autoBinding_10.setConverter(new StringToBooleanConverter());
		autoBinding_10.bind();
		//
		BeanProperty<FindFociHelperView, Integer> findFociPickerViewBeanProperty_8 = BeanProperty.create("searchMode");
		AutoBinding<FindFociHelperView, Integer, JComboBox, Object> autoBinding_12 = Bindings.createAutoBinding(UpdateStrategy.READ_WRITE, instance, findFociPickerViewBeanProperty_8, comboSearchMode, jComboBoxBeanProperty);
		autoBinding_12.setConverter(new SearchModeConverter());
		autoBinding_12.bind();
		//
		BeanProperty<FindFociHelperView, Boolean> findFociPickerViewBeanProperty_9 = BeanProperty.create("assignDragged");
		AutoBinding<FindFociHelperView, Boolean, JCheckBox, Boolean> autoBinding_11 = Bindings.createAutoBinding(UpdateStrategy.READ_WRITE, instance, findFociPickerViewBeanProperty_9, chckbxAssigndragged, jCheckBoxBeanProperty);
		autoBinding_11.bind();
		//
		AutoBinding<FindFociHelperView, String, JButton, Boolean> autoBinding_13 = Bindings.createAutoBinding(UpdateStrategy.READ, instance, findFociPickerViewBeanProperty_4, btnSaveResults, jButtonBeanProperty);
		autoBinding_13.setConverter(new StringToBooleanConverter());
		autoBinding_13.bind();
		//
		BeanProperty<FindFociHelperView, Boolean> FindFociHelperView2BeanProperty = BeanProperty.create("showOverlay");
		BeanProperty<JToggleButton, Boolean> jToggleButtonBeanProperty = BeanProperty.create("selected");
		AutoBinding<FindFociHelperView, Boolean, JToggleButton, Boolean> autoBinding_14 = Bindings.createAutoBinding(UpdateStrategy.READ_WRITE, instance, FindFociHelperView2BeanProperty, tglbtnOverlay, jToggleButtonBeanProperty);
		autoBinding_14.bind();
		//
		BeanProperty<JToggleButton, Boolean> jToggleButtonBeanProperty_1 = BeanProperty.create("enabled");
		AutoBinding<FindFociHelperView, String, JToggleButton, Boolean> autoBinding_15 = Bindings.createAutoBinding(UpdateStrategy.READ, instance, findFociPickerViewBeanProperty_4, tglbtnOverlay, jToggleButtonBeanProperty_1);
		autoBinding_15.setConverter(new StringToBooleanConverter());
		autoBinding_15.bind();
		//
		BeanProperty<FindFociModel, List<String>> findFociModelBeanProperty_2 = BeanProperty.create("maskImageList");
		JComboBoxBinding<String, FindFociModel, JComboBox> jComboBinding_1 = SwingBindings.createJComboBoxBinding(UpdateStrategy.READ, model, findFociModelBeanProperty_2, comboMaskImageList);
		jComboBinding_1.bind();
		//
		BeanProperty<FindFociModel, String> findFociModelBeanProperty_3 = BeanProperty.create("maskImage");
		AutoBinding<FindFociModel, String, JComboBox, Object> autoBinding_16 = Bindings.createAutoBinding(UpdateStrategy.READ_WRITE, model, findFociModelBeanProperty_3, comboMaskImageList, jComboBoxBeanProperty);
		autoBinding_16.bind();
	}
}
