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
package gdsc.colocalisation.cda.engine;

/**
 * Used to store the calculation results of the intersection of two images.
 */
public class CalculationResult
{
	public double distance; // Shift distance
	public double m1; // Mander's 1
	public double m2; // Mander's 2
	public double r; // Correlation

	public CalculationResult(double distance, double m1, double m2, double r)
	{
		this.distance = distance;
		this.m1 = m1;
		this.m2 = m2;
		this.r = r;
	}
}
