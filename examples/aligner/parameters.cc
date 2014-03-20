#include "parameters.h"

// // //----------------------------------------------------------------------------------------
// void set_go_honest(map<string, map<string,double> > &tcounts) {
//   assert(tcounts.find("dishonest_honest") != tcounts.end());
//   assert(tcounts.find("dishonest_dishonest") != tcounts.end());
//   assert(tcounts.find("dishonest_stop") != tcounts.end());
//   return
//     tcounts["dishonest_honest"] / (tcounts["dishonest_honest"] + tcounts["dishonest_dishonest"] + tcounts["dishonest_stop"]);
// }
// //----------------------------------------------------------------------------------------
// void set_go_dishonest(map<string,double> &tcounts, map<string,double> *pars) {
//   assert(tcounts.find("h_d") != tcounts.end());
//   assert(tcounts.find("h_h") != tcounts.end());
//   assert(tcounts.find("h_s") != tcounts.end());
//   (*pars)["go_dishonest"] =
//     tcounts["h_d"] / (tcounts["h_d"] + tcounts["h_h"] + tcounts["h_s"]);
// }
void update_parameters(map<string, map<int,double> > &ecounts,
		       map<string, map<string,double> > &tcounts,
		       map<string, double> *pars) {
  // TODO: need to assert that we have all of these
  (*pars)["go_honest"]    = tcounts["dishonest"]["honest"] / (tcounts["dishonest"]["honest"] + tcounts["dishonest"]["dishonest"] + tcounts["dishonest"]["stop"]);
  (*pars)["go_dishonest"] = tcounts["honest"]["dishonest"] / (tcounts["honest"]["dishonest"] + tcounts["honest"]["honest"]       + tcounts["honest"]["stop"]);

  (*pars)["go_dishonest"] = tcounts["honest"]["dishonest"] / (tcounts["honest"]["dishonest"] + tcounts["honest"]["honest"]       + tcounts["honest"]["stop"]);
}
