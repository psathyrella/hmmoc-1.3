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
package hmmoc.code;



import java.util.*;

import hmmoc.xml.HMM;
import hmmoc.xml.Code;
import hmmoc.xml.Probability;
import hmmoc.xml.Transition;
import hmmoc.xml.State;
import hmmoc.util.IntVec;




public class TransitionCode extends Generator {

    static String loopId = "iLoop";

    Code trCode;
    TemporariesCode temporariesCode;
    boolean cacheValues;

    public TransitionCode( HMM himamo, Book book, TreeMap objs, TemporariesCode temp0, String declare, String init, boolean cachevalues ) {

	super (himamo, book, objs);

	temporariesCode = temp0;

	cacheValues = cachevalues;

	if (cacheValues) {

	    Code c = getCode("hmlTransitionDeclare");
	    String numTransitions = Integer.toString(hmm.transitions.length);
	    book.addInitText( declare, c.bind( numTransitions ) );

	}
	
	Code c = getCode("hmlTransitionInit");
	Text t = new Text("");
	for (int i=0; i<hmm.transitions.length; i++) {
	    Transition tr = (Transition)objects.get( hmm.transitions[i] );
	    Probability p = (Probability)objects.get( tr.probability );
	    State from = (State)objects.get(tr.from);
	    if (p.activeOutputs() == 0) {
		// Check that all identifiers are listed - even though we're not using them
		for (int outputIdx=0; outputIdx<hmm.numOutputs; outputIdx++) {
		    for (int depth=0; depth<from.order[outputIdx]; depth++) {
			// This will throw an Error if the identifier is not there
			p.canBindInput( outputIdx, depth );
		    }
		}
		// precompute
		if (p.numDependentPositions() == 0 && cacheValues) {
			// Is this also correct when transitions depend on multiple symbols (depth>0)?
			t.append( p.getInitText( objects, temporariesCode ) );
			t.append( c.bind( String.valueOf(i), p.getResult( temporariesCode ) ) );
		}
	    }
	}
	book.addInitText( init, t );

	trCode = getCode("hmlTransition");

    }




    public String getText( Transition tr, Book book, SymbolCode symbolCode, PositionCode positionCode, IntVec emVec, 
			   boolean forward, boolean backTrace ) {

	Probability p = (Probability)objects.get( tr.probability );
	if ((p.activeOutputs() == 0 && p.numDependentPositions()==0) && cacheValues) {

	    // Return reference to precomputed array
	    return trCode.bind( String.valueOf(tr.number) ).toString();

	} else {

	    // Compute transition on the fly
	    State from = (State)objects.get( tr.from );

	    for (int outputIdx=0; outputIdx<hmm.numOutputs; outputIdx++) {

			int emitCoord = emVec.v[outputIdx];
			int offset = (forward == backTrace) ? emitCoord-1 : -1;
			/* int offset = EmissionCode.getOffset(forward,backTrace,emitCoord); */

			if (p.canBindPosition( hmm.outputs[ outputIdx ]) ) {
			    p.bindPosition( outputIdx, "(" + positionCode.getPos( outputIdx ) + ")-" + new Integer(emitCoord).toString());
			}		

			/*
			if (p.canBindPosition( hmm.outputs[ outputIdx ] )) {
		    	if (forward && (!backTrace) && emitCoord==1) {
		    		p.bindPosition( outputIdx, "("+positionCode.getPos( outputIdx )+")-1" );
		    	} else {	
		    		p.bindPosition( outputIdx, positionCode.getPos( outputIdx ) );
		    	}
		    }
			*/

		    /*
			if (p.canBindPosition( hmm.outputs[ outputIdx ] )) {
	    		String position = "(" + positionCode.getPos( outputIdx ) + ") - (" + Integer.toString(offset) + ")";
	    		p.bindPosition( outputIdx, position );
	    	}
	    	*/
		
		// Loop over all positions this transition depends on (this does not include any emitted symbol)
		// (Note that for no emission, order-1 corresponds to depth=0, whereas with emission, it corresponds to depth=1)
		for (int depth=emitCoord; depth<from.order[outputIdx] + emitCoord; depth++) {
		
		    // Do we need to do this position?  (Translate to depth=0 <-> emission, depth>=1 <-> order dependents)
		    if (p.canBindInput( outputIdx, depth+(1-emitCoord) )) {
			// Get symbol at this position
			// Was: depth+offset (with old call to getOffset)
			String symbol = symbolCode.getText( outputIdx, depth-offset );
			// Bind symbol to proper input  (Translate to depth=0 <-> emission, depth>=1 <-> order dependents)
			if (p.hasOutputs()) {
			    p.bindInput( outputIdx, depth+(1-emitCoord), symbol );
			} else {
			    throw new Error("<probability> "+p.id+" of <transition> "+tr.id+" (from <state> "+tr.from+" to "+tr.to+") needs <identifier>s with explicit \"output\" attribute (to bind to e.g. <output> "+hmm.outputs[outputIdx]+"); none such found.");
			}
		    }
		}
	    }

	    book.add( p.getInitText( objects, temporariesCode ) );
	    return "("+p.getResult( temporariesCode )+")";

	}

    }

}


