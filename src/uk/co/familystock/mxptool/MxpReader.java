package uk.co.familystock.mxptool;

import java.io.Closeable;
import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.nio.channels.FileChannel.MapMode;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;

import javax.annotation.Nullable;

import org.joda.time.LocalDateTime;

import com.google.common.base.Charsets;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;

/**
 * Class for reading Adobe Flash MXP containers.
 * 
 * @author bstock@google.com (Benjamin Stock)
 */
public class MxpReader implements Closeable {

  private final MxpEntry mxiFile;
  private final Map<String, MxpEntry> entries;
  private final byte[] mxpHeader;
  private final GuardedRandomAccessFileReader mxpFileReader;
  private boolean closed = false;
  
  /**
   * Private constructor as entry-point is via {@link #withFile(File)}.
   */
  private MxpReader(byte[] mxpHeader, MxpEntry mxiFile, Map<String, MxpEntry> entries,
      GuardedRandomAccessFileReader mxpFileReader) {
    Preconditions.checkNotNull(mxpHeader);
    Preconditions.checkArgument(mxpHeader.length == 8, "Mxp header must be 8 bytes long.");
    Preconditions.checkNotNull(mxiFile);
    Preconditions.checkNotNull(entries);
    Preconditions.checkNotNull(mxpFileReader);
    
    this.mxiFile = mxiFile;
    this.mxpHeader = mxpHeader;
    this.entries = entries;
    this.mxpFileReader = mxpFileReader;
  }

  /**
   * Gets an {@code InputStream} for reading the contents of the file denoted
   * by the given {@code MxpEntry}. Returns null if no matching entry could
   * be found.
   * 
   * @throws IOException if an I/O error occurs
   * @throws IllegalStateException if the reader has been closed
   */
  public InputStream getInputStream(MxpEntry entry) throws IOException {
    Preconditions.checkState(!closed, "Mxp file closed.");
    Preconditions.checkNotNull(entry, "entry must not be null.");
    
    // Ensure we use our version.
    entry = entries.get(entry.getFilePath());
    
    MxpFileDataInputStream in = null;
    if (entry != null) {
      in = new MxpFileDataInputStream(
          mxpFileReader.getChannel().map(
              MapMode.READ_ONLY, entry.getFileDataOffset(), entry.getFileDataLength()));
    }
    return in;
  }
  
  /**
   * Get the first 8 bytes of the MXP archive that form the header.
   */
  public byte[] getHeader() {
    return Arrays.copyOf(mxpHeader, mxpHeader.length);
  }

  /**
   * Get the MXP version indicating what version of the MXI specification the
   * archive has been built to work with.
   */
  public int getMxpFormatVersion() {
    return mxpHeader[0];
  }

  /**
   * Gets the {@code MxpEntry} for the file matching the given path.
   * 
   * @return the matching entry if present, else null
   */
  @Nullable
  public MxpEntry getEntry(String path) {
    return entries.get(path);
  }

  /**
   * Gets an unmodifiable view of the {@code MxpEntry}'s representing the files 
   * stored within the MXP archive.
   */
  public Collection<MxpEntry> getEntries() {
    return entries.values();
  }

  /**
   * Gets the {@code MxpEntry} representing the MXI file for this MXP archive.
   */
  public MxpEntry getMxiFile() {
    return mxiFile;
  }
  
  /**
   * Closes the reader. This has no effect on any input streams already obtained
   * via {@link #getInputStream(MxpEntry)}, but prevents further streams being
   * opened.
   */
  @Override
  public void close() throws IOException {
    mxpFileReader.close();
    closed = true;
  }
  
  /**
   * Creates and returns an {@code MxpReader} using the given file as the source.
   * 
   * @throws IOException in case of a read error
   * @throws MxpFormatException if errors are encountered reading the MXP
   */
  public static MxpReader withFile(File mxpFile) throws IOException, MxpFormatException {
    Preconditions.checkNotNull(mxpFile, "Null file.");
    
    ImmutableMap.Builder<String, MxpEntry> entriesBuilder = ImmutableMap.builder();
    byte[] mxpHeader = new byte[8];
    GuardedRandomAccessFileReader mxpFileReader = new GuardedRandomAccessFileReader(mxpFile);
    
    mxpFileReader.read(mxpHeader);

    // First entry should always be MXI file.
    MxpEntry mxiFile;
    if (mxpFileReader.hasBytesRemaining()) {
      mxiFile = readEntry(mxpFileReader);
      entriesBuilder.put(mxiFile.getFilePath(), mxiFile);
    } else {
      throw new MxpFormatException("The MXP must contain at least an MXI file.");
    }
    
    // Confirm that what we just read looks like the MXI file.
    if (!mxiFile.getFilePath().toLowerCase().endsWith(".mxi")) {
      throw new MxpFormatException("The first entry must be the MXI file.");
    }
    
    while (mxpFileReader.hasBytesRemaining()) {
      MxpEntry entry = readEntry(mxpFileReader);
      entriesBuilder.put(entry.getFilePath(), entry);
    }
    
    return new MxpReader(mxpHeader, mxiFile, entriesBuilder.build(), mxpFileReader);
  }
  
  private static MxpEntry readEntry(GuardedRandomAccessFileReader mxpFileReader)
      throws IOException {
    // Get file path.
    int pathLength = (int) mxpFileReader.readUnsignedInt32();
    if (pathLength > 256) {
      throw new MxpFormatException("Invalid file path length " + pathLength + ".");
    }
    byte[] pathData = new byte[pathLength];
    mxpFileReader.read(pathData);
    String path = MxpEntries.mxpPathToLocalSystemPath(new String(pathData, Charsets.UTF_8));

    // Get timestamp.
    LocalDateTime timeStamp = new LocalDateTime(
        mxpFileReader.readUnsignedInt16(),  // Year.
        mxpFileReader.readUnsignedInt16(),  // Month. 
        mxpFileReader.readUnsignedInt16(),  // Day.
        mxpFileReader.readUnsignedInt16(),  // Hour.
        mxpFileReader.readUnsignedInt16(),  // Min.
        mxpFileReader.readUnsignedInt16()); // Sec.
    
    // Read file type bytes (seems to be fairly pointless/random collection
    // of bits .. ).
    byte[] fileTypeData = new byte[8];
    mxpFileReader.read(fileTypeData);
    
    long fileSize = 0;
    long compressedSize = 0;
    long fileStart = mxpFileReader.getFilePointer();

    // The format uses 32-bit unsigned ints, so technically one or both of
    // the uncompressed and compressed byte counts could be more than
    // 2,147,483,647 which would result in a negative number when cast to an
    // int.
    long uncompressedBlockSize = mxpFileReader.readUnsignedInt32();
    long blockSize = mxpFileReader.readUnsignedInt32();
    if ((uncompressedBlockSize > Integer.MAX_VALUE) || (blockSize > Integer.MAX_VALUE)) {
      throw new MxpFormatException("Cannot handle files larger than 2GB");
    }

    // 8 consecutive 0's indicates the EOF.
    while ((uncompressedBlockSize + blockSize) > 0) {
      fileSize += uncompressedBlockSize;
      compressedSize += blockSize;

      // Skip over the compressed file bytes.
      mxpFileReader.seek(mxpFileReader.getFilePointer() + blockSize);

      uncompressedBlockSize = mxpFileReader.readUnsignedInt32();
      blockSize = mxpFileReader.readUnsignedInt32();
    }

    return new MxpEntry.Builder(path, compressedSize, fileSize)
        .timeStamp(timeStamp)
        .fileTypeData(fileTypeData)
        // Store the offset and length of the compressed file data for later.
        .fileDataOffset(fileStart)
        .fileDataLength(mxpFileReader.getFilePointer() - fileStart)
        .build();
  }
  
  /**
   * Helper class that wraps RandomAccessFile mainly in order to add checks that
   * verify enough bytes exist to satisfy any read operations and throw an
   * EOFException if not, but also to provide methods to read unsigned ints of
   * varying size. 
   */
  private static class GuardedRandomAccessFileReader {
    
    private final RandomAccessFile fileData;

    private GuardedRandomAccessFileReader(File file) throws IOException {
      fileData = new RandomAccessFile(file, "r");
    }
    
    /**
     * Read an unsigned 16-bit integer from the byte stream assuming
     * little-endian byte order.
     */
    private int readUnsignedInt16() throws IOException {
      int val = read();
      val |= (read() << 8);
      return val;
    }
    
    /**
     * Read an unsigned 32-bit integer from the byte stream assuming
     * little-endian byte order.
     */
    private long readUnsignedInt32() throws IOException {
      long val = readUnsignedInt16();
      val |= ((long) read() << 16);
      val |= ((long) read() << 24);
      return val;
    }
    
    private int read() throws IOException {
      ensureBytes(1);
      return fileData.read();
    }
    
    private int read(byte[] b) throws IOException {
      ensureBytes(b.length);
      return fileData.read(b);
    }

    private boolean hasBytesRemaining() throws IOException {
      return fileData.getFilePointer() < fileData.length();
    }
    
    private void ensureBytes(long bytesExpected) throws IOException, EOFException {
      if (fileData.length() - fileData.getFilePointer() < bytesExpected) {
        throw new EOFException("Unexpected end of file.");
      }
    }
    
    private void close() throws IOException {
      fileData.close();
    }
    
    private long getFilePointer() throws IOException {
      return fileData.getFilePointer();
    }
    
    private void seek(long pos) throws IOException {
      fileData.seek(pos);
    }
    
    private FileChannel getChannel() {
      return fileData.getChannel();
    }
    
  }
}
