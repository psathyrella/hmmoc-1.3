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
package hmmoc.xml;

import org.jdom.*;
import java.util.*;

import hmmoc.util.ParseUtils;
import hmmoc.code.Text;
import hmmoc.code.Book;



public class HasIdentifiers extends XmlElement {
	
	static TreeSet parameters = new TreeSet();     // static list of parameter ids accessed by used code objects
	static TreeSet inits = new TreeSet();          // static list of code ids required as initializers by used code objects
	static public TreeMap globalIdentifiers = new TreeMap();  // global substitutions (e.g. real type)
	
	String init;                                   // any initialisation code, to be used when this (code or output) block is used
	String where;
	List parameterList;                            // any parameters
	List identifierList;                           // list of identifiers mentioned in this block
	boolean hasOutputs;                            // true if identifiers mention output explicitly
	
	Element e;
	
	public HasIdentifiers( Element elem, TreeMap idMap ) {
		
		id = elem.getAttributeValue("id");
		
		// For debugging XML code
		e = elem;
		
		identifierList = parseIdentifiers( elem, idMap );
		
		where = elem.getAttributeValue("where");      // empty if code is ordinary initializer; or:
		if (where != null) {
			if (!where.equals("includes") && 
					!where.equals("declarations") && 
					!where.equals("subroutines") && 
					!where.equals("classdefinitions") &&
					!where.equals("header-includes")) {
				throw xmlError(e,"Error parsing 'where' attribute; should be one of 'includes', 'declarations', 'classdefinitions' or 'subroutines', got '"+where);
			}
		}
		
		init = elem.getAttributeValue("init");
		if (init != null) {
			init = ParseUtils.parseAttribute(elem,"init","code",idMap).getAttributeValue("id");
		}
		
		parameterList = ParseUtils.parseChildren( elem, "code", idMap );
		// Replace each Element by a String, the identifier of the corresponding code element
		for (int i=0; i<parameterList.size(); i++) {
			Element par = (Element)parameterList.get(i);
			parameterList.set(i, par.getAttributeValue("id"));
		}
		
	}
	
	

	// Resets all per-outputfile data -- although this shouldn't be necessary
    /*
	public static void resetGlobalIdentifiers() {
		
		parameters = new TreeSet();     
		inits = new TreeSet();         
		globalIdentifiers = new TreeMap();  
		
	}
    */
	
	
	
	
	// Parses identifiers (children of <code> or <output> element), returns them as a list.
	
	List parseIdentifiers( Element elem, TreeMap idMap ) {
		
		List identifierList = ParseUtils.parseChildren( elem, "identifier", idMap );
		HashSet ids = new HashSet(0);
		hasOutputs = false;
		boolean hasEmpties = false;
		for (int i=0; i<identifierList.size(); i++) {
			Identifier identifier = new Identifier((Element)identifierList.get(i), idMap);
			identifierList.set(i, identifier);
			if (ids.contains(identifier.identifier)) {
				throw xmlError(elem,"Found same <identifier> ("+identifier.identifier+") twice");
			}
			ids.add(identifier.identifier);
			identifier.bound = false;
			if (identifier.output == null) {
				if (identifier.type.equals("default")) {
					// has default identifiers NOT referring to output symbol
					hasEmpties = true;
				}
			} else {
				if (!identifier.type.equals("default") && !identifier.type.equals("position")) {
					throw xmlError(elem,"Found symbol <identifier> referring to <output> "+identifier.output+" with explicit type attribute (\""+identifier.type+"\"); default type must be used.");
				}
				// has default identifiers referring to output symbol
				hasOutputs = true;
			}
		}
		if (hasEmpties && hasOutputs) {
			throw xmlError(elem,"Found <identifier>s of default \"type\" with and without explicit \"output\" attribute - cannot be used simultaneously");
		}
		return identifierList;
	}
	
	
	// Return number of non-empty identifiers of certain type
	int activeIdentifiers(String type) {
		
		int a = 0;
		for (int i=0; i<identifierList.size(); i++) {
			Identifier identifier = (Identifier)identifierList.get(i);
			if (identifier.type.equals(type)) {
				if (!identifier.identifier.equals(""))
					a += 1;
			}
		}
		return a;
	}
	
	
	// Count number of identifiers of certain type
	int countIdType(String type) {
		if (type == null)
			return identifierList.size();
		int count = 0;
		for (int i=0; i<identifierList.size(); i++) {
			if (((Identifier)identifierList.get(i)).type.equals(type)) {
				count += 1;
			}
		}
		return count;
	}
	
	
	// Get num-th identifier of certain type
	Identifier getIdent(int num, String type) {
		int count = 0;
		for (int i=0; i<identifierList.size(); i++) {
			if ((type == null) || (((Identifier)identifierList.get(i)).type.equals(type))) {
				if (count == num) {
					return (Identifier)identifierList.get(i);
				}
				count += 1;
			}
		}
		if (type == null)
			type = "<anyType>";
		if (num==0) {
			throw xmlError(e,"Could not find identifier of type "+type);
		} else {
			throw xmlError(e,(num+1)+"th identifier of type "+type+" does not exist (there are "+count+")");
		}
	}
	
	
    // Get identifier of output character for certain tape and depth
    Identifier getOutputIdent(String output, int depth) {
	int j = -1;
	for (int i=0; i<identifierList.size(); i++) {
	    Identifier ident = (Identifier)identifierList.get(i);
	    if (output.equals(ident.output) && ident.type.equals("default")) {
		if (depth == ident.depth) {
		    if (j != -1) {
			throw xmlError(e,"Found two <identifier>s for <output> "+output+" (at depth "+depth+")");
		    }
		    j=i;
		}
	    }
	}
	if (j == -1) {
	    throw xmlError(e,"Could not find <identifier> for <output> "+output+" (at depth "+depth+")");
	}
	return (Identifier)identifierList.get(j);
    }
	
	
	
    // Get identifier of position within certain tape
    Identifier getPositionIdent(String output) {
	int j = -1;
	for (int i=0; i<identifierList.size(); i++) {
	    Identifier ident = (Identifier)identifierList.get(i);
	    if (output.equals(ident.output) && ident.type.equals("position")) {
		if (j != -1) {
		    throw xmlError(e,"Found two <identifier>s position within <output> "+output);
		}
		j=i;
	    }
	}
	if (j == -1) {
	    throw xmlError(e,"Could not find <identifier> for position within <output> "+output);
	}
	return (Identifier)identifierList.get(j);
    }
	
	
	// Empty-type version
	Identifier getIdent(int num) {
		return getIdent(num,null);
	}
	
	
	// Just check whether identifier exists:
	boolean hasIdent(int num, String type) {
		
		try {
			getIdent(num,type);
		} catch (Error e) {
			return false;
		}
		return true;
	}
	
	
	boolean hasIdent(String output, int depth) {
		try {
			getOutputIdent(output,depth);
		} catch (Error e) {
			return false;
		}
		return true;
	}
	
	boolean hasPositionIdent(String output) {
		try {
			getPositionIdent(output);
		} catch (Error e) {
			return false;
		}
		return true;
	}
	
	// Unbind all identifiers
	void reset() {
		for (int i=0; i<identifierList.size(); i++) {
			((Identifier)identifierList.get(i)).bound = false;
		}
	}
	
	
	static Code getNextInitCode(TreeMap objects, TreeSet hasInited) {
		
		String init;
		// First find an init id that has not been visited before
		do {
			if (inits.isEmpty())
				return null;
			init = (String)inits.first();
			inits.remove( init );
		} while (hasInited.contains( init ));
		// Return corresponding code object
		return (Code)objects.get( init );
		
	}
	
	
	public static void emitInitCode(TreeMap objects, Book book, String initLocation, String initScope) {

		// Those that are not processed now are collected in skippedInits
		TreeSet skippedInits = new TreeSet();
		TreeSet hasInited = new TreeSet();
		
		// Get first block to be inited
		Code c = getNextInitCode(objects, hasInited);
		while (c != null) {
			
			if ((c.where == null && initLocation.equals("")) ||
					(c.where != null && initLocation.equals(c.where))) {
				
				Text initText = c.getText();
				initText.setLine( true );    // make sure #includes etc get put on single lines
				book.addInitText( initScope, initText );
				hasInited.add( c.id );
				
			} else {
				
				skippedInits.add( c.id );
				
			}
			
			c = getNextInitCode(objects, hasInited);
		}
		
		// Reset the hasInited and inits sets
		inits = skippedInits;
		
	}
	
	
	public static void checkInitEmpty() {
		
		if (inits.isEmpty())
			return;
		System.out.println("Warning: Some initialization <code> block(s) not emitted (first one is "+inits.first() +").  Check for initializer up-references (e.g. an 'include' referring to a 'declaration' or 'subroutine')");
	}
	
	
	
	public static String getParameters(TreeMap objects) {
		
		Text t = new Text("");
		boolean addComma = false;
		Iterator i = parameters.iterator();
		while (i.hasNext()) {
			if (addComma)
				t.append(",");
			addComma = true;
			String parId = (String)i.next();
			Code c = (Code)objects.get( parId );
			t.append( c.getText() );
		}
		parameters = new TreeSet();
		return sortParameters(t.toString());
	}
	
	
	public static String addParameter(String pars, String par) {
		
		if (pars.length()==0) {
			return par;
		} else { 
			return sortParameters(pars + "," + par);
		}
	}
	
	
	static String sortParameters(String pars) {
		
		if (pars.trim().length()==0)
			return "";
		SortedSet parset = new TreeSet();
		int i = 0;
		while (pars.indexOf(',',i) != -1) {
			parset.add( pars.substring(i,pars.indexOf(',',i)).trim() );
			i = pars.indexOf(',',i) + 1;
		}
		parset.add( pars.substring(i).trim() );
		Iterator it = parset.iterator();
		StringBuffer result = new StringBuffer();
		result.append((String)it.next());
		while (it.hasNext()) {
			result.append( ","+(String)it.next() );
		}
		return result.toString();
	}
	
	
	public static String stripTypesFromParameters(String pars) {
		
		StringBuffer r = new StringBuffer("");
		boolean right = true;
		boolean left = false;
		int parenthesesLevel = 0;
		int bracketLevel = 0;
		for (int i=pars.length()-1; i>=0; i--) {
			switch (pars.charAt(i)) {
			case ')':  parenthesesLevel++; break;
			case '(':  parenthesesLevel--; break;
			case ']':  bracketLevel++; break;
			case '[':  bracketLevel--; break;
			default:
				if (parenthesesLevel<0 || bracketLevel<0) {
					throw new Error("Problem parsing parameter string '"+pars+"'");
				}
			if (parenthesesLevel + bracketLevel == 0) {
				if (pars.charAt(i)==',') {
					right = true;
					left = false;
					r.append(",");
				} else if (pars.charAt(i)=='&' || pars.charAt(i)=='*') {
					// ignore these
				} else if (pars.charAt(i)==' ' || pars.charAt(i)=='\t' || pars.charAt(i)=='\n') {
					// if we're reading chars (right==false), then stop; otherwise ignore
					left = !right;
				} else {
					right = false;
					if (!left) {
						r.append(pars.charAt(i));
					}
				}
			}
			}
		}
		r.reverse();
		return r.toString();
	}
	
	
	public static void addGlobalIdentifier(String identifier, String substitution) {
		
		globalIdentifiers.put(identifier,substitution);
		
	}
	
	
	
	// Signals that this code object is being used - add required initialization and parameters to sets
	void use() {
		
		parameters.addAll( parameterList );
		if (init != null) {
			inits.add( init );
		}
		
	}
	
}

