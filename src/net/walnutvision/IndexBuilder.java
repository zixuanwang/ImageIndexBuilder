package net.walnutvision;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.HashMap;
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
import org.apache.solr.client.solrj.SolrServer;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.impl.CommonsHttpSolrServer;
import org.apache.solr.common.SolrInputDocument;
import org.apache.thrift.TException;
import org.apache.thrift.protocol.TBinaryProtocol;
import org.apache.thrift.protocol.TProtocol;
import org.apache.thrift.transport.TFramedTransport;
import org.apache.thrift.transport.TSocket;
import org.apache.thrift.transport.TTransport;
import org.apache.thrift.transport.TTransportException;

public class IndexBuilder {
	static class Mapper1 extends TableMapper<Text, LongWritable> {
		private SolrServer mSolrServer = null;
		private HTablePool mHTablePool = new HTablePool();
		private HashMap<String, HashMap<String, String>> mMerchantCategoryMap = new HashMap<String, HashMap<String, String>>();
		private TTransport mImageTransport = null;
		private TProtocol mImageProtocol = null;
		private ImageDaemon.Client mImageClient = null;

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

		// This function converts each category in one merchant to the global
		// category
		public ArrayList<String> convertCategoryArray(String merchant,
				ArrayList<String> categoryArray) {
			ArrayList<String> ret = new ArrayList<String>();
			HashMap<String, String> merchantCategoryMap = mMerchantCategoryMap
					.get(merchant);
			if (merchantCategoryMap != null) {
				for (String category : categoryArray) {
					if (merchantCategoryMap.containsKey(category)) {
						String newCategory = merchantCategoryMap.get(category);
						StringTokenizer tokenizer = new StringTokenizer(
								newCategory, "\t");
						while (tokenizer.hasMoreTokens()) {
							ret.add(tokenizer.nextToken());
						}
					}
				}
			}
			return ret;
		}

		public ArrayList<Long> getImageKey(ArrayList<String> imageHashArray) {
			ArrayList<Long> imageKeyArray = new ArrayList<Long>();
			HTable imageTable = (HTable) mHTablePool.getTable("image");
			byte[] family = Bytes.toBytes("d");
			byte[] idColumn = Bytes.toBytes("id_0");
			for (String imageHash : imageHashArray) {
				Get get = new Get(Bytes.toBytes(imageHash));
				get.addColumn(family, idColumn);
				Result result;
				try {
					result = imageTable.get(get);
					byte[] rowKey = result.getValue(family, idColumn);
					long imageKey = EndianUtils.readSwappedLong(rowKey, 0);
					imageKeyArray.add(imageKey);
				} catch (IOException e) {
				}
			}
			mHTablePool.putTable(imageTable);
			return imageKeyArray;
		}

		public void computeFeature(ArrayList<String> imageHashArray,
				ArrayList<Long> imageKeyArray) {
			if (imageHashArray.size() != imageKeyArray.size()) {
				return;
			}
			byte[] family = Bytes.toBytes("d");
			byte[] hashColumn = Bytes.toBytes("hash");
			byte[] bowColumn = Bytes.toBytes("bow");
			byte[] colorColumn = Bytes.toBytes("color");
			byte[] shapeColumn = Bytes.toBytes("shape");
			byte[] surfColumn = Bytes.toBytes("surf");
			HTable imageIndexTable = (HTable) mHTablePool
					.getTable("image_index");
			for (int i = 0; i < imageHashArray.size(); ++i) {
				String imageHash = imageHashArray.get(i);
				long imageKey = imageKeyArray.get(i);
				byte[] rowKey = Bytes.toBytes(EndianUtils.swapLong(imageKey));
				try {
					// Compute different types of features
					Get get = new Get(rowKey);
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
				} catch (IOException e) {
					e.printStackTrace();
				} catch (TException e) {
					e.printStackTrace();
				}
			}
			mHTablePool.putTable(imageIndexTable);
		}

		public void map(ImmutableBytesWritable row, Result values,
				Context context) throws IOException {
			try {
				byte[] family = Bytes.toBytes("d");
				String id = Bytes.toString(row.get());
				ArrayList<String> imageHashArray = HbaseAdapter.getColumn(
						values, family, "fii_");
				ArrayList<Long> imageKeyArray = getImageKey(imageHashArray);
				if (imageHashArray.size() != imageKeyArray.size()) {
					System.out.println("Error in imageKeyArray size");
					return;
				}
				String name = Bytes.toString(values.getValue(family,
						Bytes.toBytes("nm_0")));
				byte[] bPrice = values.getValue(family, Bytes.toBytes("pp_0"));
				if (bPrice == null) {
					bPrice = values.getValue(family, Bytes.toBytes("pr_0"));
				}
				String price = Bytes.toString(bPrice);
				String url = Bytes.toString(values.getValue(family,
						Bytes.toBytes("u_0")));
				String merchant = Bytes.toString(values.getValue(family,
						Bytes.toBytes("m_0")));
				// Add hierarchical faceting
				ArrayList<ArrayList<String>> categoryArray = new ArrayList<ArrayList<String>>();
				ArrayList<String> categoryColumn = convertCategoryArray(
						merchant, HbaseAdapter.getColumn(values, family, "cp_"));
				if (categoryColumn.isEmpty()) {
					return;
				}
				for (String column : categoryColumn) {
					ArrayList<String> category = new ArrayList<String>();
					StringTokenizer tokenizer = new StringTokenizer(column, "|");
					while (tokenizer.hasMoreTokens()) {
						category.add(tokenizer.nextToken());
					}
					categoryArray.add(category);
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
				for (int i = 0; i < imageHashArray.size(); ++i) {
					String imageHash = imageHashArray.get(i);
					long imageKey = imageKeyArray.get(i);
					doc.addField("imagehash", imageHash);
					doc.addField("imagekey", imageKey);
				}
				mSolrServer.add(doc);
				// compute feature
				computeFeature(imageHashArray, imageKeyArray);
				// emit to reducer
				for (String oneCategory : categoryColumn) {
					for (Long imageKey : imageKeyArray) {
						try {
							if (oneCategory.startsWith("图书音像")) {
								context.write(new Text("图书音像"),
										new LongWritable(imageKey));
							} else {
								context.write(new Text(oneCategory),
										new LongWritable(imageKey));
							}
						} catch (InterruptedException e) {
							e.printStackTrace();
						}
					}
				}
			} catch (SolrServerException e) {
				e.printStackTrace();
			}
		}

		public void loadMappingFiles(String mappingDirectory) {
			// load the mapping file
			// only read files end with .map
			FilenameFilter fileFilter = new FilenameFilter() {
				public boolean accept(File dir, String name) {
					return name.endsWith(".map");
				}
			};
			File mappingDirectoryFile = new File(mappingDirectory);
			File[] mappingFiles = mappingDirectoryFile.listFiles(fileFilter);
			for (File mappingFile : mappingFiles) {
				try {
					int dotIndex = mappingFile.getName().lastIndexOf(".");
					if (dotIndex != -1) {
						// get the merchant name
						String merchant = mappingFile.getName().substring(0,
								dotIndex);
						HashMap<String, String> merchantCategoryMap = new HashMap<String, String>();
						DataInputStream in = new DataInputStream(
								new FileInputStream(mappingFile));
						BufferedReader br = new BufferedReader(
								new InputStreamReader(in));
						String strLine;
						while ((strLine = br.readLine()) != null) {
							int index = strLine.indexOf("\t");
							if (index == -1) {
								continue;
							}
							String siteCategory = strLine.substring(0, index);
							String myCategory = strLine.substring(index + 1);
							// save the mapping in the row
							merchantCategoryMap.put(siteCategory, myCategory);
						}
						in.close();
						mMerchantCategoryMap.put(merchant, merchantCategoryMap);
					}
				} catch (IOException e) {
				}
			}
		}

		@Override
		public void setup(Context context) {
			loadMappingFiles(MappingDirectory);
			// set up the connection to image server
			mImageTransport = new TFramedTransport(new TSocket(
					ImageDaemonServerAddress, ImageDaemonServerPort));
			mImageProtocol = new TBinaryProtocol(mImageTransport);
			mImageClient = new ImageDaemon.Client(mImageProtocol);
			try {
				mImageTransport.open();
			} catch (TTransportException e) {
				e.printStackTrace();
			}
			try {
				mSolrServer = new CommonsHttpSolrServer(SolrServerURL);
			} catch (MalformedURLException e1) {
				e1.printStackTrace();
			}
			System.out.println("Mapper setup done.");
		}

		@Override
		public void cleanup(Context context) {
			mImageTransport.close();
			try {
				mSolrServer.commit();
			} catch (SolrServerException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	public static class Reducer1 extends
			TableReducer<Text, LongWritable, ImmutableBytesWritable> {
		private HTablePool mHTablePool = new HTablePool();

		public int getUniqueId() {
			HTable categoryTable = (HTable) mHTablePool
					.getTable("category_index");
			byte[] family = Bytes.toBytes("d");
			byte[] id = Bytes.toBytes("id");
			byte[] rowKey = Bytes.toBytes("unique_id");
			long nextId = -1;
			try {
				nextId = categoryTable.incrementColumnValue(rowKey, family, id,
						1);
			} catch (IOException e) {
				e.printStackTrace();
			}
			return (int) nextId;
		}

		@Override
		public void reduce(Text key, Iterable<LongWritable> values,
				Context context) throws IOException, InterruptedException {
			int colorId = getUniqueId();
			int shapeId = getUniqueId();
			int surfId = getUniqueId();
			int buildId = getUniqueId();
			if (colorId != -1 && shapeId != -1 && surfId != -1 && buildId != -1) {
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
				// add treeIndex to the row
				Put put = new Put(Bytes.toBytes(key.toString()));
				put.add(family, Bytes.toBytes("color"),
						Bytes.toBytes(EndianUtils.swapInteger(colorId)));
				put.add(family, Bytes.toBytes("shape"),
						Bytes.toBytes(EndianUtils.swapInteger(shapeId)));
				put.add(family, Bytes.toBytes("surf"),
						Bytes.toBytes(EndianUtils.swapInteger(surfId)));
				put.add(family, Bytes.toBytes("id"),
						Bytes.toBytes(EndianUtils.swapInteger(buildId)));
				categoryTable.put(put);
				mHTablePool.putTable(categoryTable);
			}
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
		Job job = new Job(conf, "IndexBuilder");
		job.setJarByClass(IndexBuilder.class);
		Scan scan = new Scan();
		TableMapReduceUtil.initTableMapperJob("item", scan, Mapper1.class,
				Text.class, LongWritable.class, job);
		TableMapReduceUtil.initTableReducerJob("item", Reducer1.class, job);
		boolean success = job.waitForCompletion(true);
		TTransport mANNTreeTransport = null;
		TProtocol mANNTreeProtocol = null;
		ANNTreeDaemon.Client mANNTreeDaemonClient = null;
		mANNTreeTransport = new TFramedTransport(new TSocket(
				ANNTreeDaemonServerAddress, ANNTreeDaemonServerPort));
		mANNTreeProtocol = new TBinaryProtocol(mANNTreeTransport);
		mANNTreeDaemonClient = new ANNTreeDaemon.Client(mANNTreeProtocol);
		try {
			mANNTreeTransport.open();
			mANNTreeDaemonClient.buildAllCategory();
		} catch (TTransportException e) {
			e.printStackTrace();
		} catch (TException e) {
			e.printStackTrace();
		}
		mANNTreeTransport.close();
		System.exit(success ? 0 : 1);
	}

	public static String SolrServerURL = "http://node1:8080/solr";
	public static String MappingDirectory = "/export/walnut/workspace/category/";
	public static String ImageDaemonServerAddress = "localhost";
	public static String ANNTreeDaemonServerAddress = "node1";
	public static int ImageDaemonServerPort = 9992;
	public static int ANNTreeDaemonServerPort = 9999;
}
