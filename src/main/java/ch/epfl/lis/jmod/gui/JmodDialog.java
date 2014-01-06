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

import java.awt.BorderLayout;

import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import java.awt.GridBagLayout;
import javax.swing.JLabel;

import java.awt.GridBagConstraints;
import java.awt.CardLayout;
import javax.swing.border.TitledBorder;
import java.awt.Insets;

import javax.swing.JFrame;
import javax.swing.JTextField;
import javax.swing.JCheckBox;
import javax.swing.JSpinner;
import javax.swing.JComboBox;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JProgressBar;
import javax.swing.ToolTipManager;

import java.awt.GridLayout;
import java.awt.Component;
import javax.swing.Box;
import javax.swing.JScrollPane;
import javax.swing.JTextPane;

import java.awt.Dimension;
import javax.swing.border.EtchedBorder;

import ch.tschaffter.apptips.AppTips;
import ch.tschaffter.gui.Snake;
import java.awt.Color;

/** 
 * Implements the main dialog of the GUI for Jmod.
 * 
 * @version December 2, 2011
 * 
 * @author Thomas Schaffter (firstname.name@gmail.com)
 */
public class JmodDialog extends JFrame {
	
	/** Serial */
	private static final long serialVersionUID = 1L;
	
	protected CardLayout contentPaneCardLayout_ = new CardLayout(0, 0);
	
	// input
	protected JTextField inputNetworksTField_;
	protected JButton inputNetworksBrowse_;
	protected CardLayout inputNetworksCardLayout_ = new CardLayout(0, 0);
	protected JPanel inputNetworksLabelPanel_;
	protected JLabel inputNetworksLabel_;
	protected Snake inputNetworksSnake_;
	public JComboBox<String> inputNetworksFormatCBox_;
	protected JButton aboutButton_;
	
	// modularity detection
	protected JComboBox<String> divisionMethodCBox_;
	protected JTextField divisionMethodOptionsTEdit_;
	protected JCheckBox methodMVMCBox_;
	protected JCheckBox methodGMVMCBox_;
	protected JSpinner numProcessorsSpinner_;
	protected JLabel numProcessorsLabel_;
	protected JProgressBar progressBar_;
	protected JButton runButton_;
	protected JButton cancelButton_;
	protected JButton logButton_;
	protected JPanel modularityDetectionSnakePanel_;
	protected CardLayout modularityDetectionSnakeCardLayout_ = new CardLayout(0, 0);
	protected Snake modularityDetectionSnake_;
	
	// log
	protected JTextPane logPane_;
	protected JButton logBackButton_;
	protected JButton logExportButton_;
	protected JButton logClearButton_;
	protected JComboBox<String> logLevelCBox_;
	
	// output
	protected JCheckBox datasetModularityCBox_;
	protected JCheckBox datasetCommunitiesCBox_;
	protected JCheckBox datasetColoredCommunitiesCBox_;
	protected JCheckBox datasetCommunityTreeCBox_;
	protected JComboBox<String> datasetCommunitiesFormatCBox_;
	protected JTextField outputDirectoryTField_;
	protected JButton outputDirectoryBrowse_;
	
	// apptips
	protected AppTips msgBar_ = null;
	private JLabel lblOptimizers;
	protected JComboBox<String> coloredCommunitiesNetworkFormatCBox_;
	protected JCheckBox datasetSnapshotsCBox_;
	private JLabel lblRunning;

	/**
	 * Launch the application.
	 */
	public static void main(String[] args) {
		try {
			JmodDialog dialog = new JmodDialog();
			dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
			dialog.setVisible(true);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	 * Create the dialog.
	 */
	public JmodDialog() {
		setBounds(100, 100, 594, 611);
		getContentPane().setLayout(contentPaneCardLayout_);
		
		JPanel mainPanel = new JPanel();
		getContentPane().add(mainPanel, "CARD_MAIN");
		GridBagLayout gbl_mainPanel = new GridBagLayout();
		gbl_mainPanel.columnWidths = new int[]{0, 0};
		gbl_mainPanel.rowHeights = new int[]{0, 0, 0, 0, 0};
		gbl_mainPanel.columnWeights = new double[]{1.0, Double.MIN_VALUE};
		gbl_mainPanel.rowWeights = new double[]{0.0, 0.0, 0.0, 0.0, Double.MIN_VALUE};
		mainPanel.setLayout(gbl_mainPanel);
		
		JPanel inputPanel = new JPanel();
		inputPanel.setBorder(new TitledBorder(new EtchedBorder(EtchedBorder.LOWERED, null, null), "Input", TitledBorder.LEADING, TitledBorder.TOP, null, new Color(51, 51, 51)));
		GridBagConstraints gbc_inputPanel = new GridBagConstraints();
		gbc_inputPanel.insets = new Insets(5, 5, 0, 5);
		gbc_inputPanel.fill = GridBagConstraints.BOTH;
		gbc_inputPanel.gridx = 0;
		gbc_inputPanel.gridy = 0;
		mainPanel.add(inputPanel, gbc_inputPanel);
		GridBagLayout gbl_inputPanel = new GridBagLayout();
		gbl_inputPanel.columnWidths = new int[]{0, 0, 0, 0, 0, 0};
		gbl_inputPanel.rowHeights = new int[]{0, 0};
		gbl_inputPanel.columnWeights = new double[]{0.0, 1.0, 0.0, 0.0, 0.0, Double.MIN_VALUE};
		gbl_inputPanel.rowWeights = new double[]{0.0, 0.0};
		inputPanel.setLayout(gbl_inputPanel);
		
		JLabel lblNetworks = new JLabel("Networks:");
		GridBagConstraints gbc_lblNetworks = new GridBagConstraints();
		gbc_lblNetworks.insets = new Insets(0, 5, 5, 5);
		gbc_lblNetworks.anchor = GridBagConstraints.EAST;
		gbc_lblNetworks.gridx = 0;
		gbc_lblNetworks.gridy = 0;
		inputPanel.add(lblNetworks, gbc_lblNetworks);
		
		inputNetworksTField_ = new JTextField();
		GridBagConstraints gbc_inputNetworksTField_ = new GridBagConstraints();
		gbc_inputNetworksTField_.gridwidth = 3;
		gbc_inputNetworksTField_.fill = GridBagConstraints.HORIZONTAL;
		gbc_inputNetworksTField_.insets = new Insets(0, 0, 5, 5);
		gbc_inputNetworksTField_.gridx = 1;
		gbc_inputNetworksTField_.gridy = 0;
		inputPanel.add(inputNetworksTField_, gbc_inputNetworksTField_);
		inputNetworksTField_.setColumns(10);
		
		inputNetworksBrowse_ = new JButton("Browse");
		GridBagConstraints gbc_inputNetworksBrowse_ = new GridBagConstraints();
		gbc_inputNetworksBrowse_.fill = GridBagConstraints.HORIZONTAL;
		gbc_inputNetworksBrowse_.insets = new Insets(0, 0, 5, 5);
		gbc_inputNetworksBrowse_.gridx = 4;
		gbc_inputNetworksBrowse_.gridy = 0;
		inputPanel.add(inputNetworksBrowse_, gbc_inputNetworksBrowse_);
		
		JLabel lblSelection = new JLabel("Selection:");
		GridBagConstraints gbc_lblSelection = new GridBagConstraints();
		gbc_lblSelection.anchor = GridBagConstraints.WEST;
		gbc_lblSelection.insets = new Insets(0, 5, 5, 5);
		gbc_lblSelection.gridx = 0;
		gbc_lblSelection.gridy = 1;
		inputPanel.add(lblSelection, gbc_lblSelection);
		
		inputNetworksLabelPanel_ = new JPanel();
		GridBagConstraints gbc_inputNetworksLabelPanel = new GridBagConstraints();
		gbc_inputNetworksLabelPanel.anchor = GridBagConstraints.WEST;
		gbc_inputNetworksLabelPanel.insets = new Insets(0, 0, 5, 5);
		gbc_inputNetworksLabelPanel.gridx = 1;
		gbc_inputNetworksLabelPanel.gridy = 1;
		inputPanel.add(inputNetworksLabelPanel_, gbc_inputNetworksLabelPanel);
		inputNetworksLabelPanel_.setLayout(inputNetworksCardLayout_);
		
		inputNetworksLabel_ = new JLabel("0 file");
		inputNetworksLabelPanel_.add(inputNetworksLabel_, "CARD_INPUT_NETWORKS_LABEL");
		
		inputNetworksSnake_ = new Snake();
//		int x = inputNetworksLabelPanel_.getPreferredSize().width;
//		inputNetworksSnake_.setSnakeCenterX(x-14);
		inputNetworksSnake_.setSnakeCenterX(0);
		inputNetworksSnake_.setSnakeCenterY(0);
		inputNetworksSnake_.setNumPathBullets(8);
		inputNetworksSnake_.setNumSnakeBullets(5);
		inputNetworksSnake_.setR(5.5f);
		inputNetworksSnake_.setr(1.5f);
		inputNetworksLabelPanel_.add(inputNetworksSnake_, "CARD_INPUT_NETWORKS_SNAKE");
		inputNetworksSnake_.setLayout(null);
		
		JLabel lblFormat = new JLabel("Format:");
		GridBagConstraints gbc_lblFormat = new GridBagConstraints();
		gbc_lblFormat.anchor = GridBagConstraints.WEST;
		gbc_lblFormat.insets = new Insets(0, 5, 5, 5);
		gbc_lblFormat.gridx = 2;
		gbc_lblFormat.gridy = 1;
		inputPanel.add(lblFormat, gbc_lblFormat);
		
		inputNetworksFormatCBox_ = new JComboBox<String>();
		GridBagConstraints gbc_inputNetworksFormatCBox = new GridBagConstraints();
		gbc_inputNetworksFormatCBox.anchor = GridBagConstraints.EAST;
		gbc_inputNetworksFormatCBox.insets = new Insets(0, 0, 5, 5);
		gbc_inputNetworksFormatCBox.gridx = 3;
		gbc_inputNetworksFormatCBox.gridy = 1;
		inputPanel.add(inputNetworksFormatCBox_, gbc_inputNetworksFormatCBox);
		
		aboutButton_ = new JButton("About");
		GridBagConstraints gbc_aboutButton_ = new GridBagConstraints();
		gbc_aboutButton_.insets = new Insets(0, 0, 5, 5);
		gbc_aboutButton_.fill = GridBagConstraints.HORIZONTAL;
		gbc_aboutButton_.gridx = 4;
		gbc_aboutButton_.gridy = 1;
		inputPanel.add(aboutButton_, gbc_aboutButton_);
		aboutButton_.setVisible(false);
		
		JPanel modularityDetectionPanel = new JPanel();
		modularityDetectionPanel.setBorder(new TitledBorder(new EtchedBorder(EtchedBorder.LOWERED, null, null), "Module Detection", TitledBorder.LEADING, TitledBorder.TOP, null, new Color(51, 51, 51)));
		GridBagConstraints gbc_modularityDetectionPanel = new GridBagConstraints();
		gbc_modularityDetectionPanel.insets = new Insets(0, 5, 0, 5);
		gbc_modularityDetectionPanel.fill = GridBagConstraints.BOTH;
		gbc_modularityDetectionPanel.gridx = 0;
		gbc_modularityDetectionPanel.gridy = 1;
		mainPanel.add(modularityDetectionPanel, gbc_modularityDetectionPanel);
		GridBagLayout gbl_modularityDetectionPanel = new GridBagLayout();
		gbl_modularityDetectionPanel.columnWidths = new int[]{0, 0, 0, 0, 0, 0, 0, 0};
		gbl_modularityDetectionPanel.rowHeights = new int[]{0, 0, 0, 0, 0, 0, 0, 0, 0, 0};
		gbl_modularityDetectionPanel.columnWeights = new double[]{0.0, 1.0, 0.0, 0.0, 0.0, 1.0, 0.0, Double.MIN_VALUE};
		gbl_modularityDetectionPanel.rowWeights = new double[]{0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 1.0};
		modularityDetectionPanel.setLayout(gbl_modularityDetectionPanel);
		
		JLabel lblMethod = new JLabel("Method:");
		GridBagConstraints gbc_lblMethod = new GridBagConstraints();
		gbc_lblMethod.anchor = GridBagConstraints.WEST;
		gbc_lblMethod.insets = new Insets(5, 5, 5, 5);
		gbc_lblMethod.gridx = 0;
		gbc_lblMethod.gridy = 0;
		modularityDetectionPanel.add(lblMethod, gbc_lblMethod);
		
		divisionMethodCBox_ = new JComboBox<String>();
		GridBagConstraints gbc_divisionMethodCBox_ = new GridBagConstraints();
		gbc_divisionMethodCBox_.fill = GridBagConstraints.HORIZONTAL;
		gbc_divisionMethodCBox_.gridwidth = 7;
		gbc_divisionMethodCBox_.insets = new Insets(5, 0, 5, 5);
		gbc_divisionMethodCBox_.gridx = 1;
		gbc_divisionMethodCBox_.gridy = 0;
		modularityDetectionPanel.add(divisionMethodCBox_, gbc_divisionMethodCBox_);
		
		JPanel panel = new JPanel();
		GridBagConstraints gbc_panel = new GridBagConstraints();
		gbc_panel.insets = new Insets(0, 0, 5, 0);
		gbc_panel.gridwidth = 7;
		gbc_panel.fill = GridBagConstraints.BOTH;
		gbc_panel.gridx = 1;
		gbc_panel.gridy = 1;
		modularityDetectionPanel.add(panel, gbc_panel);
		GridBagLayout gbl_panel = new GridBagLayout();
		gbl_panel.columnWidths = new int[]{0, 0, 0, 0};
		gbl_panel.rowHeights = new int[]{25, 0};
		gbl_panel.columnWeights = new double[]{0.0, 0.0, 1.0, Double.MIN_VALUE};
		gbl_panel.rowWeights = new double[]{0.0, Double.MIN_VALUE};
		panel.setLayout(gbl_panel);
		
		JLabel lblOptions = new JLabel("Options:");
		GridBagConstraints gbc_lblOptions = new GridBagConstraints();
		gbc_lblOptions.insets = new Insets(0, 0, 0, 5);
		gbc_lblOptions.anchor = GridBagConstraints.WEST;
		gbc_lblOptions.gridx = 0;
		gbc_lblOptions.gridy = 0;
		panel.add(lblOptions, gbc_lblOptions);
		
		divisionMethodOptionsTEdit_ = new JTextField();
		GridBagConstraints gbc_divisionMethodOptionsTEdit_ = new GridBagConstraints();
		gbc_divisionMethodOptionsTEdit_.insets = new Insets(0, 0, 0, 5);
		gbc_divisionMethodOptionsTEdit_.gridwidth = 2;
		gbc_divisionMethodOptionsTEdit_.fill = GridBagConstraints.HORIZONTAL;
		gbc_divisionMethodOptionsTEdit_.gridx = 1;
		gbc_divisionMethodOptionsTEdit_.gridy = 0;
		panel.add(divisionMethodOptionsTEdit_, gbc_divisionMethodOptionsTEdit_);
		divisionMethodOptionsTEdit_.setColumns(5);
		
		lblOptimizers = new JLabel("Refinement:");
		GridBagConstraints gbc_lblOptimizers = new GridBagConstraints();
		gbc_lblOptimizers.anchor = GridBagConstraints.WEST;
		gbc_lblOptimizers.insets = new Insets(0, 5, 5, 5);
		gbc_lblOptimizers.gridx = 0;
		gbc_lblOptimizers.gridy = 2;
		modularityDetectionPanel.add(lblOptimizers, gbc_lblOptimizers);
		
		methodMVMCBox_ = new JCheckBox("Moving vertex method (MVM)");
		GridBagConstraints gbc_methodMVMCBox = new GridBagConstraints();
		gbc_methodMVMCBox.gridwidth = 5;
		gbc_methodMVMCBox.anchor = GridBagConstraints.WEST;
		gbc_methodMVMCBox.insets = new Insets(0, 0, 5, 5);
		gbc_methodMVMCBox.gridx = 1;
		gbc_methodMVMCBox.gridy = 2;
		modularityDetectionPanel.add(methodMVMCBox_, gbc_methodMVMCBox);
		
		methodGMVMCBox_ = new JCheckBox("Global MVM (gMVM)");
		GridBagConstraints gbc_methodGMVMCBox = new GridBagConstraints();
		gbc_methodGMVMCBox.gridwidth = 5;
		gbc_methodGMVMCBox.anchor = GridBagConstraints.WEST;
		gbc_methodGMVMCBox.insets = new Insets(0, 0, 5, 5);
		gbc_methodGMVMCBox.gridx = 1;
		gbc_methodGMVMCBox.gridy = 3;
		modularityDetectionPanel.add(methodGMVMCBox_, gbc_methodGMVMCBox);
		
		Component verticalStrut_1 = Box.createVerticalStrut(5);
		GridBagConstraints gbc_verticalStrut_1 = new GridBagConstraints();
		gbc_verticalStrut_1.insets = new Insets(0, 0, 5, 5);
		gbc_verticalStrut_1.gridx = 0;
		gbc_verticalStrut_1.gridy = 4;
		modularityDetectionPanel.add(verticalStrut_1, gbc_verticalStrut_1);
		
		JPanel panel_1 = new JPanel();
		GridBagConstraints gbc_panel_1 = new GridBagConstraints();
		gbc_panel_1.gridwidth = 2;
		gbc_panel_1.insets = new Insets(0, 0, 5, 5);
		gbc_panel_1.fill = GridBagConstraints.BOTH;
		gbc_panel_1.gridx = 0;
		gbc_panel_1.gridy = 5;
		modularityDetectionPanel.add(panel_1, gbc_panel_1);
		GridBagLayout gbl_panel_1 = new GridBagLayout();
		gbl_panel_1.columnWidths = new int[]{0, 0, 0, 0};
		gbl_panel_1.rowHeights = new int[]{0, 0};
		gbl_panel_1.columnWeights = new double[]{0.0, 0.0, 0.0, Double.MIN_VALUE};
		gbl_panel_1.rowWeights = new double[]{0.0, Double.MIN_VALUE};
		panel_1.setLayout(gbl_panel_1);
		
		lblRunning = new JLabel("Processing ");
		GridBagConstraints gbc_lblRunning = new GridBagConstraints();
		gbc_lblRunning.anchor = GridBagConstraints.WEST;
		gbc_lblRunning.insets = new Insets(0, 5, 0, 5);
		gbc_lblRunning.gridx = 0;
		gbc_lblRunning.gridy = 0;
		panel_1.add(lblRunning, gbc_lblRunning);
		
		numProcessorsSpinner_ = new JSpinner();
		numProcessorsSpinner_.setToolTipText("<html>Number of networks processed simultaneously. This parameter<br>\nis useful for methods which are not parallelized such as Newman's spectral<br>\nalgorithm.</html>");
		GridBagConstraints gbc_numProcessorsSpinner_ = new GridBagConstraints();
		gbc_numProcessorsSpinner_.insets = new Insets(0, 0, 0, 5);
		gbc_numProcessorsSpinner_.gridx = 1;
		gbc_numProcessorsSpinner_.gridy = 0;
		panel_1.add(numProcessorsSpinner_, gbc_numProcessorsSpinner_);
		
		numProcessorsLabel_ = new JLabel(" network simultaneously (max: N)");
		numProcessorsLabel_.setToolTipText("<html>Number of networks processed simultaneously. This parameter<br>\nis useful for methods which are not parallelized such as Newman's spectral<br>\nalgorithm.</html>");
		GridBagConstraints gbc_numProcessorsLabel_ = new GridBagConstraints();
		gbc_numProcessorsLabel_.gridx = 2;
		gbc_numProcessorsLabel_.gridy = 0;
		panel_1.add(numProcessorsLabel_, gbc_numProcessorsLabel_);
		
		Component verticalStrut = Box.createVerticalStrut(5);
		GridBagConstraints gbc_verticalStrut = new GridBagConstraints();
		gbc_verticalStrut.insets = new Insets(0, 0, 5, 5);
		gbc_verticalStrut.gridx = 0;
		gbc_verticalStrut.gridy = 6;
		modularityDetectionPanel.add(verticalStrut, gbc_verticalStrut);
		
		JLabel lblProgress = new JLabel("Progress:");
		GridBagConstraints gbc_lblProgress = new GridBagConstraints();
		gbc_lblProgress.anchor = GridBagConstraints.WEST;
		gbc_lblProgress.insets = new Insets(0, 5, 5, 5);
		gbc_lblProgress.gridx = 0;
		gbc_lblProgress.gridy = 7;
		modularityDetectionPanel.add(lblProgress, gbc_lblProgress);
		
		progressBar_ = new JProgressBar();
		GridBagConstraints gbc_progressBar = new GridBagConstraints();
		gbc_progressBar.fill = GridBagConstraints.HORIZONTAL;
		gbc_progressBar.gridwidth = 5;
		gbc_progressBar.insets = new Insets(0, 0, 5, 5);
		gbc_progressBar.gridx = 1;
		gbc_progressBar.gridy = 7;
		modularityDetectionPanel.add(progressBar_, gbc_progressBar);
		
		JPanel panel_4 = new JPanel();
		GridBagConstraints gbc_panel_4 = new GridBagConstraints();
		gbc_panel_4.gridwidth = 4;
		gbc_panel_4.insets = new Insets(0, 0, 5, 5);
		gbc_panel_4.fill = GridBagConstraints.BOTH;
		gbc_panel_4.gridx = 1;
		gbc_panel_4.gridy = 8;
		modularityDetectionPanel.add(panel_4, gbc_panel_4);
		panel_4.setLayout(new GridLayout(0, 3, 5, 0));
		
		runButton_ = new JButton("Run");
		panel_4.add(runButton_);
		
		cancelButton_ = new JButton("Cancel");
		panel_4.add(cancelButton_);
		
		logButton_ = new JButton("Log >");
		panel_4.add(logButton_);
		
		modularityDetectionSnakePanel_ = new JPanel();
		GridBagConstraints gbc_modularityDetectionSnakePanel = new GridBagConstraints();
		gbc_modularityDetectionSnakePanel.anchor = GridBagConstraints.EAST;
		gbc_modularityDetectionSnakePanel.gridheight = 3;
		gbc_modularityDetectionSnakePanel.gridwidth = 2;
		gbc_modularityDetectionSnakePanel.fill = GridBagConstraints.VERTICAL;
		gbc_modularityDetectionSnakePanel.gridx = 6;
		gbc_modularityDetectionSnakePanel.gridy = 7;
		modularityDetectionPanel.add(modularityDetectionSnakePanel_, gbc_modularityDetectionSnakePanel);
		modularityDetectionSnakePanel_.setLayout(modularityDetectionSnakeCardLayout_);
		
		modularityDetectionSnake_ = new Snake();
		modularityDetectionSnake_.setSnakeCenterX(null);
		modularityDetectionSnake_.setSnakeCenterY(null);
		
		modularityDetectionSnakePanel_.add(modularityDetectionSnake_, "CARD_MODULARITY_DETECTION_SNAKE");
		modularityDetectionSnake_.setLayout(null);
		
		Component rigidArea = Box.createRigidArea(new Dimension(50, 20));
		modularityDetectionSnakePanel_.add(rigidArea, "CARD_MODULARITY_DETECTION_DECOY");
		
		JPanel outputPanel = new JPanel();
		outputPanel.setBorder(new TitledBorder(new EtchedBorder(EtchedBorder.LOWERED, null, null), "Output", TitledBorder.LEADING, TitledBorder.TOP, null, new Color(51, 51, 51)));
		GridBagConstraints gbc_outputPanel = new GridBagConstraints();
		gbc_outputPanel.insets = new Insets(0, 5, 0, 5);
		gbc_outputPanel.fill = GridBagConstraints.BOTH;
		gbc_outputPanel.gridx = 0;
		gbc_outputPanel.gridy = 2;
		mainPanel.add(outputPanel, gbc_outputPanel);
		GridBagLayout gbl_outputPanel = new GridBagLayout();
		gbl_outputPanel.columnWidths = new int[]{0, 0, 0, 0, 0, 0, 0};
		gbl_outputPanel.rowHeights = new int[]{0, 0, 0, 0, 0, 0, 0, 0};
		gbl_outputPanel.columnWeights = new double[]{0.0, 0.0, 0.0, 1.0, 0.0, 0.0, Double.MIN_VALUE};
		gbl_outputPanel.rowWeights = new double[]{0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, Double.MIN_VALUE};
		outputPanel.setLayout(gbl_outputPanel);
		
		JLabel lblDataset = new JLabel("Datasets:");
		GridBagConstraints gbc_lblDataset = new GridBagConstraints();
		gbc_lblDataset.anchor = GridBagConstraints.WEST;
		gbc_lblDataset.insets = new Insets(0, 5, 5, 5);
		gbc_lblDataset.gridx = 0;
		gbc_lblDataset.gridy = 0;
		outputPanel.add(lblDataset, gbc_lblDataset);
		
		datasetModularityCBox_ = new JCheckBox("Metrics");
		datasetModularityCBox_.setToolTipText("<html>Exports a minimal dataset including the modularity of the network,<br>\n the number of communities identified and the computational time in ms.</html>");
		GridBagConstraints gbc_datasetModularityCBox_ = new GridBagConstraints();
		gbc_datasetModularityCBox_.gridwidth = 5;
		gbc_datasetModularityCBox_.anchor = GridBagConstraints.WEST;
		gbc_datasetModularityCBox_.insets = new Insets(0, 0, 5, 0);
		gbc_datasetModularityCBox_.gridx = 1;
		gbc_datasetModularityCBox_.gridy = 0;
		outputPanel.add(datasetModularityCBox_, gbc_datasetModularityCBox_);
		
		datasetCommunitiesCBox_ = new JCheckBox("Individual communities");
		datasetCommunitiesCBox_.setToolTipText("Exports the communities identified as individual graphs.");
		GridBagConstraints gbc_datasetCommunitiesCBox_ = new GridBagConstraints();
		gbc_datasetCommunitiesCBox_.gridwidth = 3;
		gbc_datasetCommunitiesCBox_.insets = new Insets(0, 0, 5, 5);
		gbc_datasetCommunitiesCBox_.anchor = GridBagConstraints.WEST;
		gbc_datasetCommunitiesCBox_.gridx = 1;
		gbc_datasetCommunitiesCBox_.gridy = 1;
		outputPanel.add(datasetCommunitiesCBox_, gbc_datasetCommunitiesCBox_);
		
		datasetCommunitiesFormatCBox_ = new JComboBox<String>();
		GridBagConstraints gbc_datasetCommunitiesFormatCBox_ = new GridBagConstraints();
		gbc_datasetCommunitiesFormatCBox_.fill = GridBagConstraints.HORIZONTAL;
		gbc_datasetCommunitiesFormatCBox_.insets = new Insets(0, 0, 5, 5);
		gbc_datasetCommunitiesFormatCBox_.gridx = 4;
		gbc_datasetCommunitiesFormatCBox_.gridy = 1;
		outputPanel.add(datasetCommunitiesFormatCBox_, gbc_datasetCommunitiesFormatCBox_);
		
		datasetColoredCommunitiesCBox_ = new JCheckBox("Colored communities");
		datasetColoredCommunitiesCBox_.setToolTipText("<html>Exports the network to file where its nodes are painted based on the<br>\ncommunity they belong to.</html>");
		GridBagConstraints gbc_datasetColoredCommunitiesCBox = new GridBagConstraints();
		gbc_datasetColoredCommunitiesCBox.insets = new Insets(0, 0, 5, 5);
		gbc_datasetColoredCommunitiesCBox.anchor = GridBagConstraints.WEST;
		gbc_datasetColoredCommunitiesCBox.gridx = 1;
		gbc_datasetColoredCommunitiesCBox.gridy = 2;
		outputPanel.add(datasetColoredCommunitiesCBox_, gbc_datasetColoredCommunitiesCBox);
		
		Component horizontalStrut_1 = Box.createHorizontalStrut(20);
		GridBagConstraints gbc_horizontalStrut_1 = new GridBagConstraints();
		gbc_horizontalStrut_1.insets = new Insets(0, 0, 5, 5);
		gbc_horizontalStrut_1.gridx = 3;
		gbc_horizontalStrut_1.gridy = 2;
		outputPanel.add(horizontalStrut_1, gbc_horizontalStrut_1);
		
		coloredCommunitiesNetworkFormatCBox_ = new JComboBox<String>();
		coloredCommunitiesNetworkFormatCBox_.setModel(new DefaultComboBoxModel<String>(new String[] {"GML", "DOT"}));
		coloredCommunitiesNetworkFormatCBox_.setSelectedIndex(0);
		GridBagConstraints gbc_comboBox = new GridBagConstraints();
		gbc_comboBox.fill = GridBagConstraints.HORIZONTAL;
		gbc_comboBox.insets = new Insets(0, 0, 5, 5);
		gbc_comboBox.gridx = 4;
		gbc_comboBox.gridy = 2;
		outputPanel.add(coloredCommunitiesNetworkFormatCBox_, gbc_comboBox);
		
		datasetCommunityTreeCBox_ = new JCheckBox("Community tree");
		datasetCommunityTreeCBox_.setToolTipText("Exports the dendrogram of the community split process (Matlab format).");
		GridBagConstraints gbc_datasetCommunityTreeCBox_ = new GridBagConstraints();
		gbc_datasetCommunityTreeCBox_.gridwidth = 4;
		gbc_datasetCommunityTreeCBox_.insets = new Insets(0, 0, 5, 5);
		gbc_datasetCommunityTreeCBox_.anchor = GridBagConstraints.WEST;
		gbc_datasetCommunityTreeCBox_.gridx = 1;
		gbc_datasetCommunityTreeCBox_.gridy = 3;
		outputPanel.add(datasetCommunityTreeCBox_, gbc_datasetCommunityTreeCBox_);
		
		datasetSnapshotsCBox_ = new JCheckBox("Module detection snapshots");
		datasetSnapshotsCBox_.setToolTipText("<html>Exports differential snapshots of the current state of the module<br>\ndetection. The snapshot files contains the name of the nodes and their current<br>\nstates, which reflect the index of the community they belong too. Snapshot<br>\nfiles can then be converted to graph files to illustrate the steps of the selected<br>\nmodule detection method.</html>");
		GridBagConstraints gbc_datasetSnapshotsCBox_ = new GridBagConstraints();
		gbc_datasetSnapshotsCBox_.anchor = GridBagConstraints.WEST;
		gbc_datasetSnapshotsCBox_.insets = new Insets(0, 0, 5, 5);
		gbc_datasetSnapshotsCBox_.gridx = 1;
		gbc_datasetSnapshotsCBox_.gridy = 4;
		outputPanel.add(datasetSnapshotsCBox_, gbc_datasetSnapshotsCBox_);
		
		Component verticalStrut_2 = Box.createVerticalStrut(5);
		GridBagConstraints gbc_verticalStrut_2 = new GridBagConstraints();
		gbc_verticalStrut_2.insets = new Insets(0, 0, 5, 5);
		gbc_verticalStrut_2.gridx = 0;
		gbc_verticalStrut_2.gridy = 5;
		outputPanel.add(verticalStrut_2, gbc_verticalStrut_2);
		
		JLabel lblDirectory = new JLabel("Directory:");
		GridBagConstraints gbc_lblDirectory = new GridBagConstraints();
		gbc_lblDirectory.anchor = GridBagConstraints.EAST;
		gbc_lblDirectory.insets = new Insets(0, 5, 0, 5);
		gbc_lblDirectory.gridx = 0;
		gbc_lblDirectory.gridy = 6;
		outputPanel.add(lblDirectory, gbc_lblDirectory);
		
		outputDirectoryTField_ = new JTextField();
		outputDirectoryTField_.setEditable(false);
		GridBagConstraints gbc_outputDirectoryTField_ = new GridBagConstraints();
		gbc_outputDirectoryTField_.gridwidth = 4;
		gbc_outputDirectoryTField_.insets = new Insets(0, 0, 0, 5);
		gbc_outputDirectoryTField_.fill = GridBagConstraints.HORIZONTAL;
		gbc_outputDirectoryTField_.gridx = 1;
		gbc_outputDirectoryTField_.gridy = 6;
		outputPanel.add(outputDirectoryTField_, gbc_outputDirectoryTField_);
		outputDirectoryTField_.setColumns(10);
		
		outputDirectoryBrowse_ = new JButton("Browse");
		GridBagConstraints gbc_outputDirectoryBrowse = new GridBagConstraints();
		gbc_outputDirectoryBrowse.insets = new Insets(0, 0, 5, 5);
		gbc_outputDirectoryBrowse.gridx = 5;
		gbc_outputDirectoryBrowse.gridy = 6;
		outputPanel.add(outputDirectoryBrowse_, gbc_outputDirectoryBrowse);
		
		JPanel apptipsPanel = new JPanel();
		GridBagConstraints gbc_apptipsPanel = new GridBagConstraints();
		gbc_apptipsPanel.fill = GridBagConstraints.BOTH;
		gbc_apptipsPanel.gridx = 0;
		gbc_apptipsPanel.gridy = 3;
		mainPanel.add(apptipsPanel, gbc_apptipsPanel);
		GridBagLayout gbl_apptipsPanel = new GridBagLayout();
		gbl_apptipsPanel.columnWidths = new int[]{0, 0};
		gbl_apptipsPanel.rowHeights = new int[]{0, 0};
		gbl_apptipsPanel.columnWeights = new double[]{0.0, Double.MIN_VALUE};
		gbl_apptipsPanel.rowWeights = new double[]{0.0, Double.MIN_VALUE};
		apptipsPanel.setLayout(gbl_apptipsPanel);
		
		Component verticalStrut_3 = Box.createVerticalStrut(50);
		GridBagConstraints gbc_verticalStrut_3 = new GridBagConstraints();
		gbc_verticalStrut_3.gridx = 0;
		gbc_verticalStrut_3.gridy = 0;
		apptipsPanel.add(verticalStrut_3, gbc_verticalStrut_3);
		
		JPanel logPanel = new JPanel();
		logPanel.setBorder(new EmptyBorder(5, 5, 5, 5));
		getContentPane().add(logPanel, "CARD_LOG");
		logPanel.setLayout(new BorderLayout(0, 0));
		
		JScrollPane scrollPane = new JScrollPane();
		logPanel.add(scrollPane, BorderLayout.CENTER);
		
		logPane_ = new JTextPane();
		scrollPane.setViewportView(logPane_);
		
		JPanel panel_7 = new JPanel();
		logPanel.add(panel_7, BorderLayout.SOUTH);
		GridBagLayout gbl_panel_7 = new GridBagLayout();
		gbl_panel_7.columnWidths = new int[]{0, 0, 0, 0, 0, 0};
		gbl_panel_7.rowHeights = new int[]{0, 0};
		gbl_panel_7.columnWeights = new double[]{0.0, 0.0, 0.0, 1.0, 0.0, Double.MIN_VALUE};
		gbl_panel_7.rowWeights = new double[]{0.0, Double.MIN_VALUE};
		panel_7.setLayout(gbl_panel_7);
		
		logBackButton_ = new JButton("< Back");
		GridBagConstraints gbc_logBackButton = new GridBagConstraints();
		gbc_logBackButton.insets = new Insets(5, 0, 0, 5);
		gbc_logBackButton.gridx = 0;
		gbc_logBackButton.gridy = 0;
		panel_7.add(logBackButton_, gbc_logBackButton);
		
		logExportButton_ = new JButton("Export");
		GridBagConstraints gbc_logExportButton = new GridBagConstraints();
		gbc_logExportButton.insets = new Insets(5, 0, 0, 5);
		gbc_logExportButton.gridx = 1;
		gbc_logExportButton.gridy = 0;
		panel_7.add(logExportButton_, gbc_logExportButton);
		
		logClearButton_ = new JButton("Clear");
		GridBagConstraints gbc_logClearButton_ = new GridBagConstraints();
		gbc_logClearButton_.insets = new Insets(5, 0, 0, 5);
		gbc_logClearButton_.gridx = 2;
		gbc_logClearButton_.gridy = 0;
		panel_7.add(logClearButton_, gbc_logClearButton_);
		
		Component horizontalStrut = Box.createHorizontalStrut(20);
		GridBagConstraints gbc_horizontalStrut = new GridBagConstraints();
		gbc_horizontalStrut.insets = new Insets(0, 0, 0, 5);
		gbc_horizontalStrut.gridx = 3;
		gbc_horizontalStrut.gridy = 0;
		panel_7.add(horizontalStrut, gbc_horizontalStrut);
		
		JPanel panel_2 = new JPanel();
		GridBagConstraints gbc_panel_2 = new GridBagConstraints();
		gbc_panel_2.anchor = GridBagConstraints.SOUTHEAST;
		gbc_panel_2.gridx = 4;
		gbc_panel_2.gridy = 0;
		panel_7.add(panel_2, gbc_panel_2);
		GridBagLayout gbl_panel_2 = new GridBagLayout();
		gbl_panel_2.columnWidths = new int[]{41, 39, 0};
		gbl_panel_2.rowHeights = new int[]{15, 0};
		gbl_panel_2.columnWeights = new double[]{0.0, 0.0, Double.MIN_VALUE};
		gbl_panel_2.rowWeights = new double[]{0.0, Double.MIN_VALUE};
		panel_2.setLayout(gbl_panel_2);
		
		JLabel lblLevel = new JLabel("Level :");
		GridBagConstraints gbc_lblLevel = new GridBagConstraints();
		gbc_lblLevel.insets = new Insets(5, 0, 0, 5);
		gbc_lblLevel.anchor = GridBagConstraints.NORTHEAST;
		gbc_lblLevel.gridx = 0;
		gbc_lblLevel.gridy = 0;
		panel_2.add(lblLevel, gbc_lblLevel);
		
		logLevelCBox_ = new JComboBox<String>();
		logLevelCBox_.setModel(new DefaultComboBoxModel<String>(new String[] {"ERROR", "WARN", "INFO", "DEBUG", "TRACE"}));
		logLevelCBox_.setSelectedIndex(2);
		GridBagConstraints gbc_logLevelCBox_ = new GridBagConstraints();
		gbc_logLevelCBox_.anchor = GridBagConstraints.EAST;
		gbc_logLevelCBox_.gridx = 1;
		gbc_logLevelCBox_.gridy = 0;
		panel_2.add(logLevelCBox_, gbc_logLevelCBox_);
		
		// message bar
		msgBar_ = new AppTips();
		msgBar_.setToolTipText("");
		msgBar_.setLeftPreferredSize(new Dimension(18, 0));
		msgBar_.setRightPreferredSize(new Dimension(50, 0));
		GridBagConstraints gbc_msgBar_ = new GridBagConstraints();
		gbc_msgBar_.insets = new Insets(0, 5, 0, 5);
		gbc_msgBar_.fill = GridBagConstraints.BOTH;
		gbc_msgBar_.gridx = 1;
		gbc_msgBar_.gridy = 0;
		apptipsPanel.add(msgBar_, gbc_msgBar_);
		
		pack();
		
		int w = getPreferredSize().width;
		int h = getPreferredSize().height;
		this.setMinimumSize(new Dimension(w, h));
		
		ToolTipManager.sharedInstance().setDismissDelay(10000);
	}
}
