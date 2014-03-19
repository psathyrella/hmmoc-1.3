#ifndef EXAMPLES_ALIGNER_HMMDRIVER_H_
#define EXAMPLES_ALIGNER_HMMDRIVER_H_

#include <iomanip>
#include "aligner.h"

//----------------------------------------------------------------------------------------
template <class T_dpt, class T_ne_dpt, class T_bwc>
class HmmDriver {
public:
  HmmDriver(map<string,double> &pars, unsigned n_states, unsigned n_letters);
  void Init();
  void check_probabilities(const vector<double> probs);
  void sample();
  void estimate();
  void viterbi();
  void report();

  unsigned n_states,n_letters;
  string nukes;                              // surely this is defined somewhere else? can't find it
  vector<string> emission_labels;
  vector<string> transition_names;
  map<string,double> pars;
  map<string,vector<double> > emission_probs;
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
HmmDriver<T_dpt, T_ne_dpt, T_bwc>::HmmDriver(map<string,double> &pars, unsigned n_states, unsigned n_letters):
  n_states(n_states),
  n_letters(n_letters),
  nukes("0123"),
  emission_labels{"honest_emission","dishonest_emission"},
  transition_names{"tr_b_h","tr_b_d","tr_h_h","tr_h_d","tr_d_d","tr_d_h","tr_h_s","tr_d_s"},
  pars(pars),
  ne_dptable(0),
  bw_counters(0),
  fw_dptable(0),
  bw_dptable(0),
  vt_dptable(0),
  true_path(0),
  path_out(0),
  iterations(1)
{
  Init();
}
//----------------------------------------------------------------------------------------
template <class T_dpt, class T_ne_dpt, class T_bwc>
void HmmDriver<T_dpt, T_ne_dpt, T_bwc>::Init() {
  emission_probs["honest"] = {0.1,0.1,0.1,0.7};
  emission_probs["dishonest"] = {0.3,0.3,0.3,0.1};
  check_probabilities(emission_probs["honest"]);
  check_probabilities(emission_probs["dishonest"]);
}
//----------------------------------------------------------------------------------------
// make sure probabilities sum to 1
template <class T_dpt, class T_ne_dpt, class T_bwc>
void HmmDriver<T_dpt, T_ne_dpt, T_bwc>::check_probabilities(const vector<double> probs) {
  double eps(1e-10); // Arbitrary! Not sure what the effect of non-unit sum is, actually.
  double total(0.0);
  for (unsigned ip=0; ip<probs.size(); ip++)
    total += probs[ip];
  if (fabs(total-1.0) > eps) {
    printf("ERROR: %20.20f\n",total);
    assert(0);
  }
}
//----------------------------------------------------------------------------------------
template <class T_dpt, class T_ne_dpt, class T_bwc>
void HmmDriver<T_dpt, T_ne_dpt, T_bwc>::sample()  {
  // sample a path from the no-emission version
  cout << "sampling " << endl;
  do {
    NEBackward(&ne_dptable, pars);           // Initialize the DP table using the Backward algorithm
    true_path = &NESample(ne_dptable, pars); // Sample
  } while ((true_path->size() - 1) < 1/pars["go_stop"]);            // We don't want short sequences
  // get a record of the state_id : state_str mapping (it's non-deterministic)
  for (unsigned is=0; is<n_states; is++) ne_state_ids[is] = ne_dptable->getStateId(is);
  // throw an emission sequence from the sampled path
  for (unsigned is=0; is<true_path->size(); is++) {
    assert(ne_state_ids.find(true_path->toState(is)) != ne_state_ids.end());
    string state_str = ne_state_ids[true_path->toState(is)].substr(2);
    if (state_str == "stop") break;
    assert(emission_probs.find(state_str) != emission_probs.end());
    double prob = random() / (double)RAND_MAX;
    unsigned ip(0);
    while (true) {
      if (prob < emission_probs[state_str][ip]) {
	sampled_seq.push_back(nukes[ip]);
	break;
      }
      prob -= emission_probs[state_str][ip];
      ip++;
      assert(prob>0);
    }
  }
}
//----------------------------------------------------------------------------------------
// use Baum-Welch training to estimate parameters
template <class T_dpt, class T_ne_dpt, class T_bwc>
void HmmDriver<T_dpt, T_ne_dpt, T_bwc>::estimate() {
  bw_counters = new T_bwc;
  cout << "parameter estimation:" << endl;
  cout << setw(12) << "iteration" << setw(12) << "likelihood"
       << setw(12) << "go_dishonest"
       << setw(12) << "go_honest"
       << endl;
  // run <iterations> baum welch steps
  for (unsigned iter=1; iter<=iterations; ++iter) {
    // calculate the forward DP table
    bfloat fw = Forward(&fw_dptable, pars, emission_probs, sampled_seq);
    bw_counters->resetCounts();
    // and the backward one
    Backward(*bw_counters, fw_dptable, &bw_dptable, pars, emission_probs, sampled_seq);
    cout
      << setw(12) << iter
      << setw(12) << fw
      << setw(12) << pars["go_dishonest"]
      << setw(12) << pars["go_honest"]
      << endl;

    // print the emission counts
    cout << "    ";
    for (auto &label : emission_labels) cout << setw(12) << label.substr(0, label.find("_"));
    cout << endl;
    for (unsigned ie=0; ie<n_letters; ++ie) {
      cout << setw(4) << nukes[ie];
      for (auto &label : emission_labels) {
	cout << setw(12) << bw_counters->emissionBaumWelchCount1[ie][bw_counters->emissionIndex(label)];
      }
      cout << endl;
    }
    // print the transition counts
    map<string,double> transition_counts;
    for (string &tname : transition_names) {
      int tindex = bw_counters->transitionIndex(tname);
      transition_counts[tname] = bw_counters->transitionBaumWelchCount0[tindex];
      cout << setw(12) << tname  << setw(12) << tindex << setw(12) << transition_counts[tname] << endl;
    }
    // set_go_dishonest(transition_counts);

    delete fw_dptable;
    fw_dptable = 0;
  }
  delete bw_counters;
  bw_counters = 0;
}
//----------------------------------------------------------------------------------------
// Compute a Viterbi alignment
template <class T_dpt, class T_ne_dpt, class T_bwc>
void HmmDriver<T_dpt, T_ne_dpt, T_bwc>::viterbi() {
  cout << "viterbi recursion and traceback" << endl;
  Viterbi_recurse(&vt_dptable, pars, emission_probs, sampled_seq);
  path_out = &Viterbi_trace(vt_dptable, pars, emission_probs, sampled_seq);
  // get a record of the state_id : state_str mapping (it's non-deterministic)
  for (unsigned is=0; is<n_states; is++)
    state_ids[is] = vt_dptable->getStateId(is);
  delete vt_dptable;
  vt_dptable = 0;
}
//----------------------------------------------------------------------------------------
// Print the Viterbi alignment, and a summary of the posteriors
template <class T_dpt, class T_ne_dpt, class T_bwc>
void HmmDriver<T_dpt, T_ne_dpt, T_bwc>::report()  {
  string seqStr(""),pathStr("");
  // if they aren't the same size, we want to print to the end of the longer one
  size_t imax = max((size_t)path_out->size(), sampled_seq.size());
  for (size_t is=0; is<imax; is++) {
    if (is < path_out->size()) {
      assert(state_ids.find(path_out->toState(is)) != state_ids.end());
      pathStr += state_ids[path_out->toState(is)][0];// - 32; // capitalize for readibility
    }
    if (is < sampled_seq.size()) {
      seqStr += sampled_seq[is];
    }
  }
  cout << "emitted sequence " << seqStr << endl;
  cout << "vtb path         " << pathStr << endl;

  string true_path_str("");
  for (unsigned is=0; is<true_path->size(); is++) {
    assert(ne_state_ids.find(true_path->toState(is)) != ne_state_ids.end());
    true_path_str += ne_state_ids[true_path->toState(is)][2];// - 32; // capitalize
  }
  cout << "true path        " << true_path_str << endl;
}
#endif // EXAMPLES_ALIGNER_HMMDRIVER_H_
