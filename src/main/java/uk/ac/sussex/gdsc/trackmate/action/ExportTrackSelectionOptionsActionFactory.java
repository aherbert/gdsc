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

import fiji.plugin.trackmate.action.TrackMateAction;
import fiji.plugin.trackmate.action.TrackMateActionFactory;
import fiji.plugin.trackmate.gui.TrackMateGUIController;
import javax.swing.ImageIcon;
import org.scijava.plugin.Plugin;

/**
 * A factory for creating {@link ExportTrackSelectionOptionsAction} objects.
 */
@Plugin(type = TrackMateActionFactory.class)
public class ExportTrackSelectionOptionsActionFactory implements TrackMateActionFactory {
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
  public TrackMateAction create(final TrackMateGUIController controller) {
    return new ExportTrackSelectionOptionsAction(controller.getPlugin().getModel());
  }
}
