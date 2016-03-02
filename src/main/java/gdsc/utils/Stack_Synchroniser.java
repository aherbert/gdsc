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
import ij.ImageListener;
import ij.ImagePlus;
import ij.Prefs;
import ij.WindowManager;
import ij.gui.GUI;
import ij.macro.MacroRunner;
import ij.plugin.frame.PlugInFrame;

import java.awt.BorderLayout;
import java.awt.Choice;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Label;
import java.awt.Panel;
import java.awt.Point;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowEvent;
import java.util.ArrayList;
import java.util.List;

import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JToggleButton;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

import gdsc.UsageTracker;

/**
 * Provides the ability to synchronise the display frame of multiple stack windows
 */
public class Stack_Synchroniser extends PlugInFrame implements ItemListener, ImageListener, ListSelectionListener
{
	private static final long serialVersionUID = 1L;
	private static String TITLE = "Stack Synchroniser";
	private static Frame instance;

	private ImagePlus parentImage = null;
	private int currentSlice = 0;
	private List<ImagePlus> childImages = new ArrayList<ImagePlus>();

	// Options
	private final String OPT_LOCATION = "Stack_Synchroniser.location";

	private Choice imageChoice;
	private JToggleButton synchroniseButton;
	private JButton helpButton;
	private DefaultListModel<String> listModel;
	private JList<String> childList;

	// Used to check whether to update the image list
	private ArrayList<String> imageList = new ArrayList<String>();
	private boolean updateChildren = true;

	/**
	 * Instance constructor
	 */
	public Stack_Synchroniser()
	{
		super(TITLE);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see ij.plugin.frame.PlugInFrame#run(java.lang.String)
	 */
	public void run(String arg)
	{
		UsageTracker.recordPlugin(this.getClass(), arg);
		
		if (WindowManager.getImageCount() == 0)
		{
			IJ.showMessage("No images opened.");
			return;
		}

		if (instance != null)
		{
			instance.toFront();
			return;
		}

		instance = this;
		IJ.register(Stack_Synchroniser.class);
		WindowManager.addWindow(this);

		// Register to be notified of image changes
		ImagePlus.addImageListener(this);

		createFrame();
		fillImagesList();

		addKeyListener(IJ.getInstance());
		pack();
		Point loc = Prefs.getLocation(OPT_LOCATION);
		if (loc != null)
			setLocation(loc);
		else
		{
			GUI.center(this);
		}

		setVisible(true);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see ij.plugin.frame.PlugInFrame#windowActivated(java.awt.event.WindowEvent)
	 */
	public void windowActivated(WindowEvent e)
	{
		super.windowActivated(e);

		fillImagesList();
		WindowManager.setWindow(this);
	}

	/**
	 * Populate the drop-down with the current valid images
	 */
	public void fillImagesList()
	{
		// Find the currently open images
		ArrayList<String> newImageList = new ArrayList<String>();

		if (WindowManager.getImageCount() > 0)
		{
			for (int id : gdsc.utils.ImageJHelper.getIDList())
			{
				ImagePlus imp = WindowManager.getImage(id);

				// Image must be a stack
				if (imp != null && imp.getStackSize() > 1)
				{
					String imageTitle = -imp.getID() + " : " + imp.getTitle();
					newImageList.add(imageTitle);
				}
			}
		}

		// Check if the image list has changed
		if (imageList.equals(newImageList))
			return;

		synchronized (imageChoice)
		{
			imageList = newImageList;

			// Re-populate the image lists
			imageChoice.removeAll();
			updateChildren = false;

			for (String imageTitle : imageList)
			{
				imageChoice.add(imageTitle);
			}

			updateChildren = true;
			
    		// Ensure the drop-downs are resized
    		pack();
		}
		
		fillChildList();
	}

	private void fillChildList()
	{
		synchronized (childList)
		{
    		// Re-populate the image lists
    		childList.clearSelection();
    		listModel.clear();
    
    		for (String imageTitle : imageList)
    		{
    			if (!imageTitle.equals(imageChoice.getSelectedItem()))
    			{
    				listModel.addElement(imageTitle);
    			}
    		}
    
    		invalidate();
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see ij.plugin.frame.PlugInFrame#windowClosing(java.awt.event.WindowEvent)
	 */
	public void windowClosing(WindowEvent e)
	{
		Prefs.saveLocation(OPT_LOCATION, getLocation());
		instance = null;
		ImagePlus.removeImageListener(this);
		super.close();
	}

	private void createFrame()
	{
		Panel mainPanel = new Panel();
		add(mainPanel);

		imageChoice = new Choice();
		mainPanel.add(createChoicePanel(imageChoice, null, null, "Image"));
		imageChoice.addItemListener(this);

		synchroniseButton = new JToggleButton("Synchronise");
		synchroniseButton.addItemListener(this);
		
		helpButton = new JButton("Help");
		helpButton.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent e) {
				String macro = "run('URL...', 'url="+gdsc.help.URL.UTILITY+"');";
				new MacroRunner(macro);
			}
		});
		
		JPanel buttonPanel = new JPanel();
		FlowLayout l = new FlowLayout();
		l.setVgap(0);
		buttonPanel.setLayout(l);
		buttonPanel.add(synchroniseButton, BorderLayout.CENTER);
		buttonPanel.add(helpButton, BorderLayout.CENTER);
		
		mainPanel.add(buttonPanel);
		
		mainPanel.add(createLabelPanel("Images to sync:"));

		listModel = new DefaultListModel<String>();
		childList = new JList<String>(listModel);
		childList.setVisibleRowCount(15);
		JScrollPane scrollPane = new JScrollPane(childList);
		mainPanel.add(scrollPane);
		childList.addListSelectionListener(this);

		GridBagLayout mainGrid = new GridBagLayout();
		int y = 0;
		GridBagConstraints c = new GridBagConstraints();
		c.gridx = 0;
		c.fill = GridBagConstraints.BOTH;
		c.anchor = GridBagConstraints.WEST;
		c.gridwidth = 1;
		c.insets = new Insets(2, 2, 2, 2);

		for (Component comp : mainPanel.getComponents())
		{
			c.gridy = y++;
			mainGrid.setConstraints(comp, c);
		}

		mainPanel.setLayout(mainGrid);
	}

	private Panel createChoicePanel(Choice list, String[] options, String selected, String label)
	{
		Panel panel = new Panel();
		panel.setLayout(new BorderLayout());
		Label listLabel = new Label(label, 0);
		if (options != null)
		{
			for (String option : options)
				list.add(option);
			try
			{
				list.select(Integer.parseInt(selected));
			}
			catch (Exception ex)
			{
				list.select((String) selected);
			}
		}
		panel.add(listLabel, BorderLayout.WEST);
		panel.add(list, BorderLayout.CENTER);
		return panel;
	}

	private Panel createLabelPanel(String label)
	{
		Panel panel = new Panel();
		panel.setLayout(new BorderLayout());
		Label listLabel = new Label(label, 0);
		panel.add(listLabel, BorderLayout.WEST);
		return panel;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see ij.ImageListener#imageOpened(ij.ImagePlus)
	 */
	public void imageOpened(ImagePlus imp)
	{
		fillImagesList();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see ij.ImageListener#imageClosed(ij.ImagePlus)
	 */
	public void imageClosed(ImagePlus imp)
	{
		if (imp == parentImage)
		{
			parentImage = null;
		}
		else if (childImages.contains(imp))
		{
			childImages.remove(imp);
		}
		fillImagesList();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see ij.ImageListener#imageUpdated(ij.ImagePlus)
	 */
	public void imageUpdated(ImagePlus imp)
	{
		if (imp == parentImage && currentSlice != parentImage.getCurrentSlice() && !childImages.isEmpty())
		{
			currentSlice = parentImage.getCurrentSlice();
			int channel = parentImage.getChannel();
			int frame = parentImage.getFrame();
			int slice = parentImage.getSlice();

//			System.out.printf("Image Id %d : Slice %d (c=%d,z=%d,t=%d)\n", imp.getID(), currentSlice, channel, slice,
//					frame);
			for (ImagePlus childImp : childImages)
			{
				int stackIndex = childImp.getStackIndex(channel, slice, frame);
				childImp.setSlice(stackIndex);
			}
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.awt.event.ItemListener#itemStateChanged(java.awt.event.ItemEvent)
	 */
	public void itemStateChanged(ItemEvent e)
	{
		Object actioner = e.getSource();

		if (actioner instanceof Choice)
		{
			if (updateChildren)
			{
				fillChildList();
			}
		}

		parentImage = (synchroniseButton.isSelected()) 
			? WindowManager.getImage(extractId(this.imageChoice.getSelectedItem()))
			: null;

		updateSynchronisation();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see javax.swing.event.ListSelectionListener#valueChanged(javax.swing.event.ListSelectionEvent)
	 */
	public void valueChanged(ListSelectionEvent e)
	{
		updateSynchronisation();
	}

	/**
	 * Updates the list of child images that will be synchronised to the parent image
	 */
	private void updateSynchronisation()
	{
		if (parentImage != null)
		{
			currentSlice = -1;

			childImages.clear();

			for (int index : childList.getSelectedIndices())
			{
				String imageTitle = (String) listModel.get(index);

				ImagePlus imp = WindowManager.getImage(extractId(imageTitle));

				if (imp != null)
				{
					childImages.add(imp);
				}
			}

			imageUpdated(parentImage);
		}
	}

	private int extractId(String imageTitle)
	{
		String[] data = imageTitle.split(" : ");
		int id = 0;
		try
		{
			id = Integer.parseInt(data[0]);
		}
		catch (NumberFormatException ex)
		{
		}
		return -id;
	}
}
