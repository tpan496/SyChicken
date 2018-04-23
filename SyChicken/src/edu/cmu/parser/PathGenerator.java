package edu.cmu.parser;

import soot.Type;
import java.util.*;

public class PathGenerator {

    private Type retType;

    public PathGenerator(Map<Type, Integer> typeMap, Type retType, Set<MethodSignature> methodSet) {
        this.retType = retType;
    }

    List<List<MethodSignature>> generate(Set<MethodSignature> methodSet, Map<Type, Integer> typeMap) {
        List<List<MethodSignature>> list = new ArrayList<>();
        for (MethodSignature method : methodSet) {
            // Check if the argtypes are viable
            Map<Type, Integer> remainMap = fits(method.getArgTypes(), typeMap);

            if (remainMap != null) {
                if (methodSet.size() == 1){
                    if(nonPositive(remainMap)){
                        // Variable map has to be empty before return
                        if (method.getRetType().equals(retType)) {
                            // Successful
                            List<MethodSignature> sgList = new ArrayList<>();
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
                    remainMap.put(method.getRetType(), 1);
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

    private Map<Type, Integer> fits(List<Type> argTypes, Map<Type, Integer> inMap) {
        Map<Type, Integer> map = new HashMap<>(inMap);
        for (Type type : argTypes) {
            if (map.containsKey(type)) {
                int count = map.get(type);
                // ok, decrement count
                map.put(type, count - 1);
            }else{
                return null;
            }
        }
        return map;
    }

    private boolean nonPositive(Map<Type, Integer> map){
        for(int v : map.values()){
            if(v>0){
                return false;
            }
        }
        return true;
    }
}
