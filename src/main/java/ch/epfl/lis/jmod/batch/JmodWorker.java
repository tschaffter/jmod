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
import java.util.concurrent.Callable;

import com.esotericsoftware.minlog.Log;

import ch.epfl.lis.jmod.Jmod;
import ch.epfl.lis.jmod.JmodException;
import ch.epfl.lis.jmod.JmodNetwork;
import ch.epfl.lis.jmod.JmodSettings;
import ch.epfl.lis.jmod.gui.JmodGui;
import ch.epfl.lis.networks.Structure;
import ch.epfl.lis.networks.parsers.TSVParser;

/** 
 * Implements a Callable object to enable running a Jmod instance in a dedicated thread.
 * 
 * <p>There are two ways to provide a network to JmodWorker. The first one is
 * to initialize it with an URI pointing to a network file to open and
 * process. The second is to provide JmodWorker directly with a JmodNetwork.</p>
 * 
 * <p>To cancel the current worker, call the method cancel(). Instead of killing simply
 * the worker thread, a status flag is used to stop the modularity detection. This is the
 * way to do to avoid undetermined states especially since workers are writing to files.</p>
 * 
 * @version December 7, 2011
 * 
 * @author Thomas Schaffter (firstname.name@gmail.com)
 */
public class JmodWorker implements Callable<Void> {
	
	/** Thread identifier. */
	protected int threadId_ = 0;
	
	/** Jmod instance. */
	protected Jmod jmod_ = null;
	
	/** Network URI. */
	protected URI networkURI_ = null;
	/**
	 * JmodNetwork network.<br>
	 * Note that this can be highly memory consuming.<br>
	 * In that case, networkURI_ is set to null and is ignored.
	 */
	protected JmodNetwork network_ = null;
	
	/** Network type. */
	protected Structure.Format networkFormat_ = Structure.Format.UNDEFINED;
	/** Output directory. */
	protected URI outputDirectory_ = null;
	
	/** Delta progress of the complete batch. */
	protected double deltaProgress_ = 0.;

	/** Status to indicate to this batch processing that it must leave. */
	protected boolean canceled_ = false;
	
	// ============================================================================
	// PUBLIC METHODS
	
	/** Constructor */
	public JmodWorker(int threadId) {
		
		threadId_ = threadId;
	}
	
	// ----------------------------------------------------------------------------
	
	/** This is the method that will be executed when the thread is running. */
	@Override
	public Void call() throws Exception, JmodException {
		
		// meanwhile, the thread has been canceled
		if (canceled_)
			throw new JmodException("Remaining modularity detections canceled.");
		
		// if an JmodNetwork reference has been given, nothing more to do
		// if an URI has been provided, load the network
		if (network_ == null) {
			if (networkURI_ == null)
				throw new JmodException("At least network_ or networkURI_ must be set.");
			network_ = new JmodNetwork();
			network_.read(networkURI_, networkFormat_);
			
			// Looks for the file containing the identity of the community (mainly used by
			// the method "Known modules"). Removes the extension of the network filename
			// and add complete the filename accordingly.
			String communityIndexesFilename = networkURI_.getPath().replace(Structure.getFormatExtension(networkFormat_), "") + 
				JmodSettings.COMMUNITY_IDENTITIES_FILENAME_SUFFIX;
			File communityIndexesFile = new File(communityIndexesFilename);
			if (communityIndexesFile.exists()) {
				try {
					Log.info(network_.getName(), "Reading community identities.");
					ArrayList<String[]> data = TSVParser.readTSV(communityIndexesFile.toURI());
					// first column is the name of the node, the second is the indexes of the communities
					for (int i = 0; i < data.size(); i++)
						network_.getNode(data.get(i)[0].replace(" ", "")).setCommunityIndex(Integer.parseInt(data.get(i)[1].replace(" ", "")));
				} catch (Exception e) {
					Log.warn("JmodWorker", "Unable to read the community file " + communityIndexesFilename);
				}
			}
		}
		
		// run modularity detection
		jmod_ = new Jmod();
		jmod_.setOutputDirectory(outputDirectory_);
		jmod_.runModularityDetection(network_); // throws an exception if cancelled
		
		jmod_.printResult();
		jmod_.exportDataset();
		
		// update the progress bar of the gui (if any)
		if (JmodGui.exists())
			JmodGui.getInstance().setModularityDetectionProgress(deltaProgress_);
		
		Log.info(network_.getName(), "Done");
		
		return null;
	}
	
	// ----------------------------------------------------------------------------
	
	/** Cancels the execution as soon as this worker get aware of it. It is possible to
	 * kill the thread using executor_.shutdownNow(). However the workers can be
	 * manipulating file at that time and thus kill them would result in having
	 * undetermined states. That's while it's safer to use an approach based on
	 * the use of a status flag checked as often as possible in order to leave the
	 * process in good condition.
	 */
	public void cancel() {
		
		canceled_ = true;
		if (jmod_ != null)
			jmod_.cancel();
	}
	
	// ============================================================================
	// GETTERS AND SETTERS
	
	public void setThreadId(int id) { threadId_ = id; }
	public int getThreadId() { return threadId_; }
	
	public void setNetworkURI(URI uri) { networkURI_ = uri; }
	public URI getNetworkURI() { return networkURI_; }
	
	public void setNetwork(JmodNetwork network) { network_ = network; }
	public JmodNetwork getNetwork() { return network_; }
	
	public void setNetworkFormat(Structure.Format format) { networkFormat_ = format; }
	public Structure.Format getNetworkFormat() { return networkFormat_; }
	
	public void setOutputDirectory(URI uri) { outputDirectory_ = uri; }
	public URI getOutputDirectory() { return outputDirectory_; }
	
	public void setDeltaProgress(double value) { deltaProgress_ = value; }
	public double getDeltaProgress() { return deltaProgress_; }
	
	public Jmod getJmod() { return jmod_; }
}
