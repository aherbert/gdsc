package gdsc.foci;

import gdsc.PluginTracker;

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

import gdsc.foci.gui.FindFociHelperView;
import gdsc.foci.gui.OptimiserView;
import ij.IJ;
import ij.WindowManager;
import ij.plugin.PlugIn;

import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.io.PrintWriter;
import java.io.StringWriter;

import javax.swing.JFrame;

/**
 * Create a window that allows the user to pick ROI points and have them mapped to the closest maxima found
 * by the FindFoci algorithm.
 */
public class FindFociHelperPlugin implements PlugIn, WindowListener
{
	private static FindFociHelperView instance;

	/*
	 * (non-Javadoc)
	 * 
	 * @see ij.plugin.PlugIn#run(java.lang.String)
	 */
	public void run(String arg)
	{
		PluginTracker.recordPlugin(this.getClass(), arg);
		showFindFociPickerWindow();
	}

	private void showFindFociPickerWindow()
	{
		if (instance != null)
		{
			showInstance();
			return;
		}

		IJ.showStatus("Initialising FindFoci Helper ...");

		String errorMessage = null;
		Throwable exception = null;

		try
		{
			Class.forName("org.jdesktop.beansbinding.Property", false, this.getClass().getClassLoader());

			// it exists on the classpath
			instance = new FindFociHelperView();
			instance.addWindowListener(this);
			instance.setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);

			IJ.register(OptimiserView.class);

			showInstance();
			IJ.showStatus("FindFoci Helper ready");
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

	private void showInstance()
	{
		WindowManager.addWindow(instance);
		instance.setVisible(true);
		instance.toFront();
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
}
