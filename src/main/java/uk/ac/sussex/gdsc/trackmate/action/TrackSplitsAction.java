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

import fiji.plugin.trackmate.Dimension;
import fiji.plugin.trackmate.Model;
import fiji.plugin.trackmate.Settings;
import fiji.plugin.trackmate.Spot;
import fiji.plugin.trackmate.TrackMate;
import fiji.plugin.trackmate.TrackModel;
import fiji.plugin.trackmate.action.AbstractTMAction;
import fiji.plugin.trackmate.action.TrackMateAction;
import fiji.plugin.trackmate.action.TrackMateActionFactory;
import fiji.plugin.trackmate.gui.TrackMateGUIController;
import fiji.plugin.trackmate.util.TMUtils;
import gnu.trove.map.hash.TIntIntHashMap;
import ij.gui.Plot;
import java.util.HashSet;
import java.util.Set;
import javax.swing.ImageIcon;
import org.jgrapht.graph.DefaultWeightedEdge;
import org.scijava.plugin.Plugin;
import uk.ac.sussex.gdsc.core.ij.ImageJUtils;
import uk.ac.sussex.gdsc.core.utils.SimpleArrayUtils;
import uk.ac.sussex.gdsc.core.utils.TextUtils;

/**
 * Displays track data in a table.
 */
public class TrackSplitsAction extends AbstractTMAction {
  /**
   * A factory for creating {@link TrackSplitsAction} objects.
   */
  @Plugin(type = TrackMateActionFactory.class)
  public static class Factory implements TrackMateActionFactory {
    /** Description of the action. */
    private static final String INFO_TEXT =
        "<html><p>This action will count the track splits per frame.</p></html>";

    /** Key used for the action. */
    private static final String KEY = "TRACK_SPLITS";

    /** Display name. */
    private static final String NAME = "Count the track splits";

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
      return new TrackSplitsAction();
    }
  }

  @Override
  public void execute(final TrackMate trackmate) {
    final Model model = trackmate.getModel();
    final TrackModel trackModel = model.getTrackModel();

    // Visible tracks only
    final Set<Integer> trackIds = trackModel.unsortedTrackIDs(true);
    logger.log("Analysing " + TextUtils.pleural(trackIds.size(), "track id"));
    final TIntIntHashMap counts = new TIntIntHashMap(trackIds.size() * 2);
    trackIds.forEach(trackId -> {
      final Set<Spot> spots = trackModel.trackSpots(trackId);
      spots.forEach(spot -> {
        // Performed as per trackmate.features.track.TrackBranchingAnalyzer
        final Set<DefaultWeightedEdge> edges = trackModel.edgesOf(spot);

        final Set<Spot> neighbours = new HashSet<>(edges.size() + 1);
        for (final DefaultWeightedEdge edge : edges) {
          neighbours.add(trackModel.getEdgeSource(edge));
          neighbours.add(trackModel.getEdgeTarget(edge));
        }
        neighbours.remove(spot);

        // Inspect neighbours relative time position
        int earlier = 0;
        int later = 0;
        final int t1 = spot.getFeature(Spot.FRAME).intValue();
        for (final Spot neighbour : neighbours) {
          final int t2 = neighbour.getFeature(Spot.FRAME).intValue();
          if (t2 < t1) {
            earlier++; // Neighbour is before in time
          } else {
            later++;
          }
        }

        // 'Classify spot' uses: classical; split; merge; complex.
        // Note: If earlier is above 1 then this is a complex spot.
        if (later > 1 && earlier <= 1) {
          // Store the frame
          counts.adjustOrPutValue(t1, 1, 1);
        }
      });
    });

    // Plot the splits across the entire time series used in the analysis.
    final Settings settings = trackmate.getSettings();
    final int tstart = settings.tstart;
    final int tend = settings.tend;
    final int[] frames = SimpleArrayUtils.newArray(tend - tstart + 1, tstart, 1);
    final float[] time = SimpleArrayUtils.toFloat(frames);
    SimpleArrayUtils.multiply(time, settings.dt);
    final float[] frequency = new float[frames.length];
    counts.forEachEntry((t, c) -> {
      frequency[t - tstart] = c;
      return true;
    });

    // X label
    final String title = "Track Splits";
    final String xLabel = "Time ("
        + TMUtils.getUnitsFor(Dimension.TIME, model.getSpaceUnits(), model.getTimeUnits()) + ")";
    final Plot plot = new Plot(title, xLabel, "Frequency");
    plot.addPoints(time, frequency, Plot.SEPARATED_BAR);
    ImageJUtils.display(title, plot);

    logger.log(". Done.\n");
  }
}
