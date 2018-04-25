package edu.cmu.typeact;

import edu.cmu.parser.MethodSignature;
import soot.JastAddJ.Signatures;
import soot.Type;

import java.util.*;


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
        //System.out.println("want type: "+retType);
        //System.out.println("poly map: " + polyMap);
        int totalCount = methodSet.size();
        List<List<MethodSignature>> lists = generateHelper(methodSet, typeMap, 0);
        List<List<MethodSignature>> emptyList = new LinkedList<>();
        //System.out.println("==== results ====");
        for(List list : lists){
            if(list.size() == totalCount){
                //System.out.println(list);
                emptyList.add(list);
            }
        }
        //System.out.println("==== end ===");
        return emptyList;
    }

    private List<List<MethodSignature>> generateHelper(Set<MethodSignature> methodSet, Map<String, Integer> typeMap, int level){
        List<List<MethodSignature>> list = new LinkedList<>();
        for (MethodSignature method : methodSet) {
            // Check if the argtypes are viable
            Map<String, Integer> remainMap = fits(method, typeMap);

            /*if(level == 0) {
                System.out.println("level: " + level);
                System.out.println("current methodSet: " + methodSet);
                System.out.println("current method: " + method);
                System.out.println("current typeMap: " + typeMap);
                System.out.println("current remainMap: " + remainMap);
                System.out.println("===============================");
            }*/

            if (remainMap != null) {
                if (methodSet.size() == 1) {
                    if (nonPositive(remainMap)) {
                        // Variable map has to be empty before return
                        if (method.getRetType().toString().equals(retType) || typeMap.containsKey(retType) && typeMap.get(retType) > 0) {
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

                // check if method is static
                if (method.getIsStatic()) {
                    // do nothing
                } else if(!method.getRetType().toString().equals("void")){

                    // add method return type to map as variable, since it is being created
                    String type = method.getRetType().toString();
                    if(remainMap.containsKey(type)){
                        if(remainMap.get(type) > 0) {
                            remainMap.put(type, remainMap.get(type) + 1);
                        }else{
                            remainMap.put(type, 1);
                        }
                    }else {
                        remainMap.put(type, 1);
                    }
                }
                list.addAll(add(method, generateHelper(copySet, remainMap, level+1)));
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
        for (Type type : argTypes) {
            if (polyContain(type.toString(), inMap)) {
                int count = map.get(type.toString());
                // ok, decrement count
                map.put(type.toString(), count - 1);
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
    private boolean polyContain(String type, Map<String, Integer> inMap) {
        for (String key : inMap.keySet()) {
            if (isSuper(key, type) || key.equals(type)) {
                return true;
            }
        }
        return false;
    }
}
