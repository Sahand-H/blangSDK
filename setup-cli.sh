#!/bin/bash

echo
echo "      INSTALLING BLANG (COMMAND LINE INTERFACE)"
echo "       This may take some time as dependencies"
echo "                are being downloaded"       
echo

# some weird gradle-xtext-blang problem may be caused by deamon trying to 
# handle 2 Blang versions, try to avoid this restarting the daemon after a Blang update
./gradlew --stop
  
./gradlew clean
./gradlew installDist

# Fix problem arising if eclipse is used jointly
mkdir build/xtend/test
mkdir build/blang/test

echo
echo "             INSTALLATION WAS SUCCESSFUL"
echo "               Type 'blang' to try it"
echo

if hash blang 2>/dev/null; then
    echo 
else
    echo "NOTE: We are adding a line into ~/.bash_profile to make the blang CLI command"
    echo "      accessible from any directory (as blang is not found in PATH right now)."
    echo
    to_add="$(pwd)/build/install/blang/bin/"
    existing='$PATH'
    line="export PATH=${existing}:${to_add}"
    export PATH=$PATH:${to_add}
    echo $line >>~/.bash_profile
fi
