/*
Copyright (c) 2013 Thomas Schaffter

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

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import no.uib.cipr.matrix.DenseVector;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.OptionBuilder;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.PosixParser;
import org.apache.commons.cli.UnrecognizedOptionException;
import org.apache.commons.io.FilenameUtils;

import ch.epfl.lis.jmod.JmodException;
import ch.epfl.lis.jmod.modularity.ModularityDetector;

import com.esotericsoftware.minlog.Log;

/**
 * Multi-thread brute-force method for optimally split communities in two subcommunities.<p>
 * 
 * Brute-force testing of the 2^{N-1} possible split vectors s for the division of a N-node community.<p>
 * 
 * The process is performed on batches of maximum NUM_WORKERS_PER_BATCH workers which evaluate each
 * NUM_EVALUATIONS_PER_THREAD different split vectors s.<p>
 * 
 * This method supports parallel processing.
 *
 * @version June 11, 2013
 * 
 * @author Thomas Schaffter (firstname.name@gmail.com)
 */
public class BruteForceDivider extends CommunityDivider {
	
	/** Maximum community size accepted. */
	public static final int BRUTEFORCE_MAX_COMMUNITY_SIZE = 58; // see comments below
	
	/** Value of the parameter --numproc to indicate that all processors available must be used. */
	public static final String USE_NUM_PROC_AVAILABLE = "MAX";
	
	/** Number of workers submitted per batch. */
	public static final int NUM_WORKERS_PER_BATCH = 1000;
	/** Number of solutions evaluated in each thread. */
	public static final int NUM_EVALUATIONS_PER_THREAD = 1000;
	
	/** Number of concurrent workers in the pool which should not exceed the number of processors. */
	protected int numProc_ = Runtime.getRuntime().availableProcessors();
	
	/** Reference to the modularity detector. */
	protected ModularityDetector modDetector_ = null;
	/** Name of the network being processed. */
	protected String networkName_ = null;
	
	/** Current best split vector s. */
//	protected DoubleMatrix1D bestS_ = null;
	protected DenseVector bestS_ = null;
	/** Modularity Q of current best split vector s. */
	protected double bestQ_ = -1.;
	/** Evaluation counter. */
	protected long counter_ = 0L;
	
	/** Size of the community being split. */
	protected int N_ = 0;
	/** Number of split vectors s to evaluate. */
	protected long numEvals_ = 0L;
	
	/** Index of the next split vector s to evaluate. */
	protected static long splitVectorSIndex_ = 0L;

    // =======================================================================================
    // PROTECTED METHODS
	
	@SuppressWarnings("static-access")
	@Override
	protected void buildOptions() {
		
		// create Options object
		options_ = new Options();
		
		// SHORT AND LONG OPTIONS FLAGS MUST BE DIFFERENT FROM THOSE OF JMOD
//		options_.addOption(OptionBuilder.withValueSeparator()
//				.withLongOpt("logTimeStep") // used with "--logTimeStep PERCENT", e.g. "--logTimeStep 5"
//				.withDescription("Report the modularity of the best split vector found every given percent of the total process length.")
//				.hasArgs()
//				.withArgName("PERCENT") // string used as in "--doubleArg MYVALUE"
//				.create());
		
		options_.addOption(OptionBuilder.withValueSeparator()
				.withLongOpt("numproc")
				.withDescription("Use the given number of processors (max: " + Runtime.getRuntime().availableProcessors() + ", specify MAX to use all the processors available, default: MAX).")
				.hasArgs()
				.withArgName("NUM")
				.create());
		
		// <!--- END --->
	}
	
	// ----------------------------------------------------------------------------
	
	@Override
	protected void parseOptions(String args[]) throws JmodException, Exception {
		
		// parse options
		CommandLineParser parser = new PosixParser();
		CommandLine cmd = null;
		try {
			cmd = parser.parse(options_, args);
			
			// <!--- TODO PARSES THE OPTIONS GIVEN TO THE METHOD -->
			
//			if (cmd.hasOption("logTimeStep")) {
//				// the value is returned as a string
//				String valueStr = cmd.getOptionValue("logTimeStep");
//				logTimeStep_ = Double.parseDouble(valueStr);
//			}
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
			
			// <!--- END --->
		
		} catch (UnrecognizedOptionException e) {
			Log.error(identifier_, e.getMessage());
			printHelp();
			throw new JmodException(e.getMessage());
		} catch (Exception e) {
			Log.error(identifier_, "Could not recognize all the options.", e);
			printHelp();
			throw new JmodException(e.getMessage());
		}
	}
	
	// ----------------------------------------------------------------------------
	
	/** Generates the split vector associated to the given index i. */
	protected void generateSplitVector(DenseVector s, int N, long numEvals, long i) {

		// s and -s have the same Q as expected
		// the sequence generated is not in the "paper" order
		for(int j = 0; j < N; j++) {
            long val = numEvals * j + i;
            long ret = (1 & (val >>> j));
            s.set(j, ret != 0 ? 1 : -1);
        }
	}
	
	// ----------------------------------------------------------------------------
	
	/** Updates the best split vector s if the given solution is better. */
	protected synchronized void submitBestSplitVectorS(DenseVector s, double bestQ, long bestIndex) {
		
		if (bestQ > bestQ_) {
//			bestS_.assign(s);
			bestS_.set(s);
			bestQ_ = bestQ;
			
			double percent = Math.min((100. * counter_) / numEvals_, 100.);
			Log.debug(networkName_ + "|" + identifier_, "Best Q = " + bestQ_ + " for i = " + bestIndex + " (" + new DecimalFormat("#.##").format(percent) + "%)");

			// Saves the current state of the module detection.
			// Don't forget to set modDetector_.currentSubcommunityS_ or the snapshot
			// will not see the change in differential mode.
//			modDetector_.currentSubcommunityS_.assign(bestS_);
			modDetector_.currentSubcommunityS_.set(bestS_);
			modDetector_.takeSnapshot(identifier_);
		}
	}
	
    // =======================================================================================
    // PUBLIC METHODS
	
	/** Constructor. */
	public BruteForceDivider() {
		
		super();
		
		// builds the options of the methods
		buildOptions();
		
		// <!--- TODO GIVES A NAME AND A DESCRIPTION TO THE METHOD -->

		identifier_ = "BF"; // without empty space
		name_ = "Brute force"; // any name
		// description in HTML format, use "<br>" as line break
		// no more than about 70 chars per line
		description_ = "<html>Brute force method for evaluating all 2^{n-1} possible split<br>" +
							 "vectors <b>s</b> that describe the split of a community in two<br>" +
							 "subcommunities.<br><br>" +
							 "Note that the computational time doubles each time the size of a<br>" +
							 "network is incremented by one, thus limiting the application of this<br>" +
							 "method to relatively small networks (typically n < 35-40).</html>";
		
		// <!--- END --->
	}
	
	// ----------------------------------------------------------------------------
	
	public BruteForceDivider(BruteForceDivider divider) {
		
		super(divider);

		numProc_ = divider.numProc_;
	}
	
	// ----------------------------------------------------------------------------
	
	/** Copy operator. */
	@Override
	public BruteForceDivider copy() {
		
		return new BruteForceDivider(this);
	}
	
	// ----------------------------------------------------------------------------
	
	@Override
	public void divide(ModularityDetector modDetector) throws JmodException, Exception {
		
		// <!--- TODO IMPLEMENTS A CUSTOM METHOD TO DIVIDE A COMMUNITY INTO TWO -->
		
		// At the end of the method, the split vector modDetector.currentSubcommunityS_ must have been filled with -1 and 1.
		// Here we set -1 and 1 element randomly in the split vector.
		
		modDetector_ = modDetector;
		networkName_ = FilenameUtils.getBaseName(modDetector.getNetwork().getName());
		
		// get size of the current community to divide
		N_ = modDetector.currentSubcommunitySize_;
		
		// long -> 64 bits -> 65 nodes
		// but due to the implementation -> 2^63 (max) * N => 2^(N-1) * N +N < 2^63 (see generateSplitVector()) => limit is N = 58
		if (N_ > BRUTEFORCE_MAX_COMMUNITY_SIZE)
			throw new JmodException("The community is too large to be split using the brute-force approach (" + N_ + " > Nmax,bf = " + BRUTEFORCE_MAX_COMMUNITY_SIZE + ").");
		
		// total number of split vectors s to evaluate
		numEvals_ = (long) Math.pow(2, N_-1); // overflows to 2147483647 using if using int
		Log.info(networkName_ + "|" + identifier_, "Total number of split vectors s to evaluate for community " + modDetector.currentCommunityBeingSplit_.getName() + ": " + numEvals_);
		
		// set the number of evaluations before reporting the current best fitness
//		logThreshold_ = (long) Math.max(1, (logTimeStep_ * (numEvals_ / 100)));
		
		// don't forget to initialize as this method is used several times
//		bestS_ = new DenseDoubleMatrix1D(N_);
//		bestS_.assign(-1);
		bestS_ = new DenseVector(N_);
		ModularityDetector.assign(bestS_, -1);
		bestQ_ = -1.;
	
		// use a thread pool to limit the number of concurrent s vector evaluations
		ExecutorService executor = null;	
		try{
			executor = Executors.newFixedThreadPool(numProc_);
			Log.debug(networkName_ + "|" + identifier_, "Options: " + getOptionsStr());
			
			counter_ = 0L;
			while (counter_ < numEvals_) {
				
				List<SplitVectorEvaluationWorker> workers = new ArrayList<SplitVectorEvaluationWorker>();
				List<Future<Void>> results = null; // references to the results
				
				// create workers
				SplitVectorEvaluationWorker worker = null;
				for (int i = 0; i < NUM_WORKERS_PER_BATCH; i++) {
					if (counter_ >= numEvals_) // no need to create more workers
						break;
					
					worker = new SplitVectorEvaluationWorker(counter_);
					workers.add(worker);
					counter_ += NUM_EVALUATIONS_PER_THREAD;
				}
				
				try {
					// perform the workers
					results = executor.invokeAll(workers);
					// wait until all workers are done
					for (Future<Void> result: results)
						result.get();
					
				} catch (Exception e) {
					throw e;
				} finally {
					workers = null;
					results = null;
				}
			}
		} catch (Exception e) {
			throw e;
		} finally {
			if (executor != null) {
				executor.shutdownNow();
				executor = null;
			}
		}
	
		// DON'T FORGET TO SET modDetector.currentSubcommunityS_
		// assign the best solution	
//		modDetector.currentSubcommunityS_.assign(bestS_);
		modDetector.currentSubcommunityS_.set(bestS_);
		modDetector.currentSubcommunityQ_ = bestQ_;
			
//		} catch (Exception e) {
//			throw e;
//		} finally {
//			if (executor_ != null) {
//				executor_.shutdownNow();
//				executor_ = null;
//			}
//		}
		
		// <!--- END -->
	}
	
	// ----------------------------------------------------------------------------
	
	@Override
	public String getOptionsStr() {
		
		String optionsStr = "";
		
		// <!--- TODO SUMMARIZES THE CURRENT OPTIONS AS A SINGLE STRING DISPLAYED BEFORE RUNNING EACH METHOD -->
		
//		optionsStr = "--logTimeStep " + logTimeStep_ + " --numproc MAX";
		optionsStr = "--numproc ";
		if (numProc_ == Runtime.getRuntime().availableProcessors())
			optionsStr += "MAX";
		else
			optionsStr += "" + numProc_;

		// <!-- END -->
		
		return optionsStr;
	}
	
    // =======================================================================================
    // GETTERS AND SETTERS
	
	public void setNumProc(int numProc) { numProc_ = numProc; }
	
    // =======================================================================================
    // INNER CLASSES
	
	/** Evaluates a batch of split vector s. */
	private class SplitVectorEvaluationWorker implements Callable<Void> {
		
		/** Index of the first split vector to evaluate. */
		private long firstSplitVectorIndex_ = 0;
		
		/** Constructor. */
		public SplitVectorEvaluationWorker(long firstSplitVectorIndex) {
			
			firstSplitVectorIndex_ = firstSplitVectorIndex;
		}
		
		/** Evaluates a batch of split vectors. */
		@Override
		public Void call() throws Exception {
			
//			DoubleMatrix1D bestS = new DenseDoubleMatrix1D(N_);
			DenseVector bestS = new DenseVector(N_);
			double bestQ = -1.;
			
			// do not evaluate more solutions after the last one
			long maxIters = Math.min(firstSplitVectorIndex_ + NUM_EVALUATIONS_PER_THREAD, numEvals_);
			// index associated to the best s vector found
			long bestIndex = 0L;
			
//			DoubleMatrix1D s = new DenseDoubleMatrix1D(N_);
			DenseVector s = new DenseVector(N_);
			double Q = 0.;
			for (long i = firstSplitVectorIndex_; i < maxIters; i++) {
				generateSplitVector(s, N_, numEvals_, i); // set s
				Q = modDetector_.computeModularity(s);
				
				if (Q > bestQ) {
//					bestS.assign(s);
					bestS.set(s);
					bestQ = Q;
					bestIndex = i;
				}
			}
			// submits the results
			submitBestSplitVectorS(bestS, bestQ, bestIndex);
			
			return null;
		}
	}
}
