/*-
 * #%L
 * Genome Damage and Stability Centre SMLM ImageJ Plugins
 *
 * Software for single molecule localisation microscopy (SMLM)
 * %%
 * Copyright (C) 2011 - 2020 Alex Herbert
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

package uk.ac.sussex.gdsc.utils;

import org.apache.commons.math3.fitting.leastsquares.MultivariateJacobianFunction;
import org.apache.commons.math3.linear.ArrayRealVector;
import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.linear.RealVector;
import org.apache.commons.math3.util.Pair;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import uk.ac.sussex.gdsc.test.api.TestAssertions;
import uk.ac.sussex.gdsc.test.api.TestHelper;
import uk.ac.sussex.gdsc.test.api.function.DoubleDoubleBiPredicate;
import uk.ac.sussex.gdsc.utils.PhotobleachAnalysis_PlugIn.DecayFunction;

@SuppressWarnings({"javadoc"})
class PhotobleachAnalysis_PluginTest {
  @Test
  void canComputeDecayFunction() {
    final double delta = 1e-10;
    final DoubleDoubleBiPredicate test = TestHelper.doublesAreClose(1e-3);
    RealVector v1;
    RealVector v2;
    for (final int size : new int[] {10, 50}) {
      final MultivariateJacobianFunction f = new DecayFunction(size);
      for (final double y : new double[] {20, 30}) {
        for (final double b : new double[] {5, 10}) {
          for (final double tau : new double[] {0.5 / size, 1.0 / size, 2 / size}) {
            // Check the value and Jacobian
            final RealVector point = new ArrayRealVector(new double[] {y, b, tau}, false);
            final Pair<RealVector, RealMatrix> p = f.value(point);
            final double[] value = p.getFirst().toArray();
            Assertions.assertEquals(size, value.length);
            for (int t = 0; t < size; t++) {
              // f(t) = y + b exp(-tau * t)
              final double msd = y + b * Math.exp(-tau * t);
              TestAssertions.assertTest(msd, value[t], test, "value");
            }
            // Columns of the Jacobian
            final double[] dfda1 = p.getSecond().getColumn(0);
            final double[] dfdb1 = p.getSecond().getColumn(1);
            final double[] dfdc1 = p.getSecond().getColumn(2);

            point.setEntry(0, y - delta);
            v1 = f.value(point).getFirst();
            point.setEntry(0, y + delta);
            v2 = f.value(point).getFirst();
            final double[] dfda = v2.subtract(v1).mapDivide(2 * delta).toArray();
            point.setEntry(0, y);

            point.setEntry(1, b - delta);
            v1 = f.value(point).getFirst();
            point.setEntry(1, b + delta);
            v2 = f.value(point).getFirst();
            final double[] dfdb = v2.subtract(v1).mapDivide(2 * delta).toArray();
            point.setEntry(1, b);

            point.setEntry(2, tau - delta);
            v1 = f.value(point).getFirst();
            point.setEntry(2, tau + delta);
            v2 = f.value(point).getFirst();
            final double[] dfdc = v2.subtract(v1).mapDivide(2 * delta).toArray();

            // Element-by-element relative error
            TestAssertions.assertArrayTest(dfda, dfda1, test, "jacobian dfda");
            TestAssertions.assertArrayTest(dfdb, dfdb1, test, "jacobian dfdb");
            TestAssertions.assertArrayTest(dfdc, dfdc1, test, "jacobian dfdc");
          }
        }
      }
    }
  }
}
