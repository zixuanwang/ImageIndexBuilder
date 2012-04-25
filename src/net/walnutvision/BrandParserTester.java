package net.walnutvision;

import java.io.IOException;

import org.apache.hadoop.hbase.HBaseConfiguration;
import org.apache.hadoop.hbase.client.Result;
import org.apache.hadoop.hbase.client.Scan;
import org.apache.hadoop.hbase.io.ImmutableBytesWritable;
import org.apache.hadoop.hbase.mapreduce.TableMapReduceUtil;
import org.apache.hadoop.hbase.mapreduce.TableMapper;
import org.apache.hadoop.hbase.util.Bytes;
import org.apache.hadoop.io.NullWritable;
import org.apache.hadoop.mapreduce.Job;

public class BrandParserTester {
	static class Mapper extends TableMapper<NullWritable, NullWritable> {

		public void map(ImmutableBytesWritable row, Result values,
				Context context) throws IOException {
			String name = Bytes.toString(values.getValue(Bytes.toBytes("d"),
					Bytes.toBytes("nm_0")));
			String brand = mBrandParser.getBrand(name);
//			if(!brand.isEmpty()){
//				System.out.println(brand);
//			}
		}

		@Override
		public void setup(Context context) {
			mBrandParser = new BrandParser();
			mBrandParser.init();
		}

		@Override
		public void cleanup(Context context) {

		}

		private BrandParser mBrandParser;
	}

	public static void main(String[] args) throws Exception {
		HBaseConfiguration conf = new HBaseConfiguration();
		Job job = new Job(conf, "BrandParserTester");
		job.setJarByClass(BrandParserTester.class);
		job.setMapperClass(Mapper.class);
		job.setNumReduceTasks(0);
		Scan scan = new Scan();
		TableMapReduceUtil.initTableMapperJob("test_item", scan, Mapper.class,
				NullWritable.class, NullWritable.class, job);
		TableMapReduceUtil.initTableReducerJob("test_item", null, job);
		boolean success = job.waitForCompletion(true);
		System.exit(success ? 0 : 1);
	}

//	public static void main(String[] args) {
//		BrandParser brandParser = new BrandParser();
//		brandParser.init();
//		try {
//			Configuration conf = (Configuration) HBaseConfiguration.create();
//			HTablePool hTablePool = new HTablePool(conf, 5);
//			String tableName = "test_item";
//			HTable brandTable = (HTable) hTablePool.getTable(tableName);
//			Scan scan = new Scan();
//			scan.addColumn(Bytes.toBytes("d"), Bytes.toBytes("nm_0"));
//			ResultScanner scanner = brandTable.getScanner(scan);
//			for (Result res : scanner) {
//				String name = Bytes.toString(res.getValue(Bytes.toBytes("d"),
//						Bytes.toBytes("nm_0")));
//				String brand = brandParser.getBrand(name);
//				System.out.println(brand);
//			}
//			scanner.close();
//			hTablePool.closeTablePool(tableName);
//		} catch (IOException e) {
//			e.printStackTrace();
//		}
//	}

}
