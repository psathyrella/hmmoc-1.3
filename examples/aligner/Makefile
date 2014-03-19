CC_OPTIONS = -std=c++0x -Wno-strict-overflow -Wno-deprecated -Wall
# commented out while debugging
OPTIMIZE_OPTIONS = #-O3 -ffast-math -finline-limit=10000
DEBUG_OPTIONS = #-g

aligner: main.cc aligner.cc aligner.h algebras.cc algebras.h dptables.h mybanding.h HmmDriver.h parameters.h
	g++ ${CC_OPTIONS} ${OPTIMIZE_OPTIONS} ${DEBUG_OPTIONS} -o $@ -lstdc++ main.cc aligner.cc algebras.cc parameters.cc

aligner.cc aligner.h: aligner.xml
	../../bin/hmmoc aligner.xml
	../../bin/use-unused-vars.sh aligner.cc CurStateMemoryNEblock1To CurStateMemoryNEblock2To CurStateMemoryNEblock3To iPrevSlowCoord CurStateMemoryblock1To
	../../bin/replace-strings aligner.cc aligner.h

algebras.cc algebras.h dptables.h:
	cp ../../include/* .

clean:
	rm -f algebras.*
	rm -f dptables.h
	rm -f *.debug
	rm -f aligner.cc
	rm -f aligner.h
	rm -f aligner
	rm -f *.o
