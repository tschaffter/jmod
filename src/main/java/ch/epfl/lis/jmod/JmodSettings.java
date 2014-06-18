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

import java.util.StringTokenizer;

import ch.epfl.lis.jmod.modularity.ModularityDetectorSnapshot;
import ch.epfl.lis.jmod.modularity.community.dividers.CommunityDividerManager;
import ch.epfl.lis.networks.Structure;

import com.esotericsoftware.minlog.Log;

/**  
 * Offers global parameters (settings) and functions used by all classes of imod package.
 * 
 * <p>JmodSettings makes use of the Singleton design pattern: There's at most one
 * instance present, which can only be accessed through getInstance().</p>
 * 
 * @version November 26, 2011
 * 
 * @author Thomas Schaffter (firstname.name@gmail.com)
 * @author Daniel Marbach (firstname.name@gmail.com)
 */
public class JmodSettings {
	
	/** File separator specific to the current system (e.g. Unix = "/", Windows = "\") */
	public static final String FILE_SEPARATOR = "/"; //System.getProperty("file.separator");
	
	/** Suffix of the filename containing the identities of the communities. */
	public static final String COMMUNITY_IDENTITIES_FILENAME_SUFFIX = "_community.dat";
	
	/** String representing the moving vertex method. */
	public static final String MVM_METHOD_STR = "MVM";
	/** String representing the global moving vertex method. */
	public static final String GLOBAL_MVM_METHOD_STR = "gMVM";
	
	/** The unique instance of JmodSettings (Singleton design pattern). */
	private static JmodSettings instance_ = null;
	
	/** If true, use moving vertex method (MVM) in modularity Q computation. */
	private boolean useMovingVertex_ = true;
	/** If true, use global moving vertex (gMVM) in modularity Q computation. */
	private boolean useGlobalMovingVertex_ = true;

	/** String containing the options of the selected community divider. */
	private String currentCommunityDividerOptions_ = "";
	
	/** Defines the number of concurrent module detections. */
	private int numConcurrentModuleDetections_ = 1;
	
	/** Saves the basic dataset (modularity and number of indivisible communities). */
	private boolean exportBasicDataset_ = false;
	/** Saves each community to network file in the given format. */
	private boolean exportCommunityNetworks_ = false;
	/** Network file format in which communities are saved. */
	private Structure.Format communityNetworkFormat_ = Structure.Format.TSV;
	/** Saves input network with communities colored. */
	private boolean exportColoredCommunities_ = false;
	/** Format for saving colored communities (GML or DOT). */
	private Structure.Format coloredCommunitiesNetworkFormat_ = Structure.Format.GML;
	/** Saves the dendrogram of the hierarchical community tree. */
	private boolean exportCommunityTree_ = false;
	/** Saves snapshots of the state of the running modularity detection. */
	private boolean exportSnapshots_ = false;
	
	/** Snapshot export mode. */
	private int modularityDetectionSnapshotMode_ = ModularityDetectorSnapshot.EXPORT_DIFFERENTIAL_SNAPSHOTS;
	
	// ============================================================================
	// PUBLIC METHODS
	
	/** Default constructor. */
	public JmodSettings() {
		
		Log.setLogger(new JmodLogger());
	}
	
	// ----------------------------------------------------------------------------
	
	/** Gets JmodSettings instance. */
	static public JmodSettings getInstance() {
		
		if (instance_ == null)
			instance_ = new JmodSettings();
		return instance_;
	}
	
	// ----------------------------------------------------------------------------
	
	/** Returns a string reflecting the method selected. */
	public String getSelectedMethod() throws Exception, JmodException {
		
		String method = "";
		method += CommunityDividerManager.getInstance().getSelectedDivider().getIdentifier();

		// optimizers
		if (useMovingVertex_)
			method += " + MVM";
		if (useGlobalMovingVertex_)
			method += " + gMVM";
		
		return method;
	}

	// ----------------------------------------------------------------------------
	
	/** Rounds a given double number with n decimals. */
	public static double floor(double a, int n) {
		
		double p = Math.pow(10.0, n);
		return Math.floor((a*p)+0.5) / p;
	}
		
	// ----------------------------------------------------------------------------
	
	/** Displays the given array in the console. */
	public static void printArray(double[] v) {
		
		int size = v.length;
		
		for (int i=0; i < size; i++)
			System.out.print(v[i] + "\t");
		
		System.out.println("");
	}
	
	// ----------------------------------------------------------------------------
	
	/** Sets the modularity detection method. */
	public void setModularityDetectionMethod(String[] methods) throws Exception, JmodException {
		
		// start by disabling all methods
		useMovingVertex_ = false;
		useGlobalMovingVertex_ = false;
		
		CommunityDividerManager dividerManager = CommunityDividerManager.getInstance();
		
		for (int i = 0; i < methods.length; i++) {
			for (int j = 0; j < dividerManager.getNumDividers(); j++) {
				if (dividerManager.getDivider(j).getIdentifier().compareTo(methods[i]) == 0) {
					dividerManager.setSelectedDividerIndex(j);
					break;
				}
			}
			if (methods[i].compareTo(MVM_METHOD_STR) == 0)
				useMovingVertex_ = true;
			else if (methods[i].compareTo(GLOBAL_MVM_METHOD_STR) == 0)
				useGlobalMovingVertex_ = true;
//			else
//				throw new JmodException("Unknown method " + methods[i] + ".");
		}
	}
	
	// ----------------------------------------------------------------------------
	
	/** Sets the modularity detection method. */
	public void setModularityDetectionMethod(String methods) throws Exception {
		
		StringTokenizer tokenizer = new StringTokenizer(methods, "+");
		String[] methodsStr = new String[tokenizer.countTokens()];
		int i = 0;
		while (tokenizer.hasMoreTokens()) {
			methodsStr[i] = tokenizer.nextToken();
			i++;
		}
		setModularityDetectionMethod(methodsStr);
	}
	
	// ----------------------------------------------------------------------------
	
	/** Sets log level. */
	public static void setLogLevel(String level) throws Exception, JmodException {
		
//		Log.info("Setting log level to " + level + ".");
		
		if (level.compareTo("NONE") == 0)
			Log.set(Log.LEVEL_NONE);
		else if (level.compareTo("TRACE") == 0)
			Log.set(Log.LEVEL_TRACE);
		else if (level.compareTo("DEBUG") == 0)
			Log.set(Log.LEVEL_DEBUG);
		else if (level.compareTo("INFO") == 0)
			Log.set(Log.LEVEL_INFO);
		else if (level.compareTo("WARN") == 0)
			Log.set(Log.LEVEL_WARN);
		else if (level.compareTo("ERROR") == 0)
			Log.set(Log.LEVEL_ERROR);
		else
			throw new JmodException("Invalid log level \"" + level + "\".");
	}
	
	// ============================================================================
	// GETTERS AND SETTERS

	public boolean getUseMovingVertex() { return useMovingVertex_; }
	public void setUseMovingVertex(boolean b) { useMovingVertex_ = b;}

	public boolean getUseGlobalMovingVertex() {	return useGlobalMovingVertex_; }
	public void setUseGlobalMovingVertex(boolean b) { useGlobalMovingVertex_ = b; }
	
	public void setNumConcurrentModuleDetections(int numConcurrentModuleDetections) { numConcurrentModuleDetections_ = numConcurrentModuleDetections; }
	public int getNumConcurrentModuleDetections() { return numConcurrentModuleDetections_; }
	
	public void setExportBasicDataset(boolean b) { exportBasicDataset_ = b; }
	public boolean getExportBasicDataset() { return exportBasicDataset_; }
	
	public void setExportCommunityNetworks(boolean b) { exportCommunityNetworks_ = b; }
	public boolean getExportCommunityNetworks() { return exportCommunityNetworks_; }
	
	public void setCommunityNetworkFormat(Structure.Format format) { communityNetworkFormat_ = format; }
	public Structure.Format getCommunityNetworkFormat() { return communityNetworkFormat_; }
	
	public void setExportColoredCommunities(boolean b) { exportColoredCommunities_ = b; }
	public boolean getExportColoredCommunities() { return exportColoredCommunities_; }
	
	public void setColoredCommunitiesNetworkFormat(Structure.Format networkFormat) { coloredCommunitiesNetworkFormat_ = networkFormat; }
	public Structure.Format getColoredCommunitiesNetworkFormat() { return coloredCommunitiesNetworkFormat_; }
	
	public void setExportCommunityTree(boolean b) { exportCommunityTree_ = b; }
	public boolean getExportCommunityTree() { return exportCommunityTree_; }
	
	public void setCurrentCommunityDividerOptions(String str) { currentCommunityDividerOptions_ = str; }
	public String getCurrentCommunityDividerOptions() { return currentCommunityDividerOptions_; }
	
	public void setModularityDetectionSnapshotMode(int mode) { modularityDetectionSnapshotMode_ = mode; }
	public int getModularityDetectionSnapshotMode() { return modularityDetectionSnapshotMode_; }
	
	public void setExportSnapshots(boolean b) { exportSnapshots_ = b; }
	public boolean getExportSnapshots() { return exportSnapshots_; }
}
