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




import hmmoc.xml.Code;
import hmmoc.xml.HMM;



public class MatrixCode extends Generator {

    static final String matrixId = "matrix";
    static int uniqueId = 0;

    String matrixName;
    String matrixInvName;
    String definition;               // definition of matrix variables
    String calculate;                // calculation of matrix value, and inverse
    String exit;
    int dimension;                 
    boolean hasInverse = false;
    boolean hasInverseDeclaration = false;


    public MatrixCode( HMM himamo, Book book ) {

	super (himamo, book, himamo.objects);

	matrixName = matrixId + uniqueId;
	matrixInvName = matrixName + "inv";

	uniqueId += 1;

    }


    public void init( String def, String calc, String ex, int dim ) {

	dimension = dim;
	definition = def;
	calculate = calc;
	exit = ex;

	Code cd = getCode("hmlMatrixDeclare");
	Code ci = getCode("hmlMatrixInit");

	book.addInitText( definition, cd.bind(matrixName, String.valueOf(dim) ) );
	book.addInitText( calculate, ci.bind(matrixName) );

    }


    public void exit() {

	if (hasInverse) {
	    hasInverse = false;
	    Code c = getCode("hmlRemoveInverseMatrix");
	    book.addExitText( exit, c.bind( matrixInvName ) );
	}

    }

    public void addToEntry( int i, int j, String expression ) {

	// We could cache these, to speed things up when coefficients are accessed multiple times.

	if ((i<0)||(j<0)||(i>=dimension)||(j>=dimension)) {
	    throw new Error("Matrix coefficient access out of bounds: ("+i+","+j+"), dimension="+dimension);
	}

	if (hasInverse) {
	    throw new Error("Altering matrix elements after accessing coefficients from inverse matrix");
	}

	Code c = getCode("hmlMatrixEntryIncrement");

	book.addInitText( calculate, c.bind(matrixName, Integer.toString(i), Integer.toString(j), expression) );

    }


    public String getMatrixName() {

	return matrixName;

    }


    public String getInverseMatrixCoeff(int i, int j) {

	if (!hasInverseDeclaration) {
	    Code c = getCode("hmlMatrixInvDeclare");
	    book.addInitText( definition, c.bind(matrixInvName) );
	    hasInverseDeclaration = true;
	}

	if (!hasInverse) {
	    Code c = getCode("hmlComputeInverseMatrix");
	    book.addInitText( calculate, c.bind(matrixInvName, matrixName) );
	    hasInverse = true;
	}
	if ((i<0)||(j<0)||(i>=dimension)||(j>=dimension)) {
	    throw new Error("Matrix coefficient access out of bounds: ("+i+","+j+"), dimension="+dimension);
	}
	Code c = getCode("hmlGetMatrixInvEntry");
	return c.bind(matrixInvName, Integer.toString(i), Integer.toString(j)).toString();
    }
}
