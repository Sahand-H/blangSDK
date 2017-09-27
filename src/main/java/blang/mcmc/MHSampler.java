package blang.mcmc;

import java.util.List;
import java.util.Random;

import blang.core.LogScaleFactor;
import blang.mcmc.internals.Callback;



public abstract class MHSampler<T> implements Sampler
{
  @SampledVariable
  protected T variable;
  
  @ConnectedFactor
  protected List<LogScaleFactor> numericFactors;
  
  public boolean setup() 
  {
    return true;
  }
  
  public void execute(Random random)
  {
    // record likelihood before
    final double logBefore = logDensity();
    Callback callback = new Callback()
    {
      private Double proposalLogRatio = null;
      @Override
      public void setProposalLogRatio(double logRatio)
      {
        this.proposalLogRatio = logRatio;
      }
      @Override
      public boolean sampleAcceptance()
      {
        if (proposalLogRatio == null)
          throw new RuntimeException("Use setProposalLogRatio(..) before calling sampleAcceptance()");
        final double logAfter = logDensity();
        final double ratio = Math.exp(proposalLogRatio + logAfter - logBefore);
        return random.nextDouble() < ratio;
      }
    };
    propose(random, callback);
  }
  
  private double logDensity() {
    double sum = 0.0;
    for (LogScaleFactor f : numericFactors)
      sum += f.logDensity();
    return sum;
  }

  public abstract void propose(Random random, Callback callback);

}