#ifndef EXAMPLES_ALIGNER_HMMDRIVER_H_
#define EXAMPLES_ALIGNER_HMMDRIVER_H_

#include <iomanip>
#include <set>

#include "aligner.h"
#include "parameters.h"

//----------------------------------------------------------------------------------------
template <class T_dpt, class T_ne_dpt, class T_bwc>
class HmmDriver {
public:
  HmmDriver(map<string,double> &pars);
  void Init();
  void CheckProbabilities(const vector<double> probs);
  void Sample();
  void Estimate();
  void Viterbi();
  void Report();

  map<string,double> pars_;
  unsigned n_states_;
  string alphabet_;                           // alphabet for the output sequence
  set<string> silent_states_,states_;
  vector<string> transition_names_;
  map<string,vector<double> > emission_probs_;
  unsigned iterations_;
  T_ne_dpt *ne_dptable_;                      // DP table for emmissionless HMM
  T_bwc *bw_counters_;                        // Baum Welch counters for regular HMM
  T_dpt *fw_dptable_,*bw_dptable_,*vt_dptable_; // DP tables for regular HMM
  map<unsigned,string> ne_state_ids_;         // state ID translators for emmissionless and regular HMM
  map<unsigned,string> state_ids_;            // (no, they're not necessarily the *#$! same)
  vector<char> sampled_seq_;                  // true (sampled) sequence
  Path *true_path_,*path_out_;                 // true (sampled) path out viterbi path
};
//----------------------------------------------------------------------------------------
template <class T_dpt, class T_ne_dpt, class T_bwc>
HmmDriver<T_dpt, T_ne_dpt, T_bwc>::HmmDriver(map<string,double> &pars):
  pars_(pars),
  iterations_(5),
  ne_dptable_(0),
  bw_counters_(0),
  fw_dptable_(0),
  bw_dptable_(0),
  vt_dptable_(0),
  true_path_(0),
  path_out_(0)
{
  Init();
}
//----------------------------------------------------------------------------------------
template <class T_dpt, class T_ne_dpt, class T_bwc>
void HmmDriver<T_dpt, T_ne_dpt, T_bwc>::Init() {
  ifstream ifs("hmm.txt");
  string line;
  while (getline(ifs,line)) {
    stringstream ss(line);
    if(line[0] == '#') continue; // comments
    if(line[0] == '^') {
      string label;
      ss >> label;
      label.erase(0,1);
      if (label == "alphabet") {
	ss >> alphabet_;
      } else if (label == "silent_states") {
	while (!ss.eof()) {
	  string state;
	  ss >> state;
	  silent_states_.insert(state);
	}
      } else if (label == "states") {
	while (!ss.eof()) {
	  string state;
	  ss >> state;
	  states_.insert(state);
	}
      } else if (label == "transitions") {
	while (!ss.eof()) {
	  string trans;
	  ss >> trans;
	  transition_names_.push_back("tr_"+trans);
	}
      } else {
	assert(silent_states_.find(label) != silent_states_.end() ||
	       states_.find(label) != states_.end());
	emission_probs_[label] = vector<double>();
	while (!ss.eof()) {
	  double prob;
	  ss >> prob;
	  emission_probs_[label].push_back(prob);
	}
      }
    }
  }
  n_states_ = silent_states_.size() + states_.size();
  for (auto &state : states_) { // make sure we have emission probs for each non-silent state
    assert(emission_probs_.find(state) != emission_probs_.end());
    CheckProbabilities(emission_probs_[state]);
  }
}
//----------------------------------------------------------------------------------------
// make sure probabilities sum to 1
template <class T_dpt, class T_ne_dpt, class T_bwc>
void HmmDriver<T_dpt, T_ne_dpt, T_bwc>::CheckProbabilities(const vector<double> probs) {
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
void HmmDriver<T_dpt, T_ne_dpt, T_bwc>::Sample()  {
  // sample a path from the no-emission version
  cout << "sampling " << endl;
  do {
    NEBackward(&ne_dptable_, pars_);           // Initialize the DP table using the Backward algorithm
    true_path_ = &NESample(ne_dptable_, pars_); // Sample
  } while ((true_path_->size() - 1) < 1/pars_["go_stop"]);            // We don't want short sequences
  // get a record of the state_id : state_str mapping (it's non-deterministic)
  for (unsigned is=0; is<n_states_; is++) ne_state_ids_[is] = ne_dptable_->getStateId(is);
  // throw an emission sequence from the sampled path
  for (unsigned is=0; is<true_path_->size(); is++) {
    assert(ne_state_ids_.find(true_path_->toState(is)) != ne_state_ids_.end());
    string state_str = ne_state_ids_[true_path_->toState(is)].substr(2);
    if (state_str == "stop") break;
    assert(emission_probs_.find(state_str) != emission_probs_.end());
    double prob = random() / (double)RAND_MAX;
    unsigned ip(0);
    while (true) {
      if (prob < emission_probs_[state_str][ip]) {
	sampled_seq_.push_back(alphabet_[ip]);
	break;
      }
      prob -= emission_probs_[state_str][ip];
      ip++;
      assert(prob>0);
    }
  }
}
//----------------------------------------------------------------------------------------
// use Baum-Welch training to estimate parameters
template <class T_dpt, class T_ne_dpt, class T_bwc>
void HmmDriver<T_dpt, T_ne_dpt, T_bwc>::Estimate() {
  bw_counters_ = new T_bwc;
  cout << "parameter estimation:" << endl;
  cout << setw(12) << "iteration" << setw(12) << "likelihood";
  for (auto &parameter : pars_) cout << setw(20) << parameter.first;
  cout << endl;
  cout << setw(24) << "start";
  for (auto &parameter : pars_) cout << setw(20) << parameter.second;
  cout << endl;
  // run <iterations_> baum welch steps
  for (unsigned iter=1; iter<=iterations_; ++iter) {
    // calculate the forward DP table
    bfloat fw = Forward(&fw_dptable_, pars_, emission_probs_, sampled_seq_);
    bw_counters_->resetCounts();
    // and the backward one
    Backward(*bw_counters_, fw_dptable_, &bw_dptable_, pars_, emission_probs_, sampled_seq_);

    // print the emission counts
    // cout << "    ";
    // for (auto &state : states_) cout << setw(12) << state;
    // cout << endl;
    for (unsigned ie=0; ie<alphabet_.size(); ++ie) {
      // cout << setw(4) << alphabet_[ie];
      // for (auto &state : states_) {
      // 	cout << setw(12) << bw_counters_->emissionBaumWelchCount1[ie][bw_counters_->emissionIndex(state+"_emission")];
      // }
      // cout << endl;
    }
    // print the transition counts
    map<string,double> transition_counts;
    for (string &tname : transition_names_) {
      int tindex = bw_counters_->transitionIndex(tname);
      transition_counts[tname] = bw_counters_->transitionBaumWelchCount0[tindex];
      // cout << setw(12) << tname  << setw(12) << tindex << setw(12) << transition_counts[tname] << endl;
    }
    set_go_dishonest(transition_counts, &pars_);
    cout << setw(12) << iter << setw(12) << fw;
    for (auto &parameter : pars_) cout << setw(20) << parameter.second;
    cout << endl;

    delete fw_dptable_;
    fw_dptable_ = 0;
  }
  delete bw_counters_;
  bw_counters_ = 0;
}
//----------------------------------------------------------------------------------------
// Compute a Viterbi alignment
template <class T_dpt, class T_ne_dpt, class T_bwc>
void HmmDriver<T_dpt, T_ne_dpt, T_bwc>::Viterbi() {
  cout << "viterbi recursion and traceback" << endl;
  Viterbi_recurse(&vt_dptable_, pars_, emission_probs_, sampled_seq_);
  path_out_ = &Viterbi_trace(vt_dptable_, pars_, emission_probs_, sampled_seq_);
  // get a record of the state_id : state_str mapping (it's non-deterministic)
  for (unsigned is=0; is<n_states_; is++)
    state_ids_[is] = vt_dptable_->getStateId(is);
  delete vt_dptable_;
  vt_dptable_ = 0;
}
//----------------------------------------------------------------------------------------
// Print the Viterbi alignment, and a summary of the posteriors
template <class T_dpt, class T_ne_dpt, class T_bwc>
void HmmDriver<T_dpt, T_ne_dpt, T_bwc>::Report()  {
  string seqStr(""),pathStr("");
  // if they aren't the same size, we want to print to the end of the longer one
  size_t imax = max((size_t)path_out_->size(), sampled_seq_.size());
  for (size_t is=0; is<imax; is++) {
    if (is < path_out_->size()) {
      assert(state_ids_.find(path_out_->toState(is)) != state_ids_.end());
      pathStr += state_ids_[path_out_->toState(is)][0];// - 32; // capitalize for readibility
    }
    if (is < sampled_seq_.size()) {
      seqStr += sampled_seq_[is];
    }
  }
  // cout << "emitted sequence " << seqStr << endl;
  // cout << "vtb path         " << pathStr << endl;

  string true_path_str("");
  for (unsigned is=0; is<true_path_->size(); is++) {
    assert(ne_state_ids_.find(true_path_->toState(is)) != ne_state_ids_.end());
    true_path_str += ne_state_ids_[true_path_->toState(is)][2];// - 32; // capitalize
  }
  // cout << "true path        " << true_path_str << endl;
}
#endif // EXAMPLES_ALIGNER_HMMDRIVER_H_
