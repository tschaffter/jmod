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

package ch.epfl.lis.networks;

import com.esotericsoftware.minlog.Log;

/**
 * Generates an artificial network using the hierarchical scale-free model of Ravasz et al.<p>
 * 
 * <b>Reference:</b><p>
 * 
 * E Ravasz, AL Somera, DA Mongru, ZN Oltvai, AL Barab&aacute;si. <a href="http://www.sciencemag.org/content/297/5586/1551.short" 
 * target="_blank">Hierarchical organization of modularity in metabolic networks</a>. <i>Science</i>, 
 * 297(5586):1551-1555, 2002.<p>
 *
 * Level 1 leads to network size equals to 4 nodes, level 2 to 16 nodes, level 3 to 64 nodes, etc.
 * 
 * @see Structure
 * 
 * @version 2010
 * 
 * @author Thomas Schaffter (thomas.schaff...@gmail.com)
 */
public class HierarchicalScaleFreeNetwork<N extends INode & IFactory<N>, E extends IEdge<N> & IFactory<E>> extends Structure<N,E> {

	/** Defines the number of modules created (4^{level_-1}). */
	protected int level_ = 3;
	/** Label prefix for the nodes. */
	protected String nodeNamePrefix_ = "";

	// ============================================================================
	// protected METHODS
	
    /** Creates a node module. */
	protected void generateModuleLevel(int level) {
	
		if (level <= 0)
			return;

		int moduleSize = (int) Math.pow(4, level);
		int numModules = getSize(level_)/moduleSize;

		// generate the network structure module-by-module
		E edge = null;
		for (int i = 0; i < numModules; i++) {
			int c = i*moduleSize; // centre index of the module
			// index difference between the three node p1, p2, p3
			int inc = (int)Math.pow(4, level-1);
			int start = c + inc;
			int end = (i+1)*moduleSize -1;
			
			for (int j = start; j <= end; j++) {
				edge = edgeFactory_.create();
				edge.setSource(getNode(nodeNamePrefix_ + c));
				edge.setTarget(getNode(nodeNamePrefix_ + j));
				addEdge(edge);
			}

			N node0 = getNode(nodeNamePrefix_ + start);
			N node1 = getNode(nodeNamePrefix_ + (start+inc));
			N node2 = getNode(nodeNamePrefix_ + (start+2*inc));
			
			// add edge between node0 and node2
			edge = edgeFactory_.create();
			edge.setSource(node0);
			edge.setTarget(node2);
			addEdge(edge);
			
			// add edge between node1 and node0
			edge = edgeFactory_.create();
			edge.setSource(node1);
			edge.setTarget(node0);
			addEdge(edge);
			
			// add edge between node2 and node1
			edge = edgeFactory_.create();
			edge.setSource(node2);
			edge.setTarget(node1);
			addEdge(edge);
		}
	}
	
	// ----------------------------------------------------------------------------
	
	/** Initializes the network with a size equals to 4^{level-1}. */
	protected void initialize() {
		
		directed_ = true;
		name_ = "Ravasz2002-level" + level_;
		comment_ = 	"Example network generated with the hierarchical scale-free model of " +
				"Ravasz et al. (Science, 2002, 297, 1551-1555). This network has scale-free " +
				"topology with embedded modularity similar to many biological networks";
	}
	
	// ============================================================================
	// PUBLIC METHODS
	
	/** Constructor. */
	public HierarchicalScaleFreeNetwork(NodeFactory<N> nodeFactory, EdgeFactory<E> edgeFactory, int level) {
		
		super(nodeFactory, edgeFactory);
		
		initialize();
		generate(level);
	}
	
	// ----------------------------------------------------------------------------
	
	/** Constructor. */
	public HierarchicalScaleFreeNetwork(NodeFactory<N> nodeFactory, EdgeFactory<E> edgeFactory, int level, String nodeNamePrefix) {
		
		super(nodeFactory, edgeFactory);
		
		initialize();
		nodeNamePrefix_ = nodeNamePrefix;
		generate(level);
	}
	
	// ----------------------------------------------------------------------------
	
	/** Initializes the network structure according to the given level of modules. */
	public void generate(int level) {

		nodes_.clear();
		edges_.clear();
		
		level_ = level;
		
		if (level <= 0)
			return;
		
		// add nodes
		int n = getSize(level);
		N node = null;
		for (int i = 0; i < n; i++) {
			node = nodeFactory_.create();
			node.setName(nodeNamePrefix_ + i);
			addNode(node);
		}
		
		// create the modules level by level
		for (int i = 0; i < level_; i++)
			generateModuleLevel(i+1);
	}
	
	//----------------------------------------------------------------------------
	
	/** Main method. */
	public static void main(String args[]) {
		
		try {
			// instantiate a hierarchical network with one level
			NodeFactory<Node> nodeFactory = new NodeFactory<Node>(new Node());
			EdgeFactory<Edge<Node>> edgeFactory = new EdgeFactory<Edge<Node>>(new Edge<Node>());
			HierarchicalScaleFreeNetwork<Node, Edge<Node>> structure = new HierarchicalScaleFreeNetwork<Node, Edge<Node>>(nodeFactory, edgeFactory, 1);
			
			for (int level = 1; level < 4; level++) {
				structure.generate(level);
				Log.info("HierarchicalScaleFreeNetwork structure with " + level + " level(s) of modules");
				Log.info("Number of nodes: " + structure.getSize());
				Log.info("Number of edges: " + structure.getNumEdges());
			}
			
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			Log.info("Done");
			System.exit(0);
		}
	}
	
	// ============================================================================
	// GETTERS AND SETTERS
	
	public int getLevel() { return level_; }
	
	public void setNodeNamePrefix(String nodeNamePrefix) { nodeNamePrefix_ = nodeNamePrefix; }
	public String getNodeNamePrefix() { return nodeNamePrefix_; }
	
	public int getSize(int level) { return (int) Math.pow(4, level); }
}
