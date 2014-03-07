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
documentation:
	@cp Makefile doc/
	$(MAKE) -C doc/ manual.pdf

jar:
	@cp Makefile src/
	$(MAKE) -C src/ hmmoc.jar

allexamples:
	$(MAKE) -C examples/aligner
	$(MAKE) -C examples/casino
	$(MAKE) -C examples/cpgislands
	$(MAKE) -C examples/genefinder
	$(MAKE) -C examples/hmmer

all: jar documentation

clean:
	rm -f src/Makefile
	rm -f src/hmmoc/*/*.class
	rm -f doc/Makefile

very-clean: clean
	rm -rf build/
	rm -rf distrib/
	rm -f src/hmmoc.jar
	rm -f doc/manual.pdf
	rm -f examples/*/*.d
	$(MAKE) -C examples/aligner clean
	$(MAKE) -C examples/casino clean
	$(MAKE) -C examples/classifier clean
	$(MAKE) -C examples/cpgislands clean	
	$(MAKE) -C examples/hmmer clean
	$(MAKE) -C examples/genefinder clean
	$(MAKE) -C examples/localalign clean
	$(MAKE) -C examples/saskia clean

# target for src/
hmmoc.jar: hmmoc/appl/*.java hmmoc/code/*.java hmmoc/util/*.java hmmoc/xml/*.java hmmoc/appl/snippets.xml
	javac -g -classpath ../lib/jdom.jar:../lib/xerces.jar:../lib/xml-apis.jar -source 1.4 -target 1.4 hmmoc/appl/*.java hmmoc/code/*.java hmmoc/util/*.java hmmoc/xml/*.java
	@echo "Manifest-Version: 1.0" > manifest
	@echo "Main-Class: hmmoc.appl.hmmoc" >> manifest
	@echo "Class-Path: jdom.jar xerces.jar xml-apis.jar" >> manifest
	jar cvfm hmmoc.jar manifest hmmoc/appl/*.class hmmoc/code/*.class hmmoc/util/*.class hmmoc/xml/*.class hmmoc/appl/snippets.xml >/dev/null
	@rm manifest
	@mv hmmoc.jar ../lib/hmmoc.jar

# target for doc/
manual.pdf: manual.tex
	pdflatex manual.tex > manual.log1
	pdflatex manual.tex > manual.log2
	@rm *.aux
	@rm *.log*

version=1.3
examples=aligner casino cpgislands hmmer genefinder

distrib-dave: examples=aligner casino cpgislands localalign
distrib-dave: version=1.0-dave
distrib-dave: distrib

distrib-saskia: examples=aligner casino cpgislands saskia
distrib-saskia: version=1.0-saskia
distrib-saskia: distrib

distrib: very-clean
	rm -rf build/
	mkdir build
	cp Makefile LICENSE.txt README.txt build/
	cp --parent src/hmmoc/*/*.java doc/*.tex doc/*.jpg include/* lib/* bin/hmmoc build/
	cp --parent $(examples:%=examples/%/Makefile) build/
	cp --parent $(examples:%=examples/%/*.cc) build/
	-cp --parent $(examples:%=examples/%/*.h) build/
	-cp --parent $(examples:%=examples/%/*.tex) build/
	-cp --parent $(examples:%=examples/%/*.xml) build/
	-cp --parent $(examples:%=examples/%/*.txt) build/
	-cp --parent $(examples:%=examples/%/*.fa) build/
	-cp examples/hmmer/*.hmm examples/hmmer/parse-hmmer.py build/examples/hmmer
	cp examples/aligner/sequence.fa build/examples/aligner/
	sed "s/VERSION/$(version)/g" < src/hmmoc/appl/snippets.xml > build/src/hmmoc/appl/snippets.xml
	sed "s/VERSION/$(version)/g" < src/hmmoc/appl/hmmoc.java > build/src/hmmoc/appl/hmmoc.java
	bin/gpl $(version) -make build/Makefile
	bin/gpl $(version) -c build/src/hmmoc/*/*.java
	bin/gpl $(version) -tex build/doc/*.tex
	bin/gpl $(version) -c build/include/*
	bin/gpl $(version) -c build/examples/*/*.cc
	bin/gpl $(version) -c build/examples/*/*.h
	bin/gpl $(version) -xml build/examples/*/*.xml
	bin/gpl $(version) -make build/examples/*/Makefile
	make -C build jar
	make -C build clean
	mv build hmmoc-$(version)
	-tar -zcvf hmmoc-$(version).tgz hmmoc-$(version)/*
	rm -rf hmmoc-$(version)
	mkdir -p distrib
	mv hmmoc-$(version).tgz distrib

