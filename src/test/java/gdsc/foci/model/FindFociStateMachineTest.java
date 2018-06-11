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
package gdsc.foci.model;

import java.beans.PropertyChangeEvent;
import java.util.Random;

import org.junit.Test;

public class FindFociStateMachineTest
{
	/**
	 * Performs multiple state changes and outputs the time.
	 */
	@Test
	public void timeStateTransitions()
	{
		FindFociStateMachine sm = new FindFociStateMachine();
		String[] propertyNames = sm.getObservedProperties().toArray(new String[0]);
		Random rand = new Random(30051977);
		Integer oldValue = new Integer(0);
		Integer newValue = new Integer(1);

		String[] randomNames = new String[propertyNames.length * 10];
		for (int j = 0, x = 0; j < 10; j++)
		{
			for (int i = 0; i < propertyNames.length; i++)
			{
				randomNames[x++] = propertyNames[rand.nextInt(propertyNames.length)];
			}
		}

		long start = System.nanoTime();
		long steps = 0;
		while (steps < 1000000)
		{
			for (int j = 0, x = 0; j < 10; j++)
			{
				for (int i = 0; i < propertyNames.length; i++)
				{
					steps++;
					sm.propertyChange(new PropertyChangeEvent(this, randomNames[x++],
							oldValue, newValue));
				}
				sm.setState(FindFociState.COMPLETE);
			}
		}

		System.out.printf("%d steps : %f ms\n", steps, (System.nanoTime() - start) / 1000000.0);
	}
}
