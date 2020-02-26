/*-
 * #%L
 * Genome Damage and Stability Centre ImageJ Plugins
 *
 * Software for microscopy image analysis
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

package uk.ac.sussex.gdsc.foci.model;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;

/**
 * Abstract model that provides methods for property change notification.
 */
public abstract class AbstractModelObject {
  private final PropertyChangeSupport propertyChangeSupport = new PropertyChangeSupport(this);

  /**
   * Adds the property change listener.
   *
   * @param listener the listener
   */
  public void addPropertyChangeListener(PropertyChangeListener listener) {
    propertyChangeSupport.addPropertyChangeListener(listener);
  }

  /**
   * Adds the property change listener.
   *
   * @param propertyName the property name
   * @param listener the listener
   */
  public void addPropertyChangeListener(String propertyName, PropertyChangeListener listener) {
    propertyChangeSupport.addPropertyChangeListener(propertyName, listener);
  }

  /**
   * Removes the property change listener.
   *
   * @param listener the listener
   */
  public void removePropertyChangeListener(PropertyChangeListener listener) {
    propertyChangeSupport.removePropertyChangeListener(listener);
  }

  /**
   * Removes the property change listener.
   *
   * @param propertyName the property name
   * @param listener the listener
   */
  public void removePropertyChangeListener(String propertyName, PropertyChangeListener listener) {
    propertyChangeSupport.removePropertyChangeListener(propertyName, listener);
  }

  /**
   * Fire property change.
   *
   * @param propertyName the property name
   * @param oldValue the old value
   * @param newValue the new value
   */
  protected void firePropertyChange(String propertyName, Object oldValue, Object newValue) {
    propertyChangeSupport.firePropertyChange(propertyName, oldValue, newValue);
  }

  /**
   * Fire property change.
   *
   * @param propertyName the property name
   * @param oldValue the old value
   * @param newValue the new value
   */
  protected void firePropertyChange(String propertyName, int oldValue, int newValue) {
    propertyChangeSupport.firePropertyChange(propertyName, oldValue, newValue);
  }

  /**
   * Fire property change.
   *
   * @param propertyName the property name
   * @param oldValue the old value
   * @param newValue the new value
   */
  protected void firePropertyChange(String propertyName, boolean oldValue, boolean newValue) {
    propertyChangeSupport.firePropertyChange(propertyName, oldValue, newValue);
  }

  /**
   * Fire property change.
   *
   * @param evt the evt
   */
  protected void firePropertyChange(PropertyChangeEvent evt) {
    propertyChangeSupport.firePropertyChange(evt);
  }

  /**
   * Fire indexed property change.
   *
   * @param propertyName the property name
   * @param index the index
   * @param oldValue the old value
   * @param newValue the new value
   */
  protected void fireIndexedPropertyChange(String propertyName, int index, Object oldValue,
      Object newValue) {
    propertyChangeSupport.fireIndexedPropertyChange(propertyName, index, oldValue, newValue);
  }

  /**
   * Fire indexed property change.
   *
   * @param propertyName the property name
   * @param index the index
   * @param oldValue the old value
   * @param newValue the new value
   */
  protected void fireIndexedPropertyChange(String propertyName, int index, int oldValue,
      int newValue) {
    propertyChangeSupport.fireIndexedPropertyChange(propertyName, index, oldValue, newValue);
  }

  /**
   * Fire indexed property change.
   *
   * @param propertyName the property name
   * @param index the index
   * @param oldValue the old value
   * @param newValue the new value
   */
  protected void fireIndexedPropertyChange(String propertyName, int index, boolean oldValue,
      boolean newValue) {
    propertyChangeSupport.fireIndexedPropertyChange(propertyName, index, oldValue, newValue);
  }
}
