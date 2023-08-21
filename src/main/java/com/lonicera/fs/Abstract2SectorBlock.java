package com.lonicera.fs;

public class Abstract2SectorBlock implements Block {
  public Abstract2SectorBlock(byte[] bytes){
    if(bytes == null || bytes.length != 1024){
      throw new IllegalArgumentException("bytes");
    }
  }
}
