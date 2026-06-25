package site.ycsb.db.hbase094.bulkload;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Random;

/**
 * Generate ImportTsv input for HBase 0.94 bulk load (one line per cell).
 *
 * <p>Semantics align with {@code site.ycsb.db.hbase094.HBaseClient94#buildPut}:
 * row key, CF, qualifier, explicit HBase timestamp, 20-byte field value.
 */
public final class HBaseBulkLoadTsvGenerator {

  private static final int WRITER_BUFFER_BYTES = 8 * 1024 * 1024;

  private static final String[] FIELD_NAMES = {
      "field0", "field1", "field2", "field3", "field4",
      "field5", "field6", "field7", "field8", "field9"
  };

  private static final long DEFAULT_VERSION_ANCHOR_TS = 1781193600000L;
  private static final long DEFAULT_VERSION_WINDOW_MS = 15552000000L;
  private static final long DEFAULT_VERSION_DELTA_MS = 1000L;
  private static final int DEFAULT_ZERO_PADDING = 20;
  private static final int DEFAULT_FIELD_LENGTH = 20;

  private HBaseBulkLoadTsvGenerator() {
  }

  public static void main(String[] args) throws IOException {
    Config config = Config.parse(args);
    Path output = Paths.get(config.outputPath);
    Path parent = output.getParent();
    if (parent != null && !Files.isDirectory(parent)) {
      Files.createDirectories(parent);
    }

    long cellsWritten = 0L;
    Writer baseWriter = Files.newBufferedWriter(output, StandardCharsets.UTF_8);
    try (BufferedWriter writer = new BufferedWriter(baseWriter, WRITER_BUFFER_BYTES)) {
      for (long keyIndex = config.keyStart; keyIndex < config.keyEnd; keyIndex++) {
        String ycsbKey = formatKey(keyIndex, config.zeroPadding);
        long latestTs = computeLatestVersionTs(ycsbKey, config);
        for (String field : FIELD_NAMES) {
          byte[] valueBytes = generateValueBytes(ycsbKey, field, config);
          for (int i = 0; i < config.versionsPerQualifier; i++) {
            long ts = latestTs - (long) i * config.versionDeltaMs;
            writeImportTsvRow(writer, ycsbKey, config.columnFamily, field, ts, valueBytes);
            writer.newLine();
            cellsWritten++;
          }
        }
      }
    }

    System.out.println("Wrote " + cellsWritten + " ImportTsv rows to " + output.toAbsolutePath());
    System.out.println("Keys: [" + config.keyStart + ", " + config.keyEnd + "), V="
        + config.versionsPerQualifier + ", cf=" + config.columnFamily);
  }

  static void writeImportTsvRow(BufferedWriter writer, String rowKey, String cf, String qualifier,
      long timestampMs, byte[] valueBytes) throws IOException {
    // Tab-separated; YCSB value bytes are in [32,127] and never contain TAB.
    writeCsvField(writer, rowKey);
    writer.write('\t');
    writeCsvField(writer, cf);
    writer.write('\t');
    writeCsvField(writer, qualifier);
    writer.write('\t');
    writer.write(Long.toString(timestampMs));
    writer.write('\t');
    writeRawBytesField(writer, valueBytes);
  }

  /** Raw bytes for bulk ImportTsv; YCSB values never contain TAB. */
  static void writeRawBytesField(BufferedWriter writer, byte[] valueBytes) throws IOException {
    int i = 0;
    for (; i < valueBytes.length; i++) {
      writer.write((char) (valueBytes[i] & 0xFF));
    }
  }

  static void writeCsvField(BufferedWriter writer, String value) throws IOException {
    boolean needQuote = false;
    int i = 0;
    for (; i < value.length(); i++) {
      char c = value.charAt(i);
      if (',' == c || '"' == c || '\\' == c || '\n' == c || '\r' == c) {
        needQuote = true;
        break;
      }
    }
    if (!needQuote) {
      writer.write(value);
      return;
    }
    writer.write('"');
    for (i = 0; i < value.length(); i++) {
      char c = value.charAt(i);
      if ('"' == c || '\\' == c) {
        writer.write('\\');
      }
      writer.write(c);
    }
    writer.write('"');
  }

  static void writeCsvBytesField(BufferedWriter writer, byte[] valueBytes) throws IOException {
    boolean needQuote = false;
    int i = 0;
    for (; i < valueBytes.length; i++) {
      int c = valueBytes[i] & 0xFF;
      if (',' == c || '"' == c || '\\' == c || '\n' == c || '\r' == c) {
        needQuote = true;
        break;
      }
    }
    if (!needQuote) {
      for (i = 0; i < valueBytes.length; i++) {
        writer.write((char) (valueBytes[i] & 0xFF));
      }
      return;
    }
    writer.write('"');
    for (i = 0; i < valueBytes.length; i++) {
      int c = valueBytes[i] & 0xFF;
      if ('"' == c || '\\' == c) {
        writer.write('\\');
      }
      writer.write((char) c);
    }
    writer.write('"');
  }

  static String formatKey(long keyIndex, int zeroPadding) {
    return String.format("%0" + zeroPadding + "d", keyIndex);
  }

  static long computeLatestVersionTs(String ycsbKey, Config config) {
    long t0 = config.versionAnchorTs;
    if (config.versionSpreadInWindow) {
      long spreadOffset = keySpreadOffset(ycsbKey) % config.versionWindowMs;
      t0 = config.versionAnchorTs - spreadOffset;
    }
    return t0;
  }

  static long keySpreadOffset(String key) {
    int end = key.length();
    int start = end;
    while (start > 0 && Character.isDigit(key.charAt(start - 1))) {
      start--;
    }
    if (start < end) {
      return Long.parseLong(key.substring(start));
    }
    return Math.abs(Long.parseLong(key.trim()));
  }

  static byte[] generateValueBytes(String ycsbKey, String fieldName, Config config) {
    return buildYcsbRandomValueBytes(ycsbKey, fieldName, config.fieldLength);
  }

  static byte[] buildYcsbRandomValueBytes(String ycsbKey, String fieldName, int len) {
    long seed = (long) ycsbKey.hashCode() * 31L + (long) fieldName.hashCode();
    Random rnd = new Random(seed);
    byte[] ret = new byte[len];
    int off = 0;
    while (off < len) {
      fillRandomChunk(ret, off, rnd.nextInt());
      int chunk = Math.min(6, len - off);
      off += chunk;
    }
    return ret;
  }

  private static void fillRandomChunk(byte[] buffer, int base, int bytes) {
    switch (buffer.length - base) {
      default:
        buffer[base + 5] = (byte) (((bytes >> 25) & 95) + ' ');
        // fall through
      case 5:
        buffer[base + 4] = (byte) (((bytes >> 20) & 63) + ' ');
        // fall through
      case 4:
        buffer[base + 3] = (byte) (((bytes >> 15) & 31) + ' ');
        // fall through
      case 3:
        buffer[base + 2] = (byte) (((bytes >> 10) & 95) + ' ');
        // fall through
      case 2:
        buffer[base + 1] = (byte) (((bytes >> 5) & 63) + ' ');
        // fall through
      case 1:
        buffer[base] = (byte) ((bytes & 31) + ' ');
        // fall through
      case 0:
        break;
    }
  }

  static final class Config {
    long keyStart;
    long keyEnd;
    int versionsPerQualifier = 50;
    int zeroPadding = DEFAULT_ZERO_PADDING;
    int fieldLength = DEFAULT_FIELD_LENGTH;
    long versionAnchorTs = DEFAULT_VERSION_ANCHOR_TS;
    long versionWindowMs = DEFAULT_VERSION_WINDOW_MS;
    long versionDeltaMs = DEFAULT_VERSION_DELTA_MS;
    boolean versionSpreadInWindow = true;
    String columnFamily = "v";
    String outputPath = "import.tsv";

    static Config parse(String[] args) {
      Config config = new Config();
      for (int i = 0; i < args.length; i++) {
        String arg = args[i];
        if ("--key-start".equals(arg) && i + 1 < args.length) {
          config.keyStart = Long.parseLong(args[++i]);
        } else if ("--key-end".equals(arg) && i + 1 < args.length) {
          config.keyEnd = Long.parseLong(args[++i]);
        } else if ("--versions".equals(arg) && i + 1 < args.length) {
          config.versionsPerQualifier = Integer.parseInt(args[++i]);
        } else if ("--output".equals(arg) && i + 1 < args.length) {
          config.outputPath = args[++i];
        } else if ("--zeropadding".equals(arg) && i + 1 < args.length) {
          config.zeroPadding = Integer.parseInt(args[++i]);
        } else if ("--column-family".equals(arg) && i + 1 < args.length) {
          config.columnFamily = args[++i];
        } else if ("--version-anchor-ts".equals(arg) && i + 1 < args.length) {
          config.versionAnchorTs = Long.parseLong(args[++i]);
        } else if ("--version-delta-ms".equals(arg) && i + 1 < args.length) {
          config.versionDeltaMs = Long.parseLong(args[++i]);
        } else if ("--version-window-ms".equals(arg) && i + 1 < args.length) {
          config.versionWindowMs = Long.parseLong(args[++i]);
        } else if ("--no-version-spread".equals(arg)) {
          config.versionSpreadInWindow = false;
        } else if ("--help".equals(arg) || "-h".equals(arg)) {
          printUsage();
          System.exit(0);
        } else {
          throw new IllegalArgumentException("Unknown argument: " + arg);
        }
      }
      if (config.keyEnd <= config.keyStart) {
        throw new IllegalArgumentException("key-end must be > key-start");
      }
      if (config.versionsPerQualifier <= 0) {
        throw new IllegalArgumentException("versions must be > 0");
      }
      return config;
    }

    private static void printUsage() {
      System.out.println("Usage: HBaseBulkLoadTsvGenerator --key-start N --key-end M "
          + "--versions V --output /path/import.tsv [--column-family v] [--zeropadding 20]");
    }
  }
}
