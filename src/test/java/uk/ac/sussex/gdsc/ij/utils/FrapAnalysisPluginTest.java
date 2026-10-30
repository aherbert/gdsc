/*-
 * #%L
 * Genome Damage and Stability Centre ImageJ Plugins
 *
 * Software for microscopy image analysis
 * %%
 * Copyright (C) 2011 - 2026 Alex Herbert
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

package uk.ac.sussex.gdsc.ij.utils;

import ij.ImagePlus;
import ij.ImageStack;
import ij.process.FloatProcessor;
import org.apache.commons.math3.linear.ArrayRealVector;
import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.linear.RealVector;
import org.apache.commons.math3.util.Pair;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import uk.ac.sussex.gdsc.ij.utils.FrapAnalysis_PlugIn.Bessel;
import uk.ac.sussex.gdsc.ij.utils.FrapAnalysis_PlugIn.DecayFunction;
import uk.ac.sussex.gdsc.ij.utils.FrapAnalysis_PlugIn.DiffusionLimitedRecoveryFunction;
import uk.ac.sussex.gdsc.ij.utils.FrapAnalysis_PlugIn.DiffusionLimitedRecoveryFunctionB;
import uk.ac.sussex.gdsc.ij.utils.FrapAnalysis_PlugIn.FrapFunction;
import uk.ac.sussex.gdsc.ij.utils.FrapAnalysis_PlugIn.ReactionLimitedRecoveryFunction;
import uk.ac.sussex.gdsc.ij.utils.FrapAnalysis_PlugIn.ReactionLimitedRecoveryFunctionB;
import uk.ac.sussex.gdsc.test.api.Predicates;
import uk.ac.sussex.gdsc.test.api.TestAssertions;
import uk.ac.sussex.gdsc.test.api.function.DoubleDoubleBiPredicate;

@SuppressWarnings({"javadoc"})
class FrapAnalysisPluginTest {
  @Test
  void canComputeDecayFunction() {
    final double delta = 0x1.0p-30;
    final DoubleDoubleBiPredicate test = Predicates.doublesAreRelativelyClose(1e-3);
    RealVector v1;
    RealVector v2;
    for (final int size : new int[] {10, 50}) {
      final FrapFunction f = new DecayFunction(size, 1);
      for (final double b : new double[] {5, 10}) {
        for (final double a : new double[] {20, 30}) {
          for (final double tau : new double[] {0.5 / size, 1.0 / size, 2.0 / size}) {
            // Check the value and Jacobian
            final RealVector point = new ArrayRealVector(new double[] {b, a, tau}, false);
            final Pair<RealVector, RealMatrix> p = f.value(point);
            final double[] value = p.getFirst().toArray();
            Assertions.assertEquals(size, value.length);
            for (int t = 0; t < size; t++) {
              // f(t) = b + a exp(-tau * t)
              final double ft = b + a * Math.exp(-tau * t);
              TestAssertions.assertTest(ft, value[t], test, "value");
            }
            Assertions.assertArrayEquals(value, f.values(point));

            // Columns of the Jacobian
            final double[] dfda1 = p.getSecond().getColumn(0);
            final double[] dfdb1 = p.getSecond().getColumn(1);
            final double[] dfdc1 = p.getSecond().getColumn(2);

            point.setEntry(0, b - delta);
            v1 = f.value(point).getFirst();
            point.setEntry(0, b + delta);
            v2 = f.value(point).getFirst();
            final double[] dfda = v2.subtract(v1).mapDivide(2 * delta).toArray();
            point.setEntry(0, b);

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
  void canComputeReactionLimitedRecoveryFunction() {
    final double delta = 0x1.0p-30;
    final DoubleDoubleBiPredicate test = Predicates.doublesAreRelativelyClose(1e-3);
    RealVector v1;
    RealVector v2;
    for (final int size : new int[] {10, 50}) {
      final FrapFunction f = new ReactionLimitedRecoveryFunction(size, 1);
      for (final double i0 : new double[] {20, 30}) {
        for (final double a : new double[] {5, 10}) {
          for (final double koff : new double[] {0.5 / size, 1.0 / size, 2.0 / size}) {
            // Check the value and Jacobian
            final RealVector point = new ArrayRealVector(new double[] {i0, a, koff}, false);
            final Pair<RealVector, RealMatrix> p = f.value(point);
            final double[] value = p.getFirst().toArray();
            Assertions.assertEquals(size, value.length);
            for (int t = 0; t < size; t++) {
              // f(t) = i0 + A(1 - exp(-koff * t))
              final double ft = i0 + a * (1 - Math.exp(-koff * t));
              TestAssertions.assertTest(ft, value[t], test, "value");
            }
            Assertions.assertArrayEquals(value, f.values(point));

            // Columns of the Jacobian
            final double[] dfda1 = p.getSecond().getColumn(0);
            final double[] dfdb1 = p.getSecond().getColumn(1);
            final double[] dfdc1 = p.getSecond().getColumn(2);

            point.setEntry(0, i0 - delta);
            v1 = f.value(point).getFirst();
            point.setEntry(0, i0 + delta);
            v2 = f.value(point).getFirst();
            final double[] dfda = v2.subtract(v1).mapDivide(2 * delta).toArray();
            point.setEntry(0, i0);

            point.setEntry(1, a - delta);
            v1 = f.value(point).getFirst();
            point.setEntry(1, a + delta);
            v2 = f.value(point).getFirst();
            final double[] dfdb = v2.subtract(v1).mapDivide(2 * delta).toArray();
            point.setEntry(1, a);

            point.setEntry(2, koff - delta);
            v1 = f.value(point).getFirst();
            point.setEntry(2, koff + delta);
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
  void canComputeReactionLimitedRecoveryFunctionB() {
    final double delta = 0x1.0p-30;
    final DoubleDoubleBiPredicate test = Predicates.doublesAreRelativelyClose(1e-3);
    RealVector v1;
    RealVector v2;
    for (final int size : new int[] {10, 50}) {
      final FrapFunction f = new ReactionLimitedRecoveryFunctionB(size, 1);
      for (final double i0 : new double[] {20, 30}) {
        for (final double a : new double[] {5, 10}) {
          for (final double koff : new double[] {0.5 / size, 1.0 / size, 2.0 / size}) {
            for (final double b : new double[] {1, 2}) {
              for (final double tau : new double[] {0.5 / size, 1.0 / size, 2.0 / size}) {
                // Check the value and Jacobian
                final RealVector point =
                    new ArrayRealVector(new double[] {i0, a, koff, b, tau}, false);
                final Pair<RealVector, RealMatrix> p = f.value(point);
                final double[] value = p.getFirst().toArray();
                Assertions.assertEquals(size, value.length);
                for (int t = 0; t < size; t++) {
                  // f(t) = B + (i0 + A(1 - exp(-koff * t))) * exp(-tau * t)
                  final double ft = b + (i0 + a * (1 - Math.exp(-koff * t))) * Math.exp(-tau * t);
                  TestAssertions.assertTest(ft, value[t], test, "value");
                }
                Assertions.assertArrayEquals(value, f.values(point));

                // Columns of the Jacobian
                final double[] dfda1 = p.getSecond().getColumn(0);
                final double[] dfdb1 = p.getSecond().getColumn(1);
                final double[] dfdc1 = p.getSecond().getColumn(2);
                final double[] dfdd1 = p.getSecond().getColumn(3);
                final double[] dfde1 = p.getSecond().getColumn(4);

                point.setEntry(0, i0 - delta);
                v1 = f.value(point).getFirst();
                point.setEntry(0, i0 + delta);
                v2 = f.value(point).getFirst();
                final double[] dfda = v2.subtract(v1).mapDivide(2 * delta).toArray();
                point.setEntry(0, i0);

                point.setEntry(1, a - delta);
                v1 = f.value(point).getFirst();
                point.setEntry(1, a + delta);
                v2 = f.value(point).getFirst();
                final double[] dfdb = v2.subtract(v1).mapDivide(2 * delta).toArray();
                point.setEntry(1, a);

                point.setEntry(2, koff - delta);
                v1 = f.value(point).getFirst();
                point.setEntry(2, koff + delta);
                v2 = f.value(point).getFirst();
                final double[] dfdc = v2.subtract(v1).mapDivide(2 * delta).toArray();
                point.setEntry(2, koff);

                point.setEntry(3, b - delta);
                v1 = f.value(point).getFirst();
                point.setEntry(3, b + delta);
                v2 = f.value(point).getFirst();
                final double[] dfdd = v2.subtract(v1).mapDivide(2 * delta).toArray();
                point.setEntry(3, b);

                point.setEntry(4, tau - delta);
                v1 = f.value(point).getFirst();
                point.setEntry(4, tau + delta);
                v2 = f.value(point).getFirst();
                final double[] dfde = v2.subtract(v1).mapDivide(2 * delta).toArray();
                point.setEntry(4, tau);

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
  void canComputeDiffusionLimitedRecoveryFunction() {
    final double delta = 0x1.0p-30;
    final DoubleDoubleBiPredicate test = Predicates.doublesAreRelativelyClose(1e-3);
    RealVector v1;
    RealVector v2;
    for (final int size : new int[] {10, 50}) {
      final FrapFunction f = new DiffusionLimitedRecoveryFunction(size, 1);
      for (final double i0 : new double[] {20, 30}) {
        for (final double a : new double[] {5, 10}) {
          for (final double tD : new double[] {0.5 / size, 1.0 / size, 2.0 / size}) {
            // Check the value and Jacobian
            final RealVector point = new ArrayRealVector(new double[] {i0, a, tD}, false);
            final Pair<RealVector, RealMatrix> p = f.value(point);
            final double[] value = p.getFirst().toArray();
            Assertions.assertEquals(size, value.length);
            TestAssertions.assertTest(i0, value[0], test, "value");
            for (int t = 1; t < size; t++) {
              // f(t) = i0 + A(exp(-2tD/t) * (I0(2tD/t) + I1(2tD/t)))
              final double x = 2 * tD / t;
              final double ft = i0 + a * (Math.exp(-x) * (Bessel.i0(x) + Bessel.i1(x)));
              TestAssertions.assertTest(ft, value[t], test, "value");
            }
            Assertions.assertArrayEquals(value, f.values(point));

            // Columns of the Jacobian
            final double[] dfda1 = p.getSecond().getColumn(0);
            final double[] dfdb1 = p.getSecond().getColumn(1);
            final double[] dfdc1 = p.getSecond().getColumn(2);

            point.setEntry(0, i0 - delta);
            v1 = f.value(point).getFirst();
            point.setEntry(0, i0 + delta);
            v2 = f.value(point).getFirst();
            final double[] dfda = v2.subtract(v1).mapDivide(2 * delta).toArray();
            point.setEntry(0, i0);

            point.setEntry(1, a - delta);
            v1 = f.value(point).getFirst();
            point.setEntry(1, a + delta);
            v2 = f.value(point).getFirst();
            final double[] dfdb = v2.subtract(v1).mapDivide(2 * delta).toArray();
            point.setEntry(1, a);

            point.setEntry(2, tD - delta);
            v1 = f.value(point).getFirst();
            point.setEntry(2, tD + delta);
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
  void canComputeDiffusionLimitedRecoveryFunctionB() {
    final double delta = 0x1.0p-30;
    final DoubleDoubleBiPredicate test = Predicates.doublesAreRelativelyClose(1e-3);
    RealVector v1;
    RealVector v2;
    for (final int size : new int[] {10, 50}) {
      final FrapFunction f = new DiffusionLimitedRecoveryFunctionB(size, 1);
      for (final double i0 : new double[] {20, 30}) {
        for (final double a : new double[] {5, 10}) {
          for (final double tD : new double[] {0.5 / size, 1.0 / size, 2.0 / size}) {
            for (final double b : new double[] {1, 2}) {
              for (final double tau : new double[] {0.5 / size, 1.0 / size, 2.0 / size}) {
                // Check the value and Jacobian
                final RealVector point =
                    new ArrayRealVector(new double[] {i0, a, tD, b, tau}, false);
                final Pair<RealVector, RealMatrix> p = f.value(point);
                final double[] value = p.getFirst().toArray();
                Assertions.assertEquals(size, value.length);
                TestAssertions.assertTest(b + i0, value[0], test, "value");
                for (int t = 1; t < size; t++) {
                  // f(t) = B + (i0 + A(exp(-2tD/t) * (I0(2tD/t) + I1(2tD/t)))) * exp(-tau * t)
                  final double x = 2 * tD / t;
                  final double ft = b + (i0 + a * (Math.exp(-x) * (Bessel.i0(x) + Bessel.i1(x))))
                      * Math.exp(-tau * t);
                  TestAssertions.assertTest(ft, value[t], test, "value");
                }
                Assertions.assertArrayEquals(value, f.values(point));

                // Columns of the Jacobian
                final double[] dfda1 = p.getSecond().getColumn(0);
                final double[] dfdb1 = p.getSecond().getColumn(1);
                final double[] dfdc1 = p.getSecond().getColumn(2);
                final double[] dfdd1 = p.getSecond().getColumn(3);
                final double[] dfde1 = p.getSecond().getColumn(4);

                point.setEntry(0, i0 - delta);
                v1 = f.value(point).getFirst();
                point.setEntry(0, i0 + delta);
                v2 = f.value(point).getFirst();
                final double[] dfda = v2.subtract(v1).mapDivide(2 * delta).toArray();
                point.setEntry(0, i0);

                point.setEntry(1, a - delta);
                v1 = f.value(point).getFirst();
                point.setEntry(1, a + delta);
                v2 = f.value(point).getFirst();
                final double[] dfdb = v2.subtract(v1).mapDivide(2 * delta).toArray();
                point.setEntry(1, a);

                point.setEntry(2, tD - delta);
                v1 = f.value(point).getFirst();
                point.setEntry(2, tD + delta);
                v2 = f.value(point).getFirst();
                final double[] dfdc = v2.subtract(v1).mapDivide(2 * delta).toArray();
                point.setEntry(2, tD);

                point.setEntry(3, b - delta);
                v1 = f.value(point).getFirst();
                point.setEntry(3, b + delta);
                v2 = f.value(point).getFirst();
                final double[] dfdd = v2.subtract(v1).mapDivide(2 * delta).toArray();
                point.setEntry(3, b);

                point.setEntry(4, tau - delta);
                v1 = f.value(point).getFirst();
                point.setEntry(4, tau + delta);
                v2 = f.value(point).getFirst();
                final double[] dfde = v2.subtract(v1).mapDivide(2 * delta).toArray();
                point.setEntry(4, tau);

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
  void canExtractBackground() {
    // A stack with a dark block that drifts with the frames.
    // The alignment offsets differ in X and Y.
    final int n = 2;
    final int size = 2 * n + 1;
    final double[] dx = {2, 3, 1};
    final double[] dy = {0, 1, -1};
    // Top-left of the block in the neutral (aligned) frame
    final int x = 15;
    final int y = 20;
    final ImageStack stack = new ImageStack(40, 40);
    for (int i = 0; i < dx.length; i++) {
      final FloatProcessor fp = new FloatProcessor(40, 40);
      fp.add(100);
      // The block values vary across the block. The mean is 3.
      final int x2 = x - (int) dx[i];
      final int y2 = y - (int) dy[i];
      for (int yy = 0; yy < size; yy++) {
        for (int xx = 0; xx < size; xx++) {
          fp.setf(x2 + xx, y2 + yy, 1 + xx);
        }
      }
      stack.addSlice(fp);
    }
    final ImagePlus imp = new ImagePlus("test", stack);
    final float[] background =
        FrapAnalysis_PlugIn.extractBackground(imp, n, new double[][] {dx, dy});
    Assertions.assertArrayEquals(new float[] {3, 3, 3}, background, 1e-6f);
  }

  @Test
  void canFitBleachingWithFrameInterval() {
    // f(t) = y0 + B exp(-koff * t) with time t = frame * interval
    final double y0 = 10;
    final double b = 50;
    final double koff = 0.5;
    final double interval = 0.1;
    final float[] y = new float[100];
    for (int i = 0; i < y.length; i++) {
      y[i] = (float) (y0 + b * Math.exp(-koff * i * interval));
    }
    final double[] fit = FrapAnalysis_PlugIn.fitBleaching(y, interval);
    Assertions.assertNotNull(fit);
    Assertions.assertEquals(y0, fit[0], 1e-3);
    Assertions.assertEquals(b, fit[1], 1e-3);
    Assertions.assertEquals(koff, fit[2], 1e-4);
  }
}
