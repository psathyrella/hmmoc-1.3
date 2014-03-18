#include <iostream>
#include <iomanip>
#include <fstream>

#include "aligner.h"
#include <sstream>
#include <cassert>

//----------------------------------------------------------------------------------------
class Pars 
{
public:
  double tau; // length parameter
  double gamma1; // rate G1 to G2
  double gamma2; // rate G2 to G1
};
//----------------------------------------------------------------------------------------
template <class T_dpt, class T_ne_dpt, class T_bwc>
class HMMDriver
{
public:
  HMMDriver(Pars &pars, unsigned n_states, unsigned n_letters);
  void sample();
  void estimate();
  void viterbi();
  void report();

  unsigned n_states,n_letters;
  Pars pars;
  vector<double> emit_probs_1,emit_probs_2;
  T_ne_dpt *ne_dptable;                      // DP table for emmissionless HMM
  T_bwc *bw_counters;                        // Baum Welch counters for regular HMM
  T_dpt *fw_dptable,*bw_dptable,*vt_dptable; // DP tables for regular HMM
  map<unsigned,string> ne_state_ids;         // state ID translators for emmissionless and regular HMM
  map<unsigned,string> state_ids;            // (no, they're not necessarily the *#$! same)
  vector<char> sampled_seq;                  // true (sampled) sequence
  Path *true_path,*path_out;                 // true (sampled) path out viterbi path
  unsigned iterations;
};
//----------------------------------------------------------------------------------------
template <class T_dpt, class T_ne_dpt, class T_bwc>
HMMDriver<T_dpt, T_ne_dpt, T_bwc>::HMMDriver(Pars &pars, unsigned n_states, unsigned n_letters):
  n_states(n_states),
  n_letters(n_letters),
  pars(pars),
  emit_probs_1({0.1,0.1,0.4,0.4}),
  emit_probs_2({0.1,0.4,0.1,0.4}),
  ne_dptable(0),
  bw_counters(0),
  fw_dptable(0),
  bw_dptable(0),
  vt_dptable(0),
  true_path(0),
  path_out(0),
  iterations(2)
{
}
//----------------------------------------------------------------------------------------
template <class T_dpt, class T_ne_dpt, class T_bwc>
void HMMDriver<T_dpt, T_ne_dpt, T_bwc>::sample() 
{
  cout << "sampling " << endl;
  do {
    NEBackward(&ne_dptable, pars.tau, pars.gamma1, pars.gamma2);           // Initialize the DP table using the Backward algorithm
    true_path = &NESample(ne_dptable, pars.tau, pars.gamma1, pars.gamma2); // Sample
  } while ((true_path->size() - 1) < 1/pars.tau);            // We don't want short sequences
  cout << setw(25) << (true_path->size() - 1) << " throws" << endl;
  for (unsigned is=0; is<n_states; is++) ne_state_ids[is] = ne_dptable->getStateId(is);
  const char *nukes = "ACGT"; // surely this is defined somewhere else? can't find it
  for (unsigned is=0; is<true_path->size(); is++) {
    assert(ne_state_ids.find(true_path->toState(is)) != ne_state_ids.end());
    string stateStr = ne_state_ids[true_path->toState(is)];
    vector<double> *emit_probs(0);
    if(stateStr=="NEgenerate1")      emit_probs = &emit_probs_1;
    else if(stateStr=="NEgenerate2") emit_probs = &emit_probs_2;
    else if(stateStr=="NEbegin" || stateStr=="NEstop") {
      break;
    } else {
      cout << "ACK: " << stateStr << endl;
      assert(0);
    }
      
    double prob = random() / (double)RAND_MAX;
    unsigned ip(0);
    while(true) {
      if(prob < (*emit_probs)[ip]) {
	sampled_seq.push_back(nukes[ip]);
	break;
      }
      prob -= (*emit_probs)[ip];
      ip++;
      assert(prob>0);
    }
  }
}
//----------------------------------------------------------------------------------------
// use Baum-Welch training to estimate parameters
template <class T_dpt, class T_ne_dpt, class T_bwc>
void HMMDriver<T_dpt, T_ne_dpt, T_bwc>::estimate()
{
  bw_counters = new T_bwc;
  cout << "parameter estimation:" << endl;
  cout << setw(12) << "iteration" << setw(12) << "likelihood"
       << setw(12) << "gamma1"
       << setw(12) << "gamma2"
       << endl;
  for (unsigned iter=1; iter<=iterations; ++iter) {
    bfloat fw = Forward(&fw_dptable, pars.tau, pars.gamma1, pars.gamma2, sampled_seq, emit_probs_1, emit_probs_2); // calculate the forward DP table
    // calculate the Baum-Welch estimated transition counts
    bw_counters->resetCounts();
    Backward(*bw_counters, fw_dptable, &bw_dptable, pars.tau, pars.gamma1, pars.gamma2, sampled_seq, emit_probs_1, emit_probs_2);
    cout
      << setw(12) << iter
      << setw(12) << fw
      << setw(12) << pars.gamma1
      << setw(12) << pars.gamma2
      << endl;
    delete fw_dptable;
    fw_dptable = 0;
    // get the expected transition counts
    cout			 
      << setw(12) << "emit1"
      << setw(12) << "emit2"
      << endl;
    for (unsigned ie=0; ie<n_letters; ++ie) {
    cout
      << setw(12) << bw_counters->emissionBaumWelchCount1[ie][bw_counters->emissionIndex("emit1" )]
      << setw(12) << bw_counters->emissionBaumWelchCount1[ie][bw_counters->emissionIndex("emit2" )] << endl;
    }

      // << setw(12) << "trBG1"  << setw(12) << bw_counters->transitionIndex("trBG1" ) << setw(12) << bw_counters->transitionBaumWelchCount0[bw_counters->transitionIndex("trBG1" )] << endl
      // << setw(12) << "trBG2"  << setw(12) << bw_counters->transitionIndex("trBG2" ) << setw(12) << bw_counters->transitionBaumWelchCount0[bw_counters->transitionIndex("trBG2" )] << endl
      // << setw(12) << "trG1G1" << setw(12) << bw_counters->transitionIndex("trG1G1") << setw(12) << bw_counters->transitionBaumWelchCount0[bw_counters->transitionIndex("trG1G1")] << endl
      // << setw(12) << "trG1G2" << setw(12) << bw_counters->transitionIndex("trG1G2") << setw(12) << bw_counters->transitionBaumWelchCount0[bw_counters->transitionIndex("trG1G2")] << endl
      // << setw(12) << "trG2G2" << setw(12) << bw_counters->transitionIndex("trG2G2") << setw(12) << bw_counters->transitionBaumWelchCount0[bw_counters->transitionIndex("trG2G2")] << endl
      // << setw(12) << "trG2G1" << setw(12) << bw_counters->transitionIndex("trG2G1") << setw(12) << bw_counters->transitionBaumWelchCount0[bw_counters->transitionIndex("trG2G1")] << endl
      // << setw(12) << "trG1S"  << setw(12) << bw_counters->transitionIndex("trG1S" ) << setw(12) << bw_counters->transitionBaumWelchCount0[bw_counters->transitionIndex("trG1S" )] << endl
      // << setw(12) << "trG2S"  << setw(12) << bw_counters->transitionIndex("trG2S" ) << setw(12) << bw_counters->transitionBaumWelchCount0[bw_counters->transitionIndex("trG2S" )] << endl;

    // calculate the new parameters
    // double trG1G2 = bw_counters->transitionBaumWelchCount0[ bw_counters->transitionIndex("trG1G2") ];
    // double trG2G1 = bw_counters->transitionBaumWelchCount0[ bw_counters->transitionIndex("trG2G1") ];
    // pars.gamma1 = trG1G2 + 0.0001;
    // pars.gamma2 = trG2G1 + 0.0001;
  }
  delete bw_counters;
  bw_counters = 0;
}
//----------------------------------------------------------------------------------------
// Compute a Viterbi alignment
template <class T_dpt, class T_ne_dpt, class T_bwc>
void HMMDriver<T_dpt, T_ne_dpt, T_bwc>::viterbi()
{
  cout << "viterbi recursion and traceback" << endl;
  Viterbi_recurse(&vt_dptable, pars.tau, pars.gamma1, pars.gamma2, sampled_seq, emit_probs_1, emit_probs_2);
  path_out = &Viterbi_trace(vt_dptable, pars.tau, pars.gamma1, pars.gamma2, sampled_seq, emit_probs_1, emit_probs_2);
  for (unsigned is=0; is<n_states; is++) state_ids[is] = vt_dptable->getStateId(is);
  delete vt_dptable;
  vt_dptable = 0;
}
//----------------------------------------------------------------------------------------
// Print the Viterbi alignment, and a summary of the posteriors
template <class T_dpt, class T_ne_dpt, class T_bwc>
void HMMDriver<T_dpt, T_ne_dpt, T_bwc>::report() 
{
  string seqStr(""),pathStr("");
  unsigned imax = max((unsigned)path_out->size(), (unsigned)sampled_seq.size());
  for (unsigned is=0; is<imax; is++) {
    if(is < path_out->size()) {
      assert(state_ids.find(path_out->toState(is)) != state_ids.end());
      pathStr += *(state_ids[path_out->toState(is)].end()-1);
    }
    if(is < sampled_seq.size()) {
      seqStr += sampled_seq[is];
    }
  }
  cout << "emitted sequence " << seqStr << endl;
  cout << "vtb path         " << pathStr << endl;

  string true_path_str("");
  for (unsigned is=0; is<true_path->size(); is++) {
    assert(ne_state_ids.find(true_path->toState(is)) != ne_state_ids.end());
    true_path_str += *(ne_state_ids[true_path->toState(is)].end()-1);
  }
  cout << "true path        " << true_path_str << endl;
}
//----------------------------------------------------------------------------------------
// read input file: first column should be sequence IDs, second column the sequences. Gaps are removed and sequences are uppercased
void readFile(istream& is, vector<pair<string,vector<char> > > &seqs) 
{
  string line;
  while(getline(is,line)) {
    stringstream ss(line);
    if(line[0]=='#') continue; // skip comments
    string id,seq;
    ss >> id >> seq; // not using the later columns a.t.m.
    vector<char> vseq;
    unsigned iT(0); // number of initial Ts encountered (for removal)
    // for (unsigned ich=0; ich<seq.size(); ich++) {
    //   char ch(seq[ich]);
    int ich(0);
    for (char &ch : seq) {
      if(ch>='a' && ch<='z')
	// seq[ich] &= 0xDF;  // make upper case
	ch &= 0xDF;  // make upper case
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
      ++ich;
    }
    pair<string,vector<char> > newSequence(pair<string,vector<char> >(id,vseq));
    seqs.push_back(newSequence);
  }
}
//----------------------------------------------------------------------------------------
int main(int argc, char** argv) 
{
  srand(getpid());
  Pars pars;
  pars.tau = 0.01;
  pars.gamma1 = 0.02;
  pars.gamma2 = 0.05;
  unsigned n_states(4);
  unsigned n_letters(4);
  HMMDriver<SeqGenDPTable, NESeqGenDPTable, SeqGenBaumWelch> hmmd(pars, n_states, n_letters);
  hmmd.sample();
  hmmd.estimate();
  hmmd.viterbi();
  hmmd.report();
}
