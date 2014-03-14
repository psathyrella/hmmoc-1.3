#include <iostream>
#include <iomanip>
#include <fstream>

#include "aligner.h"
#include <sstream>
#include <cassert>

//----------------------------------------------------------------------------------------
class Pars {
public:
  double tau; // length parameter
};

//----------------------------------------------------------------------------------------
// Accessor classes
class SeqGenAccess {
public:
  typedef SeqGenDPTable    DPTable;
  static bfloat Viterbi_recurse(DPTable** ppOutTable, Pars pars, vector<char>& iSequence) {
    return ::Viterbi_recurse(ppOutTable, pars.tau, iSequence);
  }
  static Path& Viterbi_trace(DPTable* pInTable, Pars pars, vector<char>& iSequence) {
    return ::Viterbi_trace(pInTable, pars.tau, iSequence);
  }
};

//----------------------------------------------------------------------------------------
// read input file: first column should be sequence IDs, second column the sequences. Gaps are removed and sequences are uppercased
void readFile(istream& is, vector<pair<string,vector<char> > > &seqs) {
  string line;
  while(getline(is,line)) {
    stringstream ss(line);
    if(line[0]=='#') continue; // skip comments
    string id,seq;
    ss >> id >> seq; // not using the later columns a.t.m.
    vector<char> vseq;
    unsigned iT(0); // number of initial Ts encountered (for removal)
    for(unsigned ich=0; ich<seq.size(); ich++) {
      char ch(seq[ich]);
      if(ch>='a' && ch<='z')
	seq[ich] &= 0xDF;  // make upper case
      if(iT<9) {
	assert(ch=='T');
	iT++;
	continue;
      } else if(ich>118) { // stop thingy at end I think we also want to ignore
	continue;
      } else if(ch=='A' || ch=='C' || ch=='G' || ch=='T') { // skip gaps
	vseq.push_back(ch);
      } else {
	cerr << "WARNING: encountered " << ch << " in sequence" << endl;
	continue;
      }
    }
    pair<string,vector<char> > newSequence(pair<string,vector<char> >(id,vseq));
    seqs.push_back(newSequence);
  }
}
    
//----------------------------------------------------------------------------------------
// Compute a Viterbi alignment
template<class T>
Path& viterbi(Pars &pars, vector<char> iSeq, map<unsigned,string> &stateIDs) {
  typename T::DPTable* pViterbiTable;
  cout << "Viterbi recursion..." << endl;
  T::Viterbi_recurse(&pViterbiTable, pars, iSeq);
  cout << "Viterbi traceback..." << endl;
  Path& path = T::Viterbi_trace(pViterbiTable, pars, iSeq);
  stateIDs[0] = pViterbiTable->getStateId(0);
  stateIDs[1] = pViterbiTable->getStateId(1);
  stateIDs[2] = pViterbiTable->getStateId(2);
  stateIDs[3] = pViterbiTable->getStateId(3);
  delete pViterbiTable;
  return path;
}

//----------------------------------------------------------------------------------------
// Print the Viterbi alignment, and a summary of the posteriors
template<class T>
void report( Path& path, vector<char>& iSeq, map<unsigned,string> stateIDs) {
  string pathStr(""),stateStr("");
  cout << "path: " << path.size() << " seq: " << iSeq.size() << endl;
  for (unsigned i=0; i < max((unsigned)path.size(), (unsigned)iSeq.size()); i++) {
    if(i<iSeq.size()) {
      pathStr += iSeq[i];
    }
    if(i<path.size()) {
      assert(stateIDs.find(path.toState(i)) != stateIDs.end());
      stateStr += *(stateIDs[path.toState(i)].end()-1);
    }
  }
  cout << "path  " << pathStr << endl;
  cout << "state " << stateStr << endl;
}

template<class T>
void execute(Pars pars, vector<char>& iSeq) {
  map<unsigned,string> stateIDs;
  Path& path = viterbi<T>(pars, iSeq, stateIDs);
  report<T>(path, iSeq, stateIDs);
}

int main(int argc, char** argv) {
  Pars pars;
  pars.tau = 0.01;

  char* file = (char*)"sequence.fa";
  if (argc == 1) {
    cout << "Using default: file=sequence.fa:\n" << endl;
  } else {
    file = argv[1];
  }
  if (argc > 2) {
    cout << "Too many parameters" << endl;
    exit(1);
  }

  std::ifstream ifsIn( file );
  vector<pair<string,vector<char> > > seqs;
  readFile(ifsIn, seqs);
  for(unsigned i1=0; i1<seqs.size(); i1++) {
    vector<char>& iSeq = seqs[i1].second;
    execute<SeqGenAccess>(pars, iSeq);
    return 1;
  }
}
