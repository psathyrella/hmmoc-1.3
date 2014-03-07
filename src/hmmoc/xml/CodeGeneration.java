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
import java.io.*;

import hmmoc.code.Book;
import hmmoc.code.Generator;
import hmmoc.code.Text;
import hmmoc.util.ParseUtils;


public class CodeGeneration {
	
	String id;
	
	String file;
	String language;
	String realtype;
	String headerFile;
	
	public CodeGeneration( Element elem, TreeMap idRef, TreeMap objects ) {
		
		id = elem.getAttributeValue("id");
		
		// Fetch attributes 'file' and 'language'
		
		file = elem.getAttributeValue("file");
		if (file==null) {
			throw new Error("<codeGeneration>: no file attribute found");
		}
		
		language = elem.getAttributeValue("language");
		if (language==null) {
			throw new Error("<codeGeneration>: no language attribute found");
		}
		
		realtype = elem.getAttributeValue("realtype");
		if (realtype == null)
			realtype = "double";
		if (!realtype.equals("double") && 
		    !realtype.equals("bfloat") &&
		    !realtype.equals("logspace")) {
			throw new Error("<"+elem.getName()+">: Attribute 'realtype' has value '"+realtype+"', expected 'double', 'bfloat' or 'logspace'");
		}
		
		headerFile = elem.getAttributeValue("header");
		
		// Create book to write code in.
		
		Book book = new Book( objects );
		
		book.openScope("header-includes");
		book.openScope("header-classdef");
		book.openScope("header-funcdecl");
		book.openScope("includes");
		book.openScope("declarations");
		book.openScope("subroutines");
		
		book.addInitText( "header-includes", new Text("/* line */\n#ifndef _BODY_\n"));
		
		String headerLabel;
		if (headerFile != null) {
			headerLabel = headerFile.replace('.','_').replace('/','_');
		} else {
			headerLabel = "header_of_" + file.replace('.','_').replace('/','_');
		}
		
		book.addInitText( "header-includes", Generator.getCode("hmlHeaderStart",objects).bind(headerLabel));
		book.addInitText( "includes", Generator.getCode("hmlHeaderEnd",objects).bind(headerLabel));
		book.addInitText( "includes", new Text("/* line */\n#endif // _BODY_\n#ifndef _HEADER_\n"));
		
		if (headerFile != null) {
			book.addInitText( "includes", new Text("/* line */\n#include \""+headerFile+"\"/* line */\n") );
		} else {
			book.addInitText( "includes", new Text("/* line */\n/* Start of implementation */\n/* line */\n") );
		}
		
		book.addExitText( "header-includes", new Text("/* line */\n#endif // _HEADER_\n"));
		
		//book.addInitText( "declarations", Generator.getCode("hmlBookOmega",objects).getText() );
		//book.addInitText( "declarations", Generator.getCode("hmlBookAlpha",objects).getText() );
		
		HasIdentifiers.addGlobalIdentifier("_extreal_",realtype);

		if (realtype.equals("logspace")) {
		    HasIdentifiers.addGlobalIdentifier("_shortreal_","logspace");
		} else {
		    HasIdentifiers.addGlobalIdentifier("_shortreal_","double");
		}
		
		// Now iterate over the algorithms to write
		
		List children = elem.getChildren();
		Iterator i = children.iterator();
		TreeMap invariantObjects = new TreeMap( objects );   // store objects before parsing the HMM
		
		//System.out.println("Generating code...");
		
		while (i.hasNext()) {
			
			
			Element e = (Element)i.next();
			Element id = (Element)idRef.get(e.getAttributeValue("id"));
			
			try {
			    String hmmid = ParseUtils.parseChild(id, "hmm", idRef).getAttributeValue("id");
			    System.out.println("For HMM '"+hmmid+"': processing algorithm '"+id.getName()+"'");
			} catch (Error err) {
			    System.out.println("Parsing anonymous <code> block");
			}

			if ((id.getName().equals( "forward" ))||(id.getName().equals( "backward" ))) {
								
				ForwardBackward f = new ForwardBackward( id, idRef, objects );
				f.generate( book, language );
				
			} else if (id.getName().equals( "sample")) {
				
				Sample s = new Sample( id, idRef, objects );
				s.generate( book, language );
				
			} else if (id.getName().equals( "viterbi")) {
				
				ForwardBackward f = new ForwardBackward( id, idRef, objects );
				f.name += "_recurse";
				f.outputtable = true;
				f.generate( book, language );
				
				Sample s = new Sample( id, idRef, objects );
				s.name += "_trace";
				s.generate( book, language );
								
			} else if (id.getName().equals("code")) {
				
				book.add( Generator.getCode(id.getAttributeValue("id"),objects).getText() );
				
			} else {
				
				throw new Error("<codeGeneration>: Don't know how to handle child <"+e.getName()+">");
				
			}
			
			// finally, restore the object map to its original state
			objects.clear();
			objects.putAll( invariantObjects );
		}
		
		Code.emitInitCode(objects, book, "subroutines", "subroutines");
		Code.emitInitCode(objects, book, "declarations", "header-funcdecl");
		Code.emitInitCode(objects, book, "classdefinitions", "header-classdef");
		Code.emitInitCode(objects, book, "includes", "includes");
		Code.emitInitCode(objects, book, "header-includes", "header-includes");
		Code.checkInitEmpty();
		
		book.closeScope("subroutines");
		book.closeScope("declarations");
		book.closeScope("includes");
		book.closeScope("header-funcdecl");
		book.closeScope("header-classdef");
		book.closeScope("header-includes");
		
		// Finished - write code
		
		System.out.println("Writing code to file '" + file + "'...");

		OutputStream out;
		String curFile = null;
		
		try {
			
			if (headerFile == null) {
				curFile = file;
			} else {
				curFile = headerFile;
			}

			out = new FileOutputStream(curFile);
			out.write( book.getContent( "_HEADER_" ).toString().getBytes() );

			if (headerFile != null) {
				out.close();
				curFile = file;
				out = new FileOutputStream(curFile);
			}

			out.write( book.getContent( "_BODY_" ).toString().getBytes() );
			out.close();
			
		} catch (IOException e) {
			
			throw new Error("<codeGeneration>: Error trying to output to file "+curFile,e);
			
		}
	}
}

