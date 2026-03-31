package com.example.authorization.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("license_file")
public class LicenseFile {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String customerName;

    private String customerMachineCode;

    private String licenseFileUrl;

    private LocalDateTime generateDate;

    private LocalDate expiryDate;

    private Integer userCount;
}