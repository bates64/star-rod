package util.ui;

import java.awt.geom.Path2D;

public abstract class Squircle
{
	public static Path2D.Double path(double x, double y, double w, double h, double arc)
	{
		var path = new Path2D.Double();

		path.moveTo(x + w - arc, y);
		path.lineTo(x + arc, y);
		path.quadTo(x, y, x, y + arc);
		path.lineTo(x, y + h - arc);
		path.quadTo(x, y + h, x + arc, y + h);
		path.lineTo(x + w - arc, y + h);
		path.quadTo(x + w, y + h, x + w, y + h - arc);
		path.lineTo(x + w, y + arc);
		path.quadTo(x + w, y, x + w - arc, y);
		path.closePath();

		return path;
	}

	public static Path2D.Double path(double x, double y, double w, double h, double topLeft, double topRight, double bottomRight, double bottomLeft)
	{
		var path = new Path2D.Double();

		// Start at top edge after top-right corner
		path.moveTo(x + w - topRight, y);
		// Top edge to top-left corner
		if (topLeft > 0) {
			path.lineTo(x + topLeft, y);
			path.quadTo(x, y, x, y + topLeft);
		}
		else {
			path.lineTo(x, y);
		}
		// Left edge
		if (bottomLeft > 0) {
			path.lineTo(x, y + h - bottomLeft);
			path.quadTo(x, y + h, x + bottomLeft, y + h);
		}
		else {
			path.lineTo(x, y + h);
		}
		// Bottom edge
		if (bottomRight > 0) {
			path.lineTo(x + w - bottomRight, y + h);
			path.quadTo(x + w, y + h, x + w, y + h - bottomRight);
		}
		else {
			path.lineTo(x + w, y + h);
		}
		// Right edge
		if (topRight > 0) {
			path.lineTo(x + w, y + topRight);
			path.quadTo(x + w, y, x + w - topRight, y);
		}
		else {
			path.lineTo(x + w, y);
		}
		path.closePath();

		return path;
	}
}
