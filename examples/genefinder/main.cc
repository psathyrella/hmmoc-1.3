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
#include <fstream>
#include <iomanip>
#include <sstream>

#include "genefinder.h"


using namespace std;


/***********************************
 *
 *  
 *  A quick-and-dirty gene finder,
 *  to show how HMMoC can be used
 *  to generate G(eneralized) HMMs,
 *  which may output more than one
 *  symbol per state or transition.
 *
 *
 ***********************************/


// Return a character a-zA-Z representing a posterior probability (from 0 to 1)
char posteriorCode( double posterior ) {

  int p = int(min(51.0,posterior*52));
  return p < 26 ? (char)('a'+p) : (char)('A'+p-26);

}

// Prints a representation of the gene model, and posterior probabilities
void printPathWithPosterior( Path& path, GeneFinderDPTable* pFW, GeneFinderDPTable* pBW, const char* aSeq, bfloat likelihood) {

  int idx = 0;
  int emitted = 0;
  int state = path.fromState(idx);
  ostringstream posteriors;
  cout << "Gene model: (upper case are codons)" << endl;
  while (state != pFW->getId("end")) {
    
    int to = path.toState(idx);
    if (to == pFW->getId("background")) {
      // emit lower case
      cout << (char)(aSeq[ emitted ] | 0x20);
      double p = pFW->getProb( to, emitted+1 ) * pBW->getProb( to, emitted+1 ) / likelihood;
      posteriors << posteriorCode( p );
    } else if (path.emission(idx)[0] == 3) {
      // codon
      cout << aSeq[ emitted ] << aSeq[ emitted+1 ] << aSeq[ emitted+2 ];
      double p = pFW->getProb( to, emitted+3 ) * pBW->getProb( to, emitted+3 ) / likelihood;
      posteriors << " " << posteriorCode( p ) << " ";
    }
    // update number of emitted symbols
    emitted += path.emission(idx)[0];
    // follow path
    idx += 1;
    state = to;
  }
  cout << endl;
  cout << "Posterior probabilities (a-zA-Z; a=0, Z=1):" << endl;
  cout << posteriors.str() << endl;

}



int main( int argc, char **argv )
{
  // define expectations for gene density (per nucleotide) and gene length (in codons)
  double iGeneDensity = 0.001;
  double iGeneLength = 100;

  // emission distribution for background
  double aEmission[4] = { 0.25, 0.25, 0.25, 0.25 };

  // read sequence
  if (argc != 2) {
    cout << "Usage: " << argv[0] << " file.fasta" << endl;
    cout << "The fasta file must contain a description, and a sequence on a single line (all nucleotides in UPPER case)" << endl;
    return 1;
  }
  ifstream inputfile;
  inputfile.open(argv[1]);
  string line;
  getline(inputfile,line);
  if (line[0] != '>') {
    cerr << "File " << argv[1] << " does not start with '>'" << endl;
    return 1;
  }
  getline(inputfile,line);
  for (int i=0; i<(int)line.length(); i++) {
    if (line[i] != 'A' && line[i] != 'C' && line[i] != 'G' && line[i] != 'T') {
      cerr << "Sequence contains non-nucleotide character at position " << i << ":'" << line[i] << "'" << endl;
      return 1;
    }
  }
    
  GeneFinderDPTable* pFWTable, *pBWTable, *pViterbiTable;
  GeneFinderBaumWelch baumwelch;

  bfloat vit = Viterbi_recurse(&pViterbiTable, line.c_str(), iGeneDensity, iGeneLength, aEmission, line.length());
  cout << "Viterbi:" << vit << endl;

  Path& path = Viterbi_trace(pViterbiTable, line.c_str(), iGeneDensity, iGeneLength, aEmission, line.length());

  bfloat fw = Forward(&pFWTable, line.c_str(), iGeneDensity, iGeneLength, aEmission, line.length());
  cout << "Forward:" << fw << endl;

  bfloat bw = Backward(baumwelch, pFWTable, &pBWTable, line.c_str(), iGeneDensity, iGeneLength, aEmission, line.length());
  cout << "Backward:" << bw << endl << endl;

  printPathWithPosterior( path, pFWTable, pBWTable, line.c_str(), fw );

  // Print counts for all order-0 transitions
  cout << endl << "Order-0 transitions:" << endl;
  for (int i=0; i < baumwelch.transitionDimension0; i++) {
    int id = baumwelch.transitionIdentifier0[i];
    cout << setw(10) << pFWTable->transitionId[id] << " " 
	 << setw(25) << pFWTable->transitionFrom[id] + "->" + pFWTable->transitionTo[id] << ": " 
	 << baumwelch.transitionBaumWelchCount0[i] << endl;
  }

  cout << endl;

  // Print background emission counts (just one)
  cout << "Background emission:" << endl;
  for (int i=0; i<baumwelch.emissionDimension1; i++) {
    int id = baumwelch.emissionIdentifier1[i];
    for (int j=0; j<4; j++) {
      cout << pFWTable->emissionId[id] << " " 
	   << "ACGT"[j] << ": "
	   << baumwelch.emissionBaumWelchCount1[j][i] << endl;
    }
  }
  cout << endl;

  // Print codon emission counts:
  cout << "Codon emissions: "<< endl << "        ";
  for (int i=0; i<baumwelch.emissionDimension3; i++) {
    cout << setw(16) << pFWTable->emissionId[ baumwelch.emissionIdentifier3[i] ];
  }
  cout << endl;
  for (int j=0; j<4; j++) {
    for (int k=0; k<4; k++) {
      for (int l=0; l<4; l++) {
	cout << "ACGT"[j] << "ACGT"[k] << "ACGT"[l] << " : ";
	for (int i=0; i<baumwelch.emissionDimension3; i++) {
	  cout << setw(13) << baumwelch.emissionBaumWelchCount3[j][k][l][i];
	}
	cout << endl;
      }
    }
  }

  return 0;
  
}

