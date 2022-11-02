#!/bin/bash

mkdir ../ios
mkdir ../logs
for file in $(ls|grep bz2)
do 
echo "$file"
./bgpdump $file >> readable.txt
done
