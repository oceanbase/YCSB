package site.ycsb.db.hbase094.bulkload;

import java.io.IOException;

import org.apache.hadoop.hbase.KeyValue;
import org.apache.hadoop.hbase.client.Put;
import org.apache.hadoop.hbase.io.ImmutableBytesWritable;
import org.apache.hadoop.hbase.util.Bytes;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Counter;
import org.apache.hadoop.mapreduce.Mapper;

/**
 * ImportTsv mapper for one-cell-per-line YCSB bulk load files.
 *
 * <p>Each line: rowKey TAB cf TAB qualifier TAB timestampMs TAB valueBytes
 * (tab-separated; value is raw bytes with code points in [32,127], no TAB).
 */
public class YcsbMultiVersionTsvMapper
    extends Mapper<LongWritable, Text, ImmutableBytesWritable, Put> {

  private static final byte TAB = (byte) '\t';
  private static final int FIELD_COUNT = 5;

  private Counter badLineCount;

  @Override
  protected void setup(Context context) {
    badLineCount = context.getCounter("ImportTsv", "Bad Lines");
  }

  @Override
  protected void map(LongWritable offset, Text value, Context context)
      throws IOException, InterruptedException {
    byte[] lineBytes = value.getBytes();
    int length = value.getLength();
    int[] fieldStart = new int[FIELD_COUNT];
    int[] fieldLen = new int[FIELD_COUNT];
    int fieldIdx = 0;
    int fieldBegin = 0;
    int i = 0;
    for (; i < length; i++) {
      if (TAB == lineBytes[i]) {
        if (fieldIdx >= FIELD_COUNT - 1) {
          markBadLine(offset, "Too many fields", context);
          return;
        }
        fieldStart[fieldIdx] = fieldBegin;
        fieldLen[fieldIdx] = i - fieldBegin;
        fieldIdx++;
        fieldBegin = i + 1;
      }
    }
    if (fieldIdx != FIELD_COUNT - 1) {
      markBadLine(offset, "Expected " + FIELD_COUNT + " fields", context);
      return;
    }
    fieldStart[fieldIdx] = fieldBegin;
    fieldLen[fieldIdx] = length - fieldBegin;

    long tsMs = 0L;
    try {
      tsMs = parseLongField(lineBytes, fieldStart[3], fieldLen[3]);
    } catch (NumberFormatException e) {
      markBadLine(offset, "Invalid timestamp", context);
      return;
    }

    byte[] rowKey = copyField(lineBytes, fieldStart[0], fieldLen[0]);
    byte[] cf = copyField(lineBytes, fieldStart[1], fieldLen[1]);
    byte[] qual = copyField(lineBytes, fieldStart[2], fieldLen[2]);
    byte[] val = copyField(lineBytes, fieldStart[4], fieldLen[4]);

    ImmutableBytesWritable rowKeyWritable = new ImmutableBytesWritable(rowKey);
    Put put = new Put(rowKey);
    KeyValue kv = new KeyValue(rowKey, cf, qual, tsMs, KeyValue.Type.Put, val);
    put.add(kv);
    context.write(rowKeyWritable, put);
  }

  private static long parseLongField(byte[] bytes, int offset, int len) {
    return Long.parseLong(Bytes.toString(bytes, offset, len));
  }

  private static byte[] copyField(byte[] bytes, int offset, int len) {
    byte[] out = new byte[len];
    int i = 0;
    for (; i < len; i++) {
      out[i] = bytes[offset + i];
    }
    return out;
  }

  private void markBadLine(LongWritable offset, String reason, Context context)
      throws IOException, InterruptedException {
    badLineCount.increment(1);
    System.err.println("Bad line at offset " + offset.get() + ": " + reason);
  }
}
