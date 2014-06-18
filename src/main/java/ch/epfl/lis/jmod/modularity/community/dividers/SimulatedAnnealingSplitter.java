package ch.epfl.lis.jmod.modularity.community.dividers;

import no.uib.cipr.matrix.DenseVector;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.OptionBuilder;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.PosixParser;
import org.apache.commons.cli.UnrecognizedOptionException;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.math3.random.MersenneTwister;

import com.esotericsoftware.minlog.Log;

import ch.epfl.lis.ga.binary.Chromosome;
import ch.epfl.lis.ga.binary.Population;
import ch.epfl.lis.jmod.JmodException;
import ch.epfl.lis.jmod.modularity.ModularityDetector;

/**
 * Uses a simulated annealing (SA) algorithm to split communities in two.<p>
 * 
 * Source: <a href="http://www.theprojectspot.com/tutorial-post/simulated-annealing-algorithm-for-beginners/6" target="blank_">
 * Simulated Annealing for beginners</a>
 * 
 * @version June 2, 2014
 * 
 * @author Thomas Schaffter (firstname.name@gmail.com)
 */
public class SimulatedAnnealingSplitter extends CommunityDivider {
	
	/** Value of the parameter --numproc to indicate to use the maximum number of processors available. */
	public static final String USE_NUM_PROC_AVAILABLE = "MAX";
	
	/** Initial temperature. */
	protected double t0_ = 10;
	/** Target or final temperature. */
	protected double tf_ = 0.01;
	/** Cooling rate. */
	protected double coolingRate_ = 0.01;
	
	/** Number of concurrent workers in the pool which should not exceed the number of processors. */
	protected int numProc_ = Runtime.getRuntime().availableProcessors();
	
	/** Use brute force approach when GA num evals > 2^{N-1}. */
	protected boolean bf_ = false;
	
	/** Reference to the modularity detector. */
	protected ModularityDetector modDetector_ = null;
	
	/** Index of the current run. */
	protected int currentRunIndex_ = 0;
	/** Number of runs per community split (default: 1). */
	protected int numRuns_ = 1;
	
	/** Displays an update every X generations. */
	protected int logsInterval_ = 100;
	/** Next generation where logs will be displayed. */
	protected int nextLog_ = logsInterval_;
	
	// temp variables
	private int networkSize_ = 0;
	
    // =======================================================================================
    // PROTECTED METHODS
	
	@SuppressWarnings("static-access")
	@Override
	protected void buildOptions() {
		
		// create Options object
		options_ = new Options();
		
		// SHORT AND LONG OPTIONS FLAGS MUST BE DIFFERENT FROM THOSE OF JMOD
		options_.addOption(OptionBuilder.withValueSeparator()
				.withLongOpt("t0")
				.withDescription("Initial temperature (default: " + t0_ + ").")
				.hasArgs(1)
				.withArgName("VALUE")
				.create());
		
		options_.addOption(OptionBuilder.withValueSeparator()
				.withLongOpt("tf")
				.withDescription("Target/final temperature (default: " + tf_ + ").")
				.hasArgs(1)
				.withArgName("VALUE")
				.create());
		
		options_.addOption(OptionBuilder.withValueSeparator()
				.withLongOpt("coolingRate")
				.withDescription("Cooling rate (default: " + coolingRate_ + ").")
				.hasArgs(1)
				.withArgName("RATE")
				.create());
		
		options_.addOption(OptionBuilder.withValueSeparator()
				.withLongOpt("numRuns")
				.withDescription("Number of SA runs per community split (default: " + numRuns_ + ").")
				.hasArgs(1)
				.withArgName("NUM")
				.create("r"));
		
		options_.addOption(OptionBuilder.withValueSeparator()
				.withLongOpt("bf")
				.withDescription("Use brute force approach when pop size * max num generations >= 2^{n-1} (default: disabled).")
				.create());
		
		options_.addOption(OptionBuilder.withValueSeparator()
				.withLongOpt("numproc")
				.withDescription("Use the given number of processors (used by BF method only; max: " + Runtime.getRuntime().availableProcessors() + ", specify MAX to use all the processors available, default: MAX).")
				.hasArgs()
				.withArgName("NUM")
				.create());
	}
	
	// ----------------------------------------------------------------------------
	
	@Override
	protected void parseOptions(String args[]) throws JmodException, Exception {
		
		// parse options
		CommandLineParser parser = new PosixParser();
		CommandLine cmd = null;
		try {
			cmd = parser.parse(options_, args);
			if (cmd.hasOption("t0"))
				t0_ = Double.parseDouble(cmd.getOptionValue("t0"));
			if (cmd.hasOption("tf"))
				tf_ = Double.parseDouble(cmd.getOptionValue("tf"));
			if (cmd.hasOption("coolingRate"))
				coolingRate_ = Double.parseDouble(cmd.getOptionValue("coolingRate"));
			if (cmd.hasOption("numRuns"))
				numRuns_ = Integer.parseInt(cmd.getOptionValue("numRuns"));
			bf_ = cmd.hasOption("bf");
			
			if (cmd.hasOption("numproc")) {
				String valueStr = cmd.getOptionValue("numproc");
				int value = 0;
				int numProcAvail = Runtime.getRuntime().availableProcessors();
				
				if (valueStr.compareTo(USE_NUM_PROC_AVAILABLE) == 0)
					numProc_ = numProcAvail;
				else if ((value = Integer.parseInt(valueStr)) < 1) {
					Log.info(identifier_, "At least one processor is required. Using now one processor.");
					numProc_ = value;
				} else if ((value = Integer.parseInt(valueStr)) > numProcAvail) {
					Log.info(identifier_, "Number of processors available is " + numProcAvail + ". Using now all the processors available.");
					numProc_ = numProcAvail;
				} else
					numProc_ = value;
			}
		} catch (UnrecognizedOptionException e) {
			Log.error(identifier_, e.getMessage());
			printHelp();
			throw e;
		} catch (Exception e) {
			Log.error(identifier_, "Could not recognize all the options.", e);
			printHelp();
			throw e;
		}
	}
	
	// ----------------------------------------------------------------------------
	
	/** Calculates the acceptance probability. */
	protected double acceptanceProbability(double energy, double newEnergy, double temperature) {
		// If the new solution is better, accept it
		if (newEnergy > energy) {
//			Log.info("New best solution: Q=" + newEnergy);
			return 1.0;
		}
		// If the new solution is worse, calculate an acceptance probability
//		return Math.exp((energy - newEnergy) / temperature);
		
		double p = Math.exp(-(energy - newEnergy)*networkSize_ / temperature);
//		double p = Math.exp(-1/temperature);
//		Log.info("N: " + networkSize_ + ", delta: " + (energy - newEnergy) + ", p: " + p);
		return p;
    }
	
	// ----------------------------------------------------------------------------
	
	/** Set currentSubcommunityS_ from a Chromosome. */
	protected void setCurrentSubCommunityS(Chromosome chromosome) throws JmodException, Exception {
		
		if (chromosome == null)
			throw new JmodException("Chromosome is null.");
		if (modDetector_ == null)
			throw new JmodException("Modularity detector is null.");
		
		int[] intVector = chromosome.getAsIntegerArray(1); // one bit per gene
		for (int i = 0; i < modDetector_.currentSubcommunitySize_; i++) {
			if (intVector[i] == 0)
				modDetector_.currentSubcommunityS_.set(i, -1);
			else
				modDetector_.currentSubcommunityS_.set(i, 1);
		}
	}
	
    // =======================================================================================
    // PUBLIC METHODS
	
	/** Constructor. */
	public SimulatedAnnealingSplitter() {
		
		super();
		
		// builds the options of the methods
		buildOptions();
		
		identifier_ = "SA";
		name_ = "Simulated annealing (SA)";
		description_ = "<html>Instead of calculating the dominant eigenvector of the modularity<br>" +
					         "matrix <b>B</b> to define the split of a community in two<br>" +
					         "subcommunities, a simulated annealing algorithm is applied to find the<br>" +
					         "split vector <b>s</b> that maximizes the modularity Q.<br><br>" +
					         "Use the option --bf to run the brute force method when the number of<br>" +
					         "SA evaluations (popSize*numGenerations) is larger than 2^{n-1}<br>" +
					         "where n is the size of the current community to split.</html>";
	}
	
	// ----------------------------------------------------------------------------
	
	/** Copy constructor. */
	public SimulatedAnnealingSplitter(SimulatedAnnealingSplitter splitter) {
		
		super(splitter);

		t0_ = splitter.t0_;
		tf_ = splitter.tf_;
		coolingRate_ = splitter.coolingRate_;
		numRuns_ = splitter.numRuns_;
		bf_ = splitter.bf_;
		numProc_ = splitter.numProc_;
	}
	
	// ----------------------------------------------------------------------------
	
	@Override
	public CommunityDivider copy() {
		
		return new SimulatedAnnealingSplitter(this);
	}
	
	// ----------------------------------------------------------------------------

	@Override
	public void divide(ModularityDetector modDetector) throws JmodException, Exception {

		modDetector_ = modDetector;
		networkSize_ = modDetector.getNetwork().getSize();
		int N = modDetector.currentSubcommunitySize_;
		String name = FilenameUtils.getBaseName(modDetector.getNetwork().getName());
		
        double bestCurrentSubcommunityQ = 0.;
        DenseVector bestCurrentSubcommunityS = new DenseVector(N);
        
        // run brute force approach if numCombinations < popSize*numGenerations
        if (bf_ && N <= BruteForceDivider.BRUTEFORCE_MAX_COMMUNITY_SIZE) {
//        	int numCombinations = (int) Math.pow(2, N-1);
        	if (N <= 25) {
        		Log.debug(name + "|" + identifier_, "Running brute force method on small community.");
        		BruteForceDivider bf = new BruteForceDivider();
        		bf.setNumProc(numProc_);
        		bf.divide(modDetector);
        		return;
        	}
        }
        
        // required for calling Chromosome.randomize()
        Population.rng_ = new MersenneTwister();
		
		for (currentRunIndex_= 1; currentRunIndex_ <= numRuns_; currentRunIndex_++) {
			
			Log.debug(name + "|" + identifier_, "Running SA run " + currentRunIndex_ + " over " + numRuns_ + ".");
			Log.debug(name + "|" + identifier_, "Options: " + getOptionsStr());
			
			nextLog_ = logsInterval_;
			
	        double temp = t0_;
	        double coolingRate = coolingRate_;
	
	        // initial solution
	        Chromosome currentSolution = new Chromosome(N);
	        currentSolution.randomize();
	        double currentEnergy = evaluateFitness(currentSolution);
	        
	        // set as current best
	        Chromosome best = new Chromosome(currentSolution);
	        double bestEnergy = currentEnergy;
	      
	        // Loop until system has cooled
	        Chromosome newSolution = null;
	        int iter = 0;
	        while (temp > tf_) {
	        	// generate a new solution from current solution
	        	newSolution = new Chromosome(currentSolution);
	        	// mutate a random bit
	        	Chromosome.mutate(newSolution);
//	        	Chromosome.mutate3(newSolution, 2);
//	        	double mutationRate = 0.01; // *N
//	        	int numNodesToMutate = (int) (temp*(mutationRate*N - 1)/(t0_ - 1) + 1);
//	        	Chromosome.mutate2(newSolution, numNodesToMutate);
	        	
	        	// get energy of solution
	        	double neighbourEnergy = evaluateFitness(newSolution);
	        	
	        	// decide if we should accept the neighbour
	            if (acceptanceProbability(currentEnergy, neighbourEnergy, temp) > Math.random()) {
	            	currentSolution = new Chromosome(newSolution);
	            	currentEnergy = neighbourEnergy;
	            }
	
	            // keep track of the best solution found
	            if (currentEnergy > bestEnergy) {
	                best = new Chromosome(currentSolution);
	                bestEnergy = currentEnergy;
	            }
	            
				// Saves the current state of the module detection.
				// Don't forget to set modDetector_.currentSubcommunityS_ or the snapshot
				// will not see the change in differential mode.
				setCurrentSubCommunityS(best);
				modDetector_.takeSnapshot(identifier_);
				
				if (iter > nextLog_-1) {
					Log.debug(name + "|" + identifier_, "Best Q = " + evaluateFitness(best) + " (run " + currentRunIndex_ + ", iteration " + iter + ", temp " + temp + ")");
					nextLog_ += logsInterval_;
				}
	        	
	        	// cool system (annealing schedule)
	        	temp *= 1-coolingRate; // geometric cooling
	        	iter++;
	        }
	        
	        // set currentSubcommunityS_ with the best solution
	        setCurrentSubCommunityS(best);
	        modDetector.currentSubcommunityQ_ = modDetector.computeModularity();
	        
	        // gradient descent optimization (MVM)
	        modDetector.movingVertexMethod();
	        
	        // save the solution if it's the best found so far
	        if (modDetector.currentSubcommunityQ_ > bestCurrentSubcommunityQ) {
	        	bestCurrentSubcommunityQ = modDetector.currentSubcommunityQ_;
	        	bestCurrentSubcommunityS.set(modDetector.currentSubcommunityS_);
	        }
	        Log.debug(name + "|" + identifier_, "Q = " + modDetector.currentSubcommunityQ_ + " (best Q = " + bestCurrentSubcommunityQ + ")");
		}
		
		// DON'T FORGET TO SET modDetector.currentSubcommunityS_
		// assign the best solution
		modDetector.currentSubcommunityS_.set(bestCurrentSubcommunityS);
	}
	
	// ----------------------------------------------------------------------------
	
	/** Function to evaluate safely the modularity of the given individual. */
	protected double evaluateFitness(Chromosome chromosome) throws Exception {
		
		if (chromosome == null)
			throw new JmodException("Chromosome is null.");
		if (modDetector_ == null)
			throw new JmodException("Modularity detector is null.");
		
		// decode to obtain s
		DenseVector s = new DenseVector(chromosome.bitlen);
		for (int i = 0; i < chromosome.bitlen; i++)
			s.set(i, chromosome.get(i) == false ? -1 : 1);
		
		// compute modularity without affecting the modularity detector
		// so that several evaluation of the fitness can be performed
		// at the same time, for example by concurrent threads
		return modDetector_.computeModularity(s);
	}
	
	// ----------------------------------------------------------------------------

	@Override
	public String getOptionsStr() {

		String optionsStr = "";
		optionsStr += "--t0 " + t0_;
		optionsStr += " --tf " + tf_;
		optionsStr += " --coolingRate " + coolingRate_;
		optionsStr += " --numRuns " + numRuns_;
		if (bf_) optionsStr += " --bf";
		optionsStr += " --numproc " + numProc_;
		return optionsStr;
	}

    // =======================================================================================
    // GETTERS AND SETTERS
}
