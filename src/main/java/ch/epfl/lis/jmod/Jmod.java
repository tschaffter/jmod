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

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;

import javax.swing.JOptionPane;
import javax.swing.SwingWorker;
import javax.swing.UIManager;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.OptionBuilder;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.PosixParser;
import org.apache.commons.cli.UnrecognizedOptionException;
import org.apache.commons.io.FilenameUtils;

import ch.epfl.lis.jmod.batch.JmodBatch;
import ch.epfl.lis.jmod.gui.JmodGui;
import ch.epfl.lis.jmod.modularity.ModularityDetector;
import ch.epfl.lis.jmod.modularity.ModularityDetectorSnapshot;
import ch.epfl.lis.jmod.modularity.community.RootCommunity;
import ch.epfl.lis.jmod.modularity.community.dividers.CommunityDividerManager;
import ch.epfl.lis.networks.NetworkException;
import ch.epfl.lis.networks.Node;
import ch.epfl.lis.networks.Structure;
import ch.epfl.lis.networks.parsers.GMLParser;
import ch.tschaffter.utils.ColorUtils;
import ch.tschaffter.utils.TimeUtils;

import com.esotericsoftware.minlog.Log;

/** 
 * Main class of the Jmod project.<p>
 * 
 * The main purpose of this class is to be used its Main method for the release. First
 * it parses command-line arguments. Before running a modularity detection, one has to
 * specify first:<p>
 * 
 * <ul>
 * <li>The path or regular expression (regex) which is used to identify the network files to open.
 * <li>The format of the input network files (they must all be available in the same format).
 * <li>The output directory where files are written (if any).
 * </ul><p>
 * 
 * Once this fields have been set, check the main settings of Jmod saved in the Singleton instance
 * of JmodSettings. Then, there is two ways to run an instance of Jmod:<p>
 * 
 * <ul>
 * <li>Jmod.run() runs the modularity detection and block the primary thread until all networks
 * have been processed. If settings.getNumConcurrentModuleDetections is > 1, several networks are 
 * processed in parallel (if several networks have been given as input). This option is useful for 
 * module detection methods which are not parallelized such as Newman's spectral algorithm. Both the 
 * GA-based and brute-force methods harness multiple processors.
 * <li>Jmod.execute() runs the modularity detection and gives the hand back to the main thread.
 * For example, this should be used when running a GUI in order to not freeze the interface.
 * Meanwhile, all the networks are processed in separated thread if JmodSettings.numProcessors_ > 1.
 * If JmodSettings.numProcessors_ = 1, then each network will be processed at a time.
 * </ul><p>
 * 
 * Jmod has a graphical user interface (GUI) too. To launch it, use the argument --gui.</p>
 * 
 * @version December 12, 2011
 * 
 * @author Thomas Schaffter (firstname.name@gmail.com)
 * @author Daniel Marbach (firstname.name@gmail.com)
 */
public class Jmod extends SwingWorker<Void, Void> {
	
	/** Current version of Jmod. */
	public static final String VERSION = "1.2 Beta";

	/** Reference to batch experiment. */
	private JmodBatch batch_ = null;
	/** Reference to ModularityDetector instance. */
	private ModularityDetector modularityDetector_ = null;
	/** Reference to current community root */
	private RootCommunity rootCommunity_ = null;
	
	/** Command-line options. */
	private Options options_ = null;
	/** GUI mode. */
	private boolean useGUI_ = false;
	
	/** CASE 1: Regular expression to identify which network file to open and analyze. */
	private String inputNetworksRegex_ = null;
	/** CASE 2: List of absolute paths to input network structure files. */
	private List<URI> inputNetworksURI_ = null;
	
	/** File format of the input networks. */
	private Structure.Format inputNetworksFormat_ = Structure.Format.UNDEFINED;
	
	/** Directory where the output files are saved (default: current directory). */
	private URI outputDirectory_ = new File(".").toURI();
	
	/** Status to indicate if the current modularity detection must be aborted. */
	private boolean canceled_ = false;
	
	/** When shutdown hook is called, delay in ms before applying force exit (default: 10 seconds). */
	public static final long FORCE_EXIT_DELAY_IN_MS = 10 * 1000;
    
	// ============================================================================
	// PRIVATE METHODS
	
	/** Shows the command line options help. */
	private void printHelp() {

		HelpFormatter formatter = new HelpFormatter();
		formatter.setSyntaxPrefix("");
		String prefix = "Jmod: An extensible toolkit for network module detection\n\n" +
						"Usage: java -jar jmod.jar [-f <FORMAT>] [-i <NETWORK>] [-m <METHOD>] [--methodOptions <METHOD_OPTIONS>]\n" +
						"   or: java -jar jmod.jar [-f <FORMAT>] [-i <NETWORK>] [-m <METHOD>] [--methodOptions <METHOD_OPTIONS>] [-o <DIRECTORY>]\n" +
						"   or: java -jar jmod.jar [-f <FORMAT>] [-i <NETWORK>] [-m <METHOD>] [--methodOptions <METHOD_OPTIONS>] [-o <DIRECTORY>] [-d <DATASETS>]\n" + 
						"   or: java -jar jmod.jar --gui\n";

		System.out.println(prefix);
		formatter.printHelp(100, "Options:", "", options_, "");
		
		System.exit(1);
	}
	
	// ----------------------------------------------------------------------------
	
	/** Prints the version message. */
	private void printVersion() {

		System.out.println("Jmod " + VERSION);
		System.exit(1);
	}
    
	// ============================================================================
	// PUBLIC METHODS
	
	/** Default constructor. */
	public Jmod() {
		
		GMLParser.CREATOR = "Jmod";
		GMLParser.VERSION = Jmod.VERSION;
	}
	
	// ----------------------------------------------------------------------------
	
	/** Parse the command line arguments. */
	@SuppressWarnings("static-access")
	public void parse(String args[]) throws Exception {
		
		JmodSettings settings = JmodSettings.getInstance();
		
		// create Options object
		options_ = new Options();

		options_.addOption("h", "help", false, "Print this message.");
		options_.addOption("v", "version", false, "Print the version information.");
		options_.addOption("q", "quiet", false, "Be extra quiet (set log level to WARN).");
		
		options_.addOption(OptionBuilder.withValueSeparator()
				.withLongOpt("gui")
				.withDescription("Launch the GUI of Jmod.")
				.create());
		
		int numAvailableProcessors = Runtime.getRuntime().availableProcessors();
		options_.addOption(OptionBuilder.withValueSeparator()
				.withLongOpt("numnet")
				.withDescription("Number of networks processed simultaneously (max: " + numAvailableProcessors + ", specify MAX to process in parallel as many networks as the number of available processors, default: 1).")
				.hasArgs(1)
				.withArgName("NUM")
				.create());
		
//		options_.addOption(OptionBuilder.withValueSeparator()
//				.withLongOpt("snapshots")
//				.withDescription("Export snapshots of the module detection (1=full snapshots, 2=differential snapshots, default: 2).")
//				.hasArgs(1)
//				.withArgName("MODE")
//				.create());
		
		options_.addOption(OptionBuilder.withValueSeparator()
				.withLongOpt("color-seed")
				.withDescription("Seed for generating colors to paint the communities found (default: current time).")
				.hasArgs(1)
				.withArgName("SEED")
				.create());
		
		options_.addOption(OptionBuilder.withValueSeparator()
				.withLongOpt("input")
				.withDescription("Path or regular expression to indicate which network file to open and analyze. Use wildcard matching \"*\" to specify a batch of network files (e.g. ./network_*.tsv).")
				.hasArgs()
				.withArgName("NETWORK")
				.create("i"));
		
		options_.addOption(OptionBuilder.withValueSeparator()
				.withLongOpt("output")
				.withDescription("Directory where results are saved (default: current directory)")
				.hasArgs(1)
				.withArgName("DIRECTORY")
				.create("o"));
		
		options_.addOption(OptionBuilder.withValueSeparator()
				.withLongOpt("format")
				.withDescription("Specify the format of the input network file (TSV, GML or DOT).")
				.hasArgs(1)
				.withArgName("FORMAT")
				.create("f"));
		
		options_.addOption(OptionBuilder.withValueSeparator()
				.withLongOpt("method")
				.withDescription("Select modularity detection method (default: Newman MVM gMVM).\n" +
						"Newman: Newman's spectral algorithm.\n" +
						"GA: Genetic algorithm.\n" +
						"BF: Brute force.\n" +
						"KnownModules: Computes the modularity Q of a given network partition.\n" +
						"MVM: Moving vertex method (refinement).\n" +
						"gMVM: Global moving vertex method (refinement).\n")
				.hasArgs()
				.withArgName("METHOD1 METHOD2 ...")
				.create("m"));
		
		options_.addOption(OptionBuilder.withValueSeparator()
				.withLongOpt("methodOptions")
				.withDescription("Specify the options for the selected community divider.\n" +
						"Details about the options of the available community dividers are available on Jmod webpage.")
				.hasArgs(1)
				.withArgName("\"OPTION1 OPTION2 ...\"")
				.create());
		
		options_.addOption(OptionBuilder.withValueSeparator()
				.withLongOpt("log")
				.withDescription("Set the log level (NONE, TRACE, DEBUG, INFO, WARN, ERROR, default: INFO). Messages with log level equal or above the selected log level will be printed.")
				.hasArgs(1)
				.withArgName("LEVEL")
				.create("l"));
		
		options_.addOption(OptionBuilder.withValueSeparator()
				.withLongOpt("dataset")
				.withDescription("Specify the dataset to save (default: none).\n" +
						"METRICS: Save the modularity Q, the number of indivisible communities and the computation time in milliseconds to file.\n" +
						"COMMUNITY_NETWORKS <FORMAT>: Save each community to network file using the given file format.\n" +
						"COMMUNITY_COLOR <FORMAT>: Save the input network in GML or DOT format with nodes colored depending on which community they belong to (Cytoscape and Graphviz can be used to visualize networks in GML and DOT formats, respectively).\n" +
						"COMMUNITY_TREE: Save the dendrogram of the hierarchical community tree (open it with the Matlab command \"dendrogram\"). An additional file describes in which indivisible community each vertex belong to.\n" +
						"SNAPSHOTS: Save differential snapshots of the module detection, which can be used later for making a movie of the network modular decomposition using tools provided by Jmod.\n" +
						"ALL: Save the complete dataset. Note that network files will be exported in the same format as the input network.")
				.hasArgs()
				.withArgName("DATA1 DATA2 ...")
				.create("d"));
		
		// parse options
		CommandLineParser parser = new PosixParser();
		CommandLine cmd = null;
		try {
			cmd = parser.parse(options_, args);

			if (cmd.getOptions().length == 0)
				printHelp();
			if (cmd.hasOption("help"))
				printHelp();
			if (cmd.hasOption("version"))
				printVersion();
			
			if (cmd.hasOption("log"))
				JmodSettings.setLogLevel(cmd.getOptionValue("log"));
			if (cmd.hasOption("color-seed")) {
				ColorUtils.uniformSeed_ = Integer.parseInt(cmd.getOptionValue("color-seed"));
				Log.info("Setting color seed to " + ColorUtils.uniformSeed_);
			}
			
			// whether the GUI of Jmod is launched
			useGUI_ = cmd.hasOption("gui");
			
			// required options but not extracted now (only in CLI mode)
			if (!useGUI_) {
				if (cmd.hasOption("input")) {
					String[] content = cmd.getOptionValues("input");
					if (content.length == 1) {
						// CASE 1: regex
						inputNetworksRegex_ = cmd.getOptionValue("input");
						inputNetworksURI_ = null;
					} else if (content.length > 1) {
						// CASE 2: list of URI
						inputNetworksURI_ = new ArrayList<URI>();
						for (int i = 0; i < content.length; i++)
							inputNetworksURI_.add(new File(content[i]).toURI());
						inputNetworksRegex_ = null;
					} else
						throw new JmodException("The usage of the option --input is incorrect.");
				}
				else {
					Log.warn("Jmod", "Input network file(s) not specified.\n");
					printHelp();
				}
				if (cmd.hasOption("format"))
					inputNetworksFormat_ = Structure.getFormat(cmd.getOptionValue("format"));
				else {
					Log.warn("Jmod", "Network file format not specified.\n");
					printHelp();
				}
			}
			
			if (cmd.hasOption("method")) {
				String[] methods = cmd.getOptionValues("method");
				settings.setModularityDetectionMethod(methods);
			}
			// after setting the community divider method!
			if (cmd.hasOption("methodOptions"))
				CommunityDividerManager.getInstance().getSelectedDivider().parseOptions(cmd.getOptionValue("methodOptions"));
			
			if (cmd.hasOption("numnet")) {
				int numNets = Runtime.getRuntime().availableProcessors();
				if (cmd.getOptionValue("numnet").compareTo("MAX") != 0)
					numNets = new Integer(cmd.getOptionValue("numnet"));
				settings.setNumConcurrentModuleDetections(numNets);
			}
//			if (cmd.hasOption("snapshots")) {
//				settings.setExportSnapshots(true);
//				settings.setModularityDetectionSnapshotMode(new Integer(cmd.getOptionValue("snapshots")));
//			}
			if (cmd.hasOption("quiet"))
				JmodSettings.setLogLevel("WARN");
			if (cmd.hasOption("output"))
				outputDirectory_ = new File(cmd.getOptionValue("output")).toURI(); // tested later if valid or not
			if (cmd.hasOption("dataset")) {
				String[] datasets = cmd.getOptionValues("dataset");
				// option ALL can only be in first position
				boolean datasetAll = (datasets[0].compareTo("ALL") == 0);
				for (int i = 0; i < datasets.length; i++) {
					if (datasets[i].compareTo("METRICS") == 0 || datasetAll) {
						settings.setExportBasicDataset(true);
					}
					else if (datasets[i].compareTo("COMMUNITY_NETWORKS") == 0 || datasetAll) {
						// save each community to network file using the given format
						// (EACH community and not only the indivisible communities)
						// if the network format is not recognized or if the dataset option
						// ALL is selected, the format is set as the format of the input network				
						Structure.Format format = Structure.getFormat(datasets[++i]);
						if (format == Structure.Format.UNDEFINED) {
							format = Structure.getFormat(cmd.getOptionValue("format"));
							Log.warn("Unrecognized network file format. Saving in " + format.name() + " format.");
						}
						settings.setExportCommunityNetworks(true);
						settings.setCommunityNetworkFormat(format);
					}
					else if (datasets[i].compareTo("COMMUNITY_COLOR") == 0 || datasetAll) {
						Structure.Format format = Structure.getFormat(datasets[++i]);
						if (format == Structure.Format.UNDEFINED) {
							format = Structure.getFormat(cmd.getOptionValue("format"));
							Log.warn("Unrecognized network file format. Saving in " + format.name() + " format.");
						}
						settings.setExportColoredCommunities(true);
						settings.setColoredCommunitiesNetworkFormat(format);
					}
					else if (datasets[i].compareTo("COMMUNITY_TREE") == 0 || datasetAll) {
						settings.setExportCommunityTree(true);
					}
					else if (datasets[i].compareTo("SNAPSHOTS") == 0 || datasetAll) {
						settings.setExportSnapshots(true);
					}
					else
						throw new UnrecognizedOptionException("Unknown --dataset argument " + datasets[i] + ".");
				}
			}
		} catch (UnrecognizedOptionException e) {
			Log.error("Jmod", e.getMessage());
			printHelp();
		} catch (Exception e) {
			Log.error("Jmod", "Could not successfully recognized the command-line options.", e);
			printHelp();
		}
	}

	// ----------------------------------------------------------------------------
	
	@Override
	protected Void doInBackground() throws Exception {

		// launch GUI
		if (useGUI_ && !JmodGui.exists()) {
			
			// it is required to set the look and feel before instantiating the gui
			// this is fine even if the application is used with the CLI
			// EDIT: Actually not, on Mac OS X and if run in command line, an icon
			// will briefly appear in the dock. We are safe by setting the look & fill here.
		    try {
		    	UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
		    } catch(Exception e) {
		    	Log.warn("JmodGui", "Error setting native LAF: " + e.getMessage(), e);
		    }

			Log.info("Launching GUI");
			JmodGui.getInstance().run();
			
		} else {
			batch_ = new JmodBatch();
			batch_.setOutputDirectory(outputDirectory_);
			
			// set references to input network structure files
			if (inputNetworksRegex_ != null)
				batch_.setNetworksURI(inputNetworksRegex_, inputNetworksFormat_);
			else if (inputNetworksURI_ != null)
				batch_.setNetworksURI(inputNetworksURI_, inputNetworksFormat_);
			
			batch_.run();
		}
		return null;
	}
	
	// ----------------------------------------------------------------------------
	
    @Override
    protected void done() {
    	
    	try {
			get();
		} catch (Exception e) {
			Log.info("Modularity detection interrupted.", e);
			
			if (JmodGui.exists()) {
				// if the exception is a JmodException, there is a way to
				// access the former message
				String messageOnly = e.getMessage();
				if (e instanceof ExecutionException) {
					Throwable t = e.getCause();
					if (e.getCause().getCause() != null)
						t = e.getCause().getCause();
					messageOnly = t.getMessage();
					if (t instanceof JmodException)
						messageOnly = ((JmodException) t).getMessage();
					else if (t instanceof NetworkException)
						messageOnly = ((NetworkException) t).getMessage();
				}
				if (!messageOnly.contains("canceled")) {
					messageOnly += "\nSee console for details.";
					JOptionPane.showMessageDialog(JmodGui.getInstance(), messageOnly, "Jmod message", JOptionPane.INFORMATION_MESSAGE);
				}
			}
		} finally {
			JmodGui.getInstance().modularityDetectionDone();
			
			// free memory
			if (batch_ != null)
				batch_.clear();
			if (modularityDetector_ != null)
				modularityDetector_.clear();
			if (rootCommunity_ != null)
				rootCommunity_.clear();
		}
    }
	
	// ----------------------------------------------------------------------------
	
	/** Return modularity detection on the given network */
	public double runModularityDetection(JmodNetwork network) throws Exception {
			
		try {
			JmodSettings settings = JmodSettings.getInstance();
			
			// compute modularity Q
			Log.info(network.getName(), "Running modularity detection algorithm (" + settings.getSelectedMethod() + ")");
			modularityDetector_ = new ModularityDetector(network);
			modularityDetector_.setOutputDirectory(outputDirectory_);
			
			// NEW: take snapshot of the current state of the module detection
			if (settings.getExportSnapshots()) {
				String outputDirectory = FilenameUtils.getFullPath(outputDirectory_.getPath());
						//FileUtils.getDirectoryString(outputDirectory_.getPath(), File.separator);
				String rawFilename = FilenameUtils.getBaseName(modularityDetector_.getNetwork().getName());
				ModularityDetectorSnapshot snapshot = new ModularityDetectorSnapshot(modularityDetector_,
						settings.getModularityDetectionSnapshotMode(),
						outputDirectory + rawFilename);
				modularityDetector_.setSnapshot(snapshot);
			}
			
			rootCommunity_ = new RootCommunity(modularityDetector_, network);
			rootCommunity_.runModularityDetection(true);
			
			return modularityDetector_.getModularity();
			
		} catch (OutOfMemoryError e) {
			String str = "There is not enough memory available to run this program.\n" +
						 "Quit one or more programs, and then try again.\n" +
						 "If the problem persits, you may consider increasing\n" +
						 "the amount of physical memory installed on this computer.";
			Log.error("JmodGui", str);
			throw e;
		}			
	}
	
	// ----------------------------------------------------------------------------
	
	/** Prints the result of the modularity detection to the given PrintStream. */
	public void printResult() throws Exception {
		
		String networkName = modularityDetector_.getNetwork().getName();
		Log.info(networkName, "Modularity Q: " + modularityDetector_.getModularity());
		Log.info(networkName, "Number of indivisible communities: " + rootCommunity_.getNumIndivisibleCommunities());
		Log.info(networkName, "Number of community tree depths: " + rootCommunity_.getCommunityTreeDepth());
		Log.info(networkName, "Computation time: " + TimeUtils.formatTime(rootCommunity_.getComputationTime()));
	}
	
	// ----------------------------------------------------------------------------
	
	/** Saves modularity to file. */
	public void exportModularity(URI uri) throws Exception {
		
		FileWriter fstream = null;
		BufferedWriter out = null;
		try {
			Log.info(modularityDetector_.getNetwork().getName(), "Writing network modularity Q " + uri.getPath());
			Double Q = modularityDetector_.getModularity();
			fstream = new FileWriter(new File(uri));
			out = new BufferedWriter(fstream);
			out.write(Q.toString());

		} catch (Exception e) {
			Log.error("Jmod", "Unable to save the modularity Q to " + uri.getPath());
			throw e;
		} finally {
			if (out != null)
				out.close();
		}
	}
	
	// ----------------------------------------------------------------------------
	
	/** Saves number of indivisible communities to file. */
	public void exportNumIndivisibleCommunities(URI uri) throws Exception {
		
		FileWriter fstream = null;
		BufferedWriter out = null;
		try {;
			Log.info(modularityDetector_.getNetwork().getName(), "Writing number of indivisible communities " + uri.getPath());
			Integer numIndivisibleCommunities = rootCommunity_.getNumIndivisibleCommunities();
			fstream = new FileWriter(new File(uri));
			out = new BufferedWriter(fstream);
			out.write(numIndivisibleCommunities.toString());

		} catch (Exception e) {
			Log.error("Jmod", "Unable to save the number of indivisible communities to " + uri.getPath());
			throw e;
		} finally {
			if (out != null)
				out.close();
		}
	}
	
	// ----------------------------------------------------------------------------
	
	/** Saves the computation time in milliseconds to file. */
	public void exportComputationTime(URI uri) throws Exception {
		
		FileWriter fstream = null;
		BufferedWriter out = null;
		try {;
			Log.info(modularityDetector_.getNetwork().getName(), "Writing computation time " + uri.getPath());
			Long computationTime = rootCommunity_.getComputationTime();
			fstream = new FileWriter(new File(uri));
			out = new BufferedWriter(fstream);
			out.write(computationTime.toString());

		} catch (Exception e) {
			Log.error("Jmod", "Unable to save the computation time to " + uri.getPath());
			throw e;
		} finally {
			if (out != null)
				out.close();
		}
	}
	
	// ----------------------------------------------------------------------------
	
	/** Saves to which community each node has been detected to belong to. */
	public void exportCommunities(URI uri) throws Exception {
		
		FileWriter fstream = null;
		BufferedWriter out = null;
		try {
			Log.info(modularityDetector_.getNetwork().getName(), "Writing communities " + uri.getPath());
			fstream = new FileWriter(new File(uri));
			out = new BufferedWriter(fstream);
			String content = "";
			
			// sort nodes by names
			List<Node> nodes = new ArrayList<Node>(modularityDetector_.getNetwork().getNodes().values());
			Collections.sort(nodes, new Structure.NodeComparator<Node>());
			
			for (Node node : nodes)
				content += node.getName() + "\t" + node.getCommunityIndex() + "\n";
			out.write(content);

		} catch (Exception e) {
			Log.error("Jmod", "Unable to save the communities to " + uri.getPath());
			throw e;
		} finally {
			if (out != null)
				out.close();
		}
	}
	
	// ----------------------------------------------------------------------------
	
	/** Save the dataset. */
	public void exportDataset() throws Exception {
		
		JmodSettings settings = JmodSettings.getInstance();
		
		String outputDirectoryStr = FilenameUtils.getFullPath(outputDirectory_.getPath());
				//FileUtils.getDirectoryString(outputDirectory_.getPath(), "/");
		String rawFilename = FilenameUtils.getBaseName(modularityDetector_.getNetwork().getName());
		String networkName = modularityDetector_.getNetwork().getName();
		
		// save the modularity and the number of individual communities
		try {
			if (canceled_) return;
			if (settings.getExportBasicDataset() && !canceled_) {
				exportModularity(new File(outputDirectoryStr + rawFilename + "_Q.txt").toURI());
				exportNumIndivisibleCommunities(new File(outputDirectoryStr + rawFilename + "_numIndCommunities.txt").toURI());
				exportComputationTime(new File(outputDirectoryStr + rawFilename + "_time.txt").toURI());
				exportCommunities(new File(outputDirectoryStr + rawFilename + "_inferred_community.dat").toURI());
			}
		} catch (Exception e) {
			Log.warn("Jmod", "Unable to export dataset content METRICS.", e);
		}
		
		// save each community to network file in the given format
		try {
			if (canceled_) return;
			if (settings.getExportCommunityNetworks() && !canceled_) {
				Structure.Format communityFormat = settings.getCommunityNetworkFormat();
				Log.info(networkName, "Writing each community to network file in " + communityFormat.name());
				getRootCommunity().exportCommunityNetworks(outputDirectory_, communityFormat);
			}
		} catch (Exception e) {
			Log.warn("Jmod", "Unable to export dataset content COMMUNITY_NETWORKS.", e);
		}
		
		// save input network in GML format with communities colored
		try {
			if (canceled_) return;
			if (settings.getExportColoredCommunities() && !canceled_) {
				// get the file format to use for exporting colored communities
				Structure.Format networkFormat = settings.getColoredCommunitiesNetworkFormat();		
				Log.info(networkName, "Writing the input network with communities colored in GML format");
				getRootCommunity().exportColorCommunities(outputDirectory_, networkFormat);
			}
		} catch (Exception e) {
			Log.warn("Jmod", "Unable to export dataset content COMMUNITY_COLOR.", e);
		}
		
		// if the dendrogram of the hierarchical community tree
		try {
			if (canceled_) return;
			if (settings.getExportCommunityTree() && !canceled_) {
				Log.info(networkName, "Writing the dendrogram of the hierarchical community tree");
				getRootCommunity().exportIndivisibleCommunities(outputDirectory_);
			}
		} catch (Exception e) {
			Log.warn("Jmod", "Unable to export dataset content COMMUNITY_TREE.", e);
		}
	}
	
	// ----------------------------------------------------------------------------
	
	/** Returns the signature of Jmod. */
	public static void printSignature() {
		
		System.out.println("");
		System.out.println("Copyright (c) 2013 Thomas Schaffter, Daniel Marbach");
		System.out.println("Project webpage: http://tschaffter.ch/projects/jmod");
	}
	
	// ----------------------------------------------------------------------------
	
	/**
	 * Cancels the execution as soon as this batch get aware of it. It is possible to
	 * kill the thread using executor_.shutdownNow(). However the workers can be
	 * manipulating file at that time and thus kill them would result in having
	 * undetermined states. That's while it's safer to use an approach based on
	 * the use of a status flag checked as often as possible in order to leave the
	 * process in good condition. Note that the methods writing networks or datasets
	 * to files are not canceled during execution but before starting writing each
	 * file, the application checks if it must exit or not.
	 */
	public void cancel() {
		
		canceled_ = true;
		
		// either it's batch_ which is running or this.modularityDetector_
		if (batch_ != null && batch_.isRunning())
			batch_.cancel();
		else if (modularityDetector_ != null)
			modularityDetector_.cancel();
		
		if (rootCommunity_ != null)
			rootCommunity_.isCanceled(true);
	}
	
	// ----------------------------------------------------------------------------
	
	/** Calls this method to properly exit Jmod. */
	public void exitJmod() {
		
		try {
			// stop running processes (if any)
			cancel();
			
		} catch (Exception e) {
			Log.error("Jmod", "Unable to exit Jmod properly.", e);
		} finally {
			Jmod.printSignature();
		}
	}
	
	// ----------------------------------------------------------------------------
	
	/** Main method. */
	public static void main(String args[]) {
		
		try {
			Jmod jmod = new Jmod();
			
			// load additional community dividers
			CommunityDividerManager.getInstance();
			
			// set shutdown hook to ensure that the app exits safely
			Runtime.getRuntime().addShutdownHook(new Jmod.JmodShutdownHook(jmod));
			
			// Hack to run the GUI with Java Web Start
			// For an unknown reason <argument> is ignored...
			// do not forget to change below arg into arg2 
//			String[] args2 = new String[args.length+1];
//			for (int i = 0; i < args.length; i++)
//				args2[i] = args[i];
//			args2[args2.length-1] = "--gui";
			
			// parse the command-line options
			jmod.parse(args); // args2 for javaws
			
			jmod.run();
			jmod.get();
			
		} catch (Exception e) {
			Log.error("Jmod", e);
		} finally {
			// leave the application if CLI used
			if (!JmodGui.exists())
				System.exit(0);
		}
	}
	
	// ============================================================================
	// GETTERS AND SETTERS
	
	public void setMyModularityDetector(ModularityDetector md) { modularityDetector_ = md; }
	public ModularityDetector getModularityDetector() { return modularityDetector_; }
	
	public double getModularity() { return modularityDetector_.getModularity(); }
	
	public JmodBatch getBatch() { return batch_; }
	
	public RootCommunity getRootCommunity() { return rootCommunity_; }
	
	public void setInputNetworksRegex(String str) { inputNetworksRegex_ = str; }
	public String getInputNetworskRegex() { return inputNetworksRegex_; }
	
	public void setInputNetworksFormat(Structure.Format format) { inputNetworksFormat_ = format; }
	public Structure.Format getInputNetworksFormat() { return inputNetworksFormat_; }
	
	public void setOutputDirectory(URI directory) { outputDirectory_ = directory; }
	public URI getOutputDirectory() { return outputDirectory_; }
	
	// ============================================================================
	// INNER CLASSES
	
	/**
	 * Implements the shutdown hook for Jmod.<p>
	 * The shutdown hook is called by System.exit(). Note that capturing Ctrl+c
	 * also calls System.exit().
	 */
	private static class JmodShutdownHook extends Thread {
		
		// ============================================================================
		// PUBLIC METHODS
		
		/** Jmod instance. */
		private Jmod jmod_ = null;
		
		/** Constructor. */
		public JmodShutdownHook(Jmod jmod) {
			
			jmod_ = jmod;
		}
		
		// ----------------------------------------------------------------------------
		
		/** Exits Jmod safely. */
		@Override
	    public void run() {

	    	jmod_.exitJmod();
	    }
	}
}
