/*
Copyright (c) 2008-2012 Thomas Schaffter, Daniel Marbach

We release this software open source under an MIT license (see below). 
Please cite the papers listed on http://lis.epfl.ch/tschaffter/jmod/ 
when using Jmod in your publication.

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in
all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
THE SOFTWARE.
*/

package ch.epfl.lis.jmod.modularity.community.dividers;

import java.io.File;
import java.io.FileWriter;

import ch.epfl.lis.ga.binary.Chromosome;
import ch.epfl.lis.ga.binary.FitnessFunction;
import ch.epfl.lis.ga.binary.Population;
import ch.epfl.lis.ga.binary.PopulationPostProcessing;
import no.uib.cipr.matrix.DenseVector;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.OptionBuilder;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.PosixParser;
import org.apache.commons.cli.UnrecognizedOptionException;
import org.apache.commons.io.FilenameUtils;

import com.esotericsoftware.minlog.Log;

import ch.epfl.lis.jmod.JmodException;
import ch.epfl.lis.jmod.modularity.ModularityDetector;

/**
 * Uses a binary genetic algorithm (GA) to split communities in two.
 * 
 * @version January 24, 2012
 * 
 * @author Thomas Schaffter (firstname.name@gmail.com)
 */
public class GeneticAlgorithmDivider extends CommunityDivider implements FitnessFunction, PopulationPostProcessing {
	
	/** Value of the parameter --numproc to indicate to use the maximum number of processors available. */
	public static final String USE_NUM_PROC_AVAILABLE = "MAX";
	
	/** Crossover types. */
	public static int[] CROSSOVER_TYPES = {Chromosome.CROSS_OVER_ONE_POINT, Chromosome.CROSS_OVER_TWO_POINT, Chromosome.CROSS_OVER_UNIFORM};
	/** Names of crossover types. */
	public static String[] CROSSOVER_TYPES_STR = {"one-point", "two-point", "uniform"};
	
	/** Number of concurrent workers in the pool which should not exceed the number of processors. */
	protected int numProc_ = Runtime.getRuntime().availableProcessors();
	
	/** Number of bits per gene is one. */
	protected int numBitsPerGene_ = 1;
	
	/** Population size (default: 100). */
	protected int popSize_ = 100;
	/** Number of generations. */
	protected int numGenerations_ = 1000;
	/** Stopping criterion. */
	protected int stoppingCriterion_ = Population.STOPPING_CRITERIA_GENETIC_CONVERGENCE;
	/** If Population.STOPPING_CRITERIA_GENETIC_CONVERGENCE is used, mean normalized Hamming distance to satisfy. */
	protected double targetMeanHammingDistance_ = 1.;
	
	/** Average number of nodes moved to the other community due to mutation. */
	protected int numNodesMutated_ = 1;
	/** Crossover type (1=one-point, 2=two-point, 3=uniform, default: 3). */
	protected int crossOverType_ = 3;
	/** Crossover probability for GA in [0.,1.] (default: 1.0). */
	protected double crossOverRate_ = 1.0;
	/** First fraction of generations where crossover is enabled in [0,1] (default: 1). */
	protected double crossOverLifetime_ = 1.;
	/** Number of elites saved and replaced randomly in the next population round (default: 1). */
	protected int numElites_ = 1;
	/** Number of GA runs per community or module division (default: 1). */
	protected int numGaRunsPerCommunityDivision_ = 1;
	
	/** Use brute force approach when GA num evals > 2^{N-1}. */
	protected boolean bf_ = false;
	
	/** Save population individuals' fitness to file. */
	protected boolean saveStats_ = false;
	
	/** Reference to the modularity detector. */
	protected ModularityDetector modDetector_ = null;
	
	/** Index of the current run. */
	protected int currentRunIndex_ = 0;
	
	/** Displays an update every X generations. */
	protected int logsInterval_ = 100;
	/** Next generation where logs will be displayed. */
	protected int nextLog_ = logsInterval_;
	
    // =======================================================================================
    // PROTECTED METHODS
	
	@SuppressWarnings("static-access")
	@Override
	protected void buildOptions() {
		
		// create Options object
		options_ = new Options();
		
		// SHORT AND LONG OPTIONS FLAGS MUST BE DIFFERENT FROM THOSE OF JMOD
		options_.addOption(OptionBuilder.withValueSeparator()
				.withLongOpt("popSize")
				.withDescription("Population size (default: " + Integer.toString(popSize_) + ").")
				.hasArgs(1)
				.withArgName("NUM")
				.create("p"));
		
		options_.addOption(OptionBuilder.withValueSeparator()
				.withLongOpt("numGenerations")
				.withDescription("Maximum number of generations (default: " + numGenerations_ + ").")
				.hasArgs(1)
				.withArgName("NUM")
				.create("g"));
		
		options_.addOption(OptionBuilder.withValueSeparator()
				.withLongOpt("stoppingCriterion")
				.withDescription(Population.STOPPING_CRITERIA_MAX_ITERATIONS + "=Maximum number of generations reached, " + Population.STOPPING_CRITERIA_GENETIC_CONVERGENCE + "=Genetic convergence reached.")
				.hasArgs(1)
				.withArgName("NUM")
				.create("s"));
		
		options_.addOption(OptionBuilder.withValueSeparator()
				.withLongOpt("targetMeanHammingDistance")
				.withDescription("Mean Hamming distance to reach between two individuals to converge.")
				.hasArgs(1)
				.withArgName("NUM")
				.create());
		
		options_.addOption(OptionBuilder.withValueSeparator()
				.withLongOpt("numNodesMutated")
				.withDescription("Number of nodes moved to the other subcommunity using mutation during community splits (default: " + numNodesMutated_ + ").")
				.hasArgs(1)
				.withArgName("NUM")
				.create("n"));
		
		options_.addOption(OptionBuilder.withValueSeparator()
				.withLongOpt("xOverType")
				.withDescription("Crossover type (1=one-point, 2=two-point or 3=uniform, default: " + CROSSOVER_TYPES_STR[crossOverType_-1] + ").")
				.hasArgs(1)
				.withArgName("TYPE")
				.create());
		
		options_.addOption(OptionBuilder.withValueSeparator()
				.withLongOpt("xOverRate")
				.withDescription("Crossover rate in [0.,1.] (default: " + crossOverRate_ + ").")
				.hasArgs(1)
				.withArgName("RATE")
				.create());
		
		options_.addOption(OptionBuilder.withValueSeparator()
				.withLongOpt("xOverLifetime")
				.withDescription("First fraction of generations where crossover is enabled in [0,1] (default: " + crossOverLifetime_ + ").")
				.hasArgs(1)
				.withArgName("DOUBLE")
				.create());
		
		options_.addOption(OptionBuilder.withValueSeparator()
				.withLongOpt("numElites")
				.withDescription("Number of elites saved and replaced randomly in the next population (default: " + numElites_ + ").")
				.hasArgs(1)
				.withArgName("NUM")
				.create("e"));
		
		options_.addOption(OptionBuilder.withValueSeparator()
				.withLongOpt("numRuns")
				.withDescription("Number of GA runs per community division (default: " + numGaRunsPerCommunityDivision_ + ").")
				.hasArgs(1)
				.withArgName("NUM")
				.create("r"));
		
		options_.addOption(OptionBuilder.withValueSeparator()
				.withLongOpt("bf")
				.withDescription("Use brute force approach when pop size * max num generations >= 2^{n-1} (default: disabled).")
				.create());
		
		options_.addOption(OptionBuilder.withValueSeparator()
				.withLongOpt("saveStats")
				.withDescription("Save the fitness of the population individuals and the population pair-wise Hamming distance to file (default: false).")
				.create());
		
		options_.addOption(OptionBuilder.withValueSeparator()
				.withLongOpt("numproc")
				.withDescription("Use the given number of processors (max: " + Runtime.getRuntime().availableProcessors() + ", specify MAX to use all the processors available, default: MAX).")
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
			if (cmd.hasOption("popSize"))
				popSize_ = Integer.parseInt(cmd.getOptionValue("popSize"));
			if (cmd.hasOption("numGenerations"))
				numGenerations_ = Integer.parseInt(cmd.getOptionValue("numGenerations"));
			if (cmd.hasOption("stoppingCriterion"))
				stoppingCriterion_ = Integer.parseInt(cmd.getOptionValue("stoppingCriterion"));
			if (cmd.hasOption("targetMeanHammingDistance"))
				targetMeanHammingDistance_ = Double.parseDouble(cmd.getOptionValue("targetMeanHammingDistance"));
			if (cmd.hasOption("numNodesMutated"))
				numNodesMutated_ = Integer.parseInt(cmd.getOptionValue("numNodesMutated"));
			if (cmd.hasOption("xOverType"))
				crossOverType_ = Integer.parseInt(cmd.getOptionValue("xOverType"));
			if (cmd.hasOption("xOverRate"))
				crossOverRate_ = Double.parseDouble(cmd.getOptionValue("xOverRate"));
			if (cmd.hasOption("xOverLifetime"))
				crossOverLifetime_ = Double.parseDouble(cmd.getOptionValue("xOverLifetime"));			
			if (cmd.hasOption("numElites"))
				numElites_ = Integer.parseInt(cmd.getOptionValue("numElites"));
			if (cmd.hasOption("numRuns"))
				numGaRunsPerCommunityDivision_ = Integer.parseInt(cmd.getOptionValue("numRuns"));
			saveStats_ = cmd.hasOption("saveStats");
			bf_ = cmd.hasOption("bf");
			
			if (crossOverType_ < 1 || crossOverType_ > 3)
				throw new Exception("Crossover type must take values 1=one-pont, 2=two-point or 3=uniform.");
			
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
	public GeneticAlgorithmDivider() {
		
		super();
		
		// builds the options of the methods
		buildOptions();
		
		identifier_ = "GA";
		name_ = "Genetic algorithm (GA)";
		description_ = "<html>Instead of calculating the dominant eigenvector of the modularity<br>" +
					         "matrix <b>B</b> to define the split of a community in two<br>" +
					         "subcommunities, a binary generational GA is applied to find the<br>" +
					         "split vector <b>s</b> that maximizes the modularity Q.<br><br>" +
					         "Use the option --bf to run the brute force method when the number of<br>" +
					         "GA evaluations (popSize*numGenerations) is larger than 2^{n-1}<br>" +
					         "where n is the size of the current community to split.</html>";
	}
	
	// ----------------------------------------------------------------------------
	
	/** Copy constructor. */
	public GeneticAlgorithmDivider(GeneticAlgorithmDivider divider) {
		
		super(divider);

		numProc_ = divider.numProc_;
		numBitsPerGene_ = divider.numBitsPerGene_;
		popSize_ = divider.popSize_;
		numGenerations_ = divider.numGenerations_;
		stoppingCriterion_ = divider.stoppingCriterion_;
		targetMeanHammingDistance_ = divider.targetMeanHammingDistance_;
		numNodesMutated_ = divider.numNodesMutated_;
		crossOverType_ = divider.crossOverType_;
		crossOverRate_ = divider.crossOverRate_;
		crossOverLifetime_ = divider.crossOverLifetime_;
		numElites_ = divider.numElites_;
		numGaRunsPerCommunityDivision_ = divider.numGaRunsPerCommunityDivision_;
		bf_ = divider.bf_;
		saveStats_ = divider.saveStats_;
	}
	
	// ----------------------------------------------------------------------------
	
	@Override
	public GeneticAlgorithmDivider copy() {
		
		return new GeneticAlgorithmDivider(this);
	}
	
	// ----------------------------------------------------------------------------
	
	@Override
	public void divide(ModularityDetector modDetector) throws JmodException, Exception {
		
		modDetector_ = modDetector;
		String name = FilenameUtils.getBaseName(modDetector.getNetwork().getName());
		int N = modDetector.currentSubcommunitySize_;
        int totalBitlen = N * numBitsPerGene_;
        // compute bit mutation probability
        double mutationProbability = (double) numNodesMutated_ / totalBitlen;

        double bestCurrentSubcommunityQ = 0.;
        DenseVector bestCurrentSubcommunityS = new DenseVector(modDetector.currentSubcommunitySize_);
        
        // run brute force approach if numCombinations < popSize*numGenerations
        if (bf_ && N <= BruteForceDivider.BRUTEFORCE_MAX_COMMUNITY_SIZE) {
        	int numCombinations = (int) Math.pow(2, N-1);
        	if (numCombinations < popSize_*numGenerations_) {
        		Log.debug(name + "|" + identifier_, "Running brute force method on small community.");
        		BruteForceDivider bf = new BruteForceDivider();
        		bf.setNumProc(numProc_);
        		bf.divide(modDetector);
        		return;
        	}
        }
        
        // run GA
		for (currentRunIndex_= 1; currentRunIndex_ <= numGaRunsPerCommunityDivision_; currentRunIndex_++) {
			
			Log.debug(name + "|" + identifier_, "Running GA run " + currentRunIndex_ + " over " + numGaRunsPerCommunityDivision_ + ".");
			Log.debug(name + "|" + identifier_, "Options: " + getOptionsStr());
//			Log.debug(name + "|" + identifier_, "Mutation rate per bit: " + mutationProbability);
//			Log.debug(name + "|" + identifier_, "Mutation rate: " + numNodesMutated_ + " node" + (numNodesMutated_ > 1 ? "s" : "") + "/individual");
			
			nextLog_ = logsInterval_;
			
	        Population pop = new Population(popSize_, numBitsPerGene_, totalBitlen);
	        pop.setNumProc(numProc_);
	        pop.setFitnessFunc(this);
	        pop.setPostProcessing(this);
	        pop.setMaxDoubleForGenes(1);
	        pop.setMinDoubleForGenes(0);
	        pop.setElitismSize(numElites_);
	        pop.randomize();
	        
	        pop.setMutateProb(mutationProbability);
	        pop.setCrossProb(crossOverRate_);
	        pop.setCrossOverType(crossOverType_);
	        pop.setCrossOverLifetime(crossOverLifetime_);
	        
	        if (saveStats_) {
	        	String outputDirectory = FilenameUtils.getFullPath(modDetector_.getOutputDirectory().getPath());
	        	String networkName = FilenameUtils.getBaseName(modDetector_.getNetwork().getName());
				String statsFilename = networkName + "_" + currentCommunityName_ + "_GA_stats_run_" + currentRunIndex_ + ".txt";
				File statsFile = new File(outputDirectory + statsFilename);
				Log.debug(name + "|" + identifier_, "Opening " + statsFile.getPath());
				pop.setStatsFileWriter(new FileWriter(statsFile, false));
	        }
	        
	        try{
	        	pop.setStoppingCriterion(stoppingCriterion_);
	        	pop.setTargetMeanHammingDistance(targetMeanHammingDistance_);
	            pop.startOptimization(numGenerations_);
	        } catch (Exception e) {
	        	Log.error(name + "|" + identifier_, "GA unsuccessful.", e);
	            throw new JmodException("GA stopped unexpectedly.");
	        }
	        
	        // set currentSubcommunityS_ with the best solution
	        setCurrentSubCommunityS(pop.get(0));
	        modDetector.currentSubcommunityQ_ = modDetector.computeModularity();
	        
	        // gradient descent optimization (MVM)
	        modDetector.movingVertexMethod();
	        
	        // save the solution if it's the best found so far
	        if (modDetector.currentSubcommunityQ_ > bestCurrentSubcommunityQ) {
	        	bestCurrentSubcommunityQ = modDetector.currentSubcommunityQ_;
//	        	bestCurrentSubcommunityS.assign(modDetector.currentSubcommunityS_);
	        	bestCurrentSubcommunityS.set(modDetector.currentSubcommunityS_);
	        }
	        Log.debug(name + "|" + identifier_, "Q = " + modDetector.currentSubcommunityQ_ + " (best Q = " + bestCurrentSubcommunityQ + ")");
		}
		
		// DON'T FORGET TO SET modDetector.currentSubcommunityS_
		// assign the best solution
		modDetector.currentSubcommunityS_.set(bestCurrentSubcommunityS);	
	}
		
	// ----------------------------------------------------------------------------
	
	/** Fitness function of the GA, which is the modularity Q or dQ associated to a module division. */
	@Override
	public double f(Population pop, Chromosome chromosome) throws Exception {
		
		if (modDetector_.isCanceled())
			throw new Exception("Module detection canceled.");
		
		try {			
			return evaluateFitness(chromosome);
			
		} catch (Exception e) {
			Log.warn("ModularityDetector", "Error while evaluating GA fitness.", e);
			return -1.;
		}
	}
	
	// ----------------------------------------------------------------------------
	
	/** Method called at the end of each generation. Currently updates the state of the network. */
	@Override
	public void postProcess(Population pop, Chromosome bestIndividual, int generation) throws Exception {
		
		// Saves the current state of the module detection.
		// Don't forget to set modDetector_.currentSubcommunityS_ or the snapshot
		// will not see the change in differential mode.
		setCurrentSubCommunityS(bestIndividual);
		modDetector_.takeSnapshot(identifier_);
		
		if (generation > nextLog_-1) {
			String name = FilenameUtils.getBaseName(modDetector_.getNetwork().getName());
			Log.debug(name + "|" + identifier_, "Best Q = " + bestIndividual.getFitness() + " (run " + currentRunIndex_ + ", generation " + generation + ")");
			nextLog_ += logsInterval_;
		}
	}
	
	// ----------------------------------------------------------------------------
	
	@Override
	public String getOptionsStr() {
		
		String optionsStr = "";
		optionsStr += "--popSize " + popSize_;
		optionsStr += " --numGenerations " + numGenerations_;
		optionsStr += " --stoppingCriterion " + stoppingCriterion_;
		optionsStr += " --targetMeanHammingDistance " + targetMeanHammingDistance_;
		optionsStr += " --numNodesMutated " + numNodesMutated_;
		optionsStr += " --xOverType " + crossOverType_;
		optionsStr += " --xOverRate " + crossOverRate_;
		optionsStr += " --xOverLifetime " + crossOverLifetime_;		
		optionsStr += " --numElites " + numElites_;
		optionsStr += " --numRuns " + numGaRunsPerCommunityDivision_;
		if (bf_) optionsStr += " --bf";
		optionsStr += " --numproc " + numProc_;
		if (saveStats_) optionsStr += " --saveStats";
		return optionsStr;
	}

    // =======================================================================================
    // GETTERS AND SETTERS
	
	public void setPopSize(int popSize) { popSize_ = popSize; }
	public int getPopSize() { return popSize_; }
	
	public void setNumGenerations(int numGenerations) { numGenerations_ = numGenerations; }
	public int getNumGenerations() { return numGenerations_; }
	
	public void setAverageNumberNodesMutated(int avgNumberNodesMutated) { numNodesMutated_ = avgNumberNodesMutated; }
	public int getAverageNumberNodesMutated() { return numNodesMutated_; };
	
	public void setCrossOverProbability(double crossOverProbability) { crossOverRate_ = crossOverProbability; }
	public double getCrossOverProbability() { return crossOverRate_; }
	
	public void setCrossOverLifetime(double fractionOfNumberOfGenerations) { crossOverLifetime_ = fractionOfNumberOfGenerations; }
	public double getCrossOverLifetime() { return crossOverLifetime_; }
	
	public void setNumGaRunsPerCommunityDivision(int numRuns) { numGaRunsPerCommunityDivision_ = numRuns; }
	public int getNumGaRunsPerCommunityDivision() { return numGaRunsPerCommunityDivision_; }
}
