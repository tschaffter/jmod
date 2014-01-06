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

package ch.epfl.lis.jmod;

/** 
 * Jmod exception.
 * 
 * @version December 7, 2011
 * 
 * @author Thomas Schaffter (thomas.schaff...@gmail.com)
 */
public class JmodException extends Exception {
	
	/** Serial version UID. */
	private static final long serialVersionUID = 1L;
	
	// ============================================================================
	// PUBLIC METHODS
	
	/** Default constructor. */
	public JmodException() {}
	
	// ----------------------------------------------------------------------------
	
	/** Constructor. */
	public JmodException(String message) {
		
		super (message);
	}
	
	// ----------------------------------------------------------------------------
	
	/** Constructor. */
	public JmodException(Throwable cause) {
		
		super (cause);
	}
	
	// ----------------------------------------------------------------------------
	
	/** Constructor. */
	public JmodException(String message, Throwable cause) {
		
		super (message, cause);
	}
}
