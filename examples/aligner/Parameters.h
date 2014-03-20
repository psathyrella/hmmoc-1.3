#ifndef EXAMPLES_ALIGNER_PARAMETERS_H_
#define EXAMPLES_ALIGNER_PARAMETERS_H_

#include <iostream>
#include <iomanip>
#include <sstream>
#include <cassert>
#include <map>
#include <string>

using namespace std;

//----------------------------------------------------------------------------------------
// class to ensure that values in the transition matrix get updated whenever the parameters do
class Parameters {
public:
  Parameters(map<string,map<string,string> > *transition_config,
	     map<string,map<string,string> > *emission_config,
	     map<string,map<string,double> > *transition_probs,
	     map<string,map<string,double> > *emission_probs);
  map<string,double> vals() { assert(is_updated_); return vals_; }
  void add_val(string name, double val);
  void set_val(string name, double val);
  void SetProbabilities();
  void SetProbabilities(map<string,map<string,string> > *configs,
			map<string,map<string,double> > *probs);
private:
  bool is_updated_;
  map<string,double> vals_; // copied -- we own this
  map<string,map<string,string> > *transition_config_;
  map<string,map<string,string> > *emission_config_;
  map<string,map<string,double> > *transition_probs_; // owned by HmmDriver
  map<string,map<string,double> > *emission_probs_; // owned by HmmDriver
};
#endif //  EXAMPLES_ALIGNER_PARAMETERS_H_
