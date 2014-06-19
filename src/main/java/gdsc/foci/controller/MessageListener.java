package gdsc.foci.controller;

/*----------------------------------------------------------------------------- 
 * GDSC Plugins for ImageJ
 * 
 * Copyright (C) 2011 Alex Herbert
 * Genome Damage and Stability Centre
 * University of Sussex, UK
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *---------------------------------------------------------------------------*/

/**
 * Provides a mechanism for passing messages about the processing state
 */
public interface MessageListener
{
	public enum MessageType
	{
		BACKGROUND_LEVEL
	}

	/**
	 * @param message The type of the message
	 * @param params The parameters of the message
	 */
	public void notify(MessageType message, Object... params);
}
