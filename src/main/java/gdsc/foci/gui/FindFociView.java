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
package gdsc.foci.gui;

import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.List;

import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFormattedTextField;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSeparator;
import javax.swing.JSlider;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import org.jdesktop.beansbinding.AutoBinding;
import org.jdesktop.beansbinding.AutoBinding.UpdateStrategy;
import org.jdesktop.beansbinding.BeanProperty;
import org.jdesktop.beansbinding.Bindings;
import org.jdesktop.beansbinding.ELProperty;
import org.jdesktop.swingbinding.JComboBoxBinding;
import org.jdesktop.swingbinding.SwingBindings;

import gdsc.foci.FindFoci;
import gdsc.foci.controller.FindFociController;
import gdsc.foci.controller.MessageListener;
import gdsc.foci.controller.NullController;
import gdsc.foci.converter.BackgroundMethodConverter;
import gdsc.foci.converter.BackgroundParamAbsoluteDisabledConverter;
import gdsc.foci.converter.BackgroundParamAbsoluteEnabledConverter;
import gdsc.foci.converter.BackgroundParamEnabledConverter;
import gdsc.foci.converter.BackgroundThresholdMethodEnabledConverter;
import gdsc.foci.converter.DoubleConverter;
import gdsc.foci.converter.PeakMethodConverter;
import gdsc.foci.converter.PeakParamAbsoluteDisabledConverter;
import gdsc.foci.converter.PeakParamAbsoluteEnabledConverter;
import gdsc.foci.converter.SearchMethodConverter;
import gdsc.foci.converter.SearchParamEnabledConverter;
import gdsc.foci.converter.ShowMaskConverter;
import gdsc.foci.converter.SliderConverter;
import gdsc.foci.converter.SliderDoubleConverter;
import gdsc.foci.converter.SortMethodColorConverter;
import gdsc.foci.converter.SortMethodConverter;
import gdsc.foci.converter.StatisticsModeParamEnabledConverter;
import gdsc.foci.converter.ValidImagesConverter;
import gdsc.foci.model.FindFociModel;
import gdsc.format.LimitedNumberFormat;
import ij.IJ;
import ij.macro.MacroRunner;

/**
 * Provides a permanent form front-end for the FindFoci algorithm
 */
public class FindFociView extends JFrame implements PropertyChangeListener, MessageListener
{
	private static final long serialVersionUID = 4515468509409681730L;

	private FindFociModel model;
	private FindFociController controller;
	private FindFociAdvancedOptions options;

	// Flags used to control the enabled status of the run button.
	// The button should be enabled when there are images in the list and the model has been changed.
	private boolean validImages = false;
	private boolean changed = false;
	private boolean runEnabled = false;
	private double backgroundLevel = 0;
	private boolean sortIndexError = false;
	private int oldSortIndex = -1;
	private FindFociView instance = this;

	// Used to set the limits for the absolute threshold slider
	private int[] limits = new int[2];

	private JPanel contentPane;
	private JLabel lblBackgroundMethod;
	private JComboBox<String> comboBackgroundMethod;
	private JComboBox<String> comboThresholdMethod;
	private JLabel lblBackgroundParam;
	private JLabel lblGaussianBlur;
	private JLabel lblSearchMethod;
	private JComboBox<String> comboSearchMethod;
	private JLabel lblSearchParam;
	private JSlider sliderSearchParam;
	private JSeparator separator;
	private JSeparator separator_1;
	private JSeparator separator_2;
	private JLabel lblMinimumSize;
	private JLabel lblPeakMethod;
	private JLabel lblPeakParam;
	private JComboBox<String> comboImageList;
	private JLabel lblThresholdMethod;
	private JSlider sliderMinimumSize;
	private JCheckBox chckbxMinSizeAboveSaddle;
	private JCheckBox chckbxConnectedAboveSaddle;
	private JComboBox<String> comboPeakMethod;
	private JSlider sliderPeakParam;
	private JSeparator separator_4;
	private JLabel lblSortMethod;
	private JComboBox<String> comboSortMethod;
	private JLabel lblMaxPeaks;
	private JSlider sliderMaxPeaks;
	private JLabel lblShowMask;
	private JComboBox<String> comboShowMask;
	private JSlider sliderGaussianBlur;
	private JSlider sliderBackgroundParam;
	private JSlider sliderBackgroundParamAbsolute;
	private JSeparator separator_3;
	private JButton btnRun;
	private JFormattedTextField txtGaussianBlur;
	private JFormattedTextField txtBackgroundParam;
	private JFormattedTextField txtSearchParam;
	private JFormattedTextField txtMinimumSize;
	private JFormattedTextField txtPeakParam;
	private JFormattedTextField txtMaxPeaks;
	private JButton btnAdvancedOptions;
	private JPanel panel;
	private JSlider sliderPeakParamAbsolute;
	private JCheckBox chckbxPreview;
	private JLabel lblMaskImage;
	private JComboBox<String> comboMaskImageList;
	private JComboBox<String> comboStatisticsMode;
	private JLabel lblStatisticsMode;
	private JButton btnHelp;
	private JLabel lblFractionParam;
	private JSlider sliderFractionParam;
	private JTextField txtFractionParam;
	private JLabel lblBackgroundLevel;
	private JLabel lblBackgroundLevelValue;

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
					FindFociView frame = new FindFociView();
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
	 * 
	 * @param model
	 *            The FindFociModel to use
	 * @param controller
	 *            The FindFociController to use
	 */
	public FindFociView(FindFociModel model, FindFociController controller)
	{
		this.model = model;
		this.controller = controller;
		controller.addMessageListener(this);
		init();
	}

	/**
	 * Create the frame.
	 */
	public FindFociView()
	{
		model = new FindFociModel();
		controller = new NullController(model);
		init();
	}

	private void init()
	{		
		addWindowListener(new WindowAdapter()
		{
			@Override
			public void windowActivated(WindowEvent e)
			{
				controller.updateImageList();
				updateImageLimits();
			}

			@Override
			public void windowClosing(WindowEvent e)
			{
				if (options != null && options.isVisible())
					options.setVisible(false);
			}
		});

		// Track updates to the image
		model.addPropertyChangeListener("selectedImage", this);
		model.addPropertyChangeListener("valid", this);

		setTitle("FindFoci");
		setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		setBounds(100, 100, 450, 696);
		contentPane = new JPanel();
		contentPane.setBorder(new EmptyBorder(5, 5, 5, 5));
		setContentPane(contentPane);
		GridBagLayout gbl_contentPane = new GridBagLayout();
		gbl_contentPane.columnWidths = new int[] { 0, 182, 50, 0 };
		gbl_contentPane.rowHeights = new int[] { 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
				0, 0 };
		gbl_contentPane.columnWeights = new double[] { 0.0, 1.0, 1.0, Double.MIN_VALUE };
		gbl_contentPane.rowWeights = new double[] { 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0,
				0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 1.0, Double.MIN_VALUE };
		contentPane.setLayout(gbl_contentPane);

		JLabel lblName = new JLabel("Image");
		GridBagConstraints gbc_lblName = new GridBagConstraints();
		gbc_lblName.anchor = GridBagConstraints.EAST;
		gbc_lblName.insets = new Insets(0, 0, 5, 5);
		gbc_lblName.gridx = 0;
		gbc_lblName.gridy = 0;
		contentPane.add(lblName, gbc_lblName);

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
				// Quick check to see if images have been closed since
				// the windowActivated event does fire if the plugin window
				// was already active when an image was closed.
				if (controller.getImageCount() != comboImageList.getItemCount())
				{
					controller.updateImageList();
					updateImageLimits();
				}
			}
		});
		GridBagConstraints gbc_comboImageList = new GridBagConstraints();
		gbc_comboImageList.gridwidth = 2;
		gbc_comboImageList.insets = new Insets(0, 0, 5, 0);
		gbc_comboImageList.fill = GridBagConstraints.HORIZONTAL;
		gbc_comboImageList.gridx = 1;
		gbc_comboImageList.gridy = 0;
		contentPane.add(comboImageList, gbc_comboImageList);

		separator = new JSeparator();
		GridBagConstraints gbc_separator = new GridBagConstraints();
		gbc_separator.insets = new Insets(0, 0, 15, 5);
		gbc_separator.gridx = 1;
		gbc_separator.gridy = 1;
		contentPane.add(separator, gbc_separator);

		lblGaussianBlur = new JLabel("Gaussian blur");
		lblGaussianBlur.setToolTipText("");
		GridBagConstraints gbc_lblGaussianBlur = new GridBagConstraints();
		gbc_lblGaussianBlur.anchor = GridBagConstraints.EAST;
		gbc_lblGaussianBlur.insets = new Insets(0, 0, 5, 5);
		gbc_lblGaussianBlur.gridx = 0;
		gbc_lblGaussianBlur.gridy = 2;
		contentPane.add(lblGaussianBlur, gbc_lblGaussianBlur);

		sliderGaussianBlur = new JSlider();
		sliderGaussianBlur.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				if (e.getClickCount() > 1)
				{
					SliderLimitHelper.updateRangeLimits(sliderGaussianBlur, "Gaussian blur",
							SliderConverter.SCALE_FACTOR, 0, Double.POSITIVE_INFINITY);
				}
			}
		});
		sliderGaussianBlur.setToolTipText("Apply a pre-processing blur. Helps noisy images");
		sliderGaussianBlur.addChangeListener(new ChangeListener()
		{
			public void stateChanged(ChangeEvent e)
			{
				sliderGaussianBlur.firePropertyChange("value", 0, 1);
			}
		});
		sliderGaussianBlur.setMinorTickSpacing(250);
		sliderGaussianBlur.setMajorTickSpacing(250);
		sliderGaussianBlur.setValue(5);
		sliderGaussianBlur.setMaximum(20000);
		GridBagConstraints gbc_sliderGaussianBlur = new GridBagConstraints();
		gbc_sliderGaussianBlur.fill = GridBagConstraints.HORIZONTAL;
		gbc_sliderGaussianBlur.insets = new Insets(0, 0, 5, 5);
		gbc_sliderGaussianBlur.gridx = 1;
		gbc_sliderGaussianBlur.gridy = 2;
		contentPane.add(sliderGaussianBlur, gbc_sliderGaussianBlur);

		txtGaussianBlur = new JFormattedTextField(new LimitedNumberFormat(0));
		txtGaussianBlur.addPropertyChangeListener(new PropertyChangeListener()
		{
			public void propertyChange(PropertyChangeEvent evt)
			{
				if (evt.getPropertyName() == "value")
				{
					txtGaussianBlur.firePropertyChange("text", 0, 1);
				}
			}
		});
		txtGaussianBlur.addKeyListener(new KeyAdapter()
		{
			@Override
			public void keyReleased(KeyEvent e)
			{
				txtGaussianBlur.firePropertyChange("text", 0, 1);
			}
		});
		txtGaussianBlur.setText("0");
		txtGaussianBlur.setHorizontalAlignment(SwingConstants.RIGHT);
		//txtGaussianBlur.add
		GridBagConstraints gbc_txtGaussianBlur = new GridBagConstraints();
		gbc_txtGaussianBlur.insets = new Insets(0, 0, 5, 0);
		gbc_txtGaussianBlur.fill = GridBagConstraints.HORIZONTAL;
		gbc_txtGaussianBlur.gridx = 2;
		gbc_txtGaussianBlur.gridy = 2;
		contentPane.add(txtGaussianBlur, gbc_txtGaussianBlur);

		lblMaskImage = new JLabel("Mask Image");
		GridBagConstraints gbc_lblMaskImage = new GridBagConstraints();
		gbc_lblMaskImage.anchor = GridBagConstraints.EAST;
		gbc_lblMaskImage.insets = new Insets(0, 0, 5, 5);
		gbc_lblMaskImage.gridx = 0;
		gbc_lblMaskImage.gridy = 3;
		contentPane.add(lblMaskImage, gbc_lblMaskImage);

		// TODO - Add the bindings necessary to update the list 
		comboMaskImageList = new JComboBox<String>();
		comboMaskImageList.setToolTipText("Select a mask defining the analysis area");
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
				// Quick check to see if images have been closed since
				// the the windowActivated event does fire if the plugin window
				// was already active when an image was closed.
				if (controller.getImageCount() != comboImageList.getItemCount())
				{
					controller.updateImageList();
					updateImageLimits();
				}
			}
		});
		GridBagConstraints gbc_comboMaskImageList = new GridBagConstraints();
		gbc_comboMaskImageList.gridwidth = 2;
		gbc_comboMaskImageList.insets = new Insets(0, 0, 5, 0);
		gbc_comboMaskImageList.fill = GridBagConstraints.HORIZONTAL;
		gbc_comboMaskImageList.gridx = 1;
		gbc_comboMaskImageList.gridy = 3;
		contentPane.add(comboMaskImageList, gbc_comboMaskImageList);

		lblBackgroundMethod = new JLabel("Background method");
		GridBagConstraints gbc_lblBackgroundMethod = new GridBagConstraints();
		gbc_lblBackgroundMethod.anchor = GridBagConstraints.EAST;
		gbc_lblBackgroundMethod.insets = new Insets(0, 0, 5, 5);
		gbc_lblBackgroundMethod.gridx = 0;
		gbc_lblBackgroundMethod.gridy = 4;
		contentPane.add(lblBackgroundMethod, gbc_lblBackgroundMethod);

		comboBackgroundMethod = new JComboBox<String>();
		comboBackgroundMethod.setToolTipText("Specify the background threshold method");
		comboBackgroundMethod.addItemListener(new ItemListener()
		{
			public void itemStateChanged(ItemEvent e)
			{
				comboBackgroundMethod.firePropertyChange("selectedItem", 0, 1);
			}
		});
		comboBackgroundMethod.setModel(new DefaultComboBoxModel<String>(FindFoci.backgroundMethods));
		comboBackgroundMethod.setSelectedIndex(3);
		GridBagConstraints gbc_comboBackgroundMethod = new GridBagConstraints();
		gbc_comboBackgroundMethod.fill = GridBagConstraints.HORIZONTAL;
		gbc_comboBackgroundMethod.gridwidth = 2;
		gbc_comboBackgroundMethod.insets = new Insets(0, 0, 5, 0);
		gbc_comboBackgroundMethod.gridx = 1;
		gbc_comboBackgroundMethod.gridy = 4;
		contentPane.add(comboBackgroundMethod, gbc_comboBackgroundMethod);

		lblBackgroundParam = new JLabel("Background param");
		GridBagConstraints gbc_lblBackgroundParam = new GridBagConstraints();
		gbc_lblBackgroundParam.anchor = GridBagConstraints.EAST;
		gbc_lblBackgroundParam.insets = new Insets(0, 0, 5, 5);
		gbc_lblBackgroundParam.gridx = 0;
		gbc_lblBackgroundParam.gridy = 5;
		contentPane.add(lblBackgroundParam, gbc_lblBackgroundParam);

		sliderBackgroundParam = new JSlider();
		sliderBackgroundParam.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				if (e.getClickCount() > 1)
				{
					SliderLimitHelper.updateRangeLimits(sliderBackgroundParam, "Background parameter",
							SliderConverter.SCALE_FACTOR, 0, Double.POSITIVE_INFINITY);
				}
			}
		});
		sliderBackgroundParam.setToolTipText("Controls the selected background method");
		sliderBackgroundParam.addChangeListener(new ChangeListener()
		{
			public void stateChanged(ChangeEvent e)
			{
				if (model.getBackgroundMethod() != FindFoci.BACKGROUND_ABSOLUTE)
				{
					sliderBackgroundParam.firePropertyChange("value", 0, 1);
				}
			}
		});
		sliderBackgroundParam.setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
		sliderBackgroundParam.setValue(5);
		sliderBackgroundParam.setMinorTickSpacing(250);
		sliderBackgroundParam.setMaximum(10000);
		sliderBackgroundParam.setMajorTickSpacing(250);
		GridBagConstraints gbc_sliderBackgroundParam = new GridBagConstraints();
		gbc_sliderBackgroundParam.fill = GridBagConstraints.HORIZONTAL;
		gbc_sliderBackgroundParam.insets = new Insets(0, 0, 5, 5);
		gbc_sliderBackgroundParam.gridx = 1;
		gbc_sliderBackgroundParam.gridy = 5;
		contentPane.add(sliderBackgroundParam, gbc_sliderBackgroundParam);

		sliderBackgroundParamAbsolute = new JSlider();
		sliderBackgroundParamAbsolute.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				if (e.getClickCount() > 1)
				{
					int[] limits = controller.getImageLimits(null);
					SliderLimitHelper.updateRangeLimits(sliderBackgroundParamAbsolute, "Background parameter", 1,
							limits[0], limits[1]);
				}
			}
		});
		sliderBackgroundParamAbsolute.addChangeListener(new ChangeListener()
		{
			public void stateChanged(ChangeEvent e)
			{
				if (model.getBackgroundMethod() == FindFoci.BACKGROUND_ABSOLUTE)
				{
					sliderBackgroundParamAbsolute.firePropertyChange("value", 0, 1);
				}
			}
		});
		sliderBackgroundParamAbsolute.setValue(5);
		sliderBackgroundParamAbsolute.setMinorTickSpacing(250);
		sliderBackgroundParamAbsolute.setMaximum(20000);
		sliderBackgroundParamAbsolute.setMajorTickSpacing(250);
		GridBagConstraints gbc_sliderBackgroundParamAbsolute = new GridBagConstraints();
		gbc_sliderBackgroundParamAbsolute.fill = GridBagConstraints.HORIZONTAL;
		gbc_sliderBackgroundParamAbsolute.insets = new Insets(0, 0, 5, 5);
		gbc_sliderBackgroundParamAbsolute.gridx = 1;
		gbc_sliderBackgroundParamAbsolute.gridy = 5;
		contentPane.add(sliderBackgroundParamAbsolute, gbc_sliderBackgroundParamAbsolute);

		txtBackgroundParam = new JFormattedTextField(new LimitedNumberFormat(0));
		txtBackgroundParam.addPropertyChangeListener(new PropertyChangeListener()
		{
			public void propertyChange(PropertyChangeEvent evt)
			{
				if (evt.getPropertyName() == "value")
				{
					txtBackgroundParam.firePropertyChange("text", 0, 1);
				}
			}
		});
		txtBackgroundParam.addKeyListener(new KeyAdapter()
		{
			@Override
			public void keyReleased(KeyEvent e)
			{
				txtBackgroundParam.firePropertyChange("text", 0, 1);
			}
		});
		txtBackgroundParam.setHorizontalAlignment(SwingConstants.RIGHT);
		txtBackgroundParam.setText("0");
		GridBagConstraints gbc_txtBackgroundParam = new GridBagConstraints();
		gbc_txtBackgroundParam.insets = new Insets(0, 0, 5, 0);
		gbc_txtBackgroundParam.fill = GridBagConstraints.HORIZONTAL;
		gbc_txtBackgroundParam.gridx = 2;
		gbc_txtBackgroundParam.gridy = 5;
		contentPane.add(txtBackgroundParam, gbc_txtBackgroundParam);

		lblThresholdMethod = new JLabel("Threshold method");
		GridBagConstraints gbc_lblThresholdMethod = new GridBagConstraints();
		gbc_lblThresholdMethod.insets = new Insets(0, 0, 5, 5);
		gbc_lblThresholdMethod.anchor = GridBagConstraints.EAST;
		gbc_lblThresholdMethod.gridx = 0;
		gbc_lblThresholdMethod.gridy = 6;
		contentPane.add(lblThresholdMethod, gbc_lblThresholdMethod);

		comboThresholdMethod = new JComboBox<String>();
		comboThresholdMethod.setToolTipText("Method used for auto-thresholding");
		comboThresholdMethod.addItemListener(new ItemListener()
		{
			public void itemStateChanged(ItemEvent e)
			{
				comboThresholdMethod.firePropertyChange("selectedItem", 0, 1);
			}
		});
		comboThresholdMethod.setModel(new DefaultComboBoxModel<String>(FindFoci.autoThresholdMethods));
		comboThresholdMethod.setSelectedIndex(10);
		GridBagConstraints gbc_comboThresholdMethod = new GridBagConstraints();
		gbc_comboThresholdMethod.fill = GridBagConstraints.HORIZONTAL;
		gbc_comboThresholdMethod.gridwidth = 2;
		gbc_comboThresholdMethod.insets = new Insets(0, 0, 5, 0);
		gbc_comboThresholdMethod.gridx = 1;
		gbc_comboThresholdMethod.gridy = 6;
		contentPane.add(comboThresholdMethod, gbc_comboThresholdMethod);

		lblStatisticsMode = new JLabel("Statistics mode");
		GridBagConstraints gbc_lblStatisticsMode = new GridBagConstraints();
		gbc_lblStatisticsMode.insets = new Insets(0, 0, 5, 5);
		gbc_lblStatisticsMode.anchor = GridBagConstraints.EAST;
		gbc_lblStatisticsMode.gridx = 0;
		gbc_lblStatisticsMode.gridy = 7;
		contentPane.add(lblStatisticsMode, gbc_lblStatisticsMode);

		comboStatisticsMode = new JComboBox<String>();
		comboStatisticsMode.setToolTipText("Calculate background using area inside/outside the ROI/Masked region");
		comboStatisticsMode.addItemListener(new ItemListener()
		{
			public void itemStateChanged(ItemEvent e)
			{
				comboStatisticsMode.firePropertyChange("selectedItem", 0, 1);
			}
		});
		comboStatisticsMode.setModel(new DefaultComboBoxModel<String>(FindFoci.statisticsModes));
		GridBagConstraints gbc_comboStatisticsMode = new GridBagConstraints();
		gbc_comboStatisticsMode.gridwidth = 2;
		gbc_comboStatisticsMode.insets = new Insets(0, 0, 5, 0);
		gbc_comboStatisticsMode.fill = GridBagConstraints.HORIZONTAL;
		gbc_comboStatisticsMode.gridx = 1;
		gbc_comboStatisticsMode.gridy = 7;
		contentPane.add(comboStatisticsMode, gbc_comboStatisticsMode);

		lblBackgroundLevel = new JLabel("Background Level");
		GridBagConstraints gbc_lblBackgroundLevel = new GridBagConstraints();
		gbc_lblBackgroundLevel.anchor = GridBagConstraints.EAST;
		gbc_lblBackgroundLevel.insets = new Insets(0, 0, 5, 5);
		gbc_lblBackgroundLevel.gridx = 0;
		gbc_lblBackgroundLevel.gridy = 8;
		contentPane.add(lblBackgroundLevel, gbc_lblBackgroundLevel);

		lblBackgroundLevelValue = new JLabel("0");
		GridBagConstraints gbc_lblBackgroundLevelValue = new GridBagConstraints();
		gbc_lblBackgroundLevelValue.anchor = GridBagConstraints.WEST;
		gbc_lblBackgroundLevelValue.insets = new Insets(0, 0, 5, 5);
		gbc_lblBackgroundLevelValue.gridx = 1;
		gbc_lblBackgroundLevelValue.gridy = 8;
		contentPane.add(lblBackgroundLevelValue, gbc_lblBackgroundLevelValue);

		separator_1 = new JSeparator();
		GridBagConstraints gbc_separator_1 = new GridBagConstraints();
		gbc_separator_1.insets = new Insets(0, 0, 15, 5);
		gbc_separator_1.gridx = 1;
		gbc_separator_1.gridy = 9;
		contentPane.add(separator_1, gbc_separator_1);

		lblSearchMethod = new JLabel("Search method");
		GridBagConstraints gbc_lblSearchMethod = new GridBagConstraints();
		gbc_lblSearchMethod.anchor = GridBagConstraints.EAST;
		gbc_lblSearchMethod.insets = new Insets(0, 0, 5, 5);
		gbc_lblSearchMethod.gridx = 0;
		gbc_lblSearchMethod.gridy = 10;
		contentPane.add(lblSearchMethod, gbc_lblSearchMethod);

		comboSearchMethod = new JComboBox<String>();
		comboSearchMethod.setToolTipText("Specify the method used to expand maxima into peaks");
		comboSearchMethod.addItemListener(new ItemListener()
		{
			public void itemStateChanged(ItemEvent e)
			{
				comboSearchMethod.firePropertyChange("selectedItem", 0, 1);
			}
		});
		comboSearchMethod.setModel(new DefaultComboBoxModel<String>(FindFoci.searchMethods));
		comboSearchMethod.setSelectedIndex(0);
		GridBagConstraints gbc_comboSearchMethod = new GridBagConstraints();
		gbc_comboSearchMethod.fill = GridBagConstraints.HORIZONTAL;
		gbc_comboSearchMethod.gridwidth = 2;
		gbc_comboSearchMethod.insets = new Insets(0, 0, 5, 0);
		gbc_comboSearchMethod.gridx = 1;
		gbc_comboSearchMethod.gridy = 10;
		contentPane.add(comboSearchMethod, gbc_comboSearchMethod);

		lblSearchParam = new JLabel("Search param");
		GridBagConstraints gbc_lblSearchParam = new GridBagConstraints();
		gbc_lblSearchParam.anchor = GridBagConstraints.EAST;
		gbc_lblSearchParam.insets = new Insets(0, 0, 5, 5);
		gbc_lblSearchParam.gridx = 0;
		gbc_lblSearchParam.gridy = 11;
		contentPane.add(lblSearchParam, gbc_lblSearchParam);

		sliderSearchParam = new JSlider();
		sliderSearchParam.setToolTipText("Controls the selected search method");
		sliderSearchParam.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				if (e.getClickCount() > 1)
				{
					SliderLimitHelper.updateRangeLimits(sliderSearchParam, "Search parameter",
							SliderConverter.SCALE_FACTOR, 0, 1);
				}
			}
		});
		sliderSearchParam.addChangeListener(new ChangeListener()
		{
			public void stateChanged(ChangeEvent e)
			{
				sliderSearchParam.firePropertyChange("value", 0, 1);
			}
		});
		sliderSearchParam.setMaximum(1000);
		sliderSearchParam.setValue((int) (0.3 * sliderSearchParam.getMaximum()));
		sliderSearchParam.setMinorTickSpacing(50);
		sliderSearchParam.setMajorTickSpacing(100);
		GridBagConstraints gbc_sliderSearchParam = new GridBagConstraints();
		gbc_sliderSearchParam.fill = GridBagConstraints.HORIZONTAL;
		gbc_sliderSearchParam.insets = new Insets(0, 0, 5, 5);
		gbc_sliderSearchParam.gridx = 1;
		gbc_sliderSearchParam.gridy = 11;
		contentPane.add(sliderSearchParam, gbc_sliderSearchParam);

		txtSearchParam = new JFormattedTextField(new LimitedNumberFormat(0, 1));
		txtSearchParam.addPropertyChangeListener(new PropertyChangeListener()
		{
			public void propertyChange(PropertyChangeEvent evt)
			{
				if (evt.getPropertyName() == "value")
				{
					txtSearchParam.firePropertyChange("text", 0, 1);
				}
			}
		});
		txtSearchParam.addKeyListener(new KeyAdapter()
		{
			@Override
			public void keyReleased(KeyEvent e)
			{
				txtSearchParam.firePropertyChange("text", 0, 1);
			}
		});
		txtSearchParam.setHorizontalAlignment(SwingConstants.RIGHT);
		txtSearchParam.setText("0");
		GridBagConstraints gbc_txtSearchParam = new GridBagConstraints();
		gbc_txtSearchParam.insets = new Insets(0, 0, 5, 0);
		gbc_txtSearchParam.fill = GridBagConstraints.HORIZONTAL;
		gbc_txtSearchParam.gridx = 2;
		gbc_txtSearchParam.gridy = 11;
		contentPane.add(txtSearchParam, gbc_txtSearchParam);

		separator_2 = new JSeparator();
		GridBagConstraints gbc_separator_2 = new GridBagConstraints();
		gbc_separator_2.insets = new Insets(0, 0, 15, 5);
		gbc_separator_2.gridx = 1;
		gbc_separator_2.gridy = 12;
		contentPane.add(separator_2, gbc_separator_2);

		lblMinimumSize = new JLabel("Minimum size");
		GridBagConstraints gbc_lblMinimumSize = new GridBagConstraints();
		gbc_lblMinimumSize.anchor = GridBagConstraints.EAST;
		gbc_lblMinimumSize.insets = new Insets(0, 0, 5, 5);
		gbc_lblMinimumSize.gridx = 0;
		gbc_lblMinimumSize.gridy = 15;
		contentPane.add(lblMinimumSize, gbc_lblMinimumSize);

		sliderMinimumSize = new JSlider();
		sliderMinimumSize.setMinimum(1);
		sliderMinimumSize.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				if (e.getClickCount() > 1)
				{
					SliderLimitHelper.updateRangeLimits(sliderMinimumSize, "Minimum size", 1, 0,
							Double.POSITIVE_INFINITY);
				}
			}
		});
		sliderMinimumSize.setToolTipText("The minimum size required to define a peak");
		sliderMinimumSize.addChangeListener(new ChangeListener()
		{
			public void stateChanged(ChangeEvent e)
			{
				sliderMinimumSize.firePropertyChange("value", 0, 1);
			}
		});
		sliderMinimumSize.setValue(5);
		sliderMinimumSize.setMaximum(100);
		GridBagConstraints gbc_sliderMinimumSize = new GridBagConstraints();
		gbc_sliderMinimumSize.fill = GridBagConstraints.HORIZONTAL;
		gbc_sliderMinimumSize.insets = new Insets(0, 0, 5, 5);
		gbc_sliderMinimumSize.gridx = 1;
		gbc_sliderMinimumSize.gridy = 15;
		contentPane.add(sliderMinimumSize, gbc_sliderMinimumSize);

		txtMinimumSize = new JFormattedTextField(new LimitedNumberFormat(0));
		txtMinimumSize.addPropertyChangeListener(new PropertyChangeListener()
		{
			public void propertyChange(PropertyChangeEvent evt)
			{
				if (evt.getPropertyName() == "value")
				{
					txtMinimumSize.firePropertyChange("text", 0, 1);
				}
			}
		});
		txtMinimumSize.addKeyListener(new KeyAdapter()
		{
			@Override
			public void keyReleased(KeyEvent e)
			{
				txtMinimumSize.firePropertyChange("text", 0, 1);
			}
		});
		txtMinimumSize.setHorizontalAlignment(SwingConstants.RIGHT);
		txtMinimumSize.setText("0");
		GridBagConstraints gbc_txtMinimumSize = new GridBagConstraints();
		gbc_txtMinimumSize.insets = new Insets(0, 0, 5, 0);
		gbc_txtMinimumSize.fill = GridBagConstraints.HORIZONTAL;
		gbc_txtMinimumSize.gridx = 2;
		gbc_txtMinimumSize.gridy = 15;
		contentPane.add(txtMinimumSize, gbc_txtMinimumSize);

		chckbxMinSizeAboveSaddle = new JCheckBox("Minimum size above saddle");
		chckbxMinSizeAboveSaddle.setToolTipText("Restrict minimum size to the peak volume above the highest saddle point");
		chckbxMinSizeAboveSaddle.addItemListener(new ItemListener()
		{
			public void itemStateChanged(ItemEvent e)
			{
				chckbxMinSizeAboveSaddle.firePropertyChange("selected", 0, 1);
			}
		});
		chckbxMinSizeAboveSaddle.setSelected(true);
		GridBagConstraints gbc_chckbxMinSizeAboveSaddle = new GridBagConstraints();
		gbc_chckbxMinSizeAboveSaddle.gridwidth = 2;
		gbc_chckbxMinSizeAboveSaddle.anchor = GridBagConstraints.WEST;
		gbc_chckbxMinSizeAboveSaddle.insets = new Insets(0, 0, 5, 0);
		gbc_chckbxMinSizeAboveSaddle.gridx = 1;
		gbc_chckbxMinSizeAboveSaddle.gridy = 16;
		contentPane.add(chckbxMinSizeAboveSaddle, gbc_chckbxMinSizeAboveSaddle);

		chckbxConnectedAboveSaddle = new JCheckBox("Connected above saddle");
		chckbxConnectedAboveSaddle.setToolTipText("The peak volume above the highest saddle point must be connected pixels");
		chckbxConnectedAboveSaddle.addItemListener(new ItemListener()
		{
			public void itemStateChanged(ItemEvent e)
			{
				chckbxConnectedAboveSaddle.firePropertyChange("selected", 0, 1);
			}
		});
		GridBagConstraints gbc_chckbxConnectedAboveSaddle = new GridBagConstraints();
		gbc_chckbxConnectedAboveSaddle.gridwidth = 2;
		gbc_chckbxConnectedAboveSaddle.anchor = GridBagConstraints.WEST;
		gbc_chckbxConnectedAboveSaddle.insets = new Insets(0, 0, 5, 0);
		gbc_chckbxConnectedAboveSaddle.gridx = 1;
		gbc_chckbxConnectedAboveSaddle.gridy = 17;
		contentPane.add(chckbxConnectedAboveSaddle, gbc_chckbxConnectedAboveSaddle);
		
		lblPeakMethod = new JLabel("Peak method");
		GridBagConstraints gbc_lblPeakMethod = new GridBagConstraints();
		gbc_lblPeakMethod.anchor = GridBagConstraints.EAST;
		gbc_lblPeakMethod.insets = new Insets(0, 0, 5, 5);
		gbc_lblPeakMethod.gridx = 0;
		gbc_lblPeakMethod.gridy = 13;
		contentPane.add(lblPeakMethod, gbc_lblPeakMethod);

		comboPeakMethod = new JComboBox<String>();
		comboPeakMethod.setToolTipText("Specify the required height for a peak");
		comboPeakMethod.addItemListener(new ItemListener()
		{
			public void itemStateChanged(ItemEvent e)
			{
				comboPeakMethod.firePropertyChange("selectedItem", 0, 1);
			}
		});
		comboPeakMethod.setModel(new DefaultComboBoxModel<String>(FindFoci.peakMethods));
		comboPeakMethod.setSelectedIndex(2);
		GridBagConstraints gbc_comboPeakMethod = new GridBagConstraints();
		gbc_comboPeakMethod.gridwidth = 2;
		gbc_comboPeakMethod.insets = new Insets(0, 0, 5, 0);
		gbc_comboPeakMethod.fill = GridBagConstraints.HORIZONTAL;
		gbc_comboPeakMethod.gridx = 1;
		gbc_comboPeakMethod.gridy = 13;
		contentPane.add(comboPeakMethod, gbc_comboPeakMethod);

		lblPeakParam = new JLabel("Peak param");
		GridBagConstraints gbc_lblPeakParam = new GridBagConstraints();
		gbc_lblPeakParam.anchor = GridBagConstraints.EAST;
		gbc_lblPeakParam.insets = new Insets(0, 0, 5, 5);
		gbc_lblPeakParam.gridx = 0;
		gbc_lblPeakParam.gridy = 14;
		contentPane.add(lblPeakParam, gbc_lblPeakParam);

		sliderPeakParam = new JSlider();
		sliderPeakParam.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				if (e.getClickCount() > 1)
				{
					SliderLimitHelper.updateRangeLimits(sliderPeakParam, "Peak parameter", SliderConverter.SCALE_FACTOR,
							0, 1);
				}
			}
		});
		sliderPeakParam.setToolTipText("Controls the selected peak method");
		sliderPeakParam.addChangeListener(new ChangeListener()
		{
			public void stateChanged(ChangeEvent e)
			{
				if (model.getPeakMethod() != FindFoci.PEAK_ABSOLUTE)
				{
					sliderPeakParam.firePropertyChange("value", 0, 1);
				}
			}
		});
		sliderPeakParam.setMinorTickSpacing(50);
		sliderPeakParam.setMajorTickSpacing(100);
		sliderPeakParam.setMaximum(1000);
		sliderPeakParam.setValue((int) (0.5 * sliderPeakParam.getMaximum()));
		GridBagConstraints gbc_sliderPeakRaram = new GridBagConstraints();
		gbc_sliderPeakRaram.fill = GridBagConstraints.HORIZONTAL;
		gbc_sliderPeakRaram.insets = new Insets(0, 0, 5, 5);
		gbc_sliderPeakRaram.gridx = 1;
		gbc_sliderPeakRaram.gridy = 14;
		contentPane.add(sliderPeakParam, gbc_sliderPeakRaram);

		sliderPeakParamAbsolute = new JSlider();
		sliderPeakParamAbsolute.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				if (e.getClickCount() > 1)
				{
					SliderLimitHelper.updateRangeLimits(sliderPeakParamAbsolute, "Peak parameter", 1, 0,
							Double.POSITIVE_INFINITY);
				}
			}
		});
		sliderPeakParamAbsolute.addChangeListener(new ChangeListener()
		{
			public void stateChanged(ChangeEvent e)
			{
				if (model.getPeakMethod() == FindFoci.PEAK_ABSOLUTE)
				{
					sliderPeakParamAbsolute.firePropertyChange("value", 0, 1);
				}
			}
		});
		sliderPeakParamAbsolute.setMinorTickSpacing(50);
		sliderPeakParamAbsolute.setMajorTickSpacing(100);
		sliderPeakParamAbsolute.setMaximum(1000);
		sliderPeakParamAbsolute.setValue(0);
		GridBagConstraints gbc_sliderPeakParamAbsolute = new GridBagConstraints();
		gbc_sliderPeakParamAbsolute.fill = GridBagConstraints.HORIZONTAL;
		gbc_sliderPeakParamAbsolute.insets = new Insets(0, 0, 5, 5);
		gbc_sliderPeakParamAbsolute.gridx = 1;
		gbc_sliderPeakParamAbsolute.gridy = 14;
		contentPane.add(sliderPeakParamAbsolute, gbc_sliderPeakParamAbsolute);

		txtPeakParam = new JFormattedTextField(new LimitedNumberFormat(0));
		txtPeakParam.addPropertyChangeListener(new PropertyChangeListener()
		{
			public void propertyChange(PropertyChangeEvent evt)
			{
				if (evt.getPropertyName() == "value")
				{
					txtPeakParam.firePropertyChange("text", 0, 1);
				}
			}
		});
		txtPeakParam.addKeyListener(new KeyAdapter()
		{
			@Override
			public void keyReleased(KeyEvent e)
			{
				txtPeakParam.firePropertyChange("text", 0, 1);
			}
		});
		txtPeakParam.setText("0");
		txtPeakParam.setHorizontalAlignment(SwingConstants.RIGHT);
		GridBagConstraints gbc_txtPeakParam = new GridBagConstraints();
		gbc_txtPeakParam.insets = new Insets(0, 0, 5, 0);
		gbc_txtPeakParam.fill = GridBagConstraints.HORIZONTAL;
		gbc_txtPeakParam.gridx = 2;
		gbc_txtPeakParam.gridy = 14;
		contentPane.add(txtPeakParam, gbc_txtPeakParam);

		separator_4 = new JSeparator();
		GridBagConstraints gbc_separator_4 = new GridBagConstraints();
		gbc_separator_4.insets = new Insets(0, 0, 15, 5);
		gbc_separator_4.gridx = 1;
		gbc_separator_4.gridy = 18;
		contentPane.add(separator_4, gbc_separator_4);

		lblSortMethod = new JLabel("Sort method");
		GridBagConstraints gbc_lblSortMethod = new GridBagConstraints();
		gbc_lblSortMethod.anchor = GridBagConstraints.EAST;
		gbc_lblSortMethod.insets = new Insets(0, 0, 5, 5);
		gbc_lblSortMethod.gridx = 0;
		gbc_lblSortMethod.gridy = 19;
		contentPane.add(lblSortMethod, gbc_lblSortMethod);

		comboSortMethod = new JComboBox<String>();
		comboSortMethod.setToolTipText("Metric used to sort the peaks");
		comboSortMethod.addItemListener(new ItemListener()
		{
			public void itemStateChanged(ItemEvent e)
			{
				comboSortMethod.firePropertyChange("selectedItem", 0, 1);
			}
		});
		GridBagConstraints gbc_comboSortMethod = new GridBagConstraints();
		gbc_comboSortMethod.gridwidth = 2;
		gbc_comboSortMethod.insets = new Insets(0, 0, 5, 0);
		comboSortMethod.setModel(new DefaultComboBoxModel<String>(FindFoci.sortIndexMethods));
		comboSortMethod.setSelectedIndex(1);
		gbc_comboSortMethod.fill = GridBagConstraints.HORIZONTAL;
		gbc_comboSortMethod.gridx = 1;
		gbc_comboSortMethod.gridy = 19;
		contentPane.add(comboSortMethod, gbc_comboSortMethod);

		lblMaxPeaks = new JLabel("Max peaks");
		GridBagConstraints gbc_lblMaxPeaks = new GridBagConstraints();
		gbc_lblMaxPeaks.anchor = GridBagConstraints.EAST;
		gbc_lblMaxPeaks.insets = new Insets(0, 0, 5, 5);
		gbc_lblMaxPeaks.gridx = 0;
		gbc_lblMaxPeaks.gridy = 20;
		contentPane.add(lblMaxPeaks, gbc_lblMaxPeaks);

		sliderMaxPeaks = new JSlider();
		sliderMaxPeaks.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				if (e.getClickCount() > 1)
				{
					SliderLimitHelper.updateRangeLimits(sliderMaxPeaks, "Maximum peaks", 1, 1,
							Double.POSITIVE_INFINITY);
				}
			}
		});
		sliderMaxPeaks.setToolTipText("Specify the maximum number of peaks");
		sliderMaxPeaks.addChangeListener(new ChangeListener()
		{
			public void stateChanged(ChangeEvent e)
			{
				sliderMaxPeaks.firePropertyChange("value", 0, 1);
			}
		});
		sliderMaxPeaks.setMinimum(1);
		sliderMaxPeaks.setMaximum(1000);
		sliderMaxPeaks.setValue(50);
		GridBagConstraints gbc_sliderMaxPeaks = new GridBagConstraints();
		gbc_sliderMaxPeaks.fill = GridBagConstraints.HORIZONTAL;
		gbc_sliderMaxPeaks.insets = new Insets(0, 0, 5, 5);
		gbc_sliderMaxPeaks.gridx = 1;
		gbc_sliderMaxPeaks.gridy = 20;
		contentPane.add(sliderMaxPeaks, gbc_sliderMaxPeaks);

		txtMaxPeaks = new JFormattedTextField(new LimitedNumberFormat(1));
		txtMaxPeaks.addPropertyChangeListener(new PropertyChangeListener()
		{
			public void propertyChange(PropertyChangeEvent evt)
			{
				if (evt.getPropertyName() == "value")
				{
					txtMaxPeaks.firePropertyChange("text", 0, 1);
				}
			}
		});
		txtMaxPeaks.addKeyListener(new KeyAdapter()
		{
			@Override
			public void keyReleased(KeyEvent e)
			{
				txtMaxPeaks.firePropertyChange("text", 0, 1);
			}
		});
		txtMaxPeaks.setText("0");
		txtMaxPeaks.setHorizontalAlignment(SwingConstants.RIGHT);
		GridBagConstraints gbc_txtMaxPeaks = new GridBagConstraints();
		gbc_txtMaxPeaks.insets = new Insets(0, 0, 5, 0);
		gbc_txtMaxPeaks.fill = GridBagConstraints.HORIZONTAL;
		gbc_txtMaxPeaks.gridx = 2;
		gbc_txtMaxPeaks.gridy = 20;
		contentPane.add(txtMaxPeaks, gbc_txtMaxPeaks);

		lblShowMask = new JLabel("Show mask");
		GridBagConstraints gbc_lblShowMask = new GridBagConstraints();
		gbc_lblShowMask.anchor = GridBagConstraints.EAST;
		gbc_lblShowMask.insets = new Insets(0, 0, 5, 5);
		gbc_lblShowMask.gridx = 0;
		gbc_lblShowMask.gridy = 21;
		contentPane.add(lblShowMask, gbc_lblShowMask);

		comboShowMask = new JComboBox<String>();
		comboShowMask.setToolTipText("Configure the output mask");
		comboShowMask.addItemListener(new ItemListener()
		{
			public void itemStateChanged(ItemEvent e)
			{
				comboShowMask.firePropertyChange("selectedItem", 0, 1);
			}
		});
		comboShowMask.setModel(new DefaultComboBoxModel<String>(FindFoci.maskOptions));
		comboShowMask.setSelectedIndex(3);
		GridBagConstraints gbc_comboShowMask = new GridBagConstraints();
		gbc_comboShowMask.gridwidth = 2;
		gbc_comboShowMask.fill = GridBagConstraints.HORIZONTAL;
		gbc_comboShowMask.insets = new Insets(0, 0, 5, 0);
		gbc_comboShowMask.gridx = 1;
		gbc_comboShowMask.gridy = 21;
		contentPane.add(comboShowMask, gbc_comboShowMask);

		lblFractionParam = new JLabel("Fraction param");
		GridBagConstraints gbc_lblFractionParam = new GridBagConstraints();
		gbc_lblFractionParam.anchor = GridBagConstraints.EAST;
		gbc_lblFractionParam.insets = new Insets(0, 0, 5, 5);
		gbc_lblFractionParam.gridx = 0;
		gbc_lblFractionParam.gridy = 22;
		contentPane.add(lblFractionParam, gbc_lblFractionParam);

		sliderFractionParam = new JSlider();
		sliderFractionParam.addChangeListener(new ChangeListener()
		{
			public void stateChanged(ChangeEvent e)
			{
				sliderFractionParam.firePropertyChange("value", 0, 1);
			}
		});
		sliderFractionParam.setMinimum(1);
		sliderFractionParam.setMaximum(1000);
		GridBagConstraints gbc_sliderFractionParam = new GridBagConstraints();
		gbc_sliderFractionParam.fill = GridBagConstraints.HORIZONTAL;
		gbc_sliderFractionParam.insets = new Insets(0, 0, 5, 5);
		gbc_sliderFractionParam.gridx = 1;
		gbc_sliderFractionParam.gridy = 22;
		contentPane.add(sliderFractionParam, gbc_sliderFractionParam);

		txtFractionParam = new JTextField();
		txtFractionParam.addPropertyChangeListener(new PropertyChangeListener()
		{
			public void propertyChange(PropertyChangeEvent evt)
			{
				if (evt.getPropertyName() == "value")
				{
					txtMaxPeaks.firePropertyChange("text", 0, 1);
				}
			}
		});
		txtFractionParam.addKeyListener(new KeyAdapter()
		{
			@Override
			public void keyReleased(KeyEvent e)
			{
				txtFractionParam.firePropertyChange("text", 0, 1);
			}
		});
		txtFractionParam.setHorizontalAlignment(SwingConstants.TRAILING);
		txtFractionParam.setText("0");
		GridBagConstraints gbc_txtFFractionParam = new GridBagConstraints();
		gbc_txtFFractionParam.insets = new Insets(0, 0, 5, 0);
		gbc_txtFFractionParam.fill = GridBagConstraints.HORIZONTAL;
		gbc_txtFFractionParam.gridx = 2;
		gbc_txtFFractionParam.gridy = 22;
		contentPane.add(txtFractionParam, gbc_txtFFractionParam);
		txtFractionParam.setColumns(10);

		separator_3 = new JSeparator();
		GridBagConstraints gbc_separator_3 = new GridBagConstraints();
		gbc_separator_3.insets = new Insets(0, 0, 15, 5);
		gbc_separator_3.gridx = 1;
		gbc_separator_3.gridy = 23;
		contentPane.add(separator_3, gbc_separator_3);

		panel = new JPanel();
		GridBagConstraints gbc_panel = new GridBagConstraints();
		gbc_panel.anchor = GridBagConstraints.NORTH;
		gbc_panel.gridwidth = 3;
		gbc_panel.fill = GridBagConstraints.HORIZONTAL;
		gbc_panel.gridx = 0;
		gbc_panel.gridy = 24;
		contentPane.add(panel, gbc_panel);

		btnAdvancedOptions = new JButton("Advanced options ...");
		btnAdvancedOptions.setToolTipText("Show additional options");
		btnAdvancedOptions.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				if (options == null || !options.isVisible())
				{
					options = new FindFociAdvancedOptions(model);
					options.setTitle("FindFoci Options");
					options.setVisible(true);
				}
				else
				{
					options.toFront();
				}
			}
		});

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
		panel.add(btnHelp);
		panel.add(btnAdvancedOptions);

		btnRun = new JButton("Run");
		btnRun.setToolTipText("Runs the algorithm");
		panel.add(btnRun);

		chckbxPreview = new JCheckBox("Preview");
		chckbxPreview.setToolTipText("Update results dynamically (requires more memory)");
		chckbxPreview.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent e)
			{
				if (chckbxPreview.isSelected())
				{
					//System.out.println("preview button controller.startPreview()");
					startPreview();
				}
				else
				{
					//System.out.println("preview button controller.endPreview()");
					endPreview();
				}
			}
		});
		panel.add(chckbxPreview);
		btnRun.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent e)
			{
				// Run in a new thread to allow updates to the IJ progress bar
				Thread thread = new Thread(controller);
				thread.start();
			}
		});
		initDataBindings();

		// Fixes resizing on MacOS
		Dimension separatorDim = new Dimension(0, 1);
		separator.setPreferredSize(separatorDim);
		separator_1.setPreferredSize(separatorDim);
		separator_2.setPreferredSize(separatorDim);
		separator_3.setPreferredSize(separatorDim);
		separator_4.setPreferredSize(separatorDim);
		
		this.pack();
	}

	private void startPreview()
	{
		//System.out.println("startPreview()");
		model.addPropertyChangeListener(instance);
		controller.preview();
	}

	private void endPreview()
	{
		//System.out.println("endPreview()");
		model.removePropertyChangeListener(instance);
		controller.endPreview();
	}

	/**
	 * @param validImages
	 *            the validImages to set
	 */
	public void setValidImages(boolean validImages)
	{
		this.validImages = validImages;
		//setRunEnabled(validImages && changed);
		// Allow the Run button to be enabled even when the model has not changed
		setRunEnabled(validImages);
	}

	/**
	 * @return the validImages
	 */
	public boolean isValidImages()
	{
		return validImages;
	}

	/**
	 * @param changed
	 *            the changed to set
	 */
	public void setChanged(boolean changed)
	{
		this.changed = changed;
		// Note: The model 'changed' property is bound to this property using data bindings.
		// So when the model is set to unchanged then the run button is disabled. Disable 
		// this behaviour allowing the Run button to always be enabled when the images are valid.
		//setRunEnabled(validImages && changed);
	}

	/**
	 * @return the changed
	 */
	public boolean isChanged()
	{
		return changed;
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

		//		if (runEnabled && chckbxPreview.isSelected())
		//		{
		//			System.out.println("setRunEnabled controller.preview()");
		//			controller.preview();
		//		}
	}

	/**
	 * @return the background level
	 */
	public double getBackgroundLevel()
	{
		return backgroundLevel;
	}

	/**
	 * @param backgroundLevel
	 */
	public void setBackgroundLevel(double backgroundLevel)
	{
		double oldValue = this.backgroundLevel;
		this.backgroundLevel = backgroundLevel;
		this.firePropertyChange("backgroundLevel", oldValue, backgroundLevel);
	}

	/**
	 * @return True if the sort index will be a problem
	 */
	public boolean isSortIndexError()
	{
		return sortIndexError;
	}

	/**
	 * @param sortIndexError
	 *            True if the sort index will be a problem
	 */
	public void setSortIndexError(boolean sortIndexError)
	{
		boolean oldValue = this.sortIndexError;
		this.sortIndexError = sortIndexError;
		this.firePropertyChange("sortIndexError", oldValue, sortIndexError);

		if (sortIndexError)
		{
			if (oldSortIndex != model.getSortMethod())
				IJ.log("WARNING: Image minimum is below zero and the chosen sort index is sensitive to negative values: " +
						FindFoci.sortIndexMethods[model.getSortMethod()]);
			oldSortIndex = model.getSortMethod();
		}
		else
		{
			oldSortIndex = -1;
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
	 * @return The image minimum value
	 */
	public int getImageMinimum()
	{
		return limits[0];
	}

	/**
	 * @return The image maximum value
	 */
	public int getImageMaximum()
	{
		return limits[1];
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.beans.PropertyChangeListener#propertyChange(java.beans.PropertyChangeEvent)
	 */
	public void propertyChange(PropertyChangeEvent evt)
	{
		if (evt.getPropertyName().equals("selectedImage") || evt.getPropertyName().equals("valid"))
		{
			endPreview();
			chckbxPreview.setSelected(false);
			controller.updateImageList();
			updateImageLimits();
		}
		else if (!evt.getPropertyName().equals("changed"))
		{
			// Just in case the preview has been disabled
			if (chckbxPreview.isSelected())
				controller.preview();
		}
	}

	private void updateImageLimits()
	{
		// Update the limits on the background param absolute slider
		int oldMin = limits[0];
		int oldMax = limits[1];
		double backgroundParam = model.getBackgroundParameter();
		controller.getImageLimits(limits);
		firePropertyChange("imageMaximum", oldMax, limits[1]);
		firePropertyChange("imageMinimum", oldMin, limits[0]);
		// When the slider limits are updated this can cause clipping of the value
		// which is propagated to the model. Ensure the actual value in the model is not changed.
		model.setBackgroundParameter(backgroundParam);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see gdsc.foci.gui.MessageListener#notify(java.lang.String, java.lang.Object[])
	 */
	public void notify(MessageType message, Object... params)
	{
		switch (message)
		{
			case BACKGROUND_LEVEL:
				setBackgroundLevel((Float) params[0]);
				break;

			case SORT_INDEX_OK:
				setSortIndexError(false);
				break;

			case SORT_INDEX_SENSITIVE_TO_NEGATIVE_VALUES:
				setSortIndexError(true);
				break;

			case ERROR:
				// This is done when the runner had an error that prevents any further calculations
				chckbxPreview.setSelected(false);
				// Fall-through to reset the foreground

			case FINISHED:
				// This is done when the runner has been shutdown for further calculations
				// Fall-through to reset the foreground

			case READY:
				// This is done when the runner is OK to calculate
				chckbxPreview.setForeground(Color.BLACK);
				break;

			case RUNNING:
				// This is done when the runner is calculating
				chckbxPreview.setForeground(Color.YELLOW);
				break;

			case DONE:
				// This is done when the runner finished calculating
				chckbxPreview.setForeground(Color.GREEN);
				break;

			case FAILED:
				// This is done when the runner had an error during the calculation
				chckbxPreview.setForeground(Color.RED);
				break;

			default:
				// Do nothing 
		}
	}
	@SuppressWarnings("rawtypes")
	protected void initDataBindings() {
		BeanProperty<FindFociModel, Double> findFociBeanProperty_1 = BeanProperty.create("searchParameter");
		BeanProperty<JSlider, Integer> jSliderBeanProperty_1 = BeanProperty.create("value");
		AutoBinding<FindFociModel, Double, JSlider, Integer> autoBinding_2 = Bindings.createAutoBinding(UpdateStrategy.READ_WRITE, model, findFociBeanProperty_1, sliderSearchParam, jSliderBeanProperty_1);
		autoBinding_2.setConverter(new SliderConverter());
		autoBinding_2.bind();
		//
		BeanProperty<FindFociModel, Double> findFociBeanProperty_2 = BeanProperty.create("peakParameter");
		BeanProperty<JSlider, Integer> jSliderBeanProperty_2 = BeanProperty.create("value");
		AutoBinding<FindFociModel, Double, JSlider, Integer> autoBinding_5 = Bindings.createAutoBinding(UpdateStrategy.READ_WRITE, model, findFociBeanProperty_2, sliderPeakParam, jSliderBeanProperty_2);
		autoBinding_5.setConverter(new SliderConverter());
		autoBinding_5.bind();
		//
		BeanProperty<FindFociModel, Integer> findFociBeanProperty_3 = BeanProperty.create("maxPeaks");
		BeanProperty<JSlider, Integer> jSliderBeanProperty_3 = BeanProperty.create("value");
		AutoBinding<FindFociModel, Integer, JSlider, Integer> autoBinding_6 = Bindings.createAutoBinding(UpdateStrategy.READ_WRITE, model, findFociBeanProperty_3, sliderMaxPeaks, jSliderBeanProperty_3);
		autoBinding_6.bind();
		//
		BeanProperty<FindFociModel, Integer> findFociBeanProperty_4 = BeanProperty.create("backgroundMethod");
		BeanProperty<JComboBox, Object> jComboBoxBeanProperty = BeanProperty.create("selectedItem");
		AutoBinding<FindFociModel, Integer, JComboBox, Object> autoBinding_8 = Bindings.createAutoBinding(UpdateStrategy.READ_WRITE, model, findFociBeanProperty_4, comboBackgroundMethod, jComboBoxBeanProperty);
		autoBinding_8.setConverter(new BackgroundMethodConverter());
		autoBinding_8.bind();
		//
		BeanProperty<JLabel, Boolean> jLabelBeanProperty_1 = BeanProperty.create("enabled");
		AutoBinding<FindFociModel, Integer, JLabel, Boolean> autoBinding_10 = Bindings.createAutoBinding(UpdateStrategy.READ, model, findFociBeanProperty_4, lblBackgroundParam, jLabelBeanProperty_1);
		autoBinding_10.setConverter(new BackgroundParamEnabledConverter());
		autoBinding_10.bind();
		//
		BeanProperty<FindFociModel, Integer> findFociBeanProperty_5 = BeanProperty.create("searchMethod");
		BeanProperty<JSlider, Boolean> jSliderBeanProperty_4 = BeanProperty.create("enabled");
		AutoBinding<FindFociModel, Integer, JSlider, Boolean> autoBinding_11 = Bindings.createAutoBinding(UpdateStrategy.READ, model, findFociBeanProperty_5, sliderSearchParam, jSliderBeanProperty_4);
		autoBinding_11.setConverter(new SearchParamEnabledConverter());
		autoBinding_11.bind();
		//
		AutoBinding<FindFociModel, Integer, JComboBox, Object> autoBinding_12 = Bindings.createAutoBinding(UpdateStrategy.READ_WRITE, model, findFociBeanProperty_5, comboSearchMethod, jComboBoxBeanProperty);
		autoBinding_12.setConverter(new SearchMethodConverter());
		autoBinding_12.bind();
		//
		AutoBinding<FindFociModel, Integer, JLabel, Boolean> autoBinding_14 = Bindings.createAutoBinding(UpdateStrategy.READ, model, findFociBeanProperty_5, lblSearchParam, jLabelBeanProperty_1);
		autoBinding_14.setConverter(new SearchParamEnabledConverter());
		autoBinding_14.bind();
		//
		BeanProperty<FindFociModel, String> findFociBeanProperty_6 = BeanProperty.create("thresholdMethod");
		AutoBinding<FindFociModel, String, JComboBox, Object> autoBinding_15 = Bindings.createAutoBinding(UpdateStrategy.READ_WRITE, model, findFociBeanProperty_6, comboThresholdMethod, jComboBoxBeanProperty);
		autoBinding_15.bind();
		//
		BeanProperty<FindFociModel, Integer> findFociBeanProperty_7 = BeanProperty.create("sortMethod");
		AutoBinding<FindFociModel, Integer, JComboBox, Object> autoBinding_16 = Bindings.createAutoBinding(UpdateStrategy.READ_WRITE, model, findFociBeanProperty_7, comboSortMethod, jComboBoxBeanProperty);
		autoBinding_16.setConverter(new SortMethodConverter());
		autoBinding_16.bind();
		//
		BeanProperty<FindFociModel, Integer> findFociBeanProperty_8 = BeanProperty.create("showMask");
		AutoBinding<FindFociModel, Integer, JComboBox, Object> autoBinding_17 = Bindings.createAutoBinding(UpdateStrategy.READ_WRITE, model, findFociBeanProperty_8, comboShowMask, jComboBoxBeanProperty);
		autoBinding_17.setConverter(new ShowMaskConverter());
		autoBinding_17.bind();
		//
		BeanProperty<FindFociModel, Double> findFociBeanProperty_9 = BeanProperty.create("gaussianBlur");
		BeanProperty<JSlider, Integer> jSliderBeanProperty_5 = BeanProperty.create("value");
		AutoBinding<FindFociModel, Double, JSlider, Integer> autoBinding_9 = Bindings.createAutoBinding(UpdateStrategy.READ_WRITE, model, findFociBeanProperty_9, sliderGaussianBlur, jSliderBeanProperty_5);
		autoBinding_9.setConverter(new SliderConverter());
		autoBinding_9.bind();
		//
		AutoBinding<FindFociModel, Integer, JSlider, Boolean> autoBinding_19 = Bindings.createAutoBinding(UpdateStrategy.READ, model, findFociBeanProperty_4, sliderBackgroundParam, jSliderBeanProperty_4);
		autoBinding_19.setConverter(new BackgroundParamEnabledConverter());
		autoBinding_19.bind();
		//
		BeanProperty<FindFociModel, Double> findFociBeanProperty_10 = BeanProperty.create("backgroundParameter");
		BeanProperty<JSlider, Integer> jSliderBeanProperty_6 = BeanProperty.create("value");
		AutoBinding<FindFociModel, Double, JSlider, Integer> autoBinding_22 = Bindings.createAutoBinding(UpdateStrategy.READ_WRITE, model, findFociBeanProperty_10, sliderBackgroundParam, jSliderBeanProperty_6);
		autoBinding_22.setConverter(new SliderConverter());
		autoBinding_22.bind();
		//
		BeanProperty<JSlider, Integer> jSliderBeanProperty_7 = BeanProperty.create("value");
		AutoBinding<FindFociModel, Double, JSlider, Integer> autoBinding_23 = Bindings.createAutoBinding(UpdateStrategy.READ_WRITE, model, findFociBeanProperty_10, sliderBackgroundParamAbsolute, jSliderBeanProperty_7);
		autoBinding_23.setConverter(new SliderDoubleConverter());
		autoBinding_23.bind();
		//
		BeanProperty<JSlider, Boolean> jSliderBeanProperty_8 = BeanProperty.create("visible");
		AutoBinding<FindFociModel, Integer, JSlider, Boolean> autoBinding_24 = Bindings.createAutoBinding(UpdateStrategy.READ, model, findFociBeanProperty_4, sliderBackgroundParamAbsolute, jSliderBeanProperty_8);
		autoBinding_24.setConverter(new BackgroundParamAbsoluteEnabledConverter());
		autoBinding_24.bind();
		//
		AutoBinding<FindFociModel, Integer, JSlider, Boolean> autoBinding_25 = Bindings.createAutoBinding(UpdateStrategy.READ, model, findFociBeanProperty_4, sliderBackgroundParam, jSliderBeanProperty_8);
		autoBinding_25.setConverter(new BackgroundParamAbsoluteDisabledConverter());
		autoBinding_25.bind();
		//
		BeanProperty<FindFociModel, List<String>> findFociBeanProperty_11 = BeanProperty.create("imageList");
		JComboBoxBinding<String, FindFociModel, JComboBox> jComboBinding = SwingBindings.createJComboBoxBinding(UpdateStrategy.READ, model, findFociBeanProperty_11, comboImageList);
		jComboBinding.bind();
		//
		BeanProperty<JComboBox, Boolean> jComboBoxBeanProperty_1 = BeanProperty.create("enabled");
		AutoBinding<FindFociModel, Integer, JComboBox, Boolean> autoBinding_26 = Bindings.createAutoBinding(UpdateStrategy.READ, model, findFociBeanProperty_4, comboThresholdMethod, jComboBoxBeanProperty_1);
		autoBinding_26.setConverter(new BackgroundThresholdMethodEnabledConverter());
		autoBinding_26.bind();
		//
		AutoBinding<FindFociModel, Integer, JLabel, Boolean> autoBinding_27 = Bindings.createAutoBinding(UpdateStrategy.READ, model, findFociBeanProperty_4, lblThresholdMethod, jLabelBeanProperty_1);
		autoBinding_27.setConverter(new BackgroundThresholdMethodEnabledConverter());
		autoBinding_27.bind();
		//
		BeanProperty<FindFociModel, String> findFociBeanProperty_12 = BeanProperty.create("selectedImage");
		BeanProperty<JComboBox, String> jComboBoxBeanProperty_2 = BeanProperty.create("selectedItem");
		AutoBinding<FindFociModel, String, JComboBox, String> autoBinding_28 = Bindings.createAutoBinding(UpdateStrategy.READ_WRITE, model, findFociBeanProperty_12, comboImageList, jComboBoxBeanProperty_2);
		autoBinding_28.bind();
		//
		BeanProperty<FindFociModel, Boolean> findFociModelBeanProperty_1 = BeanProperty.create("minimumAboveSaddle");
		BeanProperty<JCheckBox, Boolean> jCheckBoxBeanProperty = BeanProperty.create("selected");
		AutoBinding<FindFociModel, Boolean, JCheckBox, Boolean> autoBinding_31 = Bindings.createAutoBinding(UpdateStrategy.READ_WRITE, model, findFociModelBeanProperty_1, chckbxMinSizeAboveSaddle, jCheckBoxBeanProperty);
		autoBinding_31.bind();
		//
		BeanProperty<JFormattedTextField, String> jFormattedTextFieldBeanProperty = BeanProperty.create("text");
		AutoBinding<FindFociModel, Double, JFormattedTextField, String> autoBinding_32 = Bindings.createAutoBinding(UpdateStrategy.READ_WRITE, model, findFociBeanProperty_9, txtGaussianBlur, jFormattedTextFieldBeanProperty);
		autoBinding_32.setConverter(new DoubleConverter());
		autoBinding_32.bind();
		//
		BeanProperty<FindFociModel, Integer> findFociBeanProperty = BeanProperty.create("minSize");
		BeanProperty<JFormattedTextField, String> jFormattedTextFieldBeanProperty_1 = BeanProperty.create("text");
		AutoBinding<FindFociModel, Integer, JFormattedTextField, String> autoBinding = Bindings.createAutoBinding(UpdateStrategy.READ_WRITE, model, findFociBeanProperty, txtMinimumSize, jFormattedTextFieldBeanProperty_1);
		autoBinding.bind();
		//
		BeanProperty<JFormattedTextField, String> jFormattedTextFieldBeanProperty_2 = BeanProperty.create("text");
		AutoBinding<FindFociModel, Double, JFormattedTextField, String> autoBinding_3 = Bindings.createAutoBinding(UpdateStrategy.READ_WRITE, model, findFociBeanProperty_2, txtPeakParam, jFormattedTextFieldBeanProperty_2);
		autoBinding_3.setConverter(new DoubleConverter());
		autoBinding_3.bind();
		//
		BeanProperty<JFormattedTextField, String> jFormattedTextFieldBeanProperty_3 = BeanProperty.create("text");
		AutoBinding<FindFociModel, Integer, JFormattedTextField, String> autoBinding_4 = Bindings.createAutoBinding(UpdateStrategy.READ_WRITE, model, findFociBeanProperty_3, txtMaxPeaks, jFormattedTextFieldBeanProperty_3);
		autoBinding_4.bind();
		//
		BeanProperty<JFormattedTextField, String> jFormattedTextFieldBeanProperty_4 = BeanProperty.create("text");
		AutoBinding<FindFociModel, Double, JFormattedTextField, String> autoBinding_7 = Bindings.createAutoBinding(UpdateStrategy.READ_WRITE, model, findFociBeanProperty_1, txtSearchParam, jFormattedTextFieldBeanProperty_4);
		autoBinding_7.setConverter(new DoubleConverter());
		autoBinding_7.bind();
		//
		BeanProperty<JFormattedTextField, String> jFormattedTextFieldBeanProperty_5 = BeanProperty.create("text");
		AutoBinding<FindFociModel, Double, JFormattedTextField, String> autoBinding_13 = Bindings.createAutoBinding(UpdateStrategy.READ_WRITE, model, findFociBeanProperty_10, txtBackgroundParam, jFormattedTextFieldBeanProperty_5);
		autoBinding_13.setConverter(new DoubleConverter());
		autoBinding_13.bind();
		//
		BeanProperty<FindFociView, Boolean> findFociViewBeanProperty = BeanProperty.create("validImages");
		AutoBinding<FindFociModel, List<String>, FindFociView, Boolean> autoBinding_18 = Bindings.createAutoBinding(UpdateStrategy.READ, model, findFociBeanProperty_11, instance, findFociViewBeanProperty);
		autoBinding_18.setConverter(new ValidImagesConverter());
		autoBinding_18.bind();
		//
		BeanProperty<FindFociView, Boolean> findFociViewBeanProperty_2 = BeanProperty.create("runEnabled");
		BeanProperty<JButton, Boolean> jButtonBeanProperty = BeanProperty.create("enabled");
		AutoBinding<FindFociView, Boolean, JButton, Boolean> autoBinding_21 = Bindings.createAutoBinding(UpdateStrategy.READ, instance, findFociViewBeanProperty_2, btnRun, jButtonBeanProperty);
		autoBinding_21.bind();
		//
		BeanProperty<FindFociModel, Integer> findFociModelBeanProperty_2 = BeanProperty.create("peakMethod");
		AutoBinding<FindFociModel, Integer, JSlider, Boolean> autoBinding_29 = Bindings.createAutoBinding(UpdateStrategy.READ, model, findFociModelBeanProperty_2, sliderPeakParam, jSliderBeanProperty_8);
		autoBinding_29.setConverter(new PeakParamAbsoluteDisabledConverter());
		autoBinding_29.bind();
		//
		AutoBinding<FindFociModel, Integer, JSlider, Boolean> autoBinding_30 = Bindings.createAutoBinding(UpdateStrategy.READ, model, findFociModelBeanProperty_2, sliderPeakParamAbsolute, jSliderBeanProperty_8);
		autoBinding_30.setConverter(new PeakParamAbsoluteEnabledConverter());
		autoBinding_30.bind();
		//
		BeanProperty<JSlider, Integer> jSliderBeanProperty_9 = BeanProperty.create("value");
		AutoBinding<FindFociModel, Double, JSlider, Integer> autoBinding_33 = Bindings.createAutoBinding(UpdateStrategy.READ_WRITE, model, findFociBeanProperty_2, sliderPeakParamAbsolute, jSliderBeanProperty_9);
		autoBinding_33.setConverter(new SliderDoubleConverter());
		autoBinding_33.bind();
		//
		AutoBinding<FindFociModel, Integer, JComboBox, Object> autoBinding_34 = Bindings.createAutoBinding(UpdateStrategy.READ_WRITE, model, findFociModelBeanProperty_2, comboPeakMethod, jComboBoxBeanProperty);
		autoBinding_34.setConverter(new PeakMethodConverter());
		autoBinding_34.bind();
		//
		BeanProperty<JFormattedTextField, Boolean> jFormattedTextFieldBeanProperty_6 = BeanProperty.create("enabled");
		AutoBinding<FindFociModel, Integer, JFormattedTextField, Boolean> autoBinding_35 = Bindings.createAutoBinding(UpdateStrategy.READ, model, findFociBeanProperty_4, txtBackgroundParam, jFormattedTextFieldBeanProperty_6);
		autoBinding_35.setConverter(new BackgroundParamEnabledConverter());
		autoBinding_35.bind();
		//
		AutoBinding<FindFociModel, Integer, JFormattedTextField, Boolean> autoBinding_36 = Bindings.createAutoBinding(UpdateStrategy.READ, model, findFociBeanProperty_5, txtSearchParam, jFormattedTextFieldBeanProperty_6);
		autoBinding_36.setConverter(new SearchParamEnabledConverter());
		autoBinding_36.bind();
		//
		ELProperty<JSlider, Object> jSliderEvalutionProperty = ELProperty.create("${value}");
		AutoBinding<FindFociModel, Integer, JSlider, Object> autoBinding_1 = Bindings.createAutoBinding(UpdateStrategy.READ_WRITE, model, findFociBeanProperty, sliderMinimumSize, jSliderEvalutionProperty);
		autoBinding_1.bind();
		//
		BeanProperty<FindFociView, Integer> findFociViewBeanProperty_3 = BeanProperty.create("imageMinimum");
		BeanProperty<JSlider, Integer> jSliderBeanProperty = BeanProperty.create("minimum");
		AutoBinding<FindFociView, Integer, JSlider, Integer> autoBinding_37 = Bindings.createAutoBinding(UpdateStrategy.READ, instance, findFociViewBeanProperty_3, sliderBackgroundParamAbsolute, jSliderBeanProperty);
		autoBinding_37.bind();
		//
		BeanProperty<FindFociView, Integer> findFociViewBeanProperty_4 = BeanProperty.create("imageMaximum");
		BeanProperty<JSlider, Integer> jSliderBeanProperty_10 = BeanProperty.create("maximum");
		AutoBinding<FindFociView, Integer, JSlider, Integer> autoBinding_38 = Bindings.createAutoBinding(UpdateStrategy.READ, instance, findFociViewBeanProperty_4, sliderBackgroundParamAbsolute, jSliderBeanProperty_10);
		autoBinding_38.bind();
		//
		BeanProperty<FindFociModel, Boolean> findFociModelBeanProperty_3 = BeanProperty.create("changed");
		BeanProperty<FindFociView, Boolean> findFociViewBeanProperty_1 = BeanProperty.create("changed");
		AutoBinding<FindFociModel, Boolean, FindFociView, Boolean> autoBinding_39 = Bindings.createAutoBinding(UpdateStrategy.READ, model, findFociModelBeanProperty_3, instance, findFociViewBeanProperty_1);
		autoBinding_39.bind();
		//
		BeanProperty<FindFociModel, String> findFociModelBeanProperty = BeanProperty.create("maskImage");
		AutoBinding<FindFociModel, String, JComboBox, Object> autoBinding_20 = Bindings.createAutoBinding(UpdateStrategy.READ_WRITE, model, findFociModelBeanProperty, comboMaskImageList, jComboBoxBeanProperty);
		autoBinding_20.bind();
		//
		BeanProperty<FindFociModel, List<String>> findFociModelBeanProperty_4 = BeanProperty.create("maskImageList");
		JComboBoxBinding<String, FindFociModel, JComboBox> jComboBinding_1 = SwingBindings.createJComboBoxBinding(UpdateStrategy.READ, model, findFociModelBeanProperty_4, comboMaskImageList);
		jComboBinding_1.bind();
		//
		BeanProperty<FindFociModel, String> findFociModelBeanProperty_5 = BeanProperty.create("statisticsMode");
		AutoBinding<FindFociModel, String, JComboBox, Object> autoBinding_40 = Bindings.createAutoBinding(UpdateStrategy.READ_WRITE, model, findFociModelBeanProperty_5, comboStatisticsMode, jComboBoxBeanProperty);
		autoBinding_40.bind();
		//
		AutoBinding<FindFociModel, Integer, JComboBox, Boolean> autoBinding_41 = Bindings.createAutoBinding(UpdateStrategy.READ, model, findFociBeanProperty_4, comboStatisticsMode, jComboBoxBeanProperty_1);
		autoBinding_41.setConverter(new StatisticsModeParamEnabledConverter());
		autoBinding_41.bind();
		//
		AutoBinding<FindFociModel, Integer, JLabel, Boolean> autoBinding_42 = Bindings.createAutoBinding(UpdateStrategy.READ, model, findFociBeanProperty_4, lblStatisticsMode, jLabelBeanProperty_1);
		autoBinding_42.setConverter(new StatisticsModeParamEnabledConverter());
		autoBinding_42.bind();
		//
		BeanProperty<FindFociModel, Double> findFociModelBeanProperty_6 = BeanProperty.create("fractionParameter");
		BeanProperty<JSlider, Integer> jSliderBeanProperty_11 = BeanProperty.create("value");
		AutoBinding<FindFociModel, Double, JSlider, Integer> autoBinding_43 = Bindings.createAutoBinding(UpdateStrategy.READ_WRITE, model, findFociModelBeanProperty_6, sliderFractionParam, jSliderBeanProperty_11);
		autoBinding_43.setConverter(new SliderConverter());
		autoBinding_43.bind();
		//
		BeanProperty<JTextField, String> jTextFieldBeanProperty = BeanProperty.create("text");
		AutoBinding<FindFociModel, Double, JTextField, String> autoBinding_44 = Bindings.createAutoBinding(UpdateStrategy.READ_WRITE, model, findFociModelBeanProperty_6, txtFractionParam, jTextFieldBeanProperty);
		autoBinding_44.setConverter(new DoubleConverter());
		autoBinding_44.bind();
		//
		BeanProperty<FindFociView, Double> findFociViewBeanProperty_5 = BeanProperty.create("backgroundLevel");
		BeanProperty<JLabel, String> jLabelBeanProperty = BeanProperty.create("text");
		AutoBinding<FindFociView, Double, JLabel, String> autoBinding_45 = Bindings.createAutoBinding(UpdateStrategy.READ, instance, findFociViewBeanProperty_5, lblBackgroundLevelValue, jLabelBeanProperty);
		autoBinding_45.setConverter(new DoubleConverter());
		autoBinding_45.bind();
		//
		BeanProperty<FindFociView, Boolean> findFociViewBeanProperty_6 = BeanProperty.create("sortIndexError");
		BeanProperty<JComboBox, Color> jComboBoxBeanProperty_3 = BeanProperty.create("foreground");
		AutoBinding<FindFociView, Boolean, JComboBox, Color> autoBinding_46 = Bindings.createAutoBinding(UpdateStrategy.READ, instance, findFociViewBeanProperty_6, comboSortMethod, jComboBoxBeanProperty_3);
		autoBinding_46.setConverter(new SortMethodColorConverter());
		autoBinding_46.bind();
		//
		BeanProperty<FindFociModel, Boolean> findFociModelBeanProperty_7 = BeanProperty.create("connectedAboveSaddle");
		AutoBinding<FindFociModel, Boolean, JCheckBox, Boolean> autoBinding_47 = Bindings.createAutoBinding(UpdateStrategy.READ_WRITE, model, findFociModelBeanProperty_7, chckbxConnectedAboveSaddle, jCheckBoxBeanProperty);
		autoBinding_47.bind();
	}
}
