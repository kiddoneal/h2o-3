package hex.deepwater;

import water.Futures;
import water.H2O;
import water.util.Log;

import java.io.IOException;
import java.util.ArrayList;

import static water.gpu.util.img2pixels;

public class DeepWaterImageIterator {

  public DeepWaterImageIterator(ArrayList<String> img_lst, ArrayList<Float> lable_lst, int batch_size, int width, int height, int channels) throws IOException {
    assert label_lst==null || img_lst.size() == lable_lst.size();
    this.img_lst = img_lst;
    this.label_lst = lable_lst;
    this.batch_size = batch_size;
    this.val_num = img_lst.size();
    start_index = 0;
    this.width = width;
    this.height = height;
    this.channels = channels;
    data = new float[2][];
    data[0] = new float[batch_size * width * height * channels];
    data[1] = new float[batch_size * width * height * channels];
    label = new float[2][];
    label[0] = new float[batch_size];
    label[1] = new float[batch_size];
    file = new String[2][];
    file[0] = new String[batch_size];
    file[1] = new String[batch_size];
  }

  //Helper for image conversion
  //TODO: add cropping, distortion, rotation, etc.
  public static class Conversion {
    int width;
    int height;
    int channels;
  }

  class ImageConverter extends H2O.H2OCountedCompleter<ImageConverter> {
    String _file;
    float _label;
    Conversion _conv;
    float[] _destData;
    float[] _destLabel;
    String[] _destFile;
    int _index;
    public ImageConverter(int index, String file, float label, Conversion conv, float[] destData, float[] destLabel, String[] destFile) {
      _index=index;
      _file=file;
      _label=label;
      _conv=conv;
      _destData=destData;
      _destLabel=destLabel;
      _destFile=destFile;
    }

    @Override
    public void compute2() {
      _destFile[_index] = _file;
      _destLabel[_index] = _label;
      try {
        final int len = _conv.width*_conv.height*_conv.channels;
        final int start=_index*len;
        img2pixels(_file, _conv.width, _conv.height, _conv.channels, _destData, start);
      } catch (IOException e) {
        Log.warn(_file + ": " + e.getMessage());
        //e.printStackTrace();
      }
      tryComplete();
    }
  }

  public boolean Next(Futures fs) throws IOException {
    if (start_index < val_num) {
      if (start_index + batch_size > val_num)
        start_index = val_num - batch_size;
      // Multi-Threaded data preparation
      Conversion conv = new Conversion();
      conv.height=this.height;
      conv.width=this.width;
      conv.channels=this.channels;
      for (int i = 0; i < batch_size; i++)
        fs.add(H2O.submitTask(new ImageConverter(i,img_lst.get(start_index+i), label_lst==null?Float.NaN:label_lst.get(start_index+i),conv, data[which],label[which],file[which])));
      fs.blockForPending();
      flip();
      start_index = start_index + batch_size;
      return true;
    } else {
      return false;
    }
  }

  public void flip() { assert(which==0 || which==1); which^=1; }
  public String[] getFiles() { return file[which^1]; }
  public float[] getData() { return data[which^1]; }
  public float[] getLabel() { return label[which^1]; }

  private int which;
  private int val_num;
  private int start_index;
  private int batch_size;
  private int width, height, channels;
  private float[][] data;
  private float[][] label;
  private String[][] file;
  private ArrayList<String> img_lst;
  private ArrayList<Float> label_lst;
}
