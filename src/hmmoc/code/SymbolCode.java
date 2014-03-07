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
import hmmoc.xml.Alphabet;
import hmmoc.xml.Code;
import hmmoc.xml.HMM;
import hmmoc.xml.Output;

import java.util.*;


//
// This class deals with retrieving symbols from the sequence
// It should be created only once per function
// Upon exit, as many temporary variables are declared as necessary
// Upon init, previously used temporary variables lose their content, and arbitrary symbols within a given Range may be accessed.
//  Symbol values are computed in an initialization block.
//


public class SymbolCode extends Generator {

    static String sequenceId = "sequence";

    int maxNumSymbols;
    int curNumSymbols;
    IntVec emVec;
    String declare;
    String init;
    AlphabetCode[] alphabetCode;
    RangeCode curRangeCode;
    PositionCode positionCode;
    TreeMap symbolMap;

    public SymbolCode( HMM himamo, Book book, TreeMap objs, String declare0, String init0 ) {

        super (himamo, book, objs);

        declare = declare0;
        maxNumSymbols = 0;
        curNumSymbols = 0;
        symbolMap = new TreeMap();

        TreeMap alphabets = new TreeMap();
        alphabetCode = new AlphabetCode[ hmm.numOutputs ];
        for (int i=0; i<hmm.numOutputs; i++) {
            Output o = (Output)objects.get( hmm.outputs[i] );
            Alphabet a = o.alphabet;
            if (!alphabets.containsKey(a.id)) {
                alphabets.put(a.id, new AlphabetCode(hmm,book,a,declare0,init0) );
            }
            alphabetCode[i] = (AlphabetCode)alphabets.get(a.id);
        }

    }


    // Clears temporary variables
    public void init( PositionCode positionCode0, RangeCode curRangeCode0, String init0 ) {

        symbolMap.clear();
        curRangeCode = curRangeCode0;
        positionCode = positionCode0;
        init = init0;
        curNumSymbols = 0;
    }


    // Returns code identifier of sequence, by global output identifier [internal]
    String getSequence( int id ) {

        return positionCode.getSeq( id );

    }


    // Returns code evaluating to sequence symbol, by global output identifier and position code. [internal]
    String getSequenceSymbol( int id, String pos ) {

        return getSequence(id) + "[" + pos + "]";

    }


    String getSymbolLabel( int output, int depth ) {

        // Build label identifying symbol
        String label = String.valueOf(output)+":"+String.valueOf(depth);
        if (symbolMap.containsKey(label))
            return label;

        // Compute offset for symbol, and build offset vector; also build RangeCode representing sequence range
        // See TransitionCode for remarks on how the offset depends on forward/backward and fillin/traceback.
        int offset = -depth;
        IntVec range = new IntVec(hmm.numOutputs);
        range.v[output] = offset;
        RangeCode seqRange = new RangeCode(hmm);       // sequence range.

        // change default range [start,end] to [start,end), i.e. last state
        // will not emit a symbol.  Do not check range for other symbols!
        seqRange.to[output] = -1;                     

        // Get bits and pieces of code
        String pos = positionCode.getPos(output) + "+" + String.valueOf(offset);
        String symbol = getSequenceSymbol( output, pos );
        String defaultSymbol = getCode("hmlSymbolDummyValue").bind( alphabetCode[output].getDummy() ).toString();
        Text t = getCode("hmlSymbolInit").bind( String.valueOf(curNumSymbols), symbol );
        Text tFallback = getCode("hmlSymbolInit").bind( String.valueOf(curNumSymbols), defaultSymbol );

        // Now include boundary checks, and emit code.  Make a copy of the range code, since we do not re-use the scope (i.e. no nested checks here)
        RangeCode tempRangeCode = new RangeCode(curRangeCode, book);
        book.openScopeAtInit( "symbolrangecheck", init );
        tempRangeCode.checkRange( range, seqRange, positionCode, "symbolrangecheck", tFallback );
        book.add( t );
        book.closeScope( "symbolrangecheck");

        // Add label and index to map
        symbolMap.put(label, String.valueOf(curNumSymbols) );

        // Finally, increase symbol counter, and check whether we need more temporary storage
        curNumSymbols += 1;
        if (curNumSymbols > maxNumSymbols)
            maxNumSymbols = curNumSymbols;

        // Finished
        return label;
    }


    AlphabetCode getAlphabetCode( int output ) {

        return alphabetCode[output];

    }

    public List getAlphabets() {

        Set alphabets = new HashSet();
        for (int output=0; output < hmm.numOutputs; output++) {
            alphabets.add( getAlphabetCode(output).alphabet);
        }
        List alphabetList = new ArrayList();
        alphabetList.addAll( alphabets );
        return alphabetList;

    }

    // Returns code evaluating to sequence symbol, by global output identifier
    public String getText( int output, int depth ) {

        String label = getSymbolLabel( output, depth );
        String index = (String)symbolMap.get( label );
        Code c = getCode("hmlSymbol");
        return c.bind( index ).toString();
    }



    public void exit() {

        Code c;
        String num;
        c = getCode("hmlSymbolDeclare");
        num = Integer.toString(maxNumSymbols);
        book.addInitText( declare, c.bind( num ) );
    }

}


