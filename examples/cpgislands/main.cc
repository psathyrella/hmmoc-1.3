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
#include <iostream>
#include <string>
#include <stdlib.h>

#include "cpgislands.h"


using namespace std;



char sampleNuc( double* aEmission ) {

  double p = random() / (double)RAND_MAX;

  if (p < aEmission[0]) return 'A';
  p -= aEmission[0];
  if (p < aEmission[1]) return 'T';
  p -= aEmission[1];
  if (p < aEmission[2]) return 'C';
  p -= aEmission[2];
  if (p < aEmission[3]) return 'G';

  cout << "Error sampling..." << endl;
  return 'G';

}


void simulateSequence( char* aSeq, int iSeqLen, double* aEmission, double iStay ) {

  int state = 0;  // start in a cpg-island state
  
  for (int i=0; i<iSeqLen; i++) {

    if (state == 0) {
      aSeq[i] = sampleNuc( aEmission );
    } else {
      if (aSeq[i-1] == 'C') {
	aSeq[i] = sampleNuc( aEmission + 4 );
      } else {
	aSeq[i] = sampleNuc( aEmission );
      }
    }

    if (random() / (double)RAND_MAX > iStay) {
      // switch from cpg island to non-cpg island
      state = 1-state;
    }

  }
}



int main()
{
  // define GC content of simulated sequences, and 1 / length of cpg islands
  double iGCcontent = 0.4;
  double iStay = 0.01;

  // conditional emission distribution (following C, and following any other nucleotide)
  double aEmission[8] = { (1-iGCcontent)/2,  /* a, normal */
			  (1-iGCcontent)/2,  /* t */
			  iGCcontent/2,      /* c */
			  iGCcontent/2,      /* g */
			  (1-iGCcontent)/2 + iGCcontent/8,   /* a, following c */
			  (1-iGCcontent)/2 + iGCcontent/8,   /* t, following c */
			  iGCcontent/2 + iGCcontent/8,       /* c, following c */
			  iGCcontent/8 };                    /* g, following c (lower frequency) */

  int iSeqLen = 100000;

  char aSeq[iSeqLen];
  simulateSequence( aSeq, iSeqLen, aEmission, iStay );

  CpGislandDPTable* pT1, *pT2, *pT3;
  CpGislandBaumWelch baumwelch;

  bfloat vit = Viterbi_recurse(&pT3, aSeq, 1.0/iSeqLen, iStay, aEmission, iSeqLen);
  cout << "Viterbi:" << vit << endl;

  bfloat fw = Forward(&pT1, aSeq, 1.0/iSeqLen, iStay, aEmission, iSeqLen);
  cout << "Forward:" << fw << endl;

  bfloat bw = Backward(baumwelch, pT1, &pT2, aSeq, 1.0/iSeqLen, iStay, aEmission, iSeqLen);
  cout << "Backward:" << bw << endl;

  // Print counts for all order-0 transitions
  cout << "Order-0 transitions:" << endl;
  for (int i=0; i < baumwelch.transitionDimension0; i++) {
    int id = baumwelch.transitionIdentifier0[i];
    cout << pT1->transitionId[id] << " " 
	 << pT1->transitionFrom[id] << "->" 
	 << pT1->transitionTo[id] << ":" 
	 << baumwelch.transitionBaumWelchCount0[i] << endl;
  }

  cout << endl << "First-order transitions:" << endl;
  // Print counts for all first-order transitions
  for (int i=0; i<baumwelch.transitionDimension1; i++) {
    int id = baumwelch.transitionIdentifier1[i];
    for (int j=0; j<4; j++) {
      cout << "Following " << "ACGT"[j]              // Note: alphabetical order
	   << " id=" << pT1->transitionId[id]
	   << "\tfrom=" << pT1->transitionFrom[id]
           << "\tto=" << pT1->transitionTo[id] 
	   << "\tcount=" << baumwelch.transitionBaumWelchCount1[j][i] << endl;
    }
  }

  // Print counts for normal, first-order emissions (signature=2):
  cout << endl << "First-order emissions:  (state id=" << pT1->emissionId[ baumwelch.emissionIdentifier2[0] ] << ")" << endl;
  for (int i=0; i<4; i++) {
    for (int j=0; j<4; j++) {
      cout << "Following " << "ACGT"[i] 
	   << ": Emit " << "ACGT"[j] 
	   << ":  count=" << baumwelch.emissionBaumWelchCount2[i][j][0] << endl;
    }
  }

  return 0;

}

