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
package uk.ac.sussex.gdsc.foci.controller;

/**
 * Provides a mechanism for passing messages about the processing state.
 */
public interface MessageListener
{
	/**
	 * Define the message type.
	 */
	public enum MessageType
	{
		/** The background level message type. */
		BACKGROUND_LEVEL,
		/** The sort index ok message type. */
		SORT_INDEX_OK,
		/** The sort index sensitive to negative values message type. */
		SORT_INDEX_SENSITIVE_TO_NEGATIVE_VALUES,
		/** The error message type. */
		ERROR,
		/** The ready message type. */
		READY,
		/** The running message type. */
		RUNNING,
		/** The done message type. */
		DONE,
		/** The failed message type. */
		FAILED,
		/** The finished message type. */
		FINISHED
	}

	/**
	 * Notify.
	 *
	 * @param message
	 *            The type of the message
	 * @param params
	 *            The parameters of the message
	 */
	public void notify(MessageType message, Object... params);
}
