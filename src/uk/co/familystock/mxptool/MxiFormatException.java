package uk.co.familystock.mxptool;

/**
 * Used to indicate an error stemming from an invalid MXI file.
 * 
 * @author bstock@google.com (Benjamin Stock)
 */
public class MxiFormatException extends Exception {

  /**
   * Constructs an MxiFormatException with the given message.
   */
  public MxiFormatException(String msg) {
    super(msg);
  }
  
  /**
   * Constructs an MxiFormatException with the given message and cause.
   */
  public MxiFormatException(String msg, Throwable cause) {
    super(msg, cause);
  }
}
