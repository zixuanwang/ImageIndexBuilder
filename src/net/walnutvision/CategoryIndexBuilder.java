package net.walnutvision;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.StringTokenizer;

import org.apache.commons.io.EndianUtils;
import org.apache.hadoop.hbase.HBaseConfiguration;
import org.apache.hadoop.hbase.client.Get;
import org.apache.hadoop.hbase.client.HTable;
import org.apache.hadoop.hbase.client.HTablePool;
import org.apache.hadoop.hbase.client.Put;
import org.apache.hadoop.hbase.client.Result;
import org.apache.hadoop.hbase.client.Scan;
import org.apache.hadoop.hbase.io.ImmutableBytesWritable;
import org.apache.hadoop.hbase.mapreduce.TableMapReduceUtil;
import org.apache.hadoop.hbase.mapreduce.TableMapper;
import org.apache.hadoop.hbase.mapreduce.TableReducer;
import org.apache.hadoop.hbase.util.Bytes;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Job;
import org.apache.thrift.TException;
import org.apache.thrift.protocol.TBinaryProtocol;
import org.apache.thrift.protocol.TProtocol;
import org.apache.thrift.transport.TFramedTransport;
import org.apache.thrift.transport.TSocket;
import org.apache.thrift.transport.TTransport;
import org.apache.thrift.transport.TTransportException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CategoryIndexBuilder {
	static class Mapper1 extends TableMapper<Text, LongWritable> {
		private static final Logger LOG = LoggerFactory
				.getLogger(CategoryIndexBuilder.class);
		private HTablePool mHTablePool = null;

		private TTransport mImageTransport = null;
		private TProtocol mImageProtocol = null;
		private ImageDaemon.Client mImageClient = null;

		public void map(ImmutableBytesWritable row, Result values,
				Context context) throws IOException {
			HTable imageTable = (HTable) mHTablePool.getTable("image");
			HTable imageIndexTable = (HTable) mHTablePool
					.getTable("image_index");
			// The mapper scans item table
			// Select the lowest category to emit
			// Before reducing, make sure the feature is computed
			try {
				byte[] family = Bytes.toBytes("d");
				byte[] idColumn = Bytes.toBytes("id_0");
				byte[] hashColumn = Bytes.toBytes("hash");
				byte[] bowColumn = Bytes.toBytes("bow");
				byte[] colorColumn = Bytes.toBytes("color");
				byte[] shapeColumn = Bytes.toBytes("shape");
				byte[] surfColumn = Bytes.toBytes("surf");
				int imageHashIndex = 0;
				while (true) {
					String imageHash = Bytes.toString(values.getValue(family,
							Bytes.toBytes("fii_" + imageHashIndex)));
					if (imageHash == null) {
						break;
					}
					Get get = new Get(Bytes.toBytes(imageHash));
					get.addColumn(family, idColumn);
					Result result = imageTable.get(get);
					byte[] rowKey = result.getValue(family, idColumn);
					long imageKey = EndianUtils.readSwappedLong(rowKey, 0);
					// Compute different types of features
					get = new Get(rowKey);
					get.addColumn(family, hashColumn);
					if (!imageIndexTable.exists(get)) {
						mImageClient.addImage(imageHash, imageKey);
					}
					get = new Get(rowKey);
					get.addColumn(family, bowColumn);
					if (!imageIndexTable.exists(get)) {
						mImageClient.computeBoWFeature(imageKey);
					}
					get = new Get(rowKey);
					get.addColumn(family, colorColumn);
					if (!imageIndexTable.exists(get)) {
						mImageClient.computeColorFeature(imageKey);
					}
					get = new Get(rowKey);
					get.addColumn(family, shapeColumn);
					if (!imageIndexTable.exists(get)) {
						mImageClient.computeShapeFeature(imageKey);
					}
					get = new Get(rowKey);
					get.addColumn(family, surfColumn);
					if (!imageIndexTable.exists(get)) {
						mImageClient.computeSURFFeature(imageKey);
					}
					int cpIndex = 0;
					while (true) {
						String categoryLine = Bytes.toString(values.getValue(
								family, Bytes.toBytes("cp_" + cpIndex)));
						if (categoryLine == null) {
							break;
						}
						// emit the top layer temporarily
						ArrayList<String> categoryArray = new ArrayList<String>();
						StringTokenizer tokenizer = new StringTokenizer(
								categoryLine, "|");
						while (tokenizer.hasMoreTokens()) {
							categoryArray.add(tokenizer.nextToken());
						}
						if (categoryArray.size() > 0) {
							// System.out.println(categoryArray.get(0));
							context.write(new Text(categoryArray.get(0)),
									new LongWritable(imageKey));
						}
						// context.write(new Text(categoryLine), new
						// LongWritable(
						// imageKey));
						++cpIndex;
					}
					++imageHashIndex;
				}
			} catch (InterruptedException e) {
				e.printStackTrace();
			} catch (TException e) {
				e.printStackTrace();
			}
			mHTablePool.putTable(imageTable);
			mHTablePool.putTable(imageIndexTable);
		}

		@Override
		public void setup(Context context) {
			mHTablePool = new HTablePool();
			// set up the connection to image server
			mImageTransport = new TFramedTransport(new TSocket(
					IMAGE_DAEMON_SERVER_NAME, IMAGE_DAEMON_SERVER_PORT));
			mImageProtocol = new TBinaryProtocol(mImageTransport);
			mImageClient = new ImageDaemon.Client(mImageProtocol);
			try {
				mImageTransport.open();
			} catch (TTransportException e) {
				e.printStackTrace();
			}
		}

		@Override
		public void cleanup(Context context) {
			mImageTransport.close();
		}
	}

	public static class Reducer1 extends
			TableReducer<Text, LongWritable, ImmutableBytesWritable> {
		private HTablePool mHTablePool = null;

		@Override
		public void reduce(Text key, Iterable<LongWritable> values,
				Context context) throws IOException, InterruptedException {
			HTable categoryTable = (HTable) mHTablePool
					.getTable("category_index");
			byte[] family = Bytes.toBytes("d");
			int columnSize = 100000;
			int columnIndex = 0;
			int i = 0;
			ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
			for (LongWritable value : values) {
				++i;
				EndianUtils.writeSwappedLong(outputStream, value.get());
				if (i % columnSize == 0) {
					Put put = new Put(Bytes.toBytes(key.toString()));
					put.add(family, Bytes.toBytes("" + columnIndex),
							outputStream.toByteArray());
					categoryTable.put(put);
					++columnIndex;
					outputStream.reset();
				}
			}
			if (i % columnSize != 0) {
				Put put = new Put(Bytes.toBytes(key.toString()));
				put.add(family, Bytes.toBytes("" + columnIndex),
						outputStream.toByteArray());
				categoryTable.put(put);
				++columnIndex;
			}
			Put put = new Put(Bytes.toBytes(key.toString()));
			put.add(family, Bytes.toBytes("next"), Bytes.toBytes(columnIndex));
			categoryTable.put(put);
			mHTablePool.putTable(categoryTable);
		}

		@Override
		public void setup(Context context) {
			mHTablePool = new HTablePool();
		}

		@Override
		public void cleanup(Context context) {

		}
	}

	public static void main(String[] args) throws Exception {
		HBaseConfiguration conf = new HBaseConfiguration();
		Job job = new Job(conf, "CategoryIndexBuilder");
		job.setJarByClass(CategoryIndexBuilder.class);
		Scan scan = new Scan();
		TableMapReduceUtil.initTableMapperJob("item", scan, Mapper1.class,
				Text.class, LongWritable.class, job);
		TableMapReduceUtil.initTableReducerJob("item", Reducer1.class, job);
		boolean success = job.waitForCompletion(true);
		System.exit(success ? 0 : 1);
	}

	public static String IMAGE_DAEMON_SERVER_NAME = "localhost";
	public static int IMAGE_DAEMON_SERVER_PORT = 9992;
}
