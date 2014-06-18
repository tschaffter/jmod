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

package ch.epfl.lis.jmod.batch;

import java.io.File;
import java.io.FilenameFilter;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.io.FilenameUtils;

import com.esotericsoftware.minlog.Log;

import ch.epfl.lis.jmod.JmodException;
import ch.epfl.lis.jmod.JmodSettings;

/**
 * Defines a filename filter to know if a file should be considered.
 * 
 * <p>A file is considered if its name match the given regex. Wildcard
 * regexes are allowed. For example: "./network_*.tsv".</p>
 * 
 * @version December 5, 2011
 * 
 * @author Thomas Schaffter (firstname.name@gmail.com)
 */
public class NetworkFilenameFilter implements FilenameFilter {
	
	/** Regex */
	private String regex_ = null;
	
	// ============================================================================
	// PUBLIC METHODS
	
	/** Constructor */
	public NetworkFilenameFilter(String regex) {
		
		super();
		regex_ = regex.replace("?", ".?").replace("*", ".*?");
	}
	
	// ----------------------------------------------------------------------------
	
	public boolean accept(File dir, String name) {
		
		// the directory must be valid
    	if (dir == null || !dir.isDirectory())
    		return false;
    	
    	// ignore hidden files starting with '.'
    	if (name.startsWith("."))
    		return false;
    	
    	// the file must not be a directory
    	File f = new File(dir.getAbsolutePath() + JmodSettings.FILE_SEPARATOR + name);
    	if (f == null || f.isDirectory())
    		return false;
    	
    	// '(' and ')' are special symbols which can be used in regex and
    	// lead to error if there is only one '(' or ')', for example.
    	boolean ok = false;
    	try {
    		ok = name.matches(regex_);
    	} catch (Exception e) {
    		// do nothing and the file with invalid pattern 
    	}
    	
    	return ok;
    }
	
	// ----------------------------------------------------------------------------
	
	/**
	 * Lists network files which match the input regex. The networks are sorted
	 * in ascending alphabetical order.
	 */
	public static List<URI> findNetworks(String regex) throws Exception, JmodException, URISyntaxException {
		
		// extract the directory string from the input regex
		String directory = FilenameUtils.getFullPath(regex);
		File directoryFile = new File(directory);
		if (directory.length() == 0)
			directoryFile = new File(".");

		if (directoryFile == null || !directoryFile.isDirectory())
			throw new JmodException("The directory " + directory + " is not valid and network files can't be found.");
		
		// list network files matching the input regex
		// TODO: check next update
		String truncatedRegex = FilenameUtils.getName(regex);
//		String truncatedRegex = FileUtils.getFilenameString(regex, JmodSettings.FILE_SEPARATOR);
//		Log.debug("Listing networks matching: " + truncatedRegex);
		
		String[] children = directoryFile.list(new NetworkFilenameFilter(truncatedRegex));
		
		Arrays.sort(children);
		List<URI> networks = new ArrayList<URI>();
		for (int i = 0; i < children.length; i++) {
			try {
//				networks.add(FileUtils.makeURI(directory + children[i]));
				networks.add(new File(directory + children[i]).toURI());
			} catch (Exception e) {
				Log.info(e.getMessage());
			}
		}

		return networks;
	}
}