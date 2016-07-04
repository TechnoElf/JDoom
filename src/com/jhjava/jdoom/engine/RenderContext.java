package com.jhjava.jdoom.engine;

import java.util.ArrayList;

public class RenderContext extends Bitmap {
	private float[] depthBuffer;

	public RenderContext(int width, int height) {
		super(width, height);
		depthBuffer = new float[width * height];
	}

	public void clearDepthBuffer() {
		for (int i = 0; i < depthBuffer.length; i++) {
			depthBuffer[i] = Float.MAX_VALUE;
		}
	}

	private void clipPolygonComponent(ArrayList<Vertex> vertices, int componentIndex, float componentFactor, ArrayList<Vertex> result) {
		Vertex previousVertex = vertices.get(vertices.size() - 1);
		float previousComponent = previousVertex.get(componentIndex) * componentFactor;
		boolean previousInside = previousComponent <= previousVertex.getPos().getW();

		for (Vertex currentVertex : vertices) {
			float currentComponent = currentVertex.get(componentIndex) * componentFactor;
			boolean currentInside = currentComponent <= currentVertex.getPos().getW();

			if(currentInside ^ previousInside) {
				float lerpAmt = (previousVertex.getPos().getW() - previousComponent) /
						((previousVertex.getPos().getW() - previousComponent) -
						(currentVertex.getPos().getW() - currentComponent));

				result.add(previousVertex.lerp(currentVertex, lerpAmt));
			}

			if(currentInside) {
				result.add(currentVertex);
			}

			previousVertex = currentVertex;
			previousComponent = currentComponent;
			previousInside = currentInside;
		}
	}

	public void drawMesh(Mesh mesh, Matrix4f transform, Bitmap texture) {
		for (int i = 0; i < mesh.getNumIndices(); i += 3) {
			fillTriangle(mesh.getVertex(mesh.getIndex(i)).transform(transform),
					mesh.getVertex(mesh.getIndex(i + 1)).transform(transform),
					mesh.getVertex(mesh.getIndex(i + 2)).transform(transform),
					texture);
		}
	}

	public void fillTriangle(Vertex v1, Vertex v2, Vertex v3, Bitmap texture) {
		Matrix4f screenSpaceTransform = new Matrix4f().initScreenSpaceTransform(getWidth() / 2, getHeight() / 2);
		Vertex minYVert = v1.transform(screenSpaceTransform).perspectiveDivide();
		Vertex midYVert = v2.transform(screenSpaceTransform).perspectiveDivide();
		Vertex maxYVert = v3.transform(screenSpaceTransform).perspectiveDivide();

		if(minYVert.triangleAreaTimesTwo(maxYVert, midYVert) >= 0) {
			return;
		}

		if(maxYVert.getY() < midYVert.getY()) {
			Vertex temp = maxYVert;
			maxYVert = midYVert;
			midYVert = temp;
		}
		if(midYVert.getY() < minYVert.getY()) {
			Vertex temp = midYVert;
			midYVert = minYVert;
			minYVert = temp;
		}
		if(maxYVert.getY() < midYVert.getY()) {
			Vertex temp = maxYVert;
			maxYVert = midYVert;
			midYVert = temp;
		}

		scanTriangle(minYVert, midYVert, maxYVert, minYVert.triangleAreaTimesTwo(maxYVert, midYVert) >= 0, texture);
	}

	public void scanTriangle(Vertex minYVert, Vertex midYVert, Vertex maxYVert, boolean handedness, Bitmap texture) {
		Gradients gradients = new Gradients(minYVert, midYVert, maxYVert);

		Edge topToBottom = new Edge(gradients, minYVert, maxYVert, 0);
		Edge topToMiddle = new Edge(gradients, minYVert, midYVert, 0);
		Edge middleToBottom = new Edge(gradients, midYVert, maxYVert, 1);

		scanEdges(topToBottom, topToMiddle, handedness, texture);
		scanEdges(topToBottom, middleToBottom, handedness, texture);
	}

	private void scanEdges(Edge a, Edge b, boolean handedness, Bitmap texture) {
		Edge left = a;
		Edge right = b;
		if(handedness) {
			Edge temp = left;
			left = right;
			right = temp;
		}

		int yStart = b.getYStart();
		int yEnd = b.getYEnd();
		for (int j = yStart; j < yEnd; j++) {
			drawScanLine(left, right, j, texture);
			left.step();
			right.step();
		}
	}

	private void drawScanLine(Edge left, Edge right, int j, Bitmap texture) {
		int xMin = (int) Math.ceil(left.getX());
		int xMax = (int) Math.ceil(right.getX());
		float xPrestep = xMin - left.getX();

		float xDist = right.getX() - left.getX();

		float texCoordXXStep = (right.getTexCoordX() - left.getTexCoordX()) / xDist;
		float texCoordYXStep = (right.getTexCoordY() - left.getTexCoordY()) / xDist;
		float oneOverZXStep = (right.getOneOverZ() - left.getOneOverZ()) / xDist;
		float depthXStep = (right.getDepth() - left.getDepth()) / xDist;

		float texCoordX = left.getTexCoordX() + texCoordXXStep * xPrestep;
		float texCoordY = left.getTexCoordY() + texCoordYXStep * xPrestep;
		float oneOverZ = left.getOneOverZ() + oneOverZXStep * xPrestep;
		float depth = left.getDepth() + depthXStep * xPrestep;

		for (int i = xMin; i < xMax; i++) {
			int index = i + j * getWidth();
			if(depth < depthBuffer[index]) {
				depthBuffer[index] = depth;
				float z = 1.0f / oneOverZ;
				int srcX = (int) ((texCoordX * z) * (texture.getWidth() - 1) + 0.5f);
				int srcY = (int) ((texCoordY * z) * (texture.getHeight() - 1) + 0.5f);

				copyPixel(i, j, srcX, srcY, texture);
			}

			texCoordX += texCoordXXStep;
			texCoordY += texCoordYXStep;
			oneOverZ += oneOverZXStep;
			depth += depthXStep;
		}
	}
}
