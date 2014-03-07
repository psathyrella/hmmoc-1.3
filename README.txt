This is the readme file for HMMoC, the HMM compiler.



Requirements
============

You need Java (version 1.4 or later) to build and run HMMoC.  The Sun
java package version 1.5.0_07 works great; earlier versions had
problems.  Old versions of the GNU Java interpreter (installed on most
Linux systems) had some problems too; newer versions work fine.

To build the documentation .pdf file, you need pdflatex.  This is
installed on most Linux systems.

To compile the examples, you need a C++ compiler that understands
templates.  GCC 4.0.0, 4.0.1, 4.0.2 and 4.1.2 have been tested and
work fine; GCC 4.1.1 appears to be broken.

To run the HMMER example, you need to have python installed, and the
HMMER package (see below).



Installation
============

No installation is necessary -- type 'bin/hmmoc' to start the compiler.
To build the documentation, type 'make documentation' in the current 
directory.  You need to have installed pdflatex for this.  Ignore any 
warnings about a missing 'times.sty' file you might get -- just press 
enter to continue.  The manual.pdf file is in the doc/ directory.  




Examples
========

Examples are located in the examples/ directory.  Type 'make' inside the
appropriate directory to run HMMoC and build the executable.

'Casino' is the classic dishonest casino example from Durbin, Eddy, Krogh
and Mitchison 1998; perhaps the simplest possible HMM that does something
interesting.

An example of using an order-1 HMM is provided by the 'cpgisland' program.

'Aligner' is a simple but reasonably fully-fledged pairwise nucleotide
sequence aligner, as an example of how to implement pair HMMs.

'Genefinder' is a gene finder which, despite being quite naive, manages to
correctly find the yeast YHM2 gene - the first and only example I tried.
Technically, it shows how to use GHMMs, i.e. how to emit more than one symbol 
at a time.

The 'hmmer' directory contains a simple script that converts HMMER profile
HMMs into HMMoC xml files.  The Makefile builds two of these profiles, and
compares the running times of HMMER and the HMMoC-generated algorithm.
To make this work, you need to install HMMER (http://hmmer.janelia.org/),
and edit the HMMER_SEARCH variable in the Makefile to point to the
hmmsearch program.



Revision history
================

1.0  Upgraded to mature-sounding version number after review (3 Sept 07)
1.1  Small bugfix (18 Sept 07)
1.2  Added Generalized HMMs, and an example gene finder (6 Oct 07)
1.3  Sampler bugfixes: in the sampler, position-dependent code was off 
     by one, and the first BW table entry was overwritten.  DPTables.h
     now implements 4-D DP tables. (Thanks to Ian Holmes.)  (21 Feb 07)
