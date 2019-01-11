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
import uk.ac.sussex.gdsc.foci.gui.FindFociHelperView;
import uk.ac.sussex.gdsc.foci.gui.OptimiserView;

import ij.IJ;
import ij.WindowManager;
import ij.plugin.PlugIn;

import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.io.PrintWriter;
import java.io.StringWriter;

import javax.swing.WindowConstants;

/**
 * Create a window that allows the user to pick ROI points and have them mapped to the closest
 * maxima found by the FindFoci algorithm.
 */
public class FindFociHelper_PlugIn implements PlugIn, WindowListener {
  private static FindFociHelperView instance;

  /** {@inheritDoc} */
  @Override
  public void run(String arg) {
    UsageTracker.recordPlugin(this.getClass(), arg);
    showFindFociPickerWindow();
  }

  private void showFindFociPickerWindow() {
    if (instance != null) {
      showInstance();
      return;
    }

    IJ.showStatus("Initialising FindFoci Helper ...");

    String errorMessage = null;
    Throwable exception = null;

    try {
      Class.forName("org.jdesktop.beansbinding.Property", false, this.getClass().getClassLoader());

      // it exists on the classpath
      instance = new FindFociHelperView();
      instance.addWindowListener(this);
      instance.setDefaultCloseOperation(WindowConstants.HIDE_ON_CLOSE);

      IJ.register(OptimiserView.class);

      showInstance();
      IJ.showStatus("FindFoci Helper ready");
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

  private static void showInstance() {
    WindowManager.addWindow(instance);
    instance.setVisible(true);
    instance.toFront();
  }

  @Override
  public void windowOpened(WindowEvent event) {
    // Ignore
  }

  @Override
  public void windowClosing(WindowEvent event) {
    WindowManager.removeWindow(instance);
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
}
