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
import hmmoc.code.Generator;

public class Banding extends XmlElement {

    public String id;

    TreeMap objects;
    HMM hmm;

    TreeMap outputCoordinates = new TreeMap();
    Code bandingIdentifier = null;

    // Parses <banding> element
    public Banding( Element elem, TreeMap idMap, HMM hmm0 ) {
	
    	id = elem.getAttributeValue("id");
    	hmm = hmm0;
    	objects = hmm.objects;

    	List codes = ParseUtils.parseChildren( elem, "code", idMap );
    	
    	for (int i=0; i<codes.size(); i++) {
    		
    		// get code element, by de-referencing the identifier if necessary
    		String codeString = ((Element)codes.get(i)).getAttributeValue("id");
    		Code code = (Code)objects.get( codeString );
    		if (code == null) {
    			throw xmlError(elem, "Banding: referred to code element "+codeString+" but could not find it.");
    		}
    		
    		// allow a single expression-type code element, and one coordinate-type per output
    		if (code.type.equals("expression")) {
    			if (bandingIdentifier == null) {
    				bandingIdentifier = code;
    			} else {
    				throw xmlError(elem, "Banding: found two expression-type <code> elements, one expected.");
    			}
    		} else if (code.type.equals("coordinate")) {
    			if (code.output.equals("")) {
    				throw xmlError(elem, "Banding: missing 'output' attribute in 'coordinate' type <code> element (id="+code.id+")");
    			}
    			outputCoordinates.put( code.output, code );
    		} else {
    			throw xmlError(elem, "Banding: <code> element encountered of type '"+code.type+"'; only 'expression' and 'coordinate' allowed");
    		}
    	}
    	
    	if (bandingIdentifier == null) {
    		throw xmlError(elem, "Banding: no expression-type <code> element found, one required.");
    	}
    }

    public String getPosId( String output ) {
    	
    	Code code = (Code)(outputCoordinates.get( output ));
    	if (code == null) {
    		throw new Error("<banding> (id="+id+"): coordinate for <output> '"+output+"' requested, but not defined as <code> element");
    	}
    	code.reset();
    	String positionIdentifier = Generator.getCode("hmlBandingIdentifier",objects).getText().toString();
    	return positionIdentifier + "[" + code.getText().toString() + "]";
    	
    }
    
    public String getDim() {
    	
    	return Integer.toString( outputCoordinates.size() );
    }
	
    public String getBandingIdentifier() {
    	
    	bandingIdentifier.reset();
    	return bandingIdentifier.getText().toString();
    	
    }
}
