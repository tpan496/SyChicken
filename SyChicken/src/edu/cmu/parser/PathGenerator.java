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
                if (methodSet.size() == 1) {
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
                }
                Set<MethodSignature> copySet = new HashSet<>(methodSet);
                copySet.remove(method);
                try {
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
                if (count > 0) {
                    // ok, decrement count
                    map.put(type, count - 1);
                } else {
                    return null;
                }
            }
        }
        return map;
    }
}
