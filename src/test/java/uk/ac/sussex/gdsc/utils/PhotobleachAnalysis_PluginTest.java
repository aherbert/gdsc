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
import uk.ac.sussex.gdsc.utils.PhotobleachAnalysis_PlugIn.RecoveryFunction;
import uk.ac.sussex.gdsc.utils.PhotobleachAnalysis_PlugIn.RecoveryFunction1;
import uk.ac.sussex.gdsc.utils.PhotobleachAnalysis_PlugIn.RecoveryFunctionB;
import uk.ac.sussex.gdsc.utils.PhotobleachAnalysis_PlugIn.SimpleRecoveryFunction;

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
              final double ft = y + b * Math.exp(-tau * t);
              TestAssertions.assertTest(ft, value[t], test, "value");
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

  @Test
  void canComputeSimpleRecoveryFunction() {
    final double delta = 1e-10;
    final DoubleDoubleBiPredicate test = TestHelper.doublesAreClose(1e-3);
    RealVector v1;
    RealVector v2;
    for (final int size : new int[] {10, 50}) {
      final MultivariateJacobianFunction f = new SimpleRecoveryFunction(size);
      for (final double y : new double[] {20, 30}) {
        for (final double a : new double[] {5, 10}) {
          for (final double tau : new double[] {0.5 / size, 1.0 / size, 2 / size}) {
            // Check the value and Jacobian
            final RealVector point = new ArrayRealVector(new double[] {y, a, tau}, false);
            final Pair<RealVector, RealMatrix> p = f.value(point);
            final double[] value = p.getFirst().toArray();
            Assertions.assertEquals(size, value.length);
            for (int t = 0; t < size; t++) {
              // f(t) = y0 + A(1 - exp(-tau1 * t))
              final double ft = y + a * (1 - Math.exp(-tau * t));
              TestAssertions.assertTest(ft, value[t], test, "value");
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

            point.setEntry(1, a - delta);
            v1 = f.value(point).getFirst();
            point.setEntry(1, a + delta);
            v2 = f.value(point).getFirst();
            final double[] dfdb = v2.subtract(v1).mapDivide(2 * delta).toArray();
            point.setEntry(1, a);

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

  @Test
  void canComputeRecoveryFunction() {
    final double delta = 1e-10;
    final DoubleDoubleBiPredicate test = TestHelper.doublesAreClose(1e-3, 1e-4);
    RealVector v1;
    RealVector v2;
    for (final int size : new int[] {10, 50}) {
      final MultivariateJacobianFunction f = new RecoveryFunction(size);
      for (final double y : new double[] {20, 30}) {
        for (final double a : new double[] {5, 10}) {
          for (final double tau1 : new double[] {0.5 / size, 1.0 / size, 2 / size}) {
            for (final double tau2 : new double[] {0.5 / size, 1.0 / size, 2 / size}) {
              // Check the value and Jacobian
              final RealVector point = new ArrayRealVector(new double[] {y, a, tau1, tau2}, false);
              final Pair<RealVector, RealMatrix> p = f.value(point);
              final double[] value = p.getFirst().toArray();
              Assertions.assertEquals(size, value.length);
              for (int t = 0; t < size; t++) {
                // f(t) = y0 + A(1 - exp(-tau1 * t))exp(-tau2 * t)
                final double ft = y + a * (1 - Math.exp(-tau1 * t)) * Math.exp(-tau2 * t);
                TestAssertions.assertTest(ft, value[t], test, "value");
              }
              // Columns of the Jacobian
              final double[] dfda1 = p.getSecond().getColumn(0);
              final double[] dfdb1 = p.getSecond().getColumn(1);
              final double[] dfdc1 = p.getSecond().getColumn(2);
              final double[] dfdd1 = p.getSecond().getColumn(3);

              point.setEntry(0, y - delta);
              v1 = f.value(point).getFirst();
              point.setEntry(0, y + delta);
              v2 = f.value(point).getFirst();
              final double[] dfda = v2.subtract(v1).mapDivide(2 * delta).toArray();
              point.setEntry(0, y);

              point.setEntry(1, a - delta);
              v1 = f.value(point).getFirst();
              point.setEntry(1, a + delta);
              v2 = f.value(point).getFirst();
              final double[] dfdb = v2.subtract(v1).mapDivide(2 * delta).toArray();
              point.setEntry(1, a);

              point.setEntry(2, tau1 - delta);
              v1 = f.value(point).getFirst();
              point.setEntry(2, tau1 + delta);
              v2 = f.value(point).getFirst();
              final double[] dfdc = v2.subtract(v1).mapDivide(2 * delta).toArray();
              point.setEntry(2, tau1);

              point.setEntry(3, tau2 - delta);
              v1 = f.value(point).getFirst();
              point.setEntry(3, tau2 + delta);
              v2 = f.value(point).getFirst();
              final double[] dfdd = v2.subtract(v1).mapDivide(2 * delta).toArray();
              point.setEntry(3, tau2);

              // Element-by-element relative error
              TestAssertions.assertArrayTest(dfda, dfda1, test, "jacobian dfda");
              TestAssertions.assertArrayTest(dfdb, dfdb1, test, "jacobian dfdb");
              TestAssertions.assertArrayTest(dfdc, dfdc1, test, "jacobian dfdc");
              TestAssertions.assertArrayTest(dfdd, dfdd1, test, "jacobian dfdd");
            }
          }
        }
      }
    }
  }


  @Test
  void canComputeRecoveryFunctionB() {
    final double delta = 1e-10;
    final DoubleDoubleBiPredicate test = TestHelper.doublesAreClose(1e-3, 1e-4);
    RealVector v1;
    RealVector v2;
    for (final int size : new int[] {10, 50}) {
      final MultivariateJacobianFunction f = new RecoveryFunctionB(size);
      for (final double y : new double[] {20, 30}) {
        for (final double a : new double[] {5, 10}) {
          for (final double tau1 : new double[] {0.5 / size, 1.0 / size, 2 / size}) {
            for (final double b : new double[] {1, 2}) {
              for (final double tau2 : new double[] {0.5 / size, 1.0 / size, 2 / size}) {
                // Check the value and Jacobian
                final RealVector point =
                    new ArrayRealVector(new double[] {y, a, tau1, b, tau2}, false);
                final Pair<RealVector, RealMatrix> p = f.value(point);
                final double[] value = p.getFirst().toArray();
                Assertions.assertEquals(size, value.length);
                for (int t = 0; t < size; t++) {
                  // f(t) = y0 + A(1 - exp(-tau1 * t))exp(-tau2 * t) + B exp(-tau2 * t)
                  final double x = Math.exp(-tau2 * t);
                  final double ft = y + a * (1 - Math.exp(-tau1 * t)) * x + b * x;
                  TestAssertions.assertTest(ft, value[t], test, "value");
                }
                // Columns of the Jacobian
                final double[] dfda1 = p.getSecond().getColumn(0);
                final double[] dfdb1 = p.getSecond().getColumn(1);
                final double[] dfdc1 = p.getSecond().getColumn(2);
                final double[] dfdd1 = p.getSecond().getColumn(3);
                final double[] dfde1 = p.getSecond().getColumn(4);

                point.setEntry(0, y - delta);
                v1 = f.value(point).getFirst();
                point.setEntry(0, y + delta);
                v2 = f.value(point).getFirst();
                final double[] dfda = v2.subtract(v1).mapDivide(2 * delta).toArray();
                point.setEntry(0, y);

                point.setEntry(1, a - delta);
                v1 = f.value(point).getFirst();
                point.setEntry(1, a + delta);
                v2 = f.value(point).getFirst();
                final double[] dfdb = v2.subtract(v1).mapDivide(2 * delta).toArray();
                point.setEntry(1, a);

                point.setEntry(2, tau1 - delta);
                v1 = f.value(point).getFirst();
                point.setEntry(2, tau1 + delta);
                v2 = f.value(point).getFirst();
                final double[] dfdc = v2.subtract(v1).mapDivide(2 * delta).toArray();
                point.setEntry(2, tau1);

                point.setEntry(3, b - delta);
                v1 = f.value(point).getFirst();
                point.setEntry(3, b + delta);
                v2 = f.value(point).getFirst();
                final double[] dfdd = v2.subtract(v1).mapDivide(2 * delta).toArray();
                point.setEntry(3, b);

                point.setEntry(4, tau2 - delta);
                v1 = f.value(point).getFirst();
                point.setEntry(4, tau2 + delta);
                v2 = f.value(point).getFirst();
                final double[] dfde = v2.subtract(v1).mapDivide(2 * delta).toArray();
                point.setEntry(4, tau2);

                // Element-by-element relative error
                TestAssertions.assertArrayTest(dfda, dfda1, test, "jacobian dfda");
                TestAssertions.assertArrayTest(dfdb, dfdb1, test, "jacobian dfdb");
                TestAssertions.assertArrayTest(dfdc, dfdc1, test, "jacobian dfdc");
                TestAssertions.assertArrayTest(dfdd, dfdd1, test, "jacobian dfdd");
                TestAssertions.assertArrayTest(dfde, dfde1, test, "jacobian dfde");
              }
            }
          }
        }
      }
    }
  }

  @Test
  void canComputeRecoveryFunction1() {
    final double delta = 1e-10;
    final DoubleDoubleBiPredicate test = TestHelper.doublesAreClose(1e-3);
    RealVector v1;
    RealVector v2;
    for (final int size : new int[] {10, 50}) {
      final MultivariateJacobianFunction f = new RecoveryFunction1(size);
      for (final double y : new double[] {20, 30}) {
        for (final double a : new double[] {5, 10}) {
          for (final double tau1 : new double[] {0.5 / size, 1.0 / size, 2 / size}) {
            for (final double b : new double[] {5, 10}) {
              for (final double tau2 : new double[] {0.5 / size, 1.0 / size, 2 / size}) {
                // Check the value and Jacobian
                final RealVector point =
                    new ArrayRealVector(new double[] {y, a, tau1, b, tau2}, false);
                final Pair<RealVector, RealMatrix> p = f.value(point);
                final double[] value = p.getFirst().toArray();
                Assertions.assertEquals(size, value.length);
                for (int t = 0; t < size; t++) {
                  // f(t) = A(1 - exp(-tau1 * t))(y0 + B exp(-tau2 * t)
                  final double ft = a * (1 - Math.exp(-tau1 * t)) * (y + b * Math.exp(-tau2 * t));
                  TestAssertions.assertTest(ft, value[t], test, "value");
                }
                // Columns of the Jacobian
                final double[] dfda1 = p.getSecond().getColumn(0);
                final double[] dfdb1 = p.getSecond().getColumn(1);
                final double[] dfdc1 = p.getSecond().getColumn(2);
                final double[] dfdd1 = p.getSecond().getColumn(3);
                final double[] dfde1 = p.getSecond().getColumn(4);

                point.setEntry(0, y - delta);
                v1 = f.value(point).getFirst();
                point.setEntry(0, y + delta);
                v2 = f.value(point).getFirst();
                final double[] dfda = v2.subtract(v1).mapDivide(2 * delta).toArray();
                point.setEntry(0, y);

                point.setEntry(1, a - delta);
                v1 = f.value(point).getFirst();
                point.setEntry(1, a + delta);
                v2 = f.value(point).getFirst();
                final double[] dfdb = v2.subtract(v1).mapDivide(2 * delta).toArray();
                point.setEntry(1, a);

                point.setEntry(2, tau1 - delta);
                v1 = f.value(point).getFirst();
                point.setEntry(2, tau1 + delta);
                v2 = f.value(point).getFirst();
                final double[] dfdc = v2.subtract(v1).mapDivide(2 * delta).toArray();
                point.setEntry(2, tau1);

                point.setEntry(3, b - delta);
                v1 = f.value(point).getFirst();
                point.setEntry(3, b + delta);
                v2 = f.value(point).getFirst();
                final double[] dfdd = v2.subtract(v1).mapDivide(2 * delta).toArray();
                point.setEntry(3, b);

                point.setEntry(4, tau2 - delta);
                v1 = f.value(point).getFirst();
                point.setEntry(4, tau2 + delta);
                v2 = f.value(point).getFirst();
                final double[] dfde = v2.subtract(v1).mapDivide(2 * delta).toArray();
                point.setEntry(4, tau2);

                // Element-by-element relative error
                TestAssertions.assertArrayTest(dfda, dfda1, test, "jacobian dfda");
                TestAssertions.assertArrayTest(dfdb, dfdb1, test, "jacobian dfdb");
                TestAssertions.assertArrayTest(dfdc, dfdc1, test, "jacobian dfdc");
                TestAssertions.assertArrayTest(dfdd, dfdd1, test, "jacobian dfdd");
                TestAssertions.assertArrayTest(dfde, dfde1, test, "jacobian dfde");
              }
            }
          }
        }
      }
    }
  }
}
