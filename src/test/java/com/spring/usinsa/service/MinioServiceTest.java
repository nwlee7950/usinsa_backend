package com.spring.usinsa.service;

import com.spring.usinsa.exception.ApiErrorCode;
import com.spring.usinsa.exception.ApiException;
import io.minio.*;
import io.minio.errors.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
public class MinioServiceTest {

    @Autowired
    private MinioClient minioClient;

    private String filePath ="test.txt";
    private String uploadFilePath ="test.txt";
    private String bucket = "usinsa";
    private String contentType = "text/plain";
    private InputStream inputStream;

    @Test
    public void uploadFile() {
        try {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(uploadFilePath)
                    .stream(inputStream, -1, 10485760)
                    .contentType(contentType)
                    .build());
        } catch (Exception e) {
            System.out.println(e.getMessage());
            throw new ApiException(ApiErrorCode.MINIO_INVALID_UPLOAD_REQUEST);
        }
    }

    @Test
    public void getFile() {
        InputStream file = null;
        try {
            InputStream obj = minioClient.getObject(GetObjectArgs.builder()
                    .bucket(bucket)
                    .object(filePath)
                    .build());
            file = obj;
            assertThat(file).isNotNull();
        } catch (Exception e) {
            System.out.println(e.getMessage());
            System.out.println("getFile - ?????? ????????? ?????? ??????????????? ?????????????????????.");
        }

    }

    @Test
    public void delete(){
        InputStream file = null;

        // ?????? ??? ?????? ?????? ??????
        try {
            InputStream obj = minioClient.getObject(GetObjectArgs.builder()
                    .bucket(bucket)
                    .object(filePath)
                    .build());
            file = obj;
        }catch (Exception e){
            assertThat(e.getMessage()).isEqualTo("The specified key does not exist.");
            System.out.println("?????? ????????? ???????????? ????????????.");
            return;
        }

        // ?????? ??????
        try {
            minioClient.removeObject(RemoveObjectArgs.builder()
                    .bucket(bucket)
                    .object(filePath)
                    .build());

            //?????? ?????? ??? ?????? ??????, ??? ??????????????? ??????
            InputStream fileDown = null;
            try {
                InputStream obj = minioClient.getObject(GetObjectArgs.builder()
                        .bucket(bucket)
                        .object(filePath)
                        .build());
                fileDown = obj;
                assertThat(file).isNull();
            }catch (Exception e){
                assertThat(e.getMessage()).isEqualTo("The specified key does not exist.");
                System.out.println("?????? ?????? ??????");
            }
        } catch (Exception e) {
            System.out.println(e.getMessage());
            System.out.println("removeFile - ?????? ????????? ?????????????????????.");
//            throw new ApiException(ApiErrorCode.MINIO_INVALID_REMOVE_REQUEST);
        }
    }

    @Test
    public void getObjectStat() {
        StatObjectResponse statObject = null;
        try {
            statObject = minioClient.statObject(StatObjectArgs.builder()
                    .bucket(bucket)
                    .object(filePath)
                    .build());

            assertThat(validateContentType(statObject.contentType()))
                    .isEqualTo(true);
        } catch (Exception e) {
            System.out.println("getObjectStat - ?????? ????????? ????????? ???????????? ????????????.");
        }
    }

    public boolean validateContentType(String contentType) {
        if(!(contentType.equals("image/png") || contentType.equals("image/jpg") || contentType.equals("image/jpeg") || contentType.equals("text/plain"))) {
            return false;
        }else{
            return true;
        }
    }

}
