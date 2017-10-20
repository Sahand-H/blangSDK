package blang.mcmc;

import bayonet.distributions.Random;




public interface Sampler 
{
  /*
   * Todo: need facilities for
   *  - command line options
   *  - logging 
   *      - summary stats such as acceptance rate
   *      - samples
   *  - adaptation
   *  - bailing out
   *  - annealing
   *      - remove -Inf in support
   *      - exponents for AIS/parallel tempering/simulated annealing
   *  - return an order of magnitude of the number of FLOPS required
   */
  
  public void execute(Random rand);
  
  /**
   * @return If this sampler is actually compatible.
   */
  public boolean setup();
}
