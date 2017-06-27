package com.softdu.utils;

import lombok.Data;

import java.util.Map;
import java.util.TreeMap;

/**
 * 下载记录，用于断点续传，
 * 下次下载继续上次下载的内容
 */
@Data
public class DownInfo {
    private String fileUrlPath;//文件网络路径
    private Map<String,Piece> pices = new TreeMap<String, Piece>();
    private int threadCount ;//线程数量
    private String filePath; //文件保存路径
    private long fileLength; //文件大小
    private String fileName;
    private long haveDwonLenth = 0;
    private String metaPath;


}
