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

package ch.epfl.lis.jmod.gui;

import java.awt.Dimension;
import java.awt.Image;
import java.awt.Point;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.swing.DefaultComboBoxModel;
import javax.swing.ImageIcon;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.WindowConstants;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.DefaultCaret;
import javax.swing.text.StyledDocument;

import org.apache.commons.io.FilenameUtils;

import ch.epfl.lis.jmod.Jmod;
import ch.epfl.lis.jmod.JmodException;
import ch.epfl.lis.jmod.JmodSettings;
import ch.epfl.lis.jmod.modularity.community.dividers.CommunityDivider;
import ch.epfl.lis.jmod.modularity.community.dividers.CommunityDividerManager;
import ch.epfl.lis.networks.Structure;

import com.esotericsoftware.minlog.Log;

/** 
 * Implements the GUI of Jmod.
 * 
 * @version December 2, 2011
 * 
 * @author Thomas Schaffter (firstname.name@gmail.com)
 * 
 * @see Jmod
 */
public class JmodGui extends JmodDialog implements ActionListener {
	
	/** Serial */
	private static final long serialVersionUID = 1L;
	
	/** The unique instance of JmodGui (Singleton design pattern) */
	private static JmodGui instance_ = null;
	
	/** Instance of Jmod currently used (null when no modularity detection is running). */
	private Jmod jmod_ = null;
	
	// ============================================================================
	// PRIVATE METHODS
	
	/** Initializes the GUI of Jmod. */
	private void initialize() throws Exception {
		
		// set dialog title
		setTitle("Jmod " + Jmod.VERSION);
		
		// override the default behavior of the closing button of the application
		setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
		addWindowListener(new WindowAdapter() {
			public void windowClosing(WindowEvent event) {
				try {
			        int reply = JOptionPane.showConfirmDialog(instance_,
					   		"Exit Jmod ?",
					   		"Jmod message",
					   		JOptionPane.YES_NO_OPTION);
			        
			        if (reply == JOptionPane.NO_OPTION)
			        	return;
			        
			        // Jmod is leaving after calling that instruction
			        exitJmodGui();
			        
				} catch (Exception e) {
					Log.error("JmodGui", "Failed to override default closing operation.", e);
				}
			}
		});
		
		try {
			// set application icon
			// the OS will choose the most suitable one
			List<Image> icons = new ArrayList<Image>();
			URL url = JmodGui.class.getResource("rsc/jmod-icon-256.png");
			icons.add(new ImageIcon(url).getImage());
			url = JmodGui.class.getResource("rsc/jmod-icon-156.png");
			icons.add(new ImageIcon(url).getImage());
			url = JmodGui.class.getResource("rsc/jmod-icon-128.png");
			icons.add(new ImageIcon(url).getImage());
			url = JmodGui.class.getResource("rsc/jmod-icon-64.png");
			icons.add(new ImageIcon(url).getImage());
			url = JmodGui.class.getResource("rsc/jmod-icon-48.png");
			icons.add(new ImageIcon(url).getImage());
			url = JmodGui.class.getResource("rsc/jmod-icon-32.png");
			icons.add(new ImageIcon(url).getImage());
			url = JmodGui.class.getResource("rsc/jmod-icon-24.png");
			icons.add(new ImageIcon(url).getImage());
			url = JmodGui.class.getResource("rsc/jmod-icon-16.png");
			icons.add(new ImageIcon(url).getImage());
			setIconImages(icons);
		} catch (Exception e) {
			Log.warn("JmodGui", "Failed to set the window icon.", e);
		}
		
//		// Try to have a nice icon on Windows, here is how I did for GNW
//		try {
//			URL jmodIcon = getClass().getResource("rsc/jmod-icon-156.png");
//			setIconImage(new ImageIcon(jmodIcon).getImage());
//		} catch (NoSuchMethodError e) {
//			// setIconImage() doesn't exist in Mac OS Java implementation.
//		}
		
		JmodSettings settings = JmodSettings.getInstance();
		
		// add document lister to know when counting the number of selected networks
		inputNetworksTField_.getDocument().addDocumentListener(new InputNetworksDocumentListener());
		inputNetworksFormatCBox_.setModel(new DefaultComboBoxModel<String>(Structure.getFormatStrings()));
		inputNetworksTField_.addActionListener(this);
		inputNetworksBrowse_.addActionListener(this);
		
		// fill the combobox with available community dividers
		divisionMethodCBox_.removeAllItems();
		CommunityDividerManager dividerManager = CommunityDividerManager.getInstance();
		for (CommunityDivider divider : dividerManager.getDividers())
			divisionMethodCBox_.addItem(divider.getName());
		
		// set the selected item of the combobox from the selected index of the community divider manager
		divisionMethodCBox_.addActionListener(this);
		divisionMethodCBox_.setSelectedIndex(dividerManager.getSelectedDividerIndex());
		
		divisionMethodCBox_.setSelectedIndex(0);
		methodMVMCBox_.setSelected(settings.getUseMovingVertex());
		methodGMVMCBox_.setSelected(settings.getUseGlobalMovingVertex());
		int numProcessors = Runtime.getRuntime().availableProcessors();
		numProcessorsSpinner_.setModel(new SpinnerNumberModel(1, 1, numProcessors, 1));
		numProcessorsLabel_.setText(numProcessorsLabel_.getText().replaceAll("N", Integer.toString(numProcessors)));
		runButton_.addActionListener(this);
		cancelButton_.addActionListener(this);
		logButton_.addActionListener(this);
		modularityDetectionSnakeCardLayout_.show(modularityDetectionSnakePanel_, "CARD_MODULARITY_DETECTION_DECOY");
		
		logBackButton_.addActionListener(this);
		logExportButton_.addActionListener(this);
		logClearButton_.addActionListener(this);
		logLevelCBox_.addActionListener(this);
		
		datasetModularityCBox_.setSelected(settings.getExportBasicDataset());
		datasetCommunitiesCBox_.setSelected(settings.getExportCommunityNetworks());
		
		String[] formats = Structure.getFormatStrings();
		datasetCommunitiesFormatCBox_.setModel(new DefaultComboBoxModel<String>(formats));
		Structure.Format selectedFormat = settings.getCommunityNetworkFormat();
		for (int i = 0; i < formats.length; i++) {
			if (selectedFormat.name().equals(formats[i])) {
				datasetCommunitiesFormatCBox_.setSelectedIndex(i);
				break;
			}
		}
		
		datasetCommunitiesFormatCBox_.setEnabled(settings.getExportCommunityNetworks());
		datasetColoredCommunitiesCBox_.setSelected(settings.getExportColoredCommunities());
		coloredCommunitiesNetworkFormatCBox_.setEnabled(settings.getExportColoredCommunities());
		coloredCommunitiesNetworkFormatCBox_.setEnabled(settings.getExportColoredCommunities());
		datasetCommunityTreeCBox_.setSelected(settings.getExportCommunityTree());
		datasetSnapshotsCBox_.setSelected(settings.getExportSnapshots());
		
		cancelButton_.setEnabled(false);
		
		outputDirectoryTField_.setText(System.getProperty("user.home").replace("\\", "/") + JmodSettings.FILE_SEPARATOR);
		outputDirectoryBrowse_.addActionListener(this);
		datasetCommunitiesCBox_.addActionListener(this);
		datasetColoredCommunitiesCBox_.addActionListener(this);
		numProcessorsSpinner_.addChangeListener(new ChangeListener() {
			@Override
	        public void stateChanged(ChangeEvent e) {
				JSpinner spinner = (JSpinner) e.getSource();
				if ((Integer)spinner.getModel().getValue() > 1) {
					if (!numProcessorsLabel_.getText().contains("networks"))
						numProcessorsLabel_.setText(numProcessorsLabel_.getText().replaceAll("network", "networks"));
				} else
					numProcessorsLabel_.setText(numProcessorsLabel_.getText().replaceAll("networks", "network"));
	        }
		});
		
		// make the caret of the log area always follow new entrees
		DefaultCaret caret = (DefaultCaret)logPane_.getCaret();
		caret.setUpdatePolicy(DefaultCaret.ALWAYS_UPDATE);
		
		// set the App Tips bar
		initializeAppTips();
		
		// center application on the screen
		Dimension screenDim = Toolkit.getDefaultToolkit().getScreenSize();
		int x0 = (screenDim.width-getSize().width)/2;
		int y0 = (screenDim.height-getSize().height)/2;
		setLocation(new Point(x0, y0));
	}
	
	// ----------------------------------------------------------------------------
	
	/**
	 * Sets App Tips bar.
	 * <br>
	 * <p><b>IMPORTANT:</b> The first message will determine the width of the application
	 * (if longer than the other components). The following messages will automatically
	 * fit the defined width.</p>
	 */
	private void initializeAppTips() {
		
		try {
			msgBar_.loadHtmlMessages(getClass().getResource("rsc/html-messages.txt"));
			Collections.shuffle(msgBar_.getMessages());
	
			msgBar_.setNormalDuration(2 * 60000);
			msgBar_.setExtendedDuration(2 * 60000);
			msgBar_.setHideAfterFirstMessageDuration(20 * 60000); //XXX
			
			msgBar_.setMessage(msgBar_.getMessages().size() - 1);
			msgBar_.start();
		} catch (Exception e) {
			Log.error("JmodGui", "Unable to set App Tips bar.", e);
		}
	}
   	
	// ----------------------------------------------------------------------------
	
	/** Opens a dialog to select a network file. */
	private void selectInputNetwork() throws Exception, JmodException {
		
		try {
	    	JFrame frame = new JFrame();
	     	frame.setAlwaysOnTop(true);
	    	JFileChooser fc = new JFileChooser();
	     	fc.setDialogTitle("Select a network");
	     	
	     	// use the input directory as current directory
	     	// if null, set current directory to USER_HOME
	     	File currentDirectory = null;
	     	String path = inputNetworksTField_.getText();
	     	boolean ok = false;
	     	if (path.compareTo("") != 0) {
	     		try {
	     			String directoryOnly = FilenameUtils.getFullPath(path); // get only the directory
		     		currentDirectory = new File(directoryOnly);
		     		ok = currentDirectory.isDirectory();
	     		} catch (Exception e) {
	     			// do nothing if there was an error, we will use USER_HOME
	     		}
	     	}
	     	if (!ok) {
	     		currentDirectory = new File(System.getProperty("user.home"));
	     		ok = currentDirectory.isDirectory();
	     	}
	     	if (!ok)
	     		throw new JmodException("Unable to set current directory.");
	     	
	     	fc.setCurrentDirectory(currentDirectory);
	     	fc.setFileSelectionMode(JFileChooser.FILES_ONLY);
	     	int returnVal = fc.showDialog(this, "Ok"); // was frame
	     	if (returnVal == JFileChooser.APPROVE_OPTION) {
	     		String regex = fc.getSelectedFile().getPath();
	     		regex = regex.replace("\\", "/");
	     		inputNetworksTField_.setText(regex);
	     		inputNetworksTField_.setCaretPosition(inputNetworksTField_.getText().length());
	     		setNumSelectedNetworks(1); // only one file can be selected at that time
	     	}
		} catch (Exception e) {
			throw e;
		}
	}
	
	// ----------------------------------------------------------------------------
	
	/** Sets the output directory. */
	private void setOutputDirectory() throws Exception, JmodException {
		
		try {
	    	JFrame frame = new JFrame();
	     	frame.setAlwaysOnTop(true);
	    	JFileChooser fc = new JFileChooser();
	     	fc.setDialogTitle("Select output directory");
	     	
	     	// use the output directory as current directory
	     	// if null, set current directory to USER_HOME
	     	File currentDirectory = null;
	     	String path = outputDirectoryTField_.getText();
	     	boolean ok = false;
	     	if (path.compareTo("") != 0) {
	     		try {
	     			String directoryOnly = FilenameUtils.getFullPath(path); // get only the directory
		     		currentDirectory = new File(directoryOnly);
		     		ok = currentDirectory.isDirectory();
	     		} catch (Exception e) {
	     			// do nothing if there was an error, we will use USER_HOME
	     		}
	     	}
	     	if (!ok) {
	     		currentDirectory = new File(System.getProperty("user.home"));
	     		ok = currentDirectory.isDirectory();
	     	}
	     	if (!ok)
	     		throw new JmodException("Unable to set current directory.");
	     	
	     	fc.setCurrentDirectory(currentDirectory);
	     	fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
	     	fc.setApproveButtonText("Select");
	     	// it is required to use chooser.showOpenDialog() for Mac OS X only
	     	// thought chooser.showDialog() is perfectly fine on Win and Linux
	     	int returnVal = fc.showOpenDialog(this); // , "Ok"); // was frame
	     	if (returnVal == JFileChooser.APPROVE_OPTION) {
	     		String directory = fc.getSelectedFile().getPath();
	     		if (!directory.endsWith(File.separator))
	     			directory += "/";
	     		directory = directory.replace("\\", "/");
	     		outputDirectoryTField_.setText(directory);
	     		outputDirectoryTField_.setCaretPosition(outputDirectoryTField_.getText().length());
	     	}
		} catch (Exception e) {
			throw e;
		}
	}

	// ----------------------------------------------------------------------------
	
	/** Constructor. */
	private JmodGui() throws Exception {
		datasetSnapshotsCBox_.setText("Community detection snapshots");
		datasetModularityCBox_.setText("Standard metrics");
		
		initialize();
	}
	
	// ============================================================================
	// PUBLIC METHODS
	
	/** Gets JmodGui instance. */
	static public JmodGui getInstance() {

		try {
			if (instance_ == null)
				instance_ = new JmodGui();
			return instance_;
		} catch (Exception e) {
			Log.error("JmodGui", "Error instantiating JmodGui.", e);
			return null;
		}
	}
	
	// ----------------------------------------------------------------------------
	
	/** Runs the GUI of Jmod. */
	public void run() throws Exception {
	
		setVisible(true);
		
		// initialize the input field only once the GUI is visible
		inputNetworksTField_.setText(System.getProperty("user.home").replace("\\", "/") + JmodSettings.FILE_SEPARATOR + "network_*.tsv");
		inputNetworksTField_.setCaretPosition(inputNetworksTField_.getText().length());
	}
	
	// ----------------------------------------------------------------------------
	
	@Override
	public void actionPerformed(ActionEvent event) {
	
		Object source = event.getSource();
		
		try {
			if (source == inputNetworksBrowse_)
				selectInputNetwork();
			else if (source == runButton_)
				runModularityDetection();
			else if (source == cancelButton_)
				cancelModularityDetection();
			else if (source == logButton_) {
				msgBar_.hideMessageBar();
				contentPaneCardLayout_.show(this.getContentPane(), "CARD_LOG");
			} else if (source == logBackButton_) {
				msgBar_.showMessageBar();
				contentPaneCardLayout_.show(this.getContentPane(), "CARD_MAIN");
			} else if (source == logExportButton_) {
				exportLog();
			}
			else if (source == logClearButton_)
				logPane_.setText("");
			else if (source == logLevelCBox_)
				JmodSettings.setLogLevel((String) logLevelCBox_.getSelectedItem());
			else if (source == outputDirectoryBrowse_)
				setOutputDirectory();
			else if (source == divisionMethodCBox_) {
				CommunityDivider divider = CommunityDividerManager.getInstance().getDivider(divisionMethodCBox_.getSelectedIndex());
				// set the tooltip of the combobox
				divisionMethodCBox_.setToolTipText(divider.getDescription());
				divisionMethodOptionsTEdit_.setToolTipText(divider.getOptionsDescriptionHTML());
				// set the content of the options TextEdit
				divisionMethodOptionsTEdit_.setText(divider.getOptionsStr());
			} else if (source == datasetCommunitiesCBox_)
				datasetCommunitiesFormatCBox_.setEnabled(datasetCommunitiesCBox_.isSelected());
			else if (source == datasetColoredCommunitiesCBox_)
				coloredCommunitiesNetworkFormatCBox_.setEnabled(datasetColoredCommunitiesCBox_.isSelected());
		} catch (OutOfMemoryError e) {
			String str = "There is not enough memory available to run this program.\n" +
						 "Quit one or more programs, and then try again.\n" +
						 "If the problem persits, you may consider increasing\n" +
						 "the amount of physical memory installed on this computer.";
			Log.error("JmodGui", str);
			JOptionPane.showMessageDialog(this, str, "Jmod message", JOptionPane.ERROR_MESSAGE);
		} catch (Exception e) {
			Log.error("JmodGui", e.getMessage(), e);
			String str = e.getMessage() + "\n";
			str += "See log for details.";
			JOptionPane.showMessageDialog(this, str, "Jmod message", JOptionPane.ERROR_MESSAGE);
		}
	}
	
	// ----------------------------------------------------------------------------
	
	/** Displays the number of selected networks. */
	public static void setNumSelectedNetworks(int numNetworks) {
		
		String str = numNetworks + " file";
		if (numNetworks > 1)
			str += "s";
		
		JmodGui gui = JmodGui.getInstance();
		gui.inputNetworksSnake_.stop(); // in case it is running
		gui.inputNetworksLabel_.setText(str);
		gui.inputNetworksCardLayout_.show(gui.inputNetworksLabelPanel_, "CARD_INPUT_NETWORKS_LABEL");
	}
	
	// ----------------------------------------------------------------------------
	
	/** Runs modularity detection. */
	public void runModularityDetection() throws Exception {
		
		JmodSettings settings = JmodSettings.getInstance();
		
		// read interface
		int index = divisionMethodCBox_.getSelectedIndex();
		CommunityDividerManager.getInstance().setSelectedDividerIndex(index);
		CommunityDividerManager.getInstance().getSelectedDivider().parseOptions(divisionMethodOptionsTEdit_.getText()); // apply options
		
		settings.setUseMovingVertex(methodMVMCBox_.isSelected());
		settings.setUseGlobalMovingVertex(methodGMVMCBox_.isSelected());
		settings.setNumConcurrentModuleDetections((Integer) numProcessorsSpinner_.getModel().getValue());
		settings.setExportBasicDataset(datasetModularityCBox_.isSelected());
		settings.setExportCommunityNetworks(datasetCommunitiesCBox_.isSelected());
		
//		settings.setCommunityNetworkFormat(datasetCommunitiesFormatCBox_.getSelectedIndex());
		String selectedFormat = (String) datasetCommunitiesFormatCBox_.getSelectedItem();
		settings.setCommunityNetworkFormat(Structure.getFormat(selectedFormat));
		
		settings.setExportColoredCommunities(datasetColoredCommunitiesCBox_.isSelected());
		settings.setExportCommunityTree(datasetCommunityTreeCBox_.isSelected());
		settings.setExportSnapshots(datasetSnapshotsCBox_.isSelected());
		
		String coloredCommunitiesNetworkFormatStr = (String)coloredCommunitiesNetworkFormatCBox_.getSelectedItem();
		String[] formats = Structure.getFormatStrings();
		for (int i = 0; i < formats.length; i++) {
			if (coloredCommunitiesNetworkFormatStr.compareTo(formats[i]) == 0)
				settings.setColoredCommunitiesNetworkFormat(Structure.getFormat(coloredCommunitiesNetworkFormatStr));
		}
		
		modularityDetectionSnake_.start();
		modularityDetectionSnakeCardLayout_.show(modularityDetectionSnakePanel_, "CARD_MODULARITY_DETECTION_SNAKE");
		progressBar_.setValue(0);
		setEnabled(false);
		
		jmod_ = new Jmod();
		jmod_.setInputNetworksRegex(inputNetworksTField_.getText());
		
//		jmod_.setInputNetworksFormat(inputNetworksFormatCBox_.getSelectedIndex());
		selectedFormat = (String) inputNetworksFormatCBox_.getSelectedItem();
		jmod_.setInputNetworksFormat(Structure.getFormat(selectedFormat));
		
		jmod_.setOutputDirectory(new File(outputDirectoryTField_.getText()).toURI());
		jmod_.execute();
		
		cancelButton_.setEnabled(true);
	}
	
	// ----------------------------------------------------------------------------
	
	/** This method is called to cancel the current modularity detection. */
	public void cancelModularityDetection() throws Exception {
		
		if (jmod_ != null) {
			Log.info("JmodGui", "Canceling modularity detection");
			
			jmod_.cancel();
			jmod_ = null;
			
			modularityDetectionSnake_.stop();
			modularityDetectionSnakeCardLayout_.show(modularityDetectionSnakePanel_, "CARD_MODULARITY_DETECTION_DECOY");
			setEnabled(true);
		}
	}
	
	// ----------------------------------------------------------------------------
	
	/** Udpdates the progress of the modularity detection. */
	public void setModularityDetectionProgress(double percent) {
		
		progressBar_.setValue(progressBar_.getValue() + (int) percent);
	}
	
	// ----------------------------------------------------------------------------
	
	/** This method is called once the modularity detection is done. */
	public void modularityDetectionDone() {
		
		modularityDetectionSnake_.stop();
		modularityDetectionSnakeCardLayout_.show(modularityDetectionSnakePanel_, "CARD_MODULARITY_DETECTION_DECOY");
		setEnabled(true);
	}
	
	// ----------------------------------------------------------------------------
	
	/**
	 * Prints the content of the given StringBuilder to the log window.<br>
	 * This method is called by the custom logger of this project.<br>
	 * <b>Important: </b> Use Log.info(), Log.warn(), etc. instead of calling
	 * this method.
	 */
	public static void printLog(StringBuilder builder) {
		
		printLog(builder.toString());
	}
	
	// ----------------------------------------------------------------------------
	
	/**
	 * Prints the content of the given String to the log window.<br>
	 * This method is called by the custom logger of this project.<br>
	 * <b>Important: </b> Use Log.info(), Log.warn(), etc. instead of calling
	 * this method.
	 */
	public static void printLog(String string) {
		
		JmodGui gui = null;
		try {
			gui = JmodGui.getInstance();
			StyledDocument doc = gui.logPane_.getStyledDocument();
			String newline = "\n";
			if (doc.getLength() == 0)
				newline = "";
			doc.insertString(doc.getLength(), newline + string, null);
		} catch (Exception e) {
			String str = "Error when printing to log.\nSee console for details.";
			Log.error("JmodGui", str, e);
			JOptionPane.showMessageDialog(gui, str, "Jmod message", JOptionPane.ERROR_MESSAGE);
		}
	}
	
	// ----------------------------------------------------------------------------
	
	/** Exports log content to file. */
	public void exportLog() throws Exception, JmodException {
		
		BufferedWriter out = null;
		
		try {
	    	JFrame frame = new JFrame();
	     	frame.setAlwaysOnTop(true);
	    	JFileChooser fc = new JFileChooser();
	     	fc.setDialogTitle("Save log");
	     	
	     	// use the output directory as current directory
	     	// if null, set current directory to USER_HOME
	     	File currentDirectory = null;
	     	String path = outputDirectoryTField_.getText();
	     	boolean ok = false;
	     	if (path.compareTo("") != 0) {
	     		try {
	     			String directoryOnly = FilenameUtils.getFullPath(path); // get only the directory
		     		currentDirectory = new File(directoryOnly);
		     		ok = currentDirectory.isDirectory();
	     		} catch (Exception e) {
	     			// do nothing if there was an error, we will use USER_HOME
	     		}
	     	}
	     	if (!ok) {
	     		currentDirectory = new File(System.getProperty("user.home"));
	     		ok = currentDirectory.isDirectory();
	     	}
	     	if (!ok)
	     		throw new JmodException("Unable to set current directory.");
	     	
	     	fc.setCurrentDirectory(currentDirectory);
	     	fc.setFileSelectionMode(JFileChooser.FILES_ONLY);
	     	int returnVal = fc.showDialog(frame, "Ok");
	     	if (returnVal == JFileChooser.APPROVE_OPTION) {
	     		FileWriter fstream = new FileWriter(fc.getSelectedFile());
	    		out = new BufferedWriter(fstream);
	    		out.write(logPane_.getText());
	    		out.close();
	     	}
		} catch (Exception e) {
			throw e;
		} finally {
			if (out != null)
				out.close();
		}
	}
	
	// ----------------------------------------------------------------------------
	
	/** Enables or disables the gui when running modularity detection. */
	public void setEnabled(boolean b) {
		
		try {
			inputNetworksTField_.setEditable(b); // affect edition only
			inputNetworksBrowse_.setEnabled(b);
			inputNetworksFormatCBox_.setEnabled(b);
			
			divisionMethodCBox_.setEnabled(b);
			divisionMethodOptionsTEdit_.setEditable(b); // affect edition only
			methodMVMCBox_.setEnabled(b);
			methodGMVMCBox_.setEnabled(b);
			numProcessorsSpinner_.setEnabled(b);
			runButton_.setEnabled(b);
			
			datasetModularityCBox_.setEnabled(b);
			datasetCommunitiesCBox_.setEnabled(b);
			datasetCommunitiesFormatCBox_.setEnabled(b && datasetCommunitiesCBox_.isSelected());
			datasetColoredCommunitiesCBox_.setEnabled(b);
			coloredCommunitiesNetworkFormatCBox_.setEnabled(b && datasetColoredCommunitiesCBox_.isSelected());
			datasetCommunityTreeCBox_.setEnabled(b);
			datasetSnapshotsCBox_.setEnabled(b);
			outputDirectoryBrowse_.setEnabled(b);
			
			this.cancelButton_.setEnabled(jmod_ != null && !jmod_.isDone());
			
		} catch (Exception e) {
			Log.error("JmodGui", "Error enabling/disabling graphical components.", e);
		}
	}
	
	// ----------------------------------------------------------------------------
	
	/** Calls this method to properly exit JmodGui. */
	public void exitJmodGui() {
		
		setVisible(false);
		System.exit(0); // calls shutdown hooker
	}
	
	// ----------------------------------------------------------------------------
	
//	/** Main method. */
//	public static void main(String args[]) {
//		
//	    try {
//	    	UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
//	    } catch(Exception e) {
//	    	Log.warn("JmodGui", "Error setting native LAF: " + e.getMessage(), e);
//	    }
//		
//	    try {
//		    JmodGui jmodGui = new JmodGui();
//		    jmodGui.run();
//
//		} catch (Exception e) {
//			Log.error("JmodGui", e);
//		}
//	}
	
	// ============================================================================
	// GETTERS AND SETTERS
	
	public static boolean exists() { return (JmodGui.instance_ != null); }
	
	// ============================================================================
	// INNER CLASSES
	
	private class InputNetworksDocumentListener implements DocumentListener {
	 
	    public void insertUpdate(DocumentEvent event) {
	    	updateNetworkSelection();
	    }
	    public void removeUpdate(DocumentEvent e) {
	    	updateNetworkSelection();
	    }
	    public void changedUpdate(DocumentEvent e) {
	    	updateNetworkSelection();
	    }
	    
	    private void updateNetworkSelection() {
	    	
	    	try {  
	    		String regex = JmodGui.this.inputNetworksTField_.getText();
	    		// cancel the previous update (if any)
	    		if (NetworksSelectionUpdater.instance_ != null)
	    			NetworksSelectionUpdater.instance_.cancel(true);
	    		// run new update
	    		NetworksSelectionUpdater.instance_ = new NetworksSelectionUpdater(regex);
	    		NetworksSelectionUpdater.instance_.execute();
	    	} catch (Exception e) {
	    		Log.error("InputNetworksDocumentListener", "Error updating the networks selection.", e);
	    		JmodGui.setNumSelectedNetworks(0);
	    	}
	    }
	}
}
