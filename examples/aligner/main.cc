#include <iostream>
#include <iomanip>
#include <fstream>

#include "aligner.h"
#include <sstream>
#include <cassert>

#include "HmmDriver.h"

//----------------------------------------------------------------------------------------
// void readFile(istream& is, vector<pair<string,vector<char> > > &seqs) {
//   string line;
//   while(getline(is,line)) {
//     stringstream ss(line);
//     if(line[0]=='#') continue; // skip comments
//     string id,seq;
//     ss >> id >> seq; // not using the later columns a.t.m.
//     vector<char> vseq;
//     unsigned iT(0); // number of initial Ts encountered (for removal)
//     int ich(0);
//     for (char &ch : seq) {
//       if(ch>='a' && ch<='z')
// 	ch &= 0xDF;  // make upper case
//       if(iT<9) {
// 	assert(ch=='T');
// 	iT++;
// 	continue;
//       } else if(ich>118) { // stop thingy at end I think we also want to ignore
// 	continue;
//       } else if(ch=='A' || ch=='C' || ch=='G' || ch=='T') { // skip gaps
// 	vseq.push_back(ch);
//       } else {
// 	cerr << "WARNING: encountered " << ch << " in sequence" << endl;
// 	continue;
//       }
//       ++ich;
//     }
//     pair<string,vector<char> > newSequence(pair<string,vector<char> >(id,vseq));
//     seqs.push_back(newSequence);
//   }
// }
int main(int argc, char** argv) {
  srand(getpid());
  map<string, double> pars;
  pars["go_stop"] = 0.001;
  pars["go_dishonest"] = 0.02;
  pars["go_honest"] = 0.05;
  HmmDriver<SeqGenDPTable, NESeqGenDPTable, SeqGenBaumWelch> hmmd(pars);
  hmmd.Sample();
  hmmd.Estimate();
  hmmd.Viterbi();
  hmmd.Report();
}
