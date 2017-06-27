package com.softdu.utils;

import com.alibaba.fastjson.JSON;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.*;

/**
 * Created by babata on 2017/6/24.
 */
@Slf4j
public class DownUtil {

    public volatile DownInfo downInfo;
    ExecutorService executor = Executors.newFixedThreadPool(10); //这个线程不宜太大
    private volatile Set<String> failThreadSet = new TreeSet();
    private volatile Set<String> complieTreadSet = new TreeSet();
    private volatile int flag = 0;  //下载状态 : 0 正在下载， 1下载成功，  -1下载过程中有线程下载失败 -2下载过程产生的线程总数大于预计线程总数 -3下载过程中网络速度为0的次数超过15次
    private long oneSecondDown = 0;
    private long zeroSpeedCount; // 速度为0的 有15次则放弃下载，若某一次下载速度不为0 则zeroSpeedCount = 0
    private String metaPath;
    int allTreadCount = 0;

    /**
     * 下载文件
     *
     * @param fileUrl
     * @param saveFilePath
     * @throws ExecutionException
     * @throws InterruptedException
     */
    public void downFile(final String fileUrl, final String saveFilePath) throws ExecutionException, InterruptedException {
        getDownInfo(fileUrl, saveFilePath);
        //打印 下载进度
        executor.submit(new Runnable() {
            @Override
            public void run() {

                while (true) {
                    log.info(metaPath + " down failThreadCount:  " + failThreadSet.size() + " successThreadCount : " + complieTreadSet.size() + " / allThreadCount :" +
                            allTreadCount + " ,  (complete : all) (" + (failThreadSet.size() + complieTreadSet.size()) + " : " + allTreadCount + " )");

                    if ((failThreadSet.size() + complieTreadSet.size()) == allTreadCount) {
                        if (failThreadSet.size() != 0) {//有失败线程
                            log.info(metaPath + " down fail : 下载过程中有线程下载失败 ");
                            dealResualt(-1);
                            break;
                        } else {//下载线程全部是成功的
                            log.info(metaPath + " down success ");
                            dealResualt(1);
                            break;
                        }
                    } else if ((failThreadSet.size() + complieTreadSet.size()) > allTreadCount) { //下载线程大于总数异常
                        log.info(metaPath + " down fail : 下载过程产生的线程总数大于预计线程总数 ");
                        dealResualt(-2);
                    } else {
                        long downAll = 0;
                        for (String threadId : downInfo.getPices().keySet()) {
                            Piece piece = downInfo.getPices().get(threadId);
                            downAll += piece.getDownSize();
                        }
                        long speed = (((downAll - oneSecondDown) == downAll) ? 0 : ((downAll - oneSecondDown) / 1024));
                        if (speed < 0) speed = 0;
                        //计算下载速度
                        log.info(metaPath + " down file ，have down " + downAll / 1024 / 1024 + "M / file all size " + downInfo.getFileLength() / 1024 / 1025 + "M" + " , speed " + speed + " kb/s  , zeroSpeedCount " + zeroSpeedCount);
                        //如果下载速度15次都出现0kb每秒，重新下载文件
                        if (speed == 0) {
                            if (zeroSpeedCount >= 15) {
                                log.info(metaPath + " the speed = 0 happend 15 frequency down client retry , fail thread failCount:" + JSON.toJSONString(failThreadSet) + " ,completeCount :" + complieTreadSet.size() + " retry again");
                                dealResualt(-3);
                                break;
                            }
                            zeroSpeedCount++;
                        }
                        oneSecondDown = downAll;
                        try {
                            TimeUnit.SECONDS.sleep(1);
                        } catch (InterruptedException e) {
                            log.error(this.getClass().getName(), e);
                        }

                    }
                }

            }
        });

        //将下载快照保存在meta临时文件中，以便重新下载时继续上次下载的位置继续下载
        executor.submit(new Runnable() {
            @Override
            public void run() {
                while (true) {
                    if ((failThreadSet.size() + complieTreadSet.size()) == allTreadCount) {
                        if (failThreadSet.size() == 0) {
                            break;
                        }
                    }
                    if (executor.isShutdown()) {
                        //如果线程池关闭，那么跳出循环
                        break;
                    }
                    saveMeta2File();
                    try {
                        Thread.sleep(200);
                    } catch (InterruptedException e) {
                        log.error(this.getClass().getName(), e);
                    }
                }
            }
        });
        //启动下载文件
        for (String threadId : downInfo.getPices().keySet()) {
            Piece piece = downInfo.getPices().get(threadId);
            Future future = executor.submit(new DownThread(piece.getPieceStartPostion(), piece.getPieceEndPosition(), piece.getStatus(),
                    piece.getRangPation(), piece.getDownSize(), downInfo.getFileUrlPath(), threadId, downInfo.getFilePath()));
        }

    }


    /**
     * 处理文件下载结果
     *
     * @param flag
     */
    private void dealResualt(int flag) {
        this.flag = flag;
        executor.shutdown();

    }

    /**
     * 计算下载文件信息：
     * 需要多少线程、每个线程需要下载多大，下载文件的大小、保存位置
     *
     * @param fileUrl
     * @param saveFilePath
     * @return
     * @throws IOException
     */
    void getDownInfo(String fileUrl, String saveFilePath) {
        ByteArrayOutputStream out = null;
        FileInputStream fileInputStream = null;
        RandomAccessFile randomAccessFile = null;
        HttpURLConnection connection = null;

        try {
            log.info("file path " + saveFilePath);
            /**
             * 文件命名
             */
            String fileName = fileUrl.substring(fileUrl.lastIndexOf("/") + 1,
                    fileUrl.lastIndexOf("?") > 0 ? fileUrl.lastIndexOf("?")
                            : fileUrl.length());
            /**
             * 获取文件长度
             */
            URL url = new URL(fileUrl);
            connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(10000);
            connection.setRequestMethod("GET");
            connection.setReadTimeout(10000);
            long fileLength = connection.getContentLength();
            if (fileLength == 0) {
                log.error(metaPath + " network error , please try  again latter");
                return;
            }
            downInfo = new DownInfo();
            /**
             * 下载信息，保存一个临时文件，零时文件命名  文件名称 + 网络地址md5
             */
            String downMetaInfoName = fileName + "_" + URLEncoder.encode(Md5.md5(fileUrl).toLowerCase(),"utf-8");
            metaPath = saveFilePath + File.separator + downMetaInfoName + ".meta";
            downInfo.setMetaPath(metaPath);
            File metaFile = new File(metaPath);
            if (!metaFile.exists() || metaFile.length() == 0) {
                File dirFile = new File(saveFilePath);
                if (!dirFile.exists()) {
                    dirFile.mkdirs();
                }
                metaFile.createNewFile();

                String filePath = saveFilePath + File.separator + fileName;

                downInfo.setFileLength(fileLength);
                downInfo.setFileUrlPath(fileUrl);
                downInfo.setThreadCount(getThreadCount(fileLength));
                downInfo.setFilePath(filePath);

                downInfo.setPices(getPiecesList(fileLength, downInfo.getThreadCount()));
                saveMeta2File();
            } else {
                out = new ByteArrayOutputStream();
                fileInputStream = new FileInputStream(metaFile);
                IOUtils.copy(fileInputStream, out);
                downInfo = JSON.parseObject(out.toByteArray(), DownInfo.class);
            }
            File downFile = new File(downInfo.getFilePath());
            if (!downFile.exists()) {
                randomAccessFile = new RandomAccessFile(downFile, "rw");
                randomAccessFile.setLength(downInfo.getFileLength());

            }
            allTreadCount = downInfo.getPices().size();
            log.info(metaPath + " 文件下载信息 " + JSON.toJSONString(downInfo));
        } catch (Exception e) {
            log.error(this.getClass().getName(), e);
        } finally {
            if (randomAccessFile != null) {
                try {
                    randomAccessFile.close();
                } catch (Exception e) {
                    log.error(this.getClass().getName(), e);
                }
            }

            if (connection != null) {
                try {
                    connection.disconnect();
                } catch (Exception e) {
                    log.error(this.getClass().getName(), e);
                }
            }

            if (out != null) {
                try {
                    out.flush();
                    out.close();
                } catch (Exception e) {
                    log.error(this.getClass().getName(), e);
                }
            }
            if (fileInputStream != null) {
                try {
                    fileInputStream.close();
                } catch (Exception e) {
                    log.error(this.getClass().getName(), e);
                }
            }
        }

    }

    /**
     * 保存每个线程下载的快照，下载失败后继续重上次下载处继续下载
     */
    private void saveMeta2File() {
        FileOutputStream fileInputStream = null;
        try {
            fileInputStream = new FileOutputStream(metaPath);
            fileInputStream.write(JSON.toJSONString(downInfo).getBytes());
        } catch (Exception e) {
            log.error(this.getClass().getName(), e);
        } finally {
            if (fileInputStream != null) {
                try {
                    fileInputStream.flush();
                    fileInputStream.close();
                } catch (IOException e) {
                    log.error(this.getClass().getName(), e);
                }
            }

        }
    }


    /**
     * 计算需要的线程数量，每个线程下载 1024 * 1024 * 3
     *
     * @param fileLength
     * @return
     */
    int getThreadCount(long fileLength) {
        int threadCount = (int) (fileLength / (1024 * 1024 * 3));
        if(threadCount == 0){
            return 1;
        }else {
            return threadCount;
        }
    }

    /**
     * 计算下载需要多少线程，以及每个线程下载文件的大小
     *
     * @param fileLength
     * @param threadCount
     * @return
     */
    private Map getPiecesList(long fileLength, int threadCount) {
        long pieceSize = fileLength / threadCount;

        Map map = new HashMap();
        for (int i = 0; i < threadCount; i++) {
            Piece piece = new Piece();
            piece.setPieceStartPostion(i * pieceSize);
            piece.setRangPation(i * pieceSize);
            if (i + 1 == threadCount) {//最后一个线程下载的结束位置就是文件的大小
                piece.setPieceEndPosition(fileLength);
            } else {
                piece.setPieceEndPosition((i + 1) * pieceSize - 1);
            }
            map.put("thread" + (i + 1), piece);
        }
        return map;
    }


    private class DownThread implements Runnable {
        private long pieceStartPostion; //开始位置
        private long pieceEndPosition; //结束位置
        private String status; // 0 未完成  1 完成
        private long rangPation; // 当前下载位置
        private long downSize; // 下载了多少
        private String fileUrl; // 文件网络地址
        private String threadIndex; // 线程标号
        private String filePath; // 文件下载地址

        public DownThread(long pieceStartPostion, long pieceEndPosition, String status, long rangPation, long downSize, String fileUrl, String threadIndex, String filePath) {
            this.pieceStartPostion = pieceStartPostion;
            this.pieceEndPosition = pieceEndPosition;
            this.status = status;
            this.rangPation = rangPation;
            this.downSize = downSize;
            this.fileUrl = fileUrl;
            this.threadIndex = threadIndex;
            this.filePath = filePath;
        }

        @Override
        public void run() {
            HttpURLConnection connection = null;
            InputStream inputStream = null;

            RandomAccessFile randomAccessFile = null;
            try {
                if (rangPation < pieceEndPosition && flag == 0) {
                    URL url = new URL(fileUrl);
                    connection = (HttpURLConnection) url.openConnection();
                    connection.setConnectTimeout(10000);
                    connection.setRequestMethod("GET");
                    connection.setReadTimeout(10000);
                    //设置http拉取文件的位置                               开始          -        结束
                    connection.setRequestProperty("Range", "bytes=" + rangPation + "-" + pieceEndPosition);
                    Integer code = connection.getResponseCode();
                    if (code == 206) {//200：请求全部资源成功， 206代表部分资源请求成功
                        randomAccessFile = new RandomAccessFile(new File(filePath), "rw");//获取前面已创建的文件.
                        randomAccessFile.seek(rangPation);//文件写入的开始位置.
                        inputStream = connection.getInputStream();
                        byte[] buffer = new byte[1024];
                        int length = -1;
                        while ((length = inputStream.read(buffer)) > 0) {
                            //记录本次下载文件的大小
                            randomAccessFile.write(buffer, 0, length);
                            rangPation += length;
                            downSize += length;
                            //更改内存中的下载信息
                            Piece piece = downInfo.getPices().get(threadIndex);
                            piece.setRangPation(rangPation);
                            piece.setDownSize(downSize);
                        }
                        //如果有一次网络正常，则继续下载
                        zeroSpeedCount = 0;
                    } else {
                        throw new Exception("http status error" + code);
                    }
                }
                //记录下载成功的线程
                complieTreadSet.add(threadIndex);

            } catch (Exception e) {
                log.error(this.getClass().getName(), e);
                //记录失败的线程
                failThreadSet.add(this.threadIndex);
            } finally { //释放资源
                if (randomAccessFile != null) {
                    try {
                        randomAccessFile.close();
                    } catch (IOException e) {
                        log.error(this.getClass().getName(), e);
                    }
                }
                if (connection != null) {
                    connection.disconnect();
                }
                if (inputStream != null) {
                    try {
                        inputStream.close();
                    } catch (IOException e) {
                        log.error(this.getClass().getName(), e);
                    }
                }

            }
        }

    }

    /**
     * flag //1下载成功  -1下载过程中有线程下载失败 -2下载过程产生的线程总数大于预计线程总数 -3下载过程中网络速度为0的次数超过15次
     *
     * @param fileUrl
     * @param saveFilePath
     * @return
     * @throws ExecutionException
     * @throws InterruptedException
     */
    public int startDown(String fileUrl, String saveFilePath) throws ExecutionException, InterruptedException {
        log.info("start down file " + fileUrl);
        downFile(fileUrl, saveFilePath);
        while (true) {
            if (flag == 1 && executor.isShutdown()) {
                log.info(metaPath + " down file succss " + fileUrl);
                log.info(metaPath + " 删除临时记录文件meta ");
                File metaFile = new File(metaPath);
                metaFile.delete();
                return flag;
            }
            if (flag < 0 && executor.isShutdown()) {
                log.info("下载状态 : 0 正在下载， 1下载成功，  -1下载过程中有线程下载失败 -2下载过程产生的线程总数大于预计线程总数 -3下载过程中网络速度为0的次数超过15次 ");
                log.info(metaPath + " down file fail , flag " + flag + " , fileUrl " + fileUrl);
                return flag;
            }
            Thread.sleep(1000);
        }
    }



}
