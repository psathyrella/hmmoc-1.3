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


public class Generator {

    HMM hmm;
    TreeMap objects;
    Book book;

    Generator( HMM himamo, Book bok, TreeMap objs ) {
        hmm = himamo;
        objects = objs;       // we could get this from the HMM...
        book = bok;
    }

    // Generator used by RangeCode, when no code book is available
    Generator( HMM himamo ) {
        hmm = himamo;
        objects = hmm.objects;
        book = null;
    }

    // Copy generator
    Generator( Generator g ) {
        hmm = g.hmm;
        objects = g.objects;
        book = g.book;
    }

    // helper function
    Code getCode(String s) {

        return getCode(s, objects);

    }

    // helper function
    static public Code getCode(String s, TreeMap objects) {
        Code c = (Code)objects.get(s);
        if (c==null) {
            throw new Error("Could not find code snippet "+s);
        }
        return c;
    }

    static public String makeCommaList(ArrayList l ) {

        String s = l.toString();
        return s.substring(1, s.length()-1);

    }


}


