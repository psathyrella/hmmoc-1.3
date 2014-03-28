#include <iostream>
#include <iomanip>
#include <fstream>

#include "aligner.h"
#include <sstream>
#include <cassert>

#include "HmmDriver.h"

//----------------------------------------------------------------------------------------
// Sets the named parameter given the elements of the transition matrix.
// To be called at the end of each Baum Welch iteration to update the parameter
// values.
// set the values in <probs> using the info in <config> and the parameter values in <pars>
void UpdateParameters(map<string, map<string,double> > &ecounts,
		      map<string, map<string,double> > &tcounts,
		      Parameters *pars) {
  // TODO: need to assert that we have all of these
  // transitions
  pars->set_val("go_honest", tcounts["dishonest"]["honest"] / (tcounts["dishonest"]["honest"] + tcounts["dishonest"]["dishonest"] + tcounts["dishonest"]["stop"]));
  pars->set_val("go_dishonest", tcounts["honest"]["dishonest"] / (tcounts["honest"]["dishonest"] + tcounts["honest"]["honest"]       + tcounts["honest"]["stop"]));
  // emissions
  // pars->set_val("honest_0", ecounts["honest"]["0"] / (ecounts["honest"]["0"] + ecounts["honest"]["1"] + ecounts["honest"]["2"] + ecounts["honest"]["3"]));
}
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
  HmmDriver<SeqGenDPTable, NESeqGenDPTable, SeqGenBaumWelch> hmmd(&UpdateParameters);
  hmmd.WriteXml();
  hmmd.Sample();
  hmmd.Estimate();
  hmmd.Viterbi();
  hmmd.Report();
}
