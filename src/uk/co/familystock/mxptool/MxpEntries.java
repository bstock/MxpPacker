package uk.co.familystock.mxptool;

import com.google.common.base.Preconditions;

import java.io.File;

/**
 * Utility class providing helper methods for dealing with MXP entries.
 * 
 * @author bstock@google.com (Benjamin Stock)
 */
public class MxpEntries {

  private MxpEntries() {
    // Static utility class.
  }
  
  /**
   * Converts a path for an MXP file entry to the current systems path format.
   *
   * @param mxpEntryPath a path for an MXP entry
   * @return the converted path
   */
  static String mxpPathToLocalSystemPath(String mxpEntryPath) {
    Preconditions.checkNotNull(mxpEntryPath, "Expected entry path as a String, but got null.");
    // MXPs created via either this tool or the extension manager will always
    // use \'s, so only do conversion when required.
    return !"\\".equals(File.separator)
        ? mxpEntryPath.replace("\\", File.separator)
        : mxpEntryPath;
  }

  /**
   * Converts a local system path to an MXP entry path.
   *
   * @param localPath the local system path to convert
   * @return the converted path
   */
  static String localSystemPathToMxpPath(String localPath) {
    Preconditions.checkNotNull(localPath, "Expected local path as a String, but got null.");
    // Extension manager always uses \'s in paths, so make sure we do the same.
    return localPath.replace("/", "\\");
  }
  
}