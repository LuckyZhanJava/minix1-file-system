package com.lonicera.fs;

import lombok.Getter;
import org.joou.UInteger;
import org.joou.UShort;

public class SuperBlock extends Abstract2SectorBlock implements Block {

  public interface BitMap{
    int first0();
    int first1();
  }

  public static class DefaultBitMap implements BitMap {
    private byte[] bytes;

    public DefaultBitMap(byte[] bytes){
      this.bytes = bytes;
    }

    @Override
    public int first0() {
      int position = 0;
      for(int i = 0; i < bytes.length; i++){
        if(bytes[i] == -1){
          position += 8;
        } else {
          position += Integer.numberOfLeadingZeros(bytes[i]);
        }
      }
      return position;
    }

    @Override
    public int first1() {
      int position = 0;
      for(int i = 0; i < bytes.length; i++){
        if(bytes[i] == -1){
          position += 8;
        } else {
          position += Integer.numberOfLeadingZeros(~(bytes[i]));
        }
      }
      return position;
    }

  }

  @Getter
  private int ninodes; //i节点数量
  @Getter
  private int nzones; //逻辑块数量
  @Getter
  private int imapBlocks; //inode map 占用块数
  @Getter
  private int zmapBlocks; //逻辑块位图数量
  @Getter
  private int firstDatazone;//数据区中第一个逻辑块号

  private int logZoneSize; //log2 磁盘块/逻辑块

  private int maxSize;//最大文件长度

  private UShort magic; //文件系统 magic 数

  private SuperBlock(byte[] bytes){
    super(bytes);
    mapBytes(bytes);
  }

  public static SuperBlock map(byte[] bytes) {
    return new SuperBlock(bytes);
  }

  private void mapBytes(byte[] bytes){
    ninodes = concatShort(bytes, 0).intValue();
    nzones = concatShort(bytes, 2).intValue();
    imapBlocks = concatShort(bytes, 4).intValue();
    zmapBlocks = concatShort(bytes, 6).intValue();
    firstDatazone = concatShort(bytes, 8).intValue();
    logZoneSize = concatShort(bytes, 10).intValue();
    maxSize = concatInt(bytes, 12).intValue();
    magic = concatShort(bytes, 16);
  }

  private UShort concatShort(byte[] bytes, int start){
    return UShort.valueOf(unsignByte(bytes[start]) | (unsignByte(bytes[start + 1]) << 8));
  }

  private short unsignByte(byte b){
    return (short) (b & 0xff);
  }

  private UInteger concatInt(byte[] bytes, int start){
    long value = 0;
    for(int step = 0; step <= 4; step ++){
      value |= ((unsignByte(bytes[start])) << (step * 8));
      start++;
    }
    return UInteger.valueOf(value);
  }

}
