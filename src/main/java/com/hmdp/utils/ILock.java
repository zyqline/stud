package com.hmdp.utils;

public interface ILock {

      public Boolean tryLock(Long outTime);

      public void delLock();


}
