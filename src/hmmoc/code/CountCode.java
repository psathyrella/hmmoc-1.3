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



import hmmoc.xml.*;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;


public class CountCode extends Generator {

	// Do not touch these!  Too many things in snippets.xml depend on it
	static String emitLabel = "emission";
	static String transLabel = "transition";
	
    String declare;
    String classdefinition;
    boolean declared = false;
    String hmmid;
    SymbolCode symbolCode;
    boolean countEmissions;
    boolean countTransitions;


    public CountCode( HMM himamo, Book book, SymbolCode symbolCode0, String decl0, String classdef0, String hmmid0, boolean countEmissions0, boolean countTransitions0 ) {

	super (himamo, book, himamo.objects);
	symbolCode = symbolCode0;
	declare = decl0;
	classdefinition = classdef0;
	hmmid = hmmid0;
	countEmissions = countEmissions0;
	countTransitions = countTransitions0;

    }


    void declare() {

	if (declared)
	    return;
	declared = true;

	/*
	if (hmm.mooreStates != 0) {
	    // Wrong: should include multiple alphabets.  Use alphabets from outputs from hmm
	    book.addInitText(declare, c.bind("moore",
					     new Integer(hmm.states.length).toString(),
					     alphabetCode.getSize()) );
	}
	if (hmm.mealyTransitions != 0) {
	    book.addInitText(declare, c.bind("mealy",
					     new Integer(hmm.transitions.length).toString(),
					     alphabetCode.getSize()) );
	}
	*/

	book.addToLinearScope(classdefinition,0,getCode("hmlCountClassA").bind(hmmid));
	book.openLinearScopeAtInit("defineclassfunc",5,"subroutines");
	book.addToLinearScope("defineclassfunc",0,getCode("hmlCountClassBA").bind(hmmid));
	book.addToLinearScope("defineclassfunc",2,getCode("hmlCountClassCA").bind(hmmid));
	book.addToLinearScope("defineclassfunc",4,getCode("hmlCountClassCC").bind(hmmid));
	
	// Make code for alphabet symbols
	//
	// Actually, don't.  It requires initialization code in the .cc file, and good naming;
	// let's not bother.  User just has to sort the alphabet alphabetically.
	/*
	List alphabets = symbolCode.getAlphabets();
	for (int k=0; k<alphabets.size(); k++) {
		Alphabet alphabet = (Alphabet)alphabets.get(k);
		AlphabetCode alphabetCode = new AlphabetCode(hmm, book, alphabet, "", "");
		book.addToLinearScope("defineclassfunc",0,alphabetCode.getInitialization());
		book.addToLinearScope(classdefinition,2,alphabetCode.getDeclaration());
	}
	*/
	
	// Make code for transition counts
	Iterator i = hmm.transSignatures.keySet().iterator();
	if (countTransitions) {
        while (i.hasNext()) {
            String type = transLabel;
            String sign = (String) i.next();
            List transitions = (List) hmm.transSignatures.get(sign);
            Transition tr = (Transition) hmm.objects.get(transitions.get(0));
            State st = (State) hmm.objects.get(tr.from);
            int[] order = st.order;
            String dim = String.valueOf(transitions.size());
            String symloopcode = "";
            String symbolsB = "";
            String symbolsC = "";
            ArrayList ids = new ArrayList(transitions.size());
            for (int j = 0; j < transitions.size(); j++) {
                ids.add(j, new Integer(((Transition) hmm.objects.get((String) transitions.get(j))).number));
            }
            String idents = Generator.makeCommaList(ids);
            for (int j = 0; j < hmm.numOutputs; j++) {
                Output o = (Output) hmm.objects.get(hmm.outputs[j]);
                for (int k = 0; k < order[j]; k++) {
                    String var = "v" + String.valueOf(j) + String.valueOf(k);
                    symloopcode += "for(int " + var + "=0;" + var + "<" + String.valueOf(o.alphabet.getSize()) + ";" + var + "++)";
                    symbolsB += "[" + var + "]";
                    symbolsC += "[" + String.valueOf(o.alphabet.getSize()) + "]";
                }
            }
            book.addToLinearScope("defineclassfunc", 0, getCode("hmlCountClassB").bind(type, sign, dim, symloopcode, symbolsB, idents, hmmid));
            book.addToLinearScope("defineclassfunc", 3, getCode("hmlCountClassCB").bind(type, sign, dim, symloopcode, symbolsB, idents, hmmid));
            book.addToLinearScope(classdefinition, 2, getCode("hmlCountClassC").bind(type, sign, dim, symbolsC));
            book.addToLinearScope("defineclassfunc", 1, getCode("hmlCountClassE").bind(type, sign, dim, hmmid));
        }
    }

	// Make code for emission counts
	i = hmm.emitSignatures.keySet().iterator();
	if (countEmissions) {
	while (i.hasNext()) {
	    String type = emitLabel;
	    String sign = (String)i.next();
	    List emissions = (List)hmm.emitSignatures.get( sign );
	    Emission em = (Emission)hmm.objects.get(emissions.get(0));
	    int[] order = em.order;
	    int[] emitvec = em.outputVec.v;
	    String dim = String.valueOf( emissions.size() );
	    String symloopcode = "";
	    String symbolsB = "";
	    String symbolsC = "";
	    ArrayList ids = new ArrayList(emissions.size());
	    for (int j=0; j<emissions.size(); j++) {
		ids.add(j, new Integer( ((Emission)hmm.objects.get((String)emissions.get(j))).number ) );
	    }
	    String idents = Generator.makeCommaList( ids );
	    for (int j=0; j<hmm.numOutputs; j++) {
		Output o = (Output)hmm.objects.get( hmm.outputs[j] );
		for (int k=0; k<order[j]+emitvec[j]; k++) {
		    String var = "v" + String.valueOf(j)+String.valueOf(k);
		    symloopcode += "for(int "+var+"=0;"+var+"<"+String.valueOf(o.alphabet.getSize())+";"+var+"++)";
		    symbolsB += "["+var+"]";
		    symbolsC += "["+String.valueOf(o.alphabet.getSize())+"]";
		}
	    }
	    book.addToLinearScope("defineclassfunc",0,getCode("hmlCountClassB").bind(type,sign,dim,symloopcode,symbolsB,idents,hmmid));
	    book.addToLinearScope("defineclassfunc",3,getCode("hmlCountClassCB").bind(type,sign,dim,symloopcode,symbolsB,idents,hmmid));
	    book.addToLinearScope(classdefinition,2,getCode("hmlCountClassC").bind(type,sign,dim,symbolsC));
	    book.addToLinearScope("defineclassfunc",1,getCode("hmlCountClassE").bind(type,sign,dim,hmmid));
	}
	}

	book.addToLinearScope("defineclassfunc",0,getCode("hmlCountClassBZ").bind(hmmid));
	book.addToLinearScope("defineclassfunc",1,getCode("hmlCountClassEZ").bind(hmmid));
	book.addToLinearScope(classdefinition,3,getCode("hmlCountClassD").bind(hmmid,
									       String.valueOf(hmm.transitions.length),
									       String.valueOf(hmm.emissions.length)));
	Code c = getCode("hmlInitCount");
	book.addInitText(declare, c.bind(hmmid) );

    }



    // Expression referring to transition counts
    public String getText( Transition t, boolean forward ) {

	declare();
	String sign = t.getSignature(hmm);
	String idx = String.valueOf(t.sigIdx);

	// Next, build string indexing the counter referring to the current state configuration
	String symbols = "";
	Emission emission = (Emission)objects.get( t.emission );
	State from = (State)objects.get( t.from);

	for (int outputIdx=0; outputIdx<emission.outputVec.v.length; outputIdx++) {
	    
	    int emitCoord = emission.outputVec.v[outputIdx];
	    
	    // Baum Welch is done in DP filling mode, so backTrace = false
	    //int offset = EmissionCode.getOffset(forward, false, emitCoord);
	    // New:
	    int offset = forward ? -1 : emitCoord-1;
	    
	    // Loop over all n-th order symbols that this transition depends on (this excludes any emitted symbol)
	    //for (int depth=emitCoord; depth<emitCoord + from.order[outputIdx]; depth++) {
	    for (int depth=emitCoord + from.order[outputIdx] -1; depth>=emitCoord; depth--) {
		
		// Get symbol at this position
		String symbol = symbolCode.getText( outputIdx, depth-offset );
		// Translate symbol into index
		String index = symbolCode.getAlphabetCode(outputIdx).getIndex( symbol );
		// Add index to array specifier
		symbols += "[" + index + "]";
	    }
	}

	return getCode("hmlRefCount").bind(transLabel,sign, symbols, idx).toString();

    }



    // Expression referring to emission counts
    public String getText( Emission e, boolean forward ) {

	declare();
	String sign = e.getSignature(hmm);
	String idx = String.valueOf(e.sigIdx);

	// Next, build string indexing the counter referring to the current state configuration
	String symbols = "";

	for (int outputIdx=0; outputIdx<e.outputVec.v.length; outputIdx++) {
	    
	    int emitCoord = e.outputVec.v[outputIdx];
	    
	    // Baum Welch is done in DP filling mode, so backTrace = false
	    //int offset = EmissionCode.getOffset(forward, false, emitCoord);
	    int offset = forward ? -1 : emitCoord-1;
	    
	    // Loop over all n-th order symbols that this transition depends on, plus any symbol emitted
	    //for (int depth=0; depth<emitCoord + e.order[outputIdx]; depth++) {
	    for (int depth=emitCoord + e.order[outputIdx] -1; depth>=0; depth--) {
		
		// Get symbol at this position
		String symbol = symbolCode.getText( outputIdx, depth-offset );
		// Translate symbol into index
		String index = symbolCode.getAlphabetCode(outputIdx).getIndex( symbol );
		// Add index to array specifier
		symbols += "[" + index + "]";
	    }
	}

	return getCode("hmlRefCount").bind(emitLabel,sign, symbols, idx).toString();

    }


}
