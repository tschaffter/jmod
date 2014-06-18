package ch.epfl.lis.jmod.benchmarks;

import java.io.File;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.FileUtils;

import com.esotericsoftware.minlog.Log;

import ch.epfl.lis.jmod.Jmod;
import ch.epfl.lis.jmod.JmodNetwork;
import ch.epfl.lis.jmod.JmodSettings;
import ch.epfl.lis.jmod.modularity.community.dividers.CommunityDividerManager;
import ch.epfl.lis.jmod.modularity.community.dividers.GeneticAlgorithmDivider;
import ch.epfl.lis.networks.Edge;
import ch.epfl.lis.networks.Node;
import ch.epfl.lis.networks.Structure;

/**
 * Generates clique ring benchmark networks.<p>
 * 
 * Clique rings are networks that are composed of cliques (sets of nodes fully
 * connected) and where each clique is attached by a single edge to two other
 * cliques in order to form a ring. This benchmark is usually used to illustrate
 * the limitation of modularity optimization-based inference methods.<p>
 * 
 * <b>Input:</b><p>
 * <ul>
 * <li>Path to the benchmark directory (is created)
 * <li>Number of cliques or range of numbers of cliques defined by min, max, and step size values.
 * <li>Size of the cliques or range of clique sizes defined by min, max, and step size values.
 * <li>Output network file format (e.g. TSV, GML, DOT, or Pajek/NET format)
 * </ul><p>
 * 
 * <b>Output:</b><p>
 * 
 * For each set of parameters:<p>
 * <ul>
 * <li>Network file
 * <li>Community structure file ({@link ch.epfl.lis.jmod.inference.community.CommunityStructure#NLINES_FORMAT NLINES_FORMAT})
 * </ul><p>
 * 
 * @version June 19, 2013
 * 
 * @author Thomas Schaffter (thomas.schaff...@gmail.com)
 */
public class CliqueRingBenchmark {
	
	/** Benchmark directory. */
	private String benchmarkDirectory_ = null;
	
	/** Minimum number of cliques (each network has a fixed number of cliques). */
	private int minNumCliques_ = 0;
	/** Maximum number of cliques (each network has a fixed number of cliques). */
	private int maxNumCliques_ = 0;
	/** Step size for the number of cliques. */
	private int numCliquesStep_ = 0;
	
	/** Minimum number of nodes in each clique (all cliques of a network has the same number of nodes). */
	private int minCliqueSize_ = 0;
	/** Maximum number of nodes in each clique (all cliques of a network has the same number of nodes). */
	private int maxCliqueSize_ = 0;
	/** Step size for the number of nodes in each clique. */
	private int cliqueSizeStep_ = 0;
	
	/** Network format (TSV, GML, DOT, Pajek/NET). */
	private Structure.Format networkFormat_ = Structure.Format.TSV;
	
	// ============================================================================
	// PUBLIC METHODS
	
	/** Main method. */
	public static void main(String args[]) {
		
		try {
			CliqueRingBenchmark benchmark = new CliqueRingBenchmark();
			benchmark.setBenchmarkDirectory("/media/data/CliqueRing_test/");
			benchmark.setNumCliques(15);
			benchmark.setCliqueSize(3);
//			benchmark.setNumCliques(10, 20, 2);
//			benchmark.setCliqueSize(3, 9, 3);
//			benchmark.setNetworkFormat(Structure.GML);
			benchmark.generate();
			
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			Log.info("Done");
			System.exit(0);
		}
	}
	
	// ----------------------------------------------------------------------------
	
	/** Default constructor. */
	public CliqueRingBenchmark() {}
	
	// ----------------------------------------------------------------------------
	
	/** Generates a clique ring benchmark with standard file organization. */
	public void generate() throws Exception {
		
		if (minNumCliques_ == 0 || minCliqueSize_ == 0)
			throw new Exception("Minimum number of cliques and clique size must be larger than 0.");
		if (numCliquesStep_ == 0 || cliqueSizeStep_ == 0)
			throw new Exception("Step size for minimum number of cliques and clique size must be larger than 0.");
		
		if (!benchmarkDirectory_.endsWith(File.separator))
			benchmarkDirectory_ += File.separator;
		FileUtils.forceMkdir(new File(benchmarkDirectory_));
		
		JmodNetwork network = null;
		for (int numCliques = minNumCliques_; numCliques <= maxNumCliques_; numCliques += numCliquesStep_) {
			for (int cliqueSize = minCliqueSize_; cliqueSize <= maxCliqueSize_; cliqueSize += cliqueSizeStep_) {
				// generate clique
				network = generateCliqueRing(numCliques, cliqueSize);
				
				// saves network to file
				String networkRootFilename = benchmarkDirectory_ + "cliqueRing_" + numCliques + "_" + cliqueSize;
				String networkFilename = networkRootFilename + Structure.getFormatExtension(networkFormat_);
				network.write(new File(networkFilename).toURI(), networkFormat_);
				
				// saves _community.dat
				FileWriter fw = null;
				try {
					fw = new FileWriter(new File(networkRootFilename + "_community.dat"), false);
					// sort nodes by names
					List<Node> nodes = new ArrayList<Node>(network.getNodes().values());
					Collections.sort(nodes, new Structure.NodeComparator<Node>());
					for (Node node : nodes)
						fw.write(node.getName() + "\t" + node.getCommunityIndex() + System.lineSeparator());
					
				} catch (Exception e) {
					Log.error("Unable to save _community.dat");
					throw e;
				} finally {
					if (fw != null)
						fw.close();
				}
			}
		}
	}
	
	// ----------------------------------------------------------------------------
	
	/** Generates a ring containing numCliques cliques of size c. */
	public static JmodNetwork generateCliqueRing(int numCliques, int c) throws Exception {
		
		Log.info("Generating a ring containing " + numCliques + " cliques of " + c + " nodes.");
		
		JmodNetwork network = new JmodNetwork();
		JmodNetwork clique = null;
		Node lastEdgeTarget = null;
		Node previousCliqueTarget = null;
		
		for (int i = 0; i < numCliques; i++) {
			clique = generateClique(c, getStringSequenceElements(i));
			// set the community index of all the nodes
			for (Map.Entry<String,Node> node : clique.getNodes().entrySet())
				node.getValue().setCommunityIndex(i+1);
			
			network.addStructure(clique);
			Iterator<String> it = clique.getNodes().keySet().iterator();
			Node randomNode = clique.getNode(it.next());
			// select a node for the last intercommunity edge
			if (i == 0)
				lastEdgeTarget = randomNode;
			else
				network.addEdge(new Edge<Node>(randomNode, previousCliqueTarget));
			
			if (i == numCliques-1)
				network.addEdge(new Edge<Node>(randomNode, lastEdgeTarget));
			
			// for next round
			previousCliqueTarget = clique.getNode(it.next());
		}
		
		// summary
		Log.info("Network contains " + network.getSize() + " nodes and " + network.getNumEdges() + " edges.");
		
		return network;
	}
	
	// ----------------------------------------------------------------------------
	
	/** Generates a c-node clique whose node names start with the given string. */
	public static JmodNetwork generateClique(int c, String nodeRootName) throws Exception {
		
		JmodNetwork network = new JmodNetwork();
		network.setName("clique_" + nodeRootName);
		for (int i = 0; i < c; i++)
			network.addNode(new Node(nodeRootName + i));
		
		List<Node> nodes = new ArrayList<Node>(network.getNodes().values());
		for (int i = 0; i < c-1; i++) {
			for (int j = i+1; j < c; j++) {
				network.addEdge(new Edge<Node>(nodes.get(i), nodes.get(j)));
			}
		}
		return network;
	}
	
	// ----------------------------------------------------------------------------
	
	/** Runs Jmod to reverse engineer clique ring network modules. */
	public static void reverseEngineerCliqueBenchmark() throws Exception {
		
		JmodSettings settings = JmodSettings.getInstance();
		
		String splitMethod = "GA"; // GA
		String experimentId = splitMethod;
		
		// benchmark settings
		String workingDirectory = "/home/tschaffter/devel/java/QLimit/cliqueRing_benchmark/";
		Structure.Format networkFormat = Structure.Format.GML;
		int[] numCliques = {10, 20, 30, 40, 50, 60, 70, 80, 90, 100};
		int[] cliqueSizes = {3, 5, 7, 9};
		//int numRuns = 20;
		
		// module detection method
		CommunityDividerManager communityManager = CommunityDividerManager.getInstance();
		communityManager.setSelectedDivider(splitMethod);
		
		// GA settings
		GeneticAlgorithmDivider gaDivider = (GeneticAlgorithmDivider)communityManager.getDivider("GA");
		gaDivider.setNumGenerations(2000);
		gaDivider.setNumGaRunsPerCommunityDivision(1);
		
		// refinement settings
		settings.setUseMovingVertex(true);
		settings.setUseGlobalMovingVertex(true);
		
		// log level
		JmodSettings.setLogLevel("INFO");
		
		// output datasets
		settings.setExportBasicDataset(true);
		settings.setExportCommunityNetworks(false);
		settings.setCommunityNetworkFormat(Structure.Format.TSV);
		settings.setExportColoredCommunities(false);
		settings.setColoredCommunitiesNetworkFormat(Structure.Format.GML);
		settings.setExportCommunityTree(true);
		settings.setExportSnapshots(true);
		
		String rootOutputDirectory = workingDirectory + experimentId + File.separator;
		FileUtils.forceMkdir(new File(rootOutputDirectory));
		
		JmodNetwork network = null;
		Jmod jmod = null;
		for (int i = 0; i < numCliques.length; i++) {
			for (int j = 0; j < cliqueSizes.length; j++) {
				for (int k = 50; k < 60; k++) {
					// loads network
					String networkRootFilename = workingDirectory + "cliqueRing_" + numCliques[i] + "_" + cliqueSizes[j];
					String inputNetworkFilename = networkRootFilename + Structure.getFormatExtension(networkFormat);
					Log.info("Reverse engineering " + inputNetworkFilename);
					network = new JmodNetwork();
					network.read(new File(inputNetworkFilename).toURI(), networkFormat);
					
					// create output run directory
					String outputDirectory = rootOutputDirectory + "cliqueRing_" + numCliques[i] + "_" + cliqueSizes[j] + "_run_" + k + "/";
					FileUtils.forceMkdir(new File(outputDirectory));

					// network module detection
					jmod = new Jmod();
					jmod.setInputNetworksRegex(inputNetworkFilename);
					jmod.setInputNetworksFormat(networkFormat);
					jmod.setOutputDirectory(new File(outputDirectory).toURI());
					// run(): one network processed at a time
					// execute(): process networks in //
					jmod.run();
				}
			}
		}
	}
	
    // =======================================================================================
    // PRIVATE METHODS
	
	/** Returns an element from the sequence a, b, c, ..., aa, ab, ..., ba, bb, etc. */
	private static String getStringSequenceElements(int num) {
		if (num < 26)
			return String.valueOf((char)('a' + num));
		else {
			num -= 26;
			return String.valueOf((char)('a' + num / 26)) + String.valueOf((char)('a' + num % 26));
		}
	}
	
	// ============================================================================
	// GETTERS AND SETTERS
	
	public void setBenchmarkDirectory(String directory) { benchmarkDirectory_ = directory; }
	public void setNumCliques(int numCliques) { minNumCliques_ = maxNumCliques_ = numCliquesStep_ = numCliques; }
	public void setCliqueSize(int cliqueSize) { minCliqueSize_ = maxCliqueSize_ = cliqueSizeStep_ = cliqueSize; }
	public void setNumCliques(int min, int max, int step) { minNumCliques_ = min; maxNumCliques_ = max; numCliquesStep_ = step; }
	public void setCliqueSize(int min, int max, int step) { minCliqueSize_ = min; maxCliqueSize_ = max; cliqueSizeStep_ = step; }
	public void setNetworkFormat(Structure.Format format) { networkFormat_ = format; }
}
