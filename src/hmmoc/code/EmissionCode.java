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
import hmmoc.xml.Emission;
import hmmoc.xml.Clique;

import hmmoc.util.IntVec;


public class EmissionCode extends Generator {

    int maxNumEmissions;
    String declare;
    boolean forward;
    boolean backTrace;
    Emission[] emArr;
    TemporariesCode temporariesCode;
    boolean cacheValues;

    public EmissionCode( HMM himamo, Book book, TreeMap objs, boolean forward0, boolean backTrace0, String declare0, boolean cachevalues ) {

        super (himamo, book, objs);

        declare = declare0;
        forward = forward0;
        backTrace = backTrace0;
	cacheValues = cachevalues;
        maxNumEmissions = 0;

    }


    // The offset 1 reflects the fact that the symbol determining this state was emitted by a previous
    // transition, and is associated to the position one before the position of the current state - for FW and BW.
    // For sampling and Viterbi backtrace however, the probability is "pushed" to the new state, not "pulled" from 
    // one previously computed.

    /*
    public static int getOffset(boolean forward, boolean backTrace, int emissionComponent) {
        int pushpull = (backTrace ? 0 : 1);
        return (forward ? pushpull : pushpull-emissionComponent);
    }
    */

    
    public void bindProbability(Probability p, SymbolCode symbolCode, PositionCode positionCode, Emission em ) {
	
	int symbolCounter = 0;
	
	for (int outputIdx=0; outputIdx<em.outputVec.v.length; outputIdx++) {
	    
	    int emissionCoord = em.outputVec.v[outputIdx];
	    
	    /*
	      if (p.canBindPosition( hmm.outputs[ outputIdx ] )) {
	      if (forward && (!backTrace) && emissionCoord==1) {
	      p.bindPosition( outputIdx, "("+positionCode.getPos( outputIdx )+")-1" );
	      } else {
	      p.bindPosition( outputIdx, positionCode.getPos( outputIdx ) );
	      }
	      }
	    */
	    
	    // NEW
	    if (p.canBindPosition( hmm.outputs[ outputIdx ]) ) {
		int offset = (forward == backTrace) ? 0 : emissionCoord;
		p.bindPosition( outputIdx, "(" + positionCode.getPos( outputIdx ) + ")-" + new Integer(offset).toString());
	    }		
	    
	    /*
	      int offset = getOffset(forward, backTrace, emissionCoord);
	    */
	    // NEW
	    // Adjust by the emission length for BW recursions, or for FW recursions when back tracing
	    // Always adjust by -1, since coordinate of arrival position corresponds to not-yet-emitted symbol
	    int offset = (forward == backTrace) ? emissionCoord-1 : -1;
	    int maxDepth = em.order[outputIdx] + emissionCoord;
	    
	    // Loop over actually emitted symbol, and all n-th order symbols that this emission depends on
	    // (Note that for no emission, 'order 1' corresponds to depth=0, whereas with emission, it corresponds to depth=1)
	    for (int depth=0; depth<maxDepth; depth++) {
		
		// Get symbol at this position (last parameter of getText is negative position offset)
		String symbol = symbolCode.getText( outputIdx, depth-offset );
		// Bind symbol to proper input
		if (p.hasOutputs()) {
		    // Translate to depth=0 <-> emission, depth>=1 <-> order dependents
		    // Do not require dependents to be present (but do require emissions to be present)
		    if ((depth < emissionCoord) || (p.canBindInput( outputIdx, depth - emissionCoord + 1))) {
			p.bindInput( outputIdx, depth - emissionCoord + 1, symbol );
		    }
		} else {
		    // p.bindInput( symbolCounter, symbol );
		    
		    // This was used for implicit binding of outputs, in the order given in the <hmm> specification.
		    // However, doesn't work if there are higher-order states; e.g. empty emission with a dependence
		    // will now need to be bound...  So, silently ignore for now, and report error because of non-bound
		    // identifiers.
		}
		symbolCounter += 1;
	    }
	}
    }
    
    
    
    public void init( IntVec emVec, Clique toClique, SymbolCode symbolCode, TemporariesCode tempCode, 
	              PositionCode positionCode, String init ) {
	
	temporariesCode = tempCode;
	
	emArr = toClique.getEmissions(forward,emVec,objects);
	if (emArr.length == 0)
	    return;
	
	Code c = getCode("hmlEmissionInit");
	for (int i=0; i<emArr.length; i++) {
	    
	    // Compute current emission
            Probability p = (Probability)objects.get( emArr[i].probability );
            if (p == null) {
                throw new Error("<emission> element "+emArr[i].id+" refers to nonexistent <probability> element "+emArr[i].probability);
            }
            p.reset();
	    
	    bindProbability( p, symbolCode, positionCode, emArr[i] );
	    
	    if (cacheValues) {
		// position-independent; pre-compute
		// Now write code.  First emit code that actually calculates the probability
		book.addInitText(init, p.getInitText( objects, temporariesCode ));
		// Then bind reference to result to emission initialization code and emit resulting code
		book.addInitText( init, c.bind( String.valueOf(i), p.getResult( temporariesCode ) ) );
	    }
        }
	
        if (maxNumEmissions < emArr.length)
            maxNumEmissions = emArr.length;
	
    }


    public String getText( Emission em, PositionCode positionCode, SymbolCode symbolCode, TemporariesCode tempCode, String init ) {
    
        Probability p = (Probability)objects.get( em.probability );
        p.reset();

	for (int i=0; i<emArr.length; i++) {
	    if (emArr[i] == em) {
		
		if (!cacheValues) {
	    
		    bindProbability( p, symbolCode, positionCode, em );
		    book.addInitText( init, p.getInitText( objects, temporariesCode ));
		    return p.getResult( temporariesCode );
		    
		} else {

		    // return pre-computed value
		    return getCode("hmlEmission").bind( String.valueOf(i) ).toString();
	    
		}
	    }
	}

	throw new Error("INTERNAL ERROR: Could not find emission "+em.id+" among "+emArr.length+" emissions.");
	    
    }



    public void exit() {

        Code c;
        String num;
	
	if (cacheValues) {
	    c = getCode("hmlEmissionDeclare");
	    num = String.valueOf(maxNumEmissions);
	    book.addInitText( declare, c.bind( num ) );
	}
    }

}


