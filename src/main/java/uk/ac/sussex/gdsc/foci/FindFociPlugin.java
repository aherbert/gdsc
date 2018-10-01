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
package uk.ac.sussex.gdsc.foci;

import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.PrintWriter;
import java.io.StringWriter;

import javax.swing.WindowConstants;

import ij.IJ;
import ij.ImageListener;
import ij.ImagePlus;
import ij.WindowManager;
import ij.gui.GenericDialog;
import ij.plugin.PlugIn;
import uk.ac.sussex.gdsc.UsageTracker;
import uk.ac.sussex.gdsc.foci.controller.FindFociController;
import uk.ac.sussex.gdsc.foci.controller.ImageJController;
import uk.ac.sussex.gdsc.foci.gui.FindFociView;
import uk.ac.sussex.gdsc.foci.model.FindFociModel;

/**
 * Provides a permanent form front-end for the FindFoci plugin filter.
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

        @Override
        public void windowOpened(WindowEvent e)
        {
            // Ignore
        }

        @Override
        public void windowClosing(WindowEvent e)
        {
            WindowManager.removeWindow(instance);
        }

        @Override
        public void windowClosed(WindowEvent e)
        {
            // Ignore
        }

        @Override
        public void windowIconified(WindowEvent e)
        {
            // Ignore
        }

        @Override
        public void windowDeiconified(WindowEvent e)
        {
            // Ignore
        }

        @Override
        public void windowActivated(WindowEvent e)
        {
            // Ignore
        }

        @Override
        public void windowDeactivated(WindowEvent e)
        {
            // Ignore
        }

        @Override
        public void imageOpened(ImagePlus imp)
        {
            // Ignore
        }

        @Override
        public void imageClosed(ImagePlus imp)
        {
            // Ignore
        }

        @Override
        public void imageUpdated(ImagePlus imp)
        {
            if (imp == null)
                return;

            // Check if the image is the selected image in the model.
            // If the slice has changed then invalidate the model
            if (imp.getTitle().equals(model.getSelectedImage()))
            {
                final int oldCurrentChannel = currentChannel;
                final int oldCurrentFrame = currentFrame;
                getCurrentSlice();
                if (oldCurrentChannel != currentChannel || oldCurrentFrame != currentFrame)
                    model.invalidate();
            }
        }

        private void getCurrentSlice()
        {
            final ImagePlus imp = WindowManager.getImage(model.getSelectedImage());
            if (imp != null)
            {
                currentChannel = imp.getChannel();
                currentFrame = imp.getFrame();
            }
            else
                currentChannel = currentFrame = 0;
        }

        @Override
        public void propertyChange(PropertyChangeEvent evt)
        {
            // Store the slice for the image when it changes.
            if (evt.getPropertyName().equals("selectedImage"))
                getCurrentSlice();
        }
    }

    /** {@inheritDoc} */
    @Override
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
                final GenericDialog gd = new GenericDialog(FindFoci.TITLE);
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

        final FindFociModel model = new FindFociModel();
        model.setResultsDirectory(System.getProperty("java.io.tmpdir"));
        final FindFociController controller = new ImageJController(model);
        final FindFociListener listener = new FindFociListener(model);

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
            instance.setDefaultCloseOperation(WindowConstants.HIDE_ON_CLOSE);

            IJ.register(FindFociView.class);

            showInstance(instance);
            IJ.showStatus("FindFoci ready");
        }
        catch (final ExceptionInInitializerError e)
        {
            exception = e;
            errorMessage = "Failed to initialize class: " + e.getMessage();
        }
        catch (final LinkageError e)
        {
            exception = e;
            errorMessage = "Failed to link class: " + e.getMessage();
        }
        catch (final ClassNotFoundException ex)
        {
            exception = ex;
            errorMessage = "Failed to find class: " + ex.getMessage() +
                    "\nCheck you have beansbinding-1.2.1.jar on your classpath\n";
        }
        catch (final Throwable ex)
        {
            exception = ex;
            errorMessage = ex.getMessage();
        }
        finally
        {
            if (exception != null)
            {
                final StringWriter sw = new StringWriter();
                final PrintWriter pw = new PrintWriter(sw);
                pw.write(errorMessage);
                pw.append('\n');
                exception.printStackTrace(pw);
                IJ.log(sw.toString());
            }
        }
    }

    private void showNewInstance()
    {
        final FindFociModel model = new FindFociModel();
        model.setResultsDirectory(System.getProperty("java.io.tmpdir"));
        final FindFociController controller = new ImageJController(model);
        final FindFociListener listener = new FindFociListener(model);

        // Track when the image changes to a new slice
        ImagePlus.addImageListener(listener);
        model.addPropertyChangeListener("selectedImage", listener);

        final FindFociView instance = new FindFociView(model, controller);
        listener.addWindowListener(instance);
        instance.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);

        showInstance(instance);
    }

    private static void showInstance(FindFociView instance)
    {
        WindowManager.addWindow(instance);
        instance.setVisible(true);
        instance.toFront();
    }
}
