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

/**
 * Contains graphics data for a graph/structure.<p>
 * 
 * The variable names come from DOT. See DOT docs for the units of the variables.
 *
 * @version June 7, 2013
 * 
 * @author Thomas Schaffter (thomas.schaff...@gmail.com)
 */
public class GraphGraphics {
	
	/** DPI. */
	protected int dpi_ = 0;
	
	/** x size of the graphics (!= "page" size in DOT). Can be in inches (floating-point value). */
	protected double width_ = 0.;
	/** y size of the graphics (!= "page" size in DOT). Can be in inches (floating-point value). */
	protected double height_ = 0.;
	
	/** Center the graph in the canvas. */
	protected boolean center_ = true;
	
	/** Hex background color. */
	protected String bgColor_ = "";
	
	/** Show label. */
	protected boolean showNodeLabels_ = false;
	
	/**
	 * Maximum number of iterations for generating DOT layout.<p>
	 * If set to 0, the position of the node is forced, otherwise the layout generation
	 * uses "pos" as starting point.
	 */
	protected int dotMaxIters_ = -1;
	
	// ============================================================================
	// PUBLIC METHODS
	
    /** Default constructor. */
	public GraphGraphics() {}
	
	// ----------------------------------------------------------------------------
	
	/** Returns a clone of this object. */
	public GraphGraphics clone() {
		
		GraphGraphics clone = new GraphGraphics();
		clone.dpi_ = this.dpi_;
	
		return clone;
	}
	
	// ============================================================================
	// GETTERS AND SETTERS
	
	public void setDpi(int dpi) { dpi_ = dpi; }
	public int getDpi() { return dpi_; }
	
	public void setWidth(double width) { width_ = width; }
	public double getWidth() { return width_; }
	
	public void setHeight(double height) { height_ = height; }
	public double getHeight() { return height_; }
	
	public void setCenter(boolean center) { center_ = center; }
	public boolean getCenter() { return center_; }
	
	public void setBgColor(String bgColor) { bgColor_ = bgColor; }
	public String getBgColor() { return bgColor_; }
	
	public void showNodeLabels(boolean b) { showNodeLabels_ = b; }
	public boolean showNodeLabels() { return showNodeLabels_; }
	
	public void setDotMaxIters(int maxIters) { dotMaxIters_ = maxIters; }
	public int getDotMaxIters() { return dotMaxIters_; }
}
