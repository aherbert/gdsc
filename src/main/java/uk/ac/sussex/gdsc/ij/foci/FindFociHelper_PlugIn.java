/*-
 * #%L
 * Genome Damage and Stability Centre ImageJ Plugins
 *
 * Software for microscopy image analysis
 * %%
 * Copyright (C) 2011 - 2022 Alex Herbert
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

package uk.ac.sussex.gdsc.ij.foci;

import ij.IJ;
import ij.WindowManager;
import ij.plugin.PlugIn;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.WindowConstants;
import uk.ac.sussex.gdsc.ij.UsageTracker;
import uk.ac.sussex.gdsc.ij.foci.gui.FindFociHelperView;
import uk.ac.sussex.gdsc.ij.foci.gui.OptimiserView;

/**
 * Create a window that allows the user to pick ROI points and have them mapped to the closest
 * maxima found by the FindFoci algorithm.
 */
public class FindFociHelper_PlugIn implements PlugIn {
  private static final AtomicReference<FindFociHelperView> instance = new AtomicReference<>();

  /** {@inheritDoc} */
  @Override
  public void run(String arg) {
    UsageTracker.recordPlugin(this.getClass(), arg);
    showFindFociPickerWindow();
  }

  private void showFindFociPickerWindow() {
    if (showInstance()) {
      return;
    }

    IJ.showStatus("Initialising FindFoci Helper ...");

    String errorMessage = null;
    Throwable exception = null;

    try {
      Class.forName("org.jdesktop.beansbinding.Property", false, this.getClass().getClassLoader());

      // it exists on the classpath
      final FindFociHelperView view = new FindFociHelperView();
      view.addWindowListener(new WindowAdapter() {
        @Override
        public void windowClosing(WindowEvent event) {
          WindowManager.removeWindow(view);
        }
      });
      view.setDefaultCloseOperation(WindowConstants.HIDE_ON_CLOSE);
      instance.set(view);

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

  private static boolean showInstance() {
    final FindFociHelperView view = instance.get();
    if (view != null) {
      WindowManager.addWindow(view);
      view.setVisible(true);
      view.toFront();
      return true;
    }
    return false;
  }
}
