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

import uk.ac.sussex.gdsc.core.match.Coordinate;

/**
 * Stores a 2D/3D point.
 * <p>
 * Overrides equals and hashCode methods using x,y,z, coordinates for equivalence. Derived classes can optionally
 * override this.
 *
 * @see java.lang.Object#equals(java.lang.Object)
 * @see java.lang.Object#hashCode()
 */
public class BasePoint implements Coordinate
{
    /** The x. */
    protected int x = 0;

    /** The y. */
    protected int y = 0;

    /** The z. */
    protected int z = 0;

    /**
     * Instantiates a new base point.
     *
     * @param x
     *            the x
     * @param y
     *            the y
     * @param z
     *            the z
     */
    public BasePoint(int x, int y, int z)
    {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    /**
     * Instantiates a new base point.
     *
     * @param x
     *            the x
     * @param y
     *            the y
     */
    public BasePoint(int x, int y)
    {
        this.x = x;
        this.y = y;
    }

    /** {@inheritDoc} */
    @Override
    public boolean equals(Object aThat)
    {
        if (this == aThat)
            return true;
        if (!(aThat instanceof BasePoint))
            return false;

        //cast to native object is now safe
        final BasePoint that = (BasePoint) aThat;

        return x == that.x && y == that.y && z == that.z;
    }

    /** {@inheritDoc} */
    @Override
    public int hashCode()
    {
        return (41 * (41 * (41 + x) + y) + z);
    }

    /** {@inheritDoc} */
    @Override
    public float getX()
    {
        return x;
    }

    /** {@inheritDoc} */
    @Override
    public float getY()
    {
        return y;
    }

    /** {@inheritDoc} */
    @Override
    public float getZ()
    {
        return z;
    }

    /** {@inheritDoc} */
    @Override
    public int getXint()
    {
        return x;
    }

    /** {@inheritDoc} */
    @Override
    public int getYint()
    {
        return y;
    }

    /** {@inheritDoc} */
    @Override
    public int getZint()
    {
        return z;
    }

    /** {@inheritDoc} */
    @Override
    public double distance(float x, float y, float z)
    {
        return Math.sqrt(distance2(x, y, z));
    }

    /** {@inheritDoc} */
    @Override
    public double distance(float x, float y)
    {
        return Math.sqrt(distance2(x, y));
    }

    /** {@inheritDoc} */
    @Override
    public double distance2(float x, float y, float z)
    {
        return (this.x - x) * (this.x - x) + (this.y - y) * (this.y - y) + (this.z - z) * (this.z - z);
    }

    /** {@inheritDoc} */
    @Override
    public double distance2(float x, float y)
    {
        return (this.x - x) * (this.x - x) + (this.y - y) * (this.y - y);
    }

    /** {@inheritDoc} */
    @Override
    public double distanceXY(Coordinate other)
    {
        return distance(other.getX(), other.getY());
    }

    /** {@inheritDoc} */
    @Override
    public double distanceXY2(Coordinate other)
    {
        return distance2(other.getX(), other.getY());
    }

    /** {@inheritDoc} */
    @Override
    public double distanceXYZ(Coordinate other)
    {
        return distance(other.getX(), other.getY(), other.getZ());
    }

    /** {@inheritDoc} */
    @Override
    public double distanceXYZ2(Coordinate other)
    {
        return distance2(other.getX(), other.getY(), other.getZ());
    }
}
