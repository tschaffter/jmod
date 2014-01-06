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

package ch.epfl.lis.jmod.graphviz;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.apache.commons.io.FilenameUtils;

import ch.epfl.lis.jmod.JmodException;
import ch.epfl.lis.jmod.batch.NetworkFilenameFilter;

import com.esotericsoftware.minlog.Log;

/**
 * Converts a batch of dot files in parallel using one of the system Graphviz commands.
 * 
 * @version June 24, 2013
 * 
 * @author Thomas Schaffter (firstname.name@gmail.com)
 */
public class GraphvizBatchConverter {
	
	/** Root filename of the DOT files including the path of the directory. */
	protected String dotRootFilename_ = null;
	
	/** Dot directory obtained from dot root filenames. */
	protected String dotDirectory_ = null;
	/** DOT base filenames (without directory). */
	protected List<String> dotBaseNames_ = null;
	
	/** Graphviz tool to run. */
	protected String graphvizTool_ = "neato";
	/** Graphviz output format. */
	protected String graphvizFormat_ = "png";
	
	// ============================================================================
	// PROTECTED METHODS
	
	/** Returns the name of the dot files without the directory. */
	protected List<String> getDotBaseNames(String snapshotRootFilename) throws Exception {
		
		String regex = snapshotRootFilename + "*.dot";
		
		// extract the directory string from the input regex
		String directory = FilenameUtils.getFullPath(regex);
		File directoryFile = new File(directory);
		if (directory.length() == 0)
			directoryFile = new File(".");

		if (directoryFile == null || !directoryFile.isDirectory())
			throw new JmodException("The directory " + directory + " is not valid and dot files can't be found.");
		
		// list network files matching the input regex
		// TODO: check next update
		String truncatedRegex = FilenameUtils.getName(regex);
//		String truncatedRegex = FileUtils.getFilenameString(regex, JmodSettings.FILE_SEPARATOR);
		
		String[] children = directoryFile.list(new NetworkFilenameFilter(truncatedRegex));
		Arrays.sort(children);
		
		List<String> dots = new ArrayList<String>();
		for (int i = 0; i < children.length; i++)
			dots.add(children[i]);

		return dots;
	}
	
	// ----------------------------------------------------------------------------
	
	/** Converts the given dot file (extension must include the dot). */
	protected void convertDots() throws Exception {
		
		// pool size = number of threads
		int numConcurrentThreads = Runtime.getRuntime().availableProcessors();
		
		// use a thread pool to limit the number of dot graph processed at a time
		ExecutorService executor = Executors.newFixedThreadPool(numConcurrentThreads);
		List<GraphvizWorker> workers = new ArrayList<GraphvizWorker>();
		List<Future<Void>> results = null; // references to the results
		
		// create workers
		GraphvizWorker worker = null;
		for (int i = 0; i < dotBaseNames_.size(); i++) {
			worker = new GraphvizWorker(i, graphvizTool_, graphvizFormat_ , dotDirectory_ + dotBaseNames_.get(i));
			workers.add(worker);
		}	
		
		try {
			// perform the workers
			results = executor.invokeAll(workers);
			// wait until all workers are done
			for (Future<Void> result: results)
				result.get();
		} catch (Exception e) {
			throw e;
		} finally {
			executor = null;
			workers = null;
			results = null;
		}
	}
	
	// ============================================================================
	// PUBLIC METHODS
	
	/** Default constructor. */
	public GraphvizBatchConverter() {}
	
	// ----------------------------------------------------------------------------
	
	/** Run method. */
	public void run() throws Exception {
		
		// get dot URI
		dotBaseNames_ = getDotBaseNames(dotRootFilename_);
		dotDirectory_ = FilenameUtils.getFullPath(dotRootFilename_);
		
		convertDots();
	}
	
	// ----------------------------------------------------------------------------
	
	/** Main method. */
	public static void main(String args[]) {
		
		try {
			GraphvizBatchConverter converter = new GraphvizBatchConverter();
			converter.setDotRootFilename("/home/tschaffter/jmod_ga_animation/karate_movie/BF_multi/snapshot_networks/karate_");
			
			converter.run();
			
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			Log.info("Done");
			System.exit(0);
		}
	}
	
	// ============================================================================
	// GETTERS AND SETTERS
	
	public void setDotRootFilename(String filename) { dotRootFilename_ = filename; }
}
