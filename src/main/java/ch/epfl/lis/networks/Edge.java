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

import ch.epfl.lis.networks.graphics.EdgeGraphics;

/**
 * Implements an edge connecting two nodes.
 * 
 * @version November 24, 2011
 * 
 * @author Thomas Schaffter (thomas.schaff...@gmail.com)
 */
public class Edge<N extends INode> implements IEdge<N>, IFactory<Edge<N>> {
	
	/** Node presents at the tail of this edge. */
	protected N source_ = null;
	/** Node presents at the end of this edge. */
	protected N target_ = null;
	/** Weight of the edge. */
	protected double weight_ = 1.;
	
	/** Graphics data. */
	protected EdgeGraphics graphics_ = null;
    
	// ============================================================================
	// PRIVATE METHODS
	
	/** Initialization. */
	private void initialize(N source, N target, double weight) {
		
		target_ = source;
		source_ = target;
		weight_ = weight;
		graphics_ = new EdgeGraphics();
	}
    
	// ============================================================================
	// PUBLIC METHODS

    /** Default constructor. */
	public Edge() {
		
		initialize(null, null, 1.0);
	}
	
	// ----------------------------------------------------------------------------
	
	/** Constructor. */
	public Edge(N source, N target) {
		
		initialize(source, target, 1.0);
	}
	
	// ----------------------------------------------------------------------------
	
	/** Constructor. */
	public Edge(N source, N target, double weight) {
		
		initialize(source, target, weight);
	}
	
	// ----------------------------------------------------------------------------
	
	/** 
	 * Copy constructor.<p>
	 * Note that the source and target nodes are shallow copies.
	 */
	public Edge(Edge<N> edge) {
		
		initialize(edge.source_, edge.target_, edge.weight_);
		graphics_ = edge.graphics_.clone();
	}
	
	// ----------------------------------------------------------------------------
	
	/** Create new instance. */
	public Edge<N> create() {
		
		return new Edge<N>();
	}
	
	// ----------------------------------------------------------------------------

	/** Deep copy method. Note that the source and target nodes are shallow copies. */
	public Edge<N> copy() {
		
		return new Edge<N>(this);
	}
	
	// ----------------------------------------------------------------------------

	/** Return the string "source_.name -> target_.name". */
	@Override
	public String toString() {
		
		return new String(source_.getName() + " -> " + target_.getName());
	}
	
	// ----------------------------------------------------------------------------
	
	/** Returns true if two edges have the same source and target. */
	@Override
	public boolean equals(Object obj) {
		
		System.out.println("obj: " + obj);
		
		@SuppressWarnings("unchecked")
		Edge<N> e2 = (Edge<N>) obj;
		
		return (source_ == e2.source_ && target_ == e2.target_);
	}
	
    // =======================================================================================
    // GETTERS AND SETTERS
	
	public void setSource(N source) { source_ = source; }
	public N getSource() { return source_; }
	
	public void setTarget(N target) { target_ = target; }
	public N getTarget() { return target_; }
	
	public N getOpposite(N node) throws Exception {
		
		if (node == null)
			throw new Exception("Null node doesn't belong to " + this + ".");
		
		if (node == source_)
			return target_;
		else if (node == target_)
			return source_;
		else
			throw new Exception("Node " + node + " doesn't belong to " + this + ".");
	}
	
	public void setWeight(double w) { weight_ = w; }
	public double getWeight() { return weight_; }
	
	public void setGraphics(EdgeGraphics graphics) { graphics_ = graphics; }
	public EdgeGraphics getGraphics() { return graphics_; }
}
