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

import hmmoc.code.RangeCode;
import hmmoc.code.Generator;
import hmmoc.util.Edge;
import hmmoc.util.Graph;
import hmmoc.util.IntVec;
import hmmoc.util.ParseUtils;
import org.jdom.Element;

import java.util.*;



public class Clique extends XmlElement {
	
	public String id;
	
	public ArrayList states = new ArrayList();      // states constituting this block (identifiers)
	public ArrayList inTrans = new ArrayList();     // incoming transitions (identifiers)
	public ArrayList selfTrans = new ArrayList();   // transitions staying in block (any emission!)
	public ArrayList outTrans = new ArrayList();    // outgoing transitions
	
	public int numOutputs;                          // total number - including unused ones
	public int stateOffset;                         // offset of global state number for this block
	
	public RangeCode range;
	public Banding banding;
	public Banding linkedbanding;                   // band to synchronize slowest coordinate with 
    public Code dptable;
	
	// Rest is transient data, is made with knowledge of currently active outputs
	public ArrayList selfEmittingTrans;             // emitting transitions, staying in block
	public ArrayList selfSilentTrans;               // nonemitting transitions, staying in block
	
	public Clique( Element elem, int numOutputs0, TreeMap idMap, HMM hmm ) {
		
		id = elem.getAttributeValue("id");
		numOutputs = numOutputs0;
		
		List bandings = ParseUtils.parseChildren( elem, "banding", idMap );
		if (bandings.size() > 1) {
			throw xmlError( elem, "<block>: found more than 1 <banding> element");
		}
		if (bandings.size() == 1) {
			String bandingid = ((Element)(bandings.get(0))).getAttribute("id").getValue();
			Element bandingElt = (Element)idMap.get( bandingid );
			if (bandingElt == null) {
				throw xmlError(elem, "<block>: could not find <banding> element with id="+bandingid);
			}
			linkedbanding = banding = (Banding)hmm.objects.get( bandingid );
			if (banding == null) {
			    linkedbanding = banding = new Banding( bandingElt, idMap, hmm );
			    hmm.objects.put( bandingid, banding );
			}
		}

		Element linkedbandingElt = ParseUtils.parseAttributeWithDefault(elem,"linkedbanding","banding",idMap);
		if (linkedbandingElt != null) {
			
			if (banding != null) {
				throw xmlError( elem, "Cannot both specify a 'linkedbanding' attribute when a <banding> element is present");
			}
			linkedbanding = (Banding)hmm.objects.get( linkedbandingElt.getAttributeValue("id") );
			if (linkedbanding == null) {
			    linkedbanding = new Banding( linkedbandingElt, idMap, hmm );
			    hmm.objects.put( linkedbandingElt.getAttributeValue("id"), linkedbanding );
			}
		}

		List codes = elem.getChildren("code");
		if (codes.size() == 0) {
		    // set default
		    dptable = Generator.getCode("DPTable", hmm.objects);
		} else if (codes.size() == 1) {
		    dptable = (Code)hmm.objects.get( ((Element)codes.get(0)).getAttributeValue("id"));
		} else {
		    throw xmlError( elem, "More than 1 <code> element specifying DP table type found (0 or 1 expected)" );
		}
		
	}
	
	
	public void initRange(HMM hmm) {
		
		for (int i=0; i<states.size(); i++) {
			State s = (State)(hmm.objects.get(states.get(i)));
			if (i==0) {
				range = new RangeCode(s.range);
			} else {
				range.include( s.range );
			}
		}
	}
	
	
	List getTransitions(boolean forward) {
		// Very simple implementation - but let's first get it to work.
		ArrayList l;
		if (forward) {
			l = new ArrayList(inTrans);
		} else {
			l = new ArrayList(outTrans);
		}
		l.addAll(selfTrans);
		return l;
	}
	
	
	// Sort non-emitting states within block into linearly ordered components.
	List selfComponents(boolean forward, TreeMap objects) {
		ArrayList stateGraph = new ArrayList();
		for (int i=0; i<selfTrans.size(); i++) {
			String ts = (String)selfTrans.get(i);
			Transition t = (Transition)objects.get(ts);
			Emission e = (Emission)objects.get(t.emission);
			// zero emission?
			if (e.outputVec.isZero()) {
				// edge from state (identifier) to state (identifier)
				stateGraph.add( new Edge( t.from, t.to ) );
			}
		}
		// Sort graph.
		List selfC = Graph.sortGraph( stateGraph );
		if (!forward)
			Collections.reverse( selfC );
		return selfC;
	}
	
	
	// Returns list of all transitions that come from a non-emitting state within block, and go to specified state
	List selfStateTransitions(String state, boolean forward, TreeMap objects) {
		
		ArrayList transList = new ArrayList();
		for (int i=0; i<selfTrans.size(); i++) {
			String ts = (String)selfTrans.get(i);
			Transition t = (Transition)objects.get(ts);
			Emission e = (Emission)objects.get(t.emission);
			// zero emission?
			if (e.outputVec.isZero()) {
				// does this transition go the specified state?
				if (t.getTo(forward).equals( state )) {
					transList.add( t );
				}
			}
		}
		return transList;
	}
	
	
	// Returns array of unique emission vectors (zero vector last)
	public IntVec[] getEmissionVectors(boolean forward,TreeMap objects) {
		
		HashSet emVecs = new HashSet(0);
		boolean hasZero = false;
		Iterator i = getTransitions(forward).iterator();
		while (i.hasNext()) {
			Transition t = (Transition)objects.get(i.next());
			Emission e = (Emission)objects.get(t.emission);
			// Treat zero vector as special case - to put it at end of list
			if (e.outputVec.isZero())
				hasZero = true;
			else
				emVecs.add( e.outputVec );
		}
		List emVecList = new ArrayList( emVecs );
		if (hasZero)
			emVecList.add( new IntVec( numOutputs ) );
		return (IntVec[])emVecList.toArray(new IntVec[0]);
	}
	
	
	// Returns array of emissions with given emission vector, going to this block
	public Emission[] getEmissions(boolean forward,IntVec emission, TreeMap objects) {
		
		HashSet ems = new HashSet(0);
		Iterator i = getTransitions(forward).iterator();
		while (i.hasNext()) {
			Transition t = (Transition)objects.get(i.next());
			Emission e = (Emission)objects.get(t.emission);
			if (e.outputVec.equals( emission )) {
				ems.add( e );
			}
		}
		return (Emission[])ems.toArray(new Emission[0]);
	}
	
	
	// Returns array of blocks that are emission away from current block (self block last)
	public Clique[] getFromBlocks(boolean forward, IntVec emission, TreeMap objects) {
		
		HashSet fromBlocks = new HashSet(0);
		boolean hasSelf = false;
		Iterator i = getTransitions(forward).iterator();
		while (i.hasNext()) {
			Transition t = (Transition)objects.get(i.next());
			Emission e = (Emission)objects.get(t.emission);
			if (e.outputVec.equals( emission )) {
				Clique from = (Clique)objects.get( t.getFromBlock( forward) );
				if (from == this)
					hasSelf = true;
				else
					fromBlocks.add( from );
			}
		}
		List fromBlockList = new ArrayList( fromBlocks );
		if (hasSelf) {
			fromBlockList.add( this );
		}
		return (Clique[])fromBlockList.toArray(new Clique[0]);
	}
	
	
	// Returns array of states that are receiving from given emission and given clique
	public State[] getToStates(boolean forward, IntVec emission, Clique clique, TreeMap objects) {
		
		HashSet coll = new HashSet(0);
		Iterator i = getTransitions(forward).iterator();
		while (i.hasNext()) {
			Transition t = (Transition)objects.get(i.next());
			Emission e = (Emission)objects.get(t.emission);
			if ((e.outputVec.equals( emission )) && 
					(forward && (t.fromBlock.equals(clique.id)) || (!forward && (t.toBlock.equals(clique.id))))) {
				
				coll.add( objects.get(t.getTo(forward)) );
				
			}
		}
		return (State[])coll.toArray(new State[0]);
	}
	
	
	// Returns array of states that are receiving from given emission, and are themselves in given clique
	public State[] getToStates(IntVec emission, Clique clique, TreeMap objects) {
		
		HashSet coll = new HashSet(0);
		Iterator i = getTransitions(true).iterator();
		while (i.hasNext()) {
			Transition t = (Transition)objects.get(i.next());
			Emission e = (Emission)objects.get(t.emission);
			if ((e.outputVec.equals( emission )) && (t.toBlock.equals(clique.id))) {
				
				coll.add( objects.get(t.from) );
				
			}
		}
		return (State[])coll.toArray(new State[0]);
	}
	
	
	
	
	// Returns array of states that are receiving from given emission and given clique
	public State[] getFromStates(boolean forward, IntVec emission, Clique clique, State to, TreeMap objects) {
		
		HashSet coll = new HashSet(0);
		Iterator i = getTransitions(forward).iterator();
		while (i.hasNext()) {
			Transition t = (Transition)objects.get(i.next());
			Emission e = (Emission)objects.get(t.emission);
			if ((e.outputVec.equals( emission )) && 
					((forward && (t.fromBlock.equals(clique.id))) || (!forward && (t.toBlock.equals(clique.id)))) &&
					((forward && (t.to.equals(to.id))) || (!forward && (t.from.equals(to.id))))) {
				
				coll.add( objects.get(t.getFrom(forward)) );
				
			}
		}
		return (State[])coll.toArray(new State[0]);
	}
	
	
	
	// Returns array of states that are receiving from given emission, and are themselves in given clique
	public State[] getFromStates(IntVec emission, Clique clique, State to, TreeMap objects) {
		
		HashSet coll = new HashSet(0);
		Iterator i = getTransitions(true).iterator();
		while (i.hasNext()) {
			Transition t = (Transition)objects.get(i.next());
			Emission e = (Emission)objects.get(t.emission);
			if ((e.outputVec.equals( emission )) && 
					(t.toBlock.equals(clique.id)) &&
					(t.from.equals(to.id))) {
				
				coll.add( objects.get(t.to) );
				
			}
		}
		return (State[])coll.toArray(new State[0]);
	}
	
	
	
	
	// Returns array of states that are receiving from given emission and given clique
	public Transition[] getFromToTransitions(boolean forward, IntVec emission, Clique clique, State to, State from, TreeMap objects) {
		
		HashSet coll = new HashSet(0);
		Iterator i = getTransitions(forward).iterator();
		while (i.hasNext()) {
			Transition t = (Transition)objects.get(i.next());
			Emission e = (Emission)objects.get(t.emission);
			if ((e.outputVec.equals( emission )) && 
					((forward && (t.fromBlock.equals(clique.id))) || (!forward && (t.toBlock.equals(clique.id)))) &&
					(t.getTo(forward).equals(to.id)) &&
					(t.getFrom(forward).equals(from.id))) {
				
				coll.add( t );
				
			}
		}
		return (Transition[])coll.toArray(new Transition[0]);
	}
	
	
	// Returns array of states that are receiving from given emission and given clique
	public Transition[] getFromToTransitions(IntVec emission, Clique clique, State to, State from, TreeMap objects) {
		
		HashSet coll = new HashSet(0);
		Iterator i = getTransitions(true).iterator();
		while (i.hasNext()) {
			Transition t = (Transition)objects.get(i.next());
			Emission e = (Emission)objects.get(t.emission);
			if ((e.outputVec.equals( emission )) && 
					(t.toBlock.equals(clique.id)) &&
					(t.from.equals(to.id)) &&
					(t.to.equals(from.id))) {
				
				coll.add( t );
				
			}
		}
		return (Transition[])coll.toArray(new Transition[0]);
	}
	
	
	
	// Calculates start position for this block.
	// Start position of blocks preceding this must be initialized.
	public IntVec getStart( TreeMap objects ) {
		
		return range.getStart();
		
	}
	
	//	 And, do reverse for transitions leaving this block
	public IntVec getEnd( TreeMap objects ) {
			
		return range.getEnd();
		
	}
	
	
	// Calculate variable output coordinates for this block.  
	// Code: 0=single fixed coordinate; 1=multiple from start, 2=multiple from end, 3=multiple with variable length
	public IntVec getMask( TreeMap objects ) {
		
		return range.getMask();
		
	}
	
}
