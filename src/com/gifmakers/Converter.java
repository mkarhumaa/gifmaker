package com.gifmakers;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;


import javax.imageio.ImageIO;
import javax.imageio.stream.FileImageOutputStream;
import javax.imageio.stream.ImageOutputStream;

import com.xuggle.mediatool.IMediaReader;
import com.xuggle.mediatool.MediaListenerAdapter;
import com.xuggle.mediatool.ToolFactory;
import com.xuggle.mediatool.event.IVideoPictureEvent;
import com.xuggle.xuggler.Global;

public class Converter {
    
	
    public static final double SECONDS_BETWEEN_FRAMES = 0.25;

    private static final String inputFilename = "/home/matias/Downloads/000.ts";
    private static final String outputFilePrefix = "/home/matias/MMS/mysnapshot";
    
    static ArrayList<String> imageList;

    // The video stream index, used to ensure we display frames from one and
    // only one video stream from the media container.
    private static int mVideoStreamIndex = -1;
    
    // Time of last frame write
    private static long mLastPtsWrite = Global.NO_PTS;
    
    public static final long MICRO_SECONDS_BETWEEN_FRAMES = 
        (long)(Global.DEFAULT_PTS_PER_SECOND * SECONDS_BETWEEN_FRAMES);

    public static void main(String[] args) throws IOException {
    	imageList = new ArrayList<String>();
        IMediaReader mediaReader = ToolFactory.makeReader(inputFilename);

        // stipulate that we want BufferedImages created in BGR 24bit color space
        mediaReader.setBufferedImageTypeToGenerate(BufferedImage.TYPE_3BYTE_BGR);
        
        mediaReader.addListener(new ImageSnapListener());

        // read out the contents of the media file and
        // dispatch events to the attached listener
        System.out.println("Starting to extract frames from video file.");
        try {
        	while (mediaReader.readPacket() == null) ;
        } catch (RuntimeException e) {
        	// Do nothing
        }

        System.out.printf("Starting to create animated gif\n");
        BufferedImage firstImage = ImageIO.read(new File(imageList.get(0)));
        System.out.printf("Create outputstream\n");
        // create a new BufferedOutputStream with the last argument
        ImageOutputStream output = 
          new FileImageOutputStream(new File("/home/matias/MMS/output.gif"));

        // create a gif sequence with the type of the first image, 1 second
        // between frames, which loops continuously
        GifSequenceWriter writer = 
          new GifSequenceWriter(output, firstImage.getType(), 250, false);

        // write out the first image to our sequence...
        System.out.printf("Starting to write to sequence\n");
        
        writer.writeToSequence(firstImage);
        for(int i=1; i<imageList.size()-1; i++) {
        	//System.out.printf("wrote image to sequence\n");
          BufferedImage nextImage = ImageIO.read(new File(imageList.get(i)));
          writer.writeToSequence(nextImage);
        }
        System.out.printf("All done!\n");
        writer.close();
        output.close();

    }

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

            // if uninitialized, back date mLastPtsWrite to get the very first frame
            if (mLastPtsWrite == Global.NO_PTS)
                mLastPtsWrite = event.getTimeStamp() - MICRO_SECONDS_BETWEEN_FRAMES;

            // if it's time to write the next frame
            if (event.getTimeStamp() - mLastPtsWrite >= 
                    MICRO_SECONDS_BETWEEN_FRAMES) {
                                
                String outputFilename = dumpImageToFile(event.getImage());

                // indicate file written
                double seconds = ((double) event.getTimeStamp()) / 
                    Global.DEFAULT_PTS_PER_SECOND;
                //System.out.printf(
                //        "at elapsed time of %6.3f seconds wrote: %s\n",
                 //       seconds, outputFilename);

                // update last write time
                mLastPtsWrite += MICRO_SECONDS_BETWEEN_FRAMES;
            }

        }
        
        private String dumpImageToFile(BufferedImage image) {
            try {
                String outputFilename = outputFilePrefix + 
                     System.currentTimeMillis() + ".png";
                imageList.add(outputFilename);
                //if (firstGifName.equals("") == true) {
                //	firstGifName = outputFilename;
                //}
                ImageIO.write(image, "png", new File(outputFilename));
                return outputFilename;
            } 
            catch (IOException e) {
                e.printStackTrace();
                return null;
            }
        }

    }

}
