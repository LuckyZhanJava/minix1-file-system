package com.lonicera.fs;


import lombok.Getter;
import org.joou.UShort;

@Getter
public class DirEntry {
  private int inode;
  private String name;

  private DirEntry(){

  }

  public static DirEntry map(byte[] bytes, int offset){
    DirEntry dirEntry = new DirEntry();
    dirEntry.inode = concatShort(bytes, offset);
    dirEntry.name = concatString(bytes, offset + 2, 14);
    return dirEntry;
  }

  private static String concatString(byte[] bytes, int start, int count){
    int effectCount = 0;
    for(int i = start; i < start + count; i++){
      if(bytes[i] != '\0'){
        effectCount ++;
      }else {
        return new String(bytes, start, effectCount);
      }
    }
    return new String(bytes, start, effectCount);
  }

  private static int concatShort(byte[] bytes, int start){
    return UShort.valueOf(unsignByte(bytes[start]) | (unsignByte(bytes[start + 1]) << 8)).intValue();
  }

  private static int unsignByte(byte b){
    return (int) (b & 0xff);
  }

  @Override
  public String toString() {
    return "DirEntry{" +
        "inode=" + inode +
        ", name='" + name + '\'' +
        '}';
  }
}
