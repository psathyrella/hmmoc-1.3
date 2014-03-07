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

import hmmoc.util.IntVec;

import hmmoc.xml.HMM;
import hmmoc.xml.State;


//
// isFixedRange( int ) : kijkt of bepaalde coord fixed is of niet; wordt gebruikt (door StateCode?) om te
// kijken of een subtable gefold moet worden of niet
//


public class RangeCode extends Generator {

    // Three possible ranges:
    //     
    // 1. Both relative to start: [from+start, to+start];  for single state to=from
    // 2. Single relative to end    [from+end, to+end];   
    // 3. Variable range, [from+start, to+end]
    // 
    // Note that the range is a closed interval!  Not half-open C-convention.
    //
    // Used to describe 
    // - allowable positions for state
    // - allowable positions for clique
    // - used positions for this retrieval / storage operation (??)

    int numOutputs;                   // from HMM - for convenience

    boolean[] relStartFrom;             // true if first (from) index is relative to start; false if rel. to end
    boolean[] relStartTo;               // true if last  (to) index is relative to start
    public int[] from;
    public int[] to;


    // Default initializer: All, i.e. [start, end] 
    // (Start state: first emitted is symbol at start; end state: first emitted would be symbol at position iSequenceLength, ie
    //  one beyond last symbol)
    public RangeCode( HMM himamo ) {

    	super(himamo);
    	init();

    }


    public RangeCode( HMM himamo, Book book ) {

    	super(himamo, book, himamo.objects);
    	init();

    }



    // Initializer that gets its information from mask and position vectors, as calculated by HMM.analyze
    public RangeCode( HMM himamo, Book book, State state ) {

    	// Allocate and initialize segments
    	this( himamo, book );
    	initFromState( state );

    }


    public RangeCode( HMM himamo, State state ) {

    	this( himamo );
    	initFromState( state );

    }


    // Copy initializer
    public RangeCode( RangeCode range ) {

    	// Allocate and initialize segments
    	
    	// Copy the Generator values:
    	super( range );               
    	// Initialize myself
    	init();
    	// Copy the RangeCode values:
    	initCopy( range );

    }


    // Copy initializer, and includes book
    public RangeCode( RangeCode range, Book book ) {

    	// Allocate and initialize segments
	
    	// Copy the Generator values, and add book:
    	super( range.hmm, book, range.hmm.objects );
    	// Initialize myself
    	init();
    	// Copy the RangeCode values:
    	initCopy( range );

    }


    public boolean isFixedRange( int pos ) {

		return relStartFrom[pos] == relStartTo[pos];

    }
	
	
	public boolean hasSameLoopVariables( RangeCode r ) {
		
		if (r == null)
			return false;
		for (int i=0; i<numOutputs; i++) {
			if (isFixedRange(i) != r.isFixedRange(i))
				return false;
		}
		return true;
		
	}
    
    
    // Emits code that assures that 'position', which is in this range,
    // will be in target Range 'bound' after moving with amount 'offset'.
    //
    // Side effect: this range changes to 'valid' range
    //
    public void checkRange( IntVec offset, RangeCode bound, PositionCode position, String scope) {

    	StringBuffer checkString = rangeString( offset, bound, position, scope, 0, numOutputs );

    	book.addInitText(scope, getCode("hmlConditional").bind(checkString.toString()) );
    	book.addExitText(scope, getCode("hmlConditionalEnd").getText() );

    }

    
    // Emits code that assures that 'position', which is in this range,
    // will be in target Range 'bound' after moving with amount 'offset'.
    //
    // Side effect: this range changes to 'valid' range
    //
    public void checkRange( IntVec offset, RangeCode bound, PositionCode position, String scope, Text fallback) {

    	StringBuffer checkString = rangeString( offset, bound, position, scope, 0, numOutputs );

    	book.addInitText(scope, getCode("hmlConditional").bind(checkString.toString()) );
    	book.addExitText(scope, getCode("hmlConditionalElse").bind(fallback.toString()) );
    
    }


    // Emits code that assures that the 'output'th output of 'position', which is in this range,
    // will be in target Range 'bound' after moving with amount 'offset'.
    //
    // Side effect: this range changes to 'valid' range
    //
    /*
    public void checkRange( IntVec offset, RangeCode bound, PositionCode position, String scope, int output) {

    	StringBuffer checkString = rangeString( offset, bound, position, scope, output, output+1 );

    	book.addInitText(scope, getCode("hmlConditional").bind(checkString.toString()) );
    	book.addExitText(scope, getCode("hmlConditionalElse").getText() );
    
    }
    */
    
    // Enlarges this range to include given range.
    // Used to compute range of clique, given range of constituent states
    public void include( RangeCode range ) {
	
    	for (int i=0; i<numOutputs; i++) {
    		// do from position
    		if (!relStartFrom[i] && !range.relStartFrom[i]) {
    			// both rel. to end; take minimum
    			from[i] = Math.min( from[i], range.from[i] );
    		} else if (!relStartFrom[i] && range.relStartFrom[i]) {
    			// current is rel. from end, given range is rel. from start; copy.
    			// (This assumes positions rel. from start are always before posns rel. to end.
    			//  Generally untrue, but if blocks are defined properly, ranges will come out okay.)
    			relStartFrom[i] = true;
    			from[i] = range.from[i];
    		} else if (range.relStartFrom[i]) {
    			// both are relative to start; take minimum
    			from[i] = Math.min( from[i], range.from[i] );
    		}
    		// Case of current rel. from start and given range rel. to end falls through.

    		// do 'to' position
    		if (relStartTo[i] && range.relStartTo[i]) {
    			// both rel. from start; take maximum
    			to[i] = Math.max( to[i], range.to[i] );
    		} else if (relStartTo[i] && !range.relStartTo[i]) {
    			// current is relative to start, given range is rel. from end; copy.
    			relStartTo[i] = false;
    			to[i] = range.to[i];
    		} else if (!range.relStartTo[i]) {
    			// both are relative to end; take maximum
    			to[i] = Math.max( to[i], range.to[i] );
    		}
    	}
    }

    
    void init() {
    
    	// Copy outputs from HMM
    	numOutputs = hmm.numOutputs;
    	
    	// create default segments
    	relStartFrom = new boolean[ numOutputs ];
    	relStartTo = new boolean[ numOutputs ];
    	from = new int[ numOutputs ];
    	to = new int[ numOutputs ];
 
    	for (int i=0; i<numOutputs; i++) {
    		// Give default values to segments
    		relStartFrom[i] = true;
    		relStartTo[i] = false;
    		from[i] = 0;
    		to[i] = 0;
    	}
    }


    void initAnything() {
	
    	// range corresponding to any position - force check
    	for (int i=0; i<numOutputs; i++) {
    		from[i] = -1;
    		to[i] = 1;
    	}
    }


    void initFromState( State state ) {
	
    	// Copy values from masks
    	for (int i=0; i<numOutputs; i++) {
    		if (state.maskStart.v[i] == 1) {
    			// fixed from start.  
    			if (state.maskEnd.v[i] == 1 && state.end) {
    				// also fixed from end, and end state -> fix from end
    				relStartFrom[i] = relStartTo[i] = false;
    				from[i] = state.posEnd.v[i];
    				to[i] = state.posEnd.v[i];      // single position, eg [-1,-1] = position -1 from end = position iSequenceLength-1
    			} else {
    				relStartFrom[i] = relStartTo[i] = true;
    				from[i] = state.posStart.v[i];
    				to[i] = state.posStart.v[i];    // single position, eg. [0,0] = position 0
    			}
    		} else if (state.maskEnd.v[i] == 1) {
    			// fixed from end
    			relStartFrom[i] = relStartTo[i] = false;
    			from[i] = state.posEnd.v[i];
    			to[i] = state.posEnd.v[i];      // single position, eg [-1,-1] = position -1 from end = position iSequenceLength-1
    		} else {
    			// variable
    			relStartFrom[i] = true;
    			relStartTo[i] = false;
    			from[i] = state.posStart.v[i];
    			to[i] = state.posEnd.v[i];
    		}
    	}
    }

    
    
    void initCopy( RangeCode range ) {
	
    	// Copy ranges from given Range
    	for (int i=0; i<numOutputs; i++) {
    		relStartFrom[i] = range.relStartFrom[i];
    		relStartTo[i] = range.relStartTo[i];
    		from[i] = range.from[i];
    		to[i] = range.to[i];
    	}
    }



    // helper function to build boolean expression in string form
    void appendCheckString( StringBuffer s, String a ) {

    	if (s.length() != 0)
    		s.append("&&");
        s.append("(").append(a).append(")");

    }


    // Returns code that assures that 'position', which is in this range,
    // will be in target Range 'bound' after moving with amount 'offset'.
    //
    // Side effect: this range changes to 'valid' range
    //
    StringBuffer rangeString( IntVec offset, RangeCode bound, PositionCode position, String scope, int fromoutput, int tooutput) {

    	StringBuffer checkString = new StringBuffer("");
    	String offsetString;
    	
    	//System.out.println("from: "+this+" bound: "+bound+" offset: "+offset);
    	
    	for (int i=fromoutput; i<tooutput; i++) {
	    
    		offsetString = Integer.toString(offset.v[i]);

    		// First do lower bound
    		// See if we need runtime check
    		if ((relStartFrom[i] && !bound.relStartFrom[i]) ||    // current relative to start; bound relative to end: always check lower bound
    				(from[i] + offset.v[i] < bound.from[i])) {
    	
    			//System.out.println("Doing lower bound for " +i);
    			
    			// Decide whether target is from start or end position
    			if (bound.relStartFrom[i]) {
    				appendCheckString(checkString,
    						position.getPos(i) + "+" + offsetString + ">=" + Integer.toString(bound.from[i]));
    			} else {
    				appendCheckString(checkString,
    						position.getPos(i) + "+" + offsetString + ">=" + 
    						position.getLen(i) + "+" + Integer.toString(bound.from[i]));
    			}
		
    			// Adjust this range when both sides are compile-time comparable.
    			// (Copy range in case of incomparability?  That might incur fewer run-time overhead)
    			if (relStartFrom[i] == bound.relStartFrom[i]) {

    				from[i] = bound.from[i] - offset.v[i];

    			}
    		}


    		// Then upper bound
    		if ((!relStartTo[i] && bound.relStartTo[i]) ||    // current relative to end; bound relative to start: always check upper bound
    				(to[i] + offset.v[i] > bound.to[i])) {
    			
    			//System.out.println("Doing upper bound for " + i);
    			
    			// Decide whether target is from start or end position
    			if (bound.relStartTo[i]) {
    				appendCheckString(checkString,
    						position.getPos(i) + "+" + offsetString + "<=" + Integer.toString(bound.to[i]));
    			} else {
    				appendCheckString(checkString,
    						position.getPos(i) + "+" + offsetString + "<=" + 
    						position.getLen(i) + "+" + Integer.toString(bound.to[i]));
    			}
    			// Adjust this range when both sides are compile-time comparable.
    			// (Copy range in case of incomparability?  That might incur fewer run-time overhead)
    			if (relStartTo[i] == bound.relStartTo[i]) {

    				to[i] = bound.to[i] - offset.v[i];

    			}
    		}
    		
    		//System.out.println("(done)");
    	
    	}

    	// Generate code
    	if (checkString.length() == 0) {
    		checkString.append(getCode("hmlTrue").getText().toString() );
    	}

    	return checkString;

    }


    
    // for debugging
    public String toString() {
	
    	StringBuffer s = new StringBuffer("Range[(");
    	for (int i=0;i<numOutputs;i++) {
    		if (i!=0) s.append(",");
    		if (relStartFrom[i]) s.append("start+"); else s.append("end-");
    		s.append(Integer.toString(Math.abs(from[i])));
    	}
    	s.append("),(");
    	for (int i=0;i<numOutputs;i++) {
    		if (i!=0) s.append(",");
    		if (relStartTo[i]) s.append("start+"); else s.append("end-");
    		s.append(Integer.toString(Math.abs(to[i])));
    	}
    	s.append(")]");
    	return s.toString();
    	
    }


    // Calculate variable output coordinates.
    // Code: 0=single fixed coordinate; 1=multiple from start, 2=multiple from end, 3=multiple with variable length
    public IntVec getMask() {

    	IntVec mask = new IntVec( numOutputs );

    	for (int i=0; i<numOutputs; i++) {

    		if ( (relStartFrom[i] == relStartTo[i]) && (from[i]>=to[i]) )
    			mask.v[i] = 0;
    		else if ( relStartFrom[i] && relStartTo[i] )
    			mask.v[i] = 1;
    		else if ( !relStartFrom[i] && !relStartTo[i] )
    			mask.v[i] = 2;
    		else
    			mask.v[i] = 3;

    	}

    	return mask;

    }
	    

    public IntVec getStart() {

    	return new IntVec(from);

    }


    public IntVec getEnd() {

    	return new IntVec(to);

    }



}
