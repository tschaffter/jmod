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

import java.io.File;
import java.net.URI;
import java.util.Iterator;
import java.util.Random;

import no.uib.cipr.matrix.DenseVector;
import no.uib.cipr.matrix.UpperSymmDenseMatrix;
import no.uib.cipr.matrix.VectorEntry;

import com.esotericsoftware.minlog.Log;

import ch.epfl.lis.jmod.JmodException;
import ch.epfl.lis.jmod.JmodNetwork;
import ch.epfl.lis.jmod.JmodSettings;
import ch.epfl.lis.jmod.modularity.community.Community;
import ch.epfl.lis.jmod.modularity.community.dividers.CommunityDivider;
import ch.epfl.lis.jmod.modularity.community.dividers.CommunityDividerManager;

/**
 * Implements the modularity detection.<p>
 * 
 * Computes the modularities Q and does the optimal decomposition of a network into a set of
 * communities using Newman's spectral method combined with the moving vertex method (MVM)
 * and the additional global moving vertex method (gMVM).<p>
 * 
 * <b>Important:</b><p>
 * The optimization procedure gMVM modify the centent of the indivisible communities and
 * thus breaks the modular decomposition hierarchy. Thus after applying gMVM, a node
 * in an indivisible communities is not necessarily in its parent community.<p>
 * 
 * @version April, 2011
 * 
 * @author Thomas Schaffter (firstname.name@gmail.com)
 * @author Daniel Marbach (firstname.name@gmail.com)
 */
public class ModularityDetector {
	
	/** Message thrown when the current modularity detection is canceled. */
	protected static final String CANCEL_MESSAGE = "Module detection canceled.";
	
	/** The modularity matrix of the complete network. */
	protected UpperSymmDenseMatrix modularityMatrix_ = null;
	/** Modularity Q. */
	protected double modularity_ = 0.;
	/** Total number of modularity evaluations so far. */
	protected int numModularityEvaluations_ = 0;
	/** Contribution of global MVM to Q. */
	protected double contributionGlobalMvm_ = 0.;
	
	// Some network properties that we need to compute modularities
	/** Reference to the network. */
	protected JmodNetwork network_ = null;
	/** Network size. */
	protected int networkSize_ = 0;
	/** Number of edges of the complete network. */
	protected int m_ = 0;
	/** Adjacency matrix of the complete network (reference to JmodNetwork.A_). */
	protected boolean[][] A_ = null;
	/** Degrees k of all vertices (reference to JmodNetwork.k_). */
	protected int[] k_ = null ;
	
	// Temporary variables that are initialized with init()
	/** Size of the current (sub)community (declared as public for fast access). */
	public int currentSubcommunitySize_ = 0;
	/** Modularity matrix / generalised modularity matrix of the current community (declared as public for fast access). */
	public UpperSymmDenseMatrix currentSubcommunityB_ = null;	
	/** s vector defining the division of the current community (declared as public for fast access). */
	public DenseVector currentSubcommunityS_ = null;
	/** Modularity Q / delta Q for the current community (declared as public for fast access). */
	public double currentSubcommunityQ_ = 0.;
	
	/** Reference to the current community being split. */
	public Community currentCommunityBeingSplit_ = null;

	/** Magnitude of element of eigenvector. */
//	protected DoubleMatrix1D amplitudeElementEigenvector_ = null;
	
	/** Status to indicate that the detection must leave. */
	protected boolean canceled_ = false;
	
	/** Directory where the output files are saved (default: current directory). */
	private URI outputDirectory_ = new File(".").toURI();
	
	/** Snapshot of the current state of the module detection. */
	protected ModularityDetectorSnapshot snapshot_ = null;

	// ============================================================================
	// PRIVATE METHODS
    
	/** Finds the appartenance vector s (currentSubcommunityS_) that maximizes the modularity (currentSubcommunityQ_). */
	private void divide(String communityName) throws Exception, JmodException {
		
		if (canceled_)
			throw new JmodException(CANCEL_MESSAGE);
		
		JmodSettings settings = JmodSettings.getInstance();
		
		// initialize the appartenance vector (put all vertexes into the same community)
		currentSubcommunityS_ = new DenseVector(currentSubcommunitySize_);
		assign(currentSubcommunityS_, 1);
		
		// NEW: A new instance of the divider present in the divider manager is created using the
		// copy operator. This is done since several module detections can be performed in parallel
		// on multiple networks. The options stored in the original divider have been explicitly
		// copied. Moreover, the life of the divider is limited to this method so memory should be
		// freed accordingly when leaving the method.
		CommunityDivider divider = CommunityDividerManager.getInstance().getSelectedDivider().copy();
		Log.info("ModularityDetector", "Dividing community " + communityName + " using " + divider.getName());
		
		// set currentSubcommunityS_
		divider.setCurrentCommunityName(communityName);
		divider.divide(this);
		
		// compute the modularity of the division defined by currentSubcommunityS_
		currentSubcommunityQ_ = computeModularity();

		if (settings.getUseMovingVertex()) {
			movingVertexMethod();
		}
	}
	
	// ----------------------------------------------------------------------------
	
	/**
	 * Computes the modularity Q according to the appartenance vector s and the matrix B.<br>
	 * If B is the generalised modularity matrix, the returned Q value is actually delta Q.<br>
	 * If B is the modularity matrix, the returned Q value is the modularity Q. 
	 */
	private double computeQ(DenseVector s, UpperSymmDenseMatrix B, int m) throws Exception, JmodException {
		
		if (canceled_)
			throw new JmodException(CANCEL_MESSAGE);
		
		numModularityEvaluations_++;
		int size = B.numRows();
		double sum = 0;
		
		// the diagonal is multiplied by 0.5, because all other elements are counted
		// only once instead of twice, and in the return statement below we divide
		// only by 2m instead of 4m
		for (int i = 0; i < size; i++)
			sum += 0.5 * B.get(i, i) * s.get(i) * s.get(i);
			
		for (int i = 0; i < size; i++)
			for (int j = 0; j < i; j++)
				sum += B.get(i, j) * s.get(i) * s.get(j);
		
		return sum/(2. * m);
	}
	
	// ----------------------------------------------------------------------------
	
	/**  Returns true if all the vertices have been put in a single community after division. */
	private boolean allVerticesInSameCommunity() throws JmodException, Exception {
		
		int positives = 0;
		for (int i = 0; i < currentSubcommunitySize_; i++) {
			if (currentSubcommunityS_.get(i) > 0)
				positives++;
		}
		return (positives == currentSubcommunitySize_ || currentSubcommunitySize_ == 0);
	}
	
	// ============================================================================
	// PUBLIC METHODS
	
    /** Default constructor. */
	public ModularityDetector() {
		
		initialize();
	}

	// ----------------------------------------------------------------------------
	
	/** Constructor. */
	public ModularityDetector(final JmodNetwork network) throws Exception {
		
		initialize();
		setNetwork(network);
	}

	// ----------------------------------------------------------------------------

	/** Initialization of instance variables. */
	public void initialize() {
		
		modularity_ = 0;
		numModularityEvaluations_ = 0;
		contributionGlobalMvm_ = 0;
		
		network_ = null;
		A_ = null;
		k_ = null;
		m_ = 0;	
		networkSize_ = 0;
		
		currentSubcommunitySize_ = 0;
		currentSubcommunityQ_ = 0;
		
		canceled_ = false;
	}
	
	// ----------------------------------------------------------------------------

	/** Initializes network properties used to compute modularities (networkSize_, m_, A_, and k_). */
	public void setNetwork(final JmodNetwork network) throws Exception {
		
		network_ = network;
		networkSize_ = network.getSize();
		network.initializeModularityDetectionVariables();
		m_ = network.getM();
		A_ = network.getA();
		k_ = network.getK();
		computeModularityMatrix();
	}

	// ----------------------------------------------------------------------------
	
	/** Clear method. */
	public void clear() {
		
		initialize();
	}
	
	// ----------------------------------------------------------------------------

	/**
	 * Divides the current community into two subcommunities and update the modularity.
	 * If the current community is indivisible, return false.
	 */
	public boolean subdivisionInTwoCommunities(Community community) throws Exception, JmodException {
		
		if (canceled_)
			throw new JmodException(CANCEL_MESSAGE);
		
		// saves reference to current community being split
		currentCommunityBeingSplit_ = community;
		divide(currentCommunityBeingSplit_.getName());

		if (currentSubcommunityQ_ < 1e-12) {
			Log.debug("ModularityDetector", "Q < 1e-12 (" + currentSubcommunityQ_ + ")");
			return false;
		}
		if (allVerticesInSameCommunity()) {
			Log.debug("ModularityDetector", "Optimal division is to put all vertices in the same community.");
			return false;
		}

		if (isFirstDivision())
			modularity_ = currentSubcommunityQ_; // set the modularity of the root community
		else 
			modularity_ += currentSubcommunityQ_; // add delta Q to the modularity Q
		
		return true;
	}

	// ----------------------------------------------------------------------------

	/**
	 * Computes the modularity Q according to the appartenance vector (currentSubcommunityS_).
	 * For the first division the modularity Q is returned, for further subdivisions delta Q.
	 */
	public double computeModularity() throws Exception, JmodException {
		
		if (canceled_)
			throw new JmodException(CANCEL_MESSAGE);
		
		// compute the corresponding modularity
		if (isFirstDivision()) // first division (root community)
			return computeQ(currentSubcommunityS_, modularityMatrix_, m_);
		else
			return computeQ(currentSubcommunityS_, currentSubcommunityB_, m_);
	}
	
	// ----------------------------------------------------------------------------
	
	/** Idem than computeModularity() but using the given vector s instead of currentSubcommunityS_. */
	public double computeModularity(DenseVector s) throws Exception, JmodException {
		
		if (canceled_)
			throw new JmodException(CANCEL_MESSAGE);
		
		// compute the corresponding modularity
		if (isFirstDivision()) // first division (root community)
			return computeQ(s, modularityMatrix_, m_);
		else
			return computeQ(s, currentSubcommunityB_, m_);
	}
	
	// ----------------------------------------------------------------------------
	
//	/**
//	 * Computes the modularity of the network for the given community structure.
//	 * The community structure can be a cover, but only the first occurrence of a
//	 * node will be considered as it was a partition. The network is repeatedly
//	 * split in two COMMUNITIES to compute the modularity Q. During the first
//	 * round, the first community of the partition is the first COMMUNITY and all
//	 * the others form the second COMMUNITY. During the second round, the second
//	 * COMMUNITY of the previous round is split in two with one community on one
//	 * side and all the others on the other side. This process ends when the second
//	 * COMMUNITY is a single community of the partition.
//	 */
//	public double computeModularity(final CommunityStructure partition) throws Exception {
//		
//		if (network_ == null)
//			throw new Exception("Network is null.");
//		
//		// describes the community structure using integers instead of node names
//		// first generate a table to map node names and indexes
//		Map<String,Integer> map = network_.getNodeNamesMap();
//		// translate community structure
//		List<List<Integer>> intPartition = partition.getCommunityStructure(map);
//		
//		// B and other variables are initialized when network is set
//		modularity_ = 0.;
//		DenseVector vertexIndexes = null;
//		for (int i = 0; i < intPartition.size()-1; i++) {
//			if (i > 0)
//				computeGeneralisedModularityMatrix(vertexIndexes);
//			
//			currentSubcommunityS_ = new DenseVector(currentSubcommunitySize_);
//			assign(currentSubcommunityS_, 1);
//			
//			// get the indexes of all the nodes that are in the first and second communities
//			// nodes of the first community (i == j) get value = -1
//			// nodes of the second community get value = 1
//			Map<Integer, Integer> currentNodes = new TreeMap<Integer, Integer>();
//			for (int j = i; j < intPartition.size(); j++) {
//				for (Integer id : intPartition.get(j))
//				currentNodes.put(id, i == j ? -1 : 1);
//				
//				// TODO does the change works ?
////				Set<Integer> nodeIds = partition.getCommunity(j).getNodeIds();
////				for (Integer nodeIdInCom : nodeIds)
////					currentNodes.put(nodeIdInCom, i == j ? -1 : 1);
//			}
//			
//			// set split vector s
//			int index = 0;
//			for(Map.Entry<Integer, Integer> entry : currentNodes.entrySet())
//				currentSubcommunityS_.set(index++, entry.getValue());
//			
//			// set vertex indexes to consider for the next round
//			vertexIndexes = new DenseVector(currentSubcommunitySize_ - partition.getCommunity(i).size());
//			
//			index = 0;
//			for(Map.Entry<Integer, Integer> entry : currentNodes.entrySet()) {
//				if (entry.getValue() == 1)
//					vertexIndexes.set(index++, entry.getKey());
//			}
//			
//			// compute modularity
//			modularity_ += computeModularity();
//			currentSubcommunitySize_ -= partition.getCommunity(i).size();
//		}
//		
//		return modularity_;
//	}

	// ----------------------------------------------------------------------------

	/** Initializes the modularity matrix B of the network (modularityMatrix_). */
	public void computeModularityMatrix() throws Exception, JmodException {
		
		if (canceled_)
			throw new JmodException(CANCEL_MESSAGE);
		
		modularityMatrix_ = new UpperSymmDenseMatrix(networkSize_);
		
		// calculating modularity matrix B
		boolean weighted = network_.isWeighted();
		double weight = 1.;
		for (int i = 0; i < networkSize_; i++) {
			for (int j = 0; j <= i; j++) {
				int a_ij = 0;
				if (A_[i][j]) {
					a_ij = 1;
					if (weighted)
						weight = network_.getWeight(i, j);
				} // otherwise we don't care about the weight since a_ij == 0
				
				double element_ij = a_ij*weight - ((k_[i]*k_[j])/(2.*(double)m_));
				modularityMatrix_.set(j, i, element_ij); // no need to set lower part (UpperSymmDenseMatrix)
			}
		}
		currentSubcommunityB_ = modularityMatrix_;
		currentSubcommunitySize_ = networkSize_;
	}
	
	// ----------------------------------------------------------------------------

	/** Initializes the generalized modularity matrix B of the current subcommunity. */
	public void computeGeneralisedModularityMatrix(DenseVector vertexIndexes) throws Exception, JmodException {
		
		if (canceled_)
			throw new JmodException(CANCEL_MESSAGE);
		
		currentSubcommunitySize_ = vertexIndexes.size();
		currentSubcommunityB_ = new UpperSymmDenseMatrix(currentSubcommunitySize_);
				
		for (int i = 0; i < currentSubcommunitySize_; i++) {
			int indexI = (int) vertexIndexes.get(i);
			double sum = 0.0;
			for (int k = 0; k < currentSubcommunitySize_; k++) {
				int indexK = (int) vertexIndexes.get(k);
				sum += modularityMatrix_.get(indexI, indexK);
			}
			currentSubcommunityB_.set(i, i, modularityMatrix_.get(indexI, indexI) - sum);
		}
			
		for (int i = 0; i < currentSubcommunitySize_; i++) {
			int indexI = (int) vertexIndexes.get(i);
				
			for (int j = 0; j < i; j++) {
				int indexJ = (int) vertexIndexes.get(j);
//				currentSubcommunityB_.set(i, j, modularityMatrix_.get(indexI, indexJ)); // not since UpperSymmDenseMatrix
				currentSubcommunityB_.set(j, i, modularityMatrix_.get(indexI, indexJ));
			}
		}
	}

	// ----------------------------------------------------------------------------

	/**
	 * Computes the modularity of a network according to the division into multiple
	 * communities as defined by the appartenance vector s
	 * (we divide the diagonal by 2 and do only one triangle of the matrix, so we
	 * don't divide by 2*m in the end).
	 */
	public double multipleCommunitiesComputeQ(DenseVector s) throws Exception, JmodException {
		
		if (canceled_)
			throw new JmodException(CANCEL_MESSAGE);
		
		double Q = 0;
		int size = s.size();
		
		for (int i = 0; i < size; i++)
			Q += modularityMatrix_.get(i, i);
		Q /= 2;
		
		for (int i = 0; i < size; i++) {
			for (int j = 0; j < i; j++) {
				// if the vertices are in the same community
				if (s.get(i) == s.get(j))
					Q += modularityMatrix_.get(i, j);
			}
		}
		return Q / m_; //(2. * m_);
	}
	
	// ----------------------------------------------------------------------------

	/** An efficient implementation of the moving vertex method described in Newman2006. */
	public void movingVertexMethod() throws Exception, JmodException {
		
		if (canceled_)
			throw new JmodException(CANCEL_MESSAGE);
		
		double currentDeltaQ = currentSubcommunityQ_;
		
		Log.debug("ModularityDetector", "Running moving vertex method (MVM)");
		boolean firstDivision = isFirstDivision();
		if (firstDivision)
			Log.debug("ModularityDetector", "Q of input sVector = " + currentDeltaQ);
		else
			Log.debug("ModularityDetector", "DeltaQ of input sVector = " + currentDeltaQ);
		
		int iter = 0, index = 0;
		double tempDeltaQ = 0;
		boolean change = true;
		
		// break the loop if there are no more amelioration
		int maxIters = Integer.MAX_VALUE;
		while (change && iter < maxIters) {
			change = false;
			index = 0;

			// TODO do this only for the vertices at the boarder of the two communities (i.e., the ones
			// that have a connection to the other community)
			for (int i=0; i<currentSubcommunitySize_; i++) {
				
				// I checked this and it gives the same results as when computing the modularity
				// with computeQ() for both cases
				//if (firstDivision)
					//tempDeltaQ = computeMovingVertexQ(i);
				//else
					tempDeltaQ = computeMovingVertexQ(i);
							
				if ((tempDeltaQ - currentDeltaQ) > 1e-12) { // to avoid spending thousands of iterations for nothing
					currentDeltaQ = tempDeltaQ;
					// Save the index of the amelioration
					index = i;
					change = true;
				}
			}
			
			if (change) {
				currentSubcommunityS_.set(index, -1 * currentSubcommunityS_.get(index));
				currentSubcommunityQ_ = currentDeltaQ;
				Log.debug("ModularityDetector", "Iter " + iter + ", index " + index + ": New Q = " + currentDeltaQ);
				takeSnapshot("MVM");
			}
			iter++;
		}
	}
	
	// ----------------------------------------------------------------------------

	/**
	 * Computes the new modularity when moving only movedVertex to the other community.
	 * This is done efficiently by only looking at the relevant row and column of
	 * the modularity matrix for this vertex. This function can be used for the root
	 * community and also for subcommunities.
	 */
	public double computeMovingVertexQ(int movedVertex) throws Exception, JmodException {
		
		if (canceled_)
			throw new JmodException(CANCEL_MESSAGE);
		
		numModularityEvaluations_++;
		
		double sum = 0;
		for (int i = 0; i < currentSubcommunitySize_; i++)
			sum += currentSubcommunityB_.get(i, movedVertex) * currentSubcommunityS_.get(i) * currentSubcommunityS_.get(movedVertex);
		
		sum -= currentSubcommunityB_.get(movedVertex, movedVertex);
		
		return currentSubcommunityQ_ - sum/m_;
	}

	// ----------------------------------------------------------------------------

	/**
	 * The global moving vertex method (gMVM). Like MVM, but over multiple communities defined
	 * by the appartenance vector s.
	 */
	public void globalMovingVertexMethod(DenseVector appartenanceVector, int numCommunities) throws Exception, JmodException {
		
		if (canceled_)
			throw new JmodException(CANCEL_MESSAGE);
		
		int iter = 0, changeIndex = -1, changeNewCommunity = -1;
		double tmpNewQ = -1;
		boolean change = false;
		
		currentSubcommunityS_ = appartenanceVector;
		currentSubcommunitySize_ = appartenanceVector.size();
		currentSubcommunityQ_ = multipleCommunitiesComputeQ(currentSubcommunityS_);
		double initQ = currentSubcommunityQ_;
		double newQ = currentSubcommunityQ_;
		
		Log.debug("ModularityDetector", "Running global moving vertex method (gMVM)");
		Log.debug("ModularityDetector", "Q of input sVector = " + currentSubcommunityQ_);
		
		// break the loop if there is no more amelioration
		do {
			if (change) {
				currentSubcommunityS_.set(changeIndex, changeNewCommunity);
				currentSubcommunityQ_ = newQ;
				Log.debug("ModularityDetector", "Iter " + iter + ", index " + changeIndex + ": New Q = " + newQ);
				takeSnapshotGlobalMovingVertexMethod(changeIndex, changeNewCommunity);
			}
			change = false;
			changeIndex = -1;
			changeNewCommunity = -1;

			// loop over all vertices
			for (int i=0; i<currentSubcommunitySize_; i++) {
				// remember the previous community
				int oldCommunity = (int)currentSubcommunityS_.get(i);
				
				// Sum of row/col i (modularity matrix is symmetric, used for efficient computation of modularity below)
				double rowSum_i = 0;
				for (int j=0; j<currentSubcommunitySize_; j++)
					// if the vertices are in the same community
					if (currentSubcommunityS_.get(i) == currentSubcommunityS_.get(j) && i != j) 
						rowSum_i += modularityMatrix_.get(i, j);
				//rowSum_i -= 0.5*modularityMatrix_.get(i, i);
				
				// loop over all communities
				for (int k=0; k<numCommunities; k++) {
					// if that's the current community of the vertex, continue
					if (k == oldCommunity)
						continue;
					
					// Move vertex
					//currentSubcommunityS_.set(i, k);
					
					// That's the naive inefficient way of doing it
					//tmpNewQ = multipleCommunitiesComputeQ(currentSubcommunityS_);
					// Instead of recomputing the whole Q every time, only consider row/col that changed 
					tmpNewQ = computeGlobalMovingVertexQ(i, k, rowSum_i);
					
					if (tmpNewQ > newQ) {
						newQ = tmpNewQ;
						// Save the index of the amelioration
						changeIndex = i;
						changeNewCommunity = k;
						change = true;
					}
				}
				// restore the previous community (required??)
				currentSubcommunityS_.set(i, oldCommunity);
			}
			iter++;
		} while(change);
		
		modularity_ = newQ;
		contributionGlobalMvm_ = newQ - initQ;
	}

	// ----------------------------------------------------------------------------

	/**
	 * Computes the new modularity when moving only movedVertex to the given community.
	 * This is done efficiently by only looking at the relevant row and column of
	 * the modularity matrix for this vertex. 
	 */
	public double computeGlobalMovingVertexQ(int movedVertex, int newCommunity, double rowSum_i) throws Exception, JmodException {
		
		if (canceled_)
			throw new JmodException(CANCEL_MESSAGE);
		
		numModularityEvaluations_++;
		
		if (newCommunity == currentSubcommunityS_.get(movedVertex))
			new IllegalArgumentException("The specified new community is the same as the previous community in the s vector");
		
		// Compute the new rowSum
		double newRowSum_i = 0;
		for (int j=0; j<currentSubcommunitySize_; j++)
			// if the vertices are in the same community
			if (newCommunity == currentSubcommunityS_.get(j)) 
				newRowSum_i += modularityMatrix_.get(movedVertex, j);
		
		// Note, the diagonal element was not counted because we tested that 
		// the newCommunity is different from the one in the s vector above
		
		return currentSubcommunityQ_ + (newRowSum_i - rowSum_i)/m_;
	}
	
	// ----------------------------------------------------------------------------

	/** Returns true if this is the first division of the network (the current "subcommunity" is the complete network). */
	public boolean isFirstDivision() {
		
		return (currentSubcommunitySize_ == networkSize_);
	}
	
	// ----------------------------------------------------------------------------
	
	/** Takes snapshot of the current split of the network. */
	public void takeSnapshot(String suffix) {
		
		try {
			if (snapshot_ != null)
				snapshot_.takeSnapshot(suffix);
		} catch (Exception e) {
			Log.debug("ERROR: Unable to take modularity detection snapshot: " + e.getMessage());
		}
	}
	
	// ----------------------------------------------------------------------------
	
	/** Takes snapshot of the current state of gMVM. */
	public void takeSnapshotGlobalMovingVertexMethod(int nodeIndex, int newCommunityIndex) {
		
		try {
			if (snapshot_ != null)
				snapshot_.takeSnapshotGlobalNodeMove("gMVM", nodeIndex, newCommunityIndex);
		} catch (Exception e) {
			Log.debug("ERROR: Unable to take modularity detection snapshot: " + e.getMessage());
		}
	}
	
	// ----------------------------------------------------------------------------
	
	/** Sets all the values of the given vector to the given value. */
	public static void assign(DenseVector vector, double value) {
		
		Iterator<VectorEntry> it = vector.iterator();
		while(it.hasNext())
			it.next().set(value);
	}
	
	// ----------------------------------------------------------------------------
	
	/** Sets all the values of the given vector to random values in [min,max[. */
	// TODO Use a single random number generator in the project (Mersenne Twister)
	public static void assignRandomly(DenseVector vector, double min, double max) {
		
		Random rng = new Random();
		Iterator<VectorEntry> it = vector.iterator();
		while(it.hasNext())
			it.next().set(rng.nextDouble()*(max - min) + min); // r.nextInt(max - min + 1) + min;
	}
	
	// ----------------------------------------------------------------------------
	
	/** Sets all the values of the given vector to random values in [0.,1.[. */
	// TODO Use a single random number generator in the project (Mersenne Twister)
	public static void assignRandomly(DenseVector vector) {
		
		Random rng = new Random();
		Iterator<VectorEntry> it = vector.iterator();
		while(it.hasNext())
			it.next().set(rng.nextDouble());
	}
	
	// ============================================================================
	// GETTERS AND SETTERS
	
	public UpperSymmDenseMatrix getModularityMatrix() { return modularityMatrix_; }
	
	public void setModularity(double value) { modularity_ = value; }
	public double getModularity() { return modularity_; }
	
	public int getNumModularityEvaluations() { return numModularityEvaluations_; }
	
	public double getContributionGlobalMvm() { return contributionGlobalMvm_; }
	
	public void setCurrentSubcommunitySize(int size) { currentSubcommunitySize_ = size; }
	public int getCurrentSubcommunitySize() { return currentSubcommunitySize_; }
	
	public void setCurrentSubcommunityS(DenseVector s) { currentSubcommunityS_ = s; }
	public DenseVector getCurrentSubcommunityS() { return currentSubcommunityS_; }
	
	public void setCurrentSubcommunityQ(double q) { currentSubcommunityQ_ = q; }
	public double getCurrentSubcommunityQ() { return currentSubcommunityQ_; }
	
	public JmodNetwork getNetwork() { return network_; }
	
	/**
	 * The modularity detection will stop as soon as it reads the cancel flag.
	 * This flag is read at each stage of the modularity detection, so the current
	 * detection will not wait until the end of the process to leave.
	 */
	public void cancel() { canceled_ = true; }
	public boolean isCanceled() { return canceled_; }
	
	public void setOutputDirectory(URI directory) { outputDirectory_ = directory; }
	public URI getOutputDirectory() { return outputDirectory_; }
	
	public void setSnapshot(ModularityDetectorSnapshot snapshot) { snapshot_ = snapshot; }
	public ModularityDetectorSnapshot getSnapshot() { return snapshot_; }
}
