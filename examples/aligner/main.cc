#include <iostream>
#include <iomanip>
#include <fstream>

#include "aligner.h"
#include <sstream>
#include <cassert>

//----------------------------------------------------------------------------------------
class Pars {
public:
  double iEta;
  double iSigma;
};

//----------------------------------------------------------------------------------------
// Accessor classes
class NoBanding {
public:
  typedef AlignDPTable    DPTable;
  typedef AlignBaumWelch  BaumWelchCounters;
  static bfloat Forward(DPTable** ppOutTable,Pars pars,vector<char>& iSequence1,vector<char>& iSequence2) {
    return ::Forward(ppOutTable,pars.iEta,pars.iSigma,iSequence1,iSequence2);
  }
  static bfloat Backward(DPTable** ppOutTable,Pars pars,vector<char>& iSequence1,vector<char>& iSequence2) {
    return ::Backward(ppOutTable,pars.iEta,pars.iSigma,iSequence1,iSequence2);
  }
  static bfloat BackwardBaumWelch(BaumWelchCounters& bw, DPTable* pInTable,Pars pars,vector<char>& iSequence1,vector<char>& iSequence2) {
    return ::BackwardBaumWelch(bw, pInTable,pars.iEta,pars.iSigma,iSequence1,iSequence2);
  }
  static bfloat Viterbi_recurse(DPTable** ppOutTable,Pars pars,vector<char>& iSequence1,vector<char>& iSequence2) {
    return ::Viterbi_recurse(ppOutTable,pars.iEta,pars.iSigma,iSequence1,iSequence2);
  }
  static Path& Viterbi_trace(DPTable* pInTable,Pars pars,vector<char>& iSequence1,vector<char>& iSequence2) {
    return ::Viterbi_trace(pInTable,pars.iEta,pars.iSigma,iSequence1,iSequence2);
  }
  static Path& Sample(DPTable* pInTable,Pars pars,vector<char>& iSequence1,vector<char>& iSequence2) {
    return ::Sample(pInTable,pars.iEta,pars.iSigma,iSequence1,iSequence2);
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
// use Baum-Welch training to estimate transition (i.e., indel) parameters
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
    cout << "estimated params: "
	 << setw(12) << params.iSigma
	 << setw(12) << params.iEta
	 << endl;

    // remove the forward table
    delete pFW;

    // get the expected transition counts
    double trBS = baumWelch.transitionBaumWelchCount00[ baumWelch.transitionIndex("trBS") ];

    // calculate the new parameters
    cout
      << setw(22) << "BX"
      << setw(22) << "BS"
      << setw(22) << "XX"
      << setw(22) << "XS"
      << setw(22) << "SY"
      << setw(22) << "SE"
      << setw(22) << "YY"
      << setw(22) << "YE" << endl
      << setw(22) << baumWelch.transitionBaumWelchCount00[ baumWelch.transitionIndex("trBX") ]
      << setw(22) << baumWelch.transitionBaumWelchCount00[ baumWelch.transitionIndex("trBS") ]
      << setw(22) << baumWelch.transitionBaumWelchCount00[ baumWelch.transitionIndex("trXX") ]
      << setw(22) << baumWelch.transitionBaumWelchCount00[ baumWelch.transitionIndex("trXS") ]
      << setw(22) << baumWelch.transitionBaumWelchCount00[ baumWelch.transitionIndex("trSY") ]
      << setw(22) << baumWelch.transitionBaumWelchCount00[ baumWelch.transitionIndex("trSE") ]
      << setw(22) << baumWelch.transitionBaumWelchCount00[ baumWelch.transitionIndex("trYY") ]
      << setw(22) << baumWelch.transitionBaumWelchCount00[ baumWelch.transitionIndex("trYE") ] << endl;
    params.iEta = trBS;
  }
}
    
//----------------------------------------------------------------------------------------
// Compute a Viterbi alignment
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

//----------------------------------------------------------------------------------------
// This computes the posterior probability of being in a particular state
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

//----------------------------------------------------------------------------------------
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

//----------------------------------------------------------------------------------------
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
  double pTot(0); // total probability
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
    pTot += posterior;
    char postId = "0123456789"[ min( int(posterior*10.0), 9) ];
    line3 += postId;
  }
  cout << line1 << endl;
  cout << line2 << endl;
  cout << line3 << endl;
  // ofstream ofs("out.txt",ios_base::app);
  // ofs << setw(12) << pTot;
  // ofs.close();
}

template<class T>
void execute( int iterations, Pars pars, vector<char>& iSeq1, vector<char>& iSeq2 ) {
    // estimate iDelta and iR using Baum-Welch training
    // estimate<T>( iterations, pars, iSeq1, iSeq2 );

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

    delete pFW;
    delete pBW;
}

int main(int argc, char** argv) {
  // set initial parameters
  Pars pars;
  pars.iSigma = 0.4;
  pars.iEta = 0.1;

  char* file = (char*)"sequence.fa";
  if (argc == 1) {
    cout << "Usage: " << argv[0] << " file.fa \n" << endl;
    cout << "'file.fa':   two nucleotide sequences on lines 2 and 3; gaps characters are ignored.\n";
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
    vector<char>& iSeq1 = seqs[i1].second;
    for(unsigned i2=i1+1; i2<seqs.size(); i2++) {
      vector<char>& iSeq2 = seqs[i2].second;
      cout << "Read sequences.  Lengths: " << iSeq1.size() << " and " << iSeq2.size() << ".  ";
      execute<NoBanding>( 5, pars, iSeq1, iSeq2 );
      assert (0);
    }
    // ofstream ofs("out.txt",ios_base::app);
    // ofs << endl;
    // ofs.close();
  }
}
