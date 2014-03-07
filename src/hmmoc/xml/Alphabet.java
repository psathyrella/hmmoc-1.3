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


public class Alphabet extends XmlElement {

    public String id;
    public String[] alphabet;
    public String type;
    public int maxchar = 256;

    public Alphabet( Element t, TreeMap idMap, TreeMap objects ) {

	id = t.getAttributeValue("id");
	type = "unsigned char";
	ArrayList alphabeta = new ArrayList();

	String text = ParseUtils.getText(t);
	if (text == null)
	    text = "";
	List codeElts = ParseUtils.parseChildren(t, "code", idMap);

	for (int i=0; i<text.length(); i++) {
	    char c = text.charAt( i );
	    if ((c != ' ')&&(c != '\n')&&(c != '\t')) {

		if (c < ' ' || c > (char)128) {
		    throw xmlError(t,"<alphabet> '"+id+"': Unprintable characters must be listed as <code> elements (found character '"+c+"')");
		}

		if (c=='\\')
		    alphabeta.add("'\\'");
		else if (c=='\'') 
		    alphabeta.add("'\''");
		else
		    alphabeta.add( "'"+c+"'");
	    }
	}

	Iterator it = codeElts.iterator();
	while (it.hasNext()) {
	    Element ce = (Element)it.next();
	    Code c = (Code)objects.get( ce.getAttributeValue("id") );
	    alphabeta.add( c.getText().toString() );
	}

	List rangeElts = ParseUtils.parseChildren(t, "range", idMap);
	it = rangeElts.iterator();
	while (it.hasNext()) {
	    Element re = (Element)it.next();
	    String from = re.getAttributeValue("from");
	    if (from == null) {
		throw xmlError(re,"No 'from' attribute found in <range> element");
	    }
	    String to = re.getAttributeValue("to");
	    if (to == null) {
		throw xmlError(re,"No 'to' attribute found in <range> element");
	    }
	    int fromI = Integer.parseInt(from);
	    int toI = Integer.parseInt(to);
	    if (fromI>=toI) {
		throw xmlError(re,"Range does not contain elements ("+from+" >= "+to+")");
	    }
	    if (toI > 256) {
		type = "unsigned int";
		maxchar = toI;
	    }
	    // Now this is a little ugly, and inefficient for large alphabets - but hey, it works.
	    for (int i=fromI; i<toI; i++) {
		alphabeta.add( "0x"+Integer.toHexString(i) );
	    }
	}

	alphabet = (String[])alphabeta.toArray(new String[0]);

	if (alphabet.length == 0) {
	    throw new Error("Found <alphabet> (id="+id+") with no characters!");
	}

	/*
	System.out.println("Alphabet "+id+": "+alphabet.length+" characters");
	for (int i=0; i<alphabet.length; i++) {
	    System.out.println(alphabet[i]);
	}
	*/

    }


    public int getSize() {

	return alphabet.length;

    }

}
