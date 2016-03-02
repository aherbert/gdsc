package gdsc.utils;

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

import ij.IJ;
import ij.ImagePlus;
import ij.Prefs;
import ij.WindowManager;
import ij.gui.GUI;
import ij.gui.Toolbar;
import ij.macro.MacroRunner;
import ij.plugin.MacroInstaller;
import ij.plugin.frame.PlugInFrame;
import ij.process.ImageProcessor;

import java.awt.BorderLayout;
import java.awt.Button;
import java.awt.Color;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Label;
import java.awt.Point;
import java.awt.Scrollbar;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.AdjustmentEvent;
import java.awt.event.AdjustmentListener;

import javax.swing.JPanel;

import org.apache.commons.math3.stat.descriptive.SummaryStatistics;

import gdsc.PluginTracker;

/**
 * Alows an RGB image to be filtered using HSB limits.
 */
public class HSB_Picker extends PlugInFrame
{
	private static final long serialVersionUID = 5755638798388461612L;

	private static final String TITLE = "HSB Picker";
	private static final String OPT_LOCATION = "HSB_Picker.location";
	private static final double SCALE = 100;

	private static HSB_Picker instance = null;
	private Scrollbar sampleSlider;
	@SuppressWarnings("unused")
	private Label sampleLabel;
	private Label nLabel;
	private Label[] statsLabel;
	private Scrollbar scaleSlider;
	@SuppressWarnings("unused")
	private Label scaleLabel;
	private Button clearButton, filterButton, okButton, helpButton;

	private SummaryStatistics[] stats;

	/**
	 * Constructor
	 */
	public HSB_Picker()
	{
		super(TITLE);
		stats = new SummaryStatistics[3];
		statsLabel = new Label[3];
		for (int i = 0; i < stats.length; i++)
		{
			stats[i] = new SummaryStatistics();
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see ij.plugin.frame.PlugInFrame#run(java.lang.String)
	 */
	public void run(String arg)
	{
		PluginTracker.recordPlugin(this.getClass(), arg);
		
		if (WindowManager.getImageCount() == 0)
		{
			IJ.showMessage("No images opened.");
			return;
		}

		if (instance != null)
		{
			if (!(instance.getTitle().equals(getTitle())))
			{
				HSB_Picker oldInstance = (HSB_Picker) instance;
				Prefs.saveLocation(OPT_LOCATION, oldInstance.getLocation());
				oldInstance.close();
			}
			else
			{
				instance.toFront();
				return;
			}
		}

		instance = this;
		IJ.register(HSB_Picker.class);
		WindowManager.addWindow(this);

		createFrame();

		addKeyListener(IJ.getInstance());
		pack();
		Point loc = Prefs.getLocation(OPT_LOCATION);
		if (loc != null)
			setLocation(loc);
		else
		{
			GUI.center(this);
		}
		if (IJ.isMacOSX())
			setResizable(false);
		setVisible(true);

		// Install the macro that is called when the image is clicked
		installTool();
	}

	private void installTool()
	{
		StringBuffer sb = new StringBuffer();
		sb.append("macro 'HSB Picker Tool - C00fT0610HC0f0T5910SCf00Tac10L' {\n");
		sb.append("   call('").append(this.getClass().getName()).append(".run');\n");
		sb.append("};\n");
		new MacroInstaller().install(sb.toString());
		Toolbar.getInstance().setTool(Toolbar.SPARE1);
	}

	public static void run()
	{
		if (instance != null)
		{
			instance.imageClicked();
		}
		else
		{
			ImagePlus imp = getCurrentImage();
			if (imp == null)
				return;
			// Create a new instance
			HSB_Picker p = new HSB_Picker();
			p.run("");
		}
	}

	private void imageClicked()
	{
		ImagePlus imp = getCurrentImage();
		if (imp == null)
			return;
		Point p = imp.getCanvas().getCursorLoc();
		if (p == null)
			return;
		ImageProcessor ip = imp.getProcessor();
		addValue(ip, p);
	}
	
	/**
	 * @return The current image (must be 24-bit and have an image canvas)
	 */
	private static ImagePlus getCurrentImage()
	{
		// NOTE: BUG
		// The ImageCanvas.mousePressed(MouseEvent e) eventually calls
		//   Toolbar.getInstance().runMacroTool(toolID);
		// This runs the HSB_Picker if the tool is selected.
		//
		// This happens before WindowManager.setCurrentImage(...) is called
		// by the containing window that has been activated or brought to the front.
		// This means it is possible to click in a different image, raising the ImageCanvas event,
		// but have the HSL values sampled from the previous current image due to the use
		// of WindowManager.getCurrentImage().
		//
		// This can be fixed by setting:
		//   WindowManager.setCurrentWindow(win);
		// in the ImageCanvas.mousePressed(...) method.
		
		ImagePlus imp = WindowManager.getCurrentImage();
		if (imp == null || imp.getBitDepth() != 24 || imp.getCanvas() == null)
			return null;
		return imp;
	}

	private void addValue(ImageProcessor ip, Point p)
	{
		int[] iArray = new int[3];
		float[] hsbvals = new float[3];

		int width = sampleSlider.getValue();
		int limit = width * width;
		for (int y = -width; y <= width; y++)
		{
			for (int x = -width; x <= width; x++)
			{
				if (x * x + y * y > limit)
					continue;

				ip.getPixel(x + p.x, y + p.y, iArray);
				Color.RGBtoHSB(iArray[0], iArray[1], iArray[2], hsbvals);
				for (int i = 0; i < 3; i++)
					stats[i].addValue(hsbvals[i]);
			}
		}

		updateDisplayedStatistics();
	}

	private void clear()
	{
		for (SummaryStatistics s : stats)
			s.clear();
		updateDisplayedStatistics();
	}

	private void runFilter()
	{
		if (stats[0].getN() < 2)
		{
			IJ.log("Not enough samples to run the filter");
			return;
		}
		// Use the SD to set a 95% interval for the width
		double scale = scaleSlider.getValue() / SCALE;
		float hWidth = (float) (stats[0].getStandardDeviation() * scale);
		float sWidth = (float) (stats[1].getStandardDeviation() * scale);
		float bWidth = (float) (stats[2].getStandardDeviation() * scale);
		HSB_Filter.hue = clip(stats[0].getMean());
		HSB_Filter.hueWidth = clip(hWidth);
		HSB_Filter.saturation = clip(stats[1].getMean());
		HSB_Filter.saturationWidth = clip(sWidth);
		HSB_Filter.brightness = clip(stats[2].getMean());
		HSB_Filter.brightnessWidth = clip(bWidth);
		IJ.doCommand("HSB Filter");
	}

	private float clip(double d)
	{
		return (float) (Math.max(0, Math.min(d, 1)));
	}

	private void updateDisplayedStatistics()
	{
		nLabel.setText(Long.toString(stats[0].getN()));
		for (int i = 0; i < 3; i++)
			statsLabel[i].setText(summary(stats[i]));
	}

	private String summary(SummaryStatistics stats)
	{
		return String.format("%.3f +/- %.4f", stats.getMean(), stats.getStandardDeviation());
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see ij.plugin.frame.PlugInFrame#close()
	 */
	public void close()
	{
		Prefs.saveLocation(OPT_LOCATION, getLocation());
		instance = null;
		super.close();
	}

	GridBagLayout mainGrid = new GridBagLayout();
	GridBagConstraints c = new GridBagConstraints();
	int row = 0;

	private void createFrame()
	{
		setLayout(mainGrid);

		createSliderPanel(sampleSlider = new Scrollbar(Scrollbar.HORIZONTAL, 2, 1, 0, 15), "Sample radius",
				sampleLabel = new Label("0"), 1);

		createLabelPanel(nLabel = new Label(), "Pixels", "0");
		createLabelPanel(statsLabel[0] = new Label(), "Hue", "0");
		createLabelPanel(statsLabel[1] = new Label(), "Saturation", "0");
		createLabelPanel(statsLabel[2] = new Label(), "Brightness", "0");

		// Add the buttons
		clearButton = new Button("Reset");
		clearButton.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent e)
			{
				clear();
			}
		});
		add(clearButton, 0, 3);
		row++;

		createSliderPanel(
				scaleSlider = new Scrollbar(Scrollbar.HORIZONTAL, (int) (2 * SCALE), 1, 1, (int) (4 * SCALE)),
				"Filter scale", scaleLabel = new Label("0"), SCALE);

		// Add the buttons
		filterButton = new Button("HSB Filter");
		filterButton.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent e)
			{
				runFilter();
			}
		});
		okButton = new Button("Close");
		okButton.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent e)
			{
				close();
			}
		});
		helpButton = new Button("Help");
		helpButton.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent e)
			{
				String macro = "run('URL...', 'url=" + gdsc.help.URL.UTILITY + "');";
				new MacroRunner(macro);
			}
		});

		JPanel buttonPanel = new JPanel();
		FlowLayout l = new FlowLayout();
		l.setVgap(0);
		buttonPanel.setLayout(l);
		buttonPanel.add(filterButton, BorderLayout.CENTER);
		buttonPanel.add(okButton, BorderLayout.CENTER);
		buttonPanel.add(helpButton, BorderLayout.CENTER);

		add(buttonPanel, 0, 3);
		row++;

		updateDisplayedStatistics();
	}

	private void add(Component comp, int x, int width)
	{
		c.gridx = x;
		c.gridy = row;
		c.gridwidth = width;
		c.fill = GridBagConstraints.HORIZONTAL;
		c.anchor = GridBagConstraints.WEST;
		mainGrid.setConstraints(comp, c);
		add(comp);
	}

	private void createSliderPanel(final Scrollbar sliderField, String label, final Label sliderLabel,
			final double scale)
	{
		Label listLabel = new Label(label, 0);
		add(listLabel, 0, 1);
		sliderField.setSize(100, 10);
		c.ipadx = 75;
		add(sliderField, 1, 1);
		c.ipadx = 0;
		sliderField.addAdjustmentListener(new AdjustmentListener()
		{

			public void adjustmentValueChanged(AdjustmentEvent e)
			{
				setSliderLabel(sliderField, sliderLabel, scale);
			}
		});
		add(sliderLabel, 2, 1);
		setSliderLabel(sliderField, sliderLabel, scale);
		row++;
	}

	private void setSliderLabel(final Scrollbar sliderField, final Label sliderLabel, double scale)
	{
		double value = sliderField.getValue() / scale;
		sliderLabel.setText(String.format("%.2f", value));
	}

	private void createLabelPanel(Label labelField, String label, String value)
	{
		Label listLabel = new Label(label, 0);
		add(listLabel, 0, 1);
		if (labelField != null)
		{
			// labelField.setSize(fontWidth * 3, fontWidth);
			labelField.setText(value);
			add(labelField, 1, 2);
		}
		row++;
	}
}
