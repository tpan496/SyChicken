import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.awt.geom.Rectangle2D;

public class Solution {

    public java.awt.geom.Rectangle2D scale(java.awt.geom.Rectangle2D sypet_arg0, double sypet_arg1, double sypet_arg2) throws Throwable{
        java.awt.geom.AffineTransform var_0 = java.awt.geom.AffineTransform.getScaleInstance(sypet_arg1,sypet_arg2);
        java.awt.Shape var_1 = var_0.createTransformedShape(sypet_arg0);
        java.awt.geom.Rectangle2D var_2 = var_1.getBounds2D();
        return var_2;
    }

}
