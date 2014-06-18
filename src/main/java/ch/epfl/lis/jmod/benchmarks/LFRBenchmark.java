package ch.epfl.lis.jmod.benchmarks;

import java.io.File;
import java.io.FileWriter;
import java.io.Writer;
import java.text.DecimalFormat;
import java.util.List;
import java.util.ArrayList;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.apache.commons.io.FileUtils;

import ch.epfl.lis.jmod.JmodNetwork;
import ch.epfl.lis.networks.Structure;

import com.esotericsoftware.minlog.Log;

/**
 * Wrapper for generating LFR benchmark networks using multiple threads.<p>
 * 
 * <b>Types of networks:</b><p>
 * <ul>
 * <li>BINARY_NETWORKS
 * <li>DIRECTED_NETWORKS
 * <li>WEIGHTED_NETWORKS
 * <li>WEIGHTED_DIRECTED_NETWORKS
 * </ul><p>
 * 
 * <b>Input:</b><p>
 * <ul>
 * <li>Type of networks (see above)
 * <li>Path to the benchmark directory (is created)
 * <li>LFR program parameters
 * <li>Number of repetitions
 * <li>Output network file format (e.g. TSV, GML, DOT, or Pajek/NET format)
 * </ul><p>
 * 
 * Because LFR benchmark programs saves networks in TSV format, selecting this
 * format leads to faster benchmark generation (network files are simply renamed
 * instead of having to rewrite them in the specified file format).<p>
 * 
 * <b>Output:</b><p>
 * <ul>
 * <li>Network file
 * <li>Community structure file ({@link ch.epfl.lis.jmod.inference.community.CommunityStructure#CLINES_FORMAT CLINES_FORMAT})
 * </ul><p>
 * 
 * <b>Requirements:</b><p>
 * 
 * First go to the folder "benchmarks" located at the root of Jmod project and execute "compile.sh" to compile LFR benchmark programs.<p>
 * 
 * <b>References:</b><p>
 * 
 * A Lancichinetti, S Fortunato. <a href="http://www.nature.com/srep/2012/120327/srep00336/full/srep00336.html" 
 * target="_blank">Consensus clustering in complex networks</a>. Scientific reports, 2, 2012.<br>
 * 
 * A Lancichinetti, S Fortunato. <a href="http://pre.aps.org/abstract/PRE/v80/i1/e016118" 
 * target="_blank">Benchmarks for testing community detection algorithms on directed and 
 * weighted graphs with overlapping communities</a>. <i>Physical Review E</i>, 80(1):016118, 2009.
 * 
 * @see ch.epfl.lis.jmod.inference.community.CommunityStructure
 * 
 * @version November 6, 2013
 * 
 * @author Thomas Schaffter (thomas.schaff...@gmail.com)
 */
public class LFRBenchmark {

	public static final int BINARY_NETWORKS = 0;
	public static final int DIRECTED_NETWORKS = 1;
	public static final int WEIGHTED_NETWORKS = 2;
	public static final int WEIGHTED_DIRECTED_NETWORKS = 3;
	
	private static final String LFR_BINARY_COMMAND				= "benchmarks/bin/lfr_binary";
	private static final String LFR_DIRECTED_COMMAND 			= "benchmarks/bin/lfr_directed";
	private static final String LFR_WEIGHTED_COMMAND 			= "benchmarks/bin/lfr_weighted";
	private static final String LFR_WEIGHTED_DIRECTED_COMMAND	= "benchmarks/bin/lfr_weighted_directed";
	
	/** Maximum number of concurrent threads (= pool size). */
	private int numConcurrentThreads_ = 6;//Runtime.getRuntime().availableProcessors();
	
	/** Command to execute. */
	private String command_ = null;
	
	/** Generate weighted networks. */
	private boolean weighted_ = false;
	/** Generate directed networks. */
	private boolean directed_ = false;
	
	/** Benchmark directory. */
	private String benchmarkDirectory_ = null;
	/** Number of nodes N. */
	private int N_ = 1000;
	/** Average node degree k (in-degree for directed networks). */
	private int k_ = 20;
	/** Maximum node degree kmax (in-degree for directed networks). */
	private int maxk_ = 50;
	
	/** Minimum size of the communities. */
	private int minc_ = 10;
	/** Maximum size of the communities. */
	private int maxc_ = 50;
	
	/** Mixing parameter for the topology (in [.1, .9]). */
	private double mut_ = 0.1;
	/** Minimum value of the mixing parameter mut. */
	private double minmut_ = 0.1;
	/** Maximum value of the mixing parameter mut. */
	private double maxmut_ = 0.9;
	/** Step size used when sampling the range [minmut_, maxmut_] of the mixing parameter mut. */
	private double mutstep_ = 0.05;
	/** If true, mut is set to muw when generating weighted networks. */
	private boolean mutEqualToMuw_ = false;
	
	/** Minus exponent for the degree sequence. */
	private double t1_ = 2.;
	/** Minus exponent for the community size distribution. */
	private double t2_ = 1.;
	/** Exponent for the weight distribution (weighted networks only). */
	private double beta_ = 1.5;
	
	/**
	 * Mixing parameter for the weights (in [.1, .6]; see paper).
	 * When generating benchmark networks for muw in a given range, set mut equal to
	 * muw or 0.5 as performed by Lancichinetti et al. in their comparative analysis.
	 */
	private double muw_ = 0.1;
	/** Minimum value of the mixing parameter mut. */
	private double minmuw_ = 0.1;
	/** Maximum value of the mixing parameter mut. */
	private double maxmuw_ = 0.9;
	/** Step size used when sampling the range [minmuw_, maxmuw_] of the mixing parameter muw. */
	private double muwstep_ = 0.05;
	
	/** Number of overlapping nodes. */
	private int on_ = 0;
	/** Number of memberships of the overlapping nodes. */
	private int om_ = 0;
	
//	/** Average clustering coefficient. */
//	private double C_ = 0.7;
	
	/** Number of network repetitions. */
	private int numRepetitions_ = 20;
	
	/** Network format (TSV, GML, DOT, Pajek/NET). */
	private Structure.Format networkFormat_ = Structure.Format.TSV;
	
	/** Number of networks to generate (used to track progress). */
	private int numWorkers_ = 0;
	/** Number of networks generated (used to track progress). */
	private int numWorkersDone_ = 0;

	// ============================================================================
	// PUBLIC METHODS
	
	/** Main method. */
	public static void main(String args[]) {
		
		// weighted: mut = 0.5 or mut=muw
		
		try {
			LFRBenchmark benchmark = new LFRBenchmark(LFRBenchmark.WEIGHTED_NETWORKS);
			benchmark.setBenchmarkDirectory("/media/data/LFR_w_undir/");
			benchmark.setMandatoryParameters(1000, 20, 50); // N, k, and maxk
			benchmark.setCommunitySizes(10, 50); // minimum and maximum community size (20,100)
			benchmark.setOverlappingNodes(0, 0); // on and om
//			benchmark.setNumRepetitions(20); // number of networks generated for each set of parameters
			// weighted networks (true: mut=muw, false: mut=0.5)
			benchmark.setMutEqualToMuw(false);
			benchmark.setNetworkFormat(Structure.Format.TSV);
			benchmark.generate();
			
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			Log.info("Done");
			System.exit(0);
		}
	}
	
	// ----------------------------------------------------------------------------
	
	/** Constructor. */
	public LFRBenchmark(int networkType) {
		
		int shift = (networkType>>1);		
		boolean weighted = (shift == 1);
		boolean directed = (networkType - 2*shift == 1);
		
		initialize(weighted, directed);
	}
	
	// ----------------------------------------------------------------------------
	
	/** Constructor. */
	public LFRBenchmark(boolean weighted, boolean directed) {
		
		initialize(weighted, directed);
	}
	
	// ----------------------------------------------------------------------------
	
	/** Generates a LFR benchmark with standard file organization. */
	public void generate() throws Exception {
		
		// use a thread pool to limit the number of dot graph processed at a time
		ExecutorService executor = Executors.newFixedThreadPool(numConcurrentThreads_);
		List<Worker> workers = new ArrayList<Worker>();
		List<Future<Void>> results = null;
		
		if (!benchmarkDirectory_.endsWith(File.separator))
			benchmarkDirectory_ += File.separator;
		String nDirectory = benchmarkDirectory_ + "N" + N_ + File.separator;
		String cDirectory = nDirectory + "c" + minc_ + "-" + maxc_ + File.separator;
		
		int multiplier = 100;
		if (mutstep_ < 1/(double)multiplier || muwstep_ < 1/(double)multiplier )
			throw new Exception("Didn't expect such small mutstep or muwstep. Please increase multiplier.");
		
		if (weighted_) {
		
			int minmuwInt = (int)(multiplier * minmuw_);
			int maxmuwInt = (int)(multiplier * maxmuw_);
			int muwstepInt = (int)(multiplier * muwstep_);
			
			String muwDirectory = null;
			for (int muwInt = minmuwInt; muwInt <= maxmuwInt; muwInt += muwstepInt) {
				
				muw_ = muwInt / (double)multiplier;
				muwDirectory = cDirectory + "muw" + muwInt/*new DecimalFormat("0.00").format(muw_)*/ + File.separator;
				addMutWorkers(workers, muwDirectory);
			}			
		} else
			addMutWorkers(workers, cDirectory);

		// for monitoring progress
		numWorkers_ = workers.size();
		
		try {
			results = executor.invokeAll(workers);
			for (Future<Void> result: results)
				result.get();
			
		} catch (OutOfMemoryError e) {
			Log.error("Out of memory. Try again with less networks at the same time (numConcurrentThreads_).");
			throw e;
		} catch (Exception e) {
			Log.error("Unable to generate LFR benchmark.");
			throw e;
		} finally {
			executor = null;
			workers = null;
			results = null;
		}
	}
	
	// ============================================================================
	// PRIVATE METHODS
	
	/** Initialization. */
	private void initialize(boolean weighted, boolean directed) {
		
		weighted_ = weighted;
		directed_ = directed;
		
		int code = 2 * (weighted_ ? 1 :0) + (directed_ ? 1 :0);
		
		switch(code) {
			case 0: command_ = LFR_BINARY_COMMAND; break;
			case 1: command_ = LFR_DIRECTED_COMMAND; break;
			case 2: command_ = LFR_WEIGHTED_COMMAND; break;
			case 3: command_ = LFR_WEIGHTED_DIRECTED_COMMAND; break;
		}
	}
	
	// ----------------------------------------------------------------------------
	
	/** Adds workers for different values of mut (depends on whether the benchmark is weighted or not). */
	private void addMutWorkers(List<Worker> workers, String currentDirectory) throws Exception {
		
		int multiplier = 100;
		int minmutInt = (int)(multiplier * minmut_);
		int maxmutInt = (int)(multiplier * maxmut_);
		int mutstepInt = (int)(multiplier * mutstep_);
		
		String mutDirectory = null;
		String commandLine = null;
		if (weighted_) {
			
			if (mutEqualToMuw_)
				mut_ = muw_;
			else
				mut_ = 0.5;
			
			mutDirectory = currentDirectory + "mut" + (int)(mut_*multiplier)/*new DecimalFormat("0.00").format(mut_)*/ + File.separator;
			commandLine = getCommandLine();
			for (int i = 1; i <= numRepetitions_; i++)
				workers.add(new Worker(commandLine, mutDirectory, i));
			
		} else {
			for (int mutInt = minmutInt; mutInt <= maxmutInt; mutInt += mutstepInt) {
				
				mut_ = mutInt / (double)multiplier;
				mutDirectory = currentDirectory + "mut" + mutInt/*new DecimalFormat("0.00").format(mut_)*/ + File.separator;
				commandLine = getCommandLine();
				for (int i = 1; i <= numRepetitions_; i++)
					workers.add(new Worker(commandLine, mutDirectory, i));
			}
		}
	}
	
	// ----------------------------------------------------------------------------
	
	/** Returns a command line for generating weighted/directed LFR networks. */
	private String getCommandLine() throws Exception {
		
		String commandLine = command_;
		commandLine += " -N " + N_;
		commandLine += " -k " + k_;
		commandLine += " -maxk " + maxk_;
		commandLine += " -minc " + minc_;
		commandLine += " -maxc " + maxc_;
		
		if (weighted_) {
			commandLine += " -muw " + muw_;
			commandLine += " -mut " + mut_;
		} else
			commandLine += " -mu " + mut_;
		
		commandLine += " -on " + on_;
		commandLine += " -om " + om_;
		
		commandLine += " -t1 " + t1_;
		commandLine += " -t2 " + t2_;
		if (weighted_)
			commandLine += " -beta " + beta_;
		
		return commandLine;
	}
	
	// ----------------------------------------------------------------------------
	
	/** Shows the percentage of workers completed. */
	private synchronized void showProgress() {
		
		numWorkersDone_++;
		double percent = (100. * numWorkersDone_) / numWorkers_;
		Log.info(new DecimalFormat("#.00").format(percent) + "%");	
	}
	
	// ============================================================================
	// GETTERS AND SETTERS
	
	public void setBenchmarkDirectory(String directory) { benchmarkDirectory_ = directory; }
	public void setMandatoryParameters(int N, int k, int maxk) { N_ = N; k_ = k; maxk_ = maxk; }
	public void setCommunitySizes(int minc, int maxc) { minc_ = minc; maxc_ = maxc; }
	public void setMuw(double minmuw, double maxmuw, double muwstep) { minmut_ = minmuw; maxmut_ = maxmuw; mutstep_ = muwstep; }
	public void setMut(double minmut, double maxmut, double mutstep) { minmut_ = minmut; maxmut_ = maxmut; mutstep_ = mutstep; }
	public void setMutEqualToMuw(boolean b) { mutEqualToMuw_ = b; }
	public void setT1(double t1) { t1_ = t1; }
	public void setT2(double t2) { t2_ = t2; }
	public void setBeta(double beta) { beta_ = beta; }
	public void setOverlappingNodes(int on, int om) { on_ = on; om_ = om; }
	public void setNumRepetitions(int numRepetitions) { numRepetitions_ = numRepetitions; }
	public void setNetworkFormat(Structure.Format format) { networkFormat_ = format; }
	
	/** Synchronized method to avoid issue with workers trying to create the same directory at the same time. */
	public synchronized void mkdir(File directoryFile) throws Exception { FileUtils.forceMkdir(directoryFile); }
	
	// ============================================================================
	// INNER CLASSES
	
	/** Generates a single LFR benchmark network. */
	private class Worker implements Callable<Void> {
		
		/** Command line to execute. */
		private String commandLine_ = null;
		/** Network index. */
		private int networkIndex_ = 0;
		/** Target directory. */
		private String targetDirectory_ = null;
		
		// ============================================================================
		// PUBLIC METHODS
		
		/** Constructor. */
		public Worker(String commandLine, String targetDirectory, int networkIndex) {
			
			commandLine_ = commandLine;
			targetDirectory_ = targetDirectory;
			networkIndex_ = networkIndex;
		}
		
		// ----------------------------------------------------------------------------
		
		@Override
		public Void call() throws Exception {
			
			String tmpDirectory = "tmp_" + UUID.randomUUID().toString() + File.separator;
			File tmpDirectoryFile = new File(tmpDirectory);
			
			try {
				mkdir(tmpDirectoryFile);
				mkdir(new File(targetDirectory_));
				
				// set random seed in time_seed.dat
				Writer wr = new FileWriter(tmpDirectory + "time_seed.dat");
				wr.write(new Integer((new Random()).nextInt()).toString()); // could use something better than Random()
				wr.close();
				
				// default working directory is the root directory of the project
				commandLine_ = "./../" + commandLine_;
				Log.info("Running " + commandLine_);
				
				// generate LFR network
				Process process = Runtime.getRuntime().exec(commandLine_, null, tmpDirectoryFile);
				process.waitFor();
				
				// rename and move network
				String rootFilename = targetDirectory_ + "network_" + networkIndex_;
				
				String srcFilename = tmpDirectory + "network.dat";
				String targetFilename = rootFilename + Structure.getFormatExtension(networkFormat_);
				
				FileUtils.deleteQuietly(new File(targetFilename));
				if (networkFormat_ == Structure.Format.TSV)
					FileUtils.moveFile(new File(srcFilename), new File(targetFilename));
				else {
					JmodNetwork network = new JmodNetwork();
					network.read(new File(srcFilename).toURI(), Structure.Format.TSV);
					network.write(new File(targetFilename).toURI(), networkFormat_);
				}
				
				srcFilename = tmpDirectory + "community.dat";
				targetFilename = rootFilename + "_community.dat";
				FileUtils.deleteQuietly(new File(targetFilename));
				FileUtils.moveFile(new File(srcFilename), new File(targetFilename));
				
				srcFilename = tmpDirectory + "statistics.dat";
				targetFilename = rootFilename + "_statistics.dat";
				FileUtils.deleteQuietly(new File(targetFilename));
				FileUtils.moveFile(new File(srcFilename), new File(targetFilename));
				
				srcFilename = tmpDirectory + "time_seed.dat";
				targetFilename = rootFilename + "_time_seed.dat";
				FileUtils.deleteQuietly(new File(targetFilename));
				FileUtils.moveFile(new File(srcFilename), new File(targetFilename));
				
				showProgress();
				
			} catch (Exception e) {
				Log.error("Unable to generate LFR benchmark network.");
				throw e;
			} finally {
				FileUtils.deleteQuietly(tmpDirectoryFile);
			}
			return null;
		}
	}
}
