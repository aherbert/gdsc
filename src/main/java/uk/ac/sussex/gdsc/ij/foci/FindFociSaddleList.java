/*-
 * #%L
 * Genome Damage and Stability Centre ImageJ Plugins
 *
 * Software for microscopy image analysis
 * %%
 * Copyright (C) 2011 - 2022 Alex Herbert
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

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import java.io.Serializable;
import java.util.Arrays;
import java.util.Comparator;

/**
 * Contains the foci saddle results of the FindFoci algorithm.
 *
 * <p>This list capacity must be explicitly resized.
 */
public class FindFociSaddleList {
  private static class IdSaddleComparator implements Comparator<FindFociSaddle>, Serializable {
    private static final long serialVersionUID = 1L;
    static final IdSaddleComparator INSTANCE = new IdSaddleComparator();

    @Override
    public int compare(FindFociSaddle o1, FindFociSaddle o2) {
      if (o1.id < o2.id) {
        return -1;
      }
      if (o1.id > o2.id) {
        return 1;
      }
      return Integer.compare(o1.order, o2.order);
    }
  }

  private static class OrderSaddleComparator implements Comparator<FindFociSaddle>, Serializable {
    private static final long serialVersionUID = 1L;
    static final OrderSaddleComparator INSTANCE = new OrderSaddleComparator();

    @Override
    public int compare(FindFociSaddle o1, FindFociSaddle o2) {
      return Integer.compare(o1.order, o2.order);
    }
  }

  /** The size. */
  int size;

  /** The list of saddles. */
  FindFociSaddle[] list;

  /**
   * Instantiates a new find foci saddle list.
   */
  public FindFociSaddleList() {
    this(0);
  }

  /**
   * Instantiates a new find foci saddle list.
   *
   * @param capacity the capacity
   */
  public FindFociSaddleList(int capacity) {
    this.list = new FindFociSaddle[capacity];
  }

  /**
   * Copy constructor.
   *
   * @param source the source
   */
  private FindFociSaddleList(FindFociSaddleList source) {
    size = source.size;
    if (source.list != null) {
      // Make same length so that add operations behave exactly the same
      list = new FindFociSaddle[source.list.length];
      // Only copy up to the correct size
      for (int i = 0; i < size; i++) {
        list[i] = source.list[i].copy();
      }
    }
  }

  /**
   * get the size of the list.
   *
   * @return The size
   */
  public int getSize() {
    return size;
  }

  /**
   * Get the saddle for the index.
   *
   * @param index The index
   * @return The saddle
   */
  public FindFociSaddle get(final int index) {
    return list[index];
  }

  /**
   * Add a new saddle. Does not check there is capacity to do this. Call
   * {@link #ensureExtraCapacity(int)} first.
   *
   * @param saddle The saddle
   */
  public void add(FindFociSaddle saddle) {
    list[size++] = saddle;
  }

  /**
   * Clear the list but do not reduce the capacity.
   */
  public void clear() {
    clear(0);
  }

  /**
   * Clear the list from the given position but do not reduce the capacity.
   *
   * @param position The position
   */
  public void clear(int position) {
    if (position == size) {
      return;
    }
    final int oldSize = size;
    size = position;
    while (position < oldSize) {
      list[position++] = null;
    }
  }

  /**
   * Free memory (Set size to zero and the list to null).
   */
  void free() {
    size = 0;
    list = null;
  }

  /**
   * Clear the list and set capacity to zero. This should be called to allow the garbage collector
   * to work on freeing memory.
   */
  public void erase() {
    clear();
    list = null;
  }

  /**
   * Ensure that n extra elements can be added to the list.
   *
   * <p>Note this is different from the ensureCapacity() method of ArrayList which checks total
   * capacity.
   *
   * @param elements The number of extra elements (this should not be negative)
   */
  public void ensureExtraCapacity(int elements) {
    if (list.length - size >= elements) {
      return;
    }
    // Increase size
    list = Arrays.copyOf(list, size + elements);
  }

  /**
   * Get the total capacity of the list.
   *
   * @return The total capacity
   */
  public int getCapacity() {
    return list.length;
  }

  /**
   * Returns a copy of this saddle list.
   *
   * @return the copy
   */
  public FindFociSaddleList copy() {
    return new FindFociSaddleList(this);
  }

  /**
   * Sort the list using the comparator.
   *
   * @param saddleComparator the saddle comparator
   */
  public void sort(Comparator<FindFociSaddle> saddleComparator) {
    if (size < 2) {
      return;
    }
    Arrays.sort(list, 0, size, saddleComparator);
  }

  /**
   * Remove duplicate Ids from the list and maintain the current order, or else do a default sort
   * using {@link FindFociSaddle#compare(FindFociSaddle, FindFociSaddle)}.
   *
   * @param maintain Maintain the current order, otherwise do default sort
   */
  public void removeDuplicates(boolean maintain) {
    if (size < 2) {
      return;
    }
    // Store the current order
    for (int i = 0; i < size; i++) {
      list[i].order = i;
    }
    // Sort by ID and remove duplicates
    sort(IdSaddleComparator.INSTANCE);
    int lastId = 0;
    int newSize = 0;
    for (int i = 0; i < size; i++) {
      if (lastId != list[i].id) {
        list[newSize++] = list[i];
        lastId = list[i].id;
      }
    }
    clear(newSize);
    // Rstore the desired order
    if (maintain) {
      sort(OrderSaddleComparator.INSTANCE);
    } else {
      sort(FindFociSaddle::compare);
    }
  }

  /**
   * Remove duplicate Ids from the list and do a default sort using
   * {@link FindFociSaddle#compare(FindFociSaddle, FindFociSaddle)}.
   */
  public void removeDuplicates() {
    if (size < 2) {
      return;
    }
    final IntOpenHashSet set = new IntOpenHashSet(size);
    int newSize = 0;
    for (int i = 0; i < size; i++) {
      if (set.add(list[i].id)) {
        list[newSize++] = list[i];
      }
    }
    clear(newSize);
    sort(FindFociSaddle::compare);
  }

  /**
   * Sort the list by Id but otherwise maintain the current order.
   */
  public void sortById() {
    if (size < 2) {
      return;
    }
    for (int i = 0; i < size; i++) {
      list[i].order = i;
    }
    sort(IdSaddleComparator.INSTANCE);
  }
}
