public boolean test() throws Throwable {
	java.awt.geom.Rectangle2D rec = new java.awt.geom.Rectangle2D.Double(10, 20, 10, 2);
	java.awt.geom.Rectangle2D target = new java.awt.geom.Rectangle2D.Double(20, 60, 20, 6);
	java.awt.geom.Rectangle2D result = scale(rec, 2, 3);
	return (target.equals(result));
}
