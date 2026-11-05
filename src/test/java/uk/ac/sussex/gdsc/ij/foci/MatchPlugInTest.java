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

package uk.ac.sussex.gdsc.ij.foci;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

@SuppressWarnings({"javadoc"})
class MatchPlugInTest {
  private static void assertTitles(String expected1, String expected2, List<String> items,
      String title1, String title2) {
    Assertions.assertArrayEquals(new String[] {expected1, expected2},
        Match_PlugIn.selectTitles(items, title1, title2));
  }

  @Test
  void testSelectTitles() {
    final List<String> items = Arrays.asList("a", "b", "c");
    // Previous titles are used
    assertTitles("c", "b", items, "c", "b");
    assertTitles("c", "c", items, "c", "c");
    // No previous titles
    assertTitles("a", "b", items, "x", "y");
    assertTitles("a", "b", items, null, null);
    // One previous title. The other is different.
    assertTitles("a", "b", items, "a", "y");
    assertTitles("b", "a", items, "x", "a");
    assertTitles("c", "a", items, "c", "y");
    assertTitles("a", "c", items, "x", "c");
  }
}
