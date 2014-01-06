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

package ch.epfl.lis.jmod.modularity;

import java.io.File;
import java.io.FileWriter;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import no.uib.cipr.matrix.DenseVector;
import ch.epfl.lis.jmod.JmodNetwork;
import ch.epfl.lis.jmod.modularity.community.Community;
import ch.epfl.lis.networks.Node;

/**
 * Takes snapshot of the current state of the module detection.<p>
 * 
 * The method takeSnapshot(String) assumes the use of of module detection
 * method using a scheme where communities are recursively split int two 
 * subcommunities. The objective of this class is to generate one file per 
 * snapshot containing information about the nodes whose state has changed 
 * (differential snapshot). It is also possible to save the entire state 
 * of the _current community_ being attempted to be split (full snapshot).
 * Differential snapshots is the default as it saves file weight.<p>
 * 
 * The snapshots are in TSV format and have two columns. The first contains 
 * the name of the node and the second column contains the new state of the 
 * corresponding node. These snapshot files can then be processed using 
 * JmodReplay. JmodReplay has been developed to take as input the snapshot 
 * files, and network file including graphics data (e.g. exported from 
 * Cytoscape or in Graphviz-DOT format) and a list of c colors where where c 
 * is the largest state written in the given snapshot files (if colors not 
 * provided, a random list of color is generated). JmodReplay then converts 
 * the snapshots to GML or DOT network files with node painted based on their 
 * state in the snapshot files. For DOT network files, GraphvizBatchConverter 
 * provided by Jmod can be used to process in parallel the dot files and 
 * convert them to image files (requires graphviz to be installed on the 
 * the system).<p>
 * 
 * In addition to the snapshots, the largest state value used and returned by  
 * the inner classStatesManager is saved to a file ending with
 * LARGEST_STATE_VALUE_FILENAME_SUFFIX  which is later used by JmodReplay to 
 * know how many distinct colors must be generated.<p>
 * 
 * @see JmodReplay
 * @see GraphvizBatchConverter
 *
 * @version June 9, 2013
 * 
 * @author Thomas Schaffter (firstname.name@gmail.com)
 */
public class ModularityDetectorSnapshot {
	
	/** String that identifies a snapshot file export as "snapshot". */
	public static final String SNAPSHOT_SUFFIX = "_snapshot";
	/** Suffix for indivisible community states. */
	public static final String INDIVISIBLE_COMMUNITY = "indivisible";
	/** Filename of the file where the largest state value is saved (required by JmodReplay). */
	public static final String LARGEST_STATE_VALUE_FILENAME_SUFFIX = "_max_state.txt";
	
	/** Saves entirely the current s vector (not entirely the state of the network!). */
	public static final int EXPORT_FULL_SNAPSHOTS = 1;
	/** Saves only the difference between the new and previous states. */
	public static final int EXPORT_DIFFERENTIAL_SNAPSHOTS = 2;
	
	/** Save mode (full or differential). */
	protected int exportMode_ = EXPORT_DIFFERENTIAL_SNAPSHOTS;
	/** Root filename for saving the states to files (including directory). */
	protected String rootFilename_ = null;
	
	/** Reference to the modularity detector. */
	ModularityDetector modDetector_ = null;
	
	/** Reference to the network which is being decomposed. */
	protected JmodNetwork network_ = null;
	
	/** List of the node name of the current community being split. */
	protected List<String> communityNodeNames_ = new ArrayList<String>();
	
	/** Name of the previous community being split. */
	protected String previousCommunityName_ = null;
	/** Previous split vector s. */
	protected DenseVector previousS_ = null;

	/** Index of the next snapshot. */
	protected int snapshotIndex_ = 0;
	
	/** History of the states. */
	protected StatesManager statesManager_ = null;
	
	/** Vector of size n that remember the state of each node. */
//	protected int[] absStates_ = null;
	/** Vector of size n that remember the state of each node (node name, state). */
	protected Map<String,Integer> absStates_ = null;
	
	// ============================================================================
	// PUBLIC METHODS
	
	/** Default constructor. */
	public ModularityDetectorSnapshot(ModularityDetector modDetector, int exportMode, String rootFilename) throws Exception {
		
		// elements are initially set to 0
		exportMode_ = exportMode;
		rootFilename_ = rootFilename + SNAPSHOT_SUFFIX;
		modDetector_ = modDetector;
		statesManager_ = new StatesManager();
		try {
			// set the destination where the largest state value is saved
			statesManager_.setLargestStateValueURI(new File(rootFilename_ + LARGEST_STATE_VALUE_FILENAME_SUFFIX).toURI());
		} catch (Exception e) {
			throw new Exception("Unable to set largest state value filename.");
		}
//		absStates_ = new int[modDetector.getNetwork().getSize()]; // initially zero vector
		absStates_ = new HashMap<String,Integer>();
		Map<String,Node> nodes = modDetector.getNetwork().getNodes();
		for (Map.Entry<String,Node> node : nodes.entrySet())
			absStates_.put(node.getKey(), 0); // initially zero vector
	}

	// ----------------------------------------------------------------------------

	/**
	 * Writes the current state of the modularity detection to file. This corresponds to saving
	 * the state of the node of the network. Suffix can be added to the filename to provide
	 * additional information about a given state. Requires the split vector to have been given
	 * to the modularity detector.
	 */
	public synchronized void takeSnapshot(String filenameSuffix) throws Exception {
		
		if (modDetector_ == null)
			throw new Exception("ERROR: Modularity detector is null.");

		// input
		Community community = modDetector_.currentCommunityBeingSplit_;
		DenseVector s = modDetector_.getCurrentSubcommunityS();
		
		String content = null;
		// update if the community being split changed
		// force indivisible communities to be entirely described
		if (previousCommunityName_ == null || previousCommunityName_.compareTo(community.getName()) != 0) {
			
//			if (modDetector_.currentCommunityBeingSplit_.getCommunitySize() == 34)
//				snapshotIndex_ = 86;
			
			// if we are here it means that it's a different community
			// was the previous one indivisible or not ?
			// do not increment for the first split of the graph
			if (previousCommunityName_ != null)
				statesManager_.update(isIndivisibleSplitVector(previousS_));
			
			// prepare the node name (do it only once for each split)
			DenseVector sNodeIndexes = community.getVertexIndexes();
			JmodNetwork network = modDetector_.getNetwork();
			
			// community name
			previousCommunityName_ = community.getName();
			// prepare community node names for fast access
			int[] sNodeIndexesBis = new int[sNodeIndexes.size()]; // format to int[]
			for (int i = 0; i < sNodeIndexesBis.length; i++)
				sNodeIndexesBis[i] = (int)sNodeIndexes.get(i);
			List<Node> nodes = network.getNodesFromIndexes(sNodeIndexesBis);
			communityNodeNames_.clear();
			for (int i = 0; i < nodes.size(); i++)
				communityNodeNames_.add(nodes.get(i).getName());
			// split vector s
			previousS_ = new DenseVector(s.size());
//			previousS_.assign(s);
			previousS_.set(s);
			
			// saves s=previousS entirely
			content = snapshotToString(previousS_, null);
			
		} else {
			content = snapshotToString(s, previousS_);
			previousS_.set(s); // backup
		}

		// write the data only if there is something to write
		if (content.compareTo("") != 0) {
			String filename = rootFilename_ + "_" + snapshotIndex_ + "_" + community.getName() + (filenameSuffix.compareTo("") == 0 ? "" : "_") + filenameSuffix + ".txt";
			FileWriter fw = new FileWriter(new File(filename), false);
			fw.write(content);
			fw.close();
			
			// save largest state value (required by JmodReplay)
			statesManager_.saveLargestStateValue();
			
			snapshotIndex_++;
		}
	}
	
	// ----------------------------------------------------------------------------
	
	/**
	 * Moves a node in a new community.
	 *
	 * Based on gMVM, the method assumes that a node is never placed in a new community
	 * of its own but is placed in another existing community.
	 * 
	 * The method is not based on the difference with a previous split vector but is 
	 * determined by the given information about a single node move.
	 * 
	 * IMPORTANT: newCommunityIndex does not match the node states used to distinguish
	 * modules in snapshots
	 */
	public synchronized void takeSnapshotGlobalNodeMove(String filenameSuffix, int nodeIndex, int newCommunityIndex) throws Exception {
		
		// find the new state of the node
		// note that the split vector s contains the community index of each node
		int newNeighborNodeOfNode = -1;
		for (int i = 0; i < modDetector_.currentSubcommunityS_.size(); i++) {
			// gMVM changes modDetector_.currentSubcommunityS_ -> OK
			// s vector contains here module indexes starting from 0
			if (modDetector_.currentSubcommunityS_.get(i) == newCommunityIndex) {
				newNeighborNodeOfNode = i;
				break;
			}
		}
		if (newNeighborNodeOfNode == -1)
			throw new Exception("Node is moved in a community that doesn't include any nodes.");
		
		// new state of the node
//		int newState = absStates_[newNeighborNodeOfNode];
		int newState = absStates_.get(modDetector_.getNetwork().getNode(newNeighborNodeOfNode));
		// content of the snapshot
		String content = "\"" + modDetector_.getNetwork().getNode(nodeIndex).getName() + "\"\t" + newState + System.lineSeparator();
		
		String filename = rootFilename_ + "_" + snapshotIndex_ + (filenameSuffix.compareTo("") == 0 ? "" : "_") + filenameSuffix + ".txt";
		FileWriter fw = new FileWriter(new File(filename), false);
		fw.write(content);
		fw.close();
		
		// save largest state value (required by JmodReplay)
		statesManager_.saveLargestStateValue();
		
		snapshotIndex_++;
	}
	
	// ----------------------------------------------------------------------------
	
	/**
	 * Generic method for getting the content of the snapshot.<p>
	 * If s is null, export previousS entirely.<p>
	 * <b>Important:</b><p>
	 * Save node names now instead of node indexes as in first implementation
	 */
	protected String snapshotToString(DenseVector s, DenseVector previousS) throws Exception {
		
		if (s.size() != communityNodeNames_.size())
			throw new Exception("ERROR: s and nodeNames must have the same size");
		
		int state1 = statesManager_.currentState1_;
		int state2 = statesManager_.currentState2_;
		
		// replace one vector by minus one vector
		replaceOneVectorByMinusOneVector(s);

		String content = "";
		int state = -1;
		if (exportMode_ == EXPORT_FULL_SNAPSHOTS || previousS == null) {
			// use si for the state of the node
			for (int i = 0; i < s.size(); i++) {
				state = (s.get(i) > 0 ? state2 : state1);
				content += "\"" + communityNodeNames_.get(i) + "\"\t" + state + System.lineSeparator();
				// save the state of the node
//				int nodeIndexInNetwork = modDetector_.getNetwork().getNodeIndex(communityNodeNames_.get(i));
//				absStates_[nodeIndexInNetwork] = state;
				absStates_.put(communityNodeNames_.get(i), state); // overwrite state
			}
		} else {
			for (int i = 0; i < s.size(); i++) {
				if (s.get(i) != previousS.get(i)) { // saves only if the node state is different from before
					state = (s.get(i) > 0 ? state2 : state1);
					content += "\"" + communityNodeNames_.get(i) + "\"\t" + state + System.lineSeparator();
					// save the state of the node
//					int nodeIndexInNetwork = modDetector_.getNetwork().getNodeIndex(communityNodeNames_.get(i));
//					absStates_[nodeIndexInNetwork] = state;
					absStates_.put(communityNodeNames_.get(i), state); // overwrite state
				}
			}
		}
		
		return content;
	}
	
	// ----------------------------------------------------------------------------
	
	/** If the split vector is [1 1 1...] set it to [-1 -1 -1...]. */
	protected void replaceOneVectorByMinusOneVector(DenseVector s) {
		
		// use the sum to know if it's a one vector
		int sum = 0;
		for (int i = 0; i < s.size(); i++)
			sum += s.get(i);
		
		if (sum == s.size()) {
			for (int i = 0; i < s.size(); i++)
				s.set(i, -1);
		}
	}
	
	// ----------------------------------------------------------------------------
	
	/** Returns the sum of the elements of the split vector s. */
	protected boolean isIndivisibleSplitVector(DenseVector s) {
		
		// use the sum to know if it's a one vector
		int sum = 0;
		for (int i = 0; i < s.size(); i++)
			sum += s.get(i);

		return (Math.abs(sum) == s.size());
	}
	
	// ============================================================================
	// INNER CLASSES
	
	/**
	 * Manages the current states of the nodes in the community being split. Assuming 
	 * community splits in two subcommunities.
	 * 
	 * When splitting a module, the detection method attempts to distribute the nodes
	 * between two subcommunities, those two colors are required. The two colors are the
	 * color of the module being split (color1) and another one (color2).
	 * 
	 * When a community is detected as indivisible (known from the name of the snapshot 
	 * INDIVISIBLE_COMMUNITY), all its nodes are back to the initial color of the module 
	 * (color1). This color is then added to a list of colors that can not be used again.
	 * 
	 * Assuming that the modularity detection is performed according two Newman's scheme 
	 * where community are recursively split in two, the indexes of the colors are decrease
	 * because we go back in the tree of decomposition. If a community can be split, the 
	 * indexes of the colors is increased. In both cases, we always check that color one 
	 * not reserved yet.
	 */
	private class StatesManager {
		
		/** Current state 1. */
		protected int currentState1_ = 0;
		/** Current state 2. */
		protected int currentState2_ = 0;
		
		/** List of states reserved/fixed. */
		private List<Integer> reservedStates_ = null;
		
		/** URI of the file where the largest state value is saved. */
		private URI largestStateValueUri_ = null;
		
		/** Constructor. */
		public StatesManager() {
			currentState1_ = 0;
			currentState2_ = 1;
			reservedStates_ = new ArrayList<Integer>();
		}
		
		/** Prepares states for next module split. */
		public void update(boolean indivisible) {
			
			if (indivisible) {
				// reserve the smaller state which is always state 1
				reservedStates_.add(currentState1_);
				while (currentState1_ >= 0) {
					currentState1_--;
					if (!reservedStates_.contains(currentState1_))
						break;
				}
				currentState2_ = getMaxReservedState() + 1;
				
			} else {
				// check that state 1 is not booked
				do {
					currentState1_++;
				} while (reservedStates_.contains(currentState1_));
				
				currentState2_++;
			}
		}
		
		/** Returns the largest state reserved. */
		private int getMaxReservedState() {
			int max = 0;
			for (int i = 0; i < reservedStates_.size(); i++) {
				if (reservedStates_.get(i) > max)
					max = reservedStates_.get(i);
			}
			return max;
		}
		
		/** Saves the largest state value to file. */
		public void saveLargestStateValue() throws Exception {
			
			int max = Math.max(getMaxReservedState(), currentState1_);
			max = Math.max(max, currentState2_);
			
			FileWriter fw = new FileWriter(new File(largestStateValueUri_), false);
			fw.write("" + max + System.lineSeparator());
			fw.close();
		}
		
		public void setLargestStateValueURI(URI uri) { largestStateValueUri_ = uri; }
	}
}
