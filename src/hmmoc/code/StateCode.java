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
import hmmoc.xml.*;

import java.util.ArrayList;
import java.util.TreeMap;


//
// add: folded; twocolumns wordt gebruikt om de class te definiteren; folded wordt gebruikt om sub-tables
//  te maken; als de langzaamste coordinaat niet loopt, wordt de tabel niet gefold.
//
// breid setposvar uit; doe dit eens per position instance, niet per tostate
//

public class StateCode extends Generator {
    
    static final String loopId = "iLoop";
    
    Clique clique;
    PositionCode position;
    TemporariesCode temporary;             // long-exponent temporary variable
    String define;
    String definemember;
    String init;
    String declare;
    String stateVector;
    String positionScope;
    String clearTableScope;
    Text defined;
    Text inited;
    Text declared;
    Text stateVectored;
    Text intermediated;
    Text cleared;
    String constMode;
    String twocolMode;
    StateCode toState;
    IntVec emissionVec;            // ??? Zero, now.
    String stateMode;
    boolean forward;
    boolean twocolumns;
    boolean fold;
    boolean noinit;
    String secondary;
    Code dptable;
	
	
    public StateCode( HMM himamo, Book book, TreeMap objs, Clique block0, String declare0, boolean forward0, 
		      boolean fold0, boolean twocolumns0, boolean noinit0 ) {
		
		super (himamo, book, objs);
		clique = block0;
		declare = declare0;     // where to put declaration of temporary pointer             
		forward = forward0;
		twocolumns = twocolumns0;
                fold = fold0;
		noinit = noinit0;
		declared = null;
		intermediated = null;
		dptable = block0.dptable;
		if (twocolumns) {
		    twocolMode = "Folded";
		} else {
		    twocolMode = "";
		}
		
	}
	
	
	// First-time init; used to declare and access states that are being updated
	// If define==null, signal not to create class definition, or create/initialize DP table......
	public void initTo( PositionCode pos0, TemporariesCode temp0, String define0, String definemember0, String init0, String stateVector0, String positionScope0, String clearTableScope0 ) {
		
		position = pos0;
		temporary = temp0;
		toState = this;                                  // to locate defined, inited.
		define = define0;                                // points to linear scope
		definemember = definemember0;
		init = init0;
		stateVector = stateVector0;
		positionScope = positionScope0;
		clearTableScope = clearTableScope0;
		defined = stateVectored = inited = null;
		emissionVec = new IntVec(hmm.numOutputs);        // 0 "emission"
		stateMode = "To";
		secondary = "";
		constMode = "";
		
	}
	
	
	// First-time init; used to declare and access states that are being updated
	// If define==null, signal not to create class definition, or create/initialize DP table......
	public void initToEmission( PositionCode pos0, TemporariesCode temp0, IntVec emissionVec0, String define0, 
				    String definemember0, String init0, String stateVector0 ) {
		
		position = pos0;
		temporary = temp0;
		toState = this;                                  // to locate defined, inited.
		define = define0;                                // points to linear scope
		definemember = definemember0;
		init = init0;
		stateVector = stateVector0;
		defined = stateVectored = inited = null;
		emissionVec = new IntVec(emissionVec0);
		if (!forward) {
		    emissionVec.negate();
		}
		stateMode = "To";
		secondary = "";
		constMode = "const";                             // this is only used by Sample, which reads but not writes the DP table
	}
	
	
	
	// Secondary init; used to access states at the "from" side of transitions
	public void initFrom( StateCode stateCode, PositionCode pos0, TemporariesCode temp0, IntVec emissionVec0, String stateVector0) {
		
		position = pos0;
		temporary = temp0;
		toState = stateCode;            // use same memory definition as stateCode
		stateVector = stateVector0;     // use private state vector definition
		stateVectored = null;
		// if backward algorithm, change sign of emission vector
		emissionVec = new IntVec(emissionVec0);
		if (!forward) {
		    emissionVec.negate();
		}
		stateMode = "From";
		secondary = "";
		constMode = "const";
	}
	
	
	// Secondary init; used to access states from secondary DP table
	public void initSecondary( PositionCode pos0, TemporariesCode temp0, String stateVector0 ) {
		
		position = pos0;
		temporary = temp0;
		toState = this;                                  // to locate defined, inited.
		define = null;    
		definemember = null;
		init = null;
		stateVector = stateVector0;
		defined = stateVectored = inited = null;
		emissionVec = new IntVec(hmm.numOutputs);        // 0 "emission"
		stateMode = "Secondary";
		secondary = "Secondary";
		constMode = "const";
		
	}
	
	
	
	// Adds a typedef (only for toState; and only for FW/BW/Viterbi-DP algorithms, not sample/Viterbi-traceback)
	void defineStateVector() {
		
		if (toState.define == null)
			return;
		if (book.getScope(toState.define) == toState.defined)
			return;
		toState.defined = book.getScope(toState.define);
		
		Code c = getCode("hmlStateVectorDefine");
		String numStates = Integer.toString(clique.states.size());
		book.addToLinearScope( toState.define, 0, c.bind(clique.id, numStates));
	}
	
	
	// From true 0-based position, and index number, calculates code referring to actual index,
	// to implement leading and trailing states
	String getIndex( String pos, int i ) {
		
		IntVec start = clique.getStart(objects);
		String code;
		if (clique.range.relStartFrom[i]) {
			// Clique is relative to start
			code = "(" + pos + ")-(" + Integer.toString(start.v[i]) + ")";
		} else {
			// Clique is relative to end, so subtract length
			code = "(" + pos + ")-" + position.getLen(i) + "-(" + Integer.toString(start.v[i]) + ")";
		}
		
		return code;
	}
	
	
	// Declares pointer to state memory at current DP position (both to and from states)
	void declareStateMemory(Code dptable) {
		
		if (book.getScope(declare) == declared)
			return;
		declared = book.getScope(declare);
		
		Code c = getCode("hmlConstCurStateMemoryDeclare");    // declare var to point to state memory    	
		Text t = c.bind(clique.id, stateMode, constMode);
		book.addInitText( declare, t );
	}
	
	
	// Declares, allocates, initializes and destroys state memory.
	void initStateMemory() {
		
		if (toState.init == null)
			return;
		if (book.getScope(toState.init) == toState.inited)
			return;
		toState.inited = book.getScope(toState.init);
		
		Code cd = getCode(fold ? "hmlStateMemoryDeclareFolded" : "hmlStateMemoryDeclare1");    
		Code ca = getCode("hmlStateMemoryAlloc");
		IntVec start = clique.getStart(objects);
		IntVec end = clique.getEnd(objects);
		IntVec mask = clique.getMask(objects);
		
		ArrayList dims = new ArrayList();
		int outputs = 0;
		for (int i=0; i<hmm.numOutputs; i++) {
			if (mask.v[i]!=0) {
				outputs += 1;
				String iLenString = Integer.toString(end.v[i] - start.v[i] + 1);
				// If interval is variable length, add in the sequence length
				if (mask.v[i] == 3)
					iLenString += "+" + position.getLen(i);
				dims.add( iLenString );
			}
		}
		
		// declaration for actual state memory
		Text t = cd.bind(clique.id, stateMode, Integer.toString(outputs), dptable.getText().toString() );
		if ( toState.define != null)
			book.addToLinearScope( toState.define, 1, t );
		
		// allocation
		if (toState.define != null) {
			book.addToLinearScope( toState.definemember, 0, ca.bind( clique.id, Generator.makeCommaList(dims) ) );
		}
		
		// destructor code
		cd = getCode("hmlStateMemoryAbsolve");
		if (toState.define != null) {
			book.addToLinearScope( toState.definemember, 1, cd.bind( clique.id ) );
		}
		
		
		// now add main initializer code.  Also see if we have start or end states that need to be initialized
		t = new Text("");
		for (int i=0; i<clique.states.size(); i++) {
			State s = (State)objects.get( clique.states.get(i) );
			if ((forward && s.start) || (!forward && s.end)) {
			    ArrayList access = new ArrayList(0);
			    for (int j=0; j<hmm.numOutputs; j++) {
				if (mask.v[j]!=0) {
				    if (forward) {
					// We assume we always start from position 0
					access.add("0");
				    } else {
					// Check that End position is at index SequenceLength
					if (!s.range.relStartFrom[j]) {
					    // Ordinary case - End state is at final symbol position
					    access.add( getIndex( position.getLen(j), j ) );
					} else {
					    // This happens when a deterministic number of symbols is emitted
					    //arrayAccess += "[" + new Integer(s.range.from[i]) + "]";
					    access.add( getIndex( Integer.toString( s.range.from[j] ), j ) );
					}
				    }
				}
			    }
			    if (!noinit) 
				t.append( getCode("hmlStateMemoryStartInit").bind( clique.id,
										   Generator.makeCommaList(access),
										   Integer.toString(s.number) ) );
			}
		}
		
		// Add init text to book
		book.addInitText(toState.init, t);

	}

	
	void setPosVar(boolean write) {

		if (!write)
			return;
		if (toState.positionScope == null)
			return;
		if (book.getScope(toState.positionScope) == toState.position.positioned)
			return;
		toState.position.positioned = book.getScope(toState.positionScope);

		// Add a variable to help clear columns of folded memory.  Initialization is done in PositionCode
		if (hmm.numOutputs > 0) {
			book.addInitText( toState.positionScope, getCode("hmlClearFoldedMemoryA").bind( position.getPos(hmm.numOutputs-1), clique.id ));
			book.addExitText( toState.positionScope, getCode("hmlClearFoldedMemoryB").bind( position.getPos(hmm.numOutputs-1) ) );
		}
		
	}

    void clearFoldedMemory( boolean write ) {

		if (!write)
			return;
		if (!fold)
			return;
		if (toState.clearTableScope == null)
			return;
		if (book.getScope(toState.clearTableScope) == toState.cleared)
			return;
		toState.cleared = book.getScope(toState.clearTableScope);

		// Clear columns of folded memory
		book.addInitText( toState.clearTableScope, 
				getCode("hmlClearFoldedMemoryPerTable").bind( position.getPos(hmm.numOutputs-1),
						clique.id ) );
    }
		
	
    
    // This also clears a row if the DP table is folded
    void accessStateVector(boolean write) {
	
	if (book.getScope(stateVector) == stateVectored)
	    return;
	stateVectored = book.getScope(stateVector);
	
	String rw = (write ? "Write" : "");
	Code c = getCode("hml"+secondary+"StateMemoryAccess"+rw);
	Code d = getCode("hml"+secondary+"StateMemoryAccess"+rw+"End");
	
	ArrayList access = new ArrayList();
	IntVec mask = clique.getMask(objects);
	
	/*
	  if (write && twocolumns) {
	  book.addInitText( stateVector, getCode("hmlClearFoldedMemoryA").bind( position.getPos(hmm.numOutputs-1), clique.id ));
	  book.addExitText( stateVector, getCode("hmlClearFoldedMemoryB").bind( position.getPos(hmm.numOutputs-1) ) );
	  }
	*/
	
	for (int i=0; i<hmm.numOutputs; i++) {
	    if (mask.v[i]!=0) {
		access.add( getIndex( position.getPos(i)+ "-(" + Integer.toString(emissionVec.v[i]) + ")",i ) );
	    }
	}
	c.bind( clique.id, stateMode, Generator.makeCommaList(access) );
	d.bind( clique.id, stateMode, Generator.makeCommaList(access) );
	book.addInitText( stateVector, c.getText() );
	book.addExitText( stateVector, d.getText() );
    }
	
	
	//
	// Implements using (temp$result) variable to store intermediate results
	//
	void doIntermediate(String state, String intermediate) {
		
		if (book.getScope(intermediate) == intermediated)
			return;
		intermediated = book.getScope(intermediate);
		
		Code c = getCode("hmlStateAccess");
		String s = c.bind( clique.id+stateMode, state ).toString();
		
		c = getCode("hmlStateAccessInit");
		c.bind( temporary.getTempProb(), s );
		book.addInitText( intermediate, c.getText() );
		
		c = getCode("hmlStateAccessExit");
		c.bind( temporary.getTempProb(), s );
		book.addExitText( intermediate, c.getText() );
	}
	
	
	public String getState( State s, String toStateScope, int numFroms ) {
		
		return getState( String.valueOf(s.number), toStateScope, numFroms, false );
		
	}
	
	
	public String getState( State s, String toStateScope, int numFroms, boolean write ) {
		
		return getState( String.valueOf(s.number), toStateScope, numFroms, write );
		
	}
	
	
	public String getState( String s, String toStateScope, int numFroms, boolean write ) {
		
		// Define state vector for this clique
		defineStateVector();
		
		// Declare memory of state vectors
		declareStateMemory(dptable);
		
		// Initialize memory
		initStateMemory();
		
		// Set position variable, to clear folded memory
		setPosVar(write);

		// clear folded memory
		clearFoldedMemory( write );
		
		// Access state vector for this position
		accessStateVector(write);
		
		// Either return reference, or access state & return reference
		if (numFroms < 99) {
			
			Code c = getCode("hmlStateAccess");
			return c.bind( clique.id+stateMode, s ).toString();
			
		} else {
			
			doIntermediate( s, toStateScope );
			return temporary.getTempProb();
			
		}
	}
	
	
	
	// helper - build parameter string for getState function
	String getGetStatePars(PositionCode positionCode) {
		
		String pars = "";
		for (int i=0; i<hmm.numOutputs; i++) {
			if (i!=0)
				pars += ",";
			pars += "int ";
			if (positionCode != null) {
				pars += positionCode.getPos(i);
			}
		}
		return pars;
	}
	
	
	void buildGetStateFunc() {
		
		IntVec zeroVec = new IntVec( hmm.numOutputs );
		PositionCode positionCode = new PositionCode(hmm, book, hmm.objects );
		positionCode.init();
		
		// generate function header
		
		book.openScopeAtInit( "getstatesub", "subroutines" );
		String pars = getGetStatePars(positionCode);
		String pars2 = HasIdentifiers.stripTypesFromParameters(pars);
		if (pars.length() != 0) {
			pars = ","+pars;
			pars2 = ","+pars2;
		}
		book.add( getCode("hmlGetStateFuncDef").bind(hmm.id, twocolMode, "iState", pars, pars2 ) );
		
		// make arrays to translate global state id to clique and local state id
		
		int num = hmm.states.length;
		ArrayList intListB = new ArrayList( num );
		ArrayList intListL = new ArrayList( num );
		TreeMap blockNum = new TreeMap();
		int blockid = 0;
		for (int i=0; i<num; i++) {
			State state = (State)objects.get( hmm.states[i] );
			if (!blockNum.containsKey( state.block )) {
				blockNum.put(state.block, new Integer(blockid));
				blockid++;
			}
			intListB.add( blockNum.get(state.block) );
			intListL.add( new Integer(state.number) );
		}
		book.openScope("gssdeclare");
		book.add( getCode("hmlSampleInitVec").bind("blockTable",Generator.makeCommaList(intListB)) );
		book.add( getCode("hmlSampleInitVec").bind("stateTable",Generator.makeCommaList(intListL)) );
		
		// generate switch statement
		
		book.openScope("gssswitch");
		book.add( getCode("hmlGetStateSwitch").bind("blockTable","iState"));
		
		// loop over all blocks
		
		for (int i=0;i<hmm.blocks.length; i++) {
			
			String blockStr = hmm.blocks[i];
			int blockId = ((Integer)blockNum.get(blockStr)).intValue();
			Clique clique = (Clique)objects.get( blockStr );
			StateCode stateCode = new StateCode(hmm, book, objects, clique, "gssdeclare", false, false, false, false);
			
			book.add( getCode("hmlSampleCase").bind( String.valueOf(blockId) ) );
			book.openScope("gssposition");
			
			RangeCode allRange = new RangeCode( hmm, book );
			allRange.initAnything();
			allRange.checkRange( zeroVec, clique.range, positionCode, "gssposition", getCode("hmlGetStateDefault").getText() );
			
			book.openScope("gssstatevector");
			stateCode.initSecondary( positionCode, null, "gssstatevector");
			// Make sure "this->" DPTable is referred to, instead of "dp2".
			stateCode.secondary = "This";
			
			book.add( new Text("return "+stateCode.getState("stateTable[iState]","",0,false)+";") );
			
			book.closeScope("gssstatevector");
			book.closeScope("gssposition");
			
		}
		
		book.closeScope("gssswitch");
		book.closeScope("gssdeclare");
		book.add( getCode("hmlGetStateFuncEnd").getText() );
		book.closeScope("getstatesub");
		
	}
	
	
	
	// Do the per-class portion of the initialisation
	// (Use the position parameter supplied instead of in-class one, to use initClass before StateCode.initTo() etc.)
	
	// Writes code to classdefinition and memberdefinition scopes; both should be linear scopes.
	
	public void initClass(PositionCode pos, String classdefinition, String memberdefinition, String hmmid) {
		
		book.addToLinearScope(classdefinition,0,getCode("hmlDPTableA").bind(hmmid+twocolMode));
		book.addToLinearScope(classdefinition,1,getCode("hmlDPTableB").bind(hmmid+twocolMode));
		String pars = getGetStatePars(null);
		if (pars.length() != 0)
			pars = ","+pars;
		book.addToLinearScope(classdefinition,2,getCode("hmlDPTableC").bind(hmmid, twocolMode, hmm.getLengthIds("int "), "iState", pars));
		book.addToLinearScope(memberdefinition,0,getCode("hmlDPTableD").bind(hmmid, twocolMode, hmm.getLengthIds("int ")));
		book.addToLinearScope(memberdefinition,1,getCode("hmlDPTableE").bind(hmmid+twocolMode));
		book.addToLinearScope(memberdefinition,2,getCode("hmlDPTableF").bind(hmmid+twocolMode));
		for (int i=0; i<hmm.numOutputs; i++) {
			Text t = getCode("hmlDPTableLenDecl").bind( pos.getLen(i) );
			book.addToLinearScope( classdefinition, 1, t );
			t = getCode("hmlDPTableLenInit").bind( pos.getLen(i) );
			book.addToLinearScope( memberdefinition, 0, t );
		}
		
		buildGetStateFunc();
		
	}
	
	
	// Do the per-class portion of the initialisation, when DP tables are supplied by caller
	public static void initDPInput(String init, String hmmid, Book book, TreeMap objects, boolean secondary ) {
		
		book.addInitText( init, getCode(secondary ? "hmlSecondaryDPTableInput" : "hmlDPTableInput",objects).bind(hmmid) );
		
	}
	
	
	// Do the per-class portion of the initialisation
	public static void initDPTable(String init, HMM hmm, Book book, TreeMap objects, boolean twocols) {
		
		// Declare and initialize state memory (but for start/end state, which is done by StateCode)
		String lengths = hmm.getLengthIds("", twocols);
		// This is necessary since "Type var()" is not a valid constructor invocation for a class that does not take init arguments.
		if (lengths.length() != 0)
			lengths = "(" + lengths + ")";
		book.addInitText( init, getCode("hmlDPTableInit",objects).bind(hmm.id+(twocols ? "Folded" : ""),lengths) );
		
	}
	
}


