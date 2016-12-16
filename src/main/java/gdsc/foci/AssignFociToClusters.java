package gdsc.foci;

import gdsc.UsageTracker;
import gdsc.help.URL;
import gdsc.core.clustering.Cluster;
import gdsc.core.clustering.ClusterPoint;
import gdsc.core.clustering.ClusteringAlgorithm;
import gdsc.core.clustering.ClusteringEngine;
import gdsc.core.ij.IJTrackProgress;
import gdsc.core.ij.Utils;
import gdsc.core.match.Coordinate;
import gdsc.core.match.MatchCalculator;
import gdsc.core.match.MatchResult;
import gdsc.core.match.PointPair;
import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.Prefs;
import ij.WindowManager;
import ij.gui.DialogListener;
import ij.gui.GenericDialog;
import ij.gui.Overlay;
import ij.gui.PointRoi;
import ij.gui.Roi;
import ij.plugin.filter.ExtendedPlugInFilter;
import ij.plugin.filter.PlugInFilterRunner;
import ij.process.ImageProcessor;
import ij.process.LUT;
import ij.text.TextWindow;

import java.awt.AWTEvent;
import java.awt.Color;
import java.awt.Label;
import java.awt.Point;
import java.awt.image.ColorModel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;

/**
 * Performs clustering on the latest Find Foci result held in memory. Optionally can draw the clusters on the Find Foci
 * output mask if this is selected when the plugin is run.
 */
public class AssignFociToClusters implements ExtendedPlugInFilter, DialogListener
{
	public static final String TITLE = "Assign Foci To Clusters";

	private static int imageFlags = DOES_8G + DOES_16 + SNAPSHOT;
	private static int noImageFlags = NO_IMAGE_REQUIRED + FINAL_PROCESSING;

	private static double radius = 100;
	//@formatter:off
	private static ClusteringAlgorithm[] algorithms = new ClusteringAlgorithm[] {
			ClusteringAlgorithm.PARTICLE_SINGLE_LINKAGE, 
			ClusteringAlgorithm.CENTROID_LINKAGE,
			ClusteringAlgorithm.PARTICLE_CENTROID_LINKAGE, 
			ClusteringAlgorithm.PAIRWISE,
			ClusteringAlgorithm.PAIRWISE_WITHOUT_NEIGHBOURS 
	};
	private static String[] names;
	static
	{
		names = new String[algorithms.length];
		for (int i = 0; i < names.length; i++)
			names[i] = algorithms[i].toString();
	}
	private static int algorithm = 1;
	private static String[] weights = new String[] {
		"None",
		"Pixel count", 
		"Total intensity",
		"Max Value",
		"Average intensity",
		"Total intensity minus background",
		"Average intensity minus background",
		"Count above saddle",
		"Intensity above saddle"
	};	
	//@formatter:on
	private static int weight = 2;
	private static int minSize = 0;
	private static boolean showMask = true;
	private static boolean eliminateEdgeClusters = false;
	private static int border = 10;
	private static boolean labelClusters = false;
	private static boolean filterUsingPointROI = false;
	private static double filterRadius = 50;
	private boolean myShowMask = false;

	private static TextWindow resultsWindow = null;
	private static TextWindow summaryWindow = null;
	private static TextWindow matchWindow = null;
	private static int resultId = 1;

	private ImagePlus imp;
	private boolean[] edge = null;
	private AssignedPoint[] roiPoints;
	private ArrayList<FindFociResult> results;
	private ArrayList<Cluster> clusters, minSizeClusters, edgeClusters, filteredClusters;
	private MatchResult matchResult;
	private ColorModel cm;
	private Label label = null;

	public int setup(String arg, ImagePlus imp)
	{
		if (arg.equals("final"))
		{
			doClustering();
			displayResults();
			return DONE;
		}
		UsageTracker.recordPlugin(this.getClass(), arg);

		results = FindFoci.getResults();
		if (results == null || results.isEmpty())
		{
			IJ.error(TITLE, "Require " + FindFoci.TITLE + " results in memory");
			return DONE;
		}

		this.imp = validateInputImage(imp);

		return (this.imp == null) ? noImageFlags : imageFlags;
	}

	public void resetPreview()
	{
		if (this.imp != null)
		{
			// Reset the preview 
			ImageProcessor ip = this.imp.getProcessor();
			ip.setColorModel(cm);
			ip.reset();
			this.imp.setOverlay(null);
		}
	}

	/**
	 * Check if the input image has the same number of non-zero values as the FindFoci results. This means it is a
	 * FindFoci mask for the results.
	 * 
	 * @param imp
	 * @return The image if valid
	 */
	private ImagePlus validateInputImage(ImagePlus imp)
	{
		if (imp == null)
			return null;
		if (!FindFoci.isSupported(imp.getBitDepth()))
			return null;

		// Find all the mask objects using a stack histogram.
		ImageStack stack = imp.getImageStack();
		int[] h = stack.getProcessor(1).getHistogram();
		for (int s = 2; s <= stack.getSize(); s++)
		{
			int[] h2 = stack.getProcessor(1).getHistogram();
			for (int i = 0; i < h.length; i++)
				h[i] += h2[i];
		}

		// Correct mask objects should be numbered sequentially from 1.
		// Find first number that is zero.
		int size = 1;
		while (size < h.length)
		{
			if (h[size] == 0)
				break;
			size++;
		}
		size--; // Decrement to find the last non-zero number 

		// Check the FindFoci results have the same number of objects
		if (size != results.size())
			return null;

		// Check each result matches the image.
		// Image values correspond to the reverse order of the results.
		for (int i = 0, id = results.size(); i < results.size(); i++, id--)
		{
			final FindFociResult result = results.get(i);
			final int x = result.x;
			final int y = result.y;
			final int z = result.z;
			try
			{
				final int value = stack.getProcessor(z + 1).get(x, y);
				if (value != id)
				{
					System.out.printf("x%d,y%d,z%d %d != %d\n", x, y, z + 1, value, id);
					return null;
				}
			}
			catch (IllegalArgumentException e)
			{
				// The stack is not the correct size
				System.out.println(e.getMessage());
				return null;
			}
		}

		// Store this so it can be reset
		cm = imp.getProcessor().getColorModel();

		// Check for a multi-point ROI
		roiPoints = PointManager.extractRoiPoints(imp.getRoi());
		if (roiPoints.length == 0)
			roiPoints = null;

		return imp;
	}

	public void run(ImageProcessor ip)
	{
		// This will not be called if we selected NO_IMAGE_REQUIRED

		doClustering();

		if (label == null)
		{
			// This occurs when the dialog has been closed and the plugin is run.
			displayResults();
			return;
		}

		// This occurs when we are supporting a preview

		if (filteredClusters.isEmpty())
		{
			// No clusters so blank the image
			for (int i = 0; i < ip.getPixelCount(); i++)
				ip.set(i, 0);
		}
		else
		{
			// Create a new mask image colouring the objects from each cluster.
			// Create a map to convert original foci pixels to clusters.
			int[] map = new int[results.size() + 1];
			for (int i = 0; i < filteredClusters.size(); i++)
			{
				for (ClusterPoint p = filteredClusters.get(i).head; p != null; p = p.next)
				{
					map[p.id] = i + 1;
				}
			}

			// Update the preview processor with the filteredClusters
			for (int i = 0; i < ip.getPixelCount(); i++)
			{
				if (ip.get(i) != 0)
					ip.set(i, map[ip.get(i)]);
			}

			ip.setColorModel(Utils.getColorModel());
			ip.setMinAndMax(0, filteredClusters.size());

			labelClusters(imp);
		}

		label.setText(Utils.pleural(filteredClusters.size(), "Cluster"));
	}

	public void setNPasses(int nPasses)
	{
		// Nothing to do
	}

	public int showDialog(ImagePlus imp, String command, PlugInFilterRunner pfr)
	{
		GenericDialog gd = new GenericDialog(TITLE);
		gd.addHelp(URL.FIND_FOCI);
		gd.addMessage(Utils.pleural(results.size(), "result"));

		gd.addSlider("Radius", 5, 500, radius);
		gd.addChoice("Algorithm", names, names[algorithm]);
		gd.addChoice("Weight", weights, weights[weight]);
		gd.addSlider("Min_size", 1, 20, minSize);
		if (this.imp != null)
		{
			gd.addCheckbox("Show_mask", showMask);
			gd.addCheckbox("Eliminate_edge_clusters", eliminateEdgeClusters);
			gd.addSlider("Border", 1, 20, border);
			gd.addCheckbox("Label_clusters", labelClusters);

			if (roiPoints != null)
			{
				gd.addCheckbox("Filter_using_Point_ROI", filterUsingPointROI);
				gd.addSlider("Filter_radius", 5, 500, filterRadius);
			}
		}

		// Allow preview
		gd.addPreviewCheckbox(pfr);
		gd.addDialogListener(this);
		gd.addMessage("");
		label = (Label) gd.getMessage();

		gd.showDialog();

		// Disable preview
		resetPreview();
		label = null;

		if (gd.wasCanceled() || !dialogItemChanged(gd, null))
		{
			return DONE;
		}

		return (this.imp == null) ? noImageFlags : imageFlags;
	}

	public boolean dialogItemChanged(GenericDialog gd, AWTEvent e)
	{
		radius = Math.abs(gd.getNextNumber());
		algorithm = gd.getNextChoiceIndex();
		weight = gd.getNextChoiceIndex();
		minSize = (int) Math.abs(gd.getNextNumber());
		if (this.imp != null)
		{
			myShowMask = showMask = gd.getNextBoolean();
			eliminateEdgeClusters = gd.getNextBoolean();
			border = (int) Math.abs(gd.getNextNumber());
			if (border < 1)
				border = 1;
			labelClusters = gd.getNextBoolean();

			if (roiPoints != null)
			{
				filterUsingPointROI = gd.getNextBoolean();
				filterRadius = Math.abs(gd.getNextNumber());
			}
		}

		if (!gd.getPreviewCheckbox().getState())
		{
			resetPreview();
		}
		else
		// We can support a preview without an image
		if (label != null && imp == null)
		{
			noImagePreview();
		}

		return true;
	}

	private synchronized void noImagePreview()
	{
		doClustering();
		label.setText(Utils.pleural(filteredClusters.size(), "Cluster"));
	}

	private double lastRadius;
	private int lastAlgorithm, lastWeight;
	private int lastMinSize;
	private boolean lastEliminateEdgeClusters;
	private int lastBorder;
	private boolean lastfilterUsingPointROI;
	private double lastFilterRadius = -1;

	private void doClustering()
	{
		long start = System.currentTimeMillis();
		IJ.showStatus("Clustering ...");

		if (clusters == null || lastRadius != radius || lastAlgorithm != algorithm || lastWeight != weight)
		{
			//System.out.println("clustering 1");
			lastRadius = radius;
			lastAlgorithm = algorithm;
			lastWeight = weight;

			minSizeClusters = null;

			ClusteringEngine e = new ClusteringEngine(Prefs.getThreads(), algorithms[algorithm], new IJTrackProgress());
			ArrayList<ClusterPoint> points = getPoints();
			clusters = e.findClusters(points, radius);
			Collections.sort(clusters, new Comparator<Cluster>()
			{
				public int compare(Cluster o1, Cluster o2)
				{
					if (o1.sumw > o2.sumw)
						return -1;
					if (o1.sumw < o2.sumw)
						return 1;
					return 0;
				}
			});
		}

		if (minSizeClusters == null || lastMinSize != minSize)
		{
			minSizeClusters = clusters;

			edgeClusters = null;

			if (minSize > 0)
			{
				//System.out.println("clustering 2");
				minSizeClusters = new ArrayList<Cluster>(clusters.size());
				for (Cluster c : clusters)
					if (c.n >= minSize)
						minSizeClusters.add(c);
			}

			lastMinSize = minSize;
		}

		if (edgeClusters == null || lastEliminateEdgeClusters != eliminateEdgeClusters || lastBorder != border)
		{
			edgeClusters = minSizeClusters;

			filteredClusters = null;

			if (imp != null && eliminateEdgeClusters && border > 0)
			{
				//System.out.println("clustering 3");
				// Cache the edge particles
				if (edge == null || lastBorder != border)
				{
					ImageStack stack = imp.getImageStack();
					edge = new boolean[results.size() + 1];
					for (int s = 1; s <= stack.getSize(); s++)
					{
						findEdgeObjects(stack.getProcessor(s), edge);
					}
				}

				// Check which clusters contain edge particles
				edgeClusters = new ArrayList<Cluster>(minSizeClusters.size());
				NextCluster: for (Cluster c : minSizeClusters)
				{
					for (ClusterPoint p = c.head; p != null; p = p.next)
					{
						if (edge[p.id])
						{
							continue NextCluster;
						}
					}
					edgeClusters.add(c);
				}
			}

			lastEliminateEdgeClusters = eliminateEdgeClusters;
			lastBorder = border;
		}

		if (filteredClusters == null || (roiPoints != null &&
				(lastfilterUsingPointROI != filterUsingPointROI || lastFilterRadius != filterRadius)))
		{
			if (roiPoints != null && filterUsingPointROI)
			{
				if (filteredClusters == null || lastFilterRadius != filterRadius)
				{
					lastFilterRadius = filterRadius;

					//System.out.println("clustering 4");
					Coordinate[] actualPoints = roiPoints;
					Coordinate[] predictedPoints = toCoordinates(edgeClusters);
					List<Coordinate> TP = null;
					List<Coordinate> FP = null;
					List<Coordinate> FN = null;
					List<PointPair> matches = new ArrayList<PointPair>(edgeClusters.size());
					matchResult = MatchCalculator.analyseResults2D(actualPoints, predictedPoints, filterRadius, TP, FP,
							FN, matches);
					filteredClusters = new ArrayList<Cluster>(matches.size());
					for (PointPair pair : matches)
						filteredClusters.add(edgeClusters.get(((TimeValuedPoint) pair.getPoint2()).time));
				}
			}
			else
			{
				// No filtering
				filteredClusters = edgeClusters;
				lastFilterRadius = -1;
			}

			lastfilterUsingPointROI = filterUsingPointROI;
		}

		double seconds = (System.currentTimeMillis() - start) / 1000.0;
		IJ.showStatus(Utils.pleural(filteredClusters.size(), "cluster") + " in " + Utils.rounded(seconds) + " seconds");
	}

	private Coordinate[] toCoordinates(ArrayList<Cluster> clusters)
	{
		Coordinate[] coords = new Coordinate[clusters.size()];
		for (int i = 0; i < clusters.size(); i++)
		{
			Cluster c = clusters.get(i);
			coords[i] = new TimeValuedPoint((float) c.x, (float) c.y, 0, i, 0);
		}
		return coords;
	}

	private void displayResults()
	{
		if (filteredClusters == null)
			return;

		IJ.showStatus("Displaying results ...");

		// Options only available if there is an input FindFoci mask image.
		// Removal of edge clusters will reduce the final number of clusters.
		if (myShowMask)
		{
			// Create a new mask image colouring the objects from each cluster.
			// Create a map to convert original foci pixels to clusters.
			int[] map = new int[results.size() + 1];
			for (int i = 0; i < filteredClusters.size(); i++)
			{
				for (ClusterPoint p = filteredClusters.get(i).head; p != null; p = p.next)
				{
					map[p.id] = i + 1;
				}
			}

			ImageStack stack = imp.getImageStack();
			ImageStack newStack = new ImageStack(stack.getWidth(), stack.getHeight(), stack.getSize());
			for (int s = 1; s <= stack.getSize(); s++)
			{
				ImageProcessor ip = stack.getProcessor(s);
				ImageProcessor ip2 = ip.createProcessor(ip.getWidth(), ip.getHeight());
				for (int i = 0; i < ip.getPixelCount(); i++)
				{
					if (ip.get(i) != 0)
						ip2.set(i, map[ip.get(i)]);
				}
				newStack.setProcessor(ip2, s);
			}

			// Set a colour table if this is a new image. Otherwise the existing one is preserved.
			ImagePlus clusterImp = WindowManager.getImage(TITLE);
			if (clusterImp == null)
				newStack.setColorModel(Utils.getColorModel());

			clusterImp = Utils.display(TITLE, newStack);

			labelClusters(clusterImp);
		}

		createResultsTables();

		// Show the results
		final String title = (imp != null) ? imp.getTitle() : "Result " + (resultId++);
		StringBuilder sb = new StringBuilder();
		DescriptiveStatistics stats = new DescriptiveStatistics();
		DescriptiveStatistics stats2 = new DescriptiveStatistics();
		for (int i = 0; i < filteredClusters.size(); i++)
		{
			final Cluster cluster = filteredClusters.get(i);
			sb.append(title).append('\t');
			sb.append(i + 1).append('\t');
			sb.append(Utils.rounded(cluster.x)).append('\t');
			sb.append(Utils.rounded(cluster.y)).append('\t');
			sb.append(Utils.rounded(cluster.n)).append('\t');
			sb.append(Utils.rounded(cluster.sumw)).append('\t');
			stats.addValue(cluster.n);
			stats2.addValue(cluster.sumw);
			sb.append('\n');

			// Auto-width adjustment is only performed when number of rows is less than 10
			// so do this before it won't work
			if (i == 9 && resultsWindow.getTextPanel().getLineCount() < 10)
			{
				resultsWindow.append(sb.toString());
				sb.setLength(0);
			}
		}
		resultsWindow.append(sb.toString());

		sb.setLength(0);
		sb.append(title).append('\t');
		sb.append(Utils.rounded(radius)).append('\t');
		sb.append(results.size()).append('\t');
		sb.append(filteredClusters.size()).append('\t');
		sb.append((int) stats.getMin()).append('\t');
		sb.append((int) stats.getMax()).append('\t');
		sb.append(Utils.rounded(stats.getMean())).append('\t');
		sb.append(Utils.rounded(stats2.getMin())).append('\t');
		sb.append(Utils.rounded(stats2.getMax())).append('\t');
		sb.append(Utils.rounded(stats2.getMean())).append('\t');
		summaryWindow.append(sb.toString());

		if (matchResult != null)
		{
			sb.setLength(0);
			sb.append(title).append('\t');
			sb.append(Utils.rounded(filterRadius)).append('\t');
			sb.append(matchResult.getNumberActual()).append('\t');
			sb.append(matchResult.getNumberPredicted()).append('\t');
			sb.append(matchResult.getTruePositives()).append('\t');
			sb.append(matchResult.getFalseNegatives()).append('\t');
			sb.append(matchResult.getFalsePositives()).append('\t');
			sb.append(Utils.rounded(matchResult.getJaccard())).append('\t');
			sb.append(Utils.rounded(matchResult.getRecall())).append('\t');
			sb.append(Utils.rounded(matchResult.getPrecision())).append('\t');
			sb.append(Utils.rounded(matchResult.getFScore(1))).append('\t');
			matchWindow.append(sb.toString());
		}

		IJ.showStatus("");
	}

	private void labelClusters(ImagePlus clusterImp)
	{
		Overlay o = null;
		if (labelClusters)
		{
			Roi roi = getClusterRoi(filteredClusters);
			if (roi != null)
			{
				o = new Overlay(roi);
				o.setStrokeColor(Color.cyan);
			}
		}
		clusterImp.setOverlay(o);
	}

	private void findEdgeObjects(ImageProcessor ip, boolean[] edge)
	{
		final int w = ip.getWidth();
		final int h = ip.getHeight();

		for (int i = 0; i < border; i++)
		{
			final int top = h - 1 - i;
			if (top < i)
				return;
			for (int x = 0, i1 = i * w, i2 = top * w; x < w; x++, i1++, i2++)
			{
				if (ip.get(i1) != 0)
					edge[ip.get(i1)] = true;
				if (ip.get(i2) != 0)
					edge[ip.get(i2)] = true;
			}
		}
		for (int i = 0; i < border; i++)
		{
			final int top = w - 1 - i;
			if (top < i)
				return;
			for (int y = border, i1 = border * w + i, i2 = border * w + top; y < w - border; y++, i1 += w, i2 += w)
			{
				if (ip.get(i1) != 0)
					edge[ip.get(i1)] = true;
				if (ip.get(i2) != 0)
					edge[ip.get(i2)] = true;
			}
		}
	}

	private Roi getClusterRoi(ArrayList<Cluster> clusters)
	{
		if (clusters == null || clusters.isEmpty())
			return null;
		int nMaxima = clusters.size();
		float[] xpoints = new float[nMaxima];
		float[] ypoints = new float[nMaxima];
		int i = 0;
		for (Cluster point : clusters)
		{
			xpoints[i] = (float) point.x;
			ypoints[i] = (float) point.y;
			i++;
		}
		PointRoi roi = new PointRoi(xpoints, ypoints, nMaxima);
		roi.setShowLabels(true);
		return roi;
	}

	private ArrayList<ClusterPoint> getPoints()
	{
		if (FindFoci.getResults() == null)
			return null;
		ArrayList<ClusterPoint> points = new ArrayList<ClusterPoint>(FindFoci.getResults().size());
		// Image values correspond to the reverse order of the results.
		for (int i = 0, id = results.size(); i < results.size(); i++, id--)
		{
			FindFociResult result = results.get(i);
			points.add(ClusterPoint.newClusterPoint(id, result.x, result.y, getWeight(result)));
		}
		return points;
	}

	private double getWeight(FindFociResult result)
	{
		switch (weight)
		{
			//@formatter:off
			case 0: return 1.0;
			case 1: return result.count;
			case 2: return result.totalIntensity;
			case 3: return result.maxValue;
			case 4: return result.averageIntensity;
			case 5: return result.totalIntensityAboveBackground;
			case 6: return result.averageIntensityAboveBackground;
			case 7: return result.countAboveSaddle;
			case 8: return result.intensityAboveSaddle;
			default: return 1.0;
			//@formatter:on
		}
	}

	private void createResultsTables()
	{
		resultsWindow = createWindow(resultsWindow, "Results", "Title\tCluster\tcx\tcy\tSize\tW", 300);
		summaryWindow = createWindow(summaryWindow, "Summary",
				"Title\tRadius\tFoci\tClusters\tMin\tMax\tAv\tMin W\tMax W\tAv W", 300);
		Point p1 = resultsWindow.getLocation();
		Point p2 = summaryWindow.getLocation();
		if (p1.x == p2.x && p1.y == p2.y)
		{
			p2.y += resultsWindow.getHeight();
			summaryWindow.setLocation(p2);
		}
		if (matchResult == null)
			return;
		matchWindow = createWindow(matchWindow, "Filter Result",
				"Title\tRadius\tPoints\tClusters\tTP\tFN\tFP\tJaccard\tRecall\tPrecision\tF1", 300);
		Point p3 = matchWindow.getLocation();
		if (p1.x == p3.x && p1.y == p3.y)
		{
			p3.y += resultsWindow.getHeight();
			matchWindow.setLocation(p3);
		}
		if (p2.x == p3.x && p2.y == p3.y)
		{
			p3.y += summaryWindow.getHeight();
			matchWindow.setLocation(p3);
		}
	}

	private TextWindow createWindow(TextWindow window, String title, String header, int h)
	{
		if (window == null || !window.isVisible())
			window = new TextWindow(TITLE + " " + title, header, "", 800, h);
		return window;
	}
}
