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
#include <fstream>

#include "mybanding.h"


//
// A struct to hold the alignment parameters, and the bandwidth
//

class Pars {

public:
  double iSigma;   // substitution rate
  double iDelta;   // indel probability
  double iR;       // indel lenght parameter
  double iTau;     // sequence length parameter
  int    iWidth;
};


//
// Accessor classes for the standard and banding algorithms and types
//

class NoBanding {

public:

  typedef AlignDPTable    DPTable;
  typedef AlignBaumWelch  BaumWelchCounters;

  static bfloat Forward(DPTable** ppOutTable,Pars pars,vector<char>& iSequence1,vector<char>& iSequence2) {
    return ::Forward(ppOutTable,pars.iDelta,pars.iR,pars.iSigma,pars.iTau,iSequence1,iSequence2);
  }

  static bfloat Backward(DPTable** ppOutTable,Pars pars,vector<char>& iSequence1,vector<char>& iSequence2) {
    return ::Backward(ppOutTable,pars.iDelta,pars.iR,pars.iSigma,pars.iTau,iSequence1,iSequence2);
  }

  static bfloat BackwardBaumWelch(BaumWelchCounters& bw, DPTable* pInTable,Pars pars,vector<char>& iSequence1,vector<char>& iSequence2) {
    return ::BackwardBaumWelch(bw, pInTable,pars.iDelta,pars.iR,pars.iSigma,pars.iTau,iSequence1,iSequence2);
  }

  static bfloat Viterbi_recurse(DPTable** ppOutTable,Pars pars,vector<char>& iSequence1,vector<char>& iSequence2) {
    return ::Viterbi_recurse(ppOutTable,pars.iDelta,pars.iR,pars.iSigma,pars.iTau,iSequence1,iSequence2);
  }
  
  static Path& Viterbi_trace(DPTable* pInTable,Pars pars,vector<char>& iSequence1,vector<char>& iSequence2) {
    return ::Viterbi_trace(pInTable,pars.iDelta,pars.iR,pars.iSigma,pars.iTau,iSequence1,iSequence2);
  }

  static Path& Sample(DPTable* pInTable,Pars pars,vector<char>& iSequence1,vector<char>& iSequence2) {
    return ::Sample(pInTable,pars.iDelta,pars.iR,pars.iSigma,pars.iTau,iSequence1,iSequence2);
  }

};



class YesBanding {

public:

  typedef AlignWithBandingDPTable    DPTable;
  typedef AlignWithBandingBaumWelch  BaumWelchCounters;

  static bfloat Forward(DPTable** ppOutTable,Pars pars,vector<char>& iSequence1,vector<char>& iSequence2) {
    return ::ForwardBanding(ppOutTable,pars.iDelta,pars.iR,pars.iSigma,pars.iTau,pars.iWidth,iSequence1,iSequence2);
  }

  static bfloat Backward(DPTable** ppOutTable,Pars pars,vector<char>& iSequence1,vector<char>& iSequence2) {
    return ::BackwardBanding(ppOutTable,pars.iDelta,pars.iR,pars.iSigma,pars.iTau,pars.iWidth,iSequence1,iSequence2);
  }

  static bfloat BackwardBaumWelch(BaumWelchCounters& bw, DPTable* pInTable,Pars pars,vector<char>& iSequence1,vector<char>& iSequence2) {
    return ::BackwardBaumWelchBanding(bw, pInTable,pars.iDelta,pars.iR,pars.iSigma,pars.iTau,pars.iWidth,iSequence1,iSequence2);
  }

  static bfloat Viterbi_recurse(DPTable** ppOutTable,Pars pars,vector<char>& iSequence1,vector<char>& iSequence2) {
    return ::ViterbiBanding_recurse(ppOutTable,pars.iDelta,pars.iR,pars.iSigma,pars.iTau,pars.iWidth,iSequence1,iSequence2);
  }
  
  static Path& Viterbi_trace(DPTable* pInTable,Pars pars,vector<char>& iSequence1,vector<char>& iSequence2) {
    return ::ViterbiBanding_trace(pInTable,pars.iDelta,pars.iR,pars.iSigma,pars.iTau,iSequence1,iSequence2);
  }

  static Path& Sample(DPTable* pInTable,Pars pars,vector<char>& iSequence1,vector<char>& iSequence2) {
    return ::SampleBanding(pInTable,pars.iDelta,pars.iR,pars.iSigma,pars.iTau,iSequence1,iSequence2);
  }

};




//
// Read one nucleotide sequence on a single line, skipping all gap characters
//

vector<char>& readSequence(istream& is) {

  vector<char>* pSeq = new vector<char>();
  char ch;

  is.get( ch );
  while (ch != '\n') {

    if (ch >= 'a' && ch <= 'z') {
      ch &= 0xDF;  // make upper case
    }

    if (ch == 'A' || ch == 'C' || ch == 'G' || ch == 'T') {
      // in particular, skip gap symbols
      pSeq->push_back(ch);
    }

    is.get( ch );

  }

  return *pSeq;

}


//
// use Baum-Welch training to estimate transition (i.e., indel) parameters
//

template<class T>
void estimate( int iterations, Pars& params, vector<char>& iSeq1, vector<char>& iSeq2 ) {

  typename T::DPTable* pFW;
  typename T::BaumWelchCounters baumWelch;

  for (int iter=1; iter <= iterations; ++iter) {

    // calculate the forward DP table
    cout << "Forward..." << endl;
    bfloat fw = T::Forward(&pFW, params, iSeq1, iSeq2);

    // calculate the Baum-Welch estimated transition counts
    baumWelch.resetCounts();
    cout << "Baum-Welch + backward..." << endl;
    bfloat bw = T::BackwardBaumWelch(baumWelch, pFW, params, iSeq1, iSeq2);

    cout << "Iteration " << iter << ": likelihood = " << fw << endl;

    // remove the forward table
    delete pFW;

    // get the expected transition counts
    double trMM = baumWelch.transitionBaumWelchCount00[ baumWelch.transitionIndex("trMM") ];
    double trMI = baumWelch.transitionBaumWelchCount00[ baumWelch.transitionIndex("trMI") ];
    double trMD = baumWelch.transitionBaumWelchCount00[ baumWelch.transitionIndex("trMD") ];

    double trII = baumWelch.transitionBaumWelchCount00[ baumWelch.transitionIndex("trII") ];
    double trIM = baumWelch.transitionBaumWelchCount00[ baumWelch.transitionIndex("trIM") ];
    double trDD = baumWelch.transitionBaumWelchCount00[ baumWelch.transitionIndex("trDD") ];
    double trDM = baumWelch.transitionBaumWelchCount00[ baumWelch.transitionIndex("trDM") ];

    // calculate the new parameters
    params.iR = (trII + trDD) / (trII + trIM + trDD + trDM);
    params.iDelta = (trMI + trMD) / (trMI + trMD + trMM);
  }
}
    


//
// Compute a Viterbi alignment
//

template<class T>
Path& viterbi( Pars& pars, vector<char> iSeq1, vector<char> iSeq2 ) {

  typename T::DPTable* pViterbiTable;

  cout << "Viterbi recursion..." << endl;
  T::Viterbi_recurse(&pViterbiTable, pars, iSeq1, iSeq2 );

  cout << "Viterbi traceback..." << endl;
  Path& path = T::Viterbi_trace(pViterbiTable, pars, iSeq1, iSeq2 );
  delete pViterbiTable;

  return path;
}



//
// This computes the posterior probability of being in a particular state
//

template<class T>
double posterior( const string state, int i, int j, typename T::DPTable* pFW, typename T::DPTable* pBW ) {

  if (pFW->outputId[0] == "sequence2") {
    // the first coordinate in getProb refers to the fastest coordinate (inner loop); in this
    // case, this is sequence2
    swap(i,j);
  }
  bfloat total = pBW->getProb("start",0,0);
  bfloat fw = pFW->getProb( state, i, j );
  bfloat bw = pBW->getProb( state, i, j );
  return (fw * bw)/total;

}


//
// Compute posteriors for alignment columns, marginalized over gap positions
//

template<class T>
double marginalizedPosterior( const string state, int i, int j, typename T::DPTable* pFW, typename T::DPTable* pBW ) {

  if (state == "match") {

    return posterior<T>( state, i, j, pFW, pBW );

  } else if (state == "insert") {

    double p = 0.0;
    for (int y=0; y < pFW->iLen2; ++y) {
      p += posterior<T>( state, i, y, pFW, pBW );
    }
    return p;

  } else if (state == "delete") {

    double p = 0.0;
    for (int x=0; x < pFW->iLen1; ++x) {
      p += posterior<T>( state, x, j, pFW, pBW );
    }
    return p;

  }

  cout << "Unknown state: " << state << endl;
  return 0;

}


//
// Print the Viterbi alignment, and a summary of the posteriors
//

template<class T>
void report( Path& path, typename T::DPTable* pFW, typename T::DPTable* pBW, vector<char>& iSeq1, vector<char>& iSeq2 ) {

  int x = 0;
  int y = 0;

  string line1 = "";
  string line2 = "";
  string line3 = "";

  // Loop over all transitions, except the last one (to the end state)
  for (int i=0; i < (int)path.size()-1; i++) {

    int stateId = path.toState(i);     
    string state = pFW->stateId[stateId];

    if (state == "match") {
      line1 += iSeq1[x++];
      line2 += iSeq2[y++];
    } else if (state == "insert") {
      line1 += iSeq1[x++];
      line2 += "-";
    } else if (state == "delete") {
      line1 += "-";
      line2 += iSeq2[y++];
    }

    double posterior = marginalizedPosterior<T>( state, x, y, pFW, pBW );
    char postId = "0123456789"[ min( int(posterior*10.0), 9) ];
    
    line3 += postId;

  }

  cout << line1 << endl;
  cout << line2 << endl;
  cout << line3 << endl;

}



template<class T>
void execute( int iterations, Pars pars, vector<char>& iSeq1, vector<char>& iSeq2 ) {

    // estimate iDelta and iR using Baum-Welch training
    estimate<T>( iterations, pars, iSeq1, iSeq2 );

    // calculate the Viterbi path
    Path& path = viterbi<T>(pars, iSeq1, iSeq2 );

    // calculate the Forward and Backward tables, to compute posteriors
    typename T::DPTable *pFW, *pBW;
    cout << "Forward..." << endl;
    T::Forward (&pFW, pars, iSeq1, iSeq2 );
    cout << "Backward..." << endl;
    T::Backward (&pBW, pars, iSeq1, iSeq2 );

    // report the results
    cout << endl << "Alignment:" << endl;
    report<T>( path, pFW, pBW, iSeq1, iSeq2 );

    // finished
    delete pFW;
    delete pBW;
}



int main(int argc, char** argv) {

  // set initial parameters
  Pars pars;
  pars.iSigma = 0.4;
  pars.iDelta = 0.05;
  pars.iR = 0.6;
  pars.iTau = 0.0001;
  pars.iWidth = 0;

  char* file = (char*)"sequence.fa";

  if (argc == 1) {

    cout << "Usage: " << argv[0] << " file.fa [bandwidth]\n" << endl;

    cout << "'file.fa':   two nucleotide sequences on lines 2 and 3; gaps characters are ignored.\n";
    cout << "'bandwidth': size of the dynamic programming band used for the recursion.\n";
    cout << "             A reasonable value is 150.  If not given, a non-banded algorithm is used.\n\n\n";
    cout << "Using defaults: bandwidth=150 and file=sequence.fa:\n" << endl;
    
    pars.iWidth = 150;

  } else {

    file = argv[1];

  }

  if (argc == 3) {

    if (sscanf(argv[2],"%d",&pars.iWidth) == 0) {
      cout << "Error interpreting width parameter '" << argv[2] << "'" << endl;
      exit(1);
    }
    if (pars.iWidth < 1) {
      cout << "Invalid width parameter: " << pars.iWidth << endl;
      exit(1);
    }
  }

  if (argc > 3) {
    cout << "Too many parameters" << endl;
    exit(1);
  }

  std::ifstream ifsIn( file );

  // skip first line
  readSequence( ifsIn );

  // read two sequences
  vector<char>& iSeq1 = readSequence( ifsIn );
  vector<char>& iSeq2 = readSequence( ifsIn );
  cout << "Read sequences.  Lengths: " << iSeq1.size() << " and " << iSeq2.size() << ".  ";

  if (pars.iWidth == 0) {

    cout << "No banding" << endl;
    execute<NoBanding>( 5, pars, iSeq1, iSeq2 );

  } else {

    cout << "Bandwidth: " << pars.iWidth << endl;
    execute<YesBanding>( 5, pars, iSeq1, iSeq2 );

  }

}
