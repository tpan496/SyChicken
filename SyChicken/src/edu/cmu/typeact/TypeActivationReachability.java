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
    private int curid = 1;
    private final Map<String,Integer> inputCounts;
    private final String retType;
    private int curlen = 1;
    private int sigmax;
    public TypeActivationReachability(List<MethodSignature> sigs, Map<String,Integer> inputCounts,String retType,Map<String,Set<String>> subtosuper) throws TimeoutException {
        this.inputCounts = inputCounts;
        this.retType= retType;
        Set<String> types = new HashSet<>();
        solver.setTimeout(100000);
        //Setting up variables for signatures
        for (MethodSignature sig : sigs){
            Met met = new Met(sig);
            sigtoint.put(met,curid);
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
        int[] satResult = null;
        if (solver.isSatisfiable()){
            satResult = solver.model();
        }
        else{
            //Increasing path length
            curlen += 1;
            if (curlen > sigtoint.size()) return null;
            System.out.println("Increasing length to "+curlen);
            try {
                solver = SolverFactory.newDefault();
                allConstraints(curlen);
            } catch (ContradictionException e) {
            }
            return null;
        }
        Set<MethodSignature> result = new HashSet<>();
        VecInt block = new VecInt();
        for (Integer id : satResult){
            if (id > 0 && id < sigmax) {
                //Block the previous solution
                block.push(-id);
                result.add(inttosig.get(id));
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
                        boollist.add(sigtoint.get(sig));
                    }
                    or(boollist,typetoint.get(type));
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
        for (int val : sigtoint.values()){
            vec.push(val);
        }
        solver.addExactly(vec,len);
    }

    private void degreeConstr() throws ContradictionException {
        Map<String,List<Met>> retmap = new HashMap<>();
        Map<String,List<Met>> inmap = new HashMap<>();
        for (Met sig : sigtoint.keySet()){
            if (retmap.containsKey(sig.output)){
                retmap.get(sig.output).add(sig);
            }
            else{
                List<Met> newlist = new LinkedList<>();
                newlist.add(sig);
                retmap.put(sig.output,newlist);
            }

            for (String argtype : sig.inputs){
                if (inmap.containsKey(argtype)){
                    inmap.get(argtype).add(sig);
                }
                else{
                    List<Met> newlist = new LinkedList<>();
                    newlist.add(sig);
                    inmap.put(argtype,newlist);
                }
            }
        }

        for (String type : typetoint.keySet()){
            int additional = 0;
            if (type.equals("void")){
                continue;
            }
            else if (inputCounts.containsKey(type)){
                additional = inputCounts.get(type);
            }
            else if (type.equals(retType)){
                additional = -1;
            }
            VecInt vec = new VecInt();
            int indegree = 0;
            if (retmap.containsKey(type)){
                indegree = retmap.get(type).size();
                for (Met sig : retmap.get(type)){
                    vec.push(-sigtoint.get(sig));
                }
            }
            if (inmap.containsKey(type)) {
                for (Met sig: inmap.get(type)){
                    vec.push(sigtoint.get(sig));
                }
            }
            solver.addAtLeast(vec,indegree+additional);
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
            vec1.push(x);
            VecInt vecsub = new VecInt();
            vecsub.push(c);
            vecsub.push(-x);
            solver.addAtMost(vecsub,1);
        }
        vec1.push(-c);
        solver.addAtMost(vec1,2);
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
}
