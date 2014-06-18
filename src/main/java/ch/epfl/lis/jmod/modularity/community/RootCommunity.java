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

package ch.epfl.lis.jmod.modularity.community;

import java.awt.Color;
import java.io.File;
import java.io.FileWriter;
import java.net.URI;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import no.uib.cipr.matrix.DenseVector;

import org.apache.commons.io.FilenameUtils;

import com.esotericsoftware.minlog.Log;

import cern.colt.matrix.*;
import cern.colt.matrix.impl.DenseDoubleMatrix1D;
import ch.epfl.lis.jmod.JmodException;
import ch.epfl.lis.jmod.JmodNetwork;
import ch.epfl.lis.jmod.JmodSettings;
import ch.epfl.lis.jmod.modularity.ModularityDetector;
import ch.epfl.lis.networks.Node;
import ch.epfl.lis.networks.Structure;
import ch.tschaffter.utils.ColorUtils;

/**
 * Represents the root community or module, that is, the entire network to decompose.<p>
 * 
 * Constructs the root community,i.e. the community at the top of the tree of communities.
 * There are sharing informations between the Community object and the given network.<p>
 * 
 * <b>Important:</b><p>
 * Do not delete the Network object before the associated communities.
 * 
 * @version 2008
 * 
 * @author Thomas Schaffter (firstname.name@gmail.com)
 * @author Daniel Marbach (firstname.name@gmail.com)
 * 
 * @see Community
 * @see JmodNetwork
 * @see ModularityDetector
 */
public class RootCommunity extends Community {
		
	/** Total number of communities in the tree. */
	protected int numCommunities_ = 0;
	/** The (maximum) depth of the tree. */
	protected int communityTreeDepth_ = 0;
	/** The indivisible communities (the leaves of the tree). */
	protected ArrayList<Community> indivisibleCommunities_ = null;
	
	/** Computation time in ms. */
	protected long computationTime_ = 0;
	/** Flag to know if the module detection has been canceled. */
	protected boolean canceled_ = false;
	
	// ============================================================================
	// PUBLIC METHODS

    /** Constructor */
	public RootCommunity(ModularityDetector modularityDetector, JmodNetwork network) throws Exception {
		
		father_ = null;
		child1_ = null;
		child2_ = null;
		
		name_ = "R";
			
		modularityDetector_ = modularityDetector;
		modularityDetector_.currentCommunityBeingSplit_ = this;
		communitySize_ = modularityDetector_.getNetwork().getSize();
		depthInCommunityTree_ = 0;
		indivisibleCommunities_ = new ArrayList<Community>();;
		
		// initialize the indexes	
		vertexIndexes_ = new DenseVector(communitySize_);
		for (int i = 0; i < communitySize_; i++)
			vertexIndexes_.set(i, i);	
	
		// root community not yet divided, lets take a snapshot of the entire network before
		// takes snapshot of the entire network before modularity detection
		modularityDetector_.currentSubcommunityS_ = new DenseVector(this.getCommunitySize());
		ModularityDetector.assign(modularityDetector_.currentSubcommunityS_, -1);
		modularityDetector_.takeSnapshot("");
		modularityDetector_.currentSubcommunityS_ = null;
	}
	
	// ----------------------------------------------------------------------------
	
	/** Clear method. */
	public void clear() {
		
		if (indivisibleCommunities_ != null) {
			indivisibleCommunities_.clear();
			indivisibleCommunities_ = null;
		}
	}

	// ----------------------------------------------------------------------------

	/** Runs the module detection algorithm selected in JmodSettings. */
	public void runModularityDetection(boolean recursive) throws Exception {
		
		JmodSettings settings = JmodSettings.getInstance();
		
		long t0 = System.currentTimeMillis();
		
		divide(recursive);
		// sets indivisibleCommunities_, numCommunities_, and communityTreeDepth_
		traverseCommunityTree();
			
		if (recursive && settings.getUseGlobalMovingVertex()) {
			DenseVector appartenanceVector = getMultipleCommunitiesSvector();
			modularityDetector_.globalMovingVertexMethod(appartenanceVector, indivisibleCommunities_.size());
			appartenanceVector = modularityDetector_.getCurrentSubcommunityS();
			
			// TODO here we update only the indivisible communities according to the new appartenanceVector
			// we should actually update the complete tree!
			for (int i = 0; i < (int)indivisibleCommunities_.size(); i++) {
				// count the size and get the ids of community i
				int newSize = 0;
				DenseVector newId;
				for (int k = 0; k < communitySize_; k++) {
					if (appartenanceVector.get(k) == i)
						newSize++;
				}
				
				newId = new DenseVector(newSize);
				int index = 0;
				for (int k = 0; k < communitySize_; k++) {
					if (appartenanceVector.get(k) == i) {
						newId.set(index, k);
						index++;
					}
				}
				indivisibleCommunities_.get(i).setVertexIndexes(newId);
				indivisibleCommunities_.get(i).setCommunitySize(newSize);
			}
		}
		
		// sets the community index for each node
		JmodNetwork network = modularityDetector_.getNetwork();
		DenseVector communityIndexes = null;
		for (int i = 0; i < indivisibleCommunities_.size(); i++) {
			communityIndexes = indivisibleCommunities_.get(i).getVertexIndexes();
			for (int j = 0; j < communityIndexes.size(); j++) {
				network.getNode((int)communityIndexes.get(j)).setCommunityIndex(i+1);
			}
		}
		computationTime_ = System.currentTimeMillis() - t0;
	}

	// ----------------------------------------------------------------------------

	/** Gets the s-vector associated to each communities. */
	public DenseVector getMultipleCommunitiesSvector() {
		
		// s is of size n and will contain for each node the index of the indivisible community it belongs to
		DenseVector s = new DenseVector(communitySize_);
		ModularityDetector.assign(s, -1);
		
		for (int i = 0; i < (int)indivisibleCommunities_.size(); i++) {
			DenseVector id = indivisibleCommunities_.get(i).getVertexIndexes();
			int N = indivisibleCommunities_.get(i).getCommunitySize();
			
			for (int k = 0; k < N; k++) {
				s.set((int)id.get(k), i); // s[id[k]] = i;
			}
		}
		return s;
	}

	// ----------------------------------------------------------------------------

	/** Gets statistics about the module detection by going through the community tree. */
	public void traverseCommunityTree() {
		
		DoubleMatrix1D numCommunities = new DenseDoubleMatrix1D(1);
		DoubleMatrix1D communityTreeDepth = new DenseDoubleMatrix1D(1);
		numCommunities.set(0, 0); // numCommunities_ = 0;
		communityTreeDepth.set(0, 1); // communityTreeDepth_ = 1;
		
		traverseCommunityTree(indivisibleCommunities_, numCommunities, communityTreeDepth);
		
		numCommunities_ = (int)numCommunities.get(0);
		communityTreeDepth_ = (int)communityTreeDepth.get(0);
	}
	
	// ----------------------------------------------------------------------------
	
	/** Exports the communities to network files. */
	public void exportCommunityNetworks(URI directory, Structure.Format format) throws Exception{
		
		// test the validity of the output directory before going further
		if (directory == null) // set to current directory
			directory = new File(".").toURI();

		String baseNetworkName = FilenameUtils.getBaseName(modularityDetector_.getNetwork().getName());
		exportCommunityNetworks(directory, baseNetworkName, format);
		
		// recursive call
		if (child1_ != null) {
			child1_.exportCommunityNetworks(directory, baseNetworkName, format);
			child2_.exportCommunityNetworks(directory, baseNetworkName, format);
		}
	}
	
	// ----------------------------------------------------------------------------
	
	/**
	 * Exports the indivisible communities and the dendrogram of the hierarchical module tree.
	 * Communities are written one per line, each line is a tab-separated list of nodes. The
	 * community of line i is called community i in the dendrogram.
	 */
	public void exportIndivisibleCommunities(URI directory) throws Exception, JmodException {
		
		FileWriter fwCommunities = null;
		FileWriter fwDendrogram = null;
		try {
			// test the validity of the output directory before going further
			if (directory == null) // set to current directory
				directory = new File(".").toURI();
			
			String outputDirectoryStr = FilenameUtils.getFullPath(directory.getPath());
			String rawFilename = FilenameUtils.getBaseName(modularityDetector_.getNetwork().getName());
			
			// One file for the communities and one for the dendrogram
			URI communityURI = new File(outputDirectoryStr + rawFilename + "_indivisible_communities.txt").toURI();
			URI dendrogramURI = new File(outputDirectoryStr + rawFilename + "_dendrogram.txt").toURI();
			fwCommunities = new FileWriter(new File(communityURI), false);
			fwDendrogram = new FileWriter(new File(dendrogramURI), false);
			
			Log.info(modularityDetector_.getNetwork().getName(), "Writing indivisible communities " + communityURI.getPath());
			Log.info(modularityDetector_.getNetwork().getName(), "Writing community tree (dendrogram) " + dendrogramURI.getPath());
			int[] interCommunityCounter = {indivisibleCommunities_.size()};
			int[] indivCommunityCounter = {0};
			exportCommunities(fwCommunities, fwDendrogram, interCommunityCounter, indivCommunityCounter, communityTreeDepth_);

		} catch (Exception e) {
			Log.error("RootCommunity", e);
			throw new JmodException("Could not write communities or dendrogram file.");
		} finally {
			if (fwCommunities != null)
				fwCommunities.close();
			if (fwDendrogram != null)
				fwDendrogram.close();
		}
	}
	
	// ----------------------------------------------------------------------------
	
	/**
	 * Export the original network with the node being colored depending on which
	 * community they belong to.
	 */
	public void exportColorCommunities(URI directory, Structure.Format format) throws Exception, JmodException {
		
		// backup original node colors
		JmodNetwork network = modularityDetector_.getNetwork();
		List<Color> nodeColors = new ArrayList<Color>();
		for (Node node : network.getOrderedNodes())
			nodeColors.add(ColorUtils.hex2Rgb(node.getGraphics().getFill().substring(1))); // remove '#'
			
		// generate as many distinct colors as there are indivisible communities
		int numIndivisibleCommunities = getNumIndivisibleCommunities(); //indivisibleCommunities_.size();
		List<Color> colors = ColorUtils.generateNDistinctColors(numIndivisibleCommunities);
		
		// test the validity of the output directory before going further
		if (directory == null) // set to current directory
			directory = new File(".").toURI();
		String outputDirectoryStr = FilenameUtils.getFullPath(directory.getPath());
		String rawFilename = FilenameUtils.getBaseName(modularityDetector_.getNetwork().getName());
		
		// Go through the community tree and paint only the nodes which belong to
		// indivisible communities (BASE_NAME + "_color_indivisible_communities").
		// Then we climb the community tree and repaint a parent with the color
		// of its first child to keep consistency (BASE_NAME + "_color_depth_DEPTH").
		Iterator<Color> colorIterator = colors.iterator();
		colorIndivisibleCommunityNodes(colorIterator);
		if (colorIterator.hasNext())
			throw new JmodException("There is an inconsistency at the level of the number of indivisible communities.");
		URI uri = new File(outputDirectoryStr + rawFilename + "_color_indivisible_communities" + Structure.getFormatExtension(format)).toURI();
		Log.info(network.getName(), "Writing indivisible communities colored " + uri.getPath());
		
		if (format == Structure.Format.GML)
			network.writeGML(uri);
		else if (format == Structure.Format.DOT)
			network.writeDOT(uri);
		else
			throw new Exception("ERROR: Unknown format for exporting colored communities.");
	
		// and now climb up the community tree
		for (int depth = communityTreeDepth_-1; depth >= 0; depth--) {
			
			if (canceled_)
				return;
			
			colorCommunityNodes(depth);
			uri = new File(outputDirectoryStr + rawFilename + "_color_depth_" + depth + Structure.getFormatExtension(format)).toURI();
			Log.info(network.getName(), "Writing communities (size: " + getCommunitySize() + ", depth: " + depth + ") colored " + uri.getPath());
			
			if (format == Structure.Format.GML)
				network.writeGML(uri);
			else if (format == Structure.Format.DOT)
				network.writeDOT(uri);
			else
				throw new Exception("ERROR: Unknown format for exporting colored communities.");
		}
		
		// finally, restore the original node colors
		for (int i = 0; i < nodeColors.size(); i++)		
			network.getNode(i).getGraphics().setFill(ColorUtils.rgb2hexString(nodeColors.get(i)));
	}

	// ============================================================================
	// GETTERS AND SETTERS

	public int getNumCommunities() { return numCommunities_; }
	
	public int getNumIndivisibleCommunities() { 
		
		// the forward way to do it below fails when gMVM redistributes all the vertices of a community to others
		//return indivisibleCommunities_.size();
		int numIndivisibleCommunities = 0;
		for (Community community : indivisibleCommunities_) {
			if (community.getCommunitySize() > 0)
				numIndivisibleCommunities++;
		}
		return numIndivisibleCommunities;
	}
	
	public int getCommunityTreeDepth() { return communityTreeDepth_; }
	
	public ArrayList<Community> getIndivisibleCommunities() { return indivisibleCommunities_; }
	
	public long getComputationTime() { return computationTime_; }
	
	public void setChild1(Community community) { child1_ = community; }
	public void setChild2(Community community) { child2_ = community; }
	
	public void isCanceled(boolean b) { canceled_ = b; }
}
