/*
Copyright (c) 2008-2012 Thomas Schaffter & Daniel Marbach

We release this software open source under an MIT license (see below). If this
software was useful for your scientific work, please cite our paper(s) listed
on <http://code.google.com/p/jmod/>.

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

package ch.epfl.lis.networks.parsers;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import com.esotericsoftware.minlog.Log;

import ch.epfl.lis.networks.Edge;
import ch.epfl.lis.networks.EdgeFactory;
import ch.epfl.lis.networks.IEdge;
import ch.epfl.lis.networks.IFactory;
import ch.epfl.lis.networks.INode;
import ch.epfl.lis.networks.NetworkException;
import ch.epfl.lis.networks.Node;
import ch.epfl.lis.networks.NodeFactory;
import ch.epfl.lis.networks.Structure;

/**
 * Parses network structures in Pajek/NET format.<p>
 * 
 * A file reader for Pajek .net files. At the moment, only supports the 
 * part of the specification that defines: <br>
 * <ul>
 * <li> node ids (must be ordered from 1 to n)
 * <li> node labels (must be in quotes)
 * <li> directed edge connections "Arcs" (single or list)
 * <li> undirected edge connections "Edges" (single or list)
 * <li> edge weights (not compatible with edges specified in list form) 
 * </ul><p>
 * 
 * Keywords supported: Network, Vertices, Arcs, Arcslist, Edges, Edgeslist
 * (with or without first capital letter).<p>
 *
 * Here is an example format for a directed graph without edge weights 
 * and edges specified in list form: <br>
 * <pre>
 * *vertices <# of vertices> 
 * 1 "a" 
 * 2 "b" 
 * 3 "c" 
 * *arcslist 
 * 1 2 3 
 * 2 3  
 * </pre><p>
 *
 * Here is an example format for an undirected graph with edge weights 
 * and edges specified in non-list form: <br>
 * <pre>
 * *vertices <# of vertices> 
 * 1 "a" 
 * 2 "b" 
 * 3 "c" 
 * *edges 
 * 1 2 0.1 
 * 1 3 0.9 
 * 2 3 1.0 
 * </pre>
 * 
 * An optional fourth element on the edge lines is used to specify the
 * edge type.
 * 
 * @see Structure
 * @see IEdge
 * @see <a href="https://gephi.org/users/supported-graph-formats/pajek-net-format/" target="_blank">Pajek NET Format</a>
 * 
 * @version November 3, 2013
 * 
 * @author Thomas Schaffter (thomas.schaff...@gmail.com)
 */
public class NETParser<N extends INode & IFactory<N>, E extends IEdge<N> & IFactory<E>> extends AbstractParser<N> {
	
	/** Keyword used to identify network name (also valid without capital letter). */
	private static final String NETWORK_KEYWORD = "*Network";
	/** Keyword used to identify the beginning of the vertices definition (also valid without capital letter). */
	private static final String VERTICES_KEYWORD = "*Vertices";
	/**
	 * Keyword used to identify the beginning of arcs (directed edges) definition (also valid without capital letter).
	 * Format: single mode (one line per edges with optional weight) 
	 */
	private static final String ARCS_KEYWORD = "*Arcs";
	/**
	 * Keyword used to identify the beginning of arcs (directed edges) definition (also valid without capital letter).
	 * Format: list mode (first element on the line is a node and following elements are its neighboring nodes)
	 */
	private static final String ARCSLIST_KEYWORD = "*Arcslist";
	/**
	 * Keyword used to identify the beginning of undirected edges definition (also valid without capital letter).
	 * Format: single mode (one line per edges with optional weight) 
	 */
	private static final String EDGES_KEYWORD = "*Edges";
	/**
	 * Keyword used to identify the beginning of undirected edges definition (also valid without capital letter).
	 * Format: list mode (first element on the line is a node and following elements are its neighboring nodes)
	 */
	private static final String EDGESLIST_KEYWORD = "*Edgeslist";
	
	
	/** Network structure. */
	protected Structure<N, E> structure_ = null;
	
	/** If true, write the NETWORK_KEYWORD followed by the name of the network. */
    protected boolean writeNetworkKeyword_ = true;
    
	/** A tree map to keep track of the association node id/node (id may not start from 0). */
	protected Map<Integer,N> nodes_ = null;
	
	// ============================================================================
	// PROTECTED METHODS
	
	/**
	 * Adds a node to the structure from the given line.<p>
	 * The format of the line is: node_id \space node_name
	 */
	protected void addNode(String line) throws Exception {
		
		String[] tokens = line.split(" ");
		int nodeId = Integer.parseInt(tokens[0]);
		
		N node = nodes_.get(nodeId);
		if (node == null) {
			node = structure_.getNodeFactory().create();;
			node.setName(tokens[1].replaceAll("\"", "").trim());
			structure_.addNode(node);
			nodes_.put(nodeId, node);
		}
	}
	
	// ----------------------------------------------------------------------------

	/**
	 * Adds an edge from the given source to the given target.<p>
	 * An node is not added if the network already includes it.
	 */
	protected void addEdge(N source, N target, double weight, String type) throws Exception, NetworkException {
		
		E edge = structure_.getEdgeFactory().create();
		edge.setSource(source);
		edge.setTarget(target);
		edge.setWeight(weight);		
		structure_.addEdge(edge);
	}
	
	// ----------------------------------------------------------------------------
	
	/**
	 * Adds the arc or edge defined by the given string (2, 3, or 4 elements separated by a white space).<p>
	 * The optional fourth element is used to define the edge type.
	 * @see Structure
	 * @see IEdge
	 */
	private void addEdge(String edgeLine) throws Exception, NetworkException {
		
		String[] tokens = edgeLine.split(" ", -1);
		if (tokens.length < 2)
			throw new NetworkException("At least two node indexes must be specified for an edge (source and target index).");
		 
		double weight = 1.;
		String type = "";
		if (tokens.length > 2)
			weight = Double.parseDouble(tokens[2]);
		if (tokens.length > 3)
			type = tokens[3];
		
		// here source and target are node indexes+1
		// here nodes_ should be filled with <node index,node>
		
		N source = nodes_.get(Integer.parseInt(tokens[0]));
		N target = nodes_.get(Integer.parseInt(tokens[1]));
		
		addEdge(source, target, weight, type);
	}
	
	// ----------------------------------------------------------------------------
	
	/**
	 * Adds the arcs or edges defined by the given list.<p>
	 * The first element is the index (+1) of a node and following elements are the
	 * indexes (+1) of the neighboring nodes.<p>
	 * The edge weight and type are automatically set to 1. and Edge.UNKNOWN.
	 * @see Structure
	 * @see IEdge
	 */
	private void addEdges(String edgesLine) throws Exception, NetworkException {
		
		String[] tokens = edgesLine.split(" ", -1);
		if (tokens.length < 2)
			throw new NetworkException("At least two node indexes must be specified for an edge (source and target index).");
		
		N source = nodes_.get(Integer.parseInt(tokens[0]));
		for (int i = 1; i < tokens.length; i++)
			addEdge(source, nodes_.get(Integer.parseInt(tokens[1])), 1., "");
	}
	
	// ----------------------------------------------------------------------------
	
	/** Returns true if the line is a comment (line starting with "%" or "/*"). */
	protected boolean isCommentLine(String str) {
		
		return str.startsWith("/*") || str.startsWith("%");
	}
	
	// ----------------------------------------------------------------------------
	
	/** Returns true if the given string matches the given keyword. */
	private boolean isValidKeyword(String str, String keyword) {
		
		return (str.startsWith(keyword) || str.startsWith(keyword.toLowerCase()));
	}
	
	// ----------------------------------------------------------------------------
	
	/** Returns the network name following *Network if found, otherwise returns null. */
	protected String extractNetworkName(String str) {
		
		if (isValidKeyword(str, NETWORK_KEYWORD)) {
			str = str.substring(NETWORK_KEYWORD.length());
			for (String s : str.split(" ")) {
				if (!s.isEmpty())
					return s.trim().replaceAll("\"", "");
			}
		}
		return null;
	}
	
	// ----------------------------------------------------------------------------
	
	/** Returns true if the line starts with "*Vertices". */
	private boolean detectVerticesKeyword(String str) { return isValidKeyword(str, VERTICES_KEYWORD); }
	/** Returns true if the line starts with *Arcs. */
	private boolean detectArcsKeyword(String str) { return isValidKeyword(str, ARCS_KEYWORD); }  
	/** Returns true if the line starts with *Arcslist. */
	private boolean detectArcslistKeyword(String str) { return isValidKeyword(str, ARCSLIST_KEYWORD); }
	/** Returns true if the line starts with *Edges. */
	private boolean detectEdgesKeyword(String str) { return isValidKeyword(str, EDGES_KEYWORD); }
	/** Returns true if the line starts with *Edgeslist. */
	private boolean detectEdgeslistKeyword(String str) { return isValidKeyword(str, EDGESLIST_KEYWORD); } 
	
	// ============================================================================
	// PUBLIC METHODS
	
	/** Constructor */
	public NETParser(Structure<N, E> structure) {
		
		super();

		structure_ = structure;
		nodes_ = new TreeMap<Integer,N>();
	}
	
	// ----------------------------------------------------------------------------
	
	/** Remove the leading and trailing quotes from a given string */
	public static String stripLeadingAndTrailingQuotes(String str) {
		
		if (str.startsWith("\""))
			str = str.substring(1, str.length());
		if (str.endsWith("\""))
			str = str.substring(0, str.length() - 1);

		return str;
	}
	
	// ----------------------------------------------------------------------------
	
	/**
	 * Loads structure in Pajek/NET format.<p>
	 * Undirected edges are not added twice to the list of edges. Instead, the flag
	 * Structure.directed_ is set to false.
	 */
	public void read(URI uri) throws Exception, NetworkException {
		
		InputStream stream = null;
		try {
			if (structure_ == null)
				throw new NetworkException("structure_ is null.");
			
			structure_.getNodes().clear();
			structure_.getEdges().clear();
			
			stream = uri.toURL().openStream();
			BufferedReader reader = new BufferedReader(new InputStreamReader(stream));
			
			boolean network = false; // optional
			boolean vertices = false;
			boolean arcs = false;
			boolean arcslist = false;
			boolean edges = false;
			boolean edgeslist = false;
			
			String networkName = null;
			String nextLine = null;
			String result = null;
			while ((nextLine = reader.readLine()) != null) {
				
				if (isCommentLine(nextLine) || nextLine.isEmpty())
					continue;

				if (!network && (result = this.extractNetworkName(nextLine)) != null) {
					networkName = result; //structure_.setName(result);
					network = true;
					continue;
				}
				
				if (!vertices) {
					vertices = detectVerticesKeyword(nextLine);
					continue;
					
				} else if (!arcs && !arcslist && !edges && !edgeslist) {
					if (arcs = this.detectArcsKeyword(nextLine)) continue;
					if (arcslist = this.detectArcslistKeyword(nextLine)) continue;
					if (edges = this.detectEdgesKeyword(nextLine)) continue;
					if (edgeslist = this.detectEdgeslistKeyword(nextLine)) continue;
					
					// add vertex
					addNode(nextLine);
					
				} else {
					if (nextLine.isEmpty())
						continue;
					
					if (arcs) addEdge(nextLine); 		// structure_.setDirected(true); below
					if (edges) addEdge(nextLine);		// structure_.setDirected(false); below
					if (arcslist) addEdges(nextLine);	// structure_.setDirected(true); below
					if (edgeslist) addEdges(nextLine);	// structure_.setDirected(false); below
				}		
			}
	
			File f = new File(uri);
			if (networkName == null || networkName.isEmpty())
				networkName = f.getName();
	
			structure_.setName(networkName);
			structure_.setComment("");
			structure_.isDirected(arcs || arcslist);
			
			// Pajek files should not contain A->B and B->A as an undirected edge
			// should be specified using *Edges or *Edgeslist and by writing A->B
			// Just to be sure, B->A are deleted only if each A->B has a B->A
			structure_.cleanEdges();
			structure_.evaluateIsWeighted();
			
			// Force to consider the network as directed if "_directed" is included
			// in the filename, and as undirected if "_undirected" is included.
			if (f.getName().contains("_directed")) {
				Log.debug("Network filename tells that the structure is directed.");
				structure_.isDirected(true);
			} else if (f.getName().contains("_undirected")) {
				Log.debug("Network filename tells that the structure is undirected.");
				structure_.isDirected(false);
			}
			// Force to consider the network as weighted if "_weighted" is included
			// in the filename, and as unweighted if "_unweighted" is included.
			if (f.getName().contains("_weighted")) {
				Log.debug("Network filename tells that the structure is weighted.");
				structure_.isWeighted(true);
			} else if (f.getName().contains("_unweighted")) {
				Log.debug("Network filename tells that the structure is unweighted.");
				structure_.isWeighted(false);
			}
			
			if (structure_.getSize() == 0)
				throw new NetworkException("The structure doesn't contain any nodes.");
			if (structure_.getNumEdges() == 0)
				throw new NetworkException("The structure doesn't contain any edges.");
			
		} catch (Exception e) {
			Log.error("Unable to read network from Pajek/net file format.");
			throw e;
		} finally {
			if (stream != null)
				stream.close();
		}
	}
	
	// ----------------------------------------------------------------------------
	
	/**
	 * Saves structure to Pajek/NET file.<p>
	 * Undirected edges are not written twice; the direction information
	 * is already given in *Arcs or *Edges.
	 * @param nodeFormat Specifies if the node name or node index must be saved.
	 * @param saveEdgeType Specifies whether edge types must be saved.
	 */
	public void write(URI uri) throws Exception, NetworkException {
		
		if (structure_ == null)
			throw new NetworkException("structure_ is null.");	
		
		BufferedWriter bf = null;
		try {
			bf = new BufferedWriter(new FileWriter(uri.getPath()));
			
			if (writeNetworkKeyword_)
				bf.write(NETWORK_KEYWORD + " " + "\"" + structure_.getName() + "\"" + System.lineSeparator());
			
			bf.write(VERTICES_KEYWORD + " " + structure_.getSize() + System.lineSeparator());
			
			// get nodes in lexicographical order
			List<N> orderedNodes = new ArrayList<N>(structure_.getNodes().values());
			Collections.sort(orderedNodes, new Structure.NodeComparator<N>());
			
			// write nodes + associate node id to node objects
			Map<N,Integer> nodes = new HashMap<N,Integer>();
			for (int i = 0; i < orderedNodes.size(); i++) {
				bf.write("" + (i+1) + " \"" + orderedNodes.get(i).getName() + "\"" + System.lineSeparator());
				nodes.put(orderedNodes.get(i), i+1); // required for writing edges
			}
			
			if (structure_.isDirected())
				bf.write(ARCS_KEYWORD + System.lineSeparator());
			else
				bf.write(EDGES_KEYWORD + System.lineSeparator());
			
			// undirected edges are not written twice; the direction information
			// is already given in *Arcs or *Edges.
			
			// get an ordered list of edges
			List<E> edges = new ArrayList<E>(structure_.getEdges().values());
			Collections.sort(edges, new Structure.EdgeNodeIndexComparator<N,E>(nodes));
			
			for (E e : edges) {
				bf.write(nodes.get(e.getSource()) + " " + nodes.get(e.getTarget()));			
				if (saveEdgeWeight_ && structure_.isWeighted())
					bf.write(" " + Double.toString(e.getWeight()));
//				if (saveEdgeType_ && structure_.isSigned())
//					bf.write(" " + e.getTypeString());
				bf.write(System.lineSeparator());
			}
		} catch (IOException e) {
			Log.error("Unable to write network to Pajek/net file format.");
			throw e;
		} finally {
			if (bf != null)
				bf.close();
		}
	}
	
	//----------------------------------------------------------------------------
	
	/** Main method. */
	public static void main(String args[]) {
		
		try {
			// instantiate structure and parser
			NodeFactory<Node> nodeFactory = new NodeFactory<Node>(new Node());
			EdgeFactory<Edge<Node>> edgeFactory = new EdgeFactory<Edge<Node>>(new Edge<Node>());
			Structure<Node, Edge<Node>> structure = new Structure<Node, Edge<Node>>(nodeFactory, edgeFactory);

			Log.info("Reading network structure in Pajek/NET format");
			NETParser<Node, Edge<Node>> parser = new NETParser<Node, Edge<Node>>(structure);
			parser.read(new File("rsc/parsers/structure.net").toURI());
			Log.info("Number of nodes: " + structure.getSize());
			Log.info("Number of edges: " + structure.getNumEdges());
			Log.info("Weighted: " + structure.isWeighted());
			Log.info("Directed: " + structure.isDirected());
			
			// writing structure to file
			NETParser<Node, Edge<Node>> netParser = new NETParser<Node, Edge<Node>>(structure);
			Log.info("Writing network structure in Pajek/NET format");
			netParser.write(new File("rsc/parsers/structure_w.net").toURI());

		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			Log.info("Done");
			System.exit(0);
		}
	}
	
	// ============================================================================
	// SETTERS AND GETTERS
	
	public Structure<N, E> getStructure() { return structure_; }
	public void setStructure(Structure<N, E> structure) { structure_ = structure; }
	
	/** If true, write the NETWORK_KEYWORD followed by the name of the network. */
	public void writeNetworkKeyword(boolean b) { writeNetworkKeyword_ = b; }
}
