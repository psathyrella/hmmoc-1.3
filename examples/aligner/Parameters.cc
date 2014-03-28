#include "Parameters.h"

//----------------------------------------------------------------------------------------
Parameters::Parameters(map<string,map<string,string> > *transition_config,
		       map<string,map<string,string> > *emission_config,
		       map<string,map<string,double> > *transition_probs,
		       map<string,map<string,double> > *emission_probs) :
  is_updated_(false),
  transition_config_(transition_config),
  emission_config_(emission_config),
  transition_probs_(transition_probs),
  emission_probs_(emission_probs) {
  SetProbabilities();
}
//----------------------------------------------------------------------------------------
void Parameters::SetProbabilities() {
  SetProbabilities(transition_config_, transition_probs_);
  SetProbabilities(emission_config_, emission_probs_);
}
//----------------------------------------------------------------------------------------
// set the transition and emission probabilities based on the values in vals_
void Parameters::SetProbabilities(map<string,map<string,string> > *configs,
				  map<string,map<string,double> > *probs) {
  assert(configs);
  assert(probs);
  // NOTE: for emissions it's not really 'from state' and 'to state'...
  for (auto &from_pair : *configs) {
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
	  ptr_to_auto = &((*probs)[from_state][to_state]);
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
      (*probs)[from_state][to_state] = prob;
    }
    // set the auto one to 1 - {the others}
    assert(total_prob <= 1.0);
    if (ptr_to_auto) {
      *ptr_to_auto = 1.0 - total_prob;
    }
    for (auto &to_pair : from_pair.second) {
      string to_state(to_pair.first);
      cout
    	<< setw(12) << from_state
    	<< setw(12) << to_state
    	<< setw(12) << (*probs)[from_state][to_state]
    	<< endl;
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
  SetProbabilities();
}
