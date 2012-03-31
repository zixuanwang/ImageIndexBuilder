package net.walnutvision;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.io.EndianUtils;
import org.apache.hadoop.hbase.HBaseConfiguration;
import org.apache.hadoop.hbase.client.Result;
import org.apache.hadoop.hbase.client.Scan;
import org.apache.hadoop.hbase.io.ImmutableBytesWritable;
import org.apache.hadoop.hbase.mapreduce.TableMapReduceUtil;
import org.apache.hadoop.hbase.mapreduce.TableMapper;
import org.apache.hadoop.hbase.mapreduce.TableReducer;
import org.apache.hadoop.hbase.util.Bytes;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.mapreduce.Job;
import org.apache.thrift.TException;
import org.apache.thrift.protocol.TBinaryProtocol;
import org.apache.thrift.protocol.TProtocol;
import org.apache.thrift.transport.TFramedTransport;
import org.apache.thrift.transport.TSocket;
import org.apache.thrift.transport.TTransport;
import org.apache.thrift.transport.TTransportException;

public class ImageIndexBuilder {
	static class Mapper1 extends TableMapper<LongWritable, PostingWritable> {
		private TTransport mTransport = null;
		private TProtocol mProtocol = null;
		private ImageDaemon.Client mClient = null;

		public static String byteArrayToHexString(byte[] byteArr) {
			StringBuilder sb = new StringBuilder();
			for (byte b : byteArr) {
				sb.append(Integer.toHexString(0xFF & b));
			}
			return sb.toString();
		}

		public void map(ImmutableBytesWritable row, Result values,
				Context context) throws IOException {
			try {
				String imageHash = Bytes.toString(row.get());
				byte[] family = Bytes.toBytes("d");
				byte[] qualifier = Bytes.toBytes("id_0");
				// imageKey is stored in little endian
				long imageKey = EndianUtils.readSwappedLong(values.getValue(family, qualifier), 0);
				mClient.addImage(imageHash, imageKey);
				List<Bin> boWHistogram = mClient.computeBoWFeature(imageKey);
				mClient.computeColorFeature(imageKey);
				mClient.computeShapeFeature(imageKey);
				try {
					for (Bin bin : boWHistogram) {
						double score = bin.frequency;
						context.write(new LongWritable(bin.visualwordID),
								new PostingWritable(imageKey, -1.0 * score));
					}
				} catch (InterruptedException e) {
					throw new IOException(e);
				}
			} catch (TException e1) {
				e1.printStackTrace();
			}
		}

		@Override
		public void setup(Context context) {
			// set up the connection to the local thrift server
			mTransport = new TFramedTransport(new TSocket("localhost", 9992));
			mProtocol = new TBinaryProtocol(mTransport);
			mClient = new ImageDaemon.Client(mProtocol);
			try {
				mTransport.open();
			} catch (TTransportException e) {
				e.printStackTrace();
			}
		}

		@Override
		public void cleanup(Context context) {
			mTransport.close();
		}
	}

	public static class Reducer1 extends
			TableReducer<LongWritable, PostingWritable, ImmutableBytesWritable> {
		private TTransport mTransport = null;
		private TProtocol mProtocol = null;
		private InvertedIndexDaemon.Client mClient = null;

		@Override
		public void reduce(LongWritable key, Iterable<PostingWritable> values,
				Context context) throws IOException, InterruptedException {
			List<Posting> postingArray = new ArrayList<Posting>();
			for (PostingWritable val : values) {
				Posting p = new Posting();
				p.imageId = val.imageId.get();
				p.score = val.score.get();
				postingArray.add(p);
			}
			try {
				mClient.savePostingList(key.get(), postingArray);
			} catch (TException e) {
				e.printStackTrace();
			}
		}

		@Override
		public void setup(Context context) {
			mTransport = new TFramedTransport(new TSocket("node1", 9991));
			mProtocol = new TBinaryProtocol(mTransport);
			mClient = new InvertedIndexDaemon.Client(mProtocol);
			try {
				mTransport.open();
			} catch (TTransportException e) {
				e.printStackTrace();
			}
		}

		@Override
		public void cleanup(Context context) {
			mTransport.close();
		}
	}

	public static void main(String[] args) throws Exception {
		HBaseConfiguration conf = new HBaseConfiguration();
		Job job = new Job(conf, "ImageIndexBuilder");
		job.setJarByClass(ImageIndexBuilder.class);
		Scan scan = new Scan();
		TableMapReduceUtil.initTableMapperJob("image", scan, Mapper1.class,
				LongWritable.class, PostingWritable.class, job);
		TableMapReduceUtil.initTableReducerJob("image", Reducer1.class, job);
		System.exit(job.waitForCompletion(true) ? 0 : 1);
	}
}
