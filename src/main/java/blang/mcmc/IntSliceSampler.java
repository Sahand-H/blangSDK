package blang.mcmc;

import java.util.List;
import bayonet.distributions.Random;

import blang.core.LogScaleFactor;
import blang.core.WritableIntVar;
import blang.distributions.Generators;


public class IntSliceSampler implements Sampler
{
  @SampledVariable
  protected WritableIntVar variable;
  
  @ConnectedFactor
  protected List<LogScaleFactor> numericFactors;
  
  private final Integer fixedWindowLeft, fixedWindowRight;
  
  public boolean useFixedWindow()
  {
    return fixedWindowLeft != null;
  }
  
  private static final int initialWindowSize = 10;
  private static final int maxNDoublingRounds = 10;
  
  private IntSliceSampler(Integer fixedWindowLeft, Integer fixedWindowRight) 
  {
    this.fixedWindowLeft = fixedWindowLeft;
    this.fixedWindowRight = fixedWindowRight;
  }
  
  private IntSliceSampler()
  {
    this(null, null);
  }
  
  public static IntSliceSampler build(WritableIntVar variable, List<LogScaleFactor> numericFactors)
  {
    return build(variable, numericFactors, null, null);
  }
  
  public static IntSliceSampler build(WritableIntVar variable, List<LogScaleFactor> numericFactors, Integer fixedWinLeft, Integer fixedWinRight)
  {
    IntSliceSampler result = new IntSliceSampler(fixedWinLeft, fixedWinRight);
    result.variable = variable;
    result.numericFactors = numericFactors;
    return result;
  }
  
  public void execute(Random random)
  {
    // sample slice
    final double logSliceHeight = RealSliceSampler.nextLogSliceHeight(random, logDensity());  // log(Y) in Neal's paper
    final int oldState = variable.intValue();        // x0 in Neal's paper
   
    int leftProposalEndPoint, rightProposalEndPoint;
    
    if (useFixedWindow())
    {
      leftProposalEndPoint = fixedWindowLeft;
      rightProposalEndPoint = fixedWindowRight;
    }
    else
    {
      // doubling procedure
      leftProposalEndPoint = oldState - random.nextInt(initialWindowSize); // L in Neal's paper
      rightProposalEndPoint = leftProposalEndPoint + initialWindowSize;    // R in Neal's paper
      
      // convention: left is inclusive, right is exclusive
      
      int iter = 0;
      while (iter++ < maxNDoublingRounds && 
          (logSliceHeight < logDensityAt(leftProposalEndPoint) || logSliceHeight < logDensityAt(rightProposalEndPoint - 1))) 
        if (random.nextBernoulli(0.5))
        {
          leftProposalEndPoint += - (rightProposalEndPoint - leftProposalEndPoint);
          // note 1: check we don't diverge to INF 
          // this as that can arise e.g. when encountering an improper posterior
          // avoid infinite loop then and warn user.
          if (leftProposalEndPoint == Double.NEGATIVE_INFINITY)
            throw new RuntimeException(RealSliceSampler.INFINITE_SLICE_MESSAGE);
        }
        else
        {
          rightProposalEndPoint += rightProposalEndPoint - leftProposalEndPoint;
          // same as note 1 above
          if (rightProposalEndPoint == Double.POSITIVE_INFINITY)
            throw new RuntimeException(RealSliceSampler.INFINITE_SLICE_MESSAGE);
        }
    }
    
    // shrinkage procedure
    int 
      leftShrankEndPoint = leftProposalEndPoint,   // bar L in Neal's paper
      rightShrankEndPoint = rightProposalEndPoint; // bar R in Neal's paper
    while (true) 
    {
      final int newState = Generators.discreteUniform(random, leftShrankEndPoint, rightShrankEndPoint); // x1 in Neal's paper
      if (logSliceHeight <= logDensityAt(newState) && accept(oldState, newState, logSliceHeight, leftProposalEndPoint, rightProposalEndPoint))
      {
        variable.set(newState);
        return;
      }
      if (newState < oldState)
        leftShrankEndPoint = newState + 1;
      else
        rightShrankEndPoint = newState;
    }
  }
  
  private boolean accept(int oldState, int newState, double logSliceHeight, int leftProposalEndPoint, int rightProposalEndPoint) 
  {
    boolean differ = false; // D in Neal's paper; whether the intervals generated by new and old differ; used for optimization
    while (rightProposalEndPoint - leftProposalEndPoint > 1) // 1.1 factor to cover for numerical round offs
    {
      final int middle = (leftProposalEndPoint + rightProposalEndPoint) / 2; // M in Neal's paper
      if ((oldState <  middle && newState >= middle) || 
          (oldState >= middle && newState < middle))
        differ = true;
      if (newState < middle)
        rightProposalEndPoint = middle;
      else
        leftProposalEndPoint = middle;
      if (differ && logSliceHeight >= logDensityAt(leftProposalEndPoint) && logSliceHeight >= logDensityAt(rightProposalEndPoint - 1))
        return false;
    }
    return true;
  }
  
  private double logDensityAt(int x)
  {
    variable.set(x);
    return logDensity();
  }
  
  private double logDensity() {
    double sum = 0.0;
    for (LogScaleFactor f : numericFactors)
      sum += f.logDensity();
    return sum;
  }
}