package cn.edu.gdou.jingbanyou.tourist;

import com.aliyun.oss OSS;
import com.aliyun.oss OSSClientBuilder;
import com.aliyun.oss.common.auth.DefaultCredential;
import com.aliyun.oss.common.auth.Credentials;
import com.aliyun.oss.model.ListBucketsRequest;
import com.aliyun.oss.model.ListBucketsResult;
import lombok.extern.slf4j.Slf4j;

/**
 * OSS 配置测试脚本
 * 用于验证阿里云 OSS AccessKey/SecretKey 是否配置正确
 */
@Slf4j
public class OssConfigTest {

    public static void main(String[] args) {
        // 配置参数
        String endpoint = "oss-cn-beijing.aliyuncs.com";
        String accessKeyId = "LTAI5t5nJGzDbFXWaqVktKAK";
        String accessKeySecret = "jM0BTeIOXmHMJd6n2v7WGATfnTC63C";
        String bucketName = "java-ai-c-z-h";

        log.info("========== OSS 配置测试 ==========");
        log.info("Endpoint: {}", endpoint);
        log.info("AccessKey ID: {}", accessKeyId);
        log.info("AccessKey Secret: {}", accessKeySecret.substring(0, 4) + "****");
        log.info("Bucket Name: {}", bucketName);

        try {
            // 创建凭证
            Credentials credentials = new DefaultCredential(accessKeyId, accessKeySecret);

            // 创建 OSS 客户端
            OSS ossClient = new OSSClientBuilder().build(endpoint, credentials);

            log.info("OSS 客户端创建成功！");

            // 尝试列出 buckets 验证连接
            ListBucketsRequest request = new ListBucketsRequest();
            ListBucketsResult result = ossClient.listBuckets(request);

            log.info("连接成功！共 {} 个 Bucket", result.getBuckets().size());
            result.getBuckets().forEach(bucket -> {
                log.info("  - Bucket: {} (创建时间: {})", bucket.getName(), bucket.getCreationDate());
            });

            // 验证指定的 bucket 是否存在
            boolean bucketExists = ossClient.doesBucketExist(bucketName);
            log.info("Bucket [{}] 是否存在: {}", bucketName, bucketExists);

            if (bucketExists) {
                log.info("配置完全正确！可以正常访问 OSS");
            } else {
                log.error("Bucket [{}] 不存在！请检查 bucket 名称是否正确", bucketName);
            }

            // 关闭客户端
            ossClient.shutdown();
            log.info("========== 测试完成 ==========");

        } catch (Exception e) {
            log.error("========== 测试失败 ==========");
            log.error("错误类型: {}", e.getClass().getName());
            log.error("错误信息: {}", e.getMessage());

            if (e.getMessage() != null) {
                if (e.getMessage().contains("AccessKey Id does not exist")) {
                    log.error("原因: AccessKey ID 不存在或配置错误！");
                } else if (e.getMessage().contains("AccessKey secret")) {
                    log.error("原因: AccessKey Secret 错误！");
                } else if (e.getMessage().contains("The bucket you are attempting to access")) {
                    log.error("原因: Bucket 名称错误或无权限访问！");
                }
            }

            e.printStackTrace();
        }
    }
}
