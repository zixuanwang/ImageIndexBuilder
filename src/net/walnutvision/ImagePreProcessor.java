package net.walnutvision;

import java.io.IOException;

import org.apache.commons.io.EndianUtils;
import org.apache.hadoop.hbase.HBaseConfiguration;
import org.apache.hadoop.hbase.client.Result;
import org.apache.hadoop.hbase.client.Scan;
import org.apache.hadoop.hbase.io.ImmutableBytesWritable;
import org.apache.hadoop.hbase.mapreduce.TableMapReduceUtil;
import org.apache.hadoop.hbase.mapreduce.TableMapper;
import org.apache.hadoop.hbase.util.Bytes;
import org.apache.hadoop.io.NullWritable;
import org.apache.hadoop.mapreduce.Job;
import org.apache.thrift.TException;
import org.apache.thrift.protocol.TBinaryProtocol;
import org.apache.thrift.protocol.TProtocol;
import org.apache.thrift.transport.TFramedTransport;
import org.apache.thrift.transport.TSocket;
import org.apache.thrift.transport.TTransport;
import org.apache.thrift.transport.TTransportException;

public class ImagePreProcessor {
	static class Mapper extends TableMapper<NullWritable, NullWritable> {
		private TTransport mTransport = null;
		private TProtocol mProtocol = null;
		private ImageDaemon.Client mClient = null;

		public void map(ImmutableBytesWritable row, Result values,
				Context context) throws IOException {
			try {
				String imageHash = Bytes.toString(row.get());
				byte[] family = Bytes.toBytes("d");
				byte[] qualifier = Bytes.toBytes("id_0");
				long imageKey = EndianUtils.readSwappedLong(
						values.getValue(family, qualifier), 0);
				mClient.addImage(imageHash, imageKey);
				mClient.cropImage(imageKey, 160, 160);
				mClient.resizeImage(imageKey, 320);
				mClient.segment(imageKey);
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

	public static void main(String[] args) throws Exception {
		HBaseConfiguration conf = new HBaseConfiguration();
		Job job = new Job(conf, "ImagePreProcessor");
		job.setJarByClass(ImagePreProcessor.class);
		job.setMapperClass(Mapper.class);
		job.setNumReduceTasks(0);
		Scan scan = new Scan();
		TableMapReduceUtil.initTableMapperJob("test_image", scan,
				Mapper.class, NullWritable.class, NullWritable.class, job);
		TableMapReduceUtil.initTableReducerJob("test_image", null, job);
		boolean success = job.waitForCompletion(true);
		System.exit(success ? 0 : 1);
	}
}
