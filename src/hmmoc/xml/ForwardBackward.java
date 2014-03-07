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

import hmmoc.util.CompileUtils;
import hmmoc.util.IntVec;
import hmmoc.code.Book;
import hmmoc.code.StateCode;
import hmmoc.code.TransitionCode;
import hmmoc.code.RangeCode;
import hmmoc.code.PositionCode;
import hmmoc.code.EmissionCode;
import hmmoc.code.Generator;
import hmmoc.code.CountCode;
import hmmoc.code.TemporariesCode;
import hmmoc.code.SymbolCode;
import hmmoc.code.MatrixCode;
import hmmoc.code.Text;

public class ForwardBackward {
	
	String id;
	
	HMM hmm;
	String hmmid;
	Code banding;
	TreeMap objects;
	boolean forward;
	boolean viterbi;
	boolean baumwelch;
	boolean baumwelchTransitions;
	boolean baumwelchEmissions;
	boolean outputtable;
        boolean cachevalues = true;
	String name;

        boolean optimizeFirstAssignment = true;
	
	
	public ForwardBackward( Element elem, TreeMap idRef, TreeMap objs ) {
		
		id = elem.getAttributeValue("id");
		
		if (elem.getName().equals("forward")) {
			forward = true;
			viterbi = false;
			name = "Forward";
		} else if (elem.getName().equals("backward")) {
			forward = false;
			viterbi = false;
			name = "Backward";
		} else if (elem.getName().equals("viterbi")) {
			forward = false;
			viterbi = true;
			name = "Viterbi";
		}
		
		baumwelchEmissions = false;
		baumwelchTransitions = false;
		String baumWelch = elem.getAttributeValue("baumWelch");
		if ((baumWelch == null) || (baumWelch.equals("no"))) {
			// default - no Baum-Welch code added
		} else {
			if (viterbi) {
				System.out.println("<viterbi> recursion cannot be combined with Baum-Welch; attribute 'baumWelch' ignored.");
			} else {
				if (baumWelch.equals("yes")) {
					baumwelchTransitions = true;
					baumwelchEmissions = true;
				} else if (baumWelch.equals("transitions")) {
					baumwelchTransitions = true;
				} else if (baumWelch.equals("emissions")) {
					baumwelchEmissions = true;
				} else {
					throw new Error("<"+elem.getName()+">: Attribute 'baumWelch' has value '"+baumWelch+"', expected 'yes' or 'no', or 'emissions', or 'transitions'");
				}
			}
		}
		baumwelch = baumwelchEmissions || baumwelchTransitions;
		
		String outputTable = elem.getAttributeValue("outputTable");
		if ((outputTable == null) || (outputTable.equals("no"))) {
			outputtable = false;
		} else if (outputTable.equals("yes")) {
			outputtable = true;
		} else {
		    throw new Error("<"+elem.getName()+">: Attribute 'outputTable' has value '"+outputTable+"', expected 'yes' or 'no'");
		}
		
		String cacheValues = elem.getAttributeValue("cacheValues");
		if ((cacheValues == null) || (cacheValues.equals("yes"))) {
		    cachevalues = true;
		} else if (cacheValues.equals("no")) {
		    cachevalues = false;
		} else {
		    throw new Error("<"+elem.getName()+">: Attribute 'cacheValues' has value '"+cacheValues+"', expected 'yes' or 'no'");
		}

		hmm = CompileUtils.getHMM(elem,idRef,objs);
		
		banding = CompileUtils.getBanding(elem,idRef,objs);
		
		if (elem.getAttributeValue("name") != null) {
			name = elem.getAttributeValue("name");
		}
		
		// This should reflect HMM and output type -- ignore output dependence for now
		hmmid = hmm.id;
		
		objects = objs;
		
	}
	
	
	Code getCode( String id ) {
		
		return Generator.getCode(id,objects);
		
	}
	
	
	public void generate( Book book, String language) {
		
		book.openLinearScopeAtInit("dptableclassdef",5,"header-classdef");
		
		book.openLinearScopeAtInit("baumwelchclassdef",4,"header-classdef");
		
		book.openLinearScopeAtInit("dptableclassmemberdef",3,"subroutines");
		
		book.add( getCode("hmlHMMIdentifiers").bind(hmmid,
				hmm.getStateIds(),
				hmm.getEmissionIds(),
				hmm.getTransitionIds(),
				hmm.getTransitionF(),
				hmm.getTransitionT(),
				hmm.getTransitionP(),
				hmm.getTransitionE(),
				hmm.getOutputIds() ) );
		
		book.add( getCode("hmlHMMIdentifiers2").bind(hmmid,
				String.valueOf(hmm.states.length),
				String.valueOf(hmm.emissions.length),
				String.valueOf(hmm.transitions.length),
				String.valueOf(hmm.outputs.length)) );
		
		book.openScope("function");
		
		book.openScope("define");
		
		book.openScope("declare");
		
		// Create code to access 'to' and 'from' blocks (goes here because of re-use of declarations between from and to parts)
		Map stateCodeFroms = new TreeMap();
		Map stateCodeTos = new TreeMap();
		for (int blIdx = 0; blIdx < hmm.sortedBlocks.length; blIdx++) {
			// Give label where to put temporary pointer to DP position (unique to each state used, either for reading or writing)
			// In case the output table is not returned, only store 2 rows of the slowest variable; do a "mod 2" before accessing
			boolean fold = !outputtable;
			Clique clique = (Clique)objects.get(hmm.blocks[blIdx]);
			if (hmm.outputs.length == 0 || clique.range.isFixedRange( hmm.outputs.length-1 )) {
				// But, only fold when the slowest variable is actually looping
				fold = false;
			}
			
			stateCodeFroms.put( hmm.blocks[blIdx],
					new StateCode(hmm, book, objects, clique, "declare", forward, fold, !outputtable, false) );
			stateCodeTos.put( hmm.blocks[blIdx],
					new StateCode(hmm, book, objects, clique, "declare", forward, fold, !outputtable, false) );
		}
		
		// Create the class definition of the DP table (using a dummy position code, to get access to getLen())
		((StateCode)stateCodeFroms.get(hmm.blocks[0])).initClass(new PositionCode(hmm,book,objects),
				"dptableclassdef", "dptableclassmemberdef", hmmid);
		
		// Create code for numerical temporary variables
		TemporariesCode temporariesCode = new TemporariesCode( hmm, book, "declare" );
		
		book.openScope("init");
		/*
		 // Declare and initialize state memory (but for start/end state, which is done by StateCode)
		  String lengths = hmm.getLengthIds("", !outputtable);
		  // This is necessary since "Type var()" is not a valid constructor invocation for a class that does not take init arguments.
		   if (lengths.length() != 0)
		   lengths = "(" + lengths + ")";
		   book.addInitText( "init", getCode("hmlDPTableInit").bind(hmmid,lengths) );
		   */
		StateCode.initDPTable("init", hmm, book, objects, !outputtable);
		
		if (baumwelch) {
			// Declare secondary (signified by 'true') DP table
			StateCode.initDPInput("init", hmmid, book, objects, true );
			
			// Get FW/BW probability from table
			Clique firstClique = (Clique)objects.get( hmm.blocks[(forward ? 0 : hmm.sortedBlocks.length-1)] );
			State firstState = (forward ? hmm.startState : hmm.endState);
			book.openScope("getprobability");
			PositionCode startPositionCode = new PositionCode( hmm, book, objects );
			startPositionCode.init( firstState.range, forward, "getprobability", null );
			StateCode firstStateCode = new StateCode(hmm, book, objects, firstClique, "declare", forward, false, false, false );
			firstStateCode.initSecondary(startPositionCode, temporariesCode, "getprobability");
			String firstStateRef = firstStateCode.getState( firstState, "", 1 );
			book.add( new Text(temporariesCode.getTempProb(2) + " = " + firstStateRef + ";\n") );
			book.add( getCode("hmlScaleCount").bind(hmmid, temporariesCode.getTempProb(2) ) );
			book.closeScope("getprobability");
		}
		
		// Declare and initialize transitions
		TransitionCode transitionCode = new TransitionCode( hmm, book, objects, temporariesCode, "declare", "init", cachevalues );
		
		// Declare symbols (No need to init later; done by emissionCode.init)
		SymbolCode symbolCode = new SymbolCode( hmm, book, objects, "declare", "init" );
		
		// Declare emissions
		EmissionCode emissionCode = new EmissionCode( hmm, book, objects, forward, false, "declare", cachevalues );
		
		// Declare count code for Baum-Welch
		CountCode countCode = new CountCode( hmm, book, symbolCode, "declare", "baumwelchclassdef", hmmid, baumwelchEmissions, baumwelchTransitions );
		
		// Allow looping both within and outside of block loop
		PositionCode outerPositionCode = null;
		PositionCode positionCode = null;
		Banding banding = null;
		boolean firstClique = true;
		boolean pastBandedClique = false;
		
		// Loop over all receiving blocks
		int step = (forward ? 1 : -1);
		for (int blockCount = (forward ? 0 : hmm.sortedBlocks.length-1);
			0 <= blockCount && blockCount < hmm.sortedBlocks.length;
			blockCount += step) {
			
			Clique clique = (Clique)objects.get(hmm.blocks[blockCount]);
			
			if (firstClique || banding != clique.linkedbanding /* || banding==null */ ) {
				
				// close previous scope
				if (!firstClique) {
					positionCode.exit();
					book.closeScope("symbol");
					book.closeScope("slowvar");
					book.closeScope("position");
				}
				firstClique = false;
				pastBandedClique = false;
				banding = clique.linkedbanding;
				
				// calculate range encompassing all cliques that will be included in this scope
				RangeCode allrange = new RangeCode(clique.range, book);
				for (int bc = blockCount; 
				0 <= bc && bc < hmm.sortedBlocks.length; 
				bc += step) {
					
					Clique c = (Clique)objects.get(hmm.blocks[bc]);
					if (c.linkedbanding != banding) break;
					allrange.include( c.range );
				}
				
				// Create (outer) position code.  Add position loop code to book.
				book.openScope("position");
				outerPositionCode = new PositionCode( hmm, book, objects );
				outerPositionCode.init( allrange, forward, "position", clique );

				// Open scope to hold slow variable code, to clear folded DP table columns
				book.openScope("slowvar");
				
				// Compute symbols for these cliques and this position
				book.openScope("symbol");
				symbolCode.init( outerPositionCode, allrange, "symbol" );
				
			}

			// Next, see if an inner position block needs to be opened
			positionCode = outerPositionCode;
			book.openScope("innerposition");
			if (banding != clique.banding) {
				positionCode = new PositionCode(hmm, book, objects );
				positionCode.init(outerPositionCode, forward, "innerposition", clique, pastBandedClique );
			} else {
				pastBandedClique = true;
			}

			book.openScope("innersymbol");
			symbolCode.init( positionCode, positionCode.range, "innersymbol");
			
			// Get range for this clique (and initialize it as code generator) (for SymbolCode later)
			//RangeCode rangeCode = new RangeCode( clique.range );
			
			// check that we're in range for the To state -- this is only necessary when several cliques
			// are merged under a single large position block
			RangeCode toBlockRangeCode = new RangeCode( positionCode.range, book );
			IntVec zero = new IntVec( hmm.outputs.length );
			toBlockRangeCode.checkRange( zero, clique.range, positionCode, "innersymbol" );
			
			book.openScope("statevector");
			
			// Get code to access states in current clique & position
			StateCode stateCode = (StateCode)stateCodeTos.get( clique.id );
			// symbol (last): slowvar?  sets prevSlowVar
			stateCode.initTo(positionCode, temporariesCode, "dptableclassdef","dptableclassmemberdef","init","statevector", "slowvar", "slowvar");  // was slowvar
			
			StateCode secondaryStateCode = null;
			if (baumwelch) {
				// Get code to access states in secondary DP table
				secondaryStateCode = new StateCode(hmm, book, objects, clique, "declare", forward, false, false, false );
				secondaryStateCode.initSecondary(positionCode, temporariesCode, "statevector");
			}
			
			// List of all 'dirty' states -- empty at first
			Map stateTouched = new TreeMap();

			// Loop over all emission vectors (zero vector last)
			IntVec[] emissions = clique.getEmissionVectors(forward, objects);
			for (int emIdx=0; emIdx<emissions.length; emIdx++) {
				
				book.openScope("emission");
				
				// Compute emission probabilities
				emissionCode.init( emissions[emIdx], clique, symbolCode, temporariesCode, positionCode, "emission" );
				
				// loop over all supplying blocks (self clique last)
				Clique[] fromBlocks = clique.getFromBlocks(forward, emissions[emIdx], objects);
				for (int blIdx=0; blIdx<fromBlocks.length; blIdx++) {
					
					book.openScope("fromblock");
					
					//book.add( new Text( "/* doing clique "+fromBlocks[blIdx].id+" */\n") );
					
					// Now check range
					RangeCode fromBlockRangeCode = new RangeCode( clique.range, book );
					IntVec offset = new IntVec(emissions[emIdx] );
					if (forward)
						offset.negate();
					fromBlockRangeCode.checkRange( offset, fromBlocks[blIdx].range, positionCode, "fromblock" );
					
					StateCode stateCodeFrom = (StateCode)stateCodeFroms.get( fromBlocks[blIdx].id );
					stateCodeFrom.initFrom( (StateCode)stateCodeTos.get( fromBlocks[blIdx].id ),
							positionCode, temporariesCode, emissions[emIdx], "fromblock");
					
					if (emissions[emIdx].isZero() && (fromBlocks[blIdx] == clique)) {
						// Special-case code for self-reference transitions
						
						List selfComponents = clique.selfComponents( forward, objects );
						Iterator scIter = selfComponents.iterator();
						while (scIter.hasNext()) {
							
							Object o = scIter.next();
							List srStates;
							if (o instanceof String) {
								// Non-self-ref state; deal as if single self-ref state w.r.t. incoming transitions
								srStates = new ArrayList(0);
								srStates.add( o );
							} else {
								srStates = (List)o;
							}
							
							//book.add( new Text( "/* doing self-ref component "+srStates+" */\n" ) );
							// Loop over all states in component, and get all non-self-ref transitions into those states
							Iterator srStateIter = srStates.iterator();
							int vectorIdx = 0;
							int matrixDimension = srStates.size();
							while (srStateIter.hasNext()) {
								String srStateString = (String)srStateIter.next();
								List srsTranss = clique.selfStateTransitions( srStateString, forward, objects );
								State srToState = (State)objects.get( srStateString );
								srToState.internalIndex = vectorIdx;
								// Signal that this one is used for writing; if we have only 2 columns, make sure to clear any previous data
								String toStateRef = stateCode.getState( srToState, "", 1, true );
								String tempVecEntryRef;

								// Read probability from state array into temporary vector entry - but only for matrices
								// of dimension 2 and greater
								if (matrixDimension >= 2) {
								    tempVecEntryRef = temporariesCode.getVectorEntry( vectorIdx );
								    book.add( new Text( tempVecEntryRef + " = " + toStateRef + ";\n" ) );
								} else {
								    // reference state directly
								    tempVecEntryRef = toStateRef;
								}

								Iterator srstIter = srsTranss.iterator();
								while (srstIter.hasNext()) {
									Transition t = (Transition)srstIter.next();
									State fromState = (State)objects.get( t.getFrom(forward) );
									if (!srStates.contains( fromState.id )) {
										// It is not a self-ref transition   
									        // [note: emissions[emIdx] is 0, so 'forward' does not matter]
										// We're reading, so don't initialize
										String fromStateRef = stateCodeFrom.getState( fromState, "", 1);
										String transRef = transitionCode.getText( t, book, symbolCode, positionCode, emissions[emIdx], forward, false );
										book.add( getCode(viterbi ? "hmlViterbi" : "hmlFWBW").bind(tempVecEntryRef, transRef + "*" + fromStateRef) );
									}
								}
								vectorIdx += 1;
							}
							
							MatrixCode matrixCode = new MatrixCode( hmm, book );
							if (viterbi) {
							    // Use a shortest-path-like algorithm to update state probabilities in temporary vector --
							    // but only for nontrivial matrices (otherwise there's nothing to do)
							    if (matrixDimension >= 2) {
								String tempVec = temporariesCode.getVectorName();
								String tempIntVec = temporariesCode.getIntVecName();
								book.add (getCode("hmlSelfRefViterbiA").bind( tempVec, tempIntVec, Integer.toString(vectorIdx) ) );
								// Make sure arrays are of suitable size
								temporariesCode.getIntVecEntry( vectorIdx );
								temporariesCode.getVectorEntry( vectorIdx );
							    }
							} else {
								// See if there are any order-n transitions in this clique.  Need to loop over all transitions.
								// Default initialization is before loop over positions
								String initLocation = "init";
								srStateIter = srStates.iterator();
								while (srStateIter.hasNext()) {
									String srStateString = (String)srStateIter.next();
									List srsTranss = clique.selfStateTransitions( srStateString, forward, objects );
									Iterator srstIter = srsTranss.iterator();
									while (srstIter.hasNext()) {
										Transition t = (Transition)srstIter.next();
										if (((Probability)objects.get(t.probability)).activeOutputs() != 0) {
											// Dependence on position, so declare when symbols are available, and matrix is needed
											initLocation = "fromblock";
										}
									}
								}
								// Build self-ref matrix, invert, and calculate exit probabilities
								matrixCode.init( "declare", initLocation, "position", matrixDimension );
							}
							
							// Loop over all self-ref transitions
							srStateIter = srStates.iterator();
							Map nonTrivialMatrices = new TreeMap();
							while (srStateIter.hasNext()) {
							        boolean nonTrivialMatrix = false;
								String srStateString = (String)srStateIter.next();
								List srsTranss = clique.selfStateTransitions( srStateString, forward, objects );
								State srToState = (State)objects.get( srStateString );
								if (viterbi && matrixDimension >= 2) {
									// Add 'case' line to switch statement:
									book.add( getCode("hmlSelfRefViterbiB").bind(Integer.toString(srToState.internalIndex) ) );
								}
								Iterator srstIter = srsTranss.iterator();
								while (srstIter.hasNext()) {
									Transition t = (Transition)srstIter.next();
									State fromState = (State)objects.get( t.getFrom(forward) );
									if (srStates.contains( fromState.id )) {
										// Self ref transition
										String transRef = transitionCode.getText( t, book, symbolCode, positionCode, emissions[emIdx], forward, false );
										if (viterbi) {
										    if (matrixDimension >= 2) {
											// Update state that this transition points to
											if (fromState.internalIndex != srToState.internalIndex) {
												book.add(getCode("hmlSelfRefViterbiC").bind(temporariesCode.getVectorName(),
														Integer.toString(fromState.internalIndex),
														Integer.toString(srToState.internalIndex),
														transRef));
											}
										    }
										} else {
											// Build matrix (including 1-dimensional ones)
											matrixCode.addToEntry( fromState.internalIndex,
													srToState.internalIndex,
													"-" + transRef );
											nonTrivialMatrix = true;
										}
									}
								}
								if (nonTrivialMatrix) {
								    // should use a set here -- it's late
								    nonTrivialMatrices.put( srStateString, srStateString );
								}
							}
							// Finally, compute effective probabilities for self-ref states, by multiplying with inverse matrix
							// For Viterbi, just close the loops
							if (viterbi && matrixDimension >= 2) {
								book.add( getCode("hmlSelfRefViterbiD").getText() );
							}
							srStateIter = srStates.iterator();
							while (srStateIter.hasNext()) {
								StringBuffer result = new StringBuffer("");
								String srStateString = (String)srStateIter.next();
								boolean nonTrivialMatrix = nonTrivialMatrices.containsKey( srStateString );
								State srToState = (State)objects.get( srStateString );
								String toStateRef = stateCode.getState( srToState, "", 1, true );
								String prefix = toStateRef + " = ";
								if (viterbi && matrixDimension >= 2) {
								    // write back result from temporary into state variable (but not for 1-dim matrices)
								    result.append( prefix + temporariesCode.getVectorEntry( srToState.internalIndex ) );
								} else {
								    // First handle special case of 1-dim matrix.  In that case, the probability
								    // has been accumulated in the state itself, rather than a temporary; so it
								    // remains to multiply the state in-place with the matrix element -- if it is not unity
								    if (matrixDimension == 1) {
									
									if (nonTrivialMatrix) {

									    result.append( toStateRef + " *= " + 
										     matrixCode.getInverseMatrixCoeff( srToState.internalIndex,
														       srToState.internalIndex ) );
									}

								    } else {

									Iterator srFromStateIter = srStates.iterator();
									while (srFromStateIter.hasNext()) {
										String srFromStateString = (String)srFromStateIter.next();
										State srFromState = (State)objects.get( srFromStateString );
										if (nonTrivialMatrix) {
										    result.append( prefix +
												   matrixCode.getInverseMatrixCoeff( srFromState.internalIndex,
																     srToState.internalIndex ) +
												   "*" +
												   temporariesCode.getVectorEntry( srFromState.internalIndex ) );
											prefix = " + ";
										} else {
										    // unit matrix
										    if (srFromState.internalIndex == srToState.internalIndex) {
											result.append( prefix + 
												       temporariesCode.getVectorEntry( srFromState.internalIndex ) );
											prefix = " + ";
										    } else {
											// off-diagonal in unit matrix -- do nothing
										    }
										}
									}
								    }
								}
								if (result.length() > 0) {
								    result.append(";\n");
								    book.add (new Text( result.toString() ));
								}
							}
							if (!viterbi) {
								matrixCode.exit();
							}
						}
						if (baumwelch) {
							
							// Include code measuring flow from current forward(i), via transitions i->j, to backward(j)
							
						}
						
					} else {
						
						// non-self-ref code.  Loop over all receiving states
						State[] toStates = clique.getToStates(forward, emissions[emIdx], fromBlocks[blIdx], objects);
						for (int stIdx=0; stIdx<toStates.length; stIdx++) {
							
							book.openScope("tostate");
							State[] fromStates = clique.getFromStates(forward, emissions[emIdx], fromBlocks[blIdx], toStates[stIdx], objects);
							// fromStates.length determines whether temporary variable is used or not.
							String stateRef = stateCode.getState( toStates[stIdx], "tostate", fromStates.length, true );
							String secondaryStateRef = null;
							if (baumwelch) {
								// Would it help to have temporaries here? (What about named temporaries?)
								secondaryStateRef =  secondaryStateCode.getState( toStates[stIdx], "tostate", 1);
							}
							//book.add( new Text( "/* doing tostate "+toStates[stIdx].id+" */\n" ) );

							for (int frstIdx=0; frstIdx<fromStates.length; frstIdx++) {
								
								// Get code referring to from state; will be used once, i.e. do not use a temporary variable
								String fromStateRef = stateCodeFrom.getState( fromStates[frstIdx], "", 1);
								book.openScope("fromstate");
								Transition[] fromTransitions = clique.getFromToTransitions(forward, emissions[emIdx], fromBlocks[blIdx],
										toStates[stIdx], fromStates[frstIdx], objects );
								
								for (int trIdx=0; trIdx<fromTransitions.length; trIdx++) {
									
									Emission em = (Emission)objects.get( fromTransitions[trIdx].emission  );
									String emRef = emissionCode.getText( em, positionCode, symbolCode, temporariesCode, "fromstate" );
									
									String transRef = transitionCode.getText( fromTransitions[trIdx], book, symbolCode,
											positionCode, emissions[emIdx], forward, false );
									String resultRef = "((" + transRef + ")*(" + emRef + "))*" + fromStateRef;
									if (baumwelch) {
										// Get result in temporary variable
										resultRef = temporariesCode.getTempProb(1) + " = " + resultRef;
									}
									if ( !stateTouched.containsKey(toStates[stIdx]) && optimizeFirstAssignment ) {
									    // first time, a simple assignment will do
									    book.add( getCode("hmlShortCircuit").bind(stateRef, resultRef ) );
									    stateTouched.put( toStates[stIdx], null );
									} else {
									    book.add( getCode(viterbi?"hmlViterbi":"hmlFWBW").bind(stateRef, resultRef ) );
									}
									if (baumwelch) {
										// Compute count (!!! check whether we need a double or bfloat variable)
										if (baumwelchTransitions && baumwelchEmissions) {
											book.add( new Text(temporariesCode.getTempProb(1) + " *= " + secondaryStateRef + ";\n"));
											// Add to posterior transition and emission counts
											book.add( new Text(countCode.getText( fromTransitions[trIdx], forward ) + " += " +
													temporariesCode.getTempProb(1) + ";\n"));
											book.add( new Text(countCode.getText( em, forward ) + " += " +
													temporariesCode.getTempProb(1) + ";\n"));
										} else {
											if (baumwelchTransitions) {
												book.add( new Text(countCode.getText( fromTransitions[trIdx], forward ) + " += " +
														temporariesCode.getTempProb(1) + " * " + secondaryStateRef + ";\n"));
											}
											if (baumwelchEmissions) {
												book.add( new Text(countCode.getText( em, forward ) + " += " +
														temporariesCode.getTempProb(1) + " * " + secondaryStateRef + ";\n"));
											}
										}
									}
									/*
									 book.add( new Text(countCode.getText( fromTransitions[trIdx], forward ) + " += " +
									 secondaryStateRef + " * " +
									 temporariesCode.getTempProb(1) + " * " +
									 temporariesCode.getTempProb(2) + ";\n" ));
									 book.add( new Text(countCode.getText( em, forward ) + " += " +
									 secondaryStateRef + " * " +
									 temporariesCode.getTempProb(1) + " * " +
									 temporariesCode.getTempProb(2) + ";\n" ));
									 */
								}
								
								book.closeScope("fromstate");
								
							}
							
							book.closeScope("tostate");
							
						}
						
					}
					
					book.closeScope("fromblock");
					
				}
				
				book.closeScope("emission");
				
			}
			
			book.closeScope("statevector");
			book.closeScope("innersymbol");
			book.closeScope("innerposition");
			
		}
		
		book.closeScope("symbol");
		
		book.closeScope("slowvar");
		
		book.closeScope("position");
		
		// Return Baum-Welch counts
		if (baumwelch) {
			book.add( getCode("hmlScaleCount").bind(hmmid, "1.0 / "+temporariesCode.getTempProb(2) ) );
			book.add( getCode("hmlCountReturn").bind(hmmid) );
		}
		
		// Compute return value 
		State lastState = (forward ? hmm.endState : hmm.startState );
		book.openScope("getendvalue");
		PositionCode endPositionCode = new PositionCode( hmm, book, objects );
		endPositionCode.init( lastState.range, forward, "getendvalue", null );
		StateCode endStateCode = (StateCode)stateCodeFroms.get( lastState.block );
		StateCode endStateCodeTo = (StateCode)stateCodeTos.get( lastState.block );
		endStateCode.initFrom( endStateCodeTo, endPositionCode, temporariesCode, new IntVec(hmm.numOutputs), "getendvalue");
		String stateRef = endStateCode.getState( lastState, "", 1 );
		book.add( getCode("hmlReturnCode1").bind( temporariesCode.getTempProb(), stateRef ) );
		endPositionCode.exit();
		book.closeScope("getendvalue");
		
		// Call static member function that emits all necessary initialization code to the init-block of the "declare" scope
		Code.emitInitCode(objects, book, "", "declare");
		
		// Build parameter string.  Add input DP table parameter for Baum-Welch, and add code to return DP table if asked for
		// Have input parameters go before outputs
		// (Goes after init code emission, as this may introduce new parameters)
		String pars = Code.getParameters(objects);
		if (baumwelch) {
			pars = Code.addParameter(pars, getCode("hmlDPTableParameterInput").bind(hmmid).toString() );
			pars = Code.addParameter(pars, getCode("hmlCountParameter").bind(hmmid).toString() );
		}
		if (outputtable) {
			pars = Code.addParameter(pars, getCode("hmlDPTableParameter").bind(hmmid).toString() );
			book.add( getCode("hmlDPTableReturn").bind(hmmid) );
		}
		
		// Close init scope
		book.closeScope("init");
		
		// Add return code
		book.add( getCode("hmlReturnCode2").bind( temporariesCode.getTempProb() ) );
		
		// Before closing declare scope, emit the necessary declarations and definitions
		symbolCode.exit();
		emissionCode.exit();
		temporariesCode.exit();
		
		book.closeScope("declare");
		
		book.closeScope("define");
		
		// Finally, build function definition and declaration
		book.addInitText( "function", getCode("hmlForwardStart").bind(name,pars));
		book.addExitText( "function", getCode("hmlForwardEnd").getText() );
		book.addInitText( "header-funcdecl", getCode("hmlForwardDeclaration").bind(name,pars));
		
		book.closeScope("function");
		
	}
}
