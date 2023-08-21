package com.lonicera.fs;


import org.joou.UShort;

public class IndexBlock extends Abstract2SectorBlock implements Block {

  private int[] indexes;

  private IndexBlock(byte[] bytes) {
    super(bytes);
    indexes = mapIndex(bytes);
  }

  private int[] mapIndex(byte[] bytes) {
    int[] indexes = new int[512];
    for (int i = 0; i < indexes.length; i += 2) {
      int index = concatShort(bytes, i).intValue();
      indexes[i] = index;
    }
    return indexes;
  }

  public int[] indexes(){
    return indexes;
  }

  private UShort concatShort(byte[] bytes, int start) {
    return UShort.valueOf(unsignByte(bytes[start]) | (unsignByte(bytes[start + 1]) << 8));
  }

  private short unsignByte(byte b) {
    return (short) (b & 0xff);
  }


  public static IndexBlock map(byte[] bytes) {
    return new IndexBlock(bytes);
  }

}
