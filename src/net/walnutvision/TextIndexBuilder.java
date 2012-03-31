package net.walnutvision;

import java.io.IOException;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.StringTokenizer;

import org.apache.commons.io.EndianUtils;
import org.apache.hadoop.hbase.HBaseConfiguration;
import org.apache.hadoop.hbase.client.Get;
import org.apache.hadoop.hbase.client.HTable;
import org.apache.hadoop.hbase.client.HTablePool;
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
		private HTablePool mHTablePool = null;

		public ArrayList<String> buildHierarchyCategory(
				ArrayList<String> category) {
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
				ArrayList<String> imageHashArray = HbaseAdapter.getColumn(
						values, family, "fii_");
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
				doc.addField("url", url);
				doc.addField("merchant", merchant);
				// add categories of the product
				for (ArrayList<String> category : categoryArray) {
					ArrayList<String> hierarchyCategoryArray = buildHierarchyCategory(category);
					for (String hierarchyCategory : hierarchyCategoryArray) {
						doc.addField("category", hierarchyCategory);
					}
				}
				// add image ids and image hashes of the product
				HTable imageTable = (HTable) mHTablePool.getTable("image");
				byte[] idColumn = Bytes.toBytes("id_0");
				for (String imageHash : imageHashArray) {
					Get get = new Get(Bytes.toBytes(imageHash));
					get.addColumn(family, idColumn);
					Result result = imageTable.get(get);
					byte[] bId = result.getValue(family, idColumn);
					long imagekey = EndianUtils.readSwappedLong(bId, 0);
					doc.addField("imagehash", imageHash);
					doc.addField("imagekey", imagekey);
					//System.out.println("" + imageId);
				}
				mHTablePool.putTable(imageTable);
				mSolrServer.add(doc);
			} catch (SolrServerException e) {
				e.printStackTrace();
			}
		}

		@Override
		public void setup(Context context) {
			try {
				mSolrServer = new CommonsHttpSolrServer(SolrServerURL);
				mHTablePool = new HTablePool();
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
