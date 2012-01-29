package net.walnutvision;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import org.apache.hadoop.io.DoubleWritable;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.Writable;

public class PostingWritable implements Writable {

	@Override
	public void readFields(DataInput in) throws IOException {
		imageId.readFields(in);
		score.readFields(in);
	}

	@Override
	public void write(DataOutput out) throws IOException {
		imageId.write(out);
		score.write(out);
	}

	public PostingWritable(long imageId, double score) {
		set(new LongWritable(imageId), new DoubleWritable(score));
	}

	public PostingWritable() {
		set(new LongWritable(0), new DoubleWritable(0));
	}

	public void set(LongWritable imageId, DoubleWritable score) {
		this.imageId = imageId;
		this.score = score;
	}

	public LongWritable imageId;
	public DoubleWritable score;
}
