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
package hmmoc.util;

/**
 *
 *  IntVec -- mathematical vector of integers.
 *
 *  Vectors provide equals() and hashCode() based on value-identity,
 *  so can be used in maps, but are NOT immutable, so take care not
 *  to change the value of map-keys.
 *
 *  @author Gerton Lunter
 *  
 *  20/3/2003
 *
 */



public class IntVec implements Cloneable {
    public int[] v;

    /** Constructor, null vector of given dimension */
    public IntVec(int iLen) {
        v = new int[iLen];
    }

    /** Constructor, +/- unit vector of given dimension */
    public IntVec(int iLen, int iN) {
        v = new int[Math.abs(iLen)];
        if (iLen<0)
            v[iN] = -1;
        else
            v[iN] = 1;
    }

    /** Constructor, copy of existing vector */
    public IntVec(IntVec vec) {
        v = (int[]) vec.v.clone();
    }

    /** Constructor, copy of integer array - handy for printing */
    public IntVec(int[] iArr) {
        v = (int[]) iArr.clone();
    }

    /** Internal, to make sure vectors are of same length. */
    private void check(IntVec vec) {
        if (vec.v.length != v.length) {
            throw new Error("IntVec.check: Vector sizes don't match: "+vec.v.length+" and "+v.length);
        }
    }

    /** Make equals() reflect value-identity instead of object-identity */
    public boolean equals(Object iObj) {
        if (iObj instanceof IntVec) {
            IntVec vec = (IntVec)iObj;
            if (vec.v.length != v.length)
                return false;
            for (int i=0; i<v.length; i++) {
                if (v[i] != vec.v[i])
                    return false;
            }
            return true;
        }
        return false;
    }

    /** hashCode() reflects value-identity instead of object-identity */
    public int hashCode() {
        int iCode = 0;
        for (int i=0; i<v.length; i++) {
            iCode = (iCode*75) + v[i];
        }
        return iCode;
    }

    /** Clone this object */
    public Object clone() {

        try {
            // This magically creates an object of the right type
            IntVec iObj = (IntVec) super.clone();
            iObj.v = (int[]) v.clone();
            return iObj;
        } catch (CloneNotSupportedException e) {
            System.out.println("IntVec.clone: Something happened that cannot happen -- ?");
            return null;
        }

    }


    /** Overriding built-in toString, produces Mathematica-readable output */
    public String toString() {
        String iResult = "{";
        for (int i=0; i<v.length; i++) {
            if (i != 0)
                iResult += ",";
            iResult += v[i];
        }
        iResult += "}";
        return iResult;
    }

    /** Now the math thingies */
    public int innerProduct(IntVec vec) {
        check(vec);
        int iSum = 0;
        for (int i=0; i<v.length; i++)
            iSum += vec.v[i]*v[i];
        return iSum;
    }

    public void multiplyComponents(IntVec vec) {
        check(vec);
        for (int i=0; i<v.length; i++) {
            v[i] *= vec.v[i];
        }
    }

    public boolean zeroEntry() {
        for (int i=0; i<v.length; i++)
            if (v[i]==0)
                return true;
        return false;
    }


    public boolean isZero() {
        for (int i=0; i<v.length; i++)
            if (v[i]!=0)
                return false;
        return true;
    }


    public void assign(IntVec vec) {
        v = (int[]) vec.v.clone();
    }

    public IntVec negate() {
        for (int i=0; i<v.length; i++)
            v[i] = -v[i];
        return this;
    }

    public void add(IntVec vec) {
        check(vec);
        for (int i=0; i<v.length; i++)
            v[i] += vec.v[i];
    }

    public void addMultiple(IntVec vec, int iMultiple) {
        check(vec);
        for (int i=0; i<v.length; i++)
            v[i] += vec.v[i] * iMultiple;
    }

    public void subtract(IntVec vec) {
        check(vec);
        for (int i=0; i<v.length; i++)
            v[i] -= vec.v[i];
    }

    public void minComponents(IntVec vec) {
        check(vec);
        for (int i=0; i<v.length; i++) {
            v[i] = Math.min( v[i], vec.v[i] );
        }
    }

    public void maxComponents(IntVec vec) {
        check(vec);
        for (int i=0; i<v.length; i++) {
            v[i] = Math.max( v[i], vec.v[i] );
        }
    }

}
	


