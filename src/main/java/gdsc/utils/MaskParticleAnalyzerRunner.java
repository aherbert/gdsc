package gdsc.utils;

import gdsc.ImageJTracker;
import ij.ImagePlus;
import ij.plugin.filter.MaskParticleAnalyzer;
import ij.plugin.filter.PlugInFilter;
import ij.process.ImageProcessor;

/**
 * Pass through class allowing the {@link ij.plugin.filter.MaskParticleAnalyzer }
 * to be loaded by the ImageJ plugin class loader
 */
public class MaskParticleAnalyzerRunner implements PlugInFilter
{
	private MaskParticleAnalyzer filter = new MaskParticleAnalyzer();
	
	public int setup(String arg, ImagePlus imp)
	{
		ImageJTracker.recordPlugin("Mask Particle Analyzer", arg);
		return filter.setup(arg, imp);
	}

	public void run(ImageProcessor ip)
	{
		filter.run(ip);
	}
}
