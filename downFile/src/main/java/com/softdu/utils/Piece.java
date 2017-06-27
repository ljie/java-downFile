package com.softdu.utils;


import lombok.Data;

/**
 *  一个线程下载内容
 */
@Data
public class Piece {
    private long pieceStartPostion; //开始位置
    private long pieceEndPosition; //结束位置
    private String status; // 0 未完成  1 完成
    private long rangPation; // 当前下载位置
    private long downSize; // 下载了多少


}
