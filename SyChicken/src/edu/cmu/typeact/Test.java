package edu.cmu.typeact;

import edu.cmu.parser.MethodSignature;
import org.sat4j.specs.TimeoutException;

import java.util.*;

public class Test {
    public static void test1() throws TimeoutException {
        Met met1 = new Met("a","b","c");
        Map<String,Integer> inputs = new HashMap();
        inputs.put("a",1);
        inputs.put("b",1);
        String output = "c";
        List<Met> mets = new ArrayList<>();
        mets.add(met1);
        TypeActivationReachability tar = new TypeActivationReachability(mets,inputs,output,true);
        System.out.println(tar.solve());
    }

    public static void main(String[] args) throws TimeoutException {
        test1();
    }
}
