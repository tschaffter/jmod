/*
Copyright (c) 2008-2013 Thomas Schaffter

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

import java.io.File;
import java.io.FileWriter;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;

import ch.epfl.lis.jmod.batch.NetworkFilenameFilter;
import ch.epfl.lis.jmod.modularity.ModularityDetectorSnapshot;
import ch.epfl.lis.networks.Edge;
import ch.epfl.lis.networks.Node;
import ch.epfl.lis.networks.Structure;
import ch.epfl.lis.networks.graphics.EdgeGraphics;
import ch.epfl.lis.networks.graphics.GraphGraphics;
import ch.epfl.lis.networks.graphics.NodeGraphics;
import ch.epfl.lis.networks.parsers.TSVParser;
import ch.tschaffter.utils.ColorUtils;

import com.esotericsoftware.minlog.Log;

/**
 * Converts module detection snapshots to graph files.
 * 
 * @see ModularityDetectorSnapshot
 *
 * @version June 9, 2013
 * 
 * @author Thomas Schaffter (firstname.name@gmail.com)
 */
public class JmodReplay {
	
	/** Name of the folder created at the location of the snapshot files to where network files will be exported (includes final /). */
	public static final String OUTPUT_FOLDER = "snapshot_networks/";
	/** Default filename suffix of the color lookup tables (without directory). */
	public static final String COLORS_LUT_FILENAME_SUFFIX = "_colors.txt";
	/** Filename suffix of the file containing the modifications to apply to the colors LUT. */
	public static final String COLORS_LUT_MODS_FILENAME_SUFFIX = "_colors_mods.txt";

	/** Root filename of the snapshots including the path of the directory. */
	protected String snapshotRegex_ = null;
	/** File of the color lookup table for painting nodes (read or write). */
	protected String colorsLUTFilename_ = null;
	/** Network filename (if possible with graphics data). */
	protected String networkFilename_ = null;
	
	/** Snapshots directory obtained from snapshot root filenames. */
	protected String snapshotsDirectory_ = null;
	/** Snapshots base filenames (without directory). */
	protected List<String> snapshotsBaseNames_ = null;
	
	/** Output directory (includes final /). */
	protected String outputDirectory_ = null;
	/** Removes any suffix after the file number (e.g. required for loading image sequence in VirtualDub). */
	protected boolean forceEndFilenamesWithNumbers_ = false;
	
	/** Hex string color lookup table. */
	protected List<String> colorsLUT_ = null;
	/** This seed is used when generating new color lookup table (set to -1 for random). */
	protected int colorsLUTSeed_ = 1;
	/** Forces the (re)generation of the color LUT file. */
	protected boolean forceColorsLUTGeneration_ = false;
	/**
	 * Colors modifications (loaded from file, optional).
	 * Format:
	 * - Column 1: iteration number where the modification must be applied
	 * - Column 2 and column 3: indexes of two colors to swap 
	 */
	protected ArrayList<String[]> colorsMods_ = null;
	
	/** Network used to export the snapshot (could include graphics data). */
	protected JmodNetwork network_ = null;
	/** Format of the input network which serves as template for graphics. */
	protected Structure.Format networkInputFormat_ = Structure.Format.GML;
	/** Format of the snapshot output networks. */
	protected Structure.Format networkOutputFormat_ = Structure.Format.GML;
	
	/** Export states that are not different from previous. */
	protected boolean exportUnchangedSnapshots_ = true;
	
	/** Flag to set to true if the input dot network files include graphics data originating from Cytoscape. */
	protected boolean cytoscapeGraphics_ = true;
	
	// ============================================================================
	// PROTECTED METHODS
	
	/** Writes the given color table to file. Indexes run from 0 to n-1. */
	protected void writeColorLookupTable(URI uri, List<String> colors) throws Exception {
		
		if (uri == null)
			throw new Exception("ERROR: URI is null");
		if (colors == null || colors.isEmpty())
			throw new Exception("ERROR: No colors to write.");
		
		String content = "";
		for (int i = 0; i < colors.size(); i++)
			content += i + "\t" + "\"" +  colors.get(i) + "\"\n";
		
		FileWriter fw = new FileWriter(new File(uri), false);
		fw.write(content);
		fw.close();
	}
	
	// ----------------------------------------------------------------------------
	
	/** Reads the color lookup table from the given file. Colors returned are in hex string format. */
	protected List<String> readColorLookupTable(URI uri) throws Exception {
		
		if (uri == null)
			throw new Exception("ERROR: URI is null");
		
		List<String> hexColors = new ArrayList<String>();
		ArrayList<String[]> rawData = TSVParser.readTSV(uri);
		for (int i = 0; i < rawData.size(); i++) {
			// ignores index
			hexColors.add(rawData.get(i)[1]);
		}
		
		return hexColors;
	}
	
	// ----------------------------------------------------------------------------
	
	/** Returns the name of the snapshot files without the directory. */
	protected List<String> getSnapshotBaseNames(String snapshotRootFilename) throws Exception {
		
		String regex = snapshotRootFilename;
		
		// extract the directory string from the input regex
		String directory = FilenameUtils.getFullPath(regex);
		File directoryFile = new File(directory);
		if (directory.length() == 0)
			directoryFile = new File(".");

		if (directoryFile == null || !directoryFile.isDirectory())
			throw new JmodException("The directory " + directory + " is not valid and snapshot files can't be found.");
		
		// list network files matching the input regex
		// TODO: check next update
		String truncatedRegex = FilenameUtils.getName(regex);
//		String truncatedRegex = FileUtils.getFilenameString(regex, JmodSettings.FILE_SEPARATOR);
		
		String[] children = directoryFile.list(new NetworkFilenameFilter(truncatedRegex));
		
		// remove the max state value and color filenames
		List<String> filtered = new ArrayList<String>();
		for (int i = 0; i < children.length; i++) {
			if (children[i].contains(ModularityDetectorSnapshot.LARGEST_STATE_VALUE_FILENAME_SUFFIX))
				continue;
			if (children[i].contains(COLORS_LUT_FILENAME_SUFFIX))
				continue;
			if (!children[i].contains(ModularityDetectorSnapshot.SNAPSHOT_SUFFIX))
				continue;
			filtered.add(children[i]);
		}
		children = filtered.toArray(new String[filtered.size()]);
		Arrays.sort(children, new NumberedFilenameComparator());
		
		List<String> snapshots = new ArrayList<String>();
		for (int i = 0; i < children.length; i++)
			snapshots.add(children[i]);

		return snapshots;
	}
	
	// ----------------------------------------------------------------------------
	
	/** Returns the maximum value of the state which can be found in the last snapshot. */
	protected int findMaximumStateValue(URI snapshotUri) throws Exception {
		
		ArrayList<String[]> rawData = TSVParser.readTSV(snapshotUri);
		int max = Integer.parseInt(rawData.get(0)[0]);

		return max;
	}
	
	// ----------------------------------------------------------------------------
	
	/**
	 * Returns the part of a string before _NUMBER_.
	 * If the pattern _NUMBER_ is not found, returns null.
	 */
	public static String getStringBeforeUnderscoreNumberUnderscore(String str) {
		
		String output = null;
		Pattern p = Pattern.compile("^(.*?)_(\\d+)_1*");
		Matcher m = p.matcher(str);
		while (m.find())
			output = m.group(1);
		
		return output;
	}
	
	// ----------------------------------------------------------------------------
	
	/** Exports one snapshot to network file. */
	protected void exportSnapshot(int snapshotIndex) throws Exception {
		
		URI snapshotUri = new File(snapshotsDirectory_ + snapshotsBaseNames_.get(snapshotIndex)).toURI();
		String outputNetworkFilename = outputDirectory_ +
			snapshotsBaseNames_.get(snapshotIndex).substring(0, snapshotsBaseNames_.get(snapshotIndex).lastIndexOf('.')) + // filename without extension
			Structure.getFormatExtension(networkOutputFormat_); // adds extension
		
		// modifies the output filename to make it compatible with programs that allows to load image sequences like VirtualDub
		// VirtualDub: filenames must end with NUMBER*.ext where * can be anything but must be the same in all filenames
		// Adobe Premiere: filenames must end with NUMBER.ext (has to been taken care of in Graphviz converter (option -O))
		if (forceEndFilenamesWithNumbers_) {
			outputNetworkFilename = getStringBeforeUnderscoreNumberUnderscore(snapshotsBaseNames_.get(snapshotIndex));
			if (outputNetworkFilename == null)
				throw new Exception("There is no pattern _NUMBER_ in the filename " + snapshotsBaseNames_.get(snapshotIndex) + ".");
			outputNetworkFilename = outputDirectory_ + outputNetworkFilename + "_" + snapshotIndex + Structure.getFormatExtension(networkOutputFormat_);
		}
	
		URI outputNetworkUri = new File(outputNetworkFilename).toURI();
		
		// read snapshot content
		ArrayList<String[]> data = TSVParser.readTSV(snapshotUri);
		
		// set the new colors  of the nodes in the network
		boolean stateChanged = (data.size() > 0);
		if (stateChanged) {
			for (int i = 0; i < data.size(); i++) {
				setNodeFillColor(network_, data.get(i)[0], colorsLUT_.get(Integer.parseInt(data.get(i)[1])));
			}
		}
		
		// write the network
		if (exportUnchangedSnapshots_ || stateChanged) {
			Log.info("Writing " + outputNetworkFilename);
			network_.write(outputNetworkUri, networkOutputFormat_);
		}
	}
	
	// ----------------------------------------------------------------------------
	
	/** In the given network, set the fill color of the node given by the node name. */
	protected void setNodeFillColor(JmodNetwork network, String nodeName, String hexColor) throws Exception {
		
		Node node = null;
		if ((node = network.getNode(nodeName)) != null)
			node.getGraphics().setFill(hexColor);
		else
			throw new Exception("Node not found.");
	}
	
	// ----------------------------------------------------------------------------
	
	/** 
	 * Method called to apply custom modification to nodes graphics data when these data originate from Cytoscape.
	 * The parameters of the conversion requires to do some test before obtaining dot files that will later
	 * generate nice and HD images.
	 */
	protected void convertCytoscapeNodeGraphicsForDotFormat() throws Exception {
		
		// get the dpi required for conversion inches/pixels
		int dpi = 150;
		int width = 1920; // px
		int height = 810; // px
		String bgcolor = "transparent"; // transparent
		boolean showNodeLabel = false;
		double nodeSizeMultiplier_ = 1.;
		double nodePenwidthMultiplier_ = 0.25;
		double edgePenwidthMultiplier_ = 0.25;
		String edgeColor = "#606060";
		String nodeShape = "circle"; // max(nodeW,nodeH) is used
		
		boolean mirrorX = true;
		boolean mirrorY = true;
		
		// set graph properties
		GraphGraphics graphGraphics = network_.getGraphics();
		graphGraphics.setDpi(dpi);
		graphGraphics.setWidth(width / (double)dpi);
		graphGraphics.setHeight(height / (double)dpi);
		graphGraphics.setBgColor(bgcolor);
		graphGraphics.showNodeLabels(showNodeLabel);
		graphGraphics.setDotMaxIters(0); // use the node positions given by Cytoscape
		graphGraphics.setCenter(true); // required with the simple implementation of the mirroring
		
		// Cytoscape gives the node position and node size in px, so lets convert them to inches
		// Divides also node and edge penwidth
		NodeGraphics nodeGraphics = null;
		
		// sort nodes by names
		List<Node> nodes = new ArrayList<Node>(network_.getNodes().values());
		Collections.sort(nodes, new Structure.NodeComparator<Node>());
		
		for (Node node : nodes) {
			nodeGraphics = node.getGraphics();
			
			// neato inverse the y-axis compared to the data exported by Cytoscape
			if (mirrorY && graphGraphics.getHeight() > 0 && nodeGraphics.getY() != null)
				nodeGraphics.setY(graphGraphics.getHeight() - nodeGraphics.getY());
			// adds the possibility to mirror x
			if (mirrorX && graphGraphics.getWidth() > 0 && nodeGraphics.getX() != null)
				nodeGraphics.setX(graphGraphics.getWidth() - nodeGraphics.getX());
			
			nodeGraphics.setLabel(node.getName());
			nodeGraphics.setX(nodeGraphics.getX() / dpi);
			nodeGraphics.setY(nodeGraphics.getY() / dpi);
			nodeGraphics.setW(nodeSizeMultiplier_ * nodeGraphics.getW() / dpi);
			nodeGraphics.setH(nodeSizeMultiplier_ * nodeGraphics.getH() / dpi);
			nodeGraphics.setOutlineWidth(nodeGraphics.getOutlineWidth() * nodePenwidthMultiplier_);
			nodeGraphics.setType(nodeShape);
		}
		
		// sort nodes by names
		List<Edge<Node>> edges = new ArrayList<Edge<Node>>(network_.getEdges().values());
		Collections.sort(edges, new Structure.EdgeComparator<Edge<Node>>());
		
		EdgeGraphics edgeGraphics = null;
		for (Edge<Node> edge : edges) {
			edgeGraphics = edge.getGraphics();
			edgeGraphics.setWidth(edgeGraphics.getWidth() * edgePenwidthMultiplier_);
			edgeGraphics.setFill(edgeColor);
		}
	}
	
	// ----------------------------------------------------------------------------
	
	/** Reads color modifications if the file exists. */
	public void readColorsMods() throws Exception {
		
		String filename = outputDirectory_ + 
			snapshotsBaseNames_.get(0).substring(0, snapshotsBaseNames_.get(0).indexOf("_0_")) + 
			COLORS_LUT_MODS_FILENAME_SUFFIX;
		
		File f = new File(filename);
		if (f == null || !f.exists())
			return;
		
		colorsMods_ = TSVParser.readTSV(new File(filename).toURI());			
	}
	
	// ----------------------------------------------------------------------------
	
	/** Looks at colorsMods_ to see if modifications must be applied to the colors. */
	public void applyColorsMods(int snapshotIndex) {
		
		if (colorsMods_ == null)
			return;
		
		while (colorsMods_.size() > 0) {

			if (Integer.parseInt(colorsMods_.get(0)[0]) != snapshotIndex)
				return;
			
			int colorA = Integer.parseInt(colorsMods_.get(0)[1]);
			int colorB = Integer.parseInt(colorsMods_.get(0)[2]);
			
			Log.info("Swapping colors " + colorA + " and " + colorB);
			
			String tmp = colorsLUT_.get(colorA);
			colorsLUT_.set(colorA, colorsLUT_.get(colorB));
			colorsLUT_.set(colorB, tmp);
			
			colorsMods_.remove(0);
		}
	}
	
	// ============================================================================
	// PUBLIC METHODS
	
	/** Default constructor. */
	public JmodReplay() {}
	
	// ----------------------------------------------------------------------------
	
	/** Run method. */
	public void run() throws Exception {
		
		// get snapshots URI
		snapshotsBaseNames_ = getSnapshotBaseNames(snapshotRegex_);
		snapshotsDirectory_ = FilenameUtils.getFullPath(snapshotRegex_);
		
		if (snapshotsBaseNames_.size() == 0) {
			Log.info("No snapshots matching " + snapshotRegex_);
			return;
		}
		
		// makes the folder to which the network files will be saved
		outputDirectory_ = snapshotsDirectory_ + OUTPUT_FOLDER;
		FileUtils.forceMkdir(new File(outputDirectory_));
		
		// find number of node states
		// build the filename containing this value assuming that the first snapshot filename
		// includes "_0_"
		int numStates = 0;
		try {
			String maxStateValueFilename = snapshotsDirectory_ + 
				snapshotsBaseNames_.get(0).substring(0, snapshotsBaseNames_.get(0).indexOf("_0_")) + 
				ModularityDetectorSnapshot.LARGEST_STATE_VALUE_FILENAME_SUFFIX;
			numStates = findMaximumStateValue(new File(maxStateValueFilename).toURI()) + 1;
		} catch (Exception e) {
			Log.error("ERROR: Unable to get the maximum state value: " + e.getMessage());
			return;
		}
		
		// load of generate new color table
		// we work directly with hex color strings
		String colorLUTDefaultFilename = outputDirectory_ + 
			snapshotsBaseNames_.get(0).substring(0, snapshotsBaseNames_.get(0).indexOf("_0_")) + 
			COLORS_LUT_FILENAME_SUFFIX;
		if (new File(colorLUTDefaultFilename).exists() && !forceColorsLUTGeneration_) {
			Log.info("Reading default color LUT " + colorLUTDefaultFilename);
			colorsLUT_ = readColorLookupTable(new File(colorLUTDefaultFilename).toURI());
		} else if (colorsLUTFilename_ == null || forceColorsLUTGeneration_) {
			// generate new color lookup table
			int seedBkp = ColorUtils.uniformSeed_;
			ColorUtils.uniformSeed_ = colorsLUTSeed_;
			colorsLUT_ = ColorUtils.rgb2hexString(ColorUtils.generateNDistinctColors(numStates));
			ColorUtils.uniformSeed_ = seedBkp;
			// write to file
			Log.info("Writing color LUT " + colorLUTDefaultFilename);
			writeColorLookupTable(new File(colorLUTDefaultFilename).toURI(), colorsLUT_);
		} else if (colorsLUTFilename_ != null && new File(colorsLUTFilename_).exists()) {
			Log.info("Reading color LUT " + colorLUTDefaultFilename);
			colorsLUT_ = readColorLookupTable(new File(colorsLUTFilename_).toURI());
		}
		
		// load color modifications (file optional)
		readColorsMods();
		
		// update the nodes graphics data in case they originate from Cytoscape
		if (cytoscapeGraphics_ && networkOutputFormat_ == Structure.Format.DOT)
			convertCytoscapeNodeGraphicsForDotFormat();
		
		// export the snapshots to network files
		for (int i = 0; i < snapshotsBaseNames_.size(); i++) {
			applyColorsMods(i);
			exportSnapshot(i);
		}
	}
	
	// ----------------------------------------------------------------------------
	
	/** Main method. */
	public static void main(String args[]) {
		
		try {
			JmodReplay replay = new JmodReplay();
			replay.setSnapshotRegex("/home/tschaffter/jmod_ga_animation/karate_movie/BF_multi/karate_*.txt");
//			replay.setSnapshotRegex("/home/tschaffter/jmod_ga_animation/LFR_movie/GA/network_1_big_*.txt");
			replay.setNetworkOutputFormat(Structure.Format.DOT);
			replay.setInputNetwork("/home/tschaffter/jmod_ga_animation/karate_movie/karate_movie_2.gml", Structure.Format.GML);
//			replay.setInputNetwork("/home/tschaffter/jmod_ga_animation/LFR_movie/network_1_big_cys_jmod.gml", Structure.GML);
			replay.setColorsLUTFilename(null);
			replay.setCytoscapeGraphics(true);
			replay.forceEndFilenamesWithNumbers(true);
			
			replay.run();
			
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			Log.info("Done");
			System.exit(0);
		}
	}
	
	// ============================================================================
	// GETTERS AND SETTERS
	
	public void setInputNetwork(String networkFilename, Structure.Format networkFormat) throws Exception {
		
		network_ = new JmodNetwork();
		network_.read(new File(networkFilename).toURI(), networkFormat);
		
		// XXX for LFR only: "10 (community X)" -> "10"
//		String nodeName = null;
//		for (int i = 0; i < network_.getSize(); i++) {
//			nodeName = network_.getNode(i).getName();
//			nodeName = nodeName.substring(0, nodeName.indexOf(" "));
//			network_.getNode(i).setName(nodeName);
//		}
	}
	
	public void setSnapshotRegex(String regex) { snapshotRegex_ = regex; }
	public void setNetworkOutputFormat(Structure.Format networkFormat) { networkOutputFormat_ = networkFormat; }
	
	/** If null, uses the default color file and generates it if not existing. */
	public void setColorsLUTFilename(String filename) { colorsLUTFilename_ = filename; }
	
	public void setCytoscapeGraphics(boolean cytoscapeGraphics) { cytoscapeGraphics_ = cytoscapeGraphics; }
	
	public void forceEndFilenamesWithNumbers(boolean b) { forceEndFilenamesWithNumbers_ = b; }
	
	// ============================================================================
	// INNER CLASSES
	
	/** Comparator to order filenames so that base_2.txt and not base_10.txt follows base_1.txt. */
	public class NumberedFilenameComparator implements Comparator<String> {
		
		@Override
		public int compare(String s1, String s2) {
			
			String number1 = null;
			String number2 = null;
			
			// extracts the first number surrounded by underscores
			Pattern p = Pattern.compile("^*_(\\d+)_1*");
			Matcher m = p.matcher(s1); 
			while (m.find())
				number1 = m.group(1);
			
			m = p.matcher(s2); 
			while (m.find())
				number2 = m.group(1);
			
			
			int val = s1.compareTo(s2);
			if (val != 0) {
				int int1 = Integer.parseInt(number1);
				int int2 = Integer.parseInt(number2);
				val = int1 - int2;
			}
			return val;
		}
	}
}
