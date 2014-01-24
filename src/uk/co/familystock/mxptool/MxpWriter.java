package uk.co.familystock.mxptool;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.logging.Logger;
import java.util.zip.Deflater;

import org.joda.time.LocalDateTime;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.helpers.DefaultHandler;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;

/**
 * Class for creating Adobe MXP containers from MXI files.
 * 
 * @author bstock@google.com (Benjamin Stock)
 */
public class MxpWriter {
  
  public static final int MAX_SUPPORTED_MXP_VERSION = 4;
  public static final int DEFAULT_MXP_VERSION = 3;
  
  // Used to sanity check values when writing unsigned integers.
  private static final int MAX_VALUE_UINT_8  = 255;
  private static final int MAX_VALUE_UINT_16 = 65535;
  private static final long MAX_VALUE_UINT_32 = 4294967295L;
  
  // Amount of extra space allotted compression buffer to allow for increase in
  // data size.
  private static final int COMPRESSION_TOLERANCE_BYTES = 250;
  
  private static final String MXI_SCHEMA = "data/mxiSchema.xsd";
  
  private static final Logger logger = Logger.getLogger(MxpWriter.class.getName());
  
  // 8 consecutive null bytes are used to indicate the end of file.
  private static final byte[] FILE_TERMINATOR = new byte[] {0, 0, 0, 0, 0, 0, 0, 0};
  
  // The first 7 bytes after the MXP version (the very first byte) are always the same.
  private static final byte[] MXP_HEADER = new byte[] {0, 0, 0, 1, 0, 0, 0};
  
  private final int mxpVersion;
  private final File mxiFile;
  private List<String> mxiFilePaths = null;
  
  /**
   * Creates a {@code MxpWriter} using the given MXI file as its source and
   * using {@link #DEFAULT_MXP_VERSION} as the MXP version number.
   *
   * @param mxiFile the MXI file to use when creating the MXP
   */
  public MxpWriter(File mxiFile) {
    this(mxiFile, DEFAULT_MXP_VERSION);
  }

  /**
   * Creates a {@code MxpWriter} using the given MXI file as its source and 
   * using {@code mxpVersion} as the MXP version number.
   *
   * @param mxiFile the MXI file to use when creating the MXP
   * @param mxpVersion the MXP/MXI version number
   */
  public MxpWriter(File mxiFile, int mxpVersion) {
    Preconditions.checkNotNull(mxiFile, "MXI file must be not be null.");
    Preconditions.checkArgument(mxiFile.canRead(),
        "Unable to read MXI file '%s'.", mxiFile.getAbsolutePath());
    Preconditions.checkArgument(
        mxpVersion >= 1 && mxpVersion <= MAX_SUPPORTED_MXP_VERSION,
        "MXP version must be between 1 and %s, but was %s", MAX_SUPPORTED_MXP_VERSION, mxpVersion);
    this.mxiFile = mxiFile;
    this.mxpVersion = mxpVersion;
  }
  
  public int getMxpVersion() {
    return mxpVersion;
  }
  
  /**
   * Write an unsigned 8-bit integer.
   */
  private void writeUnsignedInt8(OutputStream out, int val) throws IOException {
    Preconditions.checkArgument(val <= MAX_VALUE_UINT_8,
        "Value for unsigned 8-bit integer too large, must not exceed 2^8-1 but got %s", val);
    out.write(val);
  }

  /**
   * Write an unsigned 16-bit integer in little-endian order.
   */
  private void writeUnsignedInt16(OutputStream out, int val) throws IOException {
    Preconditions.checkArgument(val <= MAX_VALUE_UINT_16,
        "Value for unsigned 16-bit integer too large, must not exceed 2^16-1 but got %s", val);
    out.write((byte) val);
    out.write((byte) (val >> 8));
  }

  /**
   * Write an unsigned 32-bit integer in little-endian order.
   */
  private void writeUnsignedInt32(OutputStream out, long val) throws IOException {
    Preconditions.checkArgument(val <= MAX_VALUE_UINT_32,
        "Value for unsigned 32-bit integer too large, must not exceed 2^32-1 but got %s", val);
    out.write((byte) val);
    out.write((byte) (val >> 8));
    out.write((byte) (val >> 16));
    out.write((byte) (val >> 24));
  }
  
  /**
   * Creates a MXP file by reading the MXI set for this writer and then 
   * locating and compressing each file referenced by the MXI.
   *
   * @param mxpFile the path to use for the new MXP file
   * @throws IOException if read/write errors occur
   * @throws MxiFormatException if there is an error parsing or validating the
   * MXI file
   */
  public void write(File mxpFile) throws IOException, MxiFormatException {
    Preconditions.checkNotNull(mxpFile, "Mxp file must not be null.");
    
    if (mxiFilePaths == null) {
      mxiFilePaths = parseMxi();
    }
    if (mxiFilePaths.size() == 0) {
      logger.warning("No file entries found, only file present will be MXI file");
    }
    
    File workingDir = mxiFile.getParentFile();
    
    
    try (BufferedOutputStream mxpArchive =
        new BufferedOutputStream(new FileOutputStream(mxpFile))) {
      writeUnsignedInt8(mxpArchive, getMxpVersion());
      mxpArchive.write(MXP_HEADER);
      
      writeFileToMxpArchive(mxpArchive, mxiFile.getName(), workingDir);
      for (String path : mxiFilePaths) {
        writeFileToMxpArchive(mxpArchive, path, workingDir);
      }
    }
  }
  
  @VisibleForTesting
  List<String> parseMxi() throws IOException, MxiFormatException {
    MxiHandler handler = new MxiHandler();
    try {
      XmlUtil.parseAndValidateDocument(mxiFile, MXI_SCHEMA, handler);
    } catch (SAXException e) {
      throw new MxiFormatException("Invalid MXI file. Make sure it contains valid XML "
          + "and includes an XML declaration with the correct file encoding.", e);
    }
      
    List<String> parsedFilePaths = handler.getInstallFiles();
    if (handler.getWarnings().size() > 0) {
      for (String warning : handler.getWarnings()) {
        logger.warning(warning);
      }
    }
      
    if (handler.getErrors().size() > 0) {
      for (String error : handler.getErrors()) {
        logger.severe(error);
      }
      throw new MxiFormatException(
          "The MXI file does not appear to be valid and has one or both of "
          + "parsing and validation errors. Please check the logging output.\n"
          + "Validation errors indicate that the MXI does not conform to the "
          + "MXI schema found in " + MXI_SCHEMA + ", if this is due "
          + "to the schema being incomplete then either let me (bstock) know, "
          + "or I am happy to accept a CL ;).");
    }
    
    return parsedFilePaths;
  }
  
  private void writeFileToMxpArchive(OutputStream mxpFile, String path, File workingDir)
      throws IOException {
    File file = new File(workingDir, path);
    
    if (!file.canRead()) {
      throw new FileNotFoundException(
          String.format("The file '%s' listed in the MXI could not be found. "
              + "Paths should be relative to the MXI file.", file.getAbsolutePath()));
    }
    
    // Write path length and path string.
    byte[] pathBytes = MxpEntries.localSystemPathToMxpPath(path).getBytes();
    writeUnsignedInt32(mxpFile, pathBytes.length);
    mxpFile.write(pathBytes);
    
    // Write timestamp.
    LocalDateTime timeStamp = new LocalDateTime(file.lastModified());
    writeUnsignedInt16(mxpFile, timeStamp.getYear());
    writeUnsignedInt16(mxpFile, timeStamp.getMonthOfYear());
    writeUnsignedInt16(mxpFile, timeStamp.getDayOfMonth());
    writeUnsignedInt16(mxpFile, timeStamp.getHourOfDay());
    writeUnsignedInt16(mxpFile, timeStamp.getMinuteOfHour());
    writeUnsignedInt16(mxpFile, timeStamp.getSecondOfMinute());
    
    // File type data. This field appears to be inconsistent in terms of what
    // is actually stored here, and overall appears to be unimportant.
    // We re-purpose FILE_TERMINATOR here to simply fill the field with 0's.
    mxpFile.write(FILE_TERMINATOR);
    
    byte[] readBuffer = new byte[1024];
    byte[] compressionBuffer = new byte[readBuffer.length];

    // Create a buffer to write the compressed data to. We make large enough to
    // hold the original data and a tiny bit more as the compression algorithm
    // can in some cases result in output larger than the input.
    // ByteArrayOutputStream will handle any necessary array expansion, but we
    // try to avoid that expense.
    ByteArrayOutputStream compressedData = 
        new ByteArrayOutputStream(readBuffer.length + COMPRESSION_TOLERANCE_BYTES);
    Deflater deflater = new Deflater(Deflater.BEST_COMPRESSION);

    try (FileInputStream fin = new FileInputStream(file)) {
      int bytesRead;
      
      // Takes a block of bytes from the input file and compresses, then writes
      // the compressed and decompressed sizes to the MXP followed by the
      // compressed bytes themselves. Repeats for rest of file input.
      while ((bytesRead = fin.read(readBuffer)) != -1) {
        deflater.setInput(readBuffer, 0, bytesRead);
        deflater.finish(); // signal that this is the entire input for compression.

        int segmentSize = 0;
        compressedData.reset();
      
        // Most of the time compressionBuffer will be large enough to hold the
        // entirety of the compressed data, but as the compression algorithm can
        // sometimes result in more output bytes than input we need to use a
        // ByteArrayOutputStream to allow for this.
        int bytesCompressed;
        while ((bytesCompressed = deflater.deflate(compressionBuffer)) > 0) {
          segmentSize += bytesCompressed;
          compressedData.write(compressionBuffer, 0, bytesCompressed);
        }

        writeUnsignedInt32(mxpFile, bytesRead);
        writeUnsignedInt32(mxpFile, segmentSize);
        mxpFile.write(compressedData.toByteArray());

        deflater.reset();
      }

      mxpFile.write(FILE_TERMINATOR);
    }
  }

  /**
   * Class to handle SAX parsing of MXI file and build the list of files to
   * include in the MXP archive.
   */
  private static class MxiHandler extends DefaultHandler {
    
    private final List<String> errors = Lists.newArrayList();
    private final List<String> warnings = Lists.newArrayList();
    private final List<String> installFiles = Lists.newArrayList();
    private boolean processingFilesSection = false;
    
    @Override
    public void startElement(String uri, String localName, String name, Attributes attributes)
        throws SAXException {
      super.startElement(uri, localName, name, attributes);
      if ("files".equals(name)) {
        processingFilesSection = true;
      }
      
      if ("file".equals(name) && processingFilesSection) {
        String fileSource = attributes.getValue("source");
        if (fileSource != null) {
          installFiles.add(fileSource);
        }
      }
    }
    
    @Override
    public void endElement(String uri, String localName, String name) throws SAXException {
      if ("files".equals(name)) {
        processingFilesSection = false;
      }
      super.endElement(uri, localName, name);
    }
    
    @Override
    public void error(SAXParseException e) throws SAXException {
      super.error(e);
      errors.add(buildErrorString(e));
    }
    
    @Override
    public void warning(SAXParseException e) throws SAXException {
      super.warning(e);
      warnings.add(buildErrorString(e));
    }
    
    private String buildErrorString(SAXParseException ex) {
      String msg = ex.getMessage();
      if (ex.getLineNumber() > -1) {
        msg += " Line:" + ex.getLineNumber();
      }
      if (ex.getColumnNumber() > -1) {
        msg += " Col:" + ex.getColumnNumber();
      }
      return msg;
    }
    
    private List<String> getErrors() {
      return errors;
    }
    
    private List<String> getWarnings() {
      return warnings;
    }
    
    private List<String> getInstallFiles() {
      return installFiles;
    }
  }
}
