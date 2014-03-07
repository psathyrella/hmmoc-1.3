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

import hmmoc.util.IntVec;
import hmmoc.util.ParseUtils;
import hmmoc.util.Edge;
import hmmoc.util.Graph;

import hmmoc.code.RangeCode;


public class HMM extends XmlElement {

    String name;
    public String[] states;
    public String[] transitions;
    public String[] blocks;
    public String[] emissions;
    public String[] probabilities;
    public String[] outputs;
    public int numOutputs;
    public TreeMap objects;
    public TreeMap transSignatures;
    public TreeMap emitSignatures;

    String[] sortedBlocks;
    State startState;
    State endState;
    RangeCode range;
    int maxInDegree;
    int maxOutDegree;
    public int mealyTransitions;
    public int mooreStates;

    Element elem;

    public HMM(Element e, TreeMap idMap, TreeMap objects0) {

        String hmmid = e.getAttributeValue("id");
        id = hmmid;

        objects = objects0;

        List nameList = ParseUtils.parseChildren(e,"description", idMap);
        List outputsList = ParseUtils.parseChildren(e,"outputs",idMap);
        List graphList = ParseUtils.parseChildren(e,"graph",idMap);
        List transitionsList = ParseUtils.parseChildren(e,"transitions",idMap);

        // Some checks
        if (nameList.size()==0) {
            throw xmlError(e,"<hmm> has no <description>");
        }
        if (nameList.size()>1) {
            throw xmlError(e,"<hmm> "+hmmid+" has multiple <description>s");
        }
        if (outputsList.size()!=1) {
            throw xmlError(e,"<hmm> "+hmmid+": need exactly 1 <outputs> element");
        }
        if (graphList.size()!=1) {
            throw xmlError(e,"<hmm> "+hmmid+": need exactly 1 <graph> element");
        }
        if (transitionsList.size()!=1) {
            throw xmlError(e,"<hmm> "+hmmid+": need exactly 1 <transitions> element");
        }

        /*
          name = ((Element)(nameList.get(0))).getTextTrim();
          System.out.println(" Description: "+name);
          */

        Element outputElt = (Element)outputsList.get(0);
        Element transitionBlockElt = (Element)transitionsList.get(0);

        // parse outputs

        List outputList = ParseUtils.parseChildren(outputElt,"output", idMap);
        numOutputs = outputList.size();
        outputs = new String[ numOutputs ];
        // change Elements into Outputs
        for (int i=0; i<numOutputs; i++) {
            Output output = new Output( (Element)outputList.get(i), idMap, objects );
            objects.put( output.id, output );
            outputList.set( i, output );
        }
        // Sort them according to 'speed'; low values (fastest, shortest, inner loop) first
        class speedComparator implements Comparator {
            public int compare(Object o1, Object o2) { return ((Output)o1).speed - ((Output)o2).speed; } }
        Collections.sort( outputList, new speedComparator() );
        // Assign numbers, and put their identifiers into an array
        for (int i=0; i<numOutputs; i++) {
            ((Output)outputList.get(i)).ordinal = i;
            outputs[i] = ((Output)outputList.get(i)).id;
        }

        // parse graph:  collect all blocks

        List blockList = ParseUtils.parseChildren( (Element)graphList.get(0), "clique", idMap );
        Element[] blockElts = (Element[])blockList.toArray(new Element[0]);

        // parse blocks.  Collect all states.

        blocks = new String[ blockElts.length ];
        TreeMap stateBlockMap = new TreeMap();
        ArrayList stateStrings = new ArrayList();

        HashSet stateSet = new HashSet();
        int iTotStates = 0;
        int i;
        for (i=0; i<blockElts.length; i++) {
            Clique clique = new Clique( blockElts[i], numOutputs, idMap, this );
            blocks[i] = clique.id;
            List stateList = ParseUtils.parseChildren( blockElts[i], "state", idMap );
            if (stateList.size() == 0) {
                throw xmlError(e,"<hmm><clique>: expected at least 1 <state> in <clique> "+blocks[i]+", <hmm> "+hmmid);
            }
            // first store Elements
            clique.states = new ArrayList( new HashSet( stateList ) );
            if (clique.states.size() != stateList.size() ) {
                throw xmlError(e,"<hmm><clique>: found duplicated states in <clique> "+blocks[i]+", <hmm> "+hmmid);
            }
            for (int j=0; j<clique.states.size(); j++) {
                // update state-element -> Clique map, and change Elements into Strings
                Element s = (Element)clique.states.get(j);
                // parse state
                State state = new State( s, idMap, numOutputs, objects );
                state.block = clique.id;
                // store everything
                stateBlockMap.put( state.id, blocks[i] );
                clique.states.set( j, state.id );

                objects.put( state.id, state );
            }

            iTotStates += stateList.size();
            int prevNumber = stateSet.size();
            stateSet.addAll( stateList );
            int numberAdded = stateSet.size() - prevNumber;
            if (numberAdded != stateList.size()) {
                throw xmlError(e,"<hmm><block>: a <state> occurred in multiple <block>s (including '"+clique.id+"') in <hmm> "+hmmid);
            }
            stateStrings.addAll( clique.states );
            objects.put(blocks[i],clique);
        }
        if (stateSet.size() != iTotStates) {
            throw xmlError(e,"<hmm><block>: a <state> occurred in multiple <block>s in <hmm> "+hmmid);
        }
        states = (String[])stateStrings.toArray(new String[0]);

        // parse transitions

        List transitionList = ParseUtils.parseChildren(transitionBlockElt,"transition",idMap);
        if (transitionList.size()==0) {
            throw xmlError(e,"<hmm><transitions> Need at least one <transition>, in <hmm>"+hmmid);
        }
        transitions = new String[ transitionList.size() ];
        for (i=0; i<transitionList.size(); i++) {
            Transition trans = new Transition( (Element)transitionList.get(i), stateBlockMap, idMap, this );
            trans.number = i;
            transitions[i] = trans.id;
            objects.put(trans.id,trans);
            // check if state order is okay
            State from = (State)objects.get(trans.from);
            State to = (State)objects.get(trans.to);
            Emission em = (Emission)objects.get(trans.emission);
            for (int j=0; j<numOutputs; j++) {
                if (from.order[j] + em.outputVec.v[j] < to.order[j]) {
                    throw xmlError(e,"<transition> "+trans.id+" from order-"+from.order[j]+" <state> "+from.id+" to order-"+to.order[j]+" <state> "+to.id+" (with respect to <output> "+outputs[j]+") emitting "+em.outputVec.v[j]+" symbol(s) (through <emission> "+em.id+") violates order");
                }
            }
        }

        // parse all probabilities and emissions; number emissions
        HashSet emit = new HashSet();
        HashSet prob = new HashSet();
        for (i=0; i<transitions.length; i++) {
            Transition tr = (Transition)objects.get( transitions[i] );
            Emission em = (Emission)objects.get( tr.emission );
            Probability pr = (Probability)objects.get( tr.probability );
            emit.add( em.id );
            prob.add( pr.id );
        }
        emissions = (String[])emit.toArray(new String[0]);
        probabilities = (String[])prob.toArray(new String[0]);
        for (i=0; i<emissions.length; i++) {
            ((Emission)objects.get(emissions[i])).number = i;
        }
    }



    public void updateMask( IntVec v1, IntVec v2, IntVec mask ) {
        // helper function.  Unequal coordinates in v1,v2 are marked 'variable' in mask
        for (int i=0; i<mask.v.length; i++) {
            if (v1.v[i] != v2.v[i]) {
                mask.v[i] = 0;
            }
        }
    }


    public void analyze( TreeMap objects ) {

        // Build transition graph on blocks.  Also assign transitions to blocks
        ArrayList blockGraph = new ArrayList();
        maxInDegree = 0;
        maxOutDegree = 0;
        mooreStates = 0;
        mealyTransitions = 0;
        for (int i=0; i<transitions.length; i++) {
            Transition tr = (Transition)objects.get( transitions[i] );
            State stFrom = (State)objects.get( tr.from );
            State stTo = (State)objects.get( tr.to );
            stFrom.outDegree += 1;
            stTo.inDegree += 1;
            if (tr.mealy)
                mealyTransitions += 1;
            if (maxOutDegree < stFrom.outDegree)
                maxOutDegree = stFrom.outDegree;
            if (maxInDegree < stTo.inDegree)
                maxInDegree = stTo.inDegree;
            blockGraph.add( new Edge( stFrom.block, stTo.block ) );
            if (stFrom.block.equals(stTo.block)) {
                ((Clique)objects.get(stFrom.block)).selfTrans.add( transitions[i] );
            } else {
                ((Clique)objects.get(stFrom.block)).outTrans.add( transitions[i] );
                ((Clique)objects.get(stTo.block)).inTrans.add( transitions[i] );
            }
        }

        // Sort graph.  Check for connected components.  Also, re-order states[] array
        List sortedGraph = Graph.sortGraph( blockGraph );
        int stateCount = 0;
        sortedBlocks = new String[ sortedGraph.size() ];
        for (int i=0; i<sortedGraph.size(); i++) {
            Object blockObj = sortedGraph.get(i);
            String blockId = null;
            List l = null;        // Ah, compiler happy
            if (blockObj instanceof String) {
                // Clique without self-ref transitions
                blockId = (String)blockObj;
            } else {
                // Clique with self-ref transitions
                l = (List)sortedGraph.get(i);
                if (l.size() == 1) {
                    blockId = (String)l.get(0);
                }
            }
            if (blockId != null) {
                sortedBlocks[i] = blockId;
                Clique b = (Clique)objects.get(blockId);
                b.stateOffset = stateCount;
                List statesList = b.states;
                Iterator stIter = statesList.iterator();
                while (stIter.hasNext()) {
                    State s = (State)objects.get(stIter.next());
                    if (s.emission != null)
                        mooreStates += 1;
                    s.number = stateCount - b.stateOffset;
                    s.globalNumber = stateCount;
                    states[stateCount] = s.id;
                    stateCount++;
                }
            } else {
                // Blocks that cyclicly refer to each other are no 'block' in my book.
                throw xmlError(elem,"Graph on <block>s is cyclic; found connected component with "+l.size()+
                        " blocks, two of which are "+l.get(0) +" and "+l.get(1));
            }
        }

        // Find start and end states, label states accordingly
        ArrayList stateGraph = new ArrayList();
        for (int i=0; i<transitions.length; i++) {
            Transition tr = (Transition)objects.get( transitions[i] );
            stateGraph.add( new Edge( tr.from, tr.to ) );
        }
        List start = Graph.infimum( stateGraph );
        if (start.size()==0) {
            throw xmlError(elem,"State graph has no start element");
        }
        if (start.size()>1) {
            throw xmlError(elem,"State graph has multiple start elements, two of which are "+start.get(0)+" and "+start.get(1));
        }
        List end = Graph.supremum( stateGraph );
        if (end.size()==0) {
            throw xmlError(elem,"State graph has no end element");
        }
        if (end.size()>1) {
            throw xmlError(elem,"State graph has multiple end elements, two of which are "+end.get(0)+" and "+end.get(1));
        }
        startState = (State)objects.get( start.get(0) );
        endState = (State)objects.get( end.get(0) );
        for (int i=0; i<numOutputs; i++) {
            if (startState.order[i] != 0) {
                throw xmlError(elem,"Start <state> "+startState.id+" is not zeroth order with respect to <output> "+outputs[i]);
            }
            if (endState.order[i] != 0) {
                throw xmlError(elem,"End <state> "+endState.id+" is not zeroth order with respect to <output> "+outputs[i]);
            }
        }

        for (int i=0; i<states.length; i++) {

            if (states[i].equals( startState.id))
                ((State)objects.get(states[i])).start = true;
            else
                ((State)objects.get(states[i])).start = false;
            if (states[i].equals( endState.id))
                ((State)objects.get(states[i])).end = true;
            else
                ((State)objects.get(states[i])).end = false;
        }

        // Now assign position and mask vectors to states
        boolean changed = true;
        IntVec v,w;
        startState.posStart = new IntVec( numOutputs );   // position counted from start
        startState.maskStart = new IntVec( numOutputs );  // mask from start; 1 = fixed, 0 = variable
        endState.posEnd = new IntVec( numOutputs );       // position counted from end
        endState.maskEnd = new IntVec( numOutputs );      // mask from end; 1 = fixed, 0 = variable
        for (int i=0; i<numOutputs; i++) {
            startState.maskStart.v[i] = 1;                // initialize start and end state's masks and positions
            startState.posStart.v[i] = 0;                 // start position = 0
            endState.maskEnd.v[i] = 1;
            endState.posEnd.v[i] = 0;                   // end-state position is 0 w.r.t. length, inclusive
            // (newly emitted symbol would be 1 past end)
        }

        /* Per state:
           - Mask: variable (0), fixed from start (1), or fixed from end (-1), or fixed from both ends (1)
           - First position from start (0,1)   (>=0)
           - Last position from end (0,-1)     (<=0)
           - If fixed from both ends, total output length
           */

        while (changed) {
            changed = false;
            for (int i=0; i<transitions.length; i++) {
                Transition tr = (Transition)objects.get( transitions[i] );
                State stFrom = (State)objects.get( tr.from );
                State stTo = (State)objects.get( tr.to );
                Emission em = (Emission)objects.get( tr.emission );
                // First handle positive direction
                if (stFrom.posStart != null) {
                    v = (IntVec)stFrom.posStart.clone();
                    w = (IntVec)stFrom.maskStart.clone();
                    // calculate new position
                    v.add( em.outputVec );
                    if (stTo.posStart != null) {
                        w.multiplyComponents( stTo.maskStart); 	     // "and" the masks (0 = masked, ie variable)
                        updateMask(v, stTo.posStart, w);    	     // Update mask to reflect possibly new variable positions
                        v.minComponents( stTo.posStart );            // calculate new earliest possible position
                        // compare old and new positions and masks
                        if ( !v.equals( stTo.posStart ) ||
                                !w.equals( stTo.maskStart) ) {
                            // assign
                            stTo.posStart = v;
                            stTo.maskStart = w;
                            changed = true;
                        }
                    } else {
                        // no position for state known yet, so just assign
                        stTo.posStart = v;
                        stTo.maskStart = w;
                        changed = true;

                    }
                }
                // Then negative direction
                if (stTo.posEnd != null) {
                    v = (IntVec)stTo.posEnd.clone();
                    w = (IntVec)stTo.maskEnd.clone();
                    v.subtract( em.outputVec );
                    if (stFrom.posEnd != null) {
                        w.multiplyComponents( stFrom.maskEnd);
                        updateMask(v, stFrom.posEnd, w);
                        v.maxComponents( stFrom.posEnd );
                        // compare
                        if ( !v.equals( stFrom.posEnd ) ||
                                !w.equals( stFrom.maskEnd) ) {
                            // something changed
                            stFrom.posEnd = v;
                            stFrom.maskEnd = w;
                            changed = true;
                        }
                    } else {
                        // no data, so just assign
                        stFrom.posEnd = v;
                        stFrom.maskEnd = w;
                        changed = true;
                    }
                }
            }
        }

        // Check that all states are reached
        for (int i=0; i<states.length; i++) {
            State st = (State)objects.get( states[i] );
            if ((st.posStart == null) || (st.posEnd == null)) {
                throw xmlError(elem,"<hmm> '"+id+"': State '"+st.id+"' unreachable");
            }
        }

        // The mask of the end state from the start position should be all 'variable'
        IntVec globalMask = (IntVec)endState.maskStart.clone();
        for (int i=0; i<globalMask.v.length; i++) {
            globalMask.v[i] = 1-globalMask.v[i];
            if (globalMask.v[i] == 0) {
                System.out.println("Warning - output "+outputs[i]+" emits deterministic number of symbols ("+endState.posStart.v[i]+")");
            }
        }
        // Combine state positions relative to start and end position.  '1' means fixed from start, '-1' fixed from end.
        // Calculate position; 0 in case of variable position, otherwise position relative to either start (>=0) or end (<=0)
        for (int i=0; i<states.length; i++) {
            State st = (State)objects.get( states[i] );
            st.range = new RangeCode( this, st );

            if (st == startState ) {
                w = (IntVec)st.maskEnd.clone();         // make sure end state is fixed from end, if it is globally fixed.
                w.multiplyComponents( globalMask );     //  This will implement sequence length checking later on
                w.subtract( st.maskStart );
                w.negate();
            } else {
                w = (IntVec)st.maskStart.clone();
                w.multiplyComponents( globalMask );     // zero globally fixed positions; otherwise mask position ends up as 1 + -1 = 0
                w.subtract( st.maskEnd );               // 1=fixed from start, -1=fixed from end, 0=variable
            }
            st.mask = (IntVec)w.clone();            // assign to st.mask
        }

        // Now all states have ranges; time to initialize ranges of blocks
        range = new RangeCode(startState.range);
        for (int i=0; i<sortedBlocks.length; i++) {

            Clique clique = (Clique)objects.get( sortedBlocks[i] );
            clique.initRange( this );
            range.include( clique.range );

        }

        // Loop over all transitions, obtain signatures, and assign per-signature identifiers
        transSignatures = new TreeMap();
        for (int i=0; i<transitions.length; i++) {
            Transition tr = (Transition)objects.get( transitions[i] );
            String sign = tr.getSignature(this);
            if (!transSignatures.containsKey( sign )) {
                transSignatures.put( sign, new ArrayList(0) );
            }
            List l = (List)transSignatures.get( sign );
            tr.sigIdx = l.size();
            l.add( tr.id );
        }


        // Loop over all emissions, obtain signatures, and assign per-signature identifiers
        emitSignatures = new TreeMap();
        for (int i=0; i<emissions.length; i++) {
            Emission em = (Emission)objects.get( emissions[i] );
            String sign = em.getSignature(this);
            if (!emitSignatures.containsKey( sign )) {
                emitSignatures.put( sign, new ArrayList(0) );
            }
            List l = (List)emitSignatures.get( sign );
            em.sigIdx = l.size();
            l.add( em.id );
        }


    }


    // Helper routines for code generation:

    // Returns all length identifiers (currently active) as a comma-separated list
    // If dontkeep==true, the length of the slowest (last) coordinate is returned as 2.
    public String getLengthIds(String prefix, boolean twocolumns) {

        StringBuffer s = new StringBuffer("");
        for (int i=0; i<numOutputs; i++) {
            if (i != 0)
                s.append(",");
            if (twocolumns && i == numOutputs-1) {
                // This is used in ForwardBackward, when no DP table is returned
                s.append(prefix).append("2");
            } else {
                s.append( prefix + ((Output)objects.get(outputs[i])).lenId );
            }
        }
        return s.toString();

    }

    public String getLengthIds(String prefix) {
        return getLengthIds(prefix,false);
    }


    String commaList( List l ) {

        StringBuffer s = new StringBuffer("");
        Iterator i = l.iterator();
        while (i.hasNext()) {
            String str = (String)i.next();
            if (s.length()>0) {
                s.append( "," );
            }
            s.append( "\"" + str + "\"" );
        }
        return s.toString();
    }


    // Returns all state identifiers as comma-separated list of C strings
    public String getStateIds() {

        ArrayList l = new ArrayList();
        for (int i=0; i<states.length; i++) {
            l.add( states[i] );
        }
        return commaList( l );
    }


    // Returns all emission identifiers as comma-separated list of C strings
    public String getEmissionIds() {

        ArrayList l = new ArrayList();
        for (int i=0; i<emissions.length; i++) {
            l.add( emissions[i] );
        }
        return commaList( l );
    }


    // Returns all transition identifiers as comma-separated list of C strings
    public String getTransitionIds() {

        ArrayList l = new ArrayList();
        for (int i=0; i<transitions.length; i++) {
            l.add( ((Transition)objects.get(transitions[i])).id );
        }
        return commaList( l );
    }

    public String getTransitionF() {

        ArrayList l = new ArrayList();
        for (int i=0; i<transitions.length; i++) {
            l.add( ((Transition)objects.get(transitions[i])).from );
        }
        return commaList( l );
    }

    public String getTransitionT() {

        ArrayList l = new ArrayList();
        for (int i=0; i<transitions.length; i++) {
            l.add( ((Transition)objects.get(transitions[i])).to );
        }
        return commaList( l );
    }

    public String getTransitionP() {

        ArrayList l = new ArrayList();
        for (int i=0; i<transitions.length; i++) {
            l.add( ((Transition)objects.get(transitions[i])).probability );
        }
        return commaList( l );
    }

    public String getTransitionE() {

        ArrayList l = new ArrayList();
        for (int i=0; i<transitions.length; i++) {
            l.add( ((Transition)objects.get(transitions[i])).emission );
        }
        return commaList( l );
    }

    public String getOutputIds() {

        ArrayList l = new ArrayList();
        for (int i=0; i<outputs.length; i++) {
            l.add( ((Output)objects.get(outputs[i])).id);
        }
        return commaList( l );
    }

}
