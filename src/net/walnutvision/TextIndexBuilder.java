package net.walnutvision;

import java.io.IOException;
import java.net.MalformedURLException;

import org.apache.hadoop.hbase.HBaseConfiguration;
import org.apache.hadoop.hbase.client.Result;
import org.apache.hadoop.hbase.client.Scan;
import org.apache.hadoop.hbase.io.ImmutableBytesWritable;
import org.apache.hadoop.hbase.mapreduce.TableMapReduceUtil;
import org.apache.hadoop.hbase.mapreduce.TableMapper;
import org.apache.hadoop.hbase.util.Bytes;
import org.apache.hadoop.io.NullWritable;
import org.apache.hadoop.mapreduce.Job;
import org.apache.solr.client.solrj.SolrServer;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.impl.CommonsHttpSolrServer;
import org.apache.solr.common.SolrInputDocument;

public class TextIndexBuilder {
	static class Mapper1 extends TableMapper<NullWritable, NullWritable> {
		private SolrServer mSolrServer = null;

		public void map(ImmutableBytesWritable row, Result values,
				Context context) throws IOException {
			try {
				byte[] family = Bytes.toBytes("d");
				String id = Bytes.toString(row.get());
				String name = Bytes.toString(values.getValue(family,
						Bytes.toBytes("nm_0")));
				String price = Bytes.toString(values.getValue(family,
						Bytes.toBytes("ap_0")));
				SolrInputDocument doc = new SolrInputDocument();
				doc.addField("id", id);
				doc.addField("name", name);
				doc.addField("price", price);
				mSolrServer.add(doc);
			} catch (SolrServerException e) {
				e.printStackTrace();
			}
		}

		@Override
		public void setup(Context context) {
			try {
				String url = "http://node1:8080/solr";
				mSolrServer = new CommonsHttpSolrServer(url);
			} catch (MalformedURLException e1) {
				e1.printStackTrace();
			}
		}

		@Override
		public void cleanup(Context context) {
			try {
				mSolrServer.commit();
			} catch (SolrServerException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	public static void main(String[] args) throws Exception {
		HBaseConfiguration conf = new HBaseConfiguration();
		Job job = new Job(conf, "TextIndexBuilder");
		job.setJarByClass(TextIndexBuilder.class);
		job.setMapperClass(Mapper1.class);
		Scan scan = new Scan();
		TableMapReduceUtil.initTableMapperJob("item", scan, Mapper1.class,
				NullWritable.class, NullWritable.class, job);
		TableMapReduceUtil.initTableReducerJob("item", null, job);
		System.exit(job.waitForCompletion(true) ? 0 : 1);
	}
}
