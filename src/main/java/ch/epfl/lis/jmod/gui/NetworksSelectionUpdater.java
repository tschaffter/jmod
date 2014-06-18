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

package ch.epfl.lis.jmod.gui;

import java.net.URISyntaxException;

import javax.swing.SwingWorker;

import com.esotericsoftware.minlog.Log;

import ch.epfl.lis.jmod.JmodException;
import ch.epfl.lis.jmod.batch.NetworkFilenameFilter;
import ch.epfl.lis.networks.Structure;

/** 
 * Counts the number of networks matching the given regex and updates the GUI of Jmod once done.
 * 
 * <p>This object takes as input a regular expression string such as "PATH/network.tsv" or 
 * "PATH/network_*.tsv", for instance. The file listing is done at the level of a single
 * folder and not recursively. Once the listing is complete and if the job has not be
 * canceled with the method SwingWorker.cancel(true), a JLabel from the Jmod GUI is updated
 * to show how many files match the input regex. If the job has been canceled, the listing
 * is not interrupted for convenience but the method done() returns immediately without
 * processing the result, i.e. without updating the JLabel from the Jmod GUI.</p>
 * 
 * @version December 5, 2011
 * 
 * @author Thomas Schaffter (firstname.name@gmail.com)
 */
public class NetworksSelectionUpdater extends SwingWorker<Void, Void> {
	
	/** Current instance of NetworksSelectionUpdater. */
	public static NetworksSelectionUpdater instance_ = null;
	
	/** Regular expression (regex) evaluated to select networks. */
	private String regex_ = null;
	/** Number of network files matching the regex. */
	private Integer numNetworks_ = null;
	
	// ============================================================================
	// PROTECTED METHODS
	
	@Override
	protected Void doInBackground() throws Exception, JmodException, URISyntaxException {
		
		JmodGui gui = JmodGui.getInstance();
		
		if (regex_ == null)
			throw new JmodException("regex_ is null.");
		
		// if regex is empty
		if (regex_ != null && regex_.compareTo("") == 0) {
			numNetworks_ = 0;
			return null;
		}

		// display the snake during processing
		gui.inputNetworksSnake_.start();
		gui.inputNetworksCardLayout_.show(gui.inputNetworksLabelPanel_, "CARD_INPUT_NETWORKS_SNAKE");

		numNetworks_ = NetworkFilenameFilter.findNetworks(regex_).size();

		
		return null;
	}
	
	// ----------------------------------------------------------------------------
	
    @Override
    protected void done() {
		
    	// do not treat the result if process canceled
    	if (isCancelled())
    		return;
   
    	try {
			get();
			
			// displays the result
			if (numNetworks_ == null || numNetworks_ == 0)
				JmodGui.setNumSelectedNetworks(0);
			else {
				JmodGui.setNumSelectedNetworks(numNetworks_);
				// changes the network type if the input string end with a known extension
				String[] extensions = Structure.getFormatExtensions();
				for (int i = 0; i < extensions.length; i++) {
					if (regex_.endsWith(extensions[i]))
						JmodGui.getInstance().inputNetworksFormatCBox_.setSelectedIndex(i);
				}
			}
			
		} catch (Exception e) {
			Log.error("NetworksSelectionUpdater", "Error updating the networks selection.", e);
			JmodGui.setNumSelectedNetworks(0);
		}
	}
	
	// ============================================================================
	// PUBLIC METHODS
	
	/** Constructor. */
	public NetworksSelectionUpdater(String regex) {
		
		regex_ = regex;
	}
}
