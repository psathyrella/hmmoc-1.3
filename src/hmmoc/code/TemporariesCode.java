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



import hmmoc.xml.HMM;
import hmmoc.xml.Code;



public class TemporariesCode extends Generator {


    static final String probId = "iTempProb";
    static final String resultId = "iTempResult";
    static final String vectorId = "iTempVector";
    static final String intVecId = "iTempIntVec";


    String temporary;                // scope id, into whose Init block declaration should go
    Text probTemporaried;            // actual scope that is initialized for probId
    Text resultTemporaried;          // same for resultId
    Text vectorTemporaried;
    Text intVecTemporaried;
    int maxVectorIndex;
    int maxIntVecIndex;
    int resultTemporaries;
    int probTemporaries;


    public TemporariesCode( HMM himamo, Book book, String scope ) {

        super (himamo, book, himamo.objects);
        temporary = scope;
        probTemporaried = null;
        probTemporaries = 0;
        resultTemporaried = null;
        resultTemporaries = 0;
        vectorTemporaried = null;
        intVecTemporaried = null;
        maxVectorIndex = 0;
        maxIntVecIndex = 0;

    }


    public void exit() {

        emitVecDecl();
        emitIntVecDecl();
        emitResultDecl();
        emitProbDecl();

    }

    void emitVecDecl() {
        if (vectorTemporaried != null) {
            book.addInitText( temporary,
                    ((Code)objects.get("hmlVectorIntermediateDeclare")).bind(vectorId,
                            Integer.toString(maxVectorIndex + 1) ) );
            maxVectorIndex = 0;
        }
    }

    void emitIntVecDecl() {
        if (intVecTemporaried != null) {
            book.addInitText( temporary,
                    ((Code)objects.get("hmlIntVecIntermediateDeclare")).bind(intVecId,
                            Integer.toString(maxIntVecIndex + 1) ) );
            maxIntVecIndex = 0;
        }
    }


    void emitProbDecl() {

        if (probTemporaried != null) {
            book.addInitText( temporary, ((Code)objects.get("hmlProbIntermediateDeclare")).bind(probId,
                    Integer.toString(probTemporaries + 1) ) );
            probTemporaries = 0;
        }
    }


    // Get identifier referring to temporary probability (potentially long exponent)
    public String getTempProb() {

        return getTempProb(0);

    }


    public String getTempProb(int idx) {

        if (book.getScope(temporary) != probTemporaried) {

            emitProbDecl();
            probTemporaried = book.getScope(temporary);

        }
        if (idx > probTemporaries)
            probTemporaries = idx;

        return probId + "[" + Integer.toString(idx) + "]";

    }



    void emitResultDecl() {

        if (resultTemporaried != null) {
            book.addInitText( temporary, ((Code)objects.get("hmlRealIntermediateDeclare")).bind(resultId,
                    Integer.toString(resultTemporaries + 1) ) );
            resultTemporaries = 0;
        }
    }


    // Get identifier referring to temporary probability (potentially long exponent)
    public String getTempResult() {

        return getTempResult(0);

    }


    public String getTempResult(int idx) {

        if (book.getScope(temporary) != resultTemporaried) {

            emitResultDecl();
            resultTemporaried = book.getScope(temporary);

        }
        if (idx>resultTemporaries)
            resultTemporaries = idx;

        return resultId + "[" + Integer.toString(idx) + "]";

    }



    // Get identifier referring to temporary vector (double)
    public String getVectorEntry(int i) {

        if (book.getScope(temporary) != vectorTemporaried) {

            // Emit any previous definitions, before making new one
            emitVecDecl();
            vectorTemporaried = book.getScope(temporary);

        }

        if (i>maxVectorIndex)
            maxVectorIndex = i;

        return vectorId + "[" + Integer.toString(i) + "]";

    }

    // Get identifier referring to temporary int vector 
    public String getIntVecEntry(int i) {

        if (book.getScope(temporary) != intVecTemporaried) {

            // Emit any previous definitions, before making new one
            emitIntVecDecl();
            intVecTemporaried = book.getScope(temporary);

        }

        if (i>maxIntVecIndex)
            maxIntVecIndex = i;

        return intVecId + "[" + Integer.toString(i) + "]";

    }


    public String getVectorName() {

        return vectorId;

    }


    public String getIntVecName() {

        return intVecId;

    }

}
