package uk.co.familystock.mxptool;

import java.util.Arrays;

import org.joda.time.LocalDateTime;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;

/**
 * Represents a file-entry in an MXP container.
 *
 * @author bstock@google.com (Benjamin Stock)
 */
public final class MxpEntry {
  
  /**
   * Helper class used to create instances of MxpEntry.
   */
  static class Builder {
    
    private final String filePath;
    private LocalDateTime timestamp = new LocalDateTime();
    private byte[] fileTypeData = new byte[] {0, 0, 0, 0, 0, 0, 0, 0};
    private long compressedSize;
    private long decompressedSize;
    private long fileDataOffset;
    private long fileDataLength;
    
    /**
     * Prepare a basic MxpEntry ready for instantiation via {@link #build()}.
     * 
     * @param filePath the path of the file represented by this entry as a 
     * non-empty String.
     * @param compressedSize the size of the file in compressed form as a
     * positive integer.
     * @param decompressedSize the size of the file when decompressed as a
     * positive integer.
     */
    Builder(String filePath, long compressedSize, long decompressedSize) {
      Preconditions.checkArgument(!Strings.isNullOrEmpty(filePath),
          "Path must be a non-empty string, but was '%s'.", filePath);
      Preconditions.checkArgument(compressedSize > 0,
          "Invalid compressedSize, must be greater than 0 but was %s.", compressedSize);
      Preconditions.checkArgument(decompressedSize > 0,
          "Invalid decompressedSize, must be greater than 0 but was %s.", decompressedSize);
      this.filePath = filePath;
      this.compressedSize = compressedSize;
      this.decompressedSize = decompressedSize;
    }
    
    Builder timeStamp(LocalDateTime timestamp) {
      Preconditions.checkNotNull(timestamp);
      this.timestamp = timestamp;
      return this;
    }

    Builder fileTypeData(byte[] fileTypeData) {
      Preconditions.checkArgument((fileTypeData != null) && (fileTypeData.length == 8));
      this.fileTypeData = Arrays.copyOf(fileTypeData, fileTypeData.length);
      return this;
    }
    
    /**
     * Set the position at which the file data for the file represented by this
     * entry starts within the MXP archive.
     */
    Builder fileDataOffset(long fileDataOffset) {
      Preconditions.checkArgument(fileDataOffset >= 0);
      this.fileDataOffset = fileDataOffset;
      return this;
    }

    /**
     * Set the length of the data within the MXP archive holding the file data
     * for the file represented by this entry.
     */
    Builder fileDataLength(long fileDataLength) {
      Preconditions.checkArgument(fileDataLength > 0);
      this.fileDataLength = fileDataLength;
      return this;
    }
    
    MxpEntry build() {
      return new MxpEntry(this);
    }
    
  }

  private final String filePath;
  private final LocalDateTime timestamp;
  private final byte[] fileTypeData;
  private final long compressedSize;
  private final long decompressedSize;
  private final long fileDataOffset;
  private final long fileDataLength;

  // Private constructor as class created via Builder.
  private MxpEntry(Builder builder) {
    filePath = builder.filePath;
    timestamp = builder.timestamp;
    fileTypeData = builder.fileTypeData;
    compressedSize = builder.compressedSize;
    decompressedSize = builder.decompressedSize;
    fileDataOffset = builder.fileDataOffset;
    fileDataLength = builder.fileDataLength;
  }

  public String getFilePath() {
    return filePath;
  }
  
  public LocalDateTime getTimestamp() {
    return timestamp;
  }
  
  public long getCompressedSize() {
    return compressedSize;
  }
  
  public long getDecompressedSize() {
    return decompressedSize;
  }
  
  public byte[] getFileType() {
    return fileTypeData.clone();
  }
  
  // only used internally
  long getFileDataOffset() {
    return fileDataOffset;
  }
  
  //only used internally
  long getFileDataLength() {
    return fileDataLength;
  }
  
  @Override
  public String toString() {
    return "MXP entry: " + getFilePath();
  }
  
  /**
   * Two MxpEntries are considered equal if they share the same file path.
   */
  @Override
  public boolean equals(Object obj) {
    if (obj == this) {
      return true;
    }
    if (obj instanceof MxpEntry) {
      MxpEntry entry = (MxpEntry) obj;
      return getFilePath().equals(entry.getFilePath());
    }
    return false;
  }
  
  @Override
  public int hashCode() {
    return filePath.hashCode();
  }
}