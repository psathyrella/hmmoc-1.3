#!/bin/bash

file=$1
if ! [ -f "$file" ]; then echo "file d.n.e, exiting"; exit 1; fi

for var in iPrevSlowCoord CurStateMemoryblock1To; do
    sed -i "/bfloat.*$var;\|int.*$var;/ a\
if ($var==$var) {} // avoid unused variable warnings" aligner.cc
done
