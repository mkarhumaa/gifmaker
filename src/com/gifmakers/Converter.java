package com.gifmakers;

import java.awt.geom.AffineTransform;
import java.awt.image.AffineTransformOp;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.InputStream;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Properties;
import java.util.TimeZone;
import java.util.List;

import javax.imageio.ImageIO;
import javax.imageio.stream.FileImageOutputStream;
import javax.imageio.stream.ImageOutputStream;

import com.xuggle.mediatool.IMediaReader;
import com.xuggle.mediatool.MediaListenerAdapter;
import com.xuggle.mediatool.ToolFactory;
import com.xuggle.mediatool.event.IVideoPictureEvent;
import com.xuggle.xuggler.Global;

public class Converter {

	private static int MASTER = 0;
	private static final int THUMB = 1;
	public static volatile boolean capturationDone = false;
	public static double RESIZE_FACTOR;
	public static double FRAME_RATE;
	private static String outputFilePrefix;
	private static String masterGifName;
	private static BufferedImage masterGifThumb;

	static String firstImage;
	public static ArrayList<BufferedImage> biList;
	public static ArrayList<String> masterGifList;
	// startTime and endTime in microseconds
	private static long startTime;
	private static long endTime;
	// The video stream index, used to ensure we display frames from one and
	// only one video stream from the media container.
	private static int mVideoStreamIndex = -1;

	// Time of last frame write
	private static long mLastPtsWrite;
	private static long mFirstFrame;
	public static long MICRO_SECONDS_BETWEEN_FRAMES;

	public static void main(String[] args) {

		// Read the config file
		Properties prop = new Properties();
	    String fileName = "converter.config";
	    InputStream is = null;
	    masterGifThumb = null;
		masterGifName = "";
	    
		try {
			is = new FileInputStream(fileName);
			prop.load(is);
		} catch (FileNotFoundException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}  catch (IOException e2) {
			// TODO Auto-generated catch block
			e2.printStackTrace();
		}
		// Define configurable variables initially by default values.
		int frameRate = 5;
		RESIZE_FACTOR = 1;
		try {
			frameRate = Integer.parseInt(prop.getProperty("converter.frame_rate", "5"));
			RESIZE_FACTOR = Double.parseDouble(prop.getProperty("converter.resize_factor", "1"));
		} catch (NumberFormatException e) {
			System.out.println("Illegal values in config file. Using default values instead.");
		}
		
		List<String> srtSegments = new ArrayList<>();
		List<String> timeIntervals;
		masterGifList = new ArrayList<String>();
		String videoFile, srtFile = "";

		if (args.length < 3) {
			// Illegal arguments
			System.out.println("Illegal command line arguments.");
			System.out.println("Usage:\n$ For example: $ java -jar converter.jar inputvideo.mp4 inputsrt.srt 1 2 3-5\nNote: Converter requires Java version 1.7 >= ");
			return;
		} else {
			// Check that files exist
			videoFile = args[0];
			srtFile = args[1];

			File vf = new File(videoFile);
			if (!vf.exists()) {
				System.out.println(videoFile + " does not exist!");
				return;
			}

			File sf = new File(srtFile);
			if (!sf.exists()) {
				System.out.println(srtFile + " does not exist!");
				return;
			}

			// Create srt segment array
			System.out.println("Parsing SRT file: " + srtFile);
			for (int i = 2; i < args.length; i++) {
				// Check first that arguments are legal
				if (args[i].matches("\\d+(-\\d+)?")) {
					// This regex matches for example following: "1", "11",
					// "1-1", "11-11"
					srtSegments.add(args[i]);
				} else {
					System.out.println(args[i]
							+ " is not in srt segment number format.");
					return;
				}
			}
			
		}

		timeIntervals = parseSRT(srtFile, srtSegments);
		System.out.println("SRT file parsed.");
		System.out.println("Converting video file to animated GIFs: " + videoFile);
		try {
			for (int i = 0; i < timeIntervals.size(); i++) {
				convert(videoFile, frameRate, timeIntervals.get(i));
			}
			System.out.println("Converting done.");
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (RuntimeException e) {
			System.out.println("Runtime error.");
			return;
		}
	
		ImageOutputStream masterGifOutput;
		GifSequenceWriter masterGifWriter = null;

		// Create master GIF
		if (!masterGifList.isEmpty()) {
			String masterGifFilename = videoFile.split(".mp4")[0] + "_master";//.gif";
			System.out.println("Creating master GIF: " + masterGifFilename);
			try {
				masterGifOutput = new FileImageOutputStream(new File(masterGifFilename + ".gif"));
				masterGifWriter = new GifSequenceWriter(masterGifOutput, 5,
						1000, true, null);
				for (String item : masterGifList) {
					BufferedImage nextImage = ImageIO.read(new File(item));
					masterGifWriter.writeToSequence(nextImage);
				}
				masterGifWriter.close();
			} catch (IOException e) {
				System.out.println("Cannot create master GIF.");
				return;
			}
			if (masterGifThumb != null) {
				try {
					ImageIO.write(masterGifThumb, "png", new File(masterGifFilename + "_thumb.png"));
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
			System.out.println("Master GIF done.");
		}
	}

	/**
	 * Converts given time interval of MPEG2 ts video segments to animated GIF.
	 * 
	 * @param inputFile
	 *            MPEG2 ts video file to be converted
	 * @param frameRate
	 *            Frame rate of the output GIF animation.
	 * @param timeInterval
	 *            Time interval to be captured. Format is same than in SRT
	 *            files: 00:00:00,000 --> 00:01:17,000
	 * @throws IOException
	 * @throws InterruptedException
	 * @throws RuntimeException
	 */
	public static void convert(String inputFile, double frameRate,
			String timeInterval) throws IOException, InterruptedException,
			RuntimeException {
		if (inputFile.equals("")) {
			System.out.println("Illegal input filename.");
			throw new RuntimeException();
		}

		if (frameRate <= 0) {
			System.out.println("Illegal frame rate.");
			throw new RuntimeException();
		} else {
			FRAME_RATE = frameRate;
			MICRO_SECONDS_BETWEEN_FRAMES = (long) (Global.DEFAULT_PTS_PER_SECOND * (1 / FRAME_RATE));
		}

		String[] interval = timeInterval.split(" --> ");
		if (interval.length < 2) {
			System.out.println("Error in time interval.");
			throw new RuntimeException();
		}

		biList = new ArrayList<BufferedImage>();
		firstImage = "";
		mLastPtsWrite = Global.NO_PTS;
		mFirstFrame = -1;
		capturationDone = false;

		// Custom date format, eg. 00:00:10,500 --> 00:00:13,000"
		SimpleDateFormat format = new SimpleDateFormat(
				"yyyy-MM-dd HH:mm:ss,SSS");
		format.setTimeZone(TimeZone.getTimeZone("UTC"));

		try {
			startTime = ((format.parse("1970-01-01 " + interval[0])).getTime() * 1000);
			endTime = ((format.parse("1970-01-01 " + interval[1])).getTime() * 1000);
		} catch (ParseException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}

		IMediaReader mediaReader = ToolFactory.makeReader(inputFile);
		mediaReader
				.setBufferedImageTypeToGenerate(BufferedImage.TYPE_3BYTE_BGR);
		mediaReader.addListener(new ImageSnapListener());

		// Create output stream
		outputFilePrefix = inputFile.split(".mp4")[0];
		outputFilePrefix += "_" + startTime + "-" + endTime;
		ImageOutputStream output = new FileImageOutputStream(new File(
				outputFilePrefix + ".gif"));

		// Create new instance of GifSequenceWriter and start it in new thread
		Thread t = new Thread(new GifSequenceWriter(output, 5,
				(int) (1000 / FRAME_RATE), true, biList));
		t.start();

		// read out the contents of the media file and
		// dispatch events to the attached listener
		try {
			while (mediaReader.readPacket() == null && !capturationDone) {
				if (biList.size() > (100 / RESIZE_FACTOR)) {
					Thread.sleep(3000);
				}
			}
		} catch (RuntimeException e) {
			// Do nothing
			System.out.println("Runtime exception");
			e.printStackTrace();
		}
		
		while (t.isAlive()) {
			// Wait maximum of 3 second
			// for GifSequenceWriter thread
			// to finish.
			t.join(3000);
		}
		
		output.close();
	}

	/**
	 * Parses time frames from an SRT file based on given segment numbers
	 * 
	 * @param inputSRT
	 *            SRT file to be parsed
	 * @param segmentNumbers
	 *            List of subtitle segment numbers Can be given in the form
	 *            "x-y" or one by one
	 * @throws IOException
	 * @return timeFrames List of time frames parsed from the SRT
	 */
	public static List<String> parseSRT(String inputSRT,
			List<String> segmentNumbers) {

		BufferedReader SRTreader = null;
		String timeFrame = null;
		String combinedTimeFrame;
		int elementIndex;

		List<String> segments = new ArrayList<>();
		List<String> multipleSegments = new ArrayList<>();
		List<String> timeFrames = new ArrayList<>();
		List<String> longTimeFrames = new ArrayList<>();
                
                // Go through the list of segment numbers
                // Split the input if it contains "-" and save the segment numbers to lists
		for (String item : segmentNumbers) {
			if (item.contains("-")) {
				multipleSegments.add(item.split("-")[0]);
				multipleSegments.add(item.split("-")[1]);
			} else
				segments.add(item);
		}

		try {
			FileReader file = new FileReader(inputSRT);
			SRTreader = new BufferedReader(file);

			String line;
                        
                        // Read the srt file line by line and search for the right segments
			while ((line = SRTreader.readLine()) != null) {
				if (segments.contains(line)) {
					timeFrame = SRTreader.readLine();
					timeFrames.add(timeFrame);
				}
				if (multipleSegments.contains(line)) {
					elementIndex = multipleSegments.indexOf(line);
					timeFrame = SRTreader.readLine();
					longTimeFrames.add(timeFrame);
					if ((longTimeFrames.size() & 1) == 0) {
						combinedTimeFrame = combineFrames(
                                                                    longTimeFrames.get(elementIndex - 1),
                                                                    longTimeFrames.get(elementIndex));
						timeFrames.add(combinedTimeFrame);
					}
				}
			}
			SRTreader.close();

		} catch (IOException e) {
			e.printStackTrace();
		}
		return timeFrames;
	}
        /**
	 * Combines two separate time frames' start and end times into one time frame
	 * 
	 * @param timeFrame1
	 *            First time frame (e.g. "00:00:05,000 --> 00:00:09,693")
	 * @param timeFrame2
	 *            Second time frame (e.g. "00:00:10,000 --> 00:00:11,000")
	 * @return Combined time frame in string format (e.g. "00:00:05,000 --> 00:00:11,000")
	 */
	private static String combineFrames(String timeFrame1, String timeFrame2) {
		String combinedTimeFrame;
		String startTime;
		String stopTime;

		startTime = timeFrame1.split(" --> ")[0];
		stopTime = timeFrame2.split(" --> ")[1];

		combinedTimeFrame = startTime + " --> " + stopTime;

		return combinedTimeFrame;
	}

	/**
	 * Nested listener class for listening PictureEvents from IMediaReader.
	 * 
	 */
	private static class ImageSnapListener extends MediaListenerAdapter {

		public void onVideoPicture(IVideoPictureEvent event) {
			if (event.getStreamIndex() != mVideoStreamIndex) {
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
			if (event.getTimeStamp() - mFirstFrame >= startTime
					&& event.getTimeStamp() - mFirstFrame <= endTime) {
				// if uninitialized, back date mLastPtsWrite to get the very
				// first frame
				if (mLastPtsWrite == Global.NO_PTS)
					mLastPtsWrite = event.getTimeStamp()
							- MICRO_SECONDS_BETWEEN_FRAMES;

				// if it's time to write the next frame
				if (event.getTimeStamp() - mLastPtsWrite >= MICRO_SECONDS_BETWEEN_FRAMES) {
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

		/**
		 * This method adds BufferedImage image to the buffer to wait for
		 * writing to GIF sequence. Also the resizing can be done by defining
		 * RESIZE_FACTOR.
		 * 
		 * @param image
		 *            BufferedImage to be written to buffer
		 */
		private void addImageToBuffer(BufferedImage image) {
			int w, h;
			BufferedImage resized;
			w = image.getWidth();
			h = image.getHeight();
			if (RESIZE_FACTOR != 1) {
				resized = new BufferedImage((int) (RESIZE_FACTOR * w),
						(int) (RESIZE_FACTOR * h), BufferedImage.TYPE_INT_ARGB);
				AffineTransform at = new AffineTransform();
				at.scale(RESIZE_FACTOR, RESIZE_FACTOR);
				AffineTransformOp scaleOp = new AffineTransformOp(at,
						AffineTransformOp.TYPE_BILINEAR);
				resized = scaleOp.filter(image, resized);
			} else {
				resized = image;
			}
			biList.add(resized);
			if (firstImage.equals("") == true) {
				firstImage = dumpImageToFile(resized, THUMB);
				if (masterGifName.equals("") == true) {
					masterGifThumb = resized;
					//masterGifName = dumpImageToFile(resized, MASTER);
				}
				masterGifList.add(firstImage);
			}
		}

		/**
		 * This method dumps BufferedImage to the png file.
		 * 
		 * @param image
		 *            BufferedImage to be written to file.
		 * @return String containing filename of the written imagefile.
		 */
		private String dumpImageToFile(BufferedImage image, int type) {

			try {
				String outputFilename = "";
				if (type == THUMB) {
				outputFilename = outputFilePrefix + "_thumbnail.png";
				} else if (type == MASTER) {
					//String[] arrays = outputFilePrefix.split("_");// + "_master_thumb";
					outputFilename = outputFilePrefix + "_master_thumbnail.png";
				}
				
				ImageIO.write(image, "png", new File(outputFilename));
				return outputFilename;
			} catch (IOException e) {
				e.printStackTrace();
				return null;
			}
		}
	}
}
