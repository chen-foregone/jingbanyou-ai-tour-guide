package cn.edu.gdou.jingbanyou;

import cn.xuyanwu.spring.file.storage.EnableFileStorage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.mybatis.spring.boot.autoconfigure.MybatisAutoConfiguration;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * 启动程序
 *
 * @author ruoyi
 */
@Slf4j
@EnableFileStorage
@SpringBootApplication(exclude = { DataSourceAutoConfiguration.class, MybatisAutoConfiguration.class })
@EnableAsync
public class RuoYiApplication
{
    public static void main(String[] args)
    {
        SpringApplication.run(RuoYiApplication.class, args);
        log.info("若依系统启动成功");
    }
}
