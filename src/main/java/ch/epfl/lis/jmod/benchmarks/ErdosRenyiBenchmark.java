package ch.epfl.lis.jmod.benchmarks;

import java.io.File;
import java.io.FileWriter;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.apache.commons.io.FileUtils;

import ch.epfl.lis.networks.Edge;
import ch.epfl.lis.networks.EdgeFactory;
import ch.epfl.lis.networks.ErdosRenyiNetwork;
import ch.epfl.lis.networks.Node;
import ch.epfl.lis.networks.NodeFactory;
import ch.epfl.lis.networks.Structure;

import com.esotericsoftware.minlog.Log;

/** 
 * Generates Erdos-Renyi random graphs using multiple threads.<p>
 * 
 * Here Erdos-Renyi model G(n,p) is used by default. The probability p that two
 * nodes shaed an edge is defined as:<br>
 * <pre>
 * &lt;k&gt; = p(N-1)
 * </pre>
 * 
 * where N is the number of nodes and k is the average node degree.<p>
 * 
 * The model G(n,m) where m is a fixed number of edges is also implemented.<p>
 * 
 * <b>Input:</b><p>
 * <ul>
 * <li>Path to the benchmark directory (is created)
 * <li>Network size N
 * <li>Average node degree k or range of average node degrees defined by mink, maxk, and kStep
 * <li>Number of repetitions
 * <li>Output network file format (e.g. TSV, GML, DOT, or Pajek/NET format)
 * </ul><p>
 * 
 * <b>Output:</b><p>
 * 
 * For each set of parameters:<p>
 * <ul>
 * <li>Network file
 * <li>Community structure file ({@link ch.epfl.lis.jmod.inference.community.CommunityStructure#NLINES_FORMAT NLINES_FORMAT}; here all nodes belong to the same community)
 * </ul><p>
 * 
 * <b>References:</b><p>
 * 
 * P Erdos, A Renyi. <a href="http://ftp.math-inst.hu/~p_erdos/1959-11.pdf" taget="_blank">
 * On random graphs I.</a>. <i>Publications Matheaticae</i>, 6:290-297, 1959.<br>
 * 
 * P Erdos, A Renyi. <a href="http://leonidzhukov.ru/hse/2010/stochmod/papers/erdos-1960-10.pdf" 
 * target="_blank">On the evolution of random graphs</a>.<i>Publ. Math. Inst. Hungar. Acad. Sci</i>, 
 * 5:17-61, 1960.<br>
 * 
 * EN Gilber. <a href="http://www.jstor.org/discover/10.2307/2237458?uid=3737760&uid=2&uid=4&sid=21102891417171" 
 * target="_blank">Random graphs</a>. <i>The Annals of Mathematical Statistics</i>, 30(4):1141-1144, 1959.
 * 
 * @see ErdosRenyiNetwork
 * @see Structure
 * 
 * @version January 15, 2012
 * 
 * @author Thomas Schaffter (thomas.schaff...@gmail.com)
 */
public class ErdosRenyiBenchmark {
	
	/** Maximum number of concurrent threads (= pool size). */
	private int numConcurrentThreads_ = Runtime.getRuntime().availableProcessors();
	
	/** Benchmark directory. */
	private String benchmarkDirectory_ = null;
	/** Number of nodes N. */
	private int N_ = 1000;
	
	/** Minimum average node degree k. */
	private int mink_ = 0;
	/** Maximum average node degree k. */
	private int maxk_ = 0;
	/** Step size for the average node degree k. */
	private int kStep_ = 0;
	
	/** Number of network repetitions. */
	private int numRepetitions_ = 2;
	
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
		
		try {
			ErdosRenyiBenchmark benchmark = new ErdosRenyiBenchmark();
			benchmark.setBenchmarkDirectory("/media/data/ER_test/");
			benchmark.setN(1000);
			benchmark.setK(10);
//			benchmark.setK(10, 100, 10);
			benchmark.setNumRepetitions(20);
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
	public ErdosRenyiBenchmark() {}
	
	// ----------------------------------------------------------------------------
	
	/** Generates a random Erdos-Renyi benchmark with standard file organization. */
	public void generate() throws Exception {
		
		if (mink_ == 0 || kStep_ == 0)
			throw new Exception("Minimum average node degree and average node degree step size must be larger than 0.");
		
		// use a thread pool to limit the number of dot graph processed at a time
		ExecutorService executor = Executors.newFixedThreadPool(numConcurrentThreads_);
		List<Worker> workers = new ArrayList<Worker>();
		List<Future<Void>> results = null;
		
		if (!benchmarkDirectory_.endsWith(File.separator))
			benchmarkDirectory_ += File.separator;
		
		String nDirectory = benchmarkDirectory_ + "N" + N_ + File.separator;
		
		for (int k = mink_; k <= maxk_; k += kStep_) {
			String kDirectory = nDirectory + "k" + k + File.separator;
			for (int i = 1; i < numRepetitions_; i++)
				workers.add(new Worker(k, kDirectory, i));
		}
		
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
			Log.error("Unable to generate Erdos-Renyi benchmark.");
			throw e;
		} finally {
			executor = null;
			workers = null;
			results = null;
		}
	}
	
	// ============================================================================
	// PRIVATE METHODS
	
	/** Shows the percentage of workers completed. */
	private synchronized void showProgress() {
		
		numWorkersDone_++;
		double percent = (100. * numWorkersDone_) / numWorkers_;
		Log.info(new DecimalFormat("#.00").format(percent) + "%");	
	}
	
	// ============================================================================
	// GETTERS AND SETTERS
	
	public void setBenchmarkDirectory(String directory) { benchmarkDirectory_ = directory; }
	public void setN(int N) { N_ = N; }
	public void setK(int k) { mink_ = maxk_ = kStep_ = k; }
	public void setK(int mink, int maxk, int kStep) { mink_ = mink; maxk_ = maxk; kStep_ = kStep; }
	public void setNumRepetitions(int numRepetitions) { numRepetitions_ = numRepetitions; }
	public void setNetworkFormat(Structure.Format format) { networkFormat_ = format; }
	
	/** Synchronized method to avoid issue with workers trying to create the same directory at the same time. */
	public synchronized void mkdir(File directoryFile) throws Exception { FileUtils.forceMkdir(directoryFile); }
	
	// ============================================================================
	// INNER CLASSES
	
	/** Generates one Erdos-Renyi random graph. */
	private class Worker implements Callable<Void> {
		
		/** Average node degree k. */
		private int k_ = 0;
		/** Network index. */
		private int networkIndex_ = 0;
		/** Target directory. */
		private String targetDirectory_ = null;
		
		// ============================================================================
		// PUBLIC METHODS
		
		/** Constructor. */
		public Worker(int k, String targetDirectory, int networkIndex) {
			
			k_ = k;
			targetDirectory_ = targetDirectory;
			networkIndex_ = networkIndex;
		}
		
		// ----------------------------------------------------------------------------
		
		@Override
		public Void call() throws Exception {
			
			try {
				mkdir(new File(targetDirectory_));
				
				// instantiate an Erdos-Renyi random network model
				NodeFactory<Node> nodeFactory = new NodeFactory<Node>(new Node());
				EdgeFactory<Edge<Node>> edgeFactory = new EdgeFactory<Edge<Node>>(new Edge<Node>());
				ErdosRenyiNetwork<Node, Edge<Node>> network = new ErdosRenyiNetwork<Node, Edge<Node>>(nodeFactory, edgeFactory);
				network.setNodes(N_);
				
				// generate network
				double p = k_ / (double)(N_-1); // <k> = p(N-1)
				network.generateteGnp(p, false);
				
				// save network
				String networkRootFilename = targetDirectory_ + "network_" + networkIndex_;
				String networkFilename = networkRootFilename + Structure.getFormatExtension(networkFormat_);
				network.write(new File(networkFilename).toURI(), networkFormat_);
				
				// saves _community.dat
				// Even if random fluctuations may have generated "communities",
				// the point here is that all the node are expected to be in the
				// same community
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
				showProgress();
				
			} catch (Exception e) {
				Log.error("Unable to generate Erdos-Renyi benchmark network.");
				throw e;
			}
			return null;
		}
	}
}
