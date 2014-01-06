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

package ch.epfl.lis.jmod.modularity.utils;

import java.util.Iterator;

import no.uib.cipr.matrix.DenseMatrix;
import no.uib.cipr.matrix.DenseVector;
import no.uib.cipr.matrix.SymmDenseEVD;
import no.uib.cipr.matrix.UpperSymmDenseMatrix;
import no.uib.cipr.matrix.Vector;
import no.uib.cipr.matrix.VectorEntry;

import com.esotericsoftware.minlog.Log;

import ch.epfl.lis.jmod.JmodException;
import ch.epfl.lis.jmod.modularity.ModularityDetector;

/**
 * Implements algorithms and methods to compute eigenvalues and corresponding eigenvectors.<p>
 * 
 * Two algorithms are implemented to compute the most positive eigenvalue 
 * of modularity matrix B or generalized modularity matrix B^{(g)}.<p>
 * <ul>
 * <li>Power method computes the eigenvector associated to the most positive eigenvalue
 * <li>Lanczos method from COLT library to compute all eigenvectors (commented)
 * <li>Similar method to compute all eigenvalues and eigenvector using MTJ
 * </ul><p>
 * 
 * These commands were required to use the correct BLAS and LAPACK libraries:<p>
 * <ul>
 * <li>update-alternatives --config libblas.so.3 (choose /usr/lib64/atlas/libsatlas.so.3)
 * <li>update-alternatives --config liblapack.so.3 (choose /usr/lib64/atlas/libsatlas.so.3)
 * </ul></p>
 * 
 * <b>References:</b><p>
 * <ul>
 * <li><a href="https://github.com/fommil/matrix-toolkits-java" target="_blank">
 * matrix-toolkits-java</a>: Java linear algebra library powered by BLAS, LAPACK, and ARPACK
 * <li><a href="https://github.com/fommil/netlib-java" target="_blank">netlib-java</a>:
 * Uses BLAS/LAPACK if their are available, otherwise F2J to ensure full portability 
 * on the JVM. MTJ is based on netlib-java.
 * </ul><p>
 * 
 * @version November 23, 2013
 * 
 * @author Thomas Schaffter (thomas.schaff...@gmail.com)
 */
public class EigenMethods {

	/** Power method to compute the most positive eigenpair of B or B^(g). */
	public static final int POWER_METHOD = 1;
//	/** Lanczos method to compute the most positive eigenpair of B or B^(g). */
//	public static final int LANCZOS_METHOD = 2;

	private static final double POWER_EPS = 0.00001;
	private static final double POWER_DTA = 0.00001;
	private static final int POWER_NUM_ITERS_MAX = 50000;
	
	// ============================================================================
	// PUBLIC METHODS
	
	/**
	 * Computes the most positive eigenpair (beta, eigenvect) using Power method.
	 * @param B The symmetric modularity matrix B or the generalized modularity matrix B^(g).
	 * @param y The vector will contain the eigenvector associated to the most positive eigenvalue found.
	 * @return The most positive eigenvalue of the modularity matrix found. 
	 */
	public static double mostPositiveEigenpairPowerMethod(UpperSymmDenseMatrix B, DenseVector y) throws JmodException, Exception {

		Log.debug("EigenMethods", "Computing the most positive eigenpair using Power method.");
		
		if (B.numRows() == 0 || B.numColumns() == 0 || y.size() == 0)
			throw new JmodException("Can not compute most positive eigenpair of a system with zero dimensions.");		
		
		int size = y.size();
		Integer erreur = 0;
		double beta = -1;
		
		// compute eigenvector corresponding to leading eigenvalue
		beta = powerMethodNewman(B, y, POWER_EPS, POWER_DTA, POWER_NUM_ITERS_MAX, erreur);
		//beta = powerMethodWikipedia(B, y, POWER_EPS, POWER_NUM_ITERS_MAX);
		//beta = powerMethodWicklin(B, y, POWER_EPS, POWER_NUM_ITERS_MAX);
		
		Log.debug("EigenMethods", "Eigenvalue beta: " + beta);

		// compute most positive eigenvalue if the leading one was negative
		UpperSymmDenseMatrix idt = new UpperSymmDenseMatrix(size);
		if (beta < 0) {
			Log.debug("EigenMethods", "The leading eigenvalue beta " + beta + " is negative, so we compute now for the most positive one.");
			
			// idt = B + beta*I
			double tempBeta = beta;
			for (int i = 0; i < size; i++)
				idt.set(i, i, Math.abs(beta));
			idt.add(B);
			
			beta = powerMethodNewman(idt, y, POWER_EPS, POWER_DTA, POWER_NUM_ITERS_MAX, erreur);
//			beta = powerMethodWikipedia(idt, y, POWER_EPS, POWER_NUM_ITERS_MAX);
//			beta = powerMethodWicklin(idt, y, POWER_EPS, POWER_NUM_ITERS_MAX);
			beta = beta - Math.abs(tempBeta);
			Log.debug("EigenMethods", "Most positive beta = " + Double.toString(beta));
		}
		return beta;
	}
	
	// ----------------------------------------------------------------------------
	
	/**
	 * Computes the leading eigenvalue and the corresponding eigenvector of the symmetric matrix A.<p>
	 * <b>Notes:</b>
	 * <ul>
	 * <li>Returns the signed leading eigenvalue beta (and not abs(beta))
	 * <li>Now uses MTJ and runs faster than the original implementation
	 * </ul>
	 * <b>Reference:</b>
	 * Mark Newman's spectral algorithm
	 * @param A Real square matrix A(n,n)
	 * @param y Output eigenvector corresponding to beta
	 * @param eps Smallest number in double precision
	 * @param dta Required precision
	 * @param mit Maximum number of iterations
	 * @param err -1: no convergence, 0: method can not be applied, 1: convergence ok
	 * @return Eigenvalue beta
	 */
	public static double powerMethodNewman(UpperSymmDenseMatrix A, DenseVector y, double eps, double dta, int mit, Integer err)  {
		
		// original method stops when err = 1 (i.e. phi < dta)
		int n = y.size();
		double val = 0.;
		double phi = 0.;
		double beta = 0.;
		
		// initial eigenvector
		DenseVector u0 = new DenseVector(n);
		Iterator<VectorEntry> iter = u0.iterator();
		int index = 0;
		while(iter.hasNext())
			iter.next().set(1.0/Math.sqrt(index++ + 1));
		
		int it = 1;
		err = -1;
		while (err == -1 && it <= mit) {
			beta = 0.;
			// transform: y = A*u0;
			A.mult(u0, y);
			// set beta to largest absolute element of y
			iter = u0.iterator();
			while(iter.hasNext()) {
				val = iter.next().get();
				if (Math.abs(val) > Math.abs(beta))
					beta = val;
			}
			// considered as beta == 0 and so y should be equal to the unit vector
			// if it's the case, all nodes should belong to the same community
			if (Math.abs(beta) < eps)
				err = 0;
			else {
				y.scale(1/beta);
				phi = 0.;
				for (int i = 0; i < n; i++) {
					val = Math.abs(y.get(i) - u0.get(i));
					if (val > phi)
						phi = val; // phi is the largest phi associated to one element
				}
				// check convergence (main reason why this method is leaving)
				if (phi < dta)
					err = 1;
				else {
					u0.set(y);
					it++;
				}
			}
		}

		Log.debug("EigenMethods", "Convergence phi: " + Double.toString(phi) + ", num it: " + Integer.toString(it));

		if (phi > 1) {
			Log.debug("EigenMethods", "The Power method didn't converge (current beta: " + beta + ").");
			beta = -beta;
		}
		return beta;
		
		
		
//		// Original implementation of the power method by Mark Newman		
//		int n = y.size();
//		double beta = 0.;
//		int i,j,it;
//		double phi = 0;
//		double s;
//		double[] u0 = new double[n];
//
//		for (i=0; i<n; i++) u0[i]=1.0/Math.sqrt(i+1);
//		err=-1; it=1;
//		while (err==-1 && it<=mit) {
//			beta=0.0;
//			for (i=0; i<n; i++) {
//				y.set(i, 0.0);
//				for (j=0; j<n; j++) y.set(i, y.get(i) + (A.get(i, j) * u0[j]));
//				if (Math.abs(y.get(i))>Math.abs(beta))  beta=y.get(i);
//			}
//			if (Math.abs(beta) < eps) err=0;
//			else {
//				for (i=0; i<n; i++)  y.set(i, y.get(i) / beta);
//				phi=0.0;
//				for (i=0; i<n; i++)  {
//					s=Math.abs(y.get(i)-u0[i]);
//					if (s>phi) phi=s; // phi is the large phi associated to one element
//				}
//				if (phi<dta) err=1;
//				else {
//					for (i=0; i<n; i++) u0[i]=y.get(i);
//					it++;
//				}
//			}			
//		}
//		
//		Log.debug("EigenMethods", "Convergence phi: " + Double.toString(phi) + ", num it: " + Integer.toString(it));
//
//		if (phi > 1) {
//			Log.debug("EigenMethods", "The Power method didn't converge (current beta: " + beta + ").");
//			beta = -beta;
//		}
//		return beta;
	}
	
	// ----------------------------------------------------------------------------
	
	/**
	 * Computes the leading eigenvalue and the corresponding eigenvector of the symmetric matrix A.<p>
	 * <b>Notes:</b>
	 * <ul>
	 * <li>Same method than powerMethodWikipedia() but using MTJ BLAS methods (=> this method is much faster).
	 * <li>This method only consider the absolute value of the leading eigenvalue beta (returns abs(beta)).
	 * </ul><p>
	 * <b>Reference:</b>
	 * <a href="http://en.wikipedia.org/wiki/Power_iteration" target="_blank">Wikipedia: Power iteration</a>
	 * @param A Modularity matrix
	 * @param y Output eigenvector corresponding to the leading eigenvalue beta
	 * @return Leading eigenvalue beta (actually returns abs(beta))
	 */
	public static double powerMethodWicklin(DenseMatrix A, DenseVector v, double eps, int maxIters) throws JmodException, Exception {
		
		int n = v.size();
		int iteration = 0;
		double lambda = 0.;
		double lambdaOld = 0.;
		DenseVector z = new DenseVector(n);
		
		// initialize v
		ModularityDetector.assignRandomly(v, -1, 1);
		v.scale(1/v.norm(Vector.Norm.Two)); // normalize
		
		while (iteration <= maxIters) {
			// transform: z = A*v;
			A.mult(v, z);
			// normalize
			v.set(z);
			v.scale(1/v.norm(Vector.Norm.Two));
			
			lambda = v.dot(z);
			iteration++;
			
			if (Math.abs(lambda - lambdaOld)/lambda < eps)
				return lambda;
			
			lambdaOld = lambda;
		}
		Log.debug("EigenMethods", "The Power method didn't converge (current beta: " + lambda + ").");
		return lambda;
	}
	
	// ----------------------------------------------------------------------------
	
	/**
	 * Computes the leading eigenvalue and the corresponding eigenvector of the symmetric matrix A.<p>
	 * <b>Notes:</b>
	 * <ul>
	 * <li>Same method than powerMethodWicklin() but without using MTJ BLAS methods (=> this method is way slower).
	 * <li>This method only consider the absolute value of the leading eigenvalue beta (returns abs(beta)).
	 * </ul><p>
	 * <b>Reference:</b>
	 * <a href="http://en.wikipedia.org/wiki/Power_iteration" target="_blank">Wikipedia: Power iteration</a>
	 * @param A Modularity matrix
	 * @param y Output eigenvector corresponding to the leading eigenvalue beta
	 * @return Leading eigenvalue beta (actually returns abs(beta))
	 */
	public static double powerMethodWikipedia(DenseMatrix A, DenseVector y, double eps, int kmax) throws JmodException, Exception {
		
		int n = y.size();
		double lambda = 0.;
		double lambda0 = 0.;
		double norm = 0.;
		DenseVector yp = new DenseVector(n);
		
		// initial guess of the dominant eigenvector (random, but not zero or one=>beta found always close to zero)
//		ModularityDetector.assign(y, 1);
		// initialize v
		ModularityDetector.assignRandomly(y, -1, 1);
		y.scale(1/y.norm(Vector.Norm.Two)); // normalize
		
		for (int k = 1; k <= kmax; k++) {
			// compute y'=A*y
			for (int i = 0; i < n; i++) {
				yp.set(i, 0.);
				for (int j = 0; j < n; j++)
					yp.set(i, yp.get(i) + A.get(i, j)*y.get(j));
			}
			// normalization coefficient
			norm = 0.;
			for (int i = 0; i < n; i++)
				norm += yp.get(i)*yp.get(i);
			norm = Math.sqrt(norm);
			// normalize vector y(n) to unity for the next iteration
			for (int i = 0; i < n; i++)
				y.set(i, yp.get(i)/norm);
			lambda = norm;
			// check for convergence
			if (Math.abs(lambda-lambda0) < eps)
				return lambda;
			// prepare for the next iteration
			lambda0 = lambda;
		}
		Log.debug("EigenMethods", "The Power method didn't converge (current beta: " + lambda + ").");
		return lambda;
	}
	
	// ----------------------------------------------------------------------------
	
	/** 
	 * Computes all eigenvalues and eigenvectors of the symmetric matrix A.<p>
	 * <b>Note:</b>
	 * Computing all eigenvalues of a matrix of size n is O(n^2) as shown 
	 * <a href="http://blogs.sas.com/content/iml/2012/05/09/the-power-method/" target="_blank">here</a>.
	 * In comparison, the power method has an O(n) computation cost.
	 * @param A The symmetric modularity matrix B or the generalized modularity matrix B^(g).
	 * @param y The vector will contain the eigenvector associated to the most positive eigenvalue found.
	 * @return Leading eigenvalue beta 
	 */
	public static double mostPositiveEigenpairSymmDenseEVD(DenseMatrix A, DenseVector y) throws JmodException, Exception {
		
		Log.debug("EigenMethods", "Computing the most positive eigenpair using MTJ SymmDenseEVD.");
		
		// compute all eigenvalues and corresponding eigenvectors
		SymmDenseEVD evd = SymmDenseEVD.factorize(A);
		
		// eigenvalues are sorted by values (most positive at index n-1)
		int n = y.size();
		double[] eigenvalues = evd.getEigenvalues();
		double beta = eigenvalues[n-1];
		
		DenseMatrix eigenvectors = evd.getEigenvectors();
		for (int i = 0; i < n; i++)
			y.set(i, eigenvectors.get(i, n-1));
		
		return beta;
	}
	
	// ----------------------------------------------------------------------------
	
//	/**
//	 * Computes all eigenvalues and eigenvectors of the symmetric matrix A.
//	 * @param mod Modularity matrix
//	 * @param eigenvect Output eigenvector corresponding to the leading eigenvalue beta
//	 * @return Leading eigenvalue beta
//	 */
//	public static double mostPositiveEigenpairLanczosMethod(DenseMatrix mod, DenseVector eigenvect) throws JmodException, Exception {
//		
//		throw new JmodException("Lanczos method for eigendecomposition is deprecated.");
//		
//		Log.debug("EigenMethods", "Computing the most positive eigenpair with COLT.");
//		
//		if (mod.numRows() == 0 || mod.numColumns() == 0 || eigenvect.size() == 0)
//			throw new JmodException("Can not compute most positive eigenpair of a system with zero dimensions.");
//		
//		// create the eigenvalues decomposition system
//		EigenvalueDecomposition evd = null;
//		try {
//			Log.trace("mostPositiveEigenpairColt", "B size: " + mod.numRows() + "x" + mod.numColumns());
//			Log.trace("mostPositiveEigenpairColt", "eivenvect size: " + eigenvect.size());
//			evd = new EigenvalueDecomposition(mod);
//		} catch (OutOfMemoryError e) {
//			e.printStackTrace();
//		} catch (Exception e) {
//			throw e;
//		}
//		
//		// compute the diagonal eigenvalue matrix D
//		DoubleMatrix2D D = evd.getD();
//		// compute the eigenvector matrix V
//		DoubleMatrix2D V = evd.getV();
//		
//		// Most Positive EigenValue
//		double MPEV;
//		int indexMPEV;
//		
//		// COLT order the eigenvalues from the most negative one to the
//		// most positive one
//		indexMPEV = eigenvect.size()-1; // last one if the most positive one
//		MPEV = D.get(indexMPEV, indexMPEV);
//		
//		Log.trace("EigenMethods", "MPEV: " + MPEV);
//
////		// set MPEV
////		MPEV = D.get(0, 0);
////		indexMPEV = 0;
////		
////		// get the MPEV
////		for (int i = 1; i < D.rows(); i++) {
////			Log.trace("EigenMethods", "MPEV " + i + ": " + D.get(i, i));
////			if (D.get(i, i) > MPEV) {
////				MPEV = D.get(i, i);
////				indexMPEV = i;
////			}
////		}
//		
//		// finally, save the search {eigenvalue, eigenvector} pair
//		for (int i = 0; i < V.rows(); i++)
//			eigenvect.set(i, V.get(i, indexMPEV));
//		
//		return MPEV;
//	}
}
