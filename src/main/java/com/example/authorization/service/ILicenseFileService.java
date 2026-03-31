package com.example.authorization.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.authorization.entity.LicenseFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;

public interface ILicenseFileService extends IService<LicenseFile> {

    /**
     * 上传授权文件并保存记录
     * @param file 上传的文件
     * @param customerName 客户名称
     * @param customerMachineCode 客户机器码
     * @param expiryDate 授权至日期
     * @param userCount 用户数
     * @return 保存后的实体
     */
    LicenseFile uploadFile(MultipartFile file, String customerName, String customerMachineCode,
                           LocalDate expiryDate, Integer userCount) throws IOException;

    /**
     * 根据ID删除记录，并删除关联的文件
     * @param id 记录ID
     * @return 是否成功
     */
    boolean deleteWithFile(Long id);

    Boolean addFile(LicenseFile licenseFile);
}