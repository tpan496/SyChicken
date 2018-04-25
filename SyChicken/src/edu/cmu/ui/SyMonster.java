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
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class SyMonster {
	public static void main(String[] args) throws IOException, TimeoutException, ContradictionException {
	    //Command line arguments
        List<String> arglist = Arrays.asList(args);
        boolean clone = false;
        boolean equiv = false;
        boolean copyPoly = false;
        String jsonPath;
        BufferedWriter out = null;
        // 0. Read input from the user
        SyMonsterInput jsonInput;
        if (args.length == 0) {
            System.out.println("Please use the program args next time.");
            jsonInput = JsonParser.parseJsonInput("benchmarks/tests/1/test1.json");
        }
        else{
            jsonInput = JsonParser.parseJsonInput(args[0]);
            String outputPath = args[1];
            File outfile = new File(outputPath);
            if (!outfile.exists()) outfile.createNewFile();
            out = new BufferedWriter(new FileWriter(outfile));
            clone = arglist.contains("-c");
            equiv = arglist.contains("-e");
            copyPoly = arglist.contains("-cp");
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
        DependencyMap dependencyMap = null;
        Map<String, MethodSignature> signatureMap;
        if (equiv){
            dependencyMap = JarParser.createDependencyMap();
        }
        TimerUtils.stopTimer("equiv");
        PetriNet net;
        TimerUtils.startTimer("buildnet");
        HashMap<String,Integer> inputCounts = new HashMap<>();
        for (String input : jsonInput.srcTypes){
            if (inputCounts.containsKey(input)) inputCounts.put(input,inputCounts.get(input)+1);
            else inputCounts.put(input,1);
        }
        System.out.println(sigs);
        TypeActivationReachability tar = new TypeActivationReachability(sigs,inputCounts,jsonInput.tgtType,subclassMap);
        PathGenerator generator = new PathGenerator(retType,superclassMap);

        while (true){
            Set<MethodSignature> set = tar.solve();
            if (set != null){
                System.out.println("set: "+set);
                List<List<MethodSignature>> allseq = generator.generate(set,new HashMap<>(inputCounts));
                if(allseq.size()>0){
                    System.out.println("all seq: "+allseq);
                    System.out.println(set.size());
                    break;
                }
            }
        }

	}

	private static void writeLog(BufferedWriter out,String string){
        try {
            out.write(string);
        } catch (IOException e) {
            System.exit(1);
        }
    }
}
