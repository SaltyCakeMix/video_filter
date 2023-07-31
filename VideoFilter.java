import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.GraphicsEnvironment;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;

import javax.imageio.ImageIO;

import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.core.Size;
import org.opencv.core.Scalar;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import org.opencv.videoio.VideoCapture;
import org.opencv.videoio.VideoWriter;
import org.opencv.videoio.Videoio;

public class VideoFilter {

    public static void main(String[] args) throws Exception {
    	long startTime = System.nanoTime();
    	
    	// Loads video
    	System.loadLibrary(Core.NATIVE_LIBRARY_NAME);
        final String filePath = args[0];
    	final VideoCapture cap = new VideoCapture(filePath);
    	
    	// Variables
    	final int width = (int) cap.get(Videoio.CAP_PROP_FRAME_WIDTH);
    	final int height = (int) cap.get(Videoio.CAP_PROP_FRAME_HEIGHT);
        final float aspectRatio = width / (float) height;
        final float FPS = (float) cap.get(Videoio.CAP_PROP_FPS);
        final String frameTotal = "/" + Integer.toString((int) cap.get(Videoio.CAP_PROP_FRAME_COUNT));
        int frameCurrent = 1;

        final float fontAspectRatio = 0.6f; // Completely arbitrary value
    	final int renderH = Integer.parseInt(args[1]);
        final int renderW = (int) (renderH * aspectRatio / fontAspectRatio);
    	final int outH = Integer.parseInt(args[2]);
    	final int outW = (int) (outH * aspectRatio);

    	final int fontH = outH / renderH;
    	final int fontW = outW / renderW;
    	final Font font = Font.createFont(Font.TRUETYPE_FONT, new File(args[3])).deriveFont(fontH * 1.2f); // Another completely arbitrary value
        final GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
        ge.registerFont(font);
        
        // Constructing charSet
    	final String chars = args[4];
    	final Mat[] charSet = new Mat[chars.length()];
    	int minLeftBuffer = fontW;
    	int minUpBuffer = fontH;
    	final BufferedImage character = new BufferedImage(fontW, fontH, BufferedImage.TYPE_3BYTE_BGR);
    	final Graphics g = character.getGraphics();
    	g.setFont(font);
    	
    	// First generates a charSet without offset to calculate the "dead-space" on the left and top
    	for(int i = 0; i < chars.length(); i++) {
    		g.setColor(new Color(255, 255, 255));
    		g.drawString(Character.toString(chars.charAt(i)), 0, fontH);
    		
    		final byte[] pixels = ((DataBufferByte) character.getRaster().getDataBuffer()).getData();

            // Shifts charSet offset to last pixel that is black
    		for(int y = 0; y < minUpBuffer; y++) {
    			for(int x = 0; x < fontW; x++) {
    				int index = (y*fontW + x) * 3;
    				if(pixels[index] != 0) { // Only need to check R since the color is white
    					minUpBuffer = y;
    					break;
    				};
    			};
    		};
    		for(int x = 0; x < minLeftBuffer; x++) {
    			for(int y = 0; y < fontH; y++) {
    				int index = (y*fontW + x) * 3;
    				if(pixels[index] != 0) {
    					minLeftBuffer = x;
    					break;
    				};
    			};
    		};
    		
    		g.setColor(new Color(0, 0, 0));
    		g.fillRect(0, 0, fontW, fontH);
    	};
    	
    	// Then generates a charSet with offset
    	for(int i = 0; i < chars.length(); i++) {
    		g.setColor(new Color(255, 255, 255));
    		g.drawString(Character.toString(chars.charAt(i)), -minLeftBuffer, fontH - minUpBuffer);
    		charSet[i] = ImgToMat(character);
    		g.setColor(new Color(0, 0, 0));
    		g.fillRect(0, 0, fontW, fontH);
    	};
    	g.dispose();
    	character.flush();
        
    	Mat frame = new Mat();
    	Mat scaledMat = new Mat(renderH, renderW, CvType.CV_8UC3);
    	Mat grayMat = new Mat(renderH, renderW, CvType.CV_8UC1);
    	Mat indexMat = new Mat(renderH, renderW, CvType.CV_8SC1);
        byte[] matrixMem = new byte[renderW*renderH];
        Mat frameOut = new Mat(outH, outW, CvType.CV_8UC3);

        final VideoWriter writer = new VideoWriter(args[5], VideoWriter.fourcc('m', 'p', '4', 'v'), FPS, new Size(outW, outH));
        final Scalar brightnessScale = new Scalar((chars.length() - 1) / 256.0);
        while(cap.read(frame)){
        	// Scales frame size
            Imgproc.resize(frame, scaledMat, scaledMat.size(), 0, 0, Imgproc.INTER_NEAREST);

            // Converts to grayscale
            Imgproc.cvtColor(scaledMat, grayMat, Imgproc.COLOR_RGB2GRAY);

            // Maps brightness to character indices
            Core.multiply(grayMat, brightnessScale, indexMat);

            // Prints characters to new frames
            final byte[] matrix = new byte[renderW*renderH];
        	indexMat.get(0, 0, matrix);
        	for(int x = 0; x < renderW; x++) {
        		for(int y = 0; y < renderH; y++) {
                    int index = y*renderW + x;
        			if(matrixMem[index] != matrix[index]) {
        				int dstX = x * outW / renderW;
            			int dstY = y * outH / renderH;
        				charSet[matrix[index]].copyTo(frameOut.rowRange(dstY, dstY+fontH).colRange(dstX, dstX+fontW));
        			};
        		};
        	};

    		// Saves image
    		writer.write(frameOut);
        	matrixMem = matrix;
            if(frameCurrent % 500 == 0) System.out.printf("Frame %d%s processed\n", frameCurrent, frameTotal);
        	frameCurrent++;
        }
        cap.release();
        writer.release();
        frame.release();
        scaledMat.release();
        grayMat.release();
        indexMat.release();
        frameOut.release();
        
        double secondsElapsed = (System.nanoTime() - startTime) / 1000000000.0;
        System.out.println(Double.toString(secondsElapsed) + " seconds elapsed");
    }
    
    private static Mat ImgToMat(BufferedImage image) throws IOException {
        ByteArrayOutputStream byteOS = new ByteArrayOutputStream();
        ImageIO.write(image, "jpg", byteOS);
        byteOS.flush();
        return Imgcodecs.imdecode(new MatOfByte(byteOS.toByteArray()), Imgcodecs.IMREAD_UNCHANGED);
    }
}