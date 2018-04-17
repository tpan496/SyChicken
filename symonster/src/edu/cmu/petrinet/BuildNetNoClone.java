package edu.cmu.petrinet;

import edu.cmu.parser.MethodSignature;
import soot.Type;

import uniol.apt.adt.exception.NoSuchEdgeException;
import uniol.apt.adt.exception.NoSuchNodeException;
import uniol.apt.adt.pn.Flow;
import uniol.apt.adt.pn.PetriNet;
import uniol.apt.adt.pn.Place;
import uniol.apt.adt.pn.Transition;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.HashMap;
import java.util.Map;
import java.util.Stack;

/*
   An reimplementation of BuildNet, but eliminates all clone edges by creating
   a copy for each method that returns its own arguments.
 */
public class BuildNetNoClone{
    static public PetriNet petrinet;
    //map from transition name to a method signature
    static public Map<String, MethodSignature> dict;

    static private Map<String, List<String>> superDict;
    static private Map<String, List<String>> subDict;

    public BuildNetNoClone() {
        petrinet = new PetriNet("net");
        dict = new HashMap<String, MethodSignature>();
        superDict = new HashMap<>();
        subDict = new HashMap<>();
    }

    private static void handlePolymorphism() {
        // This method handles polymorphism by creating methods that transforms each
        // subclass into its super class
        for(String subClass : superDict.keySet()) {
            try {
                petrinet.getPlace(subClass.toString());
            } catch (NoSuchNodeException e ) {
                petrinet.createPlace(subClass.toString());
            }
            assert (petrinet.containsNode(subClass));
            for (String superClass : superDict.get(subClass)) {
                // If the class is not in the petrinet, create the class
                try {
                    petrinet.getPlace(superClass.toString());
                } catch (NoSuchNodeException e) {
                    petrinet.createPlace(superClass.toString());
                }
                assert (petrinet.containsNode(superClass));

                String methodName = subClass + "IsPolymorphicTo" + superClass;
                petrinet.createTransition(methodName);
                petrinet.createFlow(subClass, methodName);
                petrinet.createFlow(methodName, superClass);
            }
        }
    }

    /*
      Post process all transitions in the petrinet so that
      No transition return a single void
     */
    private static void postProcess() {
        Set<Transition> ts = petrinet.getTransitions();
        for(Transition t : ts) {
            List<Flow> outEdges = new ArrayList<Flow>(t.getPostsetEdges());
            List<Flow> inEdges = new ArrayList<Flow>(t.getPresetEdges());
            try {
                Flow f = petrinet.getFlow(t.getId(), "void");
                System.out.println("contain void!");
                assert(f.getWeight() == 1);
                //if (outEdges.size() == 1) { // Delete the transition.
                    f.setWeight(0);
                //}
            } catch (NoSuchEdgeException e) {}
        }
    }

    private static void generatePolymophism(Transition t,
                                            int count,
                                            Stack<Place> inputs,
                                            Stack<Place> trueInputs) {
        System.out.println(trueInputs.size() + "  " + count);
        if(inputs.size() == count) {
            // skip if true inputs is same as inputs
            boolean skip = true;
            for(int i = 0; i < inputs.size(); i++) {
                if(!inputs.get(i).equals(trueInputs.get(i))) {
                    skip = false;
                }
            }
            if(skip) {
                return;
            }

            String newTransitionName = t.getId() + "poly:(";
            for(Place p : trueInputs) {
                newTransitionName += p.getId() + " ";
            }
            newTransitionName += ")";

            if(petrinet.containsTransition(newTransitionName)) {
                return;
            }
            Transition newTransition  = petrinet.createTransition(newTransitionName);
            // Add inputs
            System.out.println(inputs.size() + " " + trueInputs.size());
            for(Place p : trueInputs) {
                try {
                    Flow f = petrinet.getFlow(p, newTransition);
                    f.setWeight(f.getWeight() + 1);
                } catch (NoSuchEdgeException e) {
                    petrinet.createFlow(p, newTransition, 1);
                }
            }

            // Add outputs but should changes as well....
            for(Flow f : t.getPostsetEdges()) {
                Place p = f.getPlace();
                int w = f.getWeight();
                petrinet.createFlow(newTransition, p, w);
            }

            dict.put(newTransitionName, dict.get(t.getId()));
        } else {
            Place p = inputs.get(count);
            List<String> subClasses = subDict.get(p.getId());
            if(subClasses == null) { // No possible polymophism
                trueInputs.push(p);
                generatePolymophism(t, count+1, inputs, trueInputs);
                return;
            } else {
                for(String subclass : subClasses) {
                    Place polyClass;
                    try {
                        polyClass = petrinet.getPlace(subclass);
                    } catch (NoSuchNodeException e) {
                        polyClass = petrinet.createPlace(subclass);
                    }
                    assert(petrinet.getPlace(subclass) != null);
                    trueInputs.push(polyClass);
                    generatePolymophism(t, count+1, inputs, trueInputs);
                    trueInputs.pop();
                }
                return;
            }
        }
    }
    private static void handlePolymorphismAlt() {
        // Handles polymorphism by creating copies for each method that
        // has superclass as input type
        for(Transition t : petrinet.getTransitions()) {
            Stack<Place> inputs = new Stack<>();
            Set<Flow> inEdges = t.getPresetEdges();
            for(Flow f : inEdges) {
                for(int i = 0; i < f.getWeight(); i++) {
                    inputs.push(f.getPlace());
                }
            }
            Stack<Place> trueInputs = new Stack<>();
            generatePolymophism(t, 0, inputs, trueInputs);
        }
    }

    private static void generateCopies(Transition t, List<Place> inputs, List<Place> outputs) {
        if(inputs.size() == 0) {
            if(outputs.size() == 0) {
                return;
            }
            String newTransitionName = t.getId()+ "(";
            for(Place p : outputs) {
                newTransitionName += p.getId() + " ";
            }
            newTransitionName += ")";

            if(petrinet.containsTransition(newTransitionName)) {
                return;
            }
            Transition newTransition  = petrinet.createTransition(newTransitionName);
            for(Flow f : t.getPresetEdges()) {
                Place p = f.getPlace();
                int w = f.getWeight();
                petrinet.createFlow(p, newTransition, w);
            }
            for(Flow f : t.getPostsetEdges()) {
                Place p = f.getPlace();
                int w = f.getWeight();
                petrinet.createFlow(newTransition, p, w);
            }
            for(Place p : outputs) {
                try {
                    Flow f = petrinet.getFlow(newTransition, p);
                    f.setWeight(f.getWeight() + 1);
                } catch (NoSuchEdgeException e) {
                    petrinet.createFlow(newTransition, p, 1);
                }
            }

            dict.put(newTransitionName, dict.get(t.getId()));
        } else {
            Place first = inputs.remove(0);
            generateCopies(t, inputs, outputs);
            outputs.add(first);
            generateCopies(t, inputs, outputs);
            inputs.add(first);
            outputs.remove(first);
        }
    }

    private static void createCopies(Transition t) {
        Set<Flow> inputEdges = petrinet.getPresetEdges(t);
        List<Place> inputs = new ArrayList<>();
        for(Flow e : inputEdges) {
            for(int i = 0 ; i < e.getWeight(); i++) {
                inputs.add(e.getPlace());
            }
        }
        List<Place> outputs = new ArrayList<>();
        generateCopies(t, inputs, outputs);
    }

    public static PetriNet build(List<MethodSignature> result,
                                 Map<String, Set<String>> superClassMap,
                                 Map<String, Set<String>> subClassMap,
                                 List<String> inputs)  throws java.io.IOException{
        // Create polymorphism dicts
        for(String s : superClassMap.keySet()) {
            Set<String> superClasses = superClassMap.get(s);
            List<String> l = new ArrayList<>(superClasses);
            if(l.size() != 0) {
                superDict.put(s, l);
            }
        }
        for(String s : subClassMap.keySet()) {
            Set<String> subClasses = subClassMap.get(s);
            List<String> l = new ArrayList<>(subClasses);
            if(l.size() != 0) {
                subDict.put(s, l);
            }
        }

        //create void type
        petrinet.createPlace("void");

        //iterate through each method

        for (MethodSignature k : result) {
            String methodname = k.getName();
            boolean isStatic = k.getIsStatic();
            boolean isConstructor = k.getIsConstructor();
            String className = k.getHostClass().getName();
            //We create two transitions for each method
            //transitionCopy will have its input as its additional output
            String transitionName;
            List<Type> args = k.getArgTypes();

            //adding transition
            if (isConstructor) {
                transitionName = methodname + "(";
                for(Type t : args) {
                    transitionName += t.toString() + " ";
                }
                transitionName += ")";
                petrinet.createTransition(transitionName);
            } else if (isStatic) {
                transitionName = className + "." + methodname + "(";
                for(Type t : args) {
                    transitionName += t.toString() + " ";
                }
                transitionName += ")";
                petrinet.createTransition(transitionName);
            } else { //The method is not static, so it has an extra argument
                transitionName = "(static)" + className + "." + methodname + "(";
                transitionName += k.getHostClass().toString() + " ";
                for(Type t : args) {
                    transitionName += t.toString() + " ";
                }
                transitionName += ")";
                petrinet.createTransition(transitionName);

                //creating the place for the class instance
                try {
                    petrinet.getPlace(k.getHostClass().toString());
                } catch (NoSuchNodeException e) {
                    petrinet.createPlace(k.getHostClass().toString());
                }

                //creating flow from class instance to transition
                try {
                    Flow f = petrinet.getFlow(k.getHostClass().toString(), transitionName);
                    f.setWeight(f.getWeight() + 1);
                } catch (NoSuchEdgeException e) {
                    petrinet.createFlow(k.getHostClass().toString(), transitionName);
                }

            }
            //add method signatures into map
            dict.put(transitionName, k);

            //If method has no argument and is static , create flow with void
            if(args.size() == 0 && (isStatic || isConstructor)) {
                petrinet.createFlow("void", transitionName);
            }

            for (Type t : args) {
                //add place for each argument
                try {
                    petrinet.getPlace(t.toString());
                } catch (NoSuchNodeException e) {
                    petrinet.createPlace(t.toString());
                }

                //add flow for each argument
                try {
                    Flow originalFlow = petrinet.getFlow(t.toString(), transitionName);
                    originalFlow.setWeight(originalFlow.getWeight() + 1);
                } catch (NoSuchEdgeException e) {
                    petrinet.createFlow(t.toString(), transitionName, 1);
                }

            }

            //add place for the return type
            Type retType = k.getRetType();

            try {
                petrinet.getPlace(retType.toString());
            } catch (NoSuchNodeException e) {
                petrinet.createPlace(retType.toString());
            }

            //add flows for the return type
            petrinet.createFlow(transitionName, retType.toString(), 1);

        }

        handlePolymorphismAlt();
        // TODO check the logic here
        for(Transition t : petrinet.getTransitions()) {
            System.out.println(t.getId());
            createCopies(t);
        }
        //handlePolymorphism();


        //Set max tokens for each place
        for (Place p : petrinet.getPlaces()) {
            int count = 0;
            for (Transition t : petrinet.getTransitions()) {
                try {
                    Flow f = petrinet.getFlow(p.getId(), t.getId());
                    count = Math.max(count, f.getWeight());
                } catch (NoSuchEdgeException e) {
                    continue;
                }
            }
            if(count != 0) {
                p.setMaxToken(count);
            } else {
                p.setMaxToken(1);
            }
            if(p.getId() == "void") {
                assert(p.getMaxToken() == 1);
            }
        }
        // Update the maxtoken for inputs
        HashMap<Place, Integer> count = new HashMap<Place, Integer>();
        for (String input : inputs) {
            Place p;
            p = petrinet.getPlace(input);
            if (count.containsKey(p)) {
                count.put(p, count.get(p) + 1);
            } else {
                count.put(p, 1);
            }
        }
        for(Place p : count.keySet()) {
             if(count.get(p) > p.getMaxToken()) {
                p.setMaxToken(count.get(p));
            }
        }

        Visualization.translate(petrinet);
        return petrinet;
    }
}
