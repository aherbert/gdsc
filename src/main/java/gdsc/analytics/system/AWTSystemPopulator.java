package gdsc.analytics.system;

import ij.IJ;

import java.awt.Dimension;
import java.awt.DisplayMode;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.HeadlessException;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.net.InetAddress;
import java.net.UnknownHostException;

import gdsc.analytics.AnalyticsConfigData;

public class AWTSystemPopulator
{

	public static final void populateConfigData(AnalyticsConfigData data)
	{
		data.setEncoding(System.getProperty("file.encoding"));

		String region = System.getProperty("user.region");
		if (region == null)
		{
			region = System.getProperty("user.country");
		}
		data.setUserLanguage(System.getProperty("user.language") + "-" + region);

		String hostName = "localhost";
		try
		{
			hostName = InetAddress.getLocalHost().getHostName();
		}
		catch (UnknownHostException e)
		{
			//ignore this
		}
		data.setHostName(hostName);

		int screenHeight = 0;
		int screenWidth = 0;

		GraphicsEnvironment ge = null;
		GraphicsDevice[] gs = null;

		try
		{
			ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
			gs = ge.getScreenDevices();

			// Get size of each screen
			for (int i = 0; i < gs.length; i++)
			{
				DisplayMode dm = gs[i].getDisplayMode();
				screenWidth += dm.getWidth();
				screenHeight += dm.getHeight();
			}
			if (screenHeight != 0 && screenWidth != 0)
			{
				data.setScreenResolution(screenWidth + "x" + screenHeight);
			}

			if (gs[0] != null)
			{
				String colorDepth = gs[0].getDisplayMode().getBitDepth() + "";
				for (int i = 1; i < gs.length; i++)
				{
					colorDepth += ", " + gs[i].getDisplayMode().getBitDepth();
				}
				data.setColorDepth(colorDepth);
			}
		}
		catch (HeadlessException e)
		{
			// defaults.
			data.setScreenResolution("NA");
			data.setColorDepth("NA");
		}
	}

	public static Dimension getScreenSize()
	{
		if (IJ.isWindows()) // GraphicsEnvironment.getConfigurations is *very* slow on Windows
			return Toolkit.getDefaultToolkit().getScreenSize();
		if (GraphicsEnvironment.isHeadless())
			return new Dimension(0, 0);
		// Can't use Toolkit.getScreenSize() on Linux because it returns 
		// size of all displays rather than just the primary display.
		GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
		GraphicsDevice[] gd = ge.getScreenDevices();
		GraphicsConfiguration[] gc = gd[0].getConfigurations();
		Rectangle bounds = gc[0].getBounds();
		if ((bounds.x == 0 && bounds.y == 0) || (IJ.isLinux() && gc.length > 1))
			return new Dimension(bounds.width, bounds.height);
		else
			return Toolkit.getDefaultToolkit().getScreenSize();
	}

}
