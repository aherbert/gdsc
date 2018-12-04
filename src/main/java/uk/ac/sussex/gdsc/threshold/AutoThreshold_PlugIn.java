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

package uk.ac.sussex.gdsc.threshold;

import uk.ac.sussex.gdsc.UsageTracker;
import uk.ac.sussex.gdsc.core.threshold.AutoThreshold;

import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.Undo;
import ij.WindowManager;
import ij.gui.GenericDialog;
import ij.gui.YesNoCancelDialog;
import ij.plugin.MontageMaker;
import ij.plugin.PlugIn;
import ij.process.ImageConverter;
import ij.process.ImageProcessor;
import ij.process.StackConverter;

// Autothreshold segmentation
// Following the guidelines at http://pacific.mpi-cbg.de/wiki/index.php/PlugIn_Design_Guidelines
// ImageJ plugin by G. Landini at bham. ac. uk
// 1.0 never released
// 1.1 2009/Apr/08 Undo single images, fixed the stack returning to slice 1
// 1.2 2009/Apr/11 global stack threshold, option to avoid displaying, fixed the stack returning to
// slice 1, fixed upper border of montage,
// 1.3 2009/Apr/11 fixed Stack option with 'Try all' method
// 1.4 2009/Apr/11 fixed 'ignore black' and 'ignore white' for stack histograms
// 1.5 2009/Apr/12 Mean method, MinimumErrorIterative method , enahanced Triangle
// 1.6 2009/Apr/14 Reverted IsoData to a copy of IJ's code as the other version does not always
// return the same value as IJ
// 1.7 2009/Apr/14 small fixes, restore histogram in Triangle if reversed
// 1.8 2009/Jun/01 Set the threshold to foreground colour
// 1.9 2009/Oct/30 report both isodata and IJ's default methods
// 1.10 2010/May/25 We are a package!
// 1.10 2011/Jan/31 J. Schindelin added support for 16 bit images and speedup of the Huang method
// 1.11 2011/Mar/31 Alex Herbert submitted a patch to threshold the stack from any slice position
// 1.12 2011/Apr/09 Fixed: Minimum with 16bit images (search data range only), setting threshold
// without applying the mask, Yen and Isodata with 16 bits offset images, histogram bracketing to
// speed up
// 1.13 2011/Apr/13 Revised the way 16bit thresholds are shown
// 1.14 2011/Apr/14 IsoData issues a warning if threhsold not found

/**
 * AutoThreshold segmentation plugin.
 *
 * <p>Adapted from the ImageJ plugin by G. Landini at bham. ac. uk.
 */
public class AutoThreshold_PlugIn implements PlugIn {
  private static final String TITLE = "Auto Threshold";

  // Original method variable changed to static to allow repeatability of dialog
  private static String myMethod = AutoThreshold.Method.OTSU.toString();
  private static boolean noBlack = false;
  private static boolean noWhite = false;
  private static boolean doIwhite = true;
  private static boolean doIset = false;
  private static boolean doIlog = false;
  private static boolean doIstack = false;
  private static boolean doIstackHistogram = false;

  /**
   * The multiplier used within the MeanPlusSD calculation.
   */
  private static double stdDevMultiplier = 3;

  /** Ask for parameters and then execute. */
  @Override
  public void run(String arg) {
    UsageTracker.recordPlugin(this.getClass(), arg);

    // 1 - Obtain the currently active image:
    final ImagePlus imp = IJ.getImage();

    if (null == imp) {
      IJ.showMessage("There must be at least one image open");
      return;
    }

    if (imp.getBitDepth() == 32) {
      final YesNoCancelDialog d = new YesNoCancelDialog(IJ.getInstance(), "Auto Threshold",
          "Convert 32-bit image to 16-bit for thresholding");
      d.setVisible(true);
      if (d.cancelPressed() || !d.yesPressed()) {
        return;
      }
      final ImageConverter ic = new ImageConverter(imp);
      ic.convertToGray16();
    }

    if (imp.getBitDepth() != 8 && imp.getBitDepth() != 16) {
      IJ.showMessage("Error", "Only 8-bit and 16-bit images are supported");
      return;
    }

    // 2 - Ask for parameters:
    final GenericDialog gd = new GenericDialog(TITLE);
    gd.addMessage("Auto Threshold v1.14");
    final String[] methods2 = AutoThreshold.getMethods(true);
    final String[] methods = new String[methods2.length + 1];
    methods[0] = "Try all";
    System.arraycopy(methods2, 0, methods, 1, methods2.length);
    gd.addChoice("Method", methods, myMethod);
    gd.addNumericField("StdDev_multiplier", stdDevMultiplier, 2);
    final String[] labels = new String[2];
    final boolean[] states = new boolean[2];
    labels[0] = "Ignore_black";
    states[0] = noBlack;
    labels[1] = "Ignore_white";
    states[1] = noWhite;
    gd.addCheckboxGroup(1, 2, labels, states);
    gd.addCheckbox("White objects on black background", doIwhite);
    gd.addCheckbox("SetThreshold instead of Threshold (single images)", doIset);
    gd.addCheckbox("Show threshold values in log window", doIlog);
    if (imp.getStackSize() > 1) {
      gd.addCheckbox("Stack", doIstack);
      gd.addCheckbox("Use_stack_histogram", doIstackHistogram);
    }
    gd.addMessage(
        "The thresholded result of 8 & 16 bit images is shown\nin white [255] in 8 bits.\nFor 16 bit images, results of \'Try all\' and single slices\nof a stack are shown in white [65535] in 16 bits.\nUnsuccessfully thresholded images are left unchanged.");
    gd.addHelp(uk.ac.sussex.gdsc.help.UrlUtils.UTILITY);

    gd.showDialog();
    if (gd.wasCanceled()) {
      return;
    }

    // 3 - Retrieve parameters from the dialog
    myMethod = gd.getNextChoice();
    stdDevMultiplier = gd.getNextNumber();
    noBlack = gd.getNextBoolean();
    noWhite = gd.getNextBoolean();
    doIwhite = gd.getNextBoolean();
    doIset = gd.getNextBoolean();
    doIlog = gd.getNextBoolean();
    doIstack = false;
    doIstackHistogram = false;

    final int stackSize = imp.getStackSize();
    if (stackSize > 1) {
      doIstack = gd.getNextBoolean();
      doIstackHistogram = gd.getNextBoolean();
      if (doIstackHistogram) {
        doIstack = true;
      }
    }

    // 4 - Execute!
    // long start = System.currentTimeMillis();
    if (myMethod.equals("Try all")) {
      ImageProcessor ip = imp.getProcessor();
      final int xe = ip.getWidth();
      final int ye = ip.getHeight();
      final int ml = methods.length;
      ImagePlus imp2;
      ImagePlus imp3;
      ImageStack tstack = null;
      ImageStack stackNew;
      if (stackSize > 1 && doIstack) {
        boolean doItAnyway = true;
        if (stackSize > 25) {
          final YesNoCancelDialog d = new YesNoCancelDialog(IJ.getInstance(), "Auto Threshold",
              "You might run out of memory.\n \nDisplay " + stackSize
                  + " slices?\n \n \'No\' will process without display and\noutput results to the log window.");
          if (!d.yesPressed()) {
            doIlog = true; // will show in the log window
            doItAnyway = false;
          }
          if (d.cancelPressed()) {
            return;
          }
        }
        if (doIstackHistogram) { // global histogram
          int j;
          int k;
          for (k = 1; k < ml; k++) {
            tstack = new ImageStack(xe, ye);
            for (j = 1; j <= stackSize; j++) {
              imp.setSlice(j);
              ip = imp.getProcessor();
              tstack.addSlice(methods[k], ip.duplicate());
            }
            imp2 = new ImagePlus("Auto Threshold", tstack);
            imp2.updateAndDraw();
            // imp2.show();
            final Object[] result = exec(imp2, methods[k], noWhite, noBlack, doIwhite, doIset,
                doIlog, doIstackHistogram);
            for (j = 1; j <= stackSize; j++) {
              tstack.setSliceLabel(tstack.getSliceLabel(j) + " = " + result[0], j);
            }
            if (doItAnyway) {
              // CanvasResizer cr = new CanvasResizer();
              stackNew = /* cr. */expandStack(tstack, (xe + 2), (ye + 18), 1, 1);
              imp3 = new ImagePlus("Auto Threshold", stackNew);
              imp3.updateAndDraw();
              final int sqrj = 1 + (int) Math.floor(Math.sqrt(stackSize));
              int sqrjp1 = sqrj - 1;
              while (sqrj * sqrjp1 < stackSize) {
                sqrjp1++;
              }
              final MontageMaker mm = new MontageMaker();
              mm.makeMontage(imp3, sqrj, sqrjp1, 1.0, 1, stackSize, 1, 0, true);
              imp2.close();
            }
          }
        } else {
          for (int j = 1; j <= stackSize; j++) {
            imp.setSlice(j);
            ip = imp.getProcessor();
            tstack = new ImageStack(xe, ye);
            for (int k = 1; k < ml; k++) {
              tstack.addSlice(methods[k], ip.duplicate());
            }
            imp2 = new ImagePlus("Auto Threshold", tstack);
            imp2.updateAndDraw();
            if (doIlog) {
              IJ.log("Slice " + j);
            }

            for (int k = 1; k < ml; k++) {
              imp2.setSlice(k);
              final Object[] result = exec(imp2, methods[k], noWhite, noBlack, doIwhite, doIset,
                  doIlog, doIstackHistogram);
              tstack.setSliceLabel(tstack.getSliceLabel(k) + " = " + result[0], k);
            }
            if (doItAnyway) {
              // CanvasResizer cr = new CanvasResizer();
              stackNew = /* cr. */expandStack(tstack, (xe + 2), (ye + 18), 1, 1);
              imp3 = new ImagePlus("Auto Threshold", stackNew);
              imp3.updateAndDraw();
              final MontageMaker mm = new MontageMaker();
              // mm.makeMontage(imp3, 4, 4, 1.0, 1, (ml - 1), 1, 0, true); // 4 columns and 4 rows
              mm.makeMontage(imp3, 6, 3, 1.0, 1, (ml - 1), 1, 0, true);
            }
          }
        }
        imp.setSlice(1);
        if (doItAnyway) {
          IJ.run("Images to Stack", "method=[Copy (center)] title=Montage");
          final ImagePlus montageImp = WindowManager.getCurrentImage();
          if (montageImp.getID() != imp.getID()) {
            montageImp.setTitle(imp.getTitle() + " Thresholds");
            montageImp.updateAndDraw();
          }
        }
        return;
      }
      // single image try all
      tstack = new ImageStack(xe, ye);
      for (int k = 1; k < ml; k++) {
        tstack.addSlice(methods[k], ip.duplicate());
      }
      imp2 = new ImagePlus("Auto Threshold", tstack);
      imp2.updateAndDraw();

      IJ.log("Auto Threshold ...");
      for (int k = 1; k < ml; k++) {
        imp2.setSlice(k);
        // IJ.log("Analyzing slice with "+methods[k]);
        final long start = System.currentTimeMillis();
        final Object[] result =
            exec(imp2, methods[k], noWhite, noBlack, doIwhite, doIset, doIlog, doIstackHistogram);
        IJ.log("  " + methods[k] + " = " + result[0] + " ("
            + IJ.d2s((System.currentTimeMillis() - start) / 1000.0, 4) + "s)");
        tstack.setSliceLabel(tstack.getSliceLabel(k) + " = " + result[0], k);
      }
      // imp2.setSlice(1);
      // CanvasResizer cr = new CanvasResizer();
      stackNew = /* cr. */expandStack(tstack, (xe + 2), (ye + 18), 1, 1);
      imp3 = new ImagePlus("Auto Threshold", stackNew);
      imp3.updateAndDraw();
      final MontageMaker mm = new MontageMaker();
      // mm.makeMontage(imp3, 4, 4, 1.0, 1, (ml - 1), 1, 0, true); // 4 columns and 4 rows
      mm.makeMontage(imp3, 6, 3, 1.0, 1, (ml - 1), 1, 0, true); // 4 columns and 4 rows
      final ImagePlus montageImp = WindowManager.getCurrentImage();
      if (montageImp.getID() != imp.getID()) {
        montageImp.setTitle(imp.getTitle() + " Thresholds");
        montageImp.updateAndDraw();
      }
      return;
    }
    // selected a method
    boolean success = false;
    if (stackSize > 1 && (doIstack || doIstackHistogram)) { // whole stack
      if (doIstackHistogram) {// one global histogram
        final Object[] result =
            exec(imp, myMethod, noWhite, noBlack, doIwhite, doIset, doIlog, doIstackHistogram);
        if (((Integer) result[0]) != -1 && imp.getBitDepth() == 16) {
          new StackConverter(imp).convertToGray8();
        }
      } else { // slice by slice
        success = true;
        for (int k = 1; k <= stackSize; k++) {
          imp.setSlice(k);
          final Object[] result =
              exec(imp, myMethod, noWhite, noBlack, doIwhite, doIset, doIlog, doIstackHistogram);
          if (((Integer) result[0]) == -1) {
            success = false;// the threshold existed
          }
        }
        if (success && imp.getBitDepth() == 16) {
          new StackConverter(imp).convertToGray8();
        }
      }
      imp.setSlice(1);
    } else { // just one slice, leave as it is
      final Object[] result =
          exec(imp, myMethod, noWhite, noBlack, doIwhite, doIset, doIlog, doIstackHistogram);
      if (((Integer) result[0]) != -1 && stackSize == 1 && imp.getBitDepth() == 16) {
        imp.setDisplayRange(0, 65535);
        imp.setProcessor(null, imp.getProcessor().convertToByte(true));
      }
    }
    // 5 - If all went well, show the image:
    // not needed here as the source image is binarised
  }

  private static ImageStack expandStack(ImageStack stackOld, int wNew, int hNew, int xOff,
      int yOff) {
    final int nFrames = stackOld.getSize();
    final ImageProcessor ipOld = stackOld.getProcessor(1);

    final ImageStack stackNew = new ImageStack(wNew, hNew, stackOld.getColorModel());
    ImageProcessor ipNew;

    for (int i = 1; i <= nFrames; i++) {
      IJ.showProgress((double) i / nFrames);
      ipNew = ipOld.createProcessor(wNew, hNew);
      ipNew.setValue(0.0);
      ipNew.fill();
      ipNew.insert(stackOld.getProcessor(i), xOff, yOff);
      stackNew.addSlice(stackOld.getSliceLabel(i), ipNew);
    }
    return stackNew;
  }

  /**
   * Execute the plugin functionality.
   *
   * @param imp the image
   * @param myMethod the my method
   * @param noWhite the no white
   * @param noBlack the no black
   * @param doIwhite flag to set the foreground as white
   * @param doIset flag to set the threshold on the image
   * @param doIlog flag to log the threshold
   * @param doIstackHistogram flag to use the stack histogram
   * @return an Object[] array with the threshold and the ImagePlus. Does NOT show the new, image;
   *         just returns it.
   */
  public Object[] exec(ImagePlus imp, String myMethod, boolean noWhite, boolean noBlack,
      boolean doIwhite, boolean doIset, boolean doIlog, boolean doIstackHistogram) {
    // 0 - Check validity of parameters
    if (null == imp) {
      return null;
    }
    int threshold = -1;
    final int currentSlice = imp.getCurrentSlice();
    ImageProcessor ip = imp.getProcessor();
    final int xe = ip.getWidth();
    final int ye = ip.getHeight();
    int x;
    int y;
    int c = 0;
    int b = imp.getBitDepth() == 8 ? 255 : 65535;
    if (doIwhite) {
      c = b;
      b = 0;
    }
    final int[] data = (ip.getHistogram());
    int[] temp = new int[data.length];

    IJ.showStatus("Thresholding...");

    // 1 Do it
    if (imp.getStackSize() == 1) {
      ip.snapshot();
      Undo.setup(Undo.FILTER, imp);
    } else if (doIstackHistogram) {
      // get the stack histogram into the data[] array
      temp = data;
      for (int i = 1; i <= imp.getStackSize(); i++) {
        // Ignore the slice that has already been included
        if (i == currentSlice) {
          continue;
        }
        imp.setSliceWithoutUpdate(i);
        ip = imp.getProcessor();
        temp = ip.getHistogram();
        for (int j = 0; j < data.length; j++) {
          data[j] += temp[j];
          // IJ.log(""+j+": "+ data[j]);
        }
      }
      imp.setSliceWithoutUpdate(currentSlice);
    }

    if (noBlack) {
      data[0] = 0;
    }
    if (noWhite) {
      data[data.length - 1] = 0;
    }

    threshold = AutoThreshold.getThreshold(myMethod, data);

    // show treshold in log window if required
    if (doIlog) {
      IJ.log(myMethod + ": " + threshold);
    }
    if (threshold > -1) {
      // threshold it
      if (doIset) {
        if (doIwhite) {
          imp.getProcessor().setThreshold(threshold + 1, data.length - 1, ImageProcessor.RED_LUT);// IJ.setThreshold(threshold+1,
                                                                                                  // data.length
                                                                                                  // -
                                                                                                  // 1);
        } else {
          imp.getProcessor().setThreshold(0, threshold, ImageProcessor.RED_LUT);// IJ.setThreshold(0,threshold);
        }
      } else {
        imp.setDisplayRange(0, Math.max(b, c)); // otherwise we can never set the threshold
        if (doIstackHistogram) {
          for (int j = 1; j <= imp.getStackSize(); j++) {
            imp.setSliceWithoutUpdate(j);
            ip = imp.getProcessor();
            // IJ.log(""+j+": "+ data[j]);
            for (y = 0; y < ye; y++) {
              for (x = 0; x < xe; x++) {
                if (ip.getPixel(x, y) > threshold) {
                  ip.putPixel(x, y, c);
                } else {
                  ip.putPixel(x, y, b);
                }
              }
            }
          } // threshold all of them
          imp.setSliceWithoutUpdate(currentSlice);
        } else {
          for (y = 0; y < ye; y++) {
            for (x = 0; x < xe; x++) {
              if (ip.getPixel(x, y) > threshold) {
                ip.putPixel(x, y, c);
              } else {
                ip.putPixel(x, y, b);
              }
            }
          }
        }
        imp.getProcessor().setThreshold(data.length - 1, data.length - 1,
            ImageProcessor.NO_LUT_UPDATE);
      }
    }
    // IJ.showProgress((double)(255-i)/255);
    imp.updateAndDraw();
    // 2 - Return the threshold and the image
    return new Object[] {threshold, imp};
  }
}
