package com.lonicera.fs;

import com.lonicera.fs.BootSector.Partition;
import java.util.Arrays;
import lombok.Getter;

public class BootBlock extends Abstract2SectorBlock implements Block {
  private BootSector bootSector;
  @Getter
  private Partition p1;
  @Getter
  private Partition p2;
  @Getter
  private Partition p3;
  @Getter
  private Partition p4;

  private BootBlock(byte[] bytes){
    super(bytes);
    if(unsignByte(bytes[510]) != 0x55 || unsignByte(bytes[511]) != 0xaa){
      throw new IllegalArgumentException("bad boot block");
    }
    bootSector = mapBootSector(bytes);
    p1 = bootSector.getP1();
    p2 = bootSector.getP2();
    p3 = bootSector.getP3();
    p4 = bootSector.getP4();
  }

  private short unsignByte(byte b){
    return (short) (b & 0xff);
  }


  private BootSector mapBootSector(byte[] bytes) {
    byte[] sectorBytes = Arrays.copyOf(bytes, 512);
    return BootSector.map(sectorBytes);
  }

  public static BootBlock map(byte[] bytes){
    return new BootBlock(bytes);
  }

}
