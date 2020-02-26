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

import fiji.plugin.trackmate.FeatureModel;
import fiji.plugin.trackmate.Logger;
import fiji.plugin.trackmate.Model;
import fiji.plugin.trackmate.TrackMate;
import fiji.plugin.trackmate.action.TrackMateAction;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import uk.ac.sussex.gdsc.core.ij.gui.MultiDialog;
import uk.ac.sussex.gdsc.core.utils.TextUtils;

/**
 * Displays track data in a table.
 */
public class ExportTrackSelectionOptionsAction implements TrackMateAction {
  /** The model. */
  private final Model model;
  /** The logger. */
  private Logger logger;

  /**
   * Instantiates a new track data action.
   *
   * @param model the model
   */
  public ExportTrackSelectionOptionsAction(final Model model) {
    this.model = model;
  }

  @Override
  public void execute(final TrackMate trackmate) {
    final FeatureModel featureModel = model.getFeatureModel();
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

  @Override
  public void setLogger(final Logger logger) {
    this.logger = logger;
  }
}
