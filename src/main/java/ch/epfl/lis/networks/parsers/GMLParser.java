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

package ch.epfl.lis.networks.parsers;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.InputStream;
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
import ch.epfl.lis.networks.graphics.EdgeGraphics;
import ch.epfl.lis.networks.graphics.NodeGraphics;

/**
 * Parses network structures in DOT format (read and write).<p>
 * 
 * This parser enables to read and write a network structure from/to GML format.<p>
 * 
 * Principal features supported by this parser:<br>
 * <ul>
 * <li> direct/undirected graph
 * <li> possibility to save a comment attached to the graph
 * <li> node position (node attribute "position")
 * <li> edge weight and type
 * </ul> <p>
 * 
 * @see Structure
 * @see Edge
 * @see <a href="http://en.wikipedia.org/wiki/Graph_Modelling_Language" target="_blank">Wikipeda: Graph Modelling Language</a>
 * 
 * @version November 3, 2013
 * 
 * @author Thomas Schaffter (thomas.schaff...@gmail.com)
 *
 */
public class GMLParser<N extends INode & IFactory<N>, E extends IEdge<N> & IFactory<E>> extends AbstractParser<N> {
	
	/** Creator name displayed on the first line of the GML file. */
	public static String CREATOR = "";
	/** Version displayed on the second line of the GML file. */
	public static String VERSION = "";
	
	/** Structure instance to load or export. */
	protected Structure<N, E> structure_ = null;
	
	/** A tree map to keep track of the association node id/node (id may not start from 0). */
	protected Map<Integer,N> nodes_ = null;
	
	/** Variable name of edge weights (e.g., "weight", "value", etc.). */
	protected String edgeWeightVarName_ = "weight";
    
	// ============================================================================
	// PUBLIC METHODS
	
    /** Constructor. */
	public GMLParser(Structure<N,E> structure) {

		structure_ = structure;
		nodes_ = new TreeMap<Integer,N>();
	}
	
	// ----------------------------------------------------------------------------
	
	/** Loads structure in GML file. */
	public void read(URI uri) throws Exception, NetworkException, NetworkException {
		
		try {
			if (structure_ == null)
				throw new NetworkException("structure_ is null.");
			
			structure_.getNodes().clear();
			structure_.getEdges().clear();
	
			InputStream stream = uri.toURL().openStream();
			GMLLexer lexer = new GMLLexer(stream);
			
			GMLObject GMLgraph = new GMLObject(lexer, null);
			GMLObject GMLtmp;
	
			// if the GML doesn't contain a "graph", assume it is a graph.
			GMLtmp = GMLgraph.getGMLSubObject("graph", GMLObject.GMLlist, false);
			if (GMLtmp != null)
				GMLgraph = GMLtmp;
			
			 // get the graph attributes
	         Integer tmp;
	         String tmpComment = "";
	         boolean directed = false;
	         
	         // get the topology type of the graph (directed/undirected)
	         if ((tmp = (Integer)GMLgraph.getValue("directed", GMLObject.GMLinteger)) != null)
	            directed = (tmp.intValue() != 0);
	         
	         // get the eventual comment
	         if ((tmpComment = (String)GMLgraph.getValue("comment", GMLObject.GMLstring)) == null)
	        	 tmpComment = "";
	         
	         // no graph graphics read
	        
	         // Read the nodes. The parameter id is required. Note that the node
	         // ids can be any integer and don't necessary start at 0.
	         Integer id;
	         String label;
	         
	         GMLObject nodegml;
	         for (nodegml = GMLgraph.getGMLSubObject("node", GMLObject.GMLlist, false);
	             nodegml != null; nodegml = GMLgraph.getNextGMLSubObject()) {
	        	 
	        	 if ((id = (Integer)nodegml.getValue("id", GMLObject.GMLinteger)) == null)
	        		 throw new NetworkException("Id field missing at one Node.");
	        	 else {
	        		 // create node
	        		 N node = structure_.getNodeFactory().create();
	        		 
	        		 label = (String)nodegml.getValue("label", GMLObject.GMLstring);
	        		 // if there is no label defined, the id is get as label
	        		 if (label == null)
	        			 label = String.valueOf(id);
	        		 
	        		 // check if Cytoscape "graphics" data structure is there
	        		 GMLObject cysGraphics = null;
	        		 if ((cysGraphics = (GMLObject)nodegml.getGMLSubObject("graphics", GMLObject.GMLlist, false)) != null) {
	        			 NodeGraphics cysNodeGraphics = node.getGraphics();
	        			 cysNodeGraphics.setLabel(label);
	        			 try { cysNodeGraphics.setX((Double)cysGraphics.getValue("x", GMLObject.GMLreal)); } catch(Exception e) {}
	        			 try { cysNodeGraphics.setY((Double)cysGraphics.getValue("y", GMLObject.GMLreal)); } catch(Exception e) {}
	        			 try { cysNodeGraphics.setW((Double)cysGraphics.getValue("w", GMLObject.GMLreal)); } catch(Exception e) {}
	        			 try { cysNodeGraphics.setH((Double)cysGraphics.getValue("h", GMLObject.GMLreal)); } catch(Exception e) {}
	        			 cysNodeGraphics.setFill((String)cysGraphics.getValue("fill", GMLObject.GMLstring));
	        			 cysNodeGraphics.setType((String)cysGraphics.getValue("type", GMLObject.GMLstring));
	        			 cysNodeGraphics.setOutline((String)cysGraphics.getValue("outline", GMLObject.GMLstring));
	        			 try { cysNodeGraphics.setOutlineWidth((Double)cysGraphics.getValue("outline_width", GMLObject.GMLreal)); } catch(Exception e) {}
	        		 }
	
	        		 node.setName(label);
	        		 structure_.addNode(node);
	        		 nodes_.put(id, node);
	        	 }
	         }
	         
	         // needed to get N from node index
//			nodes_.clear();
//			Map<N,Integer> structureNodes = structure_.getNodes();
//			for (Map.Entry<N,Integer> node : structureNodes.entrySet())
//				nodes_.put(Integer.toString(node.getValue()), node.getKey());
	         
	         // Read the edges. Required at least source and target.
	         Integer sourceId, targetId;
	         Double weight = 1.;

	         GMLObject edgegml;
	         for (edgegml = GMLgraph.getGMLSubObject("edge", GMLObject.GMLlist, false);
	             edgegml != null; edgegml = GMLgraph.getNextGMLSubObject()) {
	
	        	 if ((sourceId = (Integer)edgegml.getValue("source", GMLObject.GMLinteger)) == null)
	        		 throw new NetworkException("Source field missing at one Edge.");
	        	 if ((targetId = (Integer)edgegml.getValue("target", GMLObject.GMLinteger)) == null)
	        		 throw new NetworkException("Target field missing at one Edge.");
	        	 
	        	 // create edge
	        	 E edge = structure_.getEdgeFactory().create();
	
	        	 weight = (Double) edgegml.getValue(edgeWeightVarName_, GMLObject.GMLreal);
	        	 if (weight == null)
	        		 weight = 1.;
	        	 
//	        	 type = (String) edgegml.getValue("type", GMLObject.GMLstring);
//		         if (type == null /*|| type.equals("")*/)
//		        	 type = "?";
		         
        		 // check if Cytoscape "graphics" data structure is there
        		 GMLObject cysGraphics = null;
        		 
        		 if ((cysGraphics = (GMLObject)edgegml.getGMLSubObject("graphics", GMLObject.GMLlist, false)) != null) {
        			 EdgeGraphics cysEdgeGraphics = edge.getGraphics();
        			 try { cysEdgeGraphics.setWidth((Double)cysGraphics.getValue("width", GMLObject.GMLreal)); } catch(Exception e) {}
        			 cysEdgeGraphics.setFill((String)cysGraphics.getValue("fill", GMLObject.GMLstring));
//        			 cysEdgeGraphics.setType((String)cysGraphics.getValue("type", GMLObject.GMLstring));
        		 }
		        	 
		         // do not add the edge if sourceId and targetId are null
        		 if(sourceId != null && targetId != null) {
        			 edge.setSource(nodes_.get(sourceId));
        			 edge.setTarget(nodes_.get(targetId));
        			 edge.setWeight(weight);
//        			 edge.setType(Edge.getTypeFormString(type));
        			 structure_.addEdge(edge);
        		 }
	         }
			
	         File f = new File(uri);
	         structure_.setName(f.getName());
	         structure_.isDirected(directed);
	         structure_.setComment(tmpComment);
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
			Log.error("Unable to open GML network file.", e);
			throw e;
		}
	}
			
	// ----------------------------------------------------------------------------
	
	/** Saves structure to GML file. */
	public void write(URI uri/*, boolean saveNodePostion, boolean saveNodeFillColor*/) throws Exception {
		
		if (structure_ == null)
			throw new NetworkException("structure_ is null.");
		
		BufferedWriter bf = null;
		try {
			bf = new BufferedWriter(new FileWriter(uri.getPath()));
			
			String line = "";
			boolean isDirected = structure_.isDirected();
			String comment = structure_.getComment();

			if (!CREATOR.isEmpty())
				bf.write("Creator \"" + CREATOR + "\"" + System.lineSeparator());
			if (!VERSION.isEmpty())
				bf.write("Creator \"" + VERSION + "\"" + System.lineSeparator());
			
			bf.write("graph" + System.lineSeparator() + "[" + System.lineSeparator());
			bf.write("\tcomment \"" + comment + "\"" + System.lineSeparator());
			bf.write("\tdirected " + (isDirected ? 1 : 0) + System.lineSeparator());
			
//			// organize the node to be more easily written
//			// assumes that node indexes starts from 0 and are a consecutive list
//			nodes_.clear();
//			Map<N,Integer> structureNodes = structure_.getNodes();
//			for (Map.Entry<N,Integer> node : structureNodes.entrySet())
//				nodes_.put(node.getValue(), node.getKey());
			
			
			// get nodes in lexicographical order
//			List<N> orderedNodes = new ArrayList<N>(structure_.getNodes().values());
//			Collections.sort(orderedNodes, new Structure.NodeComparator<N>());
			List<N> orderedNodes = structure_.getNodesOrderedByNames();
			
			// associate node id to node objects
			Map<N,Integer> nodes = new HashMap<N,Integer>();
			for (int i = 0; i < orderedNodes.size(); i++)
				nodes.put(orderedNodes.get(i), i); // required for writing edges
			
			// write nodes
			for (int i = 0; i < orderedNodes.size(); i++) {
				String nodeName = orderedNodes.get(i).getName();
				
				line = "\tnode" + System.lineSeparator() + 
						"\t[" + System.lineSeparator() + 
							"\t\tid " + i + System.lineSeparator() +
							"\t\tlabel \"" + nodeName + "\"" + System.lineSeparator();
				
				// node graphics
				if (saveGraphics_) {
					NodeGraphics cysNodeGraphics = orderedNodes.get(i).getGraphics();
					if (cysNodeGraphics != null)
						line += cysNodeGraphics.toGmlString("\t\t");
				}
				
				line += "\t]" + System.lineSeparator();
				bf.write(line);
			}
			
			// get an ordered list of edges
			List<E> edges = new ArrayList<E>(structure_.getEdges().values());
			Collections.sort(edges, new Structure.EdgeComparator<E>());
			
			// write edges
			int sourceId, targetId;
			double weight = 1.;
			for (E e : edges) {
				sourceId = nodes.get(e.getSource());
				targetId = nodes.get(e.getTarget());
				weight = e.getWeight();
				
				bf.write("\tedge" + System.lineSeparator() + 
						"\t[" + System.lineSeparator() + 
							"\t\tsource " + sourceId + System.lineSeparator() +
							"\t\ttarget " + targetId + System.lineSeparator());
				
				if (saveEdgeWeight_ && structure_.isWeighted())
					bf.write("\t\t" + edgeWeightVarName_ + " " + Double.toString(weight) + System.lineSeparator());
//				if (saveEdgeType_ && structure_.isSigned())
//					bf.write("\t\ttype " + value + System.lineSeparator());
				
				// edge graphics
				if (saveGraphics_) {
					EdgeGraphics cysEdgeGraphics = e.getGraphics();
					if (cysEdgeGraphics != null)
						bf.write(cysEdgeGraphics.toGmlString("\t\t"));
				}
				
				bf.write("\t]" + System.lineSeparator());
			}
			bf.write("]");
			
		} catch (Exception e) {
			Log.error("Unable to write network to GML file format.");
			throw e;
		} finally {
			if (bf != null)
				bf.close();
		}
	}
    
    // ----------------------------------------------------------------------------
    
    /**
     * The center position of a node can be specified with the node attribute "position", for instance:<br>
     * <pre>
     * e.g. "G1" [position="1,1"];
     * </pre><p>
     * This function take the string "1,1" in the example and convert it into
     * an array of integer number.
     */
    public int[] extractPositionFromDotAttribute(String att) throws Exception, NetworkException, NumberFormatException {
    	
    	int[] output = new int[2];
    	String item[] = att.split(",");
    	if (item == null || item.length < 2)
    		throw new NetworkException("Coordinate(s) missing");
    	
    	// if more than two coordinates, take only the two first
    	for (int i = 0; i < 2; i++)
    		output[i] = Integer.parseInt(item[i]);
    	
    	return output;
    }
    
	//----------------------------------------------------------------------------
	
	/** Main method. */
	public static void main(String args[]) {
		
		try {
			// instantiate structure and parser
			NodeFactory<Node> nodeFactory = new NodeFactory<Node>(new Node());
			EdgeFactory<Edge<Node>> edgeFactory = new EdgeFactory<Edge<Node>>(new Edge<Node>());
			Structure<Node, Edge<Node>> structure = new Structure<Node, Edge<Node>>(nodeFactory, edgeFactory);
			GMLParser<Node, Edge<Node>> parser = new GMLParser<Node, Edge<Node>>(structure);
			
			// read structure from file
			Log.info("Reading network structure in GML format");
			parser.read(new File("rsc/parsers/structure.gml").toURI());
			Log.info("Number of nodes: " + structure.getSize());
			Log.info("Number of edges: " + structure.getNumEdges());
			Log.info("Weighted: " + structure.isWeighted());
			Log.info("Directed: " + structure.isDirected());
			
			// writing structure to file
			Log.info("Writing network structure in GML format");
//			parser.saveGraphics(true);
			parser.write(new File("rsc/parsers/structure_w.gml").toURI());
			
			
			
//			// read structure from file
//			Log.info("Reading network structure in GML format");
////			parser.read(new File("rsc/parsers/structure.gml").toURI());
//			parser.read(new File("/home/tschaffter/jmod_ga_animation/input/network_1_big_cys.gml").toURI());
//			Log.info("Number of nodes: " + structure.getSize());
//			Log.info("Number of edges: " + structure.getNumEdges());
//			
//			Edge<Node> e = structure.getEdge(0);
//			int n = structure.getNumEdges();
//			for (int i = 2; i < n; i++)
//				structure.removeEdge(structure.getEdge(2));
//			n = structure.getSize();
//			for (int i = 0; i < n; i++)
//				structure.removeNode(structure.getNode(0));
//			structure.addNode(e.getSource());
//			structure.addNode(e.getTarget());
//			structure.addEdge(e);
//			
//			Log.info("Number of nodes: " + structure.getSize());
//			Log.info("Number of edges: " + structure.getNumEdges());
//			
//			// writing structure to file
//			Log.info("Writing network structure in GML format");
//			GMLParser.CREATOR = "Jmod";
//			GMLParser.VERSION = Jmod.VERSION;
////			parser.write(new File("rsc/parsers/structure_w.gml").toURI(), true, true);
//			parser.write(new File("/home/tschaffter/jmod_ga_animation/input/network_1_big_cys_mini.gml").toURI(), true, true);

		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			Log.info("Done");
			System.exit(0);
		}
	}
    
	// ============================================================================
	// GETTERS AND SETTERS
	
	public void setStructure(Structure<N,E> network) { structure_ = network; }
	public Structure<N,E> getStructure() { return structure_; }
	
	public void saveGraphics(boolean b) { saveGraphics_ = b; }
	public boolean saveGraphics() { return saveGraphics_; }
	
	public void setEdgeWeightVarName(String edgeWeightVarName) { edgeWeightVarName_ = edgeWeightVarName; }
}
