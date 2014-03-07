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


public class Emission {

    public String id;

    public String probability;
    public String[] output;
    public IntVec outputVec;
    public int[] order;               // initialized by Transition
    public int[] warned;              // (to avoid double warnings for staggered orders -- see Transition.java)
    public int sigIdx;                // initialized by HMM
    public int number;                // initialized by HMM

    // Parses <emission> element
    public Emission( Element elem, TreeMap idMap, HMM hmm ) {
	id = elem.getAttributeValue("id");

	Element probabilityElt = ParseUtils.parseChild(elem,"probability",idMap);
	probability = probabilityElt.getAttributeValue("id");

	if (hmm.objects.get(probability)==null) {
	    hmm.objects.put( probability, new Probability( probabilityElt, idMap, hmm ) );
	}

	outputVec = new IntVec( hmm.numOutputs );
	warned = new int[ hmm.numOutputs ];
	order = new int[ hmm.numOutputs ];
	List outputList = ParseUtils.parseChildren(elem,"output", idMap);
	output = new String[ outputList.size() ];
	for (int i=0; i<outputList.size(); i++) {
	    output[i] = ((Element)outputList.get(i)).getAttributeValue("id");
	    Object o = hmm.objects.get( output[i] );
	    if (o == null) {
		throw new Error("Emission: <output> element "+output[i]+" was not listed in <outputs>");
	    }
	    outputVec.v[ ((Output)o).ordinal ] += 1;
	}
    }


    public String getSignature(HMM hmm) {

	StringBuffer sb = new StringBuffer("");
	for (int i=0; i<hmm.numOutputs; i++) {
	    sb.append(String.valueOf(order[i] + outputVec.v[i]));
	}
	return sb.toString();

    }

}

