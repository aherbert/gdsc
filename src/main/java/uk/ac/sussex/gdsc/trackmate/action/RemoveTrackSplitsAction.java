/*-
 * #%L
 * Genome Damage and Stability Centre ImageJ Plugins
 *
 * Software for microscopy image analysis
 * %%
 * Copyright (C) 2011 - 2020 Alex Herbert
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

package uk.ac.sussex.gdsc.trackmate.action;

import fiji.plugin.trackmate.Model;
import fiji.plugin.trackmate.SelectionModel;
import fiji.plugin.trackmate.Spot;
import fiji.plugin.trackmate.TrackMate;
import fiji.plugin.trackmate.TrackModel;
import fiji.plugin.trackmate.action.AbstractTMAction;
import fiji.plugin.trackmate.action.TrackMateAction;
import fiji.plugin.trackmate.action.TrackMateActionFactory;
import fiji.plugin.trackmate.gui.TrackMateGUIController;
import gnu.trove.set.hash.TIntHashSet;
import ij.Prefs;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import javax.swing.ImageIcon;
import org.jgrapht.graph.DefaultWeightedEdge;
import org.scijava.plugin.Plugin;
import uk.ac.sussex.gdsc.core.ij.gui.ExtendedGenericDialog;
import uk.ac.sussex.gdsc.core.utils.LocalList;
import uk.ac.sussex.gdsc.core.utils.SimpleArrayUtils;
import uk.ac.sussex.gdsc.core.utils.TextUtils;
import uk.ac.sussex.gdsc.trackmate.detector.PrecomputedDetector;

/**
 * Remove edges from track splits when the parent and child spots are in specified categories.
 *
 * <p>An example would be to remove a split between a parent cell and two child cells when the
 * parent and both children are classified as interphase cells (which do not divide).
 */
public class RemoveTrackSplitsAction extends AbstractTMAction {
  private static final String KEY_PARENT_CATEGORY =
      "gdsc.tm.action.remove_track_splits.parent_category";
  private static final String KEY_CHILD1_CATEGORY =
      "gdsc.tm.action.remove_track_splits.child_1_category";
  private static final String KEY_CHILD2_CATEGORY =
      "gdsc.tm.action.remove_track_splits.child_2_category";
  private static final String KEY_DRY_RUN = "gdsc.tm.action.remove_track_splits.dry_run";

  /** The selection model. Used to select the identified spots and edges in dry-run mode. */
  private final SelectionModel selectionModel;

  /**
   * A factory for creating {@link RemoveTrackSplitsAction} objects.
   */
  @Plugin(type = TrackMateActionFactory.class)
  public static class Factory implements TrackMateActionFactory {
    /** Description of the action. */
    private static final String INFO_TEXT =
        "<html><p>This action will remove track splits using spot categories for the parent and "
            + "child spots to identify invalid splits.</p></html>";

    /** Key used for the action. */
    private static final String KEY = "REMOVE_TRACK_SPLITS";

    /** Display name. */
    private static final String NAME = "Remove track splits";

    @Override
    public String getInfoText() {
      return INFO_TEXT;
    }

    @Override
    public ImageIcon getIcon() {
      return null; // No icon for this one.
    }

    @Override
    public String getKey() {
      return KEY;
    }

    @Override
    public String getName() {
      return NAME;
    }

    @Override
    public TrackMateAction create(final TrackMateGUIController controller) {
      return new RemoveTrackSplitsAction(controller.getSelectionModel());
    }
  }

  /**
   * Represent a track split.
   */
  private static class TrackSplit {
    /** The parent. */
    Spot parent;
    /** The edges to the children. */
    LocalList<DefaultWeightedEdge> childEdges;

    /**
     * Create a new instance.
     *
     * @param parent the parent
     * @param childEdges the edges to the children
     */
    TrackSplit(Spot parent, LocalList<DefaultWeightedEdge> childEdges) {
      this.parent = parent;
      this.childEdges = childEdges;
    }
  }

  /**
   * Create an instance.
   *
   * <p>The selection model is used to select the edges that will be removed during a dry-run.
   *
   * @param selectionModel the selection model
   */
  public RemoveTrackSplitsAction(SelectionModel selectionModel) {
    this.selectionModel = selectionModel;
  }

  @Override
  public void execute(final TrackMate trackmate) {
    // Get the options
    String parentCat = Prefs.get(KEY_PARENT_CATEGORY, "1");
    String child1Cat = Prefs.get(KEY_CHILD1_CATEGORY, "1");
    String child2Cat = Prefs.get(KEY_CHILD2_CATEGORY, "1");
    boolean dryRun = Prefs.get(KEY_DRY_RUN, false);
    final ExtendedGenericDialog gd = new ExtendedGenericDialog(Factory.NAME);
    gd.addStringField("Parent_category", parentCat);
    gd.addStringField("Child_1_category", child1Cat);
    gd.addStringField("Child_2_category", child2Cat);
    gd.addCheckbox("Dry_run", dryRun);
    gd.showDialog();
    if (gd.wasCanceled()) {
      logger.log("Cancelled.\n");
      return;
    }
    parentCat = gd.getNextString();
    child1Cat = gd.getNextString();
    child2Cat = gd.getNextString();
    dryRun = gd.getNextBoolean();
    // Save (before validation)
    Prefs.set(KEY_PARENT_CATEGORY, parentCat);
    Prefs.set(KEY_CHILD1_CATEGORY, child1Cat);
    Prefs.set(KEY_CHILD2_CATEGORY, child2Cat);
    Prefs.set(KEY_DRY_RUN, dryRun);
    // Check inputs
    final TIntHashSet parentCategories = parseSet(parentCat);
    final TIntHashSet child1Categories = parseSet(child1Cat);
    final TIntHashSet child2Categories = parseSet(child2Cat);
    if (parentCategories.isEmpty() || child1Categories.isEmpty() || child2Categories.isEmpty()) {
      logger.log("Invalid categories.\n");
      return;
    }

    final Model model = trackmate.getModel();
    final TrackModel trackModel = model.getTrackModel();

    // Visible tracks only
    final Set<Integer> trackIds = trackModel.unsortedTrackIDs(true);
    logger.log("Analysing " + TextUtils.pleural(trackIds.size(), "track id") + ".\n");
    final List<TrackSplit> splits =
        findSplits(trackModel, trackIds, parentCategories, child1Categories, child2Categories);
    logger.log("Found " + TextUtils.pleural(splits.size(), "track split") + ".\n");

    // For each split all edges but the one with the lowest weight are sent to the action
    Consumer<DefaultWeightedEdge> action;
    Runnable finalAction;
    if (dryRun) {
      // Dry-run: Highlight the tracks to remove.
      final Collection<DefaultWeightedEdge> edges = new LocalList<>();
      final Collection<Spot> spots = new HashSet<>();
      action = edge -> {
        edges.add(edge);
        spots.add(trackModel.getEdgeSource(edge));
        spots.add(trackModel.getEdgeTarget(edge));
      };
      finalAction = () -> {
        selectionModel.clearEdgeSelection();
        selectionModel.clearSpotSelection();
        selectionModel.addEdgeToSelection(edges);
        selectionModel.addSpotToSelection(spots);
      };
    } else {
      // Remove the edges
      model.beginUpdate();
      action = edge -> model.removeEdge(edge);
      finalAction = () -> model.endUpdate();
    }
    try {
      splits.sort((s1, s2) -> Double.compare(s1.parent.getFeature(Spot.FRAME),
          s2.parent.getFeature(Spot.FRAME)));
      splits.forEach(split -> {
        final Spot parent = split.parent;
        final LocalList<DefaultWeightedEdge> childEdges = split.childEdges;
        final double[] weights =
            childEdges.stream().mapToDouble(trackModel::getEdgeWeight).toArray();
        logger.log(String.format("  Parent %s (cat=%d) frame=%d\n", parent.getName(),
            getCategory(parent), parent.getFeature(Spot.FRAME).intValue()));
        childEdges.forEach(edge -> {
          final Spot child = getChild(trackModel, edge, parent);
          logger.log(String.format("    Child %s (cat=%d) edge weight=%s\n", child.getName(),
              getCategory(child), trackModel.getEdgeWeight(edge)));
        });
        // Find lowest edge and remove others
        final int index = SimpleArrayUtils.findMinIndex(weights);
        for (int i = 0; i < childEdges.size(); i++) {
          if (i != index) {
            action.accept(childEdges.unsafeGet(i));
          }
        }
      });
    } finally {
      finalAction.run();
    }

    logger.log("Done.\n");
  }

  /**
   * Parses the comma-delimited categories into a set of integers.
   *
   * @param categories the categories
   * @return the int set
   */
  private TIntHashSet parseSet(String categories) {
    final TIntHashSet set = new TIntHashSet();
    try {
      Arrays.stream(categories.split(",")).mapToInt(Integer::parseInt).forEach(set::add);
    } catch (final NumberFormatException ex) {
      logger.log("Bad category: " + ex.getMessage() + "\n");
      set.clear();
    }
    return set;
  }

  /**
   * Find splits from a parent spot in the specified categories to children of the specified
   * categories.
   *
   * @param trackModel the track model
   * @param trackIds the track ids
   * @param parentCategories the parent categories
   * @param child1Categories the child 1 categories
   * @param child2Categories the child 2 categories
   * @return the list of splits
   */
  private static List<TrackSplit> findSplits(TrackModel trackModel, Set<Integer> trackIds,
      TIntHashSet parentCategories, TIntHashSet child1Categories, TIntHashSet child2Categories) {
    final LocalList<TrackSplit> list = new LocalList<>(trackIds.size() * 2);
    trackIds.forEach(trackId -> {
      final Set<Spot> spots = trackModel.trackSpots(trackId);
      spots.forEach(parent -> {
        // Ignore if the spot is not in the parent category
        if (!parentCategories.contains(getCategory(parent))) {
          return;
        }

        // Performed as per trackmate.features.track.TrackBranchingAnalyzer
        final Set<DefaultWeightedEdge> edges = trackModel.edgesOf(parent);

        final Set<Spot> neighbours = new HashSet<>(edges.size() + 1);
        for (final DefaultWeightedEdge edge : edges) {
          neighbours.add(trackModel.getEdgeSource(edge));
          neighbours.add(trackModel.getEdgeTarget(edge));
        }
        neighbours.remove(parent);

        // Inspect neighbours relative time position. Add all children to a list.
        final LocalList<Spot> children = new LocalList<>(neighbours.size());
        final int t1 = parent.getFeature(Spot.FRAME).intValue();
        for (final Spot neighbour : neighbours) {
          final int t2 = neighbour.getFeature(Spot.FRAME).intValue();
          if (t2 > t1) {
            children.add(neighbour);
          }
        }

        // Only interested in splits to multiple children
        if (children.size() > 1) {
          // Find the first child that is in child category 1.
          // It does not matter how many are in this category.
          final int index =
              children.findIndex(child -> child1Categories.contains(getCategory(child)));
          if (index >= 0) {
            final Spot child1 = children.unsafeGet(index);
            // Remove those children not in child category 2
            children.removeIf(child -> !child2Categories.contains(getCategory(child)));
            // Add back the spot from child category 1
            if (!children.contains(child1)) {
              children.add(child1);
            }
            // If there are 2 or more children then this is a matched track split
            if (children.size() > 1) {
              // Find the original edges between parent and child.
              final LocalList<DefaultWeightedEdge> childEdges = new LocalList<>(children.size());
              edges.forEach(edge -> {
                final Spot child = getChild(trackModel, edge, parent);
                if (children.contains(child)) {
                  childEdges.add(edge);
                }
              });
              // We should have found at least as many edges as children. We may find more
              // if the model has duplicate edges.
              assert childEdges.size() >= children.size() : String.format(
                  "Incomplete track split: %d children, %d edges", children.size(),
                  childEdges.size());
              list.add(new TrackSplit(parent, childEdges));
            }
          }
        }
      });
    });
    return list;
  }

  /**
   * Gets the category of the spot.
   *
   * @param spot the spot
   * @return the category
   */
  private static int getCategory(Spot spot) {
    final Double cat = spot.getFeature(PrecomputedDetector.SPOT_CATEGORY);
    return cat == null ? 0 : cat.intValue();
  }

  /**
   * Gets the child spot for the given edge with the given parent.
   *
   * @param trackModel the track model
   * @param edge the edge
   * @param parent the parent
   * @return the child
   */
  private static Spot getChild(TrackModel trackModel, DefaultWeightedEdge edge, Spot parent) {
    final Spot source = trackModel.getEdgeSource(edge);
    final Spot target = trackModel.getEdgeTarget(edge);
    // We cannot assume the parent=source and child=target
    return parent.equals(source) ? target : source;
  }
}
