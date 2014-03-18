#!/bin/bash
# Usage: use-unused-vars.sh <source file> <var1> <var2> ...
# Adds trivial/null usage of variables in the line immediately following their declaration
# in order to avoid unused variable warnings.

file=$1
var_names=`echo $* | sed 's/[^ ][^ ]* //'`

if ! [ -f "$file" ]; then echo "file d.n.e, exiting"; exit 1; fi

for var in $var_names; do
    sed -i "/bfloat.*$var;\|int.*$var;/ a\
if ($var==$var) {} // avoid unused variable warnings" $file
done
