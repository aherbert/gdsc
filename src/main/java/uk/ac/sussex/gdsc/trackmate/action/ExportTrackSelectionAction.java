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

import uk.ac.sussex.gdsc.core.ij.BufferedTextWindow;
import uk.ac.sussex.gdsc.core.ij.ImageJUtils;
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

import ij.text.TextWindow;

import org.apache.commons.lang3.tuple.Pair;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.DefaultWeightedEdge;
import org.jgrapht.graph.SimpleDirectedGraph;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Displays track data in a table.
 */
public class ExportTrackSelectionAction implements TrackMateAction {
  private static final AtomicReference<TextWindow> resultsRef = new AtomicReference<>();
  private static final AtomicReference<List<String>> featuresRef =
      new AtomicReference<>(Collections.emptyList());

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

    static int compare(Track t1, Track t2) {
      final int result = Integer.compare(t1.trackId, t2.trackId);
      return (result == 0) ? Integer.compare(t1.getStartFrame(), t2.getStartFrame()) : result;
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
    // Q. Support selecting only spots or also use edges?
    final Set<Spot> spotSelection = selectionModel.getSpotSelection();
    // final Set<DefaultWeightedEdge> edgeSelection = selectionModel.getEdgeSelection();
    logger.log("Exporting " + TextUtils.pleural(spotSelection.size(), "spot"));

    // Get track Ids of the selection
    final TrackModel trackModel = model.getTrackModel();
    final TIntHashSet trackIds = getTrackIds(spotSelection, trackModel);
    logger.log(". " + TextUtils.pleural(trackIds.size(), "track id"));

    // For each track traverse the track and output spot data for only the selected region.
    // This is done using TrackMate's convex branch decomposition which creates tracks of
    // spots that have only one predecessor and successor.

    final TimeDirectedNeighborIndex neighborIndex =
        model.getTrackModel().getDirectedNeighborIndex();

    final List<Track> tracks = new ArrayList<>();
    trackIds.forEach(trackId -> {
      final TrackBranchDecomposition branchDecomposition =
          ConvexBranchesDecomposition.processTrack(trackId, trackModel, neighborIndex, true, false);
      final SimpleDirectedGraph<List<Spot>, DefaultEdge> branchGraph =
          ConvexBranchesDecomposition.buildBranchGraph(branchDecomposition);

      // Out sub-sections of the track in time order.
      // Only output those selected spots
      branchGraph.vertexSet()
          .forEach(branch -> convertToTracks(spotSelection, trackId, trackModel, branch, tracks));
      return true;
    });

    logger.log(". " + TextUtils.pleural(tracks.size(), "sub-track"));

    Collections.sort(tracks, Track::compare);

    displayTracks(tracks);
    logger.log(". Done.\n");
  }

  private static TIntHashSet getTrackIds(Set<Spot> spotSelection, TrackModel trackModel) {
    final TIntHashSet trackIds = new TIntHashSet();
    for (final Spot spot : spotSelection) {
      final Integer trackId = trackModel.trackIDOf(spot);
      if (trackId != null) {
        trackIds.add(trackId.intValue());
      }
    }
    return trackIds;
  }

  /**
   * Convert the branch of contiguous spots to tracks. Only those that are selected will be output
   * as a track. If there is a break in the selection of the branch this results in a new track.
   *
   * @param spotSelection the spot selection
   * @param trackId the track id
   * @param trackModel the track model
   * @param branch the branch
   * @param tracks the tracks
   */
  private static void convertToTracks(Set<Spot> spotSelection, int trackId, TrackModel trackModel,
      List<Spot> branch, List<Track> tracks) {
    splitBranch(spotSelection, trackId, trackModel, branch)
        .forEach(track -> tracks.add(convertToTrack(trackId, trackModel, track)));
  }

  /**
   * Split the branch to contiguous tracks of selected spots.
   *
   * @param spotSelection the spot selection
   * @param trackId the track id
   * @param trackModel the track model
   * @param branch the branch
   * @return the list of tracks
   */
  private static List<List<Spot>> splitBranch(Set<Spot> spotSelection, int trackId,
      TrackModel trackModel, List<Spot> branch) {
    final ArrayList<List<Spot>> tracks = new ArrayList<>();
    ArrayList<Spot> spots = null;

    final Iterator<Spot> it = branch.iterator();
    while (it.hasNext()) {
      final Spot spot = it.next();
      if (spotSelection.contains(spot)) {
        // This is a selected part of the branch
        if (spots == null) {
          // First spot in the contiguous track
          spots = new ArrayList<>();
          tracks.add(spots);
        }
        spots.add(spot);
      } else {
        // End of track
        spots = null;
      }
    }

    return tracks;
  }

  /**
   * Convert to a list of spot to a track.
   *
   * @param trackId the track id
   * @param trackModel the track model
   * @param track the track
   * @return the track
   */
  private static Track convertToTrack(int trackId, TrackModel trackModel, List<Spot> track) {
    final ArrayList<Spot> predecessor = getPredecessors(trackModel, track);
    final ArrayList<Spot> spots = new ArrayList<>();
    final Iterator<Spot> it = track.iterator();
    while (it.hasNext()) {
      final Spot spot = it.next();
      spots.add(spot);
    }
    return new Track(trackId, predecessor, spots);
  }

  /**
   * Gets the predecessors of first spot.
   *
   * @param trackModel the track model
   * @param track the track
   * @return the predecessors
   */
  private static ArrayList<Spot> getPredecessors(TrackModel trackModel, List<Spot> track) {
    final ArrayList<Spot> predecessor = new ArrayList<>();
    final Spot first = track.get(0);
    final int startFrame = first.getFeature(Spot.FRAME).intValue();
    for (final DefaultWeightedEdge edge : trackModel.edgesOf(first)) {
      // Find the spot that is not the start
      Spot other = trackModel.getEdgeSource(edge);
      if (other == first) {
        other = trackModel.getEdgeTarget(edge);
      }
      // Any spot before the start must be a predecessor
      if (other.getFeature(Spot.FRAME).intValue() < startFrame) {
        predecessor.add(other);
      }
    }
    return predecessor;
  }

  private void displayTracks(List<Track> tracks) {
    final List<Pair<String, Boolean>> features = createFeaturesList();
    final StringBuilder sb = new StringBuilder();
    try (BufferedTextWindow table = new BufferedTextWindow(createResultsTable(features))) {
      int id = 0;
      int lastTrackId = 0;
      for (final Track track : tracks) {
        if (lastTrackId != track.trackId) {
          id = 0;
          lastTrackId = track.trackId;
        }
        sb.setLength(0);
        sb.append(track.trackId).append('\t');
        sb.append(++id).append('\t');
        final String prefix = sb.toString();

        // First spot predeccesor may be multiple so this is outside a loop
        for (int i = 0; i < track.predecessor.size(); i++) {
          if (i != 0) {
            sb.append(", ");
          }
          sb.append(track.predecessor.get(i));
        }
        addSpot(features, sb, track.spots.get(0));
        table.append(sb.toString());

        // Loop over the remaining spots
        Spot previous = track.spots.get(0);
        for (int i = 1; i < track.spots.size(); i++) {
          final Spot spot = track.spots.get(i);
          sb.setLength(0);
          sb.append(prefix).append(previous);
          addSpot(features, sb, spot);
          previous = spot;
          table.append(sb.toString());
        }
      }
    }
  }

  private List<Pair<String, Boolean>> createFeaturesList() {
    final List<Pair<String, Boolean>> list = new ArrayList<>();
    final Map<String, Boolean> isInt = model.getFeatureModel().getSpotFeatureIsInt();
    for (final String feature : featuresRef.get()) {
      list.add(Pair.of(feature, isInt.get(feature)));
    }
    return list;
  }

  private TextWindow createResultsTable(List<Pair<String, Boolean>> features) {
    return ImageJUtils.refresh(resultsRef, () -> {
      final StringBuilder sb = new StringBuilder("Track Id\tSub-track\tParent\tSpot Id\t");
      final Map<String, String> map = model.getFeatureModel().getSpotFeatureNames();
      for (final Pair<String, Boolean> feature : features) {
        sb.append('\t').append(map.get(feature.getKey()));
      }
      return new TextWindow("Track Selection", sb.toString(), "", 800, 400);
    });
  }

  private static void addSpot(List<Pair<String, Boolean>> features, StringBuilder sb, Spot spot) {
    sb.append('\t').append(spot);
    for (final Pair<String, Boolean> feature : features) {
      sb.append('\t');
      final Double value = spot.getFeature(feature.getKey());
      if (value == null) {
        continue;
      }
      if (feature.getValue()) {
        // Integer value feature
        sb.append(value.intValue());
      } else {
        sb.append(value);
      }
    }
  }

  @Override
  public void setLogger(final Logger logger) {
    this.logger = logger;
  }

  /**
   * Sets the features to display in the TextWindow.
   *
   * @param features the new features
   */
  static void setFeatures(List<String> features) {
    Objects.requireNonNull(features, "Features must not be null");
    // Ensure the text window is recreated
    resultsRef.set(null);
    featuresRef.set(features);
  }

  /**
   * Gets the features to display in the TextWindow.
   *
   * @return the features
   */
  static List<String> getFeatures() {
    return Collections.unmodifiableList(featuresRef.get());
  }
}
