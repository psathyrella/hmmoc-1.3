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

import hmmoc.util.IntVec;
import hmmoc.xml.HMM;
import hmmoc.xml.Code;
import hmmoc.xml.Clique;
import hmmoc.xml.Output;
import hmmoc.xml.Banding;


//
// emitlooptext: voeg initialisatiecode voor prevslowestoutput toe
//

public class PositionCode extends Generator {
	
	
	public RangeCode range;
	PositionCode outerPosition;
	boolean forward;
	boolean gotUsed = false;
	boolean pastBandedClique;
	String bandingId;
	Banding banding = null;     // initialized only for banding
	Banding linkedbanding = null;
	Text positioned = null;     // used to keep track of initialization of previous-column variable, 
	                            // used for clearing folded memory; see StateCode.
	
	
	public PositionCode( HMM himamo, Book book, TreeMap objs ) {
		super (himamo, book, objs);
	}
	

	// this is for an 'inner' non-banded loop which is linked to a banded loop
	public void init( PositionCode outerPositionCode, boolean fw, String position, Clique clique, boolean pastBandedClique0 ) {

		range = clique.range;
		outerPosition = outerPositionCode;
		forward = fw;
		banding = clique.banding;                // null
		linkedbanding = clique.linkedbanding;
		bandingId = clique.linkedbanding.getBandingIdentifier();
		if( banding != null ) {throw new Error("");}
		pastBandedClique = pastBandedClique0;
		
		emitLoopText( book, position, clique, range );
		emitEndText( book, position, clique, range );
		
	}
	
	
	public void init( RangeCode range0, boolean fw, String position, Clique clique ) {
		
		range = range0;
		forward = fw;
		if (clique != null && clique.linkedbanding!= null) {
			banding = clique.linkedbanding;
		}
		emitLoopText( book, position, clique, range );
		emitEndText( book, position, clique, range );
		
	}
	
	
	public void init() {
		
		// used by GetSample
		
	}
	
		
	
	Text getGenLoopText(String idLoop, String idSingle, String idInner) {
		
		Text t = new Text("");
		IntVec start = range.getStart();
		IntVec end = range.getEnd();
		Code c;
		// HMM has already ordered the outputs from fastest (0, shortest, inner loop) to slowest (longest, outer loop)
		for (int i=hmm.numOutputs-1; i>=0; --i) {
			Output o = (Output)objects.get(hmm.outputs[i]);
			String startString = "";
			String endString = "";
			if ((range.relStartFrom[i] == range.relStartTo[i]) && (start.v[i] == end.v[i])) {
				// Single position
				c = getCode(idSingle);
			} else {
				// Actual loop
				c = getCode(idLoop);
			}
			// If offsets are not relative to origin, but to end, add sequence length
			if (!range.relStartFrom[i])
				startString = o.lenId + "+";
			if (!range.relStartTo[i])
				endString = o.lenId + "+";
			// See if this position corresponds to a linked loop
			if (outerPosition != null && i==hmm.numOutputs-1) {
				// add inner loop code
				String pos = outerPosition.getPos(i);
				c = getCode( idInner );
				t.append( c.bind( o.getPosId(),
						          pos,
						          startString + Integer.toString(start.v[i]),
						          endString + Integer.toString(end.v[i] + 1),
						          bandingId ) );
			} else {
				// add normal code
				t.append( 	c.bind(  o.getPosId(), 
								 startString + Integer.toString(start.v[i]),
								 endString + Integer.toString(end.v[i] + 1)       
							).toString() 
				    );
			}
		}
		return t;
	}
	
	
	public int getSlowestOutput() {
		
		IntVec start = range.getStart();
		IntVec end = range.getEnd();
		int slowestOutput = 0;
		// HMM has already ordered the outputs from fastest (0, shortest, inner loop) to slowest (longest, outer loop)
		for (int i=0; i<hmm.numOutputs; ++i) {
			
			if ((range.relStartFrom[i] == range.relStartTo[i]) && (start.v[i] == end.v[i])) {
				// Single position
			} else {
				// Actual loop
				slowestOutput = i;
			}
		}
		return slowestOutput;
	}
	
	
	void emitBandingLoopText(Book book, String position, Clique clique, RangeCode ranges) {
		
		String idLoop;
		if (forward) idLoop="hmlForwardBandingLoop"; else idLoop="hmlBackwardBandingLoop";
		
		String dim = banding.getDim();
		
		String bandingIdentifier = banding.getBandingIdentifier();
		
		// add loop code
		Code c = getCode( idLoop );
		book.addInitText( position, c.bind( dim, bandingIdentifier, getPos( getSlowestOutput() ) ) );
		
		// make range corresponding to all positions, and then some; i.e. make no
		// assumptions about the range of positions that the Banding element traverses
		RangeCode range = new RangeCode( hmm, book );
		range.initAnything();
		c = getCode( "hmlOutOfRangeWarning" );
		range.checkRange( new IntVec(hmm.numOutputs), ranges, this, position, c.bind( bandingIdentifier ) );
		
		// close loop code
		c = getCode( idLoop + "End" );
		book.addExitText( position, c.bind( dim, bandingIdentifier ) );
		
	}
	
	
	// clique==null for sampling
	void emitEndText( Book book, String position, Clique clique, RangeCode range ) {
		
		if (banding==null) {
			if (forward)
				book.addExitText(position, getGenLoopText("hmlLoopEnd1","hmlSingleEnd1","hmlInnerLoopEnd"));
			else
				book.addExitText(position, getGenLoopText("hmlLoopEnd1Backward","hmlSingleEnd1Backward","hmlInnerLoopEnd"));
		}
		
	}
	
	
	void emitLoopText( Book book, String position, Clique clique, RangeCode range) {

	    // add code to track slowest variable - but only for the outer position code
		if (outerPosition == null) {
			Text t = getCode( "hmlResetSlowCoord" ).getText();
			book.addInitText( position, t );
		}

		String linkedBanding = pastBandedClique ? "hmlInnerLoopStartPastBandedClique" : "hmlInnerLoopStart";
		
		if (banding==null) {
			if (forward)
				book.addInitText( position, getGenLoopText("hmlLoopStart1","hmlSingleStart1",linkedBanding));
			else
				book.addInitText( position, getGenLoopText("hmlLoopStart1Backward","hmlSingleStart1Backward",linkedBanding));
		} else {
			emitBandingLoopText(book, position, clique, range);
		}
		
	}
	
	
	public String getPos(int i) {
		
		// See if this position corresponds to a linked loop

		gotUsed = true;
		if (banding != null) {
		    // get index corresponding to output
		    return banding.getPosId( hmm.outputs[i] );
		}
		
		// The linked banding position and the inner position give the same result
		// However, clearing a column is done outside the inner position scope, since
		//  otherwise a second slow variable must be implemented.  Only the linked
		//  banded position is available outside the inner loop
		if (outerPosition != null && i==hmm.numOutputs-1 && linkedbanding != null)  {
		    return linkedbanding.getPosId( hmm.outputs[i] );
		}
		
		Output o = (Output)objects.get(hmm.outputs[i]);
		return o.getPosId();

	}
	
	
	/*
	 // version for sampler
	  public String getNonbandingPos(int i) {
	  
	  gotUsed = true;
	  Output o = (Output)objects.get(hmm.outputs[i]);
	  return o.getPosId();
	  
	  }
	  */
	
	public String getLen(int i) {
		
		Output o = (Output)objects.get(hmm.outputs[i]);
		return o.lenId;
		
	}
	
	public String getSeq(int i) {
		
		Output o = (Output)objects.get(hmm.outputs[i]);
		return o.seqId;
		
	}
	
	public void exit() {
		
		// Could make sure no unused loops or position references are output
		
	}
	
}

