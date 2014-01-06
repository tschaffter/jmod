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
 * Contains some common cytoscape "graphics" data of a given edge.<p>
 * 
 * The variable names come from Cytoscape. See Cytoscape docs for the units of the variables.
 *
 * @version June 7, 2013
 * 
 * @author Thomas Schaffter (thomas.schaff...@gmail.com)
 */
public class EdgeGraphics {
	
//	/** Label of the node. */
//	private String label_ = null;
	
	/** Width. */
	protected Double width_ = null;
	/** Hex fill color. */
	protected String fill_ = null;
	/** Type of edge. */
	protected String type_ = null;
	
	/** If true, specify that all eges have the same general graphics. */
	protected boolean useSameGeneralGraphics_ = true;
	
	// ============================================================================
	// PUBLIC METHODS
	
    /** Default constructor. */
	public EdgeGraphics() {}
	
	// ----------------------------------------------------------------------------
	
	/** Returns a clone of this object. */
	public EdgeGraphics clone() {
		
		EdgeGraphics clone = new EdgeGraphics();
		if (this.width_ != null) clone.width_ = new Double(this.width_);
		clone.fill_ = this.fill_;
		clone.type_ = this.type_;
	
		return clone;
	}
	
	// ----------------------------------------------------------------------------
	
	/** Returns a string that describes this object as a GML list of parameters (compatible with Cytoscape). */
	public String toGmlString(String indent) {
		
		String content = indent + "graphics [\n";
		if (width_ != null) content += indent + "\twidth\t" + width_ + "\n";
		if (fill_ != null) content += indent + "\tfill\t\"" + fill_ + "\"\n";
		if (type_ != null) content += indent + "\ttype\t\"" + type_ + "\"\n";
		content += indent + "]\n";
		
		return content;
	}
	
	// ----------------------------------------------------------------------------
	
	/** Returns a string that describes this object as a DOT list of parameters. */
	public String toDotString() {
		
		String content = "";
		
		if (!useSameGeneralGraphics_) {
			if (width_ != null)
				content += (content.compareTo("") == 0 ? "" : ",") + "penwidth=\"" + width_ + "\"";
			if (fill_ != null && fill_.compareTo("") != 0)
				content += (content.compareTo("") == 0 ? "" : ",") + "color=\"" + fill_ + "\"";
			if (type_ != null && type_.compareTo("") != 0)
				content += (content.compareTo("") == 0 ? "" : ",") + "cysLine=\"" + type_ + "\"";
		}

		// no closing char to allow adding parameters later
		
		return content;
	}
	
	// ----------------------------------------------------------------------------
	
	/**
	 * Returns the options that are considered as general edge settings.<p>
	 * TODO When reading graph file, try to know precisely which options should be considered as "general options. 
	 */
	public String getGeneralGraphicsToDotString() {
		
		String content = "";
		if (width_ != null)
			content += (content.compareTo("") == 0 ? "" : ",") + "penwidth=\"" + width_ + "\"";
		if (fill_ != null && fill_.compareTo("") != 0)
			content += (content.compareTo("") == 0 ? "" : ",") + "color=\"" + fill_ + "\"";
		if (type_ != null && type_.compareTo("") != 0)
			content += (content.compareTo("") == 0 ? "" : ",") + "cysLine=\"" + type_ + "\"";
		
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
	
//	public void setLabel(String label) { label_ = label; }
//	public String getLabel() { return label_; }
	
	public void setWidth(Double width) { width_ = new Double(width); }
	public Double getWidth() { return width_; }
	
	public void setFill(String fill) { fill_ = fill; }
	public String getFill() { return fill_; }
	
	public void setType(String type) { type_ = type; }
	public String getType() { return type_; }
	
	public void useSameGeneralGraphics(boolean b) { useSameGeneralGraphics_ = b; }
	public boolean useSameGeneralGraphics() { return useSameGeneralGraphics_; }
}
