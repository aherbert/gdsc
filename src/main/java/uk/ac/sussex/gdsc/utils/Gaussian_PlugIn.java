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

package uk.ac.sussex.gdsc.utils;

import ij.ImagePlus;
import ij.gui.GenericDialog;
import ij.plugin.PlugIn;
import ij.process.FloatProcessor;
import java.util.concurrent.atomic.AtomicReference;
import uk.ac.sussex.gdsc.UsageTracker;

/**
 * Creates a Gaussian image.
 */
public class Gaussian_PlugIn implements PlugIn {
  private static final String TITLE = "Gaussian";

  /**
   * Contains the settings that are the re-usable state of the plugin.
   */
  private static class Settings {
    /** The last settings used by the plugin. This should be updated after plugin execution. */
    private static final AtomicReference<Settings> lastSettings =
        new AtomicReference<>(new Settings());

    int width = 256;
    int height = 256;
    float amplitude = 255;
    float x = 100;
    float y = 130;
    float sx = 20;
    float sy = 10;
    float angle;
    float noise = 10f;

    /**
     * Default constructor.
     */
    Settings() {
      // Do nothing
    }

    /**
     * Copy constructor.
     *
     * @param source the source
     */
    private Settings(Settings source) {
      width = source.width;
      height = source.height;
      amplitude = source.amplitude;
      x = source.x;
      y = source.y;
      sx = source.sx;
      sy = source.sy;
      angle = source.angle;
      noise = source.noise;
    }

    /**
     * Copy the settings.
     *
     * @return the settings
     */
    Settings copy() {
      return new Settings(this);
    }

    /**
     * Load a copy of the settings.
     *
     * @return the settings
     */
    static Settings load() {
      return lastSettings.get().copy();
    }

    /**
     * Save the settings.
     */
    void save() {
      lastSettings.set(this);
    }
  }

  /** {@inheritDoc} */
  @Override
  public void run(String arg) {
    UsageTracker.recordPlugin(this.getClass(), arg);
    final Settings settings = Settings.load();
    final GenericDialog gd = new GenericDialog(TITLE);
    gd.addNumericField("Width", settings.width, 0);
    gd.addNumericField("Height", settings.height, 0);
    gd.addNumericField("Amplitude", settings.amplitude, 0);
    gd.addNumericField("X", settings.x, 1);
    gd.addNumericField("Y", settings.y, 1);
    gd.addNumericField("X_sd", settings.sx, 1);
    gd.addNumericField("Y_sd", settings.sy, 1);
    gd.addSlider("Angle", 0, 180, settings.angle);
    gd.addNumericField("Noise", settings.noise, 1);

    gd.showDialog();
    if (gd.wasCanceled()) {
      return;
    }

    settings.width = (int) gd.getNextNumber();
    settings.height = (int) gd.getNextNumber();
    settings.amplitude = (float) gd.getNextNumber();
    settings.x = (float) gd.getNextNumber();
    settings.y = (float) gd.getNextNumber();
    settings.sx = (float) gd.getNextNumber();
    settings.sy = (float) gd.getNextNumber();
    settings.angle = (float) gd.getNextNumber();
    settings.noise = (float) gd.getNextNumber();
    settings.save();

    final float[] img =
        createGaussian(settings.width, settings.height, new float[] {settings.amplitude},
            new float[] {settings.x}, new float[] {settings.y}, new float[] {settings.sx},
            new float[] {settings.sy}, new float[] {(float) Math.toRadians(settings.angle)});
    final FloatProcessor fp = new FloatProcessor(settings.width, settings.height, img, null);
    if (settings.noise > 0) {
      fp.noise(settings.noise);
    }
    new ImagePlus(TITLE, fp).show();
  }

  private static float[] createGaussian(int width, int height, float[] amplitude, float[] xpos,
      float[] ypos, float[] sx, float[] sy, float[] angle) {
    final float[] img = new float[width * height];

    for (int i = 0; i < 1; i++) {
      final float sigma_x = sx[i];
      final float sigma_y = sy[i];
      final float theta = angle[i];

      final float a = (float) (Math.cos(theta) * Math.cos(theta) / (2 * sigma_x * sigma_x)
          + Math.sin(theta) * Math.sin(theta) / (2 * sigma_y * sigma_y));
      final float b = (float) (Math.sin(2 * theta) / (4 * sigma_x * sigma_x)
          - Math.sin(2 * theta) / (4 * sigma_y * sigma_y));
      final float c = (float) (Math.sin(theta) * Math.sin(theta) / (2 * sigma_x * sigma_x)
          + Math.cos(theta) * Math.cos(theta) / (2 * sigma_y * sigma_y));

      int index = 0;
      for (int yi = 0; yi < height; yi++) {
        for (int xi = 0; xi < width; xi++) {
          img[index++] += gaussian(xi, yi, amplitude[i], xpos[i], ypos[i], a, b, c);
        }
      }
    }

    return img;
  }

  /**
   * Generic form of the 2D Gaussian.
   *
   * @see <a
   *      href="https://en.wikipedia.org/wiki/Gaussian_function#Two-dimensional_Gaussian_function">2D
   *      Gaussian function</a>
   */
  // CHECKSTYLE.OFF: ParameterName
  private static float gaussian(float x, float y, float amplitude, float x0, float y0, float a,
      float b, float c) {
    return (float) (amplitude * Math
        .exp(-(a * (x - x0) * (x - x0) + 2 * b * (x - x0) * (y - y0) + c * (y - y0) * (y - y0))));
  }
}
