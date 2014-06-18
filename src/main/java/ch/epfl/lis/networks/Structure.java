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

import java.io.FileNotFoundException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import cern.jet.random.Uniform;
import cern.jet.random.engine.MersenneTwister;
import ch.epfl.lis.networks.graphics.GraphGraphics;
import ch.epfl.lis.networks.parsers.DOTParser;
import ch.epfl.lis.networks.parsers.NETParser;
import ch.epfl.lis.networks.parsers.TSVParser;
import ch.epfl.lis.networks.parsers.GMLParser;

/**
 * Models a network structure including nodes connected by edges.<p>
 * 
 * <b>Notes:</b><p>
 * <ul>
 * <li>Two nodes refer to the same node if their name is equal
 * <li>Two edges refer to the same edge if their edge.toString() is equal
 * </ul>
 *
 * @version November 24, 2011
 * 
 * @author Thomas Schaffter (thomas.schaff...@gmail.com)
 */
public class Structure<N extends INode & IFactory<N>, E extends IEdge<N> & IFactory<E>> {
	
	/** Supported network format. */
	public static enum Format {
		/**
		 * TSV format.<p>
		 * For unsigned networks, each line defines an edge:<br>
		 * <pre>
		 * TF \tab target \tab weight
		 * </pre>
		 * where TF and target are node IDs, and weight is the weight of the interaction.
		 */
		TSV,
		GML,
		DOT,
		NET,
		UNDEFINED; // must be listed last
	}
	
	/** Seed for random number generator. Set to -1 by default to use new java.util.Date() as seed. */
	public static int uniformSeed_ = -1;
	/** Random generator with uniform distribution to pick nodes randomly. */
	public static Uniform uniform_ = null;

	/** Structure name. */
	protected String name_ = "";
	/** Comment about the structure. */
	protected String comment_ = "";
	/** Set to true if the links are directed. */
	protected boolean directed_ = true;
	/** Set to true if the edges are weighted. */
	protected boolean weighted_ = true;
	
	/** Nodes present in the structure. */
//	protected Map<N,Integer> nodes_ = null;
	protected Map<String,N> nodes_ = null;
	/** Edges present in the structure. */
	protected Map<String,E> edges_ = null;
	
	/** Factory pattern to create new node instances. */
	protected NodeFactory<N> nodeFactory_ = null;
	/** Factory pattern to create new edge instances. */
	protected EdgeFactory<E> edgeFactory_ = null;
	
	/** Graphics data. */
	protected GraphGraphics graphics_ = null;
	
	// ============================================================================
	// PROTECTED METHODS
	
	/** Initialization. */
	protected void initialize(NodeFactory<N> nodeFactory, EdgeFactory<E> edgeFactory) {
		
//		nodes_ = new HashMap<N,Integer>();
		nodes_ = new HashMap<String,N>();
		edges_ = new HashMap<String,E>();
		
		nodeFactory_ = nodeFactory;
		edgeFactory_ = edgeFactory;
		
		graphics_ = new GraphGraphics();
		
		if (uniform_ == null)
			initializeUniform();
	}
	
	// ============================================================================
	// PUBLIC METHODS
	
	/** Default constructor. */
	public Structure(NodeFactory<N> nodeFactory, EdgeFactory<E> edgeFactory) {
		
		initialize(nodeFactory, edgeFactory);	
	}
	
	// ----------------------------------------------------------------------------
	
	/** Constructor. */
	public Structure(NodeFactory<N> nodeFactory, EdgeFactory<E> edgeFactory, String name) {
		
		initialize(nodeFactory, edgeFactory);
		name_ = name;
	}
	
	// ----------------------------------------------------------------------------
	
	/** Deep copy method. */
	public Structure(Structure<N, E> structure) {
		
		initialize(structure.nodeFactory_, structure.edgeFactory_);
		
		name_ = structure.name_;
		comment_ = structure.comment_;
		directed_ = structure.directed_;
		graphics_ = structure.graphics_.clone();

		// copy nodes
		Map<String,N> nodes = structure.getNodes();
		for (Map.Entry<String,N> node : nodes.entrySet())
			nodes_.put(node.getKey(), node.getValue().copy());
//		for (N node : structure.nodes_)
//			nodes_.add(node.copy());
		
		// Edges can't be merely copied because each edge has a reference to a
		// source and target node. New edges must be created based on the ones
		// of the given structure.
		N source, target;
		Map<String,E> edges = structure.getEdges();
		for (Map.Entry<String,E> edge : edges.entrySet()) {
			source = nodes_.get(edge.getValue().getSource().getName());
			target = nodes_.get(edge.getValue().getTarget().getName());
			// create the new edge
			E e = edgeFactory_.create();
			e.setSource(source);
			e.setTarget(target);
			e.setWeight(edge.getValue().getWeight());
			// finally, add it
			edges_.put(e.toString(), e);
		}
		
		
//		Set<E> edges = structure.getEdges();
//		for (E edge : edges) {
//			source = getNode(edge.getSource().getName()); // edge belongs to other structure
//			target = getNode(edge.getTarget().getName());
//			// create the new edge
//			E e = edgeFactory_.create();
//			e.setSource(source);
//			e.setTarget(target);
//			// finally, add it
//			edges_.add(e);
//		}
	}
	
	// ----------------------------------------------------------------------------

	/** Copy method. */
	public Structure<N,E> copy() {
		
		return new Structure<N,E>(this);
	}
	
	// ----------------------------------------------------------------------------
	
	/** Returns true if the network contains the given node instance. */
	public boolean containsNode(N node) {
		
		return nodes_.containsKey(node.getName());
	}
	
	// ----------------------------------------------------------------------------
	
	/** Returns true if the network contains a node matching the given name. */
	public boolean containsNode(String name) {
		
		return nodes_.containsKey(name);
	}
	
	// ----------------------------------------------------------------------------
	
	/** Gets the index of the given node. */
//	public int getNodeIndex(N node) {
//		
//		return nodes_.indexOf(node);
//	}
	
	// ----------------------------------------------------------------------------
	
//	/**
//	 * Gets the index of the node matching the given name (return -1 if not found).<p>
//	 * The search is case insensitive.
//	 */
//	public int getNodeIndex(String node) {
//		
//		for (int i = 0; i < nodes_.size(); i++) {
//			if (nodes_.get(i).getName().equalsIgnoreCase(node))
////			if (nodes_.get(i).getName().compareTo(node) == 0)
//				return i;
//		}
//		return -1;
//	}

	// ----------------------------------------------------------------------------
	
//	/** Gets the index of the given edge instance. */
//	public int getEdgeIndex(E edge) {
//		
//		return edges_.indexOf(edge);
//	}
	
	// ----------------------------------------------------------------------------
	
	/** Returns true if the network contains the given edge instance. */
	public boolean containsEdge(E edge) {
		
		return edges_.containsKey(edge.toString());
	}
	
	// ----------------------------------------------------------------------------
	
//	/** Returns the node instance corresponding to the given node index. */
//	public N getNode(int index) {
//		
//		return nodes_.get(index);
//	}
	
	// ----------------------------------------------------------------------------
	
	/** Returns the node instance matching the given node name (return null if not found). */
	public N getNode(String name) {
		
		return nodes_.get(name);
	}
	
	// ----------------------------------------------------------------------------
	
	/** Returns the node instance matching the given node name (return null if not found). */
	public List<N> getNodes(Collection<String> names) {
		
		List<N> nodes = new ArrayList<N>();
		for (Map.Entry<String,N> node : nodes_.entrySet())
			nodes.add(node.getValue());
		
		return nodes;
	}
	
	// ----------------------------------------------------------------------------
	
	/**
	 * Adds a node to the network (ignored if a node with the same name is already included).
	 * @return Newly created or existing node associated to the given name
	 */
	public N addNode(String name) {
		
		N node = getNode(name);
		if (node == null) {
			node = getNodeFactory().create();
			node.setName(name);
			nodes_.put(name, node);
		}
		return node;
	}
	
	// ----------------------------------------------------------------------------
	
	/**
	 * Adds the given node to the network.
	 * @return Newly created or existing node with same node name.
	 */
	public N addNode(N node) {
		
		N n = getNode(node.getName());
		if (n == null) {
			nodes_.put(node.getName(), node);
			return node;
		} else
			return n;
	}
	
	// ----------------------------------------------------------------------------
	
	/** Adds the given nodes to the network (ignored if node references already included). */
	public void addNodes(Set<N> nodes) {
		
		for (N n : nodes)
			addNode(n);
	}
	
	// ----------------------------------------------------------------------------
	
	/** Adds the given nodes to the network (ignored if node references already included). */
	public void addNodes(Collection<N> nodes) {
		
		for (N n : nodes)
			addNode(n);
	}
	
	// ----------------------------------------------------------------------------
	
	/**
	 * Returns the node instance matching the given node name (return null if not found).<p>
	 * The string search is case insensitive. 
	 */
	public N getNodeIgnoreCase(String name) {

		for (Map.Entry<String,N> node : nodes_.entrySet()) {
			if (node.getKey().toLowerCase().compareTo(name.toLowerCase()) == 0)
				return node.getValue();
		}
		return null;
	}
	
	// ----------------------------------------------------------------------------
	
	/** Returns the edge instance matching the given edge name (return null if not found). */
	public E getEdge(String name) {
		
		return edges_.get(name);
	}
	
	// ----------------------------------------------------------------------------
	
	/**
	 * Adds the given edge to the network.
	 * @return Newly created or existing edge with same edge name.
	 */
	public E addEdge(E edge) {
		
		E e = getEdge(edge.toString());
		if (e == null) {
			edges_.put(edge.toString(), edge);
			return edge;
		} else
			return e;
	}
	
	// ----------------------------------------------------------------------------
	
	/** Adds the given edges to the network (ignored if already included). */
	public void addEdges(Set<E> edges) {
		
		for (E e : edges)
			addEdge(e);
	}
	
	// ----------------------------------------------------------------------------
	
	/** Adds the given edges to the network (ignored if already included). */
	public void addEdges(Collection<E> edges) {
		
		for (E e : edges)
			addEdge(e);
	}
	
	// ----------------------------------------------------------------------------
	
	/** Removes the given edge from the network. */
	public void removeEdge(E e) {
		
		edges_.remove(e.toString());
	}
	
	// ----------------------------------------------------------------------------
	
	/** Adds the nodes and edges of a structure to this structure. */
	public void addStructure(Structure<N,E> structure) {
		
		addNodes(structure.getNodes().values());
		addEdges(structure.getEdges().values());
	}
	
	// ----------------------------------------------------------------------------

//	/** Removes the given node and its edges from the network. */
//	public void removeNode(N n) {
//		
//		// remove the node
//		if (!nodes_.remove(n))
//			throw new RuntimeException("Node not found!");
//
//		// remove the corresponding edges
//		int i = 0;
//		while (i < edges_.size()) {
//			if (edges_.get(i).getSource() == n || edges_.get(i).getTarget() == n)
//				edges_.remove(i);
//			else
//				i++;
//		}
//	}
	
	// ----------------------------------------------------------------------------
	
//	/** Returns the name of all the nodes contained in the network. */
//	public List<String> getAllNodesNames() {
//		
//		List<String> names = new ArrayList<String>();
//		for (N node : nodes_)
//			names.add(node.getName());;
//		return names;
//	}
	
	// ----------------------------------------------------------------------------
	
//	/** Returns the node index of all the nodes contained in the given list. */
//	public List<Integer> getIndexesFromNodes(List<N> nodes) {
//		
//		List<Integer> output = new ArrayList<Integer>();
//		for (N name : nodes)
//			output.add(getNodeIndex(name));
//		return output;
//	}
	
	// ----------------------------------------------------------------------------
	
//	/** Returns all the nodes associated to the given list of node names. */
//	public List<N> getNodes(List<String> names) {
//		
//		List<N> nodes = new ArrayList<N>();
//		for (String name : names)
//			nodes.add(getNode(name));
//		return nodes;
//	}
	
	// ----------------------------------------------------------------------------
	
//	/** Returns the index of all the nodes associated to the given list of node names. */
//	public List<Integer> getNodeIndexesFromNodeNames(List<String> names) {
//		
//		List<Integer> indexes = new ArrayList<Integer>();
//		for (String name : names)
//			indexes.add(getNodeIndex(getNode(name)));
//		return indexes;
//	}
	
	// ----------------------------------------------------------------------------
	
	/** Gets node randomly. */
	public N getRandomNode() {
		
		int item = uniform_.nextIntFromTo(0, nodes_.size()-1);
		List<N> values = new ArrayList<N>(nodes_.values());
		
		return values.get(item);
	}
	
	// ----------------------------------------------------------------------------
	
	/** Returns the list of nodes that are neighbors of the given node. */
	public Set<N> getNeighbors(N node) {
		
		Set<N> neighbors = new HashSet<N>();
		E e = null;
		for (Map.Entry<String,E> edge : edges_.entrySet()) {
			e = edge.getValue();
			if (e.getSource().getName().compareTo(node.getName()) == 0)
				neighbors.add(e.getTarget());
			else if (e.getTarget().getName().compareTo(node.getName()) == 0)
				neighbors.add(e.getSource());
		}
		return neighbors;
	}
	
	// ----------------------------------------------------------------------------
	
	/**
	 * Gets the neighboring nodes of the given subnetwork (the set of nodes connected by
	 * one link at least to the subnetwork, but not yet part of the subnetwork).
	 */
	public Set<N> getNeighbors(Set<N> subnetwork) {
		
		Set<N> neighbors = new HashSet<N>();
		
		// add the neighbor of each node
		for (N node : subnetwork)
			neighbors.addAll(getNeighbors(node));
		
		// remove nodes that are in the subnetwork
		neighbors.removeAll(subnetwork);
		
		return neighbors;
	}
	
	// ----------------------------------------------------------------------------
	
	/** Returns the directed edge between the given source and target nodes (return null if not found). */
	public E getEdge(N source, N target) {

		E edge = edgeFactory_.create();
		edge.setSource(source);
		edge.setTarget(target);
		
		return edges_.get(edge.toString()); // uses source and target name
	}
	
	// ----------------------------------------------------------------------------
	
	/** Returns the list of edges attached to the given node (can be source or target). */
	public Set<E> getEdges(N node) {
		
		Set<E> edges = new HashSet<E>();
		E e = null;
		for (Map.Entry<String,E> edge : edges_.entrySet()) {
			e = edge.getValue();
			if (e.getSource().getName().compareTo(node.getName()) == 0 ||
					e.getTarget().getName().compareTo(node.getName()) == 0) {
				edges.add(e);
			}
		}
		return edges;
	}

	// ----------------------------------------------------------------------------
	
	/** Removes all auto-regulatory interactions (self-loops). */
	public void removeAutoregulatoryInteractions() {

		E e = null;
		for(Iterator<Map.Entry<String,E>>it = edges_.entrySet().iterator(); it.hasNext();) {		
			e = it.next().getValue();
			if (e.getSource().getName().compareTo(e.getTarget().getName()) == 0)
				it.remove();
		}
	}
	
	// ----------------------------------------------------------------------------
	
	/**
	 * Goes through all the edges and counts the number of times A->B and B->A
	 * are found. If each A->B has a B->A with same weight, B->A are
	 * removed and directed_ is set to false.
	 */
	public void cleanEdges() {

		// edges must be removed only if 2*toRemove.size() == initialNumEdges
		List<E> edges = new ArrayList<E>(edges_.values());
		List<E> toRemove = new ArrayList<E>();
		
		int numEdges = edges.size();
		int i = 0;
		while (i < numEdges) {
			E ei = edges.get(i);
			
			int j = i+1;
			while (j < numEdges) {
				E ej = edges.get(j);
				
				if (ei.getSource() == ej.getTarget() &&
					ei.getTarget() == ej.getSource() &&
					ei.getWeight() == ej.getWeight()) {
					
					toRemove.add(ej);
					break;
					
				} else
					j++;
			}
			i++;
		}
		
		if (2*toRemove.size() == numEdges) {
			for (E e : toRemove)
				removeEdge(e);
			directed_ = false;
		}
	}
	
	// ----------------------------------------------------------------------------
	
	/** Gets the number of edges, not counting the auto-regulatory edges. */
	public int getNumNotautoregulatoryEdges() {
		
		int m = 0;
		E e = null;
		for (Map.Entry<String,E> edge : edges_.entrySet()) {
			e = edge.getValue();
			if (e.getSource().getName().compareTo(e.getTarget().getName()) != 0)
				m++;
		}
		return m; 
	}
	
	// ----------------------------------------------------------------------------
	
	/** Returns true if the network has a single connected component. */
	public boolean isConnected() {
		
		// Set<N> can not have duplicated items
		Set<N> visitedNodes = depthFirstSearch(nodes_.entrySet().iterator().next().getValue());
		return (visitedNodes.size() == getSize());
	}
	
	// ----------------------------------------------------------------------------
	
	/**
	 * Returns true if the network is fully connected depending on if the network is directed or not.<p>
	 * Self-interactions are ignored. 
	 */
	public boolean isFullyConnected(boolean multipleEdges) {
		
		if (directed_ || multipleEdges)
			return (edges_.size() == (int) Math.pow(nodes_.size()-1, 2));
		else
			return (edges_.size() == (int) Math.pow(nodes_.size()-1, 2) / 2.);
	}
	
	// ----------------------------------------------------------------------------
	
	/**
	 * Returns a random edge that doesn't exist in the network.<p>
	 * Returns null if the network is fully connected or if no absent have been met
	 * before reaching the maximum number of iterations.
	 * @param multipleInteractions If true, allows to add B->A when A->B is already included
	 * @param maxNumIters Maximum number of iterations
	 */
	public E getAbsentEdgeRandomly(boolean multipleInteractions, int maxNumIters) throws Exception {
			
		// build an edge which is not already in the network and which is
		// made from two different node
		N source = null;
		N target = null;
		E edge = null;
		int numIters = 0;
		do {
			// select two different nodes
			do {
				source = getRandomNode();
				target = getRandomNode();
			} while (source == null || target == null || source == target);
			
			E forward = getEdge(source, target);
			E backward = getEdge(target, source);
			if (!multipleInteractions) {
				if (forward != null || backward != null)
					edge = null;
				else { // none of the two edges are included: add one or the other (here always forward)
					edge = edgeFactory_.create();
					edge.setSource(source);
					edge.setTarget(target);
				}
			}
			else { // multiple interactions case
				if (forward == null) { // forward is not included, we can add it
					edge = edgeFactory_.create();
					edge.setSource(source);
					edge.setTarget(target);
				} else if (backward == null) { // backward is not included, we can add it
					edge = edgeFactory_.create();
					edge.setSource(target);
					edge.setTarget(source);
				} else // both forward and backward are included, let's go for another round
					edge = null;
			}

			if (edge != null)
				return edge;
			
			numIters++;
			
		} while (numIters < maxNumIters);
		
		return null;
	}

	// ----------------------------------------------------------------------------

	/** Reads a network structure from a file URL (network format must be specify). */
	public void read(URI uri, Format format) throws FileNotFoundException, Exception, NetworkException {
		
		if (format == Format.TSV) {
			TSVParser<N, E> parser = new TSVParser<N, E>(this);
			parser.read(uri);
		} else if (format == Format.GML) {
			GMLParser<N,E> parser = new GMLParser<N, E>(this);
			parser.read(uri);
		} else if (format == Format.DOT) {
			DOTParser<N,E> parser = new DOTParser<N,E>(this);
			parser.read(uri);
		} else if (format == Format.NET) { 
			NETParser<N,E> parser = new NETParser<N,E>(this);
			parser.read(uri);
		} else
			throw new NetworkException("Unknown structure file format " + format + ".");
	}
	
	// ----------------------------------------------------------------------------
	
	/** Saves the network structure to file using the given format. */
	public void write(URI uri, Format format) throws Exception, NetworkException {

		if (format == Format.TSV)
			writeTSV(uri);
		else if (format == Format.GML)
			writeGML(uri);
		else if (format == Format.DOT)
			writeDOT(uri);
		else if (format == Format.NET)
			writePajekNET(uri);
		else
			throw new NetworkException("Unknown structure file format " + format + ".");
	}
	
	// ----------------------------------------------------------------------------
	
	/** Saves this structure in TSV format (see Structure.TSV for details). */
	public void writeTSV(URI uri) throws Exception {

		TSVParser<N, E> parser = new TSVParser<N, E>(this);
		parser.write(uri);
	}
	
	// ----------------------------------------------------------------------------
	
	/** Saves this structure into a GML format. */
	public void writeGML(URI uri) throws Exception {
		
		GMLParser<N, E> parser = new GMLParser<N, E>(this);
		parser.write(uri);
	}
	
	// ----------------------------------------------------------------------------
	
	/** Saves this structure into a DOT format. */
	public void writeDOT(URI uri) throws Exception {

		DOTParser<N, E> parser = new DOTParser<N, E>(this);
		parser.write(uri);
	}
	
	// ----------------------------------------------------------------------------
	
	/** Saves this structure into a Pajek/NET format. */
	public void writePajekNET(URI uri) throws Exception {

		NETParser<N, E> parser = new NETParser<N, E>(this);
		parser.write(uri);
	}
	
	// ----------------------------------------------------------------------------
	
	@Override
	public String toString() {
		
		return name_;
	}
	
	// ----------------------------------------------------------------------------
	
//	/**
//	 * Removes the extension of the given network filename if it ends with one of the
//	 * supported extensions.
//	 */
//	public static String removeExtension(String filename) {
//		
//		for (int i = 0; i < TYPE_EXTENSIONS.length; i++) {
//			if (filename.endsWith(TYPE_EXTENSIONS[i]))
//				return filename.substring(0, filename.length()-TYPE_EXTENSIONS[i].length()); // remove extension
//		}
//		Log.warn("The filename extension of " + filename + " is not supported. Unable to remove it.");
//		return filename;
//	}
	
	// ----------------------------------------------------------------------------
	
	/** Extracts the subnetwork corresponding to the given list of node names. */
	public Structure<N, E> getSubnetwork(String name, List<N> nodes) {
		
		Structure<N, E> network = new Structure<N, E>(nodeFactory_, edgeFactory_);
		network.name_ = name;
	
		// copy nodes
		for (N node : nodes)
			network.addNode(node.copy());
		
		// set edges
		// Note that the copy method of edge does a shallow copy of
		// the nodes, so the reference to the source and target nodes
		// must be replaced in each copied edge.
		N source = null;
		N target = null;
		E e = null;
		for (Map.Entry<String,E> edge : edges_.entrySet()) {
			source = network.getNode(edge.getValue().getSource().getName()); // null if not found
			target = network.getNode(edge.getValue().getTarget().getName()); // null if not found
			if (source != null && target != null) {
				e = edge.getValue().copy(); // shallow copy
				e.setSource(source);
				e.setTarget(target);
				network.addEdge(e);
			}
		}
		return network;
	}
	
	// ----------------------------------------------------------------------------
	
	/** Returns the network type associated to the given string. */
	public static Format getFormat(String typeStr) {
		
		for (Format format : Format.values()) {
			if (format.name().equals(typeStr))
				return format;
		}
		return Format.UNDEFINED;
	}
	
	// ----------------------------------------------------------------------------
	
	/** Returns the extension associated to the given format (includes dot). */
	public static String getFormatExtension(Format format) {
		
		return "." + format.name().toLowerCase();
	}
	
	// ----------------------------------------------------------------------------
	
	/** Returns an array that includes all the structure formats. */
	public static String[] getFormatStrings() {
		
		String[] array = new String[Format.values().length-1]; // don't include UNDEFINED
		int i = 0;
		for (Format format : Format.values()) {
			if (i < array.length)
				array[i++] = format.name();
		}
		return array;
	}
	
	// ----------------------------------------------------------------------------
	
	/** Returns an array that includes all the structure formats' extensions. */
	public static String[] getFormatExtensions() {
		
		String[] array = new String[Format.values().length-1]; // don't include UNDEFINED
		int i = 0;
		for (Format format : Format.values()) {
			if (i < array.length)
				array[i++] = getFormatExtension(format);
		}
		
		return array;
	}
	
	// ----------------------------------------------------------------------------
	
	/** Returns true if at least two edges don't have the same weight. */
	public void evaluateIsWeighted() {
		
		Iterator<E> it = edges_.values().iterator();
		double w = it.next().getWeight();
		while (it.hasNext()) {
			if (Math.abs(it.next().getWeight() - w) > 1e-12) {
				weighted_ = true;
				return;
			}
		}
		weighted_ = false;
	}
	
	// ----------------------------------------------------------------------------
	
	/**
	 * Sets the seed and initialize the Uniform random number generator.<p>
	 * If seed is -1, the seed is set to new java.util.Date().
	 */
	public void setRandomSeed(int seed) {
		Structure.uniformSeed_ = seed;
		initializeUniform();
	}
	
	// ----------------------------------------------------------------------------
	
	/** Initializes static random number generator with Uniform distribution. */
	public synchronized void initializeUniform() {
		
		MersenneTwister mersenneTwister_ = null;
		if (Structure.uniformSeed_ < 0)
			mersenneTwister_ = new MersenneTwister(new java.util.Date());
		else
			mersenneTwister_ = new MersenneTwister(Structure.uniformSeed_);
		uniform_ = new Uniform(mersenneTwister_);
	}
	
	// ----------------------------------------------------------------------------
	
	/** Returns a list of nodes ordered by node name. */
	public List<N> getNodesOrderedByNames() {
		
		List<N> nodes = new ArrayList<N>(nodes_.values());
		Collections.sort(nodes, new NodeComparator<N>());
		
		return nodes;
	}
	
	// ----------------------------------------------------------------------------
	
	/** Returns a table that map node names to unique integer ids. */
	public Map<String,Integer> getNodeNamesMap() {
		
		List<N> nodes = getNodesOrderedByNames();
		
		Map<String,Integer> map = new HashMap<String,Integer>();
		int index = 0;
		for (N node : nodes)
			map.put(node.getName(), index++);
		
		return map;
	}
	
	// ----------------------------------------------------------------------------
	
	/** 
	 * Returns the same table as {@link #getNodeNamesMap() getNodeNamesMap()} but 
	 * with unique integer ids as key.
	 */
	public Map<Integer,String> getNodeNamesInverseMap() {
		
		List<N> nodes = getNodesOrderedByNames();
		
		Map<Integer,String> map = new HashMap<Integer,String>();
		int index = 0;
		for (N node : nodes)
			map.put(index++, node.getName());
		
		return map;
	}
	
	// ============================================================================
	// PRIVATE METHODS
	
	/** Implements depth-first search and return the set of nodes visited. */
	private Set<N> depthFirstSearch(N node) {
		
		return depthFirstSearch(node, null);
	}
	
	// ----------------------------------------------------------------------------
	
	/** Implements depth-first search and return the set of nodes visited. */
	private Set<N> depthFirstSearch(N node, Set<N> visitedNodes) {
		
		if (visitedNodes == null)
			visitedNodes = new HashSet<N>();
		visitedNodes.add(node);
		
		Set<N> neighbors = getNeighbors(node);;
		for (N n : neighbors) {
			if (!visitedNodes.contains(n))
				visitedNodes.addAll(depthFirstSearch(n, visitedNodes));
		}
		return visitedNodes;
	}
	
	// ============================================================================
	// GETTERS AND SETTERS
	
	public void setNodes(Map<String,N> nodes) { nodes_ = nodes; }
	public Map<String,N> getNodes() { return nodes_; }
	
	public void setEdges(Map<String,E> edges) { edges_ = edges; }
	public Map<String,E> getEdges() { return edges_; }
	
	public void setName(String name) { name_ = name; }
	public String getName() { return name_; }
	
	public void setComment(String comment) { comment_ = comment; }
	public String getComment() { return comment_; }
	
	public int getSize() { return nodes_.size(); }
	public int getNumEdges() { return edges_.size(); }
	
	public void isDirected(boolean directed) { directed_ = directed; }
	public boolean isDirected() { return directed_; }
	
	public void isWeighted(boolean weighted) { weighted_ = weighted; }
	public boolean isWeighted() { return weighted_; }
	
	public void setNodeFactory(NodeFactory<N> nodeFactory) { nodeFactory_ = nodeFactory; }
	public NodeFactory<N> getNodeFactory() { return nodeFactory_; }
	
	public void setEdgeFactory(EdgeFactory<E> edgeFactory) { edgeFactory_ = edgeFactory; }
	public EdgeFactory<E> getEdgeFactory() { return edgeFactory_; }
	
	public void setGraphics(GraphGraphics graphics) { graphics_ = graphics; }
	public GraphGraphics getGraphics() { return graphics_; }
	
    // =======================================================================================
    // INNER CLASSES
	
	/** 
	 * Orders nodes by their name.<p>
	 * When comparing two nodes:
	 * <ul>
	 * <li>If both are integers, ascending order is used
	 * <li>If one is an integer and the second a string, the integer comes first
	 * <li>If both are strings, case is ignored and lexicographical order is used 
	 * </ul>
	 */
	public static class NodeComparator<N extends INode & IFactory<N>> implements Comparator<N> {
		
        @Override
        public int compare(N n1, N n2) {
        	
        	Integer n1Int = null;
        	Integer n2Int = null;
        	try {
        		n1Int = Integer.parseInt(n1.getName());
        	} catch (Exception e) {
        		n1Int = null;
        	}
        	try {
        		n2Int = Integer.parseInt(n2.getName());
        	} catch (Exception e) {
        		n2Int = null;
        	}
        	if (n1Int != null && n2Int != null)
        		return n1Int > n2Int ? 1 : (n2Int > n1Int) ? -1 : 0;
    		else if (n1Int != null && n2Int == null)
    			return -1;
    		else if (n1Int == null && n2Int != null)
    			return 1;
    		else
    			return n1.getName().compareToIgnoreCase(n2.getName());
        }

//		@Override
//		public int compare(N o1, N o2) {
//			
//			return o1.getName().compareTo(o2.getName());
//		}
	}
	
	// ----------------------------------------------------------------------------
	
	/** Sorts edges in lexicographical order, typically before printing. */
	public static class EdgeComparator<E extends IEdge<?> & IFactory<E>> implements Comparator<E> {

		@Override
		public int compare(E o1, E o2) {
			
			if (!o1.getSource().getName().equals(o2.getSource().getName()))
				return o1.getSource().getName().compareTo(o2.getSource().getName());
			else
				return o1.getTarget().getName().compareTo(o2.getTarget().getName());
		}
	}
	
	// ----------------------------------------------------------------------------
	
	/** Sorts edges by the indexes of their source and target nodes. */
	public static class EdgeNodeIndexComparator<N extends INode & IFactory<N>, E extends IEdge<N> & IFactory<E>> implements Comparator<E> {
		
		private Map<N,Integer> nodes_ = null;
		
		public EdgeNodeIndexComparator(Map<N,Integer> nodes) { nodes_ = nodes; }

		@Override
		public int compare(E o1, E o2) {
			
			Integer s1 = nodes_.get(o1.getSource());
			Integer s2 = nodes_.get(o2.getSource());
			Integer t1 = nodes_.get(o1.getTarget());
			Integer t2 = nodes_.get(o2.getTarget());
			
			if (s1 != s2)
				return s1 < s2 ? -1 : 1;
			else
				return t1 < t2 ? -1 : (t1 > t2 ? 1 : 0);
		}
	}
}
