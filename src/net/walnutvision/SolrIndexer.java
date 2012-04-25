package net.walnutvision;

import java.io.IOException;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.HBaseConfiguration;
import org.apache.hadoop.hbase.client.HTable;
import org.apache.hadoop.hbase.client.HTablePool;
import org.apache.hadoop.hbase.client.Result;
import org.apache.hadoop.hbase.client.ResultScanner;
import org.apache.hadoop.hbase.client.Scan;
import org.apache.hadoop.hbase.util.Bytes;

public class SolrIndexer {

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		try {
			HBaseConfiguration conf = new HBaseConfiguration(new Configuration());
			HTablePool hTablePool = new HTablePool(conf, 5);
			String tableName = "test_item";
			HTable brandTable = (HTable) hTablePool.getTable(tableName);
			Scan scan = new Scan();
			scan.addColumn(Bytes.toBytes("d"), Bytes.toBytes("nm_0"));
			ResultScanner scanner = brandTable.getScanner(scan);
			for (Result res : scanner) {
				String name = Bytes.toString(res.getValue(Bytes.toBytes("d"),
						Bytes.toBytes("nm_0")));
				System.out.println(name);
			}
			scanner.close();
			hTablePool.closeTablePool(tableName);
		} catch (IOException e) {
			e.printStackTrace();
		}

	}

}
