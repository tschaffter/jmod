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

package ch.epfl.lis.networks.graphics;

import java.text.DecimalFormat;

/**
 * Contains some common cytoscape "graphics" data of a given node.<p>
 * 
 * The variable names come from Cytoscape. See Cytoscape docs for the units of the variables.
 *
 * @version June 7, 2013
 * 
 * @author Thomas Schaffter (thomas.schaff...@gmail.com)
 */
public class NodeGraphics {
	
	/** Label of the node. */
	protected String label_ = null;
	
	/** X coordinate. */
	protected Double x_ = null;
	/** Y coordinate. */
	protected Double y_ = null;
	/** Width. */
	protected Double w_ = null;
	/** Height. */
	protected Double h_ = null;
	/** Hex fill color. */
	protected String fill_ = null;
	/** Type of shape. */
	protected String type_ = null;
	/** Hex outline color. */
	protected String outline_ = null;
	/** Outline width. */
	protected Double outlineWidth_ = null;
	
	// ============================================================================
	// PUBLIC METHODS
	
    /** Default constructor. */
	public NodeGraphics() {
		
		fill_ = "#FFFFFF"; // required for exporting colored communities
	}
	
	// ----------------------------------------------------------------------------
	
	/** Returns a clone of this object. */
	public NodeGraphics clone() {
		
		NodeGraphics clone = new NodeGraphics();
		clone.label_ = this.label_;
		if (this.x_ != null) clone.x_ = new Double(this.x_);
		if (this.y_ != null) clone.y_ = new Double(this.y_);
		if (this.w_ != null) clone.w_ = new Double(this.w_);
		if (this.h_ != null) clone.h_ = new Double(this.h_);
		clone.fill_ = this.fill_;
		clone.type_ = this.type_;
		clone.outline_ = this.outline_;
		if (this.outlineWidth_ != null) clone.outlineWidth_ = new Double(this.outlineWidth_);
		
		return clone;
	}
	
	// ----------------------------------------------------------------------------
	
	/** Returns a string that describes this object as a GML list or parameters (compatible with Cytoscape). */
	public String toGmlString(String indent) {
		
		String content = indent + "graphics [\n";
//		if (label_ != null) content += indent + "\tlabel\t\"" + label_ + "\"\n";
		if (x_ != null) content += indent + "\tx\t" + x_ + "\n";
		if (y_ != null) content += indent + "\ty\t" + y_ + "\n";
		if (w_ != null) content += indent + "\tw\t" + w_ + "\n";
		if (h_ != null) content += indent + "\th\t" + h_ + "\n";
		if (fill_ != null) content += indent + "\tfill\t\"" + fill_ + "\"\n";
		if (type_ != null) content += indent + "\ttype\t\"" + type_ + "\"\n";
		if (outline_ != null) content += indent + "\toutline\t\"" + outline_ + "\"\n";
		if (outlineWidth_ != null) content += indent + "\toutline_width\t" + outlineWidth_ + "\n"; // nothe the underscore
		content += indent + "]\n";
		
		return content;
	}
	
	// ----------------------------------------------------------------------------

	/** Returns a string that describes this object as a DOT list of parameters. */
	public String toDotString(boolean showLabel) {
		
		String content = "";
		content += "label=\"" + (showLabel ? label_ : "") + "\"";
		if (x_ != null && y_ != null)
			content += " pos=\"" + df(x_) + "," + df(y_) + "!\"";
		if (w_ != null)
			content += " width=\"" + df(w_) + "\"";
		if (h_ != null)
			content += " height=\"" + df(h_) + "\"";
		if (fill_ != null && fill_.compareTo("") != 0)
			content += " fillcolor=\"" + fill_ + "\"";
		if (type_ != null && type_.compareTo("") != 0)
			content += " shape=\"" + type_ + "\"";
		if (outline_ != null && outline_.compareTo("") != 0)
			content += " color=\"" + outline_ + "\"";
		if (outlineWidth_ != null)
			content += " penwidth=\"" + outlineWidth_ + "\"";
		
		// no closing char to allow adding parameters later
		
		return content;
	}
	
	//----------------------------------------------------------------------------
	
	/** Rounds the floating-point number to n decimals to save space in the output file. */
	protected String df(double d) {
		
		DecimalFormat df = new DecimalFormat("#.000");
		return  df.format(d);
	}
	
	// ============================================================================
	// GETTERS AND SETTERS
	
	public void setLabel(String label) { label_ = label; }
	public String getLabel() { return label_; }
	
	public void setX(Double x) { x_ = new Double(x); }
	public Double getX() { return x_; }
	
	public void setY(Double y) { y_ = new Double(y); }
	public Double getY() { return y_; }
	
	public void setW(Double w) { w_ = new Double(w); }
	public Double getW() { return w_; }
	
	public void setH(Double h) { h_ = new Double(h); }
	public Double getH() { return h_; }
	
	public void setFill(String fill) { fill_ = fill; }
	public String getFill() { return fill_; }
	
	public void setType(String type) { type_ = type; }
	public String getType() { return type_; }
	
	public void setOutline(String outline) { outline_ = outline; }
	public String getOutline() { return outline_; }
	
	public void setOutlineWidth(Double outlineWidth) { outlineWidth_ = new Double(outlineWidth); }
	public Double getOutlineWidth() { return outlineWidth_; }
}
