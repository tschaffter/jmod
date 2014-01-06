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

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.concurrent.Callable;

import com.esotericsoftware.minlog.Log;

/**
 * Converts a single dot file using one of the system Graphviz commands in a dedicated thread.
 * 
 * @version June 24, 2013
 * 
 * @author Thomas Schaffter (firstname.name@gmail.com)
 */
public class GraphvizWorker implements Callable<Void> {
	
	/** Thread identifier. */
	protected int threadId_ = 0;
	
	/** Graphviz tool (default: neato). */
	protected String tool_ = "neato";
//	/** Rendered defines the output format (default: -Tpng). */
//	protected String renderer_ = "-Tpng";
	/** Output format (e.g. png, svg, etc.). */
	protected String outputFormat_ = "png";
	
	/** Dot filename. */
	protected String dotFilename_ = null;
	
	// ============================================================================
	// PUBLIC METHODS
	
	/** Constructor. */
	public GraphvizWorker(int threadId, String dotFilename) {
		
		threadId_ = threadId;
		dotFilename_ = dotFilename;
	}
	
	// ----------------------------------------------------------------------------
	
	/** Constructor */
	public GraphvizWorker(int threadId, String tool, String outputFormat, String dotFilename) {
		
		threadId_ = threadId;
		tool_ = tool;
		outputFormat_ = outputFormat;
//		renderer_ = renderer;
		dotFilename_ = dotFilename;
	}
	
	// ----------------------------------------------------------------------------
	
	/** This is the method that will be executed when the thread is running. */
	@Override
	public Void call() throws Exception {
		
		try {
			Log.info("Running " + tool_ + " on " + dotFilename_);
			
			// output filename
			String outputFilename = dotFilename_;
			if (outputFilename.endsWith(".dot"))
				outputFilename = outputFilename.substring(0, outputFilename.length()-3) + outputFormat_;
			
			// -O saves output file as plop.dot.X where X is given by the renderer
			ProcessBuilder processBuilder = new ProcessBuilder(tool_, "-T" + outputFormat_, dotFilename_, "-o" + outputFilename); // "-O", dotFilename_
			processBuilder.redirectErrorStream(true); // merges error output to normal stream
			Process process = processBuilder.start();
			
			// logger (displays nothing if the run is fine)
			new Thread(new RunLogger(process.getInputStream())).run();
			
			int exitValue = -1;
			if ((exitValue = process.waitFor()) != 0)
				throw new Exception(tool_ + " returned " + exitValue);
			
		} catch (Exception e) {
			throw new Exception("ERROR: Unable to run " + tool_ + " on " + dotFilename_ +  ": " + e.getMessage());
		}
	
		return null;
	}
	
	// ============================================================================
	// INNER CLASSES
	
	/** Logs the stream from ProcessBuilder. */
	private class RunLogger implements Runnable {
		
		/** ProcessBuilder stream to log. */
		private InputStream stream_ = null;
		
		/** Constructor. */
		public RunLogger(InputStream stream) {
			
			stream_ = stream;
		}
		
		// ----------------------------------------------------------------------------
		
		/**
		 * Run method.
		 * IMPORTANT: neato and most likely other Graphviz tools returns output to
		 * the stdout if there is no output option specified. In that case, the logger
		 * would be stuck at reader.readLine() as the output is binary data.
		 */
		public void run() {
			
			BufferedReader reader = null;
			try {
				reader = new BufferedReader(new InputStreamReader(stream_));
				String line = "";
				while ((line = reader.readLine()) != null)
					Log.info(line);
			} catch (Exception e) {
				Log.error("Graphviz process failed.");
			} finally {
				try {
					reader.close();
				} catch (Exception e) {
					// ignore
				}
			}
		}
	}
}
