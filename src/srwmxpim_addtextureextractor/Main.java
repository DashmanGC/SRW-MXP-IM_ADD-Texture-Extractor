/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package srwmxpim_addtextureextractor;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author Jonatan
 */
public class Main {

    static String bin_file = "IM_ADD.BIN";
    static long start_of_files = 0x800;
    static long end_of_files = 0x1fd800;
    static long start_of_raw = 0x2b8800;
    static long end_of_raw = 0x1665800;
    static RandomAccessFile f;
    static int tex_counter = 0;

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        // TODO code application logic here

        if (args.length == 1){
            if (args[0].equals("-i")){ // Insert files
                try{
                    insertRaw();

                    return; // END
                }catch (IOException ex) {
                    System.err.println("ERROR: Couldn't read file.");   // END
                    Logger.getLogger(Main.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
            else{
                if (args[0].equals("-e")){  // Extract files
                    try{
                        extractRaw();

                        return; // END
                    }catch (IOException ex) {
                        System.err.println("ERROR: Couldn't read file.");   // END
                        Logger.getLogger(Main.class.getName()).log(Level.SEVERE, null, ex);
                    }

                }
                else{
                    System.out.println("ERROR: Wrong number of parameters: " + args.length);
                    System.out.println("EXTRACT:\n java -jar im_extract -e");
                    System.out.println("INSERT:\n java -jar im_extract -i");
                    return;
                }
            }
        }
    }


    // Takes a 4-byte hex little endian and returns its int value
    public static int byteSeqToInt(byte[] byteSequence){
        if (byteSequence.length != 4)
            return -1;

        int value = 0;
        value += byteSequence[0] & 0xff;
        value += (byteSequence[1] & 0xff) << 8;
        value += (byteSequence[2] & 0xff) << 16;
        value += (byteSequence[3] & 0xff) << 24;
        return value;
    }

    // Extract raw images in IM_ADD.BIN
    public static void extractRaw() throws IOException{
        RandomAccessFile file = new RandomAccessFile(bin_file, "r");

        int width = 512;
        int height = 274;

        long offset = start_of_raw ;

        byte[] CLUT = new byte[1024];
        byte[] image = new byte[0x22400];

        file.seek(offset);
        int counter = 0;

        while (offset < end_of_raw){
            file.read(CLUT);

            offset += CLUT.length;

            // Colours in the CLUT are in the RGBA format. BMP uses BGRA format, so we need to swap Rs and Bs
            for (int i = 0; i < CLUT.length; i+= 4){
                byte swap = CLUT[i];
                CLUT[i] = CLUT[i+2];
                CLUT[i+2] = swap;
            }

            file.read(image);

            offset += image.length;

            // The image has to be flipped to show it properly in BMP
            byte[] pixels_R = image.clone();
            for (int i = 0, j = image.length - width; i < image.length; i+=width, j-=width){
                for (int k = 0; k < width; ++k){
                    image[i + k] = pixels_R[j + k];
                }
            }

            writeBMP("RAW", CLUT, image, width, height, (byte) 8, counter);

            counter++;

            offset += 1024; // padding
        }

        file.close();
    }

    // Insert raw images into IM_ADD.BIN
    public static void insertRaw() throws IOException{
        File directory = new File("."); // current folder

        RandomAccessFile f_bin = new RandomAccessFile(bin_file, "rw");
        long bin_offset = start_of_raw;

        // Find the BMP files in the folder
        File[] listOfFiles = directory.listFiles(new FilenameFilter(){
            public boolean accept(File dir, String filename) {  // I *THINK* they're in alphanumeric order
                return ( filename.startsWith("RAW_") &&
                        ( filename.endsWith(".BMP") || filename.endsWith(".bmp") ) );
            }
        });

        // Abort if the number of files found is different than 80
        if (listOfFiles.length != 145){
            System.err.println("ERROR: Number of Font files incorrect. There should be 145 RAW_xx.bmp files.");
            System.out.println("Found " + listOfFiles.length + " files.");
            f_bin.close();
            return;
        }
        else{
            for (int num = 0; num < 145; num++){
                RandomAccessFile bmp_file = new RandomAccessFile(listOfFiles[num].getAbsolutePath(), "r");

                byte[] header = new byte[54];
                byte[] aux = new byte[4];
                byte[] CLUT = new byte[1024];
                byte[] pixels = new byte[0x22400];

                int offset; // Start of image data
                int width = 512;
                //int height = 274;

                // 1) Get the offset of the image data
                bmp_file.read(header);

                aux[0] = header[10];
                aux[1] = header[11];
                aux[2] = header[12];
                aux[3] = header[13];
                offset = byteSeqToInt(aux);

                // 2) Grab the CLUT
                bmp_file.seek(offset - 1024);

                bmp_file.read(CLUT);

                // Colours in the CLUT are in the RGBA format. BMP uses BGRA format, so we need to swap Rs and Bs
                for (int i = 0; i < CLUT.length; i+= 4){
                    byte swap = CLUT[i];
                    CLUT[i] = CLUT[i+2];
                    CLUT[i+2] = swap;
                }

                // 3) Grab the image data
                bmp_file.read(pixels);
                bmp_file.close();

                // Turn it upside down
                byte[] pixels_R = pixels.clone();
                int dimX = width;

                for (int i = 0, j = pixels.length - dimX; i < pixels.length; i+=dimX, j-=dimX){
                    for (int k = 0; k < dimX; ++k){
                        pixels[i + k] = pixels_R[j + k];
                    }
                }

                // 4) Overwrite the raw image
                f_bin.seek(bin_offset);

                //System.out.println("Writing at " + bin_offset);

                f_bin.write(CLUT);

                bin_offset += CLUT.length;

                f_bin.write(pixels);

                bin_offset += pixels.length;

                // I don't know why, but it doesn't need padding this time
                //bin_offset += 1024; // padding

                System.out.println(listOfFiles[num].getName() + " inserted successfully.");
            }

            f_bin.close();
        }
    }


    // Outputs a BMP file with the given data
    public static void writeBMP(String filename, byte[] CLUT, byte[] imageData, int width, int height, byte depth, int number){
        byte[] header = new byte[54];

        // Prepare the header
        // * All sizes are little endian

        // Byte 0: '42' (B) Byte 1: '4d' (M)
        header[0] = 0x42;
        header[1] = 0x4d;

        // Next 4 bytes: file size (header + CLUT + pixels)
        int file_size = 54 + CLUT.length + imageData.length;
        header[2] = (byte) (file_size & 0xff);
        header[3] = (byte) ((file_size >> 8) & 0xff);
        header[4] = (byte) ((file_size >> 16) & 0xff);
        header[5] = (byte) ((file_size >> 24) & 0xff);

        // Next 4 bytes: all 0
        header[6] = 0;
        header[7] = 0;
        header[8] = 0;
        header[9] = 0;

        // Next 4 bytes: offset to start of image (header + CLUT)
        int offset = file_size - imageData.length;
        header[10] = (byte) (offset & 0xff);
        header[11] = (byte) ((offset >> 8) & 0xff);
        header[12] = (byte) ((offset >> 16) & 0xff);
        header[13] = (byte) ((offset >> 24) & 0xff);

        // Next 4 bytes: 28 00 00 00
        header[14] = 40;
        header[15] = 0;
        header[16] = 0;
        header[17] = 0;

        // Next 4 bytes: Width
        header[18] = (byte) (width & 0xff);
        header[19] = (byte) ((width >> 8) & 0xff);
        header[20] = (byte) ((width >> 16) & 0xff);
        header[21] = (byte) ((width >> 24) & 0xff);

        // Next 4 bytes: Height
        header[22] = (byte) (height & 0xff);
        header[23] = (byte) ((height >> 8) & 0xff);
        header[24] = (byte) ((height >> 16) & 0xff);
        header[25] = (byte) ((height >> 24) & 0xff);

        // Next 2 bytes: 01 00 (number of planes in the image)
        header[26] = 1;
        header[27] = 0;

        // Next 2 bytes: bits per pixel ( 04 00 or 08 00 )
        header[28] = depth;
        header[29] = 0;

        // Next 4 bytes: 00 00 00 00 (compression)
        header[30] = 0;
        header[31] = 0;
        header[32] = 0;
        header[33] = 0;

        // Next 4 bytes: image size in bytes (pixels)
        header[34] = (byte) (imageData.length & 0xff);
        header[35] = (byte) ((imageData.length >> 8) & 0xff);
        header[36] = (byte) ((imageData.length >> 16) & 0xff);
        header[37] = (byte) ((imageData.length >> 24) & 0xff);

        // Next 12 bytes: all 0 (horizontal and vertical resolution, number of colours)
        header[38] = 0;
        header[39] = 0;
        header[40] = 0;
        header[41] = 0;
        header[42] = 0;
        header[43] = 0;
        header[44] = 0;
        header[45] = 0;
        header[46] = 0;
        header[47] = 0;
        header[48] = 0;
        header[49] = 0;

        // Next 4 bytes: important colours (= number of colours)
        header[50] = 0;
        header[51] = (byte)(CLUT.length / 4);
        header[52] = 0;
        header[53] = 0;

        // Check if folder with the name of the bin_file exists. If not, create it.
        String path = bin_file + "_extract";
        File folder = new File(path);
        if (!folder.exists()){
            boolean success = folder.mkdir();
            if (!success){
                System.err.println("ERROR: Couldn't create folder.");
                return;
            }
        }

        // Create the bmp file inside said folder
        String file_path = filename + "_";

        if (number < 100)
            file_path += "0";
        if (number < 10)
            file_path += "0";

        file_path += number + ".bmp";
        path += "/" + file_path;
        try {
            RandomAccessFile bmp = new RandomAccessFile(path, "rw");

            bmp.write(header);
            bmp.write(CLUT);
            bmp.write(imageData);

            bmp.close();

            System.out.println(file_path + " saved successfully.");
            tex_counter++;
        } catch (IOException ex) {
            System.err.println("ERROR: Couldn't write " + file_path);
            Logger.getLogger(Main.class.getName()).log(Level.SEVERE, null, ex);
        }

    }
}

