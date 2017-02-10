package com.cyphercove.gdx.covetools.assets;

import java.lang.annotation.*;
import java.lang.reflect.Array;
import java.security.AccessControlException;

import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.assets.AssetDescriptor;
import com.badlogic.gdx.assets.AssetLoaderParameters;
import com.badlogic.gdx.assets.loaders.FileHandleResolver;
import com.badlogic.gdx.utils.GdxRuntimeException;
import com.badlogic.gdx.utils.ObjectMap;
import com.badlogic.gdx.utils.ObjectSet;
import com.badlogic.gdx.utils.reflect.ClassReflection;
import com.badlogic.gdx.utils.reflect.Field;
import com.badlogic.gdx.utils.reflect.ReflectionException;

/** An AssetManager that can load and automatically populate annotated fields for assets in an asset container
 * class. Fields of a class can be annotated with {@link Asset} or {@link Assets} (for arrays of assets) to specify
 * their paths. They can be queued for loading by passing an instance of the containing class using 
 * {@link AssignmentAssetManager#loadAssetFields(Object)}. When loading is complete, references to all the loaded
 * assets are automatically populated on the container class.
 * <p>
 * All annotated assets of a container class can be unloaded by calling {@link AssignmentAssetManager#unloadAssetFields(Object)}.
 * This unloads these assets and nulls their fields in the container class. If an asset is still referenced in another loaded
 * container, it will not be unloaded.
 * 
 * @author cypherdare
 */
public class AssignmentAssetManager extends AssetManager {

	private final ObjectSet<Object> queuedContainers = new ObjectSet<Object>();
	private final ObjectSet<Object> loadedContainers = new ObjectSet<Object>();
	private final ObjectMap<Object, ObjectMap<Field, AssetDescriptor<?>>> containersFieldsToAssets 
		= new ObjectMap<Object, ObjectMap<Field, AssetDescriptor<?>>>();
	private final ObjectMap<Object, ObjectMap<Object[], AssetDescriptor<?>[]>> containersFieldsToAssetArrays 
		= new ObjectMap<Object, ObjectMap<Object[], AssetDescriptor<?>[]>>();

	public AssignmentAssetManager() {
		super();
	}

	public AssignmentAssetManager(FileHandleResolver resolver, boolean defaultLoaders) {
		super(resolver, defaultLoaders);
	}

	public AssignmentAssetManager(FileHandleResolver resolver) {
		super(resolver);
	}
	
	@Override
	public synchronized boolean update () {
		boolean done = super.update();
		if (done){
			// assign references to Asset fields of queuedContainers
			for (Object assetContainer : queuedContainers){
				ObjectMap<Field, AssetDescriptor<?>> fieldsToAssets = containersFieldsToAssets.get(assetContainer);
				for (ObjectMap.Entry<Field, AssetDescriptor<?>> fieldEntry : fieldsToAssets){
					Field field = fieldEntry.key;
					if (!field.isAccessible()) {
						try {
							field.setAccessible(true);
						} catch (AccessControlException ex) {
							throw new GdxRuntimeException(String.format("Field $s cannot be made accessible", field.getName()));
						}
					}
					try {
						field.set(assetContainer, get(fieldEntry.value));
					} catch (ReflectionException e) {
						throw new GdxRuntimeException("Failed to assign loaded asset " + field.getName(), e);
					}
				}
				ObjectMap<Object[], AssetDescriptor<?>[]> fieldsToAssetArrays = containersFieldsToAssetArrays.get(assetContainer);
				for (ObjectMap.Entry<Object[], AssetDescriptor<?>[]> arrayEntry : fieldsToAssetArrays){
					Object[] destinationArray = arrayEntry.key;
					AssetDescriptor<?>[] descriptors = arrayEntry.value;
					for (int i=0; i<descriptors.length; i++){
						destinationArray[i] = get(descriptors[i]);
					}
				}
				
				if (assetContainer instanceof AssetContainer)
					((AssetContainer)assetContainer).onAssetsLoaded();
			}
			
			loadedContainers.addAll(queuedContainers);
			queuedContainers.clear();
		}
		return done;
	}

	/** Queues the corresponding assets of the {@link Asset} and {@link Assets} annotated fields of the specified container for loading. When loading
	 * is complete, the fields will automatically reference the loaded assets.
	 * @param assetContainer An object containing fields annotated with {@link Asset} and {@link Assets}. May optionally implement
	 * {@link AssetContainer} for further customization.
	 */
	public synchronized void loadAssetFields (Object assetContainer){
		if (assetContainer == null)
			throw new GdxRuntimeException("Asset container cannot be null");
		if (queuedContainers.contains(assetContainer) || loadedContainers.contains(assetContainer))
			return;
		Class<?> containerType = assetContainer.getClass();
		String pathPrepend = null;
		if (assetContainer instanceof AssetContainer){
			pathPrepend = ((AssetContainer)assetContainer).getAssetPathPrefix();
			if (pathPrepend.equals(""))
				pathPrepend = null;
		}
		Field[] fields = ClassReflection.getDeclaredFields(containerType);
		ObjectMap<Field, AssetDescriptor<?>> containerAssets = new ObjectMap<Field, AssetDescriptor<?>>();
		ObjectMap<Object[], AssetDescriptor<?>[]> containerAssetArrays = new ObjectMap<Object[], AssetDescriptor<?>[]>();
		for (Field field : fields){
			com.badlogic.gdx.utils.reflect.Annotation assetAnnotation = field.getDeclaredAnnotation(Asset.class);
			if (assetAnnotation != null){
				Class<?> assetType = field.getType();
				Asset asset = assetAnnotation.getAnnotation(Asset.class);
				String fileName = asset.value();
				if (pathPrepend != null)
					fileName = pathPrepend + fileName;
				AssetLoaderParameters<?> parameter = findParameter(containerType, fields, asset.parameter(), field.getName());
				@SuppressWarnings({ "rawtypes", "unchecked" })
				AssetDescriptor<?> assetDescriptor = new AssetDescriptor(fileName, assetType, parameter);
				load(assetDescriptor);
				containerAssets.put(field, assetDescriptor);
				continue;
			}
			com.badlogic.gdx.utils.reflect.Annotation assetsAnnotation = field.getDeclaredAnnotation(Assets.class);
			if (assetsAnnotation != null){
				Class<?> assetType = field.getType().getComponentType();
				if (assetType == null){
					throw new GdxRuntimeException(String.format("@Assets may only be used with an array, and $s is not an array.", field.getName()));
				}
				Assets assets = assetsAnnotation.getAnnotation(Assets.class);
				String[] fileNames = assets.value();
				if (pathPrepend != null){
					for (int i=0; i<fileNames.length; i++)
						fileNames[i] = pathPrepend + fileNames[i];
				}
				AssetDescriptor<?>[] assetDescriptors = new AssetDescriptor[fileNames.length];
				
				String[] parameters = assets.parameters();
				if (parameters != null && parameters.length > 0){
					if (parameters.length != fileNames.length)
						throw new GdxRuntimeException(String.format("For asset array $s, number of parameters does not match number of file name values.", field.getName()));
					for (int i=0; i<assetDescriptors.length; i++){
						AssetLoaderParameters<?> parameter = findParameter(containerType, fields, parameters[i], field.getName());
						@SuppressWarnings({ "rawtypes", "unchecked" })
						AssetDescriptor<?> assetDescriptor = new AssetDescriptor(fileNames[i], assetType, parameter);
						assetDescriptors[i] = assetDescriptor;
						load(assetDescriptors[i]);
					}
				} else {
					// No parameters array specified, check for single parameter. Use that or null for parameters
					AssetLoaderParameters<?> parameter = findParameter(containerType, fields, assets.parameter(), field.getName());
					for (int i=0; i<assetDescriptors.length; i++){
						@SuppressWarnings({ "rawtypes", "unchecked" })
						AssetDescriptor<?> assetDescriptor = new AssetDescriptor(fileNames[i], assetType, parameter);
						assetDescriptors[i] = assetDescriptor;
						load(assetDescriptors[i]);
					}
				}

				Object[] containerArray = (Object[]) Array.newInstance(assetType, fileNames.length);
				try { // instantiate the empty array for the assets
					if (!field.isAccessible()) {
						try {
							field.setAccessible(true);
						} catch (AccessControlException ex) {
							throw new GdxRuntimeException(String.format("Field $s cannot be made accessible", field.getName()));
						}
					}
					field.set(assetContainer, containerArray);
				} catch (ReflectionException e) {
					throw new GdxRuntimeException("Failed to assign generated array for " + field.getName(), e);
				}
				containerAssetArrays.put(containerArray, assetDescriptors);
				continue;
			}
		}
		containersFieldsToAssets.put(assetContainer, containerAssets);
		containersFieldsToAssetArrays.put(assetContainer, containerAssetArrays);
		queuedContainers.add(assetContainer);
	}
	
	/** @return The AssetLoaderParameters matching the given field name, or null if the field name is "" or null. 
	 * @throws GdxRuntimeException if the field value is null, the named field does not reference an AssetLoaderParameters, or the field does not exist.  */
	private AssetLoaderParameters<?> findParameter (Class<?> containerType, Field[] containerFields, String parameterFieldName, String annotatedFieldName){
		if (parameterFieldName == null || parameterFieldName.equals(""))
			return null;
		for (Field field : containerFields){
			if (field.getName().equals(parameterFieldName)){
				try {
					AssetLoaderParameters<?> parameter = (AssetLoaderParameters<?>) field.get(containerType);
					if (parameter == null){
						throw new GdxRuntimeException(String.format("The specified parameter $s for asset $s cannot be null.", parameterFieldName, annotatedFieldName));
					}
					return parameter;
				} catch (ReflectionException e) {
					throw new GdxRuntimeException(String.format("Specified parameter $s for asset $s is not an AssetLoaderParameter.", parameterFieldName, annotatedFieldName), e);
				}
			}
		}
		throw new GdxRuntimeException(String.format("The specified parameter $s for asset $s does not exist.", parameterFieldName, annotatedFieldName));
	}
	
	/**
	 * Unloads the corresponding assets of the {@link Asset} and {@link Assets} annotated fields of the specified container, if they are not referenced by any other containers. Nulls out
	 * these fields in the asset container.
	 */
	public void unloadAssetFields (Object assetContainer){
		if (queuedContainers.contains(assetContainer)){
			queuedContainers.remove(assetContainer);
		}
		if (loadedContainers.contains(assetContainer)){
			loadedContainers.remove(assetContainer);
		}
		
		ObjectMap<Field, AssetDescriptor<?>> assets = containersFieldsToAssets.get(containersFieldsToAssets);
		ObjectMap<Object[], AssetDescriptor<?>[]> assetArrays = containersFieldsToAssetArrays.get(assetContainer);
		containersFieldsToAssets.remove(assetContainer);
		containersFieldsToAssetArrays.remove(assetContainer);

		// unload asset fields if not in any other loaded asset containers
		for (AssetDescriptor<?> asset : assets.values()){
			if (!isReferenced(asset))
				unload(asset.fileName);
		}
		for (AssetDescriptor<?>[] assetArray : assetArrays.values()){
			for (AssetDescriptor<?> asset : assetArray){
				if (!isReferenced(asset))
					unload(asset.fileName);
			}
		}
		
		// null field references of asset container
		for (Field field : assets.keys()){
			try {
				field.set(assetContainer, null);
			} catch (ReflectionException e) {
				throw new GdxRuntimeException("Failed to clear field " + field.getName(), e);
			}
		}
		for (Object[] array : assetArrays.keys()){
			Field[] fields = ClassReflection.getFields(assetContainer.getClass());
			for (Field field : fields){
				try {
					if (field.get(assetContainer) == array){
						field.set(assetContainer, null);
					}
				} catch (ReflectionException e) {
					throw new GdxRuntimeException("Failed to clear field " + field.getName(), e);
				}
			}
		}
	}
	
	private boolean isReferenced (AssetDescriptor<?> asset){
		return false;
	}

	/**
	 * Annotation for a field to be populated with a reference to the specified asset. A path for the asset must be provided. An
	 * AssetManagerParameters may be provided using {@link Assets#parameter()}. Parameters are specified by field name, and should already be
	 * populated before loading.
	 */
	@Documented
	@Retention(RetentionPolicy.RUNTIME)
	@Target(ElementType.FIELD)
	static public @interface Asset {
		public String value(); // the path
		public String parameter() default "";
	}
	
	/**
	 * Annotation for an array field to be populated with references to the assets. An array of asset paths must be provided. A single shared
	 * AssetManagerParameters may be provided using {@link Assets#parameter()}, or an array of an array of AssetManagerParameters may be 
	 * specified using {@link Assets#parameters()} (array sizes must match). Parameters are specified by field name, and should already be
	 * populated before loading.
	 */
	@Documented
	@Retention(RetentionPolicy.RUNTIME)
	@Target(ElementType.FIELD)
	static public @interface Assets {
		public String[] value(); // the paths
		public String[] parameters() default {};
		public String parameter() default "";
	}
	
	/** Optional interface for an AssetContainer, providing a loading callback and other functionality. */
	public interface AssetContainer {
		/** @return A path string that will prepended to all asset paths in this container. If a directory, a trailing slash must be included.
		 * May return null to prepend nothing. */
		String getAssetPathPrefix ();
		
		/** Called when the AssetManager has finished loading all assets and populated the annotated asset fields in this container. */
		void onAssetsLoaded ();
	}
	
}
