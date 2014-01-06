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

package ch.epfl.lis.jmod.modularity.community.dividers;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.regex.Pattern;

import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;

import ch.epfl.lis.jmod.JmodException;

/**
 * This abstract class provides a template for the implementation of a community divider.
 * 
 * @version January 24, 2012
 * 
 * @author Thomas Schaffter (firstname.name@gmail.com)
 */
public abstract class CommunityDivider implements ICommunityDivider {
	
	/** Short name of the method used as identifier. */
	protected String identifier_ = "myId";
	/** Name of the method. */
	protected String name_ = "myDivider";
	/** Description of the method in HTML format including usage of the method parameters (e.g. "<html>line 1<br>line 2</html>"). */
	protected String description_ = "No description available.";
	
	/** Name of the current community to divide (e.g. RAA, RAAABAA,etc.). */
	protected String currentCommunityName_ = "";
	
	/** Command-line options. */
	protected Options options_ = null;
	
    // =======================================================================================
    // ABSTRACT METHODS
	
	/** Builds the options. */
	protected abstract void buildOptions();
	/** Reads the options string and set the internal parameters of the community divider. */
	protected abstract void parseOptions(String args[]) throws JmodException, Exception;
	/** Duplicates the divider (required since many networks can be split in parallel). */
	public abstract CommunityDivider copy();
	
    // =======================================================================================
    // PROTECTED METHODS
	
	/** Shows the command line options help. */
	protected void printHelp() {

		HelpFormatter formatter = new HelpFormatter();
		formatter.setSyntaxPrefix(name_);
		
		formatter.printHelp(100, " options:", "", options_, "");
	}
	
	// ----------------------------------------------------------------------------
	
	/** Parses a String to String[] similarly to what the system does with main(args[]). */
	protected String[] parseArgs(String str) {

		List<String> tokens = new ArrayList<String>();
		
	    Scanner sc = new Scanner(str);
	    Pattern pattern = Pattern.compile(
	        "\"[^\"]*\"" +
	        "|'[^']*'" +
	        "|[.0-9A-Za-z'\\-]+"
	    );
	    String token;
	    while ((token = sc.findInLine(pattern)) != null) {
	    	tokens.add(token);
	    }
	    sc.close();
	    
	    return tokens.toArray(new String[0]);
	}
	
    // =======================================================================================
    // PUBLIC METHODS
	
	/** Constructor. */
	public CommunityDivider() {}
	
	
	// ----------------------------------------------------------------------------
	
	/** Copy constructor. */
	public CommunityDivider(CommunityDivider divider) {
		
		identifier_ = divider.identifier_;
		name_ = divider.name_;
		description_ = divider.description_;
		options_ = divider.options_; // it's ok to copy the reference
	}
	
	// ----------------------------------------------------------------------------

	public String getOptionsDescriptionHTML() {
		
		HelpFormatter formatter = new HelpFormatter();
		formatter.setSyntaxPrefix(name_);
		
		StringWriter stringWriter = new StringWriter();
		PrintWriter printWriter = new PrintWriter(stringWriter);
		formatter.printHelp(printWriter, HelpFormatter.DEFAULT_WIDTH, " options:", "", options_, HelpFormatter.DEFAULT_LEFT_PAD, HelpFormatter.DEFAULT_DESC_PAD, "", false);
		printWriter.flush();
	    printWriter.close();
		
	    String input = stringWriter.toString();
		input = input.replaceAll("   ", " ");
		input = input.replaceAll("  ", " ");
		input = input.replaceAll(" -", "<br>-");
		
		String output = "<html>";
		for (int i = 0; i < input.length(); i++) {
			output += input.charAt(i);
		}
		output += "</html>";
		return output;
	}
	
	// ----------------------------------------------------------------------------
	
	/** Wrapper for parseOptions(String args[]). */
	public void parseOptions(String args) throws JmodException, Exception {
		
		String[] argsArray = parseArgs(args);
		parseOptions(argsArray);
	}

    // =======================================================================================
    // GETTERS AND SETTERS
	
	public void setIdentifier(String id) { identifier_ = id; }
	public String getIdentifier() { return identifier_; }
	
	public void setName(String name) { name_ = name; }
	public String getName() { return name_; }
	
	public void setDescription(String description) { description_ = description; }
	public String getDescription() { return description_; }
	
	public void setCurrentCommunityName(String name) { currentCommunityName_ = name; }
	public String getCurrentCommunityName() { return currentCommunityName_; }
}
