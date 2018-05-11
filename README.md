# SyChicken
To run the program:
1. Go to the inner SyChicken directory.
2. Run: ant.
3. Run: ant run -Dargs="benchmarks/geometry/10/benchmark10 -e", where the first argument represents the test json file. Make sure you are using Java version <= 1.8 so that Soot does not break!
There are 13 json files in the benchmarks library. The -e option activates the equivalent program elimination.

Note: test cases typically takes more than 1 minute in total, so wait for a while if the program seems to get stuck.
