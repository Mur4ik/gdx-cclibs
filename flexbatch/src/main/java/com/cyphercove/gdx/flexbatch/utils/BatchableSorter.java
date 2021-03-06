
package com.cyphercove.gdx.flexbatch.utils;

import java.util.Comparator;

import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.ObjectMap;
import com.badlogic.gdx.utils.ObjectSet;
import com.badlogic.gdx.utils.Pool;
import com.cyphercove.gdx.flexbatch.Batchable;
import com.cyphercove.gdx.flexbatch.FlexBatch;

/** Sorts 3D {@link Batchable Batchables} to ensure proper render order before passing them to a {@link FlexBatch}.
 * <p>
 * Opaque Batchables are sorted by texture configuration to minimize flushes and drawn first. Blended Batchables are sorted by
 * distance from camera and drawn far to near.
 * 
 * @author cypherdare */
public class BatchableSorter<T extends Batchable & SortableBatchable<T>> {

	protected final int opaqueInitialCapacityPerTexture;
	private final ObjectMap<T, ObjectSet<T>> opaqueBatchables; // first entered to material group
	private final Array<T> blendedBatchables;
	private final Comparator<T> comparator;
	protected Vector3 cameraPosition;
	private boolean needSort;

	private Pool<ObjectSet<T>> objectSetPool = new Pool<ObjectSet<T>>() {
		protected void reset (ObjectSet<T> object) {
			object.clear();
		}

		protected ObjectSet<T> newObject () {
			return new ObjectSet<T>(opaqueInitialCapacityPerTexture);
		}

	};

	public BatchableSorter (Camera camera) {
		this(camera, 2, 1000, 1000);
	}

	public BatchableSorter (Camera camera, int opaqueIntialTextureCapacity, int opaqueInitialCapacityPerTexture,
		int blendedInitialCapacity) {
		this.cameraPosition = camera.position;
		this.opaqueInitialCapacityPerTexture = opaqueInitialCapacityPerTexture;
		opaqueBatchables = new ObjectMap<T, ObjectSet<T>>();
		for (int i = 0; i < opaqueIntialTextureCapacity; i++) { // seed the pool to avoid delay on first use
			objectSetPool.free(objectSetPool.obtain());
		}
		blendedBatchables = new Array<T>(blendedInitialCapacity);
		comparator = new Comparator<T>() {
			public int compare (T o1, T o2) {
				return (int)Math.signum(o2.calculateDistanceSquared(cameraPosition) - o1.calculateDistanceSquared(cameraPosition));
			}
		};
	}

	/** Clear the queue without drawing anything. */
	public void clear () {
		for (ObjectSet<T> set : opaqueBatchables.values())
			objectSetPool.free(set);
		opaqueBatchables.clear();
		blendedBatchables.clear();
	}

	/** Sort (if necessary) and draw the queued Batchables without clearing them. Must be called in between
	 * {@link FlexBatch#begin()} and {@link FlexBatch#end()}. */
	public void draw (FlexBatch<T> flexBatch) {
		if (needSort) {
			blendedBatchables.sort(comparator);
			needSort = false;
		}
		for (ObjectSet<T> set : opaqueBatchables.values())
			for (T batchable : set)
				flexBatch.draw(batchable);
		for (T batchable : blendedBatchables)
			flexBatch.draw(batchable);
	}

	/** Sort (if necessary), draw, and clear references to the queued Batchables. Must be called in between
	 * {@link FlexBatch#begin()} and {@link FlexBatch#end()}. */
	public void flush (FlexBatch<T> flexBatch) {
		draw(flexBatch);
		clear();
	}

	/** Add a Batchable to the queue. */
	public void add (T batchable) {
		if (batchable.isOpaque()) {
			for (ObjectMap.Entry<T, ObjectSet<T>> entry : opaqueBatchables) {
				if (batchable.hasEquivalentTextures(entry.key)) {
					entry.value.add((T)batchable);
					return;
				}
			}
			ObjectSet<T> set = objectSetPool.obtain();
			set.add(batchable);
			opaqueBatchables.put(batchable, set);
		} else {
			blendedBatchables.add(batchable);
		}
		needSort = true;
	}

	/** Sets the camera that is used for distance comparisons to sort the blended Batchables. */
	public void setCamera (Camera camera) {
		cameraPosition = camera.position;
	}
}
