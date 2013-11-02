package com.gifmakers;

import java.awt.geom.AffineTransform;
import java.awt.image.AffineTransformOp;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.TimeZone;

import javax.imageio.ImageIO;
import javax.imageio.stream.FileImageOutputStream;
import javax.imageio.stream.ImageOutputStream;

import com.xuggle.mediatool.IMediaReader;
import com.xuggle.mediatool.MediaListenerAdapter;
import com.xuggle.mediatool.ToolFactory;
import com.xuggle.mediatool.event.IVideoPictureEvent;
import com.xuggle.xuggler.Global;

public class Converter {

	public static volatile boolean capturationDone = false;
	public static final double RESIZE_FACTOR = 0.1;
	public static double FRAME_RATE;
	private static String outputFilePrefix;

	static String firstImage = "";
	public static ArrayList<BufferedImage> biList;
	// startTime and endTime in microseconds
	private static long startTime;
	private static long endTime;
	// The video stream index, used to ensure we display frames from one and
	// only one video stream from the media container.
	private static int mVideoStreamIndex = -1;

	// Time of last frame write
	private static long mLastPtsWrite = Global.NO_PTS;
	private static long mFirstFrame = -1;
	public static long MICRO_SECONDS_BETWEEN_FRAMES; // = (long) (Global.DEFAULT_PTS_PER_SECOND * (1 / FRAME_RATE)); // SECONDS_BETWEEN_FRAMES);

	public static void main(String[] args) {
		try {
			convert("/home/matias/Downloads/000.ts", "/home/matias/MMS/mysnapshot", 10, "00:00:00,000 --> 00:01:17,000");
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	/**
	 * Converts given time interval of MPEG2 ts video segments to animated GIF.
	 * @param inputFile MPEG2 ts video file to be converted
	 * @param outputFile The prefix for output file. Method creates a animation and thumbnail using this prefix.
	 * @param frameRate Frame rate of the output GIF animation.
	 * @param timeInterval Time interval to be captured. Format is same than in SRT files: 00:00:00,000 --> 00:01:17,000
	 * @throws IOException
	 * @throws InterruptedException
	 * @throws RuntimeException
	 */
	public static void convert(String inputFile, String outputFile, double frameRate, String timeInterval ) throws IOException,
			InterruptedException, RuntimeException {
		if (inputFile.equals("")) {
			System.out.println("Illegal input filename.");
			throw new RuntimeException();
		}
		
		if (outputFile.equals("")) {
			outputFilePrefix = "defaultoutput";
		} else {
			outputFilePrefix = outputFile;
		}
		
		if (frameRate <= 0) {
			System.out.println("Illegal frame rate.");
			throw new RuntimeException();
		} else {
			FRAME_RATE = frameRate;
			MICRO_SECONDS_BETWEEN_FRAMES = (long) (Global.DEFAULT_PTS_PER_SECOND * (1 / FRAME_RATE)); // SECONDS_BETWEEN_FRAMES);
		}
		
		String[] interval = timeInterval.split(" --> ");
		if (interval.length < 2) {
			System.out.println("Error in time interval.");
			return;
		}
		// Custom date format, eg. 00:00:10,500 --> 00:00:13,000"
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss,SSS");  
        format.setTimeZone(TimeZone.getTimeZone("UTC"));

        try {
			startTime = ((format.parse("1970-01-01 " + interval[0])).getTime() * 1000);
			endTime = ((format.parse("1970-01-01 " + interval[1])).getTime() * 1000);
		} catch (ParseException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
		
		biList = new ArrayList<BufferedImage>();

		IMediaReader mediaReader = ToolFactory.makeReader(inputFile);

		// stipulate that we want BufferedImages created in BGR 24bit color
		mediaReader
				.setBufferedImageTypeToGenerate(BufferedImage.TYPE_3BYTE_BGR);
		
		mediaReader.addListener(new ImageSnapListener());

		// Create output stream
		ImageOutputStream output = new FileImageOutputStream(new File(
				outputFilePrefix + ".gif"));

		System.out.printf("Starting to create animated gif\n");
		// Create new instance of GifSequenceWriter and start it in new thread
		Thread t = new Thread(new GifSequenceWriter(output, 5,
				(int) (1000 / FRAME_RATE), true, biList));
		t.start();

		System.out.println("Starting to extract frames from video file.");
		Date t0 = new Date();
		
		// read out the contents of the media file and
		// dispatch events to the attached listener
		try {
			while (mediaReader.readPacket() == null && !capturationDone) {
				if (biList.size() > (100/RESIZE_FACTOR)) {
					Thread.sleep(3000);
				}
			}
		} catch (RuntimeException e) {
			// Do nothing
		}
		
		Date t1 = new Date();
		System.out.println("Frames captured. Waiting for other thread to complete. It took " + (t1.getTime() - t0.getTime()) + "ms.");
		while (t.isAlive()) {
			// Wait maximum of 3 second
			// for MessageLoop thread
			// to finish.
			t.join(3000);
		}
		Date t2 = new Date();
		System.out.println("Finally! It took " + (t2.getTime() - t0.getTime()) + "ms.");
		

		output.close();
	}

	private static class ImageSnapListener extends MediaListenerAdapter {

		public void onVideoPicture(IVideoPictureEvent event) {
			if (event.getStreamIndex() != mVideoStreamIndex) {
				System.out.println("Streamindex: " + event.getStreamIndex());
				// if the selected video stream id is not yet set, go ahead an
				// select this lucky video stream
				if (mVideoStreamIndex == -1)
					mVideoStreamIndex = event.getStreamIndex();
				// no need to show frames from this video stream
				else
					return;
			}

			if (mFirstFrame == -1) {
				mFirstFrame = event.getTimeStamp();
			}
			
			
			// Check when we should start capturing
			if (event.getTimeStamp() - mFirstFrame >= startTime && event.getTimeStamp() - mFirstFrame <= endTime) {
				// if uninitialized, back date mLastPtsWrite to get the very first
				// frame
				if (mLastPtsWrite == Global.NO_PTS)
					mLastPtsWrite = event.getTimeStamp()
							- MICRO_SECONDS_BETWEEN_FRAMES;
	
				// if it's time to write the next frame
				if (event.getTimeStamp() - mLastPtsWrite >= MICRO_SECONDS_BETWEEN_FRAMES) {
					
					// String outputFilename = dumpImageToFile(event.getImage());
					addImageToBuffer(event.getImage());
	
					// update last write time
					mLastPtsWrite += MICRO_SECONDS_BETWEEN_FRAMES;
				}
			} 
			// If we have already passed endTime, we can just stop seeking.
			else if (event.getTimeStamp() - mFirstFrame > endTime) {
				capturationDone = true;
			}

		}

		private void addImageToBuffer(BufferedImage image) {
			int w, h;
			BufferedImage resized;
			//before = Converter.biList.remove(0);
			w = image.getWidth();
			h = image.getHeight();
			resized = new BufferedImage((int)(RESIZE_FACTOR*w), (int)(RESIZE_FACTOR*h), BufferedImage.TYPE_INT_ARGB);
			AffineTransform at = new AffineTransform();
			at.scale(RESIZE_FACTOR, RESIZE_FACTOR);
			AffineTransformOp scaleOp = 
			   new AffineTransformOp(at, AffineTransformOp.TYPE_BILINEAR);
			resized = scaleOp.filter(image, resized);
			
			biList.add(resized);
			if (firstImage.equals("") == true) {				
				firstImage = dumpImageToFile(resized);
			}
		}

		private String dumpImageToFile(BufferedImage image) {

			try {
				String outputFilename = outputFilePrefix
						+ "_thumbnail.png";

				ImageIO.write(image, "png", new File(outputFilename));
				return outputFilename;
			} catch (IOException e) {
				e.printStackTrace();
				return null;
			}
		}
	}
}
