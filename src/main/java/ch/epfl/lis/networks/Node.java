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

import ch.epfl.lis.networks.graphics.NodeGraphics;

/**
 * Implements a node which can included in a network.
 * 
 * @version November 24, 2011
 * 
 * @author Thomas Schaffter (thomas.schaff...@gmail.com)
 */
public class Node implements INode, IFactory<Node> {
	
	/** Name (unique identifier). */
	protected String name_ = null;
	
	/** Index of the community or module the node belongs to. */
	protected int communityIndex_ = 0;
	
	/** Graphics data. */
	protected NodeGraphics graphics_ = null;
    
	// ============================================================================
	// PROTECTED METHODS
	
	/** Initialization. */
	protected void initialize(String name) {
		
		name_ = name;
		graphics_ = new NodeGraphics();
	}
    
	// ============================================================================
	// PUBLIC METHODS
	
    /** Default constructor. */
	public Node() {
		
		initialize(null);
	}
	
	// ----------------------------------------------------------------------------
	
	/** Constructor. */
	public Node(String name) {
		
		initialize(name);
	}
	
	// ----------------------------------------------------------------------------
	
	/** Copy constructor. */
	public Node(Node node) {
		
		initialize(node.name_);
		graphics_ = node.getGraphics().clone();
	}
	
	// ----------------------------------------------------------------------------
	
	/** Create new instance. */
	public Node create() {
		
		return new Node();
	}
	
	// ----------------------------------------------------------------------------
	
	/** Deep copy method. */
	public Node copy() {

		return new Node(this);
	}
	
	// ----------------------------------------------------------------------------
	
	/** Return the name of the node. */
	@Override
	public String toString() {
		
		return name_;
	}
	
	// ----------------------------------------------------------------------------
	
//	/** Returns true if two Node have the same name. */
//	@Override
//	public boolean equals(Object obj) {
//		
//		System.out.println("Calling node.equals()");
//		
//		Node n2 = (Node) obj;
//		return name_.equals(n2.name_);
//	}
	
	// ============================================================================
	// GETTERS AND SETTERS
	
	public void setName(String name) { name_ = name; graphics_.setLabel(name); }
	public String getName() { return name_; }
	
	public void setCommunityIndex(int communityIndex) { communityIndex_ = communityIndex; }
	public int getCommunityIndex() { return communityIndex_; }
	
	public void setGraphics(NodeGraphics graphics) { graphics_ = graphics; }
	public NodeGraphics getGraphics() { return graphics_; }
}
