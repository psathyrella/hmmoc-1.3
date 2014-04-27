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
#include <vector>

extern hmmer_hmmShortReal iE[][20];
extern hmmer_hmmShortReal iT[];

void initpars(void);

//
// Read one nucleotide sequence on a single line, skipping all gap characters
//

vector<char>& readSequence(istream& is) {

  vector<char>* pSeq = new vector<char>();
  char ch;
  char* aacids = "ACDEFGHIKLMNPQRSTVWY";

  is.get( ch );
  while (!is.eof() && ch != '>') {

    if (ch >= 'a' && ch <= 'z') {
      ch &= 0xDF;  // make upper case
    }

    int i;
    for (i=0; aacids[i]!=0 && aacids[i]!=ch; i++) {}

    if (aacids[i]) {
      // in particular, skip gap symbols
      pSeq->push_back(ch);
    }

    is.get( ch );

  }

  // read away header line of next sequence
  while (!is.eof() && ch != '\n') {
    is.get(ch);
  }

  return *pSeq;

}



//
// Compute a Viterbi alignment
//

Path& viterbi( vector<char> iSeq1 ) {

  hmmer_hmmDPTable* pViterbiTable;

  cout << "Viterbi recursion..." << endl;
  hmmer_hmmReal viterbi = Viterbi_recurse(&pViterbiTable, iE, iT, iSeq1 );

  cout << viterbi << endl;

  cout << "Viterbi traceback..." << endl;
  Path& path = Viterbi_trace(pViterbiTable, iE, iT, iSeq1);
  delete pViterbiTable;

  return path;
}


double forward( vector<char> iSeq1 ) {

  hmmer_hmmDPTable* pDPTable;

  cout << "Forward recursion..." << endl;
  hmmer_hmmReal forward = Forward(&pDPTable, iE, iT, iSeq1 );
  delete pDPTable;

  cout << forward << endl;

  return log(forward);

}



int main(int argc, char** argv) {

  char* file = "sequence.fa";
  bool runviterbi = false;

  int fileidx = 1;

  if (argc >= 2 && strcmp(argv[1],"--viterbi")==0) {
    runviterbi = true;
    fileidx += 1;
  }

  if (fileidx != argc-1) {

    cout << "Usage: " << argv[0] << " [--viterbi] file.fa \n" << endl;
    return 1;

  }

  file = argv[fileidx];  

  std::ifstream ifsIn( file );

  initpars();

  do {

    // read sequence
    vector<char>& iSeq1 = readSequence( ifsIn );
    cout << "Read sequence.  Length: " << iSeq1.size() << endl;

    //for (int i=0; i<iSeq1.size(); i++) cout << iSeq1[i];
    //cout << endl;

    if (iSeq1.size() > 0) {

      if (runviterbi) {
	viterbi( iSeq1 );
      } else {
	forward( iSeq1 );
      }

    }

  } while (!ifsIn.eof());

}
