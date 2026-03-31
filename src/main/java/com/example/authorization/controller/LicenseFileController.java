package com.example.authorization.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.authorization.entity.LicenseFile;
import com.example.authorization.service.ILicenseFileService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;

@RestController
@RequestMapping("/api/license/files")
@RequiredArgsConstructor
public class LicenseFileController {

    private final ILicenseFileService licenseFileService;

    @Value("${file.upload-dir:./uploads}")
    private String uploadDir;

    /**
     * 上传授权文件
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<LicenseFile> uploadFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam("customerName") String customerName,
            @RequestParam("customerMachineCode") String customerMachineCode,
            @RequestParam(value = "expiryDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate expiryDate,
            @RequestParam(value = "userCount", defaultValue = "0") Integer userCount) throws IOException {

        LicenseFile saved = licenseFileService.uploadFile(file, customerName, customerMachineCode, expiryDate, userCount);
        return ResponseEntity.ok(saved);
    }

    /**
     * 分页查询授权文件列表
     */
    @GetMapping("listFiles")
    public ResponseEntity<Page<LicenseFile>> listFiles(
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "10") int size) {
        Page<LicenseFile> pageResult = licenseFileService.page(new Page<>(page, size));
        return ResponseEntity.ok(pageResult);
    }

    @PostMapping("/addFile")
    public ResponseEntity<LicenseFile> addFile(@RequestBody LicenseFile licenseFile){
        licenseFileService.addFile(licenseFile);
        return ResponseEntity.ok(licenseFile);
    }

    /**
     * 删除授权文件记录（同时删除本地文件）
     */
    @PostMapping("/delete")
    public ResponseEntity<Void> deleteFile(@RequestBody LicenseFile licenseFile) {
        boolean deleted = licenseFileService.deleteWithFile(licenseFile.getId());
        if (!deleted) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.noContent().build();
    }

    /**
     * 下载授权文件（通过记录ID）
     */
    @GetMapping("/{id}/download")
    public ResponseEntity<Resource> downloadFile(@PathVariable("id") Long id) throws MalformedURLException {
        LicenseFile licenseFile = licenseFileService.getById(id);
        if (licenseFile == null || licenseFile.getLicenseFileUrl() == null) {
            return ResponseEntity.notFound().build();
        }

        String fileUrl = licenseFile.getLicenseFileUrl();
        // 从URL中提取文件名（假设URL格式为 /files/{filename}）
        String filename = fileUrl.substring(fileUrl.lastIndexOf("/") + 1);
        Path filePath = Paths.get(uploadDir).resolve(filename).normalize();
        Resource resource = new UrlResource(filePath.toUri());

        if (!resource.exists()) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + resource.getFilename() + "\"")
                .body(resource);
    }
}