/*-
 * #%L
 * Genome Damage and Stability Centre ImageJ Plugins
 *
 * Software for microscopy image analysis
 * %%
 * Copyright (C) 2011 - 2025 Alex Herbert
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

package uk.ac.sussex.gdsc.ij.trackmate.action;

import fiji.plugin.trackmate.FeatureModel;
import fiji.plugin.trackmate.SelectionModel;
import fiji.plugin.trackmate.TrackMate;
import fiji.plugin.trackmate.action.AbstractTMAction;
import fiji.plugin.trackmate.action.TrackMateAction;
import fiji.plugin.trackmate.action.TrackMateActionFactory;
import fiji.plugin.trackmate.gui.displaysettings.DisplaySettings;
import java.awt.Frame;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import javax.swing.ImageIcon;
import org.scijava.plugin.Plugin;
import uk.ac.sussex.gdsc.core.ij.gui.MultiDialog;
import uk.ac.sussex.gdsc.core.utils.TextUtils;

/**
 * Displays track data in a table.
 */
public class ExportTrackSelectionOptionsAction extends AbstractTMAction {
  /**
   * A factory for creating {@link ExportTrackSelectionOptionsAction} objects.
   */
  @Plugin(type = TrackMateActionFactory.class)
  public static class Factory implements TrackMateActionFactory {
    /** Description of the action. */
    private static final String INFO_TEXT =
        "<html><p>This action configures the data exported by the export track selection "
            + "action.</p></html>";

    /** Key used for the action. */
    private static final String KEY = "EXPORT_TRACK_SELECTION_OPTIONS";

    /** Display name. */
    private static final String NAME = "Configure the export track selection action";

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
    public TrackMateAction create() {
      return new ExportTrackSelectionOptionsAction();
    }
  }

  @Override
  public void execute(TrackMate trackmate, SelectionModel selectionModel,
      DisplaySettings displaySettings, Frame frame) {
    final FeatureModel featureModel = trackmate.getModel().getFeatureModel();
    Collection<String> features = featureModel.getSpotFeatures();
    logger.log("Configuring from " + TextUtils.pleural(features.size(), "feature"));

    // Show a multi-select dialog.
    // Select those that are already selected.
    // Note:
    // The "key" of the feature will be in the ExportTrackSelectionAction class.
    // The dialog will show the "nice" name of the feature. This is done using
    // a function to convert the text for diplay.
    final ArrayList<String> items = new ArrayList<>(features);
    final MultiDialog md = new MultiDialog("Select features", items);
    md.setSelected(ExportTrackSelectionAction.getFeatures());
    final Map<String, String> featureNames = featureModel.getSpotFeatureNames();
    md.setDisplayConverter(featureNames::get);

    md.showDialog();

    if (md.wasCancelled()) {
      logger.log(". Cancelled.\n");
      return;
    }

    final List<String> selected = md.getSelectedResults();
    ExportTrackSelectionAction.setFeatures(selected);

    logger.log(". Selected " + TextUtils.pleural(selected.size(), "feature") + ".\n");
  }
}
