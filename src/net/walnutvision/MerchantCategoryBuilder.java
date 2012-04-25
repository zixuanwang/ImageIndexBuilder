package net.walnutvision;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.DataInputStream;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Iterator;
import java.util.SortedSet;
import java.util.TreeSet;

import org.apache.hadoop.hbase.HBaseConfiguration;
import org.apache.hadoop.hbase.client.Result;
import org.apache.hadoop.hbase.client.Scan;
import org.apache.hadoop.hbase.io.ImmutableBytesWritable;
import org.apache.hadoop.hbase.mapreduce.TableMapReduceUtil;
import org.apache.hadoop.hbase.mapreduce.TableMapper;
import org.apache.hadoop.hbase.mapreduce.TableReducer;
import org.apache.hadoop.hbase.util.Bytes;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Job;

public class MerchantCategoryBuilder {
	static class Mapper1 extends TableMapper<Text, Text> {

		public void map(ImmutableBytesWritable row, Result values,
				Context context) throws IOException {
			try {
				byte[] family = Bytes.toBytes("d");
				byte[] bMerchant = Bytes.toBytes("m_0");
				String merchant = Bytes.toString(values.getValue(family,
						bMerchant));
				int categoryIndex = 0;
				while (true) {
					String category = Bytes.toString(values.getValue(family,
							Bytes.toBytes("cp_" + categoryIndex)));
					if (category == null) {
						break;
					}
					context.write(new Text(merchant), new Text(category));
					++categoryIndex;
				}
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}

		@Override
		public void setup(Context context) {

		}

		@Override
		public void cleanup(Context context) {

		}
	}

	public static class Reducer1 extends
			TableReducer<Text, Text, ImmutableBytesWritable> {
		@Override
		public void reduce(Text key, Iterable<Text> values, Context context)
				throws IOException, InterruptedException {
			String filePath = OUTPUT_DIRECTORY + key.toString();
			HashMap<String, String> map = new HashMap<String, String>();
			try {
				// read existing mapping
				FileInputStream fstream = new FileInputStream(filePath);
				DataInputStream in = new DataInputStream(fstream);
				BufferedReader br = new BufferedReader(
						new InputStreamReader(in));
				String strLine;
				while ((strLine = br.readLine()) != null) {
					int index = strLine.indexOf("\t");
					if (index == -1) {
						continue;
					}
					String siteCategory = strLine.substring(0, index);
					String myCategory = strLine.substring(index);
					map.put(siteCategory, myCategory);
				}
				in.close();
			} catch (IOException e) {
			}
			BufferedWriter out = new BufferedWriter(new FileWriter(filePath));
			SortedSet<String> uniqueSet = new TreeSet<String>();
			for (Text value : values) {
				uniqueSet.add(value.toString());
			}
			Iterator<String> it = uniqueSet.iterator();
			while (it.hasNext()) {
				String category = it.next();
				if (map.containsKey(category)) {
					out.write(category + map.get(category));
				} else {
					out.write(category);
				}
				out.newLine();
			}
			out.close();
		}

		@Override
		public void setup(Context context) {
		}

		@Override
		public void cleanup(Context context) {

		}
	}

	public static void main(String[] args) throws Exception {
		HBaseConfiguration conf = new HBaseConfiguration();
		Job job = new Job(conf, "MerchantCategoryBuilder");
		job.setJarByClass(MerchantCategoryBuilder.class);
		Scan scan = new Scan();
		TableMapReduceUtil.initTableMapperJob("test_item", scan, Mapper1.class,
				Text.class, Text.class, job);
		TableMapReduceUtil.initTableReducerJob("test_item", Reducer1.class, job);
		boolean success = job.waitForCompletion(true);
		System.exit(success ? 0 : 1);
	}

	public static String OUTPUT_DIRECTORY = "/export/walnut/workspace/category/";
}
