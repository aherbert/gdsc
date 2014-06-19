package gdsc.foci;

class Assignment implements Comparable<Assignment>
{
	private int targetId;
	private int predictedId;
	private double distance;

	public Assignment(int targetId, int predictedId, double distance)
	{
		this.targetId = targetId;
		this.predictedId = predictedId;
		this.distance = distance;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Comparable#compareTo(java.lang.Object)
	 */
	public int compareTo(Assignment o)
	{
		if (this.distance < o.distance)
			return -1;
		if (this.distance > o.distance)
			return 1;
		return 0;
	}

	/**
	 * @return the targetId
	 */
	public int getTargetId()
	{
		return targetId;
	}

	/**
	 * @return the predictedId
	 */
	public int getPredictedId()
	{
		return predictedId;
	}

	/**
	 * @return the distance
	 */
	public double getDistance()
	{
		return distance;
	}
}