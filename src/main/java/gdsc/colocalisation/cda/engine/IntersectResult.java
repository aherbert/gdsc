package gdsc.colocalisation.cda.engine;

/**
 * Used to store the results of comparing the intersection of two images.
 */
public class IntersectResult
{
	public int sum1;
	public int sum2;
	public double r;

	public IntersectResult(int sum1, int sum2, double r)
	{
		this.sum1 = sum1;
		this.sum2 = sum2;
		this.r = r;
	}
}