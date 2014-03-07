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
package hmmoc.appl;

import java.net.URL;
import java.util.TreeMap;
import hmmoc.xml.ParseDoc;



public class hmmoc {


    public static void main(String[] args) {
  
        if (args.length != 1) {

	    System.out.println("HMMoC version 1.3  --  a Hidden Markov Model compiler  --  Copyright (C) 2007 Gerton Lunter, Oxford university");
            System.out.println("HMMoC is released under the GNU General Public License, see accompanying file for details.");
	    System.out.println("This product includes software developed by the JDOM Project (http://www.jdom.org/)");
            System.out.println("\nUsage:  hmmoc file.xml");
	    return;

        } 

	ParseDoc initDocument;
	String snippets = "snippets.xml";
	URL initURL = hmmoc.class.getResource(snippets);
	if (initURL==null) {
	    throw new Error("Urgh... Cannot load resource. Don't know what to do.");
	}
	initDocument = new ParseDoc(initURL, "snippets", new TreeMap() );
	initDocument.parseCode();

	ParseDoc hmlDocument;
        try {

	    hmlDocument = new ParseDoc( args[0], "hml", initDocument.idMap );

	} catch (Error e) {

	    System.out.println( e.getMessage() );
	    throw new Error();                      // Throw "empty" error - parser has said something, don't clutter output with stack dump


	}

	hmlDocument.objects.putAll( initDocument.objects );
	hmlDocument.parseCode();
	// Change identifiers of referring elements to element referred to
	// (Not before parseCode, since otherwise idref's are considered redefinitions, generating messages about shadowing)
	hmlDocument.parseIdRef();
	hmlDocument.parseCodeGeneration();
	    
    }
}


