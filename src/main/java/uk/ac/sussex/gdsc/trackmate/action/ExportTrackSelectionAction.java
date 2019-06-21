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

package uk.ac.sussex.gdsc.trackmate.action;

import uk.ac.sussex.gdsc.core.utils.TextUtils;

import fiji.plugin.trackmate.Logger;
import fiji.plugin.trackmate.Model;
import fiji.plugin.trackmate.SelectionModel;
import fiji.plugin.trackmate.Spot;
import fiji.plugin.trackmate.TrackMate;
import fiji.plugin.trackmate.TrackModel;
import fiji.plugin.trackmate.action.TrackMateAction;
import fiji.plugin.trackmate.graph.ConvexBranchesDecomposition;
import fiji.plugin.trackmate.graph.ConvexBranchesDecomposition.TrackBranchDecomposition;
import fiji.plugin.trackmate.graph.TimeDirectedNeighborIndex;

import gnu.trove.set.hash.TIntHashSet;

import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.DefaultWeightedEdge;
import org.jgrapht.graph.SimpleDirectedGraph;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Displays track data in a table.
 */
public class ExportTrackSelectionAction implements TrackMateAction {
  /** The model. */
  private final Model model;
  /** The selection model. */
  private final SelectionModel selectionModel;
  /** The logger. */
  private Logger logger;

  /**
   * Container for a track of consecutive spots. The track is made of spots with a single
   * predecessor and successor. The only exception is the track may have multiple predecessors if
   * track merging was allowed.
   */
  private static class Track {
    int trackId;
    ArrayList<Spot> predecessor;
    ArrayList<Spot> spots;
    int startFrame;

    Track(int trackId, ArrayList<Spot> predecessor, ArrayList<Spot> spots) {
      this.trackId = trackId;
      this.predecessor = predecessor;
      this.spots = spots;
      startFrame = spots.get(0).getFeature(Spot.FRAME).intValue();
    }

    int getStartFrame() {
      return startFrame;
    }
  }

  /**
   * Instantiates a new track data action.
   *
   * @param model the model
   * @param selectionModel the selection model
   */
  public ExportTrackSelectionAction(final Model model, final SelectionModel selectionModel) {
    this.model = model;
    this.selectionModel = selectionModel;
  }

  @Override
  public void execute(final TrackMate trackmate) {
    Set<Spot> spotSelection = selectionModel.getSpotSelection();
    Set<DefaultWeightedEdge> edgeSelection = selectionModel.getEdgeSelection();
    logger.log("Processing " + TextUtils.pleural(spotSelection.size(), "spot") + ", "
        + TextUtils.pleural(edgeSelection.size(), "edge") + "\n");

    // Get track Ids of the selection
    TrackModel trackModel = model.getTrackModel();
    TIntHashSet trackIds = new TIntHashSet();
    for (Spot spot : spotSelection) {
      trackIds.add(trackModel.trackIDOf(spot));
    }

    // For each track traverse the track and output spot data for only the selected region.
    // This is done using TrackMate's convex branch decomposition which creates tracks of
    // spots that have only one predecessor and successor.

    final TimeDirectedNeighborIndex neighborIndex =
        model.getTrackModel().getDirectedNeighborIndex();

    trackIds.forEach(trackId -> {
      final TrackBranchDecomposition branchDecomposition =
          ConvexBranchesDecomposition.processTrack(trackId, trackModel, neighborIndex, true, false);
      final SimpleDirectedGraph<List<Spot>, DefaultEdge> branchGraph =
          ConvexBranchesDecomposition.buildBranchGraph(branchDecomposition);

      // Out sub-sections of the track in time order.
      // Only output those selected spots
      List<Track> tracks = new ArrayList<>();
      for (final List<Spot> branch : branchGraph.vertexSet()) {
        // Find predecessor of first spot
        ArrayList<Spot> predecessor = new ArrayList<>();
        Spot first = branch.get(0);
        Spot second = branch.size() > 1 ? branch.get(1) : null;
        for (DefaultWeightedEdge edge : trackModel.edgesOf(first)) {
          // Find the spot that is not the start
          Spot other = trackModel.getEdgeSource(edge);
          if (other == first) {
            other = trackModel.getEdgeTarget(edge);
          }
          // Any spot not the start or the next must be a predecessor
          if (other != second) {
            predecessor.add(other);
          }
        }

        ArrayList<Spot> spots = new ArrayList<>();
        final Iterator<Spot> it = branch.iterator();
        while (it.hasNext()) {
          final Spot spot = it.next();
          spots.add(spot);
        }

        tracks.add(new Track(trackId, predecessor, spots));
      }

      Collections.sort(tracks, (r1, r2) -> Integer.compare(r1.getStartFrame(), r2.getStartFrame()));

      System.out.printf("Track Id %d%n", trackId);
      for (final Track track : tracks) {
        System.out.println("Parent: " + track.predecessor.stream()
            .map(spot -> Integer.toString(spot.ID())).collect(Collectors.joining(", ", "[", "]")));

        for (Spot spot : track.spots) {
          System.out.printf("Spot Id %d%n", spot.ID());
        }
      }
      System.out.printf("%n");

      return true;
    });
  }

  @Override
  public void setLogger(final Logger logger) {
    this.logger = logger;
  }
}
