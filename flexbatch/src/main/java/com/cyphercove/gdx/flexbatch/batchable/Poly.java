
package com.cyphercove.gdx.flexbatch.batchable;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.VertexAttribute;
import com.badlogic.gdx.graphics.g2d.PolygonRegion;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.NumberUtils;
import com.badlogic.gdx.utils.Pool;
import com.badlogic.gdx.utils.Pool.Poolable;
import com.cyphercove.gdx.flexbatch.Batchable;
import com.cyphercove.gdx.flexbatch.utils.AttributeOffsets;
import com.cyphercove.gdx.flexbatch.utils.BatchablePreparation;
import com.cyphercove.gdx.flexbatch.utils.RenderContextAccumulator;

/** A Batchable supporting a single {@link PolygonRegion}, with color, position, scale, and an origin offset.
 * 
 * @author cypherdare */
public abstract class Poly extends Batchable implements Poolable {
	protected PolygonRegion region;
	protected int numVertices, numIndices;
	public float x, y, color = WHITE, originX, originY, scaleX = 1, scaleY = 1;
	/** Width and height must be set with {@link #size(float, float)}. If they are not set, they default to the size of the first
	 * texture region. */
	protected float width, height;
	/** Whether the width and height have been set since the last call to {@link #refresh()}. If they have not been set, they will
	 * be set to match the size of the polygon region when drawing occurs. */
	protected boolean sizeSet;
	protected static final float WHITE = Color.WHITE.toFloatBits();

	protected void addVertexAttributes (Array<VertexAttribute> attributes) {
		BatchablePreparation.addBaseAttributes(attributes, getNumberOfTextures(), isPosition3D(), isTextureCoordinate3D());
	}

	protected final int getNumberOfTextures () {
		return 1;
	}

	/** Determines whether the position data has a Z component. Must return the same constant value for every instance of the
	 * class.
	 * <p>
	 * Overriding this method will produce a subclass that is incompatible with a FlexBatch that was instantiated for the
	 * superclass type. */
	protected abstract boolean isPosition3D ();

	/** Determines whether the texture coordinate data has a third component (for TextureArray layers). Must return the same
	 * constant value for every instance of the class.
	 * <p>
	 * Overriding this method will produce a subclass that is incompatible with a FlexBatch that was instantiated for the
	 * superclass type. */
	protected abstract boolean isTextureCoordinate3D ();

	protected boolean prepareContext (RenderContextAccumulator renderContext, int remainingVertices, int remainingIndices) {
		boolean textureChanged = false;
		if (region != null) textureChanged |= renderContext.setTextureUnit(region.getRegion().getTexture(), 0);

		return textureChanged || remainingVertices < numVertices || remainingIndices < numIndices;
	}

	public void refresh () { // Does not reset textures, in the interest of speed. There is no need for the concept of default
										// textures.
		x = y = originX = originY = 0;
		scaleX = scaleY = 1;
		color = WHITE;
		sizeSet = false;
	}

	/** Resets the state of the object and drops Texture references to prepare it for returning to a {@link Pool}. */
	public void reset () {
		refresh();
		region = null;
		numVertices = 0;
		numIndices = 0;
	}

	/** Sets the polygon region.
	 * @return This object for chaining. */
	public Poly region (PolygonRegion region) {
		this.region = region;
		numVertices = region.getVertices().length / 2;
		numIndices = region.getTriangles().length;
		return this;
	}

	public Poly size (float width, float height) {
		this.width = width;
		this.height = height;
		sizeSet = true;
		return this;
	}

	/** Sets the center point for transformations (rotation and scale). For {@link Quad2D}, this is relative to the bottom left
	 * corner of the texture region. For {@link Quad3D}, this is relative to the center of the texture region and is in the local
	 * coordinate system.
	 * @return This object for chaining. */
	public Poly origin (float originX, float originY) {
		this.originX = originX;
		this.originY = originY;
		return this;
	}

	public Poly color (Color color) {
		this.color = color.toFloatBits();
		return this;
	}

	public Poly color (float r, float g, float b, float a) {
		int intBits = (int)(255 * a) << 24 | (int)(255 * b) << 16 | (int)(255 * g) << 8 | (int)(255 * r);
		color = NumberUtils.intToFloatColor(intBits);
		return this;
	}

	public Poly color (float floatBits) {
		color = floatBits;
		return this;
	}

	public Poly scale (float scaleX, float scaleY) {
		this.scaleX = scaleX;
		this.scaleY = scaleY;
		return this;
	}

	protected int apply (float[] vertices, int vertexStartingIndex, AttributeOffsets offsets, int vertexSize) {
		final PolygonRegion region = this.region;
		final TextureRegion tRegion = region.getRegion();
		if (!sizeSet && region != null) {
			width = tRegion.getRegionWidth();
			height = tRegion.getRegionHeight();
		}

		float color = this.color;
		for (int i = 0, v = vertexStartingIndex + offsets.color0; i < numVertices; i++, v += vertexSize) {
			vertices[v] = color;
		}

		float[] textureCoords = region.getTextureCoords();
		for (int i = 0, v = vertexStartingIndex
			+ offsets.textureCoordinate0, n = textureCoords.length; i < n; i += 2, v += vertexSize) {
			vertices[v] = textureCoords[i];
			vertices[v + 1] = textureCoords[i + 1];
		}

		return 0; // handled by subclass
	}

	protected int apply (short[] triangles, int triangleStartingIndex, short firstVertex) {
		short[] regionTriangles = region.getTriangles();
		for (int i = 0; i < regionTriangles.length; i++) {
			triangles[triangleStartingIndex++] = (short)(regionTriangles[i] + firstVertex);
		}
		return numIndices;
	}
}
