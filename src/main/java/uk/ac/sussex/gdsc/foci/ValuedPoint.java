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
package uk.ac.sussex.gdsc.foci;

/**
 * Stores a 2D/3D point with a value.
 */
public class ValuedPoint extends BasePoint
{
    private float value = 0;

    /**
     * Instantiates a new valued point.
     *
     * @param x
     *            the x
     * @param y
     *            the y
     * @param z
     *            the z
     * @param value
     *            the value
     */
    public ValuedPoint(int x, int y, int z, float value)
    {
        super(x, y, z);
        this.value = value;
    }

    /**
     * Instantiates a new valued point.
     *
     * @param point
     *            the point
     * @param value
     *            the value
     */
    public ValuedPoint(AssignedPoint point, float value)
    {
        super(point.getXint(), point.getYint(), point.getZint());
        this.value = value;
    }

    /**
     * Gets the value.
     *
     * @return the value
     */
    public float getValue()
    {
        return value;
    }
}