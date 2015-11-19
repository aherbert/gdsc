package gdsc.clustering;

/*----------------------------------------------------------------------------- 
 * GDSC SMLM Software
 * 
 * Copyright (C) 2015 Alex Herbert
 * Genome Damage and Stability Centre
 * University of Sussex, UK
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 3 of the License, or
 * (at your option) any later version.
 *---------------------------------------------------------------------------*/

import gdsc.utils.ImageJHelper;
import ij.IJ;

/**
 * Report the progress of processing results
 */
public class IJTrackProgress implements TrackProgress
{
	/*
	 * (non-Javadoc)
	 * 
	 * @see gdsc.utils.fitting.results.TrackProgress#progress(double)
	 */
	public void progress(double fraction)
	{
		IJ.showProgress(fraction);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see gdsc.utils.fitting.results.TrackProgress#progress(long, long)
	 */
	public void progress(long position, long total)
	{
		IJ.showProgress((double) position / total);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see gdsc.smlm.results.TrackProgress#log(java.lang.String, java.lang.Object[])
	 */
	public void log(String format, Object... args)
	{
		IJ.log(String.format(format, args));
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see gdsc.smlm.results.TrackProgress#status(java.lang.String, java.lang.Object[])
	 */
	public void status(String format, Object... args)
	{
		IJ.showStatus(String.format(format, args));
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see gdsc.smlm.results.TrackProgress#isEnded()
	 */
	public boolean isEnded()
	{
		return ImageJHelper.isInterrupted();
	}
}
