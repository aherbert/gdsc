package gdsc.colocalisation.cda.engine;

/**
 * Used to store the results of comparing the intersection of two images.
 */
public class IntersectResult
{
	public long sum1;
	public long sum2;
	public double r;

	public IntersectResult(long sum1, long sum2, double r)
	{
		this.sum1 = sum1;
		this.sum2 = sum2;
		this.r = r;
	}
}