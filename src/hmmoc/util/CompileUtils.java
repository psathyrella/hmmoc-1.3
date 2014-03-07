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
package hmmoc.util;


import org.jdom.*;
import java.util.*;


import hmmoc.xml.HMM;
import hmmoc.xml.Code;
import hmmoc.xml.ParseDoc;


public class CompileUtils {
	
	// Retrieves single HMM reference from <forward> &c block
	
	public static HMM getHMM( Element elem, TreeMap idRef, TreeMap objects ) {
		
		Element e = ParseUtils.parseChild(elem,"hmm",idRef);
		return ParseDoc.parseHMM( e, idRef, objects );

	}
	
	// Retrieves <code> block from <forward> &c block implementing banding.
	
	public static Code getBanding( Element elem, TreeMap idRef, TreeMap objects ) {
		
		List bandings = elem.getChildren("banding");
		if (bandings.size() == 0)
			return null;
		if (bandings.size() > 1) {
			throw new Error("<"+elem.getName()+">: expected 0 or 1 <banding> element");
		}
		Element e = (Element)bandings.get(0);
		List codes = e.getChildren("code");
		if (codes.size() == 0) {
			return null;
		}
		if (codes.size() > 1) {
			throw new Error("<"+elem.getName()+"><banding>: expected 0 or 1 <code> element");
		}
		if (!objects.containsKey( ((Element)codes.get(0)).getAttributeValue("id") )) {
			throw new Error("<"+elem.getName()+"><banding>: Couldn't find code element with id "+((Element)codes.get(0)).getAttributeValue("id"));
		}
		return (Code)objects.get( ((Element)codes.get(0)).getAttributeValue("id"));
	}
	
}


