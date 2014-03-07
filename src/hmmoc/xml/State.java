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
import hmmoc.util.IntVec;

import hmmoc.code.RangeCode;


public class State implements Comparable {
	
	public String id;
	public int[] order;
	public int numOutputs;
	public String block;
	
	// Information on where this state can occur in the DP table
	IntVec mask;                     // 0=variable positions; 1=fixed from start, -1=fixed from end  (1 if fixed from both)
	
	public IntVec posStart;          // These are temporaries used by HMM.analyze
	public IntVec posEnd;
	public IntVec maskStart;
	public IntVec maskEnd;
	
	public RangeCode range;                     // Computed from temporaries above
	
	public String emission;                     // null if this is a Mealy state (emission associated to transitions)
	
	public boolean start;            // true if start state.  
	public boolean end;              // true if end state.  (Both used by StateCode to generate initialization code)
	public int inDegree;             // # of incoming transitions
	public int outDegree;            // # of outgoing transitions
	
	public int internalIndex;        // used by Forward etc. to keep temporary vector index for this state
	
	// Number in state array (initialized by Analyze) 
	public int number;
	public int globalNumber;
	
        public int compareTo( Object e2 ) {
	    return id.compareTo( ((State)e2).id );
	}

	// Parses <state> element
	public State( Element elem, TreeMap idMap, int numOutputs0, TreeMap objects ) {
		
		id = elem.getAttributeValue("id");
		Element emissionElt = ParseUtils.parseAttributeWithDefault(elem,"emission","emission",idMap);
		if (emissionElt != null) {
			emission = emissionElt.getAttributeValue("id");
		}
		numOutputs = numOutputs0;
		order = new int[numOutputs];
		List orderList = ParseUtils.parseChildren(elem,"order",idMap);
		Iterator i = orderList.iterator();
		while (i.hasNext()) {
			Element e = (Element)i.next();
			String outputS = ParseUtils.parseAttribute(e,"output","output",idMap).getAttributeValue("id");
			if (!objects.containsKey(outputS)) {
				throw new Error("<state> "+id+" refers to <output> "+outputS+" which is not listed in <outputs> section of encompassing <hmm>");
			}
			int output = ((Output)objects.get(outputS)).ordinal;
			String depthS = e.getAttributeValue("depth");
			if (depthS == null) {
				throw new Error("<state> "+id+": Found <order> element (referring to <output> "+outputS+") which has no \"depth\" attribute");
			}
			int depth = Integer.valueOf(depthS).intValue();
			if (depth < 1) {
				throw new Error("<state> "+id+": Found <order> element (referring to <output> "+outputS+") with depth "+depth+", require depth at least 1");
			}
			if (order[output] != 0) {
				throw new Error("<state> "+id+": Found more than one <order> element referring to <output> "+outputS);
			}
			order[output] = depth;
		}
	}
}

