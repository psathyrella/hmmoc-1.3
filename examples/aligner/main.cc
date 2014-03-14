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
  typedef SeqGenBaumWelch  BaumWelchCounters;
  static bfloat Forward(DPTable** ppOutTable, Pars pars, vector<char>& iSequence) {
    return ::Forward(ppOutTable, pars.tau, iSequence);
  }
  static bfloat Backward(BaumWelchCounters& bw, DPTable* pInTable, Pars pars, vector<char>& iSequence) {
    return ::Backward(bw, pInTable, pars.tau, iSequence);
  }
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
// sample from emissionless hmm
Path *sample(Pars pars, NESeqGenDPTable *NEDPTable) {
  int iPathLength(0);
  Path *pTruePath;
  do {
    NEBackward(&NEDPTable, pars.tau);                     // Initialize the DP table using the Backward algorithm
    pTruePath = &NESample(NEDPTable, pars.tau);            // Sample
    iPathLength = pTruePath->size()-1;
  } while (iPathLength < 1/pars.tau);         // We don't want short sequences
  cout << "Sampled " << iPathLength << " throws" << endl;
  return pTruePath;
}
//----------------------------------------------------------------------------------------
// use Baum-Welch training to estimate parameters
template<class T>
void estimate(int iterations, Pars& pars, vector<char>& iSeq) {
  typename T::DPTable* pFW;
  typename T::BaumWelchCounters baumWelch;
  for (int iter=1; iter <= iterations; ++iter) {
    // calculate the forward DP table
    cout << "Forward..." << endl;
    bfloat fw = T::Forward(&pFW, pars, iSeq);

    // calculate the Baum-Welch estimated transition counts
    baumWelch.resetCounts();
    cout << "Baum-Welch + backward..." << endl;
    bfloat bw = T::Backward(baumWelch, pFW, pars, iSeq);

    cout << "Iteration " << iter << ": likelihood = " << fw << endl;
    cout << "estimated pars: "
	 << setw(12) << pars.tau
	 << endl;

    // remove the forward table
    delete pFW;

    // get the expected transition counts
    double trG1S = baumWelch.transitionBaumWelchCount0[ baumWelch.transitionIndex("trG1S") ];
    double trG2S = baumWelch.transitionBaumWelchCount0[ baumWelch.transitionIndex("trG2S") ];

    // calculate the new parameters
    pars.tau = (trG1S + trG2S) / 2;
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
//----------------------------------------------------------------------------------------
template<class T>
void execute(Pars pars, vector<char>& iSeq) {
  cout << "ex" << endl;
  int iterations(9);
  estimate<T>(iterations, pars, iSeq);
  map<unsigned,string> stateIDs;
  Path& path = viterbi<T>(pars, iSeq, stateIDs);
  report<T>(path, iSeq, stateIDs);
}
//----------------------------------------------------------------------------------------
int main(int argc, char** argv) {
  srand(getpid());
  Pars pars;
  pars.tau = 0.1;
  //----------------------------------------------------------------------------------------
  // hrrrrrg why doesn't hmmoc put these in the *header*?
  double emitProbs1[4];
  emitProbs1[0] = 0.1;
  emitProbs1[1] = 0.1;
  emitProbs1[2] = 0.4;
  emitProbs1[3] = 0.4;
  double emitProbs2[4];
  emitProbs2[0] = 0.1;
  emitProbs2[1] = 0.4;
  emitProbs2[2] = 0.1;
  emitProbs2[3] = 0.4;


  NESeqGenDPTable *NEDPTable(0);
  Path *truePath = sample(pars, NEDPTable);
  map<unsigned,string> NEstateIDs;
  NEstateIDs[0] = NEDPTable->getStateId(0);
  NEstateIDs[1] = NEDPTable->getStateId(1);
  NEstateIDs[2] = NEDPTable->getStateId(2);
  NEstateIDs[3] = NEDPTable->getStateId(3);
  const char *nukes = "ACGT"; // surely this is defined somewhere else? can't find it
  vector<char> sampleSeq;
  for(unsigned is=0; is<truePath->size(); is++) {
    assert(NEstateIDs.find(truePath->toState(is)) != NEstateIDs.end());
    string stateStr = NEstateIDs[truePath->toState(is)];
    double *emitProbs(0);
    if(stateStr=="NEgenerate1")      emitProbs = emitProbs1;
    else if(stateStr=="NEgenerate2") emitProbs = emitProbs2;
    else if(stateStr=="NEbegin" || stateStr=="NEstop") {
      break;
    } else {
      cout << "ACK: " << stateStr << endl;
    }
      
    double prob = random() / (double)RAND_MAX;
    unsigned ip(0);
    while(true) {
      // cout
      // 	<< setw(12) << prob;
      if(prob<emitProbs[ip]) {
	// cout
	//   << " --> "
	//   << setw(12) << nukes[ip];
	sampleSeq.push_back(nukes[ip]);
	break;
      }
      // cout << "(-=" << emitProbs[ip] << ")";
      prob -= emitProbs[ip];
      ip++;
      assert(prob>0);
    }
    // cout << endl;
  }
  execute<SeqGenAccess>(pars, sampleSeq);
  string stateStr("");
  for(unsigned is=0; is<truePath->size(); is++) {
    assert(NEstateIDs.find(truePath->toState(is)) != NEstateIDs.end());
    stateStr += *(NEstateIDs[truePath->toState(is)].end()-1);
  }
  cout << "state " << stateStr << endl;
}
