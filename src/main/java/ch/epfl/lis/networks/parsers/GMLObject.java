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

package ch.epfl.lis.networks.parsers;

import java.lang.String;
import java.lang.Double;
import java.util.Hashtable;

import ch.epfl.lis.networks.NetworkException;

import com.esotericsoftware.minlog.Log;

/**
 * A GML object that does nothing but create itself and write itself.<p>
 * 
 * Original description:<br>
 * Graphical Database for Category Theory<br>
 * J. Bradbury, Dr. R. Rosebrugh, I. Rutherford<br>
 * Mount Allison University 2001<p>
 * 
 * File:<br>
 * GMLobject.java<br>
 * (author: Larry Barowski, Auburn University, 10/25/96)<p>
 * 
 * Description:<br>
 * A GML object that does nothing but create itself and write itself.
 * 
 * @version 2008
 * 
 * @author Modified by Thomas Schaffter (thomas.schaff...@gmail.com)
 */
   public class GMLObject
      {
      public static final int GMLinteger = 0;
      public static final int GMLreal = 1;
      public static final int GMLstring = 2;
      public static final int GMLlist = 3;
      public static final int GMLundefined = 4;
      public static final int GMLfile = 5;
   
      protected String key_;
      protected Object value_;
      protected GMLObject next_;
      protected int type_;
   
      // State of iterator.
      private GMLObject searchobj_ = null;
      private String searchkey_ = null;
      private int searchtype_;
   
   
      public GMLObject()
         {
         key_ = "undefined";
         value_ = null;
         next_ = null;
         type_ = GMLundefined;
         }
   
   
   
      public GMLObject(String key, int type)
         {
         key_ = key;
         type_ = type;
         value_ = null;
         next_ = null;
         }
   
   
   
   
   
      /** Create the object from a GMLlexer and key. A null key will cause
       *  a GMLfile object to be created.
       **/
      public GMLObject(GMLLexer lexer, String key) throws Exception, NetworkException {
         next_ = null;
      
         key_ = key;
      
         int token;
      
      	// Parse the value.
         if(key != null)
            {
            token = lexer.nextToken();
            if(token == GMLLexer.GMLinteger)
               {
               type_ = GMLinteger;
               value_ = new Integer((int)(lexer.getDoubleval()));
               }
            else if(token == GMLLexer.GMLreal)
               {
               type_ = GMLreal;
               value_ = new Double(lexer.getDoubleval());
               }
            else if(token == GMLLexer.GMLstring)
               {
               type_ = GMLstring;
               value_ = lexer.getStringval();
               }
            else if(token == '[')
               {
               type_ = GMLlist;
               value_ = parseGMLlist_(lexer);
               if(lexer.getTokenType() != ']')
                  throw new NetworkException("Expecting GML key or \']\'");
               }
            else {
            	Log.debug("GMLObject", "Ignoring key " + key);
            }
        }
         else
            {
            type_ = GMLfile;
            value_ = parseGMLlist_(lexer);
            if(lexer.getTokenType() != GMLLexer.GMLeof)
               throw new NetworkException("Expecting GML key or end-of-file");
            }
         }
   
   
   
   
  

      private GMLObject parseGMLlist_(GMLLexer lexer) throws Exception {
         GMLObject retval = null, prevobj = null;
         @SuppressWarnings("unused")
		int token;
      
         while((token = lexer.nextToken()) == GMLLexer.GMLkey)
            {
            GMLObject newobj = new GMLObject(lexer, lexer.getStringval());
            if(prevobj == null)
               retval = newobj;
            else
               prevobj.next_ = newobj;
            prevobj = newobj;
            }
         return retval;
         }
   
   
   
   
   
   
      /** Print the text representation of the object tree, with "numtabs"
       *  leading tabs.
      **/
      public String toString(int numtabs)
         {
	 if(value_ == null)
	    return "";

         String retval = "";

         int i;
         for(i = 0; i < numtabs; i++)
            retval += "   ";
         if(key_ != null)  // For the top level "GMLfile" object, the key
			   // is null.
            retval += key_;
      
         if(type_ == GMLinteger)
            retval += " " + ((Integer)value_).toString() + "\n";
         else if(type_ == GMLreal)
            {
            // Use GML real format
            double val = ((Double)value_).doubleValue();
         
            if(val == Double.POSITIVE_INFINITY || val == Double.NaN)
               val = Double.MAX_VALUE;
            if(val == Double.NEGATIVE_INFINITY)
               val = Double.MIN_VALUE;
         
            String doublestring = Double.toString(val);
            if(doublestring.indexOf('.') == -1)
            		// No decimal.
               {
               String expstring = "";
               int expstart;
               if((expstart = doublestring.indexOf('e')) != -1)
               			// Exponent is present.
                  {
                  expstring = doublestring.substring(expstart);
                  doublestring = doublestring.substring(0, expstart);
                  }
               doublestring += ".0" + expstring;
               }
            retval += " " + doublestring + "\n";
            }
         else if(type_ == GMLstring) {
	    if(value_ == null)
	       retval += " \"\"\n";
	    else
               retval += " \"" + ((String)value_) + "\"\n";
	    }
         else if(type_ == GMLlist)
         	// List
            {
            retval += " [\n";//]\n";
         
            GMLObject obj;
            for(obj = (GMLObject)value_; obj != null; obj = obj.next_)
	       if(obj.value_ != null)
                  retval += obj.toString(numtabs + 1);
         
            for(i = 0; i < numtabs; i++)
               retval += "   ";
            retval += "]\n";
            }
         else // File
            {
            GMLObject obj;
            for(obj = (GMLObject)value_; obj != null; obj = obj.next_)
               retval += obj.toString(numtabs);
            }
         return retval;
         }
   
   
   
   
   
   
   
   
      /** Iterator initialized by getGMLSubObject().
       *
       *  Returns the next sub-object matching the path-key and type specified
       *  in a previous call to getGMLSubObject(), or null if no such object
       *  exists.
      **/
      public GMLObject getNextGMLSubObject()
         {
         while(searchobj_ != null && searchobj_.key_ != null &&
	 !(searchobj_.key_.equals(searchkey_) &&
         (searchobj_.type_ == searchtype_ ||
         (searchobj_.type_ == GMLinteger && searchtype_ == GMLreal))))
            searchobj_ = searchobj_.next_;
      
         if(searchobj_ != null && searchobj_.type_ != searchtype_)
            {
         	// Promote the integer to a real.
            searchobj_.type_ = GMLreal;
            searchobj_.value_ = new
		Double((double)((Integer)searchobj_.value_).intValue());
            }
      
         GMLObject retval = searchobj_;
         if(searchobj_ != null)
            searchobj_ = searchobj_.next_;
         return retval;
         }
   


   
      /** Get the first sub-object with path-key "path" and type "type",
       *  or null if no such object exists.
       *
       *  If "create" is true, create the sub-object if it doesn't exist.
       *
       *  If type is GMLreal and a matching GMLinteger is found, the integer
       *  object will be changed to a real.
       *
       *  This function begins an iteration sequence that can continue
       *  with calls to getNextGMLSubObject().
      **/
      public GMLObject getGMLSubObject(String path, int type, boolean create)
         {
         int start, end;
         GMLObject currentobj, obj;
      
         searchobj_ = null;
         currentobj = this;
         start = 0;
      
         String key;
      
         // Should not call this function if object type is not list.
         if(currentobj.type_ != GMLlist && currentobj.type_ != GMLfile)
            return null;
      
         do
            {
            end = path.indexOf('.', start);
            if(end >= 0)
               key = path.substring(start, end);
            else
               key = path.substring(start);
         
            for(obj = (GMLObject)(currentobj.value_); obj != null;
			obj = obj.next_)
               if(obj.key_.equals(key) &&
               ((end >= 0 && (obj.type_ == GMLlist || obj.type_ == GMLfile)) ||
               (end == -1 && (obj.type_ == type ||
               (obj.type_ == GMLinteger && type == GMLreal)))))
                  break;
         
            if(obj == null)
               {
               if(!create)
                  return null;  // Key not found.
               else  // Create key.
                  {
                  obj = new GMLObject(key, GMLlist);
                  if(end == -1)  // Creating end object.
                     obj.type_ = type;
               
               			// Add to front of list;
                  obj.next_ = (GMLObject)currentobj.value_;
                  currentobj.value_ = obj;
                  }
               }
            else if(end == -1 && type != obj.type_)
               {
            	// Promote the integer to a real.
               obj.type_ = GMLreal;
               obj.value_ = new
		 Double((double)((Integer)obj.value_).intValue());
               }
         
            currentobj = obj;
         
            start = end + 1;
            }
         while(start != 0);
      
         searchobj_ = currentobj.next_;
         searchkey_ = key;
         searchtype_ = type;
         return currentobj;
         }
   
   




      /** Insert the keys and values of the first sub-object with path-key
       *  "path" and type "type", into the hash table "hash".
       *
       *  If type is GMLreal and a matching GMLinteger is found, the integer
       *  object will be changed to a real.
      **/
      @SuppressWarnings("unchecked")
	public void setHashFromGML(String path, int type, @SuppressWarnings("rawtypes") Hashtable hash)
      {
         // Should not call this function if object type is not list.
         if(type_ != GMLlist && type_ != GMLfile)
            return;
      
         GMLObject list = getGMLSubObject(path, GMLlist, false);
         if(list == null)
            return;

         GMLObject tmp;
         for(tmp = (GMLObject)list.value_; tmp != null; tmp = tmp.next_) {
            if(type == tmp.type_ ||
		(type == GMLreal && tmp.type_ == GMLinteger)) {
               hash.put(tmp.key_, tmp.value_);
            }
         }
      }





      /** Get the value of the first sub object matching the path-key "path"
       *  and type "type", or null if there is no sub object.
      **/   
      public Object getValue(String path, int type)
         {
         GMLObject obj = getGMLSubObject(path, type, false);
         if(obj == null)
            return null;
      
         return obj.value_;
         }

   
   // Do not call from a non-list object, or null pointer exception will result.
      public void setValue(String path, int type, Object value)
      {
         GMLObject obj = getGMLSubObject(path, type, true);
         obj.value_ = value;
      }
   
   

   
   // Do not call from a non-list object.
      public void addObject(GMLObject object)
         {
         if(type_ != GMLlist)
            return;
         object.next_ = (GMLObject)value_;
         value_ = object;
         }
   
   
   
   // Do not call from a non-list object.
      public void addObjectToEnd(GMLObject object)
         {
         if(type_ != GMLlist && type_ != GMLfile)
            return;
         GMLObject obj;
         for(obj = (GMLObject)value_; obj != null && obj.next_ != null; obj = obj.next_)
            ;
         if(obj == null)
            value_ = object;
         else
            obj.next_ = object;
         }
   
   
   
   
   // Delete all unsafe sub objects (hierarchical) except those with key "vgj".
      public void deleteUnsafe()
         {
         if(type_ != GMLlist && type_ != GMLfile)
            return;
         GMLObject object = (GMLObject)value_, prevobject = null;
      
         while(object != null)
            {
            int chr;
            if(!object.key_.equals("vgj") && (chr = object.key_.charAt(0)) >= 'A' && chr <= 'Z')
               {
               object = object.next_;
               if(prevobject != null)
                  prevobject.next_ = object;
               else
                  value_ = object;
               }
            else
               {
               object.deleteUnsafe();
               prevobject = object;
               object = object.next_;
               }
            }
         }
   
   
   
   
   
      public void deleteAll(String key, int type)
         {
         GMLObject object = (GMLObject)value_, prevobject = null;
      
         while(object != null)
            {
            if(object.key_.equals(key) && object.type_ == type)
               {
               object = object.next_;
               if(prevobject != null)
                  prevobject.next_ = object;
               else
                  value_ = object;
            
               }
            else
               {
               prevobject = object;
               object = object.next_;
               }
            }
         }
   
     



      /** Remove all null GMLlists from the object tree.
      **/
      public void prune()
      {
         GMLObject obj, prevobj;

	 if(type_ != GMLlist)
	    return;

         for(obj = (GMLObject)(value_); obj != null; obj = obj.next_)
	    obj.prune();

         prevobj = null;
         for(obj = (GMLObject)(value_); obj != null; obj = obj.next_) {
	    if(obj.value_ == null) {
	       if(prevobj != null)
		  prevobj.next_ = obj.next_;
	       else
		  value_ = obj.next_;
	    }
	    else
	       prevobj = obj;
	 }
  }
}
