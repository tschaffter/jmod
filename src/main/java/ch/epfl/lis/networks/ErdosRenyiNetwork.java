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

import java.util.ArrayList;
import java.util.List;

import com.esotericsoftware.minlog.Log;

/** 
 * Generates Erdos-Renyi random graph models G(n,m) or G(n,p).<p>
 * 
 * <b>References:</b><p>
 * 
 * P Erdos, A Renyi. <a href="http://ftp.math-inst.hu/~p_erdos/1959-11.pdf" taget="_blank">
 * On random graphs I.</a>. <i>Publications Matheaticae</i>, 6:290-297, 1959.<br>
 * P Erdos, A Renyi. <a href="http://leonidzhukov.ru/hse/2010/stochmod/papers/erdos-1960-10.pdf" 
 * target="_blank">On the evolution of random graphs</a>.<i>Publ. Math. Inst. Hungar. Acad. Sci</i>, 
 * 5:17-61, 1960.<br>
 * EN Gilber. <a href="http://www.jstor.org/discover/10.2307/2237458?uid=3737760&uid=2&uid=4&sid=21102891417171" 
 * target="_blank">Random graphs</a>. <i>The Annals of Mathematical Statistics</i>, 30(4):1141-1144, 1959.
 * 
 * @see Structure
 * 
 * @version November 25, 2011
 * 
 * @author Thomas Schaffter (thomas.schaff...@gmail.com)
 */
public class ErdosRenyiNetwork<N extends INode & IFactory<N>, E extends IEdge<N> & IFactory<E>> extends Structure<N, E> {
	
	// ============================================================================
	// PROTECTED METHODS

	/** Initialization. */
	protected void initialize() {
		
		name_ = "ErdosRenyi";
		comment_ = "Erdos-Renyi random graph model";
	}
	
	// ============================================================================
	// PRIVATE METHODS
	
	/** Adds an edge with probability p. */
	private void addEdgeDependingOnProbability(E e, double p) {
		
		if (containsEdge(e))
			return;
		
		// using uniform_ is not thread safe
		if (Math.random() <= p)
			addEdge(e);
	}
	
	// ============================================================================
	// PUBLIC METHODS
	
	/** Constructor. */
	public ErdosRenyiNetwork(NodeFactory<N> nodeFactory, EdgeFactory<E> edgeFactory) throws Exception {
		
		super(nodeFactory, edgeFactory);
		initialize();
	}
	
	//----------------------------------------------------------------------------
	
	/**
	 * Creates and adds n nodes to the network structure.<p>
	 * <b>Important:</b><p>
	 * setMaxNumIters(n^2) is called.</p>
	 * @param n The number of nodes
	 */
	public void setNodes(int n) throws Exception {
		
		nodes_.clear();
		
		// add nodes
		N node = null;
		for (int i = 0; i < n; i++) {
			node = nodeFactory_.create();
			node.setName("" + (i+1));
			addNode(node);
		}
	}
	
	//----------------------------------------------------------------------------
	
	/**
	 * Generates the Erdos-Renyi model G(n,m).<p>
	 * Numbers of nodes n and number of interactions m fixed. Self-interactions are not considered.
	 * @param numInteractions The number of interactions.
	 * @param multipleInteractions Allows to have both forward and backward interactions between two nodes.
	 */
	public void generateteGnm(int numInteractions, boolean multipleInteractions) throws Exception {
		
		generateteGnm(numInteractions, multipleInteractions, -1);
	}
	
	//----------------------------------------------------------------------------
	
	/**
	 * Generates the Erdos-Renyi model G(n,m).<p>
	 * Numbers of nodes n and number of interactions m fixed. Self-interactions are not considered.
	 * @param numInteractions The number of interactions.
	 * @param multipleInteractions Allows to have both forward and backward interactions between two nodes.
	 * @param maxNumItersPerInteraction The maximum number of iterations to add a single new interactions. If -1, set to n^2.
	 */
	public void generateteGnm(int numInteractions, boolean multipleInteractions, int maxNumItersPerInteraction) throws Exception, NetworkException {
		
		edges_.clear();
		
		// max number of interactions depend on if the network is directed or not
		// here networks generated do not contain self intaractions
		int n = getSize();
		int maxNumInteractions = (int) Math.pow(getSize(), 2) - n;
		if (multipleInteractions)
			maxNumInteractions /= 2;
			
		if (numInteractions < 1 || numInteractions > maxNumInteractions)
			throw new NetworkException("ERROR: numInteractions must be in [1," + maxNumInteractions + "] for num nodes equals " + getSize() + ".");
		
		if (maxNumItersPerInteraction < 0)
			maxNumItersPerInteraction = n*n;
		
		// add interactions
		// It becomes more and more computationally intensive to randomly
		// build an edge which is not yet included in the network
		for (int i = 0; i < numInteractions; i++) {
			E e = getAbsentEdgeRandomly(multipleInteractions, maxNumItersPerInteraction);
			if (e == null)
				throw new NetworkException("ERROR: Failed to add the " + i + "th interaction to G(n,m).");
			else
				addEdge(e); // finally add edge to the network
		}
	}
	
	//----------------------------------------------------------------------------
	
	/**
	 * Generates the Erdos-Renyi model G(n,p) with p = 2*ln(n)/n.<p>
	 * Thus, G(n,p) "will almost surely be connected", i.e. have a single connected component.<p>
	 * <b>Important:</b><p>
	 * The probability to generate disconnected networks is not null.
	 * @param multipleInteractions Allows to have both forward and backward interactions between two nodes.
	 */
	public void generateConnectedGnp(boolean multipleInteractions) throws Exception {
		
		int n = getSize();
		generateteGnp(getMinConnectedProbability(n), multipleInteractions);
	}
	
	//----------------------------------------------------------------------------
	
	/**
	 * Generates the Erdos-Renyi model G(n,p).<p>
	 * Numbers of nodes n fixed and independent probability p to have an interaction between
	 * any pairs of nodes. elf-interactions are not considered.
	 * @param probability The independent probability p to have an interaction between two nodes.
	 * @param multipleInteractions Allows to have both forward and backward interactions between two nodes.
	 */
	public void generateteGnp(double probability, boolean multipleInteractions) throws Exception, NetworkException {
		
		if (probability < 0. || probability > 1.)
			throw new NetworkException("ERROR: probability_ must be in [0.,1.].");
		
		edges_.clear();
		
		// for each node
		N source = null;
		N target = null;
		E edge = null;
		List<N> nodes = new ArrayList<N>(nodes_.values());
		for (int i = 0; i < nodes.size(); i++) {
			source = nodes.get(i);
			if (!multipleInteractions) {
				for (int j = i+1; j < nodes.size(); j++) {
					target = nodes.get(j);
					// do not prefer one direction over the other
					// using uniform_ is not thread safe
					if (Math.random() <= 0.5) { // direction source -> target
						edge = edgeFactory_.create();
						edge.setSource(source);
						edge.setTarget(target);
						addEdgeDependingOnProbability(edge, probability);
					} else { // direction source <- target
						edge = edgeFactory_.create();
						edge.setSource(target);
						edge.setTarget(source);
						addEdgeDependingOnProbability(edge, probability);
					}
				}	
			} else { // multiple interactions case
				for (int j = 0; j < nodes.size(); j++) {
					if (j != i) {
						target = nodes.get(j);
						edge = edgeFactory_.create();
						edge.setSource(source);
						edge.setTarget(target);
						addEdgeDependingOnProbability(edge, probability);
					}
				}
			}
		}
	}
	
	//----------------------------------------------------------------------------
	
	/** Returns the name of the network. */
	public String toString() {
		
		return name_;
	}
	
	//----------------------------------------------------------------------------
	
	/** Main method. */
	public static void main(String args[]) {
		
		try {
			// instantiate an Erdos-Renyi random network model
			NodeFactory<Node> nodeFactory = new NodeFactory<Node>(new Node());
			EdgeFactory<Edge<Node>> edgeFactory = new EdgeFactory<Edge<Node>>(new Edge<Node>());
			ErdosRenyiNetwork<Node, Edge<Node>> erNetwork = new ErdosRenyiNetwork<Node, Edge<Node>>(nodeFactory, edgeFactory);
			int n = 10;
			erNetwork.setNodes(n);
			
			// Example 1: Erdos-Renyi random model G(n,m) with n = 10 and m = 20
//			erNetwork.generateteGnm(new Edge<Node>(), 20, false);
			
			// Example 2: Erdos-Renyi random model G(n,p) with n = 10 and p = 0.5
//			erNetwork.generateteGnp(0.5, false);
			
			// Example 3: Erdos-Renyi random model G(n,p) with n = 10 and p = 2*ln(n)/n
			erNetwork.generateConnectedGnp(false);
			
			Log.info("Min probability p to obtain connected networks G(n,p) with n = " + n + ": " + getMinConnectedProbability(n));
			
			Log.info("Network connected: " + erNetwork.isConnected());	
			Log.info("Number of interactions: " + erNetwork.getNumEdges());

		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			Log.info("Done");
			System.exit(0);
		}
	}
	
	// ============================================================================
	// GETTERS AND SETTERS

	/**
	 * Returns the minimum probability p to generate a connected network with high probability.<p>
	 * p = Math.min(Math.max(2.*Math.log(n)/(double)n, 0.)
	 */
	public static double getMinConnectedProbability(int n) { return Math.min(Math.max(2.*Math.log(n)/(double)n, 0.), 1.); }
}
