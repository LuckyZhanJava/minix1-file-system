package com.lonicera.fs;

import com.lonicera.fs.BootSector.Partition;
import com.lonicera.fs.SuperBlock.BitMap;
import com.lonicera.fs.SuperBlock.DefaultBitMap;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.RandomAccessFile;
import java.io.Reader;
import java.net.URISyntaxException;
import java.net.URL;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Deque;
import java.util.Formatter;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Supplier;

public class App {

  private static RandomAccessFile randomAccessFile;
  private static int inodeStartSect;
  private static int partitionStartSect;
  private static BitMap inodeMap;
  private static BitMap zoneMap;
  private static Inode pwd;
  private static Inode root;
  private static String pwdDirName;
  private static Deque<DirEntry> pwdEntryList = new LinkedList<>();

  public static void main(String[] args) throws URISyntaxException, FileNotFoundException {
    URL url = App.class.getClassLoader().getResource("rootimage-0.12-hd");
    RandomAccessFile imageFile = new RandomAccessFile(new File(url.toURI()), "r");
    Partition[] partitions = fdisk(imageFile);
    printPartitions(partitions);
    int index = selectPartitions(partitions.length - 1);
    mount(partitions[index]);
    chroot("/");
  }

  private static void chroot(String path) {
    if (path.equals("/")) {
      pwd = root;
      pwdDirName = "/";
    } else if (!path.equals(".")) {
      Deque<DirEntry> entryList = entryChain(path);
      pwdEntryList = entryList;
      DirEntry pwdEntry = entryList.peekLast();
      if(pwdEntry != null){
        pwd = readInode(pwdEntry.getInode());
        pwdDirName = pwdEntry.getName();
      }else{
        pwd = root;
        pwdDirName = "/";
      }
    }
    exploreFileSystem();
  }

  private static Deque<DirEntry> entryChain(String path) {
    Inode parent;
    Deque<DirEntry> entryList;
    if (path.startsWith("/")) {
      parent = root;
      entryList = new LinkedList<>();
    } else {
      parent = pwd;
      entryList = pwdEntryList;
    }
    String[] segs = path.replaceFirst("/", "").split("/");
    for (String seg : segs) {
      DirEntry child = childEntry(parent, seg);
      if(seg.equals("..") && pwdEntryList.size() > 0){
        pwdEntryList.removeLast();
      }else if(seg.equals(".")){
        ;
      }else{
        pwdEntryList.add(child);
      }
      parent = readInode(child.getInode());
    }
    return entryList;
  }

  private static DirEntry childEntry(Inode parent, String pathName) {
    List<DirEntry> entryList = lsInode(parent);
    for (DirEntry entry : entryList) {
      if (entry.getName().equals(pathName)) {
        return entry;
      }
    }
    throw new CommandExecuteException("path not exists");
  }

  private static void printRootPath() {
    System.out.print("[root@localhost " + pwdDirName + "]# ");
  }


  private static void exploreFileSystem() {
    printRootPath();
    Reader reader = new InputStreamReader(System.in);
    BufferedReader br = new BufferedReader(reader);
    try {
      String line = br.readLine();
      while (line != null) {
        if (isHelp(line)) {
          printHelp();
        }
        if (isPwd(line)) {
          printPwd();
        } else if (isCd(line)) {
          String path = arg(line);
          safeCommand(() -> chroot(path));
        } else if (isList(line)) {
          String path = arg(line);
          safeCommand(() -> ls(path));
        } else if (isCat(line)) {
          String path = arg(line);
          safeCommand(() -> catFile(path));
        } else {
          System.err.println("unsupport command :" + line);
        }
        printRootPath();
        line = br.readLine();
      }
    } catch (IOException e) {
      throw new FileSystemParseException(e);
    }
  }

  private static interface Command {

    void eval();
  }

  private static void safeCommand(Command command) {
    try {
      command.eval();
    } catch (CommandExecuteException e) {
      System.err.println(e.getMessage());
    }
  }

  private static Object catFile(String path) {
    Inode inode = readPathInode(path);
    Iterator<byte[]> itr = readZoneBlockIterator(inode.getIzone(), inode.getIsize());
    System.out.println();
    while (itr.hasNext()) {
      byte[] bytes = itr.next();
      System.out.print(new String(bytes));
    }
    return null;
  }

  private static void printPwd() {
    StringBuilder sb = new StringBuilder("/");
    for(DirEntry entry : pwdEntryList){
      sb.append(entry.getName());
      sb.append("/");
    }
    if(sb.length() > 1){
      sb.deleteCharAt(sb.length() - 1);
    }
    System.out.println(sb);
  }

  private static boolean isPwd(String line) {
    return line.trim().equals("pwd");
  }

  private static String arg(String line) {
    String[] commands = line.trim().split("\\s");
    return commands.length == 2 ? commands[1] : ".";
  }

  private static boolean isCat(String line) {
    String[] commands = line.trim().split("\\s");
    return commands.length == 2 && commands[0].equals("cat");
  }

  private static boolean isList(String line) {
    String[] commands = line.trim().split("\\s");
    return commands.length <= 2 && commands[0].equals("ll");
  }

  private static boolean isCd(String line) {
    String[] commands = line.trim().split("\\s");
    return commands.length == 2 && commands[0].equals("cd");
  }

  private static void printHelp() {
    System.out.println("support : cd ll cat");
  }

  private static boolean isHelp(String line) {
    return line.trim().equals("help");
  }

  private static int selectPartitions(int maxIndex) {
    printMountTip();
    Reader reader = new InputStreamReader(System.in);
    BufferedReader br = new BufferedReader(reader);
    try {
      String line = br.readLine();
      while (line != null) {
        try {
          Integer index = Integer.valueOf(line);
          if (index > maxIndex) {
            System.out.println("index overflow.");
            printMountTip();
          } else {
            return index;
          }
        } catch (NumberFormatException e) {
          System.out.println("please enter a number.");
          printMountTip();
        }
        line = br.readLine();
      }
    } catch (IOException e) {
      throw new FileSystemParseException(e);
    }
    return 0;
  }

  private static void printMountTip() {
    System.out.print("mount a partition (e.g.0) : ");
  }

  private static Partition[] fdisk(RandomAccessFile imageFile) {
    BootSector bootSector = readBootSector(imageFile, 0, 1);
    Partition p1 = bootSector.getP1();
    Partition p2 = bootSector.getP2();
    Partition p3 = bootSector.getP3();
    Partition p4 = bootSector.getP4();
    randomAccessFile = imageFile;
    return new Partition[]{p1, p2, p3, p4};
  }

  private static void printPartitions(Partition... partitions) {
    System.out.println("Partitions List :");
    Formatter formatter = new Formatter();
    formatter.format("%3s %10s %10s %15s %15s\r\n", "no", "bootInd", "sysInd", "startSect", "nr_sects");
    for (int i = 0; i < partitions.length; i++) {
      Partition partition = partitions[i];
      formatter
          .format("%3s %10s %10s %15s %15s\r\n", i, partition.getBootInd(), partition.getSysInd(), partition.getStartSect(),
              partition.getNrSects());
    }
    System.out.println(formatter);
  }

  private static void ls(String path) {
    Inode inode = readPathInode(path);
    List<DirEntry> entryList = lsInode(inode);
    printEntryList(entryList);
  }

  private static void printEntryList(List<DirEntry> entryList) {
    Formatter formatter = new Formatter();
    formatter.format("%3s %10s %20s %20s\r\n", "type", "size", "time", "name");
    for (DirEntry dirEntry : entryList) {
      int index = dirEntry.getInode();
      Inode inode = readInode(index);
      LocalDateTime creatTime = LocalDateTime
          .ofEpochSecond(inode.getImtime(), 0, ZoneOffset.of("Z"));
      formatter
          .format("%3s %10s %20s %20s\r\n", type(inode.getImode().intValue()), inode.getIsize(),
              creatTime, dirEntry.getName());
    }
    System.out.println(formatter);
  }

  // 引导块 + 超级块 + inode Map + zone Map + blocks
  private static void mount(Partition partition) {
    // 扇区 sector : 512 byte  块 block : 1024byte
    // 1. 两个扇区为一个逻辑块
    // 2. 文件系统的基本单位是块
    // 3. boot分区的 0 扇区是cpu启动时需要校验的块，510 511 byte需要校验
    partitionStartSect = partition.getStartSect();
    if (partition.getBootInd() != 0) {
      //引导块内是内核的引导指令,这是分区引导块，没有内容
    }

    // 4. 超级块的开始扇区 = 分区开始扇区 + 引导块占用的 2 扇区
    int superSectStart = partitionStartSect + 2;

    // 7. 读取分区的超级块
    SuperBlock superBlock = readSuperBlock(superSectStart, 1);

    // 8. 从超级块中读取 inode 的块数量
    int imapBlocks = superBlock.getImapBlocks();

    // 9. 从超级块中读取 zone 的块数量
    int zmapBlocks = superBlock.getZmapBlocks();

    // 10. 计算 inode 的开始扇区 = 超级块开始扇区 + 超级块（2扇区） + （块位图 + 区位图）*2
    inodeStartSect = superSectStart + 2 + (imapBlocks + zmapBlocks) * 2;

    int ninode = superBlock.getNinodes();
    inodeMap = readBitMap(superSectStart + 2, imapBlocks);
    zoneMap = readBitMap(superSectStart + 2 + imapBlocks, zmapBlocks);

    Inode node = readInode(1);

    root = node;
    pwd = node;
    pwdDirName = "/";
  }

  private static Inode readPathInode(String path) {
    if (path.equals("/")) {
      return readInode(1);
    }

    Inode parent;
    String[] pathSegs;
    if (path.startsWith("/")) {
      parent = root;
      pathSegs = path.replaceFirst("/", "").split("/");
    } else {
      parent = pwd;
      pathSegs = path.split("/");
    }


    for (String seg : pathSegs) {
      parent = childInode(parent, seg);
    }
    return parent;
  }

  private static Inode childInode(Inode parent, String seg) {
    if (!parent.isDir()) {
      throw new CommandExecuteException("path is not a dir");
    }
    List<DirEntry> entryList = lsInode(parent);
    for (DirEntry entry : entryList) {
      if (entry.getName().equals(seg)) {
        return readInode(entry.getInode());
      }
    }
    throw new CommandExecuteException("path not exists");
  }


  private static List<DirEntry> lsInode(Inode inode) {

    char type = type(inode.getImode().intValue());
    if (type != 'd') {
      throw new CommandExecuteException("target is not dir");
    }

    // 12. 获取 inode 对应的 zones
    int[] zones = inode.getIzone();

    long size = inode.getIsize();

    Iterator<byte[]> bytesItr = readZoneBlockIterator(zones, size);

    List<DirEntry> dirList = new LinkedList<>();

    while (bytesItr.hasNext()) {
      byte[] bytes = bytesItr.next();
      List<DirEntry> mapedEntryList = mapDirInBlock(bytes);
      dirList.addAll(mapedEntryList);
    }

    return dirList;
  }

  private static class BlockIndexItr {

    private static interface IntItr {

      IntItr EMPTY = new IntItr() {

        @Override
        public boolean hasNext() {
          return false;
        }

        @Override
        public int next() {
          throw new IllegalStateException();
        }
      };

      boolean hasNext();

      int next();
    }

    private static class LazyIndexItr implements IntItr {

      private IntItr itr;
      private Supplier<int[]> supplier;

      public LazyIndexItr(Supplier<int[]> supplier) {
        this.supplier = supplier;
      }

      @Override
      public boolean hasNext() {
        if (itr == null) {
          itr = new IndexItr(supplier.get());
        }
        return itr.hasNext();
      }

      @Override
      public int next() {
        if (itr == null) {
          itr = new IndexItr(supplier.get());
        }
        return itr.next();
      }
    }

    private static class IndexItr implements IntItr {

      private final int[] indexs;
      private final int offset;
      private final int length;
      private int i;
      private final int limit;

      public IndexItr(int[] indexs) {
        this(indexs, 0, indexs.length);
      }

      public IndexItr(int[] indexs, int offset, int length) {
        if (offset < 0 || offset + length > indexs.length) {
          throw new IllegalArgumentException();
        }
        this.indexs = indexs;
        this.offset = offset;
        this.length = length;
        this.i = offset;
        this.limit = i + length;
      }


      @Override
      public boolean hasNext() {
        return i < limit - 1 && indexs[i] != 0;
      }

      @Override
      public int next() {
        if (i == limit || indexs[i] == 0) {
          throw new IllegalStateException();
        }
        int index = indexs[i];
        i++;
        return index;
      }

    }


    private IntItr level01;
    private IntItr level02;
    private IntItr level03;
    private IntItr currentItr;

    private int[] zones;

    public BlockIndexItr(int[] zones) {
      if (zones.length != 9) {
        throw new IllegalArgumentException();
      }
      this.zones = zones;
      level01 = new IndexItr(zones, 0, 7);
      level02 = zones[7] == 0 ? IntItr.EMPTY : new LazyIndexItr(indexsProvider(zones[7]));
      level03 = zones[8] == 0 ? IntItr.EMPTY : newIntItr(zones[8]);
      currentItr = level01;
    }

    private IntItr newIntItr(int zone) {
      LazyIndexItr itr = new LazyIndexItr(indexsProvider(zone));
      return new IntItr() {
        private IntItr currentItr;

        @Override
        public boolean hasNext() {
          if (currentItr == null) {
            if (itr.hasNext()) {
              currentItr = new LazyIndexItr(indexsProvider(itr.next()));
              return currentItr.hasNext();
            } else {
              return false;
            }
          } else {
            if (currentItr.hasNext()) {
              return true;
            }
            if (itr.hasNext()) {
              currentItr = new LazyIndexItr(indexsProvider(itr.next()));
              return currentItr.hasNext();
            }
          }
          return false;
        }

        @Override
        public int next() {
          return currentItr.next();
        }
      };
    }

    private static Supplier<int[]> indexsProvider(int block) {
      return () -> {
        byte[] bytes = readBlock(block);
        IndexBlock indexBlock = IndexBlock.map(bytes);
        return indexBlock.indexes();
      };
    }

    public boolean hasNext() {
      if (level01 == currentItr) {
        if (currentItr.hasNext()) {
          return true;
        } else {
          currentItr = level02;
        }
      }

      if (level02 == currentItr) {
        if (currentItr.hasNext()) {
          return true;
        } else {
          currentItr = level03;
        }
      }
      return level03.hasNext();
    }

    public int next() {
      return currentItr.next();
    }
  }

  private static Iterator<byte[]> readZoneBlockIterator(int[] zones, long size) {
    BlockIndexItr itr = new BlockIndexItr(zones);
    return new Iterator<byte[]>() {

      private long remaining = size;

      @Override
      public boolean hasNext() {
        return itr.hasNext() && remaining > 0;
      }

      @Override
      public byte[] next() {
        int index = itr.next();
        byte[] bytes = readBlock(index);
        if (remaining > bytes.length) {
          remaining -= bytes.length;
          return bytes;
        } else {
          long newLength = remaining;
          remaining = 0;
          return Arrays.copyOf(bytes, (int) newLength);
        }
      }
    };
  }

  private static List<DirEntry> mapDirInBlock(byte[] zoneBytes) {
    List<DirEntry> entryList = new LinkedList<>();
    for (int i = 0; i < zoneBytes.length; i += 16) {
      DirEntry dirEntry = DirEntry.map(zoneBytes, i);
      if (dirEntry.getInode() > 0) {
        entryList.add(dirEntry);
      }
    }
    return entryList;
  }

  private static char type(int mode) {
    int value = (mode & 0_170000) >> 12;
    if (value == 1) {
      return 'p';
    }
    if (value == 2) {
      return 'c';
    }
    if (value == 4) {
      return 'd';
    }
    if (value == 6) {
      return 'b';
    }
    if (value == 8) {
      return '-';
    }
    if (value == 10) {
      return '-';
    }
    throw new Error(); // should never happen
  }

  private static Inode readInode(int inodeIndex) {
    int actualIndex = inodeIndex - 1;
    int offset = inodeStartSect * Sector.SECTOR_SIZE + actualIndex * Inode.INODE_SIZE;
    byte[] blockBytes = readBytets(offset, Inode.INODE_SIZE);
    return new Inode(blockBytes);
  }

  private static byte[] readBytets(long offset, int length) {
    byte[] bytes = new byte[length];
    try {
      randomAccessFile.seek(offset);
      int len = randomAccessFile.read(bytes, 0, length);
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }

    return bytes;
  }

  private static BitMap readBitMap(int startSect,
      int blockCount) {
    byte[] blockBytes = readBlock(startSect, blockCount);
    return new DefaultBitMap(blockBytes);
  }

  private static SuperBlock readSuperBlock(int startSect,
      int block) {
    byte[] bytes = readBlock(startSect, block);
    SuperBlock superBlock = SuperBlock.map(bytes);
    return superBlock;
  }

  private static BootSector readBootSector(RandomAccessFile randomAccessFile, int startSector,
      int sectorCount) {
    byte[] bytes = readSector(randomAccessFile, startSector, sectorCount);
    BootSector bootSector = BootSector.map(bytes);
    return bootSector;
  }

  private static byte[] readBlock(int block) {
    byte[] blockBytes = new byte[Block.BLOCK_SIZE];
    try {
      randomAccessFile.seek((partitionStartSect + block * 2) * Sector.SECTOR_SIZE);
      int len = randomAccessFile.read(blockBytes, 0, blockBytes.length);
      if (len != blockBytes.length) {
        throw new IllegalStateException("unexpect end");
      }
      return blockBytes;
    } catch (IOException ioException) {
      throw new IllegalStateException(ioException);
    }
  }

  private static byte[] readBlock(int startSector,
      int blockCount) {
    byte[] blockBytes = new byte[Block.BLOCK_SIZE * blockCount];
    try {
      randomAccessFile.seek(startSector * Sector.SECTOR_SIZE);
      int len = randomAccessFile.read(blockBytes, 0, blockBytes.length);
      if (len != blockBytes.length) {
        throw new IllegalStateException("unexpect end");
      }
      return blockBytes;
    } catch (IOException ioException) {
      throw new IllegalStateException(ioException);
    }
  }

  private static byte[] readSector(RandomAccessFile randomAccessFile, int startSector,
      int sectorCount) {
    byte[] sectorBytes = new byte[Sector.SECTOR_SIZE * sectorCount];
    try {
      randomAccessFile.seek(startSector * Sector.SECTOR_SIZE);
      int len = randomAccessFile.read(sectorBytes, 0, sectorBytes.length);
      if (len != sectorBytes.length) {
        throw new IllegalStateException("unexpect end");
      }
      return sectorBytes;
    } catch (IOException ioException) {
      throw new IllegalStateException(ioException);
    }
  }

}
