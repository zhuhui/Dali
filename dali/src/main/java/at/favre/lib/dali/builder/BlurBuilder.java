package at.favre.lib.dali.builder;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import java.util.ArrayList;
import java.util.List;

import at.favre.lib.dali.blur.EBlurAlgorithm;
import at.favre.lib.dali.blur.IBlur;
import at.favre.lib.dali.blur.algorithms.RenderScriptGaussianBlur;
import at.favre.lib.dali.builder.img.BrightnessProcessor;
import at.favre.lib.dali.builder.img.ContrastProcessor;
import at.favre.lib.dali.builder.img.FrostGlassProcessor;
import at.favre.lib.dali.builder.img.IBitmapProcessor;
import at.favre.lib.dali.util.BlurUtil;

/**
 * Created by PatrickF on 26.05.2014.
 */
public class BlurBuilder {
	private final static String TAG = BlurBuilder.class.getSimpleName();

	private static final int MIN_BLUR_RADIUS = 1;
	private static final int MAX_BLUR_RADIUS = 25;

	private Data data;

	protected static class Data {
		protected boolean debugMode = false;
		protected BitmapFactory.Options options = new BitmapFactory.Options();
		protected IBlur blurAlgorithm;
		protected boolean copyBitmapBeforeBlur = false;
		protected int blurRadius=16;
		protected boolean rescaleIfDownscaled = false;
		protected ImageReference imageReference;
		protected ContextWrapper contextWrapper;
		protected List<IBitmapProcessor> preProcessors = new ArrayList<IBitmapProcessor>();
		protected List<IBitmapProcessor> postProcessors = new ArrayList<IBitmapProcessor>();
	}

	public BlurBuilder(ContextWrapper contextWrapper, ImageReference imageReference, boolean debugMode) {
		data = new Data();
		data.imageReference = imageReference;
		data.contextWrapper = contextWrapper;
		data.blurAlgorithm = new RenderScriptGaussianBlur(data.contextWrapper.getRenderScript());
		data.debugMode = debugMode;
	}

	public BlurBuilder blurRadius(int radius) {
		checkBlurRadiusPrecondition(radius);
		data.blurRadius = radius;
		return this;
	}

	private void checkBlurRadiusPrecondition(int blurRadius) {
		if(blurRadius < MIN_BLUR_RADIUS ||  blurRadius > MAX_BLUR_RADIUS) {
			throw new IllegalArgumentException("Valid blur radius must be between (inclusive) "+MIN_BLUR_RADIUS+" and "+MAX_BLUR_RADIUS+" found "+blurRadius);
		}
	}

	/**
	 * If this the image bitmap should be copied before blur.
	 *
	 * This will increase memory (RAM) usage while blurring, but
	 * if the bitmap's object is used anywhere else it would
	 * create side effects.
	 *
	 * @param shouldCopy
	 * @return
	 */
	public BlurBuilder copyBitmapBeforeProcess(boolean shouldCopy) {
		data.copyBitmapBeforeBlur = shouldCopy;
		return this;
	}

	/**
	 * Will scale the image down before processing for
	 * performance enhancement and less memory usage
	 * sacrificing image quality.
	 *
	 * @param scaleInSample value > 1 will scale the image width/height, so 2 will get you 1/4
	 *                      of the original size and 4 will get you 1/16 of the original size - this just sets
	 *                      the inSample size in {@link android.graphics.BitmapFactory.Options#inSampleSize } and
	 *                      behaves exactly the same, so keep the value 2^n for least scaling artifacts
	 * @return
	 */
	public BlurBuilder downScale(int scaleInSample) {
		data.options.inSampleSize = Math.min(Math.max(1, scaleInSample), 16384);
		return this;
	}

	/**
	 * Artificially rescales the image if downscaled before to
	 * it's original width/height
	 *
	 * @return
	 */
	public BlurBuilder reScaleIfDownscaled() {
		data.rescaleIfDownscaled = true;
		return this;
	}

	/**
	 * Set your custom decoder options here. Mind that that may
	 * overwrite the value in {@link #downScale(int)} ()};
	 *
	 * @param options non-null
	 * @return
	 */
	public BlurBuilder options(BitmapFactory.Options options) {
		if(options != null) {
			data.options = options;
		}
		return this;
	}

	/**
	 * Add custom processor. This will be applied to the
	 * image BEFORE blurring. The order in which this is
	 * calls defines the order the processors are applied.
	 *
	 * @param processor
	 * @return
	 */
	public BlurBuilder addPreProcessor(IBitmapProcessor processor) {
		data.preProcessors.add(processor);
		return this;
	}

	/**
	 * Set brightness to  eg. darken the resulting image for use as background
	 *
	 * @param brightness default is 0, pos values increase brightness, neg. values decrease contrast
	 *                   min value is -100 (which is all black) and max value is 900 (which is all white)
	 * @return
	 */
	public BlurBuilder brightness(float brightness) {
		data.postProcessors.add(new BrightnessProcessor(data.contextWrapper.getRenderScript(),Math.max(Math.min(900.f,brightness),-100.f)));
		return this;
	}

	/**
	 * Change contrast of the image
	 *
	 * @param contrast default is 0, pos values increase contrast, neg. values decrease contrast
	 * @return
	 */
	public BlurBuilder contrast(float contrast) {
		data.postProcessors.add(new ContrastProcessor(data.contextWrapper.getRenderScript(),Math.max(Math.min(1500.f,contrast),-1500.f)));
		return this;
	}

	public BlurBuilder frostedGlass() {
		data.postProcessors.add(new FrostGlassProcessor(data.contextWrapper.getRenderScript(),data.contextWrapper.getContext().getResources()));
		return this;
	}

	/**
	 * Add custom processor. This will be applied to the
	 * image AFTER blurring. The order in which this is
	 * calls defines the order the processors are applied.
	 *
	 * @param processor
	 * @return
	 */
	public BlurBuilder addPostProcessor(IBitmapProcessor processor) {
		data.postProcessors.add(processor);
		return this;
	}

	/**
	 * Sets the blur algorithm.
	 *
	 * NOTE: this probably never is necessary to do except for testing purpose, the default
	 * algorithm, which uses Android's {@link android.support.v8.renderscript.ScriptIntrinsicBlur}
	 * which is the best and fastest you get on Android suffices in nearly every situation
	 *
	 * @param algorithm
	 * @return
	 */
	public BlurBuilder algorithm(EBlurAlgorithm algorithm) {
		data.blurAlgorithm = BlurUtil.getIBlurAlgorithm(algorithm,data.contextWrapper);

		return this;
	}

	/**
	 * Provide your custom blur implementation
	 *
	 * @param blurAlgorithm
	 * @return
	 */
	public BlurBuilder algorithm(IBlur blurAlgorithm) {
		data.blurAlgorithm = blurAlgorithm;
		return this;
	}

	public Bitmap get() {
		return new BlurWorker(data,null).process();
	}

	public interface TaskFinishedListener {
		public void onBitmapReady(Bitmap manipulatedBitmap);
		public void onError(Throwable t);
	}

}
