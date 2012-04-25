package net.walnutvision;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.HBaseConfiguration;
import org.apache.hadoop.hbase.client.HTable;
import org.apache.hadoop.hbase.client.HTablePool;
import org.apache.hadoop.hbase.client.Result;
import org.apache.hadoop.hbase.client.ResultScanner;
import org.apache.hadoop.hbase.util.Bytes;

public class BrandParser {
	public BrandParser() {

	}

	// load the dictionary form hbase
	// dictionary is stored in the HashSet
	public void init() {
		try {
			HashMap<String, String> map = new HashMap<String, String>();
			Configuration conf = (Configuration) HBaseConfiguration.create();
			HTablePool hTablePool = new HTablePool(conf, 5);
			String tableName = "test_item_brand";
			HTable brandTable = (HTable) hTablePool.getTable(tableName);
			ResultScanner scanner = brandTable.getScanner(Bytes.toBytes("d"));
			for (Result res : scanner) {
				String brand = Bytes.toString(res.getRow());
				String url = Bytes.toString(res.getValue(Bytes.toBytes("d"),
						Bytes.toBytes("iu_0")));
				if (brand != null) {
					mDictionary.add(brand);
					map.put(brand, url);
				}
			}
			scanner.close();
			Iterator<Entry<String, String>> it = map.entrySet().iterator();
		    while (it.hasNext()) {
		        Map.Entry pairs = (Map.Entry)it.next();
		        System.out.println(pairs.getKey() + " = " + pairs.getValue());
		    }
			hTablePool.closeTablePool(tableName);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	// input the name of the product
	// output the brand of the product, if no brand is found, return empty
	// string
	public String getBrand(String name) {
		// remove leading and tailing whitespace
		name = name.trim();
		int nameLength = name.length();
		for (int i = 0; i < nameLength; ++i) {
			for (int j = i + 1; j <= nameLength; ++j) {
				String subString = name.substring(i, j).toLowerCase();
				subString = subString.replaceAll("　", " ").trim();
				if (subString.isEmpty()) {
					continue;
				}
				if (mDictionary.contains(subString)) {
					return subString;
				}
			}
		}
		return "";
	}

	private HashSet<String> mDictionary = new HashSet<String>();
}
