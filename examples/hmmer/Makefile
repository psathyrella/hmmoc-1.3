#
#    This file is part of HMMoC 1.3, a hidden Markov model compiler.
#    Copyright (C) 2007 by Gerton Lunter, Oxford University.
#
#    HMMoC is free software; you can redistribute it and/or modify
#    it under the terms of the GNU General Public License as published by
#    the Free Software Foundation; either version 2 of the License, or
#    (at your option) any later version.
#
#    HMMOC is distributed in the hope that it will be useful,
#    but WITHOUT ANY WARRANTY; without even the implied warranty of
#    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
#    GNU General Public License for more details.
#
#    You should have received a copy of the GNU General Public License
#    along with HMMoC; if not, write to the Free Software
#    Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
#
#
# Simple test suite to compare HMMoC's speed with that of HMMER.
#
# The program in main.cc just executes the core algorithms -- viterbi, or forward -- with
# no post-processing.  This is a fair comparison, because by far the bulk of running time 
# is spent on the core algorithm.
#
# The HMMER program viterbi + forward, whereas the HMMoC main.cc executes either the viterbi 
# or the forward algorithm.
#
# HMMoC's code relies heavily on inlining; however, since HMMER's HMMs are rather big, too 
# much inlining results in cache misses.  Inlining therefore needs to compromise between the
# two extremes.  The values for "-finline-limit" give good results on my machine, but need
# not be optimal on all.
#

HMMER_SEARCH=$(HOME)/Appls/hmmer-2.3.2/src/hmmsearch

run-test: test-tk test-znfc2hc

compiletests: tk_logspace tk_bfloat znfc2hc_logspace znfc2hc_bfloat

# These figures were used in the Bioinformatics paper:
test-tk: hmmer_vitfw=6.46
test-tk: hmmoc_vit=3.20
test-tk: hmmoc_fw=3.91
test-znfc2hc: hmmer_vitfw=9.45
test-znfc2hc: hmmoc_vit=3.96
test-znfc2hc: hmmoc_fw=7.87

test-%: compiletests proteins.fa
	@echo "#################################################"
	@echo "Starting tests for $*..."
	@echo "*** hmmer Viterbi + Forward ***"
	time -p $(HMMER_SEARCH) --forward $*.hmm proteins.fa >/dev/null
	@echo "(Xeon CPU E5345 @ 2.33GHz: User = $(hmmer_vitfw) )"
	@echo "*** HMMoC Viterbi ***"
	time -p ./$*_logspace --viterbi proteins.fa >/dev/null
	@echo "(Xeon CPU E5345 @ 2.33GHz: User = $(hmmoc_vit) )"
	@echo "*** HMMoC Forward ***"
	time -p ./$*_bfloat proteins.fa >/dev/null
	@echo "(Xeon CPU E5345 @ 2.33GHz: User = $(hmmoc_fw) )"
	@echo "Tests for $* done."
	@echo "#################################################"

tk_logspace: tk_logspace_main.cc tk_logspace.cc algebras.cc algebras.h dptables.h 
	g++ -o $@ -lstdc++ -O3 -ffast-math -finline-limit=10000 -Wall $^ $@_params.cc

tk_bfloat: tk_bfloat_main.cc tk_bfloat.cc algebras.cc algebras.h dptables.h 
	g++ -o $@ -lstdc++ -O3 -ffast-math -finline-limit=200 -Wall $^ $@_params.cc

znfc2hc_logspace: znfc2hc_logspace_main.cc znfc2hc_logspace.cc algebras.cc algebras.h dptables.h 
	g++ -o $@ -lstdc++ -O3 -ffast-math -finline-limit=10000 -Wall $^ $@_params.cc

znfc2hc_bfloat: znfc2hc_bfloat_main.cc znfc2hc_bfloat.cc algebras.cc algebras.h dptables.h 
	g++ -o $@ -lstdc++ -O3 -ffast-math -finline-limit=175 -Wall $^ $@_params.cc

algebras.h algebras.cc dptables.h:
	cp ../../include/* .

%_logspace.xml: %.hmm parse-hmmer.py
	python parse-hmmer.py --logspace $*.hmm > $@

%_bfloat.xml: %.hmm parse-hmmer.py
	python parse-hmmer.py $*.hmm > $@

.PRECIOUS: %_logspace .xml %_bfloat.xml

%_main.cc: main.cc %.cc
	@echo "#include \"$*.h\"" > $@
	@cat main.cc >> $@

force: ;

# the files created by hmmoc don't have the suffix _logspace or _bfloat -- so add them
# also, the hmmoc-generated files expect a header file without the suffix -- so make one
#
# don't bother about *_params -- there is not realtype-dependent stuff in there
%.cc: %.xml
	@echo `echo $* | sed s/_[^\.]*//g` > $*.stem
	../../bin/hmmoc $*.xml
	@mv `cat $*.stem`.h $*.h
	@sed s/`cat $*.stem`.h/$*.h/g < `cat $*.stem`.cc > $*.cc
	@sed s/`cat $*.stem`.h/$*.h/g < `cat $*.stem`_params.cc > $*_params.cc
	@rm `cat $*.stem`.cc
	@rm `cat $*.stem`_params.cc
	@rm $*.stem

uniprot_sprot.fasta:
	wget ftp://ftp.expasy.org/databases/uniprot/current_release/knowledgebase/complete/uniprot_sprot.fasta.gz
	gunzip uniprot_sprot.fasta.gz

proteins.fa: uniprot_sprot.fasta
	sed '600000,700000!d' uniprot_sprot.fasta | sed -n '/>/,$$p' > $@

%-clean:
	rm -f $*.cc $*.h $*.xml $*_params.h $*_params.cc $*

clean: rrm-clean globin-clean znf_c2h2-clean
	rm -f algebras.*
	rm -f dptables.h
	rm -f *.debug
	rm -f tk_*
	rm -f znfc2hc_*

very-clean: clean
	rm uniprot_sprot.fasta proteins.fa
