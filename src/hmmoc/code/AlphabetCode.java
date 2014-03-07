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
import hmmoc.xml.Alphabet;



public class AlphabetCode extends Generator {


    Alphabet alphabet;
    String declare;
    String init;
    boolean initedDecl = false;
    boolean initedInit = false;
    String dummyLetter;
    

    public AlphabetCode( HMM himamo, Book book, Alphabet alphabeta, String decl0, String init0 ) {

	super (himamo, book, himamo.objects);
	alphabet = alphabeta;
	dummyLetter = alphabet.alphabet[0];
	declare = decl0;
	init = init0;
    }


    void init() {

    Text decltext = getDeclaration();
    if (decltext != null) {
    	book.addInitText(declare, decltext );
    }
    Text inittext = getInitialization();
    if (inittext != null) {
    	book.addInitText(init, inittext );
    }
    }

    public Text getDeclaration() {
    	if (initedDecl) {
    		return null;
    	}
    	initedDecl = true;
    	ArrayList l = new ArrayList(0);
    	for (int i=0; i<alphabet.alphabet.length; i++)
    	    l.add( alphabet.alphabet[i] );
    	return getCode("hmlAlphabetDeclare").bind(alphabet.id, getSize(), makeCommaList(l), alphabet.type, Integer.toString(alphabet.maxchar) );
    }
    
    public Text getInitialization() {
    	if (initedInit) {
    		return null;
    	}
    	initedInit = true;
    	return getCode("hmlAlphabetInit").bind(alphabet.id, getSize(), alphabet.type, Integer.toString(alphabet.maxchar) );
    }

    
    public Alphabet getAlphabet() {
    	return alphabet;
    }
    
    public String getSize() {

	return Integer.toString(alphabet.alphabet.length);

    }


    public String getIndex(String symbol) {

	init();
	return getCode("hmlAlphabetGetIndex").bind(alphabet.id,symbol).toString();

    }


    public String getSymbol(String index) {

	init();
	return getCode("hmlAlphabetGetSymbol").bind(alphabet.id,index).toString();

    }

    public String getDummy() {

	return dummyLetter;

    }

}
