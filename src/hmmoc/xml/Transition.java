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


public class Transition {
	
	public String id;
	
	public String from;
	public String to;
	public String probability;
	public String emission;
	String fromBlock;
	String toBlock;
	boolean mealy;
	
	public int number;       // used by TransitionCode and Sample; set by HMM
	
	// Transient elements (dependent on active outputs)
	IntVec outputVec;        // from emission, but masked for active outputs
	IntVec depends;          // ones where transition depends on coordinate
	public int sigIdx;              // per-signature index
	
	public Transition( Element t, Map stateBlockMap, TreeMap idMap, HMM hmm ) {
		id = t.getAttributeValue("id");
		Element fromElt = ParseUtils.parseAttribute(t,"from","state",idMap);
		from = fromElt.getAttributeValue("id");
		fromBlock = (String)stateBlockMap.get( from );
		Element toElt = ParseUtils.parseAttribute(t,"to","state",idMap);
		to = toElt.getAttributeValue("id");
		toBlock = (String)stateBlockMap.get( to );
		State toState = (State)hmm.objects.get( to );
		State fromState = (State)hmm.objects.get( from );
		if (toState == null) {
			throw new Error("<transition> element "+id+": Reference to non-existing state "+to);
		}
		if (fromState == null) {
			throw new Error("<transition> element "+id+": Reference to non-existing state "+from);
		}
		Element probabilityElt = ParseUtils.parseAttribute(t,"probability","probability",idMap);
		probability = probabilityElt.getAttributeValue("id");
		Probability pr = new Probability( probabilityElt, idMap, hmm );
		hmm.objects.put( pr.id, pr );
		Element emissionElt = ParseUtils.parseAttributeWithDefault(t,"emission","emission",idMap);
		if (emissionElt == null) {
			// Transition points to Moore state -- check & get emission
			if (toState.emission != null) {
				emission = toState.emission;
				mealy = false;
			} else {
				throw new Error("<transition> element '"+id+"' (from state '"+from+"') has no emission element, and state pointed to ('"+to+"') hasn't got one either");
			}
		} else {
		    // Transition is a Mealy transition -- get emission & check
		    emission = emissionElt.getAttributeValue("id");
		    mealy = true;
		    if (toState.emission != null) {
			if (toState.emission.equals( emission )) {
			    System.out.println("WARNING: <transition> element '"+id+"' (from state '"+from+"') and state pointed to ('"+to+"') both have emission element '"+toState.emission+"' -- emission on transition ignored.");
			} else {
			    throw new Error("<transition> element '"+id+"' (from state '"+from+"') has emission element ('"+emission+"'), but state pointed to ('"+to+"') has one too ('"+toState.emission+"')");
			}
		    }
		}
		if (!hmm.objects.containsKey( emission )) {
		    // Mealy emissions need be added only once
		    emissionElt = (Element)idMap.get( emission );
		    Emission em = new Emission(emissionElt, idMap, hmm);
		    em.order = (int[])fromState.order.clone();
		    hmm.objects.put( em.id, em );
		} else {
		    // Update order of emission; the highest order compatible with all "from"-states
		    Emission em = (Emission)hmm.objects.get(emission);
		    for (int i=0; i<hmm.numOutputs; i++) {
			if (em.order[i] != fromState.order[i]) {
			    if (em.warned[i]==0 || fromState.order[i] < em.order[i]) {
				System.out.println("Warning - emission '"+em.id+
						   "' used from states with varying orders (with respect to <output> '"+
						   hmm.outputs[i]+"'), using lowest (now: "+
						   Math.min( em.order[i], fromState.order[i] )+")");
				em.warned[i]=1;
			    }
			}
			em.order[i] = Math.min( em.order[i], fromState.order[i] );
		    }
		}
	}
    
    public void Setup( IntVec dynProgTable, String language ) {
	
	if (!language.equals("C++")) {
	    throw new Error("Transition: Cannot handle language "+language);
	}
	
    }
	
	public String getFrom(boolean forward) {
		
		if (forward)
			return from;
		else
			return to;
		
	}
	
	public String getTo(boolean forward) {
		
		return getFrom(!forward);
		
	}
	
	public String getFromBlock(boolean forward) {
		
		if (forward)
			return fromBlock;
		else
			return toBlock;
		
	}
	
	public String getSignature(HMM hmm) {
		
		StringBuffer sb = new StringBuffer("");
		State fromState = (State)hmm.objects.get(from);
		for (int i=0; i<hmm.numOutputs; i++) {
			sb.append(String.valueOf(fromState.order[i]));
		}
		return sb.toString();
		
	}
	
}
