aligner: main.cc aligner.cc aligner.h algebras.cc algebras.h dptables.h mybanding.h
#	g++ -std=c++0x -Wno-strict-overflow -Wno-deprecated -o $@ -lstdc++ -O3 -ffast-math -finline-limit=10000 -Wall main.cc aligner.cc algebras.cc
# debugging:
	g++ -std=c++0x -g -Wno-strict-overflow -Wno-deprecated -o $@ -lstdc++ -Wall main.cc aligner.cc algebras.cc

aligner.cc aligner.h: aligner.xml
	../../bin/hmmoc aligner.xml
	rm -f algebras.h~
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
