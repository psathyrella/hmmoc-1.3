#include <stdlib.h>
#include "casino.h"
#include <iomanip>
#include <cassert>

//----------------------------------------------------------------------------------------
// make sure probabilities sum to 1
void check_probabilities(const vector<double> probs) {
  double total(0.0);
  for (unsigned ip=0; ip<probs.size(); ip++)
    total += probs[ip];
  assert(total == 1.0);
}
//----------------------------------------------------------------------------------------
int main(void) {
  srand(getpid());

  unsigned n_letters(4); // number of letters in emission alphabet
  Pars pars;
  pars.iGoHonest = 0.02;
  pars.iGoDishonest = 0.05;
  pars.iGoStop = 0.01;
  pars.aEmitHonest = {0.1, 0.1, 0.4, 0.4};
  pars.aEmitDishonest = {0.1, 0.4, 0.1, 0.4};
  assert(n_letters == pars.aEmitHonest.size());
  assert(n_letters == pars.aEmitDishonest.size());
  check_probabilities(pars.aEmitHonest);
  check_probabilities(pars.aEmitDishonest);

  // Sample a path.  This uses the HMM that does not emit symbols, because we want to sample from the HMM
  // itself, not conditional on an output sequence.
  int iPathLength;
  Path *pTruePath;
  NoEmissionCasinoDPTable* pDPNE;               
  do {
    NEBackward(&pDPNE, pars);                     // Initialize the DP table using the Backward algorithm
    pTruePath = &NESample(pDPNE,pars);            // Sample
    iPathLength = pTruePath->size()-1;
  } while (iPathLength < 1/pars.iGoStop);         // We don't want short sequences
  cout << "Sampled " << iPathLength << " throws" << endl;

  // Next, build an input emission sequence by sampling the emitted symbols according to true path
  char* aSequence = new char[ iPathLength ];
  for (int i=0; i<iPathLength; i++) {
    double iP = random() / (double)RAND_MAX;
    int iSymbol = -1;
    do {
      // Calculate probability of current symbol, depending on current state
      double iCurrentP;
      iSymbol += 1;
      if (pDPNE->getStateId(pTruePath->toState(i)) == "NEhonest") {
	iCurrentP = pars.aEmitHonest[iSymbol];
      } else if (pDPNE->getStateId(pTruePath->toState(i)) == "NEdishonest") {
	iCurrentP = pars.aEmitDishonest[iSymbol];
      } else assert(0);
      iP -= iCurrentP;
    } while (iP > 0.0);

    // Add symbol to sequence
    aSequence[i] = iSymbol + '1';
  }

  // Decode the emission sequence using Viterbi, and compute posteriors and Baum Welch counts using Forward and Backward
  CasinoDPTable *pFWDP(0), *pBWDP(0);
  CasinoBaumWelch bw;
  cout << setw(22) << "forward probability:";
  bfloat iFWProb = Forward(&pFWDP, pars, aSequence, iPathLength );
  cout << setw(12) << iFWProb << endl;
  cout << setw(22) << "backward probability: ";
  bfloat iBWProb = Backward(bw, pFWDP, &pBWDP, pars, aSequence, iPathLength );
  cout << setw(12) << iBWProb << endl;

  cout << "Baum Welch emission counts:"<<endl;
  cout << setw(12) << " " << setw(12) << "Honest" << setw(12) << "Dishonest" << endl;
  for (unsigned i=0; i<n_letters; i++) {
    cout << setw(12) << i
// ???	 << setw(12) << "emitHonest"  << setw(12) << bw_counters->emissionIndex("emitHonest" ) << setw(12) << bw_counters->emissionBaumWelchCount0[bw_counters->emissionIndex("emitHonest" )] << endl
	 << setw(12) << bw.emissionBaumWelchCount1[i][ bw.emissionIndex("emitHonest") ]
	 << setw(12) << bw.emissionBaumWelchCount1[i][ bw.emissionIndex("emitDishonest") ]
	 << endl;
  }

  CasinoDPTable *pViterbiDP(0);
  cout << "viterbi probability: ";
  bfloat iVitProb = Viterbi_recurse(&pViterbiDP, pars, aSequence, iPathLength );
  cout << setw(12) << iVitProb << endl;
  Path& iViterbiPath = Viterbi_trace(pViterbiDP, pars, aSequence, iPathLength );

  // Find out how many states the Viterbi algorithm recovered correctly
  int iNewCorrect = 0;
  for (int i=0; i<(int)iViterbiPath.size(); i++) {      // include last transition to the 'end' state
    string inferredState = pViterbiDP->getStateId(iViterbiPath.toState(i));
    string realState = pDPNE->getStateId(pTruePath->toState(i));
    assert(realState.find("NE") != string::npos);
    realState.erase(0,2);
    if (inferredState == realState)
      iNewCorrect += 1;
  }
  cout << "correct: "
       << setw(12) << iNewCorrect << " / " << iViterbiPath.size()
       << " = " << (double(iNewCorrect) / iViterbiPath.size()) << endl;

  // Compare the true and Viterbi paths, and print the posterior
  // probability of being in the honest state
  string sampled_seq,vtb_path,true_path;
  for (int i=0; i<iPathLength; i++) {
    sampled_seq += aSequence[i];
    if (pDPNE->getStateId(pTruePath->toState(i)) == "NEhonest") {
      true_path += "h";
    } else if (pDPNE->getStateId(pTruePath->toState(i)) == "NEdishonest") {
      true_path += "d";
    } else {
      assert(0);
    }
    if (pViterbiDP->getStateId(iViterbiPath.toState(i)) == "honest") {
      vtb_path += "h";
    } else if (pViterbiDP->getStateId(iViterbiPath.toState(i)) == "dishonest") {
      vtb_path += "d";
    } else assert(0);
    // double iPosterior = pFWDP->getProb("honest",i+1)*pBWDP->getProb("honest",i+1)/iFWProb;
    // cout << " " << iPosterior << endl;
  }
  cout << setw(22) << "emitted sequence: " << sampled_seq << endl;
  cout << setw(22) << "vtb path: " << vtb_path << endl;
  cout << setw(22) << "true path: " << true_path << endl;
}
