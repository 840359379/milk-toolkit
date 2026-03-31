package com.example.authorization.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.authorization.entity.LicenseFile;
import com.example.authorization.mapper.LicenseFileMapper;
import com.example.authorization.service.ILicenseFileService;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.Base64;
import java.util.UUID;

@Service
@Log4j2
public class LicenseFileServiceImpl extends ServiceImpl<LicenseFileMapper, LicenseFile> implements ILicenseFileService {

    @Value("${file.upload-dir:./uploads}")
    private String uploadDir;

    @Value("${file.access-url-prefix:/files/}")
    private String accessUrlPrefix;

    @Value("${license.private-key}")
    private String privateKeyBase64;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LicenseFile uploadFile(MultipartFile file, String customerName, String customerMachineCode,
                                  LocalDate expiryDate, Integer userCount) throws IOException {
        // 创建上传目录
        File dir = new File(uploadDir);
        if (!dir.exists()) {
            dir.mkdirs();
        }

        // 生成唯一文件名
        String originalFilename = file.getOriginalFilename();
        String extension = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            extension = originalFilename.substring(originalFilename.lastIndexOf("."));
        }
        String storedFilename = UUID.randomUUID() + extension;

        // 保存文件到本地
        Path filePath = Paths.get(uploadDir, storedFilename);
        Files.copy(file.getInputStream(), filePath);

        // 构建访问URL
        String fileUrl = accessUrlPrefix + storedFilename;

        // 创建数据库记录
        LicenseFile licenseFile = new LicenseFile();
        licenseFile.setCustomerName(customerName);
        licenseFile.setCustomerMachineCode(customerMachineCode);
        licenseFile.setLicenseFileUrl(fileUrl);
        licenseFile.setGenerateDate(LocalDateTime.now());
        licenseFile.setExpiryDate(expiryDate);
        licenseFile.setUserCount(userCount);

        this.save(licenseFile);
        return licenseFile;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteWithFile(Long id) {
        LicenseFile licenseFile = this.getById(id);
        if (licenseFile == null) {
            return false;
        }

        // 删除本地文件
        String fileUrl = licenseFile.getLicenseFileUrl();
        if (fileUrl != null && fileUrl.startsWith(accessUrlPrefix)) {
            String filename = fileUrl.substring(accessUrlPrefix.length());
            Path filePath = Paths.get(uploadDir, filename);
            try {
                Files.deleteIfExists(filePath);
            } catch (IOException e) {
                // 记录日志，但继续删除数据库记录
                log.error("删除文件失败: {}", filePath, e);
            }
        }

        // 删除数据库记录
        return this.removeById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean addFile(LicenseFile licenseFile) {
        try {
            // 1. 获取机器码和有效期
            String machineCode = licenseFile.getCustomerMachineCode();
            if (machineCode == null || machineCode.isEmpty()) {
                throw new IllegalArgumentException("机器码不能为空");
            }
            LocalDate expiryDate = licenseFile.getExpiryDate();
            if (expiryDate == null) {
                throw new IllegalArgumentException("有效期不能为空");
            }

            // 2. 将有效期格式化为固定10字节字符串 (YYYY-MM-DD)
            String expiryStr = expiryDate.toString(); // 格式如 "2026-12-31"
            if (expiryStr.length() != 10) {
                throw new IllegalArgumentException("有效期格式错误");
            }

            // 3. 拼接要签名的数据：机器码 + "|" + 有效期（也可以加上用户数，按需扩展）
            String dataToSign = machineCode + "|" + expiryStr;

            // 4. 使用私钥签名
            byte[] privBytes = Base64.getDecoder().decode(privateKeyBase64);
            PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(privBytes);
            KeyFactory kf = KeyFactory.getInstance("RSA");
            PrivateKey privateKey = kf.generatePrivate(spec);

            Signature sig = Signature.getInstance("SHA256withRSA");
            sig.initSign(privateKey);
            sig.update(dataToSign.getBytes(StandardCharsets.UTF_8));
            byte[] signature = sig.sign();

            // 5. 生成文件名
            String filename = UUID.randomUUID() + ".lic";
            Path filePath = Paths.get(uploadDir, filename);
            Files.createDirectories(filePath.getParent());

            // 6. 构建文件内容：有效期(10字节) + 签名
            byte[] expiryBytes = expiryStr.getBytes(StandardCharsets.UTF_8);
            byte[] fileData = new byte[expiryBytes.length + signature.length];
            System.arraycopy(expiryBytes, 0, fileData, 0, expiryBytes.length);
            System.arraycopy(signature, 0, fileData, expiryBytes.length, signature.length);

            Files.write(filePath, fileData);

            // 7. 生成访问URL
            String fileUrl = accessUrlPrefix + filename;

            // 8. 填充实体类其他字段
            licenseFile.setLicenseFileUrl(fileUrl);
            licenseFile.setGenerateDate(LocalDateTime.now());
            if (licenseFile.getUserCount() == null) {
                licenseFile.setUserCount(0);
            }

            // 9. 保存到数据库
            boolean saved = this.save(licenseFile);
            if (!saved) {
                throw new RuntimeException("保存数据库记录失败");
            }

            return true;
        } catch (Exception e) {
            log.error("生成授权文件失败", e);
            throw new RuntimeException("生成授权文件失败: " + e.getMessage(), e);
        }
    }
}