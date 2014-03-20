#include "Parameters.h"

//----------------------------------------------------------------------------------------
Parameters::Parameters(map<string,map<string,string> > *transition_config,
		       map<string,map<string,double> > *transition_probs) :
  is_updated_(false),
  transition_config_(transition_config),
  transition_probs_(transition_probs) {
  SetTransitionProbs();
}
//----------------------------------------------------------------------------------------
void Parameters::SetTransitionProbs() {
  assert(transition_probs_);
  assert(transition_config_);
  for (auto &from_pair : *transition_config_) {
    string from_state(from_pair.first);
    double total_prob(0); // 1 - $auto
    double *ptr_to_auto(0); // pointer to the value that we'll set as 1 - {the others}
    for (auto &to_pair : from_pair.second) {
      string to_state(to_pair.first);
      string config(to_pair.second);
      double prob(0.0);
      if (config=="x") { // disallowed transition
	prob = 0.0;
      } else if (config[0]=='$') { // a variable, we need to do some processing
	config.erase(0,1); // remove the $
	if (config=="auto") { // just want it to be 1 - {the others}, so have to calculate it last
	  ptr_to_auto = &((*transition_probs_)[from_state][to_state]);
	} else { // the transition prob is just the value of a parameter in <par>
	  assert(vals_.find(config) != vals_.end()); // make sure we were already given the parameter
	 prob = vals_[config];
	}
      } else { // hard-coded number
	stringstream ss(config);
	ss >> prob;
	assert(prob >= 0.0);
	assert(prob <= 1.0);
      }
      total_prob += prob;
      (*transition_probs_)[from_state][to_state] = prob;
    }
    // set the auto one to 1 - {the others}
    if (ptr_to_auto) {
      *ptr_to_auto = 1.0 - total_prob;
    }
  }
  is_updated_ = true;
}
//----------------------------------------------------------------------------------------
void Parameters::add_val(string name, double val) {
  assert(vals_.find(name) == vals_.end());
  vals_[name] = val;
  is_updated_ = false; // If we're adding parameters, it sure isn't updated.
                       // In general we'll need to call SetTransitionProbs by hand after
                       // we've added all of them.
}
//----------------------------------------------------------------------------------------
void Parameters::set_val(string name, double val) {
  assert(vals_.find(name) != vals_.end());
  vals_[name] = val;
  SetTransitionProbs();
}
