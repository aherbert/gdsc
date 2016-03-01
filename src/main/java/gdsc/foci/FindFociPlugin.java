package gdsc.foci;

import gdsc.UsageTracker;

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

import gdsc.foci.controller.FindFociController;
import gdsc.foci.controller.ImageJController;
import gdsc.foci.gui.FindFociView;
import gdsc.foci.model.FindFociModel;
import ij.IJ;
import ij.ImageListener;
import ij.ImagePlus;
import ij.WindowManager;
import ij.gui.GenericDialog;
import ij.plugin.PlugIn;

import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.PrintWriter;
import java.io.StringWriter;

import javax.swing.JFrame;

/**
 * Provides a permanent form front-end for the FindFoci plugin filter
 */
public class FindFociPlugin implements PlugIn
{
	private static FindFociView instance = null;

	private class FindFociListener implements WindowListener, ImageListener, PropertyChangeListener
	{
		FindFociModel model;
		FindFociView instance;
		int currentChannel = 0;
		int currentFrame = 0;

		FindFociListener(FindFociModel model)
		{
			this.model = model;
		}

		public void addWindowListener(FindFociView instance)
		{
			this.instance = instance;
			instance.addWindowListener(this);
		}

		public void windowOpened(WindowEvent e)
		{
		}

		public void windowClosing(WindowEvent e)
		{
			WindowManager.removeWindow(instance);
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

		public void imageOpened(ImagePlus imp)
		{
			// Ignore
		}

		public void imageClosed(ImagePlus imp)
		{
			// Ignore
		}

		public void imageUpdated(ImagePlus imp)
		{
			if (imp == null)
				return;

			// Check if the image is the selected image in the model.
			// If the slice has changed then invalidate the model
			if (imp.getTitle().equals(model.getSelectedImage()))
			{
				int oldCurrentChannel = currentChannel;
				int oldCurrentFrame = currentFrame;
				getCurrentSlice();
				if (oldCurrentChannel != currentChannel || oldCurrentFrame != currentFrame)
				{
					model.invalidate();
				}
			}
		}

		private void getCurrentSlice()
		{
			ImagePlus imp = WindowManager.getImage(model.getSelectedImage());
			if (imp != null)
			{
				currentChannel = imp.getChannel();
				currentFrame = imp.getFrame();
			}
			else
			{
				currentChannel = currentFrame = 0;
			}
		}

		public void propertyChange(PropertyChangeEvent evt)
		{
			// Store the slice for the image when it changes.
			if (evt.getPropertyName().equals("selectedImage"))
			{
				getCurrentSlice();
			}
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see ij.plugin.frame.PlugInFrame#run(java.lang.String)
	 */
	public void run(String arg)
	{
		UsageTracker.recordPlugin(this.getClass(), arg);
		
		if (WindowManager.getImageCount() < 1)
		{
			IJ.showMessage("No images opened.");
			return;
		}

		if (instance != null)
		{
			if (instance.isVisible())
			{
				// Ask if the user would like a second instance
				GenericDialog gd = new GenericDialog(FindFoci.TITLE);
				gd.enableYesNoCancel();
				gd.addMessage(FindFoci.TITLE + " is already open.\n \nDo you want to create another instance?");
				gd.showDialog();
				if (gd.wasCanceled())
					return;
				if (gd.wasOKed())
				{
					showNewInstance();
					return;
				}
			}
			showInstance(instance);
			return;
		}

		FindFociModel model = new FindFociModel();
		model.setResultsDirectory(System.getProperty("java.io.tmpdir"));
		FindFociController controller = new ImageJController(model);
		FindFociListener listener = new FindFociListener(model);

		// Track when the image changes to a new slice
		ImagePlus.addImageListener(listener);
		model.addPropertyChangeListener("selectedImage", listener);

		IJ.showStatus("Initialising FindFoci ...");

		String errorMessage = null;
		Throwable exception = null;

		try
		{
			Class.forName("org.jdesktop.beansbinding.Property", false, this.getClass().getClassLoader());

			// it exists on the classpath
			instance = new FindFociView(model, controller);
			listener.addWindowListener(instance);
			instance.setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);

			IJ.register(FindFociView.class);

			showInstance(instance);
			IJ.showStatus("FindFoci ready");
		}
		catch (ExceptionInInitializerError e)
		{
			exception = e;
			errorMessage = "Failed to initialize class: " + e.getMessage();
		}
		catch (LinkageError e)
		{
			exception = e;
			errorMessage = "Failed to link class: " + e.getMessage();
		}
		catch (ClassNotFoundException ex)
		{
			exception = ex;
			errorMessage = "Failed to find class: " + ex.getMessage() +
					"\nCheck you have beansbinding-1.2.1.jar on your classpath\n";
		}
		catch (Throwable ex)
		{
			exception = ex;
			errorMessage = ex.getMessage();
		}
		finally
		{
			if (exception != null)
			{
				StringWriter sw = new StringWriter();
				PrintWriter pw = new PrintWriter(sw);
				pw.write(errorMessage);
				pw.append('\n');
				exception.printStackTrace(pw);
				IJ.log(sw.toString());
			}
		}
	}

	private void showNewInstance()
	{
		FindFociModel model = new FindFociModel();
		model.setResultsDirectory(System.getProperty("java.io.tmpdir"));
		FindFociController controller = new ImageJController(model);
		FindFociListener listener = new FindFociListener(model);

		// Track when the image changes to a new slice
		ImagePlus.addImageListener(listener);
		model.addPropertyChangeListener("selectedImage", listener);

		FindFociView instance = new FindFociView(model, controller);
		listener.addWindowListener(instance);
		instance.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

		showInstance(instance);
	}

	private void showInstance(FindFociView instance)
	{
		WindowManager.addWindow(instance);
		instance.setVisible(true);
		instance.toFront();
	}
}
