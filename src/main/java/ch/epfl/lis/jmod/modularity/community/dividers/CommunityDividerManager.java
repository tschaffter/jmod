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

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;

import com.esotericsoftware.minlog.Log;

import ch.epfl.lis.jmod.JmodException;

/**
 * Registers and manages the community divider loaded in Jmod.<p>
 * 
 * An interesting feature is the capability to load dynamically community dividers (.class files).<p>
 * 
 * @version January 24, 2012
 * 
 * @author Thomas Schaffter (firstname.name@gmail.com)
 */
public class CommunityDividerManager {
	
	/** Manager instance (Singleton pattern). */
	private static CommunityDividerManager instance_ = null;
	
	/** Filename containing the class names of the additional community dividers to load (this file must be placed in the directory USER_HOME/jmod/). */
	private String communityDividerListFilename_ = "jmodDividers.txt";
	
	/** List of community dividers registered. */
	private List<CommunityDivider> dividers_ = new ArrayList<CommunityDivider>();
	/** Index of the selected divider. */
	private int selectedDividerIndex_ = 0;
	
    // =======================================================================================
    // PRIVATE METHODS
	
	/** Private constructor. */
	private CommunityDividerManager() {
		
		// included in Jmod
		dividers_.add(new NewmanSpectralAlgorithmDivider());
		dividers_.add(new GeneticAlgorithmDivider());
		dividers_.add(new BruteForceDivider());
		dividers_.add(new KnownModulesDivider());
		dividers_.add(new SimulatedAnnealingSplitter());
		
		// load dynamically custom community divider methods
		loadDynamicallyCustomCommunityDividers();
	}
	
	// ----------------------------------------------------------------------------
	
	/** Sets the current module divider from the given method name. */
	public void setSelectedDivider(String methodIdentifier) throws Exception {
		
		for (int i = 0; i < dividers_.size(); i++) {
			if (dividers_.get(i).getIdentifier().compareTo(methodIdentifier) == 0) {
				setSelectedDividerIndex(i);
				return;
			}
		}
		throw new Exception("Community divider not found.");
	}
	
	// ----------------------------------------------------------------------------
	
	/** Returns the list of class names listed in "jmodDividers.txt". */
	private List<String> getCommunityDividerClassnames() throws JmodException, Exception {
		
		List<String> classnames = new ArrayList<String>();
		
		String sep = System.getProperty("file.separator");
		URI uri = new File(System.getProperty("user.home") + sep + "jmod" + sep + communityDividerListFilename_).toURI();
		InputStream stream = null;
		try {
			stream = uri.toURL().openStream();
			BufferedReader reader = new BufferedReader(new InputStreamReader(stream));
			String nextLine = null;
			while ((nextLine = reader.readLine()) != null) {
				if (!nextLine.startsWith("#")) // line commented
					classnames.add(nextLine);
			}
		} catch (Exception e) {
			Log.warn("CommunityDividerManager", "Unable to read " + communityDividerListFilename_ + ".", e);
			throw e;
		} finally {
			if (stream != null)
				stream.close();
		}
		return classnames;
	}
	
	// ----------------------------------------------------------------------------
	
	/** Loads dynamically custom community divider methods. */
	private void loadDynamicallyCustomCommunityDividers() {
		
		try {
			// jmod directory
			String jmodDirectoryStr = System.getProperty("user.home") + System.getProperty("file.separator") + "jmod";
			File jmodDirectory = new File(jmodDirectoryStr);
			
			if (!jmodDirectory.exists())
				Log.debug("CommunityDividerManager", "Jmod directory " + jmodDirectoryStr + " doesn't exist.");
			else {
				Log.debug("CommunityDividerManager", "Jmod directory " + jmodDirectoryStr + " found.");
				// read the class names listed in the text file communityDividerBookFilename_
				List<String> extraCommunityDividersClassnames = getCommunityDividerClassnames();
				for (String classname : extraCommunityDividersClassnames) {
					try {
						Log.info("Loading custom community divider " + classname);
						dividers_.add(loadCommunityDivider(jmodDirectory.toURI().toURL(), classname));
					} catch (Exception e) {
						Log.warn("Unable to load custom community divider " + classname, e);
					}
				}
			}
		} catch (Exception e) {
			Log.warn("CommunityDividerManager", "Unable to load custom community dividers.", e);
		}
	}
	
	// ----------------------------------------------------------------------------
	
	/** Loads and returns the given community divider. */
	@SuppressWarnings("resource")
	private CommunityDivider loadCommunityDivider(URL jmodDirectory, String communityDividerClassname) throws JmodException, Exception {
		
		URL[] urls = new URL[]{jmodDirectory};
		
		ClassLoader cl = new URLClassLoader(urls);
		Class<?> clazz = cl.loadClass(communityDividerClassname);
		
		return (CommunityDivider) clazz.newInstance();
	}
	
    // =======================================================================================
    // PUBLIC METHODS
	
	/** Gets Singleton instance of the manager. */
	public static CommunityDividerManager getInstance(){
		
		if (instance_ == null)
			instance_ = new CommunityDividerManager();
		return instance_;
	}
	
	// ----------------------------------------------------------------------------
	
	/** Adds or registers a community divider to the manager. Returns the index of the new divider. */
	public int addDivider(CommunityDivider divider) throws JmodException, Exception {
		
		if (divider == null)
			throw new JmodException("Divider is null and so cannot be added.");
		
		dividers_.add(divider);
		selectedDividerIndex_ = dividers_.size()-1; // set the last divider added as default
		
		return selectedDividerIndex_;
	}
	
	// ----------------------------------------------------------------------------
	
	/** Returns the community divider matching the given identifier. */
	public CommunityDivider getDivider(String identifier) throws JmodException, Exception {
		
		for (CommunityDivider divider : dividers_) {
			if (divider.getIdentifier().compareTo(identifier)== 0)
				return divider;
		}
		throw new JmodException("No community divider method found matching the identifier \"" + identifier + "\".");
	}
	
    // =======================================================================================
    // GETTERS AND SETTERS
	
	public void setSelectedDividerIndex(int index) { selectedDividerIndex_ = index; }
	public int getSelectedDividerIndex() { return selectedDividerIndex_; }
	
	public CommunityDivider getDivider(int index) { return dividers_.get(index); }
	
	public CommunityDivider getSelectedDivider() { return dividers_.get(selectedDividerIndex_); }
	
	public List<CommunityDivider> getDividers() { return dividers_; }
	
	public int getNumDividers() { return dividers_.size(); }
}
