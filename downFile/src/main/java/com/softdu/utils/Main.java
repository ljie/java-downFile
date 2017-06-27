package com.softdu.utils;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

/**
 * Created by babata on 2017/6/27.
 */
@Slf4j
public class Main {

    public static void main(String[] args) throws IOException, InterruptedException, ExecutionException {
//        String fileUrl = "http://sw.bos.baidu.com/sw-search-sp/software/92f7b2170f9b7/BaiduNetdisk_5.6.1.2.exe";
        final String fileUrl = "http://sw.bos.baidu.com/sw-search-sp/software/7a3c61c1bdf37/War3Setup.exe";
//        String fileUrl = "http://sw.bos.baidu.com/sw-search-sp/software/b9bf5a0aef02b/cloudmusicsetup_2.2.0.59525_baidupc.exe";
        int flag = new DownUtil().startDown(fileUrl, "d:/11");
        if (flag < 0) {
            TimeUnit.SECONDS.sleep(10);
            log.error("down fail error ,try 2 " + fileUrl);
            flag = new DownUtil().startDown(fileUrl, "d:/11");
            if (flag < 0) {
                TimeUnit.SECONDS.sleep(10);
                log.error("down fail error ,try 3 " + fileUrl);
                flag = new DownUtil().startDown(fileUrl, "d:/11");
                if (flag < 0) {
                    log.error("down fail error " + fileUrl);
                }
            }

        }
    }
}
