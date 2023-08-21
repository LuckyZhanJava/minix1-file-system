package com.lonicera.fs;


import java.util.Arrays;
import lombok.Getter;
import org.joou.UByte;
import org.joou.UInteger;

@Getter
public class BootSector implements Sector {

  private Partition p1;
  private Partition p2;
  private Partition p3;
  private Partition p4;

  public interface Partition{
    int PARTITION_SIZE = 16;
    int getBootInd();
    int getSysInd();
    int getStartSect();
    int getNrSects();
  }

  private class SimplePartition implements Partition {

    private UByte bootInd; //引导标识
    private UByte head; //分区起始磁头号
    private UByte sector;//分区起始扇区
    private UByte cyl;//分区起始柱面
    private UByte sysInd; //0x0b-DOS  0x80-Old Minix 0x83-Linux
    private UByte endHead;//分区结束磁头号
    private UByte endSector;//分区结束扇区
    private UByte endCyl;//结束柱面
    @Getter
    private int startSect; //起始物理扇区
    @Getter
    private int nrSects; // 占用扇区数

    private SimplePartition(byte[] bytes){
      if(bytes == null || bytes.length != PARTITION_SIZE){
        throw new IllegalStateException("bytes not enough");
      }
      mapBytes(bytes);
    }

    private void mapBytes(byte[] bytes){
      bootInd = UByte.valueOf(bytes[0]);
      head = UByte.valueOf(bytes[1]);
      sector = UByte.valueOf(bytes[2]);
      cyl = UByte.valueOf(bytes[3]);
      sysInd = UByte.valueOf(bytes[4]);
      endHead = UByte.valueOf(bytes[5]);
      endSector = UByte.valueOf(bytes[6]);
      endCyl = UByte.valueOf(bytes[7]);
      startSect = concatInt(bytes, 8);
      nrSects = concatInt(bytes, 12);
    }

    private int concatInt(byte[] bytes, int start){
      int value = 0;
      for(int step = 0; step < 3; step ++){
        value |= ((bytes[start] & 0xff) << (step * 8));
        start++;
      }
      return UInteger.valueOf(value).intValue();
    }

    @Override
    public int getBootInd() {
      return bootInd.intValue();
    }

    @Override
    public int getSysInd() {
      return sysInd.intValue();
    }
  }

  private BootSector(byte[] bytes){
    mapBytes(bytes);
  }

  private void mapBytes(byte[] bytes){
    byte[] partition1Bytes = Arrays.copyOfRange(bytes, 0x1be, 0x1be + 16);
    p1 = new SimplePartition(partition1Bytes);
    byte[] partition2Bytes = Arrays.copyOfRange(bytes, 0x1ce, 0x1ce + 16);
    p2 = new SimplePartition(partition2Bytes);
    byte[] partition3Bytes = Arrays.copyOfRange(bytes, 0x1de, 0x1de + 16);
    p3 = new SimplePartition(partition3Bytes);
    byte[] partition4Bytes = Arrays.copyOfRange(bytes, 0x1ee, 0x1ee + 16);
    p4 = new SimplePartition(partition4Bytes);
  }

  public static BootSector map(byte[] bytes){
    return new BootSector(bytes);
  }
}
