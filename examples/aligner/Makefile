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
aligner: main.cc aligner.cc aligner.h algebras.cc algebras.h dptables.h mybanding.h
	g++ -Wno-deprecated -o $@ -lstdc++ -O3 -ffast-math -finline-limit=10000 -Wall main.cc aligner.cc algebras.cc

aligner.cc aligner.h: aligner.xml
	../../bin/hmmoc aligner.xml

algebras.cc algebras.h dptables.h:
	cp ../../include/* .

clean:
	rm -f algebras.*
	rm -f dptables.h
	rm -f *.debug
	rm -f aligner.cc
	rm -f aligner.h
	rm -f aligner
