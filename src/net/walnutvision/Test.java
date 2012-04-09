package net.walnutvision;

import java.io.File;
import java.io.FilenameFilter;
import java.util.StringTokenizer;

public class Test {

	/**
	 * @param args
	 */

	public static void main(String[] args) {
		String test="\t";
		int index = test.indexOf("\t");
		if (index == -1) {
			return;
		}
		String myCategory = test.substring(index + 1);

		System.out.println(""+myCategory.length());
//		StringTokenizer tokenizer = new StringTokenizer(
//				test, "\t");
//		while (tokenizer.hasMoreTokens()) {
//			System.out.println(tokenizer.nextToken());
//		}
		
//		FilenameFilter filefilter = new FilenameFilter() {
//			public boolean accept(File dir, String name) {
//				return name.endsWith(".map");
//			}
//		};
//		File mappingDirectory = new File(
//				"/media/node1/export/walnut/workspace/category");
//		String[] files = mappingDirectory.list(filefilter);
//		for (String file : files) {
//			System.out.println(file);
//		}

		// try {
		// String url = "http://walnutvision.stanford.edu:8080/solr";
		// SolrServer solrServer = new CommonsHttpSolrServer(url);
		// SolrInputDocument doc = new SolrInputDocument();
		// doc.addField("id", "id0");
		// doc.addField("name", "这是一个测试");
		// doc.addField("price", "105");
		// solrServer.add(doc);
		// solrServer.commit();
		//
		// } catch (MalformedURLException e) {
		// // TODO Auto-generated catch block
		// e.printStackTrace();
		// } catch (SolrServerException e) {
		// // TODO Auto-generated catch block
		// e.printStackTrace();
		// } catch (IOException e) {
		// // TODO Auto-generated catch block
		// e.printStackTrace();
		// }

	}

}
