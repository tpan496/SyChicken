package edu.cmu.typeact;

import edu.cmu.parser.MethodSignature;
import soot.JastAddJ.Signatures;
import soot.Type;

import java.awt.*;
import java.util.*;
import java.util.List;


public class PathGenerator {

    private String retType;
    private Map<String, Set<String>> polyMap;

    public PathGenerator(String retType, Map<String, Set<String>> polyMap) {
        this.retType = retType;
        this.polyMap = polyMap;
    }

    /**
     * Generates possible combinations of methods that will give out the wanted return type
     *
     * @param methodSet initial method set
     * @param typeMap   mapping from variable to its count
     * @return lists of viable combinations
     */
    public List<List<MethodSignature>> generate(Set<MethodSignature> methodSet, Map<String, Integer> typeMap) {
        int totalCount = methodSet.size();
        List<List<MethodSignature>> lists = generateHelper(methodSet, typeMap, 0);
        List<List<MethodSignature>> emptyList = new LinkedList<>();
        for(List list : lists){
            if(list.size() == totalCount){
                emptyList.add(list);
            }
        }
        return emptyList;
    }

    private List<List<MethodSignature>> generateHelper(Set<MethodSignature> methodSet, Map<String, Integer> typeMap, int level){
        List<List<MethodSignature>> list = new LinkedList<>();
        for (MethodSignature method : methodSet) {
            // Check if the argtypes are viable
            Map<String, Integer> remainMap = fits(method, typeMap);
    /*
                System.out.println("=="+"level: "+level+"=======");
                System.out.println("method: "+method);
                System.out.println("in map: "+typeMap);
                System.out.println("out map: "+remainMap);
                System.out.println("===================");*/


            if (remainMap != null) {
                if (methodSet.size() == 1) {
                    if (nonPositive(remainMap)) {
                        // Variable map has to be empty before return
                        if (method.getRetType().toString().equals(retType) || isSuper(retType, method.getRetType().toString()) || polyContain(retType, typeMap)!=null) {
                            // Successful
                            List<MethodSignature> sgList = new LinkedList<>();
                            sgList.add(method);
                            list.add(sgList);
                            return list;
                        } else {
                            // Nothing there
                            list.add(new LinkedList<>());
                            return list;
                        }
                    } else {
                        // Nothing there
                        list.add(new LinkedList<>());
                        return list;
                    }
                }
                Set<MethodSignature> copySet = new HashSet<>(methodSet);
                copySet.remove(method);

                Map<String, Integer> copyRemainMap = new HashMap<>(remainMap);

                // add return type
                if(!method.getRetType().toString().equals("void")){

                    // add method return type to map as variable, since it is being created
                    String type = method.getRetType().toString();
                    if(copyRemainMap.containsKey(type)){
                        if(copyRemainMap.get(type) > 0) {
                            copyRemainMap.put(type, copyRemainMap.get(type) + 1);
                        }else{
                            copyRemainMap.put(type, 1);
                        }
                    }else {
                        copyRemainMap.put(type, 1);
                    }
                }
                list.addAll(add(method, generateHelper(copySet, copyRemainMap, level+1)));
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

    private Map<String, Integer> fits(MethodSignature method, Map<String, Integer> inMap) {
        List<Type> argTypes = new ArrayList<>(method.getArgTypes());
        if(!method.getIsStatic() && !method.getIsConstructor()){
            argTypes.add(method.getHostClass().getType());
        }
        Map<String, Integer> map = new HashMap<>(inMap);
        //System.out.println("want: "+argTypes);
        for (Type type : argTypes) {
            String res = polyContain(type.toString(), inMap);
            if (res != null) {
                int count = map.get(res);
                // ok, decrement count
                map.put(res, count - 1);
            } else {
                return null;
            }
        }
        return map;
    }

    private boolean nonPositive(Map<String, Integer> map) {
        for (int v : map.values()) {
            if (v > 0) {
                return false;
            }
        }
        return true;
    }

    // Checks if b is super class of a
    private boolean isSuper(String b, String a) {
        if (polyMap.get(a) == null) return false;
        return polyMap.get(a).contains(b);
    }

    // If required argument input types are superclasses, then it should also work
    private String polyContain(String type, Map<String, Integer> inMap) {
        for (String key : inMap.keySet()) {
            //System.out.println(key+","+type);
            if (isSuper(type,key) || key.equals(type)) {
                //System.out.println("equals");
                return key;
            }
            //System.out.println("not equals");
        }
        return null;
    }
}
