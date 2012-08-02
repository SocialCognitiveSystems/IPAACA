#!/bin/bash

PACKAGES="rsb"

mkdir -p deps/bin
mkdir -p deps/lib
mkdir -p deps/include
mkdir -p deps/scripts
for P in $PACKAGES; do
	cp -a ../$P/dist/bin/* deps/bin/
	cp -a ../$P/dist/lib/* deps/lib/
	cp -a ../$P/dist/include/* deps/include/
	cp -a ../$P/dist/scripts/* deps/scripts/
done

