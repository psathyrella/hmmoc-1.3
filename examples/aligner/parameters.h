#ifndef EXAMPLES_ALIGNER_PARAMETERS_H_
#define EXAMPLES_ALIGNER_PARAMETERS_H_

#include <map>
#include <string>
#include <cassert>
using namespace std;
//
// Sets the named parameter given the elements of the transition matrix.
// To be called at the end of each Baum Welch iteration to update the parameter
// values.
//
// void set_go_honest(map<string,double> &tcounts, map<string,double> *pars);
// void set_go_dishonest(map<string,double> &tcounts, map<string,double> *pars);
void update_parameters(map<string, map<int,double> > &ecounts,
		       map<string, map<string,double> > &tcounts,
		       map<string, double> *pars);

#endif //  EXAMPLES_ALIGNER_PARAMETERS_H_
