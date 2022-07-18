/*-
 * #%L
 * Genome Damage and Stability Centre ImageJ Plugins
 *
 * Software for microscopy image analysis
 * %%
 * Copyright (C) 2011 - 2022 Alex Herbert
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

package uk.ac.sussex.gdsc.ij.foci.gui;

import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import uk.ac.sussex.gdsc.core.ij.ImageJUtils;
import uk.ac.sussex.gdsc.ij.help.Urls;

/**
 * Simple {@link MouseListener} that can be used to show the FindFoci help page for the
 * {@link MouseListener#mouseClicked(MouseEvent)} event.
 */
class FindFociHelpMouseListener extends MouseAdapter {

  /** The instance. */
  public static final FindFociHelpMouseListener INSTANCE = new FindFociHelpMouseListener();

  /**
   * No public construction.
   */
  private FindFociHelpMouseListener() {}

  @Override
  public void mouseClicked(MouseEvent event) {
    ImageJUtils.showUrl(Urls.FIND_FOCI);
  }
}
