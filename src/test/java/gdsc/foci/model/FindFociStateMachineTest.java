package gdsc.foci.model;

import java.beans.PropertyChangeEvent;
import java.util.Random;

import org.junit.Test;

import gdsc.foci.model.FindFociState;
import gdsc.foci.model.FindFociStateMachine;

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
