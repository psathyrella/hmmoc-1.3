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

import hmmoc.util.ParseUtils;
import hmmoc.code.Text;
import hmmoc.code.TemporariesCode;


public class Probability {

    public String id;

    TreeMap objects;
    HMM hmm;

    String[] depends;
    String codeString;
    Code code;

    // Parses <probability> element
    public Probability( Element elem, TreeMap idMap, HMM hmm0 ) {
	
    	id = elem.getAttributeValue("id");

    	hmm = hmm0;
    	objects = hmm.objects;

    	List al = ParseUtils.parseMultiAttribute(elem,"depends","output",idMap);
    	depends = new String[ al.size() ];
    	for (int i=0; i<al.size(); i++) {
    		depends[i] = ((Element)al.get(i)).getAttributeValue("id");
    	}

    	codeString = ParseUtils.parseChild( elem, "code", idMap ).getAttributeValue("id");
    	code = (Code)objects.get( codeString );
    	if (code == null) {
    		throw new Error("Probability: referred to code element "+codeString+" but could not find it.");
    	}
    }


    public void reset() {
    	code.reset();
    }
    
    public void bindInput(int ord, String withWhat) {
	
    	code.bind(ord, withWhat, "default");

    }


    public void bindInput(int output, int depth, String withWhat) {

    	code.bind(hmm.outputs[output], depth, withWhat);

    }


    public boolean canBindInput(int output, int depth) {

    	return code.canBind(hmm.outputs[output], depth);

    }


    // true if <code> has <identifier>s attached to explicit <output>s
    public boolean hasOutputs() {

    	return code.hasOutputs;

    }

    // Returns number of outputs that have nonempty bindings
    public int activeOutputs() {

    	return code.activeIdentifiers("default");

    }

    // Returns number of positions on which probability element depends
    public int numDependentPositions() {
    	
    	return code.countIdType("position");
    	
    }
    
    public boolean canBindPosition(String position) {
    	
    	return code.canBindPosition(position);
    		
    }

    public void bindPosition(int output, String withWhat) {

    	code.bindOutput(hmm.outputs[output], withWhat);

    }

    public Text getInitText(TreeMap objects, TemporariesCode temp) {

    	if (code.type.equals("expression"))
    		return new Text("");
    	code.bind(0, temp.getTempResult(),"result");
    	return code.getText();
    }


    public String getResult(TemporariesCode temp) {

    	if (code.type.equals("expression")) {
    		String s = code.getText().toString();
    		code.reset();
    		return s;
    	} else {
    		code.reset();
    		return temp.getTempResult();
    	}
    }
}

