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