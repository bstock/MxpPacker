package uk.co.familystock.mxptool;

import com.google.common.base.Preconditions;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

/**
 * Provides a means of reading a file from a series of compressed data blocks
 * in an MXP archive.
 * 
 * <p>Rather than a continuous stream of compressed bytes, each file in an MXP
 * archive is broken up into a series of compressed data blocks of no more than
 * 1K in size, and each block preceded by two 32-bit unsigned integers holding
 * the uncompressed and compressed block sizes.
 * 
 * @author bstock@google.com (Benjamin Stock)
 */
final class MxpFileDataInputStream extends InputStream {
  
  private boolean eofReached = false;
  private boolean closed = false;
  
  private ByteBuffer fileData;
  private Inflater inflater;
  
  private byte[] singleByteBuffer = new byte[1];
  private byte[] buffer = new byte[2048];
  
  /**
   * Creates a MxpFileDataInputStream with the given ByteBuffer as the data
   * source.
   */
  public MxpFileDataInputStream(ByteBuffer fileData) {
    Preconditions.checkArgument(fileData != null);
    this.fileData = fileData;
    inflater = new Inflater();
  }
  
  private void fill() throws IOException {
    // First we need to read the uncompressed and compressed sizes of the 
    // data block from the stream.
    long uncompressedBlockSize = readUnsignedInt32();
    long blockSize = readUnsignedInt32();

    // blocksize should never be anywhere near the max integer size, but we
    // check just in case as that is what good citizens do.
    if (blockSize > Integer.MAX_VALUE) {
      throw new IOException("Invalid block size.");
    }
    
    // Now we check if the EOF marker has been reached (8 consecutive null
    // bytes), and if not then decompress the block of actual file data.
    if (uncompressedBlockSize + blockSize == 0) {
      eofReached = true;
    } else {
      int dataSize = (int) blockSize;
      
      // If the byte buffer is not large enough to hold the compressed bytes,
      // reallocate so that it is. This code should never actually fire as
      // the blocksize should never be more than 1024 (exman chokes otherwise)
      // but means we are safe if it is.
      if (buffer.length < dataSize) {
        buffer = new byte[dataSize];
      }
      
      fileData.get(buffer, 0, dataSize);
      inflater.reset();
      inflater.setInput(buffer, 0, dataSize);
    }
  }
  
  /*
   * Read an unsigned 32-bit integer in little-endian format.
   */
  private long readUnsignedInt32() {
    long val = fileData.get() & 0xff;
    val |= ((fileData.get() & 0xff) << 8);
    val |= ((fileData.get() & 0xff) << 16);
    val |= ((fileData.get() & 0xffL) << 24);
    return val;
  }
  
  private void sanityCheck() {
    if (closed) {
      throw new IllegalStateException("Attempt to read from closed stream.");
    }
  }
  
  @Override
  public void close() {
    // free resources for GC.
    fileData = null;
    inflater = null;
    closed = true;
  }
  
  @Override
  public int read() throws IOException {
    return read(singleByteBuffer, 0, 1) == -1 ? -1 : singleByteBuffer[0] & 0xff;
  }
  
  @Override
  public int read(byte[] b) throws IOException {
    return read(b, 0, b.length);
  }
  
  @Override
  public int read(byte[] b, int off, int len) throws IOException {
    sanityCheck();
    try {
      int read;
      while ((read = inflater.inflate(b, off, len)) == 0) {
        if (eofReached) {
          return -1;
        }
        if (inflater.needsInput()) {
          fill();
        }
      }
      return read;
    } catch (DataFormatException e) {
      throw new MxpFormatException("Error trying to read file data.", e);
    }
  }
}