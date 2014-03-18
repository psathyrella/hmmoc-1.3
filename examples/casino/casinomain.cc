#include <stdlib.h>
#include "casino.h"
#include <iomanip>
#include <cassert>

int main(void) {
  srand(getpid());

  unsigned n_letters(4); // number of letters in emission alphabet
  Pars pars;
  pars.iGoHonest = 0.02;
  pars.iGoDishonest = 0.05;
  pars.iGoStop = 0.005;
  // pars.aEmitHonest = {0.25, 0.25, 0.25, 0.25};
  // pars.aEmitDishonest = {0.1, 0.1, 0.1, 0.7};
  pars.aEmitHonest = {0.1, 0.4, 0.1, 0.4};
  pars.aEmitDishonest = {0.1, 0.1, 0.4, 0.4};
  assert(n_letters == pars.aEmitHonest.size());
  assert(n_letters == pars.aEmitDishonest.size());

  //
  // Sample a path.  This uses the HMM that does not emit symbols, because we want to sample from the HMM
  // itself, not conditional on an output sequence.
  //
  int iPathLength;
  Path *pTruePath;
  NoEmissionCasinoDPTable* pDPNE;               
  do {
    NEBackward(&pDPNE, pars);                     // Initialize the DP table using the Backward algorithm
    pTruePath = &NESample(pDPNE,pars);            // Sample
    iPathLength = pTruePath->size()-1;
  } while (iPathLength < 1/pars.iGoStop);         // We don't want short sequences
  cout << "Sampled " << iPathLength << " throws" << endl;

  //
  // Next, build an input emission sequence by sampling the emitted symbols according to true path
  //
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
  CasinoDPTable *pViterbiDP, *pFWDP, *pBWDP;
  CasinoBaumWelch bw;

  cout << "Calculating Viterbi probability..." << endl;
  bfloat iVitProb = Viterbi_recurse(&pViterbiDP, pars, aSequence, iPathLength );
  cout << "Viterbi: "<<iVitProb<<endl;

  cout << "Calculating Forward probability..." << endl;
  bfloat iFWProb = Forward(&pFWDP, pars, aSequence, iPathLength );
  cout << "Forward: "<<iFWProb<<endl;

  cout << "Calculating Backward probability..." << endl;
  bfloat iBWProb = Backward(bw, pFWDP, &pBWDP, pars, aSequence, iPathLength );
  cout << "Backward:"<<iBWProb<<endl;
  
  cout << "Calculating Viterbi path..." << endl;
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

  cout << "Baum Welch emission counts:"<<endl;
  cout << setw(12) << " " << setw(12) << "Honest" << setw(12) << "Dishonest" << endl;
  for (unsigned i=0; i<n_letters; i++) {
    cout << setw(12) << i
	 << setw(12) << bw.emissionBaumWelchCount1[i][ bw.emissionIndex("emitHonest") ]
	 << setw(12) << bw.emissionBaumWelchCount1[i][ bw.emissionIndex("emitDishonest") ]
	 << endl;
  }

  // Compare the true and Viterbi paths, and print the posterior
  // probability of being in the honest state
  for (int i=0; i<min(iPathLength,30); i++) {
    cout << aSequence[i] << " True:";
    if (pDPNE->getStateId(pTruePath->toState(i)) == "NEhonest") {
      cout << "H";
    } else if (pDPNE->getStateId(pTruePath->toState(i)) == "NEdishonest") {
      cout << "D";
    } else assert(0);
    cout << " Viterbi:";
    if (pViterbiDP->getStateId(iViterbiPath.toState(i)) == "honest") {
      cout << "H";
    } else if (pViterbiDP->getStateId(iViterbiPath.toState(i)) == "dishonest") {
      cout << "D";
    } else assert(0);

    double iPosterior = pFWDP->getProb("honest",i+1)*pBWDP->getProb("honest",i+1)/iFWProb;
    cout << " " << iPosterior << endl;
  }
}
