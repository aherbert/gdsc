/*-
 * #%L
 * Genome Damage and Stability Centre ImageJ Plugins
 *
 * Software for microscopy image analysis
 * %%
 * Copyright (C) 2011 - 2019 Alex Herbert
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

import uk.ac.sussex.gdsc.UsageTracker;
import uk.ac.sussex.gdsc.foci.controller.FindFociController;
import uk.ac.sussex.gdsc.foci.controller.ImageJController;
import uk.ac.sussex.gdsc.foci.gui.FindFociView;
import uk.ac.sussex.gdsc.foci.model.FindFociModel;

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
import java.util.concurrent.atomic.AtomicReference;

import javax.swing.WindowConstants;

/**
 * Provides a permanent form front-end for the FindFoci plugin filter.
 */
public class FindFociGui_PlugIn implements PlugIn {
  private static final AtomicReference<FindFociView> instance = new AtomicReference<>();

  private static class FindFociListener
      implements WindowListener, ImageListener, PropertyChangeListener {
    FindFociModel model;
    FindFociView view;
    int currentChannel;
    int currentFrame;

    FindFociListener(FindFociModel model) {
      this.model = model;
    }

    public void addWindowListener(FindFociView view) {
      this.view = view;
      view.addWindowListener(this);
    }

    @Override
    public void windowOpened(WindowEvent event) {
      // Ignore
    }

    @Override
    public void windowClosing(WindowEvent event) {
      WindowManager.removeWindow(view);
    }

    @Override
    public void windowClosed(WindowEvent event) {
      // Ignore
    }

    @Override
    public void windowIconified(WindowEvent event) {
      // Ignore
    }

    @Override
    public void windowDeiconified(WindowEvent event) {
      // Ignore
    }

    @Override
    public void windowActivated(WindowEvent event) {
      // Ignore
    }

    @Override
    public void windowDeactivated(WindowEvent event) {
      // Ignore
    }

    @Override
    public void imageOpened(ImagePlus imp) {
      // Ignore
    }

    @Override
    public void imageClosed(ImagePlus imp) {
      // Ignore
    }

    @Override
    public void imageUpdated(ImagePlus imp) {
      if (imp == null) {
        return;
      }

      // Check if the image is the selected image in the model.
      // If the slice has changed then invalidate the model
      if (imp.getTitle().equals(model.getSelectedImage())) {
        final int oldCurrentChannel = currentChannel;
        final int oldCurrentFrame = currentFrame;
        getCurrentSlice();
        if (oldCurrentChannel != currentChannel || oldCurrentFrame != currentFrame) {
          model.invalidate();
        }
      }
    }

    private void getCurrentSlice() {
      final ImagePlus imp = WindowManager.getImage(model.getSelectedImage());
      if (imp != null) {
        currentChannel = imp.getChannel();
        currentFrame = imp.getFrame();
      } else {
        currentChannel = currentFrame = 0;
      }
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
      // Store the slice for the image when it changes.
      if (evt.getPropertyName().equals("selectedImage")) {
        getCurrentSlice();
      }
    }
  }

  /** {@inheritDoc} */
  @Override
  public void run(String arg) {
    UsageTracker.recordPlugin(this.getClass(), arg);

    if (WindowManager.getImageCount() < 1) {
      IJ.showMessage("No images opened.");
      return;
    }

    FindFociView view = instance.get();

    if (view != null) {
      if (view.isVisible()) {
        // Ask if the user would like a second instance
        final GenericDialog gd = new GenericDialog(FindFoci_PlugIn.TITLE);
        gd.enableYesNoCancel();
        gd.addMessage(FindFoci_PlugIn.TITLE
            + " is already open.\n \nDo you want to create another instance?");
        gd.showDialog();
        if (gd.wasCanceled()) {
          return;
        }
        if (gd.wasOKed()) {
          showNewInstance();
          return;
        }
      }
      showInstance(view);
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

    try {
      Class.forName("org.jdesktop.beansbinding.Property", false, this.getClass().getClassLoader());

      // it exists on the classpath
      view = new FindFociView(model, controller);
      listener.addWindowListener(view);
      view.setDefaultCloseOperation(WindowConstants.HIDE_ON_CLOSE);
      instance.set(view);

      IJ.register(FindFociView.class);

      showInstance(view);
      IJ.showStatus("FindFoci ready");
    } catch (final ExceptionInInitializerError ex) {
      exception = ex;
      errorMessage = "Failed to initialize class: " + ex.getMessage();
    } catch (final LinkageError ex) {
      exception = ex;
      errorMessage = "Failed to link class: " + ex.getMessage();
    } catch (final ClassNotFoundException ex) {
      exception = ex;
      errorMessage = "Failed to find class: " + ex.getMessage()
          + "\nCheck you have beansbinding-1.2.1.jar on your classpath\n";
    } catch (final Throwable ex) {
      exception = ex;
      errorMessage = ex.getMessage();
    } finally {
      if (exception != null) {
        final StringWriter sw = new StringWriter();
        final PrintWriter pw = new PrintWriter(sw);
        pw.write(errorMessage);
        pw.append('\n');
        exception.printStackTrace(pw);
        IJ.log(sw.toString());
      }
    }
  }

  private static void showNewInstance() {
    final FindFociModel model = new FindFociModel();
    model.setResultsDirectory(System.getProperty("java.io.tmpdir"));
    final FindFociController controller = new ImageJController(model);
    final FindFociListener listener = new FindFociListener(model);

    // Track when the image changes to a new slice
    ImagePlus.addImageListener(listener);
    model.addPropertyChangeListener("selectedImage", listener);

    final FindFociView view = new FindFociView(model, controller);
    listener.addWindowListener(view);
    view.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);

    showInstance(view);
  }

  private static void showInstance(FindFociView view) {
    WindowManager.addWindow(view);
    view.setVisible(true);
    view.toFront();
  }
}
