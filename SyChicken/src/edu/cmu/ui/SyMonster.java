package edu.cmu.ui;
import edu.cmu.codeformer.CodeFormer;
import edu.cmu.compilation.Test;
import edu.cmu.equivprogram.DependencyMap;
import edu.cmu.parser.*;
import edu.cmu.typeact.PathGenerator;
import edu.cmu.typeact.TypeActivationReachability;
import edu.cmu.utils.TimerUtils;
import org.sat4j.specs.ContradictionException;
import org.sat4j.specs.TimeoutException;
import uniol.apt.adt.pn.PetriNet;

import java.io.*;
import java.util.*;

public class SyMonster {
	public static void main(String[] args) throws IOException, TimeoutException, ContradictionException {
	    //Command line arguments
        List<String> arglist = Arrays.asList(args);
        String jsonPath;
        BufferedWriter out = null;
        // 0. Read input from the user
        SyMonsterInput jsonInput;
        if (args.length == 0) {
            System.out.println("Please use the program args next time.");
            jsonInput = JsonParser.parseJsonInput("benchmarks/math/1/benchmark1.json");
            File outfile = new File("benchmarks/thing.txt");
            out = new BufferedWriter(new FileWriter(outfile));
        }
        else{
            jsonInput = JsonParser.parseJsonInput(args[0]);
            String outputPath = args[1];
            File outfile = new File(outputPath);
            if (!outfile.exists()) outfile.createNewFile();
            out = new BufferedWriter(new FileWriter(outfile));
        }

        // 1. Read config
        SymonsterConfig jsonConfig = JsonParser.parseJsonConfig("config/config.json");
        Set<String> acceptableSuperClasses = new HashSet<>();
        acceptableSuperClasses.addAll(jsonConfig.acceptableSuperClasses);

        String methodName = jsonInput.methodName;
        List<String> libs = jsonInput.libs;
        List<String> inputs = jsonInput.srcTypes;

        List<String> varNames = jsonInput.paramNames;
        String retType = jsonInput.tgtType;
        File file = new File(jsonInput.testPath);
        BufferedReader br = new BufferedReader(new FileReader(file));
        StringBuilder fileContents = new StringBuilder();
        String line = br.readLine();
        while (line != null) {
            fileContents.append(line);
            line = br.readLine();
        }
        String testCode = fileContents.toString();
        TimerUtils.startTimer("soot");
        List<MethodSignature> sigs = JarParser.parseJar(libs,jsonInput.packages,jsonConfig.blacklist);
        Map<String,Set<String>> superclassMap = JarParser.getSuperClasses(acceptableSuperClasses);
        Map<String,Set<String>> subclassMap = new HashMap<>();
        for (String key : superclassMap.keySet()){
            for (String value :superclassMap.get(key)){
                if (!subclassMap.containsKey(value)){
                    subclassMap.put(value,new HashSet<String>());
                }
                subclassMap.get(value).add(key);
            }
        }
        TimerUtils.stopTimer("soot");
        // 3. build a petrinet and signatureMap of library
        // Currently built without clone edges
        TimerUtils.startTimer("equiv");
        Set<List<MethodSignature>> repeatSolutions = new HashSet<>();
        Map<String, MethodSignature> signatureMap;
        DependencyMap dependencyMap = JarParser.createDependencyMap();
        TimerUtils.stopTimer("equiv");
        PetriNet net;
        TimerUtils.startTimer("buildnet");
        HashMap<String,Integer> inputCounts = new HashMap<>();
        for (String input : jsonInput.srcTypes){
            if (inputCounts.containsKey(input)) inputCounts.put(input,inputCounts.get(input)+1);
            else inputCounts.put(input,1);
        }
        PathGenerator generator = new PathGenerator(retType,superclassMap);
        System.out.println("super!: "+ superclassMap);
        System.out.println("input counts: "+inputCounts);

        int programs = 0;
        int sets = 0;
        int validsets = 0;
        int paths = 0;

        int curlen = 3;
        TimerUtils.startTimer("constraints");
        TypeActivationReachability tar = new TypeActivationReachability(sigs,inputCounts,jsonInput.tgtType,superclassMap,curlen);
        TimerUtils.stopTimer("constraints");
        while (true){
            TimerUtils.startTimer("set");
            Set<MethodSignature> set = tar.solve();
            TimerUtils.stopTimer("set");
            if (set == null){
                curlen += 1;
                System.out.println("Curlen: "+curlen);
                TimerUtils.startTimer("constraints");
                tar = new TypeActivationReachability(sigs,inputCounts,jsonInput.tgtType,superclassMap,curlen);
                TimerUtils.stopTimer("constraints");
            }
            else{
                List<List<MethodSignature>> allseq = generator.generate(set,new HashMap<>(inputCounts));
                if (allseq.size() > 0){
                    validsets += 1;
                }
                sets += 1;
                paths += allseq.size();
                for (List<MethodSignature> signatures : allseq){
                    boolean sat = true;
                    CodeFormer former = new CodeFormer(signatures,inputs,retType, varNames, methodName,subclassMap, superclassMap);
                    while (sat){
                        TimerUtils.startTimer("code");
                        String code;
                        try {
                            code = former.solve();
                        } catch (TimeoutException e) {
                            sat = false;
                            break;
                        }
                        sat = !former.isUnsat();
                        TimerUtils.stopTimer("code");
                        // 6. Run the test cases
                        // TODO: write this code; if all test cases pass then we can terminate
                        TimerUtils.startTimer("compile");
                        boolean compre = Test.runTest(code,testCode);
                        TimerUtils.stopTimer("compile");
                        programs ++;
                        if (compre) {
                            writeLog(out,"Options:\n");
                            writeLog(out,"Programs explored = " + programs+"\n");
                            writeLog(out,"Sets explored = " + sets+"\n");
                            writeLog(out,"Valid Sets explored = " + validsets+"\n");
                            writeLog(out,"Paths explored = " + paths+"\n");
                            writeLog(out,"code:\n");
                            writeLog(out,code+"\n");
                            writeLog(out,"Soot time: "+TimerUtils.getCumulativeTime("soot")+"\n");
                            writeLog(out,"Equivalent program preprocess time: "+TimerUtils.getCumulativeTime("equiv")+"\n");
                            writeLog(out,"Form code time: "+TimerUtils.getCumulativeTime("code")+"\n");
                            writeLog(out,"Set finding time: "+TimerUtils.getCumulativeTime("set")+"\n");
                            writeLog(out,"Compilation time: "+TimerUtils.getCumulativeTime("compile")+"\n");
                            writeLog(out,"Constraint time: "+TimerUtils.getCumulativeTime("constraints")+"\n");
                            out.close();

                            File compfile = new File("build/Target.class");
                            compfile.delete();
                            System.exit(0);
                        }
                    }
                }
            }
        }
	}

	private static void writeLog(BufferedWriter out,String string){
        try {
            System.out.println(string);
            out.write(string);
        } catch (IOException e) {
            System.exit(1);
        }
    }
}
