package gdsc.utils;

import gdsc.ImageJTracker;
import ij.plugin.PlugIn;
import ij.plugin.ZProjector2;

/**
 * Pass through class allowing the {@link ij.plugin.filter.MaskParticleAnalyzer }
 * to be loaded by the ImageJ plugin class loader
 */
public class ZProjector2Runner implements PlugIn
{
	private ZProjector2 filter = new ZProjector2();
	
	public void run(String arg)
	{
		ImageJTracker.recordPlugin(this.getClass(), arg);
		filter.run(arg);
	}
}
