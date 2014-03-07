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



/**  <identifier type="input">  iSymbol   </identifier>   */



public class Identifier {

    String id;

    String identifier;
    String type;
    String output;
    int depth;
    boolean bound;             // used by Code, to mark this identifier as bound

    public Identifier( Element t, TreeMap idMap ) {
	id = t.getAttributeValue("id");
	identifier = ParseUtils.getText( t );
	if (identifier.equals("")) {
	    //throw new Error("<identifier> "+id+": No identifier given.");
	    //now allowed.
	}
	type = t.getAttributeValue( "type" );
	if (type == null) {
	    type = "default";
	}

	Element outputElt = ParseUtils.parseAttributeWithDefault(t,"output","output",idMap);
	if (outputElt == null) {
	    output = null;
	} else {
	    output = outputElt.getAttributeValue("id");
	}

	String depthS = t.getAttributeValue("depth");
	String heightS = t.getAttributeValue("height");
	if (depthS == null && heightS == null)
	    depth = 0;
	else if (depthS != null && heightS != null) {
	    throw new Error("<identifier> "+id+": Found both \"depth\" and \"height\" attributes");
	} else {
	    if (output == null) {
		throw new Error("<identifier> "+id+": Found \"depth\" attribute without \"output\" attribute");
	    }
	    if (depthS != null) {
		depth = Integer.valueOf(depthS).intValue();
	    } else {
		depth = -Integer.valueOf(heightS).intValue();
	    }
	}
    }
}
