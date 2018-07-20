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
package uk.ac.sussex.gdsc.utils;

import ij.ImagePlus;
import ij.plugin.filter.MaskParticleAnalyzer;
import ij.plugin.filter.PlugInFilter;
import ij.process.ImageProcessor;
import uk.ac.sussex.gdsc.UsageTracker;

/**
 * Pass through class allowing the {@link ij.plugin.filter.MaskParticleAnalyzer }
 * to be loaded by the ImageJ plugin class loader.
 */
public class MaskParticleAnalyzerRunner implements PlugInFilter
{
	private final MaskParticleAnalyzer filter = new MaskParticleAnalyzer();

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
}
