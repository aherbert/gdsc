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

package uk.ac.sussex.gdsc.utils;

import ij.IJ;
import ij.ImagePlus;
import ij.gui.Roi;
import ij.plugin.filter.PlugInFilter;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;
import java.awt.Rectangle;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.logging.Level;
import java.util.logging.Logger;
import uk.ac.sussex.gdsc.UsageTracker;
import uk.ac.sussex.gdsc.core.annotation.Nullable;
import uk.ac.sussex.gdsc.core.ij.ImageJPluginLoggerHelper;
import uk.ac.sussex.gdsc.core.utils.SimpleArrayUtils;

/**
 * Fits a circular 2D Gaussian.
 */
@SuppressWarnings({"unchecked", "rawtypes"})
public class GaussianFit_PlugIn implements PlugInFilter {
  /**
   * The name of the class implementing the Gaussian fit method.
   *
   * <p>This class requires another package on the runtime classpath and is created using
   * reflection.
   */
  private static final String GAUSSIAN_FIT_CLASS = "uk.ac.sussex.gdsc.smlm.ij.plugins.GaussianFit";

  private static final boolean FITTING_ENABLED;
  private static final String ERROR_MESSAGE;
  private static final Throwable EXCEPTION;

  // Try and perform a Gaussian fit
  static {
    boolean fittingEnabled = false;
    String errorMessage = null;
    Throwable exception = null;

    try {
      // Get a class in this package to find the package class loader
      final Class clazz =
          Class.forName(GAUSSIAN_FIT_CLASS, true, Gaussian_PlugIn.class.getClassLoader());

      // ... it exists on the classpath

      final int size = 16;
      final int mu = size / 2;
      final float s0 = 2;
      final float amplitude = 10;

      // Create a 2D Gaussian curve
      final float[] data = new float[size * size];
      for (int y = 0; y < size; y++) {
        for (int x = 0; x < size; x++) {
          data[y * size + x] = (float) (amplitude
              * (Math.exp(-0.5 * ((mu - x) * (mu - x) + (mu - y) * (mu - y)) / (s0 * s0))));
        }
      }

      // Try a fit.
      // Use reflection to allow building without the SMLM plugins on the classpath
      final Method m = clazz.getDeclaredMethod("fit", float[].class, int.class, int.class);
      final double[] fit = (double[]) m.invoke(clazz.newInstance(), data, size, size);
      if (fit != null) {
        fittingEnabled = true;
      }
    } catch (final ExceptionInInitializerError ex) {
      exception = ex;
      errorMessage = "Failed to initialize class: " + ex.getMessage();
    } catch (final LinkageError ex) {
      exception = ex;
      errorMessage = "Failed to link class: " + ex.getMessage();
    } catch (final ClassNotFoundException ex) {
      exception = ex;
      errorMessage = "Failed to find class: " + ex.getMessage();
    } catch (final Exception ex) {
      exception = ex;
      errorMessage = ex.getMessage();
    } finally {
      FITTING_ENABLED = fittingEnabled;
      ERROR_MESSAGE = errorMessage;
      EXCEPTION = exception;
    }

    if (!fittingEnabled && IJ.debugMode) {
      ImageJPluginLoggerHelper.getLogger(GaussianFit_PlugIn.class.getName()).log(Level.WARNING,
          () -> ERROR_MESSAGE + ". The GDSC 2D Gaussian fit functionality will be disabled.");
    }
  }

  /**
   * Fit the data using a 2D Gaussian.
   *
   * <p>Returns null if fitting failed.
   *
   * @param data the data
   * @param width the width
   * @param height the height
   * @return The fitted Gaussian parameters (Background, Amplitude, x0, x1, s)
   */
  @Nullable
  public double[] fit(float[] data, int width, int height) {
    SimpleArrayUtils.hasData2D(width, height, data);

    try {
      // Use reflection to allow building without the SMLM plugins on the classpath
      final Class c = Class.forName(GAUSSIAN_FIT_CLASS, true, this.getClass().getClassLoader());
      final Method m = c.getDeclaredMethod("fit", float[].class, int.class, int.class);
      return (double[]) m.invoke(c.newInstance(), data, width, height);
    } catch (final ExceptionInInitializerError | RuntimeException | ClassNotFoundException
        | NoSuchMethodException | IllegalAccessException | InvocationTargetException
        | InstantiationException ex) {
      Logger.getLogger(getClass().getName()).log(Level.WARNING,
          () -> "Fitting failed: " + ex.getMessage());
      return null;
    }
  }

  /**
   * Checks if is fitting enabled.
   *
   * @return true if fitting is possible.
   */
  public static boolean isFittingEnabled() {
    return FITTING_ENABLED;
  }

  /**
   * Gets the error message if fitting is not possible.
   *
   * @return the errorMessage if fitting is not possible.
   */
  public static String getErrorMessage() {
    return ERROR_MESSAGE;
  }

  /**
   * Gets the exception if fitting is not possible.
   *
   * @return the exception if fitting is not possible.
   */
  public static Throwable getException() {
    return EXCEPTION;
  }

  /** {@inheritDoc} */
  @Override
  public int setup(String arg, ImagePlus imp) {
    UsageTracker.recordPlugin(this.getClass(), arg);
    if (!isFittingEnabled()) {
      IJ.error("Fitting is not enabled.\n \n" + ERROR_MESSAGE);
      return DONE;
    }
    final Roi roi = imp.getRoi();
    if (roi == null || !roi.isArea()) {
      IJ.error("Require a region ROI");
      return DONE;
    }
    return DOES_ALL | NO_CHANGES;
  }

  /** {@inheritDoc} */
  @Override
  public void run(ImageProcessor ip) {
    if (!isFittingEnabled()) {
      IJ.error("Fitting is not enabled.\n \n" + ERROR_MESSAGE);
      return;
    }
    FloatProcessor fp = ip.toFloat(0, null);
    final Rectangle bounds = fp.getRoi();
    fp = (FloatProcessor) fp.crop();

    final double[] params = fit((float[]) fp.getPixels(), fp.getWidth(), fp.getHeight());
    if (params != null) {
      IJ.log(String.format(
          "f(x,y) = %g + %g exp -( ( (x-%g)^2 / (2 * %g^2) + (y-%g)^2 ) / (2 * %g^2) )", params[0],
          params[1], bounds.x + params[2], params[4], bounds.y + params[3], params[5]));
    }
  }
}
