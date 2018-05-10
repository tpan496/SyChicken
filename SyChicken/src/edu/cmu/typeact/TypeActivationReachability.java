package edu.cmu.typeact;

import edu.cmu.parser.MethodSignature;
import org.python.antlr.ast.Str;
import org.sat4j.core.ConstrGroup;
import org.sat4j.core.VecInt;
import org.sat4j.minisat.SolverFactory;
import org.sat4j.specs.ContradictionException;
import org.sat4j.specs.IConstr;
import org.sat4j.specs.ISolver;
import org.sat4j.specs.TimeoutException;
import soot.Type;

import java.util.*;

/**
 * This class provide the reachability analysis with the type activation model.
 */
public class TypeActivationReachability {
    private ISolver solver = SolverFactory.newDefault();
    private Map<Met,Integer> sigtoint = new HashMap<>();
    private Map<Integer,MethodSignature> inttosig = new HashMap<>();

    private Map<String,Integer> typetoint = new HashMap<>();
    private Map<Integer,String> inttotype = new HashMap<>();
    private Map<Integer,Met> inttosigtest = new HashMap<>();
    private int curid = 1;
    private final Map<String,Integer> inputCounts;
    private final String retType;
    private int sigmax;
    public TypeActivationReachability(List<MethodSignature> sigs, Map<String,Integer> inputCounts,String retType,Map<String,Set<String>> subtosuper,int curlen) throws TimeoutException {
        this.inputCounts = inputCounts;
        this.retType= retType;
        Set<String> types = new HashSet<>();
        solver.setTimeout(100000);
        //Setting up variables for signatures
        for (MethodSignature sig : sigs){
            Met met = new Met(sig);
            sigtoint.put(met,curid);
            inttosigtest.put(curid,met);
            inttosig.put(curid,sig);
            types.addAll(met.inputs);
            types.add(met.output);
            curid += 1;
        }

        sigmax = curid;
        //Additional transitions for polymorphism
        for (String sub : subtosuper.keySet()){
            Set<String> sups = subtosuper.get(sub);
            for (String sup : sups){
                sigtoint.put(new Met(sub,sup),curid);
                types.add(sup);
                curid += 1;
            }
            types.add(sub);
        }
        for (String type : types){
            if (type != null){
                typetoint.put(type,curid);
                inttotype.put(curid,type);
                curid += 1;
            }
        }

        try {
            allConstraints(curlen);
        } catch (ContradictionException e) {
            solver = null;
        }
    }

    private void allConstraints(int len) throws ContradictionException {
        eachTypeGeneratedSomehow();
        eachMethodHaveRetType();
        eachMethodHaveInputTypes();
        finalReq();
        degreeConstr();
        lengthConstr(len);
    }


    public Set<MethodSignature> solve() throws TimeoutException {
        //Solve
        if (solver == null) return null;
        int[] satResult = null;
        if (solver.isSatisfiable()){
            satResult = solver.model();
        }
        else{
            return null;
        }
        Set<MethodSignature> result = new HashSet<>();
        List<Met> testresult = new LinkedList<>();
        VecInt block = new VecInt();
        for (Integer id : satResult) {
            if (id > 0 && id < sigmax) {
                //Block the previous solution
                block.push(-id);
                result.add(inttosig.get(id));
                testresult.add(inttosigtest.get(id));
            }
        }

        try {
            solver.addClause(block);
        } catch (ContradictionException e) {
        }
        return result;
    }

    private void eachTypeGeneratedSomehow() throws ContradictionException {
        Map<String,List<Met>> tmpmap = new HashMap<>();
        for (Met sig : sigtoint.keySet()){
            if (tmpmap.containsKey(sig.output)){
                tmpmap.get(sig.output).add(sig);
            }
            else{
                List<Met> newlist = new LinkedList<>();
                newlist.add(sig);
                tmpmap.put(sig.output,newlist);
            }
        }

        for (String type : typetoint.keySet()){
            if (!inputCounts.keySet().contains(type)){
                if (tmpmap.containsKey(type)){
                    List<Integer> boollist = new LinkedList<>();
                    for (Met sig : tmpmap.get(type)){
                        if (!sig.inputs.contains(sig.output))
                            boollist.add(sigtoint.get(sig));
                    }
                    or(boollist,curid);
                    VecInt vec = new VecInt();
                    vec.push(curid);
                    vec.push(-typetoint.get(type));
                    solver.addAtLeast(vec,1);
                    curid += 1;
                }
                else{
                    VecInt vec = new VecInt();
                    vec.push(-typetoint.get(type));
                    solver.addClause(vec);
                }
            }
            else{
                VecInt vec = new VecInt();
                vec.push(typetoint.get(type));
                solver.addClause(vec);
            }
        }
    }

    private void eachMethodHaveRetType() throws ContradictionException {
        for (Met sig : sigtoint.keySet()){
            if (sig.output != null){
                VecInt vec = new VecInt();
                vec.push(typetoint.get(sig.output));
                vec.push(-sigtoint.get(sig));
                solver.addAtLeast(vec,1);
            }
        }
    }

    private void eachMethodHaveInputTypes() throws ContradictionException {
        for (Met sig : sigtoint.keySet()){
            List<Integer> boollist = new LinkedList<>();
            for (String type : sig.inputs){
                boollist.add(typetoint.get(type));
            }
            and(boollist,curid);
            VecInt vec = new VecInt();
            vec.push(curid);
            vec.push(-sigtoint.get(sig));
            solver.addAtLeast(vec,1);
            curid += 1;
        }
    }

    private void finalReq() throws ContradictionException {
        VecInt vec = new VecInt();
        vec.push(typetoint.get(retType));
        solver.addClause(vec);
    }

    private void lengthConstr(int len) throws ContradictionException {
        VecInt vec = new VecInt();
        for (Met met : sigtoint.keySet()){
            vec.push(sigtoint.get(met));
        }
        solver.addExactly(vec,len);
    }

    private void degreeConstr() throws ContradictionException {
        Map<String,Set<Met>> retmap = new HashMap<>();
        Map<String,Set<Met>> inmap = new HashMap<>();
        for (Met sig : sigtoint.keySet()){
            if (retmap.containsKey(sig.output)){
                retmap.get(sig.output).add(sig);
            }
            else{
                Set<Met> newlist = new HashSet<>();
                newlist.add(sig);
                retmap.put(sig.output,newlist);
            }

            for (String argtype : sig.inputs){
                if (inmap.containsKey(argtype)){
                    inmap.get(argtype).add(sig);
                }
                else{
                    Set<Met> newlist = new HashSet<>();
                    newlist.add(sig);
                    inmap.put(argtype,newlist);
                }
            }
        }

        for (String type : typetoint.keySet()){
            int additional = 0;
            if (inputCounts.containsKey(type)){
                additional += inputCounts.get(type);
            }
            if (type.equals(retType)){
                additional -= 1;
            }
            VecInt vec = new VecInt();
            int indegree = 0;
            if (retmap.containsKey(type)){
                for (Met sig : retmap.get(type)){
                    vec.push(-sigtoint.get(sig));
                    indegree += 1;
                }
            }
            if (inmap.containsKey(type)) {
                for (Met sig: inmap.get(type)){
                    int typecount = 0;
                    for (String inputtype : sig.inputs){
                        if (inputtype.equals(type)) typecount += 1;
                    }
                    VecInt split = splitVar(sigtoint.get(sig),typecount);
                    vec.pushAll(split);
                }
            }
            solver.addAtLeast(vec,indegree+additional);
            solver.addAtMost(vec,indegree+additional+2);
        }
    }

    //Encoding a -> b = c
    private void implies(int a, int b, int c) throws ContradictionException {
        VecInt vec1 = new VecInt();
        vec1.push(-a);
        vec1.push(b);
        vec1.push(-c);
        solver.addAtLeast(vec1,1);
        VecInt vec2 = new VecInt();
        vec2.push(-a);
        vec2.push(-c);
        solver.addAtMost(vec2,1);
        VecInt vec3 = new VecInt();
        vec3.push(b);
        vec3.push(-c);
        solver.addAtMost(vec3,1);
    }

    //Encoding and_{x in xs}x = c
    private void and(List<Integer> xs,int c) throws ContradictionException {
        VecInt vec1 = new VecInt();
        for (int x : xs){
            vec1.push(-x);
            VecInt vecsub = new VecInt();
            vecsub.push(c);
            vecsub.push(-x);
            solver.addAtMost(vecsub,1);
        }
        vec1.push(c);
        solver.addAtLeast(vec1,1);
    }

    //Encoding or_{x in xs}x = c
    private void or(List<Integer> xs,int c) throws ContradictionException {
        VecInt vec1 = new VecInt();
        for (int x : xs){
            vec1.push(x);
            VecInt vecsub = new VecInt();
            vecsub.push(c);
            vecsub.push(-x);
            solver.addAtLeast(vecsub,1);
        }
        vec1.push(-c);
        solver.addAtLeast(vec1,1);
    }

    private void hardcodeString(String string) throws ContradictionException {
        VecInt vec = new VecInt();
        for (Met met: sigtoint.keySet()){
            if (met.name.contains(string)){
                vec.push(sigtoint.get(met));
            }
        }
        solver.addAtLeast(vec,1);
    }

    private void excludeString(String string) throws ContradictionException {
        VecInt vec = new VecInt();
        for (Met met: sigtoint.keySet()){
            if (met.name.contains(string)){
                vec.push(sigtoint.get(met));
            }
        }
        solver.addAtMost(vec,0);
    }

    private void calcDegree(List<Met> methods) {
        Map<String,Integer> incount = new HashMap<>();
        Map<String,Integer> outcount = new HashMap<>();
        for (Met met : methods){
            for (String input: met.inputs){
                if (incount.containsKey(input)) incount.put(input,incount.get(input)+1);
                else incount.put(input,1);
            }
            if (outcount.containsKey(met.output)) outcount.put(met.output,outcount.get(met.output)+1);
            else outcount.put(met.output,1);
        }
        System.out.println("outdegree:" + incount);
        System.out.println("indegree" + outcount);
    }

    private HashMap<Integer,List<Integer>> splitmap = new HashMap<>();
    private VecInt splitVar(int var, int numbersplit) throws ContradictionException {
        VecInt result = new VecInt();
        if (!splitmap.containsKey(var)){
            splitmap.put(var,new ArrayList<>());
        }
        if (splitmap.get(var).size() < numbersplit){
            while (numbersplit > splitmap.get(var).size()){
                splitmap.get(var).add(curid);
                VecInt vec = new VecInt();
                vec.push(curid);
                vec.push(-var);
                solver.addExactly(vec,1);
                curid += 1;
            }
        }
        for (int i = 0; i < numbersplit; i += 1) {
            result.push(splitmap.get(var).get(i));
        }
        return result;
    }
}
