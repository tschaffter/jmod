/*
Copyright (c) 2008-2013 Thomas Schaffter, Daniel Marbach

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

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.net.URI;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import no.uib.cipr.matrix.DenseVector;

import org.apache.commons.cli.Options;
import org.apache.commons.io.FilenameUtils;

import com.esotericsoftware.minlog.Log;

import ch.epfl.lis.jmod.JmodException;
import ch.epfl.lis.jmod.JmodNetwork;
import ch.epfl.lis.jmod.JmodSettings;
import ch.epfl.lis.jmod.modularity.ModularityDetector;
import ch.epfl.lis.jmod.modularity.community.Community;
import ch.epfl.lis.jmod.modularity.community.RootCommunity;
import ch.epfl.lis.networks.Structure;
import ch.epfl.lis.networks.parsers.TSVParser;
import ch.tschaffter.utils.TimeUtils;

/**
 * Computes the modularity Q of the given partition of a network.<p>
 * 
 * Unlike the other methods to split communities in two subcommunities,
 * this method requires the nodes of the given network to have their field
 * communityIndex_ set with the index of the community they belong to.
 * If the file NETWORK_community.dat is at the same location than the network
 * file NETWORK.ext, Jmod loads it and set communityIndex_. The community
 * file contains one line per node and each line contains the name of the
 * node and the index of the community separated by a tab.<p>
 * 
 * As performed by the other "split" method, here we take all the communities
 * and split them into two groups. Each group is then divided in two and so on
 * until each community is in its own group in order to compute the modularity
 * Q of the network. Two methods are implemented to group communities. However,
 * each of them returns the same modularity Q (only one method is available to
 * the user).<p>
 *
 * <ul>
 * <li><b>Split the communities equally:</b> For each split, the C communities to
 * group are distributed evenly between the two groups.
 * <li><b>Apply a hierarchical clustering algorithm (for experiments):</b> 
 * For each split, the number of edges between each communities is measured
 * before creating a distance matrix. A hierarchical clustering algorithm is 
 * then applied to split communities in two groups.
 * </ul>
 * 
 * TODO: Use the method implemented in ModularityDetector.computeModularity(partition)
 * instead of splitting communities in two groups of about equal size (both methods
 * provide the same result).<p>
 * 
 * TODO: There is a bug in the hierarchical clustering algorithm (last community
 * not split?). Anyway, the method is now deprecated.
 *
 * @see NewmanSpectralAlgorithmDivider
 * @see GeneticAlgorithmDivider
 * @see BruteForceDivider
 *
 * @version June 19, 2013
 * 
 * @author Thomas Schaffter (firstname.name@gmail.com)
 */
public class KnownModulesDivider extends CommunityDivider  {
	
	/** Maximum number of communities accepted. */
	public static final int BRUTEFORCE_MAX_NUM_COMMUNITIES = 58; // see BruteForceDividerMultiThreads
	
	/** Value of the parameter --numproc to indicate that all processors available must be used. */
	public static final String USE_NUM_PROC_AVAILABLE = "MAX";
	
	/** Places half of the communities in one group and the rest in the second group. */
	public static final int SPLIT_METHOD_HALF = 0;
	/** Runs hierarchical clustering algorithm to cluster communities in two groups based on the number of edges they share with each other. */
	public static final int SPLIT_METHOD_CLUSTERING = 1;
	/** Uses brute force selection method to group communities. */
	public static final int SPLIT_METHOD_BRUTEFORCE = 2;
	
	/** Number of workers submitted per batch. */
	public static final int NUM_WORKERS_PER_BATCH = 500;
	/** Number of solutions evaluated in each thread. */
	public static final int NUM_EVALUATIONS_PER_THREAD = 1000;
	
	/** Default split method. */
	protected int splitMethod_ = SPLIT_METHOD_HALF;
	/** Number of concurrent workers in the pool which should not exceed the number of processors. */
	protected int numProc_ = Runtime.getRuntime().availableProcessors();
	
	/** Reference to the modularity detector. */
	protected ModularityDetector modDetector_ = null;
	/** Name of the network being processed. */
	protected String networkName_ = null;
	
	/** Current best split vector s. */
	protected DenseVector bestS_ = null;
	/** Modularity Q of current best split vector s. */
	protected double bestQ_ = -1.;
	/** Evaluation counter. */
	protected long counter_ = 0L;
	
	/** Size of the community being split. */
	protected int N_ = 0;
	/** Number of communities which is known in advance (stored in node graphics label). */
	protected int numCommunities_ = 0;
	/** Number of community split vectors to evaluate. */
	protected long numEvals_ = 0L;
	/** Contains the community index of each node. */
	protected List<Integer> communityIndexes_ = null;
	/** Lookup table that provides the indexes of the "community indexes" (its size is given by the number of communities). */
	protected List<Integer> communityIndexesLUT_ = null;
	
	/** Index of the next community split vector to evaluate. */
	protected static long splitVectorSIndex_ = 0L;
	
    // =======================================================================================
    // PROTECTED METHODS
	
	@Override
	protected void buildOptions() {
		
		// create Options object
		options_ = new Options();
		
		// SHORT AND LONG OPTIONS FLAGS MUST BE DIFFERENT FROM THOSE OF JMOD
		
//		options_.addOption(OptionBuilder.withValueSeparator()
//				.withLongOpt("splitMethod")
//				.withDescription("Method to split communities in two groups (0=split in two groups of equal size, 1=hierarchical clustering, default: " + SPLIT_METHOD_CLUSTERING + ").")
//				.hasArgs()
//				.withArgName("NUM")
//				.create());
		
//		options_.addOption(OptionBuilder.withValueSeparator()
//				.withLongOpt("numproc")
//				.withDescription("Use the given number of processors (max: " + Runtime.getRuntime().availableProcessors() + ", specify MAX to use all the processors available, default: MAX).")
//				.hasArgs()
//				.withArgName("NUM")
//				.create());
	}
	
	// ----------------------------------------------------------------------------
	
	@Override
	protected void parseOptions(String args[]) throws JmodException, Exception {
		
//		// parse options
//		CommandLineParser parser = new PosixParser();
//		CommandLine cmd = null;
//		try {
//			cmd = parser.parse(options_, args);
//			
//			if (cmd.hasOption("splitMethod")) {
//				splitMethod_ = Integer.parseInt(cmd.getOptionValue("splitMethod"));
//			}
//
//			if (cmd.hasOption("numproc")) {
//				String valueStr = cmd.getOptionValue("numproc");
//				int value = 0;
//				int numProcAvail = Runtime.getRuntime().availableProcessors();
//				
//				if (valueStr.compareTo(USE_NUM_PROC_AVAILABLE) == 0)
//					numProc_ = numProcAvail;
//				else if ((value = Integer.parseInt(valueStr)) < 1) {
//					Log.info(identifier_, "At least one processor is required. Using now one processor.");
//					numProc_ = value;
//				} else if ((value = Integer.parseInt(valueStr)) > numProcAvail) {
//					Log.info(identifier_, "Number of processors available is " + numProcAvail + ". Using now all the processors available.");
//					numProc_ = numProcAvail;
//				} else
//					numProc_ = value;
//			}
//		
//		} catch (UnrecognizedOptionException e) {
//			Log.error(identifier_, e.getMessage());
//			printHelp();
//			throw new JmodException(e.getMessage());
//		} catch (Exception e) {
//			Log.error(identifier_, "Could not successfully recognized the options.", e);
//			printHelp();
//			throw new JmodException(e.getMessage());
//		}
	}
	
	// ----------------------------------------------------------------------------
	
	/** Returns a vector that says how to group the communities. */
	protected void generateCommunitySplitVector(boolean[] communitySplitVector, int N, long numEvals, long i) {
		
		for(int j = 0; j < N; j++) {
            long val = numEvals * j + i;
            long ret = (1 & (val >>> j));
            communitySplitVector[j] = ret != 0; //? 1 : -1;
        }
	}
	
	// ----------------------------------------------------------------------------
	
	/** Updates the best split vector s if the given solution is better (brute force method). */
	protected synchronized void submitBestSplitVectorS(DenseVector s, double bestQ, long bestIndex) {
		
		if (bestQ > bestQ_) {
			bestS_.set(s);
			bestQ_ = bestQ;
			
			double percent = Math.min((100. * bestIndex) / numEvals_, 100.);
			Log.debug(networkName_ + "|" + identifier_, "Best Q = " + bestQ_ + " for i = " + bestIndex + " (" + new DecimalFormat("#.##").format(percent) + "%)");

			// Saves the current state of the module detection.
			// Don't forget to set modDetector_.currentSubcommunityS_ or the snapshot
			// will not see the change in differential mode.
			modDetector_.currentSubcommunityS_.set(bestS_);
			modDetector_.takeSnapshot(identifier_);
		}
	}
	
    // =======================================================================================
    // PUBLIC METHODS
	
	/** Constructor. */
	public KnownModulesDivider() {
		
		super();
		
		// builds the options of the methods
		buildOptions();

		identifier_ = "KnownModules"; // without empty space
		name_ = "Known modules"; // any name
		// description in HTML format, use "<br>" as line break
		// no more than about 70 chars per line
		description_ = "<html>Computes the modularity Q of networks where modules are known.<br>" +
							 "This method can typically only be applied to benchmark networks<br>" +
							 "such as LFR graphs where the composition of each community is known,<br>" +
							 "but this method can also be used to compute the modularity Q of a<br>" +
							 "network partition produced by a third-party software.<br><br>" +
							 "The identities of the modules must be provided to Jmod in a TSV<br>" +
							 "file named NETWORK_NAME_community.dat where lines are formatted as<br>" +
							 "following: NODE_NAME \\tab COMMUNITY_INDEX<br><br>" +
							 "Note that refinement methods such as MVM and gMVM would modify the<br>" +
							 "composition of the communities, so unless desired, they should<br>" +
							 "be disabled.</html>";
	}
	
	// ----------------------------------------------------------------------------
	
	public KnownModulesDivider(KnownModulesDivider divider) {
		
		super(divider);

		splitMethod_ = divider.splitMethod_;
		numProc_ = divider.numProc_;
	}
	
	// ----------------------------------------------------------------------------
	
	/** Copy operator. */
	@Override
	public KnownModulesDivider copy() {
		
		return new KnownModulesDivider(this);
	}
	
	// ----------------------------------------------------------------------------
	
	@Override
	public void divide(ModularityDetector modDetector) throws JmodException, Exception {
		
		JmodSettings settings = JmodSettings.getInstance();
		
		modDetector_ = modDetector;
		networkName_ = FilenameUtils.getBaseName(modDetector.getNetwork().getName());
		N_ = modDetector.currentSubcommunitySize_;
		
		if (settings.getUseMovingVertex() || settings.getUseGlobalMovingVertex())
			Log.warn(networkName_ + "|" + identifier_, "There are refinement methods enabled that can modify the given network modules.");
		
		// gets the nodes and the number of effective communities
		Community community = modDetector.currentCommunityBeingSplit_;
		DenseVector nodeIndexes = community.getVertexIndexes();
		
		communityIndexes_ = new ArrayList<Integer>(); // for each node, the index of the community it belongs to
		communityIndexesLUT_ = new ArrayList<Integer>();
		numCommunities_ = 0;

		int communityIndex = -1;
		for (int i = 0; i < N_; i++) {
			communityIndex = modDetector.getNetwork().getNode((int)nodeIndexes.get(i)).getCommunityIndex();
			
			if (!communityIndexesLUT_.contains(communityIndex)) {
				communityIndexesLUT_.add(communityIndex);
				numCommunities_++;
			}
			communityIndexes_.add(communityIndex);
		}
		
		Log.debug(networkName_ + "|" + identifier_, "Number of communities: " + numCommunities_);
		Log.debug(networkName_ + "|" + identifier_, "Options: " + getOptionsStr());
		
		if (N_ == modDetector_.getNetwork().getSize() && numCommunities_ == 1)
			Log.warn(networkName_ + "|" + identifier_, "This network has only one community (Q=0). Are you sure that the community file has been provided?");
		
		if (numCommunities_ == 1) {
//			modDetector_.getCurrentSubcommunityS().assign(1); // places all nodes in the same community
			ModularityDetector.assign(modDetector_.getCurrentSubcommunityS(), 1);
			
		} else if (splitMethod_ == SPLIT_METHOD_HALF) {
			
			Log.debug(networkName_ + "|" + identifier_, "Clustering communities randomly.");
			boolean[] communitySplitVector = new boolean[numCommunities_];
			int groupSize = (int) (numCommunities_/2.);
			for (int i = 0; i < communitySplitVector.length; i++)
				communitySplitVector[i] = (i < groupSize);
//				communitySplitVector[i] = (i%2 == 1);
			
			// translates community split vector to split vector s
			DenseVector s = modDetector_.getCurrentSubcommunityS();
			for (int j = 0; j < N_; j++)
				s.set(j, communitySplitVector[communityIndexesLUT_.indexOf(communityIndexes_.get(j))] ? 1 : -1); // that's the s vector returned
			
		} else if (splitMethod_ == SPLIT_METHOD_BRUTEFORCE) {
			
			Log.debug(networkName_ + "|" + identifier_, "Clustering communities using brute force method.");
			
			// long -> 64 bits -> 65 nodes
			// but due to the implementation -> 2^63 (max) * N => 2^(N-1) * N +N < 2^63 (see generateSplitVector()) => limit is N = 58
			if (numCommunities_ > BRUTEFORCE_MAX_NUM_COMMUNITIES)
				throw new JmodException("The number of communities is too large to be clustered using the brute force approach (" + numCommunities_ + " > Number of communities max = " + BRUTEFORCE_MAX_NUM_COMMUNITIES + ").");
			
			// a brute force approach is performed to find the organization of the communities
			// in two groups that maximized the modularity Q.
			numEvals_ = (long)Math.pow(2, numCommunities_-1);
			Log.info(networkName_ + "|" + identifier_, "Total number of community split vectors to evaluate: " + numEvals_);
			
			// don't forget to initialize as this method is used several times
//			bestS_ = new DenseDoubleMatrix1D(N_);
//			bestS_.assign(-1);
			bestS_ = new DenseVector(N_);
			ModularityDetector.assign(bestS_, -1);
			bestQ_ = -1.;
			
			ExecutorService executor = null;
			try {
				// use a thread pool to limit the number of concurrent s vector evaluations
				executor = Executors.newFixedThreadPool(numProc_);
				counter_ = 0L;
				while (counter_ < numEvals_) {
					
					List<CommunitySplitVectorEvaluationWorker> workers = new ArrayList<CommunitySplitVectorEvaluationWorker>();
					List<Future<Void>> results = null; // references to the results
					
					// create workers
					CommunitySplitVectorEvaluationWorker worker = null;
					for (int i = 0; i < NUM_WORKERS_PER_BATCH; i++) {
						if (counter_ >= numEvals_) // no need to create more workers
							break;
						
						worker = new CommunitySplitVectorEvaluationWorker(counter_);
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
			modDetector.currentSubcommunityS_.set(bestS_);
			modDetector.currentSubcommunityQ_ = bestQ_;
			
		} else if (splitMethod_ == SPLIT_METHOD_CLUSTERING) {
			
			throw new Exception("SPLIT_METHOD_CLUSTERING is not available anymore.");
			
//			Log.debug(networkName_ + "|" + identifier_, "Clustering communities using hierarchical clustering method.");
//			
//			// builds the distance matrix: computes the number of edges shared between any pair
//			// of communities and use 1/numEdges so that two communities that have many shared
//			// connections are defined as "close"
//			double[][] D = new double[numCommunities_][numCommunities_];
//			List<Edge<Node>> edges = modDetector_.getNetwork().getEdges();
//			int community1Index = 0;
//			int community2Index = 0;
//			int a, b;
//			for (int i = 0; i < edges.size(); i++) {
//				community1Index = edges.get(i).getSource().getCommunityIndex();
//				community2Index = edges.get(i).getTarget().getCommunityIndex();
//				
//				// consider only if both indexes are in the current set of communities to split in two
//				if (!communityIndexesLUT_.contains(community1Index) || !communityIndexesLUT_.contains(community2Index))
//					continue;
//				
//				a = communityIndexesLUT_.indexOf(community1Index);
//				b = communityIndexesLUT_.indexOf(community2Index);
//				D[a][b]++;
//				D[b][a]++;
//			}
//			
//			// the more connections between two modules the closer they are
//			for (int i = 0; i < numCommunities_; i++){
//				for (int j = 0; j < numCommunities_; j++) {
//					D[i][j] = 1/D[i][j];
//					D[j][i] = 1/D[j][i];
//				}
//			}
//			// list of cluster names
//			String[] names = new String[numCommunities_];
//			for (int i = 0; i < numCommunities_; i++)
//				names[i] = "" + i; // not the index of the community but directly the index in the community split vector
//			
//			// runs clustering on the distance matrix
//			ClusteringAlgorithm alg = new DefaultClusteringAlgorithm();
//			Cluster cluster = alg.performClustering(D, names, new AverageLinkageStrategy());
//			
//			// gets the result as strings where cluster names are separated with &
//			// working one only one of the two clusters is enough
//			List<Cluster> children = cluster.getChildren();
//			int firstClusterSize = children.get(0).countLeafs();
//			if (firstClusterSize == 0 || firstClusterSize == numCommunities_)
//				throw new Exception("The clustering failed to cluster communities in two groups.");
//			
//			// generate the community split vector
//			boolean[] communitySplitVector = new boolean[numCommunities_]; // initialized with false
//			String[] communitiesNames = children.get(0).getName().split("&");
//			for (int i = 0; i < communitiesNames.length; i++)
//				communitySplitVector[Integer.parseInt(communitiesNames[i])] = true;
//			
//			// translates community split vector to split vector s
//			DoubleMatrix1D s = modDetector_.getCurrentSubcommunityS();
//			for (int j = 0; j < N_; j++)
//				s.set(j, communitySplitVector[communityIndexesLUT_.indexOf(communityIndexes_.get(j))] ? 1 : -1); // that's the s vector returned
			
		} else
			throw new Exception("Unknown split method for splitting communities in two groups.");
	}
	
	// ----------------------------------------------------------------------------
	
	/** Run method. */
	public static void run() throws Exception {
		
		String benchmarkDirectory = "/home/tschaffter/devel/java/imod-java/benchmarks/LFR/";
		int N = 1000;
		String[] communitySizes = {"small"};
		double[] mus = {0.1, 0.15, 0.2, 0.25, 0.3, 0.35, 0.4, 0.45, 0.5, 0.55, 0.6, 0.65, 0.7, 0.75, 0.8, 0.85, 0.9}; // {0.1}; // do 0.75
		int firstNetworkIndex = 1;
		int lastNetworkIndex = 100;
		Structure.Format networkFormat = Structure.Format.TSV;
		
		int counter = 0;
		int counterThreshold = 20;
		
		for (int communitySizeIndex = 0; communitySizeIndex < communitySizes.length; communitySizeIndex++) {
			for (int muIndex = 0; muIndex < mus.length; muIndex++) {
				for (int networkIndex = firstNetworkIndex; networkIndex <= lastNetworkIndex && counter < counterThreshold; networkIndex++) {
					try {
						processLFRNetwork(benchmarkDirectory, N, communitySizes[communitySizeIndex], mus[muIndex], networkIndex, networkFormat);
						counter++;
					} catch (Exception e) {
						Log.info("Network skipped: " + e.getMessage());
					}
				}
				counter = 0;
			}
		}
	}
	
	// ----------------------------------------------------------------------------
	
	/** Computes the modularity of one LFR benchmark graph. */
	public static void processLFRNetwork(String benchmarkDirectory, int N, String communitySize, double mu, int networkIndex, Structure.Format networkFormat) throws Exception {
		
		// for computing runtime
		long t0 = System.currentTimeMillis();
		
		// folder containing the network file, the community file, and where the modularity Q will be exported
		String workingDirectory = benchmarkDirectory + "N" + N + "/" + communitySize + "/mu" + new DecimalFormat("#").format(100*mu) + "/";
		
		// reads the network
		// loads the benchmark network
		String filename = workingDirectory + "network_" + networkIndex + ".tsv";
		JmodNetwork network = new JmodNetwork();
		network.read(new File(filename).toURI(), networkFormat);
		
		// loads the identity of the modules and set the node graphic parameter "label" with the community index
		filename = workingDirectory + "network_" + networkIndex + "_community.dat";
		ArrayList<String[]> data = TSVParser.readTSV(new File(filename).toURI());
		// first column is the name of the node, the second is the indexes of the communities
		for (int i = 0; i < data.size(); i++)
			network.getNode(data.get(i)[0].replace(" ", "")).setCommunityIndex(Integer.parseInt(data.get(i)[1].replace(" ", "")));
		
		// sets the community divider
		int dividerIndex = CommunityDividerManager.getInstance().addDivider(new KnownModulesDivider());
		CommunityDividerManager.getInstance().setSelectedDividerIndex(dividerIndex);
		
		// run the module detection
		ModularityDetector modularityDetector = new ModularityDetector(network);
		RootCommunity rootCommunity = new RootCommunity(modularityDetector, network);
		
		// saves snapshot
//		String outputDirectory = "/home/tschaffter/A_LFR_Q/N1000/big/";
//		String rawFilename = Structure.removeExtension(modularityDetector.getNetwork().getName());
//		ModularityDetectorSnapshot snapshot = new ModularityDetectorSnapshot(modularityDetector,
//				ModularityDetectorSnapshot.EXPORT_FULL_SNAPSHOTS,
//				outputDirectory + rawFilename + "_mu" + new DecimalFormat("#").format(100*mu));
//		modularityDetector.setSnapshot(snapshot);
		
		rootCommunity.runModularityDetection(true);
		
		Log.info(network.getName(), "Q = " + modularityDetector.getModularity());
		
		// export the modularity to file
		String outputModularityFilename = workingDirectory + "network_" + networkIndex + "_Q_cluster.txt";
		exportModularity(modularityDetector, new File(outputModularityFilename).toURI());
		
		// prints runtime
		Log.info(network.getName(), TimeUtils.formatTime(System.currentTimeMillis() - t0));
	}
	
	// ----------------------------------------------------------------------------
	
	/** Saves modularity to file. */
	public static void exportModularity(ModularityDetector modDetector, URI uri) throws Exception {
		
		FileWriter fstream = null;
		BufferedWriter out = null;
		try {
			Log.info(modDetector.getNetwork().getName(), "Writing network modularity Q " + uri.getPath());
			Double Q = modDetector.getModularity();
			fstream = new FileWriter(new File(uri));
			out = new BufferedWriter(fstream);
			out.write(Q.toString());

		} catch (Exception e) {
			Log.error("Unable to save the modularity Q to " + uri.getPath());
			throw e;
		} finally {
			if (out != null)
				out.close();
		}
	}
	
	// ----------------------------------------------------------------------------
	
	/** Main method. */
	public static void main(String args[]) {
		
		try {
			long t0 = System.currentTimeMillis();
			
			JmodSettings.getInstance().setUseMovingVertex(false);
			JmodSettings.getInstance().setUseGlobalMovingVertex(false);
			JmodSettings.getInstance().setExportSnapshots(true);
			
			KnownModulesDivider.run();
			
			Log.info(TimeUtils.formatTime(System.currentTimeMillis() - t0));

		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			Log.info("Done");
			System.exit(0);
		}
	}
	
	// ----------------------------------------------------------------------------
	
	@Override
	public String getOptionsStr() {
		
		String optionsStr = "";
		
//		optionsStr += "--splitMethod " + splitMethod_;
//		optionsStr += " --numproc ";
//		if (numProc_ == Runtime.getRuntime().availableProcessors())
//			optionsStr += "MAX";
//		else
//			optionsStr += "" + numProc_;
		
		return optionsStr;
	}
	
    // =======================================================================================
    // GETTERS AND SETTERS
	
	public void setNumProc(int numProc) { numProc_ = numProc; }
	
	public void setSplitMethod(int splitMethod) { splitMethod_ = splitMethod; }
	
    // =======================================================================================
    // INNER CLASSES
	
	/** Evaluates a batch of community split vectors (brute force method). */
	private class CommunitySplitVectorEvaluationWorker implements Callable<Void> {
		
		/** Index of the first community split vector to evaluate. */
		private long firstCommunitySplitVectorIndex_ = 0;
		
		/** Constructor. */
		public CommunitySplitVectorEvaluationWorker(long firstCommunitySplitVectorIndex) {
			
			firstCommunitySplitVectorIndex_ = firstCommunitySplitVectorIndex;
		}
		
		/** Evaluates a batch of split vectors. */
		@Override
		public Void call() throws Exception {
			
			DenseVector bestS = new DenseVector(N_);
			double bestQ = -1.;
			
			// do not evaluate more solutions after the last one
			long maxIters = Math.min(firstCommunitySplitVectorIndex_ + NUM_EVALUATIONS_PER_THREAD, numEvals_);
			// index associated to the best s vector found
			long bestIndex = 0L;
			
			DenseVector s = new DenseVector(N_);
			boolean[] communitySplitVector = new boolean[numCommunities_];
			double Q = 0.;
			for (long i = firstCommunitySplitVectorIndex_; i < maxIters; i++) {
				generateCommunitySplitVector(communitySplitVector, numCommunities_, numEvals_, i); // set s	

				// translates community split vector to split vector s
				for (int j = 0; j < N_; j++)
					s.set(j, communitySplitVector[communityIndexesLUT_.indexOf(communityIndexes_.get(j))] ? 1 : -1);
				
				Q = modDetector_.computeModularity(s);
				
				if (Q > bestQ) {
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
