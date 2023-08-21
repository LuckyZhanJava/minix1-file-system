package com.lonicera.fs;

public class CommandExecuteException extends RuntimeException {

  public CommandExecuteException(String msg){
    super(msg);
  }

  public CommandExecuteException(Throwable throwable){
    super(throwable);
  }
}
