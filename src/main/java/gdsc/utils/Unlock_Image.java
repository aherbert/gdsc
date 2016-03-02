package gdsc.utils;
import gdsc.PluginTracker;
import ij.ImagePlus;
import ij.plugin.filter.PlugInFilter;
import ij.process.ImageProcessor;

/**
 * Unlocks the image
 */
public class Unlock_Image implements PlugInFilter
{
	/*
	 * (non-Javadoc)
	 * 
	 * @see ij.plugin.filter.PlugInFilter#setup(java.lang.String, ij.ImagePlus)
	 */
	public int setup(String arg, ImagePlus imp)
	{
		PluginTracker.recordPlugin(this.getClass(), arg);
		if (imp != null)
		{
			imp.unlock();
		}
		return DONE;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see ij.plugin.filter.PlugInFilter#run(ij.process.ImageProcessor)
	 */
	public void run(ImageProcessor inputProcessor)
	{
	}
}
