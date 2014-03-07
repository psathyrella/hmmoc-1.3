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

import hmmoc.util.ParseUtils;
import org.jdom.Element;

import java.util.TreeMap;



public class Output extends HasIdentifiers {
	
	static final String posId = "iPos";
	
	public Alphabet alphabet;
	public String seqId;
	public String lenId;
	public int speed;   		// "0" for fastest, shortest, inner loop; higher for others
	public int ordinal;
	
	// Parses <output> element
	public Output( Element elem, TreeMap idMap, TreeMap objects ) {
		
		// calls HasIdentifiers constructor
		super(elem,idMap);
		
		Element alphabetElem = ParseUtils.parseChild(elem,"alphabet",idMap);
		if (!objects.containsKey(alphabetElem.getAttributeValue("id"))) {
			alphabet = new Alphabet( alphabetElem, idMap, objects );
			objects.put( alphabet.id, alphabet );
		} else {
			alphabet = (Alphabet)objects.get( alphabetElem.getAttributeValue("id") );
		}
		
		seqId = getIdent(0,"sequence").identifier;
		lenId = getIdent(0,"length").identifier;
		String spd = elem.getAttributeValue("speed");
		if (spd == null) {
			speed = -1;
		} else {
			speed = Integer.parseInt( spd );
		}
		
		// "ordinal" is filled by calling routine
	}
	
	
	public String getPosId() {
		
		// Signal that init blocks and parameters are to be used:
		use();
		
		// Return identifier
		return posId + new Integer(ordinal);
		
	}
	
}

