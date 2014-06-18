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

import cern.colt.matrix.*;
import cern.colt.matrix.impl.DenseDoubleMatrix1D;
import ch.epfl.lis.jmod.JmodException;
import ch.epfl.lis.jmod.JmodNetwork;
import ch.epfl.lis.jmod.JmodSettings;
import ch.epfl.lis.jmod.modularity.ModularityDetector;
import ch.epfl.lis.jmod.modularity.ModularityDetectorSnapshot;
import ch.epfl.lis.networks.Edge;
import ch.epfl.lis.networks.Node;
import ch.epfl.lis.networks.Structure;
import ch.tschaffter.utils.ColorUtils;

import java.awt.Color;
import java.io.File;
import java.io.FileWriter;
import java.net.URI;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.apache.commons.io.FilenameUtils;

import no.uib.cipr.matrix.DenseVector;

import com.esotericsoftware.minlog.Log;

/**
 * Network community or module used when computing modularity detection.
 * 
 * @version April 14, 2008
 * 
 * @author Thomas Schaffter (firstname.name@gmail.com)
 */
public class Community {
	
	/** Pointer to the ModularityDetector of this Community tree. */
	protected ModularityDetector modularityDetector_ = null;
	
	/** Name of the community. */
	protected String name_ = "";
	
	/** The indexes (as indexed in the original network) of the vertices belonging to this community. */
	protected DenseVector vertexIndexes_ = null;
	/** Size of the community. */
	protected int communitySize_ = 0;
	/** Depth in the community tree. The root has depth zero. */
	protected int depthInCommunityTree_ = 0;
	/** The delta Q of this division (-1 for indivisible communities). */
	protected double deltaQ_ = -1;
	/** The father_ of this community in the community structure tree. */
	protected Community father_ = null;
	/** First child community obtained after division of this community. */
	protected Community child1_ = null;
	/** Second child community obtained after division of this community. */
	protected Community child2_ = null;
	
	// ============================================================================
	// PROTECTED METHODS
	
	/** Default constructor. */
	protected Community() {
		
		modularityDetector_ = null;
		communitySize_ = -1;
		depthInCommunityTree_ = -1;
		
		father_ = null;
		child1_ = null;
		child2_ = null;
	}
	
	// ----------------------------------------------------------------------------
	
	/**
	 * Constructor of the communities which have depth equal or more than 1.<br>
	 * Receives in parameter a vector with elements are the ID vertexes which compose the communities.
	 */
	protected Community(Community father, DenseVector vertexIndexes) {
		
		modularityDetector_ = father.modularityDetector_;
		
		vertexIndexes_ = vertexIndexes;
		communitySize_ = vertexIndexes_.size();
		depthInCommunityTree_ = (father.depthInCommunityTree_) + 1;
		
		father_ = father;
		child1_ = null;
		child2_ = null;
	}
	
	// ============================================================================
	// PUBLIC METHODS
	
	/** Tries to divide each community which has a depth equals to the given depth. */
	public void extractCommunitiesSameDepth(int depth, List<Community> com) {
		
		if (getDepthInCommunityTree() == depth)
			com.add(this);
		else if (child1_ != null) {
				child1_.extractCommunitiesSameDepth(depth, com);
				child2_.extractCommunitiesSameDepth(depth, com);
		}
	}
	
	// ----------------------------------------------------------------------------
	
	/** Tries to divide all the community contained in the given list of communities. */
	public void extractAllCommunities(List<Community> com) {
		
		com.add(this);
		if (child1_ != null) {
				child1_.extractAllCommunities(com);
				child2_.extractAllCommunities(com);
		}
	}
	
	// ----------------------------------------------------------------------------
	
	/** Returns the community associated to the given community name from the given list of communities. */
	public void getCommunity(String name, List<Community> com) {
		
		if (this.name_.equals(name)) {
			com.add(this);
			return;
		}
		else {
			if (child1_ != null) {
				child1_.getCommunity(name, com);
				child2_.getCommunity(name, com);
			}
		}
	}
	
	// ----------------------------------------------------------------------------
	
	/**
	 * Builds the tree of communities as two lists, one for the source communities and one for the target communities.
	 * For instance, source.get(i) is the source community of target.get(i).
	 */
	public void buildNameTree(List<String> source, List<String> target) {
		
		if (child1_ != null) {
			source.add(this.name_);
			target.add(child1_.name_);
			source.add(this.name_);
			target.add(child2_.name_);
			child1_.buildNameTree(source, target);
			child2_.buildNameTree(source, target);
		}
	}

	// ----------------------------------------------------------------------------

	/** Tries to divide the community (recursively if recursive is true). */
	public void divide(boolean recursive) throws Exception {
		
		if (father_ != null) {
			Log.debug("Community", "Dividing child community (size: " + communitySize_ + ", depth: " + depthInCommunityTree_ + ").");

			if (communitySize_ == 1) {
				Log.debug("Community", "Nothing to divide. Skipping.");
				// snapshot: set all the element to -1 and take snapshot
				ModularityDetector.assign(modularityDetector_.currentSubcommunityS_, -1);
				modularityDetector_.takeSnapshot(ModularityDetectorSnapshot.INDIVISIBLE_COMMUNITY);
				return;
			}
			
			// print the id of the vertices of the community only if communitySize_ < 100
			if (communitySize_ < 100) {
				
				String str = "Vertices indexes: ";
				for (int i = 0; i < communitySize_; i++) {
					str += (int)vertexIndexes_.get(i) + " ";
				}
				Log.debug("Community", str);
			}
			
			// compute generalized modularity matrix B
			modularityDetector_.computeGeneralisedModularityMatrix(vertexIndexes_);
		}
		
		boolean divisible = modularityDetector_.subdivisionInTwoCommunities(this);
		Log.debug("Community", "Modularity Q (delta Q for subcommunities) = " + modularityDetector_.getCurrentSubcommunityQ());
		
		if (!divisible) {
			Log.debug("Community", "The community " + name_ + " is indivisible (size: " + communitySize_ + ").");
			child1_ = null;
			child2_ = null;
			// snapshot: set all the element to -1 and take snapshot
			ModularityDetector.assign(modularityDetector_.currentSubcommunityS_, -1);
			modularityDetector_.takeSnapshot(ModularityDetectorSnapshot.INDIVISIBLE_COMMUNITY);
			return;
		}
		
		// remember the delta Q
		deltaQ_ = modularityDetector_.getCurrentSubcommunityQ();
		
		// distribution of the vertexes in two groups
		DenseVector appartenanceVector = modularityDetector_.getCurrentSubcommunityS();
		DenseVector vertexIndexesChild1, vertexIndexesChild2;
		int communityChild1Size = 0;
		
		for (int i = 0; i < communitySize_; i++) {
			if (appartenanceVector.get(i) > 0)
				communityChild1Size++;
		}
		vertexIndexesChild1 = new DenseVector(communityChild1Size);
		vertexIndexesChild2 = new DenseVector(communitySize_ - communityChild1Size);
		
		int indexChild1 = 0;
		int indexChild2 = 0;
		
		for (int i = 0; i < communitySize_; i++) {
			if (appartenanceVector.get(i) > 0) {
				vertexIndexesChild1.set(indexChild1, vertexIndexes_.get(i));
				indexChild1++;
			}
			else {
				vertexIndexesChild2.set(indexChild2, vertexIndexes_.get(i));
				indexChild2++;
			}
		}
		Log.debug("Community", "This community can be divided into two communities.");
		
		// create children
		child1_ = new Community(this, vertexIndexesChild1);
		child2_ = new Community(this, vertexIndexesChild2);
		
		child1_.name_ = name_ + "A";
		child2_.name_ = name_ + "B";
		
		// divide children
		if (recursive) {
			child1_.divide(true);
			child2_.divide(true);
		}
	}

	// ----------------------------------------------------------------------------
	
	/** Gets statistics from the tree of community divisions. */
	public void traverseCommunityTree(ArrayList<Community> indivisibleCommunities, DoubleMatrix1D numCommunities, DoubleMatrix1D communityTreeDepth_) {
		
		if ((child1_ == null && child2_ != null) || (child1_ != null && child2_ == null))
			throw new RuntimeException("A community must have zero or two children!");
		
		// increase the number of communities by one
		numCommunities.set(0, numCommunities.get(0) + 1); //numCommunities++;
		
		// recursive call
		if (child1_ != null) {
			DoubleMatrix1D depthChild1 = new DenseDoubleMatrix1D(1);
			DoubleMatrix1D depthChild2 = new DenseDoubleMatrix1D(1);
			depthChild1.assign(0);
			depthChild2.assign(0);

			child1_.traverseCommunityTree(indivisibleCommunities, numCommunities, depthChild1);
			child2_.traverseCommunityTree(indivisibleCommunities, numCommunities, depthChild2);
			
			if (depthChild1.get(0) > depthChild2.get(0)) // compare depth child
				communityTreeDepth_.set(0, depthChild1.get(0) + 1);
			else
				communityTreeDepth_.set(0, depthChild2.get(0) + 1);
			
		} else { // this is an indivisible community (a leaf of the tree)
			communityTreeDepth_.set(0, 1);	// communityTreeDepth = 1;
			indivisibleCommunities.add(this);
		}
	}
	
	// ----------------------------------------------------------------------------
	
	/** Exports communities to file. */
	public int exportCommunities(FileWriter fwCommunities, FileWriter fwDendrogram,  int[] interCommunityCounter, int[] indivCommunityCounter, int communityTreeDepth) throws Exception, JmodException {
		
		if ((child1_ == null && child2_ != null) || (child1_ != null && child2_ == null))
			throw new JmodException("A community must have zero or two children.");
		
		// recursive call
		if (child1_ != null) {

			int child1_id = child1_.exportCommunities(fwCommunities, fwDendrogram, interCommunityCounter, indivCommunityCounter, communityTreeDepth);
			int child2_id = child2_.exportCommunities(fwCommunities, fwDendrogram, interCommunityCounter, indivCommunityCounter, communityTreeDepth);
			
			fwDendrogram.write(child1_id + "\t" + child2_id + "\t" + (communityTreeDepth - depthInCommunityTree_-1) + "\n");
			
			interCommunityCounter[0]++;
			return interCommunityCounter[0];
			
		} else { // this is an indivisible community (a leaf of the tree)
			
			JmodNetwork network = modularityDetector_.getNetwork();

			// write this indivisible community (first element on each line is the name of the community)
			fwCommunities.write(name_ + "\t");
			
			// Important: it is possible that gMVM emptied a community
			if (this.communitySize_ > 0) {
				// write all the nodes but the last one
				for (int i = 0; i < vertexIndexes_.size()-1; i++)
					fwCommunities.write(network.getNode((int)vertexIndexes_.get(i)).getName() + "\t");
				// write the last node followed by a new line
				fwCommunities.write(network.getNode((int)vertexIndexes_.get(vertexIndexes_.size()-1)).getName() + "\n");
				
				indivCommunityCounter[0]++;
			} else {
				fwCommunities.write("EMPTIED\n");
			}
			return indivCommunityCounter[0];
		}
	}
	
	// ----------------------------------------------------------------------------
	
	/** Exports the community to network file. This method is called recursively for each children (if any). */
	public void exportCommunityNetworks(final URI directory, final String baseNetworkName, final Structure.Format format) throws Exception, JmodException {
		
		if ((child1_ == null && child2_ != null) || (child1_ != null && child2_ == null))
			throw new JmodException("A community must have zero or two children.");
		
		// prepare the node index in a usable format
		int[] nodeIndexes = new int[vertexIndexes_.size()];
		for (int i = 0; i < nodeIndexes.length; i++)
			nodeIndexes[i] = (int) vertexIndexes_.get(i);
		
		// build the structure object and save it to file
		String outputDirectoryStr = FilenameUtils.getFullPath(directory.getPath());
		URI uri = new File(outputDirectoryStr + baseNetworkName + "_" + name_ + Structure.getFormatExtension(format)).toURI();
		
		Structure<Node,Edge<Node>> communityNetwork = modularityDetector_.getNetwork().getSubnetworkFromNodeIndexes("", nodeIndexes);
		Log.info(modularityDetector_.getNetwork().getName(), "Writing community network (size: " + getCommunitySize() + ", depth: " + getDepthInCommunityTree() + ") " + uri.getPath());
		communityNetwork.write(uri, format);
		
		// recursive call
		if (child1_ != null) {
			child1_.exportCommunityNetworks(directory, baseNetworkName, format);
			child2_.exportCommunityNetworks(directory, baseNetworkName, format);
		}
	}
	
	// ----------------------------------------------------------------------------
	
	/** Takes the original network and colors the node which belong to this community. */
	public void colorCommunityNodes(Color color) throws Exception {
		
		if (color == null)
			throw new JmodException("color is null.");
		
		Log.debug("Community", "Painting community " + this.name_ + " with " + ColorUtils.rgb2hexString(color));
		
		JmodNetwork network = modularityDetector_.getNetwork();
		for (int i = 0; i < vertexIndexes_.size(); i++)
			network.getNode((int) vertexIndexes_.get(i)).getGraphics().setFill(ColorUtils.rgb2hexString(color));
	}
	
	// ----------------------------------------------------------------------------
	
	/** Takes the original network and colors the node which belong to this community. */
	public void colorIndivisibleCommunityNodes(Iterator<Color> colorIterator) throws Exception, JmodException {
		
		if ((child1_ == null && child2_ != null) || (child1_ != null && child2_ == null))
			throw new JmodException("A community must have zero or two children.");
		
		// this community is indivisible so we paint it
		if (child1_ == null) {
			if (getCommunitySize() > 0) // don't waste a color if the community size is zero
				colorCommunityNodes(colorIterator.next());
		} else {
			child1_.colorIndivisibleCommunityNodes(colorIterator);
			child2_.colorIndivisibleCommunityNodes(colorIterator);
		}
	}
	
	// ----------------------------------------------------------------------------
	
	/**
	 * Recursive method to paint the community which has the given depthInCommunityTree_.<p>
	 * 
	 * The method assumes that Community.colorIndivisibleCommunityNodes() has been called first.
	 * Indeed, painting starts from the indivisible communities painted and climb up the community
	 * tree.<p>
	 * 
	 * <b>VERSION 2 (if gMVM used; slower):</b> If the current community has depth equals to the given one and has children,
	 * all its node (this.getVertexIndexes()) are painted with the color of a node which is
	 * present BOTH in the current community and in child1_. Note that if the current community
	 * is composed of at least one indivisible communities (e.g. after applying gMVM),
	 * child1_ + child2_ doesn't necessarily equal to the content of this community.<p>
	 * 
	 * <b>VERSION 1:</b> If the current community has depth equals to the given one and has children,
	 * all its node are painted with the color of its first child (i.e. only the nodes
	 * of the second child community are painted). If the community is indivisible,
	 * it's color is not changed. Usually you must call colorIndivisibleCommunityNodes()
	 * before calling this method. <b>NOTE:</b> The problem is if gMVM is applied, one node
	 * in an indivisible communities (there can be indivisible communities with different
	 * depthInCommunityTree_) is not necessarily in its parent community.
	 */
	public void colorCommunityNodes(int depth) throws Exception, JmodException {
		
		if ((child1_ == null && child2_ != null) || (child1_ != null && child2_ == null))
			throw new JmodException("A community must have zero or two children.");
		
		// XXX: We suppose here that settings.getUseGlobalMovingVertex() doesn't change
		// between the time of the modularity computation and exporting the dataset for
		// this experiment.
		boolean gMVMUsed = JmodSettings.getInstance().getUseGlobalMovingVertex();
		
		if (depthInCommunityTree_ == depth && child1_!= null) {
			
			if (getCommunitySize() < 1)
				return;
			
			if (gMVMUsed) {
				// VERSION 2: when gMVM is used, find a node which is both in the current
				// community and in child1_
				Color color = null;
				DenseVector child1NodeIds = child1_.getVertexIndexes();
				for (int i = 0; i < child1NodeIds.size() && color == null; i++) {
					int id = (int) child1NodeIds.get(i);
					for (int j = 0; j < vertexIndexes_.size() && color == null; j++){
						if (id == (int) vertexIndexes_.get(j)) {
							color = ColorUtils.hex2Rgb(modularityDetector_.getNetwork().getNode(
									(int) vertexIndexes_.get(j)).getGraphics().getFill().substring(1)); // remove '#'
						}
					}
				}
				if (color == null) // color with the color of the first node
					color = ColorUtils.hex2Rgb(modularityDetector_.getNetwork().getNode(
							(int) vertexIndexes_.get(0)).getGraphics().getFill().substring(1)); // remove '#'
				colorCommunityNodes(color);
			} else {
				// VERSION 1: get the color of the first node of the first child
				Color color = ColorUtils.hex2Rgb(modularityDetector_.getNetwork().getNode(
						(int) child1_.getVertexIndexes().get(0)).getGraphics().getFill().substring(1)); // remove '#'
				child2_.colorCommunityNodes(color);
			}
		}
		else if (depthInCommunityTree_ < depth && child1_!= null) {
			child1_.colorCommunityNodes(depth);
			child2_.colorCommunityNodes(depth);
		}
	}
	
	// ============================================================================
	// GETTERS AND SETTERS

	public void setName(String name) { name_ = name; }
	public String getName() { return name_; }
	
	public void setVertexIndexes(DenseVector v) { vertexIndexes_ = v; }
	public DenseVector getVertexIndexes() { return vertexIndexes_; }
	
	public void setCommunitySize(int i) { communitySize_ = i; }
	public int getCommunitySize() { return communitySize_; }
	
	public Community getChild1() { return child1_; }
	public Community getChild2() { return child2_; }
	
	public int getDepthInCommunityTree() { return depthInCommunityTree_; }
}
