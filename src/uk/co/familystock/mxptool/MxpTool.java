package uk.co.familystock.mxptool;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.Collections;
import java.util.Comparator;
import java.util.Formatter;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.common.io.ByteStreams;

/**
 * Acts as command-line launcher and also provides static entry points for 
 * using MxpTool.
 *
 * @author bstock@google.com (Ben Stock)
 */
public class MxpTool {

  /*
  @FlagSpec(help = ": Create an MXP file." +
      " Usage: --create MXP_FILE MXI_FILE [--mxpVersion=VERSION]")
  private static final Flag<Boolean> FLAG_create = Flag.value(false);
  
  @FlagSpec(help = ": Print a list of files contained in the MXP archive." +
      " Usage: --list MXP_FILE [FILE..]")
  private static final Flag<Boolean> FLAG_list = Flag.value(false);
  
  @FlagSpec(help = ": Extract the contents of an MXP archive." +
      " Usage: --extract MXP_FILE [--dir=TARGET_DIR] [FILE..]")
  private static final Flag<Boolean> FLAG_extract = Flag.value(false);
  
  @FlagSpec(help = ": Print the MXI file for a given MXP file." +
      " Usage: --dump MXP_FILE")
  private static final Flag<Boolean> FLAG_dump = Flag.value(false);
  
  @FlagSpec(help = ": Optional flag to specify target directory when extracting an MXP archive")
  private static final Flag<String> FLAG_dir = Flag.value(".");
  
  @FlagSpec(help = ": Increase the verbosity of logging statements")
  private static final Flag<Boolean> FLAG_verbose = Flag.value(false);
  
  @FlagSpec(help = ": Optional flag to specify MXP version when creating an MXP archive")
  private static final Flag<Integer> FLAG_mxpVersion = 
      Flag.positiveValue(MxpWriter.DEFAULT_MXP_VERSION);
      */
  
  private static final Logger logger = Logger.getLogger(MxpTool.class.getCanonicalName());
    
  private MxpTool() {
    // Class designed to be used statically.
  }
  
  public static void main(String[] args) throws Exception {
    /*
    Flags.setUsagePrefix("Usage: " + MxpTool.class.getSimpleName() 
        + " --[create|list|dump|extract] [--opts...] [args...]");
    // Restrict which flags are recognised/printed.
    Flags.setAllowedFlags(Lists.newArrayList(MxpTool.class.getPackage().getName().concat(".")));
    args = Flags.parseAndReturnLeftovers(args);
    
    // By default only warnings and above are logged.
    if (FLAG_verbose.get()) {
      Logger.getLogger("uk.co.familystock.mxptool").setLevel(Level.ALL);
    }

    int cmdFlags = 0;
    if (FLAG_create.get()) {
      cmdFlags++;
    }
    if (FLAG_list.get()) {
      cmdFlags++;
    }
    if (FLAG_extract.get()) {
      cmdFlags++;
    }
    if (FLAG_dump.get()) {
      cmdFlags++;
    }
    
    if (cmdFlags > 1) {
      throw new IllegalArgumentException("Only one of --[create|list|extract|dump] may specifed.");
    } else if (cmdFlags == 0) {
      throw new IllegalArgumentException("No command specified. See --help for help/usage.");
    }
    
    Preconditions.checkArgument(args.length >= 1, "MXP path must be specified as first argument");
    File mxpFile = new File(args[0]);
    
    if (FLAG_create.get()) {
      Preconditions.checkArgument(args.length >= 2,
          "MXI path must be specified as second argument");
      File mxiFile = new File(args[1]);
      create(mxpFile, mxiFile, FLAG_mxpVersion.get());
    } else if (FLAG_list.get()) {
      Set<String> targetPaths = getTargetPaths(args, 1);
      list(mxpFile, new OutputStreamWriter(System.out), targetPaths);
    } else if (FLAG_extract.get()) {
      Set<String> targetPaths = getTargetPaths(args, 1);
      extract(mxpFile, new File(FLAG_dir.get()), targetPaths);
    } else if (FLAG_dump.get()) {
      dump(mxpFile, new OutputStreamWriter(System.out));
    }
    */
  }
  
  private static Set<String> getTargetPaths(String[] argList, int argOffset) {
    // Build list of paths to extract if specified.
    Set<String> targetPaths = null;
    if (argList.length > argOffset) {
      targetPaths = Sets.newHashSetWithExpectedSize(argList.length - argOffset);
      for (int i = argOffset; i < argList.length; i++) {
        if (!argList[i].trim().isEmpty()) {
          targetPaths.add(argList[i]);
        }
      }
    }
    return targetPaths;
  }
  
  /**
   * Creates an MXP archive from an MXI file. The MXP archive will contain the
   * MXI file and any files listed within the FILES section of the MXI, note
   * that these file paths should be relative to the MXI file.
   * 
   * @param mxpFile the path of the MXP archive to create
   * @param mxiFile the path of the MXI file
   * @throws IOException if there is an error reading or writing the files
   */
  public static void create(File mxpFile, File mxiFile)
      throws IOException, MxiFormatException {
    create(mxpFile, mxiFile, MxpWriter.DEFAULT_MXP_VERSION);
  }
  
  /**
   * Creates an MXP archive from an MXI file using the specified level of
   * compression and MXP/MXI version. The MXP archive will contain the
   * MXI file and any files listed within the FILES section of the MXI, note
   * that these file paths should be relative to the MXI file.
   * 
   * @param mxpFile the path of the MXP archive to create
   * @param mxiFile the path of the MXI file
   * @param mxpVersion the MXP/MXI version to set for the archive
   * @throws IOException if there is an error reading or writing the files
   */
  public static void create(File mxpFile, File mxiFile, int mxpVersion)
      throws IOException, MxiFormatException {
    Preconditions.checkNotNull(mxpFile, "mxpFile must be non-null.");
    Preconditions.checkNotNull(mxiFile, "mxiFile must be non-null.");
    
    Preconditions.checkArgument(mxiFile.canRead(), "Can't read MXI file '%s'", mxiFile.getPath());
    
    if (mxpFile.isDirectory()
        || (mxpFile.getParentFile() != null && !mxpFile.getParentFile().canWrite())) {
      throw new IOException("Unable to create MXP file '" + mxpFile.getPath() + "'");
    }
    
    MxpWriter writer = new MxpWriter(mxiFile, mxpVersion);
    writer.write(mxpFile);
  }

  /**
   * Prints a nicely formatted list of the files contained within a given MXP
   * archive.
   * 
   * @param mxpFile the path of the MXP archive to list
   * @throws IOException if there is an error reading or writing the files
   */
  public static void list(File mxpFile, Writer out) throws IOException {
    list(mxpFile, out, null);
  }
  
  /**
   * Prints a nicely formatted list of the files contained within a given MXP
   * archive filtered using {@code targetPaths}. If {@code targetPaths} is null
   * then all files are listed.
   * 
   * @param mxpFile the path of the MXP archive to list
   * @param targetPaths a set of file paths used to filter the output
   * @throws IOException if there is an error reading or writing the files
   */
  public static void list(File mxpFile, Writer out, Set<String> targetPaths) throws IOException {
    Preconditions.checkNotNull(mxpFile, "mxpFile must be non-null.");
    Preconditions.checkNotNull(out, "out must be non-null.");
    
    Preconditions.checkArgument(mxpFile.canRead(), "Can't read MXP file '%s'", mxpFile.getPath());
    
    @SuppressWarnings("resource")
    Formatter stringFormatter = new Formatter(out);
    NumberFormat percentFormat = DecimalFormat.getPercentInstance();
    DateTimeFormatter timestampFormat = DateTimeFormat.forPattern("dd-MM-yyyy  HH:mm");
    
    long totalBytes = 0;
    int fileCount = 0;
    
    MxpReader reader = MxpReader.withFile(mxpFile);
    List<MxpEntry> entries = getTargetEntries(reader, targetPaths);
    reader.close();
    
    Collections.sort(entries, new Comparator<MxpEntry>() {
      @Override
      public int compare(MxpEntry o1, MxpEntry o2) {
        return o1.getFilePath().compareTo(o2.getFilePath());
      }
    });

    stringFormatter.format(
        "MXP Archive v%d: %s\n\n", reader.getMxpFormatVersion(), mxpFile.getName());
    out.write("      Length  Date        Time   Ratio  Path\n");
    out.write("    --------  ----------  -----  -----  ------\n");
    for (MxpEntry entry : entries) {
      fileCount++;
      totalBytes += entry.getDecompressedSize();
      String tStamp = timestampFormat.print(entry.getTimestamp());
      
      float compressionRatio = 0;
      if (entry.getCompressedSize() < entry.getDecompressedSize()) {
        compressionRatio = 1 - ((float) entry.getCompressedSize() / entry.getDecompressedSize());
        compressionRatio = (float) Math.floor(compressionRatio * 100) / 100;
      }
      
      stringFormatter.format("%12d  %s  %5s  %s\n", entry.getDecompressedSize(),
          tStamp, percentFormat.format(compressionRatio), entry.getFilePath());
    }
    out.write("    --------                            ------\n");
    stringFormatter.format("%12d %26s %d files\n\n", totalBytes, "", fileCount);
    out.close();
  }

  /**
   * Extracts the contents of an MXP archive to the specified location.
   * 
   * @param mxpFile the path of the MXP archive to extract
   * @param targetDir the path to extract to
   * @throws IOException if there is an error reading or writing the files
   */
  public static void extract(File mxpFile, File targetDir) throws IOException {
    extract(mxpFile, targetDir, null);
  }
  
  /**
   * Extracts the contents of an MXP archive to the specified location.
   * If {@code targetPaths} is not null then it is used to filter the list of
   * files to extract, else all files are extracted.
   * 
   * @param mxpFile the path of the MXP archive to extract
   * @param targetDir the path to extract to
   * @param targetPaths a set of file paths to extract
   * @throws IOException if there is an error reading or writing the files
   */
  public static void extract(File mxpFile, File targetDir, Set<String> targetPaths)
      throws IOException {
    Preconditions.checkNotNull(mxpFile, "mxpFile must be non-null.");
    Preconditions.checkNotNull(targetDir, "targetDir must be non-null.");
    
    Preconditions.checkArgument(mxpFile.canRead(), "Can't read MXP file '%s'", mxpFile.getPath());
    
    // Check if target directory for extraction exists,
    // if so then check it is writable, if not - try and create it.
    if (!targetDir.exists() && !targetDir.mkdirs() || !targetDir.canWrite()) {
      throw new IOException("Unable to write to extraction target '" 
          + targetDir.getAbsolutePath() + "'.");
    }

    try (MxpReader reader = MxpReader.withFile(mxpFile)) {
      for (MxpEntry entry : getTargetEntries(reader, targetPaths)) {
        File target = new File(targetDir, entry.getFilePath());
        target.getParentFile().mkdirs();
        target.setLastModified(entry.getTimestamp().toDateTime().getMillis());
        try (BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(target))) {
          ByteStreams.copy(reader.getInputStream(entry), out);
        }
      }
    }
  }
  
  private static List<MxpEntry> getTargetEntries(MxpReader reader, Set<String> targetPaths) {
    List<MxpEntry> entries;
    if (targetPaths == null) {
      entries = Lists.newArrayList(reader.getEntries());
    } else {
      entries = Lists.newArrayListWithExpectedSize(targetPaths.size());
      for (String path : targetPaths) {
        MxpEntry entry = reader.getEntry(path);
        if (entry == null) {
          logger.warning("File '" + path + "' not present in MXP.");
        } else {
          entries.add(entry);
        }
      }
    }
    return entries;
  }

  /**
   * Locates and prints the contents of the MXI file for an MXP archive.
   * 
   * @param mxpFile the path of the MXP file to print the MXI file for
   * @param out output to write MXI file to
   * @throws IOException if there is an error reading or writing the files
   */
  public static void dump(File mxpFile, Writer out) throws IOException {
    Preconditions.checkNotNull(mxpFile, "mxpFile must be non-null.");
    Preconditions.checkNotNull(out, "out must be non-null.");
    
    Preconditions.checkArgument(mxpFile.canRead(), "Can't read MXP file '%s'", mxpFile.getPath());

    MxpReader reader = MxpReader.withFile(mxpFile);
    MxpEntry mxiEntry = reader.getMxiFile();
    
    try (InputStream in = reader.getInputStream(mxiEntry)) {
      reader.close();
      Document mxiDoc = XmlUtil.getDocumentBuilder().parse(in);
      XmlUtil.printDom(mxiDoc, out);
    } catch (SAXException e) {
      throw new IOException("Parsing/printing of MXI file failed - '" + e.getMessage() + "'", e);
    }
  }
}
