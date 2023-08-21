package com.lonicera.fs;

import lombok.Getter;
import org.joou.UShort;

@Getter
public class Inode {
  //1110_110_101_000_001

  public static final int INODE_SIZE = 32;

  private UShort imode;
  private UShort iuid;
  private long isize;
  private long imtime;
  private short igid;
  private short inlinks;
  private int[] izone;

  public Inode(byte[] bytes){
    imode = concatShort(bytes, 0);
    iuid = concatShort(bytes, 2);
    isize = concatLong(bytes, 4);
    imtime = concatLong(bytes, 8);
    igid = unsignByte(bytes[12]);
    inlinks = unsignByte(bytes[13]);
    izone = mapIzone(bytes, 14);
  }

  private int[] mapIzone(byte[] bytes, int start){
    int[] izones = new int[9];
    for(int i = 0; i < izones.length; i++){
      izones[i] = concatShort(bytes, start).intValue();
      start += 2;
    }
    return izones;
  }

  private UShort concatShort(byte[] bytes, int start){
    return UShort.valueOf(unsignByte(bytes[start]) | (unsignByte(bytes[start + 1]) << 8));
  }

  private short unsignByte(byte b){
    return (short) (b & 0xff);
  }

  private long concatLong(byte[] bytes, int start){
    long value = 0;
    for(int step = 0; step < 4; step ++){
      value |= ((unsignByte(bytes[start])) << (step * 8));
      start++;
    }
    return value;
  }

  public boolean isDir(){
    return (imode.longValue() & 0b1111_0000_0000_0000) == 0b0100_0000_0000_0000;
  }
}
