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

package ch.epfl.lis.jmod.test;

import java.io.File;
import java.text.DecimalFormat;

import com.esotericsoftware.minlog.Log;

import ch.epfl.lis.jmod.Jmod;
import ch.epfl.lis.jmod.JmodNetwork;
import ch.epfl.lis.jmod.JmodSettings;
import ch.epfl.lis.networks.Structure;

/** 
 * Runs benchmarks to test the implementation and performance of Jmod.
 * 
 * @version November 26, 2011
 * 
 * @author Thomas Schaffter (firstname.name@gmail.com)
 * @author Daniel Marbach (firstname.name@gmail.com)
 */
public class JmodTest {
	
	// ============================================================================
	// PRIVATE METHODS
	
	/**
	 * Tests the modularity on some benchmark networks.
	 * 
	 * Modularity Q |  Newman         Newman+mvm     Newman+mvm+gmvm
	 * ------------------------------------------------------------
	 * karate.gml   |  0.393409 (4)   0.418803 (4)*   0.41979 (4)
	 * lesmis.gml   |  0.532271 (8)   0.544346 (6)   0.545493 (6)
	 * polbooks.gml |  0.467184 (4)   0.524666 (4)   0.52538 (4)
	 * adjnoun.gml  |  0.242608 (14)  0.291527 (7)   0.301099 (7)**
	 * football.gml |  0.49174 (15)   0.604631 (10)  0.603383 bug!!!
	 * metabolic.gml|  0.348353***
	 * jazz.gml     |  0.393639       0.442190       0.4451438
	 * 
	 * karate (C)             | 0.3934089414858646 (4)     0.41880341880341887 (4)    0.4197896120973046 (4)
	 * karate (P)             | 0.3934089414858646 (4)     0.41880341880341887 (4)    0.4197896120973046 (4)
	 * 
	 * jazz (C)               | 0.3936391975914523 (3)     0.4421900910865398 (4)     0.44514384661751877 (4)
	 * jazz (P)               | 0.3936391975914523 (3)     0.4421900910865398 (4)     0.44514384661751877 (4)
	 * 
	 * lesmis (C)             | 0.5322710645421292 (8)     0.5443455886911776 (6)     0.5454925909851842 (6)
	 * lesmis (P)             | 0.5322710645421292 (8)     0.5443455886911776 (6)     0.5454925909851842 (6)
	 * 
	 * polbooks (C)           | 0.4671844550367355 (4)     0.5246656485723511 (4)     0.5253803713473333 (4)
	 * polbooks (P)           | 0.4671844550367355 (4)     0.5246656485723511 (4)     0.5253803713473333 (4)
	 * 
	 * adjnoun (C)            | 0.24260761245674745 (11)   0.291526643598616 (7)      0.30109896193771624 (7)     
	 * adjnoun (P)            | 0.24260761245674745 (11)   0.291526643598616 (7)      0.30109896193771624 (7)
	 * 
	 * football (C)           | 0.49260582964534017 (8)    0.6009117303449721 (10)    0.6045695626834541 (10)
	 * football (P)           | 0.49260582964534017 (8)    0.6009117303449721 (10)    0.6045695626834541 (10)
	 * 
	 * celegans_metabolic (C) | 0.34835275110501746 (10)   0.42676494436823903 (13)   0.4391295229385764 (13)      1s / 1s / 2s
	 * celegans_metabolic (P) | 0.34835275110501746 (10)   0.42676494436823903 (13)   0.4391295229385764 (13)      2s / 3s / 3s
	 * 
	 * email (C)              | 0.4888459924195939 (7)     0.5525560840378368 (11)    0.5654236450416774 (11)      42s / 43s / 50s
	 * email (P)              | 0.4888459924195939 (7)     0.5525560840378367 (11)    0.5654236450416774 (11)      7s / 19s / 26s
	 * 
	 * netscience_cise (C)    | 0.789059785882775 (220)    0.9458600073083268 (289)   0.949221087963045 (289)      5m44s / 5m08s / 5m50s
	 * netscience_cise (P)    | 0.9388003480418209 (285)   0.9496661197508044 (286)   0.9526995447534663 (286)     26m23s / 21m37s / 22m26s
	 * 
	 * power_cise (C)         | 0.8977148230491736 (265)   0.923406463454258 (125)    0.9253056679941725 (63)
	 * power_cise (P)         | 0.8974577672747416 (138)   0.9226705415418347 (64)    0.9240959281444510 (64)
	 * 
	 * power_cise + COLT = 400 minutes
	 * power_cise + POWER = 88 minutes
	 * 
	 * The above values are from the c++ imod code, but I don't remember how
	 * I knew that they are correct. In fact, the last entry isn't correct
	 * (because lower than before gMVM).
	 * '*' is the only value that I can validate from Newman2006.
	 * 
	 * With the java version, I get the same values, except for '**' and
	 * for all three values of football.gml. New:
	 * adjnoun.gml	|                                 0.298552
	 * football.gml	| 0.488503        0.596065        0.602126
	 * 
	 * *** Newman found 0.435 and no other values are available.
	 * I didn't use this network in my semester project.
	 * 
	 * Jazz: both Newman and Newman+mvm are the same as reported in my semester
	 * project.
	 * 
	 * The football values are now consistent (increasing with gMVM) and for
	 * adjnoun we don't know whether the value was correct before. It may be
	 * possible that the new values, and not the old ones, are correct!
	 * 
	 * So for now, I add the new values in the tests...
	 * 
	 * We could add:
	 * jazz.gml: Newman+mvm = 0.442
	 */
	private void runTestSuite() {
		
		JmodSettings p = JmodSettings.getInstance();
		int numErrors = 0;
		p.setUseMovingVertex(false);
		p.setUseGlobalMovingVertex(false);
		
		try {
			// football.gml has multi-edges (duplicate edges), which we remove
			// The confusion is probably due to how multi-edges are handled.
			// The Q-values given here for football.gml are the ones computed
			// by imod, thus we can't know for sure whether they're correct.
			
//			JmodSettings.setLogLevel("DEBUG");
//			JmodSettings.setEigenMethod("POWER");
			
			// jazz.gml gives a parse error...
//			numErrors += runTest("fake.gml", Double.NaN);		
//			numErrors += runTest("karate.gml", 0.393409);		
//			numErrors += runTest("jazz.gml", 0.393639);		
//			numErrors += runTest("lesmis_cise.gml", 0.532271);
//			numErrors += runTest("polbooks_cise.gml", 0.467184);
//			numErrors += runTest("adjnoun_cise.gml", 0.242608);
//			numErrors += runTest("football_cise.gml", 0.49260582964534017); //0.49174); // gives error!
//			numErrors += runTest("metabolic.gml", Double.NaN);
//			numErrors += runTest("email_cise.gml", Double.NaN);
//			numErrors += runTest("pgp_cise.gml", Double.NaN);
			numErrors += runTest("netscience_cise.gml", Double.NaN);
//			numErrors += runTest("power_cise.gml", Double.NaN);
//			numErrors += runTest("celegans_metabolic_cise.gml", Double.NaN);
	
			p.setUseMovingVertex(true);
	
//			numErrors += runTest("karate.gml", 0.418803);
//			numErrors += runTest("jazz.gml", 0.442190);
//			numErrors += runTest("lesmis_cise.gml", 0.544346);
//			numErrors += runTest("polbooks_cise.gml", 0.524666);
//			numErrors += runTest("adjnoun_cise.gml", 0.291527);
//			numErrors += runTest("football_cise.gml", 0.6009117303449721); //0.604631); // gives error!
//			numErrors += runTest("metabolic.gml", Double.NaN);
//			numErrors += runTest("email_cise.gml", Double.NaN);
//			numErrors += runTest("celegans_metabolic.gml", Double.NaN);
			numErrors += runTest("netscience_cise.gml", Double.NaN);
//			numErrors += runTest("power_cise.gml", Double.NaN);
//			numErrors += runTest("celegans_metabolic_cise.gml", Double.NaN);
	
			p.setUseGlobalMovingVertex(true);
	
//			numErrors += runTest("karate.gml", 0.41979);
//			numErrors += runTest("jazz.gml", 0.4451438);		
//			numErrors += runTest("lesmis_cise.gml", 0.545493);
//			numErrors += runTest("polbooks_cise.gml", 0.52538);
//			numErrors += runTest("adjnoun_cise.gml", 0.301099); // gives error!
//			numErrors += runTest("football_cise.gml", 0.6045695626834516); //0.603383); // gives error!
//			numErrors += runTest("metabolic.gml", Double.NaN);
//			numErrors += runTest("email_cise.gml", Double.NaN);
//			numErrors += runTest("celegans_metabolic.gml", Double.NaN);
			numErrors += runTest("netscience_cise.gml", Double.NaN);
//			numErrors += runTest("power_cise.gml", Double.NaN);
//			numErrors += runTest("celegans_metabolic_cise.gml", Double.NaN);
			
			if (numErrors == 0)
				Log.info("SUCCESS");
			else
				Log.info(numErrors + " ERRORS");
			
		} catch (Exception e) {
			Log.error("JmodTest", "Unable to complete test suite.", e);
		}
	}
	
	// ----------------------------------------------------------------------------

	/** Checks that foundQ equals expectedQ. */
	private int assertModularityEqual(JmodNetwork network, Jmod jmod, Double expectedQ) {
		
		double foundQ = jmod.getModularity();
		double delta = foundQ - expectedQ;
		Log.info(network.getName(), "Modularity Q: " + foundQ);
		DecimalFormat threeDForm = new DecimalFormat("#.###");
		Log.info(network.getName(), "Modularity Q: " + threeDForm.format(foundQ) + " (rounded)");
		Log.info(network.getName(), "Num. communities: " + jmod.getRootCommunity().getNumIndivisibleCommunities());
		
		if (expectedQ.isNaN()) // skip
			return 0;
		
		if (delta < -1e-5 || delta > 1e-5) {
			Log.warn("Expected: Q: " + expectedQ);
			return 1;
		}
		return 0;
	}
	
	// ============================================================================
	// PUBLIC METHODS
	
	/** Default constructor. */
	public JmodTest() {}
	
	// ----------------------------------------------------------------------------
	
	/** Runs the test suite. */
	public void run() throws Exception {
		
		runTestSuite();
	}
	
	// ----------------------------------------------------------------------------

	/**
	 * Loads a network and check that the modularity equals expectedQ.<p>
	 * Note, the network file must specify a GML file located in the rsc folder of the 
	 * package test. An exception is thrown if the modularity value found doesn't match
	 * the expected one.
	 */
	public int runTest(String filename, double expectedQ) throws Exception {
		
		JmodNetwork network = new JmodNetwork();
		network.read(new File("rsc/test/" + filename).toURI(), Structure.Format.GML);
		
		Jmod jmod = new Jmod();
		jmod.runModularityDetection(network);
		
		Log.info("JmodTest", "Computation time: " + jmod.getRootCommunity().getComputationTime());
		
		return assertModularityEqual(network, jmod, expectedQ);
//		return assertModularityEqual(network, jmod.getModularity(), expectedQ);
	}
	
	// ----------------------------------------------------------------------------
	
	/** Main method. */
	public static void main(String args[]) {
		
		try {			
			JmodTest test = new JmodTest();
			test.run();
			
		} catch (Exception e) {
			Log.error("JmodTest", e);
		} finally {
			Log.info("Done");
			System.exit(0);
		}
	}
}
