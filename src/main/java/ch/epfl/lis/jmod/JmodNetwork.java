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

package ch.epfl.lis.jmod;

import java.io.File;
import java.io.FileNotFoundException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import junit.framework.Assert;

import com.esotericsoftware.minlog.Log;

import ch.epfl.lis.networks.Edge;
import ch.epfl.lis.networks.EdgeFactory;
import ch.epfl.lis.networks.Node;
import ch.epfl.lis.networks.NodeFactory;
import ch.epfl.lis.networks.Structure;

/**  
 * Models a network structure compatible with modularity computation.<p>
 * 
 * The variable names are the ones used by Mark Newman in his paper form 2006.<p>
 * 
 * <b>Reference:</b><p>
 * 
 * M Newman. <a href="http://www.pnas.org/content/103/23/8577.short" target="_blank">Modularity 
 * and community structure in networks</a>. <i>PNAS</i>, 103(23):8577-8582, 2006.
 * 
 * @see Structure
 * 
 * @version November 26, 2011
 * 
 * @author Thomas Schaffter (thomas.schaff...@gmail.com)
 */
public class JmodNetwork extends Structure<Node,Edge<Node>> {
	
	/** Ordered list of nodes (sorted by node name in lexicographical order). */
	private List<Node> orderedNodes_ = null;
	
	/** Edge weights. */
	private Map<Integer,Map<Integer,Double>> weights_ = null;
	
	/**
	 * Undirected adjacency matrix (a_ij = a_ji).<p>
	 * For the computation of the modularity matrix, weights are
	 * taken directly from edges. An alternative to A_ and weights_
	 * could be to use a sparse matrix, however here if the network
	 * is undirected, only boolean[][] A_ is used.
	 */
	private boolean[][] A_ = null;
	
	/** Lists the node degrees. */
	private int[] k_ = null;
	/**
	 * Number of edges of the complete network.<p>
	 * For now we consider undirected and unweighted edges, but Structure still conserves
	 * all edge, even backward edges. Self-edges are also removed. Here m includes only
	 * one edge between two nodes even if there is a backward edge. As in Newman2006, 
	 * m must equal 0.5*\sum_i k_i.
	 */
	private int m_ = 0;
	
	// ============================================================================
	// PUBLIC METHODS
	
    /** Default constructor. */
	public JmodNetwork() {
		
		super(new NodeFactory<Node>(new Node()), new EdgeFactory<Edge<Node>>(new Edge<Node>()));
	}
	
	// ----------------------------------------------------------------------------
	
	/** Constructor. */
	public JmodNetwork(Structure<Node, Edge<Node>> structure) {
		
		super(structure);
	}	
	
	// ----------------------------------------------------------------------------

	/**
	 * Initializes the adjacency matrix (A_) from the list of edges (edges_).<p>
	 * We do not use the functions of the class Structure because we need the undirected
	 * adjacency matrix and the degrees without self-interactions and multi-edges.<p>
	 * Also, if there are both an edge and a backward edge, it must be counted as
	 * a single edge, which was not the case before January 19, 2012.
	 */
	public void initializeModularityDetectionVariables() {
		
		int size = getSize();
		A_ = new boolean[size][size];
		
		// get nodes in lexicographical order
		orderedNodes_ = new ArrayList<Node>(nodes_.values());
		Collections.sort(orderedNodes_, new Structure.NodeComparator<Node>());
		
		// map to decode source/target node into index in orderedNodes_
		Map<Node,Integer> nodeToIndexMap = new HashMap<Node,Integer>();
		for (int i = 0; i < orderedNodes_.size(); i++)
			nodeToIndexMap.put(orderedNodes_.get(i), i);
		
		// set A (refer to order of orderedNodes_)
		int sourceNodeIndex = 0;
		int targetNodeIndex = 0;
		Edge<Node> e = null;
		boolean weighted = isWeighted();
		
		if (weighted)
			weights_ = new HashMap<Integer,Map<Integer,Double>>();
		
		for (Map.Entry<String,Edge<Node>> edge : edges_.entrySet()) {
			e = edge.getValue();			
			sourceNodeIndex = nodeToIndexMap.get(e.getSource());
			targetNodeIndex = nodeToIndexMap.get(e.getTarget());

			// not count self-edge
			if (targetNodeIndex != sourceNodeIndex) { // symmetric for now
				A_[targetNodeIndex][sourceNodeIndex] = true;
				A_[sourceNodeIndex][targetNodeIndex] = true;
				if (weighted) {
					setWeight(sourceNodeIndex, targetNodeIndex, e.getWeight());
					setWeight(targetNodeIndex, sourceNodeIndex, e.getWeight());
				}
			}
		}
		
		// now the degrees of the undirected network without multi-edges
		k_ = new int[size];
		for (int i = 0; i < size; i++)
			k_[i] = 0;
		
		// set m by looking only on the upper (or lower) triangle of A with discarding
		// self-edges.
		// XXX weight the degree if network is weighted ?
		m_ = 0;
		for (int i = 0; i < size; i++) {
			for (int j = i+1; j < size; j++) { // self-edges are not counted
				if (A_[i][j]) {
					m_++;
					k_[i]++;
					k_[j]++;
				}
			}
		}
		
		// assert that m = 0.5 * \sum_i k_i (Newman2006)
		int m2 = 0;
		for (int i = 0; i < size; i++)
			m2 += k_[i];
		m2 /= 2;
		Assert.assertEquals(m_, m2, 0);
	}
	
	// ----------------------------------------------------------------------------
	
	@Override
	public void read(URI uri, Structure.Format format) throws FileNotFoundException, Exception {
		
		super.read(uri, format);
		
		// remove multiple edges
		String str = "Contains " + getSize() + " nodes and " + getNumEdges();
		if (directed_) str += " directed";
		else str += " undirected";
		
		if (isWeighted()) str += " weighted";
		else str += " unweighted";
		
		str += " edges";
		
		Log.info(name_, str);
	}
	
	// ----------------------------------------------------------------------------
	
	/** Returns the average node degree &lt;k&gt;. */
	public double computeAverageNodeDegree() {
		
		int ksum = 0;
		for (int i = 0; i < k_.length; i++)
			ksum += k_[i];
		
		return (double) ksum / k_.length;
	}
	
	// ----------------------------------------------------------------------------
	
	/** Returns all the nodes associated to the given list of node indexes. */
	public List<Node> getNodesFromIndexes(List<Integer> indexes) {
		
		List<Node> output = new ArrayList<Node>();
		for (Integer i : indexes)
			output.add(orderedNodes_.get(i));
		return output;
	}
	
	// ----------------------------------------------------------------------------
	
	/** Extracts the subnetwork corresponding to the given list of node names. */
	public Structure<Node, Edge<Node>> getSubnetworkFromNodeNames(String name, List<String> nodeNames) {
		
		List<Node> nodes = getNodes(nodeNames);
		return getSubnetwork(name, nodes);
	}
	
	// ----------------------------------------------------------------------------
	
	/** Returns all the nodes associated to the given list of node indexes. */
	public List<Node> getNodesFromIndexes(int[] indexes) {
		
		List<Node> output = new ArrayList<Node>();
		for (int i = 0; i < indexes.length; i++)
			output.add(orderedNodes_.get(indexes[i]));
		return output;
	}
	
	// ----------------------------------------------------------------------------
	
	/** Extracts the subnetwork corresponding to the given list of node indexes. */
	public Structure<Node, Edge<Node>> getSubnetworkFromNodeIndexes(String name, List<Integer> nodeIndexes) {
		
		List<Node> nodes = getNodesFromIndexes(nodeIndexes);
		return getSubnetwork(name, nodes);
	}
	
	// ----------------------------------------------------------------------------
	
	/** Extracts the subnetwork corresponding to the given list of node indexes. */
	public Structure<Node, Edge<Node>> getSubnetworkFromNodeIndexes(String name, int[] nodeIndexes) {
		
		List<Node> nodes = getNodesFromIndexes(nodeIndexes);
		return getSubnetwork(name, nodes);
	}
	
	// ----------------------------------------------------------------------------
	
	/** Main method. */
	public static void main(String args[]) {
		
		try {
			JmodNetwork network = new JmodNetwork();
			network.read(new File("rsc/jmod/polbooks.gml").toURI(), Structure.Format.GML);
			network.initializeModularityDetectionVariables();
			
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			Log.info("Done");
			System.exit(0);
		}
	}
	
	// ============================================================================
	// PRIVATE METHODS
	
	/** Sets edge weight for the given source and target node index. */
	public void setWeight(int sourceIndex, int targetIndex, double weight) {
		
		Map<Integer,Double> y = weights_.get(sourceIndex);
		if (y == null) {
			y = new HashMap<Integer,Double>();
			y.put(targetIndex, weight);
			weights_.put(sourceIndex, y);
		} else
			y.put(targetIndex, weight);
	}

	// ============================================================================
	// GETTERS AND SETTERS
	
	public void setA(boolean[][] A) { A_ = A; }
	public boolean[][] getA() { return A_; }
	
	public void setK(int[] k) { k_ = k; }
	public int[] getK() { return k_; }

	public void setM(int m) { m_ = m; }
	public int getM() { return m_; }
	
	public Node getNode(int nodeIndex) { return orderedNodes_.get(nodeIndex); }
	public List<Node> getOrderedNodes() { return orderedNodes_; }
	
	public Double getWeight(int sourceIndex, int targetIndex) {
		return weights_.get(sourceIndex).get(targetIndex);
	}
}
