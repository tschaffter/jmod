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

package ch.epfl.lis.jmod.batch;

import java.io.File;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import ch.epfl.lis.jmod.Jmod;
import ch.epfl.lis.jmod.JmodException;
import ch.epfl.lis.jmod.JmodNetwork;
import ch.epfl.lis.jmod.JmodSettings;
import ch.epfl.lis.jmod.gui.JmodGui;
import ch.epfl.lis.networks.Structure;

import com.esotericsoftware.minlog.Log;

/** 
 * Runs modularity detection for a batch of networks.<p>
 * 
 * JmodBatch provides support to run modularity detection of multiple networks
 * using different threads. The number of thread executed in parallel is specified
 * by JmodSettings.numProcessors_. First, the thread pool is filled with JmodWordks.
 * Each JmodWorker takes care of the performing the modularity detection of one
 * network. There are two ways to provide networks to JmodBatch. The first one is
 * to initialize it with a List&lt;URI&gt; pointing to network files to open and
 * process. The second is to provide JmodBatch directly with a List&lt;JmodNetwork&gt;
 * (be careful with the amount of memory used). The strategy used is that each
 * worker will 1) computes the modularity detection and 2) write the required
 * datasets to files. Then the main objects created by the modularity detection
 * framework are freed from the memory.<p>
 * 
 * To cancel the current batch processing, call the method cancel(). This is done
 * in two phases. First, no more workers are executed by ExecutorService. Then, a status
 * flag is used to stop the modularity detection. This is the way to do to avoid
 * undetermined states especially since workers are writing to files.
 * 
 * @version December 6, 2011
 * 
 * @author Thomas Schaffter (firstname.name@gmail.com)
 */
public class JmodBatch {
	
	/**
	 * Case 1: List of URI pointing to the networks to process.<br>
	 * In that case, networks_ is set to null and is ignored.
	 */
	protected List<URI> networksURI_ = null;
	/** 
	 * Case 2: List of networks. Note that this can be highly memory consuming.<br>
	 * In that case, networksURI_ is set to null and is ignored.
	 */
	protected List<JmodNetwork> networks_ = null;
	
	/** Network file format to use when opening networks from URI. */
	protected Structure.Format networkFormat_ = Structure.Format.UNDEFINED;
	
	/** Directory where the output files are saved (default: current directory). */
	protected URI outputDirectory_ = new File(".").toURI();
	
	/** Status to indicate to this batch processing that it must leave. */
	protected boolean canceled_ = false;
	
	/** ExecutorService reference. */
	protected ExecutorService executor_ = null;
	/** Workers references. */
	protected List<JmodWorker> workers_ = null;
	/** References to the results. */
	List<Future<Void>> results_ = null;
	
	// ============================================================================
	// PUBLIC METHODS
	
	/** Default constructor */
	public JmodBatch() {}
	
	// ----------------------------------------------------------------------------
	
	/** Computes the modularity Q of several networks using multiple threads. */
	public void run() throws Exception, JmodException {
		
		if ((networksURI_ != null && networksURI_.size() == 0) || (networks_ != null && networks_.size() == 0))
			throw new JmodException("The network selection is empty.");
		
		Log.info("Starting batch module detections.");
		JmodSettings settings = JmodSettings.getInstance();
		int numNets = settings.getNumConcurrentModuleDetections();
		int numAvailableProcessors = Runtime.getRuntime().availableProcessors();
		
		if (numNets > numAvailableProcessors)
			throw new JmodException("There are only " + numAvailableProcessors + " processors available to the Java virtual machine.");
		Log.info("Number of networks processed in parallel (max: " + numAvailableProcessors + "): " + numNets);
		
		// test the validity of the output directory before going further
		if (outputDirectory_ == null || outputDirectory_.getPath() == null) { // set to current directory
			Log.info("Setting current directory as output directory.");
			outputDirectory_ = new File(".").toURI();
		}
		File directoryFile = new File(outputDirectory_);
		if (directoryFile == null || !directoryFile.isDirectory())
			throw new JmodException("Invalid output directory " + outputDirectory_.getPath());
		
		// use a thread pool executor to generate the lists
    	if (executor_ != null)
    		executor_.shutdownNow();
		executor_ = Executors.newFixedThreadPool(numNets);
		// maintain a reference to the Jmod instances
		workers_ = new ArrayList<JmodWorker>();
		// references to the results
		results_ = null;
		
		// get the number of networks
		int numNetworks = 0;
		if (networks_ != null) numNetworks = networks_.size();
		else numNetworks = networksURI_.size();
		double deltaProgress = 100. / numNetworks;
		
		// create workers
		JmodWorker worker = null;
		for (int i = 0; i < numNetworks; i++) {
			worker = new JmodWorker(i);
			// set network instance or URI
			if (networks_ != null)
				worker.setNetwork(networks_.get(i));
			else {
				worker.setNetworkURI(networksURI_.get(i));
				worker.setNetworkFormat(networkFormat_);
				worker.setDeltaProgress(deltaProgress);
			}
			worker.setOutputDirectory(outputDirectory_);
			workers_.add(worker);
		}
		
		Log.info("Total number of networks: " + numNetworks);
		try {
			// perform the workers
			results_ = executor_.invokeAll(workers_);
			// wait until all workers are done
			for (Future<Void> result: results_)
				result.get();
			
			// send a signal to gui to inform that the batch is done (if it exists)
			if (JmodGui.exists())
				JmodGui.getInstance().modularityDetectionDone();

		} catch (Exception e) {
			throw e;
		} finally {
			canceled_ = false;
			if (executor_ != null) {
				executor_.shutdownNow();
				executor_ = null;
			}
			workers_ = null;
			results_ = null;
		}
	}
	
	// ----------------------------------------------------------------------------
	
	/**
	 * Cancels the execution as soon as this batch get aware of it. It is possible to
	 * kill the thread using executor_.shutdownNow(). However the workers can be
	 * manipulating file at that time and thus kill them would result in having
	 * undetermined states. That's while it's safer to use an approach based on
	 * the use of a status flag checked as often as possible in order to leave the
	 * process in good condition. Note that the methods writing networks or datasets
	 * to files are not canceled during execution but before starting writing each
	 * file, the application checks if it must exit or not.
	 */
	public void cancel() {
		
		try {
			if (executor_ == null)
				return;
			
			Log.info("\nWaiting for Jmod to terminate (can take a few seconds)");
			executor_.shutdown(); // disable new tasks from being submitted
			if (workers_ != null) {
				for (JmodWorker worker : workers_)
					worker.cancel();	
			}
			executor_.awaitTermination(Jmod.FORCE_EXIT_DELAY_IN_MS, TimeUnit.MILLISECONDS);
			
		} catch (Exception e) {
			Log.error("Unable to cancel batch of workers.");
		}
	}
	
	// ----------------------------------------------------------------------------
	
	/** Returns true if the executor_ != null. */
	public boolean isRunning() {
		
		return (executor_ != null);
	}
	
	// ----------------------------------------------------------------------------
	
	/** Clear method. */
	public void clear() {
		
		if (networksURI_ != null)
			networksURI_.clear();
		if (networks_ != null)
			networks_.clear();
		networksURI_ = null;
		networks_ = null;
	}
	
	// ----------------------------------------------------------------------------
	
	/** Main method. */
	public static void main(String args[]) {
		
		try {
			// note the use of the wildcard "*"
			String inputRegex = new File("rsc/batch").getAbsolutePath() + JmodSettings.FILE_SEPARATOR + "structure_*.tsv";
			JmodBatch batch = new JmodBatch();
			batch.setNetworksURI(inputRegex, Structure.Format.TSV);
			batch.setOutputDirectory(new File("rsc/batch/output").toURI());
			batch.run();
			
		} catch (Exception e) {
			Log.error("JmodBatch", e);
		} finally {
			Log.info("Done");
			System.exit(0);
		}
	}
	
	// ============================================================================
	// GETTERS AND SETTERS

	/** 
	 * Generates a list of URI pointing to networks to open and process. To provide multiple networks,
	 * use wildcard matching to specify several network files (e.g. ./network_*.tsv).<br>
	 * Note that List<JmodNetwork> networks_ will be set to null and ignored.
	 */
	public void setNetworksURI(String regex, Structure.Format networkFormat) throws Exception, JmodException {
		networksURI_ = NetworkFilenameFilter.findNetworks(regex);
		networkFormat_ = networkFormat;
	}
	/**
	 * Specify a list of URI pointing to networks to open and process.<br>
	 * Note that List<JmodNetwork> networks_ will be set to null and ignored.
	 */
	public void setNetworksURI(List<URI> networks, Structure.Format networkFormat) {
		networksURI_ = networks;
		networkFormat_ = networkFormat;	
	}
	/**
	 * Specify a list of networks to open and process.<br>
	 * Note that List<URI> networksURI_ will be set to null and ignored.
	 */
	public void setNetworks(List<JmodNetwork> networks) { networks_ = networks; }
	
	public void setNetworkFormat(Structure.Format format) { networkFormat_ = format; }
	public Structure.Format getNetworkFormat() { return networkFormat_; }
	
	/** Set the output directory (current directory if null). */
	public void setOutputDirectory(URI uri) { outputDirectory_ = uri; }
	public URI getOutputDirectory() { return outputDirectory_; }
}
