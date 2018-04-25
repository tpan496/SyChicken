package edu.cmu.typeact;

import edu.cmu.parser.MethodSignature;
import soot.Type;
import java.util.*;


public class PathGenerator {

    private String retType;
    private Map<String, List<String>> polyMap;

    public PathGenerator(Set<MethodSignature> methodSet, Map<String, Integer> typeMap, String retType, Map<String, List<String>> polyMap) {
        this.retType = retType;
        this.polyMap = polyMap;
        //generate(methodSet, typeMap);
    }

    /**
     * Generates possible combinations of methods that will give out the wanted return type
     * @param methodSet initial method set
     * @param typeMap mapping from variable to its count
     * @return lists of viable combinations
     */
    List<List<MethodSignature>> generate(Set<MethodSignature> methodSet, Map<String, Integer> typeMap) {
        List<List<MethodSignature>> list = new LinkedList<>();
        for (MethodSignature method : methodSet) {
            // Check if the argtypes are viable
            Map<String, Integer> remainMap = fits(method.getArgTypes(), typeMap);

            if (remainMap != null) {
                if (methodSet.size() == 1){
                    if(nonPositive(remainMap)){
                        // Variable map has to be empty before return
                        if (method.getRetType().toString().equals(retType)) {
                            // Successful
                            List<MethodSignature> sgList = new LinkedList<>();
                            sgList.add(method);
                            list.add(sgList);
                            return list;
                        } else {
                            // Nothing there
                            throw new IllegalArgumentException();
                        }
                    }else{
                        // Not all variables are used, abort
                        throw new IllegalArgumentException();
                    }
                }
                Set<MethodSignature> copySet = new HashSet<>(methodSet);
                copySet.remove(method);
                try {
                    remainMap.put(method.getRetType().toString(), 1);

                    // check if method is static
                    if(method.getIsStatic()){
                        // do nothing
                    }else{
                        // add method class type to map as variable, since it is being invoked
                        remainMap.put(method.getHostClass().getType().toString(), 1);
                    }
                    list.addAll(add(method, generate(copySet, remainMap)));
                } catch (Error ignored) {}
            }
        }
        return list;
    }

    private List<List<MethodSignature>> add(MethodSignature method, List<List<MethodSignature>> lists) {
        for (List<MethodSignature> list : lists) {
            list.add(0, method);
        }
        return lists;
    }

    private Map<String, Integer> fits(List<Type> argTypes, Map<String, Integer> inMap) {
        Map<String, Integer> map = new HashMap<>(inMap);
        for (Type type : argTypes) {
            if (polyContain(type.toString(), inMap)) {
                int count = map.get(type.toString());
                // ok, decrement count
                map.put(type.toString(), count - 1);
            }else{
                return null;
            }
        }
        return map;
    }

    private boolean nonPositive(Map<String, Integer> map){
        for(int v : map.values()){
            if(v>0){
                return false;
            }
        }
        return true;
    }

    // Checks if b is super class of a
    private boolean isSuper(String b, String a){
        for(String typeA : polyMap.get(a)){
            if(typeA.equals(b)){
                return true;
            }
        }
        return false;
    }

    // If required argument input types are superclasses, then it should also work
    private boolean polyContain(String type, Map<String ,Integer> inMap){
        for(String key : inMap.keySet()){
            if(isSuper(key, type) || key.equals(type)){
                return true;
            }
        }
        return false;
    }
}
