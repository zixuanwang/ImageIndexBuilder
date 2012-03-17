package net.walnutvision;

import java.io.IOException;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.StringTokenizer;

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

		public ArrayList<String> buildHierarchyCategory(ArrayList<String> category) {
			ArrayList<String> ret = new ArrayList<String>();
			int levelCount = 0;
			String current = "";
			for (String token : category) {
				current += "|" + token;
				String facet = "" + levelCount + current;
				ret.add(facet);
				++levelCount;
			}
			return ret;
		}

		public void map(ImmutableBytesWritable row, Result values,
				Context context) throws IOException {
			try {
				byte[] family = Bytes.toBytes("d");
				String id = Bytes.toString(row.get());
				String name = Bytes.toString(values.getValue(family,
						Bytes.toBytes("nm_0")));
				String price = Bytes.toString(values.getValue(family,
						Bytes.toBytes("ap_0")));
				String imageHash = Bytes.toString(values.getValue(family,
						Bytes.toBytes("fii_0")));
				String url = Bytes.toString(values.getValue(family,
						Bytes.toBytes("u_0")));
				String merchant = Bytes.toString(values.getValue(family,
						Bytes.toBytes("m_0")));
				// Add hierarchical faceting
				ArrayList<ArrayList<String>> categoryArray = new ArrayList<ArrayList<String>>();
				int cpIndex = 0;
				while (true) {
					String categoryLine = Bytes.toString(values.getValue(
							family, Bytes.toBytes("cp_" + cpIndex)));
					if (categoryLine == null) {
						break;
					}
					ArrayList<String> category = new ArrayList<String>();
					StringTokenizer tokenizer = new StringTokenizer(
							categoryLine, "|");
					while (tokenizer.hasMoreTokens()) {
						category.add(tokenizer.nextToken());
					}
					categoryArray.add(category);
					++cpIndex;
				}
				SolrInputDocument doc = new SolrInputDocument();
				doc.addField("id", id);
				doc.addField("name", name);
				doc.addField("price", price);
				doc.addField("imagehash", imageHash);
				doc.addField("url", url);
				doc.addField("merchant", merchant);
				for (ArrayList<String> category : categoryArray) {
					ArrayList<String> hierarchyCategoryArray = buildHierarchyCategory(category);
					for (String hierarchyCategory : hierarchyCategoryArray) {
						doc.addField("category", hierarchyCategory);
					}
				}
				mSolrServer.add(doc);
			} catch (SolrServerException e) {
				e.printStackTrace();
			}
		}

		@Override
		public void setup(Context context) {
			try {
				mSolrServer = new CommonsHttpSolrServer(SolrServerURL);
			} catch (MalformedURLException e1) {
				e1.printStackTrace();
			}
		}

		@Override
		public void cleanup(Context context) {

		}
	}

	public static void main(String[] args) throws Exception {
		HBaseConfiguration conf = new HBaseConfiguration();
		Job job = new Job(conf, "TextIndexBuilder");
		job.setJarByClass(TextIndexBuilder.class);
		job.setMapperClass(Mapper1.class);
		job.setNumReduceTasks(0);
		Scan scan = new Scan();
		TableMapReduceUtil.initTableMapperJob("item", scan, Mapper1.class,
				NullWritable.class, NullWritable.class, job);
		TableMapReduceUtil.initTableReducerJob("item", null, job);
		boolean success = job.waitForCompletion(true);
		SolrServer solrServer = new CommonsHttpSolrServer(SolrServerURL);
		solrServer.commit();
		System.exit(success ? 0 : 1);
	}

	public static String SolrServerURL = "http://node1:8080/solr";
}
