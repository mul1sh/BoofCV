/*
 * Copyright (c) 2011-2019, Peter Abeles. All Rights Reserved.
 *
 * This file is part of BoofCV (http://boofcv.org).
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package boofcv.demonstrations.feature.disparity;

import boofcv.abst.feature.disparity.StereoDisparity;
import boofcv.alg.distort.ImageDistort;
import boofcv.alg.geo.PerspectiveOps;
import boofcv.alg.geo.RectifyImageOps;
import boofcv.alg.geo.rectify.RectifyCalibrated;
import boofcv.concurrency.BoofConcurrency;
import boofcv.core.image.GeneralizedImageOps;
import boofcv.factory.feature.disparity.DisparityAlgorithms;
import boofcv.factory.feature.disparity.FactoryStereoDisparity;
import boofcv.gui.BoofSwingUtil;
import boofcv.gui.DemonstrationBase;
import boofcv.gui.d3.DisparityToColorPointCloud;
import boofcv.gui.image.ImagePanel;
import boofcv.gui.image.VisualizeImageData;
import boofcv.io.PathLabel;
import boofcv.io.ProgressMonitorThread;
import boofcv.io.UtilIO;
import boofcv.io.calibration.CalibrationIO;
import boofcv.io.image.ConvertBufferedImage;
import boofcv.struct.border.BorderType;
import boofcv.struct.calib.CameraPinhole;
import boofcv.struct.calib.StereoParameters;
import boofcv.struct.distort.DoNothing2Transform2_F64;
import boofcv.struct.distort.Point2Transform2_F64;
import boofcv.struct.image.*;
import boofcv.visualize.*;
import georegression.struct.se.Se3_F64;
import org.ejml.data.DMatrixRMaj;
import org.ejml.data.FMatrixRMaj;
import org.ejml.dense.row.CommonOps_DDRM;
import org.ejml.ops.ConvertMatrixData;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

import static boofcv.alg.geo.RectifyImageOps.transformRectToPixel;

/**
 * Computes and displays disparity from still disparity images.  The disparity can be viewed
 * as a color surface plot or as a 3D point cloud.  Different tuning parameters can be adjusted
 * use a side control panel.
 *
 * @author Peter Abeles
 */
public class VisualizeStereoDisparity <T extends ImageGray<T>, D extends ImageGray<D>>
		extends DemonstrationBase
		implements DisparityDisplayPanel.Listener
{
	// original input before rescaling
	BufferedImage origLeft;
	BufferedImage origRight;
	StereoParameters origCalib;

	// rectified color image from left and right camera for display
	private BufferedImage colorLeft;
	private BufferedImage colorRight;
	// Output disparity color surface plot
	private BufferedImage disparityOut;

	// gray scale input image before rectification
	private T inputLeft;
	private T inputRight;
	// gray scale input images after rectification
	private T rectLeft;
	private T rectRight;

	// calibration parameters
	private StereoParameters calib;
	// rectification algorithm
	private RectifyCalibrated rectifyAlg = RectifyImageOps.createCalibrated();

	// GUI components
	private final DisparityDisplayPanel control = new DisparityDisplayPanel();
	private final JPanel panel = new JPanel();
	private final ImagePanel gui = new ImagePanel();
	private final PointCloudViewer pcv = VisualizeData.createPointCloudViewer();

	// if true the point cloud has already been computed and does not need to be recomputed
	private boolean computedCloud;

	// instance of the selected algorithm
	private StereoDisparity<T,D> activeAlg;

	// camera calibration matrix of rectified images
	private DMatrixRMaj rectK;
	private DMatrixRMaj rectR;

	// makes sure process has been called before render disparity is done
	// There was a threading issue where disparitySettingChange() created a new alg() but render was called before
	// it could process an image.
	private volatile boolean processCalled = false;
	private boolean rectifiedImages = false;

	// coordinate transform from left rectified image to its original pixels
	Point2Transform2_F64 leftRectToPixel;

	public VisualizeStereoDisparity(List<PathLabel> examples ) {
		super(true,false,examples,ImageType.single(GrayF32.class));

		// Tell the demo code to not extract inputs from inputs
		super.inputAsFile = true;

//		addAlgorithm(0,"Five Region",0);
//		addAlgorithm(0,"Region",1);
//		addAlgorithm(0,"Region Basic",2);

		control.setListener(this);

		panel.setLayout(new BorderLayout());
		panel.add(control, BorderLayout.WEST);
		panel.add(gui,BorderLayout.CENTER);

		add(BorderLayout.WEST,control);
		add(BorderLayout.CENTER,panel);
	}

	@Override
	protected void openFileMenuBar() {
		JOptionPane.showMessageDialog(this, "Opening files not yet supported");
	}

	@Override
	public void reprocessInput() {
		// this really should use the demonstration thread pool
		processDisparityInThread();
	}

	@Override
	public void processImage(int sourceID, long frameID, BufferedImage buffered, ImageBase input) {}

	@Override
	public void processFiles(String[] files) {
		if( files.length != 3 )
			throw new IllegalArgumentException("3 Files required");

		origCalib = CalibrationIO.load(media.openFile(files[0]));

		origLeft = media.openImage(files[1]);
		origRight = media.openImage(files[2]);

		changeInputScale();
	}


	public void processDisparityInThread() {
		new Thread(this::processDisparity).start();
	}

	public synchronized void processDisparity() {
		if( !rectifiedImages )
			return;

		ProcessThread progress = new ProcessThread(this);
		progress.start();

		computedCloud = false;
		BoofConcurrency.USE_CONCURRENT = control.concurrent;
		long time0 = System.nanoTime();
		activeAlg.process(rectLeft, rectRight);
		long time1 = System.nanoTime();
		BoofConcurrency.USE_CONCURRENT = true;
		processCalled = true;

		progress.stopThread();

		SwingUtilities.invokeLater(()->{
			control.setProcessingTimeMS((time1-time0)/1e6);
			disparityRender();
		});
	}

	/**
	 * Changes which image is being displayed depending on GUI selection
	 */
	private synchronized void changeImageView()
	{
		JComponent comp;
		if( control.selectedView < 3 ) {
			BufferedImage img;

			switch (control.selectedView) {
				case 0: img = disparityOut; break;
				case 1: img = colorLeft; break;
				case 2: img = colorRight; break;
				default: throw new RuntimeException("Unknown option");
			}

			gui.setImage(img);
			gui.setPreferredSize(new Dimension(origLeft.getWidth(), origLeft.getHeight()));
			comp = gui;
		} else {
			if( !computedCloud ) {
				computedCloud = true;
				DisparityToColorPointCloud d2c = new DisparityToColorPointCloud();

				double baseline = calib.getRightToLeft().getT().norm();
				d2c.configure(baseline, rectK,rectR, leftRectToPixel, control.minDisparity,control.maxDisparity);
				d2c.process(activeAlg.getDisparity(),colorLeft);

				CameraPinhole rectifiedPinhole = PerspectiveOps.matrixToPinhole(
						rectK,colorLeft.getWidth(),colorLeft.getHeight(),null);
				pcv.clearPoints();
				pcv.setCameraHFov(PerspectiveOps.computeHFov(rectifiedPinhole));
				pcv.addCloud(d2c.getCloud(),d2c.getCloudColor());
				changeView3D();
			}
			comp = pcv.getComponent();
			comp.requestFocusInWindow();
		}
		panel.remove(gui);
		panel.remove(pcv.getComponent());
		panel.add(comp,BorderLayout.CENTER);
		panel.validate();
		comp.repaint();
	}

	/**
	 * Removes distortion and rectifies images.
	 */
	private void rectifyInputImages() {
		// Check to see if the input images have already been recitified
		rectifiedImages = calib.isRectified(1e-7);

		// get intrinsic camera calibration matrices
		DMatrixRMaj K1 = PerspectiveOps.pinholeToMatrix(calib.left, (DMatrixRMaj)null);
		DMatrixRMaj K2 = PerspectiveOps.pinholeToMatrix(calib.right, (DMatrixRMaj)null);

		// if rectified just copy the image and return, computing rectification again will not help improve it
		// and could add a little bit of noise
		if( rectifiedImages ) {
			rectLeft.setTo(inputLeft);
			rectRight.setTo(inputRight);
			leftRectToPixel = new DoNothing2Transform2_F64();
			rectK = K1;
			rectR = CommonOps_DDRM.identity(3);
			return;
		}

		// compute rectification matrices
		rectifyAlg.process(K1,new Se3_F64(),K2,calib.getRightToLeft().invert(null));

		DMatrixRMaj rect1 = rectifyAlg.getRect1();
		DMatrixRMaj rect2 = rectifyAlg.getRect2();
		rectK = rectifyAlg.getCalibrationMatrix();
		rectR = rectifyAlg.getRectifiedRotation();

		// adjust view to maximize viewing area while not including black regions
		RectifyImageOps.allInsideLeft(calib.left, rect1, rect2, rectK);

		// compute transforms to apply rectify the images
		leftRectToPixel = transformRectToPixel(calib.left, rect1);

		ImageType<T> imageType = ImageType.single(activeAlg.getInputType());

		FMatrixRMaj rect1_F32 = new FMatrixRMaj(3,3); // TODO simplify code some how
		FMatrixRMaj rect2_F32 = new FMatrixRMaj(3,3);
		ConvertMatrixData.convert(rect1, rect1_F32);
		ConvertMatrixData.convert(rect2, rect2_F32);

		ImageDistort<T,T> distortRect1 = RectifyImageOps.rectifyImage(
				calib.left, rect1_F32, BorderType.SKIP,imageType);
		ImageDistort<T,T> distortRect2 = RectifyImageOps.rectifyImage(
				calib.right, rect2_F32, BorderType.SKIP, imageType);

		// rectify and undo distortion
		distortRect1.apply(inputLeft, rectLeft);
		distortRect2.apply(inputRight,rectRight);

		rectifiedImages = true;
	}

	@Override
	public void algorithmChanged() {
		createAlgConcurrent();
		processDisparityInThread();
	}

	@Override
	public synchronized void disparitySettingChange() {
		if( control.recompute ) {
			processCalled = false;
			activeAlg = createAlg();
			processDisparityInThread();
		}
	}

	@Override
	public synchronized void disparityGuiChange() {
		changeImageView();
	}

	@Override
	public synchronized void disparityRender() {
		if( !processCalled )
			return;

		int color = control.colorInvalid ? 0x02 << 16 | 0xB0 << 8 | 0x90 : 0;

		D disparity = activeAlg.getDisparity();

		disparityOut = VisualizeImageData.disparity(disparity,null,
				activeAlg.getMinDisparity(),activeAlg.getMaxDisparity(),
				color);

		changeImageView();
	}

	@SuppressWarnings("unchecked")
	public StereoDisparity<T,D> createAlg() {
		processCalled = false;

		int r = control.regionRadius;

		// make sure the disparity is in a valid range
		int maxDisparity = Math.min(colorLeft.getWidth()-2*r,control.maxDisparity);
		int minDisparity = Math.min(maxDisparity,control.minDisparity);

		if( control.useSubpixel ) {
			switch( control.selectedAlg ) {
				case 2:
					changeGuiActive(false,false);
					return (StereoDisparity)FactoryStereoDisparity.regionSubpixelWta(DisparityAlgorithms.RECT,minDisparity,
							maxDisparity, r, r, -1, -1, -1, GrayU8.class);

				case 1:
					changeGuiActive(true,true);
					return (StereoDisparity)FactoryStereoDisparity.regionSubpixelWta(DisparityAlgorithms.RECT,minDisparity,
							maxDisparity, r, r, control.pixelError, control.reverseTol, control.texture,
							GrayU8.class);

				case 0:
					changeGuiActive(true,true);
					return (StereoDisparity)FactoryStereoDisparity.regionSubpixelWta(DisparityAlgorithms.RECT_FIVE,
							minDisparity, maxDisparity, r, r,
							control.pixelError, control.reverseTol, control.texture,
							GrayU8.class);

				default:
					throw new RuntimeException("Unknown selection");
			}
		} else {
			switch( control.selectedAlg ) {
				case 2:
					changeGuiActive(false,false);
					return (StereoDisparity)FactoryStereoDisparity.regionWta(DisparityAlgorithms.RECT,minDisparity,
							maxDisparity, r, r, -1, -1, -1, GrayU8.class);

				case 1:
					changeGuiActive(true,true);
					return (StereoDisparity)FactoryStereoDisparity.regionWta(DisparityAlgorithms.RECT,minDisparity,
							maxDisparity, r, r, control.pixelError, control.reverseTol, control.texture,
							GrayU8.class);

				case 0:
					changeGuiActive(true,true);
					return (StereoDisparity)FactoryStereoDisparity.regionWta(DisparityAlgorithms.RECT_FIVE,
							minDisparity, maxDisparity, r, r,
							control.pixelError, control.reverseTol, control.texture,
							GrayU8.class);

				default:
					throw new RuntimeException("Unknown selection");
			}
		}

	}

	/**
	 * Active and deactivates different GUI configurations
	 */
	private void changeGuiActive( final boolean error , final boolean reverse ) {
		SwingUtilities.invokeLater(() -> control.setActiveGui(error,reverse));
	}

	@Override
	public synchronized void changeInputScale() {
		calib = new StereoParameters(origCalib);

		double scale = control.inputScale/100.0;

		PerspectiveOps.scaleIntrinsic(calib.left,scale);
		PerspectiveOps.scaleIntrinsic(calib.right,scale);

		int w = (int)(origLeft.getWidth()*scale);
		int h = (int)(origLeft.getHeight()*scale);

		colorLeft = new BufferedImage(w,h,BufferedImage.TYPE_INT_BGR);
		colorRight = new BufferedImage(w,h,BufferedImage.TYPE_INT_BGR);

		colorLeft.createGraphics().drawImage(origLeft, AffineTransform.getScaleInstance(scale,scale),null);
		colorRight.createGraphics().drawImage(origRight, AffineTransform.getScaleInstance(scale,scale),null);

		createAlgConcurrent();

		inputLeft = GeneralizedImageOps.createSingleBand(activeAlg.getInputType(),w,h);
		inputRight = GeneralizedImageOps.createSingleBand(activeAlg.getInputType(),w,h);
		rectLeft = GeneralizedImageOps.createSingleBand(activeAlg.getInputType(),w,h);
		rectRight = GeneralizedImageOps.createSingleBand(activeAlg.getInputType(),w,h);

		ConvertBufferedImage.convertFrom(colorLeft,inputLeft,true);
		ConvertBufferedImage.convertFrom(colorRight,inputRight,true);

		setPreferredSize(new Dimension(200+inputLeft.width,30+inputLeft.height));

		rectifyInputImages();

		BoofSwingUtil.invokeNowOrLater(()->control.setImageSize(rectLeft.width,rectLeft.height));

		processDisparity();
	}

	private void createAlgConcurrent() {
		BoofConcurrency.USE_CONCURRENT = false;
		activeAlg = createAlg();
		BoofConcurrency.USE_CONCURRENT = true;
	}

	@Override
	public void changeView3D() {
		double baseline = calib.getRightToLeft().getT().norm();
		double periodColor = baseline*5*control.periodScale();
		PeriodicColorizer colorizer=null;
		switch( control.colorScheme ) {
			case 0: pcv.removeColorizer();break;
			case 1: colorizer = new RainbowColorSingleAxis.X(); break;
			case 2: colorizer = new RainbowColorSingleAxis.Y(); break;
			case 3: colorizer = new RainbowColorSingleAxis.Z(); break;
			case 4: colorizer = new RainbowColorAxisPlane.X_YZ(4.0); break;
			case 5: colorizer = new RainbowColorAxisPlane.Y_XZ(4.0); break;
			case 6: colorizer = new RainbowColorAxisPlane.Z_XY(4.0); break;
		}
		if( colorizer != null ) {
			colorizer.setPeriod(periodColor);
			colorizer.setOffset(control.offsetScale());
			pcv.setColorizer(colorizer);
		}

		pcv.setTranslationStep(control.speedScale()*baseline/30);
		pcv.getComponent().repaint();
	}

	/**
	 * Displays a progress monitor and updates its state periodically
	 */
	public class ProcessThread extends ProgressMonitorThread
	{
		int state = 0;

		public ProcessThread( JComponent owner ) {
			super(new ProgressMonitor(owner, "Computing Disparity", "", 0, 100));
		}

		@Override
		public void doRun() {
			SwingUtilities.invokeLater(() -> {
				monitor.setProgress(state);
				state = (++state % 100);
			});
		}
	}

	public static void main( String args[] ) {

		String stereoCalib = UtilIO.pathExample("calibration/stereo/Bumblebee2_Chess/stereo.yaml");

		List<PathLabel> examples = new ArrayList<>();
		examples.add(new PathLabel("Chair 1",  stereoCalib
				,UtilIO.pathExample("stereo/chair01_left.jpg"),UtilIO.pathExample("stereo/chair01_right.jpg")));
//		inputs.add(new PathLabel("Chair 2",  new File(dirCalib,"stereo.yaml"),dirImgs+"chair02_left.jpg",dirImgs+"chair02_right.jpg"));
		examples.add(new PathLabel("Stones 1", stereoCalib
				,UtilIO.pathExample("stereo/stones01_left.jpg"),UtilIO.pathExample("stereo/stones01_right.jpg")));
		examples.add(new PathLabel("Lantern 1",stereoCalib
				,UtilIO.pathExample("stereo/lantern01_left.jpg"),UtilIO.pathExample("stereo/lantern01_right.jpg")));
		examples.add(new PathLabel("Wall 1",   stereoCalib
				,UtilIO.pathExample("stereo/wall01_left.jpg"),UtilIO.pathExample("stereo/wall01_right.jpg")));
//		inputs.add(new PathLabel("Garden 1", new File(dirCalib,"stereo.yaml",dirImgs+"garden01_left.jpg",dirImgs+"garden01_right.jpg"));
		examples.add(new PathLabel("Garden 2", stereoCalib
				,UtilIO.pathExample("stereo/garden02_left.jpg"),UtilIO.pathExample("stereo/garden02_right.jpg")));
		examples.add(new PathLabel("Sundial 1", stereoCalib
				,UtilIO.pathExample("stereo/sundial01_left.jpg"),UtilIO.pathExample("stereo/sundial01_right.jpg")));

		SwingUtilities.invokeLater(()->{
			VisualizeStereoDisparity app = new VisualizeStereoDisparity(examples);

			app.openExample(examples.get(0));
			app.waitUntilInputSizeIsKnown();
			app.display("Stereo Disparity");
		});
	}
}
