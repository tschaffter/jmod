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

package ch.epfl.lis.jmod.modularity;

import com.esotericsoftware.minlog.Log;

import cern.colt.matrix.*;
import cern.colt.matrix.impl.DenseDoubleMatrix1D;

/**
 * Supports the computation of network modularity Q.
 * 
 * @version November 26, 2011
 * 
 * @author Thomas Schaffter (firstname.name@gmail.com)
 * @author Daniel Marbach (firstname.name@gmail.com)
 */
public class Modularity {
	
	/** Modularity Q of a network. */
	private double modularity_ = 0.;
	
	/** Permanent variables which are initialized in the constructor (total number of edges in the network). */
	int permM_ = 0;
	/** Permanent variables which are initialized in the constructor (adjacency matrix). */
	private DoubleMatrix2D permA_ = null;
	/** Permanent variables which are initialized in the constructor (node degree vector). */
	private DoubleMatrix1D permK_ = null;
	
	/** Temporary variables which are initialized with initialize() (size). */
	private int tempSize_ = 0;
	/** Temporary variables which are initialized with initialize() (is root community). */
	private boolean tempRoot_ = false;
	/** Temporary variables which are initialized with initialize() (modularity matrix). */
	private DoubleMatrix2D tempB_ = null;
//	/** Temporary variables which are initialized with initialize() (s-vector). */
//	private DoubleMatrix1D tempS_ = null;
	
	// ============================================================================
	// PRIVATE METHODS
    
//	/** Update the modularity matrix B */
//	private void computeModularityMatrix(DoubleMatrix2D B, int size) {
//		
//		int i, j;
//		double Bij = 0.;
//
//		// calculating modularity matrix B
//		for(i=0; i < size; i++) {
//			for(j=i; j < size; j++) {
//				
//				Bij = permA_.get(i, j) - ((permK_.get(i)*permK_.get(j))/(2.*permM_));
//				B.set(i, j, Bij);
//				B.set(j, i, Bij);
//			}
//		}
//	}
    
	// ============================================================================
	// PUBLIC METHODS
	
    /** Default constructor. */
	public Modularity() {}

	// ----------------------------------------------------------------------------
	
	/** Constructor */
	public Modularity(DoubleMatrix2D A, DoubleMatrix1D k, int m) {
		
		initialize(A, k, m);
	}
	
	// ----------------------------------------------------------------------------
	
	/** Initialization */
	public void initialize(DoubleMatrix2D A, DoubleMatrix1D k, int m) {
		
		permA_ = A;
		permK_ = k;
		permM_ = m;
	}
	
	// ----------------------------------------------------------------------------
	
	/** Initialization */
	public void initialize(DoubleMatrix1D sVector, DoubleMatrix2D B, int size, boolean root) {
		
		tempSize_ = size;
		tempRoot_ = root;
		tempB_ = B;
//		tempS_ = sVector;
	}
	
	// ----------------------------------------------------------------------------
	
	/**
	 * Performs a community division.
	 * @return int 0 = all is fine, -1 = error in s moving vertex method, -2 = dominant eigenvalue is negative
	 */
//	public int subdivisionIn2Communities() throws Exception {
//
//		int result;
//		
//		int i;
//		double beta = -1;
//		DoubleMatrix1D eigenvect = new DenseDoubleMatrix1D(tempSize_);
//
//		// Return most positive eigenpair. It's possible that "most positive" eigenvalue beta is negative.
//		beta = EigenMethods.mostPositiveEigenpairColt(tempB_, eigenvect);
//		//beta = Universal.mostPositiveEigenpair(tempSize_, tempB_, err, beta, eigenvect);
//		
//		if (beta > 0) {
//			Log.debug("Modularity", "Best Beta is > 0: " + Double.toString(beta));			
//			// set the elements of s with "0" or "1" according to the sign of the elements of the eigenvector
//			for (i = 0; i < tempSize_; i++) {
//				if(eigenvect.get(i) >= 0)
//					tempS_.set(i, 1);
//				else
//					tempS_.set(i, -1);
//			}
//
//			JmodSettings settings = JmodSettings.getInstance();
//			if (settings.getUseMovingVertex()) {
//				Log.debug("Modularity", "Running moving vertex method (MVM)");
//				result = sMovingVertexMethod(tempS_);
//				
//				if (result == 0) // MVM successful
//					return 0;
//				else
//					return -1;
//			}
//		}
//		else {
//			// Beta is negative -> no subdivision that increase the modularity
//			return -2;
//		}
//		
//		return 0;
//	}
	
	// ----------------------------------------------------------------------------
	
	/**
	 * Computes modularity Q from the given s-vector.
	 * This method is called by the root community.
	 */
	public double computeQ(DoubleMatrix1D s) {
		
		int i, j;
		double sum = 0;
		for (i = 0; i < tempSize_; i++) {
			for (j = 0; j < tempSize_; j++)
				sum += (permA_.get(i, j) - ((permK_.get(i)*permK_.get(j))/(2.*permM_))) * s.get(i) * s.get(j);
		}
		return sum/(4. * permM_);
	}
	
	// ----------------------------------------------------------------------------
	
	/**
	 * Computes modularity deltaQ from the given s-vector.
	 * This method is called by the children community.
	 */
	public double computeDeltaQ(DoubleMatrix1D s) {

		double deltaQ = 0;
		double sumLeft = 0;
		double sumRight = 0;
		int i, j;
		for (i = 0; i < tempSize_; i++) {
			for (j = 0; j < tempSize_; j++) {
				sumRight += tempB_.get(i, j);
				sumLeft += tempB_.get(i, j) * s.get(i) * s.get(j);
			}
		}
		deltaQ = (sumLeft - sumRight)/(4. * permM_);

		return deltaQ;
	}
	
	// ----------------------------------------------------------------------------
	
	/** Returns the best s-vector by the moving vertex method. */
	public int sMovingVertexMethod(DoubleMatrix1D s) {

		int i, iter = 0, index = 0;
		double currentDeltaQ = 0, tempDeltaQ = 0;
		boolean change = false;
		DoubleMatrix1D result = new DenseDoubleMatrix1D(tempSize_);
		DoubleMatrix1D oneElementModifiedSVector = new DenseDoubleMatrix1D(tempSize_);

		// copy the vector s to the vector result
		for (i = 0; i < tempSize_; i++)
			result.set(i, s.get(i));
		
		if (tempRoot_) {
			// Even if the variable's name contains "DeltaQ", it represent Q en not deltaQ in this
			// loop if(temp_root)
			currentDeltaQ = computeQ(s);
			Log.debug("Modularity", "Q of input sVector = " + currentDeltaQ);
			
			// Break the loop if there are no more amelioration
			do{
				if (change)
					result.set(index, -result.get(index));

				change = false;
				index = 0;

				for (i = 0; i < tempSize_; i++) {
					// Copying $result in $oneElementModifiedSVector
					oneElementModifiedSVector.assign(result);
					// Only one element are modified (1 -> -1 and -1 -> 1)
					oneElementModifiedSVector.set(i, -oneElementModifiedSVector.get(i));
					tempDeltaQ = computeQ(oneElementModifiedSVector);
					if(tempDeltaQ > currentDeltaQ) {
						currentDeltaQ = tempDeltaQ;
						// Save the index of the amelioration
						index = i;
						Log.debug("Modularity", "Iter " + iter + ", index " + i + ": New Q = " + currentDeltaQ);
						change = true;
					}
				}
				iter++;
			} while (change);
		}
		else {
			currentDeltaQ = computeDeltaQ(s);
			Log.debug("Modularity", "DeltaQ of input sVector = " + currentDeltaQ);

			do{
				if (change)
					result.set(index, -result.get(index));

				change = false;
				index = 0;

				for (i = 0; i < tempSize_; i++) {
					oneElementModifiedSVector.assign(result);
					oneElementModifiedSVector.set(i, -oneElementModifiedSVector.get(i));
					
					tempDeltaQ = computeDeltaQ(oneElementModifiedSVector);
					if (tempDeltaQ > currentDeltaQ) {
						currentDeltaQ = tempDeltaQ;
						// Save the index of the amelioration
						index = i;
						Log.debug("Modularity", "Iter " + iter + ", index " + i + ": New deltaQ = " + currentDeltaQ);
						change = true;
					}
				}
				iter++;
			} while (change);
		}
		s.assign(result);
		
		return 0;
	}
	
	// ============================================================================
	// GETTERS AND SETTERS
	
	public void setModularity(double m) { modularity_ = m; }
	public double getModularity() { return modularity_; }
	
	public void addModularity(double value) { modularity_ += value; }
}
