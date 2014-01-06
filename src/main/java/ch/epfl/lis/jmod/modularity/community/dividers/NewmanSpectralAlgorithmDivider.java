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

import no.uib.cipr.matrix.DenseVector;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.PosixParser;
import org.apache.commons.cli.UnrecognizedOptionException;

import ch.epfl.lis.jmod.JmodException;
import ch.epfl.lis.jmod.modularity.ModularityDetector;
import ch.epfl.lis.jmod.modularity.utils.EigenMethods;

import com.esotericsoftware.minlog.Log;

/**
 * Implements Newman's spectral algorithm to divide modules or communities in two (Newman,2006).
 * 
 * @version January 24, 2012
 * 
 * @author Thomas Schaffter (firstname.name@gmail.com)
 */
public class NewmanSpectralAlgorithmDivider extends CommunityDivider {
	
	/** Method to use for computing the most positive leading eigenvalue and corresponding eigenvector. */
	protected int eigenvalueMethod_ = EigenMethods.POWER_METHOD;

    // =======================================================================================
    // PROTECTED METHODS
	
	@Override
	protected void buildOptions() {
		
		// create Options object
		options_ = new Options();
		
//		// SHORT AND LONG OPTIONS FLAGS MUST BE DIFFERENT FROM THOSE OF JMOD
//		options_.addOption(OptionBuilder.withValueSeparator()
//				.withLongOpt("eigenvalueMethod")
//				.withDescription("Set the method to use for computing the dominant eigenpair of <b>B</b> (COLT, POWER, default: POWER).")
//				.hasArgs()
//				.withArgName("METHOD")
//				.create("e"));
	}
	
	// ----------------------------------------------------------------------------
	
	@Override
	protected void parseOptions(String args[]) throws JmodException, Exception {
		
		// parse options
		CommandLineParser parser = new PosixParser();
		@SuppressWarnings("unused")
		CommandLine cmd = null;
		try {
			cmd = parser.parse(options_, args);
//			if (cmd.hasOption("eigenvalueMethod"))
//				setEigenvalueMethod(cmd.getOptionValue("eigenvalueMethod"));
		
		} catch (UnrecognizedOptionException e) {
			Log.error(identifier_, e.getMessage());
			printHelp();
			throw new JmodException(e.getMessage());
		} catch (Exception e) {
			Log.error(identifier_, "Could not successfully recognized the options.", e);
			printHelp();
			throw new JmodException(e.getMessage());
		}
	}
	
    // =======================================================================================
    // PUBLIC METHODS
	
	/** Constructor. */
	public NewmanSpectralAlgorithmDivider() {
		
		super();
		
		// builds the options of the methods
		buildOptions();

		identifier_ = "Newman";
		name_ = "Newman's spectral algorithm";
		description_ = "<html>Newman's spectral algorithm recursively splits communities in two<br>" +
						     "subcommunities. For each split, the dominant eigenpair of the modularity<br>" +
						     "matrix <b>B</b> is computed before assigning each node to one subcommunity<br>" +
						     "depending on the sign of the corresponding eigenvector element.</html>";
	}
	
	// ----------------------------------------------------------------------------
	
	public NewmanSpectralAlgorithmDivider(NewmanSpectralAlgorithmDivider divider) {
		
		super(divider);
		
		eigenvalueMethod_ = divider.eigenvalueMethod_;
	}
	
	// ----------------------------------------------------------------------------
	
	/** Copy operator. */
	@Override
	public NewmanSpectralAlgorithmDivider copy() {
		
		return new NewmanSpectralAlgorithmDivider(this);
	}
	
	// ----------------------------------------------------------------------------
	
	@Override
	public void divide(ModularityDetector modDetector) throws JmodException, Exception {

		double beta;
		DenseVector eigenvect = new DenseVector(modDetector.currentSubcommunitySize_);

		// Return most positive eigenpair. It's possible that "most positive" eigenvalue beta is negative.
		// In this case, there are no subcommunities
		
		Log.debug(modDetector.getNetwork().getName() + "|" + identifier_, "Options: " + getOptionsStr());
		
		if (eigenvalueMethod_ == EigenMethods.POWER_METHOD)
			beta = EigenMethods.mostPositiveEigenpairPowerMethod(modDetector.currentSubcommunityB_, eigenvect);
//		else if (eigenvalueMethod_ == EigenMethods.LANCZOS_METHOD)
//			beta = EigenMethods.mostPositiveEigenpairLanczosMethod(modDetector.currentSubcommunityB_, eigenvect);
		else
			throw new JmodException("Invalid eigenvalue method.");

		if (beta <= 0) {
			Log.debug(modDetector.getNetwork().getName() + "|" + identifier_, "The dominant eigenvalue is definitively negative.");
			modDetector.currentSubcommunityQ_ = 0;
			return;
		}	
		
		// DON'T FORGET TO SET modDetector.currentSubcommunityS_
		// set the elements of s with "-1" or "1" according to the sign of the elements of the eigenvector
		for (int i = 0; i < modDetector.currentSubcommunitySize_; i++) {
			if (eigenvect.get(i) < 0) // note, they have been initialized at 1
				modDetector.currentSubcommunityS_.set(i, -1);
		}
		
		// Saves the current state of the module detection.
		// Don't forget to set modDetector_.currentSubcommunityS_ or the snapshot
		// will not see the change in differential mode (done just above).
		modDetector.takeSnapshot(identifier_);
	}
	
	// ----------------------------------------------------------------------------
	
	@Override
	public String getOptionsStr() {
		
		String optionsStr = "";
//		optionsStr = "-e ";
//		if (eigenvalueMethod_ == EigenMethods.LANCZOS_METHOD)
//			optionsStr += "COLT";
//		else if (eigenvalueMethod_ == EigenMethods.POWER_METHOD)
//			optionsStr += "POWER";
		return optionsStr;
	}
	
	// ----------------------------------------------------------------------------
	
//	/** Set the method to use to compute eigenvectors. */
//	public void setEigenvalueMethod(String method) throws Exception, JmodException {
//		
//		Log.info("Setting eigenvalue method to " + method + ".");
//		
//		if (method.compareTo("COLT") == 0)
//			eigenvalueMethod_ = EigenMethods.LANCZOS_METHOD;
//		else if (method.compareTo("POWER") == 0)
//			eigenvalueMethod_ = EigenMethods.POWER_METHOD;
//		else
//			throw new JmodException("Invalid eigenvalue method \"" + method + "\".");
//	}
	
    // =======================================================================================
    // GETTERS AND SETTERS

	public int getEigenvalueMethod() { return eigenvalueMethod_; }
	public void seEigenvalueMethod(int method) { eigenvalueMethod_ = method; }
}
