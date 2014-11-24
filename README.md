SRW MX Portable IM_ADD Texture Extractor by Dashman
-------------------------------------

This program allows you to extract and reinsert the raw textures inside IM_ADD.BIN. You'll need to have Java installed in your computer to operate this.

How to extract:

1) Extract IM_ADD.BIN to the folder with the program.
2) In a command / shell window (or using a batch / script file), execute this:

java -jar im_extract.jar -e

3) The textures will be generated in a IM_ADD.BIN_extracted subfolder.

How to insert:

1) Put the program, the BIN file and all the BMP files in the same directory.
2) Execute

java -jar im_extract.jar -i

3) The extracted files will be re-inserted into IM_ADD.BIN.


IMPORTANT NOTES:

* Don't change the names of files. The program looks for IM_ADD.BIN and RAW_*.bmp files. If those files are named differently, they'll be ignored.

* All extrated textures are indexed, and should stay indexed when you re-insert them. You can change the palettes for all files to your liking and will be shown like that ingame, but don't change their palette size (always 256 colours).