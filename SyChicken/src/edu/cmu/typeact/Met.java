package edu.cmu.typeact;

import edu.cmu.parser.MethodSignature;
import soot.Type;
import java.util.*;
public class Met {
    public List<String> inputs = new ArrayList<>();
    public String output = "";
    protected Met(MethodSignature met){
        if (met.getIsConstructor()){
            for (Type type : met.getArgTypes()){
                inputs.add(type.toString());
            }
            output = met.getHostClass().getType().toString();
        }
        else{
            for (Type type : met.getArgTypes()){
                inputs.add(type.toString());
            }
            if (!met.getIsStatic()) inputs.add(met.getHostClass().getType().toString());
            if (met.getRetType().toString().equals("void")){
                output = met.getHostClass().getType().toString();
            }
            else{
                output = met.getRetType().toString();
            }
        }
        if (inputs.contains(output)){output = null;}
    }

    protected Met(String input1, String input2, String output){
        inputs.add(input1);
        inputs.add(input2);
        this.output = output;
    }
}
