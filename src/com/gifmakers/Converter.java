package com.gifmakers;

import java.awt.geom.AffineTransform;
import java.awt.image.AffineTransformOp;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.BufferedReader;
import java.io.FileReader;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
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

	public static volatile boolean capturationDone = false;
	public static double RESIZE_FACTOR = 1;
	public static double FRAME_RATE;
	private static String outputFilePrefix;

	static String firstImage ;
	public static ArrayList<BufferedImage> biList;
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
		
		List<String> srtSegments = new ArrayList<>();
		List<String> timeIntervals;
		String videoFile, srtFile = "";
		
		if (args.length < 3) {
			// Illegar arguments
			System.out.println("Illegal command line arguments.");
			//TODO Write instructions
			System.out.println("Some instructions for usage...");
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
			
			//Create srt segment array
			for (int i=2; i<args.length; i++) {
				//Check first that arguments are legal
				if (args[i].matches("\\d+(-\\d+)?")) {
					// This regex matches for ecample following: "1", "11", "1-1", "11-11"
					srtSegments.add(args[i]);
				} else {
					System.out.println(args[i] + " is not in srt segment number format.");
					return;
				}
			}
			
		}
		
		timeIntervals = parseSRT(srtFile, srtSegments);
		try {
			for (int i=0; i<timeIntervals.size(); i++) {
				convert(videoFile, 10, timeIntervals.get(i));
			}
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
	public static void convert(String inputFile,
			double frameRate, String timeInterval) throws IOException,
			InterruptedException, RuntimeException {
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
				if (biList.size() > (100 / RESIZE_FACTOR)) {
					Thread.sleep(3000);
				}
			}
		} catch (RuntimeException e) {
			// Do nothing
		}

		Date t1 = new Date();
		System.out
				.println("Frames captured. Waiting for other thread to complete. It took "
						+ (t1.getTime() - t0.getTime()) + "ms.");
		while (t.isAlive()) {
			// Wait maximum of 3 second
			// for GifSequenceWriter thread
			// to finish.
			t.join(3000);
		}
		Date t2 = new Date();
		System.out.println("Finally! It took " + (t2.getTime() - t0.getTime())
				+ "ms.");

		output.close();
	}
        
        /**
	 * Parses time frames from an SRT file based on given segment numbers
	 * 
	 * @param inputSRT
	 *            SRT file to be parsed
	 * @param segmentNumbers
	 *            List of subtitle segment numbers
         *            Can be given in the form "x-y" or one by one
	 * @throws IOException
	 * @return timeFrames
         *            List of time frames parsed from the SRT
	 */
        public static List<String> parseSRT(String inputSRT, List<String> segmentNumbers){

            BufferedReader SRTreader = null;
            String timeFrame = null;
            String totalTimeFrame;
            int elementIndex;

            List<String> segments = new ArrayList<>();
            List<String> multipleSegments = new ArrayList<>();
            List<String> timeFrames = new ArrayList<>();
            List<String> longTimeFrames = new ArrayList<>();


            for (String item : segmentNumbers){
                if (item.contains("-")){
                    multipleSegments.add(item.split("-")[0]);
                    multipleSegments.add(item.split("-")[1]);
                }
                else
                    segments.add(item);
            }

            try {
                FileReader file = new FileReader(inputSRT);
                SRTreader = new BufferedReader(file);

                String line;

                while(( line = SRTreader.readLine()) != null)
                {
                    if (segments.contains(line)){
                        timeFrame = SRTreader.readLine();
                        timeFrames.add(timeFrame);
                    }
                    if (multipleSegments.contains(line)){
                        elementIndex = multipleSegments.indexOf(line);
                        timeFrame = SRTreader.readLine();
                        longTimeFrames.add(timeFrame);
                        if ((longTimeFrames.size()&1) == 0){
                            totalTimeFrame = timeFrameParse(longTimeFrames.get(elementIndex-1),longTimeFrames.get(elementIndex));
                            timeFrames.add(totalTimeFrame);
                        }
                    }
                }
                SRTreader.close();

            } catch (IOException e) {
                e.printStackTrace();
            }
            return timeFrames;
        }
        
        private static String timeFrameParse(String timeFrame1, String timeFrame2)
        {
        	System.out.println(timeFrame1 + " " + timeFrame2);
            String totalTimeFrame;
            String startTime;
            String stopTime;

            startTime = timeFrame1.split(" --> ")[0];
            stopTime = timeFrame2.split(" --> ")[1];

            totalTimeFrame = startTime + " --> " + stopTime;
            
            return totalTimeFrame;
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
				firstImage = dumpImageToFile(resized);
			}
		}

		/**
		 * This method dumps BufferedImage to the png file.
		 * 
		 * @param image
		 *            BufferedImage to be written to file.
		 * @return String containing filename of the written imagefile.
		 */
		private String dumpImageToFile(BufferedImage image) {

			try {
				String outputFilename = outputFilePrefix + "_thumbnail.png";
				ImageIO.write(image, "png", new File(outputFilename));
				return outputFilename;
			} catch (IOException e) {
				e.printStackTrace();
				return null;
			}
		}
	}
}
