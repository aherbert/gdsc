/*-
 * #%L
 * Genome Damage and Stability Centre ImageJ Plugins
 * 
 * Software for microscopy image analysis
 * %%
 * Copyright (C) 2011 - 2018 Alex Herbert
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
package gdsc.utils;

import java.awt.AWTEvent;

import gdsc.UsageTracker;
import ij.ImagePlus;
import ij.gui.DialogListener;
import ij.gui.GenericDialog;
import ij.plugin.filter.DifferenceOfGaussians;
import ij.plugin.filter.ExtendedPlugInFilter;
import ij.plugin.filter.PlugInFilterRunner;
import ij.process.ImageProcessor;

/**
 * Pass through class allowing the {@link ij.plugin.filter.DifferenceOfGaussians }
 * to be loaded by the ImageJ plugin class loader
 */
public class DifferenceOfGaussiansRunner implements ExtendedPlugInFilter, DialogListener
{
	private DifferenceOfGaussians filter = new DifferenceOfGaussians();

	@Override
	public boolean dialogItemChanged(GenericDialog gd, AWTEvent e)
	{
		return filter.dialogItemChanged(gd, e);
	}

	@Override
	public int setup(String arg, ImagePlus imp)
	{
		UsageTracker.recordPlugin(this.getClass(), arg);
		return filter.setup(arg, imp);
	}

	@Override
	public void run(ImageProcessor ip)
	{
		filter.run(ip);
	}

	@Override
	public int showDialog(ImagePlus imp, String command, PlugInFilterRunner pfr)
	{
		return filter.showDialog(imp, command, pfr);
	}

	@Override
	public void setNPasses(int nPasses)
	{
		filter.setNPasses(nPasses);
	}
}
