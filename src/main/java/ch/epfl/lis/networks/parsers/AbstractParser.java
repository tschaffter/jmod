package ch.epfl.lis.networks.parsers;

/**
 * Abstract class extended by network file parsers.
 * 
 * @version November 7, 2013
 * 
 * @author Thomas Schaffter (thomas.schaff...@gmail.com)
 */
public abstract class AbstractParser<N> {

	/** If true, saves the edge weight to file. */
	protected boolean saveEdgeWeight_ = true;
	
	/** If true, saves graphic information. */
	protected boolean saveGraphics_ = false;
	
//	/** A map used to keep track of the nodes created. */
//	protected Map<String,N> nodes_ = null;
	
	// ============================================================================
	// PUBLIC METHODS
	
	/** Default constructor. */
	public AbstractParser() {
		
//		nodes_ = new TreeMap<String,N>();
	}
	
	// ============================================================================
	// GETTERS AND SETTERS
	
	public void saveEdgeWeight(boolean b) { saveEdgeWeight_ = b; }
	public void saveGraphics(boolean b) { saveGraphics_ = b; }
}