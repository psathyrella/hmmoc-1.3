#include "parameters.h"

void set_go_dishonest(map<string,double> &tcounts, map<string,double> *pars) {
  assert(tcounts.find("tr_h_d") != tcounts.end());
  assert(tcounts.find("tr_h_h") != tcounts.end());
  assert(tcounts.find("tr_h_s") != tcounts.end());
  (*pars)["go_dishonest"] =
    tcounts["tr_h_d"] / (tcounts["tr_h_d"] + tcounts["tr_h_h"] + tcounts["tr_h_s"]);
}
