# java-downFile
由于要写一个从第三方服务器拉取文件的插件，百度了很多都 不是很满意，所以自己写了一个，废话少说，看代码，欢迎拍板

断点续传，多线程下载文件，idea开发工具需要安装 lombok 插件，具体百度下吧，
不过没有也没关系，我这里主要lombok 的set get、log 两个方法，具体自己可以重写

pom.xml dependency 依赖


        <dependency>
            <groupId>org.slf4j</groupId>
            <artifactId>slf4j-log4j12</artifactId>
            <version>1.5.8</version>
        </dependency>
        
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <version>1.16.10</version>
        </dependency>

        <dependency>
            <groupId>com.alibaba</groupId>
            <artifactId>fastjson</artifactId>
            <version>1.2.33</version>
        </dependency>

        <dependency>
            <groupId>commons-io</groupId>
            <artifactId>commons-io</artifactId>
            <version>2.5</version>
        </dependency>

