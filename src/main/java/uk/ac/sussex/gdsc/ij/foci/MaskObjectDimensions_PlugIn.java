/*-
 * #%L
 * Genome Damage and Stability Centre ImageJ Plugins
 *
 * Software for microscopy image analysis
 * %%
 * Copyright (C) 2011 - 2026 Alex Herbert
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
import ij.ImagePlus;
import ij.ImageStack;
import ij.gui.Line;
import ij.gui.Overlay;
import ij.measure.Calibration;
import ij.plugin.filter.PlugInFilter;
import ij.process.ImageProcessor;
import ij.text.TextWindow;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.commons.math3.linear.BlockRealMatrix;
import org.apache.commons.math3.linear.EigenDecomposition;
import org.apache.commons.math3.linear.RealVector;
import uk.ac.sussex.gdsc.core.ij.ImageJUtils;
import uk.ac.sussex.gdsc.core.ij.gui.ExtendedGenericDialog;
import uk.ac.sussex.gdsc.core.trees.DoubleDistanceFunctions;
import uk.ac.sussex.gdsc.core.trees.KdTrees;
import uk.ac.sussex.gdsc.core.trees.ObjDoubleKdTree;
import uk.ac.sussex.gdsc.core.utils.LocalList;
import uk.ac.sussex.gdsc.core.utils.MathUtils;
import uk.ac.sussex.gdsc.ij.UsageTracker;
import uk.ac.sussex.gdsc.ij.help.HelpUrls;

/**
 * For each unique pixel value in the mask (defining an object), analyse the pixels values and
 * calculate the inertia tensor. Then produce the dimensions of the object on the axes of the
 * moments of inertia.
 */
public class MaskObjectDimensions_PlugIn implements PlugInFilter {
  /** The plugin title. */
  private static final String TITLE = "Mask Object Dimensions";

  private static final int FLAGS = DOES_16 + DOES_8G;

  private static AtomicReference<TextWindow> resultsWindowRef = new AtomicReference<>();

  private ImagePlus imp;

  /** The plugin settings. */
  private Settings settings;

  /**
   * Contains the settings that are the re-usable state of the plugin.
   */
  private static class Settings {
    /** The last settings used by the plugin. This should be updated after plugin execution. */
    private static final AtomicReference<Settings> lastSettings =
        new AtomicReference<>(new Settings());

    static final String[] sortMethods = new String[] {"Value", "Area", "CoM"};
    static final int SORT_VALUE = 0;
    static final int SORT_AREA = 1;
    static final int SORT_COM = 2;

    double mergeDistance;
    boolean showOverlay;
    boolean clearTable;
    boolean showVectors;
    int sortMethod;

    Settings() {
      showOverlay = true;
      clearTable = true;
      sortMethod = SORT_VALUE;
    }

    Settings(Settings source) {
      mergeDistance = source.mergeDistance;
      showOverlay = source.showOverlay;
      clearTable = source.clearTable;
      showVectors = source.showVectors;
      sortMethod = source.sortMethod;
    }

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

  /**
   * Define an object in the mask.
   */
  static class MaskObject {
    double cx;
    double cy;
    double cz;
    int size;
    int[] values;
    int[] lower;
    int[] upper;

    MaskObject(double cx, double cy, double cz, int size, int value) {
      this.cx = cx;
      this.cy = cy;
      this.cz = cz;
      this.size = size;
      values = new int[] {value};
    }

    int getValue() {
      return values[0];
    }

    double distance2(MaskObject that) {
      final double dx = this.cx - that.cx;
      final double dy = this.cy - that.cy;
      final double dz = this.cz - that.cz;
      return dx * dx + dy * dy + dz * dz;
    }

    void merge(MaskObject that) {
      final int n2 = this.size + that.size;
      this.cx = (this.cx * this.size + that.cx * that.size) / n2;
      this.cy = (this.cy * this.size + that.cy * that.size) / n2;
      this.cz = (this.cz * this.size + that.cz * that.size) / n2;
      this.size = n2;

      // Merge the values
      final int[] newValues = new int[this.values.length + that.values.length];
      System.arraycopy(this.values, 0, newValues, 0, this.values.length);
      System.arraycopy(that.values, 0, newValues, this.values.length, that.values.length);
      this.values = newValues;

      // Remove values from the other object
      that.size = 0;
    }

    void initialiseBounds() {
      lower = new int[] {(int) cx, (int) cy, (int) cz};
      upper = lower.clone();
    }

    void updateBounds(int x, int y, int z) {
      if (lower[0] > x) {
        lower[0] = x;
      }
      if (lower[1] > y) {
        lower[1] = y;
      }
      if (lower[2] > z) {
        lower[2] = z;
      }
      if (upper[0] < x) {
        upper[0] = x;
      }
      if (upper[1] < y) {
        upper[1] = y;
      }
      if (upper[2] < z) {
        upper[2] = z;
      }
    }
  }

  /** {@inheritDoc} */
  @Override
  public int setup(String arg, ImagePlus imp) {
    UsageTracker.recordPlugin(this.getClass(), arg);

    if (imp == null) {
      IJ.noImage();
      return DONE;
    }
    this.imp = imp;
    if (!showDialog()) {
      return DONE;
    }
    return FLAGS;
  }

  private boolean showDialog() {
    final ExtendedGenericDialog gd = new ExtendedGenericDialog(TITLE);

    gd.addMessage(
        "For each object defined with a unique pixel value,\ncompute the dimensions along the "
            + "axes of the inertia tensor");

    settings = Settings.load();
    gd.addSlider("Merge_distance", 0, 15, settings.mergeDistance);
    gd.addCheckbox("Show_overlay", settings.showOverlay);
    gd.addCheckbox("Clear_table", settings.clearTable);
    gd.addCheckbox("Show_vectors", settings.showVectors);
    gd.addChoice("Sort_method", Settings.sortMethods, settings.sortMethod);

    gd.addHelp(HelpUrls.getUrl("mask-object-dimensions"));
    gd.showDialog();

    if (gd.wasCanceled()) {
      return false;
    }

    settings.mergeDistance = Math.abs(gd.getNextNumber());
    settings.showOverlay = gd.getNextBoolean();
    settings.clearTable = gd.getNextBoolean();
    settings.showVectors = gd.getNextBoolean();
    settings.sortMethod = gd.getNextChoiceIndex();
    settings.save();

    return true;
  }

  /** {@inheritDoc} */
  @Override
  public void run(ImageProcessor inputProcessor) {
    // Extract the current z-stack
    final int channel = imp.getChannel();
    final int frame = imp.getFrame();
    final int[] dimensions = imp.getDimensions();
    final int maxx = dimensions[0];
    final int maxy = dimensions[1];
    final int maxz = dimensions[3];

    final int[] histogram = new int[(imp.getBitDepth() == 8) ? 256 : 65536];
    final int size = maxx * maxy;
    final int[] image = new int[size * maxz];
    final ImageStack stack = imp.getImageStack();
    for (int slice = 1, j = 0; slice <= maxz; slice++) {
      final int index = imp.getStackIndex(channel, slice, frame);
      final ImageProcessor ip = stack.getProcessor(index);
      for (int i = 0; i < size; i++, j++) {
        final int value = ip.get(i);
        histogram[value]++;
        image[j] = value;
      }
    }

    // Calculate the objects (non-zero pixels)
    MaskObject[] objects = createObjects(image, histogram, maxx, maxy, maxz);
    if (objects.length == 0) {
      IJ.error(TITLE, "No objects in mask");
      return;
    }
    final int max = objects[objects.length - 1].getValue();

    // Iteratively join closest objects
    if (settings.mergeDistance > 0) {
      mergeObjects(objects, image, settings.mergeDistance);
    }

    // Remove merged objects and map the value to the new index
    final int[] objectMap = new int[max + 1];
    int newLength = 0;
    for (int i = 0; i < objects.length; i++) {
      if (objects[i].size == 0) {
        continue;
      }
      objects[newLength] = objects[i];
      objectMap[objects[i].getValue()] = newLength;
      newLength++;
    }
    objects = Arrays.copyOf(objects, newLength);

    // Output lines
    final Overlay overlay = (settings.showOverlay) ? new Overlay() : null;

    // Compute the bounding box for each object. This increases the speed of processing later
    for (final MaskObject o : objects) {
      o.initialiseBounds();
    }
    for (int z = 0, i = 0; z < maxz; z++) {
      for (int y = 0; y < maxy; y++) {
        for (int x = 0; x < maxx; x++, i++) {
          if (image[i] != 0) {
            objects[objectMap[image[i]]].updateBounds(x, y, z);
          }
        }
      }
    }

    // Sort the objects
    if (settings.sortMethod == Settings.SORT_COM) {
      Arrays.sort(objects, (o1, o2) -> {
        if (o1.cx < o2.cx) {
          return -1;
        }
        if (o1.cx > o2.cx) {
          return 1;
        }
        if (o1.cy < o2.cy) {
          return -1;
        }
        if (o1.cy > o2.cy) {
          return 1;
        }
        if (o1.cz < o2.cz) {
          return -1;
        }
        if (o1.cz > o2.cz) {
          return 1;
        }
        return 0;
      });
    } else if (settings.sortMethod == Settings.SORT_AREA) {
      Arrays.sort(objects, (o1, o2) -> {
        if (o1.size < o2.size) {
          return -1;
        }
        if (o1.size > o2.size) {
          return 1;
        }
        return 0;
      });
    }
    // Get the calibrated units
    final Calibration cal = imp.getCalibration();
    String units = cal.getUnits();
    final double calx;
    final double caly;
    final double calz;
    if (cal.getXUnit().equals(cal.getYUnit()) && cal.getXUnit().equals(cal.getZUnit())) {
      calx = cal.pixelWidth;
      caly = cal.pixelHeight;
      calz = cal.pixelDepth;
    } else {
      calx = caly = calz = 1;
      units = "px";
    }

    final TextWindow resultsWindow = createResultsWindow();
    if (settings.clearTable) {
      resultsWindow.getTextPanel().clear();
    }

    // For each object
    for (final MaskObject object : objects) {
      final int objectValue = object.getValue();

      // Set bounds
      final int[] mind = object.lower;
      final int[] maxd = object.upper.clone();
      // Increase the upper bounds by 1 to allow < and >= range checks
      for (int i = 0; i < 3; i++) {
        maxd[i] += 1;
      }

      // Calculate the inertia tensor
      final double[][] tensor = new double[3][3];

      // Remove 0.5 pixel offset for convenience
      final double cx = object.cx - 0.5;
      final double cy = object.cy - 0.5;
      final double cz = object.cz - 0.5;
      for (int z = mind[2]; z < maxd[2]; z++) {
        for (int y = mind[1]; y < maxd[1]; y++) {
          for (int x = mind[0], i = z * size + y * maxx + mind[0]; x < maxd[0]; x++, i++) {
            if (image[i] == objectValue) {
              final double dx = x - cx;
              final double dy = y - cy;
              final double dz = z - cz;
              final double dx2 = dx * dx;
              final double dy2 = dy * dy;
              final double dz2 = dz * dz;

              tensor[0][0] += dy2 + dz2;
              tensor[0][1] -= dx * dy;
              tensor[0][2] -= dx * dz;
              tensor[1][1] += dx2 + dz2;
              tensor[1][2] -= dy * dz;
              tensor[2][2] += dx2 + dy2;
            }
          }
        }
      }

      // Inertia tensor is symmetric
      tensor[1][0] = tensor[0][1];
      tensor[2][0] = tensor[0][2];
      tensor[2][1] = tensor[1][2];

      // Eigen decompose
      final double[] eigenValues = new double[3];
      final double[][] eigenVectors = new double[3][3];

      final BlockRealMatrix matrix = new BlockRealMatrix(3, 3);
      for (int i = 0; i < 3; i++) {
        matrix.setColumn(i, tensor[i]);
      }

      final EigenDecomposition eigen = new EigenDecomposition(matrix);
      for (int i = 0; i < 3; i++) {
        eigenValues[i] = eigen.getRealEigenvalue(i);
        final RealVector v = eigen.getEigenvector(i);
        for (int j = 0; j < 3; j++) {
          eigenVectors[i][j] = v.getEntry(j);
        }
      }

      // Sort
      eigenSort3x3(eigenValues, eigenVectors);

      // Compute the distance along each axis that is within the object.
      // Do this by constructing a line across the entire image in pixel increments,
      // then checking the max and min pixel that are the object

      // TODO - This currently works for blobs where the COM is in the centre.
      // It does not work for objects that are joined that do not touch. It could be
      // made far better by finding the bounding rectangle of an object and then computing
      // the longest line that can be drawn across the bounding rectangle using the
      // tensor axes.

      final double[] com = new double[] {cx + 0.5, cy + 0.5, cz + 0.5};

      final StringBuilder sb = new StringBuilder();
      sb.append(imp.getTitle());
      sb.append('\t').append(units);
      Arrays.sort(object.values);
      sb.append('\t').append(Arrays.toString(object.values));
      sb.append('\t').append(object.size);
      for (int i = 0; i < 3; i++) {
        sb.append('\t').append(MathUtils.rounded(com[i]));
      }

      // The minor moment of inertia will be around the longest axis of the object, so start
      // downwards
      for (int axis = 3; axis-- > 0;) {
        final double[] direction1 = com.clone();
        final double[] direction2 = com.clone();
        findAxisEnds(image, maxx, maxy, objectValue, com, eigenVectors[axis], mind, maxd,
            direction1, direction2);

        // Round to half pixels (since that is our accuracy during the pixel search)
        for (int i = 0; i < 3; i++) {
          direction2[i] = (int) direction2[i] + 0.5;
          direction1[i] = (int) direction1[i] + 0.5;
        }

        if (settings.showVectors) {
          for (int i = 0; i < 3; i++) {
            sb.append('\t').append(MathUtils.rounded(direction1[i]));
          }
          for (int i = 0; i < 3; i++) {
            sb.append('\t').append(MathUtils.rounded(direction2[i]));
          }
        }

        // Distance in pixels
        double dx = direction2[0] - direction1[0];
        double dy = direction2[1] - direction1[1];
        double dz = direction2[2] - direction1[2];
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        sb.append('\t').append(MathUtils.rounded(distance));

        // Calibrated length
        dx *= calx;
        dy *= caly;
        dz *= calz;
        distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        sb.append('\t').append(MathUtils.rounded(distance));

        // Draw lines on the image
        if (overlay != null) {
          final Line roi = new Line(direction1[0], direction1[1], direction2[0], direction2[1]);
          overlay.add(roi);
        }
      }

      resultsWindow.append(sb.toString());
    }

    if (settings.showOverlay) {
      imp.setOverlay(overlay);
    }
  }

  /**
   * A node in the tree of object centres.
   */
  private static class Node {
    final MaskObject object;
    final double[] centre;
    boolean active = true;
    /** The nearest active neighbour. */
    Node neighbour;
    /** The squared distance to the nearest active neighbour. */
    double distance;

    Node(MaskObject object) {
      this.object = object;
      centre = new double[] {object.cx, object.cy, object.cz};
    }
  }

  /**
   * Iteratively merge the closest pair of objects if the distance between their centres is within
   * the merge distance. The smaller object is merged into the larger object; objects of the same
   * size are merged into the object with the lower value. Merged objects have a size of zero. The
   * image values of a merged object are updated to the value of the object it was merged into.
   *
   * @param objects the objects
   * @param image the image
   * @param mergeDistance the merge distance
   */
  static void mergeObjects(MaskObject[] objects, int[] image, double mergeDistance) {
    final ObjDoubleKdTree<Node> tree = KdTrees.newObjDoubleKdTree(3);
    final LocalList<Node> nodes = new LocalList<>(objects.length * 2);
    int maxValue = 0;
    for (final MaskObject o : objects) {
      final Node node = new Node(o);
      nodes.add(node);
      tree.add(node.centre, node);
      maxValue = Math.max(maxValue, o.getValue());
    }
    nodes.forEach(node -> findNeighbour(tree, node));

    // The value of the object each value was merged into
    final int[] parent = new int[maxValue + 1];
    boolean merged = false;
    for (;;) {
      // Find the closest pair
      Node best = null;
      for (int i = 0; i < nodes.size(); i++) {
        final Node node = nodes.unsafeGet(i);
        if (node.active && node.neighbour != null
            && (best == null || node.distance < best.distance)) {
          best = node;
        }
      }

      if (best == null || Math.sqrt(best.distance) > mergeDistance) {
        break;
      }

      // Merge
      final Node a = best;
      final Node b = best.neighbour;
      a.active = false;
      b.active = false;
      MaskObject big = a.object;
      MaskObject small = b.object;
      if (small.size > big.size || (small.size == big.size && small.getValue() < big.getValue())) {
        big = b.object;
        small = a.object;
      }
      parent[small.getValue()] = big.getValue();
      big.merge(small);
      merged = true;

      // Add the merged object to the tree
      final Node c = new Node(big);
      tree.add(c.centre, c);
      findNeighbour(tree, c);
      // Update the neighbours of the remaining objects
      for (int i = 0; i < nodes.size(); i++) {
        final Node node = nodes.unsafeGet(i);
        if (!node.active) {
          continue;
        }
        if (node.neighbour == a || node.neighbour == b) {
          findNeighbour(tree, node);
        } else {
          final double d = DoubleDistanceFunctions.SQUARED_EUCLIDEAN_3D.distance(node.centre,
              c.centre);
          if (node.neighbour == null || d < node.distance) {
            node.neighbour = c;
            node.distance = d;
          }
        }
      }
      nodes.add(c);
    }

    if (merged) {
      // Map each value to the final object value
      final int[] map = new int[parent.length];
      for (int value = 1; value < map.length; value++) {
        int root = value;
        while (parent[root] != 0) {
          root = parent[root];
        }
        map[value] = root;
      }
      for (int i = 0; i < image.length; i++) {
        image[i] = map[image[i]];
      }
    }
  }

  /**
   * Find the nearest active neighbour of the node.
   *
   * @param tree the tree
   * @param node the node
   */
  private static void findNeighbour(ObjDoubleKdTree<Node> tree, Node node) {
    node.neighbour = null;
    tree.nearestNeighbour(node.centre, DoubleDistanceFunctions.SQUARED_EUCLIDEAN_3D,
        n -> n.active && n != node, (n, d) -> {
          node.neighbour = n;
          node.distance = d;
        });
  }

  /**
   * Create the objects using the unique non-zero values in the image. The objects are in ascending
   * order of value. The centre-of-mass is computed in a single pass of the image.
   *
   * @param image the image
   * @param histogram the histogram of the image values
   * @param maxx the image width
   * @param maxy the image height
   * @param maxz the image depth
   * @return the objects
   */
  static MaskObject[] createObjects(int[] image, int[] histogram, int maxx, int maxy, int maxz) {
    // Map the values to consecutive labels starting from 1
    final int[] label = new int[histogram.length];
    final int[] values = new int[histogram.length];
    int count = 0;
    for (int value = 1; value < histogram.length; value++) {
      if (histogram[value] != 0) {
        label[value] = ++count;
        values[count] = value;
      }
    }

    // Sum the coordinates of each object
    final long[] sx = new long[count + 1];
    final long[] sy = new long[count + 1];
    final long[] sz = new long[count + 1];
    final int[] n = new int[count + 1];
    for (int z = 0, i = 0; z < maxz; z++) {
      for (int y = 0; y < maxy; y++) {
        for (int x = 0; x < maxx; x++, i++) {
          final int l = label[image[i]];
          sx[l] += x;
          sy[l] += y;
          sz[l] += z;
          n[l]++;
        }
      }
    }

    // Find the Centre-of-Mass. Set 0.5 as the centre of the voxel mass.
    final MaskObject[] objects = new MaskObject[count];
    for (int l = 1; l <= count; l++) {
      final double cx = (double) sx[l] / n[l] + 0.5;
      final double cy = (double) sy[l] / n[l] + 0.5;
      final double cz = (double) sz[l] / n[l] + 0.5;
      objects[l - 1] = new MaskObject(cx, cy, cz, n[l], values[l]);
    }
    return objects;
  }

  /**
   * Find the furthest pixels in the object along the axis from the centre-of-mass. The end
   * positions are only updated if a pixel inside the object is found.
   *
   * @param image the image
   * @param maxx the image width
   * @param maxy the image height
   * @param objectValue the object value
   * @param com the centre-of-mass
   * @param axis the axis direction
   * @param mind the minimum for each dimension
   * @param maxd the maximum (exclusive) for each dimension
   * @param end1 the end position in the positive direction
   * @param end2 the end position in the negative direction
   */
  static void findAxisEnds(int[] image, int maxx, int maxy, int objectValue, double[] com,
      double[] axis, int[] mind, int[] maxd, double[] end1, double[] end2) {
    final double epsilon = 1e-6;
    final double[] direction = new double[3];
    double longest = 0; // Used to normalise the longest dimension to 1
    for (int i = 0; i < 3; i++) {
      final double v = Math.abs(axis[i]);
      if (v >= epsilon) {
        direction[i] = axis[i];
        if (longest < v) {
          longest = v;
        }
      }
    }
    if (longest == 0) {
      return;
    }
    // Assuming unit vector then moving in increments of 1 should never skip pixels
    // in any dimension. Normalise to 1 in the longest dimension should still be OK.
    for (int i = 0; i < 3; i++) {
      direction[i] /= longest;
    }

    final int size = maxx * maxy;
    final double[] pos = new double[3];

    // Move one way, then the other
    for (final int dir : new int[] {-1, 1}) {
      final double[] tmp = (dir == 1) ? end1 : end2;
      for (int n = dir;; n += dir) {
        if (updatePosition(com, n, direction, mind, maxd, pos)) {
          break;
        }
        final int index = ((int) pos[2]) * size + ((int) pos[1]) * maxx + ((int) pos[0]);
        // Check if we are inside the object
        if (image[index] == objectValue) {
          System.arraycopy(pos, 0, tmp, 0, pos.length);
        }
      }
    }
  }

  /**
   * Update the position by moving from the centre-of-mass n steps in the given direction.
   *
   * <p>Check if the move results in exceeding the bounds.
   *
   * @param com the centre-of-mass
   * @param numberOfSteps the number of steps
   * @param direction the direction
   * @param mind the minimum for the dimension
   * @param maxd the maximum for the dimension
   * @param pos the position
   * @return true if out-of-bounds
   */
  private static boolean updatePosition(double[] com, int numberOfSteps, double[] direction,
      int[] mind, int[] maxd, double[] pos) {
    for (int i = 0; i < 3; i++) {
      pos[i] = com[i] + numberOfSteps * direction[i];
      // Check bounds
      if (pos[i] < mind[i] || pos[i] >= maxd[i]) {
        return true;
      }
    }
    return false;
  }

  private TextWindow createResultsWindow() {
    final String header = createResultsHeader();
    final TextWindow tw = ImageJUtils.refresh(resultsWindowRef,
        () -> new TextWindow(TITLE + " Results", header, "", 900, 300));
    // The columns change with the show vectors option. This clears the table.
    if (!header.equals(tw.getTextPanel().getColumnHeadings())) {
      tw.getTextPanel().setColumnHeadings(header);
    }
    return tw;
  }

  private String createResultsHeader() {
    final StringBuilder sb = new StringBuilder();
    sb.append("Image");
    sb.append("\tUnits");
    sb.append("\tObject");
    sb.append("\tArea");
    sb.append("\tcx\tcy\tcz");
    for (int i = 1; i <= 3; i++) {
      if (settings.showVectors) {
        sb.append("\tv").append(i).append(" lx");
        sb.append("\tv").append(i).append(" ly");
        sb.append("\tv").append(i).append(" lz");
        sb.append("\tv").append(i).append(" ux");
        sb.append("\tv").append(i).append(" uy");
        sb.append("\tv").append(i).append(" uz");
      }
      sb.append("\tv").append(i).append(" len (px)");
      sb.append("\tv").append(i).append(" len (units)");
    }
    return sb.toString();
  }

  /**
   * Vector sorting routine for 3x3 set of vectors. The vectors are sorted by weight in descending
   * order.
   *
   * @param weights Vector weights
   * @param vectors Vectors
   */
  static void eigenSort3x3(double[] weights, double[][] vectors) {
    for (int i = 3; i-- > 0;) {
      int minIndex = i;
      double minWeight = weights[minIndex];
      for (int j = i; j-- > 0;) {
        if (weights[j] <= minWeight) {
          minIndex = j;
          minWeight = weights[minIndex];
        }
      }
      if (minIndex != i) {
        weights[minIndex] = weights[i];
        weights[i] = minWeight;
        final double[] tmp = vectors[i];
        vectors[i] = vectors[minIndex];
        vectors[minIndex] = tmp;
      }
    }
  }
}
