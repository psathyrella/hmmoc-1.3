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




public class Code extends HasIdentifiers {
	
	
	String language;
	String output;
	String type;
	String text;
	StringBuffer current;
	
	
	// Parses <code> element
	public Code( Element elem, TreeMap idMap ) {
		
		// call HasIdentifier constructor 
		super(elem,idMap);
		
		text = ParseUtils.getTextNotrim(elem);
		if (text == null)
			text = "";
		
		language = elem.getAttributeValue("language","C++");
		
		type = elem.getAttributeValue("type","expression");
		if (!type.equals("expression") && !type.equals("statement") && !type.equals("parameter") && !type.equals("coordinate")) {
			throw xmlError(elem,"Require 'type' attribute with value \"expression\", \"statement\", \"coordinate\" or \"parameter\" (got \""+type+"\")");
		}
		
		// get output attribute; valid for type==coordinate only
		output = elem.getAttributeValue("output","");
		if (!output.equals("") && !type.equals("coordinate")) {
			throw new Error("Illegal code element: has 'output' attribute, but is not of type 'coordinate'");
		}
		
		reset();
		
	}
	
	
	
	// Unbind all identifiers
	void reset() {
		super.reset();
		current = new StringBuffer(text);
	}
	
	

    // Time-critical subroutine	
	static public boolean replaceSubstring(StringBuffer buffer, String identifier, String withWhat) {
		
		boolean hasReplaced = false;
		String bufferAsString = buffer.toString();
		int idLength = identifier.length();
		int bufLength = buffer.length();
		int i=0;
		while (i<=bufLength - idLength) {
			if (identifier.regionMatches(0,bufferAsString,i,idLength)) {
				hasReplaced = true;
				buffer.replace(i,i+idLength, withWhat);
				i += withWhat.length() - idLength;
				bufferAsString = buffer.toString();
				bufLength = buffer.length();
			}
			i++;
		}
		return hasReplaced;
	}
	
	
	
	// Binds identifier with string
	void bind(Identifier identifier, String withWhat ) {
		
		String identifierS = identifier.identifier;
		boolean hasReplaced = false;
		
		if (identifier.bound) {
			throw xmlError(e,"Trying to bind identifier "+identifier.identifier+" of type "+identifier.type+" twice");
		}
		identifier.bound = true;
		if (identifierS.equals(""))
			return;
		hasReplaced = replaceSubstring(current, identifierS, withWhat);
		if ((!hasReplaced)&&(!id.startsWith("hml"))) {
			// Do not give warnings for internal snippets
			System.out.println("Warning: Identifier "+identifier.identifier+" in code "+id+" was not matched in code body.");
		}
	}
	
	
	boolean canBind(Identifier identifier) {

		if (identifier.identifier.equals(""))
			return false;
		else
			return true;
		
	}
	
	
	// Version for certain output at certain depth
	boolean canBind(String output, int depth) {
		
		if (hasIdent(output,depth)) {
			return canBind( getOutputIdent(output,depth) );
		} else {
			return false;
		}
		
	}
	
	// Version for position identifier
	boolean canBindPosition(String output) {
		
	    if (hasPositionIdent(output)) {
		return canBind( getPositionIdent(output) );
	    } else {
		return false;
	    }
		
	}
	
	
	
	// Returns text of code
	public Text getText() {
		
		// Signal that this code object has been used; makes sure parameters and init code blocks are going to be emitted
		use();
		
		// Now check all identifiers are bound
		for (int i=0; i<identifierList.size(); i++) {
			Identifier ident = (Identifier)identifierList.get(i); 
			if ((!ident.bound) && (!ident.identifier.equals(""))) {
				throw xmlError(e,"Unbound identifier "+ident.identifier);
			}
		}
		
		// Process global identifiers - only on internal snippets
		if (id.startsWith("hml")) {
			Iterator i = globalIdentifiers.keySet().iterator();
			while (i.hasNext()) {
				String identifier = (String)i.next();
				String withWhat = (String)globalIdentifiers.get(identifier);
				replaceSubstring(current,identifier,withWhat);
			}
		}
		
		// An expression (or parameter) does not appear by itself on a single line
		// (Just to make code look nice -- skips indentation.  However for //-style comments difference can be crucial.)
		Text t = new Text(current.toString());
		if (!type.equals("statement")) {
			t.setLine(false);
		}
		return t;
	}
	
	
	
	// Bind num-th identifier of certain type to string withWhat
	void bind(int num, String withWhat, String type) {
		
		bind( getIdent(num,type), withWhat );
		
	}
	
	
	// Empty-type version
	void bind(int num, String withWhat) {
		
		bind(num,withWhat,null);
		
	}
	
	
	
	// Version for certain output at certain depth
	void bind(String output, int depth, String withWhat) {
		
		bind( getOutputIdent(output,depth), withWhat );
		
	}
	
	
	// Version for position of certain output
	void bindOutput(String output, String withWhat) {
		
		bind( getPositionIdent(output), withWhat );
		
	}
	
	
	
	// Does reset, bind and getText all at once; expects single identifier
	public Text bind(String withWhat) {
		reset();
		bind(0,withWhat);
		return getText();
	}
	
	
	// Does reset, bind and getText all at once; expects two identifiers
	public Text bind(String withWhat, String withWhat2) {
		reset();
		bind(0,withWhat);
		bind(1,withWhat2);
		return getText();
	}
	
	// Does reset, bind and getText all at once; expects three identifiers
	public Text bind(String withWhat, String withWhat2, String withWhat3) {
		reset();
		bind(0,withWhat);
		bind(1,withWhat2);
		bind(2,withWhat3);
		return getText();
	}
	
	
	// Does reset, bind and getText all at once; expects four identifiers
	public Text bind(String withWhat, String withWhat2, String withWhat3, String withWhat4) {
		reset();
		bind(0,withWhat);
		bind(1,withWhat2);
		bind(2,withWhat3);
		bind(3,withWhat4);
		return getText();
	}
	
	
	public Text bind(String withWhat, String withWhat2, String withWhat3, String withWhat4, String ww5) {
		reset();
		bind(0,withWhat);
		bind(1,withWhat2);
		bind(2,withWhat3);
		bind(3,withWhat4);
		bind(4,ww5);
		return getText();
	}
	
	
	public Text bind(String withWhat, String withWhat2, String withWhat3, String withWhat4, String ww5, String ww6) {
		reset();
		bind(0,withWhat);
		bind(1,withWhat2);
		bind(2,withWhat3);
		bind(3,withWhat4);
		bind(4,ww5);
		bind(5,ww6);
		return getText();
	}
	
	
	public Text bind(String withWhat, String withWhat2, String withWhat3, String withWhat4, String ww5, String ww6, String ww7) {
		reset();
		bind(0,withWhat);
		bind(1,withWhat2);
		bind(2,withWhat3);
		bind(3,withWhat4);
		bind(4,ww5);
		bind(5,ww6);
		bind(6,ww7);
		return getText();
	}
	
	
	public Text bind(String withWhat, String withWhat2, String withWhat3, String withWhat4, String ww5, String ww6, String ww7, String ww8) {
		reset();
		bind(0,withWhat);
		bind(1,withWhat2);
		bind(2,withWhat3);
		bind(3,withWhat4);
		bind(4,ww5);
		bind(5,ww6);
		bind(6,ww7);
		bind(7,ww8);
		return getText();
	}
	
	public Text bind(String withWhat, String withWhat2, String withWhat3, String withWhat4, String ww5, String ww6, String ww7, String ww8, String ww9) {
		reset();
		bind(0,withWhat);
		bind(1,withWhat2);
		bind(2,withWhat3);
		bind(3,withWhat4);
		bind(4,ww5);
		bind(5,ww6);
		bind(6,ww7);
		bind(7,ww8);
		bind(8,ww9);
		return getText();
	}
	
}

