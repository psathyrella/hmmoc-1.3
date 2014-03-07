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
import org.jdom.input.SAXBuilder;
import org.jdom.output.XMLOutputter;

import java.io.*;
import java.util.*;
import java.net.URL;

import hmmoc.util.ParseUtils;
import hmmoc.xml.HasIdentifiers;



public class ParseDoc {
	
	Document document;
	Element root;
	public TreeMap objects;
	public TreeMap idMap;
	
	
	
	public ParseDoc(Object xmlFile, String rootId, TreeMap prevIdMap) {
		
		SAXBuilder saxBuilder = new SAXBuilder();
		
		try {
			if (xmlFile instanceof String) {
				document = saxBuilder.build( (String)xmlFile );
			} else if (xmlFile instanceof URL) {
				document = saxBuilder.build( (URL)xmlFile );
			}
			root = ParseUtils.parseRoot( document, rootId );
			objects = new TreeMap();
			
			// Build description string (but not for the snippets.xml file)
			if (xmlFile instanceof String) {
			    List authorL = root.getChildren("author");
			    String author = "<unknown>";
			    if (root.getAttributeValue("name") != null) {
				author = root.getAttributeValue("name");
			    } else if (authorL.size() > 0) {
				author = ((Element)authorL.get(0)).getValue();
			    }
			    String description = "from file "+xmlFile.toString() + " (author: "+author+") on "+new Date().toString();
			    HasIdentifiers.addGlobalIdentifier("_description_",description);
			}
			
			// Get all macro definitions, and remove them from document
			TreeMap macroMap = ParseUtils.getMacroDefs( root );
			
			// Process all macro expansions; replace macro expansion request with result
			ParseUtils.expandMacros( root, macroMap );
			
			// Get all elements in document, and assign identifiers to them;
			idMap = ParseUtils.parseId( root, prevIdMap );
			
			// Rewriting done; output debug XML code
			if (root.getAttributeValue("debug") != null) {
				XMLOutputter outputter = new XMLOutputter();
				FileOutputStream fos = new FileOutputStream(xmlFile+".debug");
				outputter.output(document, fos);
				fos.close();
			}
			
		} catch (JDOMException e) { // indicates a well-formedness or other error
			
			throw new Error("JDOMException: "+e.getMessage());
			
		} catch (IOException e) { // indicates an IO problem
			
			throw new Error("IOException: "+e.getMessage());
			
		}      
	}

    public void parseIdRef() {
	
	// Change identifiers of referring elements to element referred to
	ParseUtils.parseIdref( root, idMap );

    }
			
	public void parseCode() {
		
		// Parse all <code> and <parameter> elements
		List codeList = ParseUtils.parseDescendants( root, "code", idMap );
		Iterator i = codeList.iterator();
		while (i.hasNext()) {
			Element codeElt = (Element)i.next();
			String codeId = codeElt.getAttributeValue("id");
			if (objects.containsKey( codeId )) {
			    System.out.println("Note: Shadowing definition of <code id=\"" + codeId + "\">");
			}
			objects.put( codeId, new Code( codeElt, idMap ) );
		}
	}
	
	
	static public HMM parseHMM( Element hmmelt, TreeMap idMap, TreeMap objects ) {
		
		HMM hmm = new HMM(hmmelt, idMap, objects );
		hmm.analyze( objects );
		objects.put( hmmelt.getAttributeValue("id"), hmm );
		return hmm;
		
	}
	
	
	public void parseCodeGeneration() {
		
		// parse all <codeGeneration> elements
		List codeGenList = ParseUtils.parseDescendants( root, "codeGeneration", idMap);
		Iterator i = codeGenList.iterator();
		while (i.hasNext()) {
			Element cgelt = (Element)i.next();
			// It's the side effect that we're after here.
			new CodeGeneration(cgelt, idMap, objects);
		}
	}
	
}

