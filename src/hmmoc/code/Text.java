/*
 *    This file is part of HMMoC 1.3, a hidden Markov model compiler.
 *    Copyright (C) 2007 by Gerton Lunter, Oxford University.
 *
 *    HMMoC is free software; you can redistribute it and/or modify
 *    it under the terms of the GNU General Public License as published by
 *    the Free Software Foundation; either version 2 of the License, or
 *    (at your option) any later version.
 *
 *    HMMOC is distributed in the hope that it will be useful,
 *    but WITHOUT ANY WARRANTY; without even the implied warranty of
 *    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *    GNU General Public License for more details.
 *
 *    You should have received a copy of the GNU General Public License
 *    along with HMMoC; if not, write to the Free Software
 *    Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
\*/
package hmmoc.code;


import java.util.*;
import hmmoc.xml.Code;


public class Text {

    // Implements a finished piece of code, and its relationship to other pieces.

    StringBuffer text;
    Text parent = null;
    int depth;
    boolean line = true;

    
    public Text( String s ) {
    	text = new StringBuffer(s);
    }

    
    public Text( String s, Text t ) {
    	text = new StringBuffer(s);
    	parent = t;
    	depth = t.depth;
    }

    
    public void append( String s ) {
    	text.append( s );
    }

    
    public void append( Text t ) {
    	text.append( t.toString() );
    	if (t.parent != null) {
    		throw new Error("Text: appending text that is already in book");
    	}
    	line |= t.line;
    }


    public void prepend( Text t ) {
    	text.replace(0,0,t.toString() );
    	if (t.parent != null) {
    		throw new Error("Text: prepending text that is already in book");
    	}
    }


    public void setLine( boolean isLine ) {
    	line = isLine;
    }


    public void changeDepth( int i ) {
    	depth += i;
    }


    public int deltaDepth() {

    	return deltaDepth( null, null );

    }


    int deltaDepth( List lines, List deltas ) {
    	// Parses text, and counts number of {s and }s.
    	String t = toString();
    	int delta = 0;
    	boolean isWhiting = true;
    	boolean inComment = false;
    	boolean inLineComment = false;
    	boolean inSQuote = false;
    	boolean inDQuote = false;
    	boolean slashed = false;
    	boolean asterisked = false;
    	StringBuffer line = new StringBuffer("");
	
    	int i=0;
	int tlength = t.length();

    	while (i<tlength) {

    		char c = t.charAt(i);

    		if (inLineComment) {
    			if (c=='\n') {
    				inLineComment = false;
    				isWhiting = true;
    			}
    		} else if (inComment) {
    			if (c=='*')
    				asterisked = true;
    			else if (asterisked) {
    				if (c=='/')
    					inComment = false;
    				else 
    					asterisked = false;
    			}
    		} else if (inSQuote) {
    			if (c=='\\') {
    				slashed = true;
    			} else if (slashed) {
    				slashed = false;
    			} else {
    				if (c=='\'')
    					inSQuote = false;
    			}
    		} else if (inDQuote) {
    			if (c=='\\') {
    				slashed = true;
    			} else if (slashed) {
    				slashed = false;
    			} else {
    				if (c=='"') {
    					inDQuote = false;
    				}
    			}
    		} else if (c=='\\') {
    			slashed = true;
    		} else {
    			slashed = false;
    			if ((c!=' ')&&(c!='\t'))
    				isWhiting = false;
    			if (c=='\n')
    				isWhiting = true;
    			if (c=='{') {
    				delta += 1;
    			} else if (c=='}') {
    				delta -= 1;
    			}
    		}

    		if (lines != null) {
		
    			if ((!isWhiting)||(c=='\n'))
    				line.append(c);
    			if ((c=='\n') && (!inComment) && (!inSQuote) && (!inDQuote)) {
    				lines.add( line.toString() );
    				deltas.add( new Integer(delta) );
    				line.setLength(0);
    			}
    		}

    		i += 1;

    	}

    	if (inLineComment) {
    		line.append('\n');
    	}

    	if ((lines != null) && (line.length() != 0)) {
    		lines.add( line.toString() );
    		deltas.add( new Integer(delta) );
    	}

    	if (inSQuote) {
    		throw new Error("Error while parsing text: unclosed single quote in: <![[" + t + "]]>");
    	}
    	if (inDQuote) {
    		throw new Error("Error while parsing text: unclosed double quote in: <![[" + t + "]]>");
    	}
    	if (inComment) {
    		throw new Error("Error while parsing text: unclosed comment in: <![[" + t + "]]>");
    	}
	
    	return delta;

    }


    // Build indentation whitespace
    String getIndent( int indent ) {

    	StringBuffer indentS = new StringBuffer();
    	for (int i=0; i<indent; i++) {
    		indentS.append("    ");
    	}
    	return indentS.toString();

    }


    public String getFormattedString( int indent ) {

    	StringBuffer result = new StringBuffer();
    	ArrayList lines = new ArrayList(0);
    	ArrayList deltas = new ArrayList(0);
    	deltaDepth( lines, deltas );
    	int i;

    	// Skip first line of whitespace, if it exists
    	if ((!lines.isEmpty()) && (((String)lines.get(0)).length()!=0) && (((String)lines.get(0)).charAt(0) == '\n'))
    		i = 1;
    	else
    		i = 0;

    	// Now loop the text and add whitespace
    	int prevDelta = 0;
    	while (i<lines.size()) {

    		int newDelta = ((Integer)deltas.get(i)).intValue();
    		if (newDelta < prevDelta)
    			prevDelta = newDelta;
    		String s = (String)lines.get(i);
    		if ((s.length()!=0) && s.charAt(0)!='#') {
    			result.append( getIndent( indent + prevDelta ) + s );
    		} else {
    			// Do not add whitespace to lines meant for preprocessor (#defines etc)
    			result.append( s );
    		}
    		prevDelta = newDelta;
    		i += 1;

    	}

    	return result.toString();
    }


    public String toString() {

    	StringBuffer t = new StringBuffer(text.toString().trim());
    	Code.replaceSubstring(t, "/* line */", "\n");
    	if (line)
    		t.append("\n");
    	return t.toString();

    }


    void setParent( Text t, int d ) {
    	if (parent == null) {
    		parent = t;
    		depth = d;
    	} else 
    		throw new Error("Text: setting parent, but parent already set.");
    	}


    // Inserts t before this
    public void insert( Text t ) {
    
    	t.setParent( parent, depth );
    	parent = t;
    
    }


}
