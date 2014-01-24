package uk.co.familystock.mxptool;

import java.io.IOException;

/**
 * Used to indicate an error stemming from an invalid MXP file.
 * 
 * @author bstock@google.com (Benjamin Stock)
 */
public class MxpFormatException extends IOException{

  /**
   * Constructs an MxpFormatException with the given message.
   */
  public MxpFormatException(String message) {
    super(message);
  }
  
  /**
   * Constructs an MxpFormatException with the given message and cause.
   */
  public MxpFormatException(String message, Throwable cause) {
    super(message, cause);
  }
}