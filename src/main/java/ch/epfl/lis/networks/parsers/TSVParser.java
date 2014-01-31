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
import java.io.InputStreamReader;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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
import au.com.bytecode.opencsv.CSVReader;

/**
 * Parses network structures in TSV format (read and write).<p>
 * 
 * The Tab-Separated Values (TSV) file must contains at least two columns and
 * where each line represents a single edge or interaction:<br>
 * <pre>
 * source_node_name \tab target_node_name
 * </pre><p>
 * 
 * If the third column contains numerical values, these values are used to specify
 * the edge weights.<br>
 * <pre>
 * source_node_name \tab target_node_name \tab edge_weight
 * </pre><p>
 * 
 * If the third column of the TSV file doesn't include numerical values, it is used
 * to specify the type of each edge or interaction (e.g. see the description of the
 * class Edge).<br>
 * <pre>
 * source_node_name \tab target_node_name \tab edge_type
 * </pre><p>
 * 
 * If a fourth column is specified, each line is decoded as follows:<br>
 * <pre>
 * source_node_name \tab target_node_name \tab edge_weight \tab edge_type
 * </pre>
 * 
 * @see Structure
 * @see IEdge
 * 
 * @version November 25, 2011
 * 
 * @author Thomas Schaffter (thomas.schaff...@gmail.com)
 * @author Daniel Marbach (daniel.marb...@gmail.com)
 */
public class TSVParser<N extends INode & IFactory<N>, E extends IEdge<N> & IFactory<E>> extends AbstractParser<N> {
	
	/** Network structure. */
	protected Structure<N, E> structure_ = null;
	
	/** If true, do not write B -> A even if network is undirected. */
	protected boolean saveUndirectedNetworkTargetToSource_ = true;
    
	// ============================================================================
	// PROTECTED METHODS

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
	
	// ============================================================================
	// PUBLIC METHODS
	
	/** Constructor. */
	public TSVParser(Structure<N, E> structure) {
		
		super();
		
		structure_ = structure;
	}
	
	// ----------------------------------------------------------------------------
	
	/**
	 * Loads structure in TSV format.<p>
	 * Undirected edges are not added twice to the list of edges. Instead, the flag
	 * Structure.directed_ is set to false.
	 * @param uri URI to the network file.
	 */
	public void read(URI uri) throws Exception, NetworkException {
		
		read(uri, '\t');
	}
	
	// ----------------------------------------------------------------------------
	
	/**
	 * Loads structure in char-separated values format.<p>
	 * Undirected edges are not added twice to the list of edges. Instead, the flag
	 * Structure.directed_ is set to false.
	 * @param uri URI to the network file.
	 * @param separator Char separating elements on a line.
	 */
	public void read(URI uri, char separator) throws Exception, NetworkException {
		
		try {
			if (structure_ == null)
				throw new NetworkException("structure_ is null.");
			
			structure_.getNodes().clear();
			structure_.getEdges().clear();
			
			ArrayList<String[]> rawData = readTSV(uri, separator);
			if (rawData.size() < 1)
				throw new Exception("At least one edge must be specified.");
			
			int numColumns = rawData.get(0).length;
			if (numColumns < 2)
				throw new Exception("A source and target nodes must be at least specified on each line.");
			
			boolean weightColumn = false;
			boolean typeColumn = false;
			
			if (numColumns > 2) {
				// does the 3rd column contain the edge weight or type ?
				try {
					Double.valueOf(rawData.get(0)[2]);
					weightColumn = true;
				} catch (Exception e) {
					typeColumn = true;
				}
			}
			
			if (numColumns > 3) // the 3rd column must contain the weights
				typeColumn = true;
			
			N source = null;
			N target = null;
			double weight = 1.;
			String type = "";
			for (int i = 0; i < rawData.size(); i++) {
				
				try {
					source = structure_.addNode(rawData.get(i)[0]);
					target = structure_.addNode(rawData.get(i)[1]);
					weight = 1.;
					type = "";
					
					if (weightColumn)
						weight = Double.valueOf(rawData.get(i)[2]);
					if (typeColumn && !weightColumn)
						type = rawData.get(i)[2];
					if (typeColumn && weightColumn)
						type = rawData.get(i)[3];
					
					addEdge(source, target, weight, type);
					
				} catch (ArrayIndexOutOfBoundsException e) {
					Log.warn("TSVParser", "In " + uri.getPath());
					Log.warn("TSVParser", "Line " + (i+1) + " doesn't include enough tab-separated values. Line is ignored.");
				}
			}
	
			File f = new File(uri);
			structure_.setName(f.getName());
			structure_.setComment("");
			
			// if the TSV file is describing an undirected graph (i.e. A->B and B->A are
			// both written), the flag Structure.directed_ is set to false after having
			// removed the duplicated edges. Otherwise Structure.directed_ stays with
			// its current value.
			structure_.isDirected(true);
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
			Log.error("Unable to open TSV network file.", e);
			throw e;
		}
	}
	
	// ----------------------------------------------------------------------------
	
	/**
	 * Saves structure to TSV file.<p>
	 * Undirected edges are written twice (A->B and B->A).
	 */
	public void write(URI uri) throws Exception, NetworkException {
		
		write(uri, "\t");
	}
	
	// ----------------------------------------------------------------------------
	
	/**
	 * Saves structure to TSV file.<p>
	 * Undirected edges are written twice (A->B and B->A).
	 * @param uri URI to network file.
	 * @param separator Char separating elements on a line.
	 * @param nodeFormat Specifies if the node name or node index must be saved.
	 */
	public void write(URI uri, String separator) throws Exception, NetworkException {
		
		if (structure_ == null)
			throw new NetworkException("structure_ is null.");
		
		BufferedWriter bf = null;
		try {
			bf = new BufferedWriter(new FileWriter(uri.getPath()));
			
			// get an ordered list of edges
			List<E> edges = new ArrayList<E>(structure_.getEdges().values());
			Collections.sort(edges, new Structure.EdgeComparator<E>());
			
			String source = null;
			String target = null;
			for (E e : edges) {
					source = e.getSource().getName();
					target = e.getTarget().getName();
				
				String edgeWeight = Double.toString(e.getWeight());
				if (!saveEdgeWeight_)
					edgeWeight = "";
				
				bf.write(source + separator + target + separator + edgeWeight + System.lineSeparator());
				if (saveUndirectedNetworkTargetToSource_ && !structure_.isDirected()) // for undirected edges
					bf.write(target + separator + source + separator + edgeWeight + System.lineSeparator());
			}
		} catch (Exception e) {
			Log.error("Unable to write network to TSV file format.");
			throw e;
		} finally {
			if (bf != null)
				bf.close();
		}
	}
	
	// ----------------------------------------------------------------------------
	
	/** Reads a TSV file and return a String[] for each line. */
	public static ArrayList<String[]> readTSV(URI uri) throws Exception {
		
		return readTSV(uri, '\t');
	}
	
	// ----------------------------------------------------------------------------
	
	/** Reads a TSV file and return a String[] for each line. */
	public static ArrayList<String[]> readTSV(URI uri, char separator) throws Exception {
		
		InputStream stream = uri.toURL().openStream();
		CSVReader reader = new CSVReader(new InputStreamReader(stream), separator);
		String[] nextLine = null;
		ArrayList<String[]> rawData = new ArrayList<String[]>();
		boolean ok = true;
		//int currentLine = 0;
		
		while (ok) {
			nextLine = reader.readNext();
			
			if (nextLine != null) { // Is not the end of the file
				rawData.add(nextLine);
				//currentLine++;
			} else {
				ok = false;
			}
		}
		reader.close();
		
		return rawData;
	}

	// ----------------------------------------------------------------------------
	
//	/** Write the given data to a file in TSV format */
//	public static void writeTSV(URI uri, ArrayList<String[]> data) throws IOException {
//								
//		FileWriter fw = new FileWriter(new File(uri), false);
//		for (int l = 0; l < data.size(); l++) {
//
//			String[] currentLine = data.get(l);
//			int lastfield = currentLine.length - 1;
//
//			// for all fields except the last one, write it followed by a tab
//			for (int i = 0; i < lastfield; i++)
//				fw.write(currentLine[i] + "\t");
//
//			// write the last field followed by a new line
//			fw.write(currentLine[lastfield] + System.lineSeparator());
//		}
//		fw.close();
//	}
	
	//----------------------------------------------------------------------------
	
	/** Main method. */
	public static void main(String args[]) {
		
		try {
			Log.set(Log.LEVEL_DEBUG);
			
			// instantiate structure and parser
			NodeFactory<Node> nodeFactory = new NodeFactory<Node>(new Node());
			EdgeFactory<Edge<Node>> edgeFactory = new EdgeFactory<Edge<Node>>(new Edge<Node>());
			Structure<Node, Edge<Node>> structure = new Structure<Node, Edge<Node>>(nodeFactory, edgeFactory);
			TSVParser<Node, Edge<Node>> parser = new TSVParser<Node, Edge<Node>>(structure);

			// read structure from file
			Log.info("Reading network structure in TSV format");
			parser.read(new File("rsc/parsers/structure.tsv").toURI());
			Log.info("Number of nodes: " + structure.getSize());
			Log.info("Number of edges: " + structure.getNumEdges());
			
			// writing structure to file
			Log.info("Writing network structure in TSV format");
			parser.write(new File("rsc/parsers/structure_w.tsv").toURI());
//			parser.saveEdgeWeight(false);
			Log.info("Weighted: " + structure.isWeighted());
			Log.info("Directed: " + structure.isDirected());
			
//			Node source = new Node("a");
//			Node target = new Node("b");
//			structure.addEdge(new Edge<Node>(source, target, 0.5));
//			structure.addEdge(new Edge<Node>(source, target, 1.0));
//			
//			Log.info("Number of edges: " + structure.getNumEdges());
			
//			parser.read(new File("/home/tschaffter/Downloads/RNA_GENE.txt").toURI());
//			Log.info("Converting TSV network");
//			System.out.println("N = " + structure.getSize());
//			System.out.println("L = " + structure.getNumEdges());
//			structure.saveGML(new File("drosophila_RNA_GENE.gml").toURI());

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
	
	public void saveUndirectedNetworkTargetToSource(boolean b) { saveUndirectedNetworkTargetToSource_ = b; }
}
