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

import org.jdom.Element;


public class XmlElement {
	
	public String id;
	
	public static Error xmlError(Element e, String s) {
		
		List stack = new ArrayList(0);
		Parent p = e;
		while (p != null) {
			
			if (p instanceof Element) {
				stack.add( "<" + ((Element)p).getName() + " id=\"" + ((Element)p).getAttributeValue("id") + "\">");
			} else {
				stack.add( "[Document "+((Document)p).getBaseURI() + "]" );
			}
			p = p.getParent();
			
		}
		StringBuffer pref = new StringBuffer("");
		StringBuffer err = new StringBuffer(s+"\nDocument trace:\n");
		for (int i=stack.size()-1; i>=0; i--) {
			pref.append("  ");
			err.append(pref);
			err.append( (String)stack.get(i) );
			err.append( "\n" );
		}
		
		return new Error(err.toString());
		
	}
	
}



