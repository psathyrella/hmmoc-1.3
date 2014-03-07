#include <stdlib.h>
#include "casino.h"
#include <iomanip>


int main(void) {
  Params iPar; // The parameters of the model

  iPar.iGoDishonest = 0.05;     // probability of going from Honest to the Dishonest state
  iPar.iGoHonest = 0.02;        // probability of going from Dishonest to the Honest state
  iPar.iGoStop = 0.00001;       // probability of going from either to the End state
  iPar.aEmitDishonest[0] = 0.1; 
  iPar.aEmitDishonest[1] = 0.1;
  iPar.aEmitDishonest[2] = 0.1;
  iPar.aEmitDishonest[3] = 0.1;
  iPar.aEmitDishonest[4] = 0.1;
  iPar.aEmitDishonest[5] = 0.5; // Probability of throwing a 6 is heavily favoured in the Dishonest state

  //
  // Sample a path.  This uses the HMM that does not emit symbols, because we want to sample from the HMM
  // itself, not conditional on an output sequence.
  //
  int iPathLength;
  Path *pTruePath;
  NoEmissionCasinoDPTable* pDPNE;               
  do {
    NEBackward(&pDPNE, iPar);                     // Initialize the DP table using the Backward algorithm
    pTruePath = &NESample(pDPNE,iPar);            // Sample
    iPathLength = pTruePath->size()-1;
  } while (iPathLength < 1/iPar.iGoStop);         // We don't want short sequences
  cout << "Sampled " << iPathLength << " throws" << endl;

  //
  // Next, build an input emission sequence by sampling the emitted symbols according to true path
  //
  char* aSequence = new char[ iPathLength ];
  int iNEHonest = pDPNE->getId("NEhonest");         // Get identifier of the honest state
  for (int i=0; i<iPathLength; i++) {
    double iP = random() / (double)RAND_MAX;
    int iSymbol = -1;
    do {
      // Calculate probability of current symbol, depending on current state
      double iCurrentP;
      iSymbol += 1;
      if (pTruePath->toState(i) == iNEHonest) {
	iCurrentP = 1/6.0;
      } else {
	iCurrentP = iPar.aEmitDishonest[iSymbol];
      }
      iP -= iCurrentP;
    } while (iP > 0.0);

    // Add symbol to sequence
    aSequence[i] = iSymbol + '1';
  }

  // Decode the emission sequence using Viterbi, and compute posteriors and Baum Welch counts using Forward and Backward
  CasinoDPTable *pViterbiDP, *pFWDP, *pBWDP;
  CasinoBaumWelch bw;

  cout << "Calculating Viterbi probability..." << endl;
  bfloat iVitProb = Viterbi_recurse(&pViterbiDP, iPar, aSequence, iPathLength );
  cout << "Viterbi: "<<iVitProb<<endl;

  cout << "Calculating Forward probability..." << endl;
  bfloat iFWProb = Forward(&pFWDP, iPar, aSequence, iPathLength );
  cout << "Forward: "<<iFWProb<<endl;

  cout << "Calculating Backward probability..." << endl;
  bfloat iBWProb = Backward(bw, pFWDP, &pBWDP, iPar, aSequence, iPathLength );
  cout << "Backward:"<<iBWProb<<endl;
  
  cout << "Calculating Viterbi path..." << endl;
  Path& iViterbiPath = Viterbi_trace(pViterbiDP, iPar, aSequence, iPathLength );
  
  // Find out how many states the Viterbi algorithm recovered correctly
  int iCorrect = 0,iNewCorrect = 0;
  for (int i=0; i<(int)iViterbiPath.size(); i++) {      // include last transition to the 'end' state
    if (iViterbiPath.toState(i) == pTruePath->toState(i)) {
      iCorrect += 1; // NOTE: since the code generation is non-deterministic, in this case the ordering of state labels
                     //    is not predictable (make clean; make gives different values), this gives the number which were
                     //    *in*correct maybe 1/4 of the time
    }
    if(pViterbiDP->getStateId(iViterbiPath.toState(i)) == "honest" && pDPNE->getStateId(pTruePath->toState(i)) == "NEhonest")
      iNewCorrect += 1;
    if(pViterbiDP->getStateId(iViterbiPath.toState(i)) == "dishonest" && pDPNE->getStateId(pTruePath->toState(i)) == "NEdishonest")
      iNewCorrect += 1;
  }
  cout << "Viterbi decoding got " << iCorrect << " out of " << iViterbiPath.size() << " states correct." << endl;
  cout << "or: " << setw(12) << iNewCorrect << endl;

  cout << "Baum Welch emission counts:"<<endl;

  cout << "\tHonest\tDishonest" << endl;
  for (int i=0; i<6; i++) {
    cout << i << ":\t"  
	 << bw.emissionBaumWelchCount1[i][ bw.emissionIndex("emitHonest") ] << "\t" 
	 << bw.emissionBaumWelchCount1[i][ bw.emissionIndex("emitDishonest") ] << endl;
  }

  // Compare the true and Viterbi paths, and print the posterior probability of being in the honest state
  int iVHonest = pViterbiDP->getId("honest");

  for (int i=0; i<min(iPathLength,30); i++) {

    cout << aSequence[i] << " True:";
    if (pTruePath->toState(i) == iNEHonest) {
      cout << "H";
    } else {
      cout << "D";
    }
    cout << " Viterbi:";
    if (iViterbiPath.toState(i) == iVHonest) {
      cout << "H";
    } else {
      cout << "D";
    }
    double iPosterior = pFWDP->getProb("honest",i+1)*pBWDP->getProb("honest",i+1)/iFWProb;
    cout << " " << iPosterior << endl;

  }
}
