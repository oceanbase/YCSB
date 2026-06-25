package com.oceanbase.obkv.bulkload;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Random;

/**
 * Generate KQTV CSV aligned with YCSB load + OBKV physical storage.
 *
 * <p>OBHBaseClient puts positive HBase cell timestamps; OBKV stores {@code T} negated.
 * Default value mode matches YCSB {@code RandomByteIterator} (20 printable bytes per field).
 */
public final class BulkLoadDataGenerator {

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

  enum ValueMode {
    RANDOM,
    DATA_INTEGRITY
  }

  enum CsvFormat {
    HEX,
    PLAIN
  }

  private BulkLoadDataGenerator() {
  }

  public static void main(String[] args) throws IOException {
    Config config = Config.parse(args);
    Path output = Paths.get(config.outputPath);
    Path parent = output.getParent();
    if (parent != null && !Files.isDirectory(parent)) {
      Files.createDirectories(parent);
    }

    long rowsWritten = 0L;
    Writer baseWriter = Files.newBufferedWriter(output, StandardCharsets.UTF_8);
    try (BufferedWriter writer = new BufferedWriter(baseWriter, WRITER_BUFFER_BYTES)) {
      for (long keyIndex = config.keyStart; keyIndex < config.keyEnd; keyIndex++) {
        String ycsbKey = formatKey(keyIndex, config.zeroPadding);
        long latestHbaseTs = computeLatestVersionTs(ycsbKey, config);
        for (String field : FIELD_NAMES) {
          byte[] valueBytes = generateValueBytes(ycsbKey, field, config);
          if (CsvFormat.PLAIN == config.csvFormat) {
            for (int i = 0; i < config.versionsPerQualifier; i++) {
              long hbaseTs = latestHbaseTs - (long) i * config.versionDeltaMs;
              writePlainCsvRow(writer, ycsbKey, field, toObkvStoredT(hbaseTs), valueBytes);
              writer.newLine();
              rowsWritten++;
            }
          } else {
            for (int i = 0; i < config.versionsPerQualifier; i++) {
              long hbaseTs = latestHbaseTs - (long) i * config.versionDeltaMs;
              long storedT = toObkvStoredT(hbaseTs);
              writeHexCsvRow(writer, ycsbKey, field, storedT, valueBytes);
              writer.newLine();
              rowsWritten++;
            }
          }
        }
      }
    }

    System.out.println("Wrote " + rowsWritten + " KQTV rows to " + output.toAbsolutePath());
    System.out.println("Keys: [" + config.keyStart + ", " + config.keyEnd + "), V="
        + config.versionsPerQualifier + ", valueMode=" + config.valueMode
        + ", csvFormat=" + config.csvFormat);
  }

  static void writePlainCsvRow(BufferedWriter writer, String ycsbKey, String field,
      long storedT, byte[] valueBytes) throws IOException {
    writer.write(ycsbKey);
    writer.write(',');
    writer.write(field);
    writer.write(',');
    writer.write(Long.toString(storedT));
    writer.write(',');
    writeCsvBytesField(writer, valueBytes);
  }

  static void writeHexCsvRow(BufferedWriter writer, String ycsbKey, String field, long storedT,
      byte[] valueBytes) throws IOException {
    writer.write(toHex(ycsbKey.getBytes(StandardCharsets.UTF_8)));
    writer.write(',');
    writer.write(toHex(field.getBytes(StandardCharsets.UTF_8)));
    writer.write(',');
    writer.write(Long.toString(storedT));
    writer.write(',');
    writer.write(toHex(valueBytes));
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

  /**
   * MySQL/OceanBase LOAD DATA: OPTIONALLY ENCLOSED BY '"' + ESCAPED BY '\\'.
   */
  static String csvField(String value) {
    boolean needQuote = false;
    int i = 0;
    for (; i < value.length(); i++) {
      char c = value.charAt(i);
      if (',' == c || '"' == c || '\n' == c || '\r' == c || '\\' == c) {
        needQuote = true;
        break;
      }
    }
    if (!needQuote) {
      return value;
    }
    StringBuilder sb = new StringBuilder(value.length() + 4);
    sb.append('"');
    for (i = 0; i < value.length(); i++) {
      char c = value.charAt(i);
      if ('"' == c || '\\' == c) {
        sb.append('\\');
      }
      sb.append(c);
    }
    sb.append('"');
    return sb.toString();
  }

  static String csvFieldBytes(byte[] valueBytes) {
    return csvField(new String(valueBytes, StandardCharsets.ISO_8859_1));
  }

  /**
   * OBKV KQTV {@code T} is the negation of the HBase cell timestamp written by OBHBaseClient.
   */
  static long toObkvStoredT(long hbaseCellTimestampMs) {
    return -hbaseCellTimestampMs;
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
    if (ValueMode.DATA_INTEGRITY == config.valueMode) {
      String value = buildDeterministicValue(ycsbKey, fieldName, config.fieldLength);
      return value.getBytes(StandardCharsets.UTF_8);
    }
    return buildYcsbRandomValueBytes(ycsbKey, fieldName, config.fieldLength);
  }

  /**
   * Same byte distribution as {@code site.ycsb.RandomByteIterator}, seeded per (key, field)
   * so bulk load is reproducible (YCSB load itself uses thread-local random per insert).
   */
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

  static String buildDeterministicValue(String key, String fieldkey, int size) {
    StringBuilder sb = new StringBuilder(size);
    sb.append(key).append(':').append(fieldkey);
    while (sb.length() < size) {
      sb.append(':');
      sb.append(sb.toString().hashCode());
    }
    sb.setLength(size);
    return sb.toString();
  }

  static String toHex(byte[] bytes) {
    StringBuilder sb = new StringBuilder(bytes.length * 2);
    for (byte b : bytes) {
      sb.append(String.format("%02X", b & 0xFF));
    }
    return sb.toString();
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
    ValueMode valueMode = ValueMode.RANDOM;
    CsvFormat csvFormat = CsvFormat.HEX;
    String outputPath = "kqtv.csv";

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
        } else if ("--version-anchor-ts".equals(arg) && i + 1 < args.length) {
          config.versionAnchorTs = Long.parseLong(args[++i]);
        } else if ("--version-delta-ms".equals(arg) && i + 1 < args.length) {
          config.versionDeltaMs = Long.parseLong(args[++i]);
        } else if ("--version-window-ms".equals(arg) && i + 1 < args.length) {
          config.versionWindowMs = Long.parseLong(args[++i]);
        } else if ("--value-mode".equals(arg) && i + 1 < args.length) {
          config.valueMode = parseValueMode(args[++i]);
        } else if ("--csv-format".equals(arg) && i + 1 < args.length) {
          config.csvFormat = parseCsvFormat(args[++i]);
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
      if (config.fieldLength <= 0) {
        throw new IllegalArgumentException("field length must be > 0");
      }
      return config;
    }

    private static ValueMode parseValueMode(String mode) {
      if ("random".equalsIgnoreCase(mode)) {
        return ValueMode.RANDOM;
      }
      if ("dataintegrity".equalsIgnoreCase(mode)) {
        return ValueMode.DATA_INTEGRITY;
      }
      throw new IllegalArgumentException("value-mode must be random or dataintegrity, got: "
          + mode);
    }

    private static CsvFormat parseCsvFormat(String format) {
      if ("hex".equalsIgnoreCase(format)) {
        return CsvFormat.HEX;
      }
      if ("plain".equalsIgnoreCase(format)) {
        return CsvFormat.PLAIN;
      }
      throw new IllegalArgumentException("csv-format must be hex or plain, got: " + format);
    }

    private static void printUsage() {
      System.out.println("Usage: BulkLoadDataGenerator --key-start N --key-end M --versions V "
          + "--output /path/file.csv [--zeropadding 20] [--value-mode random|dataintegrity] "
          + "[--csv-format hex|plain]");
    }
  }
}
