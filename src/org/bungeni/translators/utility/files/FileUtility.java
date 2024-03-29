package org.bungeni.translators.utility.files;

//~--- non-JDK imports --------------------------------------------------------

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.channels.FileChannel;
import javax.xml.transform.stream.StreamSource;
import org.bungeni.translators.globalconfigurations.GlobalConfigurations;
import org.bungeni.translators.utility.runtime.CloseHandle;
import org.xml.sax.InputSource;

/**
 * This class supplies several method useful for the management of the File documents
 *
 */
public class FileUtility {

    /* The instance of this FileUtility object */
    private static FileUtility instance = null;

    /* This is the logger */
    private static org.apache.log4j.Logger log =
        org.apache.log4j.Logger.getLogger("org.bungeni.translators.utility.files.FileUtility");

    /**
     * The system line separator string is generated statically,
     * we dont hardcode for different platforms but determine the line separator
     * on the fly
     */
    public static final String LINE_SEPARATOR;

    static {

        // get the line separator for the platform
        StringWriter buf = new StringWriter(4);
        PrintWriter  out = new PrintWriter(buf);

        out.println();
        LINE_SEPARATOR = buf.toString();
    }

    /**
     * Private constructor used to create the FileUtility instance
     */
    private FileUtility() {}

    /**
     * Get the current instance of the FileUtility class
     * @return the Utility instance
     */
    public static FileUtility getInstance() {

        // if the instance is null create a new instance
        if (instance == null) {

            // create the instance
            instance = new FileUtility();
        }

        // otherwise return the instance
        return instance;
    }

    /**
     * Write the File at the given path to a String
     * 30th July - fixed to use bufferedReader to prevent encoding problems
     * @param aFilePath the path of the file to retrieve as a String
     * @return the String representation of the file
     * @throws IOException
     */
    public String FileToString(String aFilePath) throws IOException {
        FileReader     fileReader     = null;
        BufferedReader bufferedReader = null;
        StringBuilder  textFromFile   = new StringBuilder();

        try {
            fileReader     = new FileReader(aFilePath);    // throws FileNotFoundException
            bufferedReader = new BufferedReader(fileReader);

            // Read through the entire file
            String currentLineFromFile = bufferedReader.readLine();    // throws IOException
            int    i                   = 0;

            while (currentLineFromFile != null) {
                if (i == 0) {
                    textFromFile.append(currentLineFromFile);
                } else {

                    // Add a carriage return (line break) to preserve the file formatting.
                    textFromFile.append(LINE_SEPARATOR).append(currentLineFromFile);
                }

                currentLineFromFile = bufferedReader.readLine();    // throws IOException
            }

            return textFromFile.toString();
        } catch (IOException ioException) {
            log.error("FileToString: file not found ", ioException);
        } finally {
            // Good practice: Close the readers to free up any resources.
            CloseHandle.closeQuietly(bufferedReader);
            CloseHandle.closeQuietly(fileReader);
        }

        return textFromFile.toString();
    }

    /**
     * Write the given content to the file at the given path
     * @param aFilePath the path of the file to create
     * @param aFileContent the content of the file to create
     * @throws IOException
     */
    public void StringToFile(String aFilePath, String aFileContent) throws IOException {
        // create the buffered Writer
        BufferedWriter out = new BufferedWriter(
                    new OutputStreamWriter(
                        new FileOutputStream(aFilePath),
                        "UTF-8"
                    )
                );
        try {
            // write the content of the File
            out.write(aFileContent);
            out.flush();
        } catch (Exception ex) {
            log.error("Error while writing to :" + aFilePath, ex);
        } finally {
            CloseHandle.closeQuietly(out);
        }
    }

    /**
     * Make a copy of the file using the NIO channel API
     * @param in source file to copy
     * @param out target copy
     * @throws IOException
     */
    public File copyFile(File in, File out) throws IOException {
        FileChannel inChannel  = new FileInputStream(in).getChannel();
        FileChannel outChannel = new FileOutputStream(out).getChannel();

        copyChannel(inChannel, outChannel);
        return out;
    }

    /**
     * transfer bytes from input channel to output channel
     * @param inChannel
     * @param outChannel
     * @throws IOException
     */
    private void copyChannel(FileChannel inChannel, FileChannel outChannel) throws IOException {
        try {
            inChannel.transferTo(0, inChannel.size(), outChannel);
        } catch (IOException e) {
            throw e;
        } finally {
            CloseHandle.closeQuietly(inChannel);
            CloseHandle.closeQuietly(outChannel);
        }
    }

    /**
     * Transfer bytes from FileInputStream to outputChannel
     * @param fis
     * @param out
     * @throws IOException
     */
    public void copyFile(FileInputStream fis, File out) throws IOException {
        FileChannel inChannel  = fis.getChannel();
        FileChannel outChannel = new FileOutputStream(out).getChannel();

        copyChannel(inChannel, outChannel);
    }

    /**
     * Returns a bufferedreader for a file path
     * @param sPath
     * @return
     * @throws FileNotFoundException
     */
    public BufferedReader BufferedFileReader(String sPath) throws FileNotFoundException, UnsupportedEncodingException {
        File fFile = new File(sPath);
        return BufferedFileReader(fFile);
    }

    /**
     * Returns a buffered reader for a file handle
     * @param fFile
     * @return
     * @throws FileNotFoundException
     */
    public BufferedReader BufferedFileReader(File fFile) throws FileNotFoundException, UnsupportedEncodingException {
        BufferedReader bReader = null;
        InputStreamReader ir = new InputStreamReader(new FileInputStream(fFile), "UTF-8");
        bReader = new BufferedReader(ir);
        return bReader;
    }

    /**
     * Returns a buffered reader for an inputstream
     * @param iStream
     * @return
     */
    public BufferedReader BufferedFileReader(InputStream iStream) {
        return new BufferedReader(new InputStreamReader(iStream));
    }

    
    public BufferedWriter BufferedFileWriter(File fOut) throws UnsupportedEncodingException, FileNotFoundException {
        BufferedWriter out = new BufferedWriter(
                        new OutputStreamWriter(
                            new FileOutputStream(fOut), "UTF-8"
                            )
                        );
        return out;
    }
    /**
     * Returns a file path as an input source
     * @param sPath
     * @return
     * @throws FileNotFoundException
     */
    public InputSource FileAsInputSource(String sPath) throws FileNotFoundException, UnsupportedEncodingException {
        InputSource    iFileSource = null;
        BufferedReader bReader     = BufferedFileReader(sPath);

        if (bReader != null) {
            iFileSource = new InputSource(bReader);
        }
        
        return iFileSource;
    }

    /**
     * Converts an InputStream to an InputSource
     * @param is
     * @return
     */
    public InputSource StreamAsInputSource(InputStream is) {
        InputSource    iFileSource = null;
        BufferedReader bReader     = BufferedFileReader(is);

        if (bReader != null) {
            iFileSource = new InputSource(bReader);
        }

        return iFileSource;
    }

    /**
     * Takes a File handle and returns the file contents as an InputSource
     * @param fFile
     * @return
     * @throws FileNotFoundException
     */
    public InputSource FileAsInputSource(File fFile) throws FileNotFoundException, UnsupportedEncodingException {
        InputSource    iFileSource = null;
        BufferedReader bReader     = BufferedFileReader(fFile);

        if (bReader != null) {
            iFileSource = new InputSource(bReader);
        }

        return iFileSource;
    }

    /**
     * Takes a path to a file and returns it as a StreamSource
     * @param sPath
     * @return
     * @throws FileNotFoundException
     */
    public StreamSource FileAsStreamSource(String sPath) throws FileNotFoundException, UnsupportedEncodingException {
        StreamSource   sSource = null;
        BufferedReader bReader = BufferedFileReader(sPath);

        sSource = new StreamSource(bReader);

        return sSource;
    }

    /**
     * Takes a File handle to a file and returns it as a StreamSource
     * @param sPath
     * @return
     * @throws FileNotFoundException
     */
    public StreamSource FileAsStreamSource(File fPath) throws FileNotFoundException, UnsupportedEncodingException {
        StreamSource   sSource = null;
        BufferedReader bReader = BufferedFileReader(fPath);

        sSource = new StreamSource(bReader);
        
        return sSource;
    }

    public enum HREF_TYPE {
        FILE_URL,
        FILE_URI,
        PATH
    };

    public HREF_TYPE getHrefType(String sHref) {
        if (sHref.startsWith("file://")) {
            return HREF_TYPE.FILE_URL;
        }
        if (sHref.startsWith("file:/")){
            return HREF_TYPE.FILE_URI;
        } else {
            return HREF_TYPE.PATH;
        }
    }

/**
 * This API resolves a HREF in a step.
 * Both relative hrefs and absolute URIs are accepted, both are resolved to File handles
 * @param stepHref
 * @return
 */
    public File resolveHref(String stepHref) {
      File fstepFile = null;
      HREF_TYPE hrefType = getHrefType(stepHref);
        if (hrefType.equals(HREF_TYPE.FILE_URI) || hrefType.equals(HREF_TYPE.FILE_URL)) {
            URI uriFile;
            try {
                uriFile = new URI(stepHref);
                fstepFile = new File(uriFile);
            } catch (URISyntaxException ex) {
               log.error("Wrong URI syntax : " + stepHref, ex);
            }
        } else {
           fstepFile = new File(
                   GlobalConfigurations.getApplicationPathPrefix() +
                   stepHref
                   );
        }
      return fstepFile;
  }

}
