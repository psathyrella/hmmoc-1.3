#ifndef EXAMPLES_ALIGNER_HMMDRIVER_H_
#define EXAMPLES_ALIGNER_HMMDRIVER_H_

#include <iomanip>
#include <set>

#include "aligner.h"
#include "Parameters.h"

//----------------------------------------------------------------------------------------
template <class T_dp_table, class T_dp_table_no_emit, class T_bw_counters>
class HmmDriver {
public:
  HmmDriver(void (*update_parameters)(map<string, map<string,double> > &ecounts,
				      map<string, map<string,double> > &tcounts,
				      Parameters *pars));
  void Init();
  void CheckProbabilities(const vector<double> probs);
  void Sample();
  void Estimate();
  void Viterbi();
  void Report();

  map<string, map<string,string> > transition_config_;       // tells us what to do with each transition
  map<string, map<string,double> > transition_probs_;
  map<string,map<string,string> > emission_config_;
  map<string,map<string,double> > emission_probs_;
  Parameters pars_;
  unsigned n_states_;
  vector<string> alphabet_;                            // alphabet for the output sequence
  set<string> silent_states_,states_;
  set<string> transitions_;
  unsigned iterations_;
  T_dp_table_no_emit *ne_dptable_;             // DP table for emmissionless HMM
  T_bw_counters *bw_counters_;                 // Baum Welch counters for regular HMM
  T_dp_table *fw_dptable_,*bw_dptable_,*vt_dptable_; // DP tables for regular HMM
  map<unsigned,string> ne_state_ids_;          // state ID translators for emmissionless and regular HMM
  map<unsigned,string> state_ids_;             // (no, they're not necessarily the *#$! same)
  vector<char> sampled_seq_;                   // true (sampled) sequence
  Path *true_path_,*path_out_;                 // true (sampled) path out viterbi path
private:
  void (*update_parameters_)(map<string, map<string,double> > &ecounts,
			     map<string, map<string,double> > &tcounts,
			     Parameters *pars);
};
//----------------------------------------------------------------------------------------
template <class T_dp_table, class T_dp_table_no_emit, class T_bw_counters>
HmmDriver<T_dp_table, T_dp_table_no_emit, T_bw_counters>
::HmmDriver(void (*update_parameters)(map<string, map<string,double> > &ecounts,
				      map<string, map<string,double> > &tcounts,
				      Parameters *pars)):
  pars_(&transition_config_, &emission_config_, &transition_probs_, &emission_probs_),
  iterations_(5),
  ne_dptable_(0),
  bw_counters_(0),
  fw_dptable_(0),
  bw_dptable_(0),
  vt_dptable_(0),
  true_path_(0),
  path_out_(0),
  update_parameters_(update_parameters)
{
  Init();
}
//----------------------------------------------------------------------------------------
template <class T_dp_table, class T_dp_table_no_emit, class T_bw_counters>
void HmmDriver<T_dp_table, T_dp_table_no_emit, T_bw_counters>
::Init() {
  ifstream ifs("hmm.txt");
  string line;
  while (getline(ifs,line)) {
    stringstream ss(line);
    if(line[0] == '#') continue; // comments
    if(line[0] == '^') {
      string config;
      ss >> config;
      config.erase(0,1);
      if (config == "alphabet") {
	string tmp_str;
	ss >> tmp_str;
	for (size_t ich=0; ich<tmp_str.size(); ++ich) alphabet_.push_back(tmp_str.substr(ich,1));
      } else if (config == "silent_states") {
	while (!ss.eof()) {
	  string state;
	  ss >> state;
	  silent_states_.insert(state);
	}
      } else if (config == "states") {
	while (!ss.eof()) {
	  string state;
	  ss >> state;
	  states_.insert(state);
	}
      // } else if (config == "emission") {
      // 	string state;
      // 	ss >> state;
      // 	assert(states_.find(state) != states_.end());
      // 	emission_probs_[state] = map<string,double>();
      // 	for (auto &letter : alphabet_) {
      // 	  double prob;
      // 	  ss >> prob;
      // 	  emission_probs_[state][letter] = prob;
      // 	}
      } else if (config == "emission") {
	string state;
	ss >> state;
	assert(states_.find(state) != states_.end());
	// emission_probs_[state] = map<string,double>();
	for (auto &letter : alphabet_) {
	  string config;
	  ss >> config;
	  emission_config_[state][letter] = config;
	}
      } else if (config == "parameter") {
	string name;
	double value;
	ss >> name >> value;
	pars_.add_val(name,value);
      } else if (config == "transition_config") {
	// first read in the 'to' states along the top row
	getline(ifs,line);
	stringstream ss2(line);
	vector<string> to_states;
      	while (!ss2.eof()) {
	  string to_state;
	  ss2 >> to_state; // should really assert that the state was already read above
	  to_states.push_back(to_state);
	}
	// then read a line for each 'from' state
	while (getline(ifs,line)) { // has to be at the end of the file a.t.m.
	  if(line[0]=='#') continue;
	  stringstream ss3(line);
	  string from_state;
	  ss3 >> from_state;
	  int i_to_state(0);
	  while (!ss3.eof()) {
	    string to_state(to_states[i_to_state++]);
	    string prob_code;
	    ss3 >> prob_code;
	    transition_config_[from_state][to_state] = prob_code;
	    if(prob_code != "x") // add the allowed ones to transitions_
	      transitions_.insert(from_state+"_"+to_state);
	  }
	}
      } else {
	assert(0);
      }
    }
  }
  ifs.close();
  pars_.SetProbabilities();
  n_states_ = silent_states_.size() + states_.size();
  for (auto &state : states_) { // make sure we have emission probs for each non-silent state
    assert(emission_probs_.find(state) != emission_probs_.end());
    // CheckProbabilities(emission_probs_[state]);
  }
}
//----------------------------------------------------------------------------------------
// make sure probabilities sum to 1
template <class T_dp_table, class T_dp_table_no_emit, class T_bw_counters>
void HmmDriver<T_dp_table, T_dp_table_no_emit, T_bw_counters>
::CheckProbabilities(const vector<double> probs) {
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
template <class T_dp_table, class T_dp_table_no_emit, class T_bw_counters>
void HmmDriver<T_dp_table, T_dp_table_no_emit, T_bw_counters>
::Sample()  {
  // sample a path from the no-emission version
  cout << "sampling " << endl;
  size_t target_length(100);
  if (pars_.vals().find("go_stop") != pars_.vals().end())
    target_length = 1.0 / pars_.vals()["go_stop"];
  do {
    NEBackward(&ne_dptable_, transition_probs_);           // Initialize the DP table using the Backward algorithm
    true_path_ = &NESample(ne_dptable_, transition_probs_); // Sample
  } while ((true_path_->size() - 1) < target_length);            // We don't want short sequences
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
      if (prob < emission_probs_[state_str][alphabet_[ip]]) {
	sampled_seq_.push_back(alphabet_[ip][0]);
	break;
      }
      prob -= emission_probs_[state_str][alphabet_[ip]];
      ip++;
      assert(prob>0);
    }
  }
}
//----------------------------------------------------------------------------------------
// use Baum-Welch training to estimate parameters
template <class T_dp_table, class T_dp_table_no_emit, class T_bw_counters>
void HmmDriver<T_dp_table, T_dp_table_no_emit, T_bw_counters>
::Estimate() {
  bw_counters_ = new T_bw_counters;
  cout << "parameter estimation:" << endl;
  cout << setw(12) << "iteration" << setw(12) << "likelihood";
  for (auto &parameter : pars_.vals()) cout << setw(20) << parameter.first;
  cout << endl;
  cout << setw(24) << " ";
  for (auto &parameter : pars_.vals()) cout << setw(20) << parameter.second;
  cout << endl;
  // run <iterations_> baum welch steps
  for (unsigned iter=1; iter<=iterations_; ++iter) {
    // calculate the forward DP table
    bfloat fw = Forward(&fw_dptable_, emission_probs_, transition_probs_, sampled_seq_);
    bw_counters_->resetCounts();
    // and the backward one
    Backward(*bw_counters_, fw_dptable_, &bw_dptable_, emission_probs_, transition_probs_, sampled_seq_);

    // get the emission counts (in a form that's more accessible
    map<string,map<string,double> > emission_counts;
    for (auto &state : states_) {
      for (unsigned ie=0; ie<alphabet_.size(); ++ie) {
	// for (auto &letter : alphabet_) {
	int emission_index(bw_counters_->emissionIndex(state+"_emission"));
	double count = bw_counters_->emissionBaumWelchCount1[ie][emission_index];
	emission_counts[state+"_emission"][alphabet_[ie]] = count;
      }
    }
    // and the transition counts
    map<string, map<string,double> > transition_counts;
    set<string> all_states(silent_states_);
    all_states.insert(states_.begin(), states_.end());
    for (auto &from_state : all_states) {
      for (auto &to_state : all_states) {
	if(transitions_.find(from_state+"_"+to_state) == transitions_.end())
	  continue; // disallowed transition
	int tindex = bw_counters_->transitionIndex(from_state+"_"+to_state);
	transition_counts[from_state][to_state] = bw_counters_->transitionBaumWelchCount0[tindex];
      }
    }
    update_parameters_(emission_counts, transition_counts, &pars_); // sets the transition and emission probs after modifying the parameters
    cout << setw(12) << iter << setw(12) << fw;
    for (auto &parameter : pars_.vals()) cout << setw(20) << parameter.second;
    cout << endl;
    // pars_.SetTransitionProbs();

    delete fw_dptable_;
    fw_dptable_ = 0;
  }
  delete bw_counters_;
  bw_counters_ = 0;
}
//----------------------------------------------------------------------------------------
// Compute a Viterbi alignment
template <class T_dp_table, class T_dp_table_no_emit, class T_bw_counters>
void HmmDriver<T_dp_table, T_dp_table_no_emit, T_bw_counters>
::Viterbi() {
  cout << "viterbi recursion and traceback" << endl;
  Viterbi_recurse(&vt_dptable_, emission_probs_, transition_probs_, sampled_seq_);
  path_out_ = &Viterbi_trace(vt_dptable_, emission_probs_, transition_probs_, sampled_seq_);
  // get a record of the state_id : state_str mapping (it's non-deterministic)
  for (unsigned is=0; is<n_states_; is++)
    state_ids_[is] = vt_dptable_->getStateId(is);
  delete vt_dptable_;
  vt_dptable_ = 0;
}
//----------------------------------------------------------------------------------------
// Print the Viterbi alignment, and a summary of the posteriors
template <class T_dp_table, class T_dp_table_no_emit, class T_bw_counters>
void HmmDriver<T_dp_table, T_dp_table_no_emit, T_bw_counters>
::Report()  {
  string seq_str(""),path_str("");
  // if they aren't the same size, we want to print to the end of the longer one
  size_t imax = max((size_t)path_out_->size(), sampled_seq_.size());
  for (size_t is=0; is<imax; is++) {
    if (is < path_out_->size()) {
      assert(state_ids_.find(path_out_->toState(is)) != state_ids_.end());
      path_str += state_ids_[path_out_->toState(is)][0];// - 32; // capitalize for readibility
    }
    if (is < sampled_seq_.size()) {
      seq_str += sampled_seq_[is];
    }
  }
  // cout << "emitted sequence " << seq_str << endl;
  // cout << "vtb path         " << path_str << endl;

  string true_path_str("");
  for (unsigned is=0; is<true_path_->size(); is++) {
    assert(ne_state_ids_.find(true_path_->toState(is)) != ne_state_ids_.end());
    true_path_str += ne_state_ids_[true_path_->toState(is)][2];// - 32; // capitalize
  }
  // cout << "true path        " << true_path_str << endl;

  // count the fraction which were correct
  int n_correct(0);
  for (size_t is=0; is<path_str.size(); ++is) {
    if(true_path_str.size() <= is) { cout << "not the same length" << endl; assert(0); }
    if (path_str[is] == true_path_str[is])
      ++n_correct;
  }
  cout << "correct: "
       << setw(12) << n_correct << " / " << path_str.size()
       << " = " << (double(n_correct) / path_str.size()) << endl;
  
}
#endif // EXAMPLES_ALIGNER_HMMDRIVER_H_
