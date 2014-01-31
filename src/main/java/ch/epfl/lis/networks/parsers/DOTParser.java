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

import java.awt.Color;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import com.esotericsoftware.minlog.Log;

import att.grappa.Graph;
import att.grappa.Grappa;
import att.grappa.GrappaPoint;
import att.grappa.GrappaSize;
import att.grappa.Parser;
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
import ch.epfl.lis.networks.graphics.GraphGraphics;
import ch.epfl.lis.networks.graphics.NodeGraphics;
import ch.tschaffter.utils.ColorUtils;

/**
 * Parses network structures in DOT format (read and write).<p>
 * 
 * Only a few options used by the Graphviz's tools are supported:<p>
 * <ul>
 * <li> direct/undirected graph
 * <li> node location (node attribute "position")
 * <li> edge weight and type
 * </ul><p>
 * 
 * The methods to read and write DOT files are implemented by the Grappa library (Graph Package).
 * 
 * @version November 3, 2013
 *
 * @author Thomas Schaffter (thomas.schaff...@gmail.com)
 * 
 * @see Structure
 * @see Edge
 */
public class DOTParser<N extends INode & IFactory<N>, E extends IEdge<N> & IFactory<E>> extends AbstractParser<N> {
	
//	/** Defines the repulsive force of the extended Fruchterman-Reingold force directed model. */
//	private double repulsiveForce_ = 1.0;
	
	/** Network structure. */
	protected Structure<N,E> structure_ = null;
	
	/** A tree map to keep track of the association node name/node. */
	protected Map<String,N> nodes_ = null;
    
	// ============================================================================
	// PUBLIC METHODS
	
    /** Constructor. */
	public DOTParser(Structure<N, E> structure) {
		
		structure_ = structure;
		nodes_ = new TreeMap<String,N>();
	}
	
	// ----------------------------------------------------------------------------
	
	/** Reads a structure in DOT format. */
	public void read(URI uri) throws Exception, NetworkException {
		
		try {
			if (structure_ == null)
				throw new NetworkException("structure_ is null.");
			
			structure_.getNodes().clear();
			structure_.getEdges().clear();
			
			// instantiate a Grappa graph and set it using their DOT parser
			InputStream stream = uri.toURL().openStream();
			Graph grappaGraph = new Graph("myGraph");
			Grappa.negateStringYCoord = false;
			Parser parser = new Parser(new InputStreamReader(stream), null, grappaGraph);
			parser.parse();
			
			String networkName = grappaGraph.getName();
			if (networkName == null || networkName.isEmpty())
				networkName = new File(uri).getName();
			
			structure_.setName(networkName);
			structure_.setComment("");
			structure_.isDirected(grappaGraph.isDirected());
			
			// graph parameters expected from Jmod or Cytoscape
			// the other are hard coded
			GraphGraphics graphGraphics = structure_.getGraphics();
//			if (grappaGraph.getAttributeValue("repulsiveforce") != null)
//				repulsiveForce_ = Double.parseDouble((String)grappaGraph.getAttributeValue("repulsiveforce"));
			if (grappaGraph.getAttributeValue("dpi") != null)
				graphGraphics.setDpi(Integer.parseInt((String)grappaGraph.getAttributeValue("dpi")));
			if (grappaGraph.getAttributeValue("size") != null) {
				GrappaSize size = (GrappaSize)grappaGraph.getAttributeValue("size");
				graphGraphics.setWidth(size.width);
				graphGraphics.setHeight(size.height);
			}
			if (grappaGraph.getAttributeValue("bgcolor") != null)
				graphGraphics.setBgColor((String)grappaGraph.getAttributeValue("bgcolor")); // can be "transparent"
			if (grappaGraph.getAttributeValue("center") != null)
				graphGraphics.setCenter(((String)grappaGraph.getAttributeValue("center")).compareTo("true") == 0);
			if (grappaGraph.getAttributeValue("maxiter") != null)
				graphGraphics.setDotMaxIters(Integer.parseInt((String)grappaGraph.getAttributeValue("maxiter")));

			// iterates through the nodes (not the same order as defined by the file)
			Iterator<att.grappa.Node> nodeIter = grappaGraph.nodeElements();
			while (nodeIter.hasNext()) {
				att.grappa.Node grappaNode = nodeIter.next();
				N node = structure_.getNodeFactory().create();
				node.setName(grappaNode.getName());
				
				NodeGraphics cysNodeGraphics = new NodeGraphics();
				if (grappaNode.getAttributeValue("label") != null)
					cysNodeGraphics.setLabel((String)grappaNode.getAttributeValue("label"));
				if (grappaNode.getAttributeValue("pos") != null) {
					GrappaPoint nodeCenter = (GrappaPoint)grappaNode.getAttributeValue("pos");
					cysNodeGraphics.setX(nodeCenter.x);
					cysNodeGraphics.setY(nodeCenter.y);
				}
				if (grappaNode.getAttributeValue("width") != null)
					cysNodeGraphics.setW((Double)grappaNode.getAttributeValue("width"));
				if (grappaNode.getAttributeValue("height") != null)
					cysNodeGraphics.setH((Double)grappaNode.getAttributeValue("height"));
				if (grappaNode.getAttributeValue("fillcolor") != null)
					cysNodeGraphics.setFill(ColorUtils.rgb2hexString((Color)grappaNode.getAttributeValue("fillcolor")));
				if (grappaNode.getAttributeValue("shape") != null)
					cysNodeGraphics.setType(Grappa.shapeToKey.get((Integer)grappaNode.getAttributeValue("shape")));
				if (grappaNode.getAttributeValue("color") != null)
					cysNodeGraphics.setOutline(ColorUtils.rgb2hexString((Color)grappaNode.getAttributeValue("color")));
				if (grappaNode.getAttributeValue("penwidth") != null)
					cysNodeGraphics.setOutlineWidth(Double.parseDouble((String)grappaNode.getAttributeValue("penwidth")));
				
				node.setGraphics(cysNodeGraphics);
				structure_.addNode(node);
				nodes_.put(node.getName(), node);
			}
			
			// iterates through the edges
			N source = null;
			N target = null;
			E edge = null;
			double weight = 1.;
//			String type = null;
			Iterator<att.grappa.Edge> edgeIter = grappaGraph.edgeElements();
			while (edgeIter.hasNext()) {
				att.grappa.Edge grappaEdge = edgeIter.next();
				source = nodes_.get(grappaEdge.getTail().getName());
				target = nodes_.get(grappaEdge.getHead().getName());
				
				if (grappaEdge.getAttributeValue("weight") != null) weight = (Double)grappaEdge.getAttributeValue("weight");
				else weight = 1.;
				
//				if (grappaEdge.getAttributeValue("type") != null) type = (String)grappaEdge.getAttributeValue("type");
//				else type = "?";
				
				edge = structure_.getEdgeFactory().create();
				edge.setSource(source);
				edge.setTarget(target);
				edge.setWeight(weight);
//				edge.setType(Edge.getTypeFormString(type));
				
				EdgeGraphics cysEdgeGraphics = new EdgeGraphics();
				if (grappaEdge.getAttributeValue("penwidth") != null)
					cysEdgeGraphics.setWidth(Double.parseDouble((String)grappaEdge.getAttributeValue("penwidth")));
				if (grappaEdge.getAttributeValue("color") != null)
					cysEdgeGraphics.setFill(ColorUtils.rgb2hexString((Color)grappaEdge.getAttributeValue("color")));
				if (grappaEdge.getAttributeValue("cysLine") != null)
					cysEdgeGraphics.setType((String)grappaEdge.getAttributeValue("cysLine"));
				
				edge.setGraphics(cysEdgeGraphics);
				structure_.addEdge(edge);
			}
			
//			structure_.initializeIsSigned();
			structure_.cleanEdges();
			structure_.evaluateIsWeighted();
			
			// Force to consider the network as directed if "_directed" is included
			// in the filename, and as undirected if "_undirected" is included.
			File f = new File(uri);
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
			Log.error("DOTParser", "Unable to open DOT network file.", e);
			throw new NetworkException("Unable to open DOT network file.");
		}
	}
	
	// ----------------------------------------------------------------------------

	/** Saves structure to DOT file. */
	public void write(URI uri) throws Exception, NetworkException {
		
		if (structure_ == null)
			throw new NetworkException("structure_ is null.");

		BufferedWriter bf = null;
		try {
			bf = new BufferedWriter(new FileWriter(uri.getPath()));
		
			String graphType = "graph";
			String arrow = " -- ";
			if (structure_.isDirected()) {
				graphType = "digraph";
				arrow = " -> ";
			}
			
			// graph parameters
			bf.write(graphType + " \"" + structure_.getName() + "\" {" + System.lineSeparator());
			bf.write("\trankdir=LR;" + System.lineSeparator());
			bf.write("\toutputorder=edgesfirst;" + System.lineSeparator());
			
			// graph graphics
			GraphGraphics graphGraphics = structure_.getGraphics();
			if (saveGraphics_) {
				bf.write("\tcenter=" + (graphGraphics.getCenter() ? "true" : "false") + ";" + System.lineSeparator());
				if (graphGraphics.getBgColor().compareTo("") != 0)
					bf.write("\tbgcolor=\"" + graphGraphics.getBgColor() + "\";" + System.lineSeparator());
				if (graphGraphics.getDpi() > 0)
					bf.write("\tdpi=\"" + graphGraphics.getDpi() + "\";" + System.lineSeparator());
				if (graphGraphics.getWidth() > 0 && graphGraphics.getHeight() > 0)
					bf.write("\tsize=\"" + df(graphGraphics.getWidth()) + "," + df(graphGraphics.getHeight()) + "!\";" + System.lineSeparator());
				//content += "\tpage=\"" + df(graphSize_.width/(double)dpi_) + "," + df(graphSize_.height/(double)dpi_) + "!\";\n";
				//content += "\trepulsiveforce=\"" + repulsiveForce_ + "\";\n";
				if (graphGraphics.getDotMaxIters() > -1)
					bf.write("\tmaxiter=\"" + graphGraphics.getDotMaxIters() + "\";" + System.lineSeparator());
			
				// node general options
				bf.write("\tnode [style=filled];" + System.lineSeparator());
				
				// edge general options
				E e0 = structure_.getEdges().values().iterator().next();
				if (e0.getGraphics().useSameGeneralGraphics())
					bf.write("\tedge [" + e0.getGraphics().getGeneralGraphicsToDotString() + "];" + System.lineSeparator());
			}
			
			// get nodes in lexicographical order
			List<N> orderedNodes = new ArrayList<N>(structure_.getNodes().values());
			Collections.sort(orderedNodes, new Structure.NodeComparator<N>());
			
			NodeGraphics cysNodeGraphics = null;
			for (N n : orderedNodes) {
				String nodeLine = "\t\"" + n.getName() + "\"";
				
				// node options (graphics)
				nodeLine += " [";
				if (saveGraphics_ && (cysNodeGraphics = n.getGraphics()) != null)
					nodeLine += cysNodeGraphics.toDotString(graphGraphics.showNodeLabels());
				bf.write(nodeLine + "];" + System.lineSeparator());
			}
			
			// get an ordered list of edges
			List<E> edges = new ArrayList<E>(structure_.getEdges().values());
			Collections.sort(edges, new Structure.EdgeComparator<E>());
			
			// iterates through the edges
			EdgeGraphics cysEdgeGraphics = null;
			for (E e : edges) {
				String edgeLine = "\t\"" + e.getSource().getName() + "\"" + arrow + "\"" + e.getTarget().getName() + "\"";
				
				String edgeWeight = "weight=\"" + e.getWeight() + "\"";
				if (!saveEdgeWeight_ || !structure_.isWeighted())
					edgeWeight = "";
				
//				String edgeType = " type=\"" + e.getTypeString() + "\"";
//				if (!saveEdgeType_) {
//					if (!structure_.isSigned())
//						edgeType = "";
//				}
				
				// edge weight + edge type + edge options (graphics)
				edgeLine += " [";
				edgeLine += edgeWeight; // + edgeType;
				if (saveGraphics_) {
					if ((cysEdgeGraphics = e.getGraphics()) != null)
						edgeLine += cysEdgeGraphics.toDotString();
				}
				bf.write(edgeLine + "];" + System.lineSeparator());	
			}
			bf.write("}");
			
		} catch (Exception e) {
			Log.error("Unable to write network to DOT file format.");
			throw e;
		} finally {
			if (bf != null)
				bf.close();
		}
	}
    
	//----------------------------------------------------------------------------
	
	/** Rounds the floating-point number to n decimals to save space in the output file. */
	protected String df(double d) {
		
		DecimalFormat df = new DecimalFormat("#.000");
		return  df.format(d);
	}
    
	//----------------------------------------------------------------------------
	
	/** Main method. */
	public static void main(String args[]) {
		
		try {
			// instantiate structure and parser
			NodeFactory<Node> nodeFactory = new NodeFactory<Node>(new Node());
			EdgeFactory<Edge<Node>> edgeFactory = new EdgeFactory<Edge<Node>>(new Edge<Node>());
			Structure<Node, Edge<Node>> structure = new Structure<Node, Edge<Node>>(nodeFactory, edgeFactory);
			DOTParser<Node, Edge<Node>> parser = new DOTParser<Node, Edge<Node>>(structure);
			
			// read structure from file
			Log.info("Reading network structure in DOT format");
			parser.read(new File("rsc/parsers/structure.dot").toURI());
			Log.info("Number of nodes: " + structure.getSize());
			Log.info("Number of edges: " + structure.getNumEdges());
			Log.info("Weighted: " + structure.isWeighted());
			Log.info("Directed: " + structure.isDirected());
			
			// writing structure to file
			Log.info("Writing network structure in DOT format");
			parser.write(new File("rsc/parsers/structure_w.dot").toURI());
			
//			JmodNetwork network = new JmodNetwork();
////			network.read(new File("/home/tschaffter/A_ga/ten.tsv").toURI(), Structure.TSV);
//			network.read(new File("/home/tschaffter/jmod_ga_animation/input/network_1_big_cys.gml").toURI(), Structure.GML);
////			network.read(new File("/home/tschaffter/jmod_ga_animation/input/network_1_big_cys_mini.gml").toURI(), Structure.GML);
////			network.read(new File("/home/tschaffter/jmod_ga_animation/input/network_1_big_cys_mini_jmod.dot").toURI(), Structure.DOT);
//			network.setDirected(false);
//			
//			parser.setStructure(network);
//			
////			parser.write(new File("/home/tschaffter/A_ga/network_1.dot").toURI(), false);
//			parser.write(new File("/home/tschaffter/jmod_ga_animation/input/network_1_big_cys_mini_jmod_2.dot").toURI(), false);

		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			Log.info("Done");
			System.exit(0);
		}
	}
	
	// ============================================================================
	// GETTERS AND SETTERS

	public void setStructure(Structure<N, E> network) { structure_ = network; }
	public Structure<N,E> getStructure() { return structure_; }
}
