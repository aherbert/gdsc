package gdsc.utils;

import java.awt.AWTEvent;

import gdsc.UsageTracker;
import ij.ImagePlus;
import ij.gui.DialogListener;
import ij.gui.GenericDialog;
import ij.plugin.filter.ExtendedPlugInFilter;
import ij.plugin.filter.DifferenceOfGaussians;
import ij.plugin.filter.PlugInFilterRunner;
import ij.process.ImageProcessor;

/**
 * Pass through class allowing the {@link ij.plugin.filter.DifferenceOfGaussians }
 * to be loaded by the ImageJ plugin class loader
 */
public class DifferenceOfGaussiansRunner implements ExtendedPlugInFilter, DialogListener
{
	private DifferenceOfGaussians filter = new DifferenceOfGaussians();
	
    public boolean dialogItemChanged(GenericDialog gd, AWTEvent e) {
    	return filter.dialogItemChanged(gd, e);
    }

	public int setup(String arg, ImagePlus imp)
	{
		UsageTracker.recordPlugin(this.getClass(), arg);
		return filter.setup(arg, imp);
	}

	public void run(ImageProcessor ip)
	{
		filter.run(ip);
	}

	public int showDialog(ImagePlus imp, String command, PlugInFilterRunner pfr)
	{
		return filter.showDialog(imp, command, pfr);
	}

	public void setNPasses(int nPasses)
	{
		filter.setNPasses(nPasses);
    }
}
