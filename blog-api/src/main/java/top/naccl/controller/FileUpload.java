package top.naccl.controller;


import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import top.naccl.annotation.testPermission;
import top.naccl.model.vo.Result;
import top.naccl.util.upload.UploadUtils;

@RestController
@RequestMapping("/file")
public class FileUpload {

    @PostMapping("/uploader")
    public Result upload(MultipartFile file) throws Exception {
        String result = UploadUtils.upload(file);
        return Result.ok(result);
    }
    @PostMapping("/uploader/img")
    public Result upload(MultipartFile file,Boolean isImg) throws Exception {
        if(isImg){
            return Result.ok(UploadUtils.upload(file,"img"));
        }
        return Result.ok("上传失败");
    }
}
