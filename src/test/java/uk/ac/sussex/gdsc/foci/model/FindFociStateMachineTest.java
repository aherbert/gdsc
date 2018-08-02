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
package uk.ac.sussex.gdsc.foci.model;

import java.beans.PropertyChangeEvent;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.rng.UniformRandomProvider;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;

import uk.ac.sussex.gdsc.test.TestLog;
import uk.ac.sussex.gdsc.test.TestSettings;
import uk.ac.sussex.gdsc.test.junit5.ExtraAssumptions;
import uk.ac.sussex.gdsc.test.junit5.RandomSeed;
import uk.ac.sussex.gdsc.test.junit5.SeededTest;

@SuppressWarnings({ "javadoc" })
public class FindFociStateMachineTest
{
    private static Logger logger;

    @BeforeAll
    public static void beforeAll()
    {
        logger = Logger.getLogger(FindFociStateMachineTest.class.getName());
    }

    @AfterAll
    public static void afterAll()
    {
        logger = null;
    }

    /**
     * Performs multiple state changes and outputs the time.
     */
    @SeededTest
    public void timeStateTransitions(RandomSeed seed)
    {
        Level level = Level.INFO;
        ExtraAssumptions.assume(logger, level);

        final FindFociStateMachine sm = new FindFociStateMachine();
        final String[] propertyNames = sm.getObservedProperties().toArray(new String[0]);
        UniformRandomProvider rand = TestSettings.getRandomGenerator(seed.getSeed());
        final Integer oldValue = new Integer(0);
        final Integer newValue = new Integer(1);

        final String[] randomNames = new String[propertyNames.length * 10];
        for (int j = 0, x = 0; j < 10; j++)
            for (int i = 0; i < propertyNames.length; i++)
                randomNames[x++] = propertyNames[rand.nextInt(propertyNames.length)];

        final long start = System.nanoTime();
        long steps = 0;
        while (steps < 1000000)
            for (int j = 0, x = 0; j < 10; j++)
            {
                for (int i = 0; i < propertyNames.length; i++)
                {
                    steps++;
                    sm.propertyChange(new PropertyChangeEvent(this, randomNames[x++], oldValue, newValue));
                }
                sm.setState(FindFociState.COMPLETE);
            }

        TestLog.log(logger, level, "%d steps : %f ms", steps, (System.nanoTime() - start) / 1000000.0);
    }
}
